package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 币种导出行对象
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

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.currency.currencySymbol", width = 12)
    private String currencySymbol;

    @ExcelExportColumn(order = 6, headerKey = "excel.currency.fractionDigits", width = 10)
    private Integer fractionDigits;

    @ExcelExportColumn(order = 7, headerKey = "excel.currency.status", width = 12)
    private String status;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.currency.createdAt", width = 22)
    private LocalDateTime createdAt;
}
