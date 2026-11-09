package com.scott.payment.admin.api.export;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异步导出与下载中心公共接口权限和操作审计契约测试。
 */
class AdminExportTaskControllerContractTest {

    /**
     * 列表、详情、下载、重试和待办提交必须使用独立且稳定的权限编码。
     */
    @Test
    void shouldRequireDedicatedPermissionForEveryCapability() {
        RequestMapping root = AdminExportTaskController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/admin/operations/export-tasks");
        Map<String, String> expected = Map.of(
                "search", "operations:export-task:list",
                "detail", "operations:export-task:detail",
                "download", "operations:export-task:download",
                "retry", "operations:export-task:retry");

        expected.forEach((methodName, permission) -> {
            RequiresPermission annotation = method(AdminExportTaskController.class, methodName)
                    .getAnnotation(RequiresPermission.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(permission);
        });

        RequiresPermission submit = method(AdminOperationalWorkItemExportController.class, "submit")
                .getAnnotation(RequiresPermission.class);
        assertThat(submit.value()).isEqualTo("operations:work-item:export");
        assertThat(method(AdminTransactionExportTaskController.class, "submitOrders")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("transaction:order:export");
        assertThat(method(AdminTransactionExportTaskController.class, "submitRefunds")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("transaction:refund:export");
    }

    /**
     * 创建、下载和人工重试均会改变可审计事实，必须写入统一操作日志。
     */
    @Test
    void shouldAuditSubmissionDownloadAndRetry() {
        Set.of("download", "retry").forEach(methodName ->
                assertThat(method(AdminExportTaskController.class, methodName)
                        .getAnnotation(OperationLog.class)).isNotNull());
        assertThat(method(AdminOperationalWorkItemExportController.class, "submit")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminTransactionExportTaskController.class, "submitOrders")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminTransactionExportTaskController.class, "submitRefunds")
                .getAnnotation(OperationLog.class)).isNotNull();
    }

    /**
     * 按方法名查找 Controller 契约方法，避免测试依赖参数实现细节。
     */
    private Method method(Class<?> controller, String methodName) {
        return java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
    }
}
