package com.scott.payment.admin.api.monitor;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在线用户查询权限和强制下线审计注解契约测试。
 */
class MonitorOnlineControllerContractTest {

    @Test
    void shouldProtectOnlineQueriesAndAuditForceLogout() throws Exception {
        Method list = MonitorOnlineController.class.getMethod(
                "onlineList", int.class, int.class, String.class, String.class);
        Method forceLogout = MonitorOnlineController.class.getMethod("forceLogout", Long.class);

        assertThat(list.getAnnotation(RequiresPermission.class))
                .isNotNull()
                .extracting(RequiresPermission::value)
                .isEqualTo("system:online:list");
        assertThat(forceLogout.getAnnotation(RequiresPermission.class))
                .isNotNull()
                .extracting(RequiresPermission::value)
                .isEqualTo("system:online:forceLogout");
        assertThat(forceLogout.getAnnotation(OperationLog.class)).isNotNull();
    }
}
