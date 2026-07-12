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

    private static final String HMAC_SHA256 = "HmacSHA256";
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
