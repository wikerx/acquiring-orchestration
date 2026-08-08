package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundSummary;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcMerchantRefundQueryServiceTests
 * @date : 2026-08-08 00:50
 * @email : scott_x@163.com
 * @description : 商户端退款本地查询测试，验证认证商户隔离、商户可见投影和 ShardingSphere 普通读路由。
 * @status : create
 */
@Slf4j
class JdbcMerchantRefundQueryServiceTests {

    /** 所有商户退款分页与统计 SQL 必须绑定认证商户号。 */
    @Test
    @SuppressWarnings("unchecked")
    void searchShouldBindAuthenticatedMerchantInEveryQuery() throws Exception {
        log.info("用例：验证商户退款分页和统计的每条SQL均绑定认证商户号");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong(anyString())).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<RefundSummary>>getArgument(2)
                        .mapRow(resultSet, 0));
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcMerchantRefundQueryService service = new JdbcMerchantRefundQueryService(
                jdbcTemplate, readExecutor, new TransactionShardingProperties());
        RefundQuery query = query();
        query.setMerchantId("merchant-a");

        service.search(query);

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> countParams =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                countSql.capture(), countParams.capture(), eq(Long.class));
        ArgumentCaptor<String> summarySql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> summaryParams =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                summarySql.capture(), summaryParams.capture(), any(RowMapper.class));
        assertThat(java.util.List.of(countSql.getValue(), summarySql.getValue()))
                .allSatisfy(sql -> assertThat(sql)
                .contains("o.merchant_id = :merchantId")
                .doesNotContain("fail_reason_code", "fail_reason_message", "channel_response_code"));
        assertThat(java.util.List.of(countParams.getValue(), summaryParams.getValue()))
                .allSatisfy(parameters ->
                assertThat(parameters.getValue("merchantId")).isEqualTo("merchant-a"));
        ArgumentCaptor<String> listSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                listSql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(listSql.getValue()).contains("o.merchant_id = :merchantId");
        verify(readExecutor).read(any());
        verify(readExecutor, never()).readPrimary(any());
        log.info("结果：商户退款查询全部绑定merchant-a且未读取内部失败或渠道响应字段");
    }

    /** 缺少认证商户号时必须在进入读作用域和访问数据库前失败。 */
    @Test
    void searchShouldRejectMissingMerchantBeforeDatabaseAccess() {
        log.info("用例：验证缺少认证商户号时拒绝执行退款查询");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = mock(TransactionLogicalReadExecutor.class);
        JdbcMerchantRefundQueryService service = new JdbcMerchantRefundQueryService(
                jdbcTemplate, readExecutor, new TransactionShardingProperties());
        RefundQuery query = query();

        assertThatThrownBy(() -> service.search(query)).isInstanceOf(RuntimeException.class);

        verify(readExecutor, never()).read(any());
        verify(jdbcTemplate, never()).queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class));
        log.info("结果：商户号缺失时未进入transaction读作用域且未访问数据库");
    }

    /** 商户退款详情首查必须同时绑定认证商户号和毫秒级真实分片时间。 */
    @Test
    @SuppressWarnings("unchecked")
    void detailShouldBindMerchantAndExactTransactionShardTime() {
        log.info("用例：验证商户退款详情同时绑定认证商户号和毫秒级分片时间");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcMerchantRefundQueryService service = new JdbcMerchantRefundQueryService(
                jdbcTemplate, readExecutor, new TransactionShardingProperties());
        LocalDateTime transactionTime = LocalDateTime.of(
                2026, 8, 8, 10, 25, 30, 789_000_000);

        assertThatThrownBy(() -> service.detail(
                "merchant-a", "refund-1", transactionTime))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "o.merchant_id = :merchantId",
                "o.transaction_id = :transactionId",
                "o.transaction_date_time = :transactionDateTime");
        assertThat(parameters.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        assertThat(parameters.getValue().getValue("transactionDateTime"))
                .isEqualTo(transactionTime);
        verify(readExecutor).read(any());
        log.info("结果：退款详情使用认证商户号和精确毫秒分片时间，未执行跨商户范围扫描");
    }

    private TransactionLogicalReadExecutor executingReadExecutor() {
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        return executor;
    }

    private RefundQuery query() {
        RefundQuery query = new RefundQuery();
        query.setBeginTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 8, 8, 0, 0));
        return query;
    }
}
