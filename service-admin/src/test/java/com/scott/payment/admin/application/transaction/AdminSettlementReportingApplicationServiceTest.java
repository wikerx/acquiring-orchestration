package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.dto.export.SettlementResultItemExportRow;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminSettlementReportingQueryService;
import com.scott.payment.admin.service.AdminSettlementReviewQueryService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
 * @classname : AdminSettlementReportingApplicationServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证结算报表分页导出在每页加载时重新解析可信 Admin 账号和商户数据范围。
 * @status : create
 */
class AdminSettlementReportingApplicationServiceTest {

    @AfterEach
    void tearDown() {
        InternalAuthContextHolder.clear();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void everyExportPageShouldResolveCurrentTrustedAccountAndMerchantScope() {
        AdminSettlementReviewQueryService reviewQueryService = mock(AdminSettlementReviewQueryService.class);
        AdminSettlementReportingQueryService reportingQueryService =
                mock(AdminSettlementReportingQueryService.class);
        AdminMerchantDataScopeResolver dataScopeResolver = mock(AdminMerchantDataScopeResolver.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExcelI18nMessageResolver messageResolver = mock(ExcelI18nMessageResolver.class);
        ExcelLocaleResolver localeResolver = mock(ExcelLocaleResolver.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(localeResolver.resolveCurrentLocale()).thenReturn(Locale.SIMPLIFIED_CHINESE);
        when(messageResolver.resolve(anyString(), eq(Locale.SIMPLIFIED_CHINESE)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dataScopeResolver.resolve(any(InternalAuthAccount.class)))
                .thenAnswer(invocation -> scopeFor(invocation.getArgument(0)));
        when(reviewQueryService.searchReviews(any(ReviewSearchRequest.class), any(AdminMerchantDataScope.class)))
                .thenReturn(PageResult.of(0, 1, 200, List.of()));
        when(reportingQueryService.searchResultItems(
                any(ResultItemSearchRequest.class), any(AdminMerchantDataScope.class)))
                .thenReturn(PageResult.of(0, 1, 200, List.of()));
        when(reportingQueryService.searchPostings(
                any(PostingSearchRequest.class), any(AdminMerchantDataScope.class)))
                .thenReturn(PageResult.of(0, 1, 200, List.of()));
        AdminSettlementReportingApplicationService service = new AdminSettlementReportingApplicationService(
                reviewQueryService, reportingQueryService, dataScopeResolver, excelExportService,
                messageResolver, localeResolver);

        InternalAuthContextHolder.set(adminAccount(1L));
        service.exportReviews(new ReviewSearchRequest(), response);
        service.exportResultItems(new ResultItemSearchRequest(), response);
        service.exportPostings(new PostingSearchRequest(), response);

        ArgumentCaptor<ExcelPagedExportRequest> exportRequestCaptor =
                ArgumentCaptor.forClass(ExcelPagedExportRequest.class);
        verify(excelExportService, times(3)).exportPaged(exportRequestCaptor.capture(), eq(response));
        List<ExcelPagedExportRequest> exportRequests = exportRequestCaptor.getAllValues();

        loadTwoPages(exportRequests.get(0), 11L, 12L);
        loadTwoPages(exportRequests.get(1), 21L, 22L);
        loadTwoPages(exportRequests.get(2), 31L, 32L);

        ArgumentCaptor<InternalAuthAccount> accountCaptor = ArgumentCaptor.forClass(InternalAuthAccount.class);
        verify(dataScopeResolver, times(6)).resolve(accountCaptor.capture());
        assertThat(accountCaptor.getAllValues()).extracting(InternalAuthAccount::getAccountId)
                .containsExactly(11L, 12L, 21L, 22L, 31L, 32L);

        ArgumentCaptor<AdminMerchantDataScope> reviewScopeCaptor =
                ArgumentCaptor.forClass(AdminMerchantDataScope.class);
        verify(reviewQueryService, times(2)).searchReviews(
                any(ReviewSearchRequest.class), reviewScopeCaptor.capture());
        assertThat(reviewScopeCaptor.getAllValues()).containsExactly(scope(11L), scope(12L));

        ArgumentCaptor<AdminMerchantDataScope> resultScopeCaptor =
                ArgumentCaptor.forClass(AdminMerchantDataScope.class);
        verify(reportingQueryService, times(2)).searchResultItems(
                any(ResultItemSearchRequest.class), resultScopeCaptor.capture());
        assertThat(resultScopeCaptor.getAllValues()).containsExactly(scope(21L), scope(22L));

        ArgumentCaptor<AdminMerchantDataScope> postingScopeCaptor =
                ArgumentCaptor.forClass(AdminMerchantDataScope.class);
        verify(reportingQueryService, times(2)).searchPostings(
                any(PostingSearchRequest.class), postingScopeCaptor.capture());
        assertThat(postingScopeCaptor.getAllValues()).containsExactly(scope(31L), scope(32L));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void resultItemExportShouldIncludePaymentDimensions() {
        AdminSettlementReviewQueryService reviewQueryService = mock(AdminSettlementReviewQueryService.class);
        AdminSettlementReportingQueryService reportingQueryService =
                mock(AdminSettlementReportingQueryService.class);
        AdminMerchantDataScopeResolver dataScopeResolver = mock(AdminMerchantDataScopeResolver.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExcelI18nMessageResolver messageResolver = mock(ExcelI18nMessageResolver.class);
        ExcelLocaleResolver localeResolver = mock(ExcelLocaleResolver.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ResultItemSummary source = new ResultItemSummary();
        source.setPaymentType("BANK_CARD");
        source.setPaymentMethod("MASTERCARD");
        source.setTransactionType("PAYMENT");
        when(localeResolver.resolveCurrentLocale()).thenReturn(Locale.SIMPLIFIED_CHINESE);
        when(messageResolver.resolve(anyString(), eq(Locale.SIMPLIFIED_CHINESE)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messageResolver.resolve("excel.settlement.enum.paymentType.BANK_CARD", Locale.SIMPLIFIED_CHINESE))
                .thenReturn("银行卡");
        when(messageResolver.resolve("excel.settlement.enum.paymentMethod.MASTERCARD", Locale.SIMPLIFIED_CHINESE))
                .thenReturn("Mastercard");
        when(messageResolver.resolve("excel.settlement.enum.transactionType.PAYMENT", Locale.SIMPLIFIED_CHINESE))
                .thenReturn("支付");
        when(dataScopeResolver.resolve(any(InternalAuthAccount.class)))
                .thenReturn(AdminMerchantDataScope.all());
        when(reportingQueryService.searchResultItems(
                any(ResultItemSearchRequest.class), any(AdminMerchantDataScope.class)))
                .thenReturn(PageResult.of(1, 1, 200, List.of(source)));
        AdminSettlementReportingApplicationService service = new AdminSettlementReportingApplicationService(
                reviewQueryService, reportingQueryService, dataScopeResolver, excelExportService,
                messageResolver, localeResolver);

        InternalAuthContextHolder.set(adminAccount(1L));
        service.exportResultItems(new ResultItemSearchRequest(), response);

        ArgumentCaptor<ExcelPagedExportRequest> requestCaptor =
                ArgumentCaptor.forClass(ExcelPagedExportRequest.class);
        verify(excelExportService).exportPaged(requestCaptor.capture(), eq(response));
        List<SettlementResultItemExportRow> rows =
                (List<SettlementResultItemExportRow>) requestCaptor.getValue().getPageLoader().apply(1);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getPaymentType()).isEqualTo("银行卡");
            assertThat(row.getPaymentMethod()).isEqualTo("Mastercard");
            assertThat(row.getTransactionType()).isEqualTo("支付");
        });
    }

    private void loadTwoPages(ExcelPagedExportRequest<?> exportRequest, long firstAccountId, long secondAccountId) {
        assertThat(exportRequest.getPageSize()).isEqualTo(200);
        InternalAuthContextHolder.set(adminAccount(firstAccountId));
        exportRequest.getPageLoader().apply(1);
        InternalAuthContextHolder.set(adminAccount(secondAccountId));
        exportRequest.getPageLoader().apply(2);
    }

    private static AdminMerchantDataScope scopeFor(InternalAuthAccount account) {
        return scope(account.getAccountId());
    }

    private static AdminMerchantDataScope scope(long accountId) {
        return AdminMerchantDataScope.limited(Set.of("M" + accountId));
    }

    private static InternalAuthAccount adminAccount(long accountId) {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("ADMIN");
        account.setAccountId(accountId);
        account.setLoginAccount("admin-" + accountId);
        return account;
    }
}
