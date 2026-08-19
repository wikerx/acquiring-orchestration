package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeSimulationExportRow
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端费用试算记录导出模型，保留匹配维度、原始金额与 USD 费用结果。
 * @status : create
 */
@Data
public class FeeSimulationExportRow {
    /** 唯一试算流水号。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.fee.simulationNo", width = 24)
    private String simulationNo;
    /** 商户号；模板试算场景允许为空。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.fee.merchantId", width = 20)
    private String merchantId;
    /** 交易类型字典值。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.fee.transactionType", width = 18)
    private String transactionType;
    /** 支付类型字典值。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.fee.paymentType", width = 18)
    private String paymentType;
    /** 支付方式字典值，非卡支付通常为 ALL。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.fee.paymentMethod", width = 18)
    private String paymentMethod;
    /** 商户上送标签金额，精度以数据库值为准。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.fee.labelAmount", width = 18)
    private BigDecimal labelAmount;
    /** 标签金额 ISO 4217 三位币种。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.fee.labelCurrency", width = 14)
    private String labelCurrency;
    /** 应用固定费用和上下限后的 USD 试算费用。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.fee.finalFeeUsd", width = 18)
    private BigDecimal finalFeeUsd;
    /** 执行试算的管理端操作人姓名快照。 */
    @ExcelExportColumn(order = 9, headerKey = "excel.fee.operator", width = 18)
    private String operatorName;
    /** 试算记录创建时间。 */
    @ExcelExportColumn(order = 10, headerKey = "excel.fee.createTime", width = 22)
    private LocalDateTime createTime;
}
