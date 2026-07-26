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
     * HMAC SHA 256，用于保存 Internal Service Signature 中与 hmacsha256 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String HMAC_SHA256 = "HmacSHA256";
    /**
     * LINE SEPARATOR，用于保存 Internal Service Signature 中与 lineseparator 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 规范化hmacsha256，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param canonicalText canonical Text 输入值，参与 规范化文本 的查询、校验、转换、写入或日志摘要
     * @param secret secret 输入值，参与 secret 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 整理规范化文本，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param method HTTP 方法或内部调用方法名，用于构造请求、签名或异常摘要
     * @param path 请求地址或路径，用于定位内部服务、渠道接口或商户回调目标
     * @param timestamp 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param nonce nonce 输入值，参与 nonce 的查询、校验、转换、写入或日志摘要
     * @param caller caller 输入值，参与 caller 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private static String canonicalText(String method, String path, long timestamp, String nonce, String caller) {
        return method.toUpperCase(Locale.ROOT)
                + LINE_SEPARATOR + path
                + LINE_SEPARATOR + timestamp
                + LINE_SEPARATOR + nonce
                + LINE_SEPARATOR + caller;
    }
}
