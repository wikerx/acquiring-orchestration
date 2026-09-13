package com.scott.payment.merchant.application.settlement;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.merchant.dto.export.MerchantSettlementBatchExportRow;
import com.scott.payment.merchant.dto.export.MerchantSettlementReserveExportRow;
import com.scott.payment.merchant.dto.export.MerchantSettlementTransactionSummaryExportRow;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchSummary;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ClearingDetail;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.SummaryLine;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionSettlement;
import com.scott.payment.merchant.service.MerchantSettlementQueryService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSettlementApplicationServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Merchant 结算应用服务必须只使用可信登录上下文中的商户号。
 * @status : create
 */
class MerchantSettlementApplicationServiceTests {

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void searchShouldBindAuthenticatedMerchantToLocalQueryService() {
        MerchantSettlementQueryService queryService = mock(MerchantSettlementQueryService.class);
        MerchantSettlementApplicationService service = service(queryService);
        InternalAuthAccount account = new InternalAuthAccount();
        account.setMerchantId("M10000001");
        InternalAuthContextHolder.set(account);
        BatchQuery query = new BatchQuery();
        when(queryService.searchBatches("M10000001", query))
                .thenReturn(PageResult.of(0L, 1, 10, List.of()));

        service.searchBatches(query);

        verify(queryService).searchBatches("M10000001", query);
    }

    @Test
    void searchShouldRejectMissingMerchantContextBeforeQuery() {
        MerchantSettlementApplicationService service = service(mock(MerchantSettlementQueryService.class));

        assertThatThrownBy(() -> service.searchBatches(new BatchQuery()))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void transactionHistoryShouldBindAuthenticatedMerchant() {
        MerchantSettlementQueryService queryService = mock(MerchantSettlementQueryService.class);
        MerchantSettlementApplicationService service = service(queryService);
        InternalAuthAccount account = new InternalAuthAccount();
        account.setMerchantId("M10000001");
        InternalAuthContextHolder.set(account);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 9, 9, 17, 34, 8, 160_000_000);
        when(queryService.searchTransactionItemsByTransaction(
                "M10000001", "T-1001", transactionDateTime, 1, 20))
                .thenReturn(PageResult.of(0L, 1, 20, List.of()));
        when(queryService.findReconciliationRecordsByTransaction(
                "M10000001", "T-1001", transactionDateTime)).thenReturn(List.of());
        when(queryService.findClearingDetailByTransaction(
                "M10000001", "T-1001", transactionDateTime)).thenReturn(new ClearingDetail());
        when(queryService.searchReserveItemsByTransaction(
                "M10000001", "T-1001", transactionDateTime, 1, 20))
                .thenReturn(PageResult.of(0L, 1, 20, List.of()));

        service.findClearingDetailByTransaction("T-1001", transactionDateTime);
        service.findReconciliationRecordsByTransaction("T-1001", transactionDateTime);
        service.searchTransactionItemsByTransaction("T-1001", transactionDateTime, 1, 20);
        service.searchReserveItemsByTransaction("T-1001", transactionDateTime, 1, 20);

        verify(queryService).findClearingDetailByTransaction(
                "M10000001", "T-1001", transactionDateTime);
        verify(queryService).findReconciliationRecordsByTransaction(
                "M10000001", "T-1001", transactionDateTime);
        verify(queryService).searchTransactionItemsByTransaction(
                "M10000001", "T-1001", transactionDateTime, 1, 20);
        verify(queryService).searchReserveItemsByTransaction(
                "M10000001", "T-1001", transactionDateTime, 1, 20);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void summaryQueryAndExportShouldRebindAuthenticatedMerchantForEveryPage() {
        MerchantSettlementQueryService queryService = mock(MerchantSettlementQueryService.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExcelI18nMessageResolver messageResolver = mock(ExcelI18nMessageResolver.class);
        ExcelLocaleResolver localeResolver = mock(ExcelLocaleResolver.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(localeResolver.resolveCurrentLocale()).thenReturn(Locale.SIMPLIFIED_CHINESE);
        when(messageResolver.resolve(anyString(), eq(Locale.SIMPLIFIED_CHINESE)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(queryService.searchResultSummaries(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    Integer pageNo = invocation.getArgument(2);
                    Integer pageSize = invocation.getArgument(3);
                    return PageResult.of(0L, pageNo, pageSize, List.<SummaryLine>of());
                });
        MerchantSettlementApplicationService service = new MerchantSettlementApplicationService(
                queryService, excelExportService, messageResolver, localeResolver);

        InternalAuthContextHolder.set(merchantAccount("M10000001"));
        service.searchResultSummaries("SB20260831-00000001", 1, 20);
        service.exportResultSummaries("SB20260831-00000001", response);

        verify(queryService).searchResultSummaries(
                "M10000001", "SB20260831-00000001", 1, 20);
        ArgumentCaptor<ExcelPagedExportRequest> requestCaptor =
                ArgumentCaptor.forClass(ExcelPagedExportRequest.class);
        verify(excelExportService).exportPaged(requestCaptor.capture(), eq(response));
        InternalAuthContextHolder.set(merchantAccount("M10000002"));
        requestCaptor.getValue().getPageLoader().apply(1);
        InternalAuthContextHolder.set(merchantAccount("M10000003"));
        requestCaptor.getValue().getPageLoader().apply(2);

        verify(queryService).searchResultSummaries(
                "M10000002", "SB20260831-00000001", 1, 200);
        verify(queryService).searchResultSummaries(
                "M10000003", "SB20260831-00000001", 2, 200);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void settlementExportsShouldLocalizeOperationalEnums() {
        MerchantSettlementQueryService queryService = mock(MerchantSettlementQueryService.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExcelI18nMessageResolver messageResolver = mock(ExcelI18nMessageResolver.class);
        ExcelLocaleResolver localeResolver = mock(ExcelLocaleResolver.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(localeResolver.resolveCurrentLocale()).thenReturn(Locale.SIMPLIFIED_CHINESE);
        when(messageResolver.resolve(anyString(), eq(Locale.SIMPLIFIED_CHINESE)))
                .thenAnswer(invocation -> localizedMessage(invocation.getArgument(0)));

        BatchSummary batch = new BatchSummary();
        batch.setBatchType("REGULAR");
        batch.setBatchStatus("POSTED");
        batch.setTransactionCount(12L);
        batch.setCandidateCount(14);
        batch.setNetDirection("CREDIT");
        when(queryService.searchBatches(eq("M10000001"), any(BatchQuery.class)))
                .thenReturn(PageResult.of(1L, 1, 200, List.of(batch)));

        TransactionSettlement transaction = new TransactionSettlement();
        transaction.setPaymentType("BANK_CARD");
        transaction.setPaymentMethod("MASTERCARD");
        transaction.setTransactionType("PAYMENT");
        transaction.setComponentCount(3L);
        transaction.setNetDirection("CREDIT");
        when(queryService.searchTransactionItems(eq("M10000001"), any(TransactionItemQuery.class)))
                .thenReturn(PageResult.of(1L, 1, 200, List.of(transaction)));

        ReserveItem reserve = new ReserveItem();
        reserve.setActionType("RELEASE");
        reserve.setDirection("CREDIT");
        reserve.setReserveStatus("FROZEN");
        when(queryService.searchReserveItems(eq("M10000001"), any(ReserveItemQuery.class)))
                .thenReturn(PageResult.of(1L, 1, 200, List.of(reserve)));

        MerchantSettlementApplicationService service = new MerchantSettlementApplicationService(
                queryService, excelExportService, messageResolver, localeResolver);
        InternalAuthAccount account = new InternalAuthAccount();
        account.setMerchantId("M10000001");
        account.setLoginAccount("merchant-operator");
        InternalAuthContextHolder.set(account);

        service.exportBatches(new BatchQuery(), response);
        service.exportTransactionItems(new TransactionItemQuery(), response);
        service.exportReserveItems(new ReserveItemQuery(), response);

        ArgumentCaptor<ExcelPagedExportRequest> requestCaptor =
                ArgumentCaptor.forClass(ExcelPagedExportRequest.class);
        verify(excelExportService, times(3)).exportPaged(requestCaptor.capture(), eq(response));
        List<ExcelPagedExportRequest> requests = requestCaptor.getAllValues();

        List<MerchantSettlementBatchExportRow> batchRows =
                (List<MerchantSettlementBatchExportRow>) requests.get(0).getPageLoader().apply(1);
        assertThat(batchRows).singleElement().satisfies(row -> {
            assertThat(row.getBatchType()).isEqualTo("常规交易结算");
            assertThat(row.getBatchStatus()).isEqualTo("已入账");
            assertThat(row.getTransactionCount()).isEqualTo(12L);
            assertThat(row.getSettlementItemCount()).isEqualTo(14);
            assertThat(row.getNetDirection()).isEqualTo("增加");
        });

        List<MerchantSettlementTransactionSummaryExportRow> transactionRows =
                (List<MerchantSettlementTransactionSummaryExportRow>) requests.get(1).getPageLoader().apply(1);
        assertThat(transactionRows).singleElement().satisfies(row -> {
            assertThat(row.getPaymentType()).isEqualTo("银行卡");
            assertThat(row.getPaymentMethod()).isEqualTo("Mastercard");
            assertThat(row.getTransactionType()).isEqualTo("支付");
            assertThat(row.getComponentCount()).isEqualTo(3L);
            assertThat(row.getNetDirection()).isEqualTo("增加");
        });

        List<MerchantSettlementReserveExportRow> reserveRows =
                (List<MerchantSettlementReserveExportRow>) requests.get(2).getPageLoader().apply(1);
        assertThat(reserveRows).singleElement().satisfies(row -> {
            assertThat(row.getActionType()).isEqualTo("释放");
            assertThat(row.getDirection()).isEqualTo("增加");
            assertThat(row.getReserveStatus()).isEqualTo("已冻结");
        });
    }

    private MerchantSettlementApplicationService service(MerchantSettlementQueryService queryService) {
        return new MerchantSettlementApplicationService(
                queryService,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class));
    }

    private InternalAuthAccount merchantAccount(String merchantId) {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setMerchantId(merchantId);
        account.setLoginAccount("merchant-operator");
        return account;
    }

    private static String localizedMessage(String key) {
        return switch (key) {
            case "excel.merchantSettlement.batchTitle" -> "商户交易结算";
            case "excel.merchantSettlement.transactionTitle" -> "商户交易结算明细";
            case "excel.merchantSettlement.reserveTitle" -> "商户保证金结算明细";
            case "excel.settlement.enum.batchType.REGULAR" -> "常规交易结算";
            case "excel.settlement.enum.batchStatus.POSTED" -> "已入账";
            case "excel.settlement.enum.direction.CREDIT" -> "增加";
            case "excel.settlement.enum.resultItemType.PRINCIPAL" -> "本金";
            case "excel.settlement.enum.paymentType.BANK_CARD" -> "银行卡";
            case "excel.settlement.enum.paymentMethod.MASTERCARD" -> "Mastercard";
            case "excel.settlement.enum.transactionType.PAYMENT" -> "支付";
            case "excel.settlement.enum.feeCategory.NONE" -> "本金";
            case "excel.settlement.enum.appliedLimit.NONE" -> "未命中";
            case "excel.settlement.enum.reserveAction.RELEASE" -> "释放";
            case "excel.settlement.enum.reserveStatus.FROZEN" -> "已冻结";
            default -> key;
        };
    }
}
