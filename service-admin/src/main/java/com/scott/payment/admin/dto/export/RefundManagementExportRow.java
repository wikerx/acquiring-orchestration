package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin 退款管理导出行，包含运营核查需要的退款、审批和渠道摘要，不导出渠道原文。
 */
@Data
public class RefundManagementExportRow {

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
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.refund.merchantId", width = 18)
    private String merchantId;
    /**
     * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.refund.merchantOrderNo", width = 26)
    private String merchantOrderNo;
    /**
     * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.refund.transactionType", width = 16)
    private String transactionType;
    @ExcelExportColumn(order = 6, headerKey = "excel.refund.refundScope", width = 14)
    private String refundScope;
    @ExcelExportColumn(order = 7, headerKey = "excel.refund.requestSource", width = 18)
    private String requestSource;
    @ExcelExportColumn(order = 8, headerKey = "excel.refund.amount", width = 18)
    private BigDecimal transactionAmount;
    @ExcelExportColumn(order = 9, headerKey = "excel.refund.currency", width = 12)
    private String transactionCurrency;
    @ExcelExportColumn(order = 10, headerKey = "excel.refund.status", width = 16)
    private String transactionStatus;
    @ExcelExportColumn(order = 11, headerKey = "excel.refund.approvalStatus", width = 18)
    private String approvalStatus;
    @ExcelExportColumn(order = 12, headerKey = "excel.refund.applicant", width = 22)
    private String applicantName;
    /**
     * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.refund.channelCode", width = 16)
    private String channelCode;
    /**
     * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.refund.channelOrderNo", width = 26)
    private String channelOrderNo;
    /**
     * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.refund.transactionTime", width = 22)
    private LocalDateTime transactionDateTime;
    @ExcelExportColumn(order = 16, headerKey = "excel.refund.completeTime", width = 22)
    private LocalDateTime completeTime;
}
