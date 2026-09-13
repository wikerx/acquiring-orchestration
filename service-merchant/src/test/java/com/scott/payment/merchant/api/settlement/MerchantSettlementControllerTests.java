package com.scott.payment.merchant.api.settlement;

import com.scott.payment.merchant.application.settlement.MerchantSettlementApplicationService;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchDetail;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ClearingDetail;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
    void transactionHistoryEndpointsShouldUseIndependentDetailPermissions() {
        Map<String, String> expected = Map.of(
                "voucher", "merchant:settlement:batch:voucher-download",
                "summaries", "merchant:settlement:batch:summary:list",
                "exportSummaries", "merchant:settlement:batch:summary:export",
                "clearingDetailByTransaction", "merchant:clearing:record:detail",
                "reconciliationRecordsByTransaction", "merchant:reconciliation:record:detail",
                "transactionItemsByTransaction", "merchant:settlement:transaction-item:transaction-detail",
                "reserveItemsByTransaction", "merchant:settlement:reserve-item:transaction-detail");

        expected.forEach((methodName, permission) -> {
            java.lang.reflect.Method method = java.util.Arrays.stream(
                            MerchantSettlementController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            assertThat(method.getAnnotation(RequiresPermission.class))
                    .extracting(RequiresPermission::value)
                    .isEqualTo(permission);
        });
    }

    @Test
    void detailShouldBindSettlementBatchNumberFromRequestPath() throws Exception {
        MerchantSettlementApplicationService applicationService = mock(MerchantSettlementApplicationService.class);
        when(applicationService.getBatch(SETTLEMENT_BATCH_NO)).thenReturn(new BatchDetail());
        MockMvc mockMvc = standaloneSetup(new MerchantSettlementController(applicationService)).build();

        mockMvc.perform(get("/merchant/settlements/{settlementBatchNo}", SETTLEMENT_BATCH_NO))
                .andExpect(status().isOk());

        verify(applicationService).getBatch(SETTLEMENT_BATCH_NO);
    }

    @Test
    void voucherShouldUseIndependentPermissionAndBindBatchNumber() throws Exception {
        MerchantSettlementApplicationService applicationService = mock(MerchantSettlementApplicationService.class);
        when(applicationService.getVoucher(SETTLEMENT_BATCH_NO)).thenReturn(new BatchDetail());
        MockMvc mockMvc = standaloneSetup(new MerchantSettlementController(applicationService)).build();

        mockMvc.perform(get("/merchant/settlements/{settlementBatchNo}/voucher", SETTLEMENT_BATCH_NO))
                .andExpect(status().isOk());

        verify(applicationService).getVoucher(SETTLEMENT_BATCH_NO);
    }

    @Test
    void summariesShouldBindBatchNumberAndPageWindow() throws Exception {
        MerchantSettlementApplicationService applicationService = mock(MerchantSettlementApplicationService.class);
        when(applicationService.searchResultSummaries(SETTLEMENT_BATCH_NO, 2, 50))
                .thenReturn(PageResult.of(0L, 2, 50, List.of()));
        MockMvc mockMvc = standaloneSetup(new MerchantSettlementController(applicationService)).build();

        mockMvc.perform(get("/merchant/settlements/{settlementBatchNo}/summaries", SETTLEMENT_BATCH_NO)
                        .param("pageNo", "2").param("pageSize", "50"))
                .andExpect(status().isOk());

        verify(applicationService).searchResultSummaries(SETTLEMENT_BATCH_NO, 2, 50);
    }

    @Test
    void transactionHistoryEndpointsShouldBindTransactionIdentity() throws Exception {
        MerchantSettlementApplicationService applicationService = mock(MerchantSettlementApplicationService.class);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 9, 9, 17, 34, 8, 160_000_000);
        when(applicationService.searchTransactionItemsByTransaction(
                "T-1001", transactionDateTime, 1, 20))
                .thenReturn(PageResult.of(0L, 1, 20, List.of()));
        when(applicationService.findReconciliationRecordsByTransaction(
                "T-1001", transactionDateTime)).thenReturn(List.of());
        when(applicationService.findClearingDetailByTransaction(
                "T-1001", transactionDateTime)).thenReturn(new ClearingDetail());
        when(applicationService.searchReserveItemsByTransaction(
                "T-1001", transactionDateTime, 1, 20))
                .thenReturn(PageResult.of(0L, 1, 20, List.of()));
        MockMvc mockMvc = standaloneSetup(new MerchantSettlementController(applicationService)).build();

        mockMvc.perform(get("/merchant/settlements/clearing-records/transactions/{transactionId}", "T-1001")
                        .param("transactionDateTime", "2026-09-09T17:34:08.160"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/merchant/settlements/reconciliation-records/transactions/{transactionId}", "T-1001")
                        .param("transactionDateTime", "2026-09-09T17:34:08.160"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/merchant/settlements/transaction-items/transactions/{transactionId}", "T-1001")
                        .param("transactionDateTime", "2026-09-09T17:34:08.160")
                        .param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/merchant/settlements/reserve-items/transactions/{transactionId}", "T-1001")
                        .param("transactionDateTime", "2026-09-09T17:34:08.160")
                        .param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk());

        verify(applicationService).findClearingDetailByTransaction(
                "T-1001", transactionDateTime);
        verify(applicationService).findReconciliationRecordsByTransaction(
                "T-1001", transactionDateTime);
        verify(applicationService).searchTransactionItemsByTransaction(
                "T-1001", transactionDateTime, 1, 20);
        verify(applicationService).searchReserveItemsByTransaction(
                "T-1001", transactionDateTime, 1, 20);
    }
}
