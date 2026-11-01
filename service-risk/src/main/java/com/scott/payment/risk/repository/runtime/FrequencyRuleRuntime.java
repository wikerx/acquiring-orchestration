package com.scott.payment.risk.repository.runtime;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.domain.FrequencySuccessReservationResult;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.RiskRuntimeLookupValue;
import com.scott.payment.risk.domain.rule.FrequencyRulePolicy;
import com.scott.payment.risk.service.FrequencySuccessReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 执行单条交易频率规则的类型化解析、Redis 原子计数和成功名额预占。
 *
 * <p>所有基础设施异常、规则配置错误和必需输入缺失均返回 {@code ERROR/REVIEW}，
 * 禁止频控不可用时静默允许交易继续。</p>
 */
@Slf4j
public class FrequencyRuleRuntime {

    private static final String SCRIPT_PATH =
            "META-INF/payment/redis/scripts/v1/frequency-increment.lua";

    /**
     * 原子递增固定窗口计数并设置首次过期时间的 Redis 脚本。
     */
    private static final DefaultRedisScript<Long> FREQUENCY_INCREMENT_SCRIPT = frequencyScript();

    private final StringRedisTemplate stringRedisTemplate;

    private final FrequencySuccessReservationService successReservationService;

    private final RiskEvaluationProperties properties;

    private final PaymentRedisProperties redisProperties;

    private final RedisBusinessMetrics metrics;

    /**
     * 创建频率规则运行时执行器。
     *
     * @param stringRedisTemplate 执行固定窗口 Lua 的 Redis 模板
     * @param successReservationService 维护成功交易名额的生命周期服务
     * @param properties 风控窗口和阈值容量上限
     * @param redisProperties Redis 业务 Key 构造配置
     * @param metrics 不包含业务标识的 Redis 指标记录器
     */
    public FrequencyRuleRuntime(StringRedisTemplate stringRedisTemplate,
                                FrequencySuccessReservationService successReservationService,
                                RiskEvaluationProperties properties,
                                PaymentRedisProperties redisProperties,
                                RedisBusinessMetrics metrics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.successReservationService = successReservationService;
        this.properties = properties;
        this.redisProperties = redisProperties;
        this.metrics = metrics == null ? RedisBusinessMetrics.noop() : metrics;
    }

    /**
     * 执行一条频率规则并构造独立审计明细。
     *
     * @param rule 当前数据库频率规则
     * @param merchantId 当前商户号
     * @param requestDTO 当前支付风控请求
     * @param cardNoLookup 卡号哈希查询值
     * @param cardFingerprintLookup 稳定卡指纹查询值
     * @param ipLookup 规范化 IP 查询值
     * @param emailLookup 邮箱哈希查询值
     * @param phoneLookup 手机号哈希查询值
     * @param customerIdLookup 商户客户标识哈希查询值
     * @param deviceFingerprintLookup 设备指纹哈希查询值
     * @return 始终可审计的 PASS、HIT 或 ERROR 明细
     */
    public RiskListMatch evaluate(RiskListMatch rule,
                                  String merchantId,
                                  RiskPaymentEvaluateRequestDTO requestDTO,
                                  RiskRuntimeLookupValue cardNoLookup,
                                  RiskRuntimeLookupValue cardFingerprintLookup,
                                  RiskRuntimeLookupValue ipLookup,
                                  RiskRuntimeLookupValue emailLookup,
                                  RiskRuntimeLookupValue phoneLookup,
                                  RiskRuntimeLookupValue customerIdLookup,
                                  RiskRuntimeLookupValue deviceFingerprintLookup) {
        FrequencyRulePolicy policy = FrequencyRulePolicy.from(rule);
        if (!isExecutable(policy)) {
            return unavailable(rule, "transaction frequency rule configuration is invalid");
        }
        Map<String, String> elementValues = elementValues(
                cardNoLookup,
                cardFingerprintLookup,
                ipLookup,
                emailLookup,
                phoneLookup,
                customerIdLookup,
                deviceFingerprintLookup);
        List<String> counterIdentities = policy.counterIdentities(elementValues);
        List<String> keys = counterIdentities.stream()
                .map(identity -> counterKey(rule, merchantId, identity))
                .toList();
        if (keys.isEmpty()) {
            return unavailable(rule, "transaction frequency rule input is unavailable");
        }

        long highestCurrentCount = 0L;
        boolean exceeded = false;
        for (String counterKey : keys) {
            Optional<Long> currentCount = increment(
                    counterKey,
                    requestDTO == null ? null : requestDTO.getTransactionId(),
                    policy.windowSeconds());
            if (currentCount.isEmpty()) {
                return unavailable(rule, "transaction frequency counter is unavailable");
            }
            highestCurrentCount = Math.max(highestCurrentCount, currentCount.get());
            if (currentCount.get() > policy.allowedCount()) {
                exceeded = true;
            }
        }

        if (!exceeded && policy.successCount() > 0) {
            RiskListMatch reservationResult = reserveSuccessSlots(
                    rule,
                    merchantId,
                    requestDTO,
                    policy,
                    counterIdentities,
                    elementValues);
            if (reservationResult != null) {
                return reservationResult;
            }
        }

        RiskListMatch detail = copyRule(rule);
        detail.setHitElement("frequency");
        detail.setHitValueMasked(policy.maskedHitValue(elementValues));
        detail.setCurrentCount(highestCurrentCount);
        detail.setMatchResult(exceeded ? "HIT" : "PASS");
        if (exceeded) {
            detail.setDecisionReason(StringUtils.hasText(rule.getDecisionReason())
                    ? rule.getDecisionReason()
                    : "transaction frequency limit hit");
        } else {
            detail.setDecisionAction("PASS");
            detail.setDecisionReason("transaction frequency count " + highestCurrentCount
                    + " is within limit " + policy.allowedCount() + ", allow");
        }
        metrics.recordOperation(
                RedisBusinessMetrics.Feature.RISK_FREQUENCY,
                RedisBusinessMetrics.Operation.EVALUATE,
                exceeded ? RedisBusinessMetrics.Outcome.HIT : RedisBusinessMetrics.Outcome.SUCCESS,
                0L);
        return detail;
    }

    /**
     * 校验频控策略同时满足配置正确性和当前部署容量上限。
     *
     * @param policy 已解析的类型化频控策略
     * @return 可以安全执行 Redis 计数时返回 {@code true}
     */
    private boolean isExecutable(FrequencyRulePolicy policy) {
        return properties != null
                && policy.isExecutable(
                        properties.getFrequencyMaxWindowSeconds(),
                        properties.getFrequencyMaxThresholdCount());
    }

    /**
     * 为成功笔数限制逐维度预占交易名额。
     *
     * <p>任何维度关闭、不可用或超限都立即返回失败关闭明细；全部维度预占成功时返回空，
     * 由调用方继续生成本次请求频率结果。</p>
     *
     * @param rule 当前频率规则
     * @param merchantId 当前商户号
     * @param requestDTO 当前支付风控请求
     * @param policy 已校验的频控策略
     * @param counterIdentities 不可逆计数维度摘要
     * @param elementValues 仅供生成脱敏命中说明的规范化元素
     * @return 超限或不可用明细；全部预占成功时返回 {@code null}
     */
    private RiskListMatch reserveSuccessSlots(RiskListMatch rule,
                                              String merchantId,
                                              RiskPaymentEvaluateRequestDTO requestDTO,
                                              FrequencyRulePolicy policy,
                                              List<String> counterIdentities,
                                              Map<String, String> elementValues) {
        if (successReservationService == null
                || rule == null
                || rule.getRuleId() == null
                || !StringUtils.hasText(merchantId)
                || requestDTO == null
                || !StringUtils.hasText(requestDTO.getTransactionId())) {
            return unavailable(rule, "transaction frequency success reservation is unavailable");
        }
        for (String counterIdentity : counterIdentities) {
            FrequencySuccessReservationResult result = successReservationService.reserve(
                    merchantId.trim(),
                    requestDTO.getTransactionId().trim(),
                    rule.getRuleId(),
                    counterIdentity,
                    policy.successCount(),
                    policy.windowSeconds());
            if (result.outcome() == FrequencySuccessReservationResult.Outcome.UNAVAILABLE
                    || result.outcome() == FrequencySuccessReservationResult.Outcome.CLOSED) {
                return unavailable(rule, "transaction frequency success reservation is unavailable");
            }
            if (result.outcome() == FrequencySuccessReservationResult.Outcome.LIMIT_EXCEEDED) {
                RiskListMatch detail = copyRule(rule);
                detail.setHitElement("frequencySuccess");
                detail.setHitValueMasked(policy.maskedHitValue(elementValues));
                detail.setThresholdCount(policy.successCount());
                detail.setCurrentCount(result.currentCount() + 1L);
                detail.setMatchResult("HIT");
                detail.setDecisionReason(StringUtils.hasText(rule.getDecisionReason())
                        ? rule.getDecisionReason()
                        : "successful transaction frequency limit hit");
                metrics.recordOperation(
                        RedisBusinessMetrics.Feature.RISK_FREQUENCY,
                        RedisBusinessMetrics.Operation.EVALUATE,
                        RedisBusinessMetrics.Outcome.HIT,
                        0L);
                return detail;
            }
        }
        return null;
    }

    /**
     * 将频控基础设施或输入不可用统一映射为 {@code ERROR/REVIEW}。
     *
     * @param rule 当前频率规则
     * @param reason 不包含敏感输入的失败原因
     * @return 阻止静默放行的人工复核明细
     */
    private RiskListMatch unavailable(RiskListMatch rule, String reason) {
        metrics.recordOperation(
                RedisBusinessMetrics.Feature.RISK_FREQUENCY,
                RedisBusinessMetrics.Operation.EVALUATE,
                RedisBusinessMetrics.Outcome.UNAVAILABLE,
                0L);
        RiskListMatch detail = copyRule(rule);
        detail.setHitElement("frequency");
        detail.setHitValueMasked("COUNTER_UNAVAILABLE");
        detail.setDecisionAction("REVIEW");
        detail.setDecisionReason(reason);
        detail.setMatchResult("ERROR");
        return detail;
    }

    /**
     * 使用交易摘要作为幂等标记执行单个频控维度计数。
     *
     * @param counterKey 与规则、商户和元素维度共槽的计数 Key
     * @param transactionId 支付系统交易号，仅用于生成不可逆摘要
     * @param windowSeconds 固定窗口秒数
     * @return 当前计数；Redis 或交易标识不可用时返回空
     */
    private Optional<Long> increment(String counterKey,
                                     String transactionId,
                                     int windowSeconds) {
        if (stringRedisTemplate == null || !StringUtils.hasText(transactionId)) {
            log.warn("频控 Redis 不可用, event: RISK_FREQUENCY_REDIS_UNAVAILABLE, counterKeyDigest: {}",
                    RedisKeyDigest.sha256(counterKey));
            return Optional.empty();
        }
        String transactionDigest = RedisKeyDigest.sha256(transactionId.trim());
        List<String> keys = List.of(
                counterKey,
                counterKey + ":transaction:" + transactionDigest
        );
        return executeIncrement(keys, windowSeconds);
    }

    /**
     * 执行固定版本 Lua 并记录无业务标识的 Redis 指标。
     *
     * @param keys 共槽计数 Key 和交易幂等 Key
     * @param windowSeconds 固定窗口秒数
     * @return 原子递增后的计数；脚本异常时返回空
     */
    private Optional<Long> executeIncrement(List<String> keys, int windowSeconds) {
        long startNanos = System.nanoTime();
        try {
            Long count = stringRedisTemplate.execute(
                    FREQUENCY_INCREMENT_SCRIPT,
                    keys,
                    String.valueOf(windowSeconds));
            if (count == null) {
                throw new IllegalStateException("frequency counter script returned null");
            }
            recordOperation(RedisBusinessMetrics.Outcome.SUCCESS, startNanos);
            return Optional.of(count);
        } catch (RuntimeException exception) {
            recordOperation(RedisBusinessMetrics.Outcome.ERROR, startNanos);
            metrics.recordLuaFailure(
                    RedisBusinessMetrics.Script.RISK_FREQUENCY_FIXED,
                    metrics.classifyFailure(exception));
            log.warn("频控计数失败, event: RISK_FREQUENCY_COUNTER_FAILED, counterKeyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(keys.get(0)),
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 汇总频控支持的规范化元素，原始敏感值不得进入 Redis Key 或日志。
     *
     * @return 仅包含本次请求可用元素的映射
     */
    private Map<String, String> elementValues(RiskRuntimeLookupValue cardNoLookup,
                                              RiskRuntimeLookupValue cardFingerprintLookup,
                                              RiskRuntimeLookupValue ipLookup,
                                              RiskRuntimeLookupValue emailLookup,
                                              RiskRuntimeLookupValue phoneLookup,
                                              RiskRuntimeLookupValue customerIdLookup,
                                              RiskRuntimeLookupValue deviceFingerprintLookup) {
        Map<String, String> values = new HashMap<>();
        putIfPresent(values, FrequencyRulePolicy.ELEMENT_CARD_NO, safeLookupHash(cardNoLookup));
        putIfPresent(values, FrequencyRulePolicy.ELEMENT_CARD_FINGERPRINT,
                safeLookupHash(cardFingerprintLookup));
        putIfPresent(values, FrequencyRulePolicy.ELEMENT_IP, safeLookupKey(ipLookup));
        putIfPresent(values, FrequencyRulePolicy.ELEMENT_EMAIL, safeLookupHash(emailLookup));
        putIfPresent(values, FrequencyRulePolicy.ELEMENT_PHONE, safeLookupHash(phoneLookup));
        putIfPresent(values, FrequencyRulePolicy.ELEMENT_CUSTOMER_ID, safeLookupHash(customerIdLookup));
        putIfPresent(values, FrequencyRulePolicy.ELEMENT_DEVICE_FINGERPRINT,
                safeLookupHash(deviceFingerprintLookup));
        return values;
    }

    /**
     * 构造与规则、商户及不可逆元素身份共槽的频控 Key。
     *
     * @param rule 当前频率规则
     * @param merchantId 当前商户号
     * @param elementIdentity 不可逆元素身份摘要
     * @return Redis Cluster 可原子执行的计数 Key
     */
    private String counterKey(RiskListMatch rule,
                              String merchantId,
                              String elementIdentity) {
        long ruleId = rule == null || rule.getRuleId() == null ? 0L : rule.getRuleId();
        String merchantSegment = StringUtils.hasText(merchantId) ? merchantId.trim() : "GLOBAL";
        String slotIdentity = ruleId + ":" + merchantSegment + ":" + elementIdentity;
        return redisProperties.coLocatedBusinessKey(
                "risk",
                "frequency",
                slotIdentity,
                "counter");
    }

    /**
     * 记录频控 Lua 的执行结果和耗时，不增加高基数业务标签。
     *
     * @param outcome Redis 操作结果
     * @param startNanos 单调时钟起点
     */
    private void recordOperation(RedisBusinessMetrics.Outcome outcome, long startNanos) {
        metrics.recordOperation(
                RedisBusinessMetrics.Feature.RISK_FREQUENCY,
                RedisBusinessMetrics.Operation.EXECUTE,
                outcome,
                System.nanoTime() - startNanos);
    }

    /**
     * 仅将非空规范值加入频控元素映射。
     *
     * @param values 待补充的元素映射
     * @param element 稳定元素编码
     * @param value 已哈希或将继续摘要的规范值
     */
    private void putIfPresent(Map<String, String> values, String element, String value) {
        if (StringUtils.hasText(value)) {
            values.put(element, value.trim());
        }
    }

    /**
     * 安全读取不可逆匹配哈希。
     *
     * @param lookupValue 规范化查询值
     * @return 查询值为空时返回 {@code null}
     */
    private String safeLookupHash(RiskRuntimeLookupValue lookupValue) {
        return lookupValue == null ? null : lookupValue.getMatchValueHash();
    }

    /**
     * 选择哈希、区间数值或规范原值作为后续摘要输入。
     *
     * <p>该返回值只能继续进入 {@link RedisKeyDigest#sha256(String)}，不得写入日志。</p>
     *
     * @param lookupValue 规范化查询值
     * @return 可继续摘要的稳定值
     */
    private String safeLookupKey(RiskRuntimeLookupValue lookupValue) {
        if (lookupValue == null) {
            return null;
        }
        if (StringUtils.hasText(lookupValue.getMatchValueHash())) {
            return lookupValue.getMatchValueHash();
        }
        if (lookupValue.getNumericValue() != null) {
            return lookupValue.getNumericValue().toPlainString();
        }
        return lookupValue.getRawValue();
    }

    /**
     * 复制规则为本次评估的独立明细，避免修改数据库或缓存共享对象。
     *
     * @param source 原始规则
     * @return 独立可修改的规则明细
     */
    private RiskListMatch copyRule(RiskListMatch source) {
        RiskListMatch target = new RiskListMatch();
        if (source == null) {
            return target;
        }
        target.setRuleId(source.getRuleId());
        target.setModuleType(source.getModuleType());
        target.setFunctionCode(source.getFunctionCode());
        target.setFunctionName(source.getFunctionName());
        target.setHitElement(source.getHitElement());
        target.setHitValueMasked(source.getHitValueMasked());
        target.setRiskLevel(source.getRiskLevel());
        target.setDecisionAction(source.getDecisionAction());
        target.setDecisionReason(source.getDecisionReason());
        target.setReasonCode(source.getReasonCode());
        target.setTimeWindowSeconds(source.getTimeWindowSeconds());
        target.setThresholdCount(source.getThresholdCount());
        target.setElementsJson(source.getElementsJson());
        target.setCurrentCount(source.getCurrentCount());
        target.setAmountLimit(source.getAmountLimit());
        target.setCurrentAmount(source.getCurrentAmount());
        target.setStageCode(source.getStageCode());
        target.setStageName(source.getStageName());
        target.setStageOrder(source.getStageOrder());
        target.setMatchResult(source.getMatchResult());
        target.setDecisionEffect(source.getDecisionEffect());
        return target;
    }

    /**
     * 从固定类路径加载频控原子递增脚本。
     *
     * @return 结果类型为长整数的 Spring Data Redis 脚本定义
     */
    private static DefaultRedisScript<Long> frequencyScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_PATH));
        script.setResultType(Long.class);
        return script;
    }
}
