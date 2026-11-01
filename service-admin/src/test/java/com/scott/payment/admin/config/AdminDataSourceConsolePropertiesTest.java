package com.scott.payment.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据源外部控制台 Spring Boot 配置绑定测试。
 */
class AdminDataSourceConsolePropertiesTest {

    @Test
    void shouldBindDatasourceConsoleConfiguration() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "acquiring.monitor.datasource-console.enabled", "true",
                "acquiring.monitor.datasource-console.url", "http://192.168.10.20:8001/druid/index.html"));

        AdminDataSourceConsoleProperties properties = new Binder(source)
                .bind("acquiring.monitor.datasource-console", Bindable.of(AdminDataSourceConsoleProperties.class))
                .orElseThrow(() -> new AssertionError("Datasource console properties were not bound"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getUrl()).isEqualTo("http://192.168.10.20:8001/druid/index.html");
    }
}
