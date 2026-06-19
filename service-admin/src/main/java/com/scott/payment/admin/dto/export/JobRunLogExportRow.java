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

    @ExcelExportColumn(order = 1, headerKey = "excel.jobLog.runId", width = 28)
    private String runId;

    @ExcelExportColumn(order = 2, headerKey = "excel.jobLog.jobCode", width = 20)
    private String jobCode;

    @ExcelExportColumn(order = 3, headerKey = "excel.jobLog.jobName", width = 20)
    private String jobName;

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

    @ExcelExportColumn(order = 10, headerKey = "excel.jobLog.errorMessage", width = 36)
    private String errorMessage;

    @ExcelExportColumn(order = 11, headerKey = "excel.jobLog.createTime", width = 22)
    private LocalDateTime createTime;
}
