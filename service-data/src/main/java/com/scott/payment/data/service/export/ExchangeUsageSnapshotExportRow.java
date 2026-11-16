package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data 生成管理端汇率使用快照 Excel 的独立行模型，已使用汇率保持业务固化原值。
 */
@Data
public class ExchangeUsageSnapshotExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;
    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.snapshot.usageEventId", width = 32)
    private String usageEventId;
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.snapshot.producerService", width = 20)
    private String producerService;
    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.snapshot.usageScene", width = 16)
    private String usageScene;
    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.snapshot.businessType", width = 18)
    private String businessType;
    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.snapshot.businessNo", width = 28)
    private String businessNo;
    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;
    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;
    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.snapshot.usedRate", width = 18)
    private BigDecimal usedRate;
    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.business.rawRateId", width = 14)
    private Long rawRateId;
    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.business.ruleId", width = 14)
    private Long ruleId;
    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.snapshot.businessRateId", width = 16)
    private Long businessRateId;
    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.snapshot.rateSource", width = 18)
    private String rateSource;
    @ExcelExportColumn(order = 14, headerKey = "excel.exchange.snapshot.sourceQuoteDirection", width = 18)
    private String sourceQuoteDirection;
    @ExcelExportColumn(order = 15, headerKey = "excel.exchange.snapshot.rateEffectiveTime", width = 22)
    private LocalDateTime rateEffectiveTime;
    @ExcelExportColumn(order = 16, headerKey = "excel.exchange.snapshot.appliedTime", width = 22)
    private LocalDateTime appliedTime;
    @ExcelExportColumn(order = 17, headerKey = "excel.exchange.snapshot.calculationDescription", width = 40)
    private String calculationDescription;
    @ExcelExportColumn(order = 18, headerKey = "excel.exchange.common.createTime", width = 22)
    private LocalDateTime createTime;
}
