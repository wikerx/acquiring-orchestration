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
 * openAPI商户密钥材料服务契约，位于 公共组件库，声明该业务能力的输入、返回结果和异常边界，由实现类保持一致。
 */
public class OpenApiMerchantKeyMaterialService {


    private static final int NOT_DELETED = 0;

    private static final int ENABLED = 1;

    private static final int COPY_EXPIRE_SECONDS = 60;

    private static final String JWT_ALGORITHM = "HS256";

    private static final String PAYLOAD_ALGORITHM = "RSA-OAEP-256+A256GCM";

    private static final DateTimeFormatter KEY_VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BaseMerchantInfoMapper merchantInfoMapper;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private final BaseMerchantJwtKeyMapper jwtKeyMapper;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private final BasePlatformPayloadKeyMapper platformPayloadKeyMapper;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private final BaseMerchantResponseKeyMapper responseKeyMapper;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private final OpenApiKeyExportService keyExportService;
    private final OpenApiMerchantKeyExportProperties exportProperties;
    private final OpenApiBaseUrlResolver baseUrlResolver;

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
        this(merchantInfoMapper, jwtKeyMapper, platformPayloadKeyMapper, responseKeyMapper, keyMaterialFactory,
                keyExportService, exportProperties, () -> exportProperties == null ? null : exportProperties.getOpenApiBaseUrl());
    }

    /**
     * 创建 OpenAPI 商户密钥材料统一服务。
     *
     * @param merchantInfoMapper       商户基础资料 Mapper
     * @param jwtKeyMapper             商户 JWT 密钥 Mapper
     * @param platformPayloadKeyMapper 平台请求加密密钥 Mapper
     * @param responseKeyMapper        商户响应密钥 Mapper
     * @param keyMaterialFactory       OpenAPI 密钥指纹组件
     * @param keyExportService         OpenAPI 接入材料导出服务
     * @param exportProperties         OpenAPI 商户接入材料导出展示配置
     * @param baseUrlResolver          商户 OpenAPI 外部地址解析器
     */
    public OpenApiMerchantKeyMaterialService(BaseMerchantInfoMapper merchantInfoMapper,
                                             BaseMerchantJwtKeyMapper jwtKeyMapper,
                                             BasePlatformPayloadKeyMapper platformPayloadKeyMapper,
                                             BaseMerchantResponseKeyMapper responseKeyMapper,
                                             OpenApiKeyMaterialFactory keyMaterialFactory,
                                             OpenApiKeyExportService keyExportService,
                                             OpenApiMerchantKeyExportProperties exportProperties,
                                             OpenApiBaseUrlResolver baseUrlResolver) {
        this.merchantInfoMapper = merchantInfoMapper;
        this.jwtKeyMapper = jwtKeyMapper;
        this.platformPayloadKeyMapper = platformPayloadKeyMapper;
        this.responseKeyMapper = responseKeyMapper;
        this.keyMaterialFactory = keyMaterialFactory;
        this.keyExportService = keyExportService;
        this.exportProperties = exportProperties;
        this.baseUrlResolver = baseUrlResolver;
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
     * 启用或停用当前 OpenAPI 密钥材料，不删除、不轮换且不返回任何密钥明文。
     *
     * @param merchantId 商户号
     * @param keyType 密钥类型
     * @param enabled true 启用，false 停用
     */
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(String merchantId, OpenApiKeyType keyType, boolean enabled) {
        BaseMerchantInfoDO merchant = selectMerchant(merchantId);
        LocalDateTime now = LocalDateTime.now();
        int targetStatus = enabled ? ENABLED : 0;
        if (keyType == OpenApiKeyType.JWT_KEY) {
            BaseMerchantJwtKeyDO row = selectLatestJwtKey(merchant.getMerchantId());
            if (row == null) {
                throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "JWT 密钥未配置");
            }
            row.setEnabled(targetStatus);
            row.setEffectiveTime(enabled ? now : row.getEffectiveTime());
            row.setExpireTime(enabled ? null : now);
            row.setGmtModified(now);
            jwtKeyMapper.updateById(row);
            return;
        }
        if (keyType == OpenApiKeyType.PLATFORM_PUBLIC_KEY || keyType == OpenApiKeyType.PLATFORM_PAYLOAD_KEY) {
            BasePlatformPayloadKeyDO row = selectPlatformKey(merchant.getMerchantId());
            if (row == null) {
                throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "平台请求体密钥未配置");
            }
            row.setEnabled(targetStatus);
            row.setGmtModified(now);
            platformPayloadKeyMapper.updateById(row);
            return;
        }
        if (keyType == OpenApiKeyType.MERCHANT_RESPONSE_PUBLIC_KEY
                || keyType == OpenApiKeyType.MERCHANT_RESPONSE_PRIVATE_KEY
                || keyType == OpenApiKeyType.MERCHANT_RESPONSE_KEY) {
            BaseMerchantResponseKeyDO row = selectResponseKey(merchant.getMerchantId());
            if (row == null) {
                throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "商户响应密钥未配置");
            }
            row.setEnabled(targetStatus);
            row.setGmtModified(now);
            responseKeyMapper.updateById(row);
            return;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "keyType 不支持启停");
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

    /**
     * 停用商户当前 JWT 对称密钥并生成新版本。
     * <p>
     * 旧密钥立即标记过期，新密钥版本与材料在同一数据库事务内写入；密钥明文不得写入
     * 日志、异常或缓存。
     * </p>
     *
     * @param merchantId 需要轮换 JWT 密钥的商户号
     */
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

    /**
     * 生成并保存平台用于解密商户请求载荷的 RSA 密钥对。
     * <p>
     * 既有记录原位更新以维持商户唯一性。轮换会使使用旧公钥加密的新请求无法解密，
     * 调用方必须在发布新公钥时同步处理切换窗口。
     * </p>
     *
     * @param merchantId 需要轮换平台载荷密钥的商户号
     */
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

    /**
     * 生成并原子覆盖商户响应 RSA 密钥对，私钥仅保存在服务端受控存储。
     *
     * @param merchantId 商户号
     */
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

    /**
     * 根据材料快照生成平台请求公钥下载文件。
     *
     * @param snapshot 商户完整密钥材料快照
     * @param format 下载格式
     * @return 公钥下载文件
     */
    private OpenApiKeyDownloadFile platformPublicKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String publicKey = requireText(publicKey(snapshot.platformKey()), "平台请求公钥未配置");
        return keyExportService.platformPublicKeyFile(merchantId, publicKey, format);
    }

    /**
     * 根据材料快照生成平台请求私钥下载文件，仅供受控运维导出。
     *
     * @param snapshot 商户完整密钥材料快照
     * @param format 下载格式
     * @return 私钥下载文件
     */
    private OpenApiKeyDownloadFile platformPrivateKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String privateKey = requireText(platformPrivateKey(snapshot.platformKey()), "平台请求私钥未配置");
        return keyExportService.platformPrivateKeyFile(merchantId, privateKey, format);
    }

    /**
     * 根据材料快照生成商户响应解密私钥下载文件。
     *
     * @param snapshot 商户完整密钥材料快照
     * @param format 下载格式
     * @return 商户响应私钥下载文件
     */
    private OpenApiKeyDownloadFile merchantResponsePrivateKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String privateKey = requireText(privateKey(snapshot.responseKey()), "商户响应私钥未配置");
        return keyExportService.merchantResponsePrivateKeyFile(merchantId, privateKey, format);
    }

    /**
     * 根据材料快照生成商户响应公钥下载文件。
     *
     * @param snapshot 商户完整密钥材料快照
     * @param format 下载格式
     * @return 商户响应公钥下载文件
     */
    private OpenApiKeyDownloadFile merchantResponsePublicKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String publicKey = requireText(responsePublicKey(snapshot.responseKey()), "商户响应公钥未配置");
        return keyExportService.merchantResponsePublicKeyFile(merchantId, publicKey, format);
    }

    /**
     * 生成包含配置模板和必要密钥文件的商户 SDK 接入包。
     *
     * @param snapshot 商户完整密钥材料快照
     * @return ZIP 接入包
     */
    private OpenApiKeyDownloadFile sdkKit(MaterialSnapshot snapshot) {
        return keyExportService.sdkKit(exportContext(snapshot));
    }

    /**
     * 按明确密钥类型返回允许复制的材料，复合轮换类型和完整接入包拒绝文本复制。
     *
     * @param snapshot 商户完整密钥材料快照
     * @param keyType 请求的材料类型
     * @return 允许复制的材料文本
     */
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

    /**
     * 从数据库材料快照构建商户接入导出上下文。
     *
     * @param snapshot 商户完整密钥材料快照
     * @return 接入包导出上下文
     */
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

    /**
     * 将 JWT 密钥状态、算法、版本和不可逆指纹填入管理视图。
     *
     * @param vo 管理端密钥材料视图
     * @param row 当前 JWT 密钥记录
     */
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

    /**
     * 将平台请求加密密钥的非敏感元数据填入管理视图。
     *
     * @param vo 管理端密钥材料视图
     * @param row 当前平台载荷密钥记录
     */
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

    /**
     * 将商户响应密钥的非敏感元数据和私钥可用性填入管理视图。
     *
     * @param vo 管理端密钥材料视图
     * @param row 当前商户响应密钥记录
     */
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

    /**
     * 一次性加载商户及三类当前密钥记录，供同一管理操作保持一致视图。
     *
     * @param merchantId 商户号
     * @return 当前密钥材料快照
     */
    private MaterialSnapshot loadSnapshot(String merchantId) {
        BaseMerchantInfoDO merchant = selectMerchant(merchantId);
        return new MaterialSnapshot(
                merchant,
                selectJwtKey(merchant.getMerchantId()),
                selectPlatformKey(merchant.getMerchantId()),
                selectResponseKey(merchant.getMerchantId())
        );
    }

    /**
     * 查询未删除商户并在不存在时返回明确业务错误。
     *
     * @param merchantId 商户号
     * @return 商户记录
     */
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

    /**
     * 查询当前启用且生效时间最新的商户 JWT 密钥。
     *
     * @param merchantId 商户号
     * @return 当前启用密钥，不存在时返回 null
     */
    private BaseMerchantJwtKeyDO selectJwtKey(String merchantId) {
        return jwtKeyMapper.selectOne(Wrappers.<BaseMerchantJwtKeyDO>lambdaQuery()
                .eq(BaseMerchantJwtKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantJwtKeyDO::getDeleted, NOT_DELETED)
                .eq(BaseMerchantJwtKeyDO::getEnabled, ENABLED)
                .orderByDesc(BaseMerchantJwtKeyDO::getEffectiveTime)
                .last("LIMIT 1"));
    }

    /**
     * 查询最新 JWT 记录，包括已停用记录，供显式启停操作使用。
     */
    private BaseMerchantJwtKeyDO selectLatestJwtKey(String merchantId) {
        return jwtKeyMapper.selectOne(Wrappers.<BaseMerchantJwtKeyDO>lambdaQuery()
                .eq(BaseMerchantJwtKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantJwtKeyDO::getDeleted, NOT_DELETED)
                .orderByDesc(BaseMerchantJwtKeyDO::getEffectiveTime)
                .orderByDesc(BaseMerchantJwtKeyDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 查询商户当前平台请求载荷 RSA 密钥记录。
     *
     * @param merchantId 商户号
     * @return 平台载荷密钥记录，不存在时返回 null
     */
    private BasePlatformPayloadKeyDO selectPlatformKey(String merchantId) {
        return platformPayloadKeyMapper.selectOne(Wrappers.<BasePlatformPayloadKeyDO>lambdaQuery()
                .eq(BasePlatformPayloadKeyDO::getMerchantId, merchantId)
                .eq(BasePlatformPayloadKeyDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    /**
     * 查询商户当前响应加密 RSA 密钥记录。
     *
     * @param merchantId 商户号
     * @return 商户响应密钥记录，不存在时返回 null
     */
    private BaseMerchantResponseKeyDO selectResponseKey(String merchantId) {
        return responseKeyMapper.selectOne(Wrappers.<BaseMerchantResponseKeyDO>lambdaQuery()
                .eq(BaseMerchantResponseKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantResponseKeyDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    /**
     * 校验导出请求明确指定密钥材料类型。
     *
     * @param request 密钥导出请求
     * @return 请求的密钥类型
     */
    private OpenApiKeyType requireKeyType(OpenApiKeyExportRequest request) {
        if (request == null || request.getKeyType() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "keyType 不能为空");
        }
        return request.getKeyType();
    }

    /**
     * 按密钥材料类型选择不改变语义的默认下载格式。
     *
     * @param keyType 密钥材料类型
     * @return 默认下载格式
     */
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

    /**
     * 计算指纹摘要，用不可逆指纹关联原始内容而不暴露明文。
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 当前方法生成或规范化后的文本值
     */
    private String fingerprint(String value) {
        return StringUtils.hasText(value) ? keyMaterialFactory.fingerprint(value) : null;
    }

    /**
     * 将数据库启用值转换为管理端稳定状态文本。
     *
     * @param enabled 数据库启用值
     * @return ENABLED 或 DISABLED
     */
    private String status(Integer enabled) {
        return Objects.equals(enabled, ENABLED) ? "ENABLED" : "DISABLED";
    }

    /**
     * 从可空 JWT 密钥记录读取密钥正文。
     *
     * @param row JWT 密钥记录
     * @return 密钥正文或 null
     */
    private String secret(BaseMerchantJwtKeyDO row) {
        return row == null ? null : row.getMerchantKey();
    }

    /**
     * 从可空平台载荷密钥记录读取 X.509 公钥正文。
     *
     * @param row 平台载荷密钥记录
     * @return 公钥正文或 null
     */
    private String publicKey(BasePlatformPayloadKeyDO row) {
        return row == null ? null : row.getPublicKeyX509Base64();
    }

    /**
     * 从可空平台载荷密钥记录读取 PKCS#8 私钥正文。
     *
     * @param row 平台载荷密钥记录
     * @return 私钥正文或 null
     */
    private String platformPrivateKey(BasePlatformPayloadKeyDO row) {
        return row == null ? null : row.getPrivateKeyPkcs8Base64();
    }

    /**
     * 从可空商户响应密钥记录读取 PKCS#8 私钥正文。
     *
     * @param row 商户响应密钥记录
     * @return 私钥正文或 null
     */
    private String privateKey(BaseMerchantResponseKeyDO row) {
        return row == null ? null : row.getPrivateKeyPkcs8Base64();
    }

    /**
     * 从可空商户响应密钥记录读取 X.509 公钥正文。
     *
     * @param row 商户响应密钥记录
     * @return 公钥正文或 null
     */
    private String responsePublicKey(BaseMerchantResponseKeyDO row) {
        return row == null ? null : row.getPublicKeyX509Base64();
    }

    /**
     * 读取 JWT 算法，历史空值使用平台固定默认算法。
     *
     * @param row JWT 密钥记录
     * @return JWT 算法
     */
    private String jwtAlgorithm(BaseMerchantJwtKeyDO row) {
        return nullToDefault(row == null ? null : row.getAlgorithm(), JWT_ALGORITHM);
    }

    /**
     * 读取 JWT 有效期秒数，历史空值使用受控默认值。
     *
     * @param row JWT 密钥记录
     * @return JWT 有效期秒数
     */
    private Long jwtExpiresSeconds(BaseMerchantJwtKeyDO row) {
        return nullToDefault(row == null ? null : row.getExpiresSeconds(), 180L);
    }

    /**
     * 校验密钥材料或配置文本存在，并只返回去除首尾空白后的值。
     *
     * @param value 待校验文本
     * @param message 缺失时使用的受控错误文案
     * @return 规范化非空文本
     */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
        }
        return value.trim();
    }

    /**
     * 对空白文本使用受控默认值。
     *
     * @param value 候选文本
     * @param defaultValue 默认文本
     * @return 原值或默认值
     */
    private String nullToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 读取并校验商户接入包使用的 OpenAPI 基础地址。
     *
     * @return 非空基础地址
     */
    private String openApiBaseUrl() {
        String baseUrl = baseUrlResolver == null ? null : baseUrlResolver.resolve();
        return requireText(baseUrl, "OpenAPI 基础地址未配置");
    }

    /**
     * 对空数值使用受控默认值。
     *
     * @param value 候选数值
     * @param defaultValue 默认数值
     * @return 原值或默认值
     */
    private Long nullToDefault(Long value, Long defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * 同一管理操作读取的商户及三类密钥材料快照。
     *
     * @param merchant 商户记录
     * @param jwtKey 当前 JWT 密钥记录
     * @param platformKey 当前平台载荷密钥记录
     * @param responseKey 当前商户响应密钥记录
     */
    private record MaterialSnapshot(BaseMerchantInfoDO merchant,
                                    BaseMerchantJwtKeyDO jwtKey,
                                    BasePlatformPayloadKeyDO platformKey,
                                    BaseMerchantResponseKeyDO responseKey) {
    }
}
