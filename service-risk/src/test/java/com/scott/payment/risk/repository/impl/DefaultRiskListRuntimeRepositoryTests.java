package com.scott.payment.risk.repository.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.string.RedisStringService;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.config.RiskBaselineMode;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.config.RiskRuleCacheMode;
import com.scott.payment.risk.domain.MerchantLimitEvaluation;
import com.scott.payment.risk.domain.FrequencySuccessReservationResult;
import com.scott.payment.risk.domain.state.MerchantLimitReservationStatus;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import com.scott.payment.risk.domain.RiskListFunction;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.RiskRuntimeCacheEntry;
import com.scott.payment.risk.domain.RiskRuntimeLookupValue;
import com.scott.payment.risk.domain.RiskRuleSnapshotRow;
import com.scott.payment.risk.mapper.RiskRuntimeMapper;
import com.scott.payment.risk.observability.RiskShadowComparisonMonitor;
import com.scott.payment.risk.service.MerchantLimitReservationStateService;
import com.scott.payment.risk.service.FrequencySuccessReservationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 风控运行时 Redis 缓存行为测试。
 */
@Slf4j
class DefaultRiskListRuntimeRepositoryTests {

    @Test
    void shouldUseDedicatedHierarchicalRegionQueryInsteadOfHashColumns() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        when(redis.get(anyString())).thenReturn(null);
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setCountryAlpha3("BRB");
        lookupValue.setStateProvinceName("Saint Michael");
        lookupValue.setCityName("Bridgetown");
        lookupValue.setMatchValueMasked("BRB/Saint Michael/Bridgetown");
        RiskListMatch expected = new RiskListMatch();
        expected.setRuleId(5L);
        when(mapper.selectRegionMatch(
                "BLACK",
                "region",
                "高风险区域黑名单",
                "region",
                "200045",
                "BRB",
                "Saint Michael",
                "Bridgetown"))
                .thenReturn(expected);
        DefaultRiskListRuntimeRepository repository = repository(mapper, redis);

        assertThat(repository.findListMatch(RiskListFunction.BLACK_REGION, "200045", lookupValue))
                .containsSame(expected);

        verify(mapper, never()).selectHashMatch(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldIsolateRuleCacheEntriesByCurrentGeneration() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current("risk-runtime-rule"))
                .thenReturn(RedisCacheGenerationState.active("g-20260730"));
        when(redis.get(anyString()))
                .thenReturn(JsonUtils.toJsonString(RiskRuntimeCacheEntry.match(null)));
        DefaultRiskListRuntimeRepository repository = repository(
                mapper,
                redis,
                generationStore
        );
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setMatchValueHash("card-fingerprint-digest");

        assertThat(repository.findListMatch(
                RiskListFunction.BLACK_CARD_FINGERPRINT,
                "M202607290001",
                lookupValue
        )).isEmpty();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redis).get(keyCaptor.capture());
        assertThat(keyCaptor.getValue())
                .startsWith("acquiring:test:risk:runtime-rule:g-20260730:")
                .contains(":match:BLACK:cardFingerprint:M202607290001:")
                .doesNotContain("card-fingerprint-digest");
    }

    @Test
    void shouldReadExplicitNegativeCacheOnceWithoutQueryingDatabase() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        when(redis.get(anyString()))
                .thenReturn(JsonUtils.toJsonString(RiskRuntimeCacheEntry.match(null)));
        DefaultRiskListRuntimeRepository repository = repository(mapper, redis);
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setSourceHost("checkout.merchant.example");

        assertThat(repository.findSourceUrlRestrictionMiss("M202607290001", lookupValue)).isEmpty();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redis).get(keyCaptor.capture());
        assertThat(keyCaptor.getValue())
                .startsWith("acquiring:test:risk:runtime-rule:g-test:"
                        + "rule:source-url:miss:M202607290001:")
                .doesNotContain("checkout.merchant.example");
        verify(mapper, never()).countActiveSourceUrlRules(anyString());
    }

    @Test
    void shouldWriteStructuredNegativeCacheInsteadOfMagicString() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        when(redis.get(anyString())).thenReturn(null);
        when(mapper.countActiveSourceUrlRules("M202607290001")).thenReturn(0L);
        DefaultRiskListRuntimeRepository repository = repository(mapper, redis);
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setSourceHost("checkout.merchant.example");

        assertThat(repository.findSourceUrlRestrictionMiss("M202607290001", lookupValue)).isEmpty();

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(redis).set(anyString(), valueCaptor.capture(), ttlCaptor.capture());
        RiskRuntimeCacheEntry entry = JsonUtils.parseObject(valueCaptor.getValue(), RiskRuntimeCacheEntry.class);
        assertThat(entry).isNotNull();
        assertThat(entry.isFound()).isFalse();
        assertThat(entry.getMatch()).isNull();
        assertThat(valueCaptor.getValue()).doesNotContain("__MISS__").isNotEqualTo("1");
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void shouldBypassStaleRuleCacheWhilePublicationIsPending() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current("risk-runtime-rule"))
                .thenReturn(RedisCacheGenerationState.pending());
        when(redis.get(anyString()))
                .thenReturn(JsonUtils.toJsonString(RiskRuntimeCacheEntry.match(null)));
        when(mapper.countActiveSourceUrlRules("M202607290001")).thenReturn(1L);
        when(mapper.countActiveSourceUrlHit(
                "M202607290001",
                "checkout.merchant.example"
        )).thenReturn(0L);
        DefaultRiskListRuntimeRepository repository = repository(
                mapper,
                redis,
                generationStore
        );
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setSourceHost("checkout.merchant.example");

        assertThat(repository.findSourceUrlRestrictionMiss("M202607290001", lookupValue))
                .get()
                .satisfies(match -> assertThat(match.getDecisionAction()).isEqualTo("REJECT"));
        verify(redis, never()).get(anyString());
        verify(redis, never()).set(
                anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldBypassRuleCacheWhenGenerationLookupFails() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current("risk-runtime-rule"))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(redis.get(anyString()))
                .thenReturn(JsonUtils.toJsonString(RiskRuntimeCacheEntry.match(null)));
        RiskListMatch expected = new RiskListMatch();
        expected.setRuleId(91L);
        when(mapper.selectHashMatch(
                "risk_black_card_fingerprint",
                "BLACK",
                "cardFingerprint",
                "卡指纹黑名单",
                "cardFingerprint",
                "M202607290001",
                "card-fingerprint-digest"
        )).thenReturn(expected);
        DefaultRiskListRuntimeRepository repository = repository(mapper, redis, generationStore);
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setMatchValueHash("card-fingerprint-digest");

        assertThat(repository.findListMatch(
                RiskListFunction.BLACK_CARD_FINGERPRINT,
                "M202607290001",
                lookupValue
        )).containsSame(expected);
        verify(redis, never()).get(anyString());
        verify(redis, never()).set(anyString(), any(), any());
    }

    @Test
    void shouldVersionBooleanAndListRuleCacheFamilies() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        when(redis.get(anyString())).thenReturn(null);
        when(mapper.countActiveListRules("risk_black_card_fingerprint", "M202607290001")).thenReturn(1L);
        RiskListMatch frequencyRule = new RiskListMatch();
        frequencyRule.setRuleId(92L);
        when(mapper.selectActiveFrequencyRules("M202607290001")).thenReturn(List.of(frequencyRule));
        DefaultRiskListRuntimeRepository repository = repository(mapper, redis);

        assertThat(repository.hasActiveListRule(
                RiskListFunction.BLACK_CARD_FINGERPRINT,
                "M202607290001"
        )).isTrue();
        assertThat(repository.hasActiveFrequencyRule("M202607290001")).isTrue();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redis, org.mockito.Mockito.times(2)).get(keyCaptor.capture());
        assertThat(keyCaptor.getAllValues())
                .allSatisfy(key -> assertThat(key)
                        .startsWith("acquiring:test:risk:runtime-rule:g-test:"))
                .anySatisfy(key -> assertThat(key).contains(":list:active:BLACK_CARD_FINGERPRINT:"))
                .anySatisfy(key -> assertThat(key).contains(":rule:frequency:active:"));
    }

    @Test
    void shouldStopReadingOldRuleCacheAfterGenerationChanges() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current("risk-runtime-rule"))
                .thenReturn(
                        RedisCacheGenerationState.active("g-old"),
                        RedisCacheGenerationState.active("g-new")
                );
        when(redis.get(anyString())).thenAnswer(invocation -> {
            String cacheKey = invocation.getArgument(0);
            return cacheKey.contains(":g-old:")
                    ? JsonUtils.toJsonString(RiskRuntimeCacheEntry.match(null))
                    : null;
        });
        RiskListMatch expected = new RiskListMatch();
        expected.setRuleId(93L);
        when(mapper.selectMerchantLimitRule(
                "M202607290001",
                new BigDecimal("100.00"),
                "USD"
        )).thenReturn(expected);
        DefaultRiskListRuntimeRepository repository = repository(mapper, redis, generationStore);

        assertThat(repository.findMerchantLimitRule(
                "M202607290001",
                new BigDecimal("100.00"),
                "USD"
        )).isEmpty();
        assertThat(repository.findMerchantLimitRule(
                "M202607290001",
                new BigDecimal("100.00"),
                "USD"
        )).containsSame(expected);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redis, org.mockito.Mockito.times(2)).get(keyCaptor.capture());
        assertThat(keyCaptor.getAllValues())
                .anySatisfy(key -> assertThat(key).contains(":g-old:"))
                .anySatisfy(key -> assertThat(key).contains(":g-new:"));
        verify(mapper).selectMerchantLimitRule("M202607290001", new BigDecimal("100.00"), "USD");
    }

    @Test
    void shouldAllowConfiguredSourceHostRegardlessOfMissActionStoredOnRule() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        when(redis.get(anyString())).thenReturn(null);
        RiskListMatch configuredRule = new RiskListMatch();
        configuredRule.setRuleId(41L);
        configuredRule.setFunctionCode("sourceUrl");
        configuredRule.setFunctionName("商户来源网址限定");
        configuredRule.setHitElement("sourceUrl");
        configuredRule.setHitValueMasked("checkout.merchant.example");
        configuredRule.setRiskLevel("HIGH");
        configuredRule.setDecisionAction("REJECT");
        when(mapper.selectSourceUrlRule("M202607290001", "checkout.merchant.example"))
                .thenReturn(configuredRule);
        DefaultRiskListRuntimeRepository repository = repository(mapper, redis);
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setSourceHost("checkout.merchant.example");
        lookupValue.setMatchValueHash("source-host-digest");

        assertThat(repository.findSourceUrlRule("M202607290001", lookupValue))
                .get()
                .satisfies(match -> {
                    assertThat(match.getRuleId()).isEqualTo(41L);
                    assertThat(match.getRiskLevel()).isEqualTo("LOW");
                    assertThat(match.getDecisionAction()).isEqualTo("PASS");
                    assertThat(match.getDecisionReason()).isEqualTo("merchant source url is allowed");
                });
    }

    @Test
    void shouldRejectPendingSourceUrlWhileAllowingApprovedAndLegacySnapshotHosts() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        when(redis.get(anyString())).thenReturn(null);
        RiskRuleSnapshotRow pending = sourceUrlSnapshotRow(42L, "pending.merchant.example", false);
        RiskRuleSnapshotRow approved = sourceUrlSnapshotRow(43L, "approved.merchant.example", true);
        RiskRuleSnapshotRow legacy = sourceUrlSnapshotRow(44L, "legacy.merchant.example", null);
        when(mapper.selectActiveSourceUrlSnapshotRows(eq("M202607290001"), any(Integer.class)))
                .thenReturn(List.of(pending, approved, legacy));
        DefaultRiskListRuntimeRepository repository = snapshotRepository(mapper, redis);

        RiskRuntimeLookupValue pendingLookup = new RiskRuntimeLookupValue();
        pendingLookup.setSourceHost("pending.merchant.example");
        pendingLookup.setMatchValueHash("pending-source-digest");
        assertThat(repository.findSourceUrlRule("M202607290001", pendingLookup)).isEmpty();
        assertThat(repository.findSourceUrlRestrictionMiss("M202607290001", pendingLookup))
                .get()
                .satisfies(match -> assertThat(match.getDecisionAction()).isEqualTo("REJECT"));

        RiskRuntimeLookupValue approvedLookup = new RiskRuntimeLookupValue();
        approvedLookup.setSourceHost("approved.merchant.example");
        approvedLookup.setMatchValueHash("approved-source-digest");
        assertThat(repository.findSourceUrlRule("M202607290001", approvedLookup))
                .get()
                .satisfies(match -> assertThat(match.getDecisionAction()).isEqualTo("PASS"));

        RiskRuntimeLookupValue legacyLookup = new RiskRuntimeLookupValue();
        legacyLookup.setSourceHost("legacy.merchant.example");
        legacyLookup.setMatchValueHash("legacy-source-digest");
        assertThat(repository.findSourceUrlRule("M202607290001", legacyLookup))
                .get()
                .satisfies(match -> assertThat(match.getDecisionAction()).isEqualTo("PASS"));
        assertThat(repository.findSourceUrlRestrictionMiss("M202607290001", approvedLookup)).isEmpty();
        assertThat(repository.findSourceUrlRestrictionMiss("M202607290001", legacyLookup)).isEmpty();
    }

    @Test
    void shouldEnforceConfiguredMerchantIpWhitelistEvenWhenLegacySwitchIsOff() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        when(redis.get(anyString())).thenReturn(null);
        when(mapper.selectMerchantIpWhitelistEnabled("M202607290001")).thenReturn(0);
        when(mapper.countActiveMerchantIpWhitelist("M202607290001")).thenReturn(1L);
        when(mapper.countMerchantIpWhitelistHit("M202607290001", "198.51.100.24")).thenReturn(0L);
        DefaultRiskListRuntimeRepository repository = repository(mapper, redis);
        RiskRuntimeLookupValue ipLookup = new RiskRuntimeLookupValue();
        ipLookup.setRawValue("198.51.100.24");

        assertThat(repository.findMerchantIpWhitelistMiss("M202607290001", ipLookup))
                .get()
                .satisfies(match -> {
                    assertThat(match.getDecisionAction()).isEqualTo("REJECT");
                    assertThat(match.getFunctionCode()).isEqualTo("merchantIpWhitelist");
                });
    }

    @Test
    void shouldDigestIpAndCardBinDimensionsInPhysicalKeys() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        when(redis.get(anyString()))
                .thenReturn(JsonUtils.toJsonString(RiskRuntimeCacheEntry.match(null)));
        DefaultRiskListRuntimeRepository repository = repository(mapper, redis);
        RiskRuntimeLookupValue ipLookup = new RiskRuntimeLookupValue();
        ipLookup.setRawValue("198.51.100.24");
        RiskRuntimeLookupValue cardBinLookup = new RiskRuntimeLookupValue();
        cardBinLookup.setNumericValue(new BigDecimal("654321"));

        assertThat(repository.findMerchantIpWhitelistHit("M202607290001", ipLookup)).isEmpty();
        assertThat(repository.findIssuerCountryByCardBin(cardBinLookup)).isEmpty();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redis, org.mockito.Mockito.times(2)).get(keyCaptor.capture());
        assertThat(keyCaptor.getAllValues()).allSatisfy(key -> {
            assertThat(key).startsWith("acquiring:test:risk:runtime-rule:g-test:");
            assertThat(key).doesNotContain("198.51.100.24").doesNotContain("654321");
        });
    }

    @Test
    void shouldCacheIssuerCountryPointLookupInsteadOfLoadingOversizedSnapshot() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        RiskListMatch expected = new RiskListMatch();
        expected.setRuleId(81L);
        expected.setHitValueMasked("USA");
        when(generationStore.current("risk-runtime-rule"))
                .thenReturn(RedisCacheGenerationState.active("g-test"));
        when(redis.get(anyString())).thenReturn(
                null,
                JsonUtils.toJsonString(RiskRuntimeCacheEntry.match(expected))
        );
        when(mapper.selectIssuerCountryByCardBin(new BigDecimal("411111")))
                .thenReturn(expected);
        RiskEvaluationProperties properties = new RiskEvaluationProperties();
        properties.setRuleCacheMode(RiskRuleCacheMode.SNAPSHOT);
        properties.setCacheHitTtlSeconds(300);
        properties.setCacheMissTtlSeconds(60);
        PaymentRedisProperties redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix("acquiring:test");
        DefaultRiskListRuntimeRepository repository = new DefaultRiskListRuntimeRepository(
                provider(mapper),
                provider(redis),
                provider(generationStore),
                provider(redisTemplate),
                provider(null),
                provider(null),
                properties,
                redisProperties
        );
        RiskRuntimeLookupValue cardBinLookup = new RiskRuntimeLookupValue();
        cardBinLookup.setNumericValue(new BigDecimal("411111"));

        assertThat(repository.findIssuerCountryByCardBin(cardBinLookup)).contains("USA");
        assertThat(repository.findIssuerCountryByCardBin(cardBinLookup)).contains("USA");

        verify(mapper).selectIssuerCountryByCardBin(new BigDecimal("411111"));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldReserveCumulativeMerchantLimitFromLogicalTransactionTable() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RiskListMatch dailyRule = cumulativeRule(11L, "DAILY", "200.000000");
        when(mapper.selectActiveCumulativeMerchantLimitRules("M202607290001", "USD"))
                .thenReturn(List.of(dailyRule));
        when(mapper.sumRiskApprovedTransactionAmount(
                org.mockito.ArgumentMatchers.eq("M202607290001"),
                org.mockito.ArgumentMatchers.eq("USD"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("TXN-001")))
                .thenReturn(new BigDecimal("100.000000"));
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(110_000_000L);
        DefaultRiskListRuntimeRepository repository = repository(
                mapper, mock(RedisStringService.class), redisTemplate, null);

        MerchantLimitEvaluation evaluation = repository.reserveCumulativeMerchantLimits(
                cumulativeRequest("TXN-001", "10.000000"));

        assertThat(evaluation.details()).singleElement()
                .satisfies(detail -> {
                    assertThat(detail.getMatchResult()).isEqualTo("PASS");
                    assertThat(detail.getCurrentAmount()).isEqualByComparingTo("110.000000");
                    assertThat(detail.getAmountLimit()).isEqualByComparingTo("200.000000");
                });
        assertThat(evaluation.reservations()).hasSize(1);
        verify(mapper).sumRiskApprovedTransactionAmount(
                "M202607290001",
                "USD",
                LocalDateTime.of(2026, 7, 29, 0, 0),
                LocalDateTime.of(2026, 7, 30, 0, 0),
                "TXN-001"
        );
    }

    @Test
    void shouldKeepLegacySeedWhileObservingLifecycleBaselineInShadowMode() {
        log.info("测试累计基线 shadow，关键输入: 历史基线 100 USD、生命周期基线 90 USD");
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RiskShadowComparisonMonitor monitor = mock(RiskShadowComparisonMonitor.class);
        when(mapper.selectActiveCumulativeMerchantLimitRules("M202607290001", "USD"))
                .thenReturn(List.of(cumulativeRule(41L, "DAILY", "200.000000")));
        when(mapper.sumRiskApprovedTransactionAmount(
                anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new BigDecimal("100.000000"));
        when(mapper.sumLifecycleReservationAmountUnits(
                "M202607290001", 41L, "USD", "20260729", "TXN-BASELINE-SHADOW"))
                .thenReturn(90_000_000L);
        when(redisTemplate.execute(
                any(), org.mockito.ArgumentMatchers.anyList(),
                any(), any(), any(), any(), any()))
                .thenReturn(110_000_000L);
        RiskEvaluationProperties properties = cumulativeProperties(RiskBaselineMode.SHADOW);
        DefaultRiskListRuntimeRepository repository = repository(
                mapper,
                redisTemplate,
                properties,
                monitor);

        MerchantLimitEvaluation evaluation = repository.reserveCumulativeMerchantLimits(
                cumulativeRequest("TXN-BASELINE-SHADOW", "10.000000"));

        ArgumentCaptor<String> seedCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).execute(
                any(),
                org.mockito.ArgumentMatchers.anyList(),
                any(),
                any(),
                any(),
                any(),
                seedCaptor.capture());
        assertThat(seedCaptor.getValue()).isEqualTo("100000000");
        assertThat(evaluation.details()).singleElement()
                .satisfies(detail ->
                        assertThat(detail.getCurrentAmount()).isEqualByComparingTo("110.000000"));
        verify(monitor).recordBaseline(100_000_000L, 90_000_000L);
        log.info("累计基线 shadow 验证完成，结果: 真实决策继续使用历史 100 USD 基线");
    }

    @Test
    void shouldRebuildClusterSafeCounterFromConfirmedLifecycleBaseline() {
        log.info("测试累计计数重建，关键输入: 已确认 LIFECYCLE 基线 75 USD、同槽计数模式");
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(mapper.selectActiveCumulativeMerchantLimitRules("M202607290001", "USD"))
                .thenReturn(List.of(cumulativeRule(42L, "DAILY", "200.000000")));
        when(mapper.sumRiskApprovedTransactionAmount(
                anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new BigDecimal("100.000000"));
        when(mapper.sumLifecycleReservationAmountUnits(
                "M202607290001", 42L, "USD", "20260729", "TXN-LIFECYCLE-REBUILD"))
                .thenReturn(75_000_000L);
        when(redisTemplate.execute(
                any(), org.mockito.ArgumentMatchers.anyList(),
                any(), any(), any(), any(), any()))
                .thenReturn(85_000_000L);
        RiskEvaluationProperties properties = cumulativeProperties(RiskBaselineMode.LIFECYCLE);
        DefaultRiskListRuntimeRepository repository = repository(
                mapper,
                redisTemplate,
                properties,
                null);

        MerchantLimitEvaluation evaluation = repository.reserveCumulativeMerchantLimits(
                cumulativeRequest("TXN-LIFECYCLE-REBUILD", "10.000000"));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> seedCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).execute(
                any(),
                keysCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                seedCaptor.capture());
        assertThat(seedCaptor.getValue()).isEqualTo("75000000");
        assertThat(String.valueOf(keysCaptor.getValue().get(0)))
                .startsWith("acquiring:test:risk:merchant-limit:{")
                .endsWith("}:total");
        assertThat(evaluation.details()).singleElement()
                .satisfies(detail ->
                        assertThat(detail.getCurrentAmount()).isEqualByComparingTo("85.000000"));
        log.info("累计计数重建验证完成，结果: 同槽 Key 使用生命周期 75 USD 基线初始化");
    }

    @Test
    void shouldResolveLeapDayDailyWeeklyAndMonthlyBoundaries() {
        log.info("测试累计周期边界，关键输入: 2028-02-29 23:59:59 的日、周、月规则");
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(mapper.selectActiveCumulativeMerchantLimitRules("M202607290001", "USD"))
                .thenReturn(List.of(
                        cumulativeRule(51L, "DAILY", "100.000000"),
                        cumulativeRule(52L, "WEEKLY", "200.000000"),
                        cumulativeRule(53L, "MONTHLY", "300.000000")
                ));
        when(mapper.sumRiskApprovedTransactionAmount(
                anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(BigDecimal.ZERO);
        when(redisTemplate.execute(
                any(), org.mockito.ArgumentMatchers.anyList(),
                any(), any(), any(), any(), any()))
                .thenReturn(1_000_000L);
        DefaultRiskListRuntimeRepository repository = repository(
                mapper,
                mock(RedisStringService.class),
                redisTemplate,
                null);
        RiskPaymentEvaluateRequestDTO request =
                cumulativeRequest("TXN-LEAP-BOUNDARY", "1.000000");
        request.setTransactionDateTime(LocalDateTime.of(2028, 2, 29, 23, 59, 59));

        MerchantLimitEvaluation evaluation =
                repository.reserveCumulativeMerchantLimits(request);

        verify(mapper).sumRiskApprovedTransactionAmount(
                "M202607290001", "USD",
                LocalDateTime.of(2028, 2, 29, 0, 0),
                LocalDateTime.of(2028, 3, 1, 0, 0),
                "TXN-LEAP-BOUNDARY");
        verify(mapper).sumRiskApprovedTransactionAmount(
                "M202607290001", "USD",
                LocalDateTime.of(2028, 2, 28, 0, 0),
                LocalDateTime.of(2028, 3, 6, 0, 0),
                "TXN-LEAP-BOUNDARY");
        verify(mapper).sumRiskApprovedTransactionAmount(
                "M202607290001", "USD",
                LocalDateTime.of(2028, 2, 1, 0, 0),
                LocalDateTime.of(2028, 3, 1, 0, 0),
                "TXN-LEAP-BOUNDARY");
        assertThat(evaluation.details()).hasSize(3);
        log.info("累计周期边界验证完成，结果: 闰日日/周/月区间均为左闭右开");
    }

    @Test
    void shouldPersistPreparingBeforeRedisMutationAndMarkReservedAfterSuccess() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        MerchantLimitReservationStateService stateService =
                mock(MerchantLimitReservationStateService.class);
        when(mapper.selectActiveCumulativeMerchantLimitRules("M202607290001", "USD"))
                .thenReturn(List.of(cumulativeRule(11L, "DAILY", "200.000000")));
        when(mapper.sumRiskApprovedTransactionAmount(
                anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new BigDecimal("100.000000"));
        when(stateService.prepare(any())).thenAnswer(invocation -> {
            MerchantLimitReservationDO reservation = invocation.getArgument(0);
            reservation.setId(41L);
            reservation.setVersion(0);
            reservation.setReservationStatus(MerchantLimitReservationStatus.PREPARING.name());
            return reservation;
        });
        when(stateService.markReserved(any())).thenReturn(true);
        when(redisTemplate.execute(
                any(),
                org.mockito.ArgumentMatchers.anyList(),
                any(), any(), any(), any(), any()))
                .thenReturn(110_000_000L);
        DefaultRiskListRuntimeRepository repository = repository(
                mapper,
                mock(RedisStringService.class),
                redisTemplate,
                stateService);

        MerchantLimitEvaluation evaluation = repository.reserveCumulativeMerchantLimits(
                cumulativeRequest("TXN-LIFECYCLE-001", "10.000000"),
                "RK-LIFECYCLE-001");

        assertThat(evaluation.lifecycleManaged()).isTrue();
        assertThat(evaluation.transactionId()).isEqualTo("TXN-LIFECYCLE-001");
        assertThat(evaluation.riskRecordNo()).isEqualTo("RK-LIFECYCLE-001");
        ArgumentCaptor<MerchantLimitReservationDO> candidateCaptor =
                ArgumentCaptor.forClass(MerchantLimitReservationDO.class);
        verify(stateService).prepare(candidateCaptor.capture());
        assertThat(candidateCaptor.getValue()).satisfies(candidate -> {
            assertThat(candidate.getTransactionId()).isEqualTo("TXN-LIFECYCLE-001");
            assertThat(candidate.getRiskRecordNo()).isEqualTo("RK-LIFECYCLE-001");
            assertThat(candidate.getAmountUnits()).isEqualTo(10_000_000L);
            assertThat(candidate.getCounterMode()).isEqualTo("CLUSTER_SAFE");
        });
        InOrder order = inOrder(stateService, redisTemplate);
        order.verify(stateService).prepare(any());
        order.verify(redisTemplate).execute(
                any(),
                org.mockito.ArgumentMatchers.anyList(),
                any(), any(), any(), any(), any());
        order.verify(stateService).markReserved(any());
    }

    @Test
    void shouldUseConciseCoLocatedKeysForClusterSafeCumulativeCounter() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(mapper.selectActiveCumulativeMerchantLimitRules("M202607290001", "USD"))
                .thenReturn(List.of(cumulativeRule(11L, "DAILY", "200.000000")));
        when(mapper.sumRiskApprovedTransactionAmount(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new BigDecimal("100.000000"));
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(110_000_000L);
        DefaultRiskListRuntimeRepository repository = repository(
                mapper,
                mock(RedisStringService.class),
                redisTemplate,
                null
        );

        MerchantLimitEvaluation evaluation = repository.reserveCumulativeMerchantLimits(
                cumulativeRequest("TXN-CLUSTER-001", "10.000000"));

        assertThat(evaluation.reservations()).singleElement().satisfies(reservation -> {
            assertThat(reservation.aggregateKey())
                    .startsWith("acquiring:test:risk:merchant-limit:{")
                    .endsWith("}:total")
                    .doesNotContain("service-risk", ":v2:", "TXN-CLUSTER-001");
            assertThat(reservation.reservationKey())
                    .startsWith("acquiring:test:risk:merchant-limit:{")
                    .contains(":reservation:")
                    .doesNotContain("TXN-CLUSTER-001");
            assertThat(hashTag(reservation.aggregateKey()))
                    .isEqualTo(hashTag(reservation.reservationKey()));
        });
    }

    @Test
    void shouldRejectCumulativeMerchantLimitWithoutPersistingReservation() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(mapper.selectActiveCumulativeMerchantLimitRules("M202607290001", "USD"))
                .thenReturn(List.of(cumulativeRule(12L, "MONTHLY", "105.000000")));
        when(mapper.sumRiskApprovedTransactionAmount(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new BigDecimal("100.000000"));
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(-110_000_000L);
        DefaultRiskListRuntimeRepository repository = repository(
                mapper, mock(RedisStringService.class), redisTemplate, null);

        MerchantLimitEvaluation evaluation = repository.reserveCumulativeMerchantLimits(
                cumulativeRequest("TXN-002", "10.000000"));

        assertThat(evaluation.details()).singleElement()
                .satisfies(detail -> {
                    assertThat(detail.getMatchResult()).isEqualTo("HIT");
                    assertThat(detail.getDecisionAction()).isEqualTo("REJECT");
                });
        assertThat(evaluation.reservations()).isEmpty();
    }

    @Test
    void shouldAuditAllCumulativeRulesBeforeReturningBlockedDecision() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(mapper.selectActiveCumulativeMerchantLimitRules("M202607290001", "USD"))
                .thenReturn(List.of(
                        cumulativeRule(31L, "DAILY", "100.000000"),
                        cumulativeRule(32L, "WEEKLY", "200.000000"),
                        cumulativeRule(33L, "MONTHLY", "300.000000")
                ));
        when(mapper.sumRiskApprovedTransactionAmount(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new BigDecimal("500.000000"));
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(-510_000_000L);
        DefaultRiskListRuntimeRepository repository = repository(
                mapper, mock(RedisStringService.class), redisTemplate, null);

        MerchantLimitEvaluation evaluation = repository.reserveCumulativeMerchantLimits(
                cumulativeRequest("TXN-003", "10.000000"));

        assertThat(evaluation.details())
                .extracting(RiskListMatch::getHitElement, RiskListMatch::getMatchResult)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("DAILY", "HIT"),
                        org.assertj.core.groups.Tuple.tuple("WEEKLY", "HIT"),
                        org.assertj.core.groups.Tuple.tuple("MONTHLY", "HIT")
                );
        assertThat(evaluation.reservations()).isEmpty();
        verify(redisTemplate, org.mockito.Mockito.times(3)).execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldUseStableTransactionMarkerForFrequencyRetries() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RiskListMatch frequencyRule = new RiskListMatch();
        frequencyRule.setRuleId(21L);
        frequencyRule.setThresholdCount(5);
        frequencyRule.setTimeWindowSeconds(3600);
        frequencyRule.setElementsJson("""
                {"elements":["ip"],"statisticDimension":"ELEMENT_COMBINATION","allowedCount":5}
                """);
        when(redis.get(anyString())).thenReturn(null);
        when(mapper.selectActiveFrequencyRules("M202607290001")).thenReturn(List.of(frequencyRule));
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(1L);
        DefaultRiskListRuntimeRepository repository = repository(
                mapper, redis, redisTemplate, null);
        RiskPaymentEvaluateRequestDTO requestDTO = cumulativeRequest("TXN-FREQUENCY-001", "10.000000");
        RiskRuntimeLookupValue ipLookup = new RiskRuntimeLookupValue();
        ipLookup.setMatchValueHash("ip-hash");

        assertThat(repository.findFrequencyRuleHit(
                "M202607290001", requestDTO, null, null, ipLookup, null, null, null, null)).isEmpty();
        assertThat(repository.findFrequencyRuleHit(
                "M202607290001", requestDTO, null, null, ipLookup, null, null, null, null)).isEmpty();

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, org.mockito.Mockito.times(2)).execute(
                org.mockito.ArgumentMatchers.any(),
                keysCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("3600"));
        assertThat(keysCaptor.getAllValues()).allSatisfy(keys -> {
            assertThat(keys).hasSize(2);
            assertThat(String.valueOf(keys.get(0)))
                    .startsWith("acquiring:test:risk:frequency:{")
                    .endsWith("}:counter");
            assertThat(String.valueOf(keys.get(1)))
                    .contains(":transaction:")
                    .doesNotContain("TXN-FREQUENCY-001");
            assertThat(hashTag(String.valueOf(keys.get(0))))
                    .isEqualTo(hashTag(String.valueOf(keys.get(1))));
        });
        assertThat(keysCaptor.getAllValues().get(0).get(1))
                .isEqualTo(keysCaptor.getAllValues().get(1).get(1));
    }

    @Test
    void shouldReserveConfiguredFrequencySuccessSlotBeforeAllowingTransaction() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        FrequencySuccessReservationService successReservationService =
                mock(FrequencySuccessReservationService.class);
        RiskListMatch frequencyRule = frequencyRule(23L, 5, 2);
        when(redis.get(anyString())).thenReturn(null);
        when(mapper.selectActiveFrequencyRules("M202607290001"))
                .thenReturn(List.of(frequencyRule));
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(1L);
        when(successReservationService.reserve(
                "M202607290001",
                "TXN-FREQUENCY-SUCCESS-001",
                23L,
                RedisKeyDigest.sha256("ip=ip-hash"),
                2,
                3600))
                .thenReturn(new FrequencySuccessReservationResult(
                        FrequencySuccessReservationResult.Outcome.RESERVED,
                        1L));
        DefaultRiskListRuntimeRepository repository = repository(
                mapper, redis, redisTemplate, null, successReservationService);
        RiskRuntimeLookupValue ipLookup = new RiskRuntimeLookupValue();
        ipLookup.setMatchValueHash("ip-hash");

        List<RiskListMatch> details = repository.evaluateFrequencyRules(
                "M202607290001",
                cumulativeRequest("TXN-FREQUENCY-SUCCESS-001", "10.000000"),
                null, null, ipLookup, null, null, null, null);

        assertThat(details).singleElement().satisfies(detail -> {
            assertThat(detail.getMatchResult()).isEqualTo("PASS");
            assertThat(detail.getCurrentCount()).isEqualTo(1L);
        });
        verify(successReservationService).reserve(
                "M202607290001",
                "TXN-FREQUENCY-SUCCESS-001",
                23L,
                RedisKeyDigest.sha256("ip=ip-hash"),
                2,
                3600);
    }

    @Test
    void shouldBlockProjectedSuccessWhenFrequencySuccessLimitIsFull() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        FrequencySuccessReservationService successReservationService =
                mock(FrequencySuccessReservationService.class);
        RiskListMatch frequencyRule = frequencyRule(24L, 5, 1);
        when(redis.get(anyString())).thenReturn(null);
        when(mapper.selectActiveFrequencyRules("M202607290001"))
                .thenReturn(List.of(frequencyRule));
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(2L);
        when(successReservationService.reserve(
                anyString(), anyString(), eq(24L), anyString(), eq(1), eq(3600)))
                .thenReturn(new FrequencySuccessReservationResult(
                        FrequencySuccessReservationResult.Outcome.LIMIT_EXCEEDED,
                        1L));
        DefaultRiskListRuntimeRepository repository = repository(
                mapper, redis, redisTemplate, null, successReservationService);
        RiskRuntimeLookupValue ipLookup = new RiskRuntimeLookupValue();
        ipLookup.setMatchValueHash("ip-hash");

        List<RiskListMatch> details = repository.evaluateFrequencyRules(
                "M202607290001",
                cumulativeRequest("TXN-FREQUENCY-SUCCESS-002", "10.000000"),
                null, null, ipLookup, null, null, null, null);

        assertThat(details).singleElement().satisfies(detail -> {
            assertThat(detail.getMatchResult()).isEqualTo("HIT");
            assertThat(detail.getHitElement()).isEqualTo("frequencySuccess");
            assertThat(detail.getThresholdCount()).isEqualTo(1);
            assertThat(detail.getCurrentCount()).isEqualTo(2L);
            assertThat(detail.getDecisionReason()).contains("successful transaction frequency limit");
        });
    }

    @Test
    void shouldReviewInsteadOfAllowingWhenFrequencySuccessReservationIsUnavailable() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        FrequencySuccessReservationService successReservationService =
                mock(FrequencySuccessReservationService.class);
        RiskListMatch frequencyRule = frequencyRule(25L, 5, 2);
        when(redis.get(anyString())).thenReturn(null);
        when(mapper.selectActiveFrequencyRules("M202607290001"))
                .thenReturn(List.of(frequencyRule));
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(1L);
        when(successReservationService.reserve(
                anyString(), anyString(), eq(25L), anyString(), eq(2), eq(3600)))
                .thenReturn(FrequencySuccessReservationResult.unavailable());
        DefaultRiskListRuntimeRepository repository = repository(
                mapper, redis, redisTemplate, null, successReservationService);
        RiskRuntimeLookupValue ipLookup = new RiskRuntimeLookupValue();
        ipLookup.setMatchValueHash("ip-hash");

        List<RiskListMatch> details = repository.evaluateFrequencyRules(
                "M202607290001",
                cumulativeRequest("TXN-FREQUENCY-SUCCESS-003", "10.000000"),
                null, null, ipLookup, null, null, null, null);

        assertThat(details).singleElement().satisfies(detail -> {
            assertThat(detail.getMatchResult()).isEqualTo("ERROR");
            assertThat(detail.getDecisionAction()).isEqualTo("REVIEW");
            assertThat(detail.getDecisionReason()).contains("success reservation is unavailable");
        });
    }

    @Test
    void shouldNotReserveFrequencySuccessSlotWhenSuccessCountIsZero() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        FrequencySuccessReservationService successReservationService =
                mock(FrequencySuccessReservationService.class);
        RiskListMatch frequencyRule = frequencyRule(26L, 5, 0);
        when(redis.get(anyString())).thenReturn(null);
        when(mapper.selectActiveFrequencyRules("M202607290001"))
                .thenReturn(List.of(frequencyRule));
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(1L);
        DefaultRiskListRuntimeRepository repository = repository(
                mapper, redis, redisTemplate, null, successReservationService);
        RiskRuntimeLookupValue ipLookup = new RiskRuntimeLookupValue();
        ipLookup.setMatchValueHash("ip-hash");

        assertThat(repository.evaluateFrequencyRules(
                "M202607290001",
                cumulativeRequest("TXN-FREQUENCY-SUCCESS-004", "10.000000"),
                null, null, ipLookup, null, null, null, null))
                .singleElement()
                .satisfies(detail -> assertThat(detail.getMatchResult()).isEqualTo("PASS"));
        verifyNoInteractions(successReservationService);
    }

    @Test
    void shouldMatchMpgsThreeDsRuleAboveInclusiveUsdThreshold() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        RiskRuleSnapshotRow rule = new RiskRuleSnapshotRow();
        rule.setRuleId(30L);
        rule.setChannelCode("MPGS");
        rule.setPaymentMethod("BANK_CARD");
        rule.setCardBrand("ALL");
        rule.setAmountMatchType("GE");
        rule.setAmountMin(new BigDecimal("30.00"));
        rule.setCurrency("USD");
        rule.setRiskCondition("ANY");
        rule.setTriggerAction("FORCE_3DS");
        rule.setDecisionAction("REQUIRE_3DS");
        when(mapper.selectActiveThreeDsSnapshotRows(
                eq("M202607290001"), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(rule));
        DefaultRiskListRuntimeRepository repository = snapshotRepository(mapper, redis);

        assertThat(repository.findThreeDsRule(
                "M202607290001",
                "MPGS",
                "BANK_CARD",
                "VISA",
                new BigDecimal("31.00"),
                "USD",
                "LOW"))
                .containsSame(rule);
    }

    @Test
    void shouldMatchMpgsThreeDsRuleAtInclusiveUsdThreshold() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        RiskRuleSnapshotRow rule = mpgsThreeDsRule();
        when(mapper.selectActiveThreeDsSnapshotRows(
                eq("M202607290001"), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(rule));
        DefaultRiskListRuntimeRepository repository = snapshotRepository(mapper, redis);

        assertThat(repository.findThreeDsRule(
                "M202607290001",
                "MPGS",
                "BANK_CARD",
                "VISA",
                new BigDecimal("30.00"),
                "USD",
                "LOW"))
                .containsSame(rule);
    }

    @Test
    void shouldNotMatchMpgsThreeDsRuleBelowUsdThreshold() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        when(mapper.selectActiveThreeDsSnapshotRows(
                eq("M202607290001"), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(mpgsThreeDsRule()));
        DefaultRiskListRuntimeRepository repository = snapshotRepository(mapper, redis);

        assertThat(repository.findThreeDsRule(
                "M202607290001",
                "MPGS",
                "BANK_CARD",
                "VISA",
                new BigDecimal("19.00"),
                "USD",
                "LOW"))
                .isEmpty();
    }

    @Test
    void shouldNotMatchMpgsThreeDsRuleForAnotherRoutedChannel() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        when(mapper.selectActiveThreeDsSnapshotRows(
                eq("M202607290001"), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(mpgsThreeDsRule()));
        DefaultRiskListRuntimeRepository repository = snapshotRepository(mapper, redis);

        assertThat(repository.findThreeDsRule(
                "M202607290001",
                "WPGJSON",
                "BANK_CARD",
                "VISA",
                new BigDecimal("31.00"),
                "USD",
                "LOW"))
                .isEmpty();
    }

    @Test
    void shouldRejectFrequencyRuleAboveConfiguredWindowCapBeforeRedisMutation() {
        log.info("测试频率规则窗口上限，关键输入: 86401 秒规则超过默认 86400 秒");
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RiskListMatch frequencyRule = new RiskListMatch();
        frequencyRule.setRuleId(26L);
        frequencyRule.setThresholdCount(5);
        frequencyRule.setTimeWindowSeconds(86_401);
        frequencyRule.setElementsJson("""
                {"elements":["ip"],"statisticDimension":"ELEMENT_COMBINATION","allowedCount":5}
                """);
        when(redis.get(anyString())).thenReturn(null);
        when(mapper.selectActiveFrequencyRules("M202607290001"))
                .thenReturn(List.of(frequencyRule));
        DefaultRiskListRuntimeRepository repository = repository(
                mapper,
                redis,
                redisTemplate,
                null
        );
        RiskRuntimeLookupValue ipLookup = new RiskRuntimeLookupValue();
        ipLookup.setMatchValueHash("ip-hash");

        List<RiskListMatch> details = repository.evaluateFrequencyRules(
                "M202607290001",
                cumulativeRequest("TXN-FREQUENCY-WINDOW-CAP", "10.000000"),
                null,
                null,
                ipLookup,
                null,
                null,
                null,
                null
        );

        assertThat(details).singleElement().satisfies(detail -> {
            assertThat(detail.getMatchResult()).isEqualTo("ERROR");
            assertThat(detail.getDecisionAction()).isEqualTo("REVIEW");
            assertThat(detail.getDecisionReason())
                    .contains("configuration is invalid");
        });
        verifyNoInteractions(redisTemplate);
        log.info("频率规则窗口上限验证完成，结果: Redis 写入前进入 REVIEW");
    }

    @Test
    void shouldUseCustomerAndDeviceLookupsInsteadOfUnrelatedRequestFields() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService redis = mock(RedisStringService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RiskListMatch frequencyRule = new RiskListMatch();
        frequencyRule.setRuleId(22L);
        frequencyRule.setThresholdCount(5);
        frequencyRule.setTimeWindowSeconds(3600);
        frequencyRule.setElementsJson("""
                {"elements":["customerId","deviceFingerprint"],"statisticDimension":"ANY_ELEMENT","allowedCount":5}
                """);
        when(redis.get(anyString())).thenReturn(null);
        when(mapper.selectActiveFrequencyRules("M202607290001")).thenReturn(List.of(frequencyRule));
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(1L);
        DefaultRiskListRuntimeRepository repository = repository(
                mapper, redis, redisTemplate, null);
        RiskPaymentEvaluateRequestDTO requestDTO = cumulativeRequest("TXN-FREQUENCY-002", "10.000000");
        requestDTO.setSubMerchantId("WRONG-CUSTOMER-ID");
        requestDTO.setRequestFingerprint("WRONG-DEVICE-FINGERPRINT");
        RiskRuntimeLookupValue customerLookup = new RiskRuntimeLookupValue();
        customerLookup.setMatchValueHash("customer-hash");
        RiskRuntimeLookupValue deviceLookup = new RiskRuntimeLookupValue();
        deviceLookup.setMatchValueHash("device-hash");

        assertThat(repository.findFrequencyRuleHit(
                "M202607290001", requestDTO, null, null, null, null, null,
                customerLookup, deviceLookup)).isEmpty();

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, org.mockito.Mockito.times(2)).execute(
                org.mockito.ArgumentMatchers.any(),
                keysCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("3600"));
        List<String> counterKeys = keysCaptor.getAllValues().stream()
                .map(keys -> String.valueOf(keys.get(0)))
                .toList();
        assertThat(counterKeys).allSatisfy(key -> assertThat(key)
                .startsWith("acquiring:test:risk:frequency:{")
                .endsWith("}:counter")
                .doesNotContain("WRONG-CUSTOMER-ID", "WRONG-DEVICE-FINGERPRINT"));
        assertThat(hashTag(counterKeys.get(0))).isNotEqualTo(hashTag(counterKeys.get(1)));
    }

    private DefaultRiskListRuntimeRepository repository(RiskRuntimeMapper mapper,
                                                        RedisStringService redis) {
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current("risk-runtime-rule"))
                .thenReturn(RedisCacheGenerationState.active("g-test"));
        return repository(mapper, redis, generationStore);
    }

    private DefaultRiskListRuntimeRepository snapshotRepository(RiskRuntimeMapper mapper,
                                                                RedisStringService redis) {
        RiskEvaluationProperties properties = new RiskEvaluationProperties();
        properties.setRuntimeEnabled(true);
        properties.setRuleCacheMode(RiskRuleCacheMode.SNAPSHOT);
        PaymentRedisProperties redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix("acquiring:test");
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(generationStore.current("risk-runtime-rule"))
                .thenReturn(RedisCacheGenerationState.active("g-test"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        return new DefaultRiskListRuntimeRepository(
                provider(mapper), provider(redis), provider(generationStore), provider(redisTemplate),
                provider(null), provider(null), properties, redisProperties);
    }

    private RiskRuleSnapshotRow sourceUrlSnapshotRow(long ruleId, String sourceHost, Boolean runtimeAllowed) {
        RiskRuleSnapshotRow row = new RiskRuleSnapshotRow();
        row.setRuleId(ruleId);
        row.setFunctionCode("sourceUrl");
        row.setFunctionName("商户来源网址限定");
        row.setHitElement("sourceUrl");
        row.setSourceHost(sourceHost);
        row.setRuntimeAllowed(runtimeAllowed);
        return row;
    }

    private RiskRuleSnapshotRow mpgsThreeDsRule() {
        RiskRuleSnapshotRow rule = new RiskRuleSnapshotRow();
        rule.setRuleId(30L);
        rule.setChannelCode("MPGS");
        rule.setPaymentMethod("BANK_CARD");
        rule.setCardBrand("ALL");
        rule.setAmountMatchType("GE");
        rule.setAmountMin(new BigDecimal("30.00"));
        rule.setCurrency("USD");
        rule.setRiskCondition("ANY");
        rule.setTriggerAction("FORCE_3DS");
        rule.setDecisionAction("REQUIRE_3DS");
        return rule;
    }

    private DefaultRiskListRuntimeRepository repository(RiskRuntimeMapper mapper,
                                                        RedisStringService redis,
                                                        RedisCacheGenerationStore generationStore) {
        RiskEvaluationProperties properties = new RiskEvaluationProperties();
        properties.setCacheHitTtlSeconds(300);
        properties.setCacheMissTtlSeconds(60);
        PaymentRedisProperties redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix("acquiring:test");
        return new DefaultRiskListRuntimeRepository(
                provider(mapper),
                provider(redis),
                provider(generationStore),
                provider(null),
                provider(null),
                provider(null),
                properties,
                redisProperties);
    }

    private DefaultRiskListRuntimeRepository repository(RiskRuntimeMapper mapper,
                                                        RedisStringService redis,
                                                        StringRedisTemplate redisTemplate,
                                                        MerchantLimitReservationStateService stateService) {
        return repository(mapper, redis, redisTemplate, stateService, null);
    }

    private DefaultRiskListRuntimeRepository repository(
            RiskRuntimeMapper mapper,
            RedisStringService redis,
            StringRedisTemplate redisTemplate,
            MerchantLimitReservationStateService stateService,
            FrequencySuccessReservationService frequencySuccessReservationService) {
        RiskEvaluationProperties properties = new RiskEvaluationProperties();
        properties.setRuntimeEnabled(true);
        properties.setCacheHitTtlSeconds(300);
        properties.setCacheMissTtlSeconds(60);
        PaymentRedisProperties redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix("acquiring:test");
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current("risk-runtime-rule"))
                .thenReturn(RedisCacheGenerationState.active("g-test"));
        return new DefaultRiskListRuntimeRepository(
                provider(mapper),
                provider(redis),
                provider(generationStore),
                provider(redisTemplate),
                provider(stateService),
                provider(frequencySuccessReservationService),
                provider(null),
                properties,
                redisProperties);
    }

    private RiskListMatch frequencyRule(long ruleId, int allowedCount, int successCount) {
        RiskListMatch rule = new RiskListMatch();
        rule.setRuleId(ruleId);
        rule.setThresholdCount(allowedCount);
        rule.setTimeWindowSeconds(3600);
        rule.setDecisionAction("REJECT");
        rule.setElementsJson("{\"elements\":[\"ip\"],"
                + "\"statisticDimension\":\"ELEMENT_COMBINATION\","
                + "\"allowedCount\":" + allowedCount + ","
                + "\"successCount\":" + successCount + "}");
        return rule;
    }

    /**
     * 创建指定数据库基线模式的测试配置，并同步打开对应生产切换门禁。
     *
     * @param baselineMode 数据库累计基线迁移模式
     * @return 可用于仓储测试的风控配置
     */
    private RiskEvaluationProperties cumulativeProperties(RiskBaselineMode baselineMode) {
        RiskEvaluationProperties properties = new RiskEvaluationProperties();
        properties.setRuntimeEnabled(true);
        properties.setBaselineMode(baselineMode);
        properties.setBaselineCutoverConfirmed(baselineMode == RiskBaselineMode.LIFECYCLE);
        return properties;
    }

    /**
     * 创建可观察数据库基线和 Redis 累计计数的仓储测试实例。
     *
     * @param mapper                风控主库 Mapper
     * @param redisTemplate         Redis 脚本执行器
     * @param properties            风控迁移配置
     * @param monitor               shadow 汇总监控器，允许为空
     * @return 风控运行时仓储
     */
    private DefaultRiskListRuntimeRepository repository(
            RiskRuntimeMapper mapper,
            StringRedisTemplate redisTemplate,
            RiskEvaluationProperties properties,
            RiskShadowComparisonMonitor monitor) {
        PaymentRedisProperties redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix("acquiring:test");
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current("risk-runtime-rule"))
                .thenReturn(RedisCacheGenerationState.active("g-test"));
        return new DefaultRiskListRuntimeRepository(
                provider(mapper),
                provider(mock(RedisStringService.class)),
                provider(generationStore),
                provider(redisTemplate),
                provider(null),
                provider(monitor),
                properties,
                redisProperties);
    }

    private String hashTag(String key) {
        int start = key.indexOf('{');
        int end = key.indexOf('}', start + 1);
        return key.substring(start + 1, end);
    }

    private RiskPaymentEvaluateRequestDTO cumulativeRequest(String transactionId, String amount) {
        RiskPaymentEvaluateRequestDTO requestDTO = new RiskPaymentEvaluateRequestDTO();
        requestDTO.setMerchantId("M202607290001");
        requestDTO.setMerchantOrderNo("ORDER-" + transactionId);
        requestDTO.setTransactionId(transactionId);
        requestDTO.setAmount(new BigDecimal(amount));
        requestDTO.setCurrency("USD");
        requestDTO.setTransactionDateTime(LocalDateTime.of(2026, 7, 29, 12, 0));
        return requestDTO;
    }

    private RiskListMatch cumulativeRule(long ruleId, String limitType, String amountLimit) {
        RiskListMatch rule = new RiskListMatch();
        rule.setRuleId(ruleId);
        rule.setModuleType("RULE");
        rule.setFunctionCode("merchantLimit");
        rule.setFunctionName("商户交易限额管理");
        rule.setHitElement(limitType);
        rule.setRiskLevel("HIGH");
        rule.setDecisionAction("REJECT");
        rule.setAmountLimit(new BigDecimal(amountLimit));
        return rule;
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
