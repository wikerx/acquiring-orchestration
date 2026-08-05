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
 * @description : Open API Merchant Key Material Service 服务契约，位于 公共组件库，声明当前业务能力的输入、返回结果和异常边界，由实现类保持一致。
 * @status : create
 */
public class OpenApiMerchantKeyMaterialService {

    /**
     * NOT DELETED，用于保存 Open API Merchant Key Material Service 中与 notdeleted 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int NOT_DELETED = 0;
    /**
     * ENABLED，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * COPY EXPIRE SECONDS，用于保存 Open API Merchant Key Material Service 中与 copyexpireseconds 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int COPY_EXPIRE_SECONDS = 60;
    /**
     * JWT ALGORITHM，用于保存 Open API Merchant Key Material Service 中与 jwtalgorithm 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String JWT_ALGORITHM = "HS256";
    /**
     * PAYLOAD ALGORITHM，表示请求体、响应体或消息载荷，日志中只能保留脱敏摘要。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String PAYLOAD_ALGORITHM = "RSA-OAEP-256+A256GCM";
    /**
     * KEY VERSION FORMATTER，用于保存 Open API Merchant Key Material Service 中与 密钥versionformatter 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final DateTimeFormatter KEY_VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * merchant Info Mapper 依赖，用于 Open API Merchant Key Material Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * export Properties，用于保存 Open API Merchant Key Material Service 中与 exportproperties 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final OpenApiMerchantKeyExportProperties exportProperties;
    /**
     * base URL Resolver，表示回调、通知、来源站点或远程接口地址。
     * <p>
     * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度和协议由调用方校验；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 整理rotate商户响应密钥，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
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
     * 整理平台public密钥file，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param snapshot snapshot 输入值，参与 snapshot 的查询、校验、转换、写入或日志摘要
     * @param format format 输入值，参与 format 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private OpenApiKeyDownloadFile platformPublicKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String publicKey = requireText(publicKey(snapshot.platformKey()), "平台请求公钥未配置");
        return keyExportService.platformPublicKeyFile(merchantId, publicKey, format);
    }

    /**
     * 整理平台私钥密钥file，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param snapshot snapshot 输入值，参与 snapshot 的查询、校验、转换、写入或日志摘要
     * @param format format 输入值，参与 format 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private OpenApiKeyDownloadFile platformPrivateKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String privateKey = requireText(platformPrivateKey(snapshot.platformKey()), "平台请求私钥未配置");
        return keyExportService.platformPrivateKeyFile(merchantId, privateKey, format);
    }

    /**
     * 整理商户响应私钥密钥file，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param snapshot snapshot 输入值，参与 snapshot 的查询、校验、转换、写入或日志摘要
     * @param format format 输入值，参与 format 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private OpenApiKeyDownloadFile merchantResponsePrivateKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String privateKey = requireText(privateKey(snapshot.responseKey()), "商户响应私钥未配置");
        return keyExportService.merchantResponsePrivateKeyFile(merchantId, privateKey, format);
    }

    /**
     * 整理商户响应public密钥file，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param snapshot snapshot 输入值，参与 snapshot 的查询、校验、转换、写入或日志摘要
     * @param format format 输入值，参与 format 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private OpenApiKeyDownloadFile merchantResponsePublicKeyFile(MaterialSnapshot snapshot, OpenApiKeyExportFormat format) {
        String merchantId = snapshot.merchant().getMerchantId();
        String publicKey = requireText(responsePublicKey(snapshot.responseKey()), "商户响应公钥未配置");
        return keyExportService.merchantResponsePublicKeyFile(merchantId, publicKey, format);
    }

    /**
     * 规范化sdkkit，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param snapshot snapshot 输入值，参与 snapshot 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private OpenApiKeyDownloadFile sdkKit(MaterialSnapshot snapshot) {
        return keyExportService.sdkKit(exportContext(snapshot));
    }

    /**
     * 构造content对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param snapshot snapshot 输入值，参与 snapshot 的查询、校验、转换、写入或日志摘要
     * @param keyType 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 规范化exportcontext，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param snapshot snapshot 输入值，参与 snapshot 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 构造jwt对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 公共组件库 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param vo VO 输入值，参与 vo 的查询、校验、转换、写入或日志摘要
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
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
     * 构造platform对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 公共组件库 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param vo VO 输入值，参与 vo 的查询、校验、转换、写入或日志摘要
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
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
     * 构造响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 公共组件库 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param vo VO 输入值，参与 vo 的查询、校验、转换、写入或日志摘要
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
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
     * 查询snapshot，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
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
     * 查询商户，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
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
     * 查询jwt密钥，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private BaseMerchantJwtKeyDO selectJwtKey(String merchantId) {
        return jwtKeyMapper.selectOne(Wrappers.<BaseMerchantJwtKeyDO>lambdaQuery()
                .eq(BaseMerchantJwtKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantJwtKeyDO::getDeleted, NOT_DELETED)
                .eq(BaseMerchantJwtKeyDO::getEnabled, ENABLED)
                .orderByDesc(BaseMerchantJwtKeyDO::getEffectiveTime)
                .last("LIMIT 1"));
    }

    /** 查询最新 JWT 记录，包括已停用记录，供显式启停操作使用。 */
    private BaseMerchantJwtKeyDO selectLatestJwtKey(String merchantId) {
        return jwtKeyMapper.selectOne(Wrappers.<BaseMerchantJwtKeyDO>lambdaQuery()
                .eq(BaseMerchantJwtKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantJwtKeyDO::getDeleted, NOT_DELETED)
                .orderByDesc(BaseMerchantJwtKeyDO::getEffectiveTime)
                .orderByDesc(BaseMerchantJwtKeyDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 查询platform密钥，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private BasePlatformPayloadKeyDO selectPlatformKey(String merchantId) {
        return platformPayloadKeyMapper.selectOne(Wrappers.<BasePlatformPayloadKeyDO>lambdaQuery()
                .eq(BasePlatformPayloadKeyDO::getMerchantId, merchantId)
                .eq(BasePlatformPayloadKeyDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    /**
     * 查询response密钥，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private BaseMerchantResponseKeyDO selectResponseKey(String merchantId) {
        return responseKeyMapper.selectOne(Wrappers.<BaseMerchantResponseKeyDO>lambdaQuery()
                .eq(BaseMerchantResponseKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantResponseKeyDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    /**
     * 校验密钥type输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private OpenApiKeyType requireKeyType(OpenApiKeyExportRequest request) {
        if (request == null || request.getKeyType() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "keyType 不能为空");
        }
        return request.getKeyType();
    }

    /**
     * 整理默认downloadformat，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param keyType 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 计算fingerprint摘要，用不可逆指纹关联原始内容而不暴露明文。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String fingerprint(String value) {
        return StringUtils.hasText(value) ? keyMaterialFactory.fingerprint(value) : null;
    }

    /**
     * 整理状态，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param enabled enabled 输入值，参与 enabled 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String status(Integer enabled) {
        return Objects.equals(enabled, ENABLED) ? "ENABLED" : "DISABLED";
    }

    /**
     * 规范化secret，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String secret(BaseMerchantJwtKeyDO row) {
        return row == null ? null : row.getMerchantKey();
    }

    /**
     * 整理public密钥，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String publicKey(BasePlatformPayloadKeyDO row) {
        return row == null ? null : row.getPublicKeyX509Base64();
    }

    /**
     * 整理平台私钥密钥，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String platformPrivateKey(BasePlatformPayloadKeyDO row) {
        return row == null ? null : row.getPrivateKeyPkcs8Base64();
    }

    /**
     * 整理私钥密钥，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String privateKey(BaseMerchantResponseKeyDO row) {
        return row == null ? null : row.getPrivateKeyPkcs8Base64();
    }

    /**
     * 整理响应public密钥，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String responsePublicKey(BaseMerchantResponseKeyDO row) {
        return row == null ? null : row.getPublicKeyX509Base64();
    }

    /**
     * 规范化jwtalgorithm，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String jwtAlgorithm(BaseMerchantJwtKeyDO row) {
        return nullToDefault(row == null ? null : row.getAlgorithm(), JWT_ALGORITHM);
    }

    /**
     * 规范化jwtexpiresseconds，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Long jwtExpiresSeconds(BaseMerchantJwtKeyDO row) {
        return nullToDefault(row == null ? null : row.getExpiresSeconds(), 180L);
    }

    /**
     * 校验文本输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
        }
        return value.trim();
    }

    /**
     * 整理nullto默认，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param defaultValue default Value 输入值，参与 默认值 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String nullToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 整理openapi基础url，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String openApiBaseUrl() {
        String baseUrl = baseUrlResolver == null ? null : baseUrlResolver.resolve();
        return requireText(baseUrl, "OpenAPI 基础地址未配置");
    }

    /**
     * 整理nullto默认，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param defaultValue default Value 输入值，参与 默认值 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
