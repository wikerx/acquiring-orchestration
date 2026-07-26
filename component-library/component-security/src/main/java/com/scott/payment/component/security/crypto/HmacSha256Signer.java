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
     * 完成 hmac Sha256 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param content content 输入值，含义由调用方法名称和所属业务对象限定
     * @param secret secret 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 构建 build Canonical Text 对应的领域对象、请求对象或日志对象。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param parameters parameters 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 转换生成 to Hex 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 转换或构建后的目标对象
     */
    private String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte item : value) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }
}
