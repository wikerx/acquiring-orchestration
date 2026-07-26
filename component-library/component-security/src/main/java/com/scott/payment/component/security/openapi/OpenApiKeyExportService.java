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
 * @description : OpenApiKeyExportService 服务契约，用于声明业务能力、调用边界和返回结果约束，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class OpenApiKeyExportService {

    /**
     * TEXT CONTENT TYPE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TEXT_CONTENT_TYPE = "text/plain;charset=UTF-8";
    /**
     * PROPERTIES CONTENT TYPE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String PROPERTIES_CONTENT_TYPE = "text/plain;charset=UTF-8";
    /**
     * ZIP CONTENT TYPE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String ZIP_CONTENT_TYPE = "application/zip";

    /**
     * base Url Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
     * 执行 add Zip Entry 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 OpenApiKeyExportService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param zip zip 输入值，含义由调用方法名称和所属业务对象限定
     * @param name name 输入值，含义由调用方法名称和所属业务对象限定
     * @param content content 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /**
     * 执行 readme 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 OpenApiKeyExportService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 open Api Base Url 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 OpenApiKeyExportService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String openApiBaseUrl() {
        String baseUrl = baseUrlResolver == null ? null : baseUrlResolver.resolve();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "OpenAPI 基础地址未配置");
        }
        return baseUrl.trim();
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
