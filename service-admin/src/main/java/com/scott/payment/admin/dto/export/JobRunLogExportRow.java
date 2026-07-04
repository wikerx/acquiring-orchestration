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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Run Log Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class JobRunLogExportRow {

    /**
     * 收单支付标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.jobLog.runId", width = 28)
    private String runId;

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.jobLog.jobCode", width = 20)
    private String jobCode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.jobLog.jobName", width = 20)
    private String jobName;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.jobLog.triggerType", width = 16)
    private String triggerType;

    /**
     * 收单支付状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.jobLog.runStatus", width = 16)
    private String runStatus;

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.jobLog.executorNode", width = 24)
    private String executorNode;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.jobLog.startTime", width = 22)
    private LocalDateTime startTime;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.jobLog.endTime", width = 22)
    private LocalDateTime endTime;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.jobLog.durationMs", width = 14)
    private Long durationMs;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.jobLog.errorMessage", width = 36)
    private String errorMessage;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.jobLog.createTime", width = 22)
    private LocalDateTime createTime;
}
