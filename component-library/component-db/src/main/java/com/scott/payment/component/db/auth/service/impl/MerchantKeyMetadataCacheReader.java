package com.scott.payment.component.db.auth.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.auth.entity.BaseMerchantJwtKeyDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantResponseKeyDO;
import com.scott.payment.component.db.auth.entity.BasePlatformPayloadKeyDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.mapper.BasePlatformPayloadKeyMapper;
import com.scott.payment.component.db.auth.model.MerchantKeyMetadata;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantKeyMetadataCacheReader
 * @date : 2026-08-01 15:05
 * @email : scott_x@163.com
 * @description : 从主库加载当前密钥非敏感字段并维护 merchant:keyMeta 永久缓存，避免复制延迟回写旧安全版本
 * @status : create
 */
@Service
public class MerchantKeyMetadataCacheReader {

    /** 数据库未删除标识。 */
    private static final int NOT_DELETED = 0;

    /** 数据库启用标识。 */
    private static final int ENABLED = 1;

    /** 商户 JWT 密钥数据访问入口。 */
    private final BaseMerchantJwtKeyMapper jwtKeyMapper;

    /** 平台请求体密钥数据访问入口。 */
    private final BasePlatformPayloadKeyMapper platformPayloadKeyMapper;

    /** 商户响应密钥数据访问入口。 */
    private final BaseMerchantResponseKeyMapper responseKeyMapper;

    /**
     * 创建商户密钥元数据读取器。
     *
     * @param jwtKeyMapper 商户 JWT 密钥 Mapper
     * @param platformPayloadKeyMapper 平台请求体密钥 Mapper
     * @param responseKeyMapper 商户响应密钥 Mapper
     */
    public MerchantKeyMetadataCacheReader(BaseMerchantJwtKeyMapper jwtKeyMapper,
                                          BasePlatformPayloadKeyMapper platformPayloadKeyMapper,
                                          BaseMerchantResponseKeyMapper responseKeyMapper) {
        this.jwtKeyMapper = jwtKeyMapper;
        this.platformPayloadKeyMapper = platformPayloadKeyMapper;
        this.responseKeyMapper = responseKeyMapper;
    }

    /**
     * 读取永久缓存，未命中时从主库构建快照。
     *
     * <p>密钥版本属于安全配置，缓存重建固定读取主库，避免只读库复制延迟把旧 revision 写成永久值。</p>
     *
     * @param merchantId 已规范化商户号
     * @return 非敏感密钥元数据；未配置任何密钥时返回 null
     */
    @DS(DataSourceName.MASTER)
    @Cacheable(cacheNames = PaymentCacheNames.MERCHANT_KEY_METADATA,
            key = "#p0", unless = "#result == null")
    public MerchantKeyMetadata findCached(String merchantId) {
        return load(merchantId);
    }

    /**
     * 失效门禁存在或 Redis 状态未知时绕过缓存并读取主库。
     *
     * @param merchantId 已规范化商户号
     * @return 主库最新非敏感密钥元数据
     */
    @DS(DataSourceName.MASTER)
    public MerchantKeyMetadata findFresh(String merchantId) {
        return load(merchantId);
    }

    /**
     * 从主库重建并覆盖永久密钥元数据缓存。
     *
     * @param merchantId 已规范化商户号
     * @return 主库最新非敏感密钥元数据
     */
    @DS(DataSourceName.MASTER)
    @CachePut(cacheNames = PaymentCacheNames.MERCHANT_KEY_METADATA,
            key = "#p0", unless = "#result == null")
    public MerchantKeyMetadata refresh(String merchantId) {
        return load(merchantId);
    }

    /** 从三类当前启用密钥记录构造不含密钥正文的版本快照。 */
    private MerchantKeyMetadata load(String merchantId) {
        BaseMerchantJwtKeyDO jwtKey = selectJwtKey(merchantId);
        BasePlatformPayloadKeyDO platformKey = selectPlatformKey(merchantId);
        BaseMerchantResponseKeyDO responseKey = selectResponseKey(merchantId);
        if (jwtKey == null && platformKey == null && responseKey == null) {
            return null;
        }
        MerchantKeyMetadata metadata = new MerchantKeyMetadata();
        metadata.setMerchantId(merchantId);
        if (jwtKey != null) {
            metadata.setJwtKeyId(jwtKey.getId());
            metadata.setJwtKeyVersion(jwtKey.getKeyVersion());
            metadata.setJwtAlgorithm(jwtKey.getAlgorithm());
            metadata.setJwtExpiresSeconds(jwtKey.getExpiresSeconds());
            metadata.setJwtEffectiveTime(jwtKey.getEffectiveTime());
            metadata.setJwtModifiedTime(jwtKey.getGmtModified());
        }
        if (platformKey != null) {
            metadata.setPlatformKeyId(platformKey.getId());
            metadata.setPlatformAlgorithm(platformKey.getAlgorithm());
            metadata.setPlatformKeySize(platformKey.getKeySize());
            metadata.setPlatformModifiedTime(platformKey.getGmtModified());
        }
        if (responseKey != null) {
            metadata.setResponseKeyId(responseKey.getId());
            metadata.setResponseAlgorithm(responseKey.getAlgorithm());
            metadata.setResponseKeySize(responseKey.getKeySize());
            metadata.setResponseModifiedTime(responseKey.getGmtModified());
        }
        metadata.setRevision(revision(metadata));
        return metadata;
    }

    /** 查询当前启用 JWT 密钥的非敏感字段。 */
    private BaseMerchantJwtKeyDO selectJwtKey(String merchantId) {
        return jwtKeyMapper.selectOne(Wrappers.<BaseMerchantJwtKeyDO>query()
                .select("id", "merchant_id", "key_version", "algorithm", "expires_seconds",
                        "effective_time", "gmt_modified")
                .eq("merchant_id", merchantId)
                .eq("enabled", ENABLED)
                .eq("deleted", NOT_DELETED)
                .orderByDesc("effective_time")
                .last("LIMIT 1"));
    }

    /** 查询当前启用平台载荷密钥的非敏感字段。 */
    private BasePlatformPayloadKeyDO selectPlatformKey(String merchantId) {
        return platformPayloadKeyMapper.selectOne(Wrappers.<BasePlatformPayloadKeyDO>query()
                .select("id", "merchant_id", "algorithm", "key_size", "gmt_modified")
                .eq("merchant_id", merchantId)
                .eq("enabled", ENABLED)
                .eq("deleted", NOT_DELETED)
                .last("LIMIT 1"));
    }

    /** 查询当前启用商户响应密钥的非敏感字段。 */
    private BaseMerchantResponseKeyDO selectResponseKey(String merchantId) {
        return responseKeyMapper.selectOne(Wrappers.<BaseMerchantResponseKeyDO>query()
                .select("id", "merchant_id", "algorithm", "key_size", "gmt_modified")
                .eq("merchant_id", merchantId)
                .eq("enabled", ENABLED)
                .eq("deleted", NOT_DELETED)
                .orderByDesc("gmt_modified")
                .last("LIMIT 1"));
    }

    /**
     * 计算只依赖非敏感版本字段的稳定 revision。
     *
     * @param metadata 已组装的非敏感密钥元数据
     * @return 小写十六进制 SHA-256 revision
     */
    private String revision(MerchantKeyMetadata metadata) {
        String source = String.join("|",
                Objects.toString(metadata.getMerchantId(), ""),
                Objects.toString(metadata.getJwtKeyId(), ""),
                Objects.toString(metadata.getJwtKeyVersion(), ""),
                Objects.toString(metadata.getJwtModifiedTime(), ""),
                Objects.toString(metadata.getPlatformKeyId(), ""),
                Objects.toString(metadata.getPlatformModifiedTime(), ""),
                Objects.toString(metadata.getResponseKeyId(), ""),
                Objects.toString(metadata.getResponseModifiedTime(), ""));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for merchant key metadata revision", exception);
        }
    }
}
