package com.scott.payment.payment.service.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
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
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.SettlementPolicySnapshot;
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

import java.io.IOException;
import java.math.BigDecimal;
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
 * 支付动作费用版本的冻结服务。
 *
 * <p>当前 ACTIVE 指针始终从主库读取，确定版本后才允许按同一版本 ID 依次读取 Redis、从库和主库，
 * 禁止因从库延迟改查“最新版本”。冻结快照使用规范化 JSON 与 SHA-256 固化计费、保证金和结算规则，
 * 使后续退款、清分和审计可以复现交易发生时的费用口径。</p>
 */
@Slf4j
@Service
public class DefaultMerchantFeeVersionSnapshotService implements MerchantFeeVersionSnapshotService {

    /** 费用缓存的 Redis 业务域。 */
    private static final String CACHE_DOMAIN = "fee";
    /** 已发布不可变费用版本的缓存分区。 */
    private static final String VERSION_CACHE_BUSINESS = "version";
    /** 数据库缺失版本的短期负缓存分区，防止异常配置持续击穿主库。 */
    private static final String VERSION_MISS_CACHE_BUSINESS = "version-miss";
    /** 不可变版本缓存基础 TTL。数据库仍是最终事实来源。 */
    private static final Duration VERSION_CACHE_BASE_TTL = Duration.ofDays(30);
    /** 最多增加一天随机抖动，避免大量版本缓存同时过期。 */
    private static final long VERSION_CACHE_MAX_JITTER_SECONDS = Duration.ofDays(1).toSeconds();
    /** 缺失版本负缓存的最短有效期，单位秒。 */
    private static final long VERSION_MISS_MIN_TTL_SECONDS = 30L;
    /** 缺失版本负缓存的最长有效期，单位秒。 */
    private static final long VERSION_MISS_MAX_TTL_SECONDS = 60L;
    /** 负缓存只表达“版本不存在”，不缓存异常正文。 */
    private static final String MISS_MARKER = "1";

    private final MerchantFeeVersionQueryService queryService;
    private final StringRedisTemplate redisTemplate;
    private final PaymentRedisKeyResolver keyResolver;
    private final ObjectMapper canonicalMapper;
    private final ObjectMapper hashMapper;
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
        this.hashMapper = normalizedHashMapper(this.canonicalMapper);
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
        // ACTIVE 指针是本次交易选择费用版本的唯一入口，必须读取主库避免复制延迟选错版本。
        MerchantFeeVersionPointerDO pointer = queryService.findActivePointerFromMaster(normalizedMerchantId);
        requireValidPointer(pointer);

        MerchantFeeVersionConfigurationDTO configuration = readCachedVersion(pointer, normalizedMerchantId);
        if (configuration != null) {
            try {
                return freeze(configuration, pricingLockTime.truncatedTo(ChronoUnit.MILLIS));
            } catch (IllegalArgumentException exception) {
                log.warn("费用版本缓存业务内容无效, event: FEE_VERSION_CACHE_CONTENT_INVALID, feePlanVersionId: {}, exceptionType: {}",
                        pointer.getFeePlanVersionId(), exception.getClass().getSimpleName());
            }
        }
        if (hasMissingMarker(pointer.getFeePlanVersionId())) {
            throw unavailable();
        }
        // 缓存未命中后仍只查询指针锁定的版本，不允许切换到其他 ACTIVE 或最新版本。
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
            // 缓存摘要校验可识别内容损坏或历史版本被非预期改写，失败后必须回源数据库。
            if (entry.configuration() == null
                    || !constantTimeEquals(entry.payloadHash(), sha256(canonicalJson(entry.configuration())))) {
                log.warn("费用版本缓存摘要不一致, event: FEE_VERSION_CACHE_HASH_MISMATCH, feePlanVersionId: {}",
                        pointer.getFeePlanVersionId());
                return null;
            }
            validateConfigurationIdentity(entry.configuration(), pointer, merchantId);
            return entry.configuration();
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("费用版本缓存读取失败, event: FEE_VERSION_CACHE_READ_FAILED, feePlanVersionId: {}, exceptionType: {}",
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
            log.warn("费用版本从库读取降级, event: FEE_VERSION_SLAVE_FALLBACK, feePlanVersionId: {}, exceptionType: {}",
                    pointer.getFeePlanVersionId(), exception.getClass().getSimpleName());
            configuration = null;
        }
        if (configuration == null) {
            configuration = queryService.findVersionFromMaster(
                    merchantId, pointer.getFeePlanId(), pointer.getFeePlanVersionId());
        }
        if (configuration == null) {
            // 主从库均缺失属于配置完整性故障，写短期负缓存后 Fail Closed，禁止无费率继续交易。
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
            log.warn("费用版本缓存写入失败, event: FEE_VERSION_CACHE_WRITE_FAILED, feePlanVersionId: {}, exceptionType: {}",
                    configuration.feePlanVersionId(), exception.getClass().getSimpleName());
        }
    }

    private boolean hasMissingMarker(Long feePlanVersionId) {
        try {
            return MISS_MARKER.equals(valueOperations().get(versionMissKey(feePlanVersionId)));
        } catch (RuntimeException exception) {
            log.warn("费用版本负缓存读取失败, event: FEE_VERSION_MISS_CACHE_READ_FAILED, feePlanVersionId: {}, exceptionType: {}",
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
            log.warn("费用版本负缓存写入失败, event: FEE_VERSION_MISS_CACHE_WRITE_FAILED, feePlanVersionId: {}, exceptionType: {}",
                    feePlanVersionId, exception.getClass().getSimpleName());
        }
    }

    /** 使用现有费用币种口径生成动作级快照；不读取汇率，也不提前合并标签币种与 USD 组件。 */
    private FrozenMerchantFeeVersionSnapshotDTO freeze(MerchantFeeVersionConfigurationDTO configuration,
                                                       LocalDateTime pricingLockTime) {
        // 保证金基于标签金额，退款按比例释放；此处只冻结规则，不执行任何金额计算或汇率换算。
        ReservePolicySnapshot reserve = new ReservePolicySnapshot(
                configuration.reserveRate(),
                ReserveBasis.LABEL_AMOUNT,
                configuration.reserveDelayUnit(),
                configuration.reserveDelayDays(),
                ReserveRefundPolicy.PROPORTIONAL_RETURN);
        SettlementPolicySnapshot settlementPolicy = new SettlementPolicySnapshot(
                configuration.settlementDelayUnit(), configuration.initialSettlementDelayDays(),
                configuration.regularSettlementDelayDays(), configuration.settlementFrequency(),
                configuration.settlementFrequencyDay(), configuration.settlementFrequencyAnchorDate());
        // 百分比费用使用标签币种金额，固定费/上下限保持 USD 组件，禁止在快照阶段提前合币种。
        FeeSnapshotHashMaterial material = new FeeSnapshotHashMaterial(
                CURRENT_SCHEMA_VERSION,
                configuration.merchantId(),
                configuration.feePlanId(),
                configuration.feePlanVersionId(),
                configuration.feePlanVersionNo(),
                pricingLockTime,
                configuration.settlementCurrency(),
                settlementPolicy,
                PercentageBasis.LABEL_AMOUNT,
                FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP,
                reserve,
                RefundFeeReturnPolicy.NONE,
                configuration.rules());
        String snapshotHash = sha256(canonicalHashJson(material));
        // 哈希覆盖全部计费语义但不包含自身，生成后与相同材料一起写入不可变快照。
        FeeVersionSnapshot snapshot = new FeeVersionSnapshot(
                material.schemaVersion(),
                material.merchantId(),
                material.feePlanId(),
                material.feePlanVersionId(),
                material.feePlanVersionNo(),
                material.pricingLockTime(),
                material.settlementCurrency(),
                material.settlementPolicy(),
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
        // 无有效 ACTIVE 指针时不能猜测费用版本，支付创建必须 Fail Closed。
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
        // 每一层数据源都必须与主库指针完全一致，防止串商户、串费率方案或读取过期版本。
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

    /** v5 哈希忽略 BigDecimal 尾随零，避免 JSON 存储规范化改变同一数值的摘要。 */
    private String canonicalHashJson(Object value) {
        try {
            return hashMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "Fee snapshot hash JSON serialization failed", exception);
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
     * 使用常量时间比较校验费用版本摘要。
     *
     * @param expected 缓存中保存的摘要
     * @param actual   根据缓存配置重新计算的摘要
     * @return 两个摘要内容相同时返回 true
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

    private ObjectMapper normalizedHashMapper(ObjectMapper source) {
        ObjectMapper copy = source.copy();
        SimpleModule module = new SimpleModule();
        // 哈希材料按数值语义去除 BigDecimal 尾随零，避免 2.3 与 2.30000000 产生不同快照摘要。
        module.addSerializer(BigDecimal.class, new JsonSerializer<>() {
            @Override
            public void serialize(BigDecimal value,
                                  JsonGenerator generator,
                                  SerializerProvider serializers) throws IOException {
                generator.writeNumber(value.stripTrailingZeros());
            }
        });
        copy.registerModule(module);
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
            SettlementPolicySnapshot settlementPolicy,
            PercentageBasis percentageBasis,
            FeeCurrencyPolicy feeCurrencyPolicy,
            RoundingMode roundingMode,
            ReservePolicySnapshot reserve,
            RefundFeeReturnPolicy refundFeeReturnPolicy,
            java.util.List<com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot> rules) {
    }
}
