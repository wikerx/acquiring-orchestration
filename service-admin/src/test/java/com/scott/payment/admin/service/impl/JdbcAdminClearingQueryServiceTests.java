package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchItem;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.Summary;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Admin 清分本地查询的分片范围、标准分页、稳定排序和金额语义。 */
class JdbcAdminClearingQueryServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void findByReferencesShouldLoadBatchWithOneLogicalReadAndOneQuery() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        Summary first = new Summary();
        first.setTransactionId("PAY-1");
        Summary second = new Summary();
        second.setTransactionId("PAY-2");
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(first, second));
        JdbcAdminClearingQueryService service = new JdbcAdminClearingQueryService(
                jdbcTemplate, readExecutor, new TransactionShardingProperties());
        LocalDateTime firstTime = LocalDateTime.of(2026, 8, 27, 10, 0);
        LocalDateTime secondTime = LocalDateTime.of(2026, 8, 27, 10, 1);

        List<Summary> result = service.findByReferences(List.of(
                reference("PAY-1", firstTime), reference("PAY-2", secondTime)));

        assertThat(result).extracting(Summary::getTransactionId)
                .containsExactly("PAY-1", "PAY-2");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "f.transaction_id = :transactionId0",
                "f.transaction_date_time = :transactionDateTime0",
                "f.transaction_id = :transactionId1",
                "f.transaction_date_time = :transactionDateTime1",
                " OR ",
                "ORDER BY f.transaction_date_time DESC, f.id DESC");
        assertThat(parameters.getValue().getValue("transactionId0")).isEqualTo("PAY-1");
        assertThat(parameters.getValue().getValue("transactionDateTime1")).isEqualTo(secondTime);
        verify(readExecutor).read(any());
        verify(readExecutor, never()).readPrimary(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchShouldPageOnTransactionReplicaAndExposeTransactionAndClearingAmountsSeparately() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(11L);
        Summary authorization = new Summary();
        authorization.setTransactionId("AUTH-1");
        authorization.setLabelCurrency("USD");
        authorization.setLabelAmount(new BigDecimal("100.00"));
        authorization.setGrossLabelAmount(BigDecimal.ZERO);
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(authorization));
        JdbcAdminClearingQueryService service = new JdbcAdminClearingQueryService(
                jdbcTemplate, readExecutor, new TransactionShardingProperties());
        SearchRequest request = request();
        request.setPageNo(2);
        request.setPageSize(10);

        var page = service.search(request);

        assertThat(page.getTotal()).isEqualTo(11L);
        assertThat(page.getPageNo()).isEqualTo(2L);
        assertThat(page.getPageSize()).isEqualTo(10L);
        assertThat(page.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getLabelAmount()).isEqualByComparingTo("100.00");
            assertThat(row.getGrossLabelAmount()).isEqualByComparingTo("0");
        });
        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                countSql.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        assertThat(countSql.getValue())
                .contains("SELECT COUNT(1)", "FROM transaction_finance_state f")
                .doesNotContain("transaction_operation");
        ArgumentCaptor<String> listSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> listParameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(
                listSql.capture(), listParameters.capture(), any(RowMapper.class));
        assertThat(listSql.getValue()).contains(
                "FROM transaction_finance_state f",
                "LEFT JOIN transaction_operation o",
                "o.label_amount AS label_amount",
                "ORDER BY f.transaction_date_time DESC, f.id DESC",
                "LIMIT :offset, :limit");
        assertThat(listParameters.getValue().getValue("offset")).isEqualTo(10L);
        verify(readExecutor).read(any());
        verify(readExecutor, never()).readPrimary(any());
    }

    private SearchRequest request() {
        SearchRequest request = new SearchRequest();
        request.setBeginTime(LocalDateTime.of(2026, 7, 1, 0, 0));
        request.setEndTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        return request;
    }

    private RecalculateBatchItem reference(String transactionId, LocalDateTime transactionDateTime) {
        RecalculateBatchItem item = new RecalculateBatchItem();
        item.setTransactionId(transactionId);
        item.setTransactionDateTime(transactionDateTime);
        return item;
    }

    private TransactionLogicalReadExecutor executingReadExecutor() {
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        return executor;
    }
}
