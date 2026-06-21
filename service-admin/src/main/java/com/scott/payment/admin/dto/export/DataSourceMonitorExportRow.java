package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

/**
 * 数据源监控导出行对象。
 *
 * <p>用于导出当前服务的动态数据源、Hikari 连接池和分表绑定快照，
 * 方便运维人员在巡检或问题排查时留存环境状态。</p>
 */
@Data
public class DataSourceMonitorExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.datasource.dataSourceKey", width = 18)
    private String dataSourceKey;

    @ExcelExportColumn(order = 2, headerKey = "excel.datasource.groupName", width = 16)
    private String groupName;

    @ExcelExportColumn(order = 3, headerKey = "excel.datasource.role", width = 16)
    private String role;

    @ExcelExportColumn(order = 4, headerKey = "excel.datasource.poolName", width = 28)
    private String poolName;

    @ExcelExportColumn(order = 5, headerKey = "excel.datasource.databaseName", width = 20)
    private String databaseName;

    @ExcelExportColumn(order = 6, headerKey = "excel.datasource.jdbcUrl", width = 42)
    private String jdbcUrl;

    @ExcelExportColumn(order = 7, headerKey = "excel.datasource.running", width = 14)
    private String running;

    @ExcelExportColumn(order = 8, headerKey = "excel.datasource.reachable", width = 14)
    private String reachable;

    @ExcelExportColumn(order = 9, headerKey = "excel.datasource.activeConnections", width = 16)
    private Integer activeConnections;

    @ExcelExportColumn(order = 10, headerKey = "excel.datasource.idleConnections", width = 16)
    private Integer idleConnections;

    @ExcelExportColumn(order = 11, headerKey = "excel.datasource.totalConnections", width = 16)
    private Integer totalConnections;

    @ExcelExportColumn(order = 12, headerKey = "excel.datasource.awaitingThreads", width = 16)
    private Integer threadsAwaitingConnection;

    @ExcelExportColumn(order = 13, headerKey = "excel.datasource.maximumPoolSize", width = 16)
    private Integer maximumPoolSize;

    @ExcelExportColumn(order = 14, headerKey = "excel.datasource.minimumIdle", width = 16)
    private Integer minimumIdle;

    @ExcelExportColumn(order = 15, headerKey = "excel.datasource.relatedShardingTables", width = 32)
    private String relatedShardingTables;
}
