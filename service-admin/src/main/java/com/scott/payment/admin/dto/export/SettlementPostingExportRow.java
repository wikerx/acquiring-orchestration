package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementPostingExportRow
 * @date : 2026-09-01 23:10
 * @email : scott_x@163.com
 * @description : Admin 结算净额资金流水导出行，包含余额前后值、账户序列、人工操作审计、幂等键和冲正关联。
 * @status : update
 */
@Data
public class SettlementPostingExportRow {
    /** 不可变资金流水号。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.settlement.ledgerNo", width = 28)
    private String ledgerNo;
    /** 结算批次号。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.settlement.batchNo", width = 24)
    private String settlementBatchNo;
    /** 商户号。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.settlement.merchantId", width = 20)
    private String merchantId;
    /** 入账资金账户主键。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.settlement.accountId", width = 18)
    private Long accountId;
    /** 已本地化的结算入账或冲正业务类型。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.settlement.businessType", width = 22)
    private String businessType;
    /** 已本地化的账户金额方向。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.settlement.direction", width = 12)
    private String direction;
    /** 当前流水金额。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.settlement.amount", width = 20)
    private BigDecimal amount;
    /** 账户和流水币种。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.settlement.currency", width = 12)
    private String currency;
    /** 入账前余额。 */
    @ExcelExportColumn(order = 9, headerKey = "excel.settlement.balanceBefore", width = 20)
    private BigDecimal balanceBefore;
    /** 入账后余额。 */
    @ExcelExportColumn(order = 10, headerKey = "excel.settlement.balanceAfter", width = 20)
    private BigDecimal balanceAfter;
    /** 账户级单调递增序列。 */
    @ExcelExportColumn(order = 11, headerKey = "excel.settlement.accountSequence", width = 18)
    private Long accountSequence;
    /** 已本地化的自动或人工操作模式。 */
    @ExcelExportColumn(order = 12, headerKey = "excel.settlement.operationMode", width = 16)
    private String operationMode;
    /** 可信操作人名称，自动批次可为系统身份。 */
    @ExcelExportColumn(order = 13, headerKey = "excel.settlement.operator", width = 20)
    private String operatorName;
    /** Maker-Checker 复核人名称，非人工流程可空。 */
    @ExcelExportColumn(order = 14, headerKey = "excel.settlement.reviewer", width = 20)
    private String reviewerName;
    /** 人工操作原因。 */
    @ExcelExportColumn(order = 15, headerKey = "excel.settlement.operationReason", width = 36)
    private String operationReason;
    /** 复核意见。 */
    @ExcelExportColumn(order = 16, headerKey = "excel.settlement.reviewComment", width = 36)
    private String reviewComment;
    /** 资金成功入账时间。 */
    @ExcelExportColumn(order = 17, headerKey = "excel.settlement.postedTime", width = 22)
    private LocalDateTime postedTime;
    /** 资金流水唯一幂等键。 */
    @ExcelExportColumn(order = 18, headerKey = "excel.settlement.idempotencyKey", width = 36)
    private String idempotencyKey;
    /** 被当前流水冲正的原流水主键，非冲正为空。 */
    @ExcelExportColumn(order = 19, headerKey = "excel.settlement.reversalOfLedgerId", width = 22)
    private Long reversalOfLedgerId;
}
