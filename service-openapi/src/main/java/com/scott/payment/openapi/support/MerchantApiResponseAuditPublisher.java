package com.scott.payment.openapi.support;

import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.MerchantApiResponseAuditMessage;
import com.scott.payment.component.mq.publisher.IndependentReliableMqPublisher;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.TransactionMerchantApiResponseLogUpdateClientRequestDTO;
import com.scott.payment.openapi.config.MerchantApiResponseAuditProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 商户响应加密审计发布器。
 *
 * <p>MQ 模式先将已脱敏消息写入通用可靠 Outbox，再由 service-data 异步回写交易分片表；
 * HTTP 模式保留历史内部调用用于灰度回滚。任何审计交付异常只记录受控摘要，不能覆盖已经
 * 生成的商户响应。</p>
 */
@Slf4j
@Service
public class MerchantApiResponseAuditPublisher {

    private static final String PRODUCER_SERVICE = "service-openapi";

    private static final int MAX_RESPONSE_PLAIN_LENGTH = 16_000;

    private final PaymentInternalClient paymentInternalClient;

    private final IndependentReliableMqPublisher reliableMqPublisher;

    private final MerchantApiResponseAuditProperties properties;

    /**
     * 创建支持 MQ 主路径和 HTTP 回滚路径的审计发布器。
     *
     * @param paymentInternalClient HTTP 兼容回写客户端
     * @param reliableMqPublisher   通用可靠 Outbox 发布器
     * @param properties            审计投递模式配置
     */
    public MerchantApiResponseAuditPublisher(
            PaymentInternalClient paymentInternalClient,
            IndependentReliableMqPublisher reliableMqPublisher,
            MerchantApiResponseAuditProperties properties) {
        this.paymentInternalClient = paymentInternalClient;
        this.reliableMqPublisher = reliableMqPublisher;
        this.properties = properties;
    }

    /**
     * 按配置投递一条商户响应加密审计。
     *
     * <p>失败不得向上抛出，避免审计基础设施故障改变商户已经生成的交易响应。</p>
     *
     * @param requestDTO 仅包含脱敏明文、密文摘要和短掩码的审计命令
     */
    public void publish(TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO) {
        try {
            validate(requestDTO);
            if (properties.getMode() == MerchantApiResponseAuditProperties.Mode.MQ) {
                publishMq(requestDTO);
                return;
            }
            boolean updated = paymentInternalClient.updateMerchantApiResponseLog(requestDTO);
            log.info("OpenAPI响应审计已通过HTTP回写, event: OPENAPI_RESPONSE_AUDIT_HTTP_COMPLETED, stage: RESPONSE_AUDIT, traceId: {}, transactionId: {}, requestId: {}, updated: {}",
                    TraceContext.getTraceId(), requestDTO.getTransactionId(), requestDTO.getRequestId(), updated);
        } catch (RuntimeException exception) {
            log.warn("OpenAPI响应审计交付失败, event: OPENAPI_RESPONSE_AUDIT_DELIVERY_FAILED, stage: RESPONSE_AUDIT, traceId: {}, transactionId: {}, requestId: {}, mode: {}, exceptionType: {}",
                    TraceContext.getTraceId(),
                    requestDTO == null ? null : requestDTO.getTransactionId(),
                    requestDTO == null ? null : requestDTO.getRequestId(),
                    properties.getMode(),
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 构建冻结消息并写入 OpenAPI 通用可靠 Outbox。
     */
    private void publishMq(TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO) {
        MerchantApiResponseAuditMessage message = new MerchantApiResponseAuditMessage();
        message.setMessageId(stableMessageId(requestDTO));
        message.setEventType(MqTag.MERCHANT_API_RESPONSE_AUDIT);
        message.setProducerService(PRODUCER_SERVICE);
        message.setTransactionId(requestDTO.getTransactionId().trim());
        message.setTransactionDateTime(toDatabaseTime(requestDTO.getTransactionDateTime()));
        message.setRequestId(trimToNull(requestDTO.getRequestId()));
        message.setResponsePlainJsonMasked(truncate(
                requestDTO.getResponsePlainJsonMasked(), MAX_RESPONSE_PLAIN_LENGTH));
        message.setResponseCipherDigest(requestDTO.getResponseCipherDigest().trim());
        message.setResponseCipherMasked(trimToNull(requestDTO.getResponseCipherMasked()));
        message.setResponseTime(toDatabaseTime(requestDTO.getResponseTime()));
        String eventId = reliableMqPublisher.publish(
                MqTopic.PAYMENT_EVENT, MqTag.MERCHANT_API_RESPONSE_AUDIT, message);
        log.info("OpenAPI响应审计已可靠入队, event: OPENAPI_RESPONSE_AUDIT_MQ_ENQUEUED, stage: RESPONSE_AUDIT, traceId: {}, eventId: {}, transactionId: {}, requestId: {}",
                TraceContext.getTraceId(), eventId, message.getTransactionId(), message.getRequestId());
    }

    /**
     * 校验形成稳定消息身份和精确分片路由所需字段。
     */
    private void validate(TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO) {
        if (requestDTO == null
                || !StringUtils.hasText(requestDTO.getTransactionId())
                || requestDTO.getTransactionDateTime() == null
                || !StringUtils.hasText(requestDTO.getResponseCipherDigest())
                || requestDTO.getResponseTime() == null) {
            throw new IllegalArgumentException("merchant response audit identity is incomplete");
        }
    }

    /**
     * 使用交易、分片和请求身份生成 64 位稳定事件号。
     */
    private String stableMessageId(TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO) {
        return MerchantApiResponseAuditMessage.stableMessageId(
                requestDTO.getTransactionId(),
                requestDTO.getTransactionDateTime(),
                requestDTO.getRequestId());
    }

    /**
     * 将时间收敛到 MySQL DATETIME(3) 可无损保存的精度。
     */
    private LocalDateTime toDatabaseTime(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * 去除首尾空白，并把空文本统一为空值。
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 在进入 MQ 前限制数据库文本长度，避免依赖数据库静默截断。
     */
    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
