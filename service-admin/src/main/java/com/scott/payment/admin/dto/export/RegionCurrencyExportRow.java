package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

/**
 * 地区币种配置导出行对象
 */
@Data
public class RegionCurrencyExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.regionCurrency.alpha2Code", width = 14)
    private String alpha2Code;

    @ExcelExportColumn(order = 2, headerKey = "excel.regionCurrency.countryName", width = 20)
    private String countryName;

    @ExcelExportColumn(order = 3, headerKey = "excel.regionCurrency.continentName", width = 16)
    private String continentName;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.regionCurrency.currencyAlpha3Code", width = 16)
    private String currencyAlpha3Code;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.regionCurrency.currencyName", width = 20)
    private String currencyName;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.regionCurrency.currencySymbol", width = 12)
    private String currencySymbol;

    @ExcelExportColumn(order = 7, headerKey = "excel.regionCurrency.status", width = 12)
    private String status;
}
