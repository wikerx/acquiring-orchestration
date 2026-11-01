package com.scott.payment.data.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantJwtKeyDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantResponseKeyDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.model.MerchantKeyMetadata;
import com.scott.payment.component.db.auth.service.MerchantKeyMetadataCacheService;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
import com.scott.payment.data.model.MerchantCallbackSecurityMaterial;
import com.scott.payment.data.service.MerchantCallbackSecurityMaterialProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 依据 Redis 中的非敏感 revision 从主库加载商户回调密钥，并在当前 JVM 内做有界短时缓存。
 *
 * <p>JWT Secret 和响应公钥正文禁止写入 Redis。每次回调先读取共享 revision，版本变化、TTL 到期
 * 或容量淘汰时按记录 ID 从主库重新加载，保证本地材料与已生效密钥版本一致。</p>
 */
@Service
public class JdbcMerchantCallbackSecurityMaterialProvider implements MerchantCallbackSecurityMaterialProvider {

    /**
     * 数据库启用标识。
     */
    private static final int ENABLED = 1;
    /**
     * 数据库未删除标识。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 共享非敏感密钥版本缓存。
     */
    private final MerchantKeyMetadataCacheService metadataCacheService;
    /**
     * 商户 JWT 密钥数据访问器，仅在本地缓存未命中时查询主库。
     */
    private final BaseMerchantJwtKeyMapper jwtKeyMapper;
    /**
     * 商户响应公钥数据访问器，仅在本地缓存未命中时查询主库。
     */
    private final BaseMerchantResponseKeyMapper responseKeyMapper;
    /**
     * 商户通知和本地敏感材料缓存边界配置。
     */
    private final DataMerchantNotificationProperties properties;
    /**
     * 单调时钟，防止系统时间回拨延长敏感材料驻留。
     */
    private final LongSupplier nanoTime;
    /**
     * 商户号和 revision 维度的本地敏感材料。
     */
    private final ConcurrentHashMap<CacheKey, CacheEntry> entries = new ConcurrentHashMap<>();
    /**
     * 最近访问顺序，用于容量淘汰。
     */
    private final AtomicLong accessSequence = new AtomicLong();
    /**
     * 只保护本地过期清理和容量淘汰，不包裹数据库查询。
     */
    private final Object evictionMonitor = new Object();

    /**
     * 创建生产环境商户回调安全材料提供器。
     *
     * @param metadataCacheService 非敏感密钥版本缓存
     * @param jwtKeyMapper 商户 JWT 密钥数据访问器
     * @param responseKeyMapper 商户响应公钥数据访问器
     * @param properties 商户通知和缓存边界配置
     */
    @Autowired
    public JdbcMerchantCallbackSecurityMaterialProvider(MerchantKeyMetadataCacheService metadataCacheService,
                                                         BaseMerchantJwtKeyMapper jwtKeyMapper,
                                                         BaseMerchantResponseKeyMapper responseKeyMapper,
                                                         DataMerchantNotificationProperties properties) {
        this(metadataCacheService, jwtKeyMapper, responseKeyMapper, properties, System::nanoTime);
    }

    /**
     * 创建可控制单调时钟的实例，供 TTL 边界测试使用。
     *
     * @param metadataCacheService 非敏感密钥版本缓存
     * @param jwtKeyMapper 商户 JWT 密钥数据访问器
     * @param responseKeyMapper 商户响应公钥数据访问器
     * @param properties 商户通知和缓存边界配置
     * @param nanoTime 单调时钟提供器
     */
    JdbcMerchantCallbackSecurityMaterialProvider(MerchantKeyMetadataCacheService metadataCacheService,
                                                  BaseMerchantJwtKeyMapper jwtKeyMapper,
                                                  BaseMerchantResponseKeyMapper responseKeyMapper,
                                                  DataMerchantNotificationProperties properties,
                                                  LongSupplier nanoTime) {
        this.metadataCacheService = Objects.requireNonNull(metadataCacheService, "metadataCacheService");
        this.jwtKeyMapper = Objects.requireNonNull(jwtKeyMapper, "jwtKeyMapper");
        this.responseKeyMapper = Objects.requireNonNull(responseKeyMapper, "responseKeyMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /**
     * 获取商户当前回调密钥；Redis 只参与 revision 判断，敏感正文只驻留当前 JVM。
     *
     * @param merchantId 商户号
     * @return 当前有效的商户回调 JWT 密钥和响应公钥
     */
    @Override
    @DS(DataSourceName.MASTER)
    public MerchantCallbackSecurityMaterial load(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "merchantId");
        }
        String normalizedMerchantId = merchantId.trim();
        MerchantKeyMetadata metadata = metadataCacheService.findKeyMetadata(normalizedMerchantId);
        if (metadata == null || !StringUtils.hasText(metadata.getRevision())
                || metadata.getJwtKeyId() == null || metadata.getResponseKeyId() == null) {
            throw materialNotConfigured();
        }
        CacheKey currentKey = new CacheKey(normalizedMerchantId, metadata.getRevision());
        long now = nanoTime.getAsLong();
        CacheEntry entry = entries.compute(currentKey, (key, existing) -> {
            if (existing != null && existing.expiresAtNanos() > now) {
                existing.touch(accessSequence.incrementAndGet());
                return existing;
            }
            MerchantCallbackSecurityMaterial loaded = loadFromMaster(normalizedMerchantId, metadata);
            return new CacheEntry(loaded, expiresAt(now), accessSequence.incrementAndGet());
        });
        removeOlderMerchantRevisions(currentKey);
        trimToCapacity(now);
        return entry.material();
    }

    /**
     * 按 revision 中的记录 ID 从主库读取当前有效密钥正文。
     *
     * @param merchantId 商户号
     * @param metadata 已缓存的非敏感密钥版本元数据
     * @return 当前有效的 JWT 密钥和响应公钥
     */
    private MerchantCallbackSecurityMaterial loadFromMaster(String merchantId, MerchantKeyMetadata metadata) {
        LocalDateTime now = LocalDateTime.now();
        BaseMerchantJwtKeyDO jwtKey = jwtKeyMapper.selectOne(Wrappers.<BaseMerchantJwtKeyDO>query()
                .select("merchant_key")
                .eq("id", metadata.getJwtKeyId())
                .eq("merchant_id", merchantId)
                .eq("enabled", ENABLED)
                .eq("deleted", NOT_DELETED)
                .and(wrapper -> wrapper.isNull("effective_time").or().le("effective_time", now))
                .and(wrapper -> wrapper.isNull("expire_time").or().gt("expire_time", now))
                .last("LIMIT 1"));
        BaseMerchantResponseKeyDO responseKey = responseKeyMapper.selectOne(
                Wrappers.<BaseMerchantResponseKeyDO>query()
                        .select("public_key_x509_base64")
                        .eq("id", metadata.getResponseKeyId())
                        .eq("merchant_id", merchantId)
                        .eq("enabled", ENABLED)
                        .eq("deleted", NOT_DELETED)
                        .last("LIMIT 1"));
        if (jwtKey == null || responseKey == null
                || !StringUtils.hasText(jwtKey.getMerchantKey())
                || !StringUtils.hasText(responseKey.getPublicKeyX509Base64())) {
            throw materialNotConfigured();
        }
        return new MerchantCallbackSecurityMaterial(
                jwtKey.getMerchantKey(), responseKey.getPublicKeyX509Base64());
    }

    /**
     * 新 revision 生效后清除同商户旧版本，避免旧密钥继续驻留。
     *
     * @param currentKey 当前商户和 revision
     */
    private void removeOlderMerchantRevisions(CacheKey currentKey) {
        entries.keySet().removeIf(key -> key.merchantId().equals(currentKey.merchantId())
                && !key.equals(currentKey));
    }

    /**
     * 清理过期条目并按最近访问顺序收敛到配置容量。
     *
     * @param now 当前单调时钟值
     */
    private void trimToCapacity(long now) {
        synchronized (evictionMonitor) {
            entries.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() <= now);
            while (entries.size() > properties.getSecurityMaterialCacheMaxEntries()) {
                entries.entrySet().stream()
                        .min(Comparator.comparingLong(entry -> entry.getValue().lastAccessOrder()))
                        .ifPresent(entry -> entries.remove(entry.getKey(), entry.getValue()));
            }
        }
    }

    /**
     * 使用单调时钟计算不溢出的本地缓存到期点。
     *
     * @param now 当前单调时钟值
     * @return 缓存到期单调时钟值
     */
    private long expiresAt(long now) {
        long ttlNanos = properties.getSecurityMaterialCacheTtl().toNanos();
        return Long.MAX_VALUE - now < ttlNanos ? Long.MAX_VALUE : now + ttlNanos;
    }

    /**
     * 获取当前缓存条目数，供容量边界测试使用。
     *
     * @return 当前 JVM 敏感材料缓存条目数
     */
    int entryCount() {
        return entries.size();
    }

    /**
     * 创建不包含密钥标识或数据库细节的统一配置缺失异常。
     *
     * @return 商户回调安全材料缺失异常
     */
    private ServiceException materialNotConfigured() {
        return new ServiceException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND.getCode(),
                "merchant callback security material is not configured");
    }

    /**
     * 本地敏感材料缓存键，revision 变化会形成独立条目。
     *
     * @param merchantId 商户号
     * @param revision 非敏感密钥版本摘要
     */
    private record CacheKey(String merchantId, String revision) {
    }

    /**
     * 带到期点和访问顺序的 JVM 敏感材料缓存条目。
     */
    private static final class CacheEntry {

        /**
         * 仅驻留当前 JVM 的 JWT 密钥和响应公钥。
         */
        private final MerchantCallbackSecurityMaterial material;
        /**
         * 缓存条目的单调时钟到期点，不可解释为业务时间。
         */
        private final long expiresAtNanos;
        /**
         * 缓存条目最近访问顺序，仅用于容量淘汰。
         */
        private volatile long lastAccessOrder;

        /**
         * 创建一个具有固定到期点的本地敏感材料缓存条目。
         *
         * @param material 商户回调安全材料
         * @param expiresAtNanos 单调时钟到期点
         * @param lastAccessOrder 最近访问顺序
         */
        private CacheEntry(MerchantCallbackSecurityMaterial material,
                           long expiresAtNanos,
                           long lastAccessOrder) {
            this.material = material;
            this.expiresAtNanos = expiresAtNanos;
            this.lastAccessOrder = lastAccessOrder;
        }

        /**
         * 返回仅供当前回调请求使用的安全材料。
         *
         * @return 商户回调安全材料
         */
        private MerchantCallbackSecurityMaterial material() {
            return material;
        }

        /**
         * 返回单调时钟到期点。
         *
         * @return 到期纳秒值
         */
        private long expiresAtNanos() {
            return expiresAtNanos;
        }

        /**
         * 返回容量淘汰使用的最近访问顺序。
         *
         * @return 最近访问序号
         */
        private long lastAccessOrder() {
            return lastAccessOrder;
        }

        /**
         * 更新最近访问顺序，不延长固定 TTL。
         *
         * @param accessOrder 新访问序号
         */
        private void touch(long accessOrder) {
            this.lastAccessOrder = accessOrder;
        }
    }
}
