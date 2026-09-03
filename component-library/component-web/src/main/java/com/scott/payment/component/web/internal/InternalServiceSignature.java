package com.scott.payment.component.web.internal;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalServiceSignature
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 内部服务 HMAC-SHA256 签名工具，统一构造签名文本并提供常量时间验签能力。
 * @status : create
 */
public final class InternalServiceSignature {

    /**
     * 调用方服务标识请求头。
     */
    public static final String HEADER_CALLER = "X-Internal-Caller";

    /**
     * 请求时间戳请求头，单位为毫秒。
     */
    public static final String HEADER_TIMESTAMP = "X-Internal-Timestamp";

    /**
     * 请求随机串请求头。
     */
    public static final String HEADER_NONCE = "X-Internal-Nonce";

    /**
     * HMAC-SHA256 签名请求头。
     */
    public static final String HEADER_SIGNATURE = "X-Internal-Signature";

    /**
     * {@code HMAC_SHA256}常量，统一 {@code InternalServiceSignature} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String HMAC_SHA256 = "HmacSHA256";
    /**
     * {@code LINE_SEPARATOR}常量，统一 {@code InternalServiceSignature} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String LINE_SEPARATOR = "\n";

    private InternalServiceSignature() {
    }

    /**
     * 计算内部服务调用签名。
     *
     * @param method    HTTP 方法
     * @param path      请求路径
     * @param timestamp 毫秒时间戳
     * @param nonce     请求随机串
     * @param caller    调用方服务标识
     * @param secret    共享密钥
     * @return 小写十六进制 HMAC-SHA256 签名
     */
    public static String sign(String method, String path, long timestamp, String nonce, String caller, String secret) {
        String canonicalText = canonicalText(method, path, timestamp, nonce, caller);
        return hmacSha256(canonicalText, secret);
    }

    /**
     * 计算携带请求体摘要的 HMAC-SHA256 签名。
     * <p>
     * 渠道回调这类外部通知入口必须把原始 body 摘要纳入签名文本，避免路径和时间戳合法但业务报文被替换。
     *
     * @param method        HTTP 方法
     * @param path          请求路径
     * @param timestamp     毫秒时间戳
     * @param nonce         请求随机串
     * @param caller        调用方或渠道标识
     * @param payloadSha256 原始请求体 UTF-8 SHA-256 小写十六进制摘要
     * @param secret        共享密钥
     * @return 小写十六进制 HMAC-SHA256 签名
     */
    public static String sign(String method,
                              String path,
                              long timestamp,
                              String nonce,
                              String caller,
                              String payloadSha256,
                              String secret) {
        String canonicalText = canonicalText(method, path, timestamp, nonce, caller)
                + LINE_SEPARATOR + (payloadSha256 == null ? "" : payloadSha256);
        return hmacSha256(canonicalText, secret);
    }

    /**
     * 构造内部调用签名使用的原始请求目标。
     *
     * @param rawPath 原始 URI 路径
     * @param rawQuery 原始查询字符串，允许为空
     * @return 原始路径，存在查询参数时追加问号和原始查询字符串
     */
    public static String requestTarget(String rawPath, String rawQuery) {
        String path = rawPath == null || rawPath.isBlank() ? "/" : rawPath;
        return rawQuery == null || rawQuery.isBlank() ? path : path + "?" + rawQuery;
    }

    /**
     * 计算实际 HTTP 请求体字节的 SHA-256 摘要。
     *
     * @param payload 原始请求体字节；空正文使用零字节摘要
     * @return 64 位小写十六进制 SHA-256 摘要
     */
    public static String payloadSha256(byte[] payload) {
        try {
            byte[] source = payload == null ? new byte[0] : payload;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "internal service payload digest can not be calculated");
        }
    }

    /**
     * 按 UTF-8 计算字符串请求体的 SHA-256 摘要。
     *
     * @param payload 已完成最终序列化的请求体；null 表示空正文
     * @return 64 位小写十六进制 SHA-256 摘要
     */
    public static String payloadSha256(String payload) {
        return payloadSha256(payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256(String canonicalText, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(canonicalText.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "internal service signature can not be calculated");
        }
    }

    /**
     * 常量时间比较内部服务签名。
     *
     * @param expectedSignature 服务端计算签名
     * @param actualSignature   请求头签名
     * @return 签名是否一致
     */
    public static boolean matches(String expectedSignature, String actualSignature) {
        if (expectedSignature == null || actualSignature == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                actualSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 当前毫秒时间戳。
     *
     * @return 当前毫秒时间戳
     */
    public static long currentTimeMillis() {
        return Instant.now().toEpochMilli();
    }

    private static String canonicalText(String method, String path, long timestamp, String nonce, String caller) {
        return method.toUpperCase(Locale.ROOT)
                + LINE_SEPARATOR + path
                + LINE_SEPARATOR + timestamp
                + LINE_SEPARATOR + nonce
                + LINE_SEPARATOR + caller;
    }
}
