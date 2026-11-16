package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data 生成管理端资金账户 Excel 的独立行模型，金额保持账户结算币种事实。
 */
@Data
public class FundAccountExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.fund.accountNo", width = 24)
    private String accountNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.fund.merchantId", width = 20)
    private String merchantId;
    @ExcelExportColumn(order = 3, headerKey = "excel.fund.merchantName", width = 28)
    private String merchantName;
    @ExcelExportColumn(order = 4, headerKey = "excel.fund.currency", width = 14)
    private String settlementCurrency;
    @ExcelExportColumn(order = 5, headerKey = "excel.fund.availableBalance", width = 20)
    private BigDecimal availableBalance;
    @ExcelExportColumn(order = 6, headerKey = "excel.fund.status", width = 16)
    private String accountStatus;
    @ExcelExportColumn(order = 7, headerKey = "excel.fund.reverseRestricted", width = 18)
    private String reverseRestricted;
    @ExcelExportColumn(order = 8, headerKey = "excel.fund.updateTime", width = 22)
    private LocalDateTime updateTime;
}
