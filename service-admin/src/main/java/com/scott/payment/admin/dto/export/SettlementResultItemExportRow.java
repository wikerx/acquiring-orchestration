package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementResultItemExportRow
 * @date : 2026-09-01 23:10
 * @email : scott_x@163.com
 * @description : Admin 不可变结算结果导出行，保留真实来源交易、原币种金额、批次直接汇率、未舍入值和最终目标币种金额。
 * @status : update
 */
@Data
public class SettlementResultItemExportRow {
    /** 结算结果明细号。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.settlement.resultItemNo", width = 28)
    private String settlementResultItemNo;
    /** 所属结算批次号。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.settlement.batchNo", width = 24)
    private String settlementBatchNo;
    /** 批次业务日期。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.settlement.businessDate", width = 16)
    private LocalDate businessDate;
    /** 商户号。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.settlement.merchantId", width = 20)
    private String merchantId;
    /** 真实来源平台交易号，非交易结果可空。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.settlement.transactionId", width = 28, forceText = true)
    private String sourceTransactionId;
    /** 来源交易时间，用于物理季度定位。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.settlement.transactionTime", width = 22)
    private LocalDateTime sourceTransactionDateTime;
    /** 来源清分或保证金明细编号。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.settlement.sourceDetailNo", width = 28)
    private String sourceDetailNo;
    /** 已本地化的结算结果项目类型。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.settlement.resultItemType", width = 20)
    private String resultItemType;
    /** 已本地化的财务组件或净入账角色。 */
    @ExcelExportColumn(order = 9, headerKey = "excel.settlement.resultRole", width = 20)
    private String resultRole;
    /** 已本地化的支付类型。 */
    @ExcelExportColumn(order = 10, headerKey = "excel.settlement.paymentType", width = 16)
    private String paymentType;
    /** 已本地化的支付方式。 */
    @ExcelExportColumn(order = 11, headerKey = "excel.settlement.paymentMethod", width = 18)
    private String paymentMethod;
    /** 已本地化的交易类型。 */
    @ExcelExportColumn(order = 12, headerKey = "excel.settlement.transactionType", width = 18)
    private String transactionType;
    /** 已本地化的费用类别。 */
    @ExcelExportColumn(order = 13, headerKey = "excel.settlement.feeCategory", width = 18)
    private String feeCategory;
    /** 已本地化的商户视角方向。 */
    @ExcelExportColumn(order = 14, headerKey = "excel.settlement.direction", width = 12)
    private String direction;
    /** 清分保存的原币种金额。 */
    @ExcelExportColumn(order = 15, headerKey = "excel.settlement.sourceAmount", width = 20)
    private BigDecimal sourceAmount;
    /** 清分保存的原币种。 */
    @ExcelExportColumn(order = 16, headerKey = "excel.settlement.sourceCurrency", width = 14)
    private String sourceCurrency;
    /** 一单位原币种对应目标币种的批次锁定直接汇率。 */
    @ExcelExportColumn(order = 17, headerKey = "excel.settlement.directRate", width = 22)
    private BigDecimal directRate;
    /** DECIMAL128 计算后的未舍入目标币种金额。 */
    @ExcelExportColumn(order = 18, headerKey = "excel.settlement.unroundedTargetAmount", width = 26)
    private BigDecimal unroundedTargetAmount;
    /** 按目标币种 exponent 最终舍入后的金额。 */
    @ExcelExportColumn(order = 19, headerKey = "excel.settlement.targetAmount", width = 20)
    private BigDecimal targetAmount;
    /** 批次目标结算币种。 */
    @ExcelExportColumn(order = 20, headerKey = "excel.settlement.targetCurrency", width = 14)
    private String targetCurrency;
    /** 已本地化的最低或最高费用命中结果。 */
    @ExcelExportColumn(order = 21, headerKey = "excel.settlement.appliedLimit", width = 16)
    private String appliedLimit;
    /** 已本地化的最终舍入模式。 */
    @ExcelExportColumn(order = 22, headerKey = "excel.settlement.roundingMode", width = 16)
    private String roundingMode;
    /** 结算结果明细创建时间。 */
    @ExcelExportColumn(order = 23, headerKey = "excel.settlement.createTime", width = 22)
    private LocalDateTime createTime;
}
