package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingRangeTableContext;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingMode;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.db.sharding.TransactionShardingRuntimeState;
import com.scott.payment.admin.service.AdminRiskTimelineQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminTransactionQueryServiceTest
 * @date : 2026-07-21 20:20
 * @email : scott_x@163.com
 * @description : 管理后台交易 JDBC 查询服务单元测试，验证交易详情聚合日志查询按表结构生成 SQL，避免无软删字段日志表拼接 deleted 条件。
 * @status : create
 */
class JdbcAdminTransactionQueryServiceTest {

    @Test
    void compareModeShouldReturnLegacyPageAndShadowReadLogicalTable() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ShardingDataTemplate shardingDataTemplate = mock(ShardingDataTemplate.class);
        when(shardingDataTemplate.resolvePhysicalTables(any(ShardingRangeTableContext.class)))
                .thenReturn(List.of("transaction_order_202603"));
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(3L, 4L);
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                shardingDataTemplate,
                mock(TransactionShardingKeyParser.class),
                mock(AdminRiskTimelineQueryService.class),
                runtimeState(TransactionShardingMode.COMPARE),
                new TransactionLogicalReadExecutor());
        TransactionPageQuery query = new TransactionPageQuery();
        query.setPageNo(2);
        query.setPageSize(20);
        query.setBeginTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 7, 31, 23, 59));

        long returnedTotal = service.pageOrders(query).getTotal();

        assertThat(returnedTotal).isEqualTo(3L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForObject(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        assertThat(sqlCaptor.getAllValues().get(0)).contains("FROM transaction_order_202603");
        assertThat(sqlCaptor.getAllValues().get(1)).contains("FROM transaction_order");
        assertThat(sqlCaptor.getAllValues().get(1)).doesNotContain("transaction_order_202603");
    }

    /** ShardingSphere 模式必须通过 transaction 逻辑数据源执行固定逻辑表 SQL。 */
    @Test
    void pageOrdersShouldUseTransactionLogicalTableWithoutPhysicalResolution() throws NoSuchMethodException {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ShardingDataTemplate shardingDataTemplate = mock(ShardingDataTemplate.class);
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                shardingDataTemplate,
                mock(TransactionShardingKeyParser.class),
                mock(AdminRiskTimelineQueryService.class),
                shardingSphereRuntimeState(),
                new TransactionLogicalReadExecutor());
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setBeginTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 7, 31, 23, 59));

        service.pageOrders(query);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        assertThat(sqlCaptor.getValue()).contains("FROM transaction_order");
        assertThat(sqlCaptor.getValue()).doesNotContain("transaction_order_2026");
        assertThat(sqlCaptor.getValue()).contains("transaction_date_time >= :beginTime");
        verifyNoInteractions(shardingDataTemplate);
        assertThat(JdbcAdminTransactionQueryService.class.getAnnotation(DS.class)).isNull();
        DS dataSource = TransactionLogicalReadExecutor.class
                .getMethod("read", java.util.function.Supplier.class)
                .getAnnotation(DS.class);
        assertThat(dataSource.value()).isEqualTo(DataSourceName.TRANSACTION);
    }

    @Test
    void pageOrdersShouldApplyConfiguredResultRowBudget() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.getQueryBudget().setMaxResultRows(7);
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                mock(ShardingDataTemplate.class),
                mock(TransactionShardingKeyParser.class),
                mock(AdminRiskTimelineQueryService.class),
                shardingSphereRuntimeState(),
                new TransactionLogicalReadExecutor(),
                properties);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setPageSize(500);

        service.pageOrders(query);

        assertThat(query.getPageSize()).isEqualTo(7);
    }

    /**
     * 状态历史表没有 deleted 字段，详情聚合查询不应拼接 deleted = 0。
     */
    @Test
    void selectMapsByOperationIdShouldSkipDeletedConditionWhenTableHasNoSoftDeleteColumn() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate, "transaction_status_history_202603");
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(Collections.emptyList());

        invokeSelectMapsByOperationId(service, "transaction_status_history");

        String sql = captureQuerySql(jdbcTemplate);
        assertThat(sql).contains("FROM transaction_status_history_202603");
        assertThat(sql).contains("WHERE 1 = 1");
        assertThat(sql).contains("AND operation_id = :operationId");
        assertThat(sql).contains("AND transaction_date_time >= :beginTime");
        assertThat(sql).contains("AND transaction_date_time < :endTime");
        assertThat(sql).doesNotContain("deleted = 0");
    }

    /**
     * 渠道请求表包含 deleted 字段，详情聚合查询仍应保留软删过滤。
     */
    @Test
    void selectMapsByOperationIdShouldKeepDeletedConditionWhenTableHasSoftDeleteColumn() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate, "transaction_channel_request_202603");
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(Collections.emptyList());

        invokeSelectMapsByOperationId(service, "transaction_channel_request");

        String sql = captureQuerySql(jdbcTemplate);
        assertThat(sql).contains("FROM transaction_channel_request_202603");
        assertThat(sql).contains("WHERE deleted = 0");
        assertThat(sql).contains("AND operation_id = :operationId");
    }

    /**
     * JDBC Map 查询会返回数据库下划线字段名，详情响应必须转换成前端读取的驼峰字段名。
     */
    @Test
    void selectMapsByOperationIdShouldReturnCamelCaseKeysForFrontendDetail() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate, "transaction_channel_interaction_log_202603");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("interaction_log_id", "CI202607211953505260922");
        row.put("transaction_id", "202607211953505070920");
        row.put("request_body_json_masked", "{\"apiOperation\":\"PAY\"}");
        row.put("response_body_json_masked", "{\"result\":\"ERROR\"}");
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        List<Map<String, Object>> rows = invokeSelectMapsByOperationId(service, "transaction_channel_interaction_log");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("interactionLogId", "CI202607211953505260922");
        assertThat(rows.get(0)).containsEntry("transactionId", "202607211953505070920");
        assertThat(rows.get(0)).containsEntry("requestBodyJsonMasked", "{\"apiOperation\":\"PAY\"}");
        assertThat(rows.get(0)).containsEntry("responseBodyJsonMasked", "{\"result\":\"ERROR\"}");
        assertThat(rows.get(0)).doesNotContainKeys("interaction_log_id", "transaction_id", "request_body_json_masked", "response_body_json_masked");
    }

    private JdbcAdminTransactionQueryService buildService(NamedParameterJdbcTemplate jdbcTemplate, String physicalTableName) {
        ShardingDataTemplate shardingDataTemplate = mock(ShardingDataTemplate.class);
        when(shardingDataTemplate.resolvePhysicalTables(any(ShardingRangeTableContext.class))).thenReturn(List.of(physicalTableName));
        return new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                shardingDataTemplate,
                mock(TransactionShardingKeyParser.class),
                mock(AdminRiskTimelineQueryService.class));
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

    @SuppressWarnings("unchecked")
    private List<java.util.Map<String, Object>> invokeSelectMapsByOperationId(JdbcAdminTransactionQueryService service,
                                                                              String logicalTable) {
        return (List<java.util.Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service,
                "selectMapsByOperationId",
                logicalTable,
                LocalDateTime.of(2026, 7, 21, 0, 0),
                LocalDateTime.of(2026, 7, 21, 23, 59),
                "OP202607211953260920709");
    }

    private String captureQuerySql(NamedParameterJdbcTemplate jdbcTemplate) {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), any(MapSqlParameterSource.class));
        return sqlCaptor.getValue();
    }
}
