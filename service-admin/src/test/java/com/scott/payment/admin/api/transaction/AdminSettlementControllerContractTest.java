package com.scott.payment.admin.api.transaction;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证结算管理的查询和资金命令均使用独立最小权限。 */
class AdminSettlementControllerContractTest {

    @Test
    void everySettlementRouteShouldRequireItsDedicatedPermission() {
        Map<String, String> expected = Map.of(
                "search", "settlement:batch:list",
                "detail", "settlement:batch:detail",
                "cancel", "settlement:batch:cancel",
                "reverse", "settlement:batch:reverse");
        expected.forEach((methodName, permission) -> {
            RequiresPermission annotation = java.util.Arrays.stream(
                            AdminSettlementController.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .findFirst().orElseThrow().getAnnotation(RequiresPermission.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(permission);
        });
    }
}
