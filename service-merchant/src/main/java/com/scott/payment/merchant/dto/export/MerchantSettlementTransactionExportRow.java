package com.scott.payment.merchant.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSettlementTransactionExportRow
 * @date : 2026-09-01 22:40
 * @email : scott_x@163.com
 * @description : 商户真实交易结算明细导出行；原金额、批次直接汇率和目标金额均来自不可变结算结果。
 * @status : update
 */
@Data
public class MerchantSettlementTransactionExportRow {
    /** 结算批次号。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.settlement.batchNo", width = 24)
    private String settlementBatchNo;
    /** 批次业务日期。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.settlement.businessDate", width = 16)
    private LocalDate businessDate;
    /** 真实来源平台交易号，强制按文本导出。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.settlement.transactionId", width = 28, forceText = true)
    private String sourceTransactionId;
    /** 来源交易时间。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.settlement.transactionTime", width = 22)
    private LocalDateTime sourceTransactionDateTime;
    /** 已本地化的结算结果项目类型。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.settlement.resultItemType", width = 20)
    private String resultItemType;
    /** 已本地化的支付类型。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.settlement.paymentType", width = 16)
    private String paymentType;
    /** 已本地化的支付方式。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.settlement.paymentMethod", width = 18)
    private String paymentMethod;
    /** 已本地化的交易类型。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.settlement.transactionType", width = 18)
    private String transactionType;
    /** 已本地化的费用类别。 */
    @ExcelExportColumn(order = 9, headerKey = "excel.settlement.feeCategory", width = 18)
    private String feeCategory;
    /** 已本地化的商户视角方向。 */
    @ExcelExportColumn(order = 10, headerKey = "excel.settlement.direction", width = 12)
    private String direction;
    /** 清分保存的原币种金额。 */
    @ExcelExportColumn(order = 11, headerKey = "excel.settlement.sourceAmount", width = 20)
    private BigDecimal sourceAmount;
    /** 清分保存的原币种。 */
    @ExcelExportColumn(order = 12, headerKey = "excel.settlement.sourceCurrency", width = 14)
    private String sourceCurrency;
    /** 一单位原币种对应目标币种的批次直接汇率。 */
    @ExcelExportColumn(order = 13, headerKey = "excel.settlement.directRate", width = 22)
    private BigDecimal directRate;
    /** 批次目标币种金额。 */
    @ExcelExportColumn(order = 14, headerKey = "excel.settlement.targetAmount", width = 20)
    private BigDecimal targetAmount;
    /** 批次目标结算币种。 */
    @ExcelExportColumn(order = 15, headerKey = "excel.settlement.targetCurrency", width = 14)
    private String targetCurrency;
    /** 已本地化的费用限额命中结果。 */
    @ExcelExportColumn(order = 16, headerKey = "excel.settlement.appliedLimit", width = 16)
    private String appliedLimit;
}
