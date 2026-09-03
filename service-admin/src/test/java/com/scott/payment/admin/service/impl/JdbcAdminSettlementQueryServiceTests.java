package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.OperationalState;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
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
    void detailSummaryShouldExposeSourceAndFrozenTargetCurrencyExponents() {
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
        verify(jdbcTemplate, times(4)).query(
                sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getAllValues()).anySatisfy(summarySql -> assertThat(summarySql).contains(
                "FROM settlement_result_summary summary",
                "AS source_currency_exponent",
                "batch.target_currency_exponent"));
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
