package com.scott.payment.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRocketMqMonitorPropertiesTest
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : RocketMQ Admin 监控配置绑定和敏感凭据 toString 排除测试。
 * @status : create
 */
class AdminRocketMqMonitorPropertiesTest {

    @Test
    void shouldBindRocketMqAdminMonitorConfiguration() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "acquiring.monitor.rocketmq.enabled", "true",
                "acquiring.monitor.rocketmq.admin-group", "admin-monitor-test",
                "acquiring.monitor.rocketmq.cluster-name", "TestCluster",
                "acquiring.monitor.rocketmq.request-timeout-millis", "4500",
                "acquiring.monitor.rocketmq.access-key", "test-access",
                "acquiring.monitor.rocketmq.secret-key", "test-secret"));

        AdminRocketMqMonitorProperties properties = new Binder(source)
                .bind("acquiring.monitor.rocketmq", Bindable.of(AdminRocketMqMonitorProperties.class))
                .orElseThrow(() -> new AssertionError("RocketMQ monitor properties were not bound"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getAdminGroup()).isEqualTo("admin-monitor-test");
        assertThat(properties.getClusterName()).isEqualTo("TestCluster");
        assertThat(properties.getRequestTimeoutMillis()).isEqualTo(4500);
        assertThat(properties.getAccessKey()).isEqualTo("test-access");
        assertThat(properties.getSecretKey()).isEqualTo("test-secret");
        assertThat(properties.toString()).doesNotContain("test-access", "test-secret");
    }
}
