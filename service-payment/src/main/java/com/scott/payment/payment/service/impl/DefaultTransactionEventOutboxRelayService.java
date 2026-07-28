package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.TransactionEventOutboxRelayService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionEventOutboxRelayService
 * @date : 2026-07-12 18:45
 * @email : scott_x@163.com
 * @description : 交易本地消息投递默认实现，位于 service-payment 服务实现层，通过本地消息表实现事务提交后 RocketMQ 最终一致投递。
 * @status : create
 */
@Slf4j
@Service
public class DefaultTransactionEventOutboxRelayService implements TransactionEventOutboxRelayService {

    /**
     * 默认失败重试间隔分钟数。
     */
    private static final long DEFAULT_RETRY_DELAY_MINUTES = 1L;

    /**
     * 失败原因最大长度，必须与 transaction_event_outbox.fail_reason 保持一致。
     */
    private static final int FAIL_REASON_MAX_LENGTH = 512;

    /**
     * 交易本地消息服务。
     */
    private final TransactionEventOutboxService eventOutboxService;

    /**
     * RocketMQ 生产者。
     */
    private final MqProducer mqProducer;

    /**
     * 创建交易本地消息投递服务。
     *
     * @param eventOutboxService 交易本地消息服务
     * @param mqProducer         RocketMQ 生产者
     */
    public DefaultTransactionEventOutboxRelayService(TransactionEventOutboxService eventOutboxService,
                                                    MqProducer mqProducer) {
        this.eventOutboxService = eventOutboxService;
        this.mqProducer = mqProducer;
    }

    /**
     * 投递指定事件时间所在季度分表中的到期事件。
     *
     * @param eventTime 事件时间，用于定位物理分表
     * @param limit     最大投递条数
     * @return 本次成功投递数量
     */
    @Override
    public int publishDueEvents(LocalDateTime eventTime, int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<TransactionEventOutboxDO> events = eventOutboxService.listDueEvents(eventTime, now, limit);
        int successCount = 0;
        for (TransactionEventOutboxDO eventDO : events) {
            if (publishSingle(eventDO, now)) {
                successCount++;
            }
        }
        return successCount;
    }

    private boolean publishSingle(TransactionEventOutboxDO eventDO, LocalDateTime now) {
        long startNanos = System.nanoTime();
        try {
            BaseMqMessage message = buildMessage(eventDO);
            if (!StringUtils.hasText(message.getMessageId())) {
                message.setMessageId(eventDO.getMessageKey());
            }
            if (message.getCreatedAt() == null) {
                message.setCreatedAt(eventDO.getEventTime());
            }
            if (!StringUtils.hasText(message.getTraceId())) {
                message.setTraceId(TraceContext.getOrCreateTraceId());
            }
            log.info("event: TRANSACTION_OUTBOX_PUBLISH_START stage=MQ traceId: {} eventNo: {} messageId: {} messageKey: {} retryCount: {} topic: {} tag: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} transactionType: {} transactionDateTime: {}",
                    message.getTraceId(),
                    eventDO.getEventNo(),
                    message.getMessageId(),
                    eventDO.getMessageKey(),
                    message.getRetryCount(),
                    eventDO.getTopic(),
                    eventDO.getTag(),
                    eventDO.getTransactionId(),
                    eventDO.getOperationId(),
                    eventDO.getMerchantId(),
                    eventDO.getMerchantOrderNo(),
                    eventDO.getTransactionType(),
                    eventDO.getTransactionDateTime());
            mqProducer.send(eventDO.getTopic(), eventDO.getTag(), message);
            boolean updated = eventOutboxService.markSent(eventDO, LocalDateTime.now());
            if (!updated) {
                log.warn("event: TRANSACTION_OUTBOX_MARK_SENT_CAS_FAILED stage=MQ traceId: {} eventNo: {} messageId: {} messageKey: {} transactionId: {} operationId: {} durationMs: {}",
                        message.getTraceId(),
                        eventDO.getEventNo(),
                        message.getMessageId(),
                        eventDO.getMessageKey(),
                        eventDO.getTransactionId(),
                        eventDO.getOperationId(),
                        elapsedMillis(startNanos));
            } else {
                log.info("event: TRANSACTION_OUTBOX_PUBLISH_END stage=MQ traceId: {} eventNo: {} messageId: {} messageKey: {} transactionId: {} operationId: {} status=SENT durationMs: {}",
                        message.getTraceId(),
                        eventDO.getEventNo(),
                        message.getMessageId(),
                        eventDO.getMessageKey(),
                        eventDO.getTransactionId(),
                        eventDO.getOperationId(),
                        elapsedMillis(startNanos));
            }
            return updated;
        } catch (Exception exception) {
            LocalDateTime nextRetryTime = now.plusMinutes(DEFAULT_RETRY_DELAY_MINUTES);
            eventOutboxService.markFailed(eventDO, nextRetryTime, safeFailReason(exception), now);
            log.warn("event: TRANSACTION_OUTBOX_PUBLISH_FAILED stage=MQ traceId: {} eventNo: {} messageKey: {} transactionId: {} operationId: {} retryCount: {} nextRetryTime: {} errorType: {} message: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    eventDO.getEventNo(),
                    eventDO.getMessageKey(),
                    eventDO.getTransactionId(),
                    eventDO.getOperationId(),
                    eventDO.getRetryCount(),
                    nextRetryTime,
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    elapsedMillis(startNanos),
                    exception);
            return false;
        }
    }

    /**
     * 计算本地消息投递耗时。
     *
     * @param startNanos System.nanoTime 起始值
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 从本地事件表载荷恢复 MQ 消息体。
     * <p>
     * 前置条件：eventDO 来自 transaction_event_outbox 分表，payloadJson 可能是历史版本消息。
     * 该方法优先反序列化为 TransactionEventMessage；解析为空时返回空消息对象，由投递逻辑补齐 messageId、createdAt、traceId 和 retryCount，
     * 保证失败重试仍能带着原事件编号投递。
     * </p>
     * @param eventDO 本地事件表记录，提供 payloadJson、topic、tag 和业务标识
     * @return 可交给 MQ 生产者发送的基础消息
     */
    private BaseMqMessage buildMessage(TransactionEventOutboxDO eventDO) {
        TransactionEventMessage message = JsonUtils.parseObject(eventDO.getPayloadJson(), TransactionEventMessage.class);
        return message == null ? new TransactionEventMessage() : message;
    }

    /**
     * 规范化failreason，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param exception 下游调用、校验或持久化阶段捕获的异常对象
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String safeFailReason(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            message = exception.getClass().getSimpleName();
        }
        if (message.length() > FAIL_REASON_MAX_LENGTH) {
            return message.substring(0, FAIL_REASON_MAX_LENGTH);
        }
        return message;
    }
}
