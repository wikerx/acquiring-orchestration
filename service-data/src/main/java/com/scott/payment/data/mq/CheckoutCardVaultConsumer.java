package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.CheckoutCardVaultStoreMessage;
import com.scott.payment.data.security.DataCheckoutCardVaultTransferService;
import com.scott.payment.data.service.CheckoutCardVaultPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 消费 checkout 卡资料密文消息并在受控调用栈内完成解密和幂等落库。
 *
 * <p>消息正文、密文和任何卡资料都不得写入日志；数据库唯一约束是重复投递的最终保护。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "data.card-vault", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqTopic.CHECKOUT_CARD_VAULT,
        consumerGroup = DataMqConsumerGroups.CHECKOUT_CARD_VAULT,
        selectorExpression = MqTag.CHECKOUT_CARD_VAULT_STORE,
        messageModel = MessageModel.CLUSTERING
)
public class CheckoutCardVaultConsumer implements RocketMQListener<String> {

    /**
     * 传输信封解密服务。
     */
    private final DataCheckoutCardVaultTransferService transferService;
    /**
     * 卡资料库幂等写入服务。
     */
    private final CheckoutCardVaultPersistenceService persistenceService;

    /**
     * 创建卡资料库消费者。
     *
     * @param transferService 传输解密服务
     * @param persistenceService 卡资料写入服务
     */
    public CheckoutCardVaultConsumer(DataCheckoutCardVaultTransferService transferService,
                                     CheckoutCardVaultPersistenceService persistenceService) {
        this.transferService = transferService;
        this.persistenceService = persistenceService;
    }

    /**
     * 消费一条卡资料密文；任何日志都不得包含消息正文、密文、PAN、有效期或姓名。
     *
     * @param payload RocketMQ JSON 消息体
     */
    @Override
    public void onMessage(String payload) {
        CheckoutCardVaultStoreMessage message = parse(payload);
        if (message == null || !StringUtils.hasText(message.getMessageId())) {
            log.error("卡资料密文消息缺少必填字段, event: DATA_CARD_VAULT_MESSAGE_INVALID, payloadLength: {}",
                    payload == null ? 0 : payload.length());
            throw new IllegalArgumentException("card vault message required fields are missing");
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            DataCheckoutCardVaultTransferService.CardVaultPlaintext plaintext = transferService.decrypt(message);
            boolean inserted = persistenceService.persist(message, plaintext);
            log.info("卡资料密文消息消费完成, event: DATA_CARD_VAULT_CONSUMED, traceId: {}, messageId: {}, transactionId: {}, checkoutAttemptId: {}, inserted: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getTransactionId(),
                    message.getCheckoutAttemptId(), inserted);
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 解析卡资料密文消息，失败日志只记录载荷长度和异常类型。
     *
     * @param payload RocketMQ JSON 消息体
     * @return 卡资料密文消息
     */
    private CheckoutCardVaultStoreMessage parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("card vault payload is empty");
        }
        try {
            return JsonUtils.parseObject(payload, CheckoutCardVaultStoreMessage.class);
        } catch (RuntimeException exception) {
            log.error("卡资料密文消息解析失败, event: DATA_CARD_VAULT_MESSAGE_PARSE_FAILED, payloadLength: {}, exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            throw new IllegalArgumentException("card vault payload is invalid", exception);
        }
    }
}
