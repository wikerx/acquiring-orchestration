package com.scott.payment.admin.service.impl;

import com.scott.payment.component.core.model.ReserveActionCursor;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.TransactionSettlementSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.single.config.SingleRuleConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
 * 验证逐笔交易结算、保证金结算和入账查询的稳定分页及商户数据范围。
 */
class JdbcAdminSettlementReportingQueryServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void wideHistorySearchShouldUseRegisteredStartAndCurrentBusinessDate() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of("202603", "202604"));
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        var service = new JdbcAdminSettlementReportingQueryService(jdbc, executor, properties);
        ResultItemSearchRequest request = resultRequest();
        request.setBeginBusinessDate(LocalDate.of(2026, 6, 1));
        request.setEndBusinessDate(LocalDate.of(2099, 2, 1));

        service.searchResultItems(request, AdminMerchantDataScope.all());

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(anyString(), parameters.capture(), eq(Long.class));
        assertThat(parameters.getValue().getValue("beginDate")).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(parameters.getValue().getValue("endDate"))
                .isEqualTo(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
    }

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
    void pagedCandidatesRetainAllFinancialComponentsAndMerchantScopeThroughShardingSphere() throws Exception {
        var physical = new JdbcDataSource();
        physical.setURL("jdbc:h2:mem:transaction_settlement_paging;MODE=MySQL;DB_CLOSE_DELAY=-1");
        var setup = new JdbcTemplate(physical);
        setup.execute("""
                CREATE TABLE settlement_batch (settlement_batch_no VARCHAR PRIMARY KEY, merchant_id VARCHAR,
                    business_date DATE, batch_status VARCHAR, posted_time TIMESTAMP);
                CREATE TABLE settlement_candidate (id BIGINT PRIMARY KEY, candidate_no VARCHAR,
                    settlement_batch_no VARCHAR, merchant_id VARCHAR, source_type VARCHAR, source_transaction_id VARCHAR,
                    source_transaction_date_time TIMESTAMP);
                CREATE TABLE transaction_locator (transaction_id VARCHAR PRIMARY KEY, merchant_id VARCHAR,
                    merchant_order_no VARCHAR, transaction_date_time TIMESTAMP, transaction_type VARCHAR);
                CREATE TABLE settlement_result_item (id BIGINT PRIMARY KEY, settlement_batch_no VARCHAR,
                    candidate_id BIGINT, merchant_id VARCHAR, source_detail_type VARCHAR, result_role VARCHAR,
                    payment_type VARCHAR, payment_method VARCHAR, transaction_type VARCHAR, result_item_type VARCHAR,
                    source_amount DECIMAL(20,2), source_currency VARCHAR, source_currency_exponent INT,
                    direction VARCHAR, target_amount DECIMAL(20,2), target_currency VARCHAR,
                    target_currency_exponent INT, create_time TIMESTAMP);
                INSERT INTO settlement_batch VALUES
                    ('SB20260831-00000001', 'M1001', '2026-08-31', 'POSTED', '2026-08-31 09:00:00'),
                    ('SB20260831-00000002', 'M1002', '2026-08-31', 'POSTED', '2026-08-31 09:00:00');
                INSERT INTO settlement_candidate VALUES
                    (1, 'C1', 'SB20260831-00000001', 'M1001', 'CLEARING_REVISION', 'T1', '2026-08-30 09:00:00'),
                    (2, 'C2', 'SB20260831-00000001', 'M1001', 'CLEARING_REVISION', 'T2', '2026-08-30 09:00:00'),
                    (3, 'C3', 'SB20260831-00000002', 'M1002', 'CLEARING_REVISION', 'T3', '2026-08-30 10:00:00'),
                    (4, 'C4', 'SB20260831-00000001', 'M1001', 'CLEARING_REVISION', 'T4', '2026-08-30 10:00:00');
                INSERT INTO transaction_locator VALUES
                    ('T1', 'M1001', 'ORDER1', '2026-08-30 09:00:00', 'PAYMENT'),
                    ('T2', 'M1001', 'ORDER2', '2026-08-30 09:00:00', 'PAYMENT');
                INSERT INTO settlement_result_item VALUES
                    (1, 'SB20260831-00000001', 1, 'M1001', 'TRANSACTION_CLEARING', 'FINANCIAL_COMPONENT',
                     'BANK_CARD', 'VISA', 'PAYMENT', 'PRINCIPAL', 50, 'USD', 2, 'CREDIT', 50, 'USD', 2, NULL),
                    (2, 'SB20260831-00000001', 2, 'M1001', 'TRANSACTION_CLEARING', 'FINANCIAL_COMPONENT',
                     'BANK_CARD', 'VISA', 'PAYMENT', 'PRINCIPAL', 100, 'USD', 2, 'CREDIT', 100, 'USD', 2, NULL),
                    (3, 'SB20260831-00000001', 2, 'M1001', 'TRANSACTION_CLEARING', 'FINANCIAL_COMPONENT',
                     'BANK_CARD', 'VISA', 'PAYMENT', 'FEE', 2.50, 'USD', 2, 'DEBIT', 2.50, 'USD', 2, NULL),
                    (4, 'SB20260831-00000002', 3, 'M1002', 'TRANSACTION_CLEARING', 'FINANCIAL_COMPONENT',
                     'BANK_CARD', 'VISA', 'PAYMENT', 'PRINCIPAL', 30, 'USD', 2, 'CREDIT', 30, 'USD', 2, NULL);
                """);
        var logical = ShardingSphereDataSourceFactory.createDataSource(
                Map.of("master", physical), List.of(new SingleRuleConfiguration(List.of("master.*"), "master")),
                new Properties());
        try (var cleanup = (AutoCloseable) logical) {
            var service = service(new NamedParameterJdbcTemplate(logical));
            var scope = AdminMerchantDataScope.limited(Set.of("M1001"));
            var request = resultRequest();
            request.setDirection("CREDIT");
            request.setPageNo(1);
            request.setPageSize(1);

            var firstPage = service.searchResultItems(request, scope);
            assertThat(firstPage.getTotal()).isEqualTo(2L);
            assertThat(firstPage.getRecords()).singleElement().satisfies(row -> {
                assertThat(row.getSourceTransactionId()).isEqualTo("T2");
                assertThat(row.getComponentCount()).isEqualTo(2L);
                assertThat(row.getNetTargetAmount()).isEqualByComparingTo(new BigDecimal("97.50"));
            });

            request.setPageNo(2);
            assertThat(service.searchResultItems(request, scope).getRecords())
                    .extracting(TransactionSettlementSummary::getSourceTransactionId).containsExactly("T1");
        }
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
    void historicalWindowsWithoutIntersectionShouldSkipAllReportingReads() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of("202603", "202604"));
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        var service = new JdbcAdminSettlementReportingQueryService(jdbc, executor, properties);
        ResultItemSearchRequest result = resultRequest();
        result.setBeginBusinessDate(LocalDate.of(2026, 1, 1));
        result.setEndBusinessDate(LocalDate.of(2026, 2, 1));
        ReserveItemSearchRequest reserve = new ReserveItemSearchRequest();
        reserve.setBeginBusinessDate(LocalDate.of(2099, 1, 1));
        reserve.setEndBusinessDate(LocalDate.of(2099, 2, 1));
        PostingSearchRequest posting = new PostingSearchRequest();
        posting.setBeginPostedTime(LocalDateTime.of(2099, 1, 1, 0, 0));
        posting.setEndPostedTime(LocalDateTime.of(2099, 2, 1, 0, 0));

        assertThat(service.searchResultItems(result, AdminMerchantDataScope.all()).getTotal()).isZero();
        assertThat(service.searchReserveItems(reserve, AdminMerchantDataScope.all()).getTotal()).isZero();
        assertThat(service.searchPostings(posting, AdminMerchantDataScope.all()).getTotal()).isZero();
        verifyNoInteractions(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reserveExportContinuationShouldUseTheLastActionAndSkipTheCount() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        ReserveItemSearchRequest request = new ReserveItemSearchRequest();
        request.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        request.setEndBusinessDate(LocalDate.of(2026, 8, 31));
        request.setPageNo(200);
        request.setPageSize(50);
        ReserveActionCursor cursor = new ReserveActionCursor(LocalDateTime.of(2026, 8, 2, 2, 30), 450L);

        assertThat(service(jdbc).readReserveExportPage(request, AdminMerchantDataScope.limited(Set.of("M1003")), cursor).getRecords()).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("reserve.merchant_id IN (:permittedMerchantIds)",
                "action.action_time < :afterTime", "action.action_time = :afterTime AND action.id < :afterId",
                "ORDER BY action.action_time DESC, action.id DESC");
        assertThat(parameters.getValue().getValue("offset")).isEqualTo(0L);
        assertThat(parameters.getValue().getValue("afterTime")).isEqualTo(cursor.actionTime());
        assertThat(parameters.getValue().getValue("afterId")).isEqualTo(cursor.actionId());
        org.mockito.Mockito.verifyNoMoreInteractions(jdbc);
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
        request.setBeginTransactionTime(LocalDateTime.of(2026, 8, 10, 0, 0));
        request.setEndTransactionTime(LocalDateTime.of(2026, 8, 11, 0, 0));

        service(jdbc).searchReserveItems(request, AdminMerchantDataScope.limited(Set.of("M1003")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "fund_account.account_no AS account_no",
                "reserve.source_transaction_id AS source_transaction_id",
                "locator.transaction_date_time AS source_transaction_date_time",
                "reserve.source_transaction_id = :transactionId",
                "locator.transaction_date_time >= :beginTransactionTime",
                "locator.transaction_date_time < :endTransactionTime",
                "JOIN settlement_candidate candidate",
                "candidate.merchant_id = reserve.merchant_id",
                "LEFT JOIN merchant_fund_account fund_account",
                "fund_account.id = reserve.account_id",
                "fund_account.merchant_id = reserve.merchant_id",
                "fund_account.deleted = 0",
                "LEFT JOIN transaction_locator locator",
                "locator.transaction_id = reserve.source_transaction_id",
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
                "candidate.source_transaction_id = :transactionId",
                "reserve_detail.original_transaction_date_time",
                "JOIN transaction_reserve_clearing_detail reserve_detail",
                "LEFT JOIN merchant_reserve_action source_action");
        assertThat(parameters.getValue().getValue("beginTransactionTime"))
                .isEqualTo(LocalDateTime.of(2026, 8, 10, 0, 0));
        assertThat(parameters.getValue().getValue("endTransactionTime"))
                .isEqualTo(LocalDateTime.of(2026, 8, 11, 0, 0));
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
