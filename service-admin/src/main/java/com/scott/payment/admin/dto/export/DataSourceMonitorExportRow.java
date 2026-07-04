package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataSourceMonitorExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Data Source Monitor Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class DataSourceMonitorExportRow {

    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.datasource.dataSourceKey", width = 18)
    private String dataSourceKey;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.datasource.groupName", width = 16)
    private String groupName;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.datasource.role", width = 16)
    private String role;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.datasource.poolName", width = 28)
    private String poolName;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.datasource.databaseName", width = 20)
    private String databaseName;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.datasource.jdbcUrl", width = 42)
    private String jdbcUrl;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.datasource.running", width = 14)
    private String running;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.datasource.reachable", width = 14)
    private String reachable;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.datasource.activeConnections", width = 16)
    private Integer activeConnections;

    /**
     * 收单支付标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.datasource.idleConnections", width = 16)
    private Integer idleConnections;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.datasource.totalConnections", width = 16)
    private Integer totalConnections;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.datasource.awaitingThreads", width = 16)
    private Integer threadsAwaitingConnection;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.datasource.maximumPoolSize", width = 16)
    private Integer maximumPoolSize;

    /**
     * 收单支付标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.datasource.minimumIdle", width = 16)
    private Integer minimumIdle;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.datasource.relatedShardingTables", width = 32)
    private String relatedShardingTables;
}
