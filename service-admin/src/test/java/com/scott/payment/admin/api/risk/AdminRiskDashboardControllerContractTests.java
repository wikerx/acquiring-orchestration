package com.scott.payment.admin.api.risk;

import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证今日风险事件分页接口始终使用当前菜单自己的权限边界。 */
class AdminRiskDashboardControllerContractTests {

    @Test
    void shouldExposePagedTodayEventsWithDashboardPermission() throws Exception {
        Method method = AdminRiskDashboardController.class.getMethod(
                "pageTodayEvents", RiskDTOs.EvaluationQueryRequest.class);

        PostMapping mapping = method.getAnnotation(PostMapping.class);
        RequiresPermission permission = method.getAnnotation(RequiresPermission.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/dashboard/today-events/page");
        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo("risk:dashboard:todayEvents:list");
    }
}
