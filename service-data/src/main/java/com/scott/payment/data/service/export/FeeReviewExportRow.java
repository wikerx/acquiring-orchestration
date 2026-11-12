package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理端费率复核异步导出行。 */
@Data
public class FeeReviewExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.fee.planCode", width = 22)
    private String planCode;
    @ExcelExportColumn(order = 2, headerKey = "excel.fee.planName", width = 24)
    private String planName;
    @ExcelExportColumn(order = 3, headerKey = "excel.fee.planType", width = 14)
    private String planType;
    @ExcelExportColumn(order = 4, headerKey = "excel.fee.merchantId", width = 20)
    private String merchantId;
    @ExcelExportColumn(order = 5, headerKey = "excel.fee.version", width = 12)
    private Integer versionNo;
    @ExcelExportColumn(order = 6, headerKey = "excel.fee.changeReason", width = 36)
    private String changeReason;
    @ExcelExportColumn(order = 7, headerKey = "excel.fee.submitter", width = 18)
    private String submitByName;
    @ExcelExportColumn(order = 8, headerKey = "excel.fee.submitTime", width = 22)
    private LocalDateTime submitTime;
}
