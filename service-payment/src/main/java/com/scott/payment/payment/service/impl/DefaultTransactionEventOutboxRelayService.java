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
            log.info("event=TRANSACTION_OUTBOX_PUBLISH_START eventNo={} messageKey={} topic={} tag={} transactionDateTime={}",
                    eventDO.getEventNo(),
                    eventDO.getMessageKey(),
                    eventDO.getTopic(),
                    eventDO.getTag(),
                    eventDO.getTransactionDateTime());
            mqProducer.send(eventDO.getTopic(), eventDO.getTag(), message);
            boolean updated = eventOutboxService.markSent(eventDO, LocalDateTime.now());
            if (!updated) {
                log.warn("event=TRANSACTION_OUTBOX_MARK_SENT_CAS_FAILED eventNo={} messageKey={}",
                        eventDO.getEventNo(),
                        eventDO.getMessageKey());
            } else {
                log.info("event=TRANSACTION_OUTBOX_PUBLISH_END eventNo={} messageKey={} status=SENT",
                        eventDO.getEventNo(),
                        eventDO.getMessageKey());
            }
            return updated;
        } catch (Exception exception) {
            LocalDateTime nextRetryTime = now.plusMinutes(DEFAULT_RETRY_DELAY_MINUTES);
            eventOutboxService.markFailed(eventDO, nextRetryTime, safeFailReason(exception), now);
            log.warn("event=TRANSACTION_OUTBOX_PUBLISH_FAILED eventNo={} messageKey={} nextRetryTime={} errorType={} message={}",
                    eventDO.getEventNo(),
                    eventDO.getMessageKey(),
                    nextRetryTime,
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    exception);
            return false;
        }
    }

    private BaseMqMessage buildMessage(TransactionEventOutboxDO eventDO) {
        TransactionEventMessage message = JsonUtils.parseObject(eventDO.getPayloadJson(), TransactionEventMessage.class);
        return message == null ? new TransactionEventMessage() : message;
    }

    /**
     * 执行 safe Fail Reason 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionEventOutboxRelayService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param exception exception 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
