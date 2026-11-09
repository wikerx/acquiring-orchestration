package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminTransactionApplicationService;
import com.scott.payment.admin.application.transaction.AdminMerchantNotificationRetryApplicationService;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationDeliveryLogResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailRecordResponse;
import com.scott.payment.component.core.model.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * 管理后台交易详情接口契约测试，验证列表返回的毫秒交易时间可直接用于分片详情查询。
 */
class AdminTransactionDetailControllerTests {

    /**
     * 详情接口使用的逻辑交易号；测试明确不从该值解析分片时间。
     */
    private static final String TRANSACTION_ID = "202607140001";
    private static final LocalDateTime TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 7, 14, 12, 30, 45, 233_000_000);
    private static final LocalDateTime ROOT_TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 4, 10, 9, 15, 30);

    @Test
    void orderAndOperationDetailsShouldAcceptListTransactionDateTime() throws Exception {
        AdminTransactionApplicationService applicationService = mock(AdminTransactionApplicationService.class);
        when(applicationService.detailSummary(
                TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME))
                .thenReturn(new TransactionDetailResponse());
        MockMvc mockMvc = standaloneSetup(
                new AdminTransactionOrderController(applicationService),
                new AdminTransactionOperationController(applicationService))
                .build();

        mockMvc.perform(get("/admin/transactions/orders/{transactionId}", TRANSACTION_ID)
                        .param("transactionDateTime", "2026-07-14 12:30:45.233")
                        .param("rootTransactionDateTime", "2026-04-10 09:15:30.000"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/transactions/operations/{transactionId}", TRANSACTION_ID)
                        .param("transactionDateTime", "2026-07-14 12:30:45.233")
                        .param("rootTransactionDateTime", "2026-04-10 09:15:30.000"))
                .andExpect(status().isOk());

        verify(applicationService, times(2)).detailSummary(
                TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME);
    }

    @Test
    void orderAndOperationDetailRecordsShouldForwardSectionPaginationAndShardTimes() throws Exception {
        AdminTransactionApplicationService applicationService = mock(AdminTransactionApplicationService.class);
        when(applicationService.pageDetailRecords(
                TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME,
                "CHANNEL", 2L, 20L))
                .thenReturn(PageResult.of(0L, 2L, 20L, List.<TransactionDetailRecordResponse>of()));
        MockMvc mockMvc = standaloneSetup(
                new AdminTransactionOrderController(applicationService),
                new AdminTransactionOperationController(applicationService))
                .build();

        mockMvc.perform(get("/admin/transactions/orders/{transactionId}/detail-records", TRANSACTION_ID)
                        .param("transactionDateTime", "2026-07-14 12:30:45.233")
                        .param("rootTransactionDateTime", "2026-04-10 09:15:30.000")
                        .param("section", "CHANNEL")
                        .param("pageNo", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/transactions/operations/{transactionId}/detail-records", TRANSACTION_ID)
                        .param("transactionDateTime", "2026-07-14 12:30:45.233")
                        .param("rootTransactionDateTime", "2026-04-10 09:15:30.000")
                        .param("section", "CHANNEL")
                        .param("pageNo", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk());

        verify(applicationService, times(2)).pageDetailRecords(
                TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME,
                "CHANNEL", 2L, 20L);
    }

    @Test
    void orderDetailPayloadShouldForwardStableRecordLocator() throws Exception {
        AdminTransactionApplicationService applicationService = mock(AdminTransactionApplicationService.class);
        when(applicationService.detailRecordPayload(
                TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME,
                "MERCHANT_API", "api-log-a"))
                .thenReturn(new TransactionDetailRecordResponse());
        MockMvc mockMvc = standaloneSetup(new AdminTransactionOrderController(applicationService)).build();

        mockMvc.perform(get(
                        "/admin/transactions/orders/{transactionId}/detail-records/{recordType}/{recordId}",
                        TRANSACTION_ID, "MERCHANT_API", "api-log-a")
                        .param("transactionDateTime", "2026-07-14 12:30:45.233")
                        .param("rootTransactionDateTime", "2026-04-10 09:15:30.000"))
                .andExpect(status().isOk());

        verify(applicationService).detailRecordPayload(
                TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME,
                "MERCHANT_API", "api-log-a");
    }

    @Test
    void merchantNotificationDetailShouldAcceptListTransactionDateTime() throws Exception {
        AdminTransactionApplicationService applicationService = mock(AdminTransactionApplicationService.class);
        AdminMerchantNotificationRetryApplicationService retryApplicationService =
                mock(AdminMerchantNotificationRetryApplicationService.class);
        MerchantNotificationResponse notification = new MerchantNotificationResponse();
        notification.setNotifyId("notification-a");
        MerchantNotificationDetailResponse detailResponse = new MerchantNotificationDetailResponse();
        detailResponse.setNotification(notification);
        when(applicationService.merchantNotificationDetail("notification-a", TRANSACTION_DATE_TIME))
                .thenReturn(detailResponse);
        MockMvc mockMvc = standaloneSetup(new AdminTransactionMerchantNotificationController(
                applicationService, retryApplicationService)).build();

        mockMvc.perform(get("/admin/transactions/merchant-notifications/{notifyId}", "notification-a")
                        .param("transactionDateTime", "2026-07-14 12:30:45.233"))
                .andExpect(status().isOk());

        verify(applicationService).merchantNotificationDetail("notification-a", TRANSACTION_DATE_TIME);
    }

    @Test
    void merchantNotificationDeliveryLogsShouldForwardPaginationAndShardTime() throws Exception {
        AdminTransactionApplicationService applicationService = mock(AdminTransactionApplicationService.class);
        AdminMerchantNotificationRetryApplicationService retryApplicationService =
                mock(AdminMerchantNotificationRetryApplicationService.class);
        when(applicationService.pageMerchantNotificationDeliveryLogs(
                "notification-a", TRANSACTION_DATE_TIME, 2L, 20L))
                .thenReturn(PageResult.of(0L, 2L, 20L, List.<MerchantNotificationDeliveryLogResponse>of()));
        MockMvc mockMvc = standaloneSetup(new AdminTransactionMerchantNotificationController(
                applicationService, retryApplicationService)).build();

        mockMvc.perform(get("/admin/transactions/merchant-notifications/{notifyId}/delivery-logs", "notification-a")
                        .param("transactionDateTime", "2026-07-14 12:30:45.233")
                        .param("pageNo", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk());

        verify(applicationService).pageMerchantNotificationDeliveryLogs(
                "notification-a", TRANSACTION_DATE_TIME, 2L, 20L);
    }
}
