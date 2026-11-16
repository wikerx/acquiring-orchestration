package com.scott.payment.admin.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一待办实现中并发、幂等和来源事实边界的静态合同测试。
 */
class OperationalWorkItemImplementationContractTest {

    /**
     * 人工命令必须使用请求键、版本和状态三重约束，禁止静默吞掉冲突请求。
     */
    @Test
    void shouldKeepCommandIdempotencyAndCasGuards() throws IOException {
        String source = readImplementation();

        assertThat(source).contains(
                "findHistoryRequest(request.getRequestId())",
                "current.workItemNo().equals(existingRequest.workItemNo())",
                "action.equals(existingRequest.actionType())",
                "ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT.getCode()",
                "WHERE id = :id AND work_status = :beforeStatus AND version = :expectedVersion",
                "version = version + 1");
        assertThat(source).doesNotContain("historyRequestExists(request.getRequestId())");
        assertThat(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT.getCode()).isEqualTo("F510001");
    }

    /**
     * 同步必须覆盖创建、重开、自动关闭及活动来源 SLA/优先级刷新。
     */
    @Test
    void shouldRefreshOperationalMetadataWithoutCopyingBusinessFacts() throws IOException {
        String source = readImplementation();

        assertThat(source).contains(
                "insertWorkItem(source, syncTime)",
                "SET work_status = :desiredStatus, priority = :priority, sla_due_at = :slaDueAt",
                "\"SOURCE_TERMINAL_OR_REMOVED\".equals(current.closeReason())",
                "SET priority = :priority, sla_due_at = :slaDueAt,",
                "SET work_status = 'CLOSED', last_synced_at = :syncTime",
                "loadRefundSnapshots",
                "loadSettlementSnapshots",
                "loadAlertSnapshots",
                "loadNotificationSnapshots",
                "loadChannelAbnormalSnapshots",
                "loadClearingBacklogSnapshots",
                "loadSettlementBacklogSnapshots",
                "loadHighRiskEventSnapshots");
        assertThat(source).doesNotContain(
                "merchant_id, merchant_name, business_no, transaction_id, amount, currency, source_status,");
    }

    /**
     * 来源托管型待办必须投影来源状态，并禁止通用动作绕过来源状态机。
     */
    @Test
    void shouldKeepSourceManagedWorkItemsInTheirOwningStateMachines() throws IOException {
        String source = readImplementation();

        assertThat(source).contains(
                "CHANNEL_MATCH_ABNORMAL",
                "CLEARING_BACKLOG",
                "SETTLEMENT_BACKLOG",
                "loadBoundedActiveChannelAbnormalities",
                "loadBoundedActiveClearingBacklogs",
                "loadBoundedActiveSettlementBacklogs",
                "finance.next_retry_time IS NULL",
                "batch.batch_status = 'MANUAL_REVIEW'",
                "refreshActiveWorkItem",
                "Source-managed state synchronized",
                "isSourceManaged(current.sourceType()) && !\"REMARK\".equals(action)",
                "source-managed work item must be handled in its source workflow");
    }

    /**
     * 高风险事件必须只投影明确的拒绝或高风险结果，并保留统一待办人工闭环能力。
     */
    @Test
    void shouldKeepHighRiskEventsInOperationalWorkflowWithoutChangingRiskDecision() throws IOException {
        String source = readImplementation();

        assertThat(source).contains(
                "HIGH_RISK_EVENT",
                "decision_result = 'REJECT' OR risk_level IN ('HIGH', 'CRITICAL')",
                "loadBoundedActiveHighRiskEvents",
                "properties.getHighRiskCriticalSla()",
                "properties.getHighRiskEventSla()");
        assertThat(source).doesNotContain(
                "UPDATE risk_evaluation_record",
                "HIGH_RISK_EVENT.equals(sourceType)");
    }

    /**
     * Job 到 Admin 的内部鉴权必须精确放行待办同步路径，禁止依赖宽泛通配授权。
     */
    @Test
    void shouldAuthorizeOnlyTheRequiredOperationalSyncPath() throws IOException {
        String source = readNacosTemplate();

        assertThat(source).contains(
                "- /internal/admin/exchange/alerts/evaluate",
                "- /internal/admin/operations/work-items/sync");
        assertThat(source).doesNotContain("- /internal/admin/**");
    }

    private String readImplementation() throws IOException {
        Path direct = Path.of("src/main/java/com/scott/payment/admin/service/impl/JdbcAdminOperationalWorkItemService.java");
        Path path = Files.exists(direct) ? direct : Path.of("service-admin").resolve(direct);
        return Files.readString(path);
    }

    private String readNacosTemplate() throws IOException {
        Path direct = Path.of("docs/deployment/nacos/service-admin-dev.yaml");
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(direct).normalize();
        return Files.readString(path);
    }
}
