package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCountryExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 国家地区导出行对象
 * @status : create
 */
@Data
public class IsoCountryExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.country.alpha2Code", width = 14)
    private String alpha2Code;

    @ExcelExportColumn(order = 2, headerKey = "excel.country.alpha3Code", width = 14)
    private String alpha3Code;

    @ExcelExportColumn(order = 3, headerKey = "excel.country.chineseName", width = 20)
    private String chineseName;

    @ExcelExportColumn(order = 4, headerKey = "excel.country.englishName", width = 24)
    private String englishName;

    @ExcelExportColumn(order = 5, headerKey = "excel.country.continentName", width = 16)
    private String continentName;

    @ExcelExportColumn(order = 6, headerKey = "excel.country.currencyAlpha3Code", width = 16)
    private String currencyAlpha3Code;

    @ExcelExportColumn(order = 7, headerKey = "excel.country.status", width = 12)
    private String status;

    @ExcelExportColumn(order = 8, headerKey = "excel.country.createdAt", width = 22)
    private LocalDateTime createdAt;
}
