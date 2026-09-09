package com.scott.payment.clearing.mq;

import com.scott.payment.clearing.application.ClearingProcessingApplicationService;
import com.scott.payment.clearing.application.ClearingProcessingResult;
import com.scott.payment.clearing.config.ClearingProperties;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.ClearingRetryDueMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingRetryDueConsumer
 * @date : 2026-08-26 16:40
 * @email : scott_x@163.com
 * @description : 并发消费清分Delay Topic到期消息，保留修订和重试序号做数据库过期校验，不把Broker消息视为财务权威事实。
 * @status : create
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqTopic.PAYMENT_CLEARING_DELAY,
        consumerGroup = "service-clearing-transaction-retry-due",
        selectorExpression = MqTag.TRANSACTION_CLEARING_RETRY_DUE,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 16)
public class ClearingRetryDueConsumer
        implements RocketMQListener<String>, RocketMQPushConsumerLifecycleListener {

    private final ClearingProcessingApplicationService applicationService;
    private final GlobalIdGenerator idGenerator;
    private final ClearingProperties properties;
    private final ClearingOperationalMetrics metrics;

    /**
     * 创建清分重试到期消费者。
     *
     * @param applicationService 清分应用编排服务
     * @param idGenerator 单次处理租约编号生成器
     * @param properties 清分消费线程参数
     * @param metrics 清分低基数运行指标
     */
    public ClearingRetryDueConsumer(ClearingProcessingApplicationService applicationService,
                                    GlobalIdGenerator idGenerator,
                                    ClearingProperties properties,
                                    ClearingOperationalMetrics metrics) {
        this.applicationService = applicationService;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * 消费单条清分重试到期事件。
     *
     * @param payload 只含动作身份和重试控制字段的 JSON
     */
    @Override
    public void onMessage(String payload) {
        ClearingRetryDueMessage message = parse(payload);
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            ClearingProcessingResult result = applicationService.process(
                    message, processingOwner(), LocalDateTime.now(Clock.systemUTC()));
            log.info("event: TRANSACTION_CLEARING_RETRY_DUE_CONSUMED traceId: {} messageId: {} sourceEventNo: {} transactionId: {} operationId: {} clearingRetryCount: {} retryReasonCode: {} result: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getSourceEventNo(),
                    message.getTransactionId(), message.getOperationId(), message.getClearingRetryCount(),
                    message.getRetryReasonCode(), result);
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 按清分配置调整 RocketMQ 消费线程，Delay Topic 不启用 FIFO 消费模式。
     *
     * @param consumer 即将启动的清分延迟消息消费者
     */
    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        consumer.setConsumeThreadMin(properties.getConsumerMinThreads());
        consumer.setConsumeThreadMax(properties.getConsumerMaxThreads());
    }

    /**
     * 反序列化清分到期重试消息；数据库修订和重试序号仍是是否执行的权威依据。
     *
     * @param payload Broker 投递的非敏感 JSON
     * @return 非空清分重试消息
     * @throws IllegalArgumentException 载荷为空、反序列化失败或结果为空时抛出
     */
    private ClearingRetryDueMessage parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            metrics.recordMessageRejected("RETRY_DUE", "EMPTY");
            throw new IllegalArgumentException("clearing retry due payload is empty");
        }
        ClearingRetryDueMessage message;
        try {
            message = JsonUtils.parseObject(payload, ClearingRetryDueMessage.class);
        } catch (RuntimeException exception) {
            metrics.recordMessageRejected("RETRY_DUE", "DESERIALIZATION");
            throw new IllegalArgumentException("clearing retry due payload is invalid", exception);
        }
        if (message == null) {
            metrics.recordMessageRejected("RETRY_DUE", "NULL_MESSAGE");
            throw new IllegalArgumentException("clearing retry due payload is invalid");
        }
        return message;
    }

    /** @return 本次数据库处理租约的唯一执行者标识。 */
    private String processingOwner() {
        return "service-clearing:" + idGenerator.nextId();
    }
}
