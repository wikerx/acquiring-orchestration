package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RegionCurrencyExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 地区币种配置导出行对象
 * @status : create
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
