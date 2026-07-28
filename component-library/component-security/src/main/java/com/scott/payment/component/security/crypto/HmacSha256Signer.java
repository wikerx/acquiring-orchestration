package com.scott.payment.component.security.crypto;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HmacSha256Signer
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 通用 HMAC-SHA256 签名工具，不作为 OpenAPI JWT 验签入口使用
 * @status : create
 */
public class HmacSha256Signer {

    /**
     * Java 标准加密扩展中的 HMAC-SHA256 算法名称。
     * <p>
     * 支付开放接口 JWT 验签统一走 {@code MerchantJwtVerifier + Hutool JWTSignerUtil.hs256}，
     * 当前类仅保留给普通参数签名或历史兼容场景使用。
     */
    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 使用参数字典序构造规范化文本并计算 HMAC-SHA256 十六进制签名。
     *
     * @param parameters 待签名参数
     * @param secret     签名密钥
     * @return 小写十六进制签名
     */
    public String sign(Map<String, String> parameters, String secret) {
        Objects.requireNonNull(parameters, "parameters can not be null");
        return sign(buildCanonicalText(parameters), secret);
    }

    /**
     * 使用原始文本计算 HMAC-SHA256 十六进制签名。
     *
     * @param content 待签名文本
     * @param secret  签名密钥
     * @return 小写十六进制签名
     */
    public String sign(String content, String secret) {
        Objects.requireNonNull(content, "content can not be null");
        Objects.requireNonNull(secret, "secret can not be null");
        return toHex(hmacSha256(content, secret));
    }

    /**
     * 使用原始文本计算 JWT 兼容的 Base64Url HMAC-SHA256 签名。
     *
     * @param content 待签名文本
     * @param secret  签名密钥
     * @return Base64Url 无填充签名
     */
    public String signBase64Url(String content, String secret) {
        Objects.requireNonNull(content, "content can not be null");
        Objects.requireNonNull(secret, "secret can not be null");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacSha256(content, secret));
    }

    /**
     * 规范化hmacsha256，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param content content 输入值，参与 content 的查询、校验、转换、写入或日志摘要
     * @param secret secret 输入值，参与 secret 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private byte[] hmacSha256(String content, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "HMAC-SHA256 signature can not be calculated");
        }
    }

    /**
     * 构造canonical文本对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 公共组件库 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param parameters parameters 输入值，参与 parameters 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String buildCanonicalText(Map<String, String> parameters) {
        TreeMap<String, String> sortedParameters = new TreeMap<>(parameters);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParameters.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue() == null ? "" : entry.getValue());
        }
        return builder.toString();
    }

    /**
     * 构造hex对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 公共组件库 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte item : value) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }
}
