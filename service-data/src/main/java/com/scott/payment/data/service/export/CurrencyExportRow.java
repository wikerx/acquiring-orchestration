package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data 生成管理端币种 Excel 的独立行模型，数字代码强制按文本写入。
 */
@Data
public class CurrencyExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.currency.alpha3Code", width = 14)
    private String alpha3Code;
    @ExcelExportColumn(order = 2, headerKey = "excel.currency.numericCode", width = 14, forceText = true)
    private String numericCode;
    @ExcelExportColumn(order = 3, headerKey = "excel.currency.chineseName", width = 20)
    private String chineseName;
    @ExcelExportColumn(order = 4, headerKey = "excel.currency.englishName", width = 24)
    private String englishName;
    @ExcelExportColumn(order = 5, headerKey = "excel.currency.currencySymbol", width = 12)
    private String currencySymbol;
    @ExcelExportColumn(order = 6, headerKey = "excel.currency.fractionDigits", width = 10)
    private Integer fractionDigits;
    @ExcelExportColumn(order = 7, headerKey = "excel.currency.status", width = 12)
    private String status;
    @ExcelExportColumn(order = 8, headerKey = "excel.currency.createdAt", width = 22)
    private LocalDateTime createdAt;
}
