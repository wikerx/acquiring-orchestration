package com.scott.payment.admin.application.operation;

import com.scott.payment.admin.application.merchantpayment.MerchantPaymentGovernanceEvent;
import com.scott.payment.admin.application.merchantpayment.MerchantPaymentFeeChangedEvent;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.outbox.mapper.ReliableMqOutboxMapper;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;

/** 将确定提交的商户支付方式业务事件登记到原可靠发件箱。 */
@Service
public class MerchantPaymentCapabilityEventPublisher {

    public static final String TAG = ReliableMqOutboxMapper.CAPABILITY_EVENT_TAG;
    private final ReliableMqPublisher publisher;

    public MerchantPaymentCapabilityEventPublisher(ReliableMqPublisher publisher) {
        this.publisher = publisher;
    }

    @EventListener
    public void governanceChanged(MerchantPaymentGovernanceEvent event) {
        Message message = newMessage(event.merchantId(), event.itemKey(), event.action(),
                "review:" + event.reviewRecordId(), event.occurredAt());
        message.setApplicationItemId(event.applicationItemId());
        message.setRoundNo(event.roundNo());
        publish(message);
    }

    public void publishConfiguration(String merchantId, String itemKey, String sourceKey, LocalDateTime occurredAt) {
        publish(newMessage(merchantId, itemKey, "CONFIGURATION_CHANGED", sourceKey, occurredAt));
    }

    public void publishFeeChange(String merchantId, long feeVersion, LocalDateTime occurredAt) {
        publish(newMessage(merchantId, null, "FEE_CHANGED", "fee:" + feeVersion, occurredAt));
    }

    @EventListener
    public void feeChanged(MerchantPaymentFeeChangedEvent event) {
        publishFeeChange(event.merchantId(), event.versionId(), event.occurredAt());
    }

    public void publishReady(String merchantId, String itemKey, String transitionIdentity, LocalDateTime occurredAt) {
        publish(newMessage(merchantId, itemKey, "READY", transitionIdentity, occurredAt));
    }

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

    static byte[] digest(Object... fields) {
        try {
            String canonical = JsonUtils.toJsonString(Arrays.asList(fields));
            return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Message extends BaseMqMessage {
        private int schemaVersion;
        private String merchantId;
        private String itemKey;
        private String action;
        private Long applicationItemId;
        private Integer roundNo;
        private LocalDateTime occurredAt;
    }
}
