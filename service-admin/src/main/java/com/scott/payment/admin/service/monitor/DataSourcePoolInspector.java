package com.scott.payment.admin.service.monitor;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataSourcePoolInspector
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 物理连接池解析器，通过标准 JDBC Wrapper API 识别 HikariDataSource，避免依赖动态数据源代理的内部实现。
 * @status : create
 */
@Component
public class DataSourcePoolInspector {

    /**
     * 尝试把运行时数据源解析为 Hikari 物理连接池。
     *
     * @param dataSource 运行时数据源或代理；允许为空
     * @return Hikari 物理连接池；无法识别或解包失败时返回 {@code null}
     */
    public HikariDataSource unwrapHikari(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            return hikariDataSource;
        }
        if (dataSource == null) {
            return null;
        }
        try {
            return dataSource.isWrapperFor(HikariDataSource.class)
                    ? dataSource.unwrap(HikariDataSource.class)
                    : null;
        } catch (SQLException | RuntimeException exception) {
            return null;
        }
    }
}
