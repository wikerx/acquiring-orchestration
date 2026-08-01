package com.scott.payment.openapi.security;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.db.auth.model.MerchantKeyMetadata;
import com.scott.payment.component.db.auth.service.MerchantKeyMetadataCacheService;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.openapi.config.OpenApiMerchantSecretCacheProperties;
import com.scott.payment.openapi.entity.MerchantJwtKeyDO;
import com.scott.payment.openapi.entity.MerchantResponseKeyDO;
import com.scott.payment.openapi.entity.PlatformPayloadKeyDO;
import com.scott.payment.openapi.mapper.MerchantJwtKeyMapper;
import com.scott.payment.openapi.mapper.MerchantResponseKeyMapper;
import com.scott.payment.openapi.mapper.PlatformPayloadKeyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantSecretCache
 * @date : 2026-08-01 15:10
 * @email : scott_x@163.com
 * @description : OpenAPI 单实例短时敏感密钥缓存，以 merchant:keyMeta revision 隔离版本并从主库加载实际材料
 * @status : create
 *
 * <p>JWT Secret 和 RSA 私钥绝不进入 Redis。每次访问先读取共享非敏感 revision；版本变化、TTL 到期
 * 或容量淘汰都会重新读取主库。Redis 异常由元数据服务降级主库，不允许用旧 revision 静默放行。</p>
 */
@Service
public class OpenApiMerchantSecretCache {

    /** 数据库未删除标识。 */
    private static final int NOT_DELETED = 0;

    /** 数据库启用标识。 */
    private static final int ENABLED = 1;

    /** 共享非敏感密钥版本缓存。 */
    private final MerchantKeyMetadataCacheService metadataCacheService;

    /** 商户 JWT 密钥 Mapper，只在本地缓存未命中时读取主库。 */
    private final MerchantJwtKeyMapper jwtKeyMapper;

    /** 平台请求体密钥 Mapper，只在本地缓存未命中时读取主库。 */
    private final PlatformPayloadKeyMapper platformPayloadKeyMapper;

    /** 商户响应密钥 Mapper，只在本地缓存未命中时读取主库。 */
    private final MerchantResponseKeyMapper responseKeyMapper;

    /** RSA 公私钥解析组件。 */
    private final OpenApiPayloadCrypto payloadCrypto;

    /** 单实例密钥缓存边界配置。 */
    private final OpenApiMerchantSecretCacheProperties properties;

    /** 单调时钟，避免系统时间回拨延长敏感材料驻留。 */
    private final LongSupplier nanoTime;

    /** revision 维度敏感材料条目。 */
    private final ConcurrentHashMap<CacheKey, CacheEntry> entries = new ConcurrentHashMap<>();

    /** 最近访问顺序生成器，用于容量淘汰。 */
    private final AtomicLong accessSequence = new AtomicLong();

    /** 容量整理互斥对象，只保护淘汰选择，不包裹数据库读取。 */
    private final Object evictionMonitor = new Object();

    /**
     * 创建生产环境 OpenAPI 短时密钥缓存。
     *
     * @param metadataCacheService 共享非敏感密钥版本缓存
     * @param jwtKeyMapper 商户 JWT 密钥 Mapper
     * @param platformPayloadKeyMapper 平台请求体密钥 Mapper
     * @param responseKeyMapper 商户响应密钥 Mapper
     * @param payloadCrypto RSA 密钥解析组件
     * @param properties 本地缓存边界配置
     */
    @Autowired
    public OpenApiMerchantSecretCache(MerchantKeyMetadataCacheService metadataCacheService,
                                      MerchantJwtKeyMapper jwtKeyMapper,
                                      PlatformPayloadKeyMapper platformPayloadKeyMapper,
                                      MerchantResponseKeyMapper responseKeyMapper,
                                      OpenApiPayloadCrypto payloadCrypto,
                                      OpenApiMerchantSecretCacheProperties properties) {
        this(metadataCacheService, jwtKeyMapper, platformPayloadKeyMapper, responseKeyMapper,
                payloadCrypto, properties, System::nanoTime);
    }

    /** 创建可控制单调时钟的缓存实例，供 TTL 边界测试使用。 */
    OpenApiMerchantSecretCache(MerchantKeyMetadataCacheService metadataCacheService,
                               MerchantJwtKeyMapper jwtKeyMapper,
                               PlatformPayloadKeyMapper platformPayloadKeyMapper,
                               MerchantResponseKeyMapper responseKeyMapper,
                               OpenApiPayloadCrypto payloadCrypto,
                               OpenApiMerchantSecretCacheProperties properties,
                               LongSupplier nanoTime) {
        this.metadataCacheService = Objects.requireNonNull(metadataCacheService, "metadataCacheService");
        this.jwtKeyMapper = Objects.requireNonNull(jwtKeyMapper, "jwtKeyMapper");
        this.platformPayloadKeyMapper = Objects.requireNonNull(platformPayloadKeyMapper, "platformPayloadKeyMapper");
        this.responseKeyMapper = Objects.requireNonNull(responseKeyMapper, "responseKeyMapper");
        this.payloadCrypto = Objects.requireNonNull(payloadCrypto, "payloadCrypto");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /**
     * 获取当前商户 JWT Secret。
     *
     * @param merchantId 商户号
     * @return 当前启用的 JWT Secret；禁止记录日志或写入 Redis
     */
    @DS(DataSourceName.MASTER)
    public String getMerchantKey(String merchantId) {
        String secret = material(merchantId).merchantKey();
        if (!StringUtils.hasText(secret)) {
            throw new ApiException(ApiResultEnum.MERCHANT_SIGNING_KEY_NOT_CONFIGURED);
        }
        return secret;
    }

    /**
     * 获取平台用于解密当前商户请求体的 RSA 私钥。
     *
     * @param merchantId 商户号
     * @return 当前启用的 RSA 私钥；禁止记录日志或写入 Redis
     */
    @DS(DataSourceName.MASTER)
    public PrivateKey getPlatformPrivateKey(String merchantId) {
        PrivateKey privateKey = material(merchantId).platformPrivateKey();
        if (privateKey == null) {
            throw new ApiException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND, "platformPayloadKey");
        }
        return privateKey;
    }

    /**
     * 获取商户加密请求体时使用的平台 RSA 公钥。
     *
     * @param merchantId 商户号
     * @return 当前启用的平台 RSA 公钥
     */
    @DS(DataSourceName.MASTER)
    public PublicKey getPlatformPublicKey(String merchantId) {
        PublicKey publicKey = material(merchantId).platformPublicKey();
        if (publicKey == null) {
            throw new ApiException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND, "platformPayloadKey");
        }
        return publicKey;
    }

    /**
     * 获取平台加密响应时使用的商户 RSA 公钥。
     *
     * @param merchantId 商户号
     * @return 当前启用的商户响应 RSA 公钥
     */
    @DS(DataSourceName.MASTER)
    public PublicKey getMerchantResponsePublicKey(String merchantId) {
        PublicKey publicKey = material(merchantId).merchantResponsePublicKey();
        if (publicKey == null) {
            throw new ApiException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND, "merchantResponseKey");
        }
        return publicKey;
    }

    /**
     * 清除指定商户在当前 OpenAPI 实例内的全部敏感材料版本。
     *
     * <p>商户安全材料写事务调用该入口后，下一次请求必须依据最新非敏感 revision 回源主库。
     * 该操作只影响当前 JVM，不向 Redis 写入 Secret、私钥或公钥正文。</p>
     *
     * @param merchantId 商户号；为空时不执行操作
     */
    public void evictMerchant(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return;
        }
        String normalizedMerchantId = merchantId.trim();
        entries.keySet().removeIf(key -> key.merchantId().equals(normalizedMerchantId));
    }

    /**
     * 依据共享 revision 获取或加载本地敏感材料。
     *
     * @param merchantId 原始商户号
     * @return 当前 revision 对应的敏感材料集合
     */
    private SecretMaterial material(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiResultEnum.PARAM_MISSING, "merchantId");
        }
        String normalizedMerchantId = merchantId.trim();
        MerchantKeyMetadata metadata = metadataCacheService.findKeyMetadata(normalizedMerchantId);
        if (metadata == null || !StringUtils.hasText(metadata.getRevision())) {
            throw new ApiException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND, "keyMetadata");
        }
        CacheKey cacheKey = new CacheKey(normalizedMerchantId, metadata.getRevision());
        long now = nanoTime.getAsLong();
        CacheEntry entry = entries.compute(cacheKey, (key, existing) -> {
            if (existing != null && existing.expiresAtNanos() > now) {
                existing.touch(accessSequence.incrementAndGet());
                return existing;
            }
            SecretMaterial loaded = loadFromMaster(normalizedMerchantId, metadata);
            return new CacheEntry(loaded, expiresAt(now), accessSequence.incrementAndGet());
        });
        removeOlderMerchantRevisions(cacheKey);
        trimToCapacity(now);
        return entry.material();
    }

    /** 从主库读取当前 revision 指向的三类密钥材料并在内存中完成 RSA 解析。 */
    private SecretMaterial loadFromMaster(String merchantId, MerchantKeyMetadata metadata) {
        MerchantJwtKeyDO jwtKey = selectJwtKey(merchantId, metadata.getJwtKeyId());
        PlatformPayloadKeyDO platformKey = selectPlatformKey(merchantId, metadata.getPlatformKeyId());
        MerchantResponseKeyDO responseKey = selectResponseKey(merchantId, metadata.getResponseKeyId());
        return new SecretMaterial(
                jwtKey == null ? null : jwtKey.getMerchantKey(),
                platformKey == null || !StringUtils.hasText(platformKey.getPrivateKeyPkcs8Base64())
                        ? null : payloadCrypto.readPrivateKey(platformKey.getPrivateKeyPkcs8Base64()),
                platformKey == null || !StringUtils.hasText(platformKey.getPublicKeyX509Base64())
                        ? null : payloadCrypto.readPublicKey(platformKey.getPublicKeyX509Base64()),
                responseKey == null || !StringUtils.hasText(responseKey.getPublicKeyX509Base64())
                        ? null : payloadCrypto.readPublicKey(responseKey.getPublicKeyX509Base64())
        );
    }

    /** 按 Redis 元数据记录 ID 精确查询当前启用 JWT Secret。 */
    private MerchantJwtKeyDO selectJwtKey(String merchantId, Long keyId) {
        if (keyId == null) {
            return null;
        }
        return jwtKeyMapper.selectOne(Wrappers.<MerchantJwtKeyDO>query()
                .select("merchant_key")
                .eq("id", keyId)
                .eq("merchant_id", merchantId)
                .eq("enabled", ENABLED)
                .eq("deleted", NOT_DELETED)
                .last("LIMIT 1"));
    }

    /** 按 Redis 元数据记录 ID 精确查询当前启用平台请求体密钥对。 */
    private PlatformPayloadKeyDO selectPlatformKey(String merchantId, Long keyId) {
        if (keyId == null) {
            return null;
        }
        return platformPayloadKeyMapper.selectOne(Wrappers.<PlatformPayloadKeyDO>query()
                .select("public_key_x509_base64", "private_key_pkcs8_base64")
                .eq("id", keyId)
                .eq("merchant_id", merchantId)
                .eq("enabled", ENABLED)
                .eq("deleted", NOT_DELETED)
                .last("LIMIT 1"));
    }

    /** 按 Redis 元数据记录 ID 精确查询当前启用商户响应公钥。 */
    private MerchantResponseKeyDO selectResponseKey(String merchantId, Long keyId) {
        if (keyId == null) {
            return null;
        }
        return responseKeyMapper.selectOne(Wrappers.<MerchantResponseKeyDO>query()
                .select("public_key_x509_base64")
                .eq("id", keyId)
                .eq("merchant_id", merchantId)
                .eq("enabled", ENABLED)
                .eq("deleted", NOT_DELETED)
                .last("LIMIT 1"));
    }

    /** 新 revision 生效后清除同商户的旧版本材料，缩短敏感信息驻留时间。 */
    private void removeOlderMerchantRevisions(CacheKey currentKey) {
        entries.keySet().removeIf(key -> key.merchantId().equals(currentKey.merchantId()) && !key.equals(currentKey));
    }

    /** 清理过期条目并按最近访问顺序收敛到配置容量。 */
    private void trimToCapacity(long now) {
        synchronized (evictionMonitor) {
            entries.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() <= now);
            while (entries.size() > properties.getMaxEntries()) {
                entries.entrySet().stream()
                        .min(Comparator.comparingLong(entry -> entry.getValue().lastAccessOrder()))
                        .ifPresent(entry -> entries.remove(entry.getKey(), entry.getValue()));
            }
        }
    }

    /** 使用单调时钟计算不溢出的到期点。 */
    private long expiresAt(long now) {
        long ttlNanos = properties.getTtl().toNanos();
        return Long.MAX_VALUE - now < ttlNanos ? Long.MAX_VALUE : now + ttlNanos;
    }

    /** 获取当前条目数，供容量边界测试使用。 */
    int entryCount() {
        return entries.size();
    }

    /** 商户号与 Redis revision 组成的本地版本隔离键。 */
    private record CacheKey(String merchantId, String revision) {
    }

    /** 当前版本解析后的敏感材料，仅允许保留在 JVM 内存。 */
    private record SecretMaterial(String merchantKey,
                                  PrivateKey platformPrivateKey,
                                  PublicKey platformPublicKey,
                                  PublicKey merchantResponsePublicKey) {
    }

    /** 带单调时钟到期点和最近访问顺序的本地缓存条目。 */
    private static final class CacheEntry {

        /** 当前 revision 对应的解析后敏感材料，只驻留本 JVM。 */
        private final SecretMaterial material;

        /** 基于单调时钟的到期点，单位纳秒。 */
        private final long expiresAtNanos;

        /** 最近访问顺序，用于容量超限时选择最久未使用条目。 */
        private volatile long lastAccessOrder;

        /**
         * 创建本地敏感材料缓存条目。
         *
         * @param material 当前密钥 revision 对应的解析后材料
         * @param expiresAtNanos 基于单调时钟的到期点，单位纳秒
         * @param lastAccessOrder 初始访问顺序
         */
        private CacheEntry(SecretMaterial material, long expiresAtNanos, long lastAccessOrder) {
            this.material = material;
            this.expiresAtNanos = expiresAtNanos;
            this.lastAccessOrder = lastAccessOrder;
        }

        /** 返回当前 revision 对应的敏感材料。 */
        private SecretMaterial material() {
            return material;
        }

        /** 返回基于单调时钟的到期点，单位纳秒。 */
        private long expiresAtNanos() {
            return expiresAtNanos;
        }

        /** 返回最近访问顺序，用于容量淘汰排序。 */
        private long lastAccessOrder() {
            return lastAccessOrder;
        }

        /** 使用新的全局访问顺序标记当前条目刚被读取。 */
        private void touch(long accessOrder) {
            this.lastAccessOrder = accessOrder;
        }
    }
}
