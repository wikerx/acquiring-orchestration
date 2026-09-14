package com.scott.payment.admin.service.monitor;

import com.scott.payment.admin.config.AdminDataSourceConsoleProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataSourceConsoleAccessProviderTest
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : Druid 外部控制台地址校验、本地连接池类型识别和配置状态测试。
 * @status : create
 */
class DataSourceConsoleAccessProviderTest {

    @Test
    void shouldAllowConfiguredPrivateHttpConsoleAndReportLocalPoolType() {
        AdminDataSourceConsoleProperties properties = properties(
                true, "http://192.168.10.20:8001/druid/index.html");

        var access = provider(properties)
                .snapshot(List.of(mock(HikariDataSource.class)));

        assertThat(access.getStatus()).isEqualTo(DataSourceConsoleAccessProvider.STATUS_CONFIGURED);
        assertThat(access.getUrl()).isEqualTo("http://192.168.10.20:8001/druid/index.html");
        assertThat(access.getLocalPoolTypes()).containsExactly("HikariDataSource");
        assertThat(access.getReason()).contains("目标地址所在 JVM");
    }

    @Test
    void shouldReturnNotConfiguredWhenFeatureIsDisabled() {
        var access = provider(properties(
                false, "http://127.0.0.1:8001/druid/index.html"))
                .snapshot(List.of());

        assertThat(access.getStatus()).isEqualTo(DataSourceConsoleAccessProvider.STATUS_NOT_CONFIGURED);
        assertThat(access.getUrl()).isNull();
    }

    @Test
    void shouldRejectNonHttpProtocolAndUrlCredentials() {
        var protocolAccess = provider(properties(true, "javascript:alert(1)"))
                .snapshot(List.of());
        var credentialAccess = provider(properties(
                true, "http://admin:secret@127.0.0.1:8001/druid/index.html"))
                .snapshot(List.of());

        assertThat(protocolAccess.getStatus()).isEqualTo(DataSourceConsoleAccessProvider.STATUS_MISCONFIGURED);
        assertThat(protocolAccess.getUrl()).isNull();
        assertThat(credentialAccess.getStatus()).isEqualTo(DataSourceConsoleAccessProvider.STATUS_MISCONFIGURED);
        assertThat(credentialAccess.getUrl()).isNull();
    }

    @Test
    void shouldReportUnwrappedHikariTypeForJdbcWrapper() throws Exception {
        DataSource wrapper = mock(DataSource.class);
        HikariDataSource hikariDataSource = mock(HikariDataSource.class);
        when(wrapper.isWrapperFor(HikariDataSource.class)).thenReturn(true);
        when(wrapper.unwrap(HikariDataSource.class)).thenReturn(hikariDataSource);

        var access = provider(properties(false, null)).snapshot(List.of(wrapper));

        assertThat(access.getLocalPoolTypes()).containsExactly("HikariDataSource");
    }

    private DataSourceConsoleAccessProvider provider(AdminDataSourceConsoleProperties properties) {
        return new DataSourceConsoleAccessProvider(properties, new DataSourcePoolInspector());
    }

    private AdminDataSourceConsoleProperties properties(boolean enabled, String url) {
        AdminDataSourceConsoleProperties properties = new AdminDataSourceConsoleProperties();
        properties.setEnabled(enabled);
        properties.setUrl(url);
        return properties;
    }
}
