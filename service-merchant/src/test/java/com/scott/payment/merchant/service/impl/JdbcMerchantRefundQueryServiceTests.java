package com.scott.payment.merchant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
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
 * 商户端退款本地查询测试，验证认证商户隔离、商户可见投影和 ShardingSphere 普通读路由。
 */
@Slf4j
class JdbcMerchantRefundQueryServiceTests {

    /**
     * 所有商户退款分页与统计 SQL 必须绑定认证商户号。
     */
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

    /**
     * 分币种退款汇总必须保留交易事实中的币种精度和十进制金额，禁止按两位小数推断。
     */
    @Test
    @SuppressWarnings("unchecked")
    void searchShouldPreserveCurrencyExponentAndExactSummaryAmount() throws Exception {
        log.info("用例：验证商户端退款汇总保留JPY和USD币种精度及大额十进制金额");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        ResultSet countResultSet = mock(ResultSet.class);
        ResultSet jpyResultSet = refundCurrencyResultSet("JPY", 0, "100");
        ResultSet usdResultSet = refundCurrencyResultSet("USD", 2, "123456789012345678.90");
        when(countResultSet.getLong(anyString())).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<RefundSummary>>getArgument(2)
                        .mapRow(countResultSet, 0));
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2, RowMapper.class);
                    return List.of(mapper.mapRow(jpyResultSet, 0), mapper.mapRow(usdResultSet, 1));
                });
        JdbcMerchantRefundQueryService service = new JdbcMerchantRefundQueryService(
                jdbcTemplate, readExecutor, new TransactionShardingProperties());
        RefundQuery query = query();
        query.setMerchantId("merchant-a");

        RefundSummary summary = service.search(query).getSummary();

        assertThat(summary.getCurrencyAmounts()).hasSize(2);
        assertThat(summary.getCurrencyAmounts().get(0).getCurrency()).isEqualTo("JPY");
        assertThat(summary.getCurrencyAmounts().get(0).getCurrencyExponent()).isZero();
        assertThat(summary.getCurrencyAmounts().get(0).getTotalAmount())
                .isEqualByComparingTo("100");
        assertThat(summary.getCurrencyAmounts().get(1).getCurrency()).isEqualTo("USD");
        assertThat(summary.getCurrencyAmounts().get(1).getCurrencyExponent()).isEqualTo(2);
        assertThat(summary.getCurrencyAmounts().get(1).getTotalAmount())
                .isEqualByComparingTo("123456789012345678.90");
        String serializedSummary = new ObjectMapper()
                .writeValueAsString(summary.getCurrencyAmounts().get(1));
        assertThat(serializedSummary).contains(
                "\"currencyExponent\":2",
                "\"totalAmount\":\"123456789012345678.90\"",
                "\"pendingApprovalAmount\":\"123456789012345678.90\"",
                "\"successfulAmount\":\"123456789012345678.90\"",
                "\"pendingAmount\":\"123456789012345678.90\"");
        ArgumentCaptor<String> summarySql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                summarySql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(summarySql.getValue()).contains(
                "o.label_currency AS currency",
                "o.label_currency_exponent AS currency_exponent",
                "GROUP BY o.label_currency, o.label_currency_exponent")
                .doesNotContain("o.transaction_currency", "o.transaction_amount", "o.currency_exponent");
        log.info("结果：商户端退款汇总按币种和精度分组，金额未经过浮点转换");
    }

    /**
     * 缺少认证商户号时必须在进入读作用域和访问数据库前失败。
     */
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

    /**
     * 商户退款详情首查必须同时绑定认证商户号和毫秒级真实分片时间。
     */
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

    /**
     * 创建退款分币种汇总结果集，所有金额列使用同一精确十进制值。
     */
    private ResultSet refundCurrencyResultSet(String currency,
                                              int currencyExponent,
                                              String amount) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("currency")).thenReturn(currency);
        when(resultSet.getInt("currency_exponent")).thenReturn(currencyExponent);
        when(resultSet.getBigDecimal(anyString())).thenReturn(new BigDecimal(amount));
        return resultSet;
    }

    private RefundQuery query() {
        RefundQuery query = new RefundQuery();
        query.setBeginTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 8, 8, 0, 0));
        return query;
    }
}
