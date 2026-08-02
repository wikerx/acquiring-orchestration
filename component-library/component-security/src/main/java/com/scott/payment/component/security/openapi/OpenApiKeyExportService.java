package com.scott.payment.component.security.openapi;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyExportService
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : Open API Key Export Service 服务契约，位于 公共组件库，声明当前业务能力的输入、返回结果和异常边界，由实现类保持一致。
 * @status : create
 */
public class OpenApiKeyExportService {

    /**
     * TEXT CONTENT TYPE，用于区分 Open API Key Export Service 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TEXT_CONTENT_TYPE = "text/plain;charset=UTF-8";
    /**
     * PROPERTIES CONTENT TYPE，用于区分 Open API Key Export Service 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String PROPERTIES_CONTENT_TYPE = "text/plain;charset=UTF-8";
    /**
     * ZIP CONTENT TYPE，用于区分 Open API Key Export Service 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String ZIP_CONTENT_TYPE = "application/zip";

    /** Gateway 中所有商户 OpenAPI 的统一外部前缀。 */
    private static final String OPENAPI_BASE_PATH = "/api/rest";

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
     * 创建 OpenAPI 接入材料导出服务。
     *
     * @param exportProperties OpenAPI 商户接入材料导出配置
     */
    public OpenApiKeyExportService(OpenApiMerchantKeyExportProperties exportProperties) {
        this(() -> exportProperties == null ? null : exportProperties.getOpenApiBaseUrl());
    }

    /**
     * 创建 OpenAPI 接入材料导出服务。
     *
     * @param baseUrlResolver 商户 OpenAPI 外部地址解析器
     */
    public OpenApiKeyExportService(OpenApiBaseUrlResolver baseUrlResolver) {
        this.baseUrlResolver = baseUrlResolver;
    }

    /**
     * 生成平台请求加密公钥文件。
     *
     * @param merchantId 商户号
     * @param publicKey  平台请求加密 X.509 公钥 Base64
     * @param format     导出格式
     * @return 下载文件
     */
    public OpenApiKeyDownloadFile platformPublicKeyFile(String merchantId, String publicKey, OpenApiKeyExportFormat format) {
        if (format == OpenApiKeyExportFormat.PEM) {
            return textFile("platform-public-key.pem", OpenApiPemUtils.toPublicKeyPem(publicKey));
        }
        if (format == OpenApiKeyExportFormat.TXT || format == OpenApiKeyExportFormat.TEXT) {
            return textFile(merchantId + "-platform-public-key.txt", publicKey);
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "平台公钥仅支持 TXT 或 PEM");
    }

    /**
     * 生成平台请求解密私钥文件。该材料只能在平台管理端受控导出，不能进入商户接入包。
     *
     * @param merchantId 商户号
     * @param privateKey 平台请求 PKCS#8 私钥 Base64
     * @param format     导出格式
     * @return 下载文件
     */
    public OpenApiKeyDownloadFile platformPrivateKeyFile(String merchantId, String privateKey, OpenApiKeyExportFormat format) {
        if (format == OpenApiKeyExportFormat.PEM) {
            return textFile("platform-private-key.pem", OpenApiPemUtils.toPrivateKeyPem(privateKey));
        }
        if (format == OpenApiKeyExportFormat.TXT || format == OpenApiKeyExportFormat.TEXT) {
            return textFile(merchantId + "-platform-private-key.txt", privateKey);
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "平台请求私钥仅支持 TXT 或 PEM");
    }

    /**
     * 生成商户响应解密私钥文件。
     *
     * @param merchantId  商户号
     * @param privateKey  商户响应 PKCS#8 私钥 Base64
     * @param format      导出格式
     * @return 下载文件
     */
    public OpenApiKeyDownloadFile merchantResponsePrivateKeyFile(String merchantId, String privateKey, OpenApiKeyExportFormat format) {
        if (format == OpenApiKeyExportFormat.PEM) {
            return textFile("merchant-response-private-key.pem", OpenApiPemUtils.toPrivateKeyPem(privateKey));
        }
        if (format == OpenApiKeyExportFormat.TXT || format == OpenApiKeyExportFormat.TEXT) {
            return textFile(merchantId + "-merchant-response-private-key.txt", privateKey);
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户响应私钥仅支持 TXT 或 PEM");
    }

    /**
     * 生成商户响应公钥文件，供平台侧排查或商户确认响应密钥对一致性。
     *
     * @param merchantId 商户号
     * @param publicKey  商户响应 X.509 公钥 Base64
     * @param format     导出格式
     * @return 下载文件
     */
    public OpenApiKeyDownloadFile merchantResponsePublicKeyFile(String merchantId, String publicKey, OpenApiKeyExportFormat format) {
        if (format == OpenApiKeyExportFormat.PEM) {
            return textFile("merchant-response-public-key.pem", OpenApiPemUtils.toPublicKeyPem(publicKey));
        }
        if (format == OpenApiKeyExportFormat.TXT || format == OpenApiKeyExportFormat.TEXT) {
            return textFile(merchantId + "-merchant-response-public-key.txt", publicKey);
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户响应公钥仅支持 TXT 或 PEM");
    }

    /**
     * 生成商户完整 SDK 接入包。包内只包含商户应保存的材料，不包含平台请求解密私钥。
     *
     * @param context 接入材料上下文
     * @return ZIP 下载文件
     */
    public OpenApiKeyDownloadFile sdkKit(OpenApiKeyExportContext context) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                addZipEntry(zip, "merchant-config.properties", merchantConfig(context));
                addZipEntry(zip, "merchant-config-text.properties", merchantConfigText(context));
                addZipEntry(zip, "keys/platform-public-key.pem", OpenApiPemUtils.toPublicKeyPem(context.platformPublicKey()));
                addZipEntry(zip, "keys/merchant-response-private-key.pem", OpenApiPemUtils.toPrivateKeyPem(context.merchantResponsePrivateKey()));
                addZipEntry(zip, "README-KEYS.txt", readme());
            }
            return new OpenApiKeyDownloadFile("merchant-openapi-kit-" + context.merchantId() + ".zip", ZIP_CONTENT_TYPE, outputStream.toByteArray());
        } catch (IOException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "OpenAPI 接入包生成失败");
        }
    }

    /**
     * 生成路径版 SDK 配置文件。
     *
     * @param context 接入材料上下文
     * @return properties 文本
     */
    public String merchantConfig(OpenApiKeyExportContext context) {
        return ""
                + "# OpenAPI SDK 配置文件路径版。推荐与 keys/ 目录一起放入 classpath。\n"
                + "merchant.id=" + context.merchantId() + "\n"
                + "merchant.jwt.secret=" + context.merchantJwtSecret() + "\n"
                + "merchant.openapi.base-url=" + openApiBaseUrl() + "\n"
                + "merchant.platform.public-key-file=classpath:keys/platform-public-key.pem\n"
                + "merchant.response.private-key-file=classpath:keys/merchant-response-private-key.pem\n";
    }

    /**
     * 生成文本版 SDK 配置文件。
     *
     * @param context 接入材料上下文
     * @return properties 文本
     */
    public String merchantConfigText(OpenApiKeyExportContext context) {
        return ""
                + "# OpenAPI SDK 配置文本版。私钥和 JWT 密钥请勿提交 Git 或写入日志。\n"
                + "merchant.id=" + context.merchantId() + "\n"
                + "merchant.jwt.secret=" + context.merchantJwtSecret() + "\n"
                + "merchant.openapi.base-url=" + openApiBaseUrl() + "\n"
                + "merchant.platform.public-key=" + context.platformPublicKey() + "\n"
                + "merchant.response.private-key=" + context.merchantResponsePrivateKey() + "\n";
    }

    /**
     * 生成商户 JWT 签名密钥文本文件内容。
     *
     * @param context 接入材料上下文
     * @return TXT 文本
     */
    public String jwtText(OpenApiKeyExportContext context) {
        return ""
                + "merchant.id=" + context.merchantId() + "\n"
                + "merchant.jwt.secret=" + context.merchantJwtSecret() + "\n"
                + "jwt.algorithm=" + context.jwtAlgorithm() + "\n"
                + "jwt.expires-seconds=" + context.jwtExpiresSeconds() + "\n";
    }

    /**
     * 生成商户 JWT 签名密钥文本文件内容。
     * <p>
     * JWT 密钥导出只依赖商户号和 JWT 原始密钥，不能因为响应私钥缺失而失败。
     *
     * @param merchantId         商户号
     * @param merchantJwtSecret  商户 JWT HS256 密钥
     * @param jwtAlgorithm       JWT 算法
     * @param jwtExpiresSeconds  JWT 有效期秒数
     * @return TXT 文本
     */
    public String jwtText(String merchantId, String merchantJwtSecret, String jwtAlgorithm, Long jwtExpiresSeconds) {
        return ""
                + "merchant.id=" + merchantId + "\n"
                + "merchant.jwt.secret=" + merchantJwtSecret + "\n"
                + "jwt.algorithm=" + jwtAlgorithm + "\n"
                + "jwt.expires-seconds=" + jwtExpiresSeconds + "\n";
    }

    /**
     * 创建普通文本下载文件。
     *
     * @param fileName 文件名
     * @param content  文件内容
     * @return 下载文件
     */
    public OpenApiKeyDownloadFile textFile(String fileName, String content) {
        return new OpenApiKeyDownloadFile(fileName, TEXT_CONTENT_TYPE, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 创建 properties 下载文件。
     *
     * @param fileName 文件名
     * @param content  文件内容
     * @return 下载文件
     */
    public OpenApiKeyDownloadFile propertiesFile(String fileName, String content) {
        return new OpenApiKeyDownloadFile(fileName, PROPERTIES_CONTENT_TYPE, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 创建zipentry，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 公共组件库 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param zip zip 输入值，参与 zip 的查询、校验、转换、写入或日志摘要
     * @param name name 输入值，参与 name 的查询、校验、转换、写入或日志摘要
     * @param content content 输入值，参与 content 的查询、校验、转换、写入或日志摘要
     */
    private void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /**
     * 规范化readme，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String readme() {
        return ""
                + "1. 推荐将 merchant-config.properties 放到商户服务端 classpath 根目录。\n"
                + "2. 推荐将 keys/ 目录放到 classpath 下。\n"
                + "3. 生产环境也可以使用 file:/ 开头的外部文件路径。\n"
                + "4. merchant-response-private-key.pem 是商户响应私钥，请勿提交 Git、请勿上传前端、请勿打印日志。\n"
                + "5. 如密钥泄露，请立即在管理系统或商户系统中轮换密钥。\n";
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
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "OpenAPI 基础地址未配置");
        }
        String normalizedBaseUrl = baseUrl.trim();
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        if (normalizedBaseUrl.endsWith(OPENAPI_BASE_PATH)) {
            return normalizedBaseUrl + "/";
        }
        return normalizedBaseUrl + OPENAPI_BASE_PATH + "/";
    }

    /**
     * 商户接入材料导出上下文，所有字段都来自数据库中的原始密钥材料。
     *
     * @param merchantId                 商户号
     * @param merchantJwtSecret          商户 JWT HS256 密钥
     * @param jwtAlgorithm               JWT 算法
     * @param jwtExpiresSeconds          JWT 有效期秒数
     * @param platformPublicKey          平台请求加密公钥 Base64
     * @param merchantResponsePrivateKey 商户响应解密私钥 Base64
     */
    public record OpenApiKeyExportContext(String merchantId,
                                          String merchantJwtSecret,
                                          String jwtAlgorithm,
                                          Long jwtExpiresSeconds,
                                          String platformPublicKey,
                                          String merchantResponsePrivateKey) {
    }
}
