package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data 生成管理端充值申请 Excel 的独立行模型，保留完整审批链路。
 */
@Data
public class FundRechargeExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.fund.rechargeNo", width = 26)
    private String rechargeNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.fund.merchantId", width = 20)
    private String merchantId;
    @ExcelExportColumn(order = 3, headerKey = "excel.fund.merchantName", width = 28)
    private String merchantName;
    @ExcelExportColumn(order = 4, headerKey = "excel.fund.accountNo", width = 24)
    private String accountNo;
    @ExcelExportColumn(order = 5, headerKey = "excel.fund.amount", width = 20)
    private BigDecimal amount;
    @ExcelExportColumn(order = 6, headerKey = "excel.fund.currency", width = 12)
    private String currency;
    @ExcelExportColumn(order = 7, headerKey = "excel.fund.rechargeStatus", width = 20)
    private String rechargeStatus;
    @ExcelExportColumn(order = 8, headerKey = "excel.fund.remark", width = 36)
    private String remark;
    @ExcelExportColumn(order = 9, headerKey = "excel.fund.submitter", width = 18)
    private String submitByName;
    @ExcelExportColumn(order = 10, headerKey = "excel.fund.submitTime", width = 22)
    private LocalDateTime submitTime;
    @ExcelExportColumn(order = 11, headerKey = "excel.fund.auditor", width = 18)
    private String auditByName;
    @ExcelExportColumn(order = 12, headerKey = "excel.fund.auditTime", width = 22)
    private LocalDateTime auditTime;
    @ExcelExportColumn(order = 13, headerKey = "excel.fund.rechecker", width = 18)
    private String recheckByName;
    @ExcelExportColumn(order = 14, headerKey = "excel.fund.recheckTime", width = 22)
    private LocalDateTime recheckTime;
    @ExcelExportColumn(order = 15, headerKey = "excel.fund.ledgerNo", width = 26)
    private String ledgerNo;
    @ExcelExportColumn(order = 16, headerKey = "excel.fund.postedTime", width = 22)
    private LocalDateTime postedTime;
}
