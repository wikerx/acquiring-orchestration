package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.TransactionSettlementSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminSettlementReportingQueryServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证逐笔交易结算、保证金结算和入账查询的稳定分页及商户数据范围。
 * @status : create
 */
class JdbcAdminSettlementReportingQueryServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void transactionSettlementSearchShouldAggregateByRealTransactionAndApplyMerchantScope() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new TransactionSettlementSummary()));
        JdbcAdminSettlementReportingQueryService service = service(jdbc);
        ResultItemSearchRequest request = resultRequest();
        request.setTargetCurrency(" usd ");

        service.searchResultItems(request, AdminMerchantDataScope.limited(Set.of("M1001")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "FROM settlement_candidate candidate",
                "JOIN settlement_batch batch",
                "JOIN settlement_result_item item",
                "candidate.source_type = 'CLEARING_REVISION'",
                "item.source_detail_type = 'TRANSACTION_CLEARING'",
                "item.result_role = 'FINANCIAL_COMPONENT'",
                "filter_item.target_currency = :currency",
                "candidate.merchant_id IN (:permittedMerchantIds)",
                "GROUP BY batch.settlement_batch_no",
                "ORDER BY source_transaction_date_time DESC, candidate.id DESC",
                "LIMIT :offset, :limit");
        assertThat(sql.getValue()).doesNotContain("JOIN settlement_batch_rate rate");
        assertThat(parameters.getValue().getValue("currency")).isEqualTo("USD");
        assertThat(parameters.getValue().getValue("permittedMerchantIds")).isEqualTo(Set.of("M1001"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void postingSearchShouldUsePostedTimeStableOrderAndMerchantScope() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        JdbcAdminSettlementReportingQueryService service = service(jdbc);
        PostingSearchRequest request = new PostingSearchRequest();
        request.setBeginPostedTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        request.setEndPostedTime(LocalDateTime.of(2026, 8, 31, 23, 59));
        request.setOperationMode(" manual ");

        service.searchPostings(request, AdminMerchantDataScope.limited(Set.of("M1002")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("FROM merchant_fund_ledger ledger",
                "LEFT JOIN base_iso_currency currency", "AS currency_exponent",
                "ledger.settlement_batch_no IS NOT NULL", "ledger.merchant_id IN (:permittedMerchantIds)",
                "ORDER BY ledger.posted_time DESC, ledger.id DESC");
        assertThat(parameters.getValue().getValue("operationMode")).isEqualTo("MANUAL");
    }

    @Test
    void emptyMerchantScopeShouldReturnEmptyWithoutDatabaseAccess() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        var page = service(jdbc).searchResultItems(resultRequest(),
                AdminMerchantDataScope.limited(Set.of()));

        assertThat(page.getRecords()).isEmpty();
        assertThat(page.getTotal()).isZero();
        verifyNoInteractions(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reserveSearchShouldExposeAndFilterByOriginalTransactionWithinMerchantScope() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        ReserveItemSearchRequest request = new ReserveItemSearchRequest();
        request.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        request.setEndBusinessDate(LocalDate.of(2026, 8, 31));
        request.setSourceTransactionId("T-RETURN-1003");

        service(jdbc).searchReserveItems(request, AdminMerchantDataScope.limited(Set.of("M1003")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "fund_account.account_no AS account_no",
                "reserve_detail.original_transaction_id AS source_transaction_id",
                "COALESCE(locator.transaction_date_time, reserve_detail.original_transaction_date_time)",
                "AS source_transaction_date_time",
                "reserve_detail.original_transaction_id = :transactionId",
                "JOIN settlement_candidate candidate",
                "candidate.merchant_id = reserve.merchant_id",
                "LEFT JOIN merchant_reserve_action source_action",
                "source_action.id = action.reversal_of_action_id",
                "JOIN transaction_reserve_clearing_detail reserve_detail",
                "reserve_detail.transaction_id = candidate.source_transaction_id",
                "reserve_detail.transaction_date_time = candidate.source_transaction_date_time",
                "reserve_detail.clearing_revision = candidate.source_revision",
                "reserve_detail.reserve_clearing_detail_no = COALESCE(",
                "source_action.source_reserve_detail_no",
                "action.source_reserve_detail_no",
                "LEFT JOIN merchant_fund_account fund_account",
                "fund_account.id = reserve.account_id",
                "fund_account.merchant_id = reserve.merchant_id",
                "fund_account.deleted = 0",
                "LEFT JOIN base_iso_currency currency",
                "AS currency_exponent",
                "reserve.reserve_status = 'HELD'",
                "reserve.release_batch_no IS NOT NULL",
                "THEN 'RELEASED'",
                "END",
                "AS reserve_status",
                "reserve.merchant_id IN (:permittedMerchantIds)");
        assertThat(sql.getValue()).doesNotContain(
                "candidate.source_transaction_id,\n",
                "candidate.source_transaction_date_time,",
                "candidate.source_transaction_id = :transactionId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void resultHistoryShouldUseExactTransactionIdentityWithoutBusinessDateWindow() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 8, 15, 10, 30, 20);

        service(jdbc).searchResultItemsByTransaction(
                "T-1004", transactionDateTime, 1, 20,
                AdminMerchantDataScope.limited(Set.of("M1004")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "FROM settlement_candidate candidate",
                "JOIN settlement_result_item ri",
                "ri.settlement_batch_no = candidate.settlement_batch_no",
                "ri.candidate_id = candidate.id",
                "candidate.source_type = 'CLEARING_REVISION'",
                "candidate.source_transaction_id = :transactionId",
                "candidate.source_transaction_date_time = :transactionDateTime",
                "candidate.merchant_id IN (:permittedMerchantIds)",
                "LIMIT :offset, :limit");
        assertThat(sql.getValue()).doesNotContain("business_date BETWEEN");
        assertThat(parameters.getValue().getValue("transactionId")).isEqualTo("T-1004");
        assertThat(parameters.getValue().getValue("transactionDateTime")).isEqualTo(transactionDateTime);
        assertThat(parameters.getValue().getValue("permittedMerchantIds")).isEqualTo(Set.of("M1004"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconciliationDetailShouldUseExactTransactionIdentityAndMerchantScope() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 9, 9, 17, 34, 8, 160_000_000);

        service(jdbc).findReconciliationRecordsByTransaction(
                "T-1004", transactionDateTime,
                AdminMerchantDataScope.limited(Set.of("M1004")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "FROM transaction_operation operation",
                "operation.transaction_id = :transactionId",
                "operation.transaction_date_time = :transactionDateTime",
                "operation.merchant_id IN (:permittedMerchantIds)",
                "operation.deleted = 0",
                "operation.reconciliation_status",
                "operation.settlement_status",
                "operation.accounting_status",
                "LIMIT 1");
        assertThat(parameters.getValue().getValue("transactionId")).isEqualTo("T-1004");
        assertThat(parameters.getValue().getValue("transactionDateTime")).isEqualTo(transactionDateTime);
        assertThat(parameters.getValue().getValue("permittedMerchantIds")).isEqualTo(Set.of("M1004"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reserveHistoryShouldRouteByOriginalTransactionStateAndReturnAllReserveActions() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 8, 16, 11, 40, 30);

        service(jdbc).searchReserveItemsByTransaction(
                "T-1005", transactionDateTime, 1, 20,
                AdminMerchantDataScope.limited(Set.of("M1005")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "reserve_state.original_transaction_id AS source_transaction_id",
                "COALESCE(locator.transaction_date_time, reserve_state.transaction_date_time)",
                "AS source_transaction_date_time",
                "FROM transaction_reserve_clearing_state reserve_state",
                "JOIN merchant_reserve_item reserve",
                "reserve.source_business_no = reserve_state.original_hold_detail_no",
                "JOIN merchant_reserve_action action",
                "action.reserve_item_id = reserve.id",
                "reserve_state.original_transaction_id = :transactionId",
                "reserve_state.transaction_date_time = :transactionDateTime",
                "reserve_state.merchant_id IN (:permittedMerchantIds)",
                "LIMIT :offset, :limit");
        assertThat(sql.getValue()).doesNotContain(
                "business_date BETWEEN",
                "JOIN settlement_candidate candidate",
                "JOIN transaction_reserve_clearing_detail reserve_detail");
        assertThat(parameters.getValue().getValue("transactionId")).isEqualTo("T-1005");
        assertThat(parameters.getValue().getValue("transactionDateTime")).isEqualTo(transactionDateTime);
        assertThat(parameters.getValue().getValue("permittedMerchantIds")).isEqualTo(Set.of("M1005"));
    }

    private ResultItemSearchRequest resultRequest() {
        ResultItemSearchRequest request = new ResultItemSearchRequest();
        request.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        request.setEndBusinessDate(LocalDate.of(2026, 8, 31));
        return request;
    }

    private JdbcAdminSettlementReportingQueryService service(NamedParameterJdbcTemplate jdbc) {
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        return new JdbcAdminSettlementReportingQueryService(jdbc, executor, new TransactionShardingProperties());
    }
}
