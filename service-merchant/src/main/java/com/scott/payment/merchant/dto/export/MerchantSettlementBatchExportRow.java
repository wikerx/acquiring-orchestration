package com.scott.payment.merchant.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSettlementBatchExportRow
 * @date : 2026-09-01 22:40
 * @email : scott_x@163.com
 * @description : 商户结算账单导出行，只包含商户可见的批次、真实交易数、结算项目数和目标币种净入账信息。
 * @status : update
 */
@Data
public class MerchantSettlementBatchExportRow {
    /** 结算批次号，按文本导出。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.settlement.batchNo", width = 24)
    private String settlementBatchNo;
    /** 商户业务时区下的结算业务日期。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.settlement.businessDate", width = 16)
    private LocalDate businessDate;
    /** 已本地化的批次类型。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.settlement.batchType", width = 18)
    private String batchType;
    /** 已本地化的批次状态。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.settlement.status", width = 16)
    private String batchStatus;
    /** 批次目标结算币种。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.settlement.targetCurrency", width = 14)
    private String targetCurrency;
    /** 批次内去重后的真实交易数。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.settlement.transactionCount", width = 14)
    private Long transactionCount;
    /** 批次认领的结算项目数，包含交易清分修订或保证金动作。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.settlement.settlementItemCount", width = 16)
    private Integer settlementItemCount;
    /** 已本地化的商户净入账方向。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.settlement.direction", width = 12)
    private String netDirection;
    /** 目标结算币种净入账金额。 */
    @ExcelExportColumn(order = 9, headerKey = "excel.settlement.targetAmount", width = 20)
    private BigDecimal netAmount;
    /** 批次成功入账时间。 */
    @ExcelExportColumn(order = 10, headerKey = "excel.settlement.postedTime", width = 22)
    private LocalDateTime postedTime;
}
