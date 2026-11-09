package com.scott.payment.admin.api.transaction;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证历史标签本金差额调整提交、批准和拒绝使用独立最小权限及操作审计。
 */
class AdminSettlementPrincipalCorrectionControllerContractTest {

    @Test
    void routesShouldRequireDedicatedPermissionsAndAudit() {
        RequestMapping root = AdminSettlementPrincipalCorrectionController.class
                .getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/admin/settlement/principal-corrections");
        Map<String, String> expected = Map.of(
                "submit", "settlement:principal-correction:submit",
                "approve", "settlement:principal-correction:approve",
                "reject", "settlement:principal-correction:reject");

        expected.forEach((methodName, permission) -> {
            java.lang.reflect.Method method = java.util.Arrays.stream(
                            AdminSettlementPrincipalCorrectionController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            assertThat(method.getAnnotation(RequiresPermission.class).value()).isEqualTo(permission);
            assertThat(method.getAnnotation(OperationLog.class)).isNotNull();
        });
    }
}
