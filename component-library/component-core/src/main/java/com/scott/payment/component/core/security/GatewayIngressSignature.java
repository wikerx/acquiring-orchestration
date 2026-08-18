package com.scott.payment.component.core.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayIngressSignature
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : 收银台 Gateway 入口 HMAC-SHA256 协议，统一网关签名和下游验签使用的规范化文本。
 * @status : create
 */
public final class GatewayIngressSignature {

    /** 唯一可信的收银台公网入口调用方。 */
    public static final String CALLER_SERVICE_GATEWAY = "service-gateway";
    /** Gateway 写入并由下游固定比对的调用方请求头。 */
    public static final String HEADER_CALLER = "X-Checkout-Gateway-Caller";
    /** Gateway 签名时使用的毫秒时间戳请求头。 */
    public static final String HEADER_TIMESTAMP = "X-Checkout-Gateway-Timestamp";
    /** Gateway 为每个转发请求生成的随机串请求头。 */
    public static final String HEADER_NONCE = "X-Checkout-Gateway-Nonce";
    /** 下游以常量时间比较的 HMAC-SHA256 签名请求头。 */
    public static final String HEADER_SIGNATURE = "X-Checkout-Gateway-Signature";
    /** 共享密钥最低字符数，实际部署建议使用 32 字节以上随机值。 */
    public static final int MINIMUM_SECRET_LENGTH = 32;

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String LINE_SEPARATOR = "\n";

    private GatewayIngressSignature() {
    }

    /**
     * 计算固定调用方为 service-gateway 的入口签名。
     *
     * @param method HTTP 方法
     * @param requestTarget 原始路径及查询串
     * @param timestamp 毫秒时间戳
     * @param nonce 单次请求随机串
     * @param secret Gateway 与下游共享的独立密钥
     * @return 小写十六进制 HMAC-SHA256
     */
    public static String sign(String method, String requestTarget, long timestamp, String nonce, String secret) {
        Objects.requireNonNull(method, "method can not be null");
        Objects.requireNonNull(requestTarget, "requestTarget can not be null");
        Objects.requireNonNull(nonce, "nonce can not be null");
        Objects.requireNonNull(secret, "secret can not be null");
        String canonicalText = method.toUpperCase(Locale.ROOT)
                + LINE_SEPARATOR + requestTarget
                + LINE_SEPARATOR + timestamp
                + LINE_SEPARATOR + nonce
                + LINE_SEPARATOR + CALLER_SERVICE_GATEWAY;
        return hmacSha256(canonicalText, secret);
    }

    /**
     * 使用常量时间比较入口签名。
     *
     * @param expectedSignature 下游重新计算的签名
     * @param actualSignature 请求携带的签名
     * @return 两个签名是否一致
     */
    public static boolean matches(String expectedSignature, String actualSignature) {
        if (expectedSignature == null || actualSignature == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                actualSignature.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 组合 Servlet 与 WebFlux 都能取得的原始请求目标。
     *
     * @param rawPath 原始请求路径
     * @param rawQuery 原始查询串，可为空
     * @return 用于签名的路径和查询串
     */
    public static String requestTarget(String rawPath, String rawQuery) {
        Objects.requireNonNull(rawPath, "rawPath can not be null");
        return rawQuery == null || rawQuery.isBlank() ? rawPath : rawPath + "?" + rawQuery;
    }

    /**
     * 判断共享密钥是否满足最低强度，避免空配置形成固定签名。
     *
     * @param secret 待检查密钥
     * @return 至少 32 个字符时返回 true
     */
    public static boolean isConfiguredSecret(String secret) {
        return secret != null && secret.length() >= MINIMUM_SECRET_LENGTH;
    }

    /**
     * 判断请求路径是否属于必须经过 service-gateway 的收银台后端入口。
     *
     * @param path 不含查询串的原始请求路径
     * @return 商户建单、付款人 API、公开配置和收银台健康入口返回 true
     */
    public static boolean isProtectedCheckoutPath(String path) {
        return path != null && (path.equals("/api/rest/checkout")
                || path.startsWith("/api/rest/checkout/")
                || path.equals("/checkout/api")
                || path.startsWith("/checkout/api/")
                || path.equals("/checkout/config")
                || path.startsWith("/checkout/config/")
                || path.equals("/checkout/health"));
    }

    private static String hmacSha256(String content, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("gateway ingress signature can not be calculated", exception);
        }
    }
}
