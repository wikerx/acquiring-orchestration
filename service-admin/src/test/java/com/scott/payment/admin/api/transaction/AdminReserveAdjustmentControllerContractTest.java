package com.scott.payment.admin.api.transaction;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminReserveAdjustmentControllerContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证保证金调整提交与复核不能共用同一权限。
 * @status : create
 */
class AdminReserveAdjustmentControllerContractTest {

    @Test
    void reserveAdjustmentRoutesShouldRequireDedicatedPermissions() {
        Map<String, String> expected = Map.of(
                "submit", "clearing:reserve-adjustment:submit",
                "review", "clearing:reserve-adjustment:review");

        expected.forEach((methodName, permission) -> {
            RequiresPermission annotation = java.util.Arrays.stream(
                            AdminReserveAdjustmentController.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .findFirst().orElseThrow()
                    .getAnnotation(RequiresPermission.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(permission);
        });
    }
}
