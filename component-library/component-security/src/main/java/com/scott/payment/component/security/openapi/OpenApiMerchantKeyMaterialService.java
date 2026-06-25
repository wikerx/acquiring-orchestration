package com.scott.payment.component.security.openapi;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantJwtKeyDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantResponseKeyDO;
import com.scott.payment.component.db.auth.entity.BasePlatformPayloadKeyDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.mapper.BasePlatformPayloadKeyMapper;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.MerchantJwtKey;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.RsaKeyMaterial;
import com.scott.payment.component.security.openapi.OpenApiKeyExportService.OpenApiKeyExportContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * OpenAPI 商户密钥材料统一服务，负责查询密钥状态并生成复制文本、PEM 文件和 SDK 接入包。
 */
public class OpenApiMerchantKeyMaterialService {

    private static final int NOT_DELETED = 0;
    private static final int ENABLED = 1;
    private static final int COPY_EXPIRE_SECONDS = 60;
    private static final String JWT_ALGORITHM = "HS256";
    private static final String PAYLOAD_ALGORITHM = "RSA-OAEP-256+A256GCM";
    private static final DateTimeFormatter KEY_VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BaseMerchantInfoMapper merchantInfoMapper;
    private final BaseMerchantJwtKeyMapper jwtKeyMapper;
    private final BasePlatformPayloadKeyMapper platformPayloadKeyMapper;
    private final BaseMerchantResponseKeyMapper responseKeyMapper;
    private final OpenApiKeyMaterialFactory keyMaterialFactory;
    private final OpenApiKeyExportService keyExportService;
    private final OpenApiMerchantKeyExportProperties exportProperties;

    /**
     * 创建 OpenAPI 商户密钥材料统一服务。
     *
     * @param merchantInfoMapper       商户基础资料 Mapper
     * @param jwtKeyMapper             商户 JWT 密钥 Mapper
     * @param platformPayloadKeyMapper 平台请求加密密钥 Mapper
     * @param responseKeyMapper        商户响应密钥 Mapper
     * @param keyMaterialFactory       OpenAPI 密钥指纹组件
     * @param keyExportService         OpenAPI 接入材料导出服务
     * @param exportProperties         OpenAPI 商户接入材料导出配置
     */
    public OpenApiMerchantKeyMaterialService(BaseMerchantInfoMapper merchantInfoMapper,
                                             BaseMerchantJwtKeyMapper jwtKeyMapper,
                                             BasePlatformPayloadKeyMapper platformPayloadKeyMapper,
                                             BaseMerchantResponseKeyMapper responseKeyMapper,
                                             OpenApiKeyMaterialFactory keyMaterialFactory,
                                             OpenApiKeyExportService keyExportService,
                                             OpenApiMerchantKeyExportProperties exportProperties) {
        this.merchantInfoMapper = merchantInfoMapper;
        this.jwtKeyMapper = jwtKeyMapper;
        this.platformPayloadKeyMapper = platformPayloadKeyMapper;
        this.responseKeyMapper = responseKeyMapper;
        this.keyMaterialFactory = keyMaterialFactory;
        this.keyExportService = keyExportService;
        this.exportProperties = exportProperties;
    }

    /**
     * 查询商户 OpenAPI 密钥材料展示视图。
     *
     * @param merchantId 商户号
     * @return 密钥材料展示视图
     */
    public OpenApiMerchantKeyMaterialVO queryMaterial(String merchantId) {
        MaterialSnapshot snapshot = loadSnapshot(merchantId);
        OpenApiMerchantKeyMaterialVO vo = new OpenApiMerchantKeyMaterialVO();
        vo.setMerchantId(snapshot.merchant().getMerchantId());
        vo.setMerchantName(snapshot.merchant().getMerchantName());
        vo.setOpenApiBaseUrl(openApiBaseUrl());
        vo.setSdkVersion(nullToDefault(exportProperties.getSdkVersion(), "-"));
        vo.setCryptoMode(nullToDefault(exportProperties.getCryptoMode(), "-"));
        fillJwt(vo, snapshot.jwtKey());
        fillPlatform(vo, snapshot.platformKey());
        fillResponse(vo, snapshot.responseKey());
        vo.setCanCopyPrivateKey(StringUtils.hasText(privateKey(snapshot.responseKey())));
        vo.setCanDownloadPrivateKey(StringUtils.hasText(privateKey(snapshot.responseKey())));
        vo.setCanRotateJwtKey(true);
        vo.setCanRotatePlatformPayloadKey(true);
        vo.setCanRotateMerchantResponseKey(true);
        return vo;
    }

    /**
     * 生成复制响应。调用方应在 Controller 层增加权限和操作日志，尤其是私钥复制。
     *
     * @param merchantId 商户号
     * @param request    导出请求
     * @return 可复制文本
     */
    public OpenApiKeyCopyResponse copy(String merchantId, OpenApiKeyExportRequest request) {
        OpenApiKeyType keyType = requireKeyType(request);
        OpenApiKeyExportFormat format = request.getExportFormat() == null ? OpenApiKeyExportFormat.TEXT : request.getExportFormat();
        if (format != OpenApiKeyExportFormat.TEXT && format != OpenApiKeyExportFormat.PROPERTIES) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "复制操作仅支持 TEXT 或 PROPERTIES");
        }
        MaterialSnapshot snapshot = loadSnapshot(merchantId);
        return new OpenApiKeyCopyResponse(copyContent(snapshot, keyType), COPY_EXPIRE_SECONDS);
    }

    /**
     * 生成下载文件。平台请求私钥只允许管理端在私钥权限保护下单独导出，不进入商户 SDK 接入包。
     *
     * @param merchantId 商户号
     * @param keyType    密钥材料类型
     * @param format     下载格式
     * @return 下载文件
     */
    public OpenApiKeyDownloadFile download(String merchantId, OpenApiKeyType keyType, OpenApiKeyExportFormat format) {
        MaterialSnapshot snapshot = loadSnapshot(merchantId);
        OpenApiKeyExportFormat exportFormat = format == null ? defaultDownloadFormat(keyType) : format;
        String content;
        switch (keyType) {
            case JWT_KEY -> {
                content = keyExportService.jwtText(
                        snapshot.merchant().getMerchantId(),
                        requireText(secret(snapshot.jwtKey()), "商户 JWT 密钥未配置"),
                        jwtAlgorithm(snapshot.jwtKey()),
                        jwtExpiresSeconds(snapshot.jwtKey())
                );
                return keyExportService.textFile(snapshot.merchant().getMerchantId() + "-merchant-jwt-key.txt", content);
            }
            case PLATFORM_PUBLIC_KEY -> {
                return platformPublicKeyFile(snapshot, exportFormat);
            }
            case PLATFORM_PRIVATE_KEY -> {
                return platformPrivateKeyFile(snapshot, exportFormat);
            }
            case PLATFORM_PAYLOAD_KEY -> throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "PLATFORM_PAYLOAD_KEY 仅支持轮换，请下载 PLATFORM_PUBLIC_KEY");
            case MERCHANT_RESPONSE_PUBLIC_KEY -> {
                return merchantResponsePublicKeyFile(snapshot, exportFormat);
            }
            case MERCHANT_RESPONSE_PRIVATE_KEY -> {
                return merchantResponsePrivateKeyFile(snapshot, exportFormat);
            }
            case MERCHANT_RESPONSE_KEY -> throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "MERCHANT_RESPONSE_KEY 仅支持轮换，请下载 MERCHANT_RESPONSE_PRIVATE_KEY");
            case MERCHANT_CONFIG -> {
                content = keyExportService.merchantConfig(exportContext(snapshot));
                return keyExportService.propertiesFile("merchant-config.properties", content);
            }
            case MERCHANT_CONFIG_TEXT -> {
                content = keyExportService.merchantConfigText(exportContext(snapshot));
                return keyExportService.propertiesFile("merchant-config-text.properties", content);
            }
            case SDK_KIT -> {
                return sdkKit(snapshot);
            }
            default -> throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "不支持的密钥材料类型");
        }
    }

    /**
     * 轮换当前商户的一类 OpenAPI 密钥，并返回轮换后的概要。
     *
     * @param merchantId 商户号
     * @param keyType    轮换类型
     * @return 最新密钥材料概要
     */
    @Transactional(rollbackFor = Exception.class)
    public OpenApiMerchantKeyMaterialVO rotate(String merchantId, OpenApiKeyType keyType) {
        BaseMerchantInfoDO merchant = selectMerchant(merchantId);
        if (keyType == OpenApiKeyType.JWT_KEY) {
            rotateJwtKey(merchant.getMerchantId());
            return queryMaterial(merchant.getMerchantId());
        }
        if (keyType == OpenApiKeyType.PLATFORM_PUBLIC_KEY) {
            rotatePlatformPayloadKey(merchant.getMerchantId());
            return queryMaterial(merchant.getMerchantId());
        }
        if (keyType == OpenApiKeyType.PLATFORM_PAYLOAD_KEY) {
            rotatePlatformPayloadKey(merchant.getMerchantId());
            return queryMaterial(merchant.getMerchantId());
        }
        if (keyType == OpenApiKeyType.MERCHANT_RESPONSE_PRIVATE_KEY || keyType == OpenApiKeyType.MERCHANT_RESPONSE_KEY) {
            rotateMerchantResponseKey(merchant.getMerchantId());
            return queryMaterial(merchant.getMerchantId());
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "keyType 不支持轮换");
    }

    /**
     * 当前商户响应私钥缺失时，生成新的响应密钥对并保留 JWT 与平台请求密钥不变。
     * <p>
     * 该方法用于修复历史只保存响应公钥、未保存响应私钥的数据；若私钥已存在则不做轮换。
     *
     * @param merchantId 商户号
     * @return 最新密钥材料概要
     */
    @Transactional(rollbackFor = Exception.class)
    public OpenApiMerchantKeyMaterialVO ensureMerchantResponsePrivateKey(String merchantId) {
        BaseMerchantInfoDO merchant = selectMerchant(merchantId);
        BaseMerchantResponseKeyDO row = selectResponseKey(merchant.getMerchantId());
        if (row == null || !StringUtils.hasText(row.getPrivateKeyPkcs8Base64())) {
            rotateMerchantResponseKey(merchant.getMerchantId());
        }
        return queryMaterial(merchant.getMerchantId());
    }

    private void rotateJwtKey(String merchantId) {
        LocalDateTime now = LocalDateTime.now();
        jwtKeyMapper.update(null, Wrappers.<BaseMerchantJwtKeyDO>lambdaUpdate()
                .eq(BaseMerchantJwtKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantJwtKeyDO::getDeleted, NOT_DELETED)
                .eq(BaseMerchantJwtKeyDO::getEnabled, ENABLED)
                .set(BaseMerchantJwtKeyDO::getEnabled, 0)
                .set(BaseMerchantJwtKeyDO::getExpireTime, now)
                .set(BaseMerchantJwtKeyDO::getGmtModified, now));
        MerchantJwtKey generated = keyMaterialFactory.generateMerchantJwtKey(merchantId);
        BaseMerchantJwtKeyDO row = new BaseMerchantJwtKeyDO();
        row.setMerchantId(merchantId);
        row.setKeyVersion("jwt-" + now.format(KEY_VERSION_FORMATTER));
        row.setMerchantKey(generated.merchantKey());
        row.setAlgorithm(JWT_ALGORITHM);
        row.setExpiresSeconds(generated.expiresSeconds());
        row.setEnabled(ENABLED);
        row.setEffectiveTime(now);
        row.setGmtCreate(now);
        row.setGmtModified(now);
        row.setDeleted(NOT_DELETED);
        jwtKeyMapper.insert(row);
    }

    private void rotatePlatformPayloadKey(String merchantId) {
        LocalDateTime now = LocalDateTime.now();
        RsaKeyMaterial generated = keyMaterialFactory.generatePlatformPayloadRsaKey(merchantId);
        BasePlatformPayloadKeyDO row = selectPlatformKey(merchantId);
        if (row == null) {
            row = new BasePlatformPayloadKeyDO();
            row.setMerchantId(merchantId);
            row.setGmtCreate(now);
            row.setDeleted(NOT_DELETED);
        }
        row.setPublicKeyX509Base64(generated.publicKeyX509Base64());
        row.setPrivateKeyPkcs8Base64(generated.privateKeyPkcs8Base64());
        row.setAlgorithm(PAYLOAD_ALGORITHM);
        row.setKeySize(generated.keySize());
        row.setEnabled(ENABLED);
        row.setGmtModified(now);
        if (row.getId() == null) {
            platformPayloadKeyMapper.insert(row);
        } else {
            platformPayloadKeyMapper.updateById(row);
        }
    }

    private void rotateMerchantResponseKey(String merchantId) {
        LocalDateTime now = LocalDateTime.now();
        RsaKeyMaterial generated = keyMaterialFactory.generateMerchantResponseRsaKey(merchantId);
        BaseMerchantResponseKeyDO row = selectResponseKey(merchantId);
        if (row == null) {
            row = new BaseMerchantResponseKeyDO();
            row.setMerchantId(merchantId);
            row.setGmtCreate(now);
            row.setDeleted(NOT_DELETED);
        }
        row.setPublicKeyX509Base64(generated.publicKeyX509Base64());
        row.setPrivateKeyPkcs8Base64(generated.privateKeyPkcs8Base64());
        row.setAlgorithm(PAYLOAD_ALGORITHM);
        row.setKeySize(generated.keySize());
        row.setEnabled(ENABLED);
        row.setGmtModified(now);
        if (row.getId() == null) {
            responseKeyMapper.insert(row);
        } else {
            responseKeyMapper.updateById(row);
        }
    }

    private OpenApiKeyDownloadFile platformPublicKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String publicKey = requireText(publicKey(snapshot.platformKey()), "平台请求公钥未配置");
        return keyExportService.platformPublicKeyFile(merchantId, publicKey, format);
    }

    private OpenApiKeyDownloadFile platformPrivateKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String privateKey = requireText(platformPrivateKey(snapshot.platformKey()), "平台请求私钥未配置");
        return keyExportService.platformPrivateKeyFile(merchantId, privateKey, format);
    }

    private OpenApiKeyDownloadFile merchantResponsePrivateKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String privateKey = requireText(privateKey(snapshot.responseKey()), "商户响应私钥未配置");
        return keyExportService.merchantResponsePrivateKeyFile(merchantId, privateKey, format);
    }

    private OpenApiKeyDownloadFile merchantResponsePublicKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String publicKey = requireText(responsePublicKey(snapshot.responseKey()), "商户响应公钥未配置");
        return keyExportService.merchantResponsePublicKeyFile(merchantId, publicKey, format);
    }

    private OpenApiKeyDownloadFile sdkKit(MaterialSnapshot snapshot) {
        return keyExportService.sdkKit(exportContext(snapshot));
    }

    private String copyContent(MaterialSnapshot snapshot, OpenApiKeyType keyType) {
        return switch (keyType) {
            case JWT_KEY -> requireText(secret(snapshot.jwtKey()), "商户 JWT 密钥未配置");
            case PLATFORM_PUBLIC_KEY -> requireText(publicKey(snapshot.platformKey()), "平台请求公钥未配置");
            case PLATFORM_PRIVATE_KEY -> requireText(platformPrivateKey(snapshot.platformKey()), "平台请求私钥未配置");
            case PLATFORM_PAYLOAD_KEY -> throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "PLATFORM_PAYLOAD_KEY 仅支持轮换，请复制 PLATFORM_PUBLIC_KEY");
            case MERCHANT_RESPONSE_PUBLIC_KEY -> requireText(responsePublicKey(snapshot.responseKey()), "商户响应公钥未配置");
            case MERCHANT_RESPONSE_PRIVATE_KEY -> requireText(privateKey(snapshot.responseKey()), "商户响应私钥未配置");
            case MERCHANT_RESPONSE_KEY -> throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "MERCHANT_RESPONSE_KEY 仅支持轮换，请复制 MERCHANT_RESPONSE_PRIVATE_KEY");
            case MERCHANT_CONFIG -> keyExportService.merchantConfig(exportContext(snapshot));
            case MERCHANT_CONFIG_TEXT -> keyExportService.merchantConfigText(exportContext(snapshot));
            case SDK_KIT -> throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "完整接入包只能下载");
        };
    }

    private OpenApiKeyExportContext exportContext(MaterialSnapshot snapshot) {
        BaseMerchantJwtKeyDO jwtKey = snapshot.jwtKey();
        return new OpenApiKeyExportContext(
                snapshot.merchant().getMerchantId(),
                requireText(secret(snapshot.jwtKey()), "商户 JWT 密钥未配置"),
                nullToDefault(jwtKey == null ? null : jwtKey.getAlgorithm(), "HS256"),
                nullToDefault(jwtKey == null ? null : jwtKey.getExpiresSeconds(), 180L),
                requireText(publicKey(snapshot.platformKey()), "平台请求公钥未配置"),
                requireText(privateKey(snapshot.responseKey()), "商户响应私钥未配置")
        );
    }

    private void fillJwt(OpenApiMerchantKeyMaterialVO vo, BaseMerchantJwtKeyDO row) {
        if (row == null) {
            vo.setJwtKeyStatus("NOT_CONFIGURED");
            return;
        }
        vo.setJwtKeyStatus(status(row.getEnabled()));
        vo.setJwtAlgorithm(row.getAlgorithm());
        vo.setJwtKeyVersion(row.getKeyVersion());
        vo.setJwtKeyFingerprint(fingerprint(row.getMerchantKey()));
        vo.setJwtUpdatedTime(row.getGmtModified());
    }

    private void fillPlatform(OpenApiMerchantKeyMaterialVO vo, BasePlatformPayloadKeyDO row) {
        if (row == null) {
            vo.setPlatformPayloadKeyStatus("NOT_CONFIGURED");
            return;
        }
        vo.setPlatformPayloadKeyStatus(status(row.getEnabled()));
        vo.setPlatformPayloadAlgorithm(row.getAlgorithm());
        vo.setPlatformPayloadKeySize(row.getKeySize());
        vo.setPlatformPayloadPublicKeyFingerprint(fingerprint(row.getPublicKeyX509Base64()));
        vo.setPlatformPayloadUpdatedTime(row.getGmtModified());
    }

    private void fillResponse(OpenApiMerchantKeyMaterialVO vo, BaseMerchantResponseKeyDO row) {
        if (row == null) {
            vo.setMerchantResponseKeyStatus("NOT_CONFIGURED");
            return;
        }
        vo.setMerchantResponseKeyStatus(status(row.getEnabled()));
        vo.setMerchantResponseAlgorithm(row.getAlgorithm());
        vo.setMerchantResponseKeySize(row.getKeySize());
        vo.setMerchantResponsePublicKeyFingerprint(fingerprint(row.getPublicKeyX509Base64()));
        vo.setMerchantResponseUpdatedTime(row.getGmtModified());
        vo.setMerchantResponsePrivateKeyAvailable(StringUtils.hasText(row.getPrivateKeyPkcs8Base64()));
    }

    private MaterialSnapshot loadSnapshot(String merchantId) {
        BaseMerchantInfoDO merchant = selectMerchant(merchantId);
        return new MaterialSnapshot(
                merchant,
                selectJwtKey(merchant.getMerchantId()),
                selectPlatformKey(merchant.getMerchantId()),
                selectResponseKey(merchant.getMerchantId())
        );
    }

    private BaseMerchantInfoDO selectMerchant(String merchantId) {
        String normalized = requireText(merchantId, "商户号不能为空");
        BaseMerchantInfoDO row = merchantInfoMapper.selectOne(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getMerchantId, normalized)
                .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "商户不存在");
        }
        return row;
    }

    private BaseMerchantJwtKeyDO selectJwtKey(String merchantId) {
        return jwtKeyMapper.selectOne(Wrappers.<BaseMerchantJwtKeyDO>lambdaQuery()
                .eq(BaseMerchantJwtKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantJwtKeyDO::getDeleted, NOT_DELETED)
                .eq(BaseMerchantJwtKeyDO::getEnabled, ENABLED)
                .orderByDesc(BaseMerchantJwtKeyDO::getEffectiveTime)
                .last("LIMIT 1"));
    }

    private BasePlatformPayloadKeyDO selectPlatformKey(String merchantId) {
        return platformPayloadKeyMapper.selectOne(Wrappers.<BasePlatformPayloadKeyDO>lambdaQuery()
                .eq(BasePlatformPayloadKeyDO::getMerchantId, merchantId)
                .eq(BasePlatformPayloadKeyDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    private BaseMerchantResponseKeyDO selectResponseKey(String merchantId) {
        return responseKeyMapper.selectOne(Wrappers.<BaseMerchantResponseKeyDO>lambdaQuery()
                .eq(BaseMerchantResponseKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantResponseKeyDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    private OpenApiKeyType requireKeyType(OpenApiKeyExportRequest request) {
        if (request == null || request.getKeyType() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "keyType 不能为空");
        }
        return request.getKeyType();
    }

    private OpenApiKeyExportFormat defaultDownloadFormat(OpenApiKeyType keyType) {
        Objects.requireNonNull(keyType, "keyType can not be null");
        return switch (keyType) {
            case MERCHANT_CONFIG, MERCHANT_CONFIG_TEXT -> OpenApiKeyExportFormat.PROPERTIES;
            case PLATFORM_PUBLIC_KEY, PLATFORM_PRIVATE_KEY, MERCHANT_RESPONSE_PUBLIC_KEY, MERCHANT_RESPONSE_PRIVATE_KEY -> OpenApiKeyExportFormat.PEM;
            case SDK_KIT -> OpenApiKeyExportFormat.ZIP;
            case JWT_KEY -> OpenApiKeyExportFormat.TXT;
            case PLATFORM_PAYLOAD_KEY, MERCHANT_RESPONSE_KEY -> OpenApiKeyExportFormat.TEXT;
        };
    }

    private String fingerprint(String value) {
        return StringUtils.hasText(value) ? keyMaterialFactory.fingerprint(value) : null;
    }

    private String status(Integer enabled) {
        return Objects.equals(enabled, ENABLED) ? "ENABLED" : "DISABLED";
    }

    private String secret(BaseMerchantJwtKeyDO row) {
        return row == null ? null : row.getMerchantKey();
    }

    private String publicKey(BasePlatformPayloadKeyDO row) {
        return row == null ? null : row.getPublicKeyX509Base64();
    }

    private String platformPrivateKey(BasePlatformPayloadKeyDO row) {
        return row == null ? null : row.getPrivateKeyPkcs8Base64();
    }

    private String privateKey(BaseMerchantResponseKeyDO row) {
        return row == null ? null : row.getPrivateKeyPkcs8Base64();
    }

    private String responsePublicKey(BaseMerchantResponseKeyDO row) {
        return row == null ? null : row.getPublicKeyX509Base64();
    }

    private String jwtAlgorithm(BaseMerchantJwtKeyDO row) {
        return nullToDefault(row == null ? null : row.getAlgorithm(), JWT_ALGORITHM);
    }

    private Long jwtExpiresSeconds(BaseMerchantJwtKeyDO row) {
        return nullToDefault(row == null ? null : row.getExpiresSeconds(), 180L);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
        }
        return value.trim();
    }

    private String nullToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String openApiBaseUrl() {
        return requireText(exportProperties.getOpenApiBaseUrl(), "OpenAPI 基础地址未配置");
    }

    private Long nullToDefault(Long value, Long defaultValue) {
        return value == null ? defaultValue : value;
    }

    private record MaterialSnapshot(BaseMerchantInfoDO merchant,
                                    BaseMerchantJwtKeyDO jwtKey,
                                    BasePlatformPayloadKeyDO platformKey,
                                    BaseMerchantResponseKeyDO responseKey) {
    }
}
