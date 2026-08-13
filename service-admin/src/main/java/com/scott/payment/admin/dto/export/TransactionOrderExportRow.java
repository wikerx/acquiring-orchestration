package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionOrderExportRow
 * @date : 2026-07-17
 * @email : scott_x@163.com
 * @description : 交易主单导出行对象，位于 service-admin 导出传输层，仅承载后台运营可见的交易生命周期汇总字段。
 * @status : create
 */
@Data
public class TransactionOrderExportRow {

    /**
     * 生命周期根交易号。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.transaction.order.rootTransactionId", width = 28)
    private String rootTransactionId;

    /**
     * 最近一次动作交易号。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.transaction.order.latestTransactionId", width = 28)
    private String latestTransactionId;

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
     * 生命周期状态。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.transaction.order.lifecycleStatus", width = 18)
    private String lifecycleStatus;

    /**
     * 当前生命周期金额。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.transaction.order.currentAmount", width = 18)
    private BigDecimal currentAmount;

    /**
     * 当前生命周期金额币种。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.transaction.common.currency", width = 12)
    private String currentCurrency;

    /**
     * 授权金额。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.transaction.order.authorizedAmount", width = 18)
    private BigDecimal authorizedAmount;

    /**
     * 请款金额。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.transaction.order.capturedAmount", width = 18)
    private BigDecimal capturedAmount;

    /**
     * 退款金额。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.transaction.order.refundedAmount", width = 18)
    private BigDecimal refundedAmount;

    /**
     * 交易汇率。
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.transaction.common.transactionRate", width = 18)
    private BigDecimal transactionRate;

    /**
     * 渠道编码。
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.transaction.common.channelCode", width = 16)
    private String channelCode;

    /**
     * 渠道订单号。
     */
    @ExcelExportColumn(order = 16, headerKey = "excel.transaction.common.channelOrderNo", width = 26)
    private String channelOrderNo;

    /**
     * 商户响应码。
     */
    @ExcelExportColumn(order = 17, headerKey = "excel.transaction.common.merchantResponseCode", width = 18)
    private String merchantResponseCode;

    /**
     * 商户响应描述。
     */
    @ExcelExportColumn(order = 18, headerKey = "excel.transaction.common.merchantResponseMessage", width = 30)
    private String merchantResponseMessage;

    /**
     * 渠道勾兑状态。
     */
    @ExcelExportColumn(order = 19, headerKey = "excel.transaction.common.channelMatchStatus", width = 18)
    private String channelMatchStatus;

    /** 本笔交易是否实际使用 3DS。 */
    @ExcelExportColumn(order = 20, headerKey = "excel.transaction.common.threeDs", width = 12)
    private String threeDs;

    /** 本笔交易是否实际启用 DCC。 */
    @ExcelExportColumn(order = 21, headerKey = "excel.transaction.common.dcc", width = 12)
    private String dcc;

    /** 本笔交易是否实际启用 EDC。 */
    @ExcelExportColumn(order = 22, headerKey = "excel.transaction.common.edc", width = 12)
    private String edc;

    /**
     * 对账状态。
     */
    @ExcelExportColumn(order = 23, headerKey = "excel.transaction.common.reconciliationStatus", width = 18)
    private String reconciliationStatus;

    /**
     * 结算状态。
     */
    @ExcelExportColumn(order = 24, headerKey = "excel.transaction.common.settlementStatus", width = 18)
    private String settlementStatus;

    /**
     * 交易发生时间。
     */
    @ExcelExportColumn(order = 25, headerKey = "excel.transaction.common.transactionDateTime", width = 22)
    private LocalDateTime transactionDateTime;
}
