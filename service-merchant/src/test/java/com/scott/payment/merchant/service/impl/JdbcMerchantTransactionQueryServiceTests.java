package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingMode;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.db.sharding.TransactionShardingRuntimeState;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 商户交易查询展示规则测试。
 */
class JdbcMerchantTransactionQueryServiceTests {

    @Test
    void compareModeShouldReturnMerchantScopedLegacyPageAndShadowReadLogicalTable() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ShardingDataTemplate shardingDataTemplate = mock(ShardingDataTemplate.class);
        when(shardingDataTemplate.resolvePhysicalTables(any(com.scott.payment.component.db.sharding.ShardingRangeTableContext.class)))
                .thenReturn(java.util.List.of("transaction_order_202603"));
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(2L, 5L);
        JdbcMerchantTransactionQueryService service = new JdbcMerchantTransactionQueryService(
                jdbcTemplate,
                shardingDataTemplate,
                mock(TransactionShardingKeyParser.class),
                runtimeState(TransactionShardingMode.COMPARE),
                new TransactionLogicalReadExecutor());
        TransactionPageQuery query = new TransactionPageQuery();
        query.setMerchantId("merchant-a");
        query.setPageNo(2);
        query.setPageSize(20);
        query.setBeginTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 7, 31, 23, 59));

        long returnedTotal = service.pageOrders(query).getTotal();

        assertThat(returnedTotal).isEqualTo(2L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForObject(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        assertThat(sqlCaptor.getAllValues().get(0)).contains("FROM transaction_order_202603");
        assertThat(sqlCaptor.getAllValues().get(1)).contains("FROM transaction_order");
        assertThat(sqlCaptor.getAllValues().get(1)).contains("merchant_id = :merchantId");
    }

    @Test
    void pageOrdersShouldUseLogicalTableAndMandatoryMerchantPredicate() throws NoSuchMethodException {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ShardingDataTemplate shardingDataTemplate = mock(ShardingDataTemplate.class);
        JdbcMerchantTransactionQueryService service = new JdbcMerchantTransactionQueryService(
                jdbcTemplate,
                shardingDataTemplate,
                mock(TransactionShardingKeyParser.class),
                shardingSphereRuntimeState(),
                new TransactionLogicalReadExecutor());
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setMerchantId("merchant-a");
        query.setBeginTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 7, 31, 23, 59));

        service.pageOrders(query);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class));
        assertThat(sqlCaptor.getValue()).contains("FROM transaction_order");
        assertThat(sqlCaptor.getValue()).doesNotContain("transaction_order_2026");
        assertThat(sqlCaptor.getValue()).contains("merchant_id = :merchantId");
        assertThat(sqlCaptor.getValue()).contains("transaction_date_time >= :beginTime");
        assertThat(paramsCaptor.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        verifyNoInteractions(shardingDataTemplate);
        assertThat(JdbcMerchantTransactionQueryService.class.getAnnotation(DS.class)).isNull();
        assertThat(TransactionLogicalReadExecutor.class
                .getMethod("read", java.util.function.Supplier.class)
                .getAnnotation(DS.class).value()).isEqualTo(DataSourceName.TRANSACTION);
    }

    @Test
    void pageOrdersShouldApplyConfiguredResultRowBudget() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.getQueryBudget().setMaxResultRows(9);
        JdbcMerchantTransactionQueryService service = new JdbcMerchantTransactionQueryService(
                jdbcTemplate,
                mock(ShardingDataTemplate.class),
                mock(TransactionShardingKeyParser.class),
                shardingSphereRuntimeState(),
                new TransactionLogicalReadExecutor(),
                properties);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setMerchantId("merchant-a");
        query.setPageSize(500);

        service.pageOrders(query);

        assertThat(query.getPageSize()).isEqualTo(9);
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailShouldBindMerchantAndExactShardingTimeInFirstQuery() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ShardingDataTemplate shardingDataTemplate = mock(ShardingDataTemplate.class);
        TransactionShardingKeyParser parser = mock(TransactionShardingKeyParser.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 7, 21, 19, 53, 50);
        when(parser.parseTransactionDateTime("transaction-a")).thenReturn(transactionTime);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcMerchantTransactionQueryService service = new JdbcMerchantTransactionQueryService(
                jdbcTemplate, shardingDataTemplate, parser, shardingSphereRuntimeState(),
                new TransactionLogicalReadExecutor());

        assertThatThrownBy(() -> service.detail("merchant-a", "transaction-a"))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains("FROM transaction_operation");
        assertThat(sqlCaptor.getValue()).contains("merchant_id = :merchantId");
        assertThat(sqlCaptor.getValue()).contains("transaction_date_time = :transactionDateTime");
        assertThat(paramsCaptor.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        assertThat(paramsCaptor.getValue().getValue("transactionDateTime")).isEqualTo(transactionTime);
        verifyNoInteractions(shardingDataTemplate);
    }

    @Test
    void shouldKeepRequestedAmountForRejectedAuthorization() {
        BigDecimal amount = JdbcMerchantTransactionQueryService.resolveCurrentAmount(
                "AUTHORIZATION",
                new BigDecimal("28.50"),
                BigDecimal.ZERO
        );

        assertThat(amount).isEqualByComparingTo("28.50");
    }

    @Test
    void shouldExposePersistedRiskBlockedMessageToMerchant() {
        String message = JdbcMerchantTransactionQueryService.resolveMerchantResponseMessage(
                "FAILED",
                "Risk blocked"
        );

        assertThat(message).isEqualTo("Risk blocked");
    }

    private TransactionShardingRuntimeState shardingSphereRuntimeState() {
        return runtimeState(TransactionShardingMode.SHARDINGSPHERE);
    }

    private TransactionShardingRuntimeState runtimeState(TransactionShardingMode mode) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setMode(mode);
        TransactionShardingRuntimeState runtimeState = new TransactionShardingRuntimeState();
        runtimeState.activate(properties);
        return runtimeState;
    }
}
