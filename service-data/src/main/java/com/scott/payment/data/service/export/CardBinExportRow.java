package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data 生成管理端卡 BIN Excel 的独立行模型，避免 service-data 依赖 service-admin。
 */
@Data
public class CardBinExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.cardBin.cardBinStart", width = 18, forceText = true)
    private String cardBinStart;
    @ExcelExportColumn(order = 2, headerKey = "excel.cardBin.cardBinEnd", width = 18, forceText = true)
    private String cardBinEnd;
    @ExcelExportColumn(order = 3, headerKey = "excel.cardBin.binLength", width = 12)
    private Integer binLength;
    @ExcelExportColumn(order = 4, headerKey = "excel.cardBin.cardBrand", width = 18)
    private String cardBrand;
    @ExcelExportColumn(order = 5, headerKey = "excel.cardBin.cardSubBrand", width = 24)
    private String cardSubBrand;
    @ExcelExportColumn(order = 6, headerKey = "excel.cardBin.cardType", width = 16)
    private String cardType;
    @ExcelExportColumn(order = 7, headerKey = "excel.cardBin.cardLevel", width = 16)
    private String cardLevel;
    @ExcelExportColumn(order = 8, headerKey = "excel.cardBin.issuerCountryName", width = 24)
    private String issuerCountryName;
    @ExcelExportColumn(order = 9, headerKey = "excel.cardBin.issuerCountryAlpha2", width = 14)
    private String issuerCountryAlpha2;
    @ExcelExportColumn(order = 10, headerKey = "excel.cardBin.issuerCountryAlpha3", width = 14)
    private String issuerCountryAlpha3;
    @ExcelExportColumn(order = 11, headerKey = "excel.cardBin.issuerCountryNumeric", width = 14, forceText = true)
    private String issuerCountryNumeric;
    @ExcelExportColumn(order = 12, headerKey = "excel.cardBin.issuerBank", width = 32)
    private String issuerBank;
    @ExcelExportColumn(order = 13, headerKey = "excel.cardBin.issuerWebUrl", width = 32)
    private String issuerWebUrl;
    @ExcelExportColumn(order = 14, headerKey = "excel.cardBin.issuerTelephone", width = 20, forceText = true)
    private String issuerTelephone;
    @ExcelExportColumn(order = 15, headerKey = "excel.cardBin.dataSource", width = 18)
    private String dataSource;
    @ExcelExportColumn(order = 16, headerKey = "excel.cardBin.status", width = 14)
    private String status;
    @ExcelExportColumn(order = 17, headerKey = "excel.cardBin.effectiveTime", width = 22)
    private LocalDateTime effectiveTime;
    @ExcelExportColumn(order = 18, headerKey = "excel.cardBin.expireTime", width = 22)
    private LocalDateTime expireTime;
    @ExcelExportColumn(order = 19, headerKey = "excel.cardBin.remark", width = 30)
    private String remark;
    @ExcelExportColumn(order = 20, headerKey = "excel.cardBin.createBy", width = 18)
    private String createBy;
    @ExcelExportColumn(order = 21, headerKey = "excel.cardBin.updateBy", width = 18)
    private String updateBy;
    @ExcelExportColumn(order = 22, headerKey = "excel.cardBin.createTime", width = 22)
    private LocalDateTime createTime;
    @ExcelExportColumn(order = 23, headerKey = "excel.cardBin.updateTime", width = 22)
    private LocalDateTime updateTime;
}
