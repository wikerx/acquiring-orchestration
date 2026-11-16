package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.config.AdminOperationalWorkItemProperties;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.ActionRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SyncRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.sharding.RiskAuditLogicalReadExecutor;
import com.scott.payment.component.db.sharding.RiskAuditShardingProperties;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 统一运营待办 JDBC 投影测试。
 *
 * <p>使用真实 H2 ResultSet 验证来源表字段与统一投影列标签的契约，避免静态源码断言遗漏
 * JDBC 驱动对列标签的实际解析行为。</p>
 */
class JdbcAdminOperationalWorkItemServiceTest {

    /**
     * 商户通知的 fail_reason 必须通过统一 failure_reason 列标签进入待办摘要。
     */
    @Test
    void shouldReadNotificationFailureReasonThroughStableProjectionAlias() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareSchema(jdbc);
        prepareNotificationWorkItem(jdbc);
        JdbcAdminOperationalWorkItemService service = new JdbcAdminOperationalWorkItemService(
                new NamedParameterJdbcTemplate(dataSource),
                new TransactionLogicalReadExecutor(),
                new RiskAuditLogicalReadExecutor(),
                new RiskAuditShardingProperties(),
                new AdminOperationalWorkItemProperties(),
                new DataSourceTransactionManager(dataSource));
        SearchRequest request = new SearchRequest();
        request.setPageNo(1);
        request.setPageSize(20);

        var response = service.search(request, AdminMerchantDataScope.all(), 1001L);

        assertThat(response.getPage().getTotal()).isEqualTo(1);
        assertThat(response.getPage().getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getSourceAvailable()).isTrue();
            assertThat(row.getSourceType()).isEqualTo("MERCHANT_NOTIFICATION_FAILURE");
            assertThat(row.getFailureReason()).isEqualTo("Gateway timeout");
            assertThat(row.getAttemptCount()).isEqualTo(5);
        });
    }

    /**
     * 活动来源同步扫描必须使用与待办摘要一致的 failure_reason 列标签。
     */
    @Test
    void shouldReadNotificationFailureReasonWhileScanningActiveSources() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareSchema(jdbc);
        prepareNotificationWorkItem(jdbc);
        JdbcAdminOperationalWorkItemService service = new JdbcAdminOperationalWorkItemService(
                new NamedParameterJdbcTemplate(dataSource),
                new TransactionLogicalReadExecutor(),
                new RiskAuditLogicalReadExecutor(),
                new RiskAuditShardingProperties(),
                new AdminOperationalWorkItemProperties(),
                new DataSourceTransactionManager(dataSource));
        Map<Object, Object> activeSources = new LinkedHashMap<>();

        Method loadMethod = JdbcAdminOperationalWorkItemService.class
                .getDeclaredMethod("loadBoundedActiveNotifications", Map.class);
        loadMethod.setAccessible(true);
        loadMethod.invoke(service, activeSources);

        assertThat(activeSources).hasSize(1);
        Object snapshot = activeSources.values().iterator().next();
        Method failureReasonAccessor = snapshot.getClass().getDeclaredMethod("failureReason");
        failureReasonAccessor.setAccessible(true);
        assertThat(failureReasonAccessor.invoke(snapshot)).isEqualTo("Gateway timeout");
    }

    /**
     * 人工完成必须落终态、完成原因和审计轨迹，处理结论不得丢失。
     */
    @Test
    void shouldCompleteActiveWorkItemWithAuditedResolution() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareSchema(jdbc);
        prepareNotificationWorkItem(jdbc);
        JdbcAdminOperationalWorkItemService service = new JdbcAdminOperationalWorkItemService(
                new NamedParameterJdbcTemplate(dataSource),
                new TransactionLogicalReadExecutor(),
                new RiskAuditLogicalReadExecutor(),
                new RiskAuditShardingProperties(),
                new AdminOperationalWorkItemProperties(),
                new DataSourceTransactionManager(dataSource));
        ActionRequest request = new ActionRequest();
        request.setRequestId("REQ-COMPLETE-001");
        request.setExpectedVersion(0L);
        request.setRemark("已核对通知失败原因并完成后续处置");

        var detail = service.complete("OWI20261105000000000001", request,
                1001L, "Scott", AdminMerchantDataScope.all());

        assertThat(detail.getWorkItem().getWorkStatus()).isEqualTo("CLOSED");
        assertThat(detail.getWorkItem().getCloseReason()).isEqualTo("MANUAL_COMPLETE");
        assertThat(detail.getWorkItem().getClosedAt()).isNotNull();
        assertThat(detail.getWorkItem().getLatestRemark()).isEqualTo("已核对通知失败原因并完成后续处置");
        assertThat(detail.getWorkItem().getVersion()).isEqualTo(1L);
        assertThat(detail.getHistory()).singleElement().satisfies(history -> {
            assertThat(history.getActionType()).isEqualTo("COMPLETE");
            assertThat(history.getBeforeStatus()).isEqualTo("OPEN");
            assertThat(history.getAfterStatus()).isEqualTo("CLOSED");
            assertThat(history.getOperatorAccountId()).isEqualTo(1001L);
            assertThat(history.getRemark()).isEqualTo("已核对通知失败原因并完成后续处置");
        });
    }

    /**
     * 渠道异常必须投影来源负责人和状态，且只能在来源案件流程中完成处置。
     */
    @Test
    void shouldSynchronizeSourceManagedChannelAbnormalityAndAutoCloseTerminalCase() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareSchema(jdbc);
        prepareChannelAbnormality(jdbc);
        AdminOperationalWorkItemProperties properties = new AdminOperationalWorkItemProperties();
        JdbcAdminOperationalWorkItemService service = new JdbcAdminOperationalWorkItemService(
                new NamedParameterJdbcTemplate(dataSource),
                new TransactionLogicalReadExecutor(),
                new RiskAuditLogicalReadExecutor(),
                new RiskAuditShardingProperties(),
                properties,
                new DataSourceTransactionManager(dataSource));
        SyncRequest firstSync = syncRequest("SYNC-CHANNEL-001", LocalDateTime.of(2026, 11, 5, 10, 5));

        var firstResult = service.synchronize(firstSync);
        SearchRequest search = new SearchRequest();
        search.setSourceType("CHANNEL_MATCH_ABNORMAL");
        search.setPageNo(1);
        search.setPageSize(20);
        var active = service.search(search, AdminMerchantDataScope.all(), 1001L);

        assertThat(firstResult.getCreatedCount()).isEqualTo(1);
        assertThat(active.getPage().getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getSourceManaged()).isTrue();
            assertThat(row.getWorkStatus()).isEqualTo("OPEN");
            assertThat(row.getOwnerAccountId()).isNull();
            assertThat(row.getOwnerAccountName()).isNull();
            assertThat(row.getPriority()).isEqualTo("HIGH");
            assertThat(row.getSlaDueAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 10, 0));
            assertThat(row.getTransactionDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 5, 9, 0));
        });

        String workItemNo = active.getPage().getRecords().get(0).getWorkItemNo();
        jdbc.update("""
                UPDATE transaction_abnormal_event
                SET event_status = 'PROCESSING',
                    assigned_to_id = 'admin-account:1001',
                    assigned_to_name = 'Scott'
                WHERE abnormal_event_id = 'ABN202611050001'
                """);
        service.synchronize(syncRequest("SYNC-CHANNEL-002", LocalDateTime.of(2026, 11, 5, 10, 6)));
        var processing = service.detail(workItemNo, AdminMerchantDataScope.all());
        assertThat(processing.getWorkItem().getWorkStatus()).isEqualTo("PROCESSING");
        assertThat(processing.getWorkItem().getOwnerAccountId()).isEqualTo(1001L);
        assertThat(processing.getWorkItem().getOwnerAccountName()).isEqualTo("Scott");
        assertThat(processing.getWorkItem().getVersion()).isEqualTo(1L);
        assertThat(processing.getHistory()).extracting(history -> history.getActionType())
                .containsExactly("SOURCE_SYNC", "CREATE");

        ActionRequest complete = new ActionRequest();
        complete.setRequestId("REQ-CHANNEL-COMPLETE-001");
        complete.setExpectedVersion(1L);
        complete.setRemark("不得绕过来源案件状态机");
        assertThatThrownBy(() -> service.complete(workItemNo, complete,
                1001L, "Scott", AdminMerchantDataScope.all()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("source-managed work item");

        jdbc.update("""
                UPDATE transaction_abnormal_event
                SET event_status = 'RESOLVED'
                WHERE abnormal_event_id = 'ABN202611050001'
                """);
        service.synchronize(syncRequest("SYNC-CHANNEL-003", LocalDateTime.of(2026, 11, 5, 10, 7)));
        var closed = service.search(search, AdminMerchantDataScope.all(), 1001L);

        assertThat(closed.getPage().getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getWorkStatus()).isEqualTo("CLOSED");
            assertThat(row.getCloseReason()).isEqualTo("SOURCE_TERMINAL_OR_REMOVED");
            assertThat(row.getSourceStatus()).isEqualTo("RESOLVED");
        });
    }

    /**
     * 清分待办只能纳入需要人工介入的状态，正常延时重试不得误报为运营积压。
     */
    @Test
    void shouldSynchronizeOnlyClearingBacklogsThatRequireManualIntervention() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareSchema(jdbc);
        prepareClearingBacklogs(jdbc);
        JdbcAdminOperationalWorkItemService service = new JdbcAdminOperationalWorkItemService(
                new NamedParameterJdbcTemplate(dataSource),
                new TransactionLogicalReadExecutor(),
                new RiskAuditLogicalReadExecutor(),
                new RiskAuditShardingProperties(),
                new AdminOperationalWorkItemProperties(),
                new DataSourceTransactionManager(dataSource));

        var firstResult = service.synchronize(
                syncRequest("SYNC-CLEARING-001", LocalDateTime.of(2026, 11, 5, 10, 0)));
        SearchRequest search = new SearchRequest();
        search.setSourceType("CLEARING_BACKLOG");
        search.setPageNo(1);
        search.setPageSize(20);
        var active = service.search(search, AdminMerchantDataScope.all(), 1001L);

        assertThat(firstResult.getScannedSourceCount()).isEqualTo(2);
        assertThat(firstResult.getCreatedCount()).isEqualTo(2);
        assertThat(active.getPage().getRecords()).hasSize(2)
                .extracting(row -> row.getSourceKey())
                .containsExactlyInAnyOrder("FIN-MANUAL-001", "FIN-FAILED-001")
                .doesNotContain("FIN-WAITING-RETRY-001");
        assertThat(active.getPage().getRecords())
                .filteredOn(row -> "FIN-MANUAL-001".equals(row.getSourceKey()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getSourceManaged()).isTrue();
                    assertThat(row.getWorkStatus()).isEqualTo("OPEN");
                    assertThat(row.getSourceStatus()).isEqualTo("MANUAL_REVIEW");
                    assertThat(row.getPriority()).isEqualTo("HIGH");
                    assertThat(row.getOwnerAccountId()).isNull();
                    assertThat(row.getTransactionId()).isEqualTo("TXN-CLEARING-MANUAL-001");
                    assertThat(row.getTransactionDateTime())
                            .isEqualTo(LocalDateTime.of(2026, 11, 5, 8, 30));
                    assertThat(row.getAmount()).isEqualTo("125.500000");
                    assertThat(row.getCurrency()).isEqualTo("USD");
                    assertThat(row.getCurrencyExponent()).isEqualTo(2);
                    assertThat(row.getAttemptCount()).isEqualTo(3);
                    assertThat(row.getFailureReason()).isEqualTo("Fee configuration conflict");
                    assertThat(row.getSlaDueAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 10, 0));
                });

        String workItemNo = active.getPage().getRecords().stream()
                .filter(row -> "FIN-MANUAL-001".equals(row.getSourceKey()))
                .findFirst().orElseThrow().getWorkItemNo();
        ActionRequest complete = new ActionRequest();
        complete.setRequestId("REQ-CLEARING-COMPLETE-001");
        complete.setExpectedVersion(0L);
        complete.setRemark("不得绕过清分状态机");
        assertThatThrownBy(() -> service.complete(workItemNo, complete,
                1001L, "Scott", AdminMerchantDataScope.all()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("source-managed work item");

        jdbc.update("""
                UPDATE transaction_finance_state
                SET clearing_status = 'CLEARED', update_time = TIMESTAMP '2026-11-05 10:05:00'
                WHERE finance_state_id IN ('FIN-MANUAL-001', 'FIN-FAILED-001')
                """);
        service.synchronize(syncRequest("SYNC-CLEARING-002", LocalDateTime.of(2026, 11, 5, 10, 6)));
        var closed = service.search(search, AdminMerchantDataScope.all(), 1001L);

        assertThat(closed.getPage().getRecords()).allSatisfy(row -> {
            assertThat(row.getWorkStatus()).isEqualTo("CLOSED");
            assertThat(row.getCloseReason()).isEqualTo("SOURCE_TERMINAL_OR_REMOVED");
            assertThat(row.getSourceStatus()).isEqualTo("CLEARED");
        });
    }

    /**
     * 结算待办只纳入状态机已升级为人工复核的批次，自动重试批次必须继续留在调度器中处理。
     */
    @Test
    void shouldSynchronizeOnlySettlementBatchesEscalatedToManualReview() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareSchema(jdbc);
        prepareSettlementBacklogs(jdbc);
        JdbcAdminOperationalWorkItemService service = new JdbcAdminOperationalWorkItemService(
                new NamedParameterJdbcTemplate(dataSource),
                new TransactionLogicalReadExecutor(),
                new RiskAuditLogicalReadExecutor(),
                new RiskAuditShardingProperties(),
                new AdminOperationalWorkItemProperties(),
                new DataSourceTransactionManager(dataSource));

        var firstResult = service.synchronize(
                syncRequest("SYNC-SETTLEMENT-BACKLOG-001", LocalDateTime.of(2026, 11, 5, 10, 0)));
        SearchRequest search = new SearchRequest();
        search.setSourceType("SETTLEMENT_BACKLOG");
        search.setPageNo(1);
        search.setPageSize(20);
        var active = service.search(search, AdminMerchantDataScope.all(), 1001L);

        assertThat(firstResult.getScannedSourceCount()).isEqualTo(1);
        assertThat(firstResult.getCreatedCount()).isEqualTo(1);
        assertThat(active.getPage().getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getSourceKey()).isEqualTo("SB20261105-00000001");
            assertThat(row.getSourceManaged()).isTrue();
            assertThat(row.getWorkStatus()).isEqualTo("OPEN");
            assertThat(row.getSourceStatus()).isEqualTo("MANUAL_REVIEW");
            assertThat(row.getPriority()).isEqualTo("HIGH");
            assertThat(row.getOwnerAccountId()).isNull();
            assertThat(row.getBusinessNo()).isEqualTo("SB20261105-00000001");
            assertThat(row.getRelatedBusinessNo()).isEqualTo("SRO20261105-00000001");
            assertThat(row.getCurrency()).isEqualTo("USD");
            assertThat(row.getAmount()).isEqualTo("98.750000");
            assertThat(row.getCurrencyExponent()).isEqualTo(2);
            assertThat(row.getAttemptCount()).isEqualTo(8);
            assertThat(row.getFailureStage()).isEqualTo("RATE_LOCKING");
            assertThat(row.getFailureCode()).isEqualTo("SETTLEMENT_RETRY_EXHAUSTED");
            assertThat(row.getFailureReason()).isEqualTo("Settlement rate provider remained unavailable");
            assertThat(row.getSourceOccurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 9, 15));
            assertThat(row.getSlaDueAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 10, 15));
        });

        String workItemNo = active.getPage().getRecords().get(0).getWorkItemNo();
        ActionRequest complete = new ActionRequest();
        complete.setRequestId("REQ-SETTLEMENT-BACKLOG-COMPLETE-001");
        complete.setExpectedVersion(0L);
        complete.setRemark("不得绕过结算状态机");
        assertThatThrownBy(() -> service.complete(workItemNo, complete,
                1001L, "Scott", AdminMerchantDataScope.all()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("source-managed work item");

        jdbc.update("""
                UPDATE settlement_batch
                SET batch_status = 'CLAIMED', update_time = TIMESTAMP '2026-11-05 10:05:00'
                WHERE settlement_batch_no = 'SB20261105-00000001'
                """);
        service.synchronize(syncRequest(
                "SYNC-SETTLEMENT-BACKLOG-002", LocalDateTime.of(2026, 11, 5, 10, 6)));
        var closed = service.search(search, AdminMerchantDataScope.all(), 1001L);

        assertThat(closed.getPage().getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getWorkStatus()).isEqualTo("CLOSED");
            assertThat(row.getCloseReason()).isEqualTo("SOURCE_TERMINAL_OR_REMOVED");
            assertThat(row.getSourceStatus()).isEqualTo("CLAIMED");
        });
    }

    /**
     * 高风险待办只纳入拒绝或 HIGH/CRITICAL 评估，并在人工完成后保持关闭。
     */
    @Test
    void shouldSynchronizeHighRiskEventsAndPreserveManualCompletion() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareSchema(jdbc);
        prepareHighRiskEvents(jdbc);
        JdbcAdminOperationalWorkItemService service = new JdbcAdminOperationalWorkItemService(
                new NamedParameterJdbcTemplate(dataSource),
                new TransactionLogicalReadExecutor(),
                new RiskAuditLogicalReadExecutor(),
                new RiskAuditShardingProperties(),
                new AdminOperationalWorkItemProperties(),
                new DataSourceTransactionManager(dataSource));

        var firstResult = service.synchronize(
                syncRequest("SYNC-HIGH-RISK-001", LocalDateTime.of(2026, 11, 5, 10, 0)));
        SearchRequest search = new SearchRequest();
        search.setSourceType("HIGH_RISK_EVENT");
        search.setPageNo(1);
        search.setPageSize(20);
        var active = service.search(search, AdminMerchantDataScope.all(), 1001L);

        assertThat(firstResult.getScannedSourceCount()).isEqualTo(2);
        assertThat(firstResult.getCreatedCount()).isEqualTo(2);
        assertThat(active.getPage().getRecords()).hasSize(2);
        assertThat(active.getPage().getRecords())
                .filteredOn(row -> "RISK-CRITICAL-001".equals(row.getSourceKey()))
                .singleElement().satisfies(row -> {
                    assertThat(row.getSourceManaged()).isFalse();
                    assertThat(row.getSourceStatus()).isEqualTo("PASS");
                    assertThat(row.getPriority()).isEqualTo("CRITICAL");
                    assertThat(row.getBusinessNo()).isEqualTo("RISK-CRITICAL-001");
                    assertThat(row.getRelatedBusinessNo()).isEqualTo("PAY-CRITICAL-001");
                    assertThat(row.getTransactionId()).isEqualTo("PAY-CRITICAL-001");
                    assertThat(row.getAlertLevel()).isEqualTo("CRITICAL");
                    assertThat(row.getAttemptCount()).isEqualTo(3);
                    assertThat(row.getFailureReason()).isEqualTo("Critical velocity anomaly");
                    assertThat(row.getAmount()).isEqualTo("900.000000");
                    assertThat(row.getCurrency()).isEqualTo("USD");
                    assertThat(row.getSlaDueAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 9, 30));
                });

        var rejected = active.getPage().getRecords().stream()
                .filter(row -> "RISK-REJECT-001".equals(row.getSourceKey()))
                .findFirst().orElseThrow();
        assertThat(rejected.getPriority()).isEqualTo("HIGH");
        assertThat(rejected.getSlaDueAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 11, 10));
        ActionRequest complete = new ActionRequest();
        complete.setRequestId("REQ-HIGH-RISK-COMPLETE-001");
        complete.setExpectedVersion(0L);
        complete.setRemark("已核查拒绝原因，无需调整风控结论");
        service.complete(rejected.getWorkItemNo(), complete,
                1001L, "Scott", AdminMerchantDataScope.all());

        service.synchronize(syncRequest("SYNC-HIGH-RISK-002", LocalDateTime.of(2026, 11, 5, 10, 5)));
        var afterResync = service.detail(rejected.getWorkItemNo(), AdminMerchantDataScope.all());
        assertThat(afterResync.getWorkItem().getWorkStatus()).isEqualTo("CLOSED");
        assertThat(afterResync.getWorkItem().getCloseReason()).isEqualTo("MANUAL_COMPLETE");
        assertThat(afterResync.getWorkItem().getLatestRemark()).isEqualTo("已核查拒绝原因，无需调整风控结论");
    }

    /**
     * 列表必须由数据库稳定分页，汇总指标不得被当前页截断。
     */
    @Test
    void shouldPageWorkItemsInDatabaseWithStableOrderAndFullMetrics() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareSchema(jdbc);
        prepareNotificationRows(jdbc, 25, "200045", "ORDER-");
        JdbcAdminOperationalWorkItemService service = service(dataSource);
        SearchRequest firstRequest = searchRequest(1, 10);
        SearchRequest secondRequest = searchRequest(2, 10);

        var first = service.search(firstRequest, AdminMerchantDataScope.all(), 1001L);
        var second = service.search(secondRequest, AdminMerchantDataScope.all(), 1001L);

        assertThat(first.getPage().getTotal()).isEqualTo(25);
        assertThat(first.getPage().getPages()).isEqualTo(3);
        assertThat(first.getPage().getRecords()).hasSize(10);
        assertThat(second.getPage().getRecords()).hasSize(10);
        assertThat(first.getPage().getRecords()).extracting(row -> row.getSourceKey())
                .containsExactly("NOTIFY-001", "NOTIFY-002", "NOTIFY-003", "NOTIFY-004", "NOTIFY-005",
                        "NOTIFY-006", "NOTIFY-007", "NOTIFY-008", "NOTIFY-009", "NOTIFY-010");
        assertThat(second.getPage().getRecords()).extracting(row -> row.getSourceKey())
                .containsExactly("NOTIFY-011", "NOTIFY-012", "NOTIFY-013", "NOTIFY-014", "NOTIFY-015",
                        "NOTIFY-016", "NOTIFY-017", "NOTIFY-018", "NOTIFY-019", "NOTIFY-020");
        assertThat(first.getMetrics().getOpenCount()).isEqualTo(25);
        assertThat(first.getMetrics().getUnassignedCount()).isEqualTo(25);
    }

    /**
     * 关键字中的 SQL 通配符必须按普通字符匹配，避免扩大查询范围。
     */
    @Test
    void shouldEscapeKeywordWildcards() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareSchema(jdbc);
        prepareNotificationRows(jdbc, 1, "200045", "ORDER-%-");
        prepareNotificationRows(jdbc, 1, "200046", "ORDER-A-");
        SearchRequest request = searchRequest(1, 20);
        request.setKeyword("%");

        var response = service(dataSource).search(request, AdminMerchantDataScope.all(), 1001L);

        assertThat(response.getPage().getRecords()).singleElement()
                .satisfies(row -> assertThat(row.getRelatedBusinessNo()).contains("%"));
    }

    /**
     * 有限商户范围必须同时校验搜索投影和当前来源归属，来源归属变化时拒绝泄露。
     */
    @Test
    void shouldFailClosedWhenCurrentSourceOwnershipDiffersFromProjection() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareSchema(jdbc);
        prepareNotificationRows(jdbc, 1, "200045", "ORDER-");
        jdbc.update("""
                UPDATE transaction_merchant_notification
                SET merchant_id = '200046'
                WHERE notify_id = 'NOTIFY-001'
                """);

        var response = service(dataSource).search(searchRequest(1, 20),
                AdminMerchantDataScope.limited(Set.of("200045")), 1001L);

        assertThat(response.getPage().getTotal()).isEqualTo(1);
        assertThat(response.getPage().getRecords()).isEmpty();
    }

    /**
     * 处理轨迹必须按最新优先稳定分页，并在详情兼容窗口中返回总数。
     */
    @Test
    void shouldPageHistoryNewestFirstAndExposeTotal() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareSchema(jdbc);
        prepareNotificationWorkItem(jdbc);
        for (int index = 1; index <= 25; index++) {
            jdbc.update("""
                    INSERT INTO admin_operational_work_item_history (
                        work_item_id, work_item_no, request_id, action_type,
                        before_status, after_status, operator_account_name,
                        remark, operated_at, create_time
                    ) VALUES (1, 'OWI20261105000000000001', ?, 'REMARK',
                              'OPEN', 'OPEN', 'Tester', ?, ?, ?)
                    """, "HISTORY-" + index, "remark-" + index,
                    LocalDateTime.of(2026, 11, 5, 10, 0).plusMinutes(index),
                    LocalDateTime.of(2026, 11, 5, 10, 0).plusMinutes(index));
        }
        JdbcAdminOperationalWorkItemService service = service(dataSource);

        var detail = service.detail("OWI20261105000000000001", AdminMerchantDataScope.all());
        var secondPage = service.searchHistory("OWI20261105000000000001", 2, 10,
                AdminMerchantDataScope.all());

        assertThat(detail.getHistoryTotal()).isEqualTo(25);
        assertThat(detail.getHistory()).hasSize(10);
        assertThat(detail.getHistory()).extracting(history -> history.getRemark())
                .containsExactly("remark-25", "remark-24", "remark-23", "remark-22", "remark-21",
                        "remark-20", "remark-19", "remark-18", "remark-17", "remark-16");
        assertThat(secondPage.getTotal()).isEqualTo(25);
        assertThat(secondPage.getRecords()).extracting(history -> history.getRemark())
                .containsExactly("remark-15", "remark-14", "remark-13", "remark-12", "remark-11",
                        "remark-10", "remark-9", "remark-8", "remark-7", "remark-6");
        assertThatThrownBy(() -> service.searchHistory(
                "OWI20261105000000000001", 1, 101, AdminMerchantDataScope.all()))
                .isInstanceOf(ServiceException.class);
    }

    private JdbcAdminOperationalWorkItemService service(JdbcDataSource dataSource) {
        return new JdbcAdminOperationalWorkItemService(
                new NamedParameterJdbcTemplate(dataSource),
                new TransactionLogicalReadExecutor(),
                new RiskAuditLogicalReadExecutor(),
                new RiskAuditShardingProperties(),
                new AdminOperationalWorkItemProperties(),
                new DataSourceTransactionManager(dataSource));
    }

    private SearchRequest searchRequest(int pageNo, int pageSize) {
        SearchRequest request = new SearchRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        return request;
    }

    private void prepareNotificationRows(JdbcTemplate jdbc,
                                         int count,
                                         String merchantId,
                                         String businessPrefix) {
        jdbc.update("""
                MERGE INTO base_merchant_info (merchant_id, merchant_name, deleted)
                KEY (merchant_id) VALUES (?, ?, 0)
                """, merchantId, "Merchant " + merchantId);
        int existing = jdbc.queryForObject(
                "SELECT COUNT(1) FROM admin_operational_work_item", Integer.class);
        for (int offset = 1; offset <= count; offset++) {
            int index = existing + offset;
            String suffix = String.format("%03d", index);
            LocalDateTime occurredAt = LocalDateTime.of(2026, 11, 5, 8, 0).plusMinutes(index);
            jdbc.update("""
                    INSERT INTO admin_operational_work_item (
                        work_item_no, source_type, source_key, work_status, priority,
                        sla_due_at, first_detected_at, last_synced_at, version,
                        source_projection_version, merchant_id, merchant_name, business_no,
                        related_business_no, transaction_id, transaction_date_time, source_occurred_at,
                        create_time, update_time
                    ) VALUES (?, 'MERCHANT_NOTIFICATION_FAILURE', ?, 'OPEN', 'HIGH',
                              ?, ?, ?, 0, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, "OWI-PAGE-" + suffix, "NOTIFY-" + suffix,
                    occurredAt.plusHours(1), occurredAt, occurredAt,
                    merchantId, "Merchant " + merchantId, businessPrefix + suffix,
                    businessPrefix + suffix, "TXN-" + suffix, occurredAt, occurredAt,
                    occurredAt, occurredAt);
            jdbc.update("""
                    INSERT INTO transaction_merchant_notification (
                        notify_id, notify_status, merchant_id, merchant_order_no, transaction_id,
                        transaction_date_time, create_time, last_attempt_no, fail_reason,
                        next_retry_time, deleted
                    ) VALUES (?, 'FAILED', ?, ?, ?, ?, ?, 3, 'Timeout', NULL, 0)
                    """, "NOTIFY-" + suffix, merchantId, businessPrefix + suffix,
                    "TXN-" + suffix, occurredAt, occurredAt);
        }
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:admin_operational_work_item_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private void prepareSchema(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE admin_operational_work_item (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    work_item_no VARCHAR(32) NOT NULL,
                    source_type VARCHAR(48) NOT NULL,
                    source_key VARCHAR(128) NOT NULL,
                    work_status VARCHAR(16) NOT NULL,
                    priority VARCHAR(16) NOT NULL,
                    owner_account_id BIGINT,
                    owner_account_name VARCHAR(128),
                    sla_due_at TIMESTAMP NOT NULL,
                    first_detected_at TIMESTAMP NOT NULL,
                    last_synced_at TIMESTAMP NOT NULL,
                    closed_at TIMESTAMP,
                    close_reason VARCHAR(256),
                    latest_remark VARCHAR(1000),
                    version BIGINT NOT NULL,
                    source_projection_version INT NOT NULL DEFAULT 0,
                    merchant_id VARCHAR(64),
                    merchant_name VARCHAR(128),
                    business_no VARCHAR(128),
                    related_business_no VARCHAR(128),
                    transaction_id VARCHAR(64),
                    transaction_date_time TIMESTAMP,
                    source_occurred_at TIMESTAMP,
                    create_time TIMESTAMP NOT NULL,
                    update_time TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX uk_admin_operational_work_item_no
                ON admin_operational_work_item(work_item_no)
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX uk_admin_operational_work_item_source
                ON admin_operational_work_item(source_type, source_key)
                """);
        jdbc.execute("""
                CREATE TABLE transaction_merchant_notification (
                    id BIGINT AUTO_INCREMENT,
                    notify_id VARCHAR(64) PRIMARY KEY,
                    notify_status VARCHAR(32) NOT NULL,
                    merchant_id VARCHAR(64) NOT NULL,
                    merchant_order_no VARCHAR(128) NOT NULL,
                    transaction_id VARCHAR(64) NOT NULL,
                    transaction_date_time TIMESTAMP NOT NULL,
                    create_time TIMESTAMP NOT NULL,
                    last_attempt_no INT NOT NULL,
                    fail_reason VARCHAR(512),
                    next_retry_time TIMESTAMP,
                    deleted TINYINT NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE admin_operational_work_item_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    work_item_id BIGINT NOT NULL,
                    work_item_no VARCHAR(32) NOT NULL,
                    request_id VARCHAR(128) NOT NULL UNIQUE,
                    action_type VARCHAR(24) NOT NULL,
                    before_status VARCHAR(16),
                    after_status VARCHAR(16) NOT NULL,
                    before_owner_account_id BIGINT,
                    before_owner_account_name VARCHAR(128),
                    after_owner_account_id BIGINT,
                    after_owner_account_name VARCHAR(128),
                    operator_account_id BIGINT,
                    operator_account_name VARCHAR(128) NOT NULL,
                    remark VARCHAR(1000),
                    operated_at TIMESTAMP NOT NULL,
                    create_time TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE base_merchant_info (
                    merchant_id VARCHAR(64) PRIMARY KEY,
                    merchant_name VARCHAR(128) NOT NULL,
                    deleted TINYINT NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE transaction_refund_approval (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    approval_id VARCHAR(64), approval_status VARCHAR(32), merchant_id VARCHAR(64),
                    refund_transaction_id VARCHAR(64), source_transaction_id VARCHAR(64),
                    refund_transaction_date_time TIMESTAMP, create_time TIMESTAMP, expire_time TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE settlement_review_order (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    review_order_no VARCHAR(64), review_status VARCHAR(32), merchant_id VARCHAR(64),
                    settlement_batch_no VARCHAR(64), target_currency VARCHAR(3),
                    net_amount DECIMAL(36, 18), target_currency_exponent INT, submitted_time TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE exchange_rate_alert_event (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    event_no VARCHAR(64), event_status VARCHAR(32), rule_code VARCHAR(64),
                    first_trigger_time TIMESTAMP, rate_type VARCHAR(32), base_currency VARCHAR(3),
                    quote_currency VARCHAR(3), alert_level VARCHAR(16), deleted TINYINT
                )
                """);
        jdbc.execute("""
                CREATE TABLE transaction_abnormal_event (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    abnormal_event_id VARCHAR(64), event_status VARCHAR(32), merchant_id VARCHAR(64),
                    merchant_order_no VARCHAR(128), transaction_id VARCHAR(64),
                    transaction_date_time TIMESTAMP, platform_currency VARCHAR(3),
                    platform_amount DECIMAL(36, 18), currency_exponent INT,
                    first_seen_time TIMESTAMP, abnormal_level VARCHAR(16), occurrence_count INT,
                    abnormal_description VARCHAR(512), assigned_to_id VARCHAR(128),
                    assigned_to_name VARCHAR(128), deleted TINYINT
                )
                """);
        jdbc.execute("""
                CREATE TABLE transaction_operation (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    operation_id VARCHAR(64), transaction_id VARCHAR(64),
                    merchant_id VARCHAR(64), label_currency VARCHAR(3),
                    label_amount DECIMAL(20, 6), currency_exponent INT,
                    transaction_date_time TIMESTAMP, deleted TINYINT
                )
                """);
        jdbc.execute("""
                CREATE TABLE transaction_finance_state (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    finance_state_id VARCHAR(64), transaction_id VARCHAR(64),
                    operation_id VARCHAR(64), merchant_id VARCHAR(64),
                    label_currency VARCHAR(3), clearing_status VARCHAR(32),
                    clearing_retry_count INT, next_retry_time TIMESTAMP,
                    last_failure_code VARCHAR(64), last_failure_message VARCHAR(512),
                    transaction_date_time TIMESTAMP, update_time TIMESTAMP,
                    deleted TINYINT
                )
                """);
        jdbc.execute("""
                CREATE TABLE settlement_batch (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    settlement_batch_no VARCHAR(64), merchant_id VARCHAR(64),
                    target_currency VARCHAR(3), target_currency_exponent INT,
                    batch_type VARCHAR(32), original_batch_no VARCHAR(64),
                    review_order_no VARCHAR(64), batch_status VARCHAR(32),
                    retry_count INT, last_failure_stage VARCHAR(64),
                    last_failure_code VARCHAR(128), last_failure_message VARCHAR(512),
                    update_time TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE settlement_result_item (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    settlement_batch_no VARCHAR(64), result_role VARCHAR(32),
                    result_item_type VARCHAR(32), target_amount DECIMAL(20, 6)
                )
                """);
        jdbc.execute("""
                CREATE TABLE risk_evaluation_record (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    risk_record_no VARCHAR(64) NOT NULL,
                    merchant_id VARCHAR(64) NOT NULL,
                    merchant_name VARCHAR(128),
                    merchant_order_no VARCHAR(128),
                    payment_order_no VARCHAR(64),
                    transaction_amount DECIMAL(20, 6),
                    transaction_currency VARCHAR(3),
                    risk_level VARCHAR(16),
                    decision_result VARCHAR(16),
                    decision_reason VARCHAR(512),
                    hit_count INT,
                    evaluation_time TIMESTAMP NOT NULL
                )
                """);
    }

    private void prepareNotificationWorkItem(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO admin_operational_work_item (
                    id, work_item_no, source_type, source_key, work_status, priority,
                    owner_account_id, owner_account_name, sla_due_at, first_detected_at,
                    last_synced_at, closed_at, close_reason, latest_remark, version,
                    source_projection_version, merchant_id, merchant_name, business_no,
                    related_business_no, transaction_id, transaction_date_time, source_occurred_at,
                    create_time, update_time
                ) VALUES (
                    1, 'OWI20261105000000000001', 'MERCHANT_NOTIFICATION_FAILURE',
                    'DEMO_NOTIFY_001', 'OPEN', 'HIGH', NULL, NULL,
                    TIMESTAMP '2026-11-05 11:00:00', TIMESTAMP '2026-11-05 10:00:00',
                    TIMESTAMP '2026-11-05 10:01:00', NULL, NULL, NULL, 0,
                    1, '200045', 'Demo Merchant', 'DEMO_NOTIFY_001', 'DEMO_ORDER_001',
                    'DEMO_TXN_001', TIMESTAMP '2026-11-05 09:59:00', TIMESTAMP '2026-11-05 10:00:00',
                    TIMESTAMP '2026-11-05 10:01:00', TIMESTAMP '2026-11-05 10:01:00'
                )
                """);
        jdbc.update("""
                INSERT INTO transaction_merchant_notification (
                    notify_id, notify_status, merchant_id, merchant_order_no, transaction_id,
                    transaction_date_time, create_time, last_attempt_no, fail_reason,
                    next_retry_time, deleted
                ) VALUES (
                    'DEMO_NOTIFY_001', 'CLOSED', '200045', 'DEMO_ORDER_001', 'DEMO_TXN_001',
                    TIMESTAMP '2026-11-05 09:59:00', TIMESTAMP '2026-11-05 10:00:00', 5,
                    'Gateway timeout', NULL, 0
                )
                """);
        jdbc.update("""
                INSERT INTO base_merchant_info (merchant_id, merchant_name, deleted)
                VALUES ('200045', 'Demo Merchant', 0)
                """);
    }

    private void prepareChannelAbnormality(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO transaction_abnormal_event (
                    abnormal_event_id, event_status, merchant_id, merchant_order_no,
                    transaction_id, transaction_date_time, platform_currency, platform_amount,
                    currency_exponent, first_seen_time, abnormal_level, occurrence_count,
                    abnormal_description, assigned_to_id, assigned_to_name, deleted
                ) VALUES (
                    'ABN202611050001', 'OPEN', '200045', 'ORDER-CHANNEL-001',
                    'TXN-CHANNEL-001', TIMESTAMP '2026-11-05 09:00:00', 'USD', 120.50,
                    2, TIMESTAMP '2026-11-05 09:00:00', 'HIGH', 2,
                    'Channel status differs from platform status', NULL, NULL, 0
                )
                """);
        jdbc.update("""
                INSERT INTO base_merchant_info (merchant_id, merchant_name, deleted)
                VALUES ('200045', 'Demo Merchant', 0)
                """);
    }

    private void prepareClearingBacklogs(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO base_merchant_info (merchant_id, merchant_name, deleted)
                VALUES ('200045', 'Demo Merchant', 0)
                """);
        jdbc.update("""
                INSERT INTO transaction_operation (
                    operation_id, transaction_id, merchant_id, label_currency, label_amount,
                    currency_exponent, transaction_date_time, deleted
                ) VALUES
                    ('OP-CLEARING-MANUAL-001', 'TXN-CLEARING-MANUAL-001', '200045',
                     'USD', 125.500000, 2, TIMESTAMP '2026-11-05 08:30:00', 0),
                    ('OP-CLEARING-FAILED-001', 'TXN-CLEARING-FAILED-001', '200045',
                     'EUR', 80.250000, 2, TIMESTAMP '2026-11-05 08:40:00', 0),
                    ('OP-CLEARING-WAITING-001', 'TXN-CLEARING-WAITING-001', '200045',
                     'HKD', 500.000000, 2, TIMESTAMP '2026-11-05 08:50:00', 0)
                """);
        jdbc.update("""
                INSERT INTO transaction_finance_state (
                    finance_state_id, transaction_id, operation_id, merchant_id, label_currency,
                    clearing_status, clearing_retry_count, next_retry_time,
                    last_failure_code, last_failure_message, transaction_date_time,
                    update_time, deleted
                ) VALUES
                    ('FIN-MANUAL-001', 'TXN-CLEARING-MANUAL-001', 'OP-CLEARING-MANUAL-001',
                     '200045', 'USD', 'MANUAL_REVIEW', 3, NULL, 'CLEARING_RETRY_EXHAUSTED',
                     'Fee configuration conflict', TIMESTAMP '2026-11-05 08:30:00',
                     TIMESTAMP '2026-11-05 09:00:00', 0),
                    ('FIN-FAILED-001', 'TXN-CLEARING-FAILED-001', 'OP-CLEARING-FAILED-001',
                     '200045', 'EUR', 'FAILED', 2, NULL, 'LEGACY_RETRY_TERMINATED',
                     'Legacy retry plan is missing', TIMESTAMP '2026-11-05 08:40:00',
                     TIMESTAMP '2026-11-05 09:10:00', 0),
                    ('FIN-WAITING-RETRY-001', 'TXN-CLEARING-WAITING-001', 'OP-CLEARING-WAITING-001',
                     '200045', 'HKD', 'WAITING_SOURCE', 1, TIMESTAMP '2026-11-05 10:30:00',
                     'SOURCE_NOT_READY', 'Source transaction is still processing',
                     TIMESTAMP '2026-11-05 08:50:00', TIMESTAMP '2026-11-05 09:20:00', 0)
                """);
    }

    private void prepareSettlementBacklogs(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO base_merchant_info (merchant_id, merchant_name, deleted)
                VALUES ('200045', 'Demo Merchant', 0)
                """);
        jdbc.update("""
                INSERT INTO settlement_batch (
                    settlement_batch_no, merchant_id, target_currency, target_currency_exponent,
                    batch_type, original_batch_no, review_order_no, batch_status, retry_count,
                    last_failure_stage, last_failure_code, last_failure_message, update_time
                ) VALUES
                    ('SB20261105-00000001', '200045', 'USD', 2, 'REGULAR', NULL,
                     'SRO20261105-00000001', 'MANUAL_REVIEW', 8, 'RATE_LOCKING',
                     'SETTLEMENT_RETRY_EXHAUSTED',
                     'Settlement rate provider remained unavailable',
                     TIMESTAMP '2026-11-05 09:15:00'),
                    ('SB20261105-00000002', '200045', 'EUR', 2, 'REGULAR', NULL,
                     NULL, 'FAILED_RETRYABLE', 3, 'RESULT_CALCULATION',
                     'SETTLEMENT_DATABASE_TRANSIENT', 'Transient database failure',
                     TIMESTAMP '2026-11-05 09:20:00')
                """);
        jdbc.update("""
                INSERT INTO settlement_result_item (
                    settlement_batch_no, result_role, result_item_type, target_amount
                ) VALUES (
                    'SB20261105-00000001', 'LEDGER_POSTING', 'NET_SETTLEMENT', 98.750000
                )
                """);
    }

    private void prepareHighRiskEvents(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO risk_evaluation_record (
                    risk_record_no, merchant_id, merchant_name, merchant_order_no,
                    payment_order_no, transaction_amount, transaction_currency,
                    risk_level, decision_result, decision_reason, hit_count, evaluation_time
                ) VALUES
                    ('RISK-CRITICAL-001', '200045', 'Demo Merchant', 'ORDER-CRITICAL-001',
                     'PAY-CRITICAL-001', 900.000000, 'USD', 'CRITICAL', 'PASS',
                     'Critical velocity anomaly', 3, TIMESTAMP '2026-11-05 09:00:00'),
                    ('RISK-REJECT-001', '200045', 'Demo Merchant', 'ORDER-REJECT-001',
                     'PAY-REJECT-001', 120.500000, 'EUR', 'MEDIUM', 'REJECT',
                     'Blocked by internal risk rule', 1, TIMESTAMP '2026-11-05 09:10:00'),
                    ('RISK-LOW-001', '200045', 'Demo Merchant', 'ORDER-LOW-001',
                     'PAY-LOW-001', 10.000000, 'HKD', 'LOW', 'PASS',
                     'Normal transaction', 0, TIMESTAMP '2026-11-05 09:20:00')
                """);
    }

    private SyncRequest syncRequest(String requestKey, LocalDateTime syncTime) {
        SyncRequest request = new SyncRequest();
        request.setRequestKey(requestKey);
        request.setSyncTime(syncTime);
        return request;
    }
}
