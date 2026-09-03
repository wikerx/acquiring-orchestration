package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.CandidateSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.CandidateSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSummary;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminSettlementReviewQueryServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Admin 预审本地查询的标准分页、稳定排序和商户数据范围。
 * @status : create
 */
class JdbcAdminSettlementReviewQueryServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void candidateSearchShouldApplySourceTypeStatusAndCustomMerchantScope() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new CandidateSummary()));
        JdbcAdminSettlementReviewQueryService service = service(jdbc);
        CandidateSearchRequest request = candidateRequest();
        request.setCandidateStatus(" replay_hold ");
        request.setPageNo(1);
        request.setPageSize(10);

        var page = service.searchCandidates(request, Set.of("CLEARING_REVISION"),
                AdminMerchantDataScope.limited(Set.of("M1001")));

        assertThat(page.getTotal()).isEqualTo(1L);
        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(countSql.capture(), parameters.capture(), eq(Long.class));
        assertThat(countSql.getValue()).contains(
                "FROM settlement_candidate", "source_type IN (:sourceTypes)",
                "candidate_status = :candidateStatus", "merchant_id IN (:permittedMerchantIds)");
        ArgumentCaptor<String> pageSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(pageSql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(pageSql.getValue()).contains(
                "LEFT JOIN base_merchant_info merchant",
                "LEFT JOIN transaction_operation operation",
                "JOIN transaction_finance_state finance",
                "JOIN transaction_clearing_detail clearing_detail",
                "LEFT JOIN transaction_reserve_clearing_detail reserve_detail",
                "ORDER BY candidate.source_transaction_date_time DESC, candidate.id DESC");
        assertThat(parameters.getValue().getValue("sourceTypes")).isEqualTo(Set.of("CLEARING_REVISION"));
        assertThat(parameters.getValue().getValue("candidateStatus")).isEqualTo("REPLAY_HOLD");
        assertThat(parameters.getValue().getValue("permittedMerchantIds")).isEqualTo(Set.of("M1001"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void candidateSearchShouldUseAuthoritativeInnerJoinsAndJoinTheExactOperation() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new CandidateSummary()));

        service(jdbc).searchCandidates(candidateRequest(), Set.of("CLEARING_REVISION"),
                AdminMerchantDataScope.all());

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(countSql.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        assertThat(countSql.getValue()).contains(
                "JOIN transaction_finance_state finance",
                "JOIN transaction_clearing_detail clearing_detail",
                "LEFT JOIN transaction_reserve_clearing_detail reserve_detail")
                .doesNotContain("finance.id IS NOT NULL", "clearing_detail.id IS NOT NULL");

        ArgumentCaptor<String> pageSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(pageSql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(pageSql.getValue()).contains(
                "operation.operation_id = COALESCE(finance.operation_id, reserve_detail.operation_id)",
                "operation.transaction_date_time = CASE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reserveCandidateSearchShouldExposeOriginalTransactionAndPrioritizeEarliestRelease() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new CandidateSummary()));

        service(jdbc).searchCandidates(candidateRequest(), Set.of("RESERVE_RELEASE", "ADJUSTMENT"),
                AdminMerchantDataScope.all());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "reserve_detail.original_transaction_id",
                "reserve_detail.original_transaction_date_time",
                "AS source_transaction_id",
                "AS source_transaction_date_time",
                "JOIN transaction_reserve_clearing_detail reserve_detail",
                "ORDER BY reserve_detail.expected_reserve_release_date ASC, candidate.id ASC");
    }

    @Test
    @SuppressWarnings("unchecked")
    void transactionCandidateSearchShouldApplyOperationalFilters() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new CandidateSummary()));
        CandidateSearchRequest request = candidateRequest();
        request.setSourceTransactionId(" T1001 ");
        request.setMerchantOrderNo(" ORDER1001 ");
        request.setBeginTransactionTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        request.setEndTransactionTime(LocalDateTime.of(2026, 8, 31, 23, 59, 59));
        request.setPaymentType(" bank_card ");
        request.setPaymentMethod(" mastercard ");
        request.setTransactionType(" payment ");
        request.setLabelCurrency(" usd ");
        request.setTargetCurrency(" eur ");
        request.setSourceRevision(2);

        service(jdbc).searchCandidates(request, Set.of("CLEARING_REVISION"), AdminMerchantDataScope.all());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(sql.capture(), parameters.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains(
                "candidate.source_transaction_id = :sourceTransactionId",
                "operation.merchant_order_no = :merchantOrderNo",
                "candidate.source_transaction_date_time BETWEEN :beginTransactionTime AND :endTransactionTime",
                "clearing_detail.payment_type = :paymentType",
                "clearing_detail.payment_method = :paymentMethod",
                "clearing_detail.transaction_type = :transactionType",
                "clearing_detail.label_currency = :labelCurrency",
                "candidate.target_currency = :targetCurrency",
                "candidate.source_revision = :sourceRevision");
        assertThat(parameters.getValue().getValue("sourceTransactionId")).isEqualTo("T1001");
        assertThat(parameters.getValue().getValue("paymentType")).isEqualTo("BANK_CARD");
        assertThat(parameters.getValue().getValue("paymentMethod")).isEqualTo("MASTERCARD");
        assertThat(parameters.getValue().getValue("transactionType")).isEqualTo("PAYMENT");
        assertThat(parameters.getValue().getValue("labelCurrency")).isEqualTo("USD");
        assertThat(parameters.getValue().getValue("targetCurrency")).isEqualTo("EUR");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reserveCandidateSearchShouldApplyReserveLiabilityFilters() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new CandidateSummary()));
        CandidateSearchRequest request = candidateRequest();
        request.setReserveNo(" RSV1001 ");
        request.setReserveStatus(" frozen ");
        request.setDue(true);
        request.setFrozen(true);
        request.setBeginExpectedReleaseDate(LocalDate.of(2026, 8, 1));
        request.setEndExpectedReleaseDate(LocalDate.of(2026, 8, 31));
        request.setMinRemainingAmount(new BigDecimal("10.00"));
        request.setMaxRemainingAmount(new BigDecimal("100.00"));

        service(jdbc).searchCandidates(request, Set.of("RESERVE_RELEASE", "ADJUSTMENT"),
                AdminMerchantDataScope.all());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(sql.capture(), parameters.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains(
                "LEFT JOIN merchant_reserve_item reserve_item",
                "reserve_item.reserve_no = :reserveNo",
                "reserve_item.reserve_status = :reserveStatus",
                "reserve_detail.expected_reserve_release_date BETWEEN :beginExpectedReleaseDate AND :endExpectedReleaseDate",
                "reserve_detail.expected_reserve_release_date <= CURRENT_DATE()",
                "reserve_item.reserve_status = 'FROZEN'",
                ">= :minRemainingAmount",
                "<= :maxRemainingAmount");
        assertThat(parameters.getValue().getValue("reserveNo")).isEqualTo("RSV1001");
        assertThat(parameters.getValue().getValue("reserveStatus")).isEqualTo("FROZEN");
        assertThat(parameters.getValue().getValue("minRemainingAmount")).isEqualTo(new BigDecimal("10.00"));
        assertThat(parameters.getValue().getValue("maxRemainingAmount")).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reviewSearchShouldUseStableBusinessDatePaginationOnLocalReadExecutor() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(2L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        JdbcAdminSettlementReviewQueryService service = new JdbcAdminSettlementReviewQueryService(
                jdbc, readExecutor, new TransactionShardingProperties());
        ReviewSearchRequest request = new ReviewSearchRequest();
        request.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        request.setEndBusinessDate(LocalDate.of(2026, 8, 31));

        service.searchReviews(request, AdminMerchantDataScope.all());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "FROM settlement_review_order", "ORDER BY business_date DESC, id DESC",
                "LIMIT :offset, :limit");
        verify(readExecutor).read(any());
        verify(readExecutor, never()).readPrimary(any());
    }

    @Test
    void emptyMerchantScopeShouldReturnEmptyCandidatePageWithoutDatabaseAccess() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminSettlementReviewQueryService service = service(jdbc);

        var page = service.searchCandidates(candidateRequest(), Set.of("RESERVE_RELEASE", "ADJUSTMENT"),
                AdminMerchantDataScope.limited(Set.of()));

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void candidateDetailShouldApplySourceTypeAndMerchantScope() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new CandidateSummary()));
        JdbcAdminSettlementReviewQueryService service = service(jdbc);

        service.candidateDetail("SC-1001", Set.of("CLEARING_REVISION"),
                AdminMerchantDataScope.limited(Set.of("M1001")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("candidate_no = :candidateNo", "source_type IN (:sourceTypes)",
                "shadow_mode = 0", "merchant_id IN (:permittedMerchantIds)");
        assertThat(parameters.getValue().getValue("permittedMerchantIds")).isEqualTo(Set.of("M1001"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reviewDetailSummaryShouldExposeSourceAndFrozenTargetCurrencyExponents() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ReviewSummary review = new ReviewSummary();
        review.setReviewOrderNo("SO20260831-00000001");
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<String>getArgument(0)
                        .contains("FROM settlement_review_order") ? List.of(review) : List.of());

        service(jdbc).reviewDetail("SO20260831-00000001", AdminMerchantDataScope.all());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(4)).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getAllValues()).anySatisfy(summarySql -> assertThat(summarySql).contains(
                "FROM settlement_review_summary summary",
                "AS source_currency_exponent",
                "review.target_currency_exponent"));
    }

    private JdbcAdminSettlementReviewQueryService service(NamedParameterJdbcTemplate jdbc) {
        return new JdbcAdminSettlementReviewQueryService(
                jdbc, executingReadExecutor(), new TransactionShardingProperties());
    }

    private CandidateSearchRequest candidateRequest() {
        CandidateSearchRequest request = new CandidateSearchRequest();
        request.setBeginEligibleDate(LocalDate.of(2026, 8, 1));
        request.setEndEligibleDate(LocalDate.of(2026, 8, 31));
        return request;
    }

    private TransactionLogicalReadExecutor executingReadExecutor() {
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        return executor;
    }
}
