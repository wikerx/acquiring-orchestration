package com.scott.payment.openapi.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.MerchantOpenApiCredential;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.RsaKeyMaterial;
import com.scott.payment.openapi.dto.security.OpenApiMerchantSecurityMaterialDTO;
import com.scott.payment.openapi.dto.security.OpenApiMerchantSecuritySeedDTO;
import com.scott.payment.openapi.entity.OpenApiMerchantInfoDO;
import com.scott.payment.openapi.entity.OpenApiMerchantJwtKeyDO;
import com.scott.payment.openapi.entity.OpenApiMerchantResponseKeyDO;
import com.scott.payment.openapi.entity.OpenApiPlatformPayloadKeyDO;
import com.scott.payment.openapi.mapper.OpenApiMerchantInfoMapper;
import com.scott.payment.openapi.mapper.OpenApiMerchantJwtKeyMapper;
import com.scott.payment.openapi.mapper.OpenApiMerchantResponseKeyMapper;
import com.scott.payment.openapi.mapper.OpenApiPlatformPayloadKeyMapper;
import com.scott.payment.openapi.service.OpenApiMerchantSecurityService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantSecurityServiceImpl
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 商户安全材料 MyBatisPlus 实现
 * @status : create
 */
@Primary
@Service
public class OpenApiMerchantSecurityServiceImpl implements OpenApiMerchantSecurityService {

    /**
     * 商户可交易状态。
     */
    private static final String MERCHANT_STATUS_ACTIVE = "ACTIVE";

    /**
     * 当前 JWT 标准算法。
     */
    private static final String JWT_ALGORITHM = "HS256";

    /**
     * 当前请求体加密算法说明。
     */
    private static final String PAYLOAD_ALGORITHM = "RSA-OAEP-256+A256GCM";

    /**
     * 默认 JWT 最大有效期，单位秒。
     */
    private static final long JWT_EXPIRES_SECONDS = 180L;

    /**
     * 默认 RSA 密钥位数。
     */
    private static final int RSA_KEY_SIZE = 2048;

    /**
     * 数据库未删除标识。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 数据库已启用标识。
     */
    private static final int ENABLED = 1;

    /**
     * 默认商户风险等级。
     */
    private static final String DEFAULT_RISK_LEVEL = "NORMAL";

    /**
     * 默认商户业务时区。
     */
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    /**
     * 默认结算币种。
     */
    private static final String DEFAULT_SETTLEMENT_CURRENCY = "USD";

    /**
     * 默认商户密钥版本。
     */
    private static final String DEFAULT_JWT_KEY_VERSION = "jwt-v1";

    /**
     * PEM 文本每行字符数。
     */
    private static final int PEM_LINE_LENGTH = 64;

    /**
     * PEM 私钥开始标识。
     */
    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";

    /**
     * PEM 私钥结束标识。
     */
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";

    /**
     * 商户基础信息 Mapper。
     */
    private final OpenApiMerchantInfoMapper merchantInfoMapper;

    /**
     * 商户 JWT 密钥 Mapper。
     */
    private final OpenApiMerchantJwtKeyMapper merchantJwtKeyMapper;

    /**
     * 平台请求体 RSA 密钥 Mapper。
     */
    private final OpenApiPlatformPayloadKeyMapper platformPayloadKeyMapper;

    /**
     * 商户响应公钥 Mapper。
     */
    private final OpenApiMerchantResponseKeyMapper merchantResponseKeyMapper;

    /**
     * OpenAPI 报文加解密工具。
     */
    private final OpenApiPayloadCrypto payloadCrypto;

    /**
     * OpenAPI 密钥材料生成入口。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    public OpenApiMerchantSecurityServiceImpl(OpenApiMerchantInfoMapper merchantInfoMapper,
                                              OpenApiMerchantJwtKeyMapper merchantJwtKeyMapper,
                                              OpenApiPlatformPayloadKeyMapper platformPayloadKeyMapper,
                                              OpenApiMerchantResponseKeyMapper merchantResponseKeyMapper,
                                              OpenApiPayloadCrypto payloadCrypto,
                                              OpenApiKeyMaterialFactory keyMaterialFactory) {
        this.merchantInfoMapper = merchantInfoMapper;
        this.merchantJwtKeyMapper = merchantJwtKeyMapper;
        this.platformPayloadKeyMapper = platformPayloadKeyMapper;
        this.merchantResponseKeyMapper = merchantResponseKeyMapper;
        this.payloadCrypto = payloadCrypto;
        this.keyMaterialFactory = keyMaterialFactory;
    }

    /**
     * 初始化商户安全材料并写入主库。
     *
     * @param seedDTO 商户开户与测试初始化入参
     * @return 商户侧需要保存的安全材料
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public OpenApiMerchantSecurityMaterialDTO provisionMerchantSecurityMaterial(OpenApiMerchantSecuritySeedDTO seedDTO) {
        validateSeed(seedDTO);
        LocalDateTime now = LocalDateTime.now();
        String platformKeyId = defaultIfBlank(seedDTO.getPlatformPayloadKeyId(), "payment-platform-payload-v1");
        String responseKeyId = seedDTO.getMerchantResponseKeyId();

        RsaKeyMaterial platformPayloadKey = keyMaterialFactory.generatePlatformPayloadRsaKey(platformKeyId);
        MerchantOpenApiCredential merchantCredential = keyMaterialFactory.generateMerchantCredential(
                seedDTO.getMerchantId(),
                platformPayloadKey
        );
        String merchantResponsePublicKey = null;
        String merchantResponsePrivateKey = null;
        if (StringUtils.hasText(responseKeyId)) {
            KeyPair merchantResponseKeyPair = payloadCrypto.generateRsaKeyPair(RSA_KEY_SIZE);
            merchantResponsePublicKey = Base64.getEncoder().encodeToString(merchantResponseKeyPair.getPublic().getEncoded());
            merchantResponsePrivateKey = Base64.getEncoder().encodeToString(merchantResponseKeyPair.getPrivate().getEncoded());
        }

        upsertMerchantInfo(seedDTO, now);
        upsertMerchantJwtKey(seedDTO.getMerchantId(), merchantCredential.merchantKey(), now);
        upsertPlatformPayloadKey(platformPayloadKey, now);
        if (StringUtils.hasText(responseKeyId)) {
            upsertMerchantResponseKey(seedDTO.getMerchantId(), responseKeyId, merchantResponsePublicKey, now);
        }

        OpenApiMerchantSecurityMaterialDTO materialDTO = new OpenApiMerchantSecurityMaterialDTO();
        materialDTO.setMerchantId(seedDTO.getMerchantId());
        materialDTO.setMerchantName(seedDTO.getMerchantName());
        materialDTO.setMerchantKey(merchantCredential.merchantKey());
        materialDTO.setJwtAlgorithm(JWT_ALGORITHM);
        materialDTO.setJwtExpiresSeconds(JWT_EXPIRES_SECONDS);
        materialDTO.setPlatformPayloadKeyId(platformPayloadKey.keyId());
        materialDTO.setPlatformPublicKeyX509Base64(platformPayloadKey.publicKeyX509Base64());
        materialDTO.setPlatformPublicKeyPem(platformPayloadKey.publicKeyPem());
        if (StringUtils.hasText(responseKeyId)) {
            materialDTO.setMerchantResponseKeyId(responseKeyId);
            materialDTO.setMerchantResponsePublicKeyX509Base64(merchantResponsePublicKey);
            materialDTO.setMerchantResponsePrivateKeyPkcs8Base64(merchantResponsePrivateKey);
            materialDTO.setMerchantResponsePrivateKeyPem(toPrivateKeyPem(merchantResponsePrivateKey));
        }
        return materialDTO;
    }

    /**
     * 从从库查询商户 JWT HS256 签名密钥。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户 JWT 签名密钥
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public String getMerchantKey(String merchantId) {
        validateMerchantId(merchantId);
        OpenApiMerchantInfoDO merchantInfoDO = getActiveMerchant(merchantId);
        OpenApiMerchantJwtKeyDO keyDO = selectActiveMerchantJwtKey(merchantInfoDO.getMerchantId());
        return keyDO.getMerchantKey();
    }

    /**
     * 从从库查询平台 RSA 私钥。
     *
     * @param keyId 密文报文 header 中的密钥编号
     * @return 平台 RSA 私钥
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PrivateKey getPlatformPrivateKey(String keyId) {
        OpenApiPlatformPayloadKeyDO keyDO = selectActivePlatformPayloadKey(keyId);
        return payloadCrypto.readPrivateKey(keyDO.getPrivateKeyPkcs8Base64());
    }

    /**
     * 从从库查询平台 RSA 公钥。
     *
     * @param keyId 密钥编号
     * @return 平台 RSA 公钥
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PublicKey getPlatformPublicKey(String keyId) {
        OpenApiPlatformPayloadKeyDO keyDO = selectActivePlatformPayloadKey(keyId);
        return payloadCrypto.readPublicKey(keyDO.getPublicKeyX509Base64());
    }

    /**
     * 从从库查询可交易商户基础信息。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户基础信息
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public OpenApiMerchantInfoDO getActiveMerchant(String merchantId) {
        validateMerchantId(merchantId);
        OpenApiMerchantInfoDO merchantInfoDO = merchantInfoMapper.selectOne(
                Wrappers.<OpenApiMerchantInfoDO>lambdaQuery()
                        .eq(OpenApiMerchantInfoDO::getMerchantId, merchantId)
                        .eq(OpenApiMerchantInfoDO::getMerchantStatus, MERCHANT_STATUS_ACTIVE)
                        .eq(OpenApiMerchantInfoDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (merchantInfoDO == null) {
            throw new ApiException(ApiCoResultEnum.CO_MERCHANT_CONFIG_NOT_FOUND, merchantId);
        }
        return merchantInfoDO;
    }

    /**
     * 从从库查询商户响应 RSA 公钥。
     *
     * @param merchantId    支付框架颁发的商户号
     * @param responseKeyId 商户响应公钥编号
     * @return 商户响应 RSA 公钥
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PublicKey getMerchantResponsePublicKey(String merchantId, String responseKeyId) {
        OpenApiMerchantResponseKeyDO keyDO = selectActiveMerchantResponseKey(merchantId, responseKeyId);
        return payloadCrypto.readPublicKey(keyDO.getPublicKeyX509Base64());
    }

    /**
     * 从从库查询商户当前启用的响应公钥编号。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 响应公钥编号
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public String getEnabledMerchantResponseKeyId(String merchantId) {
        OpenApiMerchantResponseKeyDO keyDO = selectActiveMerchantResponseKey(merchantId, null);
        return keyDO.getResponseKeyId();
    }

    private void upsertMerchantInfo(OpenApiMerchantSecuritySeedDTO seedDTO, LocalDateTime now) {
        OpenApiMerchantInfoDO entity = new OpenApiMerchantInfoDO();
        entity.setMerchantId(seedDTO.getMerchantId());
        entity.setMerchantName(seedDTO.getMerchantName());
        entity.setMerchantShortName(defaultIfBlank(seedDTO.getMerchantShortName(), seedDTO.getMerchantName()));
        entity.setMerchantStatus(MERCHANT_STATUS_ACTIVE);
        entity.setMerchantCategoryCode(defaultIfBlank(seedDTO.getMerchantCategoryCode(), "5311"));
        entity.setCountryCode(defaultIfBlank(seedDTO.getCountryCode(), "USA"));
        entity.setRegionCode(defaultIfBlank(seedDTO.getRegionCode(), "CA"));
        entity.setCity(defaultIfBlank(seedDTO.getCity(), "San Jose"));
        entity.setAddressLine(defaultIfBlank(seedDTO.getAddressLine(), "Default acquiring merchant address"));
        entity.setContactEmail(defaultIfBlank(seedDTO.getContactEmail(), "merchant@example.com"));
        entity.setContactPhone(defaultIfBlank(seedDTO.getContactPhone(), "+1-408-555-0100"));
        entity.setSettlementCurrency(defaultIfBlank(seedDTO.getSettlementCurrency(), DEFAULT_SETTLEMENT_CURRENCY));
        entity.setTimezone(defaultIfBlank(seedDTO.getTimezone(), DEFAULT_TIMEZONE));
        entity.setRiskLevel(defaultIfBlank(seedDTO.getRiskLevel(), DEFAULT_RISK_LEVEL));
        entity.setDeleted(NOT_DELETED);
        upsertMerchantInfo(entity, now);
    }

    private void upsertMerchantJwtKey(String merchantId, String merchantKey, LocalDateTime now) {
        OpenApiMerchantJwtKeyDO entity = new OpenApiMerchantJwtKeyDO();
        entity.setMerchantId(merchantId);
        entity.setKeyVersion(DEFAULT_JWT_KEY_VERSION);
        entity.setMerchantKey(merchantKey);
        entity.setAlgorithm(JWT_ALGORITHM);
        entity.setExpiresSeconds(JWT_EXPIRES_SECONDS);
        entity.setEnabled(ENABLED);
        entity.setEffectiveTime(now);
        entity.setDeleted(NOT_DELETED);
        upsertMerchantJwtKey(entity, now);
    }

    private void upsertPlatformPayloadKey(RsaKeyMaterial platformPayloadKey, LocalDateTime now) {
        OpenApiPlatformPayloadKeyDO entity = new OpenApiPlatformPayloadKeyDO();
        entity.setPlatformKeyId(platformPayloadKey.keyId());
        entity.setPublicKeyX509Base64(platformPayloadKey.publicKeyX509Base64());
        entity.setPrivateKeyPkcs8Base64(platformPayloadKey.privateKeyPkcs8Base64());
        entity.setAlgorithm(PAYLOAD_ALGORITHM);
        entity.setKeySize(platformPayloadKey.keySize());
        entity.setEnabled(ENABLED);
        entity.setDeleted(NOT_DELETED);
        upsertPlatformPayloadKey(entity, now);
    }

    private void upsertMerchantResponseKey(String merchantId,
                                           String responseKeyId,
                                           String publicKeyX509Base64,
                                           LocalDateTime now) {
        OpenApiMerchantResponseKeyDO entity = new OpenApiMerchantResponseKeyDO();
        entity.setMerchantId(merchantId);
        entity.setResponseKeyId(responseKeyId);
        entity.setPublicKeyX509Base64(publicKeyX509Base64);
        entity.setAlgorithm(PAYLOAD_ALGORITHM);
        entity.setKeySize(RSA_KEY_SIZE);
        entity.setEnabled(ENABLED);
        entity.setDeleted(NOT_DELETED);
        upsertMerchantResponseKey(entity, now);
    }

    private void upsertMerchantInfo(OpenApiMerchantInfoDO entity, LocalDateTime now) {
        OpenApiMerchantInfoDO existing = merchantInfoMapper.selectOne(
                Wrappers.<OpenApiMerchantInfoDO>lambdaQuery()
                        .eq(OpenApiMerchantInfoDO::getMerchantId, entity.getMerchantId())
                        .last("LIMIT 1")
        );
        saveOrUpdate(entity, existing == null ? null : existing.getId(), now, merchantInfoMapper::insert, merchantInfoMapper::updateById);
    }

    private void upsertMerchantJwtKey(OpenApiMerchantJwtKeyDO entity, LocalDateTime now) {
        OpenApiMerchantJwtKeyDO existing = merchantJwtKeyMapper.selectOne(
                Wrappers.<OpenApiMerchantJwtKeyDO>lambdaQuery()
                        .eq(OpenApiMerchantJwtKeyDO::getMerchantId, entity.getMerchantId())
                        .eq(OpenApiMerchantJwtKeyDO::getKeyVersion, entity.getKeyVersion())
                        .last("LIMIT 1")
        );
        saveOrUpdate(entity, existing == null ? null : existing.getId(), now, merchantJwtKeyMapper::insert, merchantJwtKeyMapper::updateById);
    }

    private void upsertPlatformPayloadKey(OpenApiPlatformPayloadKeyDO entity, LocalDateTime now) {
        OpenApiPlatformPayloadKeyDO existing = platformPayloadKeyMapper.selectOne(
                Wrappers.<OpenApiPlatformPayloadKeyDO>lambdaQuery()
                        .eq(OpenApiPlatformPayloadKeyDO::getPlatformKeyId, entity.getPlatformKeyId())
                        .last("LIMIT 1")
        );
        saveOrUpdate(entity, existing == null ? null : existing.getId(), now, platformPayloadKeyMapper::insert, platformPayloadKeyMapper::updateById);
    }

    private void upsertMerchantResponseKey(OpenApiMerchantResponseKeyDO entity, LocalDateTime now) {
        OpenApiMerchantResponseKeyDO existing = merchantResponseKeyMapper.selectOne(
                Wrappers.<OpenApiMerchantResponseKeyDO>lambdaQuery()
                        .eq(OpenApiMerchantResponseKeyDO::getMerchantId, entity.getMerchantId())
                        .eq(OpenApiMerchantResponseKeyDO::getResponseKeyId, entity.getResponseKeyId())
                        .last("LIMIT 1")
        );
        saveOrUpdate(entity, existing == null ? null : existing.getId(), now, merchantResponseKeyMapper::insert, merchantResponseKeyMapper::updateById);
    }

    private OpenApiMerchantJwtKeyDO selectActiveMerchantJwtKey(String merchantId) {
        OpenApiMerchantJwtKeyDO keyDO = merchantJwtKeyMapper.selectOne(
                Wrappers.<OpenApiMerchantJwtKeyDO>lambdaQuery()
                        .eq(OpenApiMerchantJwtKeyDO::getMerchantId, merchantId)
                        .eq(OpenApiMerchantJwtKeyDO::getAlgorithm, JWT_ALGORITHM)
                        .eq(OpenApiMerchantJwtKeyDO::getEnabled, ENABLED)
                        .eq(OpenApiMerchantJwtKeyDO::getDeleted, NOT_DELETED)
                        .orderByDesc(OpenApiMerchantJwtKeyDO::getEffectiveTime)
                        .last("LIMIT 1")
        );
        if (keyDO == null) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_NO_KEY);
        }
        return keyDO;
    }

    private OpenApiPlatformPayloadKeyDO selectActivePlatformPayloadKey(String keyId) {
        if (!StringUtils.hasText(keyId)) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_MISSING, "data.kid");
        }
        OpenApiPlatformPayloadKeyDO keyDO = platformPayloadKeyMapper.selectOne(
                Wrappers.<OpenApiPlatformPayloadKeyDO>lambdaQuery()
                        .eq(OpenApiPlatformPayloadKeyDO::getPlatformKeyId, keyId)
                        .eq(OpenApiPlatformPayloadKeyDO::getEnabled, ENABLED)
                        .eq(OpenApiPlatformPayloadKeyDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (keyDO == null) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL, "data.kid");
        }
        return keyDO;
    }

    private OpenApiMerchantResponseKeyDO selectActiveMerchantResponseKey(String merchantId, String responseKeyId) {
        validateMerchantId(merchantId);
        var queryWrapper = Wrappers.<OpenApiMerchantResponseKeyDO>lambdaQuery()
                .eq(OpenApiMerchantResponseKeyDO::getMerchantId, merchantId)
                .eq(OpenApiMerchantResponseKeyDO::getEnabled, ENABLED)
                .eq(OpenApiMerchantResponseKeyDO::getDeleted, NOT_DELETED)
                .orderByDesc(OpenApiMerchantResponseKeyDO::getGmtModified)
                .last("LIMIT 1");
        if (StringUtils.hasText(responseKeyId)) {
            queryWrapper.eq(OpenApiMerchantResponseKeyDO::getResponseKeyId, responseKeyId);
        }
        OpenApiMerchantResponseKeyDO keyDO = merchantResponseKeyMapper.selectOne(queryWrapper);
        if (keyDO == null) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL, "responseKeyId");
        }
        return keyDO;
    }

    private <T extends com.scott.payment.component.db.entity.BaseEntity> void saveOrUpdate(T entity,
                                                                                          Long existingId,
                                                                                          LocalDateTime now,
                                                                                          java.util.function.Function<T, Integer> insertFunction,
                                                                                          java.util.function.Function<T, Integer> updateFunction) {
        entity.setGmtModified(now);
        if (existingId == null) {
            entity.setGmtCreate(now);
            insertFunction.apply(entity);
            return;
        }
        entity.setId(existingId);
        updateFunction.apply(entity);
    }

    private void validateSeed(OpenApiMerchantSecuritySeedDTO seedDTO) {
        if (seedDTO == null || !StringUtils.hasText(seedDTO.getMerchantId())) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_MISSING, "merchantId");
        }
        if (!StringUtils.hasText(seedDTO.getMerchantName())) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_MISSING, "merchantName");
        }
    }

    private void validateMerchantId(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_MER_INVALID);
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String toPrivateKeyPem(String privateKeyBase64) {
        StringBuilder builder = new StringBuilder(PRIVATE_KEY_BEGIN).append('\n');
        for (int index = 0; index < privateKeyBase64.length(); index += PEM_LINE_LENGTH) {
            builder.append(privateKeyBase64, index, Math.min(index + PEM_LINE_LENGTH, privateKeyBase64.length())).append('\n');
        }
        return builder.append(PRIVATE_KEY_END).toString();
    }
}
