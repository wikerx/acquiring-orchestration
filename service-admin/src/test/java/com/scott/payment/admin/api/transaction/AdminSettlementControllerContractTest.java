package com.scott.payment.admin.api.transaction;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementControllerContractTest
 * @date : 2026-09-01 23:20
 * @email : scott_x@163.com
 * @description : 验证结算管理查询与资金命令分别绑定独立最小权限的接口契约
 * @status : create
 */
class AdminSettlementControllerContractTest {

    @Test
    void everySettlementRouteShouldRequireItsDedicatedPermission() {
        Map<String, String> expected = Map.of(
                "search", "settlement:batch:list",
                "detail", "settlement:batch:detail",
                "cancel", "settlement:batch:cancel");
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
