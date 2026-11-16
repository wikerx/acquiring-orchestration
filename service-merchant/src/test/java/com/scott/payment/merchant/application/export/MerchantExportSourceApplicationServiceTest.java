package com.scott.payment.merchant.application.export;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.application.transaction.MerchantRefundApplicationService;
import com.scott.payment.merchant.application.transaction.MerchantTransactionApplicationService;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantRefundQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantFundLedgerQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementReserveQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementTransactionQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantTransactionQuery;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.RefundPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.FundLedgerPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementReservePageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementTransactionPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.TransactionPageRequest;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionSettlement;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundRecord;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundSearchResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.merchant.service.MerchantSettlementQueryService;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.DetailQuery;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundLedgerResponse;
import com.scott.payment.merchant.service.MerchantFinanceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户导出来源安全投影测试，验证标签金额口径和服务端商户范围不会被渠道字段污染。
 */
class MerchantExportSourceApplicationServiceTest {

    /**
     * 交易来源即使包含渠道交易金额和汇率，导出行也只能返回商户标签金额和币种。
     */
    @Test
    void shouldProjectTransactionLabelAmountWithoutChannelAmountOrRate() {
        MerchantTransactionApplicationService transactionService = mock(MerchantTransactionApplicationService.class);
        MerchantRefundApplicationService refundService = mock(MerchantRefundApplicationService.class);
        MerchantExportSourceApplicationService service =
                new MerchantExportSourceApplicationService(
                        transactionService, refundService, mock(MerchantSettlementQueryService.class),
                        mock(MerchantFinanceService.class));
        TransactionOperationResponse operation = new TransactionOperationResponse();
        operation.setTransactionId("TXN1001");
        operation.setLabelAmount(new BigDecimal("100.00"));
        operation.setLabelCurrency("EUR");
        operation.setTransactionAmount(new BigDecimal("110.00"));
        operation.setTransactionCurrency("USD");
        operation.setTransactionRate(new BigDecimal("1.10000000"));
        TransactionOperationSearchResponse searchResponse = new TransactionOperationSearchResponse();
        searchResponse.setPage(PageResult.of(1L, 1, 100, List.of(operation)));
        when(transactionService.searchOperations(eq("M1001"), any())).thenReturn(searchResponse);
        MerchantTransactionQuery query = new MerchantTransactionQuery();
        query.setMerchantOrderNo("ORDER-1001");
        TransactionPageRequest request = new TransactionPageRequest();
        request.setMerchantId("M1001");
        request.setQuery(query);
        request.setPageNo(1);
        request.setPageSize(100);

        var response = service.pageTransactions(request);

        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getRecords().get(0).getTransactionAmount()).isEqualByComparingTo("100.00");
        assertThat(response.getRecords().get(0).getTransactionCurrency()).isEqualTo("EUR");
        assertThat(response.getRecords().get(0).getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("channelOrderNo", "channelCode", "transactionRate");
        ArgumentCaptor<com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery>
                queryCaptor = ArgumentCaptor.forClass(
                com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery.class);
        verify(transactionService).searchOperations(eq("M1001"), queryCaptor.capture());
        assertThat(queryCaptor.getValue().getMerchantId()).isNull();
    }

    /**
     * 退款导出同样必须使用标签金额和标签币种，不允许引入渠道交易口径。
     */
    @Test
    void shouldProjectRefundLabelAmount() {
        MerchantTransactionApplicationService transactionService = mock(MerchantTransactionApplicationService.class);
        MerchantRefundApplicationService refundService = mock(MerchantRefundApplicationService.class);
        MerchantExportSourceApplicationService service =
                new MerchantExportSourceApplicationService(
                        transactionService, refundService, mock(MerchantSettlementQueryService.class),
                        mock(MerchantFinanceService.class));
        RefundRecord refund = new RefundRecord();
        refund.setRefundTransactionId("RFD1001");
        refund.setLabelAmount(new BigDecimal("20.00"));
        refund.setLabelCurrency("EUR");
        RefundSearchResponse searchResponse = new RefundSearchResponse();
        searchResponse.setPage(PageResult.of(1L, 1, 100, List.of(refund)));
        when(refundService.search(eq("M1001"), any())).thenReturn(searchResponse);
        RefundPageRequest request = new RefundPageRequest();
        request.setMerchantId("M1001");
        request.setQuery(new MerchantRefundQuery());
        request.setPageNo(1);
        request.setPageSize(100);

        var response = service.pageRefunds(request);

        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getRecords().get(0).getTransactionAmount()).isEqualByComparingTo("20.00");
        assertThat(response.getRecords().get(0).getTransactionCurrency()).isEqualTo("EUR");
    }

    /**
     * 交易结算导出必须原样复制清分来源金额和商户结算金额，不得混入渠道交易口径。
     */
    @Test
    void shouldProjectSettlementTransactionAmountsWithoutChannelFields() {
        MerchantSettlementQueryService settlementService = mock(MerchantSettlementQueryService.class);
        MerchantExportSourceApplicationService service = new MerchantExportSourceApplicationService(
                mock(MerchantTransactionApplicationService.class),
                mock(MerchantRefundApplicationService.class), settlementService,
                mock(MerchantFinanceService.class));
        TransactionSettlement settlement = new TransactionSettlement();
        settlement.setSettlementBatchNo("ST20261110-001");
        settlement.setSourceTransactionId("TXN2001");
        settlement.setSourceAmount(new BigDecimal("80.12340000"));
        settlement.setSourceCurrency("EUR");
        settlement.setNetTargetAmount(new BigDecimal("88.13574000"));
        settlement.setTargetCurrency("USD");
        when(settlementService.searchTransactionItems(eq("M1001"), any()))
                .thenReturn(PageResult.of(1L, 1, 100, List.of(settlement)));
        SettlementTransactionPageRequest request = new SettlementTransactionPageRequest();
        request.setMerchantId("M1001");
        request.setQuery(new MerchantSettlementTransactionQuery());
        request.setPageNo(1);
        request.setPageSize(100);

        var response = service.pageSettlementTransactions(request);

        assertThat(response.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getSourceAmount()).isEqualByComparingTo("80.12340000");
            assertThat(row.getSourceCurrency()).isEqualTo("EUR");
            assertThat(row.getNetTargetAmount()).isEqualByComparingTo("88.13574000");
            assertThat(row.getTargetCurrency()).isEqualTo("USD");
            assertThat(row.getClass().getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .doesNotContain("channelAmount", "channelCurrency", "channelRate", "channelOrderNo");
        });
    }

    /**
     * 保证金导出必须保持原标签币种、动作金额和责任余额，不参与结算换汇。
     */
    @Test
    void shouldProjectReserveAmountInOriginalLabelCurrency() {
        MerchantSettlementQueryService settlementService = mock(MerchantSettlementQueryService.class);
        MerchantExportSourceApplicationService service = new MerchantExportSourceApplicationService(
                mock(MerchantTransactionApplicationService.class),
                mock(MerchantRefundApplicationService.class), settlementService,
                mock(MerchantFinanceService.class));
        ReserveItem reserve = new ReserveItem();
        reserve.setSettlementBatchNo("ST20261110-001");
        reserve.setReserveNo("RSV2001");
        reserve.setCurrency("EUR");
        reserve.setAmount(new BigDecimal("7.12340000"));
        reserve.setRemainingAmount(new BigDecimal("5.00340000"));
        when(settlementService.searchReserveItems(eq("M1001"), any()))
                .thenReturn(PageResult.of(1L, 1, 100, List.of(reserve)));
        SettlementReservePageRequest request = new SettlementReservePageRequest();
        request.setMerchantId("M1001");
        request.setQuery(new MerchantSettlementReserveQuery());
        request.setPageNo(1);
        request.setPageSize(100);

        var response = service.pageSettlementReserves(request);

        assertThat(response.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getCurrency()).isEqualTo("EUR");
            assertThat(row.getAmount()).isEqualByComparingTo("7.12340000");
            assertThat(row.getRemainingAmount()).isEqualByComparingTo("5.00340000");
        });
    }

    /**
     * 余额流水来源必须同时限定冻结商户和账户，并原样返回金额与前后余额。
     */
    @Test
    void shouldProjectFundLedgerWithFrozenMerchantAccountAndExactBalances() {
        MerchantFinanceService financeService = mock(MerchantFinanceService.class);
        MerchantExportSourceApplicationService service = new MerchantExportSourceApplicationService(
                mock(MerchantTransactionApplicationService.class),
                mock(MerchantRefundApplicationService.class),
                mock(MerchantSettlementQueryService.class), financeService);
        FundLedgerResponse ledger = new FundLedgerResponse();
        ledger.setLedgerNo("LEDGER-1001");
        ledger.setCurrency("EUR");
        ledger.setAmount(new BigDecimal("0.01000000"));
        ledger.setBalanceBefore(new BigDecimal("100.12000000"));
        ledger.setBalanceAfter(new BigDecimal("100.13000000"));
        when(financeService.pageLedgers(eq("M1001"), eq(7001L), any()))
                .thenReturn(PageResult.of(1L, 2, 137, List.of(ledger)));
        MerchantFundLedgerQuery query = new MerchantFundLedgerQuery();
        query.setAccountId(7001L);
        query.setKeyword("LEDGER-1001");
        FundLedgerPageRequest request = new FundLedgerPageRequest();
        request.setMerchantId("M1001");
        request.setQuery(query);
        request.setPageNo(2);
        request.setPageSize(137);

        var response = service.pageFundLedgers(request);

        assertThat(response.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getCurrency()).isEqualTo("EUR");
            assertThat(row.getAmount()).isEqualByComparingTo("0.01000000");
            assertThat(row.getBalanceBefore()).isEqualByComparingTo("100.12000000");
            assertThat(row.getBalanceAfter()).isEqualByComparingTo("100.13000000");
            assertThat(row.getClass().getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .doesNotContain("accountId", "transactionId", "idempotencyKey", "requestId");
        });
        ArgumentCaptor<DetailQuery> queryCaptor = ArgumentCaptor.forClass(DetailQuery.class);
        verify(financeService).pageLedgers(eq("M1001"), eq(7001L), queryCaptor.capture());
        assertThat(queryCaptor.getValue().getKeyword()).isEqualTo("LEDGER-1001");
        assertThat(queryCaptor.getValue().getPageNo()).isEqualTo(2);
        assertThat(queryCaptor.getValue().getPageSize()).isEqualTo(137);
    }
}
