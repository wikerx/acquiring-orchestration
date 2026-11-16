package com.scott.payment.component.mq.message;

import com.scott.payment.component.mq.constant.MqTag;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 商户 OpenAPI 响应加密审计消息。
 *
 * <p>消息只携带脱敏明文、不可逆密文摘要和短掩码，不允许携带完整响应密文、密钥、JWT、
 * PAN 或 CVV。生产者必须使用稳定消息号写入可靠 Outbox，消费者按交易分片时间精确更新
 * 已提交的商户 API 交互日志。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantApiResponseAuditMessage extends BaseMqMessage {

    private static final long serialVersionUID = 1L;

    /**
     * 固定事件类型，必须与 RocketMQ Tag 一致。
     */
    private String eventType;

    /**
     * 固定生产服务名，用于消费者拒绝非 OpenAPI 来源的消息。
     */
    private String producerService;

    /**
     * 平台当前交易唯一标识。
     */
    private String transactionId;

    /**
     * 当前交易真实分片时间，禁止从交易号推导。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 商户请求唯一号；为空时消费者仅按交易身份定位兼容历史记录。
     */
    private String requestId;

    /**
     * 返回商户的脱敏明文 JSON，最大保存 16000 个字符。
     */
    private String responsePlainJsonMasked;

    /**
     * 完整响应密文的 SHA-256 摘要，不保存密文本身。
     */
    private String responseCipherDigest;

    /**
     * 响应密文短掩码，仅用于人工核验同一响应。
     */
    private String responseCipherMasked;

    /**
     * OpenAPI 完成响应加密的时间。
     */
    private LocalDateTime responseTime;

    /**
     * 根据交易、分片和请求身份生成跨生产者与消费者一致的稳定消息号。
     *
     * <p>响应密文和响应时间不参与身份计算。同一商户请求即使因网络重试重新生成密文，
     * 仍只能占用同一审计事件号，由 Outbox 和消费端事实校验阻止覆盖首次响应。</p>
     *
     * @param transactionId 平台当前交易唯一标识
     * @param transactionDateTime 当前交易真实分片时间
     * @param requestId 商户请求唯一号，可为空
     * @return 64 位小写 SHA-256 十六进制消息号
     */
    public static String stableMessageId(String transactionId,
                                         LocalDateTime transactionDateTime,
                                         String requestId) {
        String normalizedTransactionId = requireText(transactionId, "transactionId");
        LocalDateTime normalizedTransactionTime = Objects.requireNonNull(
                transactionDateTime, "transactionDateTime can not be null")
                .truncatedTo(ChronoUnit.MILLIS);
        String normalizedRequestId = requestId == null ? "" : requestId.trim();
        String canonicalIdentity = MqTag.MERCHANT_API_RESPONSE_AUDIT
                + '\u001f' + normalizedTransactionId
                + '\u001f' + normalizedTransactionTime
                + '\u001f' + normalizedRequestId;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalIdentity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 规范化稳定身份必填文本，拒绝空交易号。
     *
     * @param value 待规范化文本
     * @param fieldName 字段名称
     * @return 去除首尾空白后的文本
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " can not be blank");
        }
        return value.trim();
    }
}
