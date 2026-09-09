package com.scott.payment.payment.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutTokenSupport
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout token 与摘要支撑。
 * @status : create
 */
public final class PaymentCheckoutTokenSupport {

    /**
     * token 摘要算法标识，随 token 记录入库用于后续密钥轮换和审计追溯。
     */
    public static final String TOKEN_HASH_ALG = "HMAC_SHA256";

    /**
     * 实际 JCA 算法名；外部只暴露平台语义化的 TOKEN_HASH_ALG。
     */
    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 生成 URL token 的强随机源，raw token 只返回给调用链，不写库不打日志。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PaymentCheckoutTokenSupport() {
    }

    /**
     * 生成 URL 安全的 opaqueToken、cover 或 3DS return token。
     *
     * @param byteLength 随机字节数；非法配置降级为 32 字节
     * @return Base64 URL safe 且无 padding 的 token 明文
     */
    public static String newUrlSafeToken(int byteLength) {
        int effectiveLength = byteLength <= 0 ? 32 : byteLength;
        byte[] bytes = new byte[effectiveLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 计算 token HMAC 摘要，数据库和跨服务调用只传摘要，不传 raw token。
     *
     * @param value opaqueToken 或 3DS return token 明文
     * @param pepper 平台 token pepper，不属于商户密钥
     * @return 十六进制 HMAC-SHA256 摘要
     */
    public static String hmacSha256Hex(String value, String pepper) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "checkout token value is required");
        }
        if (pepper == null || pepper.isBlank()) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "checkout token pepper is not configured");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "checkout token digest failed", exception);
        }
    }
}
