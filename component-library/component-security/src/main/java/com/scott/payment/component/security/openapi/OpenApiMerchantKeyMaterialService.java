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
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantKeyMaterialService
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : OpenApiMerchantKeyMaterialService 服务契约，用于声明业务能力、调用边界和返回结果约束，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class OpenApiMerchantKeyMaterialService {

    /**
     * NOT DELETED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int NOT_DELETED = 0;
    /**
     * ENABLED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * COPY EXPIRE SECONDS 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int COPY_EXPIRE_SECONDS = 60;
    /**
     * JWT ALGORITHM 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String JWT_ALGORITHM = "HS256";
    /**
     * PAYLOAD ALGORITHM 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String PAYLOAD_ALGORITHM = "RSA-OAEP-256+A256GCM";
    /**
     * KEY VERSION FORMATTER 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final DateTimeFormatter KEY_VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * merchant Info Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
    /**
     * export Properties 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final OpenApiMerchantKeyExportProperties exportProperties;
    /**
     * base Url Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
     * 完成 rotate Jwt Key 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
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
     * 完成 rotate Platform Payload Key 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
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
     * 完成 rotate Merchant Response Key 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
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
     * 完成 platform Public Key File 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param snapshot snapshot 输入值，含义由调用方法名称和所属业务对象限定
     * @param format format 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private OpenApiKeyDownloadFile platformPublicKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String publicKey = requireText(publicKey(snapshot.platformKey()), "平台请求公钥未配置");
        return keyExportService.platformPublicKeyFile(merchantId, publicKey, format);
    }

    /**
     * 完成 platform Private Key File 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param snapshot snapshot 输入值，含义由调用方法名称和所属业务对象限定
     * @param format format 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private OpenApiKeyDownloadFile platformPrivateKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String privateKey = requireText(platformPrivateKey(snapshot.platformKey()), "平台请求私钥未配置");
        return keyExportService.platformPrivateKeyFile(merchantId, privateKey, format);
    }

    /**
     * 完成 merchant Response Private Key File 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param snapshot snapshot 输入值，含义由调用方法名称和所属业务对象限定
     * @param format format 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private OpenApiKeyDownloadFile merchantResponsePrivateKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String privateKey = requireText(privateKey(snapshot.responseKey()), "商户响应私钥未配置");
        return keyExportService.merchantResponsePrivateKeyFile(merchantId, privateKey, format);
    }

    /**
     * 完成 merchant Response Public Key File 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param snapshot snapshot 输入值，含义由调用方法名称和所属业务对象限定
     * @param format format 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private OpenApiKeyDownloadFile merchantResponsePublicKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String publicKey = requireText(responsePublicKey(snapshot.responseKey()), "商户响应公钥未配置");
        return keyExportService.merchantResponsePublicKeyFile(merchantId, publicKey, format);
    }

    /**
     * 完成 sdk Kit 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param snapshot snapshot 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private OpenApiKeyDownloadFile sdkKit(MaterialSnapshot snapshot) {
        return keyExportService.sdkKit(exportContext(snapshot));
    }

    /**
     * 完成 copy Content 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param snapshot snapshot 输入值，含义由调用方法名称和所属业务对象限定
     * @param keyType key Type 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 export Context 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param snapshot snapshot 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 填充 fill Jwt 相关字段，保持来源对象与目标对象的业务含义一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param vo vo 输入值，含义由调用方法名称和所属业务对象限定
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
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
     * 填充 fill Platform 相关字段，保持来源对象与目标对象的业务含义一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param vo vo 输入值，含义由调用方法名称和所属业务对象限定
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
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
     * 填充 fill Response 相关字段，保持来源对象与目标对象的业务含义一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param vo vo 输入值，含义由调用方法名称和所属业务对象限定
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
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
     * 查询 load Snapshot 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 解析或查询得到的业务值
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
     * 查询 select Merchant 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 解析或查询得到的业务值
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
     * 查询 select Jwt Key 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 解析或查询得到的业务值
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
     * 查询 select Platform Key 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 解析或查询得到的业务值
     */
    private BasePlatformPayloadKeyDO selectPlatformKey(String merchantId) {
        return platformPayloadKeyMapper.selectOne(Wrappers.<BasePlatformPayloadKeyDO>lambdaQuery()
                .eq(BasePlatformPayloadKeyDO::getMerchantId, merchantId)
                .eq(BasePlatformPayloadKeyDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    /**
     * 查询 select Response Key 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 解析或查询得到的业务值
     */
    private BaseMerchantResponseKeyDO selectResponseKey(String merchantId) {
        return responseKeyMapper.selectOne(Wrappers.<BaseMerchantResponseKeyDO>lambdaQuery()
                .eq(BaseMerchantResponseKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantResponseKeyDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    /**
     * 强制校验 require Key Type 必填值，缺失时中断当前业务流程。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    private OpenApiKeyType requireKeyType(OpenApiKeyExportRequest request) {
        if (request == null || request.getKeyType() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "keyType 不能为空");
        }
        return request.getKeyType();
    }

    /**
     * 完成 default Download Format 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param keyType key Type 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 fingerprint 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private String fingerprint(String value) {
        return StringUtils.hasText(value) ? keyMaterialFactory.fingerprint(value) : null;
    }

    /**
     * 完成 status 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param enabled enabled 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String status(Integer enabled) {
        return Objects.equals(enabled, ENABLED) ? "ENABLED" : "DISABLED";
    }

    /**
     * 完成 secret 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String secret(BaseMerchantJwtKeyDO row) {
        return row == null ? null : row.getMerchantKey();
    }

    /**
     * 完成 public Key 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String publicKey(BasePlatformPayloadKeyDO row) {
        return row == null ? null : row.getPublicKeyX509Base64();
    }

    /**
     * 完成 platform Private Key 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String platformPrivateKey(BasePlatformPayloadKeyDO row) {
        return row == null ? null : row.getPrivateKeyPkcs8Base64();
    }

    /**
     * 完成 private Key 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String privateKey(BaseMerchantResponseKeyDO row) {
        return row == null ? null : row.getPrivateKeyPkcs8Base64();
    }

    /**
     * 完成 response Public Key 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String responsePublicKey(BaseMerchantResponseKeyDO row) {
        return row == null ? null : row.getPublicKeyX509Base64();
    }

    /**
     * 完成 jwt Algorithm 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String jwtAlgorithm(BaseMerchantJwtKeyDO row) {
        return nullToDefault(row == null ? null : row.getAlgorithm(), JWT_ALGORITHM);
    }

    /**
     * 完成 jwt Expires Seconds 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private Long jwtExpiresSeconds(BaseMerchantJwtKeyDO row) {
        return nullToDefault(row == null ? null : row.getExpiresSeconds(), 180L);
    }

    /**
     * 强制校验 require Text 必填值，缺失时中断当前业务流程。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     * @return 当前方法计算或转换后的业务结果
     */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
        }
        return value.trim();
    }

    /**
     * 完成 null To Default 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param defaultValue default Value 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String nullToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 完成 open Api Base Url 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    private String openApiBaseUrl() {
        String baseUrl = baseUrlResolver == null ? null : baseUrlResolver.resolve();
        return requireText(baseUrl, "OpenAPI 基础地址未配置");
    }

    /**
     * 完成 null To Default 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param defaultValue default Value 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private Long nullToDefault(Long value, Long defaultValue) {
        return value == null ? defaultValue : value;
    }

    private record MaterialSnapshot(BaseMerchantInfoDO merchant,
                                    BaseMerchantJwtKeyDO jwtKey,
                                    BasePlatformPayloadKeyDO platformKey,
                                    BaseMerchantResponseKeyDO responseKey) {
    }
}
