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
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCardVaultConsumer
 * @date : 2026-08-08 18:00
 * @email : scott_x@163.com
 * @description : service-data 卡资料密文消费者，解密后使用数据库唯一约束吸收 RocketMQ 重复投递。
 * @status : create
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

    /** 传输信封解密服务。 */
    private final DataCheckoutCardVaultTransferService transferService;
    /** 卡资料库幂等写入服务。 */
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
            log.warn("event: DATA_CARD_VAULT_MESSAGE_INVALID payloadLength: {}",
                    payload == null ? 0 : payload.length());
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            DataCheckoutCardVaultTransferService.CardVaultPlaintext plaintext = transferService.decrypt(message);
            boolean inserted = persistenceService.persist(message, plaintext);
            log.info("event: DATA_CARD_VAULT_CONSUMED traceId: {} messageId: {} transactionId: {} checkoutAttemptId: {} inserted: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getTransactionId(),
                    message.getCheckoutAttemptId(), inserted);
        } finally {
            TraceContext.clear();
        }
    }

    private CheckoutCardVaultStoreMessage parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(payload, CheckoutCardVaultStoreMessage.class);
        } catch (RuntimeException exception) {
            log.warn("event: DATA_CARD_VAULT_MESSAGE_PARSE_FAILED payloadLength: {} exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            return null;
        }
    }
}
