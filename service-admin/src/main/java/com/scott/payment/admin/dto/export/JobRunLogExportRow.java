package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 任务运行日志导出行对象
 * @status : create
 */
@Data
public class JobRunLogExportRow {

    /**
     * {@code runId}，用于定位 {@code JobRunLogExportRow} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.jobLog.runId", width = 28)
    private String runId;

    /**
     * 任务编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.jobLog.jobCode", width = 20)
    private String jobCode;

    /**
     * 任务名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.jobLog.jobName", width = 20)
    private String jobName;

    /**
     * 触发类型，用于区分手工触发、定时调度、失败重试或系统补偿执行。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.jobLog.triggerType", width = 16)
    private String triggerType;

    /**
     * 任务运行状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.jobLog.runStatus", width = 16)
    private String runStatus;

    /**
     * 执行器节点字段，保存 {@code JobRunLogExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.jobLog.executorNode", width = 24)
    private String executorNode;

    /**
     * 开始时间字段，保存 {@code JobRunLogExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.jobLog.startTime", width = 22)
    private LocalDateTime startTime;

    /**
     * 结束时间字段，保存 {@code JobRunLogExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.jobLog.endTime", width = 22)
    private LocalDateTime endTime;

    /**
     * {@code durationMs}字段，保存 {@code JobRunLogExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.jobLog.durationMs", width = 14)
    private Long durationMs;

    /**
     * 内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.jobLog.errorMessage", width = 36)
    private String errorMessage;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
     * </p>
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.jobLog.createTime", width = 22)
    private LocalDateTime createTime;
}
