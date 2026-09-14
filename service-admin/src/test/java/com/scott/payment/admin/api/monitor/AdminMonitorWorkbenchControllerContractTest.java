package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertActionRequest;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ApiMonitorQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ChannelMonitorQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.LogSearchQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TimeRangeQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TraceSearchQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.WebhookMonitorQuery;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMonitorWorkbenchControllerContractTest
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 系统监控工作台接口权限和告警操作审计注解契约测试。
 * @status : create
 */
class AdminMonitorWorkbenchControllerContractTest {

    @Test
    void shouldKeepIndependentPermissionsForEveryMonitoringSurface() throws Exception {
        Map<Method, String> expected = Map.ofEntries(
                Map.entry(method("overview", TimeRangeQuery.class), "system:monitor:overview:query"),
                Map.entry(method("services"), "system:monitor:service:query"),
                Map.entry(method("runtime", TimeRangeQuery.class), "system:monitor:service:query"),
                Map.entry(method("capabilities"), "system:monitor:overview:query"),
                Map.entry(method("searchApis", ApiMonitorQuery.class), "system:monitor:api:query"),
                Map.entry(method("searchTrace", TraceSearchQuery.class), "system:monitor:trace:query"),
                Map.entry(method("searchChannels", ChannelMonitorQuery.class), "system:monitor:channel:query"),
                Map.entry(method("searchWebhooks", WebhookMonitorQuery.class), "system:monitor:webhook:query"),
                Map.entry(method("searchLogs", LogSearchQuery.class), "system:monitor:log:query"),
                Map.entry(method("searchAlerts", AlertQuery.class), "system:monitor:alert:query"),
                Map.entry(method("alertDetail", String.class, String.class), "system:monitor:alert:query"),
                Map.entry(method("takeOver", String.class, String.class, AlertActionRequest.class), "system:monitor:alert:handle"),
                Map.entry(method("markProcessing", String.class, String.class, AlertActionRequest.class), "system:monitor:alert:handle"),
                Map.entry(method("closeAlert", String.class, String.class, AlertActionRequest.class), "system:monitor:alert:handle"),
                Map.entry(method("securityStatistics", TimeRangeQuery.class), "system:monitor:security:query")
        );

        expected.forEach((method, permission) -> assertThat(method.getAnnotation(RequiresPermission.class))
                .isNotNull()
                .extracting(RequiresPermission::value)
                .isEqualTo(permission));
    }

    @Test
    void alertMutationsShouldBeAudited() throws Exception {
        assertThat(method("takeOver", String.class, String.class, AlertActionRequest.class)
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method("markProcessing", String.class, String.class, AlertActionRequest.class)
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method("closeAlert", String.class, String.class, AlertActionRequest.class)
                .getAnnotation(OperationLog.class)).isNotNull();
    }

    private Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return AdminMonitorWorkbenchController.class.getMethod(name, parameterTypes);
    }
}
