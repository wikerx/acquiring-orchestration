package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.dto.transaction.MerchantNotificationRetryRequest;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.MerchantNotificationRetryMessage;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantNotificationRetryApplicationService
 * @date : 2026-08-04 13:45
 * @email : scott_x@163.com
 * @description : 管理后台人工重发商户终态回调应用服务，只写主库 Outbox，由 service-data 消费后执行外部 HTTP 回调
 * @status : create
 */
@Service
public class AdminMerchantNotificationRetryApplicationService {

    /** 人工重发 Outbox 事件号前缀。 */
    private static final String EVENT_ID_PREFIX = "MNR-";

    /** 操作人审计摘要最大长度。 */
    private static final int MAX_OPERATOR_LENGTH = 64;

    /** 可靠 MQ Outbox 发布器。 */
    private final ReliableMqPublisher reliableMqPublisher;

    /** 交易逻辑表强一致查询服务，用于人工重发前校验终态和通知任务。 */
    private final AdminTransactionQueryService transactionQueryService;

    /**
     * 创建人工重发应用服务。
     *
     * @param reliableMqPublisher 主库可靠消息发布器
     * @param transactionQueryService 交易逻辑表强一致查询服务
     */
    public AdminMerchantNotificationRetryApplicationService(ReliableMqPublisher reliableMqPublisher,
                                                            AdminTransactionQueryService transactionQueryService) {
        this.reliableMqPublisher = reliableMqPublisher;
        this.transactionQueryService = transactionQueryService;
    }

    /**
     * 将人工重发请求冻结为可靠 MQ 事件。
     *
     * <p>该方法不读取密钥、不构造 JWT、不访问商户 URL。消息提交后由 service-data 读取通知任务快照，
     * 并以事件号作为回调 JWT eventId/jti 和 Header 事件 ID。</p>
     *
     * @param request 交易号、真实分片时间和可选请求号
     * @param operator 当前后台操作人摘要
     * @return 已写入 Outbox 的稳定事件号
     */
    public String retry(MerchantNotificationRetryRequest request, String operator) {
        if (request == null
                || !StringUtils.hasText(request.getTransactionId())
                || request.getTransactionDateTime() == null) {
            throw new IllegalArgumentException("transactionId and transactionDateTime are required");
        }
        String transactionId = request.getTransactionId().trim();
        if (!transactionQueryService.existsRetryableTerminalMerchantNotification(
                transactionId, request.getTransactionDateTime())) {
            throw new ApiException(
                    ApiResultEnum.PARAM_INVALID,
                    "only a terminal transaction with a retryable merchant notification can be resent");
        }
        String requestId = StringUtils.hasText(request.getRequestId())
                ? request.getRequestId().trim()
                : UUID.randomUUID().toString();
        String eventId = EVENT_ID_PREFIX + requestId;
        MerchantNotificationRetryMessage message = new MerchantNotificationRetryMessage();
        message.setMessageId(eventId);
        message.setEventType(MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED);
        message.setTransactionId(transactionId);
        message.setTransactionDateTime(request.getTransactionDateTime());
        message.setRequestId(requestId);
        message.setRequestedBy(safeOperator(operator));
        return reliableMqPublisher.publish(
                MqTopic.PAYMENT_EVENT,
                MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED,
                message);
    }

    /** 返回适合 MQ 审计字段的操作人摘要。 */
    private String safeOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            return "admin";
        }
        String value = operator.trim();
        return value.length() <= MAX_OPERATOR_LENGTH ? value : value.substring(0, MAX_OPERATOR_LENGTH);
    }
}
