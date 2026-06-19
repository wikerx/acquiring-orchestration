package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCurrencyExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 币种导出行对象
 * @status : create
 */
@Data
public class IsoCurrencyExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.currency.alpha3Code", width = 14)
    private String alpha3Code;

    @ExcelExportColumn(order = 2, headerKey = "excel.currency.numericCode", width = 14)
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
