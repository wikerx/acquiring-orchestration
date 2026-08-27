package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.dto.FeeVersionCacheEntryDTO;
import com.scott.payment.clearing.dto.FeeVersionConfigurationDTO;
import com.scott.payment.clearing.entity.ClearingTransactionMerchantSnapshotDO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.mapper.ClearingTransactionMerchantSnapshotMapper;
import com.scott.payment.clearing.service.FeeConfigurationSnapshotService;
import com.scott.payment.clearing.service.FeeVersionQueryService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeCurrencyPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.PercentageBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.RefundFeeReturnPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReservePolicySnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveRefundPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

import static com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.CURRENT_SCHEMA_VERSION;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultFeeConfigurationSnapshotService
 * @date : 2026-08-26 10:05
 * @email : scott_x@163.com
 * @description : 清分费用快照加载实现，验证动作冻结 JSON 后仅按其确切版本从 Redis、Slave、Master 降级，禁止查询最新费率。
 * @status : create
 */
@Slf4j
@Service
public class DefaultFeeConfigurationSnapshotService implements FeeConfigurationSnapshotService {

    private static final Duration CACHE_TTL = Duration.ofDays(30);
    private static final long CACHE_JITTER_BOUND = Duration.ofDays(1).toSeconds() + 1;

    private final ClearingTransactionMerchantSnapshotMapper snapshotMapper;
    private final FeeVersionQueryService queryService;
    private final StringRedisTemplate redisTemplate;
    private final PaymentRedisKeyResolver keyResolver;
    private final ObjectMapper canonicalMapper;
    private final LongSupplier randomLongSupplier;
    private final ClearingOperationalMetrics metrics;

    /**
     * 创建清分费用快照服务。
     *
     * @param snapshotMapper 动作冻结费用快照 Mapper
     * @param queryService 确切版本主从查询边界
     * @param redisTemplate 不可变版本缓存
     * @param keyResolver 环境隔离 Redis Key 解析器
     * @param objectMapper 平台 Jackson 配置
     */
    @Autowired
    public DefaultFeeConfigurationSnapshotService(ClearingTransactionMerchantSnapshotMapper snapshotMapper,
                                                  FeeVersionQueryService queryService,
                                                  StringRedisTemplate redisTemplate,
                                                  PaymentRedisKeyResolver keyResolver,
                                                  ObjectMapper objectMapper,
                                                  ClearingOperationalMetrics metrics) {
        this(snapshotMapper, queryService, redisTemplate, keyResolver, objectMapper, metrics,
                () -> ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
    }

    /** 包级测试构造器，用固定随机源验证 TTL 抖动而不改变生产缓存协议。 */
    DefaultFeeConfigurationSnapshotService(ClearingTransactionMerchantSnapshotMapper snapshotMapper,
                                           FeeVersionQueryService queryService,
                                           StringRedisTemplate redisTemplate,
                                           PaymentRedisKeyResolver keyResolver,
                                           ObjectMapper objectMapper,
                                           ClearingOperationalMetrics metrics,
                                           LongSupplier randomLongSupplier) {
        this.snapshotMapper = snapshotMapper;
        this.queryService = queryService;
        this.redisTemplate = redisTemplate;
        this.keyResolver = keyResolver;
        this.canonicalMapper = canonicalMapper(objectMapper);
        this.metrics = metrics;
        this.randomLongSupplier = randomLongSupplier;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public FeeVersionSnapshot load(String merchantId,
                                   String operationId,
                                   String transactionId,
                                   LocalDateTime transactionDateTime) {
        if (!StringUtils.hasText(merchantId) || !StringUtils.hasText(operationId)
                || !StringUtils.hasText(transactionId) || transactionDateTime == null) {
            throw new IllegalArgumentException("fee snapshot transaction identity is required");
        }
        ClearingTransactionMerchantSnapshotDO row = snapshotMapper.selectByTransaction(
                transactionId, transactionDateTime);
        validateRowIdentity(row, merchantId, operationId, transactionId, transactionDateTime);

        FeeVersionSnapshot frozen = readFrozen(row);
        if (frozen != null) {
            metrics.recordFeeSource("SNAPSHOT");
            return frozen;
        }
        FeeVersionConfigurationDTO configuration = readCached(row);
        if (configuration == null) {
            configuration = loadExactVersion(row);
            writeCached(configuration);
        } else {
            metrics.recordFeeSource("REDIS");
        }
        FeeVersionSnapshot rebuilt = freeze(configuration, row.getFeeSnapshotTime());
        validateSnapshotIdentity(rebuilt, row);
        if (!constantTimeEquals(row.getFeeSnapshotHash(), rebuilt.snapshotHash())) {
            throw failure(ClearingFailureCodeEnum.FEE_SNAPSHOT_HASH_MISMATCH,
                    "exact fee version does not reproduce frozen action hash");
        }
        return rebuilt;
    }

    /** {@inheritDoc} */
    @Override
    public FeeVersionSnapshot loadForRecalculation(String merchantId,
                                                   Long feePlanId,
                                                   Long feePlanVersionId,
                                                   LocalDateTime pricingLockTime) {
        if (!StringUtils.hasText(merchantId) || feePlanId == null || feePlanId < 1
                || feePlanVersionId == null || feePlanVersionId < 1 || pricingLockTime == null) {
            throw new IllegalArgumentException("recalculation fee version identity and pricing time are required");
        }
        FeeVersionConfigurationDTO configuration = null;
        try {
            configuration = queryService.findVersionFromSlave(merchantId, feePlanId, feePlanVersionId);
            if (configuration != null) {
                metrics.recordFeeSource("SLAVE");
            }
        } catch (RuntimeException exception) {
            log.warn("event: CLEARING_RECALCULATION_FEE_VERSION_SLAVE_FALLBACK feePlanVersionId: {} exceptionType: {}",
                    feePlanVersionId, exception.getClass().getSimpleName());
        }
        if (configuration == null) {
            configuration = queryService.findVersionFromMaster(merchantId, feePlanId, feePlanVersionId);
            if (configuration != null) {
                metrics.recordFeeSource("MASTER");
            }
        }
        if (configuration == null) {
            throw failure(ClearingFailureCodeEnum.FEE_VERSION_NOT_FOUND,
                    "target immutable fee version is unavailable");
        }
        if (!Objects.equals(configuration.merchantId(), merchantId)
                || !Objects.equals(configuration.feePlanId(), feePlanId)
                || !Objects.equals(configuration.feePlanVersionId(), feePlanVersionId)) {
            throw failure(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE,
                    "target immutable fee version identity is mismatched");
        }
        if (configuration.rules() == null || configuration.rules().isEmpty()) {
            throw failure(ClearingFailureCodeEnum.FEE_RULE_NOT_CONFIGURED,
                    "target immutable fee version contains no rules");
        }
        writeCached(configuration);
        return freeze(configuration, pricingLockTime);
    }

    private FeeVersionSnapshot readFrozen(ClearingTransactionMerchantSnapshotDO row) {
        if (!StringUtils.hasText(row.getFeeConfigSnapshotJson())) {
            return null;
        }
        try {
            FeeVersionSnapshot snapshot = canonicalMapper.readValue(
                    row.getFeeConfigSnapshotJson(), FeeVersionSnapshot.class);
            validateSnapshotIdentity(snapshot, row);
            String calculated = hash(snapshot);
            if (!constantTimeEquals(snapshot.snapshotHash(), calculated)
                    || !constantTimeEquals(row.getFeeSnapshotHash(), calculated)) {
                log.warn("event: CLEARING_FEE_SNAPSHOT_HASH_MISMATCH transactionId: {} feePlanVersionId: {}",
                        row.getTransactionId(), row.getFeePlanVersionId());
                return null;
            }
            return snapshot;
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("event: CLEARING_FEE_SNAPSHOT_JSON_INVALID transactionId: {} exceptionType: {}",
                    row.getTransactionId(), exception.getClass().getSimpleName());
            return null;
        }
    }

    private FeeVersionConfigurationDTO readCached(ClearingTransactionMerchantSnapshotDO row) {
        try {
            String value = redisTemplate.opsForValue().get(versionKey(row.getFeePlanVersionId()));
            if (!StringUtils.hasText(value)) {
                return null;
            }
            FeeVersionCacheEntryDTO entry = canonicalMapper.readValue(value, FeeVersionCacheEntryDTO.class);
            if (entry.configuration() == null
                    || !constantTimeEquals(entry.payloadHash(), sha256(canonicalJson(entry.configuration())))) {
                return null;
            }
            validateConfigurationIdentity(entry.configuration(), row);
            return entry.configuration();
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("event: CLEARING_FEE_VERSION_CACHE_READ_FAILED feePlanVersionId: {} exceptionType: {}",
                    row.getFeePlanVersionId(), exception.getClass().getSimpleName());
            return null;
        }
    }

    private FeeVersionConfigurationDTO loadExactVersion(ClearingTransactionMerchantSnapshotDO row) {
        FeeVersionConfigurationDTO configuration = null;
        try {
            configuration = queryService.findVersionFromSlave(
                    row.getMerchantId(), row.getFeePlanId(), row.getFeePlanVersionId());
            if (configuration != null) {
                validateConfigurationIdentity(configuration, row);
                metrics.recordFeeSource("SLAVE");
            }
        } catch (RuntimeException exception) {
            log.warn("event: CLEARING_FEE_VERSION_SLAVE_FALLBACK feePlanVersionId: {} exceptionType: {}",
                    row.getFeePlanVersionId(), exception.getClass().getSimpleName());
            configuration = null;
        }
        if (configuration == null) {
            configuration = queryService.findVersionFromMaster(
                    row.getMerchantId(), row.getFeePlanId(), row.getFeePlanVersionId());
            if (configuration != null) {
                metrics.recordFeeSource("MASTER");
            }
        }
        validateConfigurationIdentity(configuration, row);
        return configuration;
    }

    private FeeVersionSnapshot freeze(FeeVersionConfigurationDTO configuration, LocalDateTime lockTime) {
        ReservePolicySnapshot reserve = new ReservePolicySnapshot(
                configuration.reserveRate(), ReserveBasis.LABEL_AMOUNT,
                configuration.reserveDelayUnit(), configuration.reserveDelayDays(),
                ReserveRefundPolicy.PROPORTIONAL_RETURN);
        FeeSnapshotHashMaterial material = new FeeSnapshotHashMaterial(
                CURRENT_SCHEMA_VERSION, configuration.merchantId(), configuration.feePlanId(),
                configuration.feePlanVersionId(), configuration.feePlanVersionNo(), lockTime,
                configuration.settlementCurrency(), PercentageBasis.LABEL_AMOUNT,
                FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS, RoundingMode.HALF_UP,
                reserve, RefundFeeReturnPolicy.NONE, configuration.rules());
        String hash = sha256(canonicalJson(material));
        return new FeeVersionSnapshot(
                material.schemaVersion(), material.merchantId(), material.feePlanId(),
                material.feePlanVersionId(), material.feePlanVersionNo(), material.pricingLockTime(),
                material.settlementCurrency(), material.percentageBasis(), material.feeCurrencyPolicy(),
                material.roundingMode(), material.reserve(), material.refundFeeReturnPolicy(),
                material.rules(), hash);
    }

    private String hash(FeeVersionSnapshot snapshot) {
        FeeSnapshotHashMaterial material = new FeeSnapshotHashMaterial(
                snapshot.schemaVersion(), snapshot.merchantId(), snapshot.feePlanId(),
                snapshot.feePlanVersionId(), snapshot.feePlanVersionNo(), snapshot.pricingLockTime(),
                snapshot.settlementCurrency(), snapshot.percentageBasis(), snapshot.feeCurrencyPolicy(),
                snapshot.roundingMode(), snapshot.reserve(), snapshot.refundFeeReturnPolicy(), snapshot.rules());
        return sha256(canonicalJson(material));
    }

    private void writeCached(FeeVersionConfigurationDTO configuration) {
        try {
            FeeVersionCacheEntryDTO entry = new FeeVersionCacheEntryDTO(
                    configuration, sha256(canonicalJson(configuration)));
            long jitter = Math.floorMod(randomLongSupplier.getAsLong(), CACHE_JITTER_BOUND);
            redisTemplate.opsForValue().set(versionKey(configuration.feePlanVersionId()),
                    canonicalJson(entry), CACHE_TTL.plusSeconds(jitter));
        } catch (RuntimeException exception) {
            log.warn("event: CLEARING_FEE_VERSION_CACHE_WRITE_FAILED feePlanVersionId: {} exceptionType: {}",
                    configuration.feePlanVersionId(), exception.getClass().getSimpleName());
        }
    }

    private void validateRowIdentity(ClearingTransactionMerchantSnapshotDO row,
                                     String merchantId,
                                     String operationId,
                                     String transactionId,
                                     LocalDateTime transactionDateTime) {
        if (row == null) {
            throw failure(ClearingFailureCodeEnum.FEE_SNAPSHOT_MISSING,
                    "transaction fee snapshot is missing");
        }
        if (!Objects.equals(merchantId, row.getMerchantId())
                || !Objects.equals(operationId, row.getOperationId())
                || !Objects.equals(transactionId, row.getTransactionId())
                || !Objects.equals(transactionDateTime, row.getTransactionDateTime())) {
            throw failure(ClearingFailureCodeEnum.FEE_SNAPSHOT_HASH_MISMATCH,
                    "transaction fee snapshot identity is mismatched");
        }
        if (row.getFeePlanId() == null || row.getFeePlanVersionId() == null
                || row.getFeePlanVersionNo() == null || row.getFeeSnapshotTime() == null
                || !StringUtils.hasText(row.getFeeSnapshotHash())) {
            throw failure(ClearingFailureCodeEnum.FEE_SNAPSHOT_MISSING,
                    "transaction fee snapshot version fields are missing");
        }
    }

    private void validateSnapshotIdentity(FeeVersionSnapshot snapshot,
                                          ClearingTransactionMerchantSnapshotDO row) {
        if (snapshot == null || !Objects.equals(snapshot.merchantId(), row.getMerchantId())
                || !Objects.equals(snapshot.feePlanId(), row.getFeePlanId())
                || !Objects.equals(snapshot.feePlanVersionId(), row.getFeePlanVersionId())
                || snapshot.feePlanVersionNo() != row.getFeePlanVersionNo()
                || !Objects.equals(snapshot.pricingLockTime(), row.getFeeSnapshotTime())) {
            throw new IllegalStateException("fee snapshot identity does not match transaction snapshot columns");
        }
    }

    private void validateConfigurationIdentity(FeeVersionConfigurationDTO configuration,
                                               ClearingTransactionMerchantSnapshotDO row) {
        if (configuration == null) {
            throw failure(ClearingFailureCodeEnum.FEE_VERSION_NOT_FOUND,
                    "immutable fee version is unavailable");
        }
        if (!Objects.equals(configuration.merchantId(), row.getMerchantId())
                || !Objects.equals(configuration.feePlanId(), row.getFeePlanId())
                || !Objects.equals(configuration.feePlanVersionId(), row.getFeePlanVersionId())
                || configuration.feePlanVersionNo() != row.getFeePlanVersionNo()) {
            throw failure(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE,
                    "immutable fee version identity is mismatched");
        }
        if (configuration.rules() == null || configuration.rules().isEmpty()) {
            throw failure(ClearingFailureCodeEnum.FEE_RULE_NOT_CONFIGURED,
                    "immutable fee version contains no rules");
        }
    }

    private ClearingProcessingException failure(ClearingFailureCodeEnum code, String message) {
        return new ClearingProcessingException(code, message);
    }

    private String versionKey(Long versionId) {
        return keyResolver.businessKey("fee", "version", String.valueOf(versionId));
    }

    private String canonicalJson(Object value) {
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("fee snapshot JSON serialization failed", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest unavailable", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    @SuppressWarnings("deprecation")
    private ObjectMapper canonicalMapper(ObjectMapper source) {
        ObjectMapper copy = source.copy();
        copy.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        copy.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        copy.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        copy.getFactory().configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);
        return copy;
    }

    private record FeeSnapshotHashMaterial(int schemaVersion,
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
}
