package com.scott.payment.risk.repository.impl;

import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.string.RedisStringService;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.domain.MerchantLimitEvaluation;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.RiskRuntimeLookupValue;
import com.scott.payment.risk.mapper.RiskRuntimeMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    /** 当前测试产生的 Redis Key，仅按已登记的确定值精确清理。 */
    private final Set<String> cleanupKeys = new HashSet<>();

    @BeforeAll
    static void setUpClusterConnection() {
        String configuredNodes = System.getProperty(
                "risk.redis.cluster.nodes",
                "127.0.0.1:7001,127.0.0.1:7002,127.0.0.1:7003,"
                        + "127.0.0.1:7004,127.0.0.1:7005,127.0.0.1:7006"
        );
        String password = System.getProperty("risk.redis.cluster.password");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "risk.redis.cluster.password is required for Cluster integration tests");
        }
        RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration(
                Arrays.stream(configuredNodes.split(","))
                        .map(String::trim)
                        .filter(node -> !node.isEmpty())
                        .toList()
        );
        clusterConfiguration.setMaxRedirects(5);
        clusterConfiguration.setPassword(RedisPassword.of(password));
        connectionFactory = new LettuceConnectionFactory(clusterConfiguration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterEach
    void cleanUpIntegrationKeys() {
        cleanupKeys.forEach(redisTemplate::delete);
        cleanupKeys.clear();
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
                List.of()
        );
        RiskPaymentEvaluateRequestDTO request = request(
                merchantId,
                "TXN-CLUSTER-CUMULATIVE-" + UUID.randomUUID(),
                "10.000000"
        );

        MerchantLimitEvaluation first = repository.reserveCumulativeMerchantLimits(request);
        rememberReservationKeys(first);
        MerchantLimitEvaluation duplicate = repository.reserveCumulativeMerchantLimits(request);
        rememberReservationKeys(duplicate);

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
        rememberReservationKeys(afterRollback);
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
                List.of(frequencyRule)
        );
        RiskRuntimeLookupValue ipLookup = ipLookup();
        RiskPaymentEvaluateRequestDTO firstRequest = request(
                merchantId,
                "TXN-CLUSTER-FREQUENCY-" + UUID.randomUUID(),
                "10.000000"
        );
        RiskPaymentEvaluateRequestDTO secondRequest = request(
                merchantId,
                "TXN-CLUSTER-FREQUENCY-" + UUID.randomUUID(),
                "10.000000"
        );
        rememberFrequencyKeys(frequencyRule, merchantId, firstRequest, secondRequest);

        RiskListMatch first = repository.evaluateFrequencyRules(
                merchantId, firstRequest, null, null, ipLookup, null, null, null, null
        ).get(0);
        RiskListMatch duplicate = repository.evaluateFrequencyRules(
                merchantId, firstRequest, null, null, ipLookup, null, null, null, null
        ).get(0);
        RiskListMatch second = repository.evaluateFrequencyRules(
                merchantId,
                secondRequest,
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

    private DefaultRiskListRuntimeRepository repository(String merchantId,
                                                        List<RiskListMatch> cumulativeRules,
                                                        List<RiskListMatch> frequencyRules) {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        when(mapper.selectActiveCumulativeMerchantLimitRules(merchantId, "USD"))
                .thenReturn(cumulativeRules);
        when(mapper.selectActiveFrequencyRules(merchantId)).thenReturn(frequencyRules);
        when(mapper.sumRiskApprovedTransactionAmountPhysical(
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
        PaymentRedisProperties redisProperties = redisProperties();

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

    private String uniqueMerchantId() {
        return "M" + UUID.randomUUID().toString().replace("-", "");
    }

    private void rememberReservationKeys(MerchantLimitEvaluation evaluation) {
        evaluation.reservations().forEach(reservation -> {
            cleanupKeys.add(reservation.aggregateKey());
            cleanupKeys.add(reservation.reservationKey());
        });
    }

    private void rememberFrequencyKeys(RiskListMatch rule,
                                       String merchantId,
                                       RiskPaymentEvaluateRequestDTO... requests) {
        String elementDigest = RedisKeyDigest.sha256("ip=" + IP_LOOKUP_HASH);
        String slotIdentity = rule.getRuleId() + ":" + merchantId + ":" + elementDigest;
        String counterKey = redisProperties().coLocatedBusinessKey(
                "risk", "frequency", slotIdentity, "counter");
        cleanupKeys.add(counterKey);
        Arrays.stream(requests)
                .map(RiskPaymentEvaluateRequestDTO::getTransactionId)
                .map(String::trim)
                .map(RedisKeyDigest::sha256)
                .map(transactionDigest -> counterKey + ":transaction:" + transactionDigest)
                .forEach(cleanupKeys::add);
    }

    private PaymentRedisProperties redisProperties() {
        PaymentRedisProperties redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix(REDIS_KEY_PREFIX);
        return redisProperties;
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
