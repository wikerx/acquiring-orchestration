package com.scott.payment.admin.api.operation;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一运营待办接口最小权限和人工命令审计合同测试。
 */
class AdminOperationalWorkItemControllerContractTest {

    /**
     * 查询、详情、认领、分派、转交、完成、备注和导出必须使用独立权限。
     */
    @Test
    void shouldRequireDedicatedPermissionForEveryPublicCapability() {
        RequestMapping root = AdminOperationalWorkItemController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/admin/operations/work-items");
        Map<String, String> expected = Map.of(
                "search", "operations:work-item:list",
                "detail", "operations:work-item:detail",
                "assignees", "operations:work-item:list",
                "claim", "operations:work-item:claim",
                "assign", "operations:work-item:assign",
                "transfer", "operations:work-item:transfer",
                "complete", "operations:work-item:complete",
                "remark", "operations:work-item:remark",
                "export", "operations:work-item:export");

        expected.forEach((methodName, permission) -> {
            RequiresPermission annotation = method(methodName).getAnnotation(RequiresPermission.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(permission);
        });
    }

    /**
     * 所有人工状态或负责人变更及导出都必须进入操作日志。
     */
    @Test
    void shouldAuditEveryCommandAndExport() {
        Set.of("claim", "assign", "transfer", "complete", "remark", "export")
                .forEach(methodName -> assertThat(method(methodName).getAnnotation(OperationLog.class))
                        .as("%s should be audited", methodName)
                        .isNotNull());
    }

    private Method method(String methodName) {
        return java.util.Arrays.stream(AdminOperationalWorkItemController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
    }
}
