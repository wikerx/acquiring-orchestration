package com.scott.payment.job.dto.exchange;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 汇率拉取任务 DTO 集合。
 *
 * <p>用于任务参数、Provider 解析结果和拉取统计结果，不作为管理端 API 模型暴露。</p>
 */
public final class ExchangeRateFetchDTOs {

    private ExchangeRateFetchDTOs() {
    }

    /**
     * 汇率拉取任务参数。
     */
    @Data
    public static class ExchangeRateFetchRequest {
        private String sourceCode;
        /** dryRun 为 true 时只解析和校验，不写入原始汇率记录。 */
        private Boolean dryRun;
    }

    /**
     * 汇率源解析出的单条原始报价。
     */
    @Data
    public static class RawRateItem {
        /** 外部汇率源返回的币种名称，例如中行页面中的“美元”。 */
        private String sourceCurrencyName;
        /** 平台 ISO 4217 原始币种代码，由汇率源币种映射表补齐。 */
        private String baseCurrency;
        /** 平台 ISO 4217 目标币种代码，中行来源固定为 CNY。 */
        private String quoteCurrency;
        /** 现钞买入价，统一换算为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        private BigDecimal cashBuyRate;
        /** 现钞卖出价，统一换算为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        private BigDecimal cashSellRate;
        /** 现汇买入价，统一换算为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        private BigDecimal spotBuyRate;
        /** 现汇卖出价，统一换算为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        private BigDecimal spotSellRate;
        /** 中间折算价，统一换算为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        private BigDecimal middleRate;
        /** 外部汇率源发布时间，入库时保留 DATETIME(3)。 */
        private LocalDateTime publishTime;
        private String skipReason;
    }

    /**
     * 汇率拉取任务执行统计结果。
     */
    @Data
    public static class ExchangeRateFetchResult {
        private String batchNo;
        private String sourceCode;
        private String fetchStatus;
        private int totalCount;
        private int successCount;
        private int duplicateCount;
        private int skipCount;
        private String errorMessage;
        private List<String> warnings = new ArrayList<>();
    }
}
