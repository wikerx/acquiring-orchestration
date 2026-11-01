package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业务汇率导出行模型，用于运营后台，定义 Excel 列及运营可见值，不承载数据库写入规则。
 */
@Data
public class ExchangeBusinessRateExportRow {

    /**
     * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;

    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.common.sourceCode", width = 18)
    private String sourceCode;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;

    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.business.originalRate", width = 18)
    private BigDecimal originalRate;

    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.business.finalRate", width = 18)
    private BigDecimal finalRate;

    /**
     * 业务配置或汇率开始生效的具体时刻。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.common.effectiveTime", width = 22)
    private LocalDateTime effectiveTime;

    /**
     * 业务配置、令牌或缓存条目的失效时刻。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.business.expireTime", width = 22)
    private LocalDateTime expireTime;

    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.business.generateMethod", width = 16)
    private String generateMethod;

    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.common.rateStatus", width = 14)
    private String rateStatus;

    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.business.rawRateId", width = 14)
    private Long rawRateId;

    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.business.ruleId", width = 14)
    private Long ruleId;

    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.business.adjustDescription", width = 40)
    private String adjustDescription;

    /**
     * 收单支付备注字段，用于记录人工说明，不参与核心状态流转。
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.exchange.common.remark", width = 30)
    private String remark;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.exchange.common.createTime", width = 22)
    private LocalDateTime createTime;
}
