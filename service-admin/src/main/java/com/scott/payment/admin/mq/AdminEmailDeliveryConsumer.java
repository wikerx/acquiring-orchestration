package com.scott.payment.admin.mq;

import com.scott.payment.admin.service.impl.AdminEmailDeliveryService;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.EmailDeliveryMessage;
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
 * @classname : AdminEmailDeliveryConsumer
 * @date : 2026-08-02 23:40
 * @email : scott_x@163.com
 * @description : 消费 Admin 邮件定位消息并交给数据库 CAS 状态机，日志不记录邮件正文或地址
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "acquiring.email.delivery", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqTopic.EMAIL_DELIVERY,
        consumerGroup = AdminMqConsumerGroups.EMAIL_DELIVERY,
        selectorExpression = MqTag.ADMIN_EMAIL_DELIVERY,
        messageModel = MessageModel.CLUSTERING
)
public class AdminEmailDeliveryConsumer implements RocketMQListener<String> {

    /** Admin 邮件投递状态机服务。 */
    private final AdminEmailDeliveryService deliveryService;

    /** 创建 Admin 邮件定位消息消费者。 */
    public AdminEmailDeliveryConsumer(AdminEmailDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /** 解析并消费一条定位消息，业务失败由数据库退避调度恢复。 */
    @Override
    public void onMessage(String payload) {
        EmailDeliveryMessage message = parse(payload);
        if (message == null) {
            log.warn("event: ADMIN_EMAIL_DELIVERY_INVALID payloadLength: {}", payload == null ? 0 : payload.length());
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            boolean delivered = deliveryService.deliver(message);
            log.info("event: ADMIN_EMAIL_DELIVERY_CONSUMED traceId: {} messageId: {} recordId: {} delivered: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getRecordId(), delivered);
        } finally {
            TraceContext.clear();
        }
    }

    /** 安全解析消息，只记录失败载荷长度和异常类型。 */
    private EmailDeliveryMessage parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(payload, EmailDeliveryMessage.class);
        } catch (RuntimeException exception) {
            log.warn("event: ADMIN_EMAIL_DELIVERY_DESERIALIZE_FAILED payloadLength: {} exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            return null;
        }
    }
}
