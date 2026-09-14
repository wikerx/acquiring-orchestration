package com.scott.payment.admin.dto.monitor;

import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorWorkbenchDTOs
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 系统监控工作台请求与响应契约集合，统一约束时间范围、分页、指标单位、能力状态和脱敏后的业务事件摘要。
 * @status : create
 */
public final class MonitorWorkbenchDTOs {

    private MonitorWorkbenchDTOs() {
    }

    /** 系统监控通用时间范围和分页查询；空时间范围由服务端补为最近二十四小时。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TimeRangeQuery extends PageRequest {
        /** 查询开始时间，不含时区信息，允许为空且非敏感。 */
        private LocalDateTime beginTime;
        /** 查询结束时间，不含时区信息，允许为空且非敏感。 */
        private LocalDateTime endTime;
        /** IANA 查询时区，例如 Asia/Shanghai；允许为空并默认使用数据库时区。 */
        private String queryTimeZone;
    }

    /** API 监控筛选条件；空值表示不应用对应过滤条件。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ApiMonitorQuery extends TimeRangeQuery {
        /** 服务名称过滤条件，允许为空且非敏感。 */
        private String serviceName;
        /** API 业务操作名称过滤条件，允许为空且非敏感。 */
        private String apiOperation;
        /** API 路径过滤条件，允许为空，不包含查询参数和敏感请求数据。 */
        private String apiPath;
        /** 商户标识过滤条件，允许为空，属于受权限保护的业务标识。 */
        private String merchantId;
        /** 平台业务响应码过滤条件，允许为空且非敏感。 */
        private String responseCode;
        /** API 处理结果过滤条件，允许为空且非敏感。 */
        private String result;
    }

    /** 渠道监控筛选条件；不携带渠道报文或认证凭据。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelMonitorQuery extends TimeRangeQuery {
        /** 渠道编码过滤条件，允许为空且非敏感。 */
        private String channelCode;
        /** 渠道请求场景过滤条件，允许为空且非敏感。 */
        private String requestScene;
        /** ISO 4217 三位币种代码过滤条件，允许为空且非敏感。 */
        private String currency;
        /** 渠道请求状态过滤条件，允许为空且非敏感。 */
        private String status;
        /** 平台或渠道错误码过滤条件，允许为空且非敏感。 */
        private String errorCode;
        /** 支付方式过滤条件，允许为空且非敏感。 */
        private String paymentMethod;
    }

    /** 商户 Webhook 监控筛选条件；不包含通知请求或响应正文。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class WebhookMonitorQuery extends TimeRangeQuery {
        /** 商户标识过滤条件，允许为空，属于受权限保护的业务标识。 */
        private String merchantId;
        /** 平台交易号过滤条件，允许为空，属于受权限保护的业务标识。 */
        private String transactionId;
        /** 通知事件类型过滤条件，允许为空且非敏感。 */
        private String eventType;
        /** 通知状态过滤条件，允许为空且非敏感。 */
        private String notifyStatus;
        /** 商户端 HTTP 响应状态码过滤条件，允许为空且非敏感。 */
        private Integer httpStatus;
    }

    /** 交易链路定位条件；服务端使用其中至少一个业务标识定位单笔链路。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TraceSearchQuery extends TimeRangeQuery {
        /** 平台交易号，允许为空，属于受权限保护的业务标识。 */
        private String transactionId;
        /** 商户订单号，允许为空，属于受权限保护的业务标识。 */
        private String merchantOrderNo;
        /** 渠道订单号，允许为空，属于受权限保护的业务标识。 */
        private String channelOrderNo;
        /** 分布式链路标识，允许为空，不包含 Span 内容。 */
        private String traceId;
    }

    /** 结构化日志检索条件；仅查询已脱敏业务事件，不提供原始应用日志。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LogSearchQuery extends TimeRangeQuery {
        /** 分布式链路标识过滤条件，允许为空且非敏感。 */
        private String traceId;
        /** 平台交易号过滤条件，允许为空，属于受权限保护的业务标识。 */
        private String transactionId;
        /** 商户标识过滤条件，允许为空，属于受权限保护的业务标识。 */
        private String merchantId;
        /** 商户订单号过滤条件，允许为空，属于受权限保护的业务标识。 */
        private String merchantOrderNo;
        /** 渠道订单号过滤条件，允许为空，属于受权限保护的业务标识。 */
        private String channelOrderNo;
        /** 服务名称过滤条件，允许为空且非敏感。 */
        private String serviceName;
        /** 日志级别过滤条件，允许为空且非敏感。 */
        private String level;
        /** 已脱敏结构化摘要的关键词过滤条件，允许为空。 */
        private String keyword;
    }

    /** 统一告警查询条件；来源、级别、状态和负责人均允许为空。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AlertQuery extends TimeRangeQuery {
        /** 告警来源类型过滤条件，例如 CHANNEL 或 SECURITY；允许为空。 */
        private String sourceType;
        /** 告警级别过滤条件，允许为空且非敏感。 */
        private String level;
        /** 告警处理状态过滤条件，允许为空且非敏感。 */
        private String status;
        /** 负责人账号标识过滤条件，允许为空，属于受权限保护的账号标识。 */
        private String ownerAccountId;
        /** 告警标题或内容关键词，允许为空。 */
        private String keyword;
    }

    /** 告警处理动作请求；版本号用于乐观锁，备注进入审计历史。 */
    @Data
    public static class AlertActionRequest {
        /** 告警当前版本号，不允许为空，用于阻止并发状态覆盖。 */
        @NotNull
        private Integer version;
        /** 人工处理备注，最多 512 个字符，允许为空且不得填写凭据或支付卡数据。 */
        @Size(max = 512)
        private String remark;
    }

    /** 工作台摘要指标；数值单位由 unit 声明，空值表示底层未提供该指标。 */
    @Data
    public static class MetricSummary {
        /** 前端稳定指标键，不允许为空且非敏感。 */
        private String key;
        /** 面向管理员的指标名称，不允许为空且非敏感。 */
        private String label;
        /** 指标数值，允许为空，精度取决于对应指标。 */
        private BigDecimal value;
        /** 数值单位，例如 count、percent 或 millis；允许为空。 */
        private String unit;
        /** 健康或风险状态，允许为空且非敏感。 */
        private String status;
        /** 指标口径或补充说明，允许为空且不得包含敏感原文。 */
        private String description;
    }

    /** 时间序列单个桶；values 的键由统一图表定义约束。 */
    @Data
    public static class TimeBucket {
        /** 数据库时区下的桶起始时间，不允许为空且非敏感。 */
        private LocalDateTime timestamp;
        /** 指标键到数值的映射，默认空集合；单位由图表定义声明。 */
        private Map<String, BigDecimal> values = Collections.emptyMap();
    }

    /** 分类聚合指标；用于 TopN、分布图和筛选联动。 */
    @Data
    public static class CategoryMetric {
        /** 分类稳定键，不允许为空且非敏感。 */
        private String key;
        /** 分类展示名称，允许为空且必须经过必要脱敏。 */
        private String label;
        /** 分类数值，允许为空；单位由对应图表定义声明。 */
        private BigDecimal value;
    }

    /** 监控提供方能力状态；用于明确页面空数据的真实原因。 */
    @Data
    public static class ProviderCapability {
        /** 能力提供方标识，不允许为空且非敏感。 */
        private String provider;
        /** 能力状态，例如 AVAILABLE、UNAVAILABLE 或 NOT_CONFIGURED。 */
        private String status;
        /** 脱敏后的状态原因，允许为空，不返回底层凭据或连接串。 */
        private String reason;
    }

    /** 系统监控总览响应；聚合服务、API、告警和依赖状态。 */
    @Data
    public static class OverviewResponse {
        /** 总览核心指标列表，默认空集合。 */
        private List<MetricSummary> summaries = Collections.emptyList();
        /** API 请求量时间序列，默认空集合。 */
        private List<TimeBucket> apiTrend = Collections.emptyList();
        /** API 延迟时间序列，单位毫秒，默认空集合。 */
        private List<TimeBucket> apiLatencyTrend = Collections.emptyList();
        /** 告警数量时间序列，单位条，默认空集合。 */
        private List<TimeBucket> alertTrend = Collections.emptyList();
        /** 基础设施依赖状态列表，默认空集合。 */
        private List<DependencyStatus> dependencies = Collections.emptyList();
        /** 最近告警摘要，默认空集合且不包含敏感事件原文。 */
        private List<AlertItem> latestAlerts = Collections.emptyList();
        /** 响应生成时间，使用服务端本地时区。 */
        private LocalDateTime generatedAt;
    }

    /** 单个基础设施依赖状态；用于展示发现、缓存、消息等能力是否可用。 */
    @Data
    public static class DependencyStatus {
        /** 依赖稳定键，不允许为空且非敏感。 */
        private String key;
        /** 依赖展示名称，不允许为空且非敏感。 */
        private String label;
        /** 依赖状态，例如 HEALTHY、UNAVAILABLE 或 NOT_CONFIGURED。 */
        private String status;
        /** 脱敏后的依赖摘要值，允许为空。 */
        private String value;
        /** 依赖状态说明，允许为空且不包含连接凭据。 */
        private String description;
    }

    /** 服务发现中的单个实例摘要；元数据返回前必须移除敏感键和值。 */
    @Data
    public static class ServiceInstanceItem {
        /** 注册中心服务名，不允许为空且非敏感。 */
        private String serviceName;
        /** 注册中心实例标识，允许为空且非敏感。 */
        private String instanceId;
        /** 实例主机名或地址，允许为空，属于受权限保护的基础设施信息。 */
        private String host;
        /** 实例监听端口，允许为空，单位为 TCP 端口号。 */
        private Integer port;
        /** 是否使用 TLS，允许为空。 */
        private Boolean secure;
        /** 实例健康状态，不允许为空且非敏感。 */
        private String status;
        /** 已脱敏注册元数据，默认空集合，不包含 token、secret 或密码。 */
        private Map<String, String> metadata = Collections.emptyMap();
    }

    /** 服务注册清单响应；数量均以实例或服务为计数单位。 */
    @Data
    public static class ServiceInventoryResponse {
        /** 已发现服务数量，单位个，最小为 0。 */
        private long serviceCount;
        /** 已发现实例总数，单位个，最小为 0。 */
        private long instanceCount;
        /** 健康实例数量，单位个，最小为 0。 */
        private long healthyInstanceCount;
        /** 不健康实例数量，单位个，最小为 0。 */
        private long unhealthyInstanceCount;
        /** 已脱敏服务实例列表，默认空集合。 */
        private List<ServiceInstanceItem> instances = Collections.emptyList();
        /** 服务级历史指标提供方状态，允许为空。 */
        private ProviderCapability metricsCapability;
    }

    /** 当前 Admin JVM 的单次运行时采样；不可读取的平台指标使用空值。 */
    @Data
    public static class RuntimeSample {
        /** 采样时间，使用服务端本地时间，不允许为空。 */
        private LocalDateTime timestamp;
        /** 当前 JVM 进程 CPU 使用率，单位百分比，平台不支持时为空。 */
        private Double processCpuPercent;
        /** 宿主系统 CPU 使用率，单位百分比，平台不支持时为空。 */
        private Double systemCpuPercent;
        /** 系统一分钟平均负载，无量纲，平台不支持时为空。 */
        private Double systemLoadAverage;
        /** 宿主机已使用物理内存，单位字节，平台不支持时为空。 */
        private Long physicalMemoryUsedBytes;
        /** 宿主机物理内存总量，单位字节，平台不支持时为空。 */
        private Long physicalMemoryTotalBytes;
        /** JVM 堆已使用内存，单位字节，最小为 0。 */
        private long heapUsedBytes;
        /** JVM 堆已提交内存，单位字节，最小为 0。 */
        private long heapCommittedBytes;
        /** JVM 堆最大内存，单位字节，未设置上限时为空。 */
        private Long heapMaxBytes;
        /** JVM 非堆已使用内存，单位字节，最小为 0。 */
        private long nonHeapUsedBytes;
        /** JVM 当前线程数，单位条，最小为 0。 */
        private long threadCount;
        /** JVM 启动以来峰值线程数，单位条，最小为 0。 */
        private long peakThreadCount;
        /** JVM 当前守护线程数，单位条，最小为 0。 */
        private long daemonThreadCount;
        /** JVM 当前已加载类数量，单位个，最小为 0。 */
        private long loadedClassCount;
        /** JVM 启动以来垃圾回收次数，单位次，最小为 0。 */
        private long gcCount;
        /** JVM 启动以来垃圾回收累计耗时，单位毫秒，最小为 0。 */
        private long gcDurationMillis;
        /** 采样磁盘总容量，单位字节，平台不支持时为空。 */
        private Long diskTotalBytes;
        /** 采样磁盘已使用容量，单位字节，平台不支持时为空。 */
        private Long diskUsedBytes;
        /** JVM 进程运行时长，单位毫秒，最小为 0。 */
        private long uptimeMillis;
    }

    /** JVM 运行时监控响应；历史仅来自当前 Admin 进程内的有界采样。 */
    @Data
    public static class RuntimeResponse {
        /** 最近一次 JVM 运行时采样，允许为空。 */
        private RuntimeSample current;
        /** 查询时间范围内的进程内采样，默认空集合。 */
        private List<RuntimeSample> samples = Collections.emptyList();
        /** 线程池指标摘要，默认空集合且不得包含任务参数。 */
        private List<Map<String, Object>> threadPools = Collections.emptyList();
        /** 线程池指标提供方状态，允许为空。 */
        private ProviderCapability threadPoolCapability;
        /** 响应生成时间，使用服务端本地时间。 */
        private LocalDateTime generatedAt;
    }

    /** 数据源历史指标响应；连接池来自 Hikari MXBean，SQL 延迟来自 MySQL performance_schema。 */
    @Data
    public static class DataSourceMetricsResponse {
        /** 连接池活跃、空闲和等待连接趋势，单位连接或线程，默认空集合。 */
        private List<TimeBucket> poolTrend = Collections.emptyList();
        /** SQL 平均值、P95 和 P99 延迟趋势，单位毫秒，默认空集合。 */
        private List<TimeBucket> latencyTrend = Collections.emptyList();
        /** 慢 SQL 指纹 TopN，标签已截断和脱敏，数值单位毫秒。 */
        private List<CategoryMetric> slowSqlTop = Collections.emptyList();
        /** Hikari 连接池采样能力状态，允许为空。 */
        private ProviderCapability poolMetricsCapability;
        /** MySQL performance_schema 指标能力状态，允许为空。 */
        private ProviderCapability sqlMetricsCapability;
        /** 响应生成时间，使用服务端本地时间。 */
        private LocalDateTime generatedAt;
    }

    /** Redis 历史指标响应；历史仅保存在当前 Admin 进程内，Key 类型通过有界扫描获得。 */
    @Data
    public static class CacheMetricsResponse {
        /** Redis 已用和最大内存趋势，单位字节，默认空集合。 */
        private List<TimeBucket> memoryTrend = Collections.emptyList();
        /** Redis 每秒操作数趋势，单位次每秒，默认空集合。 */
        private List<TimeBucket> opsTrend = Collections.emptyList();
        /** Redis 命中率趋势，单位百分比，默认空集合。 */
        private List<TimeBucket> hitRateTrend = Collections.emptyList();
        /** Redis Key 类型抽样分布，单位 Key 数，默认空集合。 */
        private List<CategoryMetric> keyTypeDistribution = Collections.emptyList();
        /** Redis 历史采样能力状态，允许为空。 */
        private ProviderCapability historyCapability;
        /** Redis Key 类型扫描能力状态，允许为空。 */
        private ProviderCapability keyTypeCapability;
        /** 响应生成时间，使用服务端本地时间。 */
        private LocalDateTime generatedAt;
    }

    /** 单个 API 聚合项；成功率使用百分比，延迟统一使用毫秒。 */
    @Data
    public static class ApiAggregateItem {
        /** API 聚合稳定键，不允许为空且非敏感。 */
        private String apiKey;
        /** API 业务操作名称，允许为空且非敏感。 */
        private String apiOperation;
        /** API 路径，不含查询参数和请求正文，允许为空。 */
        private String apiPath;
        /** 请求总数，单位次，最小为 0。 */
        private long requestCount;
        /** 失败请求数，单位次，最小为 0。 */
        private long failedCount;
        /** 成功率，单位百分比，分母为 0 时允许为空。 */
        private BigDecimal successRate;
        /** 平均处理耗时，单位毫秒，无法计算时允许为空。 */
        private BigDecimal averageMillis;
        /** P95 处理耗时，单位毫秒，样本不足时允许为空。 */
        private BigDecimal p95Millis;
        /** P99 处理耗时，单位毫秒，样本不足时允许为空。 */
        private BigDecimal p99Millis;
        /** 最近一次平台业务响应码，允许为空且非敏感。 */
        private String latestResponseCode;
        /** 最近一次失败时间，未失败时为空。 */
        private LocalDateTime latestFailureTime;
    }

    /** API 监控响应；包含统一图表数据、分页聚合结果和底层能力说明。 */
    @Data
    public static class ApiMonitorResponse {
        /** API 核心指标摘要，默认空集合。 */
        private List<MetricSummary> summaries = Collections.emptyList();
        /** API 请求量和失败量趋势，单位次，默认空集合。 */
        private List<TimeBucket> requestTrend = Collections.emptyList();
        /** API 延迟趋势，单位毫秒，默认空集合。 */
        private List<TimeBucket> latencyTrend = Collections.emptyList();
        /** 平台业务响应码 TopN，默认空集合。 */
        private List<CategoryMetric> responseCodeTop = Collections.emptyList();
        /** 商户请求量 TopN，商户标识受权限保护，默认空集合。 */
        private List<CategoryMetric> merchantTop = Collections.emptyList();
        /** API 聚合分页结果，允许为空。 */
        private PageResult<ApiAggregateItem> page;
        /** HTTP 原生指标能力状态，允许为空。 */
        private ProviderCapability httpMetricCapability;
    }

    /** 单个渠道聚合项；不包含渠道原始请求、响应或认证信息。 */
    @Data
    public static class ChannelAggregateItem {
        /** 渠道编码，不允许为空且非敏感。 */
        private String channelCode;
        /** 当前渠道健康或请求状态，允许为空且非敏感。 */
        private String status;
        /** 渠道请求总数，单位次，最小为 0。 */
        private long requestCount;
        /** 渠道成功请求数，单位次，最小为 0。 */
        private long successCount;
        /** 渠道超时请求数，单位次，最小为 0。 */
        private long timeoutCount;
        /** 渠道成功率，单位百分比，分母为 0 时允许为空。 */
        private BigDecimal successRate;
        /** 渠道平均耗时，单位毫秒，无法计算时允许为空。 */
        private BigDecimal averageMillis;
        /** 渠道 P95 耗时，单位毫秒，样本不足时允许为空。 */
        private BigDecimal p95Millis;
        /** 渠道 P99 耗时，单位毫秒，样本不足时允许为空。 */
        private BigDecimal p99Millis;
        /** 当前连续失败次数，单位次，最小为 0。 */
        private int continuousFailureCount;
        /** 最近错误摘要，允许为空，必须经过脱敏和长度限制。 */
        private String latestError;
        /** 最近失败时间，未失败时为空。 */
        private LocalDateTime latestFailureTime;
    }

    /** 渠道监控响应；图表类型和指标键由统一 MonitorChart 定义约束。 */
    @Data
    public static class ChannelMonitorResponse {
        /** 渠道核心指标摘要，默认空集合。 */
        private List<MetricSummary> summaries = Collections.emptyList();
        /** 渠道成功率趋势，单位百分比，默认空集合。 */
        private List<TimeBucket> successRateTrend = Collections.emptyList();
        /** 渠道延迟趋势，单位毫秒，默认空集合。 */
        private List<TimeBucket> latencyTrend = Collections.emptyList();
        /** 渠道错误码 TopN，默认空集合。 */
        private List<CategoryMetric> errorTop = Collections.emptyList();
        /** 支付方式成功率分布，单位百分比，默认空集合。 */
        private List<CategoryMetric> paymentMethodRate = Collections.emptyList();
        /** 渠道聚合分页结果，允许为空。 */
        private PageResult<ChannelAggregateItem> page;
    }

    /** 单条商户通知投递摘要；不返回请求正文、响应正文或签名密钥。 */
    @Data
    public static class WebhookItem {
        /** 通知事件唯一标识，不允许为空且非敏感。 */
        private String eventId;
        /** 商户标识，允许为空，属于受权限保护的业务标识。 */
        private String merchantId;
        /** 平台交易号，允许为空，属于受权限保护的业务标识。 */
        private String transactionId;
        /** 交易业务时间，允许为空，使用数据库时区。 */
        private LocalDateTime transactionDateTime;
        /** 通知事件类型，允许为空且非敏感。 */
        private String eventType;
        /** 最近一次商户端 HTTP 响应状态码，未收到响应时为空。 */
        private Integer httpStatus;
        /** 已执行投递次数，单位次，允许为空。 */
        private Integer attemptCount;
        /** 最近一次投递耗时，单位毫秒，未发出请求时为空。 */
        private Integer lastDurationMillis;
        /** 当前投递状态，不允许为空且非敏感。 */
        private String status;
        /** 下一次计划重试时间，无后续重试时为空。 */
        private LocalDateTime nextRetryTime;
        /** 最近错误摘要，允许为空，必须经过脱敏和长度限制。 */
        private String lastError;
        /** 最近一次投递尝试时间，从未尝试时为空。 */
        private LocalDateTime lastAttemptTime;
    }

    /** Webhook 监控响应；成功率、重试量和 HTTP 状态按统一口径聚合。 */
    @Data
    public static class WebhookMonitorResponse {
        /** Webhook 核心指标摘要，默认空集合。 */
        private List<MetricSummary> summaries = Collections.emptyList();
        /** Webhook 成功率趋势，单位百分比，默认空集合。 */
        private List<TimeBucket> successRateTrend = Collections.emptyList();
        /** Webhook 重试次数趋势，单位次，默认空集合。 */
        private List<TimeBucket> retryTrend = Collections.emptyList();
        /** 商户端 HTTP 状态码分布，单位次，默认空集合。 */
        private List<CategoryMetric> httpStatusDistribution = Collections.emptyList();
        /** 通知投递分页结果，允许为空。 */
        private PageResult<WebhookItem> page;
    }

    /** 单笔交易链路摘要；金额沿用交易表十进制主单位，不进行汇率或费用计算。 */
    @Data
    public static class TraceSummary {
        /** 当前交易号，不允许为空，属于受权限保护的业务标识。 */
        private String transactionId;
        /** 根交易号，允许为空，属于受权限保护的业务标识。 */
        private String rootTransactionId;
        /** 当前操作单号，允许为空，属于受权限保护的业务标识。 */
        private String operationId;
        /** 商户标识，允许为空，属于受权限保护的业务标识。 */
        private String merchantId;
        /** 商户订单号，允许为空，属于受权限保护的业务标识。 */
        private String merchantOrderNo;
        /** 交易类型，允许为空且非敏感。 */
        private String transactionType;
        /** 交易状态，允许为空且非敏感。 */
        private String transactionStatus;
        /** ISO 4217 三位币种代码，允许为空且非敏感。 */
        private String currency;
        /** 交易金额，单位为 currency 对应主单位，允许为空。 */
        private BigDecimal amount;
        /** 支付方式，允许为空且非敏感。 */
        private String paymentMethod;
        /** 渠道编码，允许为空且非敏感。 */
        private String channelCode;
        /** 渠道订单号，允许为空，属于受权限保护的业务标识。 */
        private String channelOrderNo;
        /** 交易创建时间，允许为空，使用数据库时区。 */
        private LocalDateTime createTime;
        /** 交易完成时间，未完成时为空，使用数据库时区。 */
        private LocalDateTime completeTime;
        /** 当前链路首尾事件总耗时，单位毫秒，无法计算时为空。 */
        private Long totalDurationMillis;
    }

    /** 单个结构化链路事件；错误和业务备注在返回前统一脱敏。 */
    @Data
    public static class TraceEvent {
        /** 事件唯一标识，允许为空且非敏感。 */
        private String eventId;
        /** 平台交易号，允许为空，属于受权限保护的业务标识。 */
        private String transactionId;
        /** 分布式链路标识，允许为空且非敏感。 */
        private String traceId;
        /** 产生事件的服务名称，允许为空且非敏感。 */
        private String serviceName;
        /** 事件类型，允许为空且非敏感。 */
        private String eventType;
        /** 事件名称，允许为空且非敏感。 */
        private String eventName;
        /** 事件处理结果，允许为空且非敏感。 */
        private String result;
        /** 事件开始时间，允许为空，使用数据库时区。 */
        private LocalDateTime startTime;
        /** 事件结束时间，未结束时为空，使用数据库时区。 */
        private LocalDateTime endTime;
        /** 事件耗时，单位毫秒，无法计算时为空。 */
        private Integer durationMillis;
        /** 平台错误码，允许为空且非敏感。 */
        private String errorCode;
        /** 脱敏后的错误摘要，允许为空，不包含报文、凭据或卡数据。 */
        private String errorMessage;
        /** 脱敏后的业务备注，允许为空，不包含请求或响应原文。 */
        private String businessRemark;
    }

    /** 瀑布图阶段数据；开始偏移和持续时间均以毫秒为单位。 */
    @Data
    public static class WaterfallStage {
        /** 阶段稳定键，不允许为空且非敏感。 */
        private String key;
        /** 阶段展示名称，不允许为空且非敏感。 */
        private String label;
        /** 相对链路首事件的开始偏移，单位毫秒，最小为 0。 */
        private long startMillis;
        /** 阶段持续时间，单位毫秒，最小为 0。 */
        private long durationMillis;
        /** 阶段处理状态，允许为空且非敏感。 */
        private String status;
    }

    /** 交易链路响应；当前提供结构化业务事件，完整 Span 能力由 apmCapability 说明。 */
    @Data
    public static class TraceResponse {
        /** 单笔交易链路摘要，定位成功时不为空。 */
        private TraceSummary summary;
        /** 按开始时间排序的脱敏事件列表，默认空集合。 */
        private List<TraceEvent> events = Collections.emptyList();
        /** 与事件列表对应的瀑布图阶段，默认空集合。 */
        private List<WaterfallStage> waterfall = Collections.emptyList();
        /** APM Span 查询能力状态，允许为空。 */
        private ProviderCapability apmCapability;
    }

    /** 单条结构化业务日志摘要；内容来自业务事实表并经过脱敏。 */
    @Data
    public static class StructuredLogItem {
        /** 日志事件稳定标识，不允许为空且非敏感。 */
        private String id;
        /** 事件发生时间，不允许为空，使用数据库时区。 */
        private LocalDateTime timestamp;
        /** 日志级别，允许为空且非敏感。 */
        private String level;
        /** 服务名称，允许为空且非敏感。 */
        private String serviceName;
        /** 结构化事件类型，允许为空且非敏感。 */
        private String eventType;
        /** 脱敏并截断后的事件摘要，允许为空。 */
        private String message;
        /** 分布式链路标识，允许为空且非敏感。 */
        private String traceId;
        /** 平台交易号，允许为空，属于受权限保护的业务标识。 */
        private String transactionId;
        /** 商户标识，允许为空，属于受权限保护的业务标识。 */
        private String merchantId;
        /** 事件关联标识，允许为空，可能是订单号或通知事件号。 */
        private String referenceId;
    }

    /** 结构化日志检索响应；能力状态用于解释无法展示原始日志上下文。 */
    @Data
    public static class LogSearchResponse {
        /** 结构化日志分页结果，允许为空。 */
        private PageResult<StructuredLogItem> page;
        /** 集中日志上下文查询能力状态，允许为空。 */
        private ProviderCapability contextCapability;
    }

    /** 统一告警项；来源事实与人工处理覆盖分离，版本号用于并发更新保护。 */
    @Data
    public static class AlertItem {
        /** 告警来源类型，例如 CHANNEL 或 SECURITY，不允许为空。 */
        private String sourceType;
        /** 来源系统内的告警唯一标识，不允许为空且非敏感。 */
        private String sourceId;
        /** 统一告警级别，不允许为空且非敏感。 */
        private String level;
        /** 关联服务名称，允许为空且非敏感。 */
        private String serviceName;
        /** 关联模块名称，允许为空且非敏感。 */
        private String moduleName;
        /** 告警标题，允许为空，返回前必须脱敏。 */
        private String title;
        /** 告警内容摘要，允许为空，返回前必须脱敏和截断。 */
        private String content;
        /** 聚合发生次数，单位次，最小为 0。 */
        private long occurrenceCount;
        /** 首次发生时间，允许为空，使用数据库时区。 */
        private LocalDateTime firstOccurredAt;
        /** 最近发生时间，允许为空，使用数据库时区。 */
        private LocalDateTime lastOccurredAt;
        /** 恢复时间，未恢复时为空，使用数据库时区。 */
        private LocalDateTime recoveredAt;
        /** 当前统一处理状态，不允许为空且非敏感。 */
        private String status;
        /** 当前负责人账号标识，未接手时为空，属于受权限保护的账号标识。 */
        private String ownerAccountId;
        /** 当前负责人展示名称，未接手时为空，属于受权限保护的人员信息。 */
        private String ownerName;
        /** 最近处理备注，允许为空，返回前必须脱敏。 */
        private String handleRemark;
        /** 告警处理记录版本号，最小为 0，用于乐观锁。 */
        private int version;
    }

    /** 单条告警人工处理历史；仅记录状态变化和脱敏备注。 */
    @Data
    public static class AlertHistoryItem {
        /** 处理历史主键，不允许为空且非敏感。 */
        private Long id;
        /** 人工处理动作，不允许为空且非敏感。 */
        private String action;
        /** 动作前状态，允许为空且非敏感。 */
        private String fromStatus;
        /** 动作后状态，允许为空且非敏感。 */
        private String toStatus;
        /** 操作人账号标识，允许为空，属于受权限保护的账号标识。 */
        private String operatorAccountId;
        /** 操作人展示名称，允许为空，属于受权限保护的人员信息。 */
        private String operatorName;
        /** 脱敏后的人工备注，允许为空。 */
        private String remark;
        /** 操作时间，不允许为空，使用数据库时区。 */
        private LocalDateTime operatedAt;
    }

    /** 告警检索响应；包含摘要、趋势、来源分布和分页明细。 */
    @Data
    public static class AlertSearchResponse {
        /** 告警核心指标摘要，默认空集合。 */
        private List<MetricSummary> summaries = Collections.emptyList();
        /** 告警数量趋势，单位条，默认空集合。 */
        private List<TimeBucket> trend = Collections.emptyList();
        /** 告警来源 TopN，单位条，默认空集合。 */
        private List<CategoryMetric> sourceTop = Collections.emptyList();
        /** 告警分页结果，允许为空。 */
        private PageResult<AlertItem> page;
    }

    /** 告警详情响应；关联指标不可用时通过能力状态明确说明。 */
    @Data
    public static class AlertDetailResponse {
        /** 告警当前快照，查询成功时不为空。 */
        private AlertItem alert;
        /** 告警人工处理历史，默认空集合。 */
        private List<AlertHistoryItem> history = Collections.emptyList();
        /** 告警来源相关指标，默认空集合，不包含来源报文。 */
        private Map<String, Object> sourceMetrics = Collections.emptyMap();
        /** 告警历史指标上下文能力状态，允许为空。 */
        private ProviderCapability contextMetricCapability;
    }

    /** 安全拦截统计响应；仅返回聚合数据，不返回请求报文、凭据或卡数据。 */
    @Data
    public static class SecurityStatisticsResponse {
        /** 安全拦截核心指标摘要，默认空集合。 */
        private List<MetricSummary> summaries = Collections.emptyList();
        /** 安全拦截数量趋势，单位条，默认空集合。 */
        private List<TimeBucket> trend = Collections.emptyList();
        /** 安全事件类型 TopN，单位条，默认空集合。 */
        private List<CategoryMetric> typeTop = Collections.emptyList();
        /** 商户安全事件 TopN，商户标识受权限保护，默认空集合。 */
        private List<CategoryMetric> merchantTop = Collections.emptyList();
    }
}
