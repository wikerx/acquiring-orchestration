package com.scott.payment.component.security.openapi;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPemUtils
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Pem 工具，位于 component-library/component-security 的安全组件层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class OpenApiPemUtils {

    /**
     * PEM 正文每行固定 64 字符，兼容 OpenSSL、Java、PHP 和 Go 等常见运行时。
     */
    private static final int PEM_LINE_LENGTH = 64;

    /**
     * 商户 OpenAPI固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";
    /**
     * 商户 OpenAPI固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String PUBLIC_KEY_END = "-----END PUBLIC KEY-----";
    /**
     * 商户 OpenAPI固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    /**
     * 商户 OpenAPI固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";

    private OpenApiPemUtils() {
    }

    /**
     * 将 X.509 DER Base64 公钥转换成标准 PEM 文本。
     *
     * @param x509Base64 X.509 DER Base64 公钥，也允许传入已有 PEM 文本
     * @return PUBLIC KEY PEM 文本
     */
    /**
     * 转换商户 OpenAPI数据结构，避免数据库实体直接暴露到外部接口。
     * @param x509Base64 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String toPublicKeyPem(String x509Base64) {
        return toPem(x509Base64, PUBLIC_KEY_BEGIN, PUBLIC_KEY_END);
    }

    /**
     * 将 PKCS#8 DER Base64 私钥转换成标准 PEM 文本。
     *
     * @param pkcs8Base64 PKCS#8 DER Base64 私钥，也允许传入已有 PEM 文本
     * @return PRIVATE KEY PEM 文本
     */
    /**
     * 转换商户 OpenAPI数据结构，避免数据库实体直接暴露到外部接口。
     * @param pkcs8Base64 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String toPrivateKeyPem(String pkcs8Base64) {
        return toPem(pkcs8Base64, PRIVATE_KEY_BEGIN, PRIVATE_KEY_END);
    }

    /**
     * 归一化 Base64 或 PEM 密钥文本，得到可用于 JCA 解析的 DER Base64 正文。
     *
     * @param pemOrBase64 PEM 或 Base64 密钥文本
     * @return 去掉 PEM 头尾和空白字符后的 Base64 文本
     */
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param pemOrBase64 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String normalizePem(String pemOrBase64) {
        if (!StringUtils.hasText(pemOrBase64)) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "openapi key can not be blank");
        }
        return pemOrBase64
                .replace(PUBLIC_KEY_BEGIN, "")
                .replace(PUBLIC_KEY_END, "")
                .replace(PRIVATE_KEY_BEGIN, "")
                .replace(PRIVATE_KEY_END, "")
                .replaceAll("\\s", "");
    }

    /**
     * 计算密钥材料的 SHA-256 十六进制指纹，用于页面展示和审计比对。
     *
     * @param pemOrBase64 PEM 或 Base64 密钥文本
     * @return SHA-256 十六进制指纹
     */
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param pemOrBase64 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String sha256Fingerprint(String pemOrBase64) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizePem(pemOrBase64).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "openapi key fingerprint can not be calculated");
        }
    }

    private static String toPem(String value, String begin, String end) {
        String normalizedBase64 = normalizePem(value);
        StringBuilder builder = new StringBuilder(begin).append('\n');
        for (int index = 0; index < normalizedBase64.length(); index += PEM_LINE_LENGTH) {
            builder.append(normalizedBase64, index, Math.min(index + PEM_LINE_LENGTH, normalizedBase64.length())).append('\n');
        }
        return builder.append(end).toString();
    }
}
