package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
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
                .contains("item.merchant_id = :merchantId", "item.source_detail_type = 'TRANSACTION_CLEARING'",
                        "item.source_transaction_id IS NOT NULL",
                        "batch.batch_status IN ('POSTED', 'REVERSED')"));
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql)
                .contains("reserve.merchant_id = :merchantId", "FROM merchant_reserve_action action",
                        "batch.batch_status IN ('POSTED', 'REVERSED')"));
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
    void reserveSearchShouldExposeCandidateTransactionTimeForPreciseTransactionLink() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        ReserveItemQuery query = new ReserveItemQuery();
        query.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        query.setEndBusinessDate(LocalDate.of(2026, 8, 31));
        query.setSourceTransactionId("T-RETURN-1001");

        service(jdbcTemplate).searchReserveItems("merchant-a", query);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
                "candidate.source_transaction_id",
                "candidate.source_transaction_date_time",
                "candidate.source_transaction_id = :transactionId",
                "JOIN settlement_candidate candidate",
                "candidate.merchant_id = reserve.merchant_id",
                "LEFT JOIN base_iso_currency currency",
                "AS currency_exponent");
        assertThat(sqlCaptor.getValue()).doesNotContain("reserve.source_transaction_id,");
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
