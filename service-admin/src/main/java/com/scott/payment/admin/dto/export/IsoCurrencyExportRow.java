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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCurrencyExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Iso Currency Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class IsoCurrencyExportRow {

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.currency.alpha3Code", width = 14)
    private String alpha3Code;

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.currency.numericCode", width = 14)
    private String numericCode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.currency.chineseName", width = 20)
    private String chineseName;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.currency.englishName", width = 24)
    private String englishName;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.currency.currencySymbol", width = 12)
    private String currencySymbol;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.currency.fractionDigits", width = 10)
    private Integer fractionDigits;

    /**
     * 收单支付状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.currency.status", width = 12)
    private String status;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.currency.createdAt", width = 22)
    private LocalDateTime createdAt;
}
