package com.scott.payment.admin.application.export;

import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReservePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReserveQuery;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementTransactionPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementTransactionQuery;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReviewPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReviewQuery;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementPostingPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementPostingQuery;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.TransactionSettlementSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminSettlementReportingQueryService;
import com.scott.payment.admin.service.AdminSettlementReviewQueryService;
import com.scott.payment.component.core.model.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结算异步导出来源测试，验证冻结范围、内部分页和财务安全投影。
 */
class AdminSettlementExportSourceApplicationServiceTest {

    /**
     * 结算交易来源必须覆盖内部页码并保持权威金额，不得暴露候选主键。
     */
    @Test
    void shouldPageSettlementTransactionsWithinFrozenScope() {
        AdminSettlementReportingQueryService queryService = mock(AdminSettlementReportingQueryService.class);
        TransactionSettlementSummary source = new TransactionSettlementSummary();
        source.setSettlementBatchNo("STB1001");
        source.setMerchantId("M1001");
        source.setSourceTransactionId("TXN1001");
        source.setSourceAmount(new BigDecimal("100.00"));
        source.setSourceCurrency("EUR");
        source.setNetTargetAmount(new BigDecimal("110.00"));
        source.setTargetCurrency("USD");
        source.setCandidateId(99L);
        when(queryService.searchResultItems(any(), any()))
                .thenReturn(PageResult.of(1L, 3L, 50L, List.of(source)));
        AdminSettlementExportSourceApplicationService service =
                new AdminSettlementExportSourceApplicationService(queryService,
                        mock(AdminSettlementReviewQueryService.class));
        SettlementTransactionQuery query = new SettlementTransactionQuery();
        query.setSettlementBatchNo("STB1001");
        query.setBeginBusinessDate(LocalDate.of(2026, 11, 1));
        query.setEndBusinessDate(LocalDate.of(2026, 11, 9));
        SettlementTransactionPageRequest request = new SettlementTransactionPageRequest();
        request.setCreatorAccountId(88L);
        request.setMerchantIds(Set.of("M1001"));
        request.setQuery(query);
        request.setPageNo(3);
        request.setPageSize(50);

        var response = service.pageTransactions(request);

        ArgumentCaptor<ResultItemSearchRequest> queryCaptor =
                ArgumentCaptor.forClass(ResultItemSearchRequest.class);
        ArgumentCaptor<AdminMerchantDataScope> scopeCaptor =
                ArgumentCaptor.forClass(AdminMerchantDataScope.class);
        verify(queryService).searchResultItems(queryCaptor.capture(), scopeCaptor.capture());
        assertThat(queryCaptor.getValue().getPageNo()).isEqualTo(3);
        assertThat(queryCaptor.getValue().getPageSize()).isEqualTo(50);
        assertThat(scopeCaptor.getValue().merchantIds()).containsExactly("M1001");
        assertThat(response.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getSourceAmount()).isEqualByComparingTo("100.00");
            assertThat(row.getNetTargetAmount()).isEqualByComparingTo("110.00");
            assertThat(row.getSourceCurrency()).isEqualTo("EUR");
            assertThat(row.getTargetCurrency()).isEqualTo("USD");
        });
        assertThat(response.getRecords().get(0).getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("candidateId", "candidateNo");
    }

    /**
     * 保证金来源必须保持标签币种金额并使用冻结商户范围。
     */
    @Test
    void shouldPageSettlementReservesWithoutCurrencyConversion() {
        AdminSettlementReportingQueryService queryService = mock(AdminSettlementReportingQueryService.class);
        ReserveItemSummary source = new ReserveItemSummary();
        source.setSettlementBatchNo("STB2001");
        source.setMerchantId("M2001");
        source.setReserveNo("RSV2001");
        source.setCurrency("EUR");
        source.setAmount(new BigDecimal("8.00"));
        source.setRemainingAmount(new BigDecimal("2.00"));
        source.setAccountId(7001L);
        source.setAccountNo("ACCOUNT-SENSITIVE");
        when(queryService.searchReserveItems(any(), any()))
                .thenReturn(PageResult.of(1L, 2L, 100L, List.of(source)));
        AdminSettlementExportSourceApplicationService service =
                new AdminSettlementExportSourceApplicationService(queryService,
                        mock(AdminSettlementReviewQueryService.class));
        SettlementReserveQuery query = new SettlementReserveQuery();
        query.setReserveNo("RSV2001");
        query.setBeginBusinessDate(LocalDate.of(2026, 11, 1));
        query.setEndBusinessDate(LocalDate.of(2026, 11, 9));
        SettlementReservePageRequest request = new SettlementReservePageRequest();
        request.setCreatorAccountId(88L);
        request.setMerchantIds(Set.of("M2001"));
        request.setQuery(query);
        request.setPageNo(2);
        request.setPageSize(100);

        var response = service.pageReserves(request);

        ArgumentCaptor<ReserveItemSearchRequest> queryCaptor =
                ArgumentCaptor.forClass(ReserveItemSearchRequest.class);
        ArgumentCaptor<AdminMerchantDataScope> scopeCaptor =
                ArgumentCaptor.forClass(AdminMerchantDataScope.class);
        verify(queryService).searchReserveItems(queryCaptor.capture(), scopeCaptor.capture());
        assertThat(queryCaptor.getValue().getPageNo()).isEqualTo(2);
        assertThat(scopeCaptor.getValue().merchantIds()).containsExactly("M2001");
        assertThat(response.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getCurrency()).isEqualTo("EUR");
            assertThat(row.getAmount()).isEqualByComparingTo("8.00");
            assertThat(row.getRemainingAmount()).isEqualByComparingTo("2.00");
        });
        assertThat(response.getRecords().get(0).getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("accountId", "accountNo", "sourceBusinessNo");
    }

    /**
     * 结算审核单与入账流水必须复用冻结商户范围并保持权威资金事实。
     */
    @Test
    void shouldPageSettlementReviewAndPostingWithinFrozenScope() {
        AdminSettlementReportingQueryService reportingService =
                mock(AdminSettlementReportingQueryService.class);
        AdminSettlementReviewQueryService reviewService =
                mock(AdminSettlementReviewQueryService.class);
        ReviewSummary review = new ReviewSummary();
        review.setReviewOrderNo("RVW-5001");
        review.setMerchantId("M5001");
        review.setNetAmount(new BigDecimal("120.00"));
        review.setTargetCurrency("USD");
        PostingSummary posting = new PostingSummary();
        posting.setLedgerNo("LDG-5001");
        posting.setMerchantId("M5001");
        posting.setAmount(new BigDecimal("120.00"));
        posting.setBalanceBefore(new BigDecimal("20.00"));
        posting.setBalanceAfter(new BigDecimal("140.00"));
        when(reviewService.searchReviews(any(), any()))
                .thenReturn(PageResult.of(1L, 1L, 100L, List.of(review)));
        when(reportingService.searchPostings(any(), any()))
                .thenReturn(PageResult.of(1L, 1L, 100L, List.of(posting)));
        AdminSettlementExportSourceApplicationService service =
                new AdminSettlementExportSourceApplicationService(reportingService, reviewService);

        SettlementReviewQuery reviewQuery = new SettlementReviewQuery();
        reviewQuery.setReviewStatus("PENDING_APPROVAL");
        SettlementReviewPageRequest reviewRequest = new SettlementReviewPageRequest();
        reviewRequest.setCreatorAccountId(88L);
        reviewRequest.setMerchantIds(Set.of("M5001"));
        reviewRequest.setQuery(reviewQuery);
        reviewRequest.setPageNo(1);
        reviewRequest.setPageSize(100);
        SettlementPostingQuery postingQuery = new SettlementPostingQuery();
        postingQuery.setCurrency("USD");
        SettlementPostingPageRequest postingRequest = new SettlementPostingPageRequest();
        postingRequest.setCreatorAccountId(88L);
        postingRequest.setMerchantIds(Set.of("M5001"));
        postingRequest.setQuery(postingQuery);
        postingRequest.setPageNo(1);
        postingRequest.setPageSize(100);

        var reviewResponse = service.pageReviews(reviewRequest);
        var postingResponse = service.pagePostings(postingRequest);

        ArgumentCaptor<ReviewSearchRequest> reviewCaptor =
                ArgumentCaptor.forClass(ReviewSearchRequest.class);
        ArgumentCaptor<PostingSearchRequest> postingCaptor =
                ArgumentCaptor.forClass(PostingSearchRequest.class);
        ArgumentCaptor<AdminMerchantDataScope> reviewScope =
                ArgumentCaptor.forClass(AdminMerchantDataScope.class);
        ArgumentCaptor<AdminMerchantDataScope> postingScope =
                ArgumentCaptor.forClass(AdminMerchantDataScope.class);
        verify(reviewService).searchReviews(reviewCaptor.capture(), reviewScope.capture());
        verify(reportingService).searchPostings(postingCaptor.capture(), postingScope.capture());
        assertThat(reviewCaptor.getValue().getReviewStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(postingCaptor.getValue().getCurrency()).isEqualTo("USD");
        assertThat(reviewScope.getValue().merchantIds()).containsExactly("M5001");
        assertThat(postingScope.getValue().merchantIds()).containsExactly("M5001");
        assertThat(reviewResponse.getRecords()).singleElement()
                .satisfies(row -> assertThat(row.getNetAmount()).isEqualByComparingTo("120.00"));
        assertThat(postingResponse.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getAmount()).isEqualByComparingTo("120.00");
            assertThat(row.getBalanceBefore()).isEqualByComparingTo("20.00");
            assertThat(row.getBalanceAfter()).isEqualByComparingTo("140.00");
        });
    }
}
