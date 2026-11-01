package com.scott.payment.merchant.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商户侧按真实交易粒度导出的正式交易结算明细。
 */
@Data
public class MerchantSettlementTransactionSummaryExportRow {

    /**
     * 交易所属结算批次号。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.settlement.batchNo", width = 24)
    private String settlementBatchNo;

    @ExcelExportColumn(order = 2, headerKey = "excel.settlement.businessDate", width = 16)
    private LocalDate businessDate;

    /**
     * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.settlement.merchantOrderNo", width = 28, forceText = true)
    private String merchantOrderNo;

    /**
     * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.settlement.transactionId", width = 28, forceText = true)
    private String sourceTransactionId;

    /**
     * 原交易受理时间。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.settlement.transactionTime", width = 22)
    private LocalDateTime sourceTransactionDateTime;

    /**
     * 原交易的支付类型。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.settlement.paymentType", width = 16)
    private String paymentType;

    @ExcelExportColumn(order = 7, headerKey = "excel.settlement.paymentMethod", width = 18)
    private String paymentMethod;

    /**
     * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.settlement.transactionType", width = 18)
    private String transactionType;

    /**
     * 原交易金额。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.settlement.sourceAmount", width = 20)
    private BigDecimal sourceAmount;

    /**
     * 原交易币种。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.settlement.sourceCurrency", width = 14)
    private String sourceCurrency;

    @ExcelExportColumn(order = 11, headerKey = "excel.settlement.componentCount", width = 16)
    private Long componentCount;

    @ExcelExportColumn(order = 12, headerKey = "excel.settlement.direction", width = 12)
    private String netDirection;

    @ExcelExportColumn(order = 13, headerKey = "excel.settlement.netAmount", width = 20)
    private BigDecimal netTargetAmount;

    @ExcelExportColumn(order = 14, headerKey = "excel.settlement.targetCurrency", width = 14)
    private String targetCurrency;

    @ExcelExportColumn(order = 15, headerKey = "excel.settlement.postedTime", width = 22)
    private LocalDateTime postedTime;
}
