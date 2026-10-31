package com.scott.payment.channel.payment.scottchannela.callback;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * ScottChannelA 原始请求体 HMAC-SHA256 回调验签器。
 **/
public class CallbackVerifier {

    /**
     * 用于定位 MID 级回调密钥并与正文 MID 交叉校验的请求头。
     **/
    public static final String MERCHANT_ID_HEADER = "X-Merchant-Id";

    /**
     * 渠道回调事件号请求头，必须与正文事件号一致。
     **/
    public static final String EVENT_ID_HEADER = "X-Callback-Event-Id";

    /**
     * 原始请求体的十六进制 HMAC-SHA256 签名请求头。
     **/
    public static final String SIGNATURE_HEADER = "X-Callback-Signature";

    /**
     * 直接验证 HTTP 原始字节的 HMAC-SHA256 签名。
     *
     * <p>签名使用常量时间比较，禁止先解析 JSON 或重新序列化后再计算，以免字段顺序和空白变化导致
     * 合法回调被拒绝。异常信息不包含密钥、签名或请求正文。</p>
     *
     * @param request 保留原始字节和原始请求头的回调请求
     * @param context 上层按 MID 解析出的回调密钥上下文
     * @throws ChannelCallbackVerificationException 请求头、密钥或签名不符合协议时抛出
     **/
    public void verify(RawChannelCallbackRequest request,
                       ChannelCallbackVerificationContext context) {
        requiredHeader(request, MERCHANT_ID_HEADER);
        requiredHeader(request, EVENT_ID_HEADER);
        String actualSignature = requiredHeader(request, SIGNATURE_HEADER);
        if (context.defaultSecret() == null || context.defaultSecret().isBlank()) {
            throw failure(ChannelCallbackVerificationException.Reason.SECRET_MISSING,
                    "ScottChannelA callback secret is not configured");
        }
        String expectedSignature;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(context.defaultSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            expectedSignature = HexFormat.of().formatHex(mac.doFinal(request.body()));
        } catch (Exception exception) {
            throw failure(ChannelCallbackVerificationException.Reason.INTERNAL_ERROR,
                    "ScottChannelA callback signature could not be calculated");
        }
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.US_ASCII),
                actualSignature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw failure(ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID,
                    "ScottChannelA callback signature is invalid");
        }
    }

    private String requiredHeader(RawChannelCallbackRequest request, String name) {
        String value = request.firstHeader(name);
        if (value == null || value.isBlank()) {
            throw failure(ChannelCallbackVerificationException.Reason.HEADER_MISSING,
                    "ScottChannelA callback header is missing: " + name);
        }
        return value.trim();
    }

    private ChannelCallbackVerificationException failure(ChannelCallbackVerificationException.Reason reason,
                                                         String message) {
        return new ChannelCallbackVerificationException(reason, message);
    }
}
