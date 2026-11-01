package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.LoginAuditMessage;
import com.scott.payment.data.service.LoginAuditPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * service-data 登录审计消费者，数据库唯一键承担最终幂等且日志不输出登录账号、IP 或 User-Agent
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqTopic.LOGIN_AUDIT,
        consumerGroup = DataMqConsumerGroups.LOGIN_AUDIT,
        selectorExpression = MqTag.LOGIN_AUDIT,
        messageModel = MessageModel.CLUSTERING
)
public class LoginAuditConsumer implements RocketMQListener<String> {

    /**
     * 登录审计持久化服务。
     */
    private final LoginAuditPersistenceService persistenceService;

    /**
     * 创建登录审计消费者。
     *
     * @param persistenceService 登录审计事务写入服务
     */
    public LoginAuditConsumer(LoginAuditPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * 消费登录审计消息，畸形消息抛出后由 RocketMQ 重试和死信处理。
     *
     * @param payload RocketMQ JSON 消息体；日志中不得输出原文
     */
    @Override
    public void onMessage(String payload) {
        LoginAuditMessage message = parse(payload);
        if (message == null || !StringUtils.hasText(message.getMessageId())
                || message.getAppId() == null || message.getLoginStatus() == null) {
            log.error("登录审计消息缺少必填字段, event: DATA_LOGIN_AUDIT_INVALID, payloadLength: {}",
                    payload == null ? 0 : payload.length());
            throw new IllegalArgumentException("login audit message required fields are missing");
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            boolean inserted = persistenceService.persist(message);
            log.info("登录审计消息消费完成, event: DATA_LOGIN_AUDIT_CONSUMED, traceId: {}, messageId: {}, appId: {}, loginStatus: {}, inserted: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getAppId(),
                    message.getLoginStatus(), inserted);
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 解析消息，失败时只记录长度和异常类型。
     *
     * @param payload RocketMQ JSON 消息体
     * @return 登录审计消息
     */
    private LoginAuditMessage parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("login audit payload is empty");
        }
        try {
            return JsonUtils.parseObject(payload, LoginAuditMessage.class);
        } catch (RuntimeException exception) {
            log.error("登录审计消息解析失败, event: DATA_LOGIN_AUDIT_DESERIALIZE_FAILED, payloadLength: {}, exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            throw new IllegalArgumentException("login audit payload is invalid", exception);
        }
    }
}
