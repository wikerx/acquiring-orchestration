package com.scott.payment.clearing.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.dto.FeeVersionConfigurationDTO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.entity.ClearingTransactionMerchantSnapshotDO;
import com.scott.payment.clearing.mapper.ClearingTransactionMerchantSnapshotMapper;
import com.scott.payment.clearing.service.FeeVersionQueryService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeCurrencyPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.PercentageBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.RefundFeeReturnPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReservePolicySnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveRefundPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.SettlementPolicySnapshot;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultFeeConfigurationSnapshotServiceTest
 * @date : 2026-08-26 09:48
 * @email : scott_x@163.com
 * @description : 验证清分优先使用动作冻结费用快照，并在摘要和结构化版本身份一致时不查询当前费率或远程数据源。
 * @status : create
 */
class DefaultFeeConfigurationSnapshotServiceTest {

    @Test
    void loadShouldUseValidFrozenActionSnapshotWithoutFallback() throws Exception {
        ObjectMapper objectMapper = canonicalMapper();
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 26, 8, 30);
        LocalDateTime lockTime = LocalDateTime.of(2026, 8, 26, 8, 29);
        FeeRuleConfigurationSnapshot rule = new FeeRuleConfigurationSnapshot(
                101L, "TRANSACTION_FEE", "PAYMENT", "BANK_CARD", "VISA", "NONE", "SUCCESS",
                new FeeRuleSnapshot(101L, FeeMode.STANDARD, new BigDecimal("2.3"),
                        null, null, null, null), List.of());
        ReservePolicySnapshot reserve = new ReservePolicySnapshot(
                new BigDecimal("10"), ReserveBasis.LABEL_AMOUNT, "D", 180,
                ReserveRefundPolicy.PROPORTIONAL_RETURN);
        HashMaterial material = new HashMaterial(3, "M-1", 10L, 11L, 2, lockTime, "USD",
                PercentageBasis.LABEL_AMOUNT, FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP, reserve, RefundFeeReturnPolicy.NONE, List.of(rule));
        String hash = sha256(objectMapper.writeValueAsString(material));
        FeeVersionSnapshot snapshot = new FeeVersionSnapshot(
                3, "M-1", 10L, 11L, 2, lockTime, "USD",
                PercentageBasis.LABEL_AMOUNT, FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP, reserve, RefundFeeReturnPolicy.NONE, List.of(rule), hash);

        ClearingTransactionMerchantSnapshotDO row = new ClearingTransactionMerchantSnapshotDO();
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setFeePlanId(10L);
        row.setFeePlanVersionId(11L);
        row.setFeePlanVersionNo(2);
        row.setFeeSnapshotHash(hash);
        row.setFeeSnapshotTime(lockTime);
        row.setFeeConfigSnapshotJson(objectMapper.writeValueAsString(snapshot));
        row.setTransactionDateTime(transactionTime);

        ClearingTransactionMerchantSnapshotMapper snapshotMapper =
                mock(ClearingTransactionMerchantSnapshotMapper.class);
        FeeVersionQueryService queryService = mock(FeeVersionQueryService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PaymentRedisKeyResolver keyResolver = mock(PaymentRedisKeyResolver.class);
        when(snapshotMapper.selectByTransaction("TX-1", transactionTime)).thenReturn(row);
        DefaultFeeConfigurationSnapshotService service = new DefaultFeeConfigurationSnapshotService(
                snapshotMapper, queryService, redisTemplate, keyResolver, objectMapper,
                mock(ClearingOperationalMetrics.class), () -> 0L);

        FeeVersionSnapshot loaded = service.load("M-1", "OP-1", "TX-1", transactionTime);

        assertThat(loaded.snapshotHash()).isEqualTo(hash);
        assertThat(loaded.rules()).hasSize(1);
        verify(queryService, never()).findVersionFromSlave("M-1", 10L, 11L);
        verify(queryService, never()).findVersionFromMaster("M-1", 10L, 11L);
    }

    @Test
    void loadShouldAcceptV5SnapshotAfterJsonDecimalScaleNormalization() throws Exception {
        ObjectMapper objectMapper = canonicalMapper();
        ObjectMapper hashMapper = normalizedHashMapper();
        LocalDateTime transactionTime = LocalDateTime.of(2026, 9, 5, 18, 30);
        LocalDateTime lockTime = transactionTime.minusMinutes(1);
        SettlementPolicySnapshot settlementPolicy = settlementPolicy(lockTime);
        FeeRuleConfigurationSnapshot fullScaleRule = percentageRule("2.30000000");
        ReservePolicySnapshot fullScaleReserve = reserve("10.00000000");
        CurrentHashMaterial material = new CurrentHashMaterial(
                5, "M-1", 10L, 11L, 2, lockTime, "USD", settlementPolicy,
                PercentageBasis.LABEL_AMOUNT, FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP, fullScaleReserve, RefundFeeReturnPolicy.NONE,
                List.of(fullScaleRule));
        String hash = sha256(hashMapper.writeValueAsString(material));
        FeeVersionSnapshot normalizedJsonSnapshot = new FeeVersionSnapshot(
                5, "M-1", 10L, 11L, 2, lockTime, "USD", settlementPolicy,
                PercentageBasis.LABEL_AMOUNT, FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP, reserve("10"), RefundFeeReturnPolicy.NONE,
                List.of(percentageRule("2.3")), hash);
        ClearingTransactionMerchantSnapshotDO row = snapshotRow(
                transactionTime, lockTime, hash, objectMapper.writeValueAsString(normalizedJsonSnapshot));

        ClearingTransactionMerchantSnapshotMapper snapshotMapper =
                mock(ClearingTransactionMerchantSnapshotMapper.class);
        FeeVersionQueryService queryService = mock(FeeVersionQueryService.class);
        when(snapshotMapper.selectByTransaction("TX-1", transactionTime)).thenReturn(row);
        DefaultFeeConfigurationSnapshotService service = new DefaultFeeConfigurationSnapshotService(
                snapshotMapper, queryService, mock(StringRedisTemplate.class),
                mock(PaymentRedisKeyResolver.class), objectMapper, mock(ClearingOperationalMetrics.class),
                () -> 0L);

        FeeVersionSnapshot loaded = service.load("M-1", "OP-1", "TX-1", transactionTime);

        assertThat(loaded.snapshotHash()).isEqualTo(hash);
        verify(queryService, never()).findVersionFromSlave("M-1", 10L, 11L);
        verify(queryService, never()).findVersionFromMaster("M-1", 10L, 11L);
    }

    @Test
    void loadShouldVerifyLegacyV4ScaleMismatchAgainstExactImmutableVersionWithoutWarning() throws Exception {
        ObjectMapper objectMapper = canonicalMapper();
        LocalDateTime transactionTime = LocalDateTime.of(2026, 9, 5, 18, 31);
        LocalDateTime lockTime = transactionTime.minusMinutes(1);
        SettlementPolicySnapshot settlementPolicy = settlementPolicy(lockTime);
        FeeRuleConfigurationSnapshot fullScaleRule = percentageRule("2.30000000");
        ReservePolicySnapshot fullScaleReserve = reserve("10.00000000");
        CurrentHashMaterial material = new CurrentHashMaterial(
                4, "M-1", 10L, 11L, 2, lockTime, "USD", settlementPolicy,
                PercentageBasis.LABEL_AMOUNT, FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP, fullScaleReserve, RefundFeeReturnPolicy.NONE,
                List.of(fullScaleRule));
        String hash = sha256(objectMapper.writeValueAsString(material));
        FeeVersionSnapshot normalizedJsonSnapshot = new FeeVersionSnapshot(
                4, "M-1", 10L, 11L, 2, lockTime, "USD", settlementPolicy,
                PercentageBasis.LABEL_AMOUNT, FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP, reserve("10"), RefundFeeReturnPolicy.NONE,
                List.of(percentageRule("2.3")), hash);
        ClearingTransactionMerchantSnapshotDO row = snapshotRow(
                transactionTime, lockTime, hash, objectMapper.writeValueAsString(normalizedJsonSnapshot));
        FeeVersionConfigurationDTO configuration = new FeeVersionConfigurationDTO(
                "M-1", 10L, 11L, 2, "USD", "T", 1, 1, "DAILY", null,
                lockTime.toLocalDate(), new BigDecimal("10.00000000"), "D", 180,
                List.of(fullScaleRule));

        ClearingTransactionMerchantSnapshotMapper snapshotMapper =
                mock(ClearingTransactionMerchantSnapshotMapper.class);
        FeeVersionQueryService queryService = mock(FeeVersionQueryService.class);
        when(snapshotMapper.selectByTransaction("TX-1", transactionTime)).thenReturn(row);
        when(queryService.findVersionFromSlave("M-1", 10L, 11L)).thenReturn(configuration);
        DefaultFeeConfigurationSnapshotService service = new DefaultFeeConfigurationSnapshotService(
                snapshotMapper, queryService, mock(StringRedisTemplate.class),
                mock(PaymentRedisKeyResolver.class), objectMapper, mock(ClearingOperationalMetrics.class),
                () -> 0L);
        Logger logger = (Logger) LoggerFactory.getLogger(DefaultFeeConfigurationSnapshotService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            FeeVersionSnapshot loaded = service.load("M-1", "OP-1", "TX-1", transactionTime);

            assertThat(loaded.schemaVersion()).isEqualTo(4);
            assertThat(loaded.snapshotHash()).isEqualTo(hash);
            assertThat(appender.list).noneMatch(event -> event.getFormattedMessage()
                    .contains("CLEARING_FEE_SNAPSHOT_HASH_MISMATCH"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void loadShouldClassifyMissingActionSnapshotAsControlledFailure() {
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 26, 8, 30);
        ClearingTransactionMerchantSnapshotMapper snapshotMapper =
                mock(ClearingTransactionMerchantSnapshotMapper.class);
        DefaultFeeConfigurationSnapshotService service = new DefaultFeeConfigurationSnapshotService(
                snapshotMapper, mock(FeeVersionQueryService.class), mock(StringRedisTemplate.class),
                mock(PaymentRedisKeyResolver.class), canonicalMapper(), mock(ClearingOperationalMetrics.class),
                () -> 0L);

        assertThatThrownBy(() -> service.load("M-1", "OP-1", "TX-1", transactionTime))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.FEE_SNAPSHOT_MISSING));
    }

    @Test
    void loadShouldClassifyUnavailableExactVersionAsControlledFailure() {
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 26, 8, 30);
        ClearingTransactionMerchantSnapshotDO row = snapshotRow(transactionTime);
        ClearingTransactionMerchantSnapshotMapper snapshotMapper =
                mock(ClearingTransactionMerchantSnapshotMapper.class);
        FeeVersionQueryService queryService = mock(FeeVersionQueryService.class);
        when(snapshotMapper.selectByTransaction("TX-1", transactionTime)).thenReturn(row);
        DefaultFeeConfigurationSnapshotService service = new DefaultFeeConfigurationSnapshotService(
                snapshotMapper, queryService, mock(StringRedisTemplate.class),
                mock(PaymentRedisKeyResolver.class), canonicalMapper(), mock(ClearingOperationalMetrics.class),
                () -> 0L);

        assertThatThrownBy(() -> service.load("M-1", "OP-1", "TX-1", transactionTime))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.FEE_VERSION_NOT_FOUND));
    }

    @Test
    void loadShouldPropagateMasterTechnicalFailure() {
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 26, 8, 30);
        ClearingTransactionMerchantSnapshotDO row = snapshotRow(transactionTime);
        ClearingTransactionMerchantSnapshotMapper snapshotMapper =
                mock(ClearingTransactionMerchantSnapshotMapper.class);
        FeeVersionQueryService queryService = mock(FeeVersionQueryService.class);
        IllegalStateException databaseFailure = new IllegalStateException("master unavailable");
        when(snapshotMapper.selectByTransaction("TX-1", transactionTime)).thenReturn(row);
        when(queryService.findVersionFromMaster("M-1", 10L, 11L)).thenThrow(databaseFailure);
        DefaultFeeConfigurationSnapshotService service = new DefaultFeeConfigurationSnapshotService(
                snapshotMapper, queryService, mock(StringRedisTemplate.class),
                mock(PaymentRedisKeyResolver.class), canonicalMapper(), mock(ClearingOperationalMetrics.class),
                () -> 0L);

        assertThatThrownBy(() -> service.load("M-1", "OP-1", "TX-1", transactionTime))
                .isSameAs(databaseFailure);
    }

    private ClearingTransactionMerchantSnapshotDO snapshotRow(LocalDateTime transactionTime) {
        ClearingTransactionMerchantSnapshotDO row = new ClearingTransactionMerchantSnapshotDO();
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setFeePlanId(10L);
        row.setFeePlanVersionId(11L);
        row.setFeePlanVersionNo(2);
        row.setFeeSnapshotHash("a".repeat(64));
        row.setFeeSnapshotTime(transactionTime.minusMinutes(1));
        row.setTransactionDateTime(transactionTime);
        return row;
    }

    private ClearingTransactionMerchantSnapshotDO snapshotRow(LocalDateTime transactionTime,
                                                               LocalDateTime lockTime,
                                                               String hash,
                                                               String snapshotJson) {
        ClearingTransactionMerchantSnapshotDO row = snapshotRow(transactionTime);
        row.setFeeSnapshotHash(hash);
        row.setFeeSnapshotTime(lockTime);
        row.setFeeConfigSnapshotJson(snapshotJson);
        return row;
    }

    private FeeRuleConfigurationSnapshot percentageRule(String percentageRate) {
        return new FeeRuleConfigurationSnapshot(
                101L, "TRANSACTION_FEE", "PAYMENT", "BANK_CARD", "VISA", "NONE", "SUCCESS",
                new FeeRuleSnapshot(101L, FeeMode.STANDARD, new BigDecimal(percentageRate),
                        null, null, null, null), List.of());
    }

    private ReservePolicySnapshot reserve(String reserveRate) {
        return new ReservePolicySnapshot(
                new BigDecimal(reserveRate), ReserveBasis.LABEL_AMOUNT, "D", 180,
                ReserveRefundPolicy.PROPORTIONAL_RETURN);
    }

    private SettlementPolicySnapshot settlementPolicy(LocalDateTime lockTime) {
        return new SettlementPolicySnapshot("T", 1, 1, "DAILY", null, lockTime.toLocalDate());
    }

    @SuppressWarnings("deprecation")
    private ObjectMapper canonicalMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.getFactory().configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);
        return mapper;
    }

    private ObjectMapper normalizedHashMapper() {
        ObjectMapper mapper = canonicalMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(BigDecimal.class, new JsonSerializer<>() {
            @Override
            public void serialize(BigDecimal value,
                                  JsonGenerator generator,
                                  SerializerProvider serializers) throws IOException {
                generator.writeNumber(value.stripTrailingZeros());
            }
        });
        mapper.registerModule(module);
        return mapper;
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record HashMaterial(int schemaVersion,
                                String merchantId,
                                Long feePlanId,
                                Long feePlanVersionId,
                                int feePlanVersionNo,
                                LocalDateTime pricingLockTime,
                                String settlementCurrency,
                                PercentageBasis percentageBasis,
                                FeeCurrencyPolicy feeCurrencyPolicy,
                                RoundingMode roundingMode,
                                ReservePolicySnapshot reserve,
                                RefundFeeReturnPolicy refundFeeReturnPolicy,
                                List<FeeRuleConfigurationSnapshot> rules) {
    }

    private record CurrentHashMaterial(int schemaVersion,
                                       String merchantId,
                                       Long feePlanId,
                                       Long feePlanVersionId,
                                       int feePlanVersionNo,
                                       LocalDateTime pricingLockTime,
                                       String settlementCurrency,
                                       SettlementPolicySnapshot settlementPolicy,
                                       PercentageBasis percentageBasis,
                                       FeeCurrencyPolicy feeCurrencyPolicy,
                                       RoundingMode roundingMode,
                                       ReservePolicySnapshot reserve,
                                       RefundFeeReturnPolicy refundFeeReturnPolicy,
                                       List<FeeRuleConfigurationSnapshot> rules) {
    }
}
