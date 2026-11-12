package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data 生成管理端国家和地区 Excel 的独立行模型，避免跨服务共享管理端 DTO。
 */
@Data
public class CountryExportRow {
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
