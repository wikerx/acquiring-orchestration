package com.scott.payment.risk.repository.impl;

import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.string.RedisStringService;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.config.RiskCounterMode;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.config.RiskFrequencyMode;
import com.scott.payment.risk.domain.MerchantLimitEvaluation;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.RiskRuntimeLookupValue;
import com.scott.payment.risk.mapper.RiskRuntimeMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis Cluster 下累计限额和频率脚本的真实执行验证。
 */
@Slf4j
@EnabledIfSystemProperty(named = "risk.redis.cluster.integration.enabled", matches = "true")
class DefaultRiskListRuntimeRepositoryClusterIntegrationTests {

    /** 真实 Cluster 集成测试专用 Key 前缀，与开发和生产业务 Key 隔离。 */
    private static final String REDIS_KEY_PREFIX = "acquiring:cluster-it";

    /** 构造频率滑动窗口 Key 使用的固定 IP 摘要，不包含真实客户端地址。 */
    private static final String IP_LOOKUP_HASH = "cluster-ip-hash";

    /** 测试类共享的 Lettuce Cluster 连接工厂，在全部用例结束后统一销毁。 */
    private static LettuceConnectionFactory connectionFactory;

    /** 执行真实 Cluster Lua 脚本并清理测试 Key 的字符串模板。 */
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void setUpClusterConnection() {
        String configuredNodes = System.getProperty(
                "risk.redis.cluster.nodes",
                "127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002"
        );
        RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration(
                Arrays.stream(configuredNodes.split(","))
                        .map(String::trim)
                        .filter(node -> !node.isEmpty())
                        .toList()
        );
        clusterConfiguration.setMaxRedirects(5);
        connectionFactory = new LettuceConnectionFactory(clusterConfiguration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void closeClusterConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldReserveOnceAndRollbackOnRedisCluster() {
        String merchantId = uniqueMerchantId();
        RiskListMatch cumulativeRule = cumulativeRule(101L);
        DefaultRiskListRuntimeRepository repository = repository(
                merchantId,
                List.of(cumulativeRule),
                List.of(),
                2_000
        );
        RiskPaymentEvaluateRequestDTO request = request(
                merchantId,
                "TXN-CLUSTER-CUMULATIVE-" + UUID.randomUUID(),
                "10.000000"
        );

        MerchantLimitEvaluation first = repository.reserveCumulativeMerchantLimits(request);
        MerchantLimitEvaluation duplicate = repository.reserveCumulativeMerchantLimits(request);

        assertThat(first.details()).singleElement()
                .satisfies(detail ->
                        assertThat(detail.getCurrentAmount()).isEqualByComparingTo("110.000000"));
        assertThat(duplicate.details()).singleElement()
                .satisfies(detail ->
                        assertThat(detail.getCurrentAmount()).isEqualByComparingTo("110.000000"));
        assertThat(first.reservations()).singleElement().satisfies(reservation -> {
            assertThat(reservation.aggregateKey()).contains(":{").endsWith("}:total");
            assertThat(reservation.reservationKey()).contains(":{", ":reservation:");
        });

        repository.rollbackMerchantLimitReservations(first);
        MerchantLimitEvaluation afterRollback =
                repository.reserveCumulativeMerchantLimits(request);
        assertThat(afterRollback.details()).singleElement()
                .satisfies(detail ->
                        assertThat(detail.getCurrentAmount()).isEqualByComparingTo("110.000000"));
    }

    @Test
    void shouldCountEachTransactionOnceOnRedisCluster() {
        String merchantId = uniqueMerchantId();
        RiskListMatch frequencyRule = frequencyRule(201L);
        DefaultRiskListRuntimeRepository repository = repository(
                merchantId,
                List.of(),
                List.of(frequencyRule),
                2_000
        );
        RiskRuntimeLookupValue ipLookup = ipLookup();
        RiskPaymentEvaluateRequestDTO firstRequest = request(
                merchantId,
                "TXN-CLUSTER-FREQUENCY-" + UUID.randomUUID(),
                "10.000000"
        );

        RiskListMatch first = repository.evaluateFrequencyRules(
                merchantId, firstRequest, null, null, ipLookup, null, null, null, null
        ).get(0);
        RiskListMatch duplicate = repository.evaluateFrequencyRules(
                merchantId, firstRequest, null, null, ipLookup, null, null, null, null
        ).get(0);
        RiskListMatch second = repository.evaluateFrequencyRules(
                merchantId,
                request(merchantId, "TXN-CLUSTER-FREQUENCY-" + UUID.randomUUID(), "10.000000"),
                null,
                null,
                ipLookup,
                null,
                null,
                null,
                null
        ).get(0);

        assertThat(first.getCurrentCount()).isEqualTo(1L);
        assertThat(duplicate.getCurrentCount()).isEqualTo(1L);
        assertThat(second.getCurrentCount()).isEqualTo(2L);
        assertThat(first.getMatchResult()).isEqualTo("PASS");
    }

    /**
     * 使用真实 Cluster 脚本验证成员数达到上限后进入 REVIEW，且已有成员不会被错误删除。
     */
    @Test
    void shouldRejectAtSlidingWindowCapacityOnRedisCluster() {
        log.info("测试真实 Cluster 滑动窗口容量，关键输入: 单 ZSet 上限 2、连续 3 个交易摘要");
        String merchantId = uniqueMerchantId();
        long ruleId = 202L;
        RiskListMatch frequencyRule = frequencyRule(ruleId, 2);
        DefaultRiskListRuntimeRepository repository = repository(
                merchantId,
                List.of(),
                List.of(frequencyRule),
                2
        );
        RiskRuntimeLookupValue ipLookup = ipLookup();
        String windowKey = slidingWindowKey(ruleId, merchantId, IP_LOOKUP_HASH);

        try {
            RiskListMatch first = evaluateFrequency(repository, merchantId, ipLookup, "CAPACITY-1");
            RiskListMatch second = evaluateFrequency(repository, merchantId, ipLookup, "CAPACITY-2");
            RiskListMatch overflow = evaluateFrequency(repository, merchantId, ipLookup, "CAPACITY-3");

            assertThat(first.getCurrentCount()).isEqualTo(1L);
            assertThat(second.getCurrentCount()).isEqualTo(2L);
            assertThat(overflow.getMatchResult()).isEqualTo("ERROR");
            assertThat(overflow.getDecisionAction()).isEqualTo("REVIEW");
            assertThat(redisTemplate.opsForZSet().size(windowKey)).isEqualTo(2L);
            log.info("真实 Cluster 滑动窗口容量测试完成，结果: 第三个摘要进入 REVIEW，ZSet 保持 2 个成员");
        } finally {
            redisTemplate.delete(windowKey);
        }
    }

    /**
     * 把目标窗口预置为 String，验证生产 Lua 遇到 WRONGTYPE 时不会静默放行交易。
     */
    @Test
    void shouldFailClosedWhenSlidingWindowLuaReceivesWrongTypeOnRedisCluster() {
        log.info("测试真实 Cluster Lua 异常，关键输入: 滑动窗口 Key 被预置为 String");
        String merchantId = uniqueMerchantId();
        long ruleId = 203L;
        RiskListMatch frequencyRule = frequencyRule(ruleId, 5);
        DefaultRiskListRuntimeRepository repository = repository(
                merchantId,
                List.of(),
                List.of(frequencyRule),
                2_000
        );
        RiskRuntimeLookupValue ipLookup = ipLookup();
        String windowKey = slidingWindowKey(ruleId, merchantId, IP_LOOKUP_HASH);
        redisTemplate.opsForValue().set(windowKey, "wrong-type");

        try {
            RiskListMatch detail = evaluateFrequency(repository, merchantId, ipLookup, "WRONGTYPE-1");

            assertThat(detail.getMatchResult()).isEqualTo("ERROR");
            assertThat(detail.getDecisionAction()).isEqualTo("REVIEW");
            assertThat(redisTemplate.opsForValue().get(windowKey)).isEqualTo("wrong-type");
            log.info("真实 Cluster Lua 异常测试完成，结果: 风控保守进入 REVIEW，污染值未被覆盖");
        } finally {
            redisTemplate.delete(windowKey);
        }
    }

    /**
     * 在本机临时 Cluster 上采样生产滑动窗口路径的顺序调用吞吐与尾延迟。
     *
     * <p>该结果只用于发现数量级回退，不代表生产网络、连接池或多实例容量。</p>
     */
    @Test
    void shouldMeasureSlidingWindowLatencyOnRedisCluster() {
        int warmupCount = 100;
        int measuredCount = 2_000;
        String merchantId = uniqueMerchantId();
        long ruleId = 204L;
        RiskListMatch frequencyRule = frequencyRule(ruleId, warmupCount + measuredCount);
        DefaultRiskListRuntimeRepository repository = repository(
                merchantId,
                List.of(),
                List.of(frequencyRule),
                warmupCount + measuredCount
        );
        RiskRuntimeLookupValue ipLookup = ipLookup();
        String windowKey = slidingWindowKey(ruleId, merchantId, IP_LOOKUP_HASH);
        long[] latencyNanos = new long[measuredCount];

        try {
            for (int index = 0; index < warmupCount; index++) {
                evaluateFrequency(repository, merchantId, ipLookup, "WARMUP-" + index);
            }

            long benchmarkStartedNanos = System.nanoTime();
            RiskListMatch last = null;
            for (int index = 0; index < measuredCount; index++) {
                long operationStartedNanos = System.nanoTime();
                last = evaluateFrequency(repository, merchantId, ipLookup, "MEASURED-" + index);
                latencyNanos[index] = System.nanoTime() - operationStartedNanos;
            }
            long elapsedNanos = System.nanoTime() - benchmarkStartedNanos;

            assertThat(last).isNotNull();
            assertThat(last.getCurrentCount()).isEqualTo((long) warmupCount + measuredCount);
            assertThat(redisTemplate.opsForZSet().size(windowKey))
                    .isEqualTo((long) warmupCount + measuredCount);
            log.info(
                    "真实 Cluster 滑动窗口基础性能完成，样本数: {} throughputOpsPerSecond: {} "
                            + "p95Micros: {} p99Micros: {}；仅代表本机临时容器顺序调用",
                    measuredCount,
                    throughputPerSecond(measuredCount, elapsedNanos),
                    percentileMicros(latencyNanos, 0.95D),
                    percentileMicros(latencyNanos, 0.99D)
            );
        } finally {
            redisTemplate.delete(windowKey);
        }
    }

    private DefaultRiskListRuntimeRepository repository(String merchantId,
                                                        List<RiskListMatch> cumulativeRules,
                                                        List<RiskListMatch> frequencyRules,
                                                        int frequencyMaxMembers) {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        when(mapper.selectActiveCumulativeMerchantLimitRules(merchantId, "USD"))
                .thenReturn(cumulativeRules);
        when(mapper.selectActiveFrequencyRules(merchantId)).thenReturn(frequencyRules);
        when(mapper.sumRiskApprovedTransactionAmount(
                any(),
                org.mockito.ArgumentMatchers.eq(merchantId),
                org.mockito.ArgumentMatchers.eq("USD"),
                any(),
                any(),
                anyString()
        )).thenReturn(new BigDecimal("100.000000"));

        ShardingDataTemplate shardingDataTemplate = mock(ShardingDataTemplate.class);
        when(shardingDataTemplate.resolvePhysicalTables(any()))
                .thenReturn(List.of("transaction_order_202607"));

        RedisStringService redisStringService = mock(RedisStringService.class);
        when(redisStringService.get(anyString())).thenReturn(null);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current("risk-runtime-rule"))
                .thenReturn(RedisCacheGenerationState.active("cluster-it"));

        RiskEvaluationProperties properties = new RiskEvaluationProperties();
        properties.setCounterMode(RiskCounterMode.CLUSTER_SAFE);
        properties.setCounterCutoverConfirmed(true);
        properties.setFrequencyMode(RiskFrequencyMode.SLIDING_WINDOW);
        properties.setFrequencyCutoverConfirmed(true);
        properties.setFrequencyMaxMembers(frequencyMaxMembers);
        properties.setFrequencyMaxThresholdCount(Math.max(
                properties.getFrequencyMaxThresholdCount(),
                frequencyMaxMembers
        ));
        PaymentRedisProperties redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix(REDIS_KEY_PREFIX);

        return new DefaultRiskListRuntimeRepository(
                provider(mapper),
                provider(redisStringService),
                provider(generationStore),
                provider(redisTemplate),
                provider(shardingDataTemplate),
                provider(null),
                provider(null),
                properties,
                redisProperties
        );
    }

    private RiskPaymentEvaluateRequestDTO request(String merchantId,
                                                  String transactionId,
                                                  String amount) {
        RiskPaymentEvaluateRequestDTO request = new RiskPaymentEvaluateRequestDTO();
        request.setMerchantId(merchantId);
        request.setMerchantOrderNo("ORDER-" + transactionId);
        request.setTransactionId(transactionId);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("USD");
        request.setTransactionDateTime(LocalDateTime.now());
        return request;
    }

    private RiskListMatch cumulativeRule(long ruleId) {
        RiskListMatch rule = new RiskListMatch();
        rule.setRuleId(ruleId);
        rule.setModuleType("RULE");
        rule.setFunctionCode("merchantLimit");
        rule.setFunctionName("merchant cumulative limit");
        rule.setHitElement("DAILY");
        rule.setRiskLevel("HIGH");
        rule.setDecisionAction("REJECT");
        rule.setAmountLimit(new BigDecimal("200.000000"));
        return rule;
    }

    private RiskListMatch frequencyRule(long ruleId) {
        return frequencyRule(ruleId, 5);
    }

    private RiskListMatch frequencyRule(long ruleId, int allowedCount) {
        RiskListMatch rule = new RiskListMatch();
        rule.setRuleId(ruleId);
        rule.setThresholdCount(allowedCount);
        rule.setTimeWindowSeconds(3600);
        rule.setDecisionAction("REJECT");
        rule.setElementsJson(
                "{\"elements\":[\"ip\"],\"statisticDimension\":\"ELEMENT_COMBINATION\","
                        + "\"allowedCount\":" + allowedCount + "}"
        );
        return rule;
    }

    /**
     * 创建固定哈希值的 IP 查询结果，确保测试可以精确计算生产窗口 Key。
     *
     * @return 不包含 IP 明文的运行时查询值
     */
    private RiskRuntimeLookupValue ipLookup() {
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setMatchValueHash(IP_LOOKUP_HASH);
        return lookupValue;
    }

    /**
     * 执行一笔只包含 IP 频率维度的风险评估，并返回唯一规则明细。
     *
     * @param repository 风控 Redis 仓储
     * @param merchantId 随机隔离商户号
     * @param ipLookup   已哈希的 IP 查询值
     * @param suffix     测试交易号后缀
     * @return 单条频率规则评估明细
     */
    private RiskListMatch evaluateFrequency(DefaultRiskListRuntimeRepository repository,
                                            String merchantId,
                                            RiskRuntimeLookupValue ipLookup,
                                            String suffix) {
        return repository.evaluateFrequencyRules(
                merchantId,
                request(merchantId, "TXN-CLUSTER-FREQUENCY-" + suffix, "10.000000"),
                null,
                null,
                ipLookup,
                null,
                null,
                null,
                null
        ).get(0);
    }

    /**
     * 按生产 Key 算法计算测试窗口 Key，供精确预置、容量核对和清理使用。
     *
     * @param ruleId     频率规则 ID
     * @param merchantId 随机隔离商户号
     * @param ipHash     IP 不可逆摘要
     * @return 单 ZSet 滑动窗口物理 Key
     */
    private String slidingWindowKey(long ruleId, String merchantId, String ipHash) {
        String legacyCounterKey = REDIS_KEY_PREFIX + ":risk:runtime:frequency:"
                + ruleId + ":" + merchantId + ":" + RedisKeyDigest.sha256("ip=" + ipHash);
        return REDIS_KEY_PREFIX + ":risk:frequency-window:" + RedisKeyDigest.sha256(legacyCounterKey);
    }

    /**
     * 计算指定样本在总耗时内的整数吞吐，单位为操作数/秒。
     *
     * @param operationCount 操作数量
     * @param elapsedNanos   总耗时，单位纳秒
     * @return 每秒完成操作数
     */
    private long throughputPerSecond(int operationCount, long elapsedNanos) {
        return Math.round(operationCount * 1_000_000_000D / Math.max(1L, elapsedNanos));
    }

    /**
     * 计算单次调用耗时样本的最近秩百分位，返回微秒便于报告阅读。
     *
     * @param latencyNanos 纳秒耗时样本
     * @param percentile  百分位，取值范围 0 到 1
     * @return 对应百分位耗时，单位微秒
     */
    private long percentileMicros(long[] latencyNanos, double percentile) {
        long[] sorted = latencyNanos.clone();
        Arrays.sort(sorted);
        int index = Math.max(0, Math.min(
                sorted.length - 1,
                (int) Math.ceil(percentile * sorted.length) - 1
        ));
        return TimeUnit.NANOSECONDS.toMicros(sorted[index]);
    }

    private String uniqueMerchantId() {
        return "M" + UUID.randomUUID().toString().replace("-", "");
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
