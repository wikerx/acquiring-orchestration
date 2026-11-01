package com.scott.payment.job.dto.exchange;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Exchange Rate Fetch DTOs 聚合类型，位于 调度任务服务，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 */
public final class ExchangeRateFetchDTOs {

    /**
     * 禁止实例化汇率拉取协议聚合类型。
     */
    private ExchangeRateFetchDTOs() {
    }

    /**
     * 汇率拉取任务参数。
     */
    @Data
    public static class ExchangeRateFetchRequest {

        private String sourceCode;


        private Boolean dryRun;
    }

    /**
     * 汇率源解析出的单条原始报价。
     */
    @Data
    public static class RawRateItem {

        /**
         * 外部汇率源返回的币种名称，例如中行页面中的“美元”。
         */

        private String sourceCurrencyName;

        /**
         * 平台 ISO 4217 原始币种代码，由汇率源币种映射表补齐。
         */

        private String baseCurrency;

        /**
         * 平台 ISO 4217 目标币种代码，中行来源固定为 CNY。
         */

        private String quoteCurrency;

        /**
         * 现钞买入价，方向为一单位原始币种兑换目标币种。
         */
        private BigDecimal cashBuyRate;

        /**
         * 现钞卖出价，方向为一单位原始币种兑换目标币种。
         */
        private BigDecimal cashSellRate;

        /**
         * 现汇买入价，方向为一单位原始币种兑换目标币种。
         */
        private BigDecimal spotBuyRate;

        /**
         * 现汇卖出价，方向为一单位原始币种兑换目标币种。
         */
        private BigDecimal spotSellRate;


        private BigDecimal middleRate;

        /**
         * 外部汇率源公布该报价的业务时间。
         */
        private LocalDateTime publishTime;
        /**
         * 当前报价未进入持久化或业务汇率生成时的可审计原因。
         */
        private String skipReason;
    }

    /**
     * 汇率拉取任务执行统计结果。
     */
    @Data
    public static class ExchangeRateFetchResult {
        /**
         * 批次号，用于关联同一次导入、抓取、清分或结算处理的记录。
         */
        private String batchNo;

        private String sourceCode;

        private String fetchStatus;

        private int totalCount;

        private int successCount;

        private int duplicateCount;

        private int skipCount;
        /**
         * 内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。
         */
        private String errorMessage;
        /**
         * {@code warnings}集合，承载 {@code ExchangeRateFetchResult} 当前请求或响应中的多值数据。
         */
        private List<String> warnings = new ArrayList<>();
    }
}
