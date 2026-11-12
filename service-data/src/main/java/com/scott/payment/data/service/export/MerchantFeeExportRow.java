package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理端商户费率异步导出行。 */
@Data
public class MerchantFeeExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.fee.merchantId", width = 20)
    private String merchantId;
    @ExcelExportColumn(order = 2, headerKey = "excel.fee.merchantName", width = 28)
    private String merchantName;
    @ExcelExportColumn(order = 3, headerKey = "excel.fee.planCode", width = 22)
    private String planCode;
    @ExcelExportColumn(order = 4, headerKey = "excel.fee.origin", width = 20)
    private String originType;
    @ExcelExportColumn(order = 5, headerKey = "excel.fee.version", width = 12)
    private Integer currentVersionNo;
    @ExcelExportColumn(order = 6, headerKey = "excel.fee.status", width = 14)
    private String status;
    @ExcelExportColumn(order = 7, headerKey = "excel.fee.updateTime", width = 22)
    private LocalDateTime updateTime;
}
