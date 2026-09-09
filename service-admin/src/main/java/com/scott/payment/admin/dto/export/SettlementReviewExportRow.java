package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewExportRow
 * @date : 2026-09-01 23:10
 * @email : scott_x@163.com
 * @description : Admin 结算预审单审批附件导出行，包含选择规模、目标币种净额和 Maker-Checker 审计结果。
 * @status : update
 */
@Data
public class SettlementReviewExportRow {
    /** 结算预审单号。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.settlement.reviewOrderNo", width = 24)
    private String reviewOrderNo;
    /** 已本地化的交易或保证金预审类型。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.settlement.reviewType", width = 18)
    private String reviewType;
    /** 已本地化的人工或自动创建模式。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.settlement.createMode", width = 16)
    private String createMode;
    /** 商户号。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.settlement.merchantId", width = 20)
    private String merchantId;
    /** 商户业务时区下的结算业务日期。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.settlement.businessDate", width = 16)
    private LocalDate businessDate;
    /** 预审目标结算币种。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.settlement.targetCurrency", width = 14)
    private String targetCurrency;
    /** 预审选择的候选项目数。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.settlement.candidateCount", width = 14)
    private Integer candidateCount;
    /** 已本地化的商户净额方向。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.settlement.netDirection", width = 14)
    private String netDirection;
    /** 目标结算币种预审净额。 */
    @ExcelExportColumn(order = 9, headerKey = "excel.settlement.netAmount", width = 20)
    private BigDecimal netAmount;
    /** 已本地化的预审状态。 */
    @ExcelExportColumn(order = 10, headerKey = "excel.settlement.reviewStatus", width = 18)
    private String reviewStatus;
    /** 可信提交人名称。 */
    @ExcelExportColumn(order = 11, headerKey = "excel.settlement.submitter", width = 20)
    private String submittedByAccountName;
    /** 预审提交时间。 */
    @ExcelExportColumn(order = 12, headerKey = "excel.settlement.submittedTime", width = 22)
    private LocalDateTime submittedTime;
    /** 可信复核人名称，待审批时可空。 */
    @ExcelExportColumn(order = 13, headerKey = "excel.settlement.reviewer", width = 20)
    private String decidedByAccountName;
    /** 已本地化的审批动作。 */
    @ExcelExportColumn(order = 14, headerKey = "excel.settlement.decisionAction", width = 16)
    private String decisionAction;
    /** 审批决定时间。 */
    @ExcelExportColumn(order = 15, headerKey = "excel.settlement.decisionTime", width = 22)
    private LocalDateTime decisionTime;
    /** 审批通过后生成的结算批次号，可空。 */
    @ExcelExportColumn(order = 16, headerKey = "excel.settlement.batchNo", width = 24)
    private String settlementBatchNo;
}
