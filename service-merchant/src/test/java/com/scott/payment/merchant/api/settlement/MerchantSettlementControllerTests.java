package com.scott.payment.merchant.api.settlement;

import com.scott.payment.merchant.application.settlement.MerchantSettlementApplicationService;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchDetail;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSettlementControllerTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户结算详情接口契约测试，验证批次号可在未保留 Java 参数名时由 Spring MVC 稳定绑定。
 * @status : create
 */
class MerchantSettlementControllerTests {

    private static final String SETTLEMENT_BATCH_NO = "SB20260831-00000001";

    @Test
    void detailShouldBindSettlementBatchNumberFromRequestPath() throws Exception {
        MerchantSettlementApplicationService applicationService = mock(MerchantSettlementApplicationService.class);
        when(applicationService.getBatch(SETTLEMENT_BATCH_NO)).thenReturn(new BatchDetail());
        MockMvc mockMvc = standaloneSetup(new MerchantSettlementController(applicationService)).build();

        mockMvc.perform(get("/merchant/settlements/{settlementBatchNo}", SETTLEMENT_BATCH_NO))
                .andExpect(status().isOk());

        verify(applicationService).getBatch(SETTLEMENT_BATCH_NO);
    }
}
