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
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
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
 * @classname : TransactionTerminalClearingConsumer
 * @date : 2026-08-26 16:40
 * @email : scott_x@163.com
 * @description : 顺序消费支付交易终态事件并委托清分应用层；只有受控失败完成数据库提交后才正常ACK，技术异常原样抛出。
 * @status : create
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqTopic.PAYMENT_TRANSACTION_FIFO,
        consumerGroup = "service-clearing-transaction-terminal",
        selectorExpression = MqTag.TRANSACTION_STATUS_CHANGED,
        consumeMode = ConsumeMode.ORDERLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 16)
public class TransactionTerminalClearingConsumer
        implements RocketMQListener<String>, RocketMQPushConsumerLifecycleListener {

    private final ClearingProcessingApplicationService applicationService;
    private final GlobalIdGenerator idGenerator;
    private final ClearingProperties properties;
    private final ClearingOperationalMetrics metrics;

    /**
     * 创建交易终态清分消费者。
     *
     * @param applicationService 清分应用编排服务
     * @param idGenerator 单次处理租约编号生成器
     * @param properties 清分消费线程参数
     * @param metrics 清分低基数运行指标
     */
    public TransactionTerminalClearingConsumer(ClearingProcessingApplicationService applicationService,
                                               GlobalIdGenerator idGenerator,
                                               ClearingProperties properties,
                                               ClearingOperationalMetrics metrics) {
        this.applicationService = applicationService;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * 消费单条交易终态事件。
     *
     * @param payload 不含卡数据、费用配置或渠道凭据的交易事件 JSON
     */
    @Override
    public void onMessage(String payload) {
        PaymentTransactionEventMessage message = parse(payload);
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            ClearingProcessingResult result = applicationService.process(
                    message, processingOwner(), LocalDateTime.now(Clock.systemUTC()));
            log.info("event: TRANSACTION_CLEARING_TERMINAL_CONSUMED traceId: {} messageId: {} transactionId: {} operationId: {} result: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getTransactionId(),
                    message.getOperationId(), result);
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 按清分配置调整 RocketMQ 消费线程，配置校验由启动门禁统一执行。
     *
     * @param consumer 即将启动的交易 FIFO 消费者
     */
    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        consumer.setConsumeThreadMin(properties.getConsumerMinThreads());
        consumer.setConsumeThreadMax(properties.getConsumerMaxThreads());
    }

    /**
     * 反序列化并校验交易终态消息外壳；业务身份和状态规则由应用层继续校验。
     *
     * @param payload Broker 投递的非敏感 JSON
     * @return 非空交易终态消息
     * @throws IllegalArgumentException 载荷为空、反序列化失败或结果为空时抛出
     */
    private PaymentTransactionEventMessage parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            metrics.recordMessageRejected("TERMINAL", "EMPTY");
            throw new IllegalArgumentException("transaction terminal clearing payload is empty");
        }
        PaymentTransactionEventMessage message;
        try {
            message = JsonUtils.parseObject(
                    payload, PaymentTransactionEventMessage.class);
        } catch (RuntimeException exception) {
            metrics.recordMessageRejected("TERMINAL", "DESERIALIZATION");
            throw new IllegalArgumentException("transaction terminal clearing payload is invalid", exception);
        }
        if (message == null) {
            metrics.recordMessageRejected("TERMINAL", "NULL_MESSAGE");
            throw new IllegalArgumentException("transaction terminal clearing payload is invalid");
        }
        return message;
    }

    /** @return 本次数据库处理租约的唯一执行者标识。 */
    private String processingOwner() {
        return "service-clearing:" + idGenerator.nextId();
    }
}
