package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

/**
 * Data 生成管理端国家默认币种关系 Excel 的独立行模型。
 */
@Data
public class RegionCurrencyExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.regionCurrency.alpha2Code", width = 14)
    private String alpha2Code;
    @ExcelExportColumn(order = 2, headerKey = "excel.regionCurrency.countryName", width = 20)
    private String countryName;
    @ExcelExportColumn(order = 3, headerKey = "excel.regionCurrency.continentName", width = 16)
    private String continentName;
    @ExcelExportColumn(order = 4, headerKey = "excel.regionCurrency.currencyAlpha3Code", width = 16)
    private String currencyAlpha3Code;
    @ExcelExportColumn(order = 5, headerKey = "excel.regionCurrency.currencyName", width = 20)
    private String currencyName;
    @ExcelExportColumn(order = 6, headerKey = "excel.regionCurrency.currencySymbol", width = 12)
    private String currencySymbol;
    @ExcelExportColumn(order = 7, headerKey = "excel.regionCurrency.status", width = 12)
    private String status;
}
