package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionOperationExportRow
 * @date : 2026-07-17
 * @email : scott_x@163.com
 * @description : 交易动作流水导出行对象，位于 service-admin 导出传输层，仅导出脱敏后的动作单运营排查字段。
 * @status : create
 */
@Data
public class TransactionOperationExportRow {

    /**
     * 平台交易号。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.transaction.operation.transactionId", width = 28)
    private String transactionId;

    /**
     * 原平台交易号。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.transaction.operation.sourceTransactionId", width = 28)
    private String sourceTransactionId;

    /**
     * 平台商户号。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.transaction.common.merchantId", width = 18)
    private String merchantId;

    /**
     * 商户订单号。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.transaction.common.merchantOrderNo", width = 26)
    private String merchantOrderNo;

    /**
     * 商户请求号。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.transaction.common.merchantOrderId", width = 26)
    private String merchantOrderId;

    /**
     * 交易类型。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.transaction.common.transactionType", width = 18)
    private String transactionType;

    /**
     * 交易状态。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.transaction.common.transactionStatus", width = 18)
    private String transactionStatus;

    /**
     * 交易金额。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.transaction.operation.transactionAmount", width = 18)
    private BigDecimal transactionAmount;

    /**
     * 交易币种。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.transaction.common.currency", width = 12)
    private String transactionCurrency;

    /**
     * 交易汇率。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.transaction.common.transactionRate", width = 18)
    private BigDecimal transactionRate;

    /**
     * 商户响应码。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.transaction.common.merchantResponseCode", width = 18)
    private String merchantResponseCode;

    /**
     * 商户响应描述。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.transaction.common.merchantResponseMessage", width = 30)
    private String merchantResponseMessage;

    /**
     * 商户通知状态。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.transaction.operation.merchantNotificationStatus", width = 20)
    private String merchantNotificationStatus;

    /**
     * 支付方式。
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.transaction.operation.paymentMethod", width = 18)
    private String paymentMethod;

    /**
     * 支付品牌或卡品牌。
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.transaction.operation.paymentBrand", width = 18)
    private String paymentBrand;

    /**
     * 卡 BIN 前缀。
     */
    @ExcelExportColumn(order = 16, headerKey = "excel.transaction.operation.cardBin", width = 14)
    private String cardBin;

    /**
     * 脱敏卡号。
     */
    @ExcelExportColumn(order = 17, headerKey = "excel.transaction.operation.cardNumberMasked", width = 20)
    private String cardNumberMasked;

    /**
     * 渠道编码。
     */
    @ExcelExportColumn(order = 18, headerKey = "excel.transaction.common.channelCode", width = 16)
    private String channelCode;

    /**
     * 渠道订单号。
     */
    @ExcelExportColumn(order = 19, headerKey = "excel.transaction.common.channelOrderNo", width = 26)
    private String channelOrderNo;

    /**
     * 渠道交易号。
     */
    @ExcelExportColumn(order = 20, headerKey = "excel.transaction.operation.channelTransactionId", width = 26)
    private String channelTransactionId;

    /**
     * 渠道响应码。
     */
    @ExcelExportColumn(order = 21, headerKey = "excel.transaction.operation.channelResponseCode", width = 18)
    private String channelResponseCode;

    /**
     * 渠道响应描述。
     */
    @ExcelExportColumn(order = 22, headerKey = "excel.transaction.operation.channelResponseMessage", width = 30)
    private String channelResponseMessage;

    /**
     * 授权码。
     */
    @ExcelExportColumn(order = 23, headerKey = "excel.transaction.operation.authCode", width = 16)
    private String authCode;

    /**
     * ARN 或收单参考号。
     */
    @ExcelExportColumn(order = 24, headerKey = "excel.transaction.operation.acquirerReferenceNo", width = 24)
    private String acquirerReferenceNo;

    /**
     * 渠道勾兑状态。
     */
    @ExcelExportColumn(order = 25, headerKey = "excel.transaction.common.channelMatchStatus", width = 18)
    private String channelMatchStatus;

    /**
     * 对账状态。
     */
    @ExcelExportColumn(order = 26, headerKey = "excel.transaction.common.reconciliationStatus", width = 18)
    private String reconciliationStatus;

    /**
     * 结算状态。
     */
    @ExcelExportColumn(order = 27, headerKey = "excel.transaction.common.settlementStatus", width = 18)
    private String settlementStatus;

    /**
     * 交易发生时间。
     */
    @ExcelExportColumn(order = 28, headerKey = "excel.transaction.common.transactionDateTime", width = 22)
    private LocalDateTime transactionDateTime;

    /**
     * 动作处理时间。
     */
    @ExcelExportColumn(order = 29, headerKey = "excel.transaction.operation.operationTime", width = 22)
    private LocalDateTime operationTime;
}
