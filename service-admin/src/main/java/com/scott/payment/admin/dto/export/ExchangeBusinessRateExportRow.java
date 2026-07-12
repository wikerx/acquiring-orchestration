package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeBusinessRateExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Exchange Business Rate Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class ExchangeBusinessRateExportRow {

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.common.sourceCode", width = 18)
    private String sourceCode;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.business.originalRate", width = 18)
    private BigDecimal originalRate;

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.business.finalRate", width = 18)
    private BigDecimal finalRate;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.common.effectiveTime", width = 22)
    private LocalDateTime effectiveTime;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.business.expireTime", width = 22)
    private LocalDateTime expireTime;

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.business.generateMethod", width = 16)
    private String generateMethod;

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.common.rateStatus", width = 14)
    private String rateStatus;

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.business.rawRateId", width = 14)
    private Long rawRateId;

    /**
     * 收单支付标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.business.ruleId", width = 14)
    private Long ruleId;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.business.adjustDescription", width = 40)
    private String adjustDescription;

    /**
     * 收单支付备注字段，用于记录人工说明，不参与核心状态流转。
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.exchange.common.remark", width = 30)
    private String remark;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.exchange.common.createTime", width = 22)
    private LocalDateTime createTime;
}
