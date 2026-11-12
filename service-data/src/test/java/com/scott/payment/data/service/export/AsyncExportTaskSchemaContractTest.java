package com.scott.payment.data.service.export;

import com.scott.payment.data.mapper.export.DataExportTaskMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异步导出任务数据库迁移、权限、调度与 Nacos 配置契约测试。
 */
class AsyncExportTaskSchemaContractTest {

    /**
     * 主迁移必须同时交付任务、下载审计、权限和受保护系统调度任务。
     */
    @Test
    void shouldDefineTablesPermissionsAndDispatchJob() throws IOException {
        String migration = readSql("async-export-task-migration.sql");

        assertThat(migration).contains(
                "CREATE TABLE IF NOT EXISTS data_export_task",
                "UNIQUE KEY uk_data_export_task_no (task_no)",
                "UNIQUE KEY uk_data_export_task_request (creator_app_code, creator_account_id, request_id)",
                "KEY idx_data_export_task_dispatch (status, next_retry_time, lease_until, id)",
                "CREATE TABLE IF NOT EXISTS data_export_download_audit",
                "UNIQUE KEY uk_data_export_download_audit_no (audit_no)",
                "DEFAULT CHARSET=utf8mb4",
                "'operations:export-task:list'",
                "'operations:export-task:detail'",
                "'operations:export-task:download'",
                "'operations:export-task:retry'",
                "'DATA_EXPORT_TASK_DISPATCH'",
                "'dataExportTaskDispatch'",
                "allow_concurrent, params, status");
    }

    /**
     * 发布脚本必须提供阻断前检、结构后检和保留历史数据的隔离回滚。
     */
    @Test
    void shouldProvidePrecheckPostcheckAndNonDestructiveRollback() throws IOException {
        String precheck = readSql("async-export-task-precheck.sql");
        String postcheck = readSql("async-export-task-postcheck.sql");
        String rollback = readSql("async-export-task-rollback.sql");

        assertThat(precheck).contains(
                "admin_app_missing",
                "home_catalog_missing",
                "data_export_task",
                "DATA_EXPORT_TASK_DISPATCH");
        assertThat(postcheck).contains(
                "information_schema.columns",
                "information_schema.statistics",
                "admin_export_task_center_v1",
                "operations:export-task:download",
                "DATA_EXPORT_TASK_DISPATCH");
        assertThat(rollback).contains(
                "DATA_EXPORT_TASK_DISPATCH",
                "operations:export-task:list",
                "admin_export_task_center_v1");
        assertThat(rollback).doesNotContain(
                "DROP TABLE data_export_task",
                "DROP TABLE IF EXISTS data_export_task",
                "DELETE FROM data_export_task",
                "DELETE FROM data_export_download_audit");
    }

    /**
     * Nacos 文档必须使用两条独立 HMAC 调用边和独立 Bucket，并从受控公共配置引用 MinIO 凭据。
     */
    @Test
    void shouldDefineSecureAndStartupCompatibleNacosConfiguration() throws IOException {
        String admin = readProjectFile("docs/deployment/nacos/service-admin-dev.yaml");
        String data = readProjectFile("docs/deployment/nacos/service-data-dev.yaml");
        String job = readProjectFile("docs/deployment/nacos/service-job-dev.yaml");
        String managementScript = readProjectFile("scripts/manage-nacos-internal-auth.sh");

        assertThat(admin).contains(
                "internal-secret: ${acquiring.internal-auth.edges.admin-data.active-secret}",
                "active-secret: ${acquiring.internal-auth.edges.data-admin.active-secret}",
                "/internal/admin/operations/work-items/export-page",
                "/internal/admin/transactions/orders/export-page",
                "/internal/admin/transactions/operations/export-page",
                "/internal/admin/transactions/merchant-notifications/export-page",
                "/internal/admin/transactions/refunds/export-page",
                "/internal/admin/settlement/result-items/export-page",
                "/internal/admin/settlement/reserve-items/export-page",
                "/internal/admin/base/card-bin/export-page",
                "/internal/admin/monitor/job-logs/export-page",
                "/internal/admin/transactions/channel-match-abnormalities/export-page",
                "/internal/admin/settlement/review-orders/export-page",
                "/internal/admin/settlement/postings/export-page",
                "/internal/admin/security/intercept-events/export-page",
                "/internal/admin/exchange/raw-rates/export-page",
                "/internal/admin/exchange/business-rates/export-page",
                "/internal/admin/exchange/usage-snapshots/export-page",
                "/internal/admin/system/users/export-page",
                "/internal/admin/system/configs/export-page",
                "/internal/admin/merchant/ip-whitelists/export-page",
                "/internal/admin/risk/configs/export-page",
                "/internal/admin/monitor/datasource-snapshot/export-page");
        assertThat(data).contains(
                "internal-secret: ${acquiring.internal-auth.edges.data-admin.active-secret}",
                "active-secret: ${acquiring.internal-auth.edges.admin-data.active-secret}",
                "internal-secret: ${acquiring.internal-auth.edges.data-merchant.active-secret}",
                "active-secret: ${acquiring.internal-auth.edges.merchant-data.active-secret}",
                "/internal/data/export-tasks/**",
                "/internal/data/merchant-export-tasks/**",
                "enabled: ${DATA_EXPORT_OBJECT_STORAGE_ENABLED:${acquiring.object-storage.enabled:false}}",
                "bucket: ${DATA_EXPORT_OBJECT_STORAGE_BUCKET:vexra-export-files}",
                "access-key: ${DATA_EXPORT_OBJECT_STORAGE_ACCESS_KEY:${acquiring.object-storage.access-key:}}",
                "secret-key: ${DATA_EXPORT_OBJECT_STORAGE_SECRET_KEY:${acquiring.object-storage.secret-key:}}");
        assertThat(job).contains(
                "read-timeout-millis: ${JOB_DATA_CLIENT_READ_TIMEOUT_MILLIS:60000}",
                "internal-secret: ${acquiring.internal-auth.edges.job-data.active-secret}");
        assertThat(managementScript).contains(
                "merchant-data",
                "data-merchant",
                "printf '%s\\n' admin-data data-admin merchant-data data-merchant job-data",
                "admin-data|admin-job|admin-payment|admin-clearing|admin-settlement",
                "data-admin|data-merchant) printf '%s' service-data",
                "merchant-data|merchant-payment) printf '%s' service-merchant",
                "admin-data) printf '%s\\n' service-data",
                "data-admin) printf '%s\\n' service-admin",
                "merchant-data) printf '%s\\n' service-data",
                "data-merchant) printf '%s\\n' service-merchant");
    }

    /**
     * 过期任务可能来自无租约持有者的历史记录，失败恢复 SQL 必须支持 NULL 安全匹配。
     */
    @Test
    void shouldRecoverStaleTaskWithoutLeaseOwner() throws NoSuchMethodException {
        Method method = DataExportTaskMapper.class.getDeclaredMethod("markFailure",
                Long.class, Long.class, String.class, String.class, String.class, String.class,
                LocalDateTime.class, LocalDateTime.class);
        Update update = method.getAnnotation(Update.class);

        assertThat(update).isNotNull();
        assertThat(String.join(" ", update.value()))
                .contains("lease_owner = #{leaseOwner} OR (lease_owner IS NULL AND #{leaseOwner} IS NULL)");
    }

    /**
     * 从当前模块或聚合工程根目录读取 SQL 资源。
     */
    private String readSql(String fileName) throws IOException {
        Path direct = Path.of("src/main/resources/sql", fileName);
        Path path = Files.exists(direct) ? direct : Path.of("service-data").resolve(direct);
        return Files.readString(path);
    }

    /**
     * 从聚合工程根目录或模块执行目录读取版本化部署文档。
     */
    private String readProjectFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path parent = Path.of("..").resolve(relativePath).normalize();
        Path path = Files.exists(direct) ? direct : parent;
        return Files.readString(path);
    }
}
