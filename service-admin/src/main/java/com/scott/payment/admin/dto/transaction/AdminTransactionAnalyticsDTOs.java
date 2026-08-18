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

    /** 管理端失败分析响应，使用后台可见失败原因且不向商户侧接口暴露。 */
    @Data
    public static class FailureResponse {
        /** 本次统计生成时间，使用页面查询时区。 */
        private LocalDateTime generatedAt;
        /** 终态交易数，等于成功和失败交易数之和。 */
        private long terminalCount;
        /** 终态失败交易数。 */
        private long failedCount;
        /** 发生终态失败交易的商户数量。 */
        private long affectedMerchantCount;
        /** 终态失败率，计算口径为 FAILED/(SUCCESS+FAILED)。 */
        private BigDecimal failureRate = BigDecimal.ZERO;
        /** 按自然日统计的失败交易趋势。 */
        private List<TrendMetric> trend = new ArrayList<>();
        /** 后台失败原因类别分布。 */
        private List<CountMetric> categories = new ArrayList<>();
        /** 后台失败原因码排行。 */
        private List<FailureReasonMetric> reasons = new ArrayList<>();
        /** 失败交易的渠道分布；尚未路由时使用 NO_CHANNEL。 */
        private List<CountMetric> channels = new ArrayList<>();
    }

    /** 管理端渠道表现响应，明确区分渠道请求结果和最终交易结果。 */
    @Data
    public static class ChannelPerformanceResponse {
        /** 本次统计生成时间，使用页面查询时区。 */
        private LocalDateTime generatedAt;
        /** 非勾兑业务渠道请求总数。 */
        private long totalRequestCount;
        /** 已取得终态请求结果的渠道请求数。 */
        private long completedRequestCount;
        /** 平台按渠道协议判断成功的渠道请求数。 */
        private long successfulRequestCount;
        /** 已完成但未成功且非超时的渠道请求数。 */
        private long failedRequestCount;
        /** 渠道请求超时数。 */
        private long timeoutRequestCount;
        /** INIT、SENT等尚未取得终态结果的渠道请求数。 */
        private long inFlightRequestCount;
        /** 渠道请求成功率，计算口径为成功请求数/已完成请求数。 */
        private BigDecimal requestSuccessRate = BigDecimal.ZERO;
        /** 已记录耗时的完成请求平均耗时，单位毫秒。 */
        private BigDecimal averageDurationMillis = BigDecimal.ZERO;
        /** 已记录耗时的完成请求最大耗时，单位毫秒。 */
        private long maximumDurationMillis;
        /** 各渠道请求表现和最终交易表现。 */
        private List<ChannelMetric> channels = new ArrayList<>();
        /** 按自然日统计的渠道请求趋势。 */
        private List<ChannelTrendMetric> trend = new ArrayList<>();
        /** 收单响应码、网关码或平台结果码分布。 */
        private List<CountMetric> responseCodes = new ArrayList<>();
    }

    /** 管理端3DS分析响应，认证事实均先按交易去重。 */
    @Data
    public static class ThreeDsResponse {
        /** 本次统计生成时间，使用页面查询时区。 */
        private LocalDateTime generatedAt;
        /** 符合筛选条件的首笔银行卡交易数。 */
        private long eligibleCardTransactionCount;
        /** 存在3DS认证事实的去重交易数。 */
        private long authenticationTransactionCount;
        /** 至少存在一条认证成功记录的去重交易数。 */
        private long authenticatedCount;
        /** 没有成功记录且至少存在一条失败记录的去重交易数。 */
        private long failedCount;
        /** 尚未形成成功或失败认证终态的去重交易数。 */
        private long processingCount;
        /** 3DS覆盖率，计算口径为认证交易数/银行卡交易数。 */
        private BigDecimal coverageRate = BigDecimal.ZERO;
        /** 3DS认证成功率，计算口径为成功/(成功+失败)。 */
        private BigDecimal authenticationSuccessRate = BigDecimal.ZERO;
        /** 参与3DS的交易中最终支付成功的笔数。 */
        private long paymentSuccessCount;
        /** 参与3DS的交易中最终支付失败的笔数。 */
        private long paymentFailedCount;
        /** 参与3DS交易的终态支付成功率。 */
        private BigDecimal paymentSuccessRate = BigDecimal.ZERO;
        /** 曾进入持卡人挑战流程的交易数。 */
        private long challengeRequiredCount;
        /** 挑战认证完成的交易数。 */
        private long challengeCompletedCount;
        /** 挑战认证失败的交易数。 */
        private long challengeFailedCount;
        /** 挑战率，计算口径为挑战交易数/认证交易数。 */
        private BigDecimal challengeRate = BigDecimal.ZERO;
        /** 已确认发生责任转移的交易数。 */
        private long liabilityShiftedCount;
        /** 已确认未发生责任转移的交易数。 */
        private long liabilityNotShiftedCount;
        /** 未获得责任转移结论的交易数。 */
        private long liabilityUnknownCount;
        /** 按自然日统计的去重认证交易趋势。 */
        private List<ThreeDsTrendMetric> trend = new ArrayList<>();
        /** 认证结果状态分布。 */
        private List<CountMetric> statuses = new ArrayList<>();
        /** 3DS协议版本分布。 */
        private List<CountMetric> versions = new ArrayList<>();
        /** 认证事实来源分布，如CHANNEL或MERCHANT。 */
        private List<CountMetric> sources = new ArrayList<>();
        /** 挑战认证结果分布。 */
        private List<CountMetric> challenges = new ArrayList<>();
        /** 责任转移状态分布，包含SHIFTED、NOT_SHIFTED和UNKNOWN。 */
        private List<CountMetric> liabilityShifts = new ArrayList<>();
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

    /** 通用数量占比指标，用于失败、响应码和3DS维度。 */
    @Data
    public static class CountMetric {
        /** 稳定维度编码；历史缺失值统一使用UNKNOWN。 */
        private String key;
        /** 当前维度交易或请求数量。 */
        private long totalCount;
        /** 当前维度在所属统计总量中的占比，单位为百分比。 */
        private BigDecimal percentage = BigDecimal.ZERO;
    }

    /** 管理端后台可见失败原因指标。 */
    @Data
    public static class FailureReasonMetric {
        /** 失败原因码；历史缺失值使用UNKNOWN。 */
        private String key;
        /** 后台可见失败原因描述，不包含完整渠道原始报文。 */
        private String message;
        /** 归一后的失败类别编码。 */
        private String category;
        /** 当前失败原因交易数。 */
        private long totalCount;
        /** 当前失败原因占全部失败交易的比例。 */
        private BigDecimal percentage = BigDecimal.ZERO;
    }

    /** 单渠道请求及最终交易表现指标。 */
    @Data
    public static class ChannelMetric {
        /** 渠道编码；历史缺失值使用UNKNOWN。 */
        private String channelCode;
        /** 非勾兑业务请求总数。 */
        private long totalRequestCount;
        /** 已完成渠道请求数。 */
        private long completedRequestCount;
        /** 平台判断成功的渠道请求数。 */
        private long successfulRequestCount;
        /** 非超时失败渠道请求数。 */
        private long failedRequestCount;
        /** 超时渠道请求数。 */
        private long timeoutRequestCount;
        /** 尚未取得终态结果的渠道请求数。 */
        private long inFlightRequestCount;
        /** 渠道请求成功率，单位为百分比。 */
        private BigDecimal requestSuccessRate = BigDecimal.ZERO;
        /** 已记录耗时的完成请求平均耗时，单位毫秒。 */
        private BigDecimal averageDurationMillis = BigDecimal.ZERO;
        /** 已记录耗时的完成请求最大耗时，单位毫秒。 */
        private long maximumDurationMillis;
        /** 最终落在当前渠道的首笔交易数。 */
        private long transactionCount;
        /** 当前渠道最终成功的首笔交易数。 */
        private long transactionSuccessCount;
        /** 当前渠道最终失败的首笔交易数。 */
        private long transactionFailedCount;
        /** 当前渠道终态交易成功率，单位为百分比。 */
        private BigDecimal transactionSuccessRate = BigDecimal.ZERO;
    }

    /** 单日渠道请求表现指标。 */
    @Data
    public static class ChannelTrendMetric {
        /** 统计日期，格式yyyy-MM-dd。 */
        private String date;
        /** 当日非勾兑业务请求总数。 */
        private long totalRequestCount;
        /** 当日成功渠道请求数。 */
        private long successfulRequestCount;
        /** 当日非超时失败渠道请求数。 */
        private long failedRequestCount;
        /** 当日超时渠道请求数。 */
        private long timeoutRequestCount;
        /** 当日尚未取得终态结果的渠道请求数。 */
        private long inFlightRequestCount;
        /** 当日已完成渠道请求成功率，单位为百分比。 */
        private BigDecimal requestSuccessRate = BigDecimal.ZERO;
    }

    /** 单日3DS去重交易趋势指标。 */
    @Data
    public static class ThreeDsTrendMetric {
        /** 统计日期，格式yyyy-MM-dd。 */
        private String date;
        /** 当日存在3DS认证事实的去重交易数。 */
        private long totalCount;
        /** 当日认证成功交易数。 */
        private long authenticatedCount;
        /** 当日认证失败交易数。 */
        private long failedCount;
        /** 当日认证处理中交易数。 */
        private long processingCount;
        /** 当日3DS认证终态成功率，单位为百分比。 */
        private BigDecimal authenticationSuccessRate = BigDecimal.ZERO;
    }
}
