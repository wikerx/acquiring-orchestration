package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FundLedgerExportRow
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端余额明细全字段核对导出行。
 * @status : create
 */
@Data
public class FundLedgerExportRow {
    /** 平台唯一余额流水号。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.fund.ledgerNo", width = 26)
    private String ledgerNo;
    /** 流水所属资金账户号。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.fund.accountNo", width = 24)
    private String accountNo;
    /** 流水所属商户号。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.fund.merchantId", width = 20)
    private String merchantId;
    /** 商户名称，仅用于管理端核对。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.fund.merchantName", width = 28)
    private String merchantName;
    /** 余额变动业务类型。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.fund.businessType", width = 18)
    private String businessType;
    /** 面向财务核对的变动摘要。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.fund.summary", width = 36)
    private String summary;
    /** 来源业务单号。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.fund.businessNo", width = 28)
    private String businessNo;
    /** AVAILABLE 或 RESERVE。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.fund.balanceType", width = 16)
    private String balanceType;
    /** CREDIT 表示增加，DEBIT 表示减少。 */
    @ExcelExportColumn(order = 9, headerKey = "excel.fund.direction", width = 12)
    private String direction;
    /** 发生金额，单位为 currency，始终为非负数。 */
    @ExcelExportColumn(order = 10, headerKey = "excel.fund.amount", width = 20)
    private BigDecimal amount;
    /** 本笔变动 ISO 4217 三位币种。 */
    @ExcelExportColumn(order = 11, headerKey = "excel.fund.currency", width = 12)
    private String currency;
    /** 入账前余额，单位为 currency，可为负。 */
    @ExcelExportColumn(order = 12, headerKey = "excel.fund.balanceBefore", width = 20)
    private BigDecimal balanceBefore;
    /** 入账后余额，单位为 currency，可为负。 */
    @ExcelExportColumn(order = 13, headerKey = "excel.fund.balanceAfter", width = 20)
    private BigDecimal balanceAfter;
    /** 同一账户内严格递增序号。 */
    @ExcelExportColumn(order = 14, headerKey = "excel.fund.accountSequence", width = 18)
    private Long accountSequence;
    /** 原操作人名称快照。 */
    @ExcelExportColumn(order = 15, headerKey = "excel.fund.operator", width = 18)
    private String operatorName;
    /** 最终复核人名称快照，允许为空。 */
    @ExcelExportColumn(order = 16, headerKey = "excel.fund.reviewer", width = 18)
    private String reviewerName;
    /** 人工操作原因，自动入账时允许为空。 */
    @ExcelExportColumn(order = 17, headerKey = "excel.fund.operationReason", width = 36)
    private String operationReason;
    /** 审核和复核意见摘要，允许为空。 */
    @ExcelExportColumn(order = 18, headerKey = "excel.fund.reviewComment", width = 36)
    private String reviewComment;
    /** 来源业务事件发生系统时间。 */
    @ExcelExportColumn(order = 19, headerKey = "excel.fund.businessTime", width = 22)
    private LocalDateTime businessTime;
    /** 可用余额实际发生变化的系统时间。 */
    @ExcelExportColumn(order = 20, headerKey = "excel.fund.postedTime", width = 22)
    private LocalDateTime postedTime;
    /** 客户端请求号，未提供时为空。 */
    @ExcelExportColumn(order = 21, headerKey = "excel.fund.requestId", width = 26)
    private String requestId;
    /** 数据库唯一资金幂等键，用于财务核对重复入账。 */
    @ExcelExportColumn(order = 22, headerKey = "excel.fund.idempotencyKey", width = 34)
    private String idempotencyKey;
}
