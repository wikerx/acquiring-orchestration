package com.scott.payment.merchant.api.transaction;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.application.transaction.MerchantNotificationApplicationService;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.DeliveryLog;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationQuery;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationRetryRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户交易通知控制器契约测试，验证商户号只能来自认证上下文。
 */
class MerchantTransactionNotificationControllerTests {

    private static final String MERCHANT_ID = "merchant-a";
    private static final LocalDateTime TRANSACTION_TIME =
            LocalDateTime.of(2026, 11, 4, 16, 20, 0, 123_000_000);

    /**
     * 每个用例结束后清理线程认证上下文。
     */
    @AfterEach
    void clearAuthenticationContext() {
        InternalAuthContextHolder.clear();
    }

    /**
     * 列表和详情必须把当前认证商户号传给应用服务。
     */
    @Test
    void shouldPassAuthenticatedMerchantToListAndDetail() {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setMerchantId(MERCHANT_ID);
        account.setLoginAccount("merchant-admin");
        InternalAuthContextHolder.set(account);
        MerchantNotificationApplicationService applicationService =
                mock(MerchantNotificationApplicationService.class);
        NotificationQuery query = new NotificationQuery();
        when(applicationService.page(MERCHANT_ID, query))
                .thenReturn(PageResult.of(0L, 1L, 10L, List.of()));
        when(applicationService.detail(MERCHANT_ID, "notify-1", TRANSACTION_TIME))
                .thenReturn(new NotificationDetailResponse());
        when(applicationService.pageDeliveryLogs(
                MERCHANT_ID, "notify-1", TRANSACTION_TIME, 2L, 20L))
                .thenReturn(PageResult.of(0L, 2L, 20L, List.<DeliveryLog>of()));
        NotificationRetryRequest retryRequest = new NotificationRetryRequest();
        retryRequest.setNotifyId("notify-1");
        retryRequest.setTransactionDateTime(TRANSACTION_TIME);
        retryRequest.setRequestId("request-1");
        when(applicationService.retry(
                MERCHANT_ID, "merchant-account:merchant-admin", retryRequest))
                .thenReturn("MNR-M-event-1");
        MerchantTransactionNotificationController controller =
                new MerchantTransactionNotificationController(applicationService);

        controller.search(query);
        controller.detail("notify-1", TRANSACTION_TIME);
        controller.deliveryLogs("notify-1", TRANSACTION_TIME, 2L, 20L);
        controller.retry(retryRequest);

        verify(applicationService).page(MERCHANT_ID, query);
        verify(applicationService).detail(MERCHANT_ID, "notify-1", TRANSACTION_TIME);
        verify(applicationService).pageDeliveryLogs(
                MERCHANT_ID, "notify-1", TRANSACTION_TIME, 2L, 20L);
        verify(applicationService).retry(
                MERCHANT_ID, "merchant-account:merchant-admin", retryRequest);
    }

    /**
     * 缺少认证商户上下文时必须在调用应用服务前拒绝请求。
     */
    @Test
    void shouldRejectMissingMerchantContext() {
        MerchantNotificationApplicationService applicationService =
                mock(MerchantNotificationApplicationService.class);
        MerchantTransactionNotificationController controller =
                new MerchantTransactionNotificationController(applicationService);

        assertThatThrownBy(() -> controller.search(new NotificationQuery()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("merchant context missing");
    }
}
