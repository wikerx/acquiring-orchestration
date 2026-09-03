package com.scott.payment.payment.service.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeCurrencyPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.PercentageBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.RefundFeeReturnPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReservePolicySnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveRefundPolicy;
import com.scott.payment.payment.entity.MerchantFeeVersionPointerDO;
import com.scott.payment.payment.service.MerchantFeeVersionQueryService;
import com.scott.payment.payment.service.MerchantFeeVersionSnapshotService;
import com.scott.payment.payment.service.dto.FrozenMerchantFeeVersionSnapshotDTO;
import com.scott.payment.payment.service.dto.MerchantFeeVersionCacheEntryDTO;
import com.scott.payment.payment.service.dto.MerchantFeeVersionConfigurationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

import static com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.CURRENT_SCHEMA_VERSION;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantFeeVersionSnapshotService
 * @date : 2026-08-25 22:45
 * @email : scott_x@163.com
 * @description : 动作费用冻结默认实现，主库读取 ACTIVE 指针、Redis/Slave/Master 读取明确版本，并以规范化 JSON SHA-256 防止历史配置漂移。
 * @status : create
 */
@Slf4j
@Service
public class DefaultMerchantFeeVersionSnapshotService implements MerchantFeeVersionSnapshotService {

    /**
     * {@code CACHE_DOMAIN}，表示远程服务主机、商户域名或渠道访问域名。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CACHE_DOMAIN = "fee";
    /**
     * {@code VERSION_CACHE_BUSINESS}，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String VERSION_CACHE_BUSINESS = "version";
    /**
     * {@code VERSION_MISS_CACHE_BUSINESS}，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String VERSION_MISS_CACHE_BUSINESS = "version-miss";
    /**
     * {@code VERSION_CACHE_BASE_TTL}，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final Duration VERSION_CACHE_BASE_TTL = Duration.ofDays(30);
    /**
     * {@code VERSION_CACHE_MAX_JITTER_SECONDS}，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long VERSION_CACHE_MAX_JITTER_SECONDS = Duration.ofDays(1).toSeconds();
    /**
     * {@code VERSION_MISS_MIN_TTL_SECONDS}，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long VERSION_MISS_MIN_TTL_SECONDS = 30L;
    /**
     * {@code VERSION_MISS_MAX_TTL_SECONDS}，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long VERSION_MISS_MAX_TTL_SECONDS = 60L;
    /**
     * {@code MISS_MARKER}常量，统一 {@code DefaultMerchantFeeVersionSnapshotService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String MISS_MARKER = "1";

    private final MerchantFeeVersionQueryService queryService;
    private final StringRedisTemplate redisTemplate;
    private final PaymentRedisKeyResolver keyResolver;
    private final ObjectMapper canonicalMapper;
    private final LongSupplier randomLongSupplier;

    /**
     * 创建费用快照服务；Redis 只承担减压，版本事实和当前指针始终由数据库约束。
     *
     * @param queryService 主从库费用版本只读边界
     * @param redisTemplate 不可变版本 JSON 缓存
     * @param keyResolver 环境隔离 Redis Key 解析器
     * @param objectMapper 平台 Jackson 配置
     */
    @Autowired
    public DefaultMerchantFeeVersionSnapshotService(MerchantFeeVersionQueryService queryService,
                                                    StringRedisTemplate redisTemplate,
                                                    PaymentRedisKeyResolver keyResolver,
                                                    ObjectMapper objectMapper) {
        this(queryService, redisTemplate, keyResolver, objectMapper,
                () -> ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
    }

    /** 包级测试构造器，允许固定 TTL 抖动源而不改变生产缓存协议。 */
    DefaultMerchantFeeVersionSnapshotService(MerchantFeeVersionQueryService queryService,
                                             StringRedisTemplate redisTemplate,
                                             PaymentRedisKeyResolver keyResolver,
                                             ObjectMapper objectMapper,
                                             LongSupplier randomLongSupplier) {
        this.queryService = Objects.requireNonNull(queryService, "fee version query service is required");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "Redis template is required");
        this.keyResolver = Objects.requireNonNull(keyResolver, "Redis key resolver is required");
        this.canonicalMapper = canonicalMapper(Objects.requireNonNull(objectMapper, "ObjectMapper is required"));
        this.randomLongSupplier = Objects.requireNonNull(randomLongSupplier, "random source is required");
    }

    /** {@inheritDoc} */
    @Override
    public FrozenMerchantFeeVersionSnapshotDTO freezeActiveVersion(String merchantId,
                                                                   LocalDateTime pricingLockTime) {
        if (!StringUtils.hasText(merchantId) || pricingLockTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(),
                    "merchantId and pricingLockTime are required");
        }
        String normalizedMerchantId = merchantId.trim();
        MerchantFeeVersionPointerDO pointer = queryService.findActivePointerFromMaster(normalizedMerchantId);
        requireValidPointer(pointer);

        MerchantFeeVersionConfigurationDTO configuration = readCachedVersion(pointer, normalizedMerchantId);
        if (configuration != null) {
            try {
                return freeze(configuration, pricingLockTime.truncatedTo(ChronoUnit.MILLIS));
            } catch (IllegalArgumentException exception) {
                log.warn("event: FEE_VERSION_CACHE_CONTENT_INVALID feePlanVersionId: {} exceptionType: {}",
                        pointer.getFeePlanVersionId(), exception.getClass().getSimpleName());
            }
        }
        if (hasMissingMarker(pointer.getFeePlanVersionId())) {
            throw unavailable();
        }
        configuration = loadImmutableVersion(pointer, normalizedMerchantId);
        validateConfigurationIdentity(configuration, pointer, normalizedMerchantId);
        try {
            FrozenMerchantFeeVersionSnapshotDTO frozen =
                    freeze(configuration, pricingLockTime.truncatedTo(ChronoUnit.MILLIS));
            writeCachedVersion(configuration);
            return frozen;
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND.getCode(),
                    "Merchant active fee configuration is incomplete", exception);
        }
    }

    /** Redis 异常、格式损坏或摘要不一致均显式记录并回源数据库。 */
    private MerchantFeeVersionConfigurationDTO readCachedVersion(MerchantFeeVersionPointerDO pointer,
                                                                 String merchantId) {
        try {
            String value = valueOperations().get(versionKey(pointer.getFeePlanVersionId()));
            if (!StringUtils.hasText(value)) {
                return null;
            }
            MerchantFeeVersionCacheEntryDTO entry = canonicalMapper.readValue(
                    value, MerchantFeeVersionCacheEntryDTO.class);
            if (entry.configuration() == null
                    || !constantTimeEquals(entry.payloadHash(), sha256(canonicalJson(entry.configuration())))) {
                log.warn("event: FEE_VERSION_CACHE_HASH_MISMATCH feePlanVersionId: {}",
                        pointer.getFeePlanVersionId());
                return null;
            }
            validateConfigurationIdentity(entry.configuration(), pointer, merchantId);
            return entry.configuration();
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("event: FEE_VERSION_CACHE_READ_FAILED feePlanVersionId: {} exceptionType: {}",
                    pointer.getFeePlanVersionId(), exception.getClass().getSimpleName());
            return null;
        }
    }

    /** Slave 缺失、延迟或结构异常时按相同版本 ID 回源 Master，禁止改查当前最新版本。 */
    private MerchantFeeVersionConfigurationDTO loadImmutableVersion(MerchantFeeVersionPointerDO pointer,
                                                                    String merchantId) {
        MerchantFeeVersionConfigurationDTO configuration = null;
        try {
            configuration = queryService.findVersionFromSlave(
                    merchantId, pointer.getFeePlanId(), pointer.getFeePlanVersionId());
            if (configuration != null) {
                validateConfigurationIdentity(configuration, pointer, merchantId);
            }
        } catch (RuntimeException exception) {
            log.warn("event: FEE_VERSION_SLAVE_FALLBACK feePlanVersionId: {} exceptionType: {}",
                    pointer.getFeePlanVersionId(), exception.getClass().getSimpleName());
            configuration = null;
        }
        if (configuration == null) {
            configuration = queryService.findVersionFromMaster(
                    merchantId, pointer.getFeePlanId(), pointer.getFeePlanVersionId());
        }
        if (configuration == null) {
            writeMissingMarker(pointer.getFeePlanVersionId());
            throw unavailable();
        }
        validateConfigurationIdentity(configuration, pointer, merchantId);
        return configuration;
    }

    /** 保存不可变版本配置，TTL 为 30 天加 0 至 24 小时随机秒。 */
    private void writeCachedVersion(MerchantFeeVersionConfigurationDTO configuration) {
        try {
            String payloadHash = sha256(canonicalJson(configuration));
            MerchantFeeVersionCacheEntryDTO entry =
                    new MerchantFeeVersionCacheEntryDTO(configuration, payloadHash);
            valueOperations().set(
                    versionKey(configuration.feePlanVersionId()),
                    canonicalJson(entry),
                    VERSION_CACHE_BASE_TTL.plusSeconds(boundedRandom(VERSION_CACHE_MAX_JITTER_SECONDS + 1)));
        } catch (RuntimeException exception) {
            log.warn("event: FEE_VERSION_CACHE_WRITE_FAILED feePlanVersionId: {} exceptionType: {}",
                    configuration.feePlanVersionId(), exception.getClass().getSimpleName());
        }
    }

    private boolean hasMissingMarker(Long feePlanVersionId) {
        try {
            return MISS_MARKER.equals(valueOperations().get(versionMissKey(feePlanVersionId)));
        } catch (RuntimeException exception) {
            log.warn("event: FEE_VERSION_MISS_CACHE_READ_FAILED feePlanVersionId: {} exceptionType: {}",
                    feePlanVersionId, exception.getClass().getSimpleName());
            return false;
        }
    }

    private void writeMissingMarker(Long feePlanVersionId) {
        try {
            long ttlSeconds = VERSION_MISS_MIN_TTL_SECONDS
                    + boundedRandom(VERSION_MISS_MAX_TTL_SECONDS - VERSION_MISS_MIN_TTL_SECONDS + 1);
            valueOperations().set(versionMissKey(feePlanVersionId), MISS_MARKER, Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException exception) {
            log.warn("event: FEE_VERSION_MISS_CACHE_WRITE_FAILED feePlanVersionId: {} exceptionType: {}",
                    feePlanVersionId, exception.getClass().getSimpleName());
        }
    }

    /** 使用现有费用币种口径生成动作级快照；不读取汇率，也不提前合并标签币种与 USD 组件。 */
    private FrozenMerchantFeeVersionSnapshotDTO freeze(MerchantFeeVersionConfigurationDTO configuration,
                                                       LocalDateTime pricingLockTime) {
        ReservePolicySnapshot reserve = new ReservePolicySnapshot(
                configuration.reserveRate(),
                ReserveBasis.LABEL_AMOUNT,
                configuration.reserveDelayUnit(),
                configuration.reserveDelayDays(),
                ReserveRefundPolicy.PROPORTIONAL_RETURN);
        FeeSnapshotHashMaterial material = new FeeSnapshotHashMaterial(
                CURRENT_SCHEMA_VERSION,
                configuration.merchantId(),
                configuration.feePlanId(),
                configuration.feePlanVersionId(),
                configuration.feePlanVersionNo(),
                pricingLockTime,
                configuration.settlementCurrency(),
                PercentageBasis.LABEL_AMOUNT,
                FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP,
                reserve,
                RefundFeeReturnPolicy.NONE,
                configuration.rules());
        String snapshotHash = sha256(canonicalJson(material));
        FeeVersionSnapshot snapshot = new FeeVersionSnapshot(
                material.schemaVersion(),
                material.merchantId(),
                material.feePlanId(),
                material.feePlanVersionId(),
                material.feePlanVersionNo(),
                material.pricingLockTime(),
                material.settlementCurrency(),
                material.percentageBasis(),
                material.feeCurrencyPolicy(),
                material.roundingMode(),
                material.reserve(),
                material.refundFeeReturnPolicy(),
                material.rules(),
                snapshotHash);
        return new FrozenMerchantFeeVersionSnapshotDTO(snapshot, canonicalJson(snapshot));
    }

    private void requireValidPointer(MerchantFeeVersionPointerDO pointer) {
        if (pointer == null
                || pointer.getFeePlanId() == null || pointer.getFeePlanId() < 1
                || pointer.getFeePlanVersionId() == null || pointer.getFeePlanVersionId() < 1
                || pointer.getFeePlanVersionNo() == null || pointer.getFeePlanVersionNo() < 1) {
            throw unavailable();
        }
    }

    private void validateConfigurationIdentity(MerchantFeeVersionConfigurationDTO configuration,
                                               MerchantFeeVersionPointerDO pointer,
                                               String merchantId) {
        if (configuration == null
                || !merchantId.equals(configuration.merchantId())
                || !Objects.equals(pointer.getFeePlanId(), configuration.feePlanId())
                || !Objects.equals(pointer.getFeePlanVersionId(), configuration.feePlanVersionId())
                || pointer.getFeePlanVersionNo() != configuration.feePlanVersionNo()
                || configuration.rules().isEmpty()) {
            throw unavailable();
        }
    }

    private ValueOperations<String, String> valueOperations() {
        return redisTemplate.opsForValue();
    }

    private String versionKey(Long feePlanVersionId) {
        return keyResolver.businessKey(CACHE_DOMAIN, VERSION_CACHE_BUSINESS,
                String.valueOf(feePlanVersionId));
    }

    private String versionMissKey(Long feePlanVersionId) {
        return keyResolver.businessKey(CACHE_DOMAIN, VERSION_MISS_CACHE_BUSINESS,
                String.valueOf(feePlanVersionId));
    }

    private long boundedRandom(long bound) {
        return Math.floorMod(randomLongSupplier.getAsLong(), bound);
    }

    private String canonicalJson(Object value) {
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "Fee snapshot JSON serialization failed", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "SHA-256 digest unavailable", exception);
        }
    }

    /**
     * 使用常量时间摘要比较校验费用快照哈希，避免普通字符串比较泄露时序差异。
     * @param expected 待比较的期望值和实际值；敏感摘要必须使用常量时间比较
     * @param actual 待比较的期望值和实际值；敏感摘要必须使用常量时间比较
     * @return 当前业务条件成立时返回 true，否则返回 false
     */
    private boolean constantTimeEquals(String expected, String actual) {
        return expected != null && MessageDigest.isEqual(
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

    private ServiceException unavailable() {
        return new ServiceException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND.getCode(),
                "Merchant active fee configuration is unavailable");
    }

    private record FeeSnapshotHashMaterial(
            int schemaVersion,
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
            java.util.List<com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot> rules) {
    }
}
