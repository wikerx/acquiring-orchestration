package com.scott.payment.admin.api.transaction;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证阶梯期间重放的申请与复核权限分离，浏览器不能直接执行重放。 */
class AdminTierPeriodReplayControllerContractTest {

    @Test
    void tierPeriodReplayRoutesShouldRequireDedicatedPermissions() {
        Map<String, String> expected = Map.of(
                "submit", "clearing:tier-period-replay:submit",
                "review", "clearing:tier-period-replay:review");

        expected.forEach((methodName, permission) -> {
            RequiresPermission annotation = java.util.Arrays.stream(
                            AdminTierPeriodReplayController.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .findFirst().orElseThrow()
                    .getAnnotation(RequiresPermission.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(permission);
        });
    }
}
