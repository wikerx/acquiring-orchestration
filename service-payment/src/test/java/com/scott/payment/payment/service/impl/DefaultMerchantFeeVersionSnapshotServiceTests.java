package com.scott.payment.payment.service.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeCurrencyPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.PercentageBasis;
import com.scott.payment.finance.money.model.Money;
import com.scott.payment.payment.entity.MerchantFeeVersionPointerDO;
import com.scott.payment.payment.service.MerchantFeeVersionQueryService;
import com.scott.payment.payment.service.dto.FrozenMerchantFeeVersionSnapshotDTO;
import com.scott.payment.payment.service.dto.MerchantFeeVersionCacheEntryDTO;
import com.scott.payment.payment.service.dto.MerchantFeeVersionConfigurationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Payment action fee version cache and freezing tests. */
class DefaultMerchantFeeVersionSnapshotServiceTests {

    private static final String MERCHANT_ID = "200045";
    private static final String VERSION_KEY = "acquiring:test:fee:version:1008";
    private static final String VERSION_MISS_KEY = "acquiring:test:fee:version-miss:1008";

    private MerchantFeeVersionQueryService queryService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PaymentRedisProperties redisProperties;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        queryService = mock(MerchantFeeVersionQueryService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix("acquiring:test");
        when(queryService.findActivePointerFromMaster(MERCHANT_ID)).thenReturn(pointer());
    }

    @Test
    void springShouldSelectProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MerchantFeeVersionQueryService.class, () -> queryService);
            context.registerBean(StringRedisTemplate.class, () -> redisTemplate);
            context.registerBean(PaymentRedisKeyResolver.class, () -> redisProperties);
            context.registerBean(ObjectMapper.class, this::objectMapper);
            context.registerBean(DefaultMerchantFeeVersionSnapshotService.class);
            context.refresh();

            assertThat(context.getBean(DefaultMerchantFeeVersionSnapshotService.class)).isNotNull();
        }
    }

    @Test
    void shouldLoadSlaveOnCacheMissAndFreezeActionHash() {
        when(valueOperations.get(VERSION_KEY)).thenReturn(null);
        when(valueOperations.get(VERSION_MISS_KEY)).thenReturn(null);
        when(queryService.findVersionFromSlave(MERCHANT_ID, 1001L, 1008L))
                .thenReturn(configuration());
        DefaultMerchantFeeVersionSnapshotService service = service(86_400L);

        FrozenMerchantFeeVersionSnapshotDTO frozen = service.freezeActiveVersion(
                MERCHANT_ID, LocalDateTime.of(2026, 8, 25, 10, 0, 0, 123_456_789));

        assertThat(frozen.snapshot().merchantId()).isEqualTo(MERCHANT_ID);
        assertThat(frozen.snapshot().feePlanVersionId()).isEqualTo(1008L);
        assertThat(frozen.snapshot().pricingLockTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 25, 10, 0, 0, 123_000_000));
        assertThat(frozen.snapshot().percentageBasis()).isEqualTo(PercentageBasis.LABEL_AMOUNT);
        assertThat(frozen.snapshot().feeCurrencyPolicy())
                .isEqualTo(FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS);
        assertThat(frozen.snapshot().rules().get(0).calculationRule().fixedFeeUsd().currency())
                .isEqualTo("USD");
        assertThat(frozen.snapshot().snapshotHash()).matches("[0-9a-f]{64}");
        assertThat(frozen.snapshotJson())
                .contains("\"snapshotHash\"", "\"percentageRate\":2.30000000",
                        "\"currency\":\"USD\"", "\"amount\":0.30000000")
                .doesNotContain("card", "cvv", "billing");

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq(VERSION_KEY), anyString(), ttl.capture());
        assertThat(ttl.getValue()).isBetween(Duration.ofDays(30), Duration.ofDays(31));
        verify(queryService, never()).findVersionFromMaster(MERCHANT_ID, 1001L, 1008L);
    }

    @Test
    void shouldUseHashValidatedImmutableCacheWithoutReloadingRules() {
        when(valueOperations.get(VERSION_KEY)).thenReturn(null);
        when(valueOperations.get(VERSION_MISS_KEY)).thenReturn(null);
        when(queryService.findVersionFromSlave(MERCHANT_ID, 1001L, 1008L))
                .thenReturn(configuration());
        DefaultMerchantFeeVersionSnapshotService service = service(7L);
        service.freezeActiveVersion(MERCHANT_ID, lockTime());

        ArgumentCaptor<String> cachedJson = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(VERSION_KEY), cachedJson.capture(), org.mockito.ArgumentMatchers.any(Duration.class));
        clearInvocations(queryService, valueOperations);
        when(valueOperations.get(VERSION_KEY)).thenReturn(cachedJson.getValue());
        when(queryService.findActivePointerFromMaster(MERCHANT_ID)).thenReturn(pointer());

        FrozenMerchantFeeVersionSnapshotDTO cached =
                service.freezeActiveVersion(MERCHANT_ID, lockTime());

        assertThat(cached.snapshot().snapshotHash()).matches("[0-9a-f]{64}");
        verify(queryService, never()).findVersionFromSlave(MERCHANT_ID, 1001L, 1008L);
        verify(queryService, never()).findVersionFromMaster(MERCHANT_ID, 1001L, 1008L);
    }

    @Test
    void shouldFallbackToDatabaseWhenCachedPayloadIsCorrupted() {
        when(valueOperations.get(VERSION_KEY)).thenReturn("{\"configuration\":{},\"payloadHash\":\"bad\"}");
        when(valueOperations.get(VERSION_MISS_KEY)).thenReturn(null);
        when(queryService.findVersionFromSlave(MERCHANT_ID, 1001L, 1008L))
                .thenReturn(configuration());

        FrozenMerchantFeeVersionSnapshotDTO frozen = service(11L)
                .freezeActiveVersion(MERCHANT_ID, lockTime());

        assertThat(frozen.snapshot().feePlanVersionId()).isEqualTo(1008L);
        verify(queryService).findVersionFromSlave(MERCHANT_ID, 1001L, 1008L);
    }

    @Test
    void shouldFallbackToDatabaseWhenHashValidCacheHasInvalidBusinessContent() throws Exception {
        MerchantFeeVersionConfigurationDTO invalid = new MerchantFeeVersionConfigurationDTO(
                MERCHANT_ID,
                1001L,
                1008L,
                8,
                "usd",
                new BigDecimal("10.00000000"),
                "D",
                180,
                configuration().rules());
        when(valueOperations.get(VERSION_KEY)).thenReturn(cacheJson(invalid));
        when(valueOperations.get(VERSION_MISS_KEY)).thenReturn(null);
        when(queryService.findVersionFromSlave(MERCHANT_ID, 1001L, 1008L))
                .thenReturn(configuration());

        FrozenMerchantFeeVersionSnapshotDTO frozen = service(12L)
                .freezeActiveVersion(MERCHANT_ID, lockTime());

        assertThat(frozen.snapshot().settlementCurrency()).isEqualTo("USD");
        verify(queryService).findVersionFromSlave(MERCHANT_ID, 1001L, 1008L);
    }

    @Test
    void shouldFallbackToDatabaseWhenRedisReadFails() {
        when(valueOperations.get(VERSION_KEY)).thenThrow(new IllegalStateException("redis unavailable"));
        when(valueOperations.get(VERSION_MISS_KEY)).thenReturn(null);
        when(queryService.findVersionFromSlave(MERCHANT_ID, 1001L, 1008L))
                .thenReturn(configuration());

        FrozenMerchantFeeVersionSnapshotDTO frozen = service(13L)
                .freezeActiveVersion(MERCHANT_ID, lockTime());

        assertThat(frozen.snapshot().merchantId()).isEqualTo(MERCHANT_ID);
        verify(queryService).findVersionFromSlave(MERCHANT_ID, 1001L, 1008L);
    }

    @Test
    void shouldFallbackToMasterBySameVersionWhenSlaveFails() {
        when(valueOperations.get(VERSION_KEY)).thenReturn(null);
        when(valueOperations.get(VERSION_MISS_KEY)).thenReturn(null);
        when(queryService.findVersionFromSlave(MERCHANT_ID, 1001L, 1008L))
                .thenThrow(new IllegalStateException("replica lag"));
        when(queryService.findVersionFromMaster(MERCHANT_ID, 1001L, 1008L))
                .thenReturn(configuration());

        FrozenMerchantFeeVersionSnapshotDTO frozen = service(17L)
                .freezeActiveVersion(MERCHANT_ID, lockTime());

        assertThat(frozen.snapshot().feePlanVersionId()).isEqualTo(1008L);
        verify(queryService).findVersionFromMaster(MERCHANT_ID, 1001L, 1008L);
    }

    @Test
    void shouldFailClosedAndWriteShortMissMarkerWhenVersionIsMissing() {
        when(valueOperations.get(VERSION_KEY)).thenReturn(null);
        when(valueOperations.get(VERSION_MISS_KEY)).thenReturn(null);
        when(queryService.findVersionFromSlave(MERCHANT_ID, 1001L, 1008L)).thenReturn(null);
        when(queryService.findVersionFromMaster(MERCHANT_ID, 1001L, 1008L)).thenReturn(null);

        assertThatThrownBy(() -> service(29L).freezeActiveVersion(MERCHANT_ID, lockTime()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Merchant active fee configuration is unavailable");

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq(VERSION_MISS_KEY), eq("1"), ttl.capture());
        assertThat(ttl.getValue()).isBetween(Duration.ofSeconds(30), Duration.ofSeconds(60));
    }

    @Test
    void shouldFailClosedWhenMerchantHasNoActiveFeeVersion() {
        when(queryService.findActivePointerFromMaster(MERCHANT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service(31L).freezeActiveVersion(MERCHANT_ID, lockTime()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Merchant active fee configuration is unavailable");

        verify(queryService, never()).findVersionFromSlave(MERCHANT_ID, 1001L, 1008L);
    }

    @Test
    void shouldRejectIncompleteConfigurationBeforeWritingImmutableCache() {
        when(valueOperations.get(VERSION_KEY)).thenReturn(null);
        when(valueOperations.get(VERSION_MISS_KEY)).thenReturn(null);
        MerchantFeeVersionConfigurationDTO invalid = new MerchantFeeVersionConfigurationDTO(
                MERCHANT_ID,
                1001L,
                1008L,
                8,
                "usd",
                new BigDecimal("10.00000000"),
                "D",
                180,
                configuration().rules());
        when(queryService.findVersionFromSlave(MERCHANT_ID, 1001L, 1008L)).thenReturn(invalid);

        assertThatThrownBy(() -> service(37L).freezeActiveVersion(MERCHANT_ID, lockTime()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Merchant active fee configuration is incomplete");

        verify(valueOperations, never()).set(eq(VERSION_KEY), anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class));
    }

    private DefaultMerchantFeeVersionSnapshotService service(long randomValue) {
        ObjectMapper objectMapper = objectMapper();
        return new DefaultMerchantFeeVersionSnapshotService(
                queryService, redisTemplate, redisProperties, objectMapper, () -> randomValue);
    }

    @SuppressWarnings("deprecation")
    private String cacheJson(MerchantFeeVersionConfigurationDTO configuration)
            throws NoSuchAlgorithmException, JsonProcessingException {
        ObjectMapper mapper = objectMapper();
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.getFactory().configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);
        String payload = mapper.writeValueAsString(configuration);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(StandardCharsets.UTF_8)));
        return mapper.writeValueAsString(new MerchantFeeVersionCacheEntryDTO(configuration, hash));
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private MerchantFeeVersionPointerDO pointer() {
        MerchantFeeVersionPointerDO pointer = new MerchantFeeVersionPointerDO();
        pointer.setFeePlanId(1001L);
        pointer.setFeePlanVersionId(1008L);
        pointer.setFeePlanVersionNo(8);
        return pointer;
    }

    private MerchantFeeVersionConfigurationDTO configuration() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                2001L,
                FeeMode.STANDARD,
                new BigDecimal("2.30000000"),
                new Money(new BigDecimal("0.30000000"), "USD", 2),
                new Money(new BigDecimal("0.50000000"), "USD", 2),
                new Money(new BigDecimal("5.00000000"), "USD", 2),
                null);
        FeeRuleConfigurationSnapshot configuredRule = new FeeRuleConfigurationSnapshot(
                2001L,
                "TRANSACTION_FEE",
                "PAYMENT",
                "BANK_CARD",
                "VISA",
                "NONE",
                "SUCCESS",
                rule,
                List.of());
        return new MerchantFeeVersionConfigurationDTO(
                MERCHANT_ID,
                1001L,
                1008L,
                8,
                "USD",
                new BigDecimal("10.00000000"),
                "D",
                180,
                List.of(configuredRule));
    }

    private LocalDateTime lockTime() {
        return LocalDateTime.of(2026, 8, 25, 10, 0, 0, 123_000_000);
    }
}
