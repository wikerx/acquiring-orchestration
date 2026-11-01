package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端费用试算逐项明细导出，保留输入、汇率、费用和净结算完整快照。
 */
@Data
public class FeeSimulationExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.fee.simulationNo", width = 24)
    private String simulationNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.fee.lineNo", width = 10)
    private Integer lineNo;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.fee.merchantId", width = 20)
    private String merchantId;
    @ExcelExportColumn(order = 4, headerKey = "excel.fee.planVersionId", width = 16)
    private Long planVersionId;
    /**
     * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
     */
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
    /**
     * 费用类别，用于区分交易手续费、退款费、风控费、争议费和结算换汇费。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.fee.feeCategory", width = 22)
    private String feeCategory;
    @ExcelExportColumn(order = 14, headerKey = "excel.fee.calculationStatus", width = 18)
    private String calculationStatus;
    /**
     * 是否计入费用合计；1 表示计入，0 表示仅展示该费用明细。
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.fee.includedInFeeTotal", width = 16)
    private String includedInFeeTotal;
    /**
     * 风控服务类型，用于区分内部风控、外部风控和 3DS 服务费用。
     */
    @ExcelExportColumn(order = 16, headerKey = "excel.fee.riskServiceType", width = 18)
    private String riskServiceType;
    /**
     * 费用规则名称，用于运营识别同一费用版本内的原子匹配规则。
     */
    @ExcelExportColumn(order = 17, headerKey = "excel.fee.ruleName", width = 24)
    private String ruleName;
    /**
     * 费用计算模式，决定当前规则采用标准费率还是阶梯费率。
     */
    @ExcelExportColumn(order = 18, headerKey = "excel.fee.feeMode", width = 16)
    private String feeMode;
    /**
     * 计费触发点，明确费用在请求、成功、失败或其它受控事件发生时计提。
     */
    @ExcelExportColumn(order = 19, headerKey = "excel.fee.chargeTrigger", width = 20)
    private String chargeTrigger;
    /**
     * 费用上下限应用结果，用于标识未触发限制、命中最低费用或命中最高费用。
     */
    @ExcelExportColumn(order = 20, headerKey = "excel.fee.appliedLimit", width = 16)
    private String appliedLimit;
    /**
     * 百分比费用换算为 USD 后与固定单笔费相加得到的原始费用，尚未应用最低和最高限制。
     */
    @ExcelExportColumn(order = 21, headerKey = "excel.fee.rawFeeUsd", width = 18)
    private BigDecimal rawFeeUsd;
    @ExcelExportColumn(order = 22, headerKey = "excel.fee.detailFeeUsd", width = 18)
    private BigDecimal detailFeeUsd;
    @ExcelExportColumn(order = 23, headerKey = "excel.fee.detailFormula", width = 42)
    private String detailFormula;
    /**
     * 应用最低和最高限制后的最终费用，币种恒为 USD。
     */
    @ExcelExportColumn(order = 24, headerKey = "excel.fee.finalFeeUsd", width = 18)
    private BigDecimal finalFeeUsd;
    /**
     * 试算保证金金额，按规则计算并换算为 USD，仅用于预览不产生资金流水。
     */
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
    /**
     * 执行本次管理操作时的账号显示名称快照，用于操作审计。
     */
    @ExcelExportColumn(order = 33, headerKey = "excel.fee.operator", width = 18)
    private String operatorName;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    @ExcelExportColumn(order = 34, headerKey = "excel.fee.createTime", width = 22)
    private LocalDateTime createTime;
}
