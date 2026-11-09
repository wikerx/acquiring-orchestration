package com.scott.payment.merchant.application.transaction;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.MerchantNotificationRetryMessage;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationRetryRequest;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationRetryTarget;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.DeliveryLog;
import com.scott.payment.merchant.service.MerchantNotificationQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 商户交易通知人工重发应用服务测试，保护商户归属、精确任务定位和 Outbox 幂等消息契约。
 */
class MerchantNotificationApplicationServiceTests {

    private static final LocalDateTime TRANSACTION_TIME =
            LocalDateTime.of(2026, 11, 4, 16, 20, 0, 123_000_000);

    /**
     * 投递日志分页必须完整传递认证商户和精确分片定位。
     */
    @Test
    void shouldDelegateDeliveryLogPageWithMerchantBoundary() {
        MerchantNotificationQueryService queryService = mock(MerchantNotificationQueryService.class);
        ReliableMqPublisher publisher = mock(ReliableMqPublisher.class);
        MerchantNotificationApplicationService service =
                new MerchantNotificationApplicationService(queryService, publisher);
        PageResult<DeliveryLog> expected = PageResult.of(0L, 2L, 20L, List.of());
        when(queryService.pageDeliveryLogs(
                "merchant-a", "notify-1", TRANSACTION_TIME, 2L, 20L))
                .thenReturn(expected);

        PageResult<DeliveryLog> actual = service.pageDeliveryLogs(
                "merchant-a", "notify-1", TRANSACTION_TIME, 2L, 20L);

        assertThat(actual).isSameAs(expected);
        verify(queryService).pageDeliveryLogs(
                "merchant-a", "notify-1", TRANSACTION_TIME, 2L, 20L);
        verifyNoInteractions(publisher);
    }

    /**
     * 合法重发请求必须冻结认证商户、精确任务号和原交易分片时间后写入可靠 MQ。
     */
    @Test
    void shouldPublishExactMerchantNotificationRetryThroughOutbox() {
        MerchantNotificationQueryService queryService = mock(MerchantNotificationQueryService.class);
        ReliableMqPublisher publisher = mock(ReliableMqPublisher.class);
        MerchantNotificationApplicationService service =
                new MerchantNotificationApplicationService(queryService, publisher);
        NotificationRetryRequest request = request("request-20261104-0001");
        NotificationRetryTarget target = target();
        when(queryService.retryTarget("merchant-a", "notify-1", TRANSACTION_TIME))
                .thenReturn(target);
        when(publisher.publish(
                eq(MqTopic.PAYMENT_EVENT),
                eq(MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED),
                any(MerchantNotificationRetryMessage.class)))
                .thenAnswer(invocation -> invocation
                        .<MerchantNotificationRetryMessage>getArgument(2)
                        .getMessageId());

        String eventId = service.retry(
                "merchant-a", "merchant-account:merchant-admin", request);

        ArgumentCaptor<MerchantNotificationRetryMessage> message =
                ArgumentCaptor.forClass(MerchantNotificationRetryMessage.class);
        verify(publisher).publish(
                eq(MqTopic.PAYMENT_EVENT),
                eq(MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED),
                message.capture());
        assertThat(eventId).startsWith("MNR-M-").hasSize(42);
        assertThat(message.getValue().getNotifyId()).isEqualTo("notify-1");
        assertThat(message.getValue().getMerchantId()).isEqualTo("merchant-a");
        assertThat(message.getValue().getTransactionId()).isEqualTo("TX202611040001");
        assertThat(message.getValue().getTransactionDateTime()).isEqualTo(TRANSACTION_TIME);
        assertThat(message.getValue().getRequestedBy())
                .isEqualTo("merchant-account:merchant-admin");
    }

    /**
     * 相同商户和客户端请求号必须生成同一事件号，支持 HTTP 重试稳定命中 Outbox 唯一键。
     */
    @Test
    void shouldGenerateStableEventIdForDuplicateClientRequest() {
        MerchantNotificationQueryService queryService = mock(MerchantNotificationQueryService.class);
        ReliableMqPublisher publisher = mock(ReliableMqPublisher.class);
        MerchantNotificationApplicationService service =
                new MerchantNotificationApplicationService(queryService, publisher);
        when(queryService.retryTarget("merchant-a", "notify-1", TRANSACTION_TIME))
                .thenReturn(target());
        when(publisher.publish(
                eq(MqTopic.PAYMENT_EVENT),
                eq(MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED),
                any(MerchantNotificationRetryMessage.class)))
                .thenAnswer(invocation -> invocation
                        .<MerchantNotificationRetryMessage>getArgument(2)
                        .getMessageId());

        String first = service.retry(
                "merchant-a", "merchant-account:merchant-admin", request("request-duplicate"));
        String duplicate = service.retry(
                "merchant-a", "merchant-account:merchant-admin", request("request-duplicate"));

        assertThat(duplicate).isEqualTo(first);
    }

    /**
     * 认证商户无法读取其他商户的通知任务时，应用服务不得发布任何人工重发消息。
     */
    @Test
    void shouldNotPublishRetryForAnotherMerchantsNotification() {
        MerchantNotificationQueryService queryService = mock(MerchantNotificationQueryService.class);
        ReliableMqPublisher publisher = mock(ReliableMqPublisher.class);
        MerchantNotificationApplicationService service =
                new MerchantNotificationApplicationService(queryService, publisher);
        when(queryService.retryTarget("merchant-b", "notify-1", TRANSACTION_TIME))
                .thenThrow(new ApiException(ApiResultEnum.ORDER_NOT_FOUND));

        assertThatThrownBy(() -> service.retry(
                "merchant-b",
                "merchant-account:merchant-b-admin",
                request("request-cross-merchant")))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(publisher);
    }

    /**
     * 构造单次测试重发请求。
     *
     * @param requestId 客户端请求号
     * @return 完整重发请求
     */
    private NotificationRetryRequest request(String requestId) {
        NotificationRetryRequest request = new NotificationRetryRequest();
        request.setNotifyId("notify-1");
        request.setTransactionDateTime(TRANSACTION_TIME);
        request.setRequestId(requestId);
        return request;
    }

    /**
     * 构造已经通过商户归属和状态校验的精确任务定位。
     *
     * @return 测试通知任务定位
     */
    private NotificationRetryTarget target() {
        NotificationRetryTarget target = new NotificationRetryTarget();
        target.setNotifyId("notify-1");
        target.setTransactionId("TX202611040001");
        target.setTransactionDateTime(TRANSACTION_TIME);
        return target;
    }
}
