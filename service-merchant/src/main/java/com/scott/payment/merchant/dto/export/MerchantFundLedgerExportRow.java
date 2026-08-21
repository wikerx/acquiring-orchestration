package com.scott.payment.merchant.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFundLedgerExportRow
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端余额明细核对导出行，不包含平台内部幂等和追踪字段。
 * @status : create
 */
@Data
public class MerchantFundLedgerExportRow {
    /** 平台唯一余额流水号。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.fund.ledgerNo", width = 26)
    private String ledgerNo;
    /** 余额变动业务类型。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.fund.businessType", width = 18)
    private String businessType;
    /** 面向商户核对的变动摘要。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.fund.summary", width = 36)
    private String summary;
    /** 来源业务单号。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.fund.businessNo", width = 28)
    private String businessNo;
    /** AVAILABLE 或 RESERVE。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.fund.balanceType", width = 16)
    private String balanceType;
    /** CREDIT 表示增加，DEBIT 表示减少。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.fund.direction", width = 12)
    private String direction;
    /** 发生金额，单位为 currency，始终为非负数。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.fund.amount", width = 20)
    private BigDecimal amount;
    /** 本笔变动 ISO 4217 三位币种。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.fund.currency", width = 12)
    private String currency;
    /** 入账前余额，单位为 currency，可为负。 */
    @ExcelExportColumn(order = 9, headerKey = "excel.fund.balanceBefore", width = 20)
    private BigDecimal balanceBefore;
    /** 入账后余额，单位为 currency，可为负。 */
    @ExcelExportColumn(order = 10, headerKey = "excel.fund.balanceAfter", width = 20)
    private BigDecimal balanceAfter;
    /** 原操作人名称快照。 */
    @ExcelExportColumn(order = 11, headerKey = "excel.fund.operator", width = 18)
    private String operatorName;
    /** 最终复核人名称快照，允许为空。 */
    @ExcelExportColumn(order = 12, headerKey = "excel.fund.reviewer", width = 18)
    private String reviewerName;
    /** 可用余额实际发生变化的系统时间。 */
    @ExcelExportColumn(order = 13, headerKey = "excel.fund.postedTime", width = 22)
    private LocalDateTime postedTime;
}
