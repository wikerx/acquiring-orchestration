package com.scott.payment.admin.service.monitor;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.scott.payment.admin.config.MonitorDynamicDataSourceProperties;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TimeBucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDatasourceMonitorSamplerTest
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 数据源指标有界历史、采样节流和 performance_schema 降级行为测试。
 * @status : create
 */
class AdminDatasourceMonitorSamplerTest {

    @Test
    void shouldKeepOnlySevenDaysOfMinuteSamples() {
        AdminDatasourceMonitorSampler sampler = sampler(null, new DataSourcePoolInspector());
        LocalDateTime base = LocalDateTime.of(2026, 9, 1, 0, 0);
        for (int index = 0; index < AdminDatasourceMonitorSampler.MAX_SAMPLES + 2; index++) {
            sampler.addPoolSample(bucket(base.plusMinutes(index), "active", index));
            sampler.addLatencySample(bucket(base.plusMinutes(index), "p95", index));
        }

        var response = sampler.metrics();

        assertThat(response.getPoolTrend()).hasSize(AdminDatasourceMonitorSampler.MAX_SAMPLES);
        assertThat(response.getPoolTrend().get(0).getTimestamp()).isEqualTo(base.plusMinutes(2));
        assertThat(response.getLatencyTrend()).hasSameSizeAs(response.getPoolTrend());
    }

    @Test
    void shouldDegradePerformanceSchemaCapabilityWhenPrimaryDatasourceCannotConnect() throws Exception {
        DynamicRoutingDataSource routing = mock(DynamicRoutingDataSource.class);
        DataSource primary = mock(DataSource.class);
        when(primary.getConnection()).thenThrow(new SQLException("permission denied"));
        when(routing.getDataSources()).thenReturn(Map.of("master", primary));
        DataSourcePoolInspector inspector = mock(DataSourcePoolInspector.class);
        when(inspector.unwrapHikari(primary)).thenReturn(null);

        var response = sampler(routing, inspector).metrics();

        assertThat(response.getPoolMetricsCapability().getStatus()).isEqualTo("UNAVAILABLE");
        assertThat(response.getSqlMetricsCapability().getStatus()).isEqualTo("UNAVAILABLE");
        assertThat(response.getSqlMetricsCapability().getReason()).contains("SQLException");
        assertThat(response.getLatencyTrend()).isEmpty();
    }

    @Test
    void shouldThrottleEndpointTriggeredSamplesToOnePerMinute() throws Exception {
        DynamicRoutingDataSource routing = mock(DynamicRoutingDataSource.class);
        DataSource primary = mock(DataSource.class);
        when(routing.getDataSources()).thenReturn(Map.of("master", primary));
        when(primary.getConnection()).thenThrow(new SQLException("offline"));
        DataSourcePoolInspector inspector = mock(DataSourcePoolInspector.class);
        when(inspector.unwrapHikari(primary)).thenReturn(null);
        AdminDatasourceMonitorSampler sampler = sampler(routing, inspector);

        sampler.metrics();
        sampler.metrics();

        verify(routing, times(1)).getDataSources();
        verify(primary, times(1)).getConnection();
    }

    private AdminDatasourceMonitorSampler sampler(DynamicRoutingDataSource routing,
                                                  DataSourcePoolInspector inspector) {
        @SuppressWarnings("unchecked")
        ObjectProvider<DynamicRoutingDataSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(routing);
        MonitorDynamicDataSourceProperties properties = new MonitorDynamicDataSourceProperties();
        properties.setPrimary("master");
        return new AdminDatasourceMonitorSampler(
                provider, properties, inspector, new MonitorSensitiveTextSanitizer());
    }

    private TimeBucket bucket(LocalDateTime timestamp, String key, long value) {
        TimeBucket bucket = new TimeBucket();
        bucket.setTimestamp(timestamp);
        bucket.setValues(Map.of(key, BigDecimal.valueOf(value)));
        return bucket;
    }
}
