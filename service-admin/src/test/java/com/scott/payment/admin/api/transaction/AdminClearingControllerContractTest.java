package com.scott.payment.admin.api.transaction;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证管理端清分查询和人工命令分别绑定独立最小权限的控制器契约
 */
class AdminClearingControllerContractTest {

    @Test
    void everyClearingRouteShouldRequireItsDedicatedPermission() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("search", "clearing:record:list"),
                Map.entry("detail", "clearing:record:detail"),
                Map.entry("transactionDetails", "clearing:record:detail"),
                Map.entry("reserveDetails", "clearing:record:detail"),
                Map.entry("recalculationOptions", "clearing:record:recalculate"),
                Map.entry("retry", "clearing:record:retry"),
                Map.entry("review", "clearing:record:review"),
                Map.entry("recalculate", "clearing:record:recalculate"),
                Map.entry("recalculateBatch", "clearing:record:recalculate"));

        expected.forEach((methodName, permission) -> {
            RequiresPermission annotation = java.util.Arrays.stream(
                            AdminClearingController.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .findFirst().orElseThrow()
                    .getAnnotation(RequiresPermission.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(permission);
        });
    }
}
