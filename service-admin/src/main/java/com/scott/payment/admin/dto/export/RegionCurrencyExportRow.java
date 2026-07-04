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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RegionCurrencyExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Region Currency Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class RegionCurrencyExportRow {

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.regionCurrency.alpha2Code", width = 14)
    private String alpha2Code;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.regionCurrency.countryName", width = 20)
    private String countryName;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
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

    /**
     * 收单支付状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.regionCurrency.status", width = 12)
    private String status;
}
