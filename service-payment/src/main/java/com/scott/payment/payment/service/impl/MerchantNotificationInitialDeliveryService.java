package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 商户通知任务的首次可靠投递调度服务。
 *
 * <p>通知任务进入可投递状态时，在同一交易事务内写入延时 Outbox；事务提交前不得同步访问
 * 商户端点。消息只携带通知身份、交易分片时间和期望版本，回调 URL、载荷与密钥继续留在
 * 通知任务和密钥域，MQ 消费者按通知版本 CAS 获取执行权。</p>
 */
@Service
public class MerchantNotificationInitialDeliveryService {

    /** 首次商户 HTTP 回调相对任务就绪时间的延迟秒数。 */
    static final long INITIAL_DELIVERY_DELAY_SECONDS = 5L;

    /** 交易 Outbox 自身发送 RocketMQ 的最大基础设施重试次数。 */
    private static final int OUTBOX_MAX_RETRY_COUNT = 8;

    /** 平台交易业务时间的统一解释时区。 */
    private static final String PLATFORM_ZONE_ID = "Asia/Shanghai";

    /** 商户通知 Outbox 的稳定事件号前缀。 */
    private static final String EVENT_NO_PREFIX = "MNR-";

    /** 交易 Outbox 聚合类型。 */
    private static final String AGGREGATE_TYPE = "MERCHANT_NOTIFICATION";

    /** 新建 Outbox 的待投递状态。 */
    private static final String EVENT_STATUS_INIT = "INIT";

    /** 交易本地消息服务，负责在当前分片事务中持久化事件。 */
    private final TransactionEventOutboxService eventOutboxService;

    /**
     * 创建首次商户通知调度服务。
     *
     * @param eventOutboxService 交易本地消息持久化服务
     */
    public MerchantNotificationInitialDeliveryService(TransactionEventOutboxService eventOutboxService) {
        this.eventOutboxService = eventOutboxService;
    }

    /**
     * 为已经持久化且可投递的通知任务创建首次延时事件。
     *
     * <p>消息只包含通知号、交易号、分片时间、版本和尝试序号。回调地址、业务载荷、
     * JWT 和商户密钥继续只保存在通知任务及密钥域，不进入 RocketMQ。</p>
     *
     * @param notification 当前通知任务快照
     * @param expectedVersion MQ 消费时必须匹配的通知任务版本
     * @param readyTime 通知任务进入可投递状态的系统时间
     */
    @DS(DataSourceName.TRANSACTION)
    public void schedule(TransactionMerchantNotificationDO notification,
                         int expectedVersion,
                         LocalDateTime readyTime) {
        validate(notification, expectedVersion, readyTime);
        // 调用方事务回滚时通知任务与首次投递事件一起回滚，避免孤立消息提前触发 HTTP 回调。
        eventOutboxService.save(buildOutbox(notification, expectedVersion, readyTime));
    }

    /** 构造首次到期消息和交易 Outbox，不复制任何商户 HTTP 协议字段。 */
    private TransactionEventOutboxDO buildOutbox(TransactionMerchantNotificationDO notification,
                                                  int expectedVersion,
                                                  LocalDateTime readyTime) {
        String eventNo = eventNo(notification.getNotifyId(), expectedVersion);
        LocalDateTime deliverAt = readyTime.plusSeconds(INITIAL_DELIVERY_DELAY_SECONDS);
        MerchantNotificationRetryDueMessage message = new MerchantNotificationRetryDueMessage();
        message.setMessageId(eventNo);
        message.setCreatedAt(readyTime);
        message.setTraceId(TraceContext.getOrCreateTraceId());
        message.setRetryCount(0);
        message.setNotifyId(notification.getNotifyId());
        message.setTransactionId(notification.getTransactionId());
        message.setTransactionDateTime(notification.getTransactionDateTime());
        message.setExpectedVersion(expectedVersion);
        message.setAttemptNo(1);
        message.setDeliverAt(deliverAt);
        message.setEventType(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE);

        TransactionEventOutboxDO event = new TransactionEventOutboxDO();
        event.setEventNo(eventNo);
        event.setAggregateType(AGGREGATE_TYPE);
        event.setAggregateNo(notification.getNotifyId());
        event.setTransactionId(notification.getTransactionId());
        event.setOperationId(notification.getOperationId());
        event.setMerchantId(notification.getMerchantId());
        event.setMerchantOrderNo(notification.getMerchantOrderNo());
        event.setTransactionType(AGGREGATE_TYPE);
        event.setEventType(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE);
        event.setEventStatus(EVENT_STATUS_INIT);
        event.setTopic(MqTopic.PAYMENT_EVENT);
        event.setTag(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE);
        event.setMessageKey(eventNo);
        event.setMessageGroup(notification.getTransactionId());
        event.setPayloadJson(JsonUtils.toJsonString(message));
        event.setRetryCount(0);
        event.setMaxRetryCount(OUTBOX_MAX_RETRY_COUNT);
        event.setNextRetryTime(readyTime);
        event.setEventTime(readyTime);
        event.setTransactionDateTime(notification.getTransactionDateTime());
        event.setTransactionUtcTime(toUtc(notification.getTransactionDateTime()));
        event.setTransactionTimeZone(PLATFORM_ZONE_ID);
        event.setVersion(0);
        event.setDeleted(0);
        event.setCreateTime(readyTime);
        event.setUpdateTime(readyTime);
        return event;
    }

    /** 使用通知号和任务版本生成可重复计算的 Outbox 唯一事件号。 */
    private String eventNo(String notifyId, int expectedVersion) {
        // 相同通知版本重复调度得到同一事件号，由 Outbox 唯一约束阻止重复首次投递。
        String source = notifyId + ":" + expectedVersion;
        return EVENT_NO_PREFIX + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    /** 把平台时区下的交易时间转换为 UTC 审计时间。 */
    private LocalDateTime toUtc(LocalDateTime transactionDateTime) {
        return transactionDateTime.atZone(ZoneId.of(PLATFORM_ZONE_ID))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /** 校验精确分片路由、CAS 和消息业务键所需字段。 */
    private void validate(TransactionMerchantNotificationDO notification,
                          int expectedVersion,
                          LocalDateTime readyTime) {
        if (notification == null
                || !StringUtils.hasText(notification.getNotifyId())
                || !StringUtils.hasText(notification.getTransactionId())
                || notification.getTransactionDateTime() == null
                || expectedVersion < 0
                || readyTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "merchant notification initial delivery metadata is invalid");
        }
    }
}
