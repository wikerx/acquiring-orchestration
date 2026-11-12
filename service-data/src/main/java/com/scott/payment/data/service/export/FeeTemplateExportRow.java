package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理端费用模板异步导出行。 */
@Data
public class FeeTemplateExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.fee.planCode", width = 22)
    private String planCode;
    @ExcelExportColumn(order = 2, headerKey = "excel.fee.planName", width = 24)
    private String planName;
    @ExcelExportColumn(order = 3, headerKey = "excel.fee.version", width = 12)
    private Integer currentVersionNo;
    @ExcelExportColumn(order = 4, headerKey = "excel.fee.status", width = 14)
    private String status;
    @ExcelExportColumn(order = 5, headerKey = "excel.fee.remark", width = 32)
    private String remark;
    @ExcelExportColumn(order = 6, headerKey = "excel.fee.updateTime", width = 22)
    private LocalDateTime updateTime;
}
