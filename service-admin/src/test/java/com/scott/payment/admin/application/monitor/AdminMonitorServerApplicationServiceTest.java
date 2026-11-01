package com.scott.payment.admin.application.monitor;

import com.scott.payment.admin.dto.monitor.ServerMonitorResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 服务器监控强类型响应测试。
 */
class AdminMonitorServerApplicationServiceTest {

    /**
     * 当前节点必须返回 CPU、JVM、操作系统和运行时间的固定结构。
     */
    @Test
    void shouldReturnTypedServerMetrics() {
        AdminMonitorServerApplicationService service = new AdminMonitorServerApplicationService();

        ServerMonitorResponse response = service.serverInfo();

        assertThat(response.getCpu()).isNotNull();
        assertThat(response.getCpu().getAvailableProcessors()).isPositive();
        assertThat(response.getJvm()).isNotNull();
        assertThat(response.getJvm().getJavaVersion()).isNotBlank();
        assertThat(response.getSystem()).isNotNull();
        assertThat(response.getSystem().getOsName()).isNotBlank();
        assertThat(response.getRuntime()).isNotNull();
        assertThat(response.getRuntime().getStartTime()).isPositive();
    }
}
