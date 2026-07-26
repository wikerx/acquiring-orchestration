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
     * HMAC SHA256 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String HMAC_SHA256 = "HmacSHA256";
    /**
     * LINE SEPARATOR 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
     * 完成 hmac Sha256 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 InternalServiceSignature 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param canonicalText canonical Text 输入值，含义由调用方法名称和所属业务对象限定
     * @param secret secret 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
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

    /**
     * 完成 canonical Text 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 InternalServiceSignature 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param method method 输入值，含义由调用方法名称和所属业务对象限定
     * @param path path 输入值，含义由调用方法名称和所属业务对象限定
     * @param timestamp 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param nonce nonce 输入值，含义由调用方法名称和所属业务对象限定
     * @param caller caller 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private static String canonicalText(String method, String path, long timestamp, String nonce, String caller) {
        return method.toUpperCase(Locale.ROOT)
                + LINE_SEPARATOR + path
                + LINE_SEPARATOR + timestamp
                + LINE_SEPARATOR + nonce
                + LINE_SEPARATOR + caller;
    }
}
