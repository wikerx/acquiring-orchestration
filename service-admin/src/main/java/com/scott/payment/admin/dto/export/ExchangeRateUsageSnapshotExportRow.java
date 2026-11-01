package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇率使用快照导出行模型，用于运营后台，定义 Excel 列及运营可见值，不承载数据库写入规则。
 */
@Data
public class ExchangeRateUsageSnapshotExportRow {

    /**
     * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;

    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.snapshot.usageEventId", width = 32)
    private String usageEventId;

    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.snapshot.producerService", width = 20)
    private String producerService;

    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.snapshot.usageScene", width = 16)
    private String usageScene;

    /**
     * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.snapshot.businessType", width = 18)
    private String businessType;

    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.snapshot.businessNo", width = 28)
    private String businessNo;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
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

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    @ExcelExportColumn(order = 18, headerKey = "excel.exchange.common.createTime", width = 22)
    private LocalDateTime createTime;
}
