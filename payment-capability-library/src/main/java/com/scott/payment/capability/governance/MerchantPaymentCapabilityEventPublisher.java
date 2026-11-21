package com.scott.payment.capability.governance;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.outbox.mapper.ReliableMqOutboxMapper;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;

/** 将确定提交的商户支付方式业务事件登记到原可靠发件箱。 */
public class MerchantPaymentCapabilityEventPublisher {

    /**
     * 能力变更消息在交易主题中的专用标签。
     */
    public static final String TAG = ReliableMqOutboxMapper.CAPABILITY_EVENT_TAG;
    private final ReliableMqPublisher publisher;

    public MerchantPaymentCapabilityEventPublisher(ReliableMqPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * 将授权治理变更转换为支付能力通知事件。
     */
    @EventListener
    public void governanceChanged(MerchantPaymentGovernanceEvent event) {
        Message message = newMessage(event.merchantId(), event.itemKey(), event.action(),
                "review:" + event.reviewRecordId(), event.occurredAt());
        message.setApplicationItemId(event.applicationItemId());
        message.setRoundNo(event.roundNo());
        publish(message);
    }

    /**
     * 登记需要重新核验配置的商户支付能力事件。
     */
    public void publishConfiguration(String merchantId, String itemKey, String sourceKey, LocalDateTime occurredAt) {
        publish(newMessage(merchantId, itemKey, "CONFIGURATION_CHANGED", sourceKey, occurredAt));
    }

    /**
     * 将已提交的费率切换登记为能力复核事件。
     */
    public void publishFeeChange(String merchantId, long feeVersion, LocalDateTime occurredAt) {
        publish(newMessage(merchantId, null, "FEE_CHANGED", "fee:" + feeVersion, occurredAt));
    }

    /**
     * 接收费率版本变动并触发对应商户的能力评估。
     */
    @EventListener
    public void feeChanged(MerchantPaymentFeeChangedEvent event) {
        publishFeeChange(event.merchantId(), event.versionId(), event.occurredAt());
    }

    /**
     * 登记已具备支付条件的商户能力通知。
     */
    public void publishReady(String merchantId, String itemKey, String transitionIdentity, LocalDateTime occurredAt) {
        publish(newMessage(merchantId, itemKey, "READY", transitionIdentity, occurredAt));
    }

    /**
     * 登记授权后的支付配置阻塞通知。
     */
    public void publishConfigurationBlocked(String merchantId, String itemKey, String workItemNo, long generation,
                                            String emailBusinessNumber, LocalDateTime occurredAt) {
        Message message = newMessage(merchantId, itemKey, "CONFIGURATION_BLOCKED", emailBusinessNumber, occurredAt);
        message.setConfigurationWorkItemNo(workItemNo);
        message.setConfigurationGeneration(generation);
        publish(message);
    }

    /**
     * 在授权有效期边界登记重新评估事件。
     */
    public void publishAuthorizationBoundary(String merchantId, String itemKey, boolean expired,
                                             String transitionIdentity, LocalDateTime occurredAt) {
        publish(newMessage(merchantId, itemKey, expired ? "AUTH_EXPIRED" : "AUTH_EFFECTIVE",
                transitionIdentity, occurredAt));
    }

    private void publish(Message message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("payment capability event requires a committed source transaction");
        }
        publisher.publish(MqTopic.PAYMENT_EVENT, TAG, message);
    }

    private static Message newMessage(String merchantId, String itemKey, String action,
                                      String stableSource, LocalDateTime occurredAt) {
        Message message = new Message();
        message.setMessageId(HexFormat.of().formatHex(digest("payment-capability-v1", merchantId, itemKey, action, stableSource)));
        message.setSchemaVersion(1);
        message.setMerchantId(merchantId);
        message.setItemKey(itemKey);
        message.setAction(action);
        message.setOccurredAt(occurredAt);
        return message;
    }

    /**
     * 按事件业务身份生成稳定摘要，供可靠事件去重关联。
     */
    public static byte[] digest(Object... fields) {
        try {
            String canonical = JsonUtils.toJsonString(Arrays.asList(fields));
            return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * 能力变动事件的消息体，保留商户、事件原因及复核所需的业务引用。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Message extends BaseMqMessage {
        private int schemaVersion;
        private String merchantId;
        private String itemKey;
        private String action;
        private Long applicationItemId;
        private Integer roundNo;
        private String configurationWorkItemNo;
        private Long configurationGeneration;
        private LocalDateTime occurredAt;
    }
}
