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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : OpenAPI 商户接入材料导出服务，统一生成 TXT、PEM、properties 和完整 SDK 接入包。
 * @status : create
 */
public class OpenApiKeyExportService {

    /**
     * 商户 OpenAPI固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String TEXT_CONTENT_TYPE = "text/plain;charset=UTF-8";
    /**
     * 商户 OpenAPI固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String PROPERTIES_CONTENT_TYPE = "text/plain;charset=UTF-8";
    /**
     * 商户 OpenAPI固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String ZIP_CONTENT_TYPE = "application/zip";

    /**
     * 商户 OpenAPI业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final OpenApiMerchantKeyExportProperties exportProperties;

    /**
     * 创建 OpenAPI 接入材料导出服务。
     *
     * @param exportProperties OpenAPI 商户接入材料导出配置
     */
    public OpenApiKeyExportService(OpenApiMerchantKeyExportProperties exportProperties) {
        this.exportProperties = exportProperties;
    }

    /**
     * 生成平台请求加密公钥文件。
     *
     * @param merchantId 商户号
     * @param publicKey  平台请求加密 X.509 公钥 Base64
     * @param format     导出格式
     * @return 下载文件
     */
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param publicKey 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param format 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param privateKey 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param format 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param privateKey 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param format 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param publicKey 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param format 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param context 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param context 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param context 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param context 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param merchantJwtSecret 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param jwtAlgorithm 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param jwtExpiresSeconds 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param fileName 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param content 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param fileName 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param content 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public OpenApiKeyDownloadFile propertiesFile(String fileName, String content) {
        return new OpenApiKeyDownloadFile(fileName, PROPERTIES_CONTENT_TYPE, content.getBytes(StandardCharsets.UTF_8));
    }

    private void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String readme() {
        return ""
                + "1. 推荐将 merchant-config.properties 放到商户服务端 classpath 根目录。\n"
                + "2. 推荐将 keys/ 目录放到 classpath 下。\n"
                + "3. 生产环境也可以使用 file:/ 开头的外部文件路径。\n"
                + "4. merchant-response-private-key.pem 是商户响应私钥，请勿提交 Git、请勿上传前端、请勿打印日志。\n"
                + "5. 如密钥泄露，请立即在管理系统或商户系统中轮换密钥。\n";
    }

    private String openApiBaseUrl() {
        String baseUrl = exportProperties.getOpenApiBaseUrl();
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
