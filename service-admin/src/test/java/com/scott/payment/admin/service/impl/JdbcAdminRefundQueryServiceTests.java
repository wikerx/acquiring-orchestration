package com.scott.payment.admin.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundRecord;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundSummary;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
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
import java.util.Set;

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
 * 管理端退款本地查询测试，验证逻辑表 SQL、统一筛选条件和 ShardingSphere 普通读路由。
 */
@Slf4j
class JdbcAdminRefundQueryServiceTests {

    /**
     * 异步退款导出必须把有限商户集合落实为 SQL IN 条件且不额外执行统计。
     */
    @Test
    void refundExportShouldApplyFrozenMerchantScopeInSql() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        JdbcAdminRefundQueryService service = new JdbcAdminRefundQueryService(
                jdbcTemplate, readExecutor, mock(AdminTransactionQueryService.class),
                new TransactionShardingProperties());

        service.pageForExport(query(), AdminMerchantDataScope.limited(Set.of("M3001", "M3002")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), params.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains("o.merchant_id IN (:scopeMerchantIds)");
        assertThat(params.getValue().getValue("scopeMerchantIds"))
                .isEqualTo(Set.of("M3001", "M3002"));
    }

    /**
     * 退款分页和统计必须在 transaction 普通读作用域内使用同一组筛选条件。
     */
    @Test
    @SuppressWarnings("unchecked")
    void searchShouldUseLogicalTablesAndReplicaEligibleReadScope() throws Exception {
        log.info("用例：验证管理端退款查询使用交易逻辑表和可路由副本的普通读作用域");
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
        JdbcAdminRefundQueryService service = new JdbcAdminRefundQueryService(
                jdbcTemplate, readExecutor, mock(AdminTransactionQueryService.class),
                new TransactionShardingProperties());
        RefundQuery query = query();
        query.setMerchantId("merchant-a");
        query.setPaymentBrand("VISA");

        service.search(query);

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> countParams =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                countSql.capture(), countParams.capture(), eq(Long.class));
        assertThat(countSql.getValue()).contains(
                "FROM transaction_operation o",
                "LEFT JOIN transaction_refund_approval a",
                "LEFT JOIN transaction_payment_method_info p",
                "o.merchant_id = :merchantId",
                "p.payment_brand = :paymentBrand",
                "o.transaction_date_time >= :beginTime");
        assertThat(countParams.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        verify(readExecutor).read(any());
        verify(readExecutor, never()).readPrimary(any());
        log.info("结果：退款分页、状态统计和金额统计均在transaction普通读作用域执行");
    }

    /**
     * 分币种退款汇总必须保留交易事实中的币种精度和十进制金额，禁止按两位小数推断。
     */
    @Test
    @SuppressWarnings("unchecked")
    void searchShouldPreserveCurrencyExponentAndExactSummaryAmount() throws Exception {
        log.info("用例：验证管理端退款汇总保留JPY和USD币种精度及大额十进制金额");
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
        JdbcAdminRefundQueryService service = new JdbcAdminRefundQueryService(
                jdbcTemplate, readExecutor, mock(AdminTransactionQueryService.class),
                new TransactionShardingProperties());

        RefundSummary summary = service.search(query()).getSummary();

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
                "o.currency_exponent",
                "GROUP BY o.transaction_currency, o.currency_exponent");
        log.info("结果：管理端退款汇总按币种和精度分组，金额未经过浮点转换");
    }

    /**
     * 非法金额范围必须在进入读作用域和访问数据库前失败。
     */
    @Test
    void searchShouldRejectInvalidAmountRangeBeforeDatabaseAccess() {
        log.info("用例：验证退款最小金额大于最大金额时拒绝访问数据库");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = mock(TransactionLogicalReadExecutor.class);
        JdbcAdminRefundQueryService service = new JdbcAdminRefundQueryService(
                jdbcTemplate, readExecutor, mock(AdminTransactionQueryService.class),
                new TransactionShardingProperties());
        RefundQuery query = query();
        query.setMinimumTransactionAmount(new java.math.BigDecimal("10.01"));
        query.setMaximumTransactionAmount(new java.math.BigDecimal("10.00"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.search(query))
                .isInstanceOf(RuntimeException.class);

        verify(readExecutor, never()).read(any());
        verify(jdbcTemplate, never()).queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class));
        log.info("结果：非法金额范围在数据库访问前被拒绝");
    }

    /**
     * 退款详情首查必须绑定交易号和毫秒级真实分片时间。
     */
    @Test
    @SuppressWarnings("unchecked")
    void detailShouldBindExactTransactionShardTime() {
        log.info("用例：验证管理端退款详情使用交易号和毫秒级分片时间精确查询");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcAdminRefundQueryService service = new JdbcAdminRefundQueryService(
                jdbcTemplate, readExecutor, mock(AdminTransactionQueryService.class),
                new TransactionShardingProperties());
        LocalDateTime transactionTime = LocalDateTime.of(
                2026, 8, 8, 10, 15, 30, 123_000_000);

        assertThatThrownBy(() -> service.detail("refund-1", transactionTime))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "o.transaction_id = :transactionId",
                "o.transaction_date_time = :transactionDateTime");
        assertThat(parameters.getValue().getValue("transactionDateTime"))
                .isEqualTo(transactionTime);
        verify(readExecutor).read(any());
        log.info("结果：退款详情使用精确毫秒分片时间且未执行范围扫描");
    }

    /**
     * 退款和撤销列表必须返回对应商户通知任务的当前状态。
     */
    @Test
    @SuppressWarnings("unchecked")
    void searchShouldExposeCurrentMerchantNotificationStatus() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 17, 11, 51, 32, 567_000_000);
        RefundRecord refund = new RefundRecord();
        refund.setRefundTransactionId("transaction-a");
        refund.setOperationId("operation-a");
        refund.setTransactionDateTime(transactionTime);
        ResultSet notificationResultSet = mock(ResultSet.class);
        when(notificationResultSet.getString("transaction_id")).thenReturn("transaction-a");
        when(notificationResultSet.getString("notify_status")).thenReturn("SUCCESS");
        ResultSet summaryResultSet = mock(ResultSet.class);
        when(summaryResultSet.getLong(anyString())).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<RefundSummary>>getArgument(2)
                        .mapRow(summaryResultSet, 0));
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    if (sql.contains("o.transaction_id AS refund_transaction_id")) {
                        return List.of(refund);
                    }
                    if (sql.contains("FROM transaction_merchant_notification")) {
                        RowMapper<?> mapper = invocation.getArgument(2, RowMapper.class);
                        return List.of(mapper.mapRow(notificationResultSet, 0));
                    }
                    return Collections.emptyList();
                });
        JdbcAdminRefundQueryService service = new JdbcAdminRefundQueryService(
                jdbcTemplate, readExecutor, mock(AdminTransactionQueryService.class),
                new TransactionShardingProperties());

        RefundRecord result = service.search(query()).getPage().getRecords().get(0);

        assertThat(result.getMerchantNotificationStatus()).isEqualTo("SUCCESS");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeast(3)).query(
                sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        int notificationQueryIndex = java.util.stream.IntStream.range(0, sqlCaptor.getAllValues().size())
                .filter(index -> sqlCaptor.getAllValues().get(index).contains("FROM transaction_merchant_notification"))
                .findFirst()
                .orElseThrow();
        assertThat(sqlCaptor.getAllValues().get(notificationQueryIndex))
                .contains("transaction_id IN (:transactionIds)")
                .contains("transaction_date_time >= :notificationBeginTime")
                .contains("transaction_date_time < :notificationEndTime")
                .doesNotContain("transaction_merchant_notification_2026");
        assertThat(paramsCaptor.getAllValues().get(notificationQueryIndex).getValue("transactionIds"))
                .isEqualTo(List.of("transaction-a"));
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
