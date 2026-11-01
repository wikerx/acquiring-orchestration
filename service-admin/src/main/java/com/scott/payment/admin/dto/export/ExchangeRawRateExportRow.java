package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 原始汇率导出行模型，用于运营后台，定义 Excel 列及运营可见值，不承载数据库写入规则。
 */
@Data
public class ExchangeRawRateExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.sourceCode", width = 18)
    private String sourceCode;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;

    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.raw.spotBuyRate", width = 18)
    private BigDecimal spotBuyRate;

    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.raw.spotSellRate", width = 18)
    private BigDecimal spotSellRate;

    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.raw.cashBuyRate", width = 18)
    private BigDecimal cashBuyRate;

    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.raw.cashSellRate", width = 18)
    private BigDecimal cashSellRate;

    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.raw.middleRate", width = 18)
    private BigDecimal middleRate;

    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.raw.publishTime", width = 22)
    private LocalDateTime publishTime;

    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.raw.fetchTime", width = 22)
    private LocalDateTime fetchTime;

    /**
     * 业务配置或汇率开始生效的具体时刻。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.common.effectiveTime", width = 22)
    private LocalDateTime effectiveTime;

    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.raw.createMethod", width = 16)
    private String createMethod;

    /**
     * 批次号，用于关联同一次导入、抓取、清分或结算处理的记录。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.raw.batchNo", width = 18)
    private String batchNo;

    @ExcelExportColumn(order = 14, headerKey = "excel.exchange.common.rateStatus", width = 14)
    private String rateStatus;

    @ExcelExportColumn(order = 15, headerKey = "excel.exchange.raw.voidReason", width = 30)
    private String voidReason;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    @ExcelExportColumn(order = 16, headerKey = "excel.exchange.common.createTime", width = 22)
    private LocalDateTime createTime;
}
