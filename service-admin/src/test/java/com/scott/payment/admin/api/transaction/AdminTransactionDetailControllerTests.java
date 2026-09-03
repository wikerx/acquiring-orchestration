package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminTransactionApplicationService;
import com.scott.payment.admin.application.transaction.AdminMerchantNotificationRetryApplicationService;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionDetailControllerTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 管理后台交易详情接口契约测试，验证列表返回的毫秒交易时间可直接用于分片详情查询。
 * @status : create
 */
class AdminTransactionDetailControllerTests {

    /** 详情接口使用的逻辑交易号；测试明确不从该值解析分片时间。 */
    private static final String TRANSACTION_ID = "202607140001";
    private static final LocalDateTime TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 7, 14, 12, 30, 45, 233_000_000);
    private static final LocalDateTime ROOT_TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 4, 10, 9, 15, 30);

    @Test
    void orderAndOperationDetailsShouldAcceptListTransactionDateTime() throws Exception {
        AdminTransactionApplicationService applicationService = mock(AdminTransactionApplicationService.class);
        when(applicationService.detail(
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

        verify(applicationService, times(2)).detail(
                TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME);
    }

    @Test
    void merchantNotificationDetailShouldAcceptListTransactionDateTime() throws Exception {
        AdminTransactionApplicationService applicationService = mock(AdminTransactionApplicationService.class);
        AdminMerchantNotificationRetryApplicationService retryApplicationService =
                mock(AdminMerchantNotificationRetryApplicationService.class);
        when(applicationService.merchantNotificationDetail("notification-a", TRANSACTION_DATE_TIME))
                .thenReturn(Map.of("notification", Map.of("notifyId", "notification-a"), "deliveryLogs", java.util.List.of()));
        MockMvc mockMvc = standaloneSetup(new AdminTransactionMerchantNotificationController(
                applicationService, retryApplicationService)).build();

        mockMvc.perform(get("/admin/transactions/merchant-notifications/{notifyId}", "notification-a")
                        .param("transactionDateTime", "2026-07-14 12:30:45.233"))
                .andExpect(status().isOk());

        verify(applicationService).merchantNotificationDetail("notification-a", TRANSACTION_DATE_TIME);
    }
}
