package com.scott.payment.merchant.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商户退款导出行，仅保留商户可见字段，不包含渠道错误、内部审批策略或 Admin 操作人。
 */
@Data
public class MerchantRefundExportRow {

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.refund.transactionId", width = 28)
    private String refundTransactionId;
    /**
     * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.refund.sourceTransactionId", width = 28)
    private String sourceTransactionId;
    /**
     * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.refund.merchantOrderNo", width = 26)
    private String merchantOrderNo;
    /**
     * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.refund.transactionType", width = 16)
    private String transactionType;
    /**
     * 全额或部分退款范围。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.refund.refundScope", width = 14)
    private String refundScope;
    /**
     * 商户标签口径的退款金额。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.refund.amount", width = 18)
    private BigDecimal transactionAmount;
    /**
     * 商户标签口径的退款币种。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.refund.currency", width = 12)
    private String transactionCurrency;
    @ExcelExportColumn(order = 8, headerKey = "excel.refund.status", width = 16)
    private String transactionStatus;
    @ExcelExportColumn(order = 9, headerKey = "excel.refund.approvalStatus", width = 18)
    private String approvalStatus;
    /**
     * 可向商户展示的退款处理说明。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.refund.merchantMessage", width = 30)
    private String merchantVisibleMessage;
    @ExcelExportColumn(order = 11, headerKey = "excel.refund.paymentMethod", width = 18)
    private String paymentMethod;
    /**
     * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.refund.transactionTime", width = 22)
    private LocalDateTime transactionDateTime;
    /**
     * 退款进入终态的时间。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.refund.completeTime", width = 22)
    private LocalDateTime completeTime;
}
