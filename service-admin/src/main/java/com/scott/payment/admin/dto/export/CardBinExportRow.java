package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinExportRow
 * @date : 2026-07-04 00:00
 * @email : scott_x@163.com
 * @description : 卡 BIN 导出行对象，位于 service-admin 导出传输层，用于控制导出字段顺序、标题和时间展示。
 * @status : create
 */
@Data
public class CardBinExportRow {

    /**
     * BIN 起始值，导出为字符串避免 Excel 科学计数法。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.cardBin.cardBinStart", width = 18)
    private String cardBinStart;

    /**
     * BIN 结束值，导出为字符串避免 Excel 科学计数法。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.cardBin.cardBinEnd", width = 18)
    private String cardBinEnd;

    /**
     * BIN 精度。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.cardBin.binLength", width = 12)
    private Integer binLength;

    /**
     * 卡品牌展示名称。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.cardBin.cardBrand", width = 18)
    private String cardBrand;

    /**
     * 卡子品牌。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.cardBin.cardSubBrand", width = 24)
    private String cardSubBrand;

    /**
     * 卡类型展示名称。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.cardBin.cardType", width = 16)
    private String cardType;

    /**
     * 卡等级。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.cardBin.cardLevel", width = 16)
    private String cardLevel;

    /**
     * 发卡国家名称。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.cardBin.issuerCountryName", width = 24)
    private String issuerCountryName;

    /**
     * 发卡国家 ISO Alpha-2 编码。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.cardBin.issuerCountryAlpha2", width = 14)
    private String issuerCountryAlpha2;

    /**
     * 发卡国家 ISO Alpha-3 编码。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.cardBin.issuerCountryAlpha3", width = 14)
    private String issuerCountryAlpha3;

    /**
     * 发卡国家 ISO Numeric 编码。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.cardBin.issuerCountryNumeric", width = 14)
    private String issuerCountryNumeric;

    /**
     * 发卡行名称。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.cardBin.issuerBank", width = 32)
    private String issuerBank;

    /**
     * 发卡行网址。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.cardBin.issuerWebUrl", width = 32)
    private String issuerWebUrl;

    /**
     * 发卡行电话。
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.cardBin.issuerTelephone", width = 20)
    private String issuerTelephone;

    /**
     * 数据来源展示名称。
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.cardBin.dataSource", width = 18)
    private String dataSource;

    /**
     * 状态展示名称。
     */
    @ExcelExportColumn(order = 16, headerKey = "excel.cardBin.status", width = 14)
    private String status;

    /**
     * 生效时间。
     */
    @ExcelExportColumn(order = 17, headerKey = "excel.cardBin.effectiveTime", width = 22)
    private LocalDateTime effectiveTime;

    /**
     * 失效时间。
     */
    @ExcelExportColumn(order = 18, headerKey = "excel.cardBin.expireTime", width = 22)
    private LocalDateTime expireTime;

    /**
     * 备注。
     */
    @ExcelExportColumn(order = 19, headerKey = "excel.cardBin.remark", width = 30)
    private String remark;

    /**
     * 创建人。
     */
    @ExcelExportColumn(order = 20, headerKey = "excel.cardBin.createBy", width = 18)
    private String createBy;

    /**
     * 修改人。
     */
    @ExcelExportColumn(order = 21, headerKey = "excel.cardBin.updateBy", width = 18)
    private String updateBy;

    /**
     * 创建时间。
     */
    @ExcelExportColumn(order = 22, headerKey = "excel.cardBin.createTime", width = 22)
    private LocalDateTime createTime;

    /**
     * 修改时间。
     */
    @ExcelExportColumn(order = 23, headerKey = "excel.cardBin.updateTime", width = 22)
    private LocalDateTime updateTime;
}
