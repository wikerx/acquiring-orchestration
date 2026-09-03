package com.scott.payment.admin.api.transaction;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementProfileControllerContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证结算档案查询、详情和编辑使用相互独立的最小权限。
 * @status : create
 */
class AdminSettlementProfileControllerContractTest {

    @Test
    void everyProfileRouteShouldRequireItsDedicatedPermission() {
        Map<String, String> expected = Map.of(
                "search", "settlement:profile:list",
                "detail", "settlement:profile:detail",
                "update", "settlement:profile:update");

        expected.forEach((methodName, permission) -> {
            RequiresPermission annotation = java.util.Arrays.stream(
                            AdminSettlementProfileController.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .findFirst().orElseThrow().getAnnotation(RequiresPermission.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(permission);
        });
    }

    @Test
    void profilePathVariablesShouldDeclareTheirRuntimeBindingName() {
        for (String methodName : new String[]{"detail", "update"}) {
            Method method = java.util.Arrays.stream(AdminSettlementProfileController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            PathVariable annotation = method.getParameters()[0].getAnnotation(PathVariable.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("settlementProfileNo");
        }
    }
}
