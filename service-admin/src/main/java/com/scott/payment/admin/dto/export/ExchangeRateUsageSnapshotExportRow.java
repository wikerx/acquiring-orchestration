package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateUsageSnapshotExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Exchange Rate Usage Snapshot Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class ExchangeRateUsageSnapshotExportRow {

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.snapshot.usageScene", width = 16)
    private String usageScene;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.snapshot.businessType", width = 18)
    private String businessType;

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.snapshot.businessNo", width = 28)
    private String businessNo;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.snapshot.usedRate", width = 18)
    private BigDecimal usedRate;

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.business.rawRateId", width = 14)
    private Long rawRateId;

    /**
     * 收单支付标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.business.ruleId", width = 14)
    private Long ruleId;

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.snapshot.businessRateId", width = 16)
    private Long businessRateId;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.snapshot.appliedTime", width = 22)
    private LocalDateTime appliedTime;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.snapshot.calculationDescription", width = 40)
    private String calculationDescription;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.common.createTime", width = 22)
    private LocalDateTime createTime;
}
