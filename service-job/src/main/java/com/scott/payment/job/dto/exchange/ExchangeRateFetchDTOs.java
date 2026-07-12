package com.scott.payment.job.dto.exchange;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateFetchDTOs
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 汇率管理Exchange Rate Fetch  DTO 集合，位于 service-job 的接口传输层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class ExchangeRateFetchDTOs {

    private ExchangeRateFetchDTOs() {
    }

    /**
     * 汇率拉取任务参数。
     */
    @Data
    public static class ExchangeRateFetchRequest {
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sourceCode;

        /** dryRun 为 true 时只解析和校验，不写入原始汇率记录。 */
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Boolean dryRun;
    }

    /**
     * 汇率源解析出的单条原始报价。
     */
    @Data
    public static class RawRateItem {

        /** 外部汇率源返回的币种名称，例如中行页面中的“美元”。 */
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String sourceCurrencyName;

        /** 平台 ISO 4217 原始币种代码，由汇率源币种映射表补齐。 */
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String baseCurrency;

        /** 平台 ISO 4217 目标币种代码，中行来源固定为 CNY。 */
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String quoteCurrency;

        /** 现钞买入价，统一换算为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal cashBuyRate;

        /** 现钞卖出价，统一换算为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal cashSellRate;

        /** 现汇买入价，统一换算为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal spotBuyRate;

        /** 现汇卖出价，统一换算为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal spotSellRate;

        /** 中间折算价，统一换算为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal middleRate;

        /** 外部汇率源发布时间，入库时保留 DATETIME(3)。 */
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime publishTime;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String skipReason;
    }

    /**
     * 汇率拉取任务执行统计结果。
     */
    @Data
    public static class ExchangeRateFetchResult {
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String batchNo;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sourceCode;
        /**
         * 汇率管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private String fetchStatus;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private int totalCount;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private int successCount;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private int duplicateCount;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private int skipCount;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String errorMessage;
        private List<String> warnings = new ArrayList<>();
    }
}
