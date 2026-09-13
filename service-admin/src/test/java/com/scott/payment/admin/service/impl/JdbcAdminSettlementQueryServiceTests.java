package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.OperationalState;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultSummaryLine;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * @classname : JdbcAdminSettlementQueryServiceTests
 * @date : 2026-09-01 23:20
 * @email : scott_x@163.com
 * @description : 验证 Admin 结算本地查询的标准分页、稳定排序、数据范围和普通读路由
 * @status : create
 */
class JdbcAdminSettlementQueryServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void searchShouldUseStandardPaginationAndNewestBusinessBatchOrder() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(11L);
        BatchSummary row = new BatchSummary();
        row.setId(1L);
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setBusinessDate(LocalDate.of(2026, 8, 26));
        row.setDailySequence(1);
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, readExecutor, new TransactionShardingProperties());
        BatchSearchRequest request = new BatchSearchRequest();
        request.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        request.setEndBusinessDate(LocalDate.of(2026, 8, 31));
        request.setPageNo(2);
        request.setPageSize(10);

        var page = service.search(request, AdminMerchantDataScope.all());

        assertThat(page.getTotal()).isEqualTo(11L);
        assertThat(page.getPageNo()).isEqualTo(2L);
        assertThat(page.getRecords()).singleElement()
                .extracting(BatchSummary::getDisplayBatchNo)
                .isEqualTo("2026-08-26 00000001");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "FROM settlement_batch",
                "AS merchant_name",
                "AS settlement_account_no",
                "COUNT(DISTINCT item.source_transaction_id)",
                "AS transaction_count",
                "item.result_role = 'LEDGER_POSTING'",
                "AS net_direction",
                "AS net_amount",
                "ORDER BY business_date DESC, id DESC",
                "LIMIT :offset, :limit");
        assertThat(parameters.getValue().getValue("offset")).isEqualTo(10L);
        verify(readExecutor).read(any());
        verify(readExecutor, never()).readPrimary(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchShouldApplyCustomMerchantScopeInCountAndPageQueries() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());
        BatchSearchRequest request = request();

        service.search(request, AdminMerchantDataScope.limited(java.util.Set.of("M1001", "M1002")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), parameters.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains("merchant_id IN (:permittedMerchantIds)");
        assertThat(parameters.getValue().getValue("permittedMerchantIds"))
                .isEqualTo(java.util.Set.of("M1001", "M1002"));
    }

    @Test
    void transactionDomainShouldUseServerControlledRegularPredicate() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());
        BatchSearchRequest request = request();
        request.setBatchDomain("transaction");

        service.search(request, AdminMerchantDataScope.all());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), parameters.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains("batch_type = :transactionBatchType");
        assertThat(parameters.getValue().getValue("transactionBatchType")).isEqualTo("REGULAR");
    }

    @Test
    void reserveDomainShouldUseReleaseAdjustmentPredicateAndOptionalExactType() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());
        BatchSearchRequest request = request();
        request.setBatchDomain("RESERVE");
        request.setBatchType("adjustment");

        service.search(request, AdminMerchantDataScope.all());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), parameters.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains(
                "batch_type IN (:reserveBatchTypes)",
                "batch_type = :batchType");
        assertThat(parameters.getValue().getValue("reserveBatchTypes"))
                .isEqualTo(List.of("RESERVE_RELEASE", "ADJUSTMENT"));
        assertThat(parameters.getValue().getValue("batchType")).isEqualTo("ADJUSTMENT");
    }

    @Test
    void batchDomainShouldRejectIncompatibleBatchTypeBeforeDatabaseQuery() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());
        BatchSearchRequest request = request();
        request.setBatchDomain("TRANSACTION");
        request.setBatchType("RESERVE_RELEASE");

        assertThatThrownBy(() -> service.search(request, AdminMerchantDataScope.all()))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void emptyScopeShouldReturnEmptyPageWithoutDatabaseQuery() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());

        var page = service.search(request(), AdminMerchantDataScope.limited(java.util.Set.of()));

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailShouldNotLoadAllResultSummaries() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        BatchSummary batch = new BatchSummary();
        batch.setSettlementBatchNo("SB20260831-00000001");
        batch.setBusinessDate(LocalDate.of(2026, 8, 31));
        batch.setDailySequence(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<String>getArgument(0).contains("FROM settlement_batch\n")
                        ? List.of(batch) : List.of());
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(new OperationalState());

        new JdbcAdminSettlementQueryService(jdbcTemplate, executingReadExecutor(),
                new TransactionShardingProperties())
                .detail("SB20260831-00000001", AdminMerchantDataScope.all());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(3)).query(
                sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getAllValues()).noneSatisfy(detailSql -> assertThat(detailSql)
                .contains("FROM settlement_result_summary summary"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void voucherShouldLoadCompleteResultSummaryAfterApplyingMerchantScope() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        BatchSummary batch = new BatchSummary();
        batch.setSettlementBatchNo("SB20260831-00000001");
        batch.setBusinessDate(LocalDate.of(2026, 8, 31));
        batch.setDailySequence(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("FROM settlement_batch\n")) return List.of(batch);
                    if (sql.contains("FROM settlement_result_summary summary")) {
                        return List.of(new ResultSummaryLine());
                    }
                    return List.of();
                });
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(new OperationalState());

        var detail = new JdbcAdminSettlementQueryService(jdbcTemplate, executingReadExecutor(),
                new TransactionShardingProperties())
                .voucherDetail("SB20260831-00000001",
                        AdminMerchantDataScope.limited(java.util.Set.of("M1001")));

        assertThat(detail.getResultSummaries()).hasSize(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(4)).query(
                sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getAllValues()).anySatisfy(summarySql -> assertThat(summarySql).contains(
                "FROM settlement_result_summary summary",
                "batch.merchant_id IN (:permittedMerchantIds)",
                "ORDER BY summary.merchant_id, summary.payment_type"));
        assertThat(parameters.getAllValues()).anySatisfy(value -> assertThat(
                value.getValue("permittedMerchantIds")).isEqualTo(java.util.Set.of("M1001")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resultSummaryShouldUseStablePaginationAndAdminMerchantScope() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(25L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new ResultSummaryLine()));
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());

        var page = service.searchResultSummaries(
                "SB20260831-00000001", 2, 20,
                AdminMerchantDataScope.limited(java.util.Set.of("M1001", "M1002")));

        assertThat(page.getTotal()).isEqualTo(25L);
        assertThat(page.getPageNo()).isEqualTo(2L);
        assertThat(page.getPageSize()).isEqualTo(20L);
        assertThat(page.getRecords()).hasSize(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "FROM settlement_result_summary summary",
                "batch.merchant_id IN (:permittedMerchantIds)",
                "AS source_currency_exponent",
                "batch.target_currency_exponent",
                "ORDER BY summary.merchant_id, summary.payment_type, summary.payment_method",
                "summary.target_currency, summary.id",
                "LIMIT :offset, :limit");
        assertThat(parameters.getValue().getValue("offset")).isEqualTo(20L);
        assertThat(parameters.getValue().getValue("limit")).isEqualTo(20);
        assertThat(parameters.getValue().getValue("permittedMerchantIds"))
                .isEqualTo(java.util.Set.of("M1001", "M1002"));
    }

    @Test
    void accessibleBatchWithoutSummariesShouldReturnEmptyPage() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForList(
                anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn(List.of("M1001"));
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());

        var page = service.searchResultSummaries(
                "SB20260831-00000001", 1, 20,
                AdminMerchantDataScope.limited(java.util.Set.of("M1001")));

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
        verify(jdbcTemplate).queryForList(
                anyString(), any(MapSqlParameterSource.class), eq(String.class));
    }

    private BatchSearchRequest request() {
        BatchSearchRequest request = new BatchSearchRequest();
        request.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        request.setEndBusinessDate(LocalDate.of(2026, 8, 31));
        return request;
    }

    private TransactionLogicalReadExecutor executingReadExecutor() {
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        return executor;
    }
}
