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
import com.scott.payment.merchant.dto.export.MerchantSettlementTransactionExportRow;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchSummary;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;
import com.scott.payment.merchant.service.MerchantSettlementQueryService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;

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

        TransactionItem transaction = new TransactionItem();
        transaction.setResultItemType("PRINCIPAL");
        transaction.setPaymentType("BANK_CARD");
        transaction.setPaymentMethod("MASTERCARD");
        transaction.setTransactionType("PAYMENT");
        transaction.setFeeCategory("NONE");
        transaction.setDirection("CREDIT");
        transaction.setAppliedLimit("NONE");
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

        List<MerchantSettlementTransactionExportRow> transactionRows =
                (List<MerchantSettlementTransactionExportRow>) requests.get(1).getPageLoader().apply(1);
        assertThat(transactionRows).singleElement().satisfies(row -> {
            assertThat(row.getResultItemType()).isEqualTo("本金");
            assertThat(row.getPaymentType()).isEqualTo("银行卡");
            assertThat(row.getPaymentMethod()).isEqualTo("Mastercard");
            assertThat(row.getTransactionType()).isEqualTo("支付");
            assertThat(row.getFeeCategory()).isEqualTo("本金");
            assertThat(row.getDirection()).isEqualTo("增加");
            assertThat(row.getAppliedLimit()).isEqualTo("未命中");
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

    private static String localizedMessage(String key) {
        return switch (key) {
            case "excel.merchantSettlement.batchTitle" -> "商户结算账单";
            case "excel.merchantSettlement.transactionTitle" -> "商户交易结算明细";
            case "excel.merchantSettlement.reserveTitle" -> "商户保证金明细";
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
