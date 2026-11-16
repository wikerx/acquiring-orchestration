package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data 生成统一待办 Excel 的行模型，字段与管理端脱敏导出源契约一一对应。
 */
@Data
public class OperationalWorkItemExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.operations.workItem.workItemNo", width = 28)
    private String workItemNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.operations.workItem.sourceType", width = 24)
    private String sourceType;
    @ExcelExportColumn(order = 3, headerKey = "excel.operations.workItem.businessNo", width = 28)
    private String businessNo;
    @ExcelExportColumn(order = 4, headerKey = "excel.operations.workItem.merchantId", width = 20)
    private String merchantId;
    @ExcelExportColumn(order = 5, headerKey = "excel.operations.workItem.workStatus", width = 16)
    private String workStatus;
    @ExcelExportColumn(order = 6, headerKey = "excel.operations.workItem.sourceStatus", width = 20)
    private String sourceStatus;
    @ExcelExportColumn(order = 7, headerKey = "excel.operations.workItem.priority", width = 14)
    private String priority;
    @ExcelExportColumn(order = 8, headerKey = "excel.operations.workItem.owner", width = 22)
    private String ownerAccountName;
    @ExcelExportColumn(order = 9, headerKey = "excel.operations.workItem.currency", width = 12)
    private String currency;
    @ExcelExportColumn(order = 10, headerKey = "excel.operations.workItem.amount", width = 20)
    private String amount;
    @ExcelExportColumn(order = 11, headerKey = "excel.operations.workItem.firstDetectedAt", width = 22)
    private LocalDateTime firstDetectedAt;
    @ExcelExportColumn(order = 12, headerKey = "excel.operations.workItem.slaDueAt", width = 22)
    private LocalDateTime slaDueAt;
    @ExcelExportColumn(order = 13, headerKey = "excel.operations.workItem.overdue", width = 12)
    private String overdue;
    @ExcelExportColumn(order = 14, headerKey = "excel.operations.workItem.latestRemark", width = 36)
    private String latestRemark;
}
