package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.ReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.SubmitRequest;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证历史标签本金修复提交与复核使用相互独立的最小权限，浏览器模型不携带可信操作人。
 */
class AdminLabelPrincipalRepairControllerContractTest {

    @Test
    void repairRoutesShouldRequireDedicatedPermissions() {
        Map<String, String> expected = Map.of(
                "submit", "clearing:principal-repair:submit",
                "review", "clearing:principal-repair:review");

        expected.forEach((methodName, permission) -> {
            Method method = Arrays.stream(AdminLabelPrincipalRepairController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(permission);
        });
    }

    @Test
    void browserRequestsShouldNotExposeOperatorFields() {
        assertThat(Arrays.stream(SubmitRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).toList())
                .doesNotContain("submitOperator", "reviewOperator");
        assertThat(Arrays.stream(ReviewRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).toList())
                .doesNotContain("submitOperator", "reviewOperator");
    }
}
