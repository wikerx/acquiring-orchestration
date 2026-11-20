package com.scott.payment.link.service;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.link.config.PaymentLinkSettings;
import com.scott.payment.link.entity.PaymentLinkDO;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/** 分享地址可受权重复取得，普通查询和数据库永远不保存凭据原文。 */
@Service
public class PaymentLinkTokenService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private final PaymentLinkSettings properties;

    public PaymentLinkTokenService(PaymentLinkSettings properties) {
        this.properties = properties;
    }

    /** 以链接、商户、冻结截止时间及密钥版本生成 HMAC 持有人凭据；未知版本拒绝签发。 */
    public String token(PaymentLinkDO link, String keyVersion) {
        String key = properties.getSigningKeys().get(keyVersion);
        if (key == null || key.isBlank()) {
            throw new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE);
        }
        String context = "PAYMENT_LINK|" + link.getLinkId() + "|" + link.getMerchantId() + "|"
                + TIME.format(link.getExpireTime()) + "|" + keyVersion;
        return link.getLinkId() + "." + hmac(context, key);
    }

    private String hmac(String value, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "payment link digest failed", exception);
        }
    }

    /** 将持有人凭据拼入已配置前端地址；返回值不得进入普通查询或日志。 */
    public String url(PaymentLinkDO link, String keyVersion) {
        return properties.getFrontendBaseUrl().replaceAll("/$", "") + "/l/" + token(link, keyVersion);
    }

    /** 限制输入长度后以恒定时间比较凭据摘要，数据库只保存摘要。 */
    public boolean matches(String supplied, String expectedHash) {
        return supplied != null && supplied.length() <= 160 && expectedHash != null
                && MessageDigest.isEqual(hash(supplied).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    /** 以 UTF-8 的 SHA-256 十六进制摘要关联凭据，不保存或输出原文。 */
    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
