package com.scott.payment.payment.api.internal;

import com.scott.payment.payment.application.PaymentCheckoutApplicationService;
import com.scott.payment.payment.application.PaymentTransactionApplicationService;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Payment 内部交易详情接口契约测试，验证内部 REST 客户端传入的真实分片时间不会在 HTTP 层丢失。
 */
class PaymentInternalTransactionDetailControllerTests {

    /** 用于验证内部详情路由参数绑定的平台交易号。 */
    private static final String TRANSACTION_ID = "202607140001";
    private static final LocalDateTime TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 7, 14, 12, 30, 45, 233_000_000);
    private static final LocalDateTime ROOT_TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 4, 10, 9, 15, 30);

    /**
     * 验证内部客户端采用的 ISO 格式可无损绑定，并原样传入应用服务。
     */
    @Test
    void detailShouldAcceptInternalClientTransactionDateTime() throws Exception {
        PaymentTransactionApplicationService applicationService = mock(PaymentTransactionApplicationService.class);
        when(applicationService.detail(
                TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME))
                .thenReturn(new TransactionDetailResponse());
        MockMvc mockMvc = standaloneSetup(new PaymentInternalController(
                applicationService, mock(PaymentCheckoutApplicationService.class))).build();

        mockMvc.perform(get("/internal/payment/transactions/{transactionId}", TRANSACTION_ID)
                        .param("transactionDateTime", "2026-07-14T12:30:45.233")
                        .param("rootTransactionDateTime", "2026-04-10T09:15:30"))
                .andExpect(status().isOk());

        verify(applicationService).detail(
                TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME);
    }
}
