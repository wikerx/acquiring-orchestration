package com.scott.payment.admin.service.monitor;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataSourcePoolInspectorTest
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 标准 JDBC Wrapper API 解包 Hikari 连接池行为测试。
 * @status : create
 */
class DataSourcePoolInspectorTest {

    @Test
    void shouldUnwrapHikariThroughStandardJdbcWrapperApi() throws Exception {
        DataSource wrapper = mock(DataSource.class);
        HikariDataSource hikari = mock(HikariDataSource.class);
        when(wrapper.isWrapperFor(HikariDataSource.class)).thenReturn(true);
        when(wrapper.unwrap(HikariDataSource.class)).thenReturn(hikari);

        assertThat(new DataSourcePoolInspector().unwrapHikari(wrapper)).isSameAs(hikari);
    }

    @Test
    void shouldReturnNullWhenWrapperCannotExposeHikari() throws Exception {
        DataSource wrapper = mock(DataSource.class);
        when(wrapper.isWrapperFor(HikariDataSource.class)).thenReturn(false);

        assertThat(new DataSourcePoolInspector().unwrapHikari(wrapper)).isNull();
    }
}
