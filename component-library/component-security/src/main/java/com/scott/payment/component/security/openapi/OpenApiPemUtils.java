package com.scott.payment.component.security.openapi;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * OpenAPI PEM 密钥格式工具，统一处理商户接入材料中的 Base64、PEM 和指纹展示。
 * <p>
 * 该工具只转换同一份原始密钥数据的展示形态，不生成新密钥，也不会在异常信息中带出密钥原文。
 */
public final class OpenApiPemUtils {

    /**
     * PEM 正文每行固定 64 字符，兼容 OpenSSL、Java、PHP 和 Go 等常见运行时。
     */
    private static final int PEM_LINE_LENGTH = 64;

    private static final String PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_KEY_END = "-----END PUBLIC KEY-----";
    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";

    private OpenApiPemUtils() {
    }

    /**
     * 将 X.509 DER Base64 公钥转换成标准 PEM 文本。
     *
     * @param x509Base64 X.509 DER Base64 公钥，也允许传入已有 PEM 文本
     * @return PUBLIC KEY PEM 文本
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
    public static String toPrivateKeyPem(String pkcs8Base64) {
        return toPem(pkcs8Base64, PRIVATE_KEY_BEGIN, PRIVATE_KEY_END);
    }

    /**
     * 归一化 Base64 或 PEM 密钥文本，得到可用于 JCA 解析的 DER Base64 正文。
     *
     * @param pemOrBase64 PEM 或 Base64 密钥文本
     * @return 去掉 PEM 头尾和空白字符后的 Base64 文本
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
