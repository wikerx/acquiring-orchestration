package com.scott.payment.risk.service.impl;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.risk.domain.FrequencySuccessReservationResult;
import com.scott.payment.risk.domain.FrequencySuccessReservationTransitionSummary;
import com.scott.payment.risk.service.FrequencySuccessReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用 Redis Cluster 同槽脚本实现频控成功名额生命周期。
 */
@Slf4j
@Service
public class DefaultFrequencySuccessReservationService
        implements FrequencySuccessReservationService {

    private static final String SCRIPT_BASE_PATH = "META-INF/payment/redis/scripts/v1/";

    private static final DefaultRedisScript<List> RESERVE_SCRIPT =
            listScript("frequency-success-reserve.lua");

    private static final DefaultRedisScript<List> TRANSITION_SCRIPT =
            listScript("frequency-success-transition.lua");

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            longScript("frequency-success-release.lua");

    private final StringRedisTemplate redisTemplate;

    private final PaymentRedisProperties redisProperties;

    /**
     * 创建频控成功名额生命周期服务。
     *
     * @param redisTemplate 执行预占、确认和释放 Lua 的 Redis 模板
     * @param redisProperties 构造环境隔离和 Cluster 共槽 Key 的配置
     */
    public DefaultFrequencySuccessReservationService(StringRedisTemplate redisTemplate,
                                                     PaymentRedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
    }

    /**
     * 原子预占一条交易的频控成功名额。
     *
     * @param merchantId 当前商户号
     * @param transactionId 支付系统交易号
     * @param ruleId 频率规则主键
     * @param counterIdentity 不可逆计数维度摘要
     * @param successLimit 窗口允许的最大成功笔数
     * @param windowSeconds 固定窗口秒数
     * @return 预占、幂等、超限、关闭或不可用结果
     */
    @Override
    public FrequencySuccessReservationResult reserve(String merchantId,
                                                     String transactionId,
                                                     long ruleId,
                                                     String counterIdentity,
                                                     int successLimit,
                                                     int windowSeconds) {
        if (!validIdentity(merchantId, transactionId)
                || ruleId <= 0L
                || !StringUtils.hasText(counterIdentity)
                || successLimit <= 0
                || windowSeconds <= 0) {
            return FrequencySuccessReservationResult.unavailable();
        }
        String indexKey = transactionIndexKey(merchantId, transactionId);
        String counterKey = counterKey(merchantId, ruleId, counterIdentity);
        try {
            List<?> rawResult = redisTemplate.execute(
                    RESERVE_SCRIPT,
                    List.of(counterKey, indexKey),
                    String.valueOf(successLimit),
                    String.valueOf(windowSeconds));
            if (rawResult == null || rawResult.size() < 2) {
                throw new IllegalStateException("frequency success reserve script returned no result");
            }
            long code = longValue(rawResult.get(0));
            long currentCount = Math.max(0L, longValue(rawResult.get(1)));
            FrequencySuccessReservationResult.Outcome outcome = switch ((int) code) {
                case 1 -> FrequencySuccessReservationResult.Outcome.RESERVED;
                case 2 -> FrequencySuccessReservationResult.Outcome.IDEMPOTENT;
                case 0 -> FrequencySuccessReservationResult.Outcome.LIMIT_EXCEEDED;
                default -> FrequencySuccessReservationResult.Outcome.CLOSED;
            };
            return new FrequencySuccessReservationResult(outcome, currentCount);
        } catch (RuntimeException exception) {
            log.warn("频控成功名额预占失败, event: RISK_FREQUENCY_SUCCESS_RESERVE_FAILED, merchantDigest: {}, transactionDigest: {}, ruleId: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(merchantId.trim()),
                    RedisKeyDigest.sha256(transactionId.trim()),
                    ruleId,
                    exception.getClass().getSimpleName());
            return FrequencySuccessReservationResult.unavailable();
        }
    }

    /**
     * 将指定交易持有的频控成功名额确认为已成功使用。
     *
     * @param merchantId 当前商户号
     * @param transactionId 支付系统交易号
     * @return 实际迁移、幂等和冲突数量
     */
    @Override
    public FrequencySuccessReservationTransitionSummary confirm(String merchantId,
                                                                String transactionId) {
        List<?> result = transition(merchantId, transactionId, "CONFIRM");
        long code = transitionCode(result);
        return switch ((int) code) {
            case 1 -> new FrequencySuccessReservationTransitionSummary(1, 0, 0);
            case 2 -> new FrequencySuccessReservationTransitionSummary(0, 1, 0);
            case -1 -> new FrequencySuccessReservationTransitionSummary(0, 0, 1);
            default -> FrequencySuccessReservationTransitionSummary.empty();
        };
    }

    /**
     * 释放指定交易尚未确认的全部频控成功名额。
     *
     * @param merchantId 当前商户号
     * @param transactionId 支付系统交易号
     * @return 实际释放、幂等和冲突数量
     */
    @Override
    public FrequencySuccessReservationTransitionSummary release(String merchantId,
                                                                String transactionId) {
        String indexKey = requireTransactionIndexKey(merchantId, transactionId);
        List<?> transitionResult = transition(indexKey, "RELEASE");
        long code = transitionCode(transitionResult);
        if (code == -1L) {
            return new FrequencySuccessReservationTransitionSummary(0, 0, 1);
        }
        if (code == 3L) {
            return new FrequencySuccessReservationTransitionSummary(0, 1, 0);
        }
        if (code == 0L) {
            return FrequencySuccessReservationTransitionSummary.empty();
        }
        List<String> releaseKeys = releaseKeys(indexKey, transitionResult);
        Long released = redisTemplate.execute(RELEASE_SCRIPT, releaseKeys);
        if (released == null) {
            throw new IllegalStateException("frequency success release script returned no result");
        }
        return new FrequencySuccessReservationTransitionSummary(
                Math.toIntExact(Math.max(0L, released)),
                code == 2L ? 1 : 0,
                0);
    }

    /**
     * 使用商户和交易标识定位交易名额索引并执行状态迁移。
     *
     * @param merchantId 当前商户号
     * @param transactionId 支付系统交易号
     * @param action CONFIRM 或 RELEASE
     * @return Lua 返回的迁移状态和关联计数 Key
     */
    private List<?> transition(String merchantId, String transactionId, String action) {
        return transition(requireTransactionIndexKey(merchantId, transactionId), action);
    }

    /**
     * 对已验证的交易索引执行原子状态迁移。
     *
     * @param indexKey 交易成功名额索引 Key
     * @param action CONFIRM 或 RELEASE
     * @return Lua 返回的迁移状态和关联计数 Key
     */
    private List<?> transition(String indexKey, String action) {
        List<?> result = redisTemplate.execute(TRANSITION_SCRIPT, List.of(indexKey), action);
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("frequency success transition script returned no result");
        }
        return result;
    }

    /**
     * 校验 Lua 返回的计数 Key 均属于同一商户槽位后组装释放参数。
     *
     * @param indexKey 交易成功名额索引 Key
     * @param transitionResult 状态迁移 Lua 返回值
     * @return 以索引 Key 开头的共槽释放 Key
     */
    private List<String> releaseKeys(String indexKey, List<?> transitionResult) {
        List<String> keys = new ArrayList<>();
        keys.add(indexKey);
        String expectedPrefix = indexKey.substring(0, indexKey.indexOf("}:" ) + 2) + "rule:";
        String expectedTag = hashTag(indexKey);
        for (int index = 1; index < transitionResult.size(); index++) {
            String counterKey = String.valueOf(transitionResult.get(index));
            if (!counterKey.startsWith(expectedPrefix) || !expectedTag.equals(hashTag(counterKey))) {
                throw new IllegalStateException("frequency success transition returned an invalid counter key");
            }
            keys.add(counterKey);
        }
        return keys;
    }

    /**
     * 校验交易身份完整并返回对应的索引 Key。
     *
     * @param merchantId 当前商户号
     * @param transactionId 支付系统交易号
     * @return 环境隔离的交易名额索引 Key
     */
    private String requireTransactionIndexKey(String merchantId, String transactionId) {
        if (!validIdentity(merchantId, transactionId)) {
            throw new IllegalArgumentException("frequency success transaction identity is incomplete");
        }
        return transactionIndexKey(merchantId, transactionId);
    }

    /**
     * 构造交易级成功名额索引 Key，交易号只以不可逆摘要参与。
     *
     * @param merchantId 当前商户号
     * @param transactionId 支付系统交易号
     * @return Redis Cluster 共槽的交易索引 Key
     */
    private String transactionIndexKey(String merchantId, String transactionId) {
        return redisProperties.coLocatedBusinessKey(
                "risk",
                "frequency-success",
                merchantSlotIdentity(merchantId),
                "transaction",
                RedisKeyDigest.sha256(transactionId.trim()),
                "reservations");
    }

    /**
     * 构造单规则、单元素维度的成功计数 Key。
     *
     * @param merchantId 当前商户号
     * @param ruleId 频率规则主键
     * @param counterIdentity 不可逆元素身份摘要
     * @return Redis Cluster 共槽的成功计数 Key
     */
    private String counterKey(String merchantId, long ruleId, String counterIdentity) {
        return redisProperties.coLocatedBusinessKey(
                "risk",
                "frequency-success",
                merchantSlotIdentity(merchantId),
                "rule",
                String.valueOf(ruleId),
                "element",
                RedisKeyDigest.sha256(counterIdentity.trim()),
                "counter");
    }

    /**
     * 生成只用于 Redis Cluster 槽位选择的商户摘要。
     *
     * @param merchantId 当前商户号
     * @return 不可逆商户摘要
     */
    private String merchantSlotIdentity(String merchantId) {
        return RedisKeyDigest.sha256(merchantId.trim());
    }

    /**
     * 校验执行成功名额生命周期所需的基础设施和业务身份。
     *
     * @param merchantId 当前商户号
     * @param transactionId 支付系统交易号
     * @return 输入及依赖完整时返回 {@code true}
     */
    private boolean validIdentity(String merchantId, String transactionId) {
        return redisTemplate != null
                && redisProperties != null
                && StringUtils.hasText(merchantId)
                && StringUtils.hasText(transactionId);
    }

    /**
     * 读取 Lua 返回列表首项的状态码。
     *
     * @param result 非空 Lua 返回列表
     * @return 长整数状态码
     */
    private long transitionCode(List<?> result) {
        return longValue(result.get(0));
    }

    /**
     * 将 Redis 驱动返回的数值或文本统一转换为长整数。
     *
     * @param value Lua 返回字段
     * @return 长整数值
     */
    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    /**
     * 提取 Redis Cluster Hash Tag 以校验跨 Key 原子操作的槽位一致性。
     *
     * @param key Redis 物理 Key
     * @return 大括号内的槽位身份
     */
    private String hashTag(String key) {
        int begin = key.indexOf('{');
        int end = key.indexOf('}', begin + 1);
        if (begin < 0 || end <= begin + 1) {
            throw new IllegalStateException("frequency success key has no cluster hash tag");
        }
        return key.substring(begin + 1, end);
    }

    /**
     * 从固定类路径加载返回列表的成功名额 Lua 脚本。
     *
     * @param filename 受代码控制的脚本文件名
     * @return Spring Data Redis 列表脚本定义
     */
    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> listScript(String filename) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_BASE_PATH + filename));
        script.setResultType(List.class);
        return script;
    }

    /**
     * 从固定类路径加载返回长整数的成功名额 Lua 脚本。
     *
     * @param filename 受代码控制的脚本文件名
     * @return Spring Data Redis 长整数脚本定义
     */
    private static DefaultRedisScript<Long> longScript(String filename) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_BASE_PATH + filename));
        script.setResultType(Long.class);
        return script;
    }
}
