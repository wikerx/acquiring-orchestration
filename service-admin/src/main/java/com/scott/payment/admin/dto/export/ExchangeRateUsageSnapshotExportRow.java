package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇率使用快照导出行对象。
 */
@Data
public class ExchangeRateUsageSnapshotExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;

    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.snapshot.usageScene", width = 16)
    private String usageScene;

    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.snapshot.businessType", width = 18)
    private String businessType;

    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.snapshot.businessNo", width = 28)
    private String businessNo;

    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;

    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;

    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.snapshot.usedRate", width = 18)
    private BigDecimal usedRate;

    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.business.rawRateId", width = 14)
    private Long rawRateId;

    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.business.ruleId", width = 14)
    private Long ruleId;

    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.snapshot.businessRateId", width = 16)
    private Long businessRateId;

    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.snapshot.appliedTime", width = 22)
    private LocalDateTime appliedTime;

    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.snapshot.calculationDescription", width = 40)
    private String calculationDescription;

    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.common.createTime", width = 22)
    private LocalDateTime createTime;
}
