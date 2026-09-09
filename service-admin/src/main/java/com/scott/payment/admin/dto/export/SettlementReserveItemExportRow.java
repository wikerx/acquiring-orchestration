package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReserveItemExportRow
 * @date : 2026-09-01 23:10
 * @email : scott_x@163.com
 * @description : Admin 保证金不可变动作与当前责任导出行，保持原标签币种且不展示或推导任何汇率。
 * @status : update
 */
@Data
public class SettlementReserveItemExportRow {
    /** 动作资金化所属结算批次号。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.settlement.batchNo", width = 24)
    private String settlementBatchNo;
    /** 批次业务日期。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.settlement.businessDate", width = 16)
    private LocalDate businessDate;
    /** 商户号。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.settlement.merchantId", width = 20)
    private String merchantId;
    /** 保证金责任编号。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.settlement.reserveNo", width = 26)
    private String reserveNo;
    /** 保证金来源真实平台交易号。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.settlement.transactionId", width = 28, forceText = true)
    private String sourceTransactionId;
    /** 来源交易时间。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.settlement.transactionTime", width = 22)
    private LocalDateTime sourceTransactionDateTime;
    /** 已本地化的保证金动作类型。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.settlement.reserveAction", width = 20)
    private String actionType;
    /** 已本地化的保证金责任方向。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.settlement.direction", width = 12)
    private String direction;
    /** 保证金原标签币种。 */
    @ExcelExportColumn(order = 9, headerKey = "excel.settlement.sourceCurrency", width = 14)
    private String currency;
    /** 当前不可变动作金额。 */
    @ExcelExportColumn(order = 10, headerKey = "excel.settlement.sourceAmount", width = 20)
    private BigDecimal amount;
    /** 动作后的保证金剩余责任金额。 */
    @ExcelExportColumn(order = 11, headerKey = "excel.settlement.reserveRemaining", width = 20)
    private BigDecimal remainingAmount;
    /** 已本地化的保证金责任状态。 */
    @ExcelExportColumn(order = 12, headerKey = "excel.settlement.reserveStatus", width = 18)
    private String reserveStatus;
    /** 预计可释放业务日期。 */
    @ExcelExportColumn(order = 13, headerKey = "excel.settlement.expectedReleaseDate", width = 18)
    private LocalDate expectedReleaseDate;
    /** 当前不可变动作发生时间。 */
    @ExcelExportColumn(order = 14, headerKey = "excel.settlement.reserveActionTime", width = 22)
    private LocalDateTime actionTime;
}
