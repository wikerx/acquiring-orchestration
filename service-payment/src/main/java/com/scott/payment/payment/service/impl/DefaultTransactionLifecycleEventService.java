package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.enums.PaymentTransactionEventStatus;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.mq.TransactionMqConstants;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionLifecycleEventService
 * @date : 2026-08-02 20:00
 * @email : scott_x@163.com
 * @description : 交易终态生命周期事件实现，在状态 CAS 和通知激活事务内持久化携带真实分片时间的状态变更 Outbox
 * @status : create
 */
@Service
public class DefaultTransactionLifecycleEventService implements TransactionLifecycleEventService {

    /** 交易生命周期事件号前缀。 */
    private static final String EVENT_NO_PREFIX = "EV";

    /** Outbox 聚合类型，表示支付交易聚合。 */
    private static final String PAYMENT_TRANSACTION_AGGREGATE = "PAYMENT_TRANSACTION";

    /** 新建 Outbox 事件待投递状态。 */
    private static final String EVENT_STATUS_INIT = "INIT";

    /** 无时区交易业务时间的默认解释时区。 */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /** 交易终态事件允许的最大投递重试次数。 */
    private static final int DEFAULT_MAX_RETRY_COUNT = 200;

    /** 与交易状态同事务写入的 Outbox 持久化服务。 */
    private final TransactionEventOutboxService eventOutboxService;

    /**
     * 创建交易生命周期事件服务。
     *
     * @param eventOutboxService 交易事件 Outbox 服务
     */
    public DefaultTransactionLifecycleEventService(
            TransactionEventOutboxService eventOutboxService) {
        this.eventOutboxService = eventOutboxService;
    }

    /**
     * 在当前交易事务内保存状态变更 Outbox 事件。
     *
     * <p>调用方必须在交易状态数据库事务中执行；事件以操作号分组并携带交易分片时间，
     * 后续由 Relay 至少一次投递，消费者需按消息号幂等。</p>
     */
    @Override
    public void saveStatusChanged(String transactionId,
                                  String operationId,
                                  String merchantId,
                                  String merchantOrderNo,
                                  String transactionType,
                                  String transactionStatus,
                                  LocalDateTime transactionDateTime) {
        if (!StringUtils.hasText(transactionId)
                || !StringUtils.hasText(operationId)
                || !StringUtils.hasText(transactionStatus)
                || transactionDateTime == null) {
            throw new IllegalArgumentException("terminal transaction event identity is incomplete");
        }
        if (!PaymentTransactionEventStatus.isTerminal(transactionStatus)) {
            throw new IllegalArgumentException("transaction status is not terminal");
        }
        LocalDateTime now = LocalDateTime.now();
        String eventNo = PaymentOrderNoGenerator.nextOrderNo(
                EVENT_NO_PREFIX,
                transactionDateTime);
        TransactionEventMessage message = new TransactionEventMessage();
        message.setMessageId(eventNo);
        message.setCreatedAt(now);
        message.setTransactionId(transactionId);
        message.setOperationId(operationId);
        message.setMerchantId(merchantId);
        message.setMerchantOrderNo(merchantOrderNo);
        message.setTransactionType(transactionType);
        message.setTransactionStatus(transactionStatus);
        message.setEventType(TransactionMqConstants.TRANSACTION_STATUS_CHANGED_TAG);
        message.setTransactionDateTime(transactionDateTime);
        message.setTraceId(TraceContext.getOrCreateTraceId());

        TransactionEventOutboxDO eventDO = new TransactionEventOutboxDO();
        eventDO.setEventNo(eventNo);
        eventDO.setAggregateType(PAYMENT_TRANSACTION_AGGREGATE);
        eventDO.setAggregateNo(operationId);
        eventDO.setTransactionId(transactionId);
        eventDO.setOperationId(operationId);
        eventDO.setMerchantId(merchantId);
        eventDO.setMerchantOrderNo(merchantOrderNo);
        eventDO.setTransactionType(transactionType);
        eventDO.setEventType(TransactionMqConstants.TRANSACTION_STATUS_CHANGED_TAG);
        eventDO.setEventStatus(EVENT_STATUS_INIT);
        eventDO.setTopic(MqTopic.PAYMENT_TRANSACTION_FIFO);
        eventDO.setTag(TransactionMqConstants.TRANSACTION_STATUS_CHANGED_TAG);
        eventDO.setMessageKey(eventNo);
        eventDO.setMessageGroup(operationId);
        eventDO.setPayloadJson(JsonUtils.toJsonString(message));
        eventDO.setEventTime(now);
        eventDO.setTransactionDateTime(transactionDateTime);
        eventDO.setTransactionUtcTime(toUtc(transactionDateTime));
        eventDO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        eventDO.setRetryCount(0);
        eventDO.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        eventDO.setNextRetryTime(now);
        eventDO.setVersion(0);
        eventDO.setDeleted(0);
        eventDO.setCreateTime(now);
        eventDO.setUpdateTime(now);
        eventOutboxService.save(eventDO);
    }

    /**
     * 按平台默认时区把无时区交易时间转换为 UTC 本地时间。
     *
     * @param transactionDateTime 平台默认时区下的交易时间
     * @return 同一时刻对应的 UTC 时间
     */
    private LocalDateTime toUtc(LocalDateTime transactionDateTime) {
        return transactionDateTime.atZone(ZoneId.of(DEFAULT_TIME_ZONE))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
