package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务运行日志导出行对象
 */
@Data
public class JobRunLogExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.jobLog.runId", width = 28)
    private String runId;

    @ExcelExportColumn(order = 2, headerKey = "excel.jobLog.jobCode", width = 20)
    private String jobCode;

    @ExcelExportColumn(order = 3, headerKey = "excel.jobLog.jobName", width = 20)
    private String jobName;

    /**
     * 触发类型，用于区分手工触发、定时调度、失败重试或系统补偿执行。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.jobLog.triggerType", width = 16)
    private String triggerType;

    @ExcelExportColumn(order = 5, headerKey = "excel.jobLog.runStatus", width = 16)
    private String runStatus;

    @ExcelExportColumn(order = 6, headerKey = "excel.jobLog.executorNode", width = 24)
    private String executorNode;

    @ExcelExportColumn(order = 7, headerKey = "excel.jobLog.startTime", width = 22)
    private LocalDateTime startTime;

    @ExcelExportColumn(order = 8, headerKey = "excel.jobLog.endTime", width = 22)
    private LocalDateTime endTime;

    @ExcelExportColumn(order = 9, headerKey = "excel.jobLog.durationMs", width = 14)
    private Long durationMs;

    /**
     * 内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.jobLog.errorMessage", width = 36)
    private String errorMessage;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.jobLog.createTime", width = 22)
    private LocalDateTime createTime;
}
