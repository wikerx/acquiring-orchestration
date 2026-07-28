package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataSourceMonitorExportRow
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Data Source Monitor Export Row 协作组件，位于 运营后台服务，封装 data来源monitorexportrow 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
public class DataSourceMonitorExportRow {

    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.datasource.dataSourceKey", width = 18)
    private String dataSourceKey;

    @ExcelExportColumn(order = 2, headerKey = "excel.datasource.groupName", width = 16)
    /**
     * group Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String groupName;

    @ExcelExportColumn(order = 3, headerKey = "excel.datasource.role", width = 16)
    /**
     * role，用于保存 Data Source Monitor Export Row 中与 角色 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String role;

    @ExcelExportColumn(order = 4, headerKey = "excel.datasource.poolName", width = 28)
    /**
     * pool Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String poolName;

    @ExcelExportColumn(order = 5, headerKey = "excel.datasource.databaseName", width = 20)
    /**
     * database Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String databaseName;

    @ExcelExportColumn(order = 6, headerKey = "excel.datasource.jdbcUrl", width = 42)
    /**
     * jdbc URL，表示回调、通知、来源站点或远程接口地址。
     * <p>
     * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度和协议由调用方校验；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String jdbcUrl;

    @ExcelExportColumn(order = 7, headerKey = "excel.datasource.running", width = 14)
    /**
     * running，用于保存 Data Source Monitor Export Row 中与 running 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String running;

    @ExcelExportColumn(order = 8, headerKey = "excel.datasource.reachable", width = 14)
    /**
     * reachable，用于保存 Data Source Monitor Export Row 中与 reachable 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String reachable;

    @ExcelExportColumn(order = 9, headerKey = "excel.datasource.activeConnections", width = 16)
    /**
     * active Connections，用于保存 Data Source Monitor Export Row 中与 activeconnections 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Integer activeConnections;

    @ExcelExportColumn(order = 10, headerKey = "excel.datasource.idleConnections", width = 16)
    /**
     * idle Connections，用于保存 Data Source Monitor Export Row 中与 idleconnections 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Integer idleConnections;

    @ExcelExportColumn(order = 11, headerKey = "excel.datasource.totalConnections", width = 16)
    /**
     * total Connections，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Integer totalConnections;

    @ExcelExportColumn(order = 12, headerKey = "excel.datasource.awaitingThreads", width = 16)
    /**
     * threads Awaiting Connection，用于保存 Data Source Monitor Export Row 中与 threadsawaitingconnection 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Integer threadsAwaitingConnection;

    @ExcelExportColumn(order = 13, headerKey = "excel.datasource.maximumPoolSize", width = 16)
    /**
     * maximum Pool Size，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private Integer maximumPoolSize;

    @ExcelExportColumn(order = 14, headerKey = "excel.datasource.minimumIdle", width = 16)
    /**
     * minimum Idle，用于定位渠道商户号配置或渠道侧 MID。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Integer minimumIdle;

    @ExcelExportColumn(order = 15, headerKey = "excel.datasource.relatedShardingTables", width = 32)
    /**
     * related Sharding Tables，用于保存 Data Source Monitor Export Row 中与 relatedshardingtables 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String relatedShardingTables;
}
