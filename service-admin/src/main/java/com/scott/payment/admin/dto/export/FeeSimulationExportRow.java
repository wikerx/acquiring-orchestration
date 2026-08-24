package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理端费用试算逐项明细导出，保留输入、汇率、费用和净结算完整快照。 */
@Data
public class FeeSimulationExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.fee.simulationNo", width = 24)
    private String simulationNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.fee.lineNo", width = 10)
    private Integer lineNo;
    @ExcelExportColumn(order = 3, headerKey = "excel.fee.merchantId", width = 20)
    private String merchantId;
    @ExcelExportColumn(order = 4, headerKey = "excel.fee.planVersionId", width = 16)
    private Long planVersionId;
    @ExcelExportColumn(order = 5, headerKey = "excel.fee.transactionType", width = 20)
    private String transactionType;
    @ExcelExportColumn(order = 6, headerKey = "excel.fee.paymentType", width = 20)
    private String paymentType;
    @ExcelExportColumn(order = 7, headerKey = "excel.fee.paymentMethod", width = 20)
    private String paymentMethod;
    @ExcelExportColumn(order = 8, headerKey = "excel.fee.selectedRiskServices", width = 24)
    private String selectedRiskServices;
    @ExcelExportColumn(order = 9, headerKey = "excel.fee.labelAmount", width = 18)
    private BigDecimal labelAmount;
    @ExcelExportColumn(order = 10, headerKey = "excel.fee.labelCurrency", width = 14)
    private String labelCurrency;
    @ExcelExportColumn(order = 11, headerKey = "excel.fee.labelToUsdRate", width = 18)
    private BigDecimal labelToUsdRate;
    @ExcelExportColumn(order = 12, headerKey = "excel.fee.itemType", width = 16)
    private String itemType;
    @ExcelExportColumn(order = 13, headerKey = "excel.fee.feeCategory", width = 22)
    private String feeCategory;
    @ExcelExportColumn(order = 14, headerKey = "excel.fee.calculationStatus", width = 18)
    private String calculationStatus;
    @ExcelExportColumn(order = 15, headerKey = "excel.fee.includedInFeeTotal", width = 16)
    private String includedInFeeTotal;
    @ExcelExportColumn(order = 16, headerKey = "excel.fee.riskServiceType", width = 18)
    private String riskServiceType;
    @ExcelExportColumn(order = 17, headerKey = "excel.fee.ruleName", width = 24)
    private String ruleName;
    @ExcelExportColumn(order = 18, headerKey = "excel.fee.feeMode", width = 16)
    private String feeMode;
    @ExcelExportColumn(order = 19, headerKey = "excel.fee.chargeTrigger", width = 20)
    private String chargeTrigger;
    @ExcelExportColumn(order = 20, headerKey = "excel.fee.appliedLimit", width = 16)
    private String appliedLimit;
    @ExcelExportColumn(order = 21, headerKey = "excel.fee.rawFeeUsd", width = 18)
    private BigDecimal rawFeeUsd;
    @ExcelExportColumn(order = 22, headerKey = "excel.fee.detailFeeUsd", width = 18)
    private BigDecimal detailFeeUsd;
    @ExcelExportColumn(order = 23, headerKey = "excel.fee.detailFormula", width = 42)
    private String detailFormula;
    @ExcelExportColumn(order = 24, headerKey = "excel.fee.finalFeeUsd", width = 18)
    private BigDecimal finalFeeUsd;
    @ExcelExportColumn(order = 25, headerKey = "excel.fee.reserveAmountUsd", width = 18)
    private BigDecimal reserveAmountUsd;
    @ExcelExportColumn(order = 26, headerKey = "excel.fee.netSettlementUsd", width = 20)
    private BigDecimal netSettlementUsd;
    @ExcelExportColumn(order = 27, headerKey = "excel.fee.feeTotalFormula", width = 42)
    private String feeTotalFormula;
    @ExcelExportColumn(order = 28, headerKey = "excel.fee.netSettlementFormula", width = 46)
    private String netSettlementFormula;
    @ExcelExportColumn(order = 29, headerKey = "excel.fee.rateSource", width = 22)
    private String rateSource;
    @ExcelExportColumn(order = 30, headerKey = "excel.fee.rateEffectiveTime", width = 22)
    private LocalDateTime rateEffectiveTime;
    @ExcelExportColumn(order = 31, headerKey = "excel.fee.rateValuationTime", width = 22)
    private LocalDateTime rateValuationTime;
    @ExcelExportColumn(order = 32, headerKey = "excel.fee.detailSnapshotStatus", width = 20)
    private String detailSnapshotStatus;
    @ExcelExportColumn(order = 33, headerKey = "excel.fee.operator", width = 18)
    private String operatorName;
    @ExcelExportColumn(order = 34, headerKey = "excel.fee.createTime", width = 22)
    private LocalDateTime createTime;
}
