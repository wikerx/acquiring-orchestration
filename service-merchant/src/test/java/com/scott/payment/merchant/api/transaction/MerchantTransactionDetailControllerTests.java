package com.scott.payment.merchant.api.transaction;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.merchant.application.transaction.MerchantTransactionApplicationService;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionDetailResponse;
import org.junit.jupiter.api.AfterEach;
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
 * 商户后台交易详情接口契约测试，验证真实交易时间和登录商户边界共同进入详情查询。
 */
class MerchantTransactionDetailControllerTests {

    /** 认证上下文中的商户号，用于断言详情查询不能越过商户边界。 */
    private static final String MERCHANT_ID = "merchant-test";
    /** 详情接口使用的逻辑交易号；测试明确不从该值解析分片时间。 */
    private static final String TRANSACTION_ID = "202607140001";
    private static final LocalDateTime TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 7, 14, 12, 30, 45, 233_000_000);
    private static final LocalDateTime ROOT_TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 4, 10, 9, 15, 30);

    @AfterEach
    void clearAuthenticationContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void detailShouldAcceptListTransactionDateTime() throws Exception {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setMerchantId(MERCHANT_ID);
        InternalAuthContextHolder.set(account);
        MerchantTransactionApplicationService applicationService = mock(MerchantTransactionApplicationService.class);
        when(applicationService.detail(
                MERCHANT_ID, TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME))
                .thenReturn(new TransactionDetailResponse());
        MockMvc mockMvc = standaloneSetup(new MerchantTransactionOrderController(applicationService)).build();

        mockMvc.perform(get("/merchant/transactions/orders/{transactionId}", TRANSACTION_ID)
                        .param("transactionDateTime", "2026-07-14 12:30:45.233")
                        .param("rootTransactionDateTime", "2026-04-10 09:15:30.000"))
                .andExpect(status().isOk());

        verify(applicationService).detail(
                MERCHANT_ID, TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME);
    }
}
