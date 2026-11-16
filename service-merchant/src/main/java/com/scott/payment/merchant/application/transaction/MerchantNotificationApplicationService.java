package com.scott.payment.merchant.application.transaction;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.MerchantNotificationRetryMessage;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.DeliveryLog;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationQuery;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationRecord;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationRetryRequest;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationRetryTarget;
import com.scott.payment.merchant.service.MerchantNotificationQueryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商户端交易通知查询应用服务，负责将认证商户边界传递给只读查询层。
 */
@Service
public class MerchantNotificationApplicationService {

    /**
     * 商户人工重发事件号前缀，事件号与认证商户及客户端请求号确定性绑定。
     */
    private static final String MERCHANT_RETRY_EVENT_PREFIX = "MNR-M-";

    /**
     * MQ 审计操作人摘要最大长度。
     */
    private static final int MAX_OPERATOR_LENGTH = 64;

    private final MerchantNotificationQueryService queryService;

    /**
     * 主库可靠 MQ Outbox 发布器；请求线程不直接访问商户回调地址。
     */
    private final ReliableMqPublisher reliableMqPublisher;

    /**
     * 创建商户通知查询应用服务。
     *
     * @param queryService 商户通知查询和重发资格校验服务
     * @param reliableMqPublisher 主库可靠 MQ Outbox 发布器
     */
    public MerchantNotificationApplicationService(MerchantNotificationQueryService queryService,
                                                  ReliableMqPublisher reliableMqPublisher) {
        this.queryService = queryService;
        this.reliableMqPublisher = reliableMqPublisher;
    }

    /**
     * 分页查询当前认证商户的通知任务。
     *
     * @param merchantId 认证商户号
     * @param query 查询条件
     * @return 通知任务分页
     */
    public PageResult<NotificationRecord> page(String merchantId, NotificationQuery query) {
        return queryService.page(merchantId, query);
    }

    /**
     * 查询当前认证商户的通知任务详情。
     *
     * @param merchantId 认证商户号
     * @param notifyId 通知任务号
     * @param transactionDateTime 真实交易分片时间
     * @return 脱敏通知详情
     */
    public NotificationDetailResponse detail(String merchantId,
                                             String notifyId,
                                             LocalDateTime transactionDateTime) {
        return queryService.detail(merchantId, notifyId, transactionDateTime);
    }

    /**
     * 分页查询当前认证商户的通知投递日志，最新尝试优先。
     *
     * @param merchantId 认证商户号
     * @param notifyId 通知任务号
     * @param transactionDateTime 真实交易分片时间
     * @param pageNo 页码，从 1 开始
     * @param pageSize 每页记录数，最大 100
     * @return 投递日志分页
     */
    public PageResult<DeliveryLog> pageDeliveryLogs(String merchantId,
                                                    String notifyId,
                                                    LocalDateTime transactionDateTime,
                                                    long pageNo,
                                                    long pageSize) {
        return queryService.pageDeliveryLogs(
                merchantId, notifyId, transactionDateTime, pageNo, pageSize);
    }

    /**
     * 将商户人工重发请求冻结为可靠 MQ 事件。
     *
     * <p>认证商户号只来自服务端会话。发布前以主库强一致查询校验任务归属、交易终态和通知自动计划终态；
     * MQ 消息同时冻结任务号与商户号，service-data 消费时再次精确匹配。相同商户和请求号生成同一事件号，
     * Outbox 唯一键提供持久化幂等；本方法不改变交易状态，也不在请求线程访问商户地址。</p>
     *
     * @param merchantId 认证上下文中的商户号
     * @param operator 当前商户账号审计摘要
     * @param request 通知任务号、真实分片时间和客户端请求号
     * @return 已受理的稳定 MQ 事件号
     */
    public String retry(String merchantId,
                        String operator,
                        NotificationRetryRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getNotifyId())
                || request.getTransactionDateTime() == null
                || !StringUtils.hasText(request.getRequestId())) {
            throw new IllegalArgumentException(
                    "notifyId, transactionDateTime and requestId are required");
        }
        String safeMerchantId = merchantId == null ? "" : merchantId.trim();
        String requestId = request.getRequestId().trim();
        NotificationRetryTarget target = queryService.retryTarget(
                safeMerchantId,
                request.getNotifyId().trim(),
                request.getTransactionDateTime());

        MerchantNotificationRetryMessage message = new MerchantNotificationRetryMessage();
        message.setMessageId(stableEventId(safeMerchantId, requestId));
        message.setEventType(MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED);
        message.setNotifyId(target.getNotifyId());
        message.setMerchantId(safeMerchantId);
        message.setTransactionId(target.getTransactionId());
        message.setTransactionDateTime(target.getTransactionDateTime());
        message.setRequestId(requestId);
        message.setRequestedBy(safeOperator(operator));
        return reliableMqPublisher.publish(
                MqTopic.PAYMENT_EVENT,
                MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED,
                message);
    }

    /**
     * 使用认证商户和客户端请求号生成固定长度的稳定事件号。
     *
     * @param merchantId 认证商户号
     * @param requestId 客户端请求号
     * @return 不暴露商户号且可重复计算的 Outbox 事件号
     */
    private String stableEventId(String merchantId, String requestId) {
        UUID stableId = UUID.nameUUIDFromBytes(
                (merchantId + '|' + requestId).getBytes(StandardCharsets.UTF_8));
        return MERCHANT_RETRY_EVENT_PREFIX + stableId;
    }

    /**
     * 截断 MQ 审计操作人摘要，避免账号显示名扩大消息契约。
     *
     * @param operator 当前商户账号摘要
     * @return 非空且不超过消息字段预算的审计文本
     */
    private String safeOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            return "merchant-account";
        }
        String value = operator.trim();
        return value.length() <= MAX_OPERATOR_LENGTH
                ? value
                : value.substring(0, MAX_OPERATOR_LENGTH);
    }
}
