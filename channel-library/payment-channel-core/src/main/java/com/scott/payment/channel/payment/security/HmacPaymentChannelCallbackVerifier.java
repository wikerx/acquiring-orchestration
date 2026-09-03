package com.scott.payment.channel.payment.security;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackVerifier;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HmacPaymentChannelCallbackVerifier
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : 协议中立 HMAC-SHA256 回调验签基类，统一平台签名原文和 keyId/SHA256/Event-Signature 兼容格式。
 * @status : create
 */
@Component
public class HmacPaymentChannelCallbackVerifier implements PaymentChannelCallbackVerifier {

    /**
     * {@code TIMESTAMP_HEADER}，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String TIMESTAMP_HEADER = "X-Channel-Timestamp";
    /**
     * 随机数请求头，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String NONCE_HEADER = "X-Channel-Nonce";
    /**
     * {@code SIGNATURE_HEADER}，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String SIGNATURE_HEADER = "X-Channel-Signature";
    /**
     * {@code EVENT_SIGNATURE_HEADER}，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String EVENT_SIGNATURE_HEADER = "Event-Signature";
    /**
     * {@code HMAC_SHA256}常量，统一 {@code HmacPaymentChannelCallbackVerifier} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String HMAC_SHA256 = "HmacSHA256";
    /**
     * {@code LINE_SEPARATOR}常量，统一 {@code HmacPaymentChannelCallbackVerifier} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String LINE_SEPARATOR = "\n";

    private final Set<String> channelCodes;

    public HmacPaymentChannelCallbackVerifier() {
        this(Set.of());
    }

    protected HmacPaymentChannelCallbackVerifier(Set<String> channelCodes) {
        this.channelCodes = channelCodes == null ? Set.of() : Set.copyOf(channelCodes);
    }

    /**
     * 返回当前渠道适配器或验签器明确支持的渠道编码集合。
     * @return 符合当前条件的只读集合或映射结果
     */
    @Override
    public Set<String> channelCodes() {
        return channelCodes;
    }

    /**
     * 校验渠道回调签名，并按报文类型选择事件签名或平台 HMAC 签名协议。
     *
     * <p>事件签名存在时优先执行事件协议；否则校验时间窗、nonce、渠道编码和
     * HMAC 摘要。签名比较使用常量时间算法，异常中不得暴露共享密钥或原始签名。</p>
     *
     * @param request 已包含请求头、原始报文、共享密钥和当前时间的验签上下文
     */
    @Override
    public void verify(ChannelCallbackVerificationRequest request) {
        String eventSignature = request.header(EVENT_SIGNATURE_HEADER);
        if (hasText(eventSignature)) {
            verifyEventSignature(request, eventSignature);
            return;
        }
        verifyPlatformSignature(request);
    }

    private void verifyPlatformSignature(ChannelCallbackVerificationRequest request) {
        String timestampText = request.header(TIMESTAMP_HEADER);
        String nonce = request.header(NONCE_HEADER);
        String signature = request.header(SIGNATURE_HEADER);
        if (!hasText(request.channelCode()) || !hasText(timestampText) || !hasText(nonce) || !hasText(signature)) {
            throw failure(ChannelCallbackVerificationException.Reason.HEADER_MISSING,
                    "channel callback signature headers are required");
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            throw failure(ChannelCallbackVerificationException.Reason.TIMESTAMP_INVALID,
                    "channel callback signature timestamp is invalid");
        }
        if (Math.abs(request.currentTimeMillis() - timestamp) > request.allowedClockSkewMillis()) {
            throw failure(ChannelCallbackVerificationException.Reason.TIMESTAMP_EXPIRED,
                    "channel callback signature timestamp is expired");
        }
        if (!hasText(request.defaultSecret())) {
            throw failure(ChannelCallbackVerificationException.Reason.SECRET_MISSING,
                    "channel callback secret is not configured");
        }
        String expected = hmacSha256(canonicalText(request, timestamp, nonce), request.defaultSecret());
        if (!constantTimeMatches(expected, signature)) {
            throw failure(ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID,
                    "channel callback signature is invalid");
        }
    }

    private void verifyEventSignature(ChannelCallbackVerificationRequest request, String header) {
        String[] segments = header.split("/", 3);
        if (segments.length != 3 || !hasText(segments[0]) || !hasText(segments[1]) || !hasText(segments[2])) {
            throw failure(ChannelCallbackVerificationException.Reason.HEADER_INVALID,
                    "channel callback event signature is invalid");
        }
        if (!"SHA256".equalsIgnoreCase(segments[1].trim())) {
            throw failure(ChannelCallbackVerificationException.Reason.ALGORITHM_UNSUPPORTED,
                    "channel callback event signature algorithm is unsupported");
        }
        String secret = eventSecret(request.eventSecrets(), segments[0].trim());
        if (!hasText(secret)) {
            secret = request.defaultSecret();
        }
        if (!hasText(secret)) {
            throw failure(ChannelCallbackVerificationException.Reason.SECRET_MISSING,
                    "channel callback event signature secret is not configured");
        }
        String expected = hmacSha256(request.rawBody() == null ? "" : request.rawBody(), secret);
        if (!constantTimeMatches(expected, segments[2].trim())) {
            throw failure(ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID,
                    "channel callback event signature is invalid");
        }
    }

    private String canonicalText(ChannelCallbackVerificationRequest request, long timestamp, String nonce) {
        return request.method().toUpperCase(Locale.ROOT)
                + LINE_SEPARATOR + request.path()
                + LINE_SEPARATOR + timestamp
                + LINE_SEPARATOR + nonce
                + LINE_SEPARATOR + request.channelCode().trim().toLowerCase(Locale.ROOT)
                + LINE_SEPARATOR + sha256Hex(request.rawBody());
    }

    private String eventSecret(Map<String, String> secrets, String keyId) {
        String direct = secrets.get(keyId);
        if (hasText(direct)) {
            return direct;
        }
        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            if (keyId.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 处理{@code hmacSha256}安全计算，严格沿用当前算法、密钥边界和敏感日志约束。
     * @param text 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param secret 敏感认证或加密材料，只能在当前安全边界内使用，禁止明文日志和异常回显
     * @return 当前方法生成或规范化后的文本值
     */
    private String hmacSha256(String text, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new ChannelCallbackVerificationException(
                    ChannelCallbackVerificationException.Reason.INTERNAL_ERROR,
                    "channel callback signature can not be calculated",
                    exception);
        }
    }

    private String sha256Hex(String rawBody) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ChannelCallbackVerificationException(
                    ChannelCallbackVerificationException.Reason.INTERNAL_ERROR,
                    "channel callback body digest can not be calculated",
                    exception);
        }
    }

    private boolean constantTimeMatches(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ChannelCallbackVerificationException failure(ChannelCallbackVerificationException.Reason reason,
                                                         String message) {
        return new ChannelCallbackVerificationException(reason, message);
    }
}
