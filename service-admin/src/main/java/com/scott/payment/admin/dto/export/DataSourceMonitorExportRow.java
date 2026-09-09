package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataSourceMonitorExportRow
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : data来源监控导出行模型，位于 运营后台服务，定义 Excel 列及运营可见值，不承载数据库写入规则。
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
     * 分组名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.datasource.groupName", width = 16)
    private String groupName;

    /**
     * 角色字段，保存 {@code DataSourceMonitorExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.datasource.role", width = 16)
    private String role;

    /**
     * {@code poolName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.datasource.poolName", width = 28)
    private String poolName;

    /**
     * {@code databaseName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.datasource.databaseName", width = 20)
    private String databaseName;

    /**
     * {@code jdbcUrl}，表示回调、通知、来源站点或远程接口地址。
     * <p>
     * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度和协议由调用方校验；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.datasource.jdbcUrl", width = 42)
    private String jdbcUrl;

    /**
     * {@code running}字段，保存 {@code DataSourceMonitorExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.datasource.running", width = 14)
    private String running;

    /**
     * {@code reachable}字段，保存 {@code DataSourceMonitorExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.datasource.reachable", width = 14)
    private String reachable;

    /**
     * {@code activeConnections}字段，保存 {@code DataSourceMonitorExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.datasource.activeConnections", width = 16)
    private Integer activeConnections;

    /**
     * {@code idleConnections}字段，保存 {@code DataSourceMonitorExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.datasource.idleConnections", width = 16)
    private Integer idleConnections;

    /**
     * {@code totalConnections}，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.datasource.totalConnections", width = 16)
    private Integer totalConnections;

    /**
     * {@code threadsAwaitingConnection}字段，保存 {@code DataSourceMonitorExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.datasource.awaitingThreads", width = 16)
    private Integer threadsAwaitingConnection;

    /**
     * {@code maximumPoolSize}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.datasource.maximumPoolSize", width = 16)
    private Integer maximumPoolSize;

    /**
     * {@code minimumIdle}，用于定位渠道商户号配置或渠道侧 MID。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.datasource.minimumIdle", width = 16)
    private Integer minimumIdle;

    /**
     * {@code relatedShardingTables}字段，保存 {@code DataSourceMonitorExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.datasource.relatedShardingTables", width = 32)
    private String relatedShardingTables;
}
