package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FundAccountExportRow
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端商户资金账户列表导出模型，金额均使用账户结算币种且不汇总在途多币种金额。
 * @status : create
 */
@Data
public class FundAccountExportRow {
    /** 平台资金账户号。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.fund.accountNo", width = 24)
    private String accountNo;
    /** 账户所属商户号。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.fund.merchantId", width = 20)
    private String merchantId;
    /** 商户名称快照。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.fund.merchantName", width = 28)
    private String merchantName;
    /** 账户 ISO 4217 三位结算币种。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.fund.currency", width = 14)
    private String settlementCurrency;
    /** 可用余额，允许为负数，币种为结算币种。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.fund.availableBalance", width = 20)
    private BigDecimal availableBalance;
    /** 账户状态。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.fund.status", width = 16)
    private String accountStatus;
    /** 逆向交易限制标识，1 表示已限制。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.fund.reverseRestricted", width = 18)
    private Integer reverseRestricted;
    /** 账户最后修改时间。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.fund.updateTime", width = 22)
    private LocalDateTime updateTime;
}
