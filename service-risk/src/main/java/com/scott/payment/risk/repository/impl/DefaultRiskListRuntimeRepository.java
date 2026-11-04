package com.scott.payment.risk.repository.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionPrimaryRouteScope;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import com.scott.payment.component.redis.string.RedisStringService;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.config.RiskBaselineMode;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.config.RiskRuleCacheMode;
import com.scott.payment.risk.domain.FrequencyRuleEvaluation;
import com.scott.payment.risk.domain.MerchantLimitEvaluation;
import com.scott.payment.risk.domain.MerchantLimitReadEvaluation;
import com.scott.payment.risk.domain.MerchantLimitReservation;
import com.scott.payment.risk.domain.RiskListFunction;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.RiskRuntimeLookupValue;
import com.scott.payment.risk.domain.RiskRuleSnapshot;
import com.scott.payment.risk.domain.RiskRuleSnapshotRow;
import com.scott.payment.risk.domain.SourceUrlRuleEvaluation;
import com.scott.payment.risk.domain.state.MerchantLimitReservationStatus;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import com.scott.payment.risk.mapper.RiskRuntimeMapper;
import com.scott.payment.risk.observability.RiskShadowComparisonMonitor;
import com.scott.payment.risk.repository.RiskListRuntimeRepository;
import com.scott.payment.risk.repository.runtime.FrequencyRuleRuntime;
import com.scott.payment.risk.repository.runtime.RiskRuleCacheRuntime;
import com.scott.payment.risk.service.MerchantLimitReservationStateService;
import com.scott.payment.risk.service.FrequencySuccessReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Redis 优先、数据库可回源的风控运行时仓储。
 *
 * <p>规则缓存通过 generation 隔离变更；累计限额和频控计数仍由持久化事实及原子脚本
 * 共同保护，不因查询合并改变失败关闭、预占或回滚语义。</p>
 */
@Slf4j
@Service
public class DefaultRiskListRuntimeRepository implements RiskListRuntimeRepository {

    /**
     * 金额进入 Redis Lua 前采用的固定小数位数，转换过程禁止隐式舍入。
     */
    private static final int INTERNAL_AMOUNT_SCALE = 6;

    /**
     * 规则 generation 状态使用的兼容命名空间，不直接作为新业务 Key 前缀。
     */
    private static final String RULE_CACHE_NAMESPACE = "risk-runtime-rule";

    /**
     * Lua 双精度整数可精确表达的上限，超过该值时拒绝执行资金计数脚本。
     */
    private static final long MAX_SAFE_LUA_INTEGER = 9_007_199_254_740_991L;

    /**
     * 按规则时区自然日累计的限额类型。
     */
    private static final String LIMIT_TYPE_DAILY = "DAILY";

    /**
     * 按规则时区周一至周日累计的限额类型。
     */
    private static final String LIMIT_TYPE_WEEKLY = "WEEKLY";

    /**
     * 按规则时区自然月累计的限额类型。
     */
    private static final String LIMIT_TYPE_MONTHLY = "MONTHLY";

    /**
     * 受版本控制的 Redis Lua 脚本类路径，脚本文件名仅由代码内常量提供。
     */
    private static final String SCRIPT_BASE_PATH = "META-INF/payment/redis/scripts/v1/";

    /**
     * 持久化累计限额预占使用的唯一 Redis 计数投影标识。
     */
    private static final String CLUSTER_SAFE_COUNTER_MODE = "CLUSTER_SAFE";

    private static final DefaultRedisScript<Long> MERCHANT_LIMIT_RESERVE_SCRIPT =
            redisScript("merchant-limit-reserve.lua");

    private static final DefaultRedisScript<Long> MERCHANT_LIMIT_ROLLBACK_SCRIPT =
            redisScript("merchant-limit-rollback.lua");

    /**
     * 原子替换单个精确名单 Hash，避免读路径观察到只写入一部分字段的快照。
     */
    private static final DefaultRedisScript<Long> RISK_HASH_SNAPSHOT_REPLACE_SCRIPT =
            redisScript("risk-hash-snapshot-replace.lua");

    /**
     * 精确名单 Hash 内保存 generation、loaded 和 count 的保留字段。
     */
    private static final String SNAPSHOT_META_FIELD = "@meta";

    /**
     * 超容量数据集使用的短 TTL 旁路标记后缀，标记值为规则 generation。
     */
    private static final String SNAPSHOT_CAPACITY_BYPASS_SUFFIX = ":capacity-bypass";


    /**
     * 从固定类路径加载返回长整数的 Redis Lua 脚本。
     *
     * @param filename 受控脚本文件名
     * @return 延迟交由 Spring Data Redis 执行的脚本定义
     */
    private static DefaultRedisScript<Long> redisScript(String filename) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_BASE_PATH + filename));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 查询数据库规则、交易事实和预占基线的运行时 Mapper。
     */
    private final RiskRuntimeMapper riskRuntimeMapper;

    /**
     * 读写可降级风控规则缓存的 Redis String 基础服务。
     */
    private final RedisStringService redisStringService;

    /**
     * 提供规则缓存 generation，用于发布后整体切换命名空间。
     */
    private final RedisCacheGenerationStore cacheGenerationStore;

    /**
     * 执行累计限额和频率计数 Lua 脚本的原生字符串模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 风控运行开关、缓存 TTL、数据库基线模式和容量上限配置。
     */
    private final RiskEvaluationProperties properties;

    /**
     * 按统一环境前缀构建 Redis 业务 Key 的配置组件。
     */
    private final PaymentRedisProperties redisProperties;

    /**
     * 持久化累计限额预占生命周期，作为 Redis 计数的补偿事实。
     */
    private final MerchantLimitReservationStateService reservationStateService;

    /**
     * 原子维护频控成功次数名额，并由支付终态确认或释放。
     */
    private final FrequencySuccessReservationService frequencySuccessReservationService;

    /**
     * 风控数据库基线比较汇总器；未装配时不影响生产决策。
     */
    private final RiskShadowComparisonMonitor shadowComparisonMonitor;

    /**
     * Redis 业务指标记录器，不接收商户、规则、交易或 Key 维度。
     */
    private final RedisBusinessMetrics metrics;

    /**
     * 执行频率策略解析、Redis 原子计数和成功名额预占。
     */
    private final FrequencyRuleRuntime frequencyRuleRuntime;

    /**
     * 统一处理 generation Cache-Aside、空命中缓存和 Redis 异常回源。
     */
    private final RiskRuleCacheRuntime ruleCacheRuntime;

    /**
     * 创建 Redis 优先、数据库可回源的风控运行时仓储。
     *
     * <p>基础设施通过可选 Provider 装配；缺少强依赖时相关规则返回空或 REVIEW，
     * 不允许因 Redis 异常静默放行累计限额和频率规则。</p>
     *
     */
    @Autowired
    public DefaultRiskListRuntimeRepository(
            ObjectProvider<RiskRuntimeMapper> riskRuntimeMapperProvider,
            ObjectProvider<RedisStringService> redisStringServiceProvider,
            ObjectProvider<RedisCacheGenerationStore> cacheGenerationStoreProvider,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            ObjectProvider<MerchantLimitReservationStateService> reservationStateServiceProvider,
            ObjectProvider<FrequencySuccessReservationService> frequencySuccessReservationServiceProvider,
            ObjectProvider<RiskShadowComparisonMonitor> shadowComparisonMonitorProvider,
            RiskEvaluationProperties properties,
            PaymentRedisProperties redisProperties,
            ObjectProvider<RedisBusinessMetrics> metricsProvider) {
        this(
                riskRuntimeMapperProvider,
                redisStringServiceProvider,
                cacheGenerationStoreProvider,
                stringRedisTemplateProvider,
                reservationStateServiceProvider,
                frequencySuccessReservationServiceProvider.getIfAvailable(),
                shadowComparisonMonitorProvider,
                properties,
                redisProperties,
                metricsProvider.getIfAvailable(RedisBusinessMetrics::noop)
        );
        if (riskRuntimeMapper == null) {
            throw new IllegalStateException("RiskRuntimeMapper is required for production risk evaluation");
        }
    }

    /**
     * 创建不产生指标副作用的风控运行时仓储，供纯单元测试和隔离集成测试直接构造。
     *
     * @param riskRuntimeMapperProvider 风控 Mapper 提供器
     * @param redisStringServiceProvider Redis String 服务提供器
     * @param cacheGenerationStoreProvider 缓存代际存储提供器
     * @param stringRedisTemplateProvider Redis 模板提供器
     * @param reservationStateServiceProvider 累计限额生命周期服务提供器
     * @param shadowComparisonMonitorProvider shadow 比较器提供器
     * @param properties 风控运行配置
     * @param redisProperties Redis Key 配置
     */
    public DefaultRiskListRuntimeRepository(
            ObjectProvider<RiskRuntimeMapper> riskRuntimeMapperProvider,
            ObjectProvider<RedisStringService> redisStringServiceProvider,
            ObjectProvider<RedisCacheGenerationStore> cacheGenerationStoreProvider,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            ObjectProvider<MerchantLimitReservationStateService> reservationStateServiceProvider,
            ObjectProvider<RiskShadowComparisonMonitor> shadowComparisonMonitorProvider,
            RiskEvaluationProperties properties,
            PaymentRedisProperties redisProperties) {
        this(
                riskRuntimeMapperProvider,
                redisStringServiceProvider,
                cacheGenerationStoreProvider,
                stringRedisTemplateProvider,
                reservationStateServiceProvider,
                null,
                shadowComparisonMonitorProvider,
                properties,
                redisProperties,
                RedisBusinessMetrics.noop()
        );
    }

    /**
     * 创建可注入频控成功名额服务的仓储，供隔离测试验证成功次数规则。
     *
     * @param frequencySuccessReservationServiceProvider 频控成功名额服务提供器
     */
    public DefaultRiskListRuntimeRepository(
            ObjectProvider<RiskRuntimeMapper> riskRuntimeMapperProvider,
            ObjectProvider<RedisStringService> redisStringServiceProvider,
            ObjectProvider<RedisCacheGenerationStore> cacheGenerationStoreProvider,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            ObjectProvider<MerchantLimitReservationStateService> reservationStateServiceProvider,
            ObjectProvider<FrequencySuccessReservationService> frequencySuccessReservationServiceProvider,
            ObjectProvider<RiskShadowComparisonMonitor> shadowComparisonMonitorProvider,
            RiskEvaluationProperties properties,
            PaymentRedisProperties redisProperties) {
        this(
                riskRuntimeMapperProvider,
                redisStringServiceProvider,
                cacheGenerationStoreProvider,
                stringRedisTemplateProvider,
                reservationStateServiceProvider,
                frequencySuccessReservationServiceProvider.getIfAvailable(),
                shadowComparisonMonitorProvider,
                properties,
                redisProperties,
                RedisBusinessMetrics.noop()
        );
    }

    /**
     * 统一完成依赖解包，确保生产自动配置和直接构造测试使用相同字段初始化逻辑。
     *
     * <p>各 Provider 允许在隔离测试或关闭对应能力时为空；运行时方法必须按既定策略回源
     * 主库、返回空结果或进入 REVIEW。指标记录器必须非空，未启用指标时传入 noop 实现。</p>
     *
     * @param riskRuntimeMapperProvider 查询规则和交易事实的 Mapper 提供器
     * @param redisStringServiceProvider 读写可降级规则缓存的 String 服务提供器
     * @param cacheGenerationStoreProvider 协调规则缓存 generation 的存储提供器
     * @param stringRedisTemplateProvider 执行风控 Lua 的字符串模板提供器
     * @param reservationStateServiceProvider 持久化累计限额预占生命周期的服务提供器
     * @param frequencySuccessReservationService 可空的频控成功名额生命周期服务
     * @param shadowComparisonMonitorProvider 汇总新旧路径比较结果的监控器提供器
     * @param properties 风控运行、迁移模式、TTL 和容量配置
     * @param redisProperties Redis 环境前缀和业务 Key 构造配置
     * @param metrics 非空的 Redis 业务指标记录器或 noop 实现
     */
    private DefaultRiskListRuntimeRepository(
            ObjectProvider<RiskRuntimeMapper> riskRuntimeMapperProvider,
            ObjectProvider<RedisStringService> redisStringServiceProvider,
            ObjectProvider<RedisCacheGenerationStore> cacheGenerationStoreProvider,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            ObjectProvider<MerchantLimitReservationStateService> reservationStateServiceProvider,
            FrequencySuccessReservationService frequencySuccessReservationService,
            ObjectProvider<RiskShadowComparisonMonitor> shadowComparisonMonitorProvider,
            RiskEvaluationProperties properties,
            PaymentRedisProperties redisProperties,
            RedisBusinessMetrics metrics) {
        this.riskRuntimeMapper = riskRuntimeMapperProvider.getIfAvailable();
        this.redisStringService = redisStringServiceProvider.getIfAvailable();
        this.cacheGenerationStore = cacheGenerationStoreProvider.getIfAvailable();
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.reservationStateService = reservationStateServiceProvider.getIfAvailable();
        this.frequencySuccessReservationService = frequencySuccessReservationService;
        this.shadowComparisonMonitor = shadowComparisonMonitorProvider.getIfAvailable();
        this.properties = properties;
        this.redisProperties = redisProperties;
        this.metrics = metrics;
        this.frequencyRuleRuntime = new FrequencyRuleRuntime(
                this.stringRedisTemplate,
                this.frequencySuccessReservationService,
                this.properties,
                this.redisProperties,
                this.metrics);
        this.ruleCacheRuntime = new RiskRuleCacheRuntime(
                this.redisStringService,
                this.cacheGenerationStore,
                this.properties,
                this.redisProperties);
    }

    /**
     * 查询名单首条命中并使用当前 generation 做 Cache-Aside 加速。
     *
     * <p>动态表名必须先经过 {@link RiskListFunction#requireKnownTable(String)} 白名单校验；
     * Redis 或 generation 不可用时回源数据库，查询值只使用脱敏值、哈希或区间数值。</p>
     *
     * @param function 名单功能及其受控表名和匹配方式
     * @param merchantId 当前商户号
     * @param lookupValue 已完成脱敏、哈希或区间归一化的查询值
     * @return 商户级优先于全局规则的命中明细；未命中或运行时关闭时返回空
     */
    @Override
    @DS(DataSourceName.MASTER)
    public Optional<RiskListMatch> findListMatch(RiskListFunction function,
                                                 String merchantId,
                                                 RiskRuntimeLookupValue lookupValue) {
        if (function == null || lookupValue == null || riskRuntimeMapper == null) {
            return Optional.empty();
        }
        RiskListFunction.requireKnownTable(function.getTableName());
        Supplier<Optional<RiskListMatch>> legacyLoader = () -> loadListMatch(function, merchantId, lookupValue);
        if (ruleCacheMode() == RiskRuleCacheMode.LEGACY) {
            return ruleCacheRuntime.findMatch(
                    legacyLoader,
                    "match",
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    safeMerchant(merchantId),
                    RedisKeyDigest.sha256(matchCacheValue(function, lookupValue))
            );
        }
        SnapshotListMatch snapshotResult = snapshotListMatch(function, merchantId, lookupValue);
        if (ruleCacheMode() == RiskRuleCacheMode.SHADOW) {
            Optional<RiskListMatch> legacyResult = ruleCacheRuntime.findMatch(
                    legacyLoader,
                    "match",
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    safeMerchant(merchantId),
                    RedisKeyDigest.sha256(matchCacheValue(function, lookupValue))
            );
            recordSnapshotShadowDifference(
                    "list-" + function.name(),
                    legacyResult,
                    snapshotResult.available() ? snapshotResult.match() : Optional.empty(),
                    snapshotResult.available()
            );
            return legacyResult;
        }
        if (snapshotResult.available()) {
            return snapshotResult.match();
        }
        return legacyLoader.get();
    }

    /**
     * 判断指定名单功能是否存在当前商户可用的启用规则。
     *
     * <p>结果用于生成 HIT/MISS 完整审计明细；缓存不可用时回源数据库，
     * 不将基础设施异常解释为存在规则。</p>
     *
     * @param function 名单功能及其受控表名
     * @param merchantId 当前商户号
     * @return 存在商户级或适用全局规则时返回 {@code true}
     */
    @Override
    @DS(DataSourceName.MASTER)
    public boolean hasActiveListRule(RiskListFunction function, String merchantId) {
        if (function == null || riskRuntimeMapper == null) {
            return false;
        }
        RiskListFunction.requireKnownTable(function.getTableName());
        Supplier<Boolean> legacyLoader =
                () -> riskRuntimeMapper.countActiveListRules(
                        function.getTableName(),
                        trim(merchantId),
                        function.getModuleType().getCode(),
                        function.getFunctionCode()) > 0;
        if (ruleCacheMode() == RiskRuleCacheMode.LEGACY) {
            return ruleCacheRuntime.resolveBoolean(
                    legacyLoader,
                    "list",
                    "active",
                    function.name(),
                    safeMerchant(merchantId)
            );
        }
        SnapshotListMatch snapshotResult = snapshotListMatch(function, merchantId, null);
        if (ruleCacheMode() == RiskRuleCacheMode.SHADOW) {
            boolean legacyResult = ruleCacheRuntime.resolveBoolean(
                    legacyLoader,
                    "list",
                    "active",
                    function.name(),
                    safeMerchant(merchantId)
            );
            if (snapshotResult.available() && legacyResult != snapshotResult.anyRows()) {
                log.warn("风控规则快照影子比对不一致, event: RISK_RULE_SNAPSHOT_SHADOW_MISMATCH, dataset: {}, comparison: ACTIVE_FLAG",
                        function.name());
            }
            return legacyResult;
        }
        return snapshotResult.available()
                ? snapshotResult.anyRows()
                : Boolean.TRUE.equals(legacyLoader.get());
    }

    /**
     * 从主库读取商户功能开通状态；平台全局规则不依赖该状态。
     */
    @Override
    @DS(DataSourceName.MASTER)
    public boolean isMerchantFunctionEnabled(RiskListFunction function, String merchantId) {
        if (function == null || !StringUtils.hasText(merchantId)
                || riskRuntimeMapper == null) {
            return false;
        }
        return riskRuntimeMapper.countEnabledMerchantFunction(
                trim(merchantId),
                function.getModuleType().getCode(),
                function.getFunctionCode()) > 0;
    }

    /**
     * 通过单次主库查询返回商户已开通名单功能，避免同一交易按多个输入维度重复查询。
     */
    @Override
    @DS(DataSourceName.MASTER)
    public Set<RiskListFunction> findEnabledMerchantFunctions(String merchantId) {
        if (!StringUtils.hasText(merchantId) || riskRuntimeMapper == null) {
            return Set.of();
        }
        String normalizedMerchantId = trim(merchantId);
        List<String> enabledKeyValues = properties.isReadQueryConsolidationEnabled()
                ? ruleCacheRuntime.findValues(
                        () -> riskRuntimeMapper.selectEnabledMerchantFunctionKeys(normalizedMerchantId),
                        "merchant-function",
                        "enabled",
                        safeMerchant(normalizedMerchantId))
                : riskRuntimeMapper.selectEnabledMerchantFunctionKeys(normalizedMerchantId);
        Set<String> enabledKeys = enabledKeyValues == null || enabledKeyValues.isEmpty()
                ? Set.of()
                : Set.copyOf(enabledKeyValues);
        EnumSet<RiskListFunction> enabledFunctions = EnumSet.noneOf(RiskListFunction.class);
        for (RiskListFunction function : RiskListFunction.values()) {
            String key = function.getModuleType().getCode() + ":" + function.getFunctionCode();
            if (enabledKeys.contains(key)) {
                enabledFunctions.add(function);
            }
        }
        return Set.copyOf(enabledFunctions);
    }

    /**
     * 查询当前商户可用的来源主机白名单规则，命中时返回受控放行结论。
     *
     * @param merchantId 当前商户号
     * @param lookupValue 已规范化的来源主机查询值
     * @return 数据库或快照命中时返回 PASS 明细；未配置或未命中时返回空
     */
    @Override
    @DS(DataSourceName.MASTER)
    public Optional<RiskListMatch> findSourceUrlRule(String merchantId, RiskRuntimeLookupValue lookupValue) {
        if (!StringUtils.hasText(merchantId)
                || lookupValue == null || !StringUtils.hasText(lookupValue.getSourceHost()) || riskRuntimeMapper == null) {
            return Optional.empty();
        }
        Supplier<Optional<RiskListMatch>> legacyLoader = () -> {
            RiskListMatch matchedRule = riskRuntimeMapper.selectSourceUrlRule(
                    merchantId.trim(),
                    lookupValue.getSourceHost()
            );
            if (matchedRule == null) {
                return Optional.empty();
            }
            // 来源网址限定是允许清单：记录上的动作描述未命中时的处置，命中 host 必须放行。
            matchedRule.setRiskLevel("LOW");
            matchedRule.setDecisionAction("PASS");
            matchedRule.setDecisionReason("merchant source url is allowed");
            return Optional.of(matchedRule);
        };
        if (ruleCacheMode() == RiskRuleCacheMode.LEGACY) {
            return ruleCacheRuntime.findMatch(
                    legacyLoader,
                    "rule",
                    "source-url",
                    merchantId,
                    RedisKeyDigest.sha256(safeKey(lookupValue.getMatchValueHash()))
            );
        }
        SnapshotRows snapshot = sourceUrlSnapshot(merchantId.trim());
        Optional<RiskListMatch> snapshotResult = snapshot.available()
                ? snapshot.rows().stream()
                .filter(this::isSourceUrlRuntimeAllowed)
                .filter(row -> equalsIgnoreCase(row.getSourceHost(), lookupValue.getSourceHost()))
                .map(row -> {
                    row.setRiskLevel("LOW");
                    row.setDecisionAction("PASS");
                    row.setDecisionReason("merchant source url is allowed");
                    return (RiskListMatch) row;
                })
                .findFirst()
                : Optional.empty();
        if (ruleCacheMode() == RiskRuleCacheMode.SHADOW) {
            Optional<RiskListMatch> legacyResult = ruleCacheRuntime.findMatch(
                    legacyLoader,
                    "rule",
                    "source-url",
                    merchantId,
                    RedisKeyDigest.sha256(safeKey(lookupValue.getMatchValueHash()))
            );
            recordSnapshotShadowDifference("source-url", legacyResult, snapshotResult, snapshot.available());
            return legacyResult;
        }
        return snapshot.available() ? snapshotResult : legacyLoader.get();
    }

    /**
     * 从同一来源网址快照同时解析允许命中和限制未命中。
     *
     * <p>仅在查询合并开关和 SNAPSHOT 模式同时开启时复用快照；LEGACY、SHADOW 或快照
     * 不可用时回到原有方法，确保迁移比较和数据库回源语义不变。</p>
     *
     * @param merchantId 当前商户号
     * @param lookupValue 已规范化的来源主机查询值
     * @return 允许命中和限制未命中的组合结果
     */
    @Override
    @DS(DataSourceName.MASTER)
    public SourceUrlRuleEvaluation evaluateSourceUrlRules(String merchantId,
                                                          RiskRuntimeLookupValue lookupValue) {
        if (!properties.isReadQueryConsolidationEnabled()
                || ruleCacheMode() != RiskRuleCacheMode.SNAPSHOT) {
            return RiskListRuntimeRepository.super.evaluateSourceUrlRules(merchantId, lookupValue);
        }
        if (!StringUtils.hasText(merchantId) || riskRuntimeMapper == null) {
            return SourceUrlRuleEvaluation.empty();
        }
        String normalizedMerchantId = merchantId.trim();
        String sourceHost = lookupValue == null ? null : lookupValue.getSourceHost();
        SnapshotRows snapshot = sourceUrlSnapshot(normalizedMerchantId);
        if (!snapshot.available()) {
            return RiskListRuntimeRepository.super.evaluateSourceUrlRules(merchantId, lookupValue);
        }
        Optional<RiskListMatch> allowedRule = !StringUtils.hasText(sourceHost)
                ? Optional.empty()
                : snapshot.rows().stream()
                .filter(this::isSourceUrlRuntimeAllowed)
                .filter(row -> equalsIgnoreCase(row.getSourceHost(), sourceHost))
                .map(row -> {
                    row.setRiskLevel("LOW");
                    row.setDecisionAction("PASS");
                    row.setDecisionReason("merchant source url is allowed");
                    return (RiskListMatch) row;
                })
                .findFirst();
        Optional<RiskListMatch> restrictionMiss = snapshot.rows().isEmpty() || allowedRule.isPresent()
                ? Optional.empty()
                : Optional.of(RiskListMatch.system(
                "sourceUrl",
                "商户来源网址限定",
                "sourceUrl",
                StringUtils.hasText(sourceHost) ? sourceHost : "EMPTY",
                "HIGH",
                "REJECT",
                "merchant source url is not allowed"));
        return new SourceUrlRuleEvaluation(allowedRule, restrictionMiss);
    }

    /**
     * 判断来源主机是否未命中商户白名单，并返回对应风控处置规则。
     *
     * @param merchantId 当前商户号
     * @param lookupValue 已规范化的来源主机查询值；主机缺失时仍需执行未命中判断
     * @return 已配置有效白名单但当前主机未命中时返回 REJECT 明细；未配置或已命中时返回空
     */
    @Override
    @DS(DataSourceName.MASTER)
    public Optional<RiskListMatch> findSourceUrlRestrictionMiss(String merchantId, RiskRuntimeLookupValue lookupValue) {
        if (!StringUtils.hasText(merchantId) || riskRuntimeMapper == null) {
            return Optional.empty();
        }
        String normalizedMerchantId = merchantId.trim();
        String sourceHost = lookupValue == null ? null : lookupValue.getSourceHost();
        Supplier<Optional<RiskListMatch>> legacyLoader = () -> {
            long activeCount = riskRuntimeMapper.countActiveSourceUrlRules(normalizedMerchantId);
            if (activeCount <= 0) {
                return Optional.empty();
            }
            if (StringUtils.hasText(sourceHost) && riskRuntimeMapper.countActiveSourceUrlHit(normalizedMerchantId, sourceHost) > 0) {
                return Optional.empty();
            }
            return Optional.of(RiskListMatch.system(
                    "sourceUrl",
                    "商户来源网址限定",
                    "sourceUrl",
                    StringUtils.hasText(sourceHost) ? sourceHost : "EMPTY",
                    "HIGH",
                    "REJECT",
                    "merchant source url is not allowed"
            ));
        };
        if (ruleCacheMode() == RiskRuleCacheMode.LEGACY) {
            return ruleCacheRuntime.findMatch(
                    legacyLoader,
                    "rule",
                    "source-url",
                    "miss",
                    normalizedMerchantId,
                    RedisKeyDigest.sha256(safeKey(sourceHost))
            );
        }
        SnapshotRows snapshot = sourceUrlSnapshot(normalizedMerchantId);
        Optional<RiskListMatch> snapshotResult = Optional.empty();
        if (snapshot.available()
                && !snapshot.rows().isEmpty()
                && (!StringUtils.hasText(sourceHost)
                || snapshot.rows().stream().noneMatch(row -> isSourceUrlRuntimeAllowed(row)
                && equalsIgnoreCase(row.getSourceHost(), sourceHost)))) {
            snapshotResult = Optional.of(RiskListMatch.system(
                    "sourceUrl",
                    "商户来源网址限定",
                    "sourceUrl",
                    StringUtils.hasText(sourceHost) ? sourceHost : "EMPTY",
                    "HIGH",
                    "REJECT",
                    "merchant source url is not allowed"
            ));
        }
        if (ruleCacheMode() == RiskRuleCacheMode.SHADOW) {
            Optional<RiskListMatch> legacyResult = ruleCacheRuntime.findMatch(
                    legacyLoader,
                    "rule",
                    "source-url",
                    "miss",
                    normalizedMerchantId,
                    RedisKeyDigest.sha256(safeKey(sourceHost))
            );
            recordSnapshotShadowDifference("source-url-miss", legacyResult, snapshotResult, snapshot.available());
            return legacyResult;
        }
        return snapshot.available() ? snapshotResult : legacyLoader.get();
    }

    /**
     * 判断来源网址快照行能否参与交易匹配。
     *
     * <p>旧版本 Redis 快照没有 {@code runtimeAllowed} 字段，但当时只缓存已启用记录，
     * 因此 {@code null} 必须按允许处理；新快照会为待审、拒绝或交易禁止记录显式写入 {@code false}。</p>
     */
    private boolean isSourceUrlRuntimeAllowed(RiskRuleSnapshotRow row) {
        return row != null && !Boolean.FALSE.equals(row.getRuntimeAllowed());
    }

    /**
     * 查询当前请求 IP 是否命中已启用的商户 IP 白名单。
     *
     * <p>只匹配审核通过、已启用且未删除的 IP；缓存 Key 使用 IP 摘要，
     * 返回明细中的 IP 仅供受控风控审计使用，不得扩散到普通业务日志。</p>
     *
     * @param merchantId 当前商户号
     * @param lookupValue 已完成格式校验的 IP 查询值
     * @return 白名单命中时的 PASS 明细；未配置、参数无效或未命中时返回空
     */
    @Override
    @DS(DataSourceName.MASTER)
    public Optional<RiskListMatch> findMerchantIpWhitelistHit(String merchantId, RiskRuntimeLookupValue lookupValue) {
        if (!StringUtils.hasText(merchantId) || riskRuntimeMapper == null) {
            return Optional.empty();
        }
        String normalizedMerchantId = merchantId.trim();
        String ipValue = lookupValue == null ? null : lookupValue.getRawValue();
        if (!StringUtils.hasText(ipValue)) {
            return Optional.empty();
        }
        return ruleCacheRuntime.findMatch(() -> {
            if (riskRuntimeMapper.countMerchantIpWhitelistHit(normalizedMerchantId, ipValue) <= 0) {
                return Optional.empty();
            }
            return Optional.of(RiskListMatch.system(
                    "merchantIpWhitelist",
                    "商户IP白名单",
                    "ip",
                    ipValue,
                    "LOW",
                    "PASS",
                    "merchant ip whitelist hit"
            ));
        }, "merchant-ip-whitelist", "hit", normalizedMerchantId,
                RedisKeyDigest.sha256(safeKey(ipValue)));
    }

    /**
     * 对商户业务 API 的 IP 白名单准入执行纵深校验。
     *
     * <p>有效名单为空、可信 IP 缺失或未命中均失败关闭；
     * Hosted Checkout 在上层依据可信内部来源跳过本方法。</p>
     *
     * @return IP 准入未通过时的拒绝明细，命中有效白名单时返回空
     */
    @Override
    @DS(DataSourceName.MASTER)
    public Optional<RiskListMatch> findMerchantIpWhitelistMiss(String merchantId, RiskRuntimeLookupValue lookupValue) {
        if (!StringUtils.hasText(merchantId) || riskRuntimeMapper == null) {
            return Optional.empty();
        }
        String normalizedMerchantId = merchantId.trim();
        String ipValue = lookupValue == null ? null : lookupValue.getRawValue();
        return ruleCacheRuntime.findMatch(() -> {
            if (StringUtils.hasText(ipValue)
                    && riskRuntimeMapper.countMerchantIpWhitelistHit(normalizedMerchantId, ipValue) > 0) {
                return Optional.empty();
            }
            String reason = StringUtils.hasText(ipValue)
                    ? "merchant ip whitelist missed"
                    : "merchant request ip missing";
            return Optional.of(merchantIpWhitelistMiss(ipValue, reason));
        }, "merchant-ip-whitelist", "miss", normalizedMerchantId,
                RedisKeyDigest.sha256(safeKey(ipValue)));
    }

    /**
     * 构建商户 IP 白名单失败关闭明细。
     *
     * @param ipValue 可信请求 IP；缺失时使用明确空值标识
     * @param reason 不包含敏感上下文的拒绝原因
     * @return 固定高风险拒绝明细
     */
    private RiskListMatch merchantIpWhitelistMiss(String ipValue, String reason) {
        return RiskListMatch.system(
                "merchantIpWhitelist",
                "商户IP白名单",
                "ip",
                StringUtils.hasText(ipValue) ? ipValue : "EMPTY",
                "HIGH",
                "REJECT",
                reason
        );
    }

    /**
     * 查询交易金额是否违反当前商户的单笔最低或最高限额。
     *
     * @return 首条超限规则；运行时关闭、参数不完整或未超限时返回空
     */
    @Override
    @DS(DataSourceName.MASTER)
    public Optional<RiskListMatch> findMerchantLimitRule(String merchantId, BigDecimal amount, String currency) {
        if (amount == null || !StringUtils.hasText(currency) || riskRuntimeMapper == null) {
            return Optional.empty();
        }
        String normalizedCurrency = currency.trim().toUpperCase();
        Supplier<Optional<RiskListMatch>> legacyLoader =
                () -> Optional.ofNullable(riskRuntimeMapper.selectMerchantLimitRule(
                trim(merchantId),
                amount,
                normalizedCurrency
        ));
        if (ruleCacheMode() == RiskRuleCacheMode.LEGACY) {
            return ruleCacheRuntime.findMatch(
                    legacyLoader,
                    "rule",
                    "merchant-limit",
                    safeMerchant(merchantId),
                    normalizedCurrency,
                    RedisKeyDigest.sha256(amount.stripTrailingZeros().toPlainString())
            );
        }
        SnapshotRows snapshot = merchantLimitSnapshot(merchantId, normalizedCurrency);
        Optional<RiskListMatch> snapshotResult = snapshot.available()
                ? snapshot.rows().stream()
                .filter(row -> singleMerchantLimitMatches(row, amount))
                .map(row -> (RiskListMatch) row)
                .findFirst()
                : Optional.empty();
        if (ruleCacheMode() == RiskRuleCacheMode.SHADOW) {
            Optional<RiskListMatch> legacyResult = ruleCacheRuntime.findMatch(
                    legacyLoader,
                    "rule",
                    "merchant-limit",
                    safeMerchant(merchantId),
                    normalizedCurrency,
                    RedisKeyDigest.sha256(amount.stripTrailingZeros().toPlainString())
            );
            recordSnapshotShadowDifference("merchant-limit-single", legacyResult, snapshotResult, snapshot.available());
            return legacyResult;
        }
        return snapshot.available() ? snapshotResult : legacyLoader.get();
    }

    /**
     * 从同一限额快照查询单笔超限和规则启用状态，不执行累计限额预占。
     *
     * <p>只有 SNAPSHOT 模式复用集合；LEGACY、SHADOW 或快照不可用时保留原有两条兼容路径。
     * 累计规则仍由后续同步预占方法独立读取和处理。</p>
     *
     * @param merchantId 当前商户号
     * @param amount 交易金额
     * @param currency ISO 4217 币种代码
     * @return 单笔限额命中和规则启用状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    public MerchantLimitReadEvaluation evaluateMerchantLimitRules(String merchantId,
                                                                  BigDecimal amount,
                                                                  String currency) {
        if (!properties.isReadQueryConsolidationEnabled()
                || ruleCacheMode() != RiskRuleCacheMode.SNAPSHOT) {
            return RiskListRuntimeRepository.super.evaluateMerchantLimitRules(merchantId, amount, currency);
        }
        if (!StringUtils.hasText(currency) || riskRuntimeMapper == null) {
            return MerchantLimitReadEvaluation.empty();
        }
        String normalizedCurrency = normalizedUpper(currency);
        SnapshotRows snapshot = merchantLimitSnapshot(merchantId, normalizedCurrency);
        if (!snapshot.available()) {
            return RiskListRuntimeRepository.super.evaluateMerchantLimitRules(merchantId, amount, currency);
        }
        Optional<RiskListMatch> singleLimitHit = amount == null
                ? Optional.empty()
                : snapshot.rows().stream()
                .filter(row -> singleMerchantLimitMatches(row, amount))
                .map(row -> (RiskListMatch) row)
                .findFirst();
        return new MerchantLimitReadEvaluation(singleLimitHit, !snapshot.rows().isEmpty());
    }

    /**
     * 兼容旧调用方执行累计限额预占，不创建持久化生命周期记录。
     *
     * @param requestDTO 当前风控交易请求
     * @return 逐条累计限额明细及可回滚 Redis 预占
     */
    @Override
    @DS(DataSourceName.MASTER)
    public MerchantLimitEvaluation reserveCumulativeMerchantLimits(RiskPaymentEvaluateRequestDTO requestDTO) {
        return reserveCumulativeMerchantLimits(requestDTO, null);
    }

    /**
     * 原子预占当前交易在日、周、月规则中的累计金额。
     *
     * <p>金额先转换为六位小数定标整数；Redis 脚本、分表基线或生命周期状态不可用时返回
     * REVIEW 明细。任一规则拒绝时回滚本次已完成预占，避免部分周期残留。</p>
     *
     * @param requestDTO 当前风控交易请求
     * @param riskRecordNo 本次稳定风控流水号；有值时同步维护数据库预占生命周期
     * @return 累计限额执行明细及仅在全部通过后保留的可回滚预占
     */
    @Override
    @DS(DataSourceName.MASTER)
    public MerchantLimitEvaluation reserveCumulativeMerchantLimits(RiskPaymentEvaluateRequestDTO requestDTO,
                                                                   String riskRecordNo) {
        if (requestDTO == null || riskRuntimeMapper == null
                || !StringUtils.hasText(requestDTO.getMerchantId())
                || !StringUtils.hasText(requestDTO.getCurrency())) {
            return MerchantLimitEvaluation.empty();
        }
        String merchantId = requestDTO.getMerchantId().trim();
        String currency = requestDTO.getCurrency().trim().toUpperCase(Locale.ROOT);
        List<RiskListMatch> rules = activeCumulativeMerchantLimitRules(merchantId, currency);
        if (rules.isEmpty()) {
            return MerchantLimitEvaluation.empty();
        }
        List<RiskListMatch> details = new ArrayList<>();
        List<MerchantLimitReservation> reservations = new ArrayList<>();
        boolean lifecycleManaged = reservationStateService != null && StringUtils.hasText(riskRecordNo);
        boolean limitBlocked = false;
        if (requestDTO.getAmount() == null || !StringUtils.hasText(requestDTO.getTransactionId())
                || stringRedisTemplate == null) {
            details.add(cumulativeLimitUnavailable(rules.get(0), "merchant cumulative limit runtime is unavailable"));
            return new MerchantLimitEvaluation(details, List.of());
        }
        long amountUnits;
        try {
            amountUnits = amountUnits(requestDTO.getAmount());
        } catch (ArithmeticException exception) {
            details.add(cumulativeLimitUnavailable(rules.get(0), "merchant cumulative limit amount precision is invalid"));
            return new MerchantLimitEvaluation(details, List.of());
        }
        if (amountUnits <= 0 || amountUnits > MAX_SAFE_LUA_INTEGER) {
            details.add(cumulativeLimitUnavailable(rules.get(0), "merchant cumulative limit amount is out of range"));
            return new MerchantLimitEvaluation(details, List.of());
        }
        LocalDateTime evaluationTime = requestDTO.getTransactionDateTime() == null
                ? LocalDateTime.now()
                : requestDTO.getTransactionDateTime();
        for (RiskListMatch rule : rules) {
            MerchantLimitPeriod period = merchantLimitPeriod(rule.getHitElement(), evaluationTime);
            if (period == null || rule.getRuleId() == null || rule.getAmountLimit() == null) {
                rollbackAndCancel(
                        reservations,
                        requestDTO.getTransactionId(),
                        lifecycleManaged,
                        "merchant cumulative limit rule is invalid");
                details.add(cumulativeLimitUnavailable(rule, "merchant cumulative limit rule is invalid"));
                return new MerchantLimitEvaluation(details, List.of());
            }
            long limitUnits;
            long seedUnits;
            try {
                limitUnits = amountUnits(rule.getAmountLimit());
                seedUnits = seedPeriodAmount(requestDTO, currency, period, rule);
            } catch (RuntimeException exception) {
                rollbackAndCancel(
                        reservations,
                        requestDTO.getTransactionId(),
                        lifecycleManaged,
                        "merchant cumulative limit baseline failed");
                log.warn("商户累计限额基线读取失败, event: RISK_MERCHANT_LIMIT_BASELINE_FAILED, merchantId: {}, ruleId: {}, limitType: {}, exceptionType: {}",
                        merchantId, rule.getRuleId(), rule.getHitElement(), exception.getClass().getSimpleName());
                details.add(cumulativeLimitUnavailable(rule, "merchant cumulative limit baseline is unavailable"));
                return new MerchantLimitEvaluation(details, List.of());
            }
            if (limitUnits <= 0 || limitUnits > MAX_SAFE_LUA_INTEGER || seedUnits < 0
                    || seedUnits > MAX_SAFE_LUA_INTEGER - amountUnits) {
                rollbackAndCancel(
                        reservations,
                        requestDTO.getTransactionId(),
                        lifecycleManaged,
                        "merchant cumulative limit value is out of range");
                details.add(cumulativeLimitUnavailable(rule, "merchant cumulative limit value is out of range"));
                return new MerchantLimitEvaluation(details, List.of());
            }
            MerchantLimitReservation reservation = clusterSafeMerchantLimitReservation(
                    rule, merchantId, currency, period, requestDTO.getTransactionId());
            MerchantLimitReservationDO lifecycleReservation = null;
            if (lifecycleManaged) {
                try {
                    lifecycleReservation = reservationStateService.prepare(buildLifecycleReservation(
                            requestDTO,
                            riskRecordNo,
                            rule,
                            currency,
                            period,
                            amountUnits));
                } catch (RuntimeException exception) {
                    rollbackAndCancel(
                            reservations,
                            requestDTO.getTransactionId(),
                            true,
                            "reservation prepare failed");
                    log.error("商户累计限额预占意图创建失败, event: RISK_MERCHANT_LIMIT_PREPARE_FAILED, merchantId: {}, transactionId: {}, ruleId: {}, exceptionType: {}",
                            merchantId, requestDTO.getTransactionId(), rule.getRuleId(),
                            exception.getClass().getSimpleName());
                    details.add(cumulativeLimitUnavailable(rule, "merchant cumulative limit lifecycle is unavailable"));
                    return new MerchantLimitEvaluation(details, List.of());
                }
                MerchantLimitReservationStatus persistedStatus = reservationStatus(lifecycleReservation);
                if (persistedStatus == null || persistedStatus.isTerminal()) {
                    rollbackAndCancel(
                            reservations,
                            requestDTO.getTransactionId(),
                            true,
                            "terminal reservation cannot be reopened");
                    details.add(cumulativeLimitUnavailable(rule, "merchant cumulative limit lifecycle is terminal"));
                    return new MerchantLimitEvaluation(details, List.of());
                }
            }
            Optional<Long> decisionResult = executeMerchantLimitReserve(
                    reservation,
                    amountUnits,
                    period.ttlSeconds(),
                    limitUnits,
                    isPassAction(rule),
                    seedUnits
            );
            if (decisionResult.isEmpty()) {
                rollbackAndCancel(
                        reservations,
                        requestDTO.getTransactionId(),
                        lifecycleManaged,
                        "merchant cumulative limit counter is unavailable");
                details.add(cumulativeLimitUnavailable(rule, "merchant cumulative limit counter is unavailable"));
                return new MerchantLimitEvaluation(details, List.of());
            }
            long currentUnits = decisionResult.get();
            if (currentUnits < 0) {
                details.add(cumulativeLimitDetail(rule, Math.negateExact(currentUnits), limitUnits, true));
                limitBlocked = true;
                continue;
            }
            reservations.add(reservation);
            if (lifecycleReservation != null && !reservationStateService.markReserved(lifecycleReservation)) {
                rollbackAndCancel(
                        reservations,
                        requestDTO.getTransactionId(),
                        true,
                        "reservation state update failed");
                details.add(cumulativeLimitUnavailable(rule, "merchant cumulative limit lifecycle is unavailable"));
                return new MerchantLimitEvaluation(details, List.of());
            }
            details.add(cumulativeLimitDetail(rule, currentUnits, limitUnits, currentUnits > limitUnits));
        }
        if (limitBlocked) {
            rollbackAndCancel(
                    reservations,
                    requestDTO.getTransactionId(),
                    lifecycleManaged,
                    "merchant cumulative limit blocked");
            return new MerchantLimitEvaluation(details, List.of());
        }
        return new MerchantLimitEvaluation(
                details,
                reservations,
                requestDTO.getTransactionId(),
                riskRecordNo,
                lifecycleManaged && !reservations.isEmpty());
    }

    /**
     * 回滚评估结果中尚未确认的 Redis 累计限额预占，并取消持久化生命周期事实。
     *
     * @param evaluation 原累计限额评估结果；空值按幂等空操作处理
     */
    @Override
    @DS(DataSourceName.MASTER)
    public void rollbackMerchantLimitReservations(MerchantLimitEvaluation evaluation) {
        if (evaluation == null) {
            return;
        }
        rollbackAndCancel(
                evaluation.reservations(),
                evaluation.transactionId(),
                evaluation.lifecycleManaged(),
                "risk evaluation rolled back");
    }

    /**
     * 判断商户和币种是否存在有效的单笔或累计金额限制。
     *
     * @return 规则运行时关闭或无有效规则时返回 {@code false}
     */
    @Override
    @DS(DataSourceName.MASTER)
    public boolean hasActiveMerchantLimitRule(String merchantId, String currency) {
        if (!StringUtils.hasText(currency) || riskRuntimeMapper == null) {
            return false;
        }
        String normalizedCurrency = currency.trim().toUpperCase();
        Supplier<Boolean> legacyLoader =
                () -> riskRuntimeMapper.countActiveMerchantLimitRules(trim(merchantId), normalizedCurrency) > 0;
        if (ruleCacheMode() == RiskRuleCacheMode.LEGACY) {
            return ruleCacheRuntime.resolveBoolean(
                    legacyLoader,
                    "rule",
                    "merchant-limit",
                    "active",
                    safeMerchant(merchantId),
                    normalizedCurrency
            );
        }
        SnapshotRows snapshot = merchantLimitSnapshot(merchantId, normalizedCurrency);
        if (ruleCacheMode() == RiskRuleCacheMode.SHADOW) {
            boolean legacyResult = ruleCacheRuntime.resolveBoolean(
                    legacyLoader,
                    "rule",
                    "merchant-limit",
                    "active",
                    safeMerchant(merchantId),
                    normalizedCurrency
            );
            if (snapshot.available() && legacyResult != !snapshot.rows().isEmpty()) {
                log.warn("风控规则快照影子比对不一致, event: RISK_RULE_SNAPSHOT_SHADOW_MISMATCH, dataset: merchant-limit, comparison: ACTIVE_FLAG");
            }
            return legacyResult;
        }
        return snapshot.available() ? !snapshot.rows().isEmpty() : Boolean.TRUE.equals(legacyLoader.get());
    }

    /**
     * 执行全部频率规则并返回首条 HIT 或 ERROR，兼容旧的单结果调用方。
     *
     * @return 首条超限或计数器不可用明细；全部通过时返回空
     */
    @Override
    @DS(DataSourceName.MASTER)
    public Optional<RiskListMatch> findFrequencyRuleHit(String merchantId,
                                                        RiskPaymentEvaluateRequestDTO requestDTO,
                                                        RiskRuntimeLookupValue cardNoLookup,
                                                        RiskRuntimeLookupValue cardFingerprintLookup,
                                                        RiskRuntimeLookupValue ipLookup,
                                                        RiskRuntimeLookupValue emailLookup,
                                                        RiskRuntimeLookupValue phoneLookup,
                                                        RiskRuntimeLookupValue customerIdLookup,
                                                        RiskRuntimeLookupValue deviceFingerprintLookup) {
        return evaluateFrequencyRules(
                merchantId,
                requestDTO,
                cardNoLookup,
                cardFingerprintLookup,
                ipLookup,
                emailLookup,
                phoneLookup,
                customerIdLookup,
                deviceFingerprintLookup
        ).stream()
                .filter(detail -> "HIT".equalsIgnoreCase(detail.getMatchResult())
                        || "ERROR".equalsIgnoreCase(detail.getMatchResult()))
                .findFirst();
    }

    /**
     * 对每条启用频率规则执行幂等原子计数并保留 PASS、HIT 或 ERROR 审计明细。
     *
     * <p>计数器异常和输入维度缺失均返回 ERROR/REVIEW，不按无规则放行。</p>
     *
     * @return 与启用规则顺序一致的不可变执行明细
     */
    @Override
    @DS(DataSourceName.MASTER)
    public List<RiskListMatch> evaluateFrequencyRules(String merchantId,
                                                      RiskPaymentEvaluateRequestDTO requestDTO,
                                                      RiskRuntimeLookupValue cardNoLookup,
                                                      RiskRuntimeLookupValue cardFingerprintLookup,
                                                      RiskRuntimeLookupValue ipLookup,
                                                      RiskRuntimeLookupValue emailLookup,
                                                      RiskRuntimeLookupValue phoneLookup,
                                                      RiskRuntimeLookupValue customerIdLookup,
                                                      RiskRuntimeLookupValue deviceFingerprintLookup) {
        if (!StringUtils.hasText(merchantId) || riskRuntimeMapper == null) {
            return List.of();
        }
        List<RiskListMatch> rules = activeFrequencyRules(merchantId.trim());
        List<RiskListMatch> details = new ArrayList<>(rules.size());
        for (RiskListMatch rule : rules) {
            details.add(frequencyRuleRuntime.evaluate(rule, merchantId, requestDTO,
                    cardNoLookup, cardFingerprintLookup, ipLookup, emailLookup, phoneLookup,
                    customerIdLookup, deviceFingerprintLookup));
        }
        return details;
    }

    /**
     * 使用一次有序规则集合读取完成频控计数并返回启用状态。
     *
     * <p>计数、成功名额预占和失败关闭逻辑仍由原有运行时逐条执行，本方法只消除执行完成后
     * 为生成 PASS 检查点而产生的第二次规则集合读取。</p>
     *
     * @return 与规则顺序一致的执行明细及规则启用状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    public FrequencyRuleEvaluation evaluateFrequencyRulesWithStatus(
            String merchantId,
            RiskPaymentEvaluateRequestDTO requestDTO,
            RiskRuntimeLookupValue cardNoLookup,
            RiskRuntimeLookupValue cardFingerprintLookup,
            RiskRuntimeLookupValue ipLookup,
            RiskRuntimeLookupValue emailLookup,
            RiskRuntimeLookupValue phoneLookup,
            RiskRuntimeLookupValue customerIdLookup,
            RiskRuntimeLookupValue deviceFingerprintLookup) {
        if (!properties.isReadQueryConsolidationEnabled()) {
            return RiskListRuntimeRepository.super.evaluateFrequencyRulesWithStatus(
                    merchantId,
                    requestDTO,
                    cardNoLookup,
                    cardFingerprintLookup,
                    ipLookup,
                    emailLookup,
                    phoneLookup,
                    customerIdLookup,
                    deviceFingerprintLookup);
        }
        if (!StringUtils.hasText(merchantId) || riskRuntimeMapper == null) {
            return FrequencyRuleEvaluation.empty();
        }
        List<RiskListMatch> rules = activeFrequencyRules(merchantId.trim());
        List<RiskListMatch> details = new ArrayList<>(rules.size());
        for (RiskListMatch rule : rules) {
            details.add(frequencyRuleRuntime.evaluate(rule, merchantId, requestDTO,
                    cardNoLookup, cardFingerprintLookup, ipLookup, emailLookup, phoneLookup,
                    customerIdLookup, deviceFingerprintLookup));
        }
        return new FrequencyRuleEvaluation(details, !rules.isEmpty());
    }

    /**
     * 释放指定交易尚未确认的全部频控成功名额。
     *
     * @param merchantId 当前商户号
     * @param transactionId 支付系统交易号
     */
    @Override
    @DS(DataSourceName.MASTER)
    public void releaseFrequencySuccessReservations(String merchantId, String transactionId) {
        if (frequencySuccessReservationService == null
                || !StringUtils.hasText(merchantId)
                || !StringUtils.hasText(transactionId)) {
            return;
        }
        frequencySuccessReservationService.release(merchantId.trim(), transactionId.trim());
    }

    /**
     * 判断当前商户是否存在至少一条可执行频率规则。
     *
     * @param merchantId 当前商户号
     * @return 规则运行时开启且存在有效规则时返回 {@code true}
     */
    @Override
    @DS(DataSourceName.MASTER)
    public boolean hasActiveFrequencyRule(String merchantId) {
        if (!StringUtils.hasText(merchantId) || riskRuntimeMapper == null) {
            return false;
        }
        return !activeFrequencyRules(merchantId.trim()).isEmpty();
    }

    /**
     * 按规范化卡 BIN 数值解析发卡国家或地区，并按 generation 缓存查询结果。
     *
     * @param cardBinLookup 仅包含 BIN 展示片段和固定长度数值的查询值
     * @return ISO alpha-3 国家或地区代码；输入无效或未命中时返回空
     */
    @Override
    @DS(DataSourceName.MASTER)
    public Optional<String> findIssuerCountryByCardBin(RiskRuntimeLookupValue cardBinLookup) {
        if (cardBinLookup == null || cardBinLookup.getNumericValue() == null
                || riskRuntimeMapper == null) {
            return Optional.empty();
        }
        Supplier<Optional<RiskListMatch>> legacyLoader =
                () -> Optional.ofNullable(riskRuntimeMapper.selectIssuerCountryByCardBin(
                        cardBinLookup.getNumericValue()
                ));
        return ruleCacheRuntime.findMatch(
                legacyLoader,
                "card-bin",
                "issuer-country",
                RedisKeyDigest.sha256(cardBinLookup.getNumericValue().toPlainString())
        )
                .map(RiskListMatch::getHitValueMasked)
                .filter(StringUtils::hasText);
    }

    /**
     * 查询适用于交易方式、卡品牌、金额和当前风险等级的 3DS 动作规则。
     *
     * @return 最高优先级的强制或跳过 3DS 规则；无适用规则时返回空
     */
    @Override
    @DS(DataSourceName.MASTER)
    public Optional<RiskListMatch> findThreeDsRule(String merchantId,
                                                   String channelCode,
                                                   String paymentMethod,
                                                   String cardBrand,
                                                   BigDecimal amount,
                                                   String currency,
                                                   String currentRiskLevel) {
        if (!StringUtils.hasText(channelCode)
                || amount == null
                || !StringUtils.hasText(currency)
                || riskRuntimeMapper == null) {
            return Optional.empty();
        }
        String normalizedChannelCode = channelCode.trim().toUpperCase();
        String normalizedCurrency = currency.trim().toUpperCase();
        String normalizedPaymentMethod = defaultAll(paymentMethod);
        String normalizedCardBrand = defaultAll(cardBrand);
        int riskWeight = riskWeight(currentRiskLevel);
        Supplier<Optional<RiskListMatch>> legacyLoader =
                () -> Optional.ofNullable(riskRuntimeMapper.selectThreeDsRule(
                trim(merchantId),
                normalizedChannelCode,
                normalizedPaymentMethod,
                normalizedCardBrand,
                amount,
                normalizedCurrency,
                riskWeight
        ));
        if (ruleCacheMode() == RiskRuleCacheMode.LEGACY) {
            return ruleCacheRuntime.findMatch(
                    legacyLoader,
                    "rule",
                    "three-ds",
                    safeMerchant(merchantId),
                    normalizedChannelCode,
                    normalizedPaymentMethod,
                    normalizedCardBrand,
                    normalizedCurrency,
                    String.valueOf(riskWeight),
                    RedisKeyDigest.sha256(amount.stripTrailingZeros().toPlainString())
            );
        }
        SnapshotRows snapshot = threeDsSnapshot(merchantId);
        Optional<RiskListMatch> snapshotResult = snapshot.available()
                ? snapshot.rows().stream()
                .filter(row -> threeDsRuleMatches(
                        row,
                        normalizedChannelCode,
                        normalizedPaymentMethod,
                        normalizedCardBrand,
                        amount,
                        normalizedCurrency,
                        riskWeight
                ))
                .map(row -> (RiskListMatch) row)
                .findFirst()
                : Optional.empty();
        if (ruleCacheMode() == RiskRuleCacheMode.SHADOW) {
            Optional<RiskListMatch> legacyResult = ruleCacheRuntime.findMatch(
                    legacyLoader,
                    "rule",
                    "three-ds",
                    safeMerchant(merchantId),
                    normalizedChannelCode,
                    normalizedPaymentMethod,
                    normalizedCardBrand,
                    normalizedCurrency,
                    String.valueOf(riskWeight),
                    RedisKeyDigest.sha256(amount.stripTrailingZeros().toPlainString())
            );
            recordSnapshotShadowDifference("three-ds", legacyResult, snapshotResult, snapshot.available());
            return legacyResult;
        }
        return snapshot.available() ? snapshotResult : legacyLoader.get();
    }

    /**
     * 按名单功能声明的匹配类型路由到受控 Mapper 查询。
     *
     * @return 首条数据库命中；查询值不匹配任何启用规则时返回空
     */
    private Optional<RiskListMatch> loadListMatch(RiskListFunction function,
                                                  String merchantId,
                                                  RiskRuntimeLookupValue lookupValue) {
        return switch (function.getMatchKind()) {
            case HASH -> Optional.ofNullable(riskRuntimeMapper.selectHashMatch(
                    function.getTableName(),
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    function.getFunctionName(),
                    function.getFunctionCode(),
                    trim(merchantId),
                    lookupValue.getMatchValueHash()
            ));
            case IP_RANGE -> Optional.ofNullable(riskRuntimeMapper.selectIpRangeMatch(
                    function.getTableName(),
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    function.getFunctionName(),
                    function.getFunctionCode(),
                    trim(merchantId),
                    lookupValue.getIpVersion(),
                    lookupValue.getNumericValue()
            ));
            case CARD_BIN_RANGE -> Optional.ofNullable(riskRuntimeMapper.selectCardBinRangeMatch(
                    function.getTableName(),
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    function.getFunctionName(),
                    function.getFunctionCode(),
                    trim(merchantId),
                    lookupValue.getNumericValue()
            ));
            case COUNTRY -> Optional.ofNullable(riskRuntimeMapper.selectCountryMatch(
                    function.getTableName(),
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    function.getFunctionName(),
                    function.getFunctionCode(),
                    trim(merchantId),
                    lookupValue.getCountryAlpha3()
            ));
            case REGION -> Optional.ofNullable(riskRuntimeMapper.selectRegionMatch(
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    function.getFunctionName(),
                    function.getFunctionCode(),
                    trim(merchantId),
                    lookupValue.getCountryAlpha3(),
                    lookupValue.getStateProvinceName(),
                    lookupValue.getCityName()
            ));
            case SOURCE_HOST -> Optional.ofNullable(riskRuntimeMapper.selectAmlSourceHostMatch(
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    function.getFunctionName(),
                    function.getFunctionCode(),
                    lookupValue.getSourceHost()
            ));
        };
    }

    /**
     * 使用稳定短 Key 读取或重建指定名单功能的完整有效快照。
     *
     * <p>精确哈希名单使用 Redis Hash，其他匹配类型使用有界 JSON 快照。快照中的 generation
     * 必须等于发布链路当前代际，否则从主库重新加载。发布门禁存在、Redis 不可用或容量越界时
     * 返回 unavailable，由调用方执行既有主库点查。</p>
     *
     * @param function   名单功能
     * @param merchantId 当前商户号
     * @param lookupValue 已标准化查询值；只检查是否存在规则时允许为空
     * @return 快照可用状态、首条匹配以及集合是否非空
     */
    private SnapshotListMatch snapshotListMatch(RiskListFunction function,
                                                String merchantId,
                                                RiskRuntimeLookupValue lookupValue) {
        Optional<String> generation = currentSnapshotGeneration();
        if (generation.isEmpty() || stringRedisTemplate == null) {
            return SnapshotListMatch.unavailable();
        }
        String cacheKey = snapshotListKey(function, merchantId);
        if (function.getMatchKind() == RiskListFunction.MatchKind.HASH) {
            return hashSnapshotListMatch(
                    function,
                    merchantId,
                    lookupValue,
                    generation.get(),
                    cacheKey
            );
        }
        SnapshotRows snapshotRows = readOrLoadRowsSnapshot(
                cacheKey,
                generation.get(),
                () -> loadSnapshotRows(function, merchantId)
        );
        if (!snapshotRows.available()) {
            return SnapshotListMatch.unavailable();
        }
        Optional<RiskListMatch> match = lookupValue == null
                ? Optional.empty()
                : snapshotRows.rows().stream()
                .filter(row -> snapshotRowMatches(function, row, lookupValue))
                .map(row -> (RiskListMatch) row)
                .findFirst();
        return SnapshotListMatch.available(match, !snapshotRows.rows().isEmpty());
    }

    /**
     * 从精确名单 Hash 读取目标字段，或从主库加载完整集合后原子替换 Hash。
     *
     * @param function    精确哈希名单功能
     * @param merchantId  当前商户号
     * @param lookupValue 哈希查询值；仅检查存在性时允许为空
     * @param generation  当前可读代际
     * @param cacheKey    稳定短 Key
     * @return 可区分有效空集合与缓存不可用的查询结果
     */
    private SnapshotListMatch hashSnapshotListMatch(RiskListFunction function,
                                                    String merchantId,
                                                    RiskRuntimeLookupValue lookupValue,
                                                    String generation,
                                                    String cacheKey) {
        try {
            Object metadataValue = stringRedisTemplate.opsForHash().get(cacheKey, SNAPSHOT_META_FIELD);
            RiskRuleSnapshot metadata = parseSnapshotMetadata(metadataValue);
            if (snapshotMatchesGeneration(metadata, generation)) {
                Optional<RiskListMatch> match = lookupValue == null
                        ? Optional.empty()
                        : readHashSnapshotMatch(cacheKey, lookupValue.getMatchValueHash());
                return SnapshotListMatch.available(match, metadata.getCount() > 0);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "风控哈希快照读取失败, event: RISK_RULE_HASH_SNAPSHOT_READ_FAILED, keyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(cacheKey),
                    exception.getClass().getSimpleName()
            );
        }

        if (snapshotCapacityBypassActive(cacheKey, generation)) {
            return SnapshotListMatch.unavailable();
        }
        List<RiskRuleSnapshotRow> rows = boundedSnapshotRows(
                cacheKey,
                generation,
                () -> loadSnapshotRows(function, merchantId)
        );
        if (rows == null) {
            return SnapshotListMatch.unavailable();
        }
        RiskRuleSnapshot metadata = RiskRuleSnapshot.rows(generation, List.of());
        metadata.setCount(rows.size());
        String metadataJson = JsonUtils.toJsonString(metadata);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(SNAPSHOT_META_FIELD, metadataJson);
        int serializedCharacters = metadataJson.length();
        for (RiskRuleSnapshotRow row : rows) {
            if (!StringUtils.hasText(row.getMatchValueHash())) {
                continue;
            }
            String rowJson = JsonUtils.toJsonString(row);
            // SQL 已按商户级、更新时间和主键排序；同一哈希只保留最高优先级规则。
            if (!fields.containsKey(row.getMatchValueHash())) {
                fields.put(row.getMatchValueHash(), rowJson);
                serializedCharacters += row.getMatchValueHash().length() + rowJson.length();
            }
        }
        if (serializedCharacters > properties.getRuleSnapshotMaxCharacters()) {
            log.warn(
                    "风控规则快照超过容量上限, event: RISK_RULE_SNAPSHOT_CAPACITY_EXCEEDED, dataset: {}, dimension: CHARACTERS, count: {}",
                    function.name(),
                    serializedCharacters
            );
            markSnapshotCapacityBypass(cacheKey, generation);
            return SnapshotListMatch.unavailable();
        }
        replaceHashSnapshot(cacheKey, fields);
        Optional<RiskListMatch> match = lookupValue == null
                ? Optional.empty()
                : rows.stream()
                .filter(row -> Objects.equals(row.getMatchValueHash(), lookupValue.getMatchValueHash()))
                .map(row -> (RiskListMatch) row)
                .findFirst();
        return SnapshotListMatch.available(match, !rows.isEmpty());
    }

    /**
     * 读取 Hash 中单个精确匹配字段。
     *
     * @param cacheKey      精确名单 Hash Key
     * @param matchValueHash 已规范化敏感值的不可逆哈希
     * @return 目标规则；字段不存在或内容无效时返回空
     */
    private Optional<RiskListMatch> readHashSnapshotMatch(String cacheKey, String matchValueHash) {
        if (!StringUtils.hasText(matchValueHash)) {
            return Optional.empty();
        }
        Object cached = stringRedisTemplate.opsForHash().get(cacheKey, matchValueHash);
        if (!(cached instanceof String text) || !StringUtils.hasText(text)) {
            return Optional.empty();
        }
        return Optional.ofNullable(JsonUtils.parseObject(text, RiskRuleSnapshotRow.class))
                .map(row -> (RiskListMatch) row);
    }

    /**
     * 使用单 Key Lua 原子替换精确名单 Hash，写入后不设置物理 TTL。
     *
     * @param cacheKey 稳定短 Key
     * @param fields   @meta 与哈希规则字段
     */
    private void replaceHashSnapshot(String cacheKey, Map<String, String> fields) {
        List<String> arguments = new ArrayList<>(fields.size() * 2);
        fields.forEach((field, value) -> {
            arguments.add(field);
            arguments.add(value);
        });
        try {
            stringRedisTemplate.execute(
                    RISK_HASH_SNAPSHOT_REPLACE_SCRIPT,
                    List.of(cacheKey),
                    arguments.toArray(Object[]::new)
            );
        } catch (RuntimeException exception) {
            // 当前请求继续使用刚从主库加载的集合；写失败不会改变真实规则判断。
            log.warn(
                    "风控哈希快照写入失败, event: RISK_RULE_HASH_SNAPSHOT_WRITE_FAILED, keyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(cacheKey),
                    exception.getClass().getSimpleName()
            );
        }
    }

    /**
     * 读取或重建序列化规则行快照。
     *
     * @param cacheKey  稳定短 Key
     * @param generation 当前规则代际
     * @param loader    主库完整集合加载器
     * @return 快照行；容量越界或基础设施不可用时返回 unavailable
     */
    private SnapshotRows readOrLoadRowsSnapshot(String cacheKey,
                                                String generation,
                                                Supplier<List<RiskRuleSnapshotRow>> loader) {
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cachedJson)) {
                RiskRuleSnapshot cached = JsonUtils.parseObject(cachedJson, RiskRuleSnapshot.class);
                if (snapshotMatchesGeneration(cached, generation)) {
                    return SnapshotRows.available(immutableSnapshotRows(cached.getRows()));
                }
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "风控规则快照读取失败, event: RISK_RULE_SNAPSHOT_READ_FAILED, keyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(cacheKey),
                    exception.getClass().getSimpleName()
            );
        }

        if (snapshotCapacityBypassActive(cacheKey, generation)) {
            return SnapshotRows.unavailable();
        }
        List<RiskRuleSnapshotRow> rows = boundedSnapshotRows(cacheKey, generation, loader);
        if (rows == null) {
            return SnapshotRows.unavailable();
        }
        RiskRuleSnapshot snapshot = RiskRuleSnapshot.rows(generation, rows);
        String snapshotJson = JsonUtils.toJsonString(snapshot);
        if (snapshotJson.length() > properties.getRuleSnapshotMaxCharacters()) {
            log.warn(
                    "风控规则快照超过容量上限, event: RISK_RULE_SNAPSHOT_CAPACITY_EXCEEDED, keyDigest: {}, dimension: CHARACTERS, count: {}",
                    RedisKeyDigest.sha256(cacheKey),
                    snapshotJson.length()
            );
            markSnapshotCapacityBypass(cacheKey, generation);
            return SnapshotRows.unavailable();
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, snapshotJson);
        } catch (RuntimeException exception) {
            log.warn(
                    "风控规则快照写入失败, event: RISK_RULE_SNAPSHOT_WRITE_FAILED, keyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(cacheKey),
                    exception.getClass().getSimpleName()
            );
        }
        return SnapshotRows.available(rows);
    }

    /**
     * 加载指定名单功能的有界完整集合。
     *
     * @param function   名单功能
     * @param merchantId 当前商户号
     * @return 按数据库优先级排序的有效规则，最多为配置上限加一条
     */
    private List<RiskRuleSnapshotRow> loadSnapshotRows(RiskListFunction function, String merchantId) {
        int queryLimit = snapshotQueryLimit();
        String normalizedMerchantId = trim(merchantId);
        return switch (function.getMatchKind()) {
            case HASH -> riskRuntimeMapper.selectActiveHashSnapshotRows(
                    function.getTableName(),
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    function.getFunctionName(),
                    function.getFunctionCode(),
                    normalizedMerchantId,
                    queryLimit
            );
            case IP_RANGE -> riskRuntimeMapper.selectActiveIpRangeSnapshotRows(
                    function.getTableName(),
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    function.getFunctionName(),
                    function.getFunctionCode(),
                    normalizedMerchantId,
                    queryLimit
            );
            case CARD_BIN_RANGE -> riskRuntimeMapper.selectActiveCardBinSnapshotRows(
                    function.getTableName(),
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    function.getFunctionName(),
                    function.getFunctionCode(),
                    normalizedMerchantId,
                    queryLimit
            );
            case COUNTRY -> riskRuntimeMapper.selectActiveCountrySnapshotRows(
                    function.getTableName(),
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    function.getFunctionName(),
                    function.getFunctionCode(),
                    normalizedMerchantId,
                    queryLimit
            );
            case REGION -> riskRuntimeMapper.selectActiveRegionSnapshotRows(
                    function.getModuleType().getCode(),
                    function.getFunctionCode(),
                    normalizedMerchantId,
                    queryLimit
            );
            case SOURCE_HOST -> riskRuntimeMapper.selectActiveAmlSourceHostSnapshotRows(queryLimit);
        };
    }

    /**
     * 判断一条快照行是否命中已标准化查询值。
     *
     * @param function    名单功能
     * @param row         快照行
     * @param lookupValue 已标准化查询值
     * @return 命中时为 true
     */
    private boolean snapshotRowMatches(RiskListFunction function,
                                       RiskRuleSnapshotRow row,
                                       RiskRuntimeLookupValue lookupValue) {
        return switch (function.getMatchKind()) {
            case HASH -> Objects.equals(row.getMatchValueHash(), lookupValue.getMatchValueHash());
            case IP_RANGE -> equalsIgnoreCase(row.getIpVersion(), lookupValue.getIpVersion())
                    && withinRange(
                    lookupValue.getNumericValue(),
                    row.getMatchValueStartNumber(),
                    row.getMatchValueEndNumber()
            );
            case CARD_BIN_RANGE -> withinRange(
                    lookupValue.getNumericValue(),
                    row.getMatchValueStartNumber(),
                    row.getMatchValueEndNumber()
            );
            case COUNTRY -> equalsIgnoreCase(row.getCountryAlpha3(), lookupValue.getCountryAlpha3());
            case REGION -> regionMatches(row, lookupValue);
            case SOURCE_HOST -> equalsIgnoreCase(row.getSourceHost(), lookupValue.getSourceHost());
        };
    }

    /**
     * 按 COUNTRY、STATE、CITY 层级匹配地域规则。
     *
     * @param row         地域快照行
     * @param lookupValue 当前交易地域
     * @return 当前层级全部必需字段相等时为 true
     */
    private boolean regionMatches(RiskRuleSnapshotRow row, RiskRuntimeLookupValue lookupValue) {
        if (!equalsIgnoreCase(row.getCountryAlpha3(), lookupValue.getCountryAlpha3())) {
            return false;
        }
        String level = StringUtils.hasText(row.getRegionMatchLevel())
                ? row.getRegionMatchLevel().trim().toUpperCase(Locale.ROOT)
                : "COUNTRY";
        return switch (level) {
            case "CITY" -> equalsIgnoreCase(row.getStateProvinceName(), lookupValue.getStateProvinceName())
                    && equalsIgnoreCase(row.getCityName(), lookupValue.getCityName());
            case "STATE" -> equalsIgnoreCase(row.getStateProvinceName(), lookupValue.getStateProvinceName());
            default -> true;
        };
    }

    /**
     * 判断数值位于闭区间内。
     *
     * @param value 当前查询值
     * @param start 闭区间起点
     * @param end   闭区间终点
     * @return 三个值均非空且 start <= value <= end 时为 true
     */
    private boolean withinRange(BigDecimal value, BigDecimal start, BigDecimal end) {
        return value != null && start != null && end != null
                && value.compareTo(start) >= 0
                && value.compareTo(end) <= 0;
    }

    /**
     * 不区分大小写比较两个非空运行时编码或规范文本。
     *
     * @param left  左值
     * @param right 右值
     * @return 两值非空且忽略大小写相等时为 true
     */
    private boolean equalsIgnoreCase(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.trim().equalsIgnoreCase(right.trim());
    }

    /**
     * 构造名单快照短 Key。
     *
     * @param function   名单功能
     * @param merchantId 当前商户号
     * @return acquiring:{env}:risk:{white|black|aml}:{function}:{merchant}
     */
    private String snapshotListKey(RiskListFunction function, String merchantId) {
        return redisProperties.businessKey(
                "risk",
                function.getModuleType().getCode().toLowerCase(Locale.ROOT),
                function.getFunctionCode(),
                safeMerchant(merchantId)
        );
    }

    /**
     * 读取当前可用规则代际；发布中或 Redis 异常时禁止读取旧快照。
     *
     * @return 当前 generation；不可安全使用缓存时返回空
     */
    private Optional<String> currentSnapshotGeneration() {
        if (cacheGenerationStore == null) {
            return Optional.empty();
        }
        try {
            RedisCacheGenerationState state = cacheGenerationStore.current(RULE_CACHE_NAMESPACE);
            if (!state.cacheReadable() || !StringUtils.hasText(state.generation())) {
                return Optional.empty();
            }
            return Optional.of(state.generation());
        } catch (RuntimeException exception) {
            log.warn("风控规则快照代际读取失败, event: RISK_RULE_SNAPSHOT_GENERATION_FAILED, exceptionType: {}",
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 校验快照已完成加载且代际与当前发布状态一致。
     *
     * @param snapshot   缓存快照或 Hash 元数据
     * @param generation 当前 generation
     * @return 快照可参与本次判断时为 true
     */
    private boolean snapshotMatchesGeneration(RiskRuleSnapshot snapshot, String generation) {
        return snapshot != null
                && snapshot.isLoaded()
                && Objects.equals(snapshot.getGeneration(), generation)
                && snapshot.getCount() >= 0
                && snapshot.getCount() <= properties.getRuleSnapshotMaxRows();
    }

    /**
     * 解析精确名单 Hash 的 @meta 字段。
     *
     * @param metadataValue Redis Hash 字段值
     * @return 元数据；缺失或类型不符时返回 null
     */
    private RiskRuleSnapshot parseSnapshotMetadata(Object metadataValue) {
        if (metadataValue instanceof String text && StringUtils.hasText(text)) {
            return JsonUtils.parseObject(text, RiskRuleSnapshot.class);
        }
        return null;
    }

    /**
     * 执行配置上限加一查询并识别大集合。
     *
     * @param cacheKey 规则快照 Key
     * @param generation 当前规则代际
     * @param loader 有界主库加载器
     * @return 不超过上限的不可变列表；越界时返回 null
     */
    private List<RiskRuleSnapshotRow> boundedSnapshotRows(String cacheKey,
                                                          String generation,
                                                          Supplier<List<RiskRuleSnapshotRow>> loader) {
        List<RiskRuleSnapshotRow> loaded = loader.get();
        List<RiskRuleSnapshotRow> safeRows = immutableSnapshotRows(loaded);
        if (safeRows.size() > properties.getRuleSnapshotMaxRows()) {
            log.warn("风控规则快照超过容量上限, event: RISK_RULE_SNAPSHOT_CAPACITY_EXCEEDED, keyDigest: {}, dimension: ROWS, count: {}",
                    RedisKeyDigest.sha256(cacheKey),
                    safeRows.size());
            markSnapshotCapacityBypass(cacheKey, generation);
            return null;
        }
        return safeRows;
    }

    /**
     * 判断当前 generation 的数据集是否已确认不适合构建完整 Redis 快照。
     *
     * <p>旁路只省略有界全量加载，调用方仍执行现有数据库精确查询。Redis 异常时返回 false，
     * 让当前请求重新探测容量，避免把缓存故障解释为规则未命中。</p>
     *
     * @param cacheKey 规则快照 Key
     * @param generation 当前规则代际
     * @return 同 generation 的短 TTL 旁路标记存在时为 true
     */
    private boolean snapshotCapacityBypassActive(String cacheKey, String generation) {
        String bypassKey = snapshotCapacityBypassKey(cacheKey);
        try {
            return Objects.equals(generation, stringRedisTemplate.opsForValue().get(bypassKey));
        } catch (RuntimeException exception) {
            log.warn(
                    "风控快照容量旁路标记读取失败, event: RISK_RULE_SNAPSHOT_CAPACITY_BYPASS_READ_FAILED, keyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(bypassKey),
                    exception.getClass().getSimpleName()
            );
            return false;
        }
    }

    /**
     * 为超容量数据集写入与 generation 绑定的短 TTL 旁路标记。
     *
     * @param cacheKey 规则快照 Key
     * @param generation 当前规则代际
     */
    private void markSnapshotCapacityBypass(String cacheKey, String generation) {
        String bypassKey = snapshotCapacityBypassKey(cacheKey);
        Duration ttl = Duration.ofSeconds(Math.max(1L, properties.getRuleSnapshotCapacityBypassTtlSeconds()));
        try {
            stringRedisTemplate.opsForValue().set(bypassKey, generation, ttl);
        } catch (RuntimeException exception) {
            log.warn(
                    "风控快照容量旁路标记写入失败, event: RISK_RULE_SNAPSHOT_CAPACITY_BYPASS_WRITE_FAILED, keyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(bypassKey),
                    exception.getClass().getSimpleName()
            );
        }
    }

    /**
     * 构造独立于正式规则快照的容量旁路 Key。
     *
     * @param cacheKey 规则快照 Key
     * @return 仅保存 generation 的短 TTL Key
     */
    private String snapshotCapacityBypassKey(String cacheKey) {
        return cacheKey + SNAPSHOT_CAPACITY_BYPASS_SUFFIX;
    }

    /**
     * 返回不会被调用方修改的快照行集合。
     *
     * @param rows 数据库或 Redis 返回的集合
     * @return 不可变非空或空列表
     */
    private List<RiskRuleSnapshotRow> immutableSnapshotRows(List<RiskRuleSnapshotRow> rows) {
        return rows == null || rows.isEmpty() ? List.of() : List.copyOf(rows);
    }

    /**
     * 返回数据库查询上限，使用配置值加一识别是否真实越界。
     *
     * @return 正数查询上限
     */
    private int snapshotQueryLimit() {
        return Math.addExact(properties.getRuleSnapshotMaxRows(), 1);
    }

    /**
     * 读取商户来源网址允许规则快照。
     *
     * @param merchantId 已规范化商户号
     * @return acquiring:{env}:risk:source:{merchantId} 对应的完整集合
     */
    private SnapshotRows sourceUrlSnapshot(String merchantId) {
        return datasetRowsSnapshot(
                redisProperties.businessKey("risk", "source", safeMerchant(merchantId)),
                () -> riskRuntimeMapper.selectActiveSourceUrlSnapshotRows(
                        trim(merchantId),
                        snapshotQueryLimit()
                )
        );
    }

    /**
     * 读取商户和币种维度的全部限额规则快照。
     *
     * @param merchantId 当前商户号
     * @param currency   ISO 4217 Alpha-3 币种
     * @return acquiring:{env}:risk:limit:{merchantId}:{currency} 对应的完整集合
     */
    private SnapshotRows merchantLimitSnapshot(String merchantId, String currency) {
        return datasetRowsSnapshot(
                redisProperties.businessKey(
                        "risk",
                        "limit",
                        safeMerchant(merchantId),
                        normalizedUpper(currency)
                ),
                () -> riskRuntimeMapper.selectActiveMerchantLimitSnapshotRows(
                        trim(merchantId),
                        normalizedUpper(currency),
                        snapshotQueryLimit()
                )
        );
    }

    /**
     * 读取商户维度的全部 3DS 规则快照。
     *
     * @param merchantId 当前商户号
     * @return acquiring:{env}:risk:3ds:{merchantId} 对应的完整集合
     */
    private SnapshotRows threeDsSnapshot(String merchantId) {
        return datasetRowsSnapshot(
                redisProperties.businessKey("risk", "3ds", safeMerchant(merchantId)),
                () -> riskRuntimeMapper.selectActiveThreeDsSnapshotRows(
                        trim(merchantId),
                        snapshotQueryLimit()
                )
        );
    }

    /**
     * 使用当前 generation 读取或重建指定条件规则数据集。
     *
     * @param cacheKey 稳定短 Key
     * @param loader   主库有界完整集合加载器
     * @return 可区分有效空集合和缓存不可用的快照
     */
    private SnapshotRows datasetRowsSnapshot(String cacheKey,
                                             Supplier<List<RiskRuleSnapshotRow>> loader) {
        Optional<String> generation = currentSnapshotGeneration();
        if (generation.isEmpty() || stringRedisTemplate == null) {
            return SnapshotRows.unavailable();
        }
        return readOrLoadRowsSnapshot(cacheKey, generation.get(), loader);
    }

    /**
     * 判断交易金额是否违反单笔最低或最高限额。
     *
     * @param row    限额快照行
     * @param amount 交易金额
     * @return SINGLE_MIN 下低于下限，或 SINGLE_MAX 下高于上限时为 true
     */
    private boolean singleMerchantLimitMatches(RiskRuleSnapshotRow row, BigDecimal amount) {
        String limitType = normalizedUpper(row.getLimitType());
        if ("SINGLE_MIN".equals(limitType)) {
            return row.getAmountMin() != null && amount.compareTo(row.getAmountMin()) < 0;
        }
        if ("SINGLE_MAX".equals(limitType)) {
            return row.getAmountMax() != null && amount.compareTo(row.getAmountMax()) > 0;
        }
        return false;
    }

    /**
     * 判断一条 3DS 快照规则是否适用于当前交易。
     *
     * @param row           3DS 快照行
     * @param channelCode   规范化路由渠道编码
     * @param paymentMethod 规范化支付方式
     * @param cardBrand     规范化卡品牌
     * @param amount        交易金额
     * @param currency      ISO 4217 Alpha-3 币种
     * @param riskWeight    当前风险权重 1~4
     * @return 所有维度均匹配时为 true
     */
    private boolean threeDsRuleMatches(RiskRuleSnapshotRow row,
                                       String channelCode,
                                       String paymentMethod,
                                       String cardBrand,
                                       BigDecimal amount,
                                       String currency,
                                       int riskWeight) {
        boolean channelMatches = "ALL".equals(normalizedUpper(row.getChannelCode()))
                || equalsIgnoreCase(row.getChannelCode(), channelCode);
        boolean paymentMethodMatches = "ALL".equals(normalizedUpper(row.getPaymentMethod()))
                || equalsIgnoreCase(row.getPaymentMethod(), paymentMethod);
        boolean cardBrandMatches = "ALL".equals(normalizedUpper(row.getCardBrand()))
                || equalsIgnoreCase(row.getCardBrand(), cardBrand);
        return channelMatches
                && paymentMethodMatches
                && cardBrandMatches
                && equalsIgnoreCase(row.getCurrency(), currency)
                && amountConditionMatches(row, amount)
                && riskWeight >= riskConditionMinimumWeight(row.getRiskCondition());
    }

    /**
     * 判断交易金额是否满足 3DS 规则的 ALL、GE、LE 或 BETWEEN 条件。
     *
     * @param row    3DS 快照行
     * @param amount 交易金额
     * @return 金额条件满足时为 true
     */
    private boolean amountConditionMatches(RiskRuleSnapshotRow row, BigDecimal amount) {
        return switch (normalizedUpper(row.getAmountMatchType())) {
            case "GE" -> row.getAmountMin() != null && amount.compareTo(row.getAmountMin()) >= 0;
            case "LE" -> row.getAmountMax() != null && amount.compareTo(row.getAmountMax()) <= 0;
            case "BETWEEN" -> row.getAmountMin() != null
                    && row.getAmountMax() != null
                    && amount.compareTo(row.getAmountMin()) >= 0
                    && amount.compareTo(row.getAmountMax()) <= 0;
            default -> true;
        };
    }

    /**
     * 把 3DS 风险条件转换为最低风险权重。
     *
     * @param riskCondition ANY、LOW_AND_ABOVE、MEDIUM_AND_ABOVE、HIGH_AND_ABOVE 或 CRITICAL_ONLY
     * @return 最低权重；未知值保守设为不可匹配的 5
     */
    private int riskConditionMinimumWeight(String riskCondition) {
        return switch (normalizedUpper(riskCondition)) {
            case "ANY", "LOW_AND_ABOVE" -> 1;
            case "MEDIUM_AND_ABOVE" -> 2;
            case "HIGH_AND_ABOVE" -> 3;
            case "CRITICAL_ONLY" -> 4;
            default -> 5;
        };
    }

    /**
     * 规范规则枚举或币种为大写文本。
     *
     * @param value 原始配置值
     * @return 去除首尾空格后的大写值；空值返回空字符串
     */
    private String normalizedUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    /**
     * 读取或重建可直接执行的规则集合快照。
     *
     * @param cacheKey 稳定短 Key
     * @param loader   主库有界完整集合加载器
     * @return 有效空集合、完整规则或 unavailable
     */
    private SnapshotMatches readOrLoadMatchesSnapshot(String cacheKey,
                                                      Supplier<List<RiskListMatch>> loader) {
        Optional<String> generation = currentSnapshotGeneration();
        if (generation.isEmpty() || stringRedisTemplate == null) {
            return SnapshotMatches.unavailable();
        }
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cachedJson)) {
                RiskRuleSnapshot cached = JsonUtils.parseObject(cachedJson, RiskRuleSnapshot.class);
                if (snapshotMatchesGeneration(cached, generation.get())
                        && cached.getCount() == immutableList(cached.getMatches()).size()) {
                    return SnapshotMatches.available(cached.getMatches());
                }
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "风控规则快照读取失败, event: RISK_RULE_SNAPSHOT_READ_FAILED, keyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(cacheKey),
                    exception.getClass().getSimpleName()
            );
        }
        if (snapshotCapacityBypassActive(cacheKey, generation.get())) {
            return SnapshotMatches.unavailable();
        }
        List<RiskListMatch> matches = immutableList(loader.get());
        if (matches.size() > properties.getRuleSnapshotMaxRows()) {
            log.warn("风控规则快照超过容量上限, event: RISK_RULE_SNAPSHOT_CAPACITY_EXCEEDED, keyDigest: {}, dimension: ROWS, count: {}",
                    RedisKeyDigest.sha256(cacheKey),
                    matches.size());
            markSnapshotCapacityBypass(cacheKey, generation.get());
            return SnapshotMatches.unavailable();
        }
        RiskRuleSnapshot snapshot = RiskRuleSnapshot.matches(generation.get(), matches);
        String snapshotJson = JsonUtils.toJsonString(snapshot);
        if (snapshotJson.length() > properties.getRuleSnapshotMaxCharacters()) {
            log.warn(
                    "风控规则快照超过容量上限, event: RISK_RULE_SNAPSHOT_CAPACITY_EXCEEDED, keyDigest: {}, dimension: CHARACTERS, count: {}",
                    RedisKeyDigest.sha256(cacheKey),
                    snapshotJson.length()
            );
            markSnapshotCapacityBypass(cacheKey, generation.get());
            return SnapshotMatches.unavailable();
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, snapshotJson);
        } catch (RuntimeException exception) {
            log.warn(
                    "风控规则快照写入失败, event: RISK_RULE_SNAPSHOT_WRITE_FAILED, keyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(cacheKey),
                    exception.getClass().getSimpleName()
            );
        }
        return SnapshotMatches.available(matches);
    }

    /**
     * 比较 shadow 模式中新旧规则集合的有序规则 ID。
     *
     * @param dataset 数据集标识
     * @param legacyRules 旧路径规则
     * @param snapshot 快照路径结果
     */
    private void recordRuleListShadowDifference(String dataset,
                                                List<RiskListMatch> legacyRules,
                                                SnapshotMatches snapshot) {
        if (!snapshot.available()) {
            return;
        }
        List<Long> legacyIds = immutableList(legacyRules).stream().map(RiskListMatch::getRuleId).toList();
        List<Long> snapshotIds = snapshot.matches().stream().map(RiskListMatch::getRuleId).toList();
        if (!legacyIds.equals(snapshotIds)) {
            log.warn("风控规则快照影子比对不一致, event: RISK_RULE_SNAPSHOT_SHADOW_MISMATCH, dataset: {}, comparison: RULE_IDS", dataset);
        }
    }

    /**
     * 比较 shadow 模式中新旧名单命中，不输出规则值或业务 Key。
     *
     * @param dataset          数据集标识
     * @param legacyResult     旧路径结果
     * @param snapshotResult   快照路径结果
     * @param snapshotAvailable 快照是否可比较
     */
    private void recordSnapshotShadowDifference(String dataset,
                                                Optional<RiskListMatch> legacyResult,
                                                Optional<RiskListMatch> snapshotResult,
                                                boolean snapshotAvailable) {
        if (!snapshotAvailable) {
            return;
        }
        Long legacyRuleId = legacyResult.map(RiskListMatch::getRuleId).orElse(null);
        Long snapshotRuleId = snapshotResult.map(RiskListMatch::getRuleId).orElse(null);
        if (!Objects.equals(legacyRuleId, snapshotRuleId)) {
            log.warn("风控规则快照影子比对不一致, event: RISK_RULE_SNAPSHOT_SHADOW_MISMATCH, dataset: {}, comparison: RULE_ID", dataset);
        }
    }

    /**
     * 读取并按当前 generation 缓存商户启用的频率规则集合。
     *
     * @param merchantId 当前商户号
     * @return 不可变规则集合
     */
    private List<RiskListMatch> activeFrequencyRules(String merchantId) {
        Supplier<List<RiskListMatch>> loader =
                () -> riskRuntimeMapper.selectActiveFrequencyRules(trim(merchantId));
        if (ruleCacheMode() == RiskRuleCacheMode.LEGACY) {
            return ruleCacheRuntime.findMatches(
                    loader,
                    "rule",
                    "frequency",
                    "active",
                    safeMerchant(merchantId)
            );
        }
        SnapshotMatches snapshot = readOrLoadMatchesSnapshot(
                redisProperties.businessKey("risk", "frequency", safeMerchant(merchantId)),
                () -> riskRuntimeMapper.selectActiveFrequencyRuleSnapshot(
                        trim(merchantId),
                        snapshotQueryLimit()
                )
        );
        if (ruleCacheMode() == RiskRuleCacheMode.SHADOW) {
            List<RiskListMatch> legacyRules = ruleCacheRuntime.findMatches(
                    loader,
                    "rule",
                    "frequency",
                    "active",
                    safeMerchant(merchantId)
            );
            recordRuleListShadowDifference("frequency", legacyRules, snapshot);
            return legacyRules;
        }
        return snapshot.available() ? snapshot.matches() : immutableList(loader.get());
    }

    /**
     * 读取并缓存商户、币种维度的日、周、月累计限额规则。
     *
     * @return 不可变规则集合
     */
    private List<RiskListMatch> activeCumulativeMerchantLimitRules(String merchantId, String currency) {
        if (ruleCacheMode() == RiskRuleCacheMode.LEGACY) {
            return ruleCacheRuntime.findMatches(
                    () -> riskRuntimeMapper.selectActiveCumulativeMerchantLimitRules(trim(merchantId), currency),
                    "rule",
                    "merchant-limit",
                    "cumulative",
                    "active",
                    safeMerchant(merchantId),
                    currency
            );
        }
        SnapshotRows snapshot = merchantLimitSnapshot(merchantId, currency);
        List<RiskListMatch> snapshotRules = snapshot.available()
                ? snapshot.rows().stream()
                .filter(row -> Set.of(LIMIT_TYPE_DAILY, LIMIT_TYPE_WEEKLY, LIMIT_TYPE_MONTHLY)
                        .contains(normalizedUpper(row.getLimitType())))
                .filter(row -> row.getAmountMax() != null && row.getAmountMax().signum() > 0)
                .map(row -> {
                    row.setAmountLimit(row.getAmountMax());
                    return (RiskListMatch) row;
                })
                .sorted(Comparator.<RiskListMatch>comparingInt(
                                match -> merchantScopeOrder((RiskRuleSnapshotRow) match))
                        .thenComparingInt(match -> merchantLimitPeriodOrder(match.getHitElement()))
                        .thenComparing(
                                RiskListMatch::getAmountLimit,
                                Comparator.nullsLast(BigDecimal::compareTo)
                        ))
                .toList()
                : List.of();
        if (ruleCacheMode() == RiskRuleCacheMode.SHADOW) {
            List<RiskListMatch> legacyRules = ruleCacheRuntime.findMatches(
                    () -> riskRuntimeMapper.selectActiveCumulativeMerchantLimitRules(trim(merchantId), currency),
                    "rule",
                    "merchant-limit",
                    "cumulative",
                    "active",
                    safeMerchant(merchantId),
                    currency
            );
            recordRuleListShadowDifference(
                    "merchant-limit-cumulative",
                    legacyRules,
                    new SnapshotMatches(snapshot.available(), snapshotRules)
            );
            return legacyRules;
        }
        return snapshot.available()
                ? List.copyOf(snapshotRules)
                : immutableList(riskRuntimeMapper.selectActiveCumulativeMerchantLimitRules(trim(merchantId), currency));
    }

    /**
     * 将商户级规则排在全局规则之前。
     *
     * @param row 规则快照行
     * @return MERCHANT 为 0，其他范围为 1
     */
    private int merchantScopeOrder(RiskRuleSnapshotRow row) {
        return "MERCHANT".equals(normalizedUpper(row.getMerchantScope())) ? 0 : 1;
    }

    /**
     * 将累计限额周期按日、周、月顺序排列。
     *
     * @param limitType 限额类型
     * @return DAILY=0、WEEKLY=1、MONTHLY=2，其他=3
     */
    private int merchantLimitPeriodOrder(String limitType) {
        return switch (normalizedUpper(limitType)) {
            case LIMIT_TYPE_DAILY -> 0;
            case LIMIT_TYPE_WEEKLY -> 1;
            case LIMIT_TYPE_MONTHLY -> 2;
            default -> 3;
        };
    }

    /**
     * 复制数据库规则为本次评估的独立审计明细，避免修改缓存对象。
     *
     * @param source 缓存或 Mapper 返回的规则
     * @return 可安全补充计数和阶段信息的新对象
     */
    private RiskListMatch copyRule(RiskListMatch source) {
        RiskListMatch target = new RiskListMatch();
        target.setRuleId(source.getRuleId());
        target.setModuleType(source.getModuleType());
        target.setFunctionCode(source.getFunctionCode());
        target.setFunctionName(source.getFunctionName());
        target.setHitElement(source.getHitElement());
        target.setHitValueMasked(source.getHitValueMasked());
        target.setRiskLevel(source.getRiskLevel());
        target.setDecisionAction(source.getDecisionAction());
        target.setDecisionReason(source.getDecisionReason());
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
     * 计算累计限额 Redis 聚合值的数据库基线。
     *
     * <p>LEGACY 使用交易事实，SHADOW 比较两类事实但仍返回旧基线，
     * LIFECYCLE 使用持久化预占事实。结果统一为六位小数定标整数。</p>
     *
     * @return 不含当前交易的周期累计整数金额单位
     */
    private long seedPeriodAmount(RiskPaymentEvaluateRequestDTO requestDTO,
                                  String currency,
                                  MerchantLimitPeriod period,
                                  RiskListMatch rule) {
        BigDecimal periodAmount = loadTransactionPeriodAmount(requestDTO, currency, period);
        long legacyUnits = amountUnits(periodAmount == null ? BigDecimal.ZERO : periodAmount);
        RiskBaselineMode baselineMode = properties.getBaselineMode();
        if (baselineMode == null || baselineMode == RiskBaselineMode.LEGACY) {
            return legacyUnits;
        }
        Long lifecycleUnits = riskRuntimeMapper.sumLifecycleReservationAmountUnits(
                requestDTO.getMerchantId().trim(),
                rule.getRuleId(),
                currency,
                period.bucket(),
                requestDTO.getTransactionId().trim());
        long normalizedLifecycleUnits = lifecycleUnits == null ? 0L : lifecycleUnits;
        if (baselineMode == RiskBaselineMode.SHADOW) {
            recordBaselineShadow(legacyUnits, normalizedLifecycleUnits);
            if (legacyUnits != normalizedLifecycleUnits) {
                log.info("商户累计限额基线影子比对不一致, event: RISK_MERCHANT_LIMIT_BASELINE_SHADOW_MISMATCH, merchantId: {}, ruleId: {}, limitType: {}, legacyUnits: {}, lifecycleUnits: {}",
                        requestDTO.getMerchantId(),
                        rule.getRuleId(),
                        rule.getHitElement(),
                        legacyUnits,
                        normalizedLifecycleUnits);
            }
            return legacyUnits;
        }
        return normalizedLifecycleUnits;
    }

    /**
     * 从 primary 上的交易逻辑表读取同商户、同币种累计交易金额。
     */
    private BigDecimal loadTransactionPeriodAmount(RiskPaymentEvaluateRequestDTO requestDTO,
                                                   String currency,
                                                   MerchantLimitPeriod period) {
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            return riskRuntimeMapper.sumRiskApprovedTransactionAmount(
                    requestDTO.getMerchantId().trim(),
                    currency,
                    period.beginTime(),
                    period.endTime(),
                    requestDTO.getTransactionId().trim());
        }
    }

    /**
     * 把两种数据库基线结果交给无敏感维度的周期汇总器。
     *
     * @param legacyUnits    历史交易事实基线
     * @param lifecycleUnits 生命周期预占事实基线
     */
    private void recordBaselineShadow(long legacyUnits, long lifecycleUnits) {
        if (shadowComparisonMonitor != null) {
            shadowComparisonMonitor.recordBaseline(legacyUnits, lifecycleUnits);
        }
    }

    /**
     * 构建 Redis Cluster 同槽的累计值与交易预占 Key。
     *
     * <p>仅该多 Key Lua 场景使用统一 Hash Tag，确保脚本可在同一 Slot 原子执行。</p>
     *
     * @return 同槽双 Key 预占标识
     */
    private MerchantLimitReservation clusterSafeMerchantLimitReservation(RiskListMatch rule,
                                                                          String merchantId,
                                                                          String currency,
                                                                          MerchantLimitPeriod period,
                                                                          String transactionId) {
        String slotIdentity = String.join(
                ":",
                safeKey(rule.getHitElement()).toLowerCase(Locale.ROOT),
                String.valueOf(rule.getRuleId()),
                safeMerchant(merchantId),
                currency,
                period.bucket()
        );
        String transactionDigest = RedisKeyDigest.sha256(transactionId.trim());
        return new MerchantLimitReservation(
                redisProperties.coLocatedBusinessKey(
                        "risk", "merchant-limit", slotIdentity, "total"),
                redisProperties.coLocatedBusinessKey(
                        "risk", "merchant-limit", slotIdentity, "reservation", transactionDigest)
        );
    }

    /**
     * 原子初始化周期基线、校验限额、记录交易预占并刷新 TTL。
     *
     * @param reservation 同槽或旧版聚合 Key 与预占 Key
     * @param amountUnits 本次六位小数定标整数金额
     * @param ttlSeconds 周期结束后一小时的剩余 TTL
     * @param limitUnits 规则限额的定标整数金额
     * @param passAction 超限时是否仍按规则动作保留预占
     * @param seedUnits 首次执行时使用的数据库基线
     * @return 非负累计值或脚本定义的负数拒绝值；执行异常时返回空
     */
    private Optional<Long> executeMerchantLimitReserve(MerchantLimitReservation reservation,
                                                       long amountUnits,
                                                       long ttlSeconds,
                                                       long limitUnits,
                                                       boolean passAction,
                                                       long seedUnits) {
        long startNanos = System.nanoTime();
        try {
            Long scriptResult = stringRedisTemplate.execute(
                    MERCHANT_LIMIT_RESERVE_SCRIPT,
                    List.of(reservation.aggregateKey(), reservation.reservationKey()),
                    String.valueOf(amountUnits),
                    String.valueOf(ttlSeconds),
                    String.valueOf(limitUnits),
                    passAction ? "0" : "1",
                    String.valueOf(seedUnits)
            );
            if (scriptResult == null) {
                throw new IllegalStateException("merchant cumulative limit script returned null");
            }
            recordRiskOperation(
                    RedisBusinessMetrics.Feature.RISK_CUMULATIVE_LIMIT,
                    RedisBusinessMetrics.Outcome.SUCCESS,
                    startNanos
            );
            return Optional.of(scriptResult);
        } catch (RuntimeException exception) {
            recordRiskOperation(
                    RedisBusinessMetrics.Feature.RISK_CUMULATIVE_LIMIT,
                    RedisBusinessMetrics.Outcome.ERROR,
                    startNanos
            );
            metrics.recordLuaFailure(
                    RedisBusinessMetrics.Script.RISK_CUMULATIVE_RESERVE,
                    metrics.classifyFailure(exception)
            );
            log.warn("商户累计限额 Redis 预占失败, event: RISK_MERCHANT_LIMIT_RESERVE_FAILED, aggregateKeyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(reservation.aggregateKey()), exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 将日、周、月限额映射为左闭右开的业务时间区间和 Redis 周期桶。
     *
     * <p>周周期从周一零时开始；TTL 至周期结束后一小时且最少保留六十秒。</p>
     *
     * @return 周期定义；类型未知或评估时间为空时返回 {@code null}
     */
    private MerchantLimitPeriod merchantLimitPeriod(String limitType, LocalDateTime evaluationTime) {
        if (!StringUtils.hasText(limitType) || evaluationTime == null) {
            return null;
        }
        LocalDateTime beginTime;
        LocalDateTime endTime;
        String normalized = limitType.trim().toUpperCase(Locale.ROOT);
        if (LIMIT_TYPE_DAILY.equals(normalized)) {
            beginTime = evaluationTime.toLocalDate().atStartOfDay();
            endTime = beginTime.plusDays(1);
        } else if (LIMIT_TYPE_WEEKLY.equals(normalized)) {
            beginTime = evaluationTime.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay();
            endTime = beginTime.plusWeeks(1);
        } else if (LIMIT_TYPE_MONTHLY.equals(normalized)) {
            beginTime = evaluationTime.toLocalDate().withDayOfMonth(1).atStartOfDay();
            endTime = beginTime.plusMonths(1);
        } else {
            return null;
        }
        long ttlSeconds = Math.max(60L, Duration.between(LocalDateTime.now(), endTime.plusHours(1)).getSeconds());
        String bucket = beginTime.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return new MerchantLimitPeriod(beginTime, endTime, bucket, ttlSeconds);
    }

    /**
     * 将累计整数金额转换为业务金额并形成 PASS 或 HIT 审计明细。
     *
     * @return 不修改原缓存规则的独立明细
     */
    private RiskListMatch cumulativeLimitDetail(RiskListMatch rule,
                                                long currentUnits,
                                                long limitUnits,
                                                boolean exceeded) {
        RiskListMatch detail = copyRule(rule);
        BigDecimal currentAmount = amountFromUnits(currentUnits);
        BigDecimal limitAmount = amountFromUnits(limitUnits);
        detail.setCurrentAmount(currentAmount);
        detail.setAmountLimit(limitAmount);
        detail.setHitValueMasked(rule.getHitElement() + ":" + currentAmount.toPlainString()
                + "/" + limitAmount.toPlainString());
        detail.setMatchResult(exceeded ? "HIT" : "PASS");
        if (exceeded) {
            detail.setDecisionReason(rule.getHitElement() + "累计金额" + currentAmount.toPlainString()
                    + "超过限额" + limitAmount.toPlainString());
        } else {
            detail.setDecisionAction("PASS");
            detail.setDecisionReason(rule.getHitElement() + "累计金额" + currentAmount.toPlainString()
                    + "未超过限额" + limitAmount.toPlainString() + "，放行");
        }
        return detail;
    }

    /**
     * 将累计限额依赖不可用统一映射为 ERROR/REVIEW，禁止静默放行。
     *
     * @param reason 不含敏感值的内部原因
     * @return 人工复核明细
     */
    private RiskListMatch cumulativeLimitUnavailable(RiskListMatch rule, String reason) {
        RiskListMatch detail = copyRule(rule);
        detail.setDecisionAction("REVIEW");
        detail.setHitValueMasked(safeKey(rule.getHitElement()) + ":COUNTER_UNAVAILABLE");
        detail.setDecisionReason(reason);
        detail.setMatchResult("ERROR");
        return detail;
    }

    /**
     * 逆序执行 Lua 回滚，按交易预占 Key 幂等扣减本次累计金额。
     *
     * @return 全部回滚成功时返回 {@code true}；任一脚本失败时记录摘要并返回 {@code false}
     */
    private boolean rollbackReservations(List<MerchantLimitReservation> reservations) {
        if (stringRedisTemplate == null || reservations == null || reservations.isEmpty()) {
            return true;
        }
        boolean successful = true;
        for (int index = reservations.size() - 1; index >= 0; index--) {
            MerchantLimitReservation reservation = reservations.get(index);
            long startNanos = System.nanoTime();
            try {
                stringRedisTemplate.execute(
                        MERCHANT_LIMIT_ROLLBACK_SCRIPT,
                        List.of(reservation.aggregateKey(), reservation.reservationKey())
                );
                recordRiskOperation(
                        RedisBusinessMetrics.Feature.RISK_CUMULATIVE_LIMIT,
                        RedisBusinessMetrics.Outcome.SUCCESS,
                        startNanos
                );
            } catch (RuntimeException exception) {
                successful = false;
                recordRiskOperation(
                        RedisBusinessMetrics.Feature.RISK_CUMULATIVE_LIMIT,
                        RedisBusinessMetrics.Outcome.ERROR,
                        startNanos
                );
                metrics.recordLuaFailure(
                        RedisBusinessMetrics.Script.RISK_CUMULATIVE_ROLLBACK,
                        metrics.classifyFailure(exception)
                );
                log.error("商户累计限额 Redis 回滚失败, event: RISK_MERCHANT_LIMIT_ROLLBACK_FAILED, aggregateKeyDigest: {}, exceptionType: {}",
                        RedisKeyDigest.sha256(reservation.aggregateKey()), exception.getClass().getSimpleName());
            }
        }
        return successful;
    }

    /**
     * 记录风控 Redis 脚本调用，不包含规则、商户、交易或聚合 Key 维度。
     *
     * @param feature    风控 Redis 能力
     * @param outcome    执行结果
     * @param startNanos 本次脚本调用起始时间
     */
    private void recordRiskOperation(RedisBusinessMetrics.Feature feature,
                                     RedisBusinessMetrics.Outcome outcome,
                                     long startNanos) {
        metrics.recordOperation(
                feature,
                RedisBusinessMetrics.Operation.EXECUTE,
                outcome,
                System.nanoTime() - startNanos
        );
    }

    /**
     * 构建累计限额预占的数据库生命周期事实。
     *
     * <p>金额保存为六位小数定标整数，过期时间与 Redis 周期宽限时间一致。</p>
     *
     * @return 尚未持久化的预占实体
     */
    private MerchantLimitReservationDO buildLifecycleReservation(RiskPaymentEvaluateRequestDTO requestDTO,
                                                                 String riskRecordNo,
                                                                 RiskListMatch rule,
                                                                 String currency,
                                                                 MerchantLimitPeriod period,
                                                                 long amountUnits) {
        MerchantLimitReservationDO reservation = new MerchantLimitReservationDO();
        reservation.setTransactionId(requestDTO.getTransactionId().trim());
        reservation.setRiskRecordNo(riskRecordNo.trim());
        reservation.setMerchantId(requestDTO.getMerchantId().trim());
        reservation.setRuleId(rule.getRuleId());
        reservation.setLimitType(rule.getHitElement().trim().toUpperCase(Locale.ROOT));
        reservation.setCurrency(currency);
        reservation.setPeriodBucket(period.bucket());
        reservation.setPeriodBeginTime(period.beginTime());
        reservation.setPeriodEndTime(period.endTime());
        reservation.setAmountUnits(amountUnits);
        reservation.setCounterMode(CLUSTER_SAFE_COUNTER_MODE);
        reservation.setExpiresAt(period.endTime().plusHours(1));
        return reservation;
    }

    /**
     * 容错解析持久化预占状态，不让未知字符串误判为可迁移状态。
     *
     * @return 已知状态；记录或状态无效时返回 {@code null}
     */
    private MerchantLimitReservationStatus reservationStatus(MerchantLimitReservationDO reservation) {
        if (reservation == null || !StringUtils.hasText(reservation.getReservationStatus())) {
            return null;
        }
        try {
            return MerchantLimitReservationStatus.valueOf(
                    reservation.getReservationStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 先回滚 Redis 预占，全部成功后再取消数据库生命周期事实。
     *
     * <p>Redis 回滚失败时保留非终态事实供补偿任务重试，避免数据库提前标记已取消。</p>
     */
    private void rollbackAndCancel(List<MerchantLimitReservation> reservations,
                                   String transactionId,
                                   boolean lifecycleManaged,
                                   String reason) {
        boolean redisRolledBack = rollbackReservations(reservations);
        if (redisRolledBack && lifecycleManaged && reservationStateService != null
                && StringUtils.hasText(transactionId)) {
            reservationStateService.cancel(transactionId, reason);
        }
    }

    /**
     * 判断超限规则动作是否明确允许交易继续。
     */
    private boolean isPassAction(RiskListMatch rule) {
        return rule != null && "PASS".equalsIgnoreCase(rule.getDecisionAction());
    }

    /**
     * 将业务金额无舍入转换为六位小数定标整数。
     *
     * @throws ArithmeticException 精度超过六位或超出 {@code long} 范围时抛出
     */
    private long amountUnits(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.setScale(INTERNAL_AMOUNT_SCALE, RoundingMode.UNNECESSARY)
                .movePointRight(INTERNAL_AMOUNT_SCALE)
                .longValueExact();
    }

    /**
     * 将六位小数定标整数还原为业务金额。
     */
    private BigDecimal amountFromUnits(long amountUnits) {
        return BigDecimal.valueOf(amountUnits, INTERNAL_AMOUNT_SCALE);
    }

    /**
     * 将空值和空集合统一为不可变空列表，非空结果复制为不可变快照。
     */
    private List<RiskListMatch> immutableList(List<RiskListMatch> matches) {
        return matches == null || matches.isEmpty() ? List.of() : List.copyOf(matches);
    }

    /**
     * 按匹配类型选择已归一化值，供外层再次摘要后组成缓存业务键。
     *
     * @return 非空占位或规范匹配值；调用方不得直接将其记录到日志
     */
    private String matchCacheValue(RiskListFunction function, RiskRuntimeLookupValue lookupValue) {
        return safeKey(switch (function.getMatchKind()) {
            case HASH -> lookupValue.getMatchValueHash();
            case IP_RANGE, CARD_BIN_RANGE -> lookupValue.getNumericValue() == null ? null : lookupValue.getNumericValue().toPlainString();
            case COUNTRY -> lookupValue.getCountryAlpha3();
            case REGION -> lookupValue.getMatchValueHash();
            case SOURCE_HOST -> lookupValue.getMatchValueHash();
        });
    }

    /**
     * 规范商户缓存维度，缺失商户号时使用明确的全局范围标识。
     */
    private String safeMerchant(String merchantId) {
        return StringUtils.hasText(merchantId) ? merchantId.trim() : "GLOBAL";
    }

    /**
     * 规范非敏感 Key 片段，空值使用稳定占位符。
     */
    private String safeKey(String value) {
        return StringUtils.hasText(value) ? value.trim() : "NONE";
    }

    /**
     * 规范数据库查询文本参数，空值转换为空字符串。
     */
    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    /**
     * 规范规则枚举维度，空值按全部适用处理。
     */
    private String defaultAll(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : "ALL";
    }

    /**
     * 将风险等级映射为 3DS 规则比较权重。
     *
     * @return CRITICAL 为 4、HIGH 为 3、MEDIUM 为 2，其余为 1
     */
    private int riskWeight(String riskLevel) {
        if (!StringUtils.hasText(riskLevel)) {
            return 1;
        }
        return switch (riskLevel.trim().toUpperCase()) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    /**
     * 返回非空的规则缓存迁移模式。
     *
     * @return 配置模式；配置绑定异常产生空值时保守使用 LEGACY
     */
    private RiskRuleCacheMode ruleCacheMode() {
        return properties.getRuleCacheMode() == null
                ? RiskRuleCacheMode.LEGACY
                : properties.getRuleCacheMode();
    }

    /**
     * 名单快照查询结果。
     *
     * @param available 快照是否安全回答本次查询
     * @param match     首条匹配；有效空集合或未命中时为空
     * @param anyRows   完整集合是否包含至少一条规则
     */
    private record SnapshotListMatch(boolean available,
                                     Optional<RiskListMatch> match,
                                     boolean anyRows) {

        /**
         * 创建缓存不可用结果，调用方必须回源既有主库查询。
         *
         * @return 不可用结果
         */
        private static SnapshotListMatch unavailable() {
            return new SnapshotListMatch(false, Optional.empty(), false);
        }

        /**
         * 创建已由完整快照回答的结果。
         *
         * @param match   首条匹配
         * @param anyRows 快照是否包含规则
         * @return 可用结果
         */
        private static SnapshotListMatch available(Optional<RiskListMatch> match, boolean anyRows) {
            return new SnapshotListMatch(true, match == null ? Optional.empty() : match, anyRows);
        }
    }

    /**
     * 序列化规则行快照读取结果。
     *
     * @param available 快照是否可用于真实决策
     * @param rows      有效空集合或完整规则集合
     */
    private record SnapshotRows(boolean available, List<RiskRuleSnapshotRow> rows) {

        /**
         * 创建不可用结果。
         *
         * @return 不可用且无规则行
         */
        private static SnapshotRows unavailable() {
            return new SnapshotRows(false, List.of());
        }

        /**
         * 创建可用结果并复制集合。
         *
         * @param rows 完整规则行
         * @return 可用快照
         */
        private static SnapshotRows available(List<RiskRuleSnapshotRow> rows) {
            return new SnapshotRows(true, rows == null ? List.of() : List.copyOf(rows));
        }
    }

    /**
     * 可直接执行规则集合的快照读取结果。
     *
     * @param available 快照是否可参与真实决策
     * @param matches   有效空集合或完整规则集合
     */
    private record SnapshotMatches(boolean available, List<RiskListMatch> matches) {

        /**
         * 创建不可用结果。
         *
         * @return 不可用且无规则
         */
        private static SnapshotMatches unavailable() {
            return new SnapshotMatches(false, List.of());
        }

        /**
         * 创建可用结果并复制规则集合。
         *
         * @param matches 完整规则集合
         * @return 可用快照
         */
        private static SnapshotMatches available(List<RiskListMatch> matches) {
            return new SnapshotMatches(true, matches == null ? List.of() : List.copyOf(matches));
        }
    }

    /**
     * 描述累计限额所在的自然时间桶和 Redis 存活时间。
     *
     * @param beginTime 时间桶起点，包含该时间
     * @param endTime 时间桶终点，不包含该时间
     * @param bucket 用于 Redis Key 的稳定桶标识
     * @param ttlSeconds 覆盖时间桶并保留补偿窗口的存活秒数
     */
    private record MerchantLimitPeriod(LocalDateTime beginTime,
                                       LocalDateTime endTime,
                                       String bucket,
                                       long ttlSeconds) {
    }
}
