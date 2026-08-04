package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.dto.transaction.MerchantNotificationRetryRequest;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.MerchantNotificationRetryMessage;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantNotificationRetryApplicationServiceTests
 * @date : 2026-08-04 13:45
 * @email : scott_x@163.com
 * @description : 管理后台商户回调人工重发测试，校验精确分片时间和稳定请求号通过主库 Outbox 发布
 * @status : create
 */
@Slf4j
class AdminMerchantNotificationRetryApplicationServiceTests {

    /** 人工重发只发布可靠 MQ，不在 Admin 请求线程直接调用商户 URL。 */
    @Test
    void shouldPublishReliableRetryEventWithExactTransactionTime() {
        log.info("测试管理后台重发商户回调，关键输入: 交易号、真实分片时间和请求号");
        ReliableMqPublisher publisher = mock(ReliableMqPublisher.class);
        AdminTransactionQueryService transactionQueryService = mock(AdminTransactionQueryService.class);
        AdminMerchantNotificationRetryApplicationService service =
                new AdminMerchantNotificationRetryApplicationService(publisher, transactionQueryService);
        MerchantNotificationRetryRequest request = new MerchantNotificationRetryRequest();
        request.setTransactionId("TX202608011600000000001");
        request.setTransactionDateTime(LocalDateTime.of(2026, 8, 1, 16, 0, 0, 255_000_000));
        request.setRequestId("REQ-20260804-0001");
        when(transactionQueryService.existsRetryableTerminalMerchantNotification(
                request.getTransactionId(), request.getTransactionDateTime())).thenReturn(true);
        when(publisher.publish(
                org.mockito.ArgumentMatchers.eq(MqTopic.PAYMENT_EVENT),
                org.mockito.ArgumentMatchers.eq(MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED),
                org.mockito.ArgumentMatchers.any(MerchantNotificationRetryMessage.class)))
                .thenReturn("MNR-REQ-20260804-0001");

        String eventId = service.retry(request, "admin-user");

        ArgumentCaptor<MerchantNotificationRetryMessage> messageCaptor =
                ArgumentCaptor.forClass(MerchantNotificationRetryMessage.class);
        verify(publisher).publish(
                org.mockito.ArgumentMatchers.eq(MqTopic.PAYMENT_EVENT),
                org.mockito.ArgumentMatchers.eq(MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED),
                messageCaptor.capture());
        MerchantNotificationRetryMessage message = messageCaptor.getValue();
        assertThat(eventId).isEqualTo("MNR-REQ-20260804-0001");
        assertThat(message.getMessageId()).isEqualTo("MNR-REQ-20260804-0001");
        assertThat(message.getTransactionId()).isEqualTo(request.getTransactionId());
        assertThat(message.getTransactionDateTime()).isEqualTo(request.getTransactionDateTime());
        assertThat(message.getRequestId()).isEqualTo("REQ-20260804-0001");
        assertThat(message.getEventType()).isEqualTo(MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED);
        assertThat(message.getRequestedBy()).isEqualTo("admin-user");
        log.info("管理后台重发商户回调测试完成，结果: 可靠 MQ 消息已冻结真实分片时间");
    }

    /** 非终态交易或不存在可重发通知任务时不得制造一个最终静默的 MQ 事件。 */
    @Test
    void shouldRejectWhenTransactionHasNoRetryableTerminalNotification() {
        ReliableMqPublisher publisher = mock(ReliableMqPublisher.class);
        AdminTransactionQueryService transactionQueryService = mock(AdminTransactionQueryService.class);
        AdminMerchantNotificationRetryApplicationService service =
                new AdminMerchantNotificationRetryApplicationService(publisher, transactionQueryService);
        MerchantNotificationRetryRequest request = new MerchantNotificationRetryRequest();
        request.setTransactionId("TX202608011600000000002");
        request.setTransactionDateTime(LocalDateTime.of(2026, 8, 1, 16, 0, 1));

        assertThatThrownBy(() -> service.retry(request, "admin-user"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("terminal transaction");
        verify(publisher, never()).publish(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(MerchantNotificationRetryMessage.class));
    }
}
