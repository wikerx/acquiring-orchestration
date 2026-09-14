package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataSourceMonitorExportRow
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 数据源监控 Excel 导出行，定义运行时连接池与分表关联信息的运营可见列，不包含数据库账号、密码或业务表数据。
 * @status : create
 */
@Data
public class DataSourceMonitorExportRow {

    /** 运行时数据源唯一标识，例如 master 或 slave_1；非敏感且不允许为空。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.datasource.dataSourceKey", width = 18)
    private String dataSourceKey;

    /** 动态数据源分组名称，例如 slave；独立数据源时允许为空，非敏感。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.datasource.groupName", width = 16)
    private String groupName;

    /** 数据源角色，取值为 PRIMARY、GROUP_MEMBER 或 SINGLE；非敏感。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.datasource.role", width = 16)
    private String role;

    /** 连接池运行名称；底层池未暴露名称时允许为空，非敏感。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.datasource.poolName", width = 28)
    private String poolName;

    /** 从 JDBC URL 提取的数据库名称；无法解析时允许为空，属于基础设施标识。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.datasource.databaseName", width = 20)
    private String databaseName;

    /** 脱敏后的 JDBC URL；查询参数中的凭据必须移除，无法获取时允许为空。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.datasource.jdbcUrl", width = 42)
    private String jdbcUrl;

    /** 连接池运行状态的国际化文案；无法识别时按“否”导出。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.datasource.running", width = 14)
    private String running;

    /** 最近一次 JDBC 连接探测结果的国际化文案；无法探测时按“否”导出。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.datasource.reachable", width = 14)
    private String reachable;

    /** 当前活跃连接数，单位为连接；连接池未暴露指标时允许为空。 */
    @ExcelExportColumn(order = 9, headerKey = "excel.datasource.activeConnections", width = 16)
    private Integer activeConnections;

    /** 当前空闲连接数，单位为连接；连接池未暴露指标时允许为空。 */
    @ExcelExportColumn(order = 10, headerKey = "excel.datasource.idleConnections", width = 16)
    private Integer idleConnections;

    /** 当前连接池总连接数，单位为连接；连接池未暴露指标时允许为空。 */
    @ExcelExportColumn(order = 11, headerKey = "excel.datasource.totalConnections", width = 16)
    private Integer totalConnections;

    /** 当前等待获取连接的线程数，单位为线程；连接池未暴露指标时允许为空。 */
    @ExcelExportColumn(order = 12, headerKey = "excel.datasource.awaitingThreads", width = 16)
    private Integer threadsAwaitingConnection;

    /** 连接池允许的最大连接数，单位为连接；连接池未暴露配置时允许为空。 */
    @ExcelExportColumn(order = 13, headerKey = "excel.datasource.maximumPoolSize", width = 16)
    private Integer maximumPoolSize;

    /** 连接池维持的最小空闲连接数，单位为连接；连接池未暴露配置时允许为空。 */
    @ExcelExportColumn(order = 14, headerKey = "excel.datasource.minimumIdle", width = 16)
    private Integer minimumIdle;

    /** 绑定到该数据源的分表逻辑表名称，多个值使用英文逗号分隔；无绑定时为空。 */
    @ExcelExportColumn(order = 15, headerKey = "excel.datasource.relatedShardingTables", width = 32)
    private String relatedShardingTables;
}
