package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FundRechargeExportRow
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端充值申请及审批链路导出行。
 * @status : create
 */
@Data
public class FundRechargeExportRow {
    /** 平台唯一充值申请号。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.fund.rechargeNo", width = 26)
    private String rechargeNo;
    /** 账户所属商户号。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.fund.merchantId", width = 20)
    private String merchantId;
    /** 商户名称，仅用于管理端核对。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.fund.merchantName", width = 28)
    private String merchantName;
    /** 充值目标资金账户号。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.fund.accountNo", width = 24)
    private String accountNo;
    /** 充值金额，单位为 currency。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.fund.amount", width = 20)
    private BigDecimal amount;
    /** 充值账户 ISO 4217 三位结算币种。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.fund.currency", width = 12)
    private String currency;
    /** PENDING_AUDIT、PENDING_RECHECK、POSTED 或 REJECTED。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.fund.rechargeStatus", width = 20)
    private String rechargeStatus;
    /** 充值原因或凭证摘要。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.fund.remark", width = 36)
    private String remark;
    /** 提交人名称快照。 */
    @ExcelExportColumn(order = 9, headerKey = "excel.fund.submitter", width = 18)
    private String submitByName;
    /** 提交系统时间。 */
    @ExcelExportColumn(order = 10, headerKey = "excel.fund.submitTime", width = 22)
    private LocalDateTime submitTime;
    /** 审核人名称快照，待审核时为空。 */
    @ExcelExportColumn(order = 11, headerKey = "excel.fund.auditor", width = 18)
    private String auditByName;
    /** 审核系统时间，待审核时为空。 */
    @ExcelExportColumn(order = 12, headerKey = "excel.fund.auditTime", width = 22)
    private LocalDateTime auditTime;
    /** 复核人名称快照，未复核时为空。 */
    @ExcelExportColumn(order = 13, headerKey = "excel.fund.rechecker", width = 18)
    private String recheckByName;
    /** 复核系统时间，未复核时为空。 */
    @ExcelExportColumn(order = 14, headerKey = "excel.fund.recheckTime", width = 22)
    private LocalDateTime recheckTime;
    /** 最终入账流水号，未完成复核时为空。 */
    @ExcelExportColumn(order = 15, headerKey = "excel.fund.ledgerNo", width = 26)
    private String ledgerNo;
    /** 最终入账系统时间，未完成复核时为空。 */
    @ExcelExportColumn(order = 16, headerKey = "excel.fund.postedTime", width = 22)
    private LocalDateTime postedTime;
}
