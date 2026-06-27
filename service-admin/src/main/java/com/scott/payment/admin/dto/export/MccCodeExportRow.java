package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCC 编码导出行对象。
 */
@Data
public class MccCodeExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.mcc.mccCode", width = 14)
    private String mccCode;

    @ExcelExportColumn(order = 2, headerKey = "excel.mcc.nameCn", width = 24)
    private String nameCn;

    @ExcelExportColumn(order = 3, headerKey = "excel.mcc.nameEn", width = 36)
    private String nameEn;

    @ExcelExportColumn(order = 4, headerKey = "excel.mcc.level1", width = 22)
    private String level1Name;

    @ExcelExportColumn(order = 5, headerKey = "excel.mcc.level2", width = 22)
    private String level2Name;

    @ExcelExportColumn(order = 6, headerKey = "excel.mcc.mccType", width = 18)
    private String mccType;

    @ExcelExportColumn(order = 7, headerKey = "excel.mcc.riskLevel", width = 18)
    private String riskLevel;

    @ExcelExportColumn(order = 8, headerKey = "excel.mcc.deliveryApplicability", width = 20)
    private String deliveryApplicability;

    @ExcelExportColumn(order = 9, headerKey = "excel.mcc.status", width = 12)
    private String status;

    @ExcelExportColumn(order = 10, headerKey = "excel.mcc.source", width = 18)
    private String source;

    @ExcelExportColumn(order = 11, headerKey = "excel.mcc.versionNo", width = 16)
    private String versionNo;

    @ExcelExportColumn(order = 12, headerKey = "excel.mcc.effectiveTime", width = 22)
    private LocalDateTime effectiveTime;

    @ExcelExportColumn(order = 13, headerKey = "excel.mcc.expireTime", width = 22)
    private LocalDateTime expireTime;

    @ExcelExportColumn(order = 14, headerKey = "excel.mcc.remark", width = 30)
    private String remark;
}
