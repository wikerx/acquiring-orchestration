package com.scott.payment.admin.api.transaction;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementReversalControllerContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证冲正管理浏览器接口使用独立最小权限并保留操作审计。
 * @status : create
 */
class AdminSettlementReversalControllerContractTest {

    @Test
    void everyReversalRouteShouldRequireDedicatedPermissionAndAudit() {
        RequestMapping root = AdminSettlementReversalController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/admin/settlement/reversal-orders");
        Map<String, String> expected = Map.of(
                "search", "settlement:reversal-order:list",
                "detail", "settlement:reversal-order:detail",
                "submit", "settlement:reversal-order:create",
                "approve", "settlement:reversal-order:approve",
                "reject", "settlement:reversal-order:reject");

        expected.forEach((methodName, permission) -> {
            java.lang.reflect.Method method = java.util.Arrays.stream(
                            AdminSettlementReversalController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            RequiresPermission authorization = method.getAnnotation(RequiresPermission.class);
            assertThat(authorization).isNotNull();
            assertThat(authorization.value()).isEqualTo(permission);
            assertThat(method.getAnnotation(OperationLog.class)).isNotNull();
        });
    }
}
