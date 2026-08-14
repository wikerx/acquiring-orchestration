package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.core.exception.TransactionDataUnavailableException;
import com.scott.payment.admin.service.AdminRiskTimelineQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminTransactionQueryServiceTest
 * @date : 2026-07-21 20:20
 * @email : scott_x@163.com
 * @description : 管理后台交易 JDBC 查询服务单元测试，验证交易详情聚合日志查询按表结构生成 SQL，避免无软删字段日志表拼接 deleted 条件。
 * @status : create
 */
class JdbcAdminTransactionQueryServiceTest {

    /** 3DS 展示应优先读取支付方式快照，并用 Hosted Checkout 尝试补齐历史记录。 */
    @Test
    @SuppressWarnings("unchecked")
    void threeDsEnrichmentShouldUseSnapshotAndCheckoutFallbackInBatches() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class)
                        .contains("transaction_payment_method_info")
                        ? List.of("operation-snapshot")
                        : List.of("operation-checkout"));
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);

        Set<String> operationIds = (Set<String>) ReflectionTestUtils.invokeMethod(
                service,
                "findThreeDsOperationIds",
                List.of("operation-snapshot", "operation-checkout"));

        assertThat(operationIds).containsExactlyInAnyOrder("operation-snapshot", "operation-checkout");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeast(2)).queryForList(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(String.class));
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql)
                .contains("three_ds_indicator", "transaction_date_time >= :registeredNodeBegin"));
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql)
                .contains("payment_checkout_attempt", "three_ds_required = 1"));
    }

    @Test
    void pageOrdersShouldClampRequestedRangeToRegisteredNodesAndCurrentTime() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of("202603", "202604"));
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                mock(AdminRiskTimelineQueryService.class),
                new TransactionLogicalReadExecutor(),
                properties);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        TransactionPageQuery query = new TransactionPageQuery();
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
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                mock(AdminRiskTimelineQueryService.class),
                new TransactionLogicalReadExecutor(),
                properties);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setBeginTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 6, 30, 23, 59, 59));

        assertThat(service.pageOrders(query).getRecords()).isEmpty();

        verify(jdbcTemplate, never()).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }

    /** ShardingSphere 模式必须通过 transaction 逻辑数据源执行固定逻辑表 SQL。 */
    @Test
    void pageOrdersShouldUseTransactionLogicalTableWithoutPhysicalResolution() throws NoSuchMethodException {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                mock(AdminRiskTimelineQueryService.class));
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
                mock(AdminRiskTimelineQueryService.class),
                new TransactionLogicalReadExecutor(),
                properties);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setPageSize(500);

        service.pageOrders(query);

        assertThat(query.getPageSize()).isEqualTo(7);
    }

    /** 详情首查必须使用列表返回的毫秒分片时间精确定位动作单。 */
    @Test
    @SuppressWarnings("unchecked")
    void detailShouldBindExactShardingTimeInFirstQuery() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 7, 21, 19, 53, 50, 233_000_000);
        LocalDateTime rootTransactionTime = LocalDateTime.of(2026, 4, 10, 9, 15, 30);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate, mock(AdminRiskTimelineQueryService.class));

        assertThatThrownBy(() -> service.detail("transaction-a", transactionTime, rootTransactionTime))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains("FROM transaction_operation");
        assertThat(sqlCaptor.getValue()).contains("transaction_date_time = :transactionDateTime");
        assertThat(sqlCaptor.getValue()).doesNotContain("transactionDateTimeEnd");
        assertThat(paramsCaptor.getValue().getValue("transactionDateTime")).isEqualTo(transactionTime);
        assertThat(paramsCaptor.getValue().hasValue("transactionDateTimeEnd")).isFalse();
    }

    /** 人工重发资格查询必须同时限定真实分片时间、交易终态和通知可重发状态。 */
    @Test
    void callbackRetryEligibilityShouldUseExactShardAndTerminalStatus() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 4, 12, 30, 0, 125_000_000);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate, mock(AdminRiskTimelineQueryService.class));

        assertThat(service.existsRetryableTerminalMerchantNotification("transaction-a", transactionTime)).isTrue();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(), paramsCaptor.capture(), eq(Boolean.class));
        assertThat(sqlCaptor.getValue())
                .contains("FROM transaction_operation operation_record")
                .contains("JOIN transaction_merchant_notification notification")
                .contains("operation_record.transaction_date_time = :transactionDateTime")
                .contains("notification.transaction_date_time = operation_record.transaction_date_time")
                .contains("operation_record.transaction_status IN ('SUCCESS', 'FAILED')")
                .contains("notification.notify_status IN ('SUCCESS', 'FAILED', 'CLOSED')")
                .doesNotContain("${");
        assertThat(paramsCaptor.getValue().getValue("transactionDateTime")).isEqualTo(transactionTime);
    }

    /** 商户回调详情必须按通知号和页面传入的真实分片时间查询任务及每次投递日志。 */
    @Test
    void merchantNotificationDetailShouldUseExactShardAndReturnAttemptLogs() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 4, 18, 29, 44, 988_000_000);
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("notifyId", "notification-a");
        LocalDateTime firstResponseTime = transactionTime.plusSeconds(2);
        LocalDateTime secondResponseTime = transactionTime.plusSeconds(4);
        Map<String, Object> firstAttempt = new LinkedHashMap<>();
        firstAttempt.put("attemptNo", 1);
        firstAttempt.put("create_time", firstResponseTime);
        Map<String, Object> secondAttempt = new LinkedHashMap<>();
        secondAttempt.put("attemptNo", 2);
        secondAttempt.put("create_time", secondResponseTime);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class)
                        .contains("transaction_merchant_notification_log")
                        ? List.of(firstAttempt, secondAttempt)
                        : List.of(notification));
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate, mock(AdminRiskTimelineQueryService.class));

        Map<String, Object> detail = service.merchantNotificationDetail("notification-a", transactionTime);

        assertThat(detail.get("notification")).isEqualTo(notification);
        assertThat((List<Map<String, Object>>) detail.get("deliveryLogs"))
                .extracting(row -> row.get("attemptNo"), row -> row.get("httpMethod"), row -> row.get("responseTime"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "POST", firstResponseTime),
                        org.assertj.core.groups.Tuple.tuple(2, "POST", secondResponseTime));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        assertThat(sqlCaptor.getAllValues()).allSatisfy(sql -> {
            assertThat(sql).contains("notify_id = :notifyId");
            assertThat(sql).contains("transaction_date_time = :transactionDateTime");
            assertThat(sql).doesNotContain("${");
            assertThat(sql).doesNotContain("transaction_merchant_notification_2026");
        });
        assertThat(sqlCaptor.getAllValues().get(1)).contains("ORDER BY attempt_no ASC");
        assertThat(paramsCaptor.getAllValues()).allSatisfy(params -> {
            assertThat(params.getValue("notifyId")).isEqualTo("notification-a");
            assertThat(params.getValue("transactionDateTime")).isEqualTo(transactionTime);
        });
    }

    /** 详情附属日志触及未登记季度时必须明确失败，禁止伪装为空日志列表。 */
    @Test
    @SuppressWarnings("unchecked")
    void detailShouldPropagateMissingQuarterFromOptionalInteractionLog() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 7, 21, 19, 53, 50, 233_000_000);
        TransactionOperationResponse operation = operation("operation-a", "transaction-a", transactionTime);
        TransactionOrderResponse order = order("operation-a", transactionTime);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    if (sql.contains("FROM transaction_operation") && sql.contains("transaction_id = :transactionId")) {
                        return List.of(operation);
                    }
                    if (sql.contains("FROM transaction_order")) {
                        return List.of(order);
                    }
                    if (sql.contains("FROM transaction_operation")) {
                        return List.of(operation);
                    }
                    return Collections.emptyList();
                });
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    if (sql.contains("FROM transaction_merchant_api_interaction_log")) {
                        throw new TransactionDataUnavailableException(
                                "transaction_merchant_api_interaction_log", "2026-Q2", "test-001");
                    }
                    return Collections.emptyList();
                });
        AdminRiskTimelineQueryService riskTimelineQueryService = mock(AdminRiskTimelineQueryService.class);
        when(riskTimelineQueryService.findRiskEvents(anyString())).thenReturn(Collections.emptyList());
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate, riskTimelineQueryService);

        assertThatThrownBy(() -> service.detail("transaction-a", transactionTime, transactionTime))
                .isInstanceOf(TransactionDataUnavailableException.class)
                .hasMessageContaining("transaction_merchant_api_interaction_log");
    }

    @Test
    @SuppressWarnings("unchecked")
    void operationPageShouldBatchLoadLifecycleWithoutUnshardedOrderJoin() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime actionTime = LocalDateTime.of(2026, 7, 21, 19, 53, 50);
        LocalDateTime rootTime = LocalDateTime.of(2026, 4, 10, 9, 15, 30);
        TransactionOperationResponse firstOperation = operation("operation-a", "transaction-a", actionTime);
        TransactionOperationResponse secondOperation = operation("operation-b", "transaction-b", actionTime.plusSeconds(1));
        firstOperation.setAccessType("DIRECT_API");
        secondOperation.setAccessType("DIRECT_API");
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
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).contains("payment_checkout_attempt")
                        ? List.of("transaction-a")
                        : Collections.emptyList());
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setBeginTime(LocalDateTime.of(2026, 7, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 7, 31, 23, 59));

        List<TransactionOperationResponse> rows = service.pageOperations(query).getRecords();

        assertThat(rows)
                .extracting(TransactionOperationResponse::getRootTransactionDateTime)
                .containsExactly(rootTime, rootTime.plusSeconds(1));
        assertThat(rows)
                .extracting(TransactionOperationResponse::getAccessType)
                .containsExactly("HOSTED_CHECKOUT", "DIRECT_API");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeast(2)).query(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getAllValues()).noneMatch(sql -> sql.contains("LEFT JOIN transaction_order"));
        assertThat(sqlCaptor.getAllValues().stream().filter(sql -> sql.contains("FROM transaction_order")))
                .singleElement()
                .satisfies(sql -> {
                    assertThat(sql).contains("operation_id IN (:operationIds)");
                    assertThat(sql).contains("transaction_date_time >= :registeredNodeBegin");
                    assertThat(sql).contains("transaction_date_time < :registeredNodeEnd");
                });
    }

    /** 新收银台交易已落库来源时，应直接映射为 Hosted Checkout。 */
    @Test
    @SuppressWarnings("unchecked")
    void operationMapperShouldUsePersistedHostedCheckoutSource() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("request_source")).thenReturn("HOSTED_CHECKOUT");
        RowMapper<TransactionOperationResponse> mapper =
                (RowMapper<TransactionOperationResponse>) ReflectionTestUtils.invokeMethod(
                        service, "operationMapper", false);

        TransactionOperationResponse row = mapper.mapRow(resultSet, 0);

        assertThat(row.getAccessType()).isEqualTo("HOSTED_CHECKOUT");
    }

    /**
     * 状态历史表没有 deleted 字段，详情聚合查询不应拼接 deleted = 0。
     */
    @Test
    void selectMapsByOperationIdShouldSkipDeletedConditionWhenTableHasNoSoftDeleteColumn() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(Collections.emptyList());

        invokeSelectMapsByOperationId(service, "transaction_status_history");

        String sql = captureQuerySql(jdbcTemplate);
        assertThat(sql).contains("FROM transaction_status_history");
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
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(Collections.emptyList());

        invokeSelectMapsByOperationId(service, "transaction_channel_request");

        String sql = captureQuerySql(jdbcTemplate);
        assertThat(sql).contains("FROM transaction_channel_request");
        assertThat(sql).contains("WHERE deleted = 0");
        assertThat(sql).contains("AND operation_id = :operationId");
    }

    /**
     * JDBC Map 查询会返回数据库下划线字段名，详情响应必须转换成前端读取的驼峰字段名。
     */
    @Test
    void selectMapsByOperationIdShouldReturnCamelCaseKeysForFrontendDetail() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
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

    /**
     * JDBC 的 BIGINT 主键必须在 JSON 序列化前转为字符串，避免浏览器按 IEEE-754 number 解析后丢失尾数。
     */
    @Test
    void selectMapsByOperationIdShouldReturnNumericIdentifiersAsStrings() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        Map<String, Object> row = new LinkedHashMap<>();
        LocalDateTime responseTime = LocalDateTime.of(2026, 3, 1, 12, 0, 0, 123_000_000);
        row.put("id", 202603000000000825L);
        row.put("account_id", 42L);
        row.put("duration_millis", 123L);
        row.put("create_time", responseTime);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        List<Map<String, Object>> rows = invokeSelectMapsByOperationId(
                service, "transaction_merchant_notification_log");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("id", "202603000000000825");
        assertThat(rows.get(0)).containsEntry("accountId", "42");
        assertThat(rows.get(0)).containsEntry("durationMillis", 123L);
        assertThat(rows.get(0)).containsEntry("httpMethod", "POST");
        assertThat(rows.get(0)).containsEntry("responseTime", responseTime);
    }

    /** 管理端通知详情只能返回脱敏 URL，完整通知配置快照不得进入查询结果或 JSON 响应。 */
    @Test
    void merchantNotificationDetailShouldExcludeSensitiveConfigSnapshot() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("notify_id", "notify-a");
        row.put("notify_config_snapshot_json", "{\"callbackUrl\":\"https://merchant.example/callback?token=secret\"}");
        row.put("target_url_masked", "https://merchant.example/callback?***");
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        List<Map<String, Object>> rows = invokeSelectMapsByOperationId(
                service, "transaction_merchant_notification");

        String sql = captureQuerySql(jdbcTemplate);
        assertThat(sql).contains("target_url_masked");
        assertThat(sql).doesNotContain("notify_config_snapshot_json");
        assertThat(rows).singleElement().satisfies(result -> {
            assertThat(result).containsEntry("targetUrlMasked", "https://merchant.example/callback?***");
            assertThat(result).doesNotContainKey("notifyConfigSnapshotJson");
        });
    }

    private JdbcAdminTransactionQueryService buildService(NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                mock(AdminRiskTimelineQueryService.class));
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

    private TransactionOperationResponse operation(String operationId,
                                                   String transactionId,
                                                   LocalDateTime transactionDateTime) {
        TransactionOperationResponse operation = new TransactionOperationResponse();
        operation.setOperationId(operationId);
        operation.setTransactionId(transactionId);
        operation.setTransactionDateTime(transactionDateTime);
        return operation;
    }

    private TransactionOrderResponse order(String operationId, LocalDateTime transactionDateTime) {
        TransactionOrderResponse order = new TransactionOrderResponse();
        order.setOperationId(operationId);
        order.setTransactionDateTime(transactionDateTime);
        return order;
    }
}
