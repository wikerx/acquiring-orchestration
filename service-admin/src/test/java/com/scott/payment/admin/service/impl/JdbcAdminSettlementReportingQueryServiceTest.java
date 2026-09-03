package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSummary;
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
 * @description : 验证结算结果和入账查询的稳定分页、汇率读取及商户数据范围。
 * @status : create
 */
class JdbcAdminSettlementReportingQueryServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void resultSearchShouldJoinLockedRateAndApplyCustomMerchantScope() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new ResultItemSummary()));
        JdbcAdminSettlementReportingQueryService service = service(jdbc);
        ResultItemSearchRequest request = resultRequest();
        request.setTargetCurrency(" usd ");

        service.searchResultItems(request, AdminMerchantDataScope.limited(Set.of("M1001")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("JOIN settlement_batch_rate rate", "rate.direct_rate",
                "ri.merchant_id IN (:permittedMerchantIds)",
                "ORDER BY COALESCE(ri.source_transaction_date_time, ri.create_time) DESC, ri.id DESC");
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
    void reserveSearchShouldExposeCandidateTransactionTimeWithinMerchantScope() {
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
                "candidate.source_transaction_id",
                "candidate.source_transaction_date_time",
                "candidate.source_transaction_id = :transactionId",
                "JOIN settlement_candidate candidate",
                "candidate.merchant_id = reserve.merchant_id",
                "LEFT JOIN base_iso_currency currency",
                "AS currency_exponent",
                "reserve.merchant_id IN (:permittedMerchantIds)");
        assertThat(sql.getValue()).doesNotContain("reserve.source_transaction_id,");
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
