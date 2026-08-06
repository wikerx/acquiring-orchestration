package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户交易查询展示规则测试。
 */
class JdbcMerchantTransactionQueryServiceTests {

    @Test
    void pageOrdersShouldClampRequestedRangeToRegisteredNodesAndCurrentTime() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of("202603", "202604"));
        JdbcMerchantTransactionQueryService service = new JdbcMerchantTransactionQueryService(
                jdbcTemplate, new TransactionLogicalReadExecutor(), properties);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setMerchantId("merchant-a");
        query.setBeginTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2099, 12, 31, 23, 59, 59));

        LocalDateTime beforeQuery = LocalDateTime.now();
        service.pageOrders(query);
        LocalDateTime afterQuery = LocalDateTime.now();

        assertThat(query.getBeginTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(query.getEndTime()).isBetween(beforeQuery, afterQuery);
    }

    @Test
    void pageOrdersShouldReturnEmptyWithoutSqlWhenRangeEndsBeforeFirstRegisteredNode() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of("202603", "202604"));
        JdbcMerchantTransactionQueryService service = new JdbcMerchantTransactionQueryService(
                jdbcTemplate, new TransactionLogicalReadExecutor(), properties);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setMerchantId("merchant-a");
        query.setBeginTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 6, 30, 23, 59, 59));

        assertThat(service.pageOrders(query).getRecords()).isEmpty();

        verify(jdbcTemplate, never()).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }

    @Test
    void pageOrdersShouldUseLogicalTableAndMandatoryMerchantPredicate() throws NoSuchMethodException {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcMerchantTransactionQueryService service = new JdbcMerchantTransactionQueryService(jdbcTemplate);
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
        LocalDateTime transactionTime = LocalDateTime.of(2026, 7, 21, 19, 53, 50, 233_000_000);
        LocalDateTime rootTransactionTime = LocalDateTime.of(2026, 4, 10, 9, 15, 30);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcMerchantTransactionQueryService service = new JdbcMerchantTransactionQueryService(jdbcTemplate);

        assertThatThrownBy(() -> service.detail(
                "merchant-a", "transaction-a", transactionTime, rootTransactionTime))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains("FROM transaction_operation");
        assertThat(sqlCaptor.getValue()).contains("merchant_id = :merchantId");
        assertThat(sqlCaptor.getValue()).contains("transaction_date_time = :transactionDateTime");
        assertThat(sqlCaptor.getValue()).doesNotContain("transactionDateTimeEnd");
        assertThat(paramsCaptor.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        assertThat(paramsCaptor.getValue().getValue("transactionDateTime")).isEqualTo(transactionTime);
        assertThat(paramsCaptor.getValue().hasValue("transactionDateTimeEnd")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void operationPageShouldLoadLifecycleOrdersOnceWithMerchantAndRegisteredNodeRange() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime actionTime = LocalDateTime.of(2026, 7, 21, 19, 53, 50);
        LocalDateTime rootTime = LocalDateTime.of(2026, 4, 10, 9, 15, 30);
        TransactionOperationResponse firstOperation = operation("operation-a", "transaction-a", actionTime);
        TransactionOperationResponse secondOperation = operation("operation-b", "transaction-b", actionTime.plusSeconds(1));
        TransactionOrderResponse firstOrder = order("operation-a", rootTime);
        TransactionOrderResponse secondOrder = order("operation-b", rootTime.plusSeconds(1));
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(2L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    if (sql.contains("SELECT o.*") && sql.contains("FROM transaction_operation o")) {
                        return List.of(firstOperation, secondOperation);
                    }
                    if (sql.contains("FROM transaction_order")) {
                        return List.of(firstOrder, secondOrder);
                    }
                    return Collections.emptyList();
                });
        JdbcMerchantTransactionQueryService service = new JdbcMerchantTransactionQueryService(jdbcTemplate);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setMerchantId("merchant-a");
        query.setBeginTime(LocalDateTime.of(2026, 7, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 7, 31, 23, 59));

        TransactionOperationSearchResponse response = service.searchOperations(query);

        assertThat(response.getPage().getRecords())
                .extracting(TransactionOperationResponse::getRootTransactionDateTime)
                .containsExactly(rootTime, rootTime.plusSeconds(1));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, atLeast(2)).query(
                sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        List<Integer> lifecycleIndexes = new java.util.ArrayList<>();
        for (int index = 0; index < sqlCaptor.getAllValues().size(); index++) {
            String sql = sqlCaptor.getAllValues().get(index);
            if (sql.contains("FROM transaction_order")) {
                lifecycleIndexes.add(index);
                assertThat(sql).contains("merchant_id = :merchantId");
                assertThat(sql).contains("operation_id IN (:operationIds)");
                assertThat(sql).contains("transaction_date_time >= :registeredNodeBegin");
                assertThat(sql).contains("transaction_date_time < :registeredNodeEnd");
                assertThat(paramsCaptor.getAllValues().get(index).getValue("merchantId"))
                        .isEqualTo("merchant-a");
            }
        }
        assertThat(lifecycleIndexes).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void orderEnrichmentShouldScopeEveryAuxiliaryQueryByMerchantAndShardingTime() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 7, 21, 19, 53, 50, 123_000_000);
        com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOrderResponse row =
                new com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOrderResponse();
        row.setMerchantId("merchant-a");
        row.setTransactionDateTime(transactionTime);
        row.setOperationId("operation-a");
        row.setLatestTransactionId("transaction-a");
        row.setRootTransactionId("transaction-root-a");
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<String>getArgument(0).contains("FROM transaction_order")
                        ? List.of(row)
                        : Collections.emptyList());
        JdbcMerchantTransactionQueryService service = new JdbcMerchantTransactionQueryService(jdbcTemplate);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setMerchantId("merchant-a");
        query.setBeginTime(LocalDateTime.of(2026, 7, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 7, 31, 23, 59));

        service.pageOrders(query);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, atLeast(4)).query(
                sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        List<String> sqlStatements = sqlCaptor.getAllValues();
        List<MapSqlParameterSource> parameterSets = paramsCaptor.getAllValues();
        for (int index = 0; index < sqlStatements.size(); index++) {
            String sql = sqlStatements.get(index);
            if (sql.contains("FROM transaction_payment_method_info p")) {
                assertThat(sql).contains("EXISTS", "o.merchant_id = :merchantId");
                assertThat(parameterSets.get(index).getValue("merchantId")).isEqualTo("merchant-a");
                assertThat(parameterSets.get(index).getValue("transactionDateTime")).isEqualTo(transactionTime);
            }
            if (sql.contains("FROM transaction_operation o") && sql.contains("LEFT JOIN")) {
                assertThat(sql).contains("o.merchant_id = :merchantId");
                assertThat(parameterSets.get(index).getValue("merchantId")).isEqualTo("merchant-a");
                assertThat(parameterSets.get(index).getValue("transactionDateTime")).isEqualTo(transactionTime);
            }
        }
        assertThat(sqlStatements.stream().filter(sql -> sql.contains("FROM transaction_payment_method_info p")))
                .hasSize(2);
        assertThat(sqlStatements.stream().filter(sql -> sql.contains("FROM transaction_operation o") && sql.contains("LEFT JOIN")))
                .hasSize(1);
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

    private TransactionOperationResponse operation(String operationId,
                                                   String transactionId,
                                                   LocalDateTime transactionDateTime) {
        TransactionOperationResponse operation = new TransactionOperationResponse();
        operation.setMerchantId("merchant-a");
        operation.setOperationId(operationId);
        operation.setTransactionId(transactionId);
        operation.setTransactionDateTime(transactionDateTime);
        return operation;
    }

    private TransactionOrderResponse order(String operationId, LocalDateTime transactionDateTime) {
        TransactionOrderResponse order = new TransactionOrderResponse();
        order.setMerchantId("merchant-a");
        order.setOperationId(operationId);
        order.setTransactionDateTime(transactionDateTime);
        return order;
    }

}
