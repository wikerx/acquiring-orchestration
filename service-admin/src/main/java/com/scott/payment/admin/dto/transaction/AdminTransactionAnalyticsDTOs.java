package com.scott.payment.admin.dto.transaction;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionAnalyticsDTOs
 * @date : 2026-08-07 10:00
 * @email : scott_x@163.com
 * @description : 管理端交易分析接口模型，限定首笔收单交易的时间、币种和维度统计契约，不承载数据库实体或渠道原始响应。
 * @status : create
 */
public final class AdminTransactionAnalyticsDTOs {

    private AdminTransactionAnalyticsDTOs() {
    }

    /** 管理端交易分析筛选条件，时间使用系统业务时区且最长允许查询 31 天。 */
    @Data
    public static class AnalyticsQuery {
        /** 查询开始时刻，系统业务时区，允许为空；为空时默认最近七个自然日。 */
        private LocalDateTime beginTime;
        /** 查询结束时刻，系统业务时区，允许为空；统计采用小于该时刻的半开区间。 */
        private LocalDateTime endTime;
        /** 平台商户号，非敏感筛选字段，允许为空表示全部商户。 */
        private String merchantId;
        /** 平台商户号集合，最多 50 个；用于管理端远程搜索多选，允许为空表示全部商户。 */
        private List<String> merchantIds = new ArrayList<>();
        /** 首笔交易类型，只允许 PAYMENT、AUTHORIZATION 或 PRE_AUTHORIZATION，允许为空。 */
        private String transactionType;
        /** ISO 4217 三位交易币种，允许为空；不同币种始终独立汇总。 */
        private String currency;
        /** 支付方式编码，允许为空；取值来自交易支付工具摘要。 */
        private String paymentMethod;
        /** 支付品牌编码，允许为空；银行卡可使用 VISA、MASTERCARD 等卡组织编码细分。 */
        private String paymentBrand;
        /** 发卡国家或地区编码，允许为空；取值来自合规支付工具摘要。 */
        private String issuerCountry;
        /** 查询时间所属 IANA 时区，允许为空时使用 Asia/Shanghai。 */
        private String queryTimeZone;
    }

    /** 交易总览响应，只包含平台级聚合数据和可用于图表展示的非敏感维度。 */
    @Data
    public static class OverviewResponse {
        /** 本次统计生成时间，系统业务时区，不允许为空。 */
        private LocalDateTime generatedAt;
        /** 符合筛选条件的首笔交易总数，单位为笔。 */
        private long totalCount;
        /** 终态成功交易数，单位为笔。 */
        private long successCount;
        /** 终态失败交易数，单位为笔。 */
        private long failedCount;
        /** 待处理交易数，单位为笔，不进入终态成功率分母。 */
        private long pendingCount;
        /** 处理中交易数，单位为笔，不进入终态成功率分母。 */
        private long processingCount;
        /** 终态成功率，单位为百分比，计算口径为 SUCCESS/(SUCCESS+FAILED)。 */
        private BigDecimal successRate = BigDecimal.ZERO;
        /** 成功交易金额列表，主币种单位，按币种及精度分别汇总。 */
        private List<AmountMetric> successAmounts = new ArrayList<>();
        /** 自然日交易趋势，日期连续且无数据日期补零。 */
        private List<TrendMetric> trend = new ArrayList<>();
        /** 平台交易状态分布，单位为笔。 */
        private List<DimensionMetric> statusDistribution = new ArrayList<>();
        /** 支付方式表现，包含笔数和终态成功率。 */
        private List<DimensionMetric> paymentMethods = new ArrayList<>();
        /** 发卡国家或地区表现，包含笔数和终态成功率。 */
        private List<DimensionMetric> issuerCountries = new ArrayList<>();
    }

    /** 商户表现响应，按照交易笔数降序返回受控数量的商户统计。 */
    @Data
    public static class MerchantPerformanceResponse {
        /** 本次统计生成时间，系统业务时区，不允许为空。 */
        private LocalDateTime generatedAt;
        /** 符合筛选条件且产生交易的商户数量。 */
        private long merchantCount;
        /** 商户表现列表，默认最多返回前 50 个商户。 */
        private List<MerchantMetric> merchants = new ArrayList<>();
    }

    /** 单币种成功金额指标。 */
    @Data
    public static class AmountMetric {
        /** ISO 4217 三位币种；历史缺失值以 UNKNOWN 表示。 */
        private String currency;
        /** 币种小数位精度，允许为空表示历史记录未保存精度。 */
        private Integer currencyExponent;
        /** 成功交易金额，主币种单位且使用 BigDecimal 保持数据库精度。 */
        private BigDecimal amount = BigDecimal.ZERO;
        /** 当前币种成功交易笔数，用于与成功金额保持相同筛选口径。 */
        private long successCount;
    }

    /** 单日交易趋势指标。 */
    @Data
    public static class TrendMetric {
        /** 统计日期，格式 yyyy-MM-dd，使用系统业务时区。 */
        private String date;
        /** 当日首笔交易总数，单位为笔。 */
        private long totalCount;
        /** 当日终态成功交易数，单位为笔。 */
        private long successCount;
        /** 当日终态失败交易数，单位为笔。 */
        private long failedCount;
        /** 当日待处理交易数，单位为笔，不进入终态成功率分母。 */
        private long pendingCount;
        /** 当日处理中交易数，单位为笔，不进入终态成功率分母。 */
        private long processingCount;
        /** 当日终态成功率，单位为百分比。 */
        private BigDecimal successRate = BigDecimal.ZERO;
    }

    /** 通用维度表现指标，用于状态、支付方式和发卡国家或地区图表。 */
    @Data
    public static class DimensionMetric {
        /** 维度编码，允许使用 UNKNOWN 表示历史缺失值。 */
        private String key;
        /** 支付方式编码，仅支付工具维度返回，其他维度允许为空。 */
        private String paymentMethod;
        /** 支付品牌编码，仅支付工具维度返回，其他维度允许为空。 */
        private String paymentBrand;
        /** 当前维度首笔交易总数，单位为笔。 */
        private long totalCount;
        /** 当前维度终态成功交易数，单位为笔。 */
        private long successCount;
        /** 当前维度终态失败交易数，单位为笔。 */
        private long failedCount;
        /** 当前维度待处理交易数，单位为笔。 */
        private long pendingCount;
        /** 当前维度处理中交易数，单位为笔。 */
        private long processingCount;
        /** 当前维度终态成功率，单位为百分比。 */
        private BigDecimal successRate = BigDecimal.ZERO;
    }

    /** 单商户交易表现指标，不包含商户密钥、渠道 MID 或渠道原始响应。 */
    @Data
    public static class MerchantMetric {
        /** 平台商户号，非敏感业务标识，不允许为空。 */
        private String merchantId;
        /** 当前商户首笔交易总数，单位为笔。 */
        private long totalCount;
        /** 当前商户终态成功交易数，单位为笔。 */
        private long successCount;
        /** 当前商户终态失败交易数，单位为笔。 */
        private long failedCount;
        /** 当前商户待处理及处理中交易数，单位为笔。 */
        private long inFlightCount;
        /** 当前商户终态成功率，单位为百分比。 */
        private BigDecimal successRate = BigDecimal.ZERO;
        /** 当前商户成功交易金额，主币种单位且按币种分别汇总。 */
        private List<AmountMetric> successAmounts = new ArrayList<>();
    }
}
