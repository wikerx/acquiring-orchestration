package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchSummary;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.SummaryLine;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcMerchantSettlementQueryServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Merchant 结算 JDBC 查询的商户隔离和真实明细口径测试。
 * @status : create
 */
class JdbcMerchantSettlementQueryServiceTests {

    @Test
    void everyPageQueryShouldBindMerchantAndKeepTransactionAndReserveSourcesSeparate() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        JdbcMerchantSettlementQueryService service = service(jdbcTemplate);
        LocalDate begin = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);
        BatchQuery batchQuery = new BatchQuery();
        batchQuery.setBeginBusinessDate(begin);
        batchQuery.setEndBusinessDate(end);
        TransactionItemQuery transactionQuery = new TransactionItemQuery();
        transactionQuery.setBeginBusinessDate(begin);
        transactionQuery.setEndBusinessDate(end);
        ReserveItemQuery reserveQuery = new ReserveItemQuery();
        reserveQuery.setBeginBusinessDate(begin);
        reserveQuery.setEndBusinessDate(end);

        service.searchBatches("merchant-a", batchQuery);
        service.searchTransactionItems("merchant-a", transactionQuery);
        service.searchReserveItems("merchant-a", reserveQuery);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, atLeast(3)).queryForObject(
                sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class));
        assertThat(paramsCaptor.getAllValues()).allSatisfy(parameters ->
                assertThat(parameters.getValue("merchantId")).isEqualTo("merchant-a"));
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql)
                .contains("batch.merchant_id = :merchantId", "batch.batch_status IN ('POSTED', 'REVERSED')"));
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql)
                .contains("candidate.merchant_id = :merchantId",
                        "item.merchant_id = candidate.merchant_id",
                        "candidate.source_transaction_id IS NOT NULL",
                        "item.source_detail_type = 'TRANSACTION_CLEARING'",
                        "item.result_role = 'FINANCIAL_COMPONENT'",
                        "batch.batch_status IN ('POSTED', 'REVERSED')"));
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql)
                .contains("reserve.merchant_id = :merchantId", "FROM merchant_reserve_action action",
                        "batch.batch_status IN ('POSTED', 'REVERSED')"));
    }

    @Test
    void transactionHistoryQueriesShouldBindMerchantAndPreciseTransactionIdentity() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        JdbcMerchantSettlementQueryService service = service(jdbcTemplate);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 9, 9, 17, 34, 8, 160_000_000);

        service.searchTransactionItemsByTransaction(
                "merchant-a", "202609091734081602975", transactionDateTime, 1, 20);
        service.searchReserveItemsByTransaction(
                "merchant-a", "202609091734081602975", transactionDateTime, 1, 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, atLeast(2)).queryForObject(
                sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class));
        assertThat(paramsCaptor.getAllValues()).allSatisfy(parameters -> {
            assertThat(parameters.getValue("merchantId")).isEqualTo("merchant-a");
            assertThat(parameters.getValue("transactionId")).isEqualTo("202609091734081602975");
            assertThat(parameters.getValue("transactionDateTime")).isEqualTo(transactionDateTime);
        });
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql)
                .contains("candidate.source_transaction_id = :transactionId",
                        "candidate.source_transaction_date_time = :transactionDateTime",
                        "candidate.merchant_id = :merchantId",
                        "item.result_role = 'FINANCIAL_COMPONENT'",
                        "batch.batch_status IN ('POSTED', 'REVERSED')")
                .doesNotContain("batch.business_date BETWEEN"));
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql)
                .contains("reserve_state.original_transaction_id = :transactionId",
                        "reserve_state.transaction_date_time = :transactionDateTime",
                        "reserve_state.merchant_id = :merchantId",
                        "reserve.merchant_id = :merchantId",
                        "batch.batch_status IN ('POSTED', 'REVERSED')")
                .doesNotContain("batch.business_date BETWEEN"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconciliationDetailShouldBindAuthenticatedMerchantAndExactTransactionIdentity() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 9, 9, 17, 34, 8, 160_000_000);

        service(jdbcTemplate).findReconciliationRecordsByTransaction(
                "merchant-a", "202609091734081602975", transactionDateTime);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
                "FROM transaction_operation operation",
                "operation.transaction_id = :transactionId",
                "operation.transaction_date_time = :transactionDateTime",
                "operation.merchant_id = :merchantId",
                "operation.deleted = 0",
                "operation.reconciliation_status",
                "operation.settlement_status",
                "operation.accounting_status",
                "LIMIT 1");
        assertThat(paramsCaptor.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        assertThat(paramsCaptor.getValue().getValue("transactionId"))
                .isEqualTo("202609091734081602975");
        assertThat(paramsCaptor.getValue().getValue("transactionDateTime")).isEqualTo(transactionDateTime);
    }

    @Test
    @SuppressWarnings("unchecked")
    void clearingDetailShouldBindAuthenticatedMerchantAndExactTransactionIdentity() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 9, 9, 17, 34, 8, 160_000_000);

        service(jdbcTemplate).findClearingDetailByTransaction(
                "merchant-a", "202609091734081602975", transactionDateTime);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
                "FROM transaction_finance_state finance",
                "finance.transaction_id = :transactionId",
                "finance.transaction_date_time = :transactionDateTime",
                "finance.merchant_id = :merchantId",
                "operation.merchant_id = finance.merchant_id",
                "finance.deleted = 0",
                "finance.gross_label_amount",
                "finance.platform_fee_amount",
                "finance.reserve_amount",
                "LIMIT 1");
        assertThat(paramsCaptor.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        assertThat(paramsCaptor.getValue().getValue("transactionId"))
                .isEqualTo("202609091734081602975");
        assertThat(paramsCaptor.getValue().getValue("transactionDateTime")).isEqualTo(transactionDateTime);
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailShouldReturnNotFoundWhenBatchDoesNotBelongToAuthenticatedMerchant() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcMerchantSettlementQueryService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.getBatch("merchant-a", "SB20260831-00000001"))
                .isInstanceOf(ServiceException.class);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
                "batch.merchant_id = :merchantId",
                "batch.settlement_batch_no = :batchNo",
                "batch.batch_status IN ('POSTED', 'REVERSED')");
        assertThat(paramsCaptor.getValue().getValue("merchantId")).isEqualTo("merchant-a");
    }

    @Test
    @SuppressWarnings("unchecked")
    void resultSummaryShouldUseStablePaginationAndAuthenticatedMerchantScope() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(25L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new SummaryLine()));
        JdbcMerchantSettlementQueryService service = service(jdbcTemplate);

        var page = service.searchResultSummaries(
                "merchant-a", "SB20260831-00000001", 2, 20);

        assertThat(page.getTotal()).isEqualTo(25L);
        assertThat(page.getPageNo()).isEqualTo(2L);
        assertThat(page.getPageSize()).isEqualTo(20L);
        assertThat(page.getRecords()).hasSize(1);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
                "FROM settlement_result_summary summary",
                "summary.merchant_id = :merchantId",
                "batch.merchant_id = :merchantId",
                "batch.batch_status IN ('POSTED', 'REVERSED')",
                "AS source_currency_exponent",
                "batch.target_currency_exponent",
                "ORDER BY summary.merchant_id, summary.payment_type, summary.payment_method",
                "summary.target_currency, summary.id",
                "LIMIT :offset, :limit");
        assertThat(paramsCaptor.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        assertThat(paramsCaptor.getValue().getValue("offset")).isEqualTo(20L);
        assertThat(paramsCaptor.getValue().getValue("limit")).isEqualTo(20);
    }

    @Test
    void accessibleBatchWithoutSummariesShouldReturnEmptyPage() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);

        var page = service(jdbcTemplate).searchResultSummaries(
                "merchant-a", "SB20260831-00000001", 1, 20);

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
        verify(jdbcTemplate).queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Integer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void voucherShouldKeepCompleteImmutableSummarySnapshot() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        BatchSummary batch = new BatchSummary();
        batch.setSettlementBatchNo("SB20260831-00000001");
        SummaryLine summary = new SummaryLine();
        summary.setPaymentType("BANK_CARD");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("FROM settlement_batch batch")) return List.of(batch);
                    if (sql.contains("FROM settlement_result_summary summary")) return List.of(summary);
                    return List.of();
                });

        var detail = service(jdbcTemplate).getVoucher(
                "merchant-a", "SB20260831-00000001");

        assertThat(detail.getSummaries()).singleElement()
                .extracting(SummaryLine::getPaymentType)
                .isEqualTo("BANK_CARD");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeast(3)).query(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql).contains(
                "FROM settlement_result_summary summary",
                "summary.settlement_batch_no = :batchNo",
                "ORDER BY summary.payment_type, summary.payment_method"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reserveSearchShouldExposeAndFilterByOriginalTransactionForPreciseTransactionLink() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        ReserveItemQuery query = new ReserveItemQuery();
        query.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        query.setEndBusinessDate(LocalDate.of(2026, 8, 31));
        query.setSourceTransactionId("T-ORIGINAL-1001");

        service(jdbcTemplate).searchReserveItems("merchant-a", query);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
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
                "reserve_detail.merchant_id = reserve.merchant_id",
                "reserve_detail.record_status = 'ACTIVE'",
                "LEFT JOIN base_iso_currency currency",
                "reserve.reserve_status = 'HELD'",
                "reserve.release_batch_no IS NOT NULL",
                "THEN 'RELEASED'",
                "AS reserve_status",
                "AS currency_exponent");
        assertThat(sqlCaptor.getValue()).doesNotContain(
                "candidate.source_transaction_id,\n",
                "candidate.source_transaction_date_time,",
                "candidate.source_transaction_id = :transactionId");
    }

    /** 交易逐笔明细必须透传源币种和目标币种 exponent，页面不能默认所有币种均为两位小数。 */
    @Test
    @SuppressWarnings("unchecked")
    void transactionSearchShouldExposeCurrencyExponents() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        TransactionItemQuery query = new TransactionItemQuery();
        query.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        query.setEndBusinessDate(LocalDate.of(2026, 8, 31));

        service(jdbcTemplate).searchTransactionItems("merchant-a", query);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
                "item.source_currency_exponent",
                "item.target_currency_exponent");
    }

    /** 批次账单必须同时暴露去重交易笔数、结算项目数和唯一净入账金额。 */
    @Test
    @SuppressWarnings("unchecked")
    void batchSearchShouldExposeOperationalCountsAndNetPosting() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        BatchQuery query = new BatchQuery();
        query.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        query.setEndBusinessDate(LocalDate.of(2026, 8, 31));

        service(jdbcTemplate).searchBatches("merchant-a", query);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
                "COUNT(DISTINCT item.source_transaction_id)",
                "AS transaction_count",
                "batch.merchant_id",
                "merchant.merchant_name",
                "account.account_no AS settlement_account_no",
                "batch.candidate_count",
                "net.direction AS net_direction",
                "net.target_amount AS net_amount");
    }

    private JdbcMerchantSettlementQueryService service(NamedParameterJdbcTemplate jdbcTemplate) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.getQueryBudget().setMaxResultRows(100);
        return new JdbcMerchantSettlementQueryService(
                jdbcTemplate, new TransactionLogicalReadExecutor(), properties);
    }
}
