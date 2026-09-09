package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HostedCheckoutTokenSupport
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout token 摘要支撑。
 * @status : create
 */
public final class HostedCheckoutTokenSupport {

    /**
     * 与 service-payment token 表保持一致的摘要算法标识。
     */
    public static final String TOKEN_HASH_ALG = "HMAC_SHA256";

    private HostedCheckoutTokenSupport() {
    }

    /**
     * 将浏览器提交的 raw token 转为 HMAC 摘要后再进入内部服务调用。
     *
     * @param value opaqueToken 或 3DS return token 明文
     * @param pepper 平台 token pepper
     * @return 可用于查询 token 摘要表的十六进制摘要
     */
    public static String hmacSha256Hex(String value, String pepper) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(pepper)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "checkout token can not be hashed");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR, "checkout token hash failed");
        }
    }
}
