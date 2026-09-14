package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.application.monitor.AdminMonitorCacheApplicationService;
import com.scott.payment.admin.application.monitor.AdminMonitorDatasourceApplicationService;
import com.scott.payment.admin.dto.monitor.DataSourceMonitorResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertActionRequest;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertDetailResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertHistoryItem;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertItem;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertSearchResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ApiAggregateItem;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ApiMonitorQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ApiMonitorResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.CategoryMetric;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ChannelAggregateItem;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ChannelMonitorQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ChannelMonitorResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.DependencyStatus;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.LogSearchQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.LogSearchResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.MetricSummary;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.OverviewResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ProviderCapability;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.RuntimeResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.SecurityStatisticsResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ServiceInstanceItem;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ServiceInventoryResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.StructuredLogItem;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TimeBucket;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TimeRangeQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TraceEvent;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TraceResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TraceSearchQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TraceSummary;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.WaterfallStage;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.WebhookItem;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.WebhookMonitorQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.WebhookMonitorResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.service.AdminMonitorWorkbenchService;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.admin.service.monitor.AdminRuntimeMonitorSampler;
import com.scott.payment.admin.service.monitor.MonitorTimeRangeNormalizer;
import com.scott.payment.admin.service.monitor.MonitorTimeRangeNormalizer.NormalizedRange;
import com.scott.payment.admin.service.monitor.MonitorSensitiveTextSanitizer;
import com.scott.payment.admin.service.monitor.RocketMqAdminMonitorProvider;
import com.scott.payment.admin.service.monitor.RocketMqAdminMonitorProvider.Snapshot;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminMonitorWorkbenchService
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 系统监控工作台 JDBC 实现，交易事实通过 ShardingSphere 逻辑表读取，管理事实从 Admin 数据库读取，并统一执行脱敏、能力声明和告警乐观锁处理。
 * @status : create
 */
@Service
public class JdbcAdminMonitorWorkbenchService implements AdminMonitorWorkbenchService {

    /** 单次结构化日志聚合允许保留的最大记录数，防止内存排序失控。 */
    private static final int MAX_RESULT_ROWS = 1000;
    /** 监控提供方未配置状态。 */
    private static final String STATUS_NOT_CONFIGURED = "NOT_CONFIGURED";
    /** 监控提供方可用状态。 */
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    /** 告警待处理状态。 */
    private static final String ALERT_OPEN = "OPEN";
    /** 告警处理中状态。 */
    private static final String ALERT_PROCESSING = "PROCESSING";
    /** 来源告警已自动恢复状态。 */
    private static final String ALERT_RECOVERED = "RECOVERED";
    /** 告警已人工关闭状态。 */
    private static final String ALERT_CLOSED = "CLOSED";
    /** API 业务交互表中用于统一判断失败的 SQL 条件，不依赖 HTTP 状态码。 */
    private static final String API_FAILURE_CONDITION = """
            (UPPER(COALESCE(request_result, '')) IN ('FAILED', 'FAIL', 'ERROR', 'REJECTED', 'TIMEOUT', 'CLOSED')
             OR UPPER(COALESCE(request_result, '')) LIKE 'F%'
             OR UPPER(COALESCE(request_result, '')) LIKE 'Z%'
             OR UPPER(COALESCE(response_result, '')) IN ('FAILED', 'FAIL', 'ERROR', 'REJECTED', 'TIMEOUT', 'CLOSED')
             OR UPPER(COALESCE(response_result, '')) LIKE 'F%'
             OR UPPER(COALESCE(response_result, '')) LIKE 'Z%')
            """.strip();
    /** 渠道交互表中用于统一判断超时的 SQL 条件。 */
    private static final String CHANNEL_TIMEOUT_CONDITION = """
            (UPPER(COALESCE(r.request_status, '')) = 'TIMEOUT'
             OR UPPER(COALESCE(r.platform_result_code, '')) LIKE '%TIMEOUT%')
            """.strip();
    /** 渠道错误码选择表达式，优先平台码，其次收单机构码。 */
    private static final String CHANNEL_ERROR_CODE_EXPRESSION = """
            COALESCE(
                NULLIF(TRIM(r.platform_result_code), ''),
                NULLIF(TRIM(r.acquirer_code), ''),
                'UNKNOWN'
            )
            """.strip();
    /** 渠道告警和安全拦截事件的统一来源查询，不读取敏感业务报文。 */
    private static final String ALERT_SOURCE_SQL = """
            SELECT 'CHANNEL' AS source_type,
                   event_code AS source_id,
                   CASE
                       WHEN UPPER(alert_level) IN ('CRITICAL', 'L3_CIRCUIT_BREAK') THEN 'CRITICAL'
                       WHEN UPPER(alert_level) IN ('HIGH', 'ERROR', 'L2_DEGRADED') THEN 'ERROR'
                       ELSE 'WARNING'
                   END AS level,
                   CONCAT('channel-', LOWER(COALESCE(NULLIF(TRIM(channel_code), ''), 'unknown'))) AS service_name,
                   'CHANNEL' AS module_name,
                   COALESCE(NULLIF(TRIM(rule_name), ''), NULLIF(TRIM(rule_type), '')) AS title,
                   CONCAT('Channel ', COALESCE(NULLIF(TRIM(channel_code), ''), 'UNKNOWN'),
                          ' triggered ', COALESCE(NULLIF(TRIM(rule_type), ''), 'UNKNOWN')) AS content,
                   1 AS occurrence_count,
                   trigger_time AS first_occurred_at,
                   trigger_time AS last_occurred_at,
                   CASE WHEN UPPER(event_status) = 'RESOLVED' THEN acknowledged_time END AS recovered_at,
                   CASE
                       WHEN UPPER(event_status) = 'ACKNOWLEDGED' THEN 'PROCESSING'
                       WHEN UPPER(event_status) = 'RESOLVED' THEN 'RECOVERED'
                       ELSE 'OPEN'
                   END AS source_status,
                   acknowledged_by AS source_owner_name,
                   remark AS source_handle_remark
            FROM channel_alert_event
            WHERE trigger_time >= :beginTime AND trigger_time <= :endTime AND deleted = 0
            UNION ALL
            SELECT 'SECURITY' AS source_type,
                   event_no AS source_id,
                   CASE
                       WHEN UPPER(risk_level) = 'CRITICAL' THEN 'CRITICAL'
                       WHEN UPPER(risk_level) IN ('HIGH', 'ERROR') THEN 'ERROR'
                       ELSE 'WARNING'
                   END AS level,
                   COALESCE(NULLIF(TRIM(service_name), ''), NULLIF(TRIM(source_layer), '')) AS service_name,
                   'SECURITY' AS module_name,
                   event_type AS title,
                   COALESCE(NULLIF(TRIM(reason_message), ''), NULLIF(TRIM(reason_code), '')) AS content,
                   1 AS occurrence_count,
                   event_time AS first_occurred_at,
                   event_time AS last_occurred_at,
                   CASE WHEN process_status <> 0 THEN processed_time END AS recovered_at,
                   CASE WHEN process_status = 0 THEN 'OPEN' ELSE 'CLOSED' END AS source_status,
                   processed_by AS source_owner_name,
                   process_remark AS source_handle_remark
            FROM security_intercept_event
            WHERE event_time >= :beginTime AND event_time <= :endTime
            """;

    /** Admin 数据库命名参数查询模板，查询超时固定为五秒。 */
    private final NamedParameterJdbcTemplate jdbcTemplate;
    /** 跨交易分表执行逻辑表只读查询的执行器。 */
    private final TransactionLogicalReadExecutor transactionReadExecutor;
    /** 监控时间范围、时区和桶宽度规范器。 */
    private final MonitorTimeRangeNormalizer rangeNormalizer;
    /** 当前 Admin JVM 运行时指标采样器。 */
    private final AdminRuntimeMonitorSampler runtimeSampler;
    /** Nacos 等注册中心的服务发现客户端。 */
    private final DiscoveryClient discoveryClient;
    /** 管理端交易详情查询服务，用于构建结构化交易链路。 */
    private final AdminTransactionQueryService transactionQueryService;
    /** Redis 运行指标和受控 Key 元数据查询应用服务。 */
    private final AdminMonitorCacheApplicationService cacheApplicationService;
    /** 数据源、Hikari 和分表运行快照查询应用服务。 */
    private final AdminMonitorDatasourceApplicationService datasourceApplicationService;
    /** 日志、告警和链路摘要统一脱敏器。 */
    private final MonitorSensitiveTextSanitizer sensitiveTextSanitizer;
    /** RocketMQ Admin 只读能力提供器。 */
    private final RocketMqAdminMonitorProvider rocketMqAdminMonitorProvider;

    /**
     * 创建 JDBC 系统监控工作台服务。
     *
     * @param dataSource Admin 运行时数据源
     * @param transactionReadExecutor 交易逻辑表只读执行器
     * @param shardingProperties 交易分表和查询数据源配置
     * @param jdbcTemplateFactory 监控 JDBC 模板工厂
     * @param rangeNormalizer 时间范围规范器
     * @param runtimeSampler JVM 运行时采样器
     * @param discoveryClient 服务发现客户端
     * @param transactionQueryService 交易详情查询服务
     * @param cacheApplicationService Redis 监控应用服务
     * @param datasourceApplicationService 数据源监控应用服务
     * @param sensitiveTextSanitizer 监控文本脱敏器
     * @param rocketMqAdminMonitorProvider RocketMQ Admin 监控提供器
     */
    public JdbcAdminMonitorWorkbenchService(DataSource dataSource,
                                            TransactionLogicalReadExecutor transactionReadExecutor,
                                            TransactionShardingProperties shardingProperties,
                                            TransactionQueryJdbcTemplateFactory jdbcTemplateFactory,
                                            MonitorTimeRangeNormalizer rangeNormalizer,
                                            AdminRuntimeMonitorSampler runtimeSampler,
                                            DiscoveryClient discoveryClient,
                                            AdminTransactionQueryService transactionQueryService,
                                            AdminMonitorCacheApplicationService cacheApplicationService,
                                            AdminMonitorDatasourceApplicationService datasourceApplicationService,
                                            MonitorSensitiveTextSanitizer sensitiveTextSanitizer,
                                            RocketMqAdminMonitorProvider rocketMqAdminMonitorProvider) {
        this.jdbcTemplate = jdbcTemplateFactory.create(dataSource, shardingProperties);
        this.jdbcTemplate.getJdbcTemplate().setQueryTimeout(5);
        this.transactionReadExecutor = transactionReadExecutor;
        this.rangeNormalizer = rangeNormalizer;
        this.runtimeSampler = runtimeSampler;
        this.discoveryClient = discoveryClient;
        this.transactionQueryService = transactionQueryService;
        this.cacheApplicationService = cacheApplicationService;
        this.datasourceApplicationService = datasourceApplicationService;
        this.sensitiveTextSanitizer = sensitiveTextSanitizer;
        this.rocketMqAdminMonitorProvider = rocketMqAdminMonitorProvider;
    }

    /**
     * 聚合服务、API、渠道、Webhook 和告警总览。
     *
     * @param query 时间范围条件；允许为空
     * @return 系统监控总览
     */
    @Override
    public OverviewResponse overview(TimeRangeQuery query) {
        TimeRangeQuery safeQuery = query == null ? new TimeRangeQuery() : query;
        ApiMonitorQuery apiQuery = copyRange(safeQuery, new ApiMonitorQuery());
        ChannelMonitorQuery channelQuery = copyRange(safeQuery, new ChannelMonitorQuery());
        WebhookMonitorQuery webhookQuery = copyRange(safeQuery, new WebhookMonitorQuery());
        AlertQuery alertQuery = copyRange(safeQuery, new AlertQuery());
        ApiMonitorResponse api = searchApis(apiQuery);
        ChannelMonitorResponse channels = searchChannels(channelQuery);
        WebhookMonitorResponse webhooks = searchWebhooks(webhookQuery);
        AlertSearchResponse alerts = searchAlerts(alertQuery);
        ServiceInventoryResponse services = services();

        OverviewResponse response = new OverviewResponse();
        response.setSummaries(List.of(
                summary("services", "服务实例", decimal(services.getHealthyInstanceCount()), "count",
                        services.getUnhealthyInstanceCount() == 0 ? "HEALTHY" : "ERROR",
                        services.getHealthyInstanceCount() + "/" + services.getInstanceCount()),
                findSummary(alerts.getSummaries(), "openAlerts", "未处理告警"),
                findSummary(api.getSummaries(), "successRate", "API 成功率"),
                findSummary(channels.getSummaries(), "healthyChannels", "渠道健康"),
                findSummary(webhooks.getSummaries(), "failed", "Webhook 失败")
        ));
        response.setApiTrend(api.getRequestTrend());
        response.setApiLatencyTrend(api.getLatencyTrend());
        response.setAlertTrend(alerts.getTrend());
        response.setDependencies(dependencyStatuses(services));
        response.setLatestAlerts(alerts.getPage() == null ? List.of()
                : alerts.getPage().getRecords().stream().limit(10).toList());
        response.setGeneratedAt(LocalDateTime.now());
        return response;
    }

    /**
     * 从服务发现客户端读取并脱敏服务实例清单。
     *
     * @return 服务和实例统计及历史指标能力状态
     */
    @Override
    public ServiceInventoryResponse services() {
        ServiceInventoryResponse response = new ServiceInventoryResponse();
        List<ServiceInstanceItem> instances = new ArrayList<>();
        try {
            List<String> serviceNames = new ArrayList<>(discoveryClient.getServices());
            serviceNames.sort(String::compareToIgnoreCase);
            for (String serviceName : serviceNames) {
                for (ServiceInstance instance : discoveryClient.getInstances(serviceName)) {
                    ServiceInstanceItem item = new ServiceInstanceItem();
                    item.setServiceName(serviceName);
                    item.setInstanceId(instance.getInstanceId());
                    item.setHost(instance.getHost());
                    item.setPort(instance.getPort());
                    item.setSecure(instance.isSecure());
                    item.setStatus("HEALTHY");
                    item.setMetadata(sanitizeMetadata(instance.getMetadata()));
                    instances.add(item);
                }
            }
            response.setServiceCount(serviceNames.size());
        } catch (RuntimeException exception) {
            response.setServiceCount(0L);
        }
        response.setInstances(instances);
        response.setInstanceCount(instances.size());
        response.setHealthyInstanceCount(instances.stream().filter(item -> "HEALTHY".equals(item.getStatus())).count());
        response.setUnhealthyInstanceCount(response.getInstanceCount() - response.getHealthyInstanceCount());
        response.setMetricsCapability(capability("PROMETHEUS", STATUS_NOT_CONFIGURED,
                "未配置 Prometheus 查询端点，服务级历史 CPU、JVM 与接口指标不可用"));
        return response;
    }

    /**
     * 查询当前 Admin JVM 的进程内运行时采样。
     *
     * @param query 时间范围条件；允许为空
     * @return 当前采样、历史采样和线程池能力状态
     */
    @Override
    public RuntimeResponse runtime(TimeRangeQuery query) {
        TimeRangeQuery safeQuery = query == null ? new TimeRangeQuery() : query;
        NormalizedRange range = rangeNormalizer.normalize(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        RuntimeResponse response = new RuntimeResponse();
        response.setCurrent(runtimeSampler.current());
        response.setSamples(runtimeSampler.between(range.beginTime(), range.endTime()));
        response.setThreadPools(List.of());
        response.setThreadPoolCapability(capability("THREAD_POOL_BINDINGS", STATUS_NOT_CONFIGURED,
                "当前服务未注册统一线程池指标采集器"));
        response.setGeneratedAt(LocalDateTime.now());
        return response;
    }

    /**
     * 查询所有监控数据提供方的配置和可用状态。
     *
     * @return 不包含地址和凭据的能力清单
     */
    @Override
    public List<ProviderCapability> capabilities() {
        Snapshot rocketMq = rocketMqAdminMonitorProvider.snapshot();
        return List.of(
                capability("NACOS_DISCOVERY", STATUS_AVAILABLE, "通过 Spring Cloud DiscoveryClient 读取服务与实例"),
                capability("LOCAL_JVM", STATUS_AVAILABLE, "通过 JVM MXBean 采集当前 Admin 节点并按分钟保存在内存"),
                capability("PROMETHEUS", STATUS_NOT_CONFIGURED, "未配置 Prometheus 查询端点"),
                capability("LOKI", STATUS_NOT_CONFIGURED, "未配置 Loki 查询端点，无法提供应用日志上下文"),
                capability("SKYWALKING_OTEL", STATUS_NOT_CONFIGURED, "未配置 APM Trace 查询提供方"),
                capability("ROCKETMQ_ADMIN", rocketMq.status(), rocketMq.reason())
        );
    }

    /**
     * 从商户 API 交互逻辑表聚合 API 业务指标。
     *
     * @param query API 筛选、时间范围和分页条件；允许为空
     * @return API 趋势、TopN、分页结果和 HTTP 指标能力状态
     */
    @Override
    public ApiMonitorResponse searchApis(ApiMonitorQuery query) {
        ApiMonitorQuery safeQuery = query == null ? new ApiMonitorQuery() : query;
        NormalizedRange range = rangeNormalizer.normalize(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        String serviceName = lower(trim(safeQuery.getServiceName()));
        if (serviceName != null && !"service-openapi".contains(serviceName)) {
            return emptyApiResponse(safeQuery, range);
        }
        ApiMonitorResponse response = transactionReadExecutor.read(() -> loadApiMonitor(safeQuery, range));
        response.setHttpMetricCapability(capability("API_HTTP_METRICS", STATUS_NOT_CONFIGURED,
                "现有商户 API 交互表未保存 HTTP Method、HTTP Status、限流与鉴权结果；页面展示业务响应码"));
        return response;
    }

    /**
     * 从渠道交互逻辑表和渠道告警表聚合渠道指标。
     *
     * @param query 渠道筛选、时间范围和分页条件；允许为空
     * @return 渠道成功率、延迟、错误分布和分页结果
     */
    @Override
    public ChannelMonitorResponse searchChannels(ChannelMonitorQuery query) {
        ChannelMonitorQuery safeQuery = query == null ? new ChannelMonitorQuery() : query;
        NormalizedRange range = rangeNormalizer.normalize(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        Map<String, String> alertStatuses = currentChannelAlertStatuses();
        return transactionReadExecutor.read(() -> loadChannelMonitor(safeQuery, range, alertStatuses));
    }

    /**
     * 从商户通知事件逻辑表聚合 Webhook 投递指标。
     *
     * @param query Webhook 筛选、时间范围和分页条件；允许为空
     * @return Webhook 成功率、重试、HTTP 状态分布和分页结果
     */
    @Override
    public WebhookMonitorResponse searchWebhooks(WebhookMonitorQuery query) {
        WebhookMonitorQuery safeQuery = query == null ? new WebhookMonitorQuery() : query;
        NormalizedRange range = rangeNormalizer.normalize(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        return transactionReadExecutor.read(() -> loadWebhookMonitor(safeQuery, range));
    }

    /**
     * 定位单笔交易并构建脱敏后的结构化业务链路。
     *
     * @param query 交易号、订单号、渠道订单号或 TraceId 定位条件
     * @return 交易摘要、事件和瀑布图数据
     * @throws ServiceException 未找到符合条件的交易时抛出
     */
    @Override
    public TraceResponse searchTrace(TraceSearchQuery query) {
        TraceSearchQuery safeQuery = query == null ? new TraceSearchQuery() : query;
        NormalizedRange range = rangeNormalizer.normalize(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        Map<String, Object> locator = transactionReadExecutor.read(() -> resolveLocator(safeQuery, range));
        if (locator == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "未找到符合条件的交易链路");
        }
        String transactionId = string(locator, "transaction_id");
        LocalDateTime transactionDateTime = dateTime(locator, "transaction_date_time");
        LocalDateTime rootTransactionDateTime = dateTime(locator, "root_transaction_date_time");
        TransactionDetailResponse detail = transactionQueryService.detail(
                transactionId, transactionDateTime, rootTransactionDateTime);

        List<TraceEvent> events = sanitizedTraceEvents(detail);
        events.sort(Comparator.comparing(TraceEvent::getStartTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        TraceResponse response = new TraceResponse();
        response.setSummary(traceSummary(detail, events));
        response.setEvents(events);
        response.setWaterfall(waterfall(events));
        response.setApmCapability(capability("SKYWALKING_OTEL", STATUS_NOT_CONFIGURED,
                "当前仅提供结构化业务时间线，未配置完整 Span 查询提供方"));
        return response;
    }

    /**
     * 聚合交易与安全事实表中的结构化业务日志。
     *
     * @param query 业务标识、级别、服务、关键词和时间范围条件；允许为空
     * @return 脱敏日志分页结果和集中日志上下文能力状态
     */
    @Override
    public LogSearchResponse searchLogs(LogSearchQuery query) {
        LogSearchQuery safeQuery = query == null ? new LogSearchQuery() : query;
        NormalizedRange range = rangeNormalizer.normalize(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        List<StructuredLogItem> logs = transactionReadExecutor.read(() -> transactionLogs(safeQuery, range));
        logs.addAll(securityLogs(safeQuery, range));
        String serviceFilter = lower(trim(safeQuery.getServiceName()));
        String levelFilter = upper(trim(safeQuery.getLevel()));
        String keyword = lower(trim(safeQuery.getKeyword()));
        List<StructuredLogItem> filtered = logs.stream()
                .filter(item -> serviceFilter == null || lower(item.getServiceName()).contains(serviceFilter))
                .filter(item -> levelFilter == null || levelFilter.equals(upper(item.getLevel())))
                .filter(item -> keyword == null || searchableText(item).contains(keyword))
                .sorted(Comparator.comparing(StructuredLogItem::getTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_RESULT_ROWS)
                .toList();
        LogSearchResponse response = new LogSearchResponse();
        response.setPage(page(filtered, safeQuery.safePageNo(), safeQuery.safePageSize()));
        response.setContextCapability(capability("CENTRAL_LOG_CONTEXT", STATUS_NOT_CONFIGURED,
                "未配置 Loki、SkyWalking 或 Elasticsearch，无法提供应用日志全文及上下文 ±20 行"));
        return response;
    }

    /**
     * 查询渠道告警和安全拦截事件的统一告警视图。
     *
     * @param query 告警来源、级别、状态、负责人和时间范围条件；允许为空
     * @return 告警摘要、趋势、来源分布和分页结果
     */
    @Override
    public AlertSearchResponse searchAlerts(AlertQuery query) {
        AlertQuery safeQuery = query == null ? new AlertQuery() : query;
        NormalizedRange range = rangeNormalizer.normalize(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        MapSqlParameterSource params = alertSearchParams(safeQuery, range);
        Map<String, Object> totals = loadAlertTotals(params);
        long total = longValue(totals, "total_count");
        long pageNo = safeQuery.safePageNo();
        long pageSize = safeQuery.safePageSize();
        long offset = Math.max((pageNo - 1L) * pageSize, 0L);
        List<AlertItem> records = offset < total ? loadAlertPage(params, offset, pageSize) : List.of();
        AlertSearchResponse response = new AlertSearchResponse();
        response.setSummaries(alertSummaries(totals));
        response.setTrend(loadAlertTrend(params, range));
        response.setSourceTop(loadAlertSourceTop(params));
        response.setPage(PageResult.of(total, pageNo, pageSize, records));
        return response;
    }

    /**
     * 查询单个来源告警及人工处理历史。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源系统内的告警标识
     * @return 告警详情和关联指标能力状态
     */
    @Override
    public AlertDetailResponse alertDetail(String sourceType, String sourceId) {
        AlertItem alert = requireSourceAlert(sourceType, sourceId);
        applyAlertOverlays(List.of(alert));
        AlertDetailResponse response = new AlertDetailResponse();
        response.setAlert(alert);
        response.setHistory(loadAlertHistory(alert.getSourceType(), alert.getSourceId()));
        response.setSourceMetrics(loadAlertSourceMetrics(alert));
        response.setContextMetricCapability(capability("ALERT_METRIC_CONTEXT", STATUS_NOT_CONFIGURED,
                "历史指标提供方未配置；详情保留触发事实与人工处理历史"));
        return response;
    }

    /**
     * 接手告警；使用请求版本号执行乐观锁更新并写入审计历史。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源系统内的告警标识
     * @param request 当前版本号和可选备注
     * @return 接手后的告警详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertDetailResponse takeOver(String sourceType, String sourceId, AlertActionRequest request) {
        return updateAlert(sourceType, sourceId, request, "TAKEOVER", null, true);
    }

    /**
     * 将告警标记为处理中；使用请求版本号阻止并发终态覆盖。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源系统内的告警标识
     * @param request 当前版本号和可选备注
     * @return 标记处理后的告警详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertDetailResponse markProcessing(String sourceType, String sourceId, AlertActionRequest request) {
        return updateAlert(sourceType, sourceId, request, "MARK_PROCESSING", ALERT_PROCESSING, false);
    }

    /**
     * 关闭告警；使用请求版本号阻止并发状态覆盖并写入审计历史。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源系统内的告警标识
     * @param request 当前版本号和可选备注
     * @return 关闭后的告警详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertDetailResponse closeAlert(String sourceType, String sourceId, AlertActionRequest request) {
        return updateAlert(sourceType, sourceId, request, "CLOSE", ALERT_CLOSED, false);
    }

    /**
     * 聚合 Admin 数据库中的安全拦截事件统计。
     *
     * @param query 时间范围条件；允许为空
     * @return 安全拦截摘要、趋势、事件类型和商户 TopN
     */
    @Override
    public SecurityStatisticsResponse securityStatistics(TimeRangeQuery query) {
        TimeRangeQuery safeQuery = query == null ? new TimeRangeQuery() : query;
        NormalizedRange range = rangeNormalizer.normalize(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        MapSqlParameterSource rangeParams = rangeParams(range);
        Map<String, Object> totals = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS intercept_count,
                       COALESCE(SUM(CASE WHEN risk_level = 'CRITICAL' THEN 1 ELSE 0 END), 0) AS critical_count,
                       COALESCE(SUM(CASE WHEN process_status = 0 THEN 1 ELSE 0 END), 0) AS unhandled_count
                FROM security_intercept_event
                WHERE event_time >= :beginTime AND event_time <= :endTime
                """, rangeParams);
        List<Map<String, Object>> trendRows = jdbcTemplate.queryForList("""
                SELECT TIMESTAMPADD(
                           MINUTE,
                           FLOOR(TIMESTAMPDIFF(MINUTE, '1970-01-01 00:00:00', event_time) / :bucketMinutes)
                               * :bucketMinutes,
                           '1970-01-01 00:00:00'
                       ) AS bucket_time,
                       COUNT(*) AS metric_value
                FROM security_intercept_event
                WHERE event_time >= :beginTime AND event_time <= :endTime
                GROUP BY bucket_time
                ORDER BY bucket_time
                """, rangeParams(range).addValue("bucketMinutes", range.bucketMinutes()));
        List<CategoryMetric> typeTop = securityCategoryTop(range, "event_type");
        List<CategoryMetric> merchantTop = securityCategoryTop(range, "merchant_id");
        long intercepts = longValue(totals, "intercept_count");
        long critical = longValue(totals, "critical_count");
        long unhandled = longValue(totals, "unhandled_count");
        SecurityStatisticsResponse response = new SecurityStatisticsResponse();
        response.setSummaries(List.of(
                summary("intercepts", "拦截总数", decimal(intercepts), "count", "INFO", null),
                summary("critical", "严重事件", decimal(critical), "count", critical > 0 ? "ERROR" : "HEALTHY", null),
                summary("unhandled", "未处理", decimal(unhandled), "count", unhandled > 0 ? "WARNING" : "HEALTHY", null)
        ));
        response.setTrend(aggregatedCountTrend(trendRows, range, "intercepts"));
        response.setTypeTop(typeTop);
        response.setMerchantTop(merchantTop);
        return response;
    }

    /**
     * 按受控列聚合安全拦截事件 TopN。
     *
     * <p>列名不能使用 JDBC 参数绑定，因此仅允许方法内白名单中的固定列，防止动态 SQL
     * 被调用方扩展为任意标识符。</p>
     *
     * @param range 已按数据库时区规范化的查询范围
     * @param column event_type 或 merchant_id
     * @return 最多十个分类及事件数
     * @throws IllegalArgumentException 列名不在固定白名单时抛出
     */
    private List<CategoryMetric> securityCategoryTop(NormalizedRange range, String column) {
        if (!Set.of("event_type", "merchant_id").contains(column)) {
            throw new IllegalArgumentException("unsupported security category column");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT COALESCE(NULLIF(TRIM(%s), ''), 'UNKNOWN') AS category_key,
                       COUNT(*) AS metric_value
                FROM security_intercept_event
                WHERE event_time >= :beginTime AND event_time <= :endTime
                GROUP BY category_key
                ORDER BY metric_value DESC, category_key
                LIMIT 10
                """.formatted(column), rangeParams(range));
        return rows.stream()
                .map(row -> category(
                        string(row, "category_key"),
                        string(row, "category_key"),
                        decimal(longValue(row, "metric_value"))))
                .toList();
    }

    /**
     * 将交易号、渠道订单号、TraceId 或商户订单号统一解析到交易定位表记录。
     *
     * <p>优先使用唯一性更强的交易号；其他标识只在限定时间范围内查找最近一笔，避免
     * 相同商户订单号跨周期复用时串联错误交易。</p>
     *
     * @param query 链路定位条件
     * @param range 已按数据库时区规范化的查询范围
     * @return 交易定位记录；未命中时返回 null
     */
    private Map<String, Object> resolveLocator(TraceSearchQuery query, NormalizedRange range) {
        String transactionId = trim(query.getTransactionId());
        if (transactionId == null) {
            transactionId = resolveTraceTransactionId(query, range);
        }
        if (transactionId != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT transaction_id, operation_id, root_transaction_id, merchant_id, merchant_order_no,
                           transaction_type, transaction_date_time, root_transaction_date_time
                    FROM transaction_locator
                    WHERE transaction_id = :transactionId
                    LIMIT 1
                    """, new MapSqlParameterSource("transactionId", transactionId));
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        }
        String merchantOrderNo = trim(query.getMerchantOrderNo());
        if (merchantOrderNo == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT transaction_id, operation_id, root_transaction_id, merchant_id, merchant_order_no,
                       transaction_type, transaction_date_time, root_transaction_date_time
                FROM transaction_locator
                WHERE merchant_order_no = :merchantOrderNo
                  AND transaction_date_time >= :beginTime AND transaction_date_time <= :endTime
                ORDER BY transaction_date_time DESC, id DESC
                LIMIT 1
                """, baseParams(range).addValue("merchantOrderNo", merchantOrderNo));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 通过渠道订单号或 TraceId 反查交易号。
     *
     * <p>渠道交互优先于商户 API 交互，因为渠道订单号和渠道 Trace 通常更接近问题现场；
     * 所有查询都携带交易日期范围，确保 ShardingSphere 具备可裁剪的分片键。</p>
     *
     * @param query 链路定位条件
     * @param range 已按数据库时区规范化的查询范围
     * @return 交易号；无法定位时返回 null
     */
    private String resolveTraceTransactionId(TraceSearchQuery query, NormalizedRange range) {
        String channelOrderNo = trim(query.getChannelOrderNo());
        if (channelOrderNo != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT transaction_id
                    FROM transaction_channel_request
                    WHERE channel_order_no = :channelOrderNo
                      AND transaction_date_time >= :beginTime AND transaction_date_time <= :endTime
                      AND deleted = 0
                    ORDER BY request_start_time DESC, id DESC
                    LIMIT 1
                    """, baseParams(range).addValue("channelOrderNo", channelOrderNo));
            if (!rows.isEmpty()) {
                return string(rows.get(0), "transaction_id");
            }
        }
        String traceId = trim(query.getTraceId());
        if (traceId == null) {
            return null;
        }
        List<Map<String, Object>> channelRows = jdbcTemplate.queryForList("""
                SELECT transaction_id
                FROM transaction_channel_interaction_log
                WHERE trace_id = :traceId
                  AND transaction_date_time >= :beginTime AND transaction_date_time <= :endTime
                ORDER BY interaction_time DESC, id DESC
                LIMIT 1
                """, baseParams(range).addValue("traceId", traceId));
        if (!channelRows.isEmpty()) {
            return string(channelRows.get(0), "transaction_id");
        }
        List<Map<String, Object>> apiRows = jdbcTemplate.queryForList("""
                SELECT transaction_id
                FROM transaction_merchant_api_interaction_log
                WHERE trace_id = :traceId
                  AND transaction_date_time >= :beginTime AND transaction_date_time <= :endTime
                ORDER BY request_time DESC, id DESC
                LIMIT 1
                """, baseParams(range).addValue("traceId", traceId));
        return apiRows.isEmpty() ? null : string(apiRows.get(0), "transaction_id");
    }

    /**
     * 将交易详情中的 API、流程、渠道和商户通知事实转换为统一链路事件。
     *
     * <p>只使用结构化字段，不读取请求报文、响应报文、卡数据或通知正文；可能包含渠道
     * 错误信息的文本在 {@link #traceEvent} 中统一限长和脱敏。</p>
     *
     * @param detail 已按交易分片定位得到的交易详情
     * @return 具有开始时间的脱敏链路事件
     */
    private List<TraceEvent> sanitizedTraceEvents(TransactionDetailResponse detail) {
        List<TraceEvent> events = new ArrayList<>();
        for (Map<String, Object> row : detail.getMerchantApiInteractionLogs()) {
            events.add(traceEvent(
                    string(row, "api_log_id"), string(row, "transaction_id"), string(row, "trace_id"),
                    "service-openapi", string(row, "api_operation"), string(row, "request_path"),
                    string(row, "request_result"), dateTime(row, "request_time"), dateTime(row, "response_time"),
                    nullableInteger(row, "duration_millis"), string(row, "merchant_response_code"),
                    isFailure(string(row, "request_result")) ? string(row, "merchant_response_message") : null,
                    "Merchant API interaction"));
        }
        for (Map<String, Object> row : detail.getFlowEvents()) {
            events.add(traceEvent(
                    string(row, "flow_event_id"), string(row, "transaction_id"), null,
                    serviceForStage(string(row, "event_stage")), string(row, "event_type"), string(row, "event_name"),
                    string(row, "event_status"), dateTime(row, "event_time"), dateTime(row, "event_time"),
                    0, string(row, "error_code"), string(row, "error_message"), string(row, "event_content")));
        }
        for (Map<String, Object> row : detail.getChannelRequests()) {
            events.add(traceEvent(
                    string(row, "request_id"), string(row, "transaction_id"), null,
                    "channel-" + lowerOrUnknown(string(row, "channel_code")), string(row, "request_scene"),
                    string(row, "channel_code"), string(row, "request_status"), dateTime(row, "request_start_time"),
                    dateTime(row, "response_time"), nullableInteger(row, "duration_millis"),
                    firstText(row, "platform_result_code", row, "acquirer_code"),
                    string(row, "platform_fail_reason"), "Channel request"));
        }
        for (Map<String, Object> row : detail.getChannelInteractionLogs()) {
            events.add(traceEvent(
                    string(row, "interaction_log_id"), string(row, "transaction_id"), string(row, "trace_id"),
                    "channel-" + lowerOrUnknown(string(row, "channel_code")), string(row, "interaction_type"),
                    string(row, "channel_code"), interactionResult(row), dateTime(row, "interaction_time"),
                    dateTime(row, "interaction_time"), nullableInteger(row, "duration_millis"),
                    nullableInteger(row, "http_status") == null ? null : String.valueOf(integer(row, "http_status")),
                    string(row, "exception_message"), "Channel interaction"));
        }
        for (Map<String, Object> row : detail.getMerchantNotificationLogs()) {
            events.add(traceEvent(
                    string(row, "notify_log_id"), string(row, "transaction_id"), null,
                    "service-webhook", "MERCHANT_NOTIFICATION", "Attempt " + integer(row, "attempt_no"),
                    bool(row, "success") ? "SUCCESS" : "FAILED", dateTime(row, "notify_time"),
                    dateTime(row, "notify_time"), nullableInteger(row, "duration_millis"),
                    nullableInteger(row, "http_status") == null ? null : String.valueOf(integer(row, "http_status")),
                    string(row, "error_message"), "Merchant notification attempt"));
        }
        return events.stream().filter(item -> item.getStartTime() != null).toList();
    }

    /**
     * 构建链路头部交易摘要并计算端到端耗时。
     *
     * <p>完成时间优先取最后一个业务操作时间，缺失时退化到最后一个结构化事件，确保
     * 未完成交易仍能显示当前已发生的链路跨度；负耗时统一收敛为零。</p>
     *
     * @param detail 交易详情
     * @param events 已脱敏的链路事件
     * @return 交易摘要，金额沿用交易详情的展示币种与精度
     */
    private TraceSummary traceSummary(TransactionDetailResponse detail, List<TraceEvent> events) {
        TransactionOrderResponse order = detail.getOrder();
        TraceSummary summary = new TraceSummary();
        summary.setTransactionId(order.getLatestTransactionId());
        summary.setRootTransactionId(order.getRootTransactionId());
        summary.setOperationId(order.getOperationId());
        summary.setMerchantId(order.getMerchantId());
        summary.setMerchantOrderNo(order.getMerchantOrderNo());
        summary.setTransactionType(order.getTransactionType());
        summary.setTransactionStatus(order.getTransactionStatus());
        summary.setCurrency(order.getLabelCurrency());
        summary.setAmount(order.getLabelAmount());
        summary.setPaymentMethod(order.getPaymentMethod());
        summary.setChannelCode(order.getChannelCode());
        summary.setChannelOrderNo(order.getChannelOrderNo());
        summary.setCreateTime(order.getTransactionDateTime());
        LocalDateTime completeTime = detail.getOperations().stream()
                .map(TransactionOperationResponse::getOperationTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElseGet(() -> events.stream().map(TraceEvent::getEndTime).filter(Objects::nonNull)
                        .max(LocalDateTime::compareTo).orElse(order.getTransactionDateTime()));
        summary.setCompleteTime(completeTime);
        if (summary.getCreateTime() != null && completeTime != null) {
            summary.setTotalDurationMillis(Math.max(Duration.between(summary.getCreateTime(), completeTime).toMillis(), 0L));
        }
        return summary;
    }

    /**
     * 将结构化链路事件转换为相对首事件的瀑布图阶段。
     *
     * <p>零耗时事件使用一毫秒最小宽度以保证前端可见，不据此伪造真实耗时。</p>
     *
     * @param events 已按开始时间排序的链路事件
     * @return 瀑布图相对偏移和展示状态
     */
    private List<WaterfallStage> waterfall(List<TraceEvent> events) {
        LocalDateTime base = events.stream().map(TraceEvent::getStartTime).filter(Objects::nonNull)
                .min(LocalDateTime::compareTo).orElse(null);
        if (base == null) {
            return List.of();
        }
        List<WaterfallStage> stages = new ArrayList<>();
        for (TraceEvent event : events) {
            WaterfallStage stage = new WaterfallStage();
            stage.setKey(event.getEventId());
            stage.setLabel(StringUtils.hasText(event.getEventName()) ? event.getEventName() : event.getEventType());
            stage.setStartMillis(Math.max(Duration.between(base, event.getStartTime()).toMillis(), 0L));
            stage.setDurationMillis(Math.max(event.getDurationMillis() == null ? 0 : event.getDurationMillis(), 1));
            stage.setStatus(isFailure(event.getResult()) ? "error" : "normal");
            stages.add(stage);
        }
        return stages;
    }

    /**
     * 从交易逻辑表聚合结构化 API、流程、渠道与 Webhook 日志。
     *
     * <p>每类事实最多读取 {@link #MAX_RESULT_ROWS} 条并在上层再次统一限流；该查询不读取
     * 原始报文，避免管理端日志搜索暴露支付敏感数据。</p>
     *
     * @param query 交易日志筛选条件
     * @param range 已按数据库时区规范化的查询范围
     * @return 未做页面级筛选和排序的结构化交易日志
     */
    private List<StructuredLogItem> transactionLogs(LogSearchQuery query, NormalizedRange range) {
        String transactionId = resolveLogTransactionId(query, range);
        MapSqlParameterSource params = baseParams(range)
                .addValue("transactionId", transactionId)
                .addValue("merchantId", trim(query.getMerchantId()))
                .addValue("traceId", trim(query.getTraceId()));
        List<StructuredLogItem> logs = new ArrayList<>();

        List<Map<String, Object>> apiRows = jdbcTemplate.queryForList("""
                SELECT api_log_id, request_time, request_result, merchant_response_code,
                       merchant_response_message, api_operation, request_path, trace_id,
                       transaction_id, merchant_id, merchant_order_no
                FROM transaction_merchant_api_interaction_log
                WHERE transaction_date_time >= :beginTime AND transaction_date_time <= :endTime
                  AND (:transactionId IS NULL OR transaction_id = :transactionId)
                  AND (:merchantId IS NULL OR merchant_id = :merchantId)
                  AND (:traceId IS NULL OR trace_id = :traceId)
                ORDER BY request_time DESC, id DESC
                LIMIT :limit
                """, params);
        for (Map<String, Object> row : apiRows) {
            logs.add(logItem(
                    string(row, "api_log_id"), dateTime(row, "request_time"),
                    isFailedApi(row) ? "ERROR" : "INFO", "service-openapi", "MERCHANT_API",
                    firstText(row, "merchant_response_message", row, "api_operation"),
                    string(row, "trace_id"), string(row, "transaction_id"), string(row, "merchant_id"),
                    string(row, "merchant_order_no")));
        }

        List<Map<String, Object>> flowRows = jdbcTemplate.queryForList("""
                SELECT flow_event_id, event_time, event_stage, event_type, event_name, event_status,
                       event_content, error_code, error_message, transaction_id, reference_id
                FROM transaction_flow_event
                WHERE transaction_date_time >= :beginTime AND transaction_date_time <= :endTime
                  AND (:transactionId IS NULL OR transaction_id = :transactionId)
                ORDER BY event_time DESC, id DESC
                LIMIT :limit
                """, params);
        for (Map<String, Object> row : flowRows) {
            boolean failed = isFailure(string(row, "event_status"));
            logs.add(logItem(
                    string(row, "flow_event_id"), dateTime(row, "event_time"), failed ? "ERROR" : "INFO",
                    serviceForStage(string(row, "event_stage")), string(row, "event_type"),
                    firstText(row, failed ? "error_message" : "event_content", row, "event_name"),
                    null, string(row, "transaction_id"), null, string(row, "reference_id")));
        }

        List<Map<String, Object>> channelRows = jdbcTemplate.queryForList("""
                SELECT interaction_log_id, interaction_time, interaction_type, http_status,
                       exception_type, exception_message, trace_id, transaction_id, channel_code, request_id
                FROM transaction_channel_interaction_log
                WHERE transaction_date_time >= :beginTime AND transaction_date_time <= :endTime
                  AND (:transactionId IS NULL OR transaction_id = :transactionId)
                  AND (:traceId IS NULL OR trace_id = :traceId)
                ORDER BY interaction_time DESC, id DESC
                LIMIT :limit
                """, params);
        for (Map<String, Object> row : channelRows) {
            Integer httpStatus = nullableInteger(row, "http_status");
            boolean failed = "EXCEPTION".equals(upper(string(row, "interaction_type")))
                    || (httpStatus != null && httpStatus >= 400);
            logs.add(logItem(
                    string(row, "interaction_log_id"), dateTime(row, "interaction_time"), failed ? "ERROR" : "INFO",
                    "channel-" + lowerOrUnknown(string(row, "channel_code")), string(row, "interaction_type"),
                    firstText(row, "exception_message", row, "exception_type"), string(row, "trace_id"),
                    string(row, "transaction_id"), null, string(row, "request_id")));
        }

        List<Map<String, Object>> webhookRows = jdbcTemplate.queryForList("""
                SELECT notify_log_id, notify_time, success, http_status, error_message,
                       transaction_id, merchant_id, notify_id
                FROM transaction_merchant_notification_log
                WHERE transaction_date_time >= :beginTime AND transaction_date_time <= :endTime
                  AND (:transactionId IS NULL OR transaction_id = :transactionId)
                  AND (:merchantId IS NULL OR merchant_id = :merchantId)
                ORDER BY notify_time DESC, id DESC
                LIMIT :limit
                """, params);
        for (Map<String, Object> row : webhookRows) {
            boolean failed = !bool(row, "success");
            logs.add(logItem(
                    string(row, "notify_log_id"), dateTime(row, "notify_time"), failed ? "ERROR" : "INFO",
                    "service-webhook", "MERCHANT_NOTIFICATION",
                    failed ? string(row, "error_message") : "Merchant notification delivered",
                    null, string(row, "transaction_id"), string(row, "merchant_id"), string(row, "notify_id")));
        }
        return logs;
    }

    /**
     * 使用商户订单号或渠道订单号补充解析日志查询所需的交易号。
     *
     * @param query 日志查询条件
     * @param range 已按数据库时区规范化的查询范围
     * @return 显式或反查得到的交易号；没有可用定位条件时返回 null
     */
    private String resolveLogTransactionId(LogSearchQuery query, NormalizedRange range) {
        String transactionId = trim(query.getTransactionId());
        if (transactionId != null) {
            return transactionId;
        }
        String merchantOrderNo = trim(query.getMerchantOrderNo());
        if (merchantOrderNo != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT transaction_id
                    FROM transaction_locator
                    WHERE merchant_order_no = :merchantOrderNo
                      AND transaction_date_time >= :beginTime AND transaction_date_time <= :endTime
                    ORDER BY transaction_date_time DESC, id DESC
                    LIMIT 1
                    """, baseParams(range).addValue("merchantOrderNo", merchantOrderNo));
            if (!rows.isEmpty()) {
                return string(rows.get(0), "transaction_id");
            }
        }
        String channelOrderNo = trim(query.getChannelOrderNo());
        if (channelOrderNo != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT transaction_id
                    FROM transaction_channel_request
                    WHERE channel_order_no = :channelOrderNo
                      AND transaction_date_time >= :beginTime AND transaction_date_time <= :endTime
                      AND deleted = 0
                    ORDER BY request_start_time DESC, id DESC
                    LIMIT 1
                    """, baseParams(range).addValue("channelOrderNo", channelOrderNo));
            if (!rows.isEmpty()) {
                return string(rows.get(0), "transaction_id");
            }
        }
        return null;
    }

    /**
     * 查询 Admin 库中的安全拦截事实并转换为统一结构化日志。
     *
     * <p>仅返回规则、原因码和已脱敏原因文本，不向日志工作台透传安全请求原文。</p>
     *
     * @param query 日志查询条件
     * @param range 已按数据库时区规范化的查询范围
     * @return 安全拦截结构化日志
     */
    private List<StructuredLogItem> securityLogs(LogSearchQuery query, NormalizedRange range) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT event_no, event_time, risk_level, source_layer, event_type, reason_code,
                       reason_message, service_name, trace_id, request_id, merchant_id
                FROM security_intercept_event
                WHERE event_time >= :beginTime AND event_time <= :endTime
                  AND (:merchantId IS NULL OR merchant_id = :merchantId)
                  AND (:traceId IS NULL OR trace_id = :traceId)
                ORDER BY event_time DESC, id DESC
                LIMIT :limit
                """, baseParams(range)
                .addValue("merchantId", trim(query.getMerchantId()))
                .addValue("traceId", trim(query.getTraceId())));
        List<StructuredLogItem> logs = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String risk = upper(string(row, "risk_level"));
            String level = "HIGH".equals(risk) || "CRITICAL".equals(risk) ? "ERROR" : "WARN";
            logs.add(logItem(
                    string(row, "event_no"), dateTime(row, "event_time"), level,
                    firstText(row, "service_name", row, "source_layer"), string(row, "event_type"),
                    firstText(row, "reason_message", row, "reason_code"), string(row, "trace_id"),
                    null, string(row, "merchant_id"), string(row, "request_id")));
        }
        return logs;
    }

    /**
     * 规范化统一告警查询参数。
     *
     * @param query 告警筛选条件
     * @param range 已按数据库时区规范化的查询范围
     * @return 可复用于摘要、趋势、TopN 和分页 SQL 的命名参数
     */
    private MapSqlParameterSource alertSearchParams(AlertQuery query, NormalizedRange range) {
        return rangeParams(range)
                .addValue("sourceType", upper(trim(query.getSourceType())))
                .addValue("level", upper(trim(query.getLevel())))
                .addValue("status", upper(trim(query.getStatus())))
                .addValue("ownerAccountId", trim(query.getOwnerAccountId()))
                .addValue("keyword", lower(trim(query.getKeyword())))
                .addValue("bucketMinutes", range.bucketMinutes());
    }

    /**
     * 在统一有效告警视图上计算状态和严重级别摘要。
     *
     * @param params 已规范化的告警查询参数
     * @return 总数、待处理、处理中、严重和已恢复计数
     */
    private Map<String, Object> loadAlertTotals(MapSqlParameterSource params) {
        return jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total_count,
                       COALESCE(SUM(CASE WHEN status = 'OPEN' THEN 1 ELSE 0 END), 0) AS open_count,
                       COALESCE(SUM(CASE WHEN status = 'PROCESSING' THEN 1 ELSE 0 END), 0) AS processing_count,
                       COALESCE(SUM(CASE WHEN level = 'CRITICAL' THEN 1 ELSE 0 END), 0) AS critical_count,
                       COALESCE(SUM(CASE WHEN status = 'RECOVERED' THEN 1 ELSE 0 END), 0) AS recovered_count
                FROM %s
                WHERE %s
                """.formatted(alertEffectiveFromSql(), alertSearchWhere()), params);
    }

    /**
     * 按固定时间桶聚合告警严重级别趋势，并补齐无事件时间桶。
     *
     * @param params 已规范化的告警查询参数
     * @param range 时间范围与桶宽度
     * @return 按时间升序排列的 warning、error、critical 趋势
     */
    private List<TimeBucket> loadAlertTrend(MapSqlParameterSource params, NormalizedRange range) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT %s AS bucket_time,
                       COALESCE(SUM(CASE WHEN level = 'WARNING' THEN 1 ELSE 0 END), 0) AS warning_count,
                       COALESCE(SUM(CASE WHEN level = 'ERROR' THEN 1 ELSE 0 END), 0) AS error_count,
                       COALESCE(SUM(CASE WHEN level = 'CRITICAL' THEN 1 ELSE 0 END), 0) AS critical_count
                FROM %s
                WHERE %s
                GROUP BY bucket_time
                ORDER BY bucket_time
                """.formatted(databaseBucketExpression("last_occurred_at"),
                alertEffectiveFromSql(), alertSearchWhere()), params);
        Map<LocalDateTime, Map<String, BigDecimal>> buckets = initializeBuckets(
                range, List.of("warning", "error", "critical"));
        for (Map<String, Object> row : rows) {
            Map<String, BigDecimal> values = buckets.get(dateTime(row, "bucket_time"));
            if (values != null) {
                values.put("warning", decimal(longValue(row, "warning_count")));
                values.put("error", decimal(longValue(row, "error_count")));
                values.put("critical", decimal(longValue(row, "critical_count")));
            }
        }
        return toTimeBuckets(buckets);
    }

    /**
     * 按来源系统聚合告警数量。
     *
     * @param params 已规范化的告警查询参数
     * @return CHANNEL、SECURITY 等来源的 TopN
     */
    private List<CategoryMetric> loadAlertSourceTop(MapSqlParameterSource params) {
        return loadCategoryTop("""
                SELECT source_type AS category_key, COUNT(*) AS metric_value
                FROM %s
                WHERE %s
                GROUP BY source_type
                ORDER BY metric_value DESC, category_key
                LIMIT 10
                """.formatted(alertEffectiveFromSql(), alertSearchWhere()), params);
    }

    /**
     * 从统一有效告警视图读取一页记录。
     *
     * @param params 已规范化的告警查询参数
     * @param offset 从零开始的分页偏移
     * @param limit 最大返回条数
     * @return 按最近发生时间倒序排列的告警
     */
    private List<AlertItem> loadAlertPage(MapSqlParameterSource params, long offset, long limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT source_type, source_id, level, service_name, module_name, title, content,
                       occurrence_count, first_occurred_at, last_occurred_at, recovered_at,
                       status, owner_account_id, owner_name, handle_remark, version
                FROM %s
                WHERE %s
                ORDER BY last_occurred_at DESC, source_type, source_id
                LIMIT :offset, :limit
                """.formatted(alertEffectiveFromSql(), alertSearchWhere()), copyParams(params)
                .addValue("offset", offset)
                .addValue("limit", limit));
        return rows.stream().map(this::alertItem).toList();
    }

    /**
     * 构造来源告警与人工处理覆盖表合并后的派生表 SQL。
     *
     * <p>来源已经恢复或关闭时始终以来源终态为准，人工覆盖只能影响仍处于活动状态的
     * 告警，避免历史接手状态把自动恢复结果重新覆盖为处理中。</p>
     *
     * @return 可作为 FROM 子句使用的统一有效告警 SQL
     */
    private String alertEffectiveFromSql() {
        return """
                (
                    SELECT source_alerts.source_type,
                           source_alerts.source_id,
                           source_alerts.level,
                           source_alerts.service_name,
                           source_alerts.module_name,
                           source_alerts.title,
                           source_alerts.content,
                           source_alerts.occurrence_count,
                           source_alerts.first_occurred_at,
                           source_alerts.last_occurred_at,
                           source_alerts.recovered_at,
                           CASE
                               WHEN source_alerts.source_status IN ('RECOVERED', 'CLOSED')
                                   THEN source_alerts.source_status
                               ELSE COALESCE(handle_state.status, source_alerts.source_status)
                           END AS status,
                           handle_state.owner_account_id,
                           COALESCE(NULLIF(TRIM(handle_state.owner_name), ''),
                                    source_alerts.source_owner_name) AS owner_name,
                           COALESCE(NULLIF(TRIM(handle_state.handle_remark), ''),
                                    source_alerts.source_handle_remark) AS handle_remark,
                           COALESCE(handle_state.version, 0) AS version
                    FROM (
                        %s
                    ) source_alerts
                    LEFT JOIN monitor_alert_handle_state handle_state
                      ON handle_state.source_type = source_alerts.source_type
                     AND handle_state.source_id = source_alerts.source_id
                ) effective_alerts
                """.formatted(ALERT_SOURCE_SQL);
    }

    /**
     * 返回统一告警派生表的固定筛选条件。
     *
     * @return 仅包含命名参数的 WHERE 条件，不拼接用户输入
     */
    private String alertSearchWhere() {
        return """
                (:sourceType IS NULL OR source_type = :sourceType)
                  AND (:level IS NULL OR level = :level)
                  AND (:status IS NULL OR status = :status)
                  AND (:ownerAccountId IS NULL OR owner_account_id = :ownerAccountId)
                  AND (
                      :keyword IS NULL
                      OR LOWER(CONCAT_WS(' ', source_type, source_id, service_name, module_name,
                                         title, content, owner_name)) LIKE CONCAT('%', :keyword, '%')
                  )
                """.strip();
    }

    /**
     * 将统一有效告警查询行转换为页面模型，并限制可展示文本长度。
     *
     * @param row 数据库查询行
     * @return 告警页面模型
     */
    private AlertItem alertItem(Map<String, Object> row) {
        AlertItem item = new AlertItem();
        item.setSourceType(string(row, "source_type"));
        item.setSourceId(string(row, "source_id"));
        item.setLevel(string(row, "level"));
        item.setServiceName(string(row, "service_name"));
        item.setModuleName(string(row, "module_name"));
        item.setTitle(safeText(string(row, "title"), 256));
        item.setContent(safeText(string(row, "content"), 512));
        item.setOccurrenceCount(longValue(row, "occurrence_count"));
        item.setFirstOccurredAt(dateTime(row, "first_occurred_at"));
        item.setLastOccurredAt(dateTime(row, "last_occurred_at"));
        item.setRecoveredAt(dateTime(row, "recovered_at"));
        item.setStatus(string(row, "status"));
        item.setOwnerAccountId(string(row, "owner_account_id"));
        item.setOwnerName(string(row, "owner_name"));
        item.setHandleRemark(safeText(string(row, "handle_remark"), 512));
        item.setVersion(integer(row, "version"));
        return item;
    }

    /**
     * 将渠道告警事实转换为统一告警模型。
     *
     * @param row channel_alert_event 查询行
     * @return 渠道来源告警
     */
    private AlertItem channelAlert(Map<String, Object> row) {
        AlertItem item = new AlertItem();
        item.setSourceType("CHANNEL");
        item.setSourceId(string(row, "event_code"));
        item.setLevel(normalizeAlertLevel(string(row, "alert_level")));
        item.setServiceName("channel-" + lowerOrUnknown(string(row, "channel_code")));
        item.setModuleName("CHANNEL");
        item.setTitle(firstText(row, "rule_name", row, "rule_type"));
        item.setContent(safeText("Channel " + Objects.toString(string(row, "channel_code"), "UNKNOWN")
                + " triggered " + Objects.toString(string(row, "rule_type"), "UNKNOWN"), 512));
        item.setOccurrenceCount(1L);
        item.setFirstOccurredAt(dateTime(row, "trigger_time"));
        item.setLastOccurredAt(dateTime(row, "trigger_time"));
        item.setRecoveredAt("RESOLVED".equals(upper(string(row, "event_status")))
                ? dateTime(row, "acknowledged_time") : null);
        item.setStatus(sourceAlertStatus(string(row, "event_status")));
        item.setOwnerName(string(row, "acknowledged_by"));
        item.setHandleRemark(safeText(string(row, "remark"), 512));
        return item;
    }

    /**
     * 将安全拦截事实转换为统一告警模型。
     *
     * @param row security_intercept_event 查询行
     * @return 安全来源告警
     */
    private AlertItem securityAlert(Map<String, Object> row) {
        AlertItem item = new AlertItem();
        item.setSourceType("SECURITY");
        item.setSourceId(string(row, "event_no"));
        item.setLevel(normalizeAlertLevel(string(row, "risk_level")));
        item.setServiceName(firstText(row, "service_name", row, "source_layer"));
        item.setModuleName("SECURITY");
        item.setTitle(string(row, "event_type"));
        item.setContent(safeText(firstText(row, "reason_message", row, "reason_code"), 512));
        item.setOccurrenceCount(1L);
        item.setFirstOccurredAt(dateTime(row, "event_time"));
        item.setLastOccurredAt(dateTime(row, "event_time"));
        item.setStatus(integer(row, "process_status") == 0 ? ALERT_OPEN : ALERT_CLOSED);
        item.setRecoveredAt(integer(row, "process_status") == 0 ? null : dateTime(row, "processed_time"));
        item.setOwnerName(string(row, "processed_by"));
        item.setHandleRemark(safeText(string(row, "process_remark"), 512));
        return item;
    }

    /**
     * 批量应用人工处理状态、负责人、备注和乐观锁版本。
     *
     * <p>使用来源类型和来源 ID 的组合键匹配，来源终态仍由
     * {@link #effectiveAlertStatus(String, String)} 保护。</p>
     *
     * @param alerts 待补充人工处理状态的告警列表
     */
    private void applyAlertOverlays(List<AlertItem> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return;
        }
        Set<String> sourceTypes = alerts.stream().map(AlertItem::getSourceType).collect(Collectors.toSet());
        Set<String> sourceIds = alerts.stream().map(AlertItem::getSourceId).collect(Collectors.toSet());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT source_type, source_id, status, owner_account_id, owner_name,
                       handle_remark, version, update_time
                FROM monitor_alert_handle_state
                WHERE source_type IN (:sourceTypes) AND source_id IN (:sourceIds)
                """, new MapSqlParameterSource()
                .addValue("sourceTypes", sourceTypes)
                .addValue("sourceIds", sourceIds));
        Map<String, Map<String, Object>> overlays = rows.stream().collect(Collectors.toMap(
                row -> alertKey(string(row, "source_type"), string(row, "source_id")),
                Function.identity(), (first, ignored) -> first));
        for (AlertItem item : alerts) {
            Map<String, Object> overlay = overlays.get(alertKey(item.getSourceType(), item.getSourceId()));
            if (overlay == null) {
                continue;
            }
            item.setStatus(effectiveAlertStatus(item.getStatus(), string(overlay, "status")));
            item.setOwnerAccountId(string(overlay, "owner_account_id"));
            String overlayOwnerName = trim(string(overlay, "owner_name"));
            if (overlayOwnerName != null) {
                item.setOwnerName(overlayOwnerName);
            }
            item.setHandleRemark(safeText(firstText(overlay, "handle_remark",
                    Map.of("handle_remark", Objects.toString(item.getHandleRemark(), "")), "handle_remark"), 512));
            item.setVersion(integer(overlay, "version"));
        }
    }

    /**
     * 从来源事实表读取并校验单个告警。
     *
     * @param sourceType CHANNEL 或 SECURITY
     * @param sourceId 来源系统告警标识
     * @return 统一来源告警
     * @throws ServiceException 来源类型非法或告警不存在时抛出
     */
    private AlertItem requireSourceAlert(String sourceType, String sourceId) {
        String normalizedType = upper(trim(sourceType));
        String normalizedId = trim(sourceId);
        if (normalizedType == null || normalizedId == null) {
            throw invalid("sourceType and sourceId are required");
        }
        if ("CHANNEL".equals(normalizedType)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT event_code, rule_name, channel_code, rule_type, alert_level, event_status,
                           sample_count, trigger_time, acknowledged_time, acknowledged_by, remark,
                           window_start_time, window_end_time
                    FROM channel_alert_event
                    WHERE event_code = :sourceId AND deleted = 0
                    LIMIT 1
                    """, new MapSqlParameterSource("sourceId", normalizedId));
            if (!rows.isEmpty()) {
                return channelAlert(rows.get(0));
            }
        } else if ("SECURITY".equals(normalizedType)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT event_no, event_time, source_layer, event_type, risk_level, merchant_id,
                           service_name, reason_code, reason_message, process_status, processed_by,
                           processed_time, process_remark
                    FROM security_intercept_event
                    WHERE event_no = :sourceId
                    LIMIT 1
                    """, new MapSqlParameterSource("sourceId", normalizedId));
            if (!rows.isEmpty()) {
                return securityAlert(rows.get(0));
            }
        } else {
            throw invalid("sourceType must be CHANNEL or SECURITY");
        }
        throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "告警不存在");
    }

    /**
     * 读取告警人工处理审计历史。
     *
     * <p>最多返回最近二百条，避免单个长期告警导致详情接口无界增长。</p>
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源系统告警标识
     * @return 按操作时间倒序排列的处理历史
     */
    private List<AlertHistoryItem> loadAlertHistory(String sourceType, String sourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, action, from_status, to_status, operator_account_id,
                       operator_name, remark, operated_at
                FROM monitor_alert_handle_history
                WHERE source_type = :sourceType AND source_id = :sourceId
                ORDER BY operated_at DESC, id DESC
                LIMIT 200
                """, new MapSqlParameterSource()
                .addValue("sourceType", sourceType)
                .addValue("sourceId", sourceId));
        List<AlertHistoryItem> history = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            AlertHistoryItem item = new AlertHistoryItem();
            item.setId(longValue(row, "id"));
            item.setAction(string(row, "action"));
            item.setFromStatus(string(row, "from_status"));
            item.setToStatus(string(row, "to_status"));
            item.setOperatorAccountId(string(row, "operator_account_id"));
            item.setOperatorName(string(row, "operator_name"));
            item.setRemark(safeText(string(row, "remark"), 512));
            item.setOperatedAt(dateTime(row, "operated_at"));
            history.add(item);
        }
        return history;
    }

    /**
     * 读取告警来源表中可安全展示的触发指标。
     *
     * <p>不读取渠道请求报文或安全事件原文，字符串字段在返回前统一限长和脱敏。</p>
     *
     * @param alert 已定位的来源告警
     * @return 来源指标映射；来源不存在时返回空映射
     */
    private Map<String, Object> loadAlertSourceMetrics(AlertItem alert) {
        if ("CHANNEL".equals(alert.getSourceType())) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT channel_code, rule_type, window_minutes, sample_count, failure_count,
                           success_count, success_rate, error_rate, max_continuous_failure_count,
                           average_latency_millis, trigger_value_count, trigger_value_rate,
                           trigger_value_millis, window_start_time, window_end_time
                    FROM channel_alert_event
                    WHERE event_code = :sourceId AND deleted = 0
                    LIMIT 1
                    """, new MapSqlParameterSource("sourceId", alert.getSourceId()));
            return rows.isEmpty() ? Map.of() : sanitizeMetricMap(rows.get(0));
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT source_layer, event_type, risk_level, action, merchant_id,
                       service_name, hit_rule_code, reason_code, event_time
                FROM security_intercept_event
                WHERE event_no = :sourceId
                LIMIT 1
                """, new MapSqlParameterSource("sourceId", alert.getSourceId()));
        return rows.isEmpty() ? Map.of() : sanitizeMetricMap(rows.get(0));
    }

    /**
     * 使用乐观锁更新告警人工处理状态并追加不可变审计历史。
     *
     * <p>首次操作通过 INSERT IGNORE 建立版本为零的覆盖记录，随后 UPDATE 同时校验版本；
     * 并发更新未命中时返回状态冲突。来源终态校验在写入前完成，防止人工操作覆盖自动恢复
     * 或已关闭告警。</p>
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源系统告警标识
     * @param request 客户端读取到的版本号和可选备注
     * @param action 审计动作
     * @param requestedStatus 目标状态；接手但不改状态时允许为空
     * @param takeOwnership 是否把当前登录账号设为负责人
     * @return 更新后的告警详情
     * @throws ServiceException 版本冲突、状态流转非法或来源告警不存在时抛出
     */
    private AlertDetailResponse updateAlert(String sourceType,
                                            String sourceId,
                                            AlertActionRequest request,
                                            String action,
                                            String requestedStatus,
                                            boolean takeOwnership) {
        if (request == null || request.getVersion() == null || request.getVersion() < 0) {
            throw invalid("version is required");
        }
        AlertItem sourceAlert = requireSourceAlert(sourceType, sourceId);
        String normalizedType = sourceAlert.getSourceType();
        String normalizedId = sourceAlert.getSourceId();
        jdbcTemplate.update("""
                INSERT IGNORE INTO monitor_alert_handle_state
                    (source_type, source_id, status, owner_account_id, owner_name,
                     handle_remark, version, create_time, update_time)
                VALUES (:sourceType, :sourceId, :status, NULL, NULL, NULL, 0,
                        CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource()
                .addValue("sourceType", normalizedType)
                .addValue("sourceId", normalizedId)
                .addValue("status", sourceAlert.getStatus()));
        Map<String, Object> current = jdbcTemplate.queryForMap("""
                SELECT status, owner_account_id, owner_name, handle_remark, version
                FROM monitor_alert_handle_state
                WHERE source_type = :sourceType AND source_id = :sourceId
                """, new MapSqlParameterSource()
                .addValue("sourceType", normalizedType)
                .addValue("sourceId", normalizedId));
        int currentVersion = integer(current, "version");
        if (currentVersion != request.getVersion()) {
            throw new ServiceException(ApiResultEnum.ABNORMAL_CASE_STATE_CONFLICT.getCode(), "告警状态已更新，请刷新后重试");
        }
        String fromStatus = effectiveAlertStatus(sourceAlert.getStatus(), string(current, "status"));
        String toStatus = requestedStatus == null ? fromStatus : requestedStatus;
        validateAlertTransition(fromStatus, toStatus, action);
        Operator operator = currentOperator();
        String ownerAccountId = takeOwnership ? operator.accountId() : string(current, "owner_account_id");
        String ownerName = takeOwnership ? operator.name() : string(current, "owner_name");
        String remark = trim(request.getRemark());
        if (remark == null) {
            remark = string(current, "handle_remark");
        }
        int updated = jdbcTemplate.update("""
                UPDATE monitor_alert_handle_state
                SET status = :status,
                    owner_account_id = :ownerAccountId,
                    owner_name = :ownerName,
                    handle_remark = :remark,
                    version = version + 1,
                    update_time = CURRENT_TIMESTAMP(3)
                WHERE source_type = :sourceType AND source_id = :sourceId AND version = :version
                """, new MapSqlParameterSource()
                .addValue("status", toStatus)
                .addValue("ownerAccountId", ownerAccountId)
                .addValue("ownerName", ownerName)
                .addValue("remark", safeText(remark, 512))
                .addValue("sourceType", normalizedType)
                .addValue("sourceId", normalizedId)
                .addValue("version", currentVersion));
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.ABNORMAL_CASE_STATE_CONFLICT.getCode(), "告警状态已更新，请刷新后重试");
        }
        jdbcTemplate.update("""
                INSERT INTO monitor_alert_handle_history
                    (source_type, source_id, action, from_status, to_status,
                     operator_account_id, operator_name, remark, operated_at)
                VALUES (:sourceType, :sourceId, :action, :fromStatus, :toStatus,
                        :operatorAccountId, :operatorName, :remark, CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource()
                .addValue("sourceType", normalizedType)
                .addValue("sourceId", normalizedId)
                .addValue("action", action)
                .addValue("fromStatus", fromStatus)
                .addValue("toStatus", toStatus)
                .addValue("operatorAccountId", operator.accountId())
                .addValue("operatorName", operator.name())
                .addValue("remark", safeText(trim(request.getRemark()), 512)));
        return alertDetail(normalizedType, normalizedId);
    }

    /**
     * 校验告警状态机边界，尤其阻止终态被普通人工动作重新打开。
     *
     * @param fromStatus 当前有效状态
     * @param toStatus 请求目标状态
     * @param action 人工处理动作
     * @throws ServiceException 状态或动作不满足流转规则时抛出
     */
    private void validateAlertTransition(String fromStatus, String toStatus, String action) {
        if (ALERT_CLOSED.equals(fromStatus) && !"TAKEOVER".equals(action)) {
            throw invalid("closed alert cannot change status");
        }
        if ("MARK_PROCESSING".equals(action)
                && !Set.of(ALERT_OPEN, ALERT_PROCESSING).contains(fromStatus)) {
            throw invalid("only open alerts can be marked processing");
        }
        if (!Set.of(ALERT_OPEN, ALERT_PROCESSING, ALERT_RECOVERED, ALERT_CLOSED).contains(toStatus)) {
            throw invalid("alert status is invalid");
        }
    }

    private List<MetricSummary> alertSummaries(Map<String, Object> totals) {
        long open = longValue(totals, "open_count");
        long processing = longValue(totals, "processing_count");
        long critical = longValue(totals, "critical_count");
        long recovered = longValue(totals, "recovered_count");
        return List.of(
                summary("openAlerts", "未处理告警", decimal(open), "count", open > 0 ? "ERROR" : "HEALTHY", null),
                summary("processing", "处理中", decimal(processing), "count", processing > 0 ? "WARNING" : "INFO", null),
                summary("critical", "严重告警", decimal(critical), "count", critical > 0 ? "ERROR" : "HEALTHY", null),
                summary("recovered", "已恢复", decimal(recovered), "count", "HEALTHY", null)
        );
    }

    /**
     * 由数据库完成 API 全量聚合，仅把低基数分组和固定时间桶返回 Admin 进程。
     *
     * <p>同一季度内使用单物理表窗口函数计算精确分位数；跨季度时先由 ShardingSphere
     * 合并基础聚合，再只对当前页分组补查全局分位数，控制查询次数和内存占用。</p>
     *
     * @param query API 筛选和分页条件
     * @param range 已规范化时间范围
     * @return API 摘要、趋势、TopN 和分页聚合结果
     */
    private ApiMonitorResponse loadApiMonitor(ApiMonitorQuery query, NormalizedRange range) {
        MapSqlParameterSource params = apiMonitorParams(query, range);
        Map<String, Object> totals = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS request_count,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0) AS failed_count,
                       AVG(duration_millis) AS average_millis,
                       COUNT(duration_millis) AS duration_count
                FROM transaction_merchant_api_interaction_log
                WHERE %s
                """.formatted(API_FAILURE_CONDITION, apiMonitorWhere()), params);
        long requestCount = longValue(totals, "request_count");
        long failedCount = longValue(totals, "failed_count");
        long durationCount = longValue(totals, "duration_count");
        BigDecimal p95 = nearestRankPercentile(
                "transaction_merchant_api_interaction_log", apiMonitorWhere(),
                "duration_millis", "id", params, durationCount, 0.95D);
        BigDecimal p99 = nearestRankPercentile(
                "transaction_merchant_api_interaction_log", apiMonitorWhere(),
                "duration_millis", "id", params, durationCount, 0.99D);
        String rateStatus = requestCount == 0L ? "UNKNOWN" : failedCount == 0L ? "HEALTHY" : "WARNING";
        String latencyStatus = durationCount == 0L ? "UNKNOWN" : "INFO";

        ApiMonitorResponse response = new ApiMonitorResponse();
        response.setSummaries(List.of(
                summary("requests", "请求量", decimal(requestCount), "count", "INFO", null),
                summary("successRate", "成功率", rate(requestCount - failedCount, requestCount), "percent",
                        rateStatus, null),
                summary("average", "平均耗时", nullableDecimalValue(totals, "average_millis"),
                        "milliseconds", latencyStatus, null),
                summary("p95", "P95", p95, "milliseconds", latencyStatus, null),
                summary("p99", "P99", p99, "milliseconds", latencyStatus, null)
        ));
        response.setRequestTrend(loadApiRequestTrend(range, params));
        response.setLatencyTrend(loadDatabaseLatencyTrend(
                "transaction_merchant_api_interaction_log", apiMonitorWhere(),
                "request_time", "duration_millis", "id", params, range));
        response.setResponseCodeTop(loadCategoryTop("""
                SELECT COALESCE(NULLIF(TRIM(merchant_response_code), ''), 'UNKNOWN') AS category_key,
                       COUNT(*) AS metric_value
                FROM transaction_merchant_api_interaction_log
                WHERE %s AND %s
                GROUP BY category_key
                ORDER BY metric_value DESC, category_key
                LIMIT 10
                """.formatted(apiMonitorWhere(), API_FAILURE_CONDITION), params));
        response.setMerchantTop(loadCategoryTop("""
                SELECT COALESCE(NULLIF(TRIM(merchant_id), ''), 'UNKNOWN') AS category_key,
                       COUNT(*) AS metric_value
                FROM transaction_merchant_api_interaction_log
                WHERE %s
                GROUP BY category_key
                ORDER BY metric_value DESC, category_key
                LIMIT 10
                """.formatted(apiMonitorWhere()), params));

        List<ApiAggregateItem> items = sameQuarter(range)
                ? loadSingleQuarterApiItems(params)
                : loadCrossQuarterApiItems(params);
        PageResult<ApiAggregateItem> page = page(items, query.safePageNo(), query.safePageSize());
        if (!sameQuarter(range)) {
            enrichCrossQuarterApiItems(page.getRecords(), params);
        }
        response.setPage(page);
        return response;
    }

    /**
     * 构造 API 监控命名参数，所有用户筛选值均通过参数绑定进入 SQL。
     *
     * @param query API 筛选条件
     * @param range 已规范化时间范围
     * @return API 摘要、趋势和分页查询共用参数
     */
    private MapSqlParameterSource apiMonitorParams(ApiMonitorQuery query, NormalizedRange range) {
        return rangeParams(range)
                .addValue("apiOperation", trim(query.getApiOperation()))
                .addValue("apiPath", trim(query.getApiPath()))
                .addValue("merchantId", trim(query.getMerchantId()))
                .addValue("responseCode", trim(query.getResponseCode()))
                .addValue("result", trim(query.getResult()))
                .addValue("bucketMinutes", range.bucketMinutes());
    }

    /**
     * 返回 API 逻辑表固定筛选条件，始终包含交易日期分片键范围。
     *
     * @return 仅包含命名参数的 WHERE 条件
     */
    private String apiMonitorWhere() {
        return """
                transaction_date_time >= :beginTime AND transaction_date_time <= :endTime
                  AND (:apiOperation IS NULL OR api_operation = :apiOperation)
                  AND (:apiPath IS NULL OR request_path LIKE CONCAT('%', :apiPath, '%'))
                  AND (:merchantId IS NULL OR merchant_id = :merchantId)
                  AND (:responseCode IS NULL OR merchant_response_code = :responseCode)
                  AND (:result IS NULL OR request_result = :result)
                """.strip();
    }

    /**
     * 聚合 API 请求总量和业务失败量趋势，并补齐空时间桶。
     *
     * @param range 时间范围与桶宽度
     * @param params API 查询参数
     * @return total、failed 两条时间序列
     */
    private List<TimeBucket> loadApiRequestTrend(NormalizedRange range, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT %s AS bucket_time,
                       COUNT(*) AS total_count,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0) AS failed_count
                FROM transaction_merchant_api_interaction_log
                WHERE %s
                GROUP BY bucket_time
                ORDER BY bucket_time
                """.formatted(databaseBucketExpression("request_time"), API_FAILURE_CONDITION, apiMonitorWhere()), params);
        Map<LocalDateTime, Map<String, BigDecimal>> buckets = initializeBuckets(range, List.of("total", "failed"));
        for (Map<String, Object> row : rows) {
            Map<String, BigDecimal> values = buckets.get(dateTime(row, "bucket_time"));
            if (values != null) {
                values.put("total", decimal(longValue(row, "total_count")));
                values.put("failed", decimal(longValue(row, "failed_count")));
            }
        }
        return toTimeBuckets(buckets);
    }

    /**
     * 在单季度物理分片内计算每个 API 的精确计数、均值和最近秩分位数。
     *
     * @param params API 查询参数，必须包含交易日期范围
     * @return 全量 API 聚合结果，供内存分页
     */
    private List<ApiAggregateItem> loadSingleQuarterApiItems(MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT api_operation, request_path,
                       MAX(request_count) AS request_count,
                       MAX(failed_count) AS failed_count,
                       MAX(average_millis) AS average_millis,
                       MAX(CASE WHEN duration_rank = CEILING(duration_count * 0.95)
                                THEN duration_millis END) AS p95_millis,
                       MAX(CASE WHEN duration_rank = CEILING(duration_count * 0.99)
                                THEN duration_millis END) AS p99_millis,
                       MAX(latest_response_code) AS latest_response_code,
                       MAX(latest_failure_time) AS latest_failure_time
                FROM (
                    SELECT api_operation, request_path, duration_millis,
                           COUNT(*) OVER (PARTITION BY api_operation, request_path) AS request_count,
                           SUM(CASE WHEN %s THEN 1 ELSE 0 END)
                               OVER (PARTITION BY api_operation, request_path) AS failed_count,
                           AVG(duration_millis) OVER (PARTITION BY api_operation, request_path) AS average_millis,
                           COUNT(duration_millis) OVER (PARTITION BY api_operation, request_path) AS duration_count,
                           ROW_NUMBER() OVER (
                               PARTITION BY api_operation, request_path
                               ORDER BY CASE WHEN duration_millis IS NULL THEN 1 ELSE 0 END,
                                        duration_millis, id
                           ) AS duration_rank,
                           FIRST_VALUE(merchant_response_code) OVER (
                               PARTITION BY api_operation, request_path
                               ORDER BY request_time DESC, id DESC
                           ) AS latest_response_code,
                           MAX(CASE WHEN %s THEN request_time END)
                               OVER (PARTITION BY api_operation, request_path) AS latest_failure_time
                    FROM transaction_merchant_api_interaction_log
                    WHERE %s
                ) ranked
                GROUP BY api_operation, request_path
                ORDER BY request_count DESC, api_operation, request_path
                """.formatted(API_FAILURE_CONDITION, API_FAILURE_CONDITION, apiMonitorWhere()), params);
        return rows.stream().map(this::apiAggregateItem).toList();
    }

    /**
     * 跨季度聚合 API 基础指标，交由 ShardingSphere 合并各物理分片结果。
     *
     * <p>该阶段不计算可能受分片局部排序影响的分位数和最新响应码；分页完成后由
     * {@link #enrichCrossQuarterApiItems(List, MapSqlParameterSource)} 对当前页精确补齐。</p>
     *
     * @param params API 查询参数，必须包含交易日期范围
     * @return 不含跨分片分位数和最新响应码的 API 聚合结果
     */
    private List<ApiAggregateItem> loadCrossQuarterApiItems(MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT api_operation, request_path,
                       COUNT(*) AS request_count,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0) AS failed_count,
                       AVG(duration_millis) AS average_millis,
                       MAX(CASE WHEN %s THEN request_time END) AS latest_failure_time
                FROM transaction_merchant_api_interaction_log
                WHERE %s
                GROUP BY api_operation, request_path
                ORDER BY request_count DESC, api_operation, request_path
                """.formatted(API_FAILURE_CONDITION, API_FAILURE_CONDITION, apiMonitorWhere()), params);
        return rows.stream().map(this::apiAggregateItem).toList();
    }

    /**
     * 将 API 聚合查询行转换为页面模型，成功率使用百分数且保留两位小数。
     *
     * @param row API 聚合查询行
     * @return API 聚合项
     */
    private ApiAggregateItem apiAggregateItem(Map<String, Object> row) {
        ApiAggregateItem item = new ApiAggregateItem();
        item.setApiOperation(string(row, "api_operation"));
        item.setApiPath(string(row, "request_path"));
        item.setApiKey(Objects.toString(item.getApiOperation(), "UNKNOWN") + "|"
                + Objects.toString(item.getApiPath(), "UNKNOWN"));
        item.setRequestCount(longValue(row, "request_count"));
        item.setFailedCount(longValue(row, "failed_count"));
        item.setSuccessRate(rate(item.getRequestCount() - item.getFailedCount(), item.getRequestCount()));
        item.setAverageMillis(nullableDecimalValue(row, "average_millis"));
        item.setP95Millis(nullableDecimalValue(row, "p95_millis"));
        item.setP99Millis(nullableDecimalValue(row, "p99_millis"));
        item.setLatestResponseCode(string(row, "latest_response_code"));
        item.setLatestFailureTime(dateTime(row, "latest_failure_time"));
        return item;
    }

    /**
     * 为跨季度结果当前页补齐全局 P95、P99 和最后响应码。
     *
     * <p>只补当前页，避免对全部 API 分组产生 N+1 查询；每个补查仍携带原始时间范围和
     * 当前 API 维度，保证跨分片结果准确。</p>
     *
     * @param items 当前页 API 聚合项
     * @param params 原始 API 查询参数
     */
    private void enrichCrossQuarterApiItems(List<ApiAggregateItem> items, MapSqlParameterSource params) {
        for (ApiAggregateItem item : items) {
            MapSqlParameterSource itemParams = copyParams(params)
                    .addValue("itemApiOperation", item.getApiOperation())
                    .addValue("itemApiPath", item.getApiPath());
            String itemWhere = apiMonitorWhere() + """
                      AND ((:itemApiOperation IS NULL AND api_operation IS NULL) OR api_operation = :itemApiOperation)
                      AND ((:itemApiPath IS NULL AND request_path IS NULL) OR request_path = :itemApiPath)
                    """;
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(duration_millis)
                    FROM transaction_merchant_api_interaction_log
                    WHERE %s
                    """.formatted(itemWhere), itemParams, Long.class);
            long durationCount = count == null ? 0L : count;
            item.setP95Millis(nearestRankPercentile(
                    "transaction_merchant_api_interaction_log", itemWhere,
                    "duration_millis", "id", itemParams, durationCount, 0.95D));
            item.setP99Millis(nearestRankPercentile(
                    "transaction_merchant_api_interaction_log", itemWhere,
                    "duration_millis", "id", itemParams, durationCount, 0.99D));
            List<Map<String, Object>> latest = jdbcTemplate.queryForList("""
                    SELECT merchant_response_code
                    FROM transaction_merchant_api_interaction_log
                    WHERE %s
                    ORDER BY request_time DESC, id DESC
                    LIMIT 1
                    """.formatted(itemWhere), itemParams);
            item.setLatestResponseCode(latest.isEmpty() ? null : string(latest.get(0), "merchant_response_code"));
        }
    }

    /**
     * 聚合渠道请求事实，健康状态只采用已配置规则产生的活动告警事件。
     *
     * <p>业务请求成功率和延迟来自交易逻辑表，渠道健康等级来自告警规则结果，避免服务
     * 根据任意即时失败率自行发明阈值。</p>
     *
     * @param query 渠道筛选和分页条件
     * @param range 已规范化时间范围
     * @param alertStatuses 当前活动渠道告警等级
     * @return 渠道摘要、趋势、错误分布和分页结果
     */
    private ChannelMonitorResponse loadChannelMonitor(ChannelMonitorQuery query,
                                                      NormalizedRange range,
                                                      Map<String, String> alertStatuses) {
        MapSqlParameterSource params = channelMonitorParams(query, range);
        Map<String, Object> totals = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS request_count,
                       COALESCE(SUM(CASE WHEN COALESCE(r.platform_success, 0) = 1 THEN 1 ELSE 0 END), 0)
                           AS success_count,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0) AS timeout_count
                FROM transaction_channel_request r
                WHERE %s
                """.formatted(CHANNEL_TIMEOUT_CONDITION, channelMonitorWhere()), params);
        List<ChannelAggregateItem> items = sameQuarter(range)
                ? loadSingleQuarterChannelItems(params, alertStatuses)
                : loadCrossQuarterChannelItems(params, alertStatuses);
        PageResult<ChannelAggregateItem> page = page(items, query.safePageNo(), query.safePageSize());
        if (!sameQuarter(range)) {
            enrichCrossQuarterChannelItems(page.getRecords(), params);
        }

        long healthy = items.stream().filter(item -> "HEALTHY".equals(item.getStatus())).count();
        long warning = items.stream().filter(item -> "WARNING".equals(item.getStatus())).count();
        long error = items.stream().filter(item -> "ERROR".equals(item.getStatus())).count();
        long requestCount = longValue(totals, "request_count");
        long successCount = longValue(totals, "success_count");
        long timeoutCount = longValue(totals, "timeout_count");

        ChannelMonitorResponse response = new ChannelMonitorResponse();
        response.setSummaries(List.of(
                summary("channels", "渠道总数", decimal(items.size()), "count", "INFO", null),
                summary("healthyChannels", "正常渠道", decimal(healthy), "count", "HEALTHY", null),
                summary("warningChannels", "告警渠道", decimal(warning), "count", warning > 0 ? "WARNING" : "HEALTHY", null),
                summary("errorChannels", "异常渠道", decimal(error), "count", error > 0 ? "ERROR" : "HEALTHY", null),
                summary("timeouts", "超时", decimal(timeoutCount), "count", timeoutCount > 0 ? "WARNING" : "HEALTHY", null),
                summary("failed", "渠道失败", decimal(requestCount - successCount), "count",
                        requestCount > successCount ? "WARNING" : "HEALTHY", null)
        ));
        response.setSuccessRateTrend(loadChannelSuccessRateTrend(range, params));
        response.setLatencyTrend(loadDatabaseLatencyTrend(
                "transaction_channel_request r", channelMonitorWhere(),
                "r.request_start_time", "r.duration_millis", "r.id", params, range));
        response.setErrorTop(loadCategoryTop("""
                SELECT %s AS category_key,
                       COUNT(*) AS metric_value
                FROM transaction_channel_request r
                WHERE %s AND COALESCE(r.platform_success, 0) <> 1
                GROUP BY category_key
                ORDER BY metric_value DESC, category_key
                LIMIT 10
                """.formatted(CHANNEL_ERROR_CODE_EXPRESSION, channelMonitorWhere()), params));
        response.setPaymentMethodRate(loadChannelPaymentMethodRates(params));
        response.setPage(page);
        return response;
    }

    /**
     * 构造渠道监控命名参数，所有用户筛选值均通过参数绑定进入 SQL。
     *
     * @param query 渠道筛选条件
     * @param range 已规范化时间范围
     * @return 渠道查询共用参数
     */
    private MapSqlParameterSource channelMonitorParams(ChannelMonitorQuery query, NormalizedRange range) {
        return rangeParams(range)
                .addValue("channelCode", trim(query.getChannelCode()))
                .addValue("requestScene", trim(query.getRequestScene()))
                .addValue("currency", trim(query.getCurrency()))
                .addValue("status", trim(query.getStatus()))
                .addValue("errorCode", trim(query.getErrorCode()))
                .addValue("paymentMethod", trim(query.getPaymentMethod()))
                .addValue("bucketMinutes", range.bucketMinutes());
    }

    /**
     * 返回渠道逻辑表固定筛选条件。
     *
     * <p>支付方式通过同操作、同交易日期的订单存在性判断，确保 ShardingSphere 能使用
     * 交易日期路由关联逻辑表，同时把没有支付方式的记录归入 UNKNOWN。</p>
     *
     * @return 仅包含命名参数的 WHERE 条件
     */
    private String channelMonitorWhere() {
        return """
                r.transaction_date_time >= :beginTime AND r.transaction_date_time <= :endTime
                  AND r.deleted = 0
                  AND (:channelCode IS NULL OR r.channel_code = :channelCode)
                  AND (:requestScene IS NULL OR r.request_scene = :requestScene)
                  AND (:currency IS NULL OR r.request_currency = :currency)
                  AND (:status IS NULL OR r.request_status = :status)
                  AND (:errorCode IS NULL OR %s = :errorCode)
                  AND (
                      :paymentMethod IS NULL
                      OR EXISTS (
                          SELECT 1
                          FROM transaction_order payment_order
                          WHERE payment_order.operation_id = r.operation_id
                            AND payment_order.transaction_date_time = r.transaction_date_time
                            AND payment_order.deleted = 0
                            AND NULLIF(TRIM(payment_order.payment_method), '') = :paymentMethod
                      )
                      OR (
                          :paymentMethod = 'UNKNOWN'
                          AND NOT EXISTS (
                              SELECT 1
                              FROM transaction_order payment_order
                              WHERE payment_order.operation_id = r.operation_id
                                AND payment_order.transaction_date_time = r.transaction_date_time
                                AND payment_order.deleted = 0
                                AND NULLIF(TRIM(payment_order.payment_method), '') IS NOT NULL
                          )
                      )
                  )
                """.formatted(CHANNEL_ERROR_CODE_EXPRESSION).strip();
    }

    /**
     * 聚合渠道成功率趋势；无请求时间桶保持 null，避免把“无样本”误展示为 0%。
     *
     * @param range 时间范围与桶宽度
     * @param params 渠道查询参数
     * @return successRate 时间序列
     */
    private List<TimeBucket> loadChannelSuccessRateTrend(NormalizedRange range, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT %s AS bucket_time,
                       COUNT(*) AS request_count,
                       COALESCE(SUM(CASE WHEN COALESCE(r.platform_success, 0) = 1 THEN 1 ELSE 0 END), 0)
                           AS success_count
                FROM transaction_channel_request r
                WHERE %s
                GROUP BY bucket_time
                ORDER BY bucket_time
                """.formatted(databaseBucketExpression("r.request_start_time"), channelMonitorWhere()), params);
        Map<LocalDateTime, Map<String, BigDecimal>> buckets = initializeNullableBuckets(
                range, List.of("successRate"));
        for (Map<String, Object> row : rows) {
            Map<String, BigDecimal> values = buckets.get(dateTime(row, "bucket_time"));
            if (values != null) {
                values.put("successRate", rate(longValue(row, "success_count"), longValue(row, "request_count")));
            }
        }
        return toTimeBuckets(buckets);
    }

    /**
     * 在单季度物理分片内计算渠道精确分位数和连续失败次数。
     *
     * @param params 渠道查询参数，必须包含交易日期范围
     * @param alertStatuses 当前活动渠道告警等级
     * @return 全量渠道聚合结果
     */
    private List<ChannelAggregateItem> loadSingleQuarterChannelItems(MapSqlParameterSource params,
                                                                     Map<String, String> alertStatuses) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT channel_code,
                       MAX(request_count) AS request_count,
                       MAX(success_count) AS success_count,
                       MAX(timeout_count) AS timeout_count,
                       MAX(average_millis) AS average_millis,
                       MAX(CASE WHEN duration_rank = CEILING(duration_count * 0.95)
                                THEN duration_millis END) AS p95_millis,
                       MAX(CASE WHEN duration_rank = CEILING(duration_count * 0.99)
                                THEN duration_millis END) AS p99_millis,
                       COALESCE(MIN(CASE WHEN platform_success = 1 THEN recency_rank END) - 1,
                                MAX(request_count)) AS continuous_failure_count,
                       MAX(CASE WHEN platform_success <> 1 AND result_rank = 1
                                THEN latest_error END) AS latest_error,
                       MAX(CASE WHEN platform_success <> 1 AND result_rank = 1
                                THEN request_start_time END) AS latest_failure_time
                FROM (
                    SELECT r.channel_code, COALESCE(r.platform_success, 0) AS platform_success,
                           r.duration_millis, r.request_start_time,
                           COALESCE(NULLIF(TRIM(r.platform_fail_reason), ''),
                                    NULLIF(TRIM(r.platform_result_code), ''),
                                    NULLIF(TRIM(r.acquirer_code), '')) AS latest_error,
                           COUNT(*) OVER (PARTITION BY r.channel_code) AS request_count,
                           SUM(CASE WHEN COALESCE(r.platform_success, 0) = 1 THEN 1 ELSE 0 END)
                               OVER (PARTITION BY r.channel_code) AS success_count,
                           SUM(CASE WHEN %s THEN 1 ELSE 0 END)
                               OVER (PARTITION BY r.channel_code) AS timeout_count,
                           AVG(r.duration_millis) OVER (PARTITION BY r.channel_code) AS average_millis,
                           COUNT(r.duration_millis) OVER (PARTITION BY r.channel_code) AS duration_count,
                           ROW_NUMBER() OVER (
                               PARTITION BY r.channel_code
                               ORDER BY CASE WHEN r.duration_millis IS NULL THEN 1 ELSE 0 END,
                                        r.duration_millis, r.id
                           ) AS duration_rank,
                           ROW_NUMBER() OVER (
                               PARTITION BY r.channel_code
                               ORDER BY r.request_start_time DESC, r.id DESC
                           ) AS recency_rank,
                           ROW_NUMBER() OVER (
                               PARTITION BY r.channel_code, COALESCE(r.platform_success, 0)
                               ORDER BY r.request_start_time DESC, r.id DESC
                           ) AS result_rank
                    FROM transaction_channel_request r
                    WHERE %s
                ) ranked
                GROUP BY channel_code
                ORDER BY channel_code
                """.formatted(CHANNEL_TIMEOUT_CONDITION, channelMonitorWhere()), params);
        return rows.stream().map(row -> channelAggregateItem(row, alertStatuses)).toList();
    }

    /**
     * 跨季度聚合渠道基础指标，由 ShardingSphere 合并各物理分片结果。
     *
     * <p>连续失败、精确分位数和最后错误需要全局顺序，留待当前页补查。</p>
     *
     * @param params 渠道查询参数，必须包含交易日期范围
     * @param alertStatuses 当前活动渠道告警等级
     * @return 未补齐全局顺序指标的渠道聚合结果
     */
    private List<ChannelAggregateItem> loadCrossQuarterChannelItems(MapSqlParameterSource params,
                                                                    Map<String, String> alertStatuses) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.channel_code,
                       COUNT(*) AS request_count,
                       COALESCE(SUM(CASE WHEN COALESCE(r.platform_success, 0) = 1 THEN 1 ELSE 0 END), 0)
                           AS success_count,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0) AS timeout_count,
                       AVG(r.duration_millis) AS average_millis,
                       MAX(CASE WHEN COALESCE(r.platform_success, 0) <> 1
                                THEN r.request_start_time END) AS latest_failure_time
                FROM transaction_channel_request r
                WHERE %s
                GROUP BY r.channel_code
                ORDER BY r.channel_code
                """.formatted(CHANNEL_TIMEOUT_CONDITION, channelMonitorWhere()), params);
        return rows.stream().map(row -> channelAggregateItem(row, alertStatuses)).toList();
    }

    /**
     * 将渠道聚合查询行转换为页面模型，并应用规则产生的健康状态。
     *
     * @param row 渠道聚合查询行
     * @param alertStatuses 当前活动渠道告警等级
     * @return 渠道聚合项
     */
    private ChannelAggregateItem channelAggregateItem(Map<String, Object> row,
                                                      Map<String, String> alertStatuses) {
        ChannelAggregateItem item = new ChannelAggregateItem();
        item.setChannelCode(Objects.toString(string(row, "channel_code"), "UNKNOWN"));
        item.setRequestCount(longValue(row, "request_count"));
        item.setSuccessCount(longValue(row, "success_count"));
        item.setTimeoutCount(longValue(row, "timeout_count"));
        item.setSuccessRate(rate(item.getSuccessCount(), item.getRequestCount()));
        item.setAverageMillis(nullableDecimalValue(row, "average_millis"));
        item.setP95Millis(nullableDecimalValue(row, "p95_millis"));
        item.setP99Millis(nullableDecimalValue(row, "p99_millis"));
        item.setContinuousFailureCount(integer(row, "continuous_failure_count"));
        item.setLatestError(safeText(string(row, "latest_error"), 512));
        item.setLatestFailureTime(dateTime(row, "latest_failure_time"));
        item.setStatus(channelStatus(item.getChannelCode(), alertStatuses));
        return item;
    }

    /**
     * 为跨季度渠道结果当前页补齐全局分位数、最后错误和连续失败次数。
     *
     * @param items 当前页渠道聚合项
     * @param params 原始渠道查询参数
     */
    private void enrichCrossQuarterChannelItems(List<ChannelAggregateItem> items, MapSqlParameterSource params) {
        for (ChannelAggregateItem item : items) {
            MapSqlParameterSource itemParams = copyParams(params).addValue("itemChannelCode", item.getChannelCode());
            String itemWhere = channelMonitorWhere() + " AND r.channel_code = :itemChannelCode";
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(r.duration_millis)
                    FROM transaction_channel_request r
                    WHERE %s
                    """.formatted(itemWhere), itemParams, Long.class);
            long durationCount = count == null ? 0L : count;
            item.setP95Millis(nearestRankPercentile(
                    "transaction_channel_request r", itemWhere,
                    "r.duration_millis", "r.id", itemParams, durationCount, 0.95D));
            item.setP99Millis(nearestRankPercentile(
                    "transaction_channel_request r", itemWhere,
                    "r.duration_millis", "r.id", itemParams, durationCount, 0.99D));
            List<Map<String, Object>> latestFailure = jdbcTemplate.queryForList("""
                    SELECT COALESCE(NULLIF(TRIM(r.platform_fail_reason), ''),
                                    NULLIF(TRIM(r.platform_result_code), ''),
                                    NULLIF(TRIM(r.acquirer_code), '')) AS latest_error,
                           r.request_start_time AS latest_failure_time
                    FROM transaction_channel_request r
                    WHERE %s AND COALESCE(r.platform_success, 0) <> 1
                    ORDER BY r.request_start_time DESC, r.id DESC
                    LIMIT 1
                    """.formatted(itemWhere), itemParams);
            if (!latestFailure.isEmpty()) {
                item.setLatestError(safeText(string(latestFailure.get(0), "latest_error"), 512));
                item.setLatestFailureTime(dateTime(latestFailure.get(0), "latest_failure_time"));
            }
            item.setContinuousFailureCount(loadCrossQuarterContinuousFailures(item, itemWhere, itemParams));
        }
    }

    /**
     * 计算跨季度范围内某渠道最后一次成功之后的连续失败次数。
     *
     * <p>使用请求时间和主键形成稳定顺序；若查询范围内没有成功请求，则全部失败请求都
     * 计入连续失败。返回值限制在 Integer 上限内以匹配接口模型。</p>
     *
     * @param item 当前渠道聚合项
     * @param itemWhere 已追加渠道维度的固定筛选条件
     * @param itemParams 当前渠道查询参数
     * @return 连续失败次数
     */
    private int loadCrossQuarterContinuousFailures(ChannelAggregateItem item,
                                                   String itemWhere,
                                                   MapSqlParameterSource itemParams) {
        List<Map<String, Object>> latestSuccess = jdbcTemplate.queryForList("""
                SELECT r.request_start_time AS success_time, r.id AS success_id
                FROM transaction_channel_request r
                WHERE %s AND COALESCE(r.platform_success, 0) = 1
                ORDER BY r.request_start_time DESC, r.id DESC
                LIMIT 1
                """.formatted(itemWhere), itemParams);
        if (latestSuccess.isEmpty()) {
            return Math.toIntExact(Math.min(item.getRequestCount(), Integer.MAX_VALUE));
        }
        Map<String, Object> success = latestSuccess.get(0);
        MapSqlParameterSource countParams = copyParams(itemParams)
                .addValue("latestSuccessTime", dateTime(success, "success_time"))
                .addValue("latestSuccessId", longValue(success, "success_id"));
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM transaction_channel_request r
                WHERE %s
                  AND (r.request_start_time > :latestSuccessTime
                       OR (r.request_start_time = :latestSuccessTime AND r.id > :latestSuccessId))
                """.formatted(itemWhere), countParams, Long.class);
        return Math.toIntExact(Math.min(count == null ? 0L : count, Integer.MAX_VALUE));
    }

    /**
     * 按支付方式聚合渠道成功率，未记录支付方式的请求归入 UNKNOWN。
     *
     * @param params 渠道查询参数
     * @return 最多十个支付方式成功率
     */
    private List<CategoryMetric> loadChannelPaymentMethodRates(MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT COALESCE(NULLIF(TRIM(o.payment_method), ''), 'UNKNOWN') AS category_key,
                       COUNT(*) AS request_count,
                       COALESCE(SUM(CASE WHEN COALESCE(r.platform_success, 0) = 1 THEN 1 ELSE 0 END), 0)
                           AS success_count
                FROM transaction_channel_request r
                LEFT JOIN transaction_order o
                  ON o.operation_id = r.operation_id
                 AND o.transaction_date_time = r.transaction_date_time
                 AND o.deleted = 0
                WHERE %s
                GROUP BY category_key
                ORDER BY request_count DESC, category_key
                LIMIT 10
                """.formatted(channelMonitorWhere()), params);
        return rows.stream().map(row -> category(
                string(row, "category_key"), string(row, "category_key"),
                rate(longValue(row, "success_count"), longValue(row, "request_count")))).toList();
    }

    /**
     * 在数据库端使用同一筛选条件计算 Webhook 任务摘要、趋势和分页。
     *
     * <p>任务状态来自通知主表，HTTP 状态和最近耗时来自最后一次尝试；成功率趋势按所有
     * 尝试计算，重试趋势按 attempt_no 大于一识别，避免混淆“任务最终状态”和“单次尝试”。</p>
     *
     * @param query Webhook 筛选和分页条件
     * @param range 已规范化时间范围
     * @return Webhook 摘要、趋势、HTTP 状态分布和分页结果
     */
    private WebhookMonitorResponse loadWebhookMonitor(WebhookMonitorQuery query, NormalizedRange range) {
        MapSqlParameterSource params = webhookMonitorParams(query, range);
        Map<String, Object> totals = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS notification_count,
                       COALESCE(SUM(CASE WHEN UPPER(n.notify_status) = 'SUCCESS' THEN 1 ELSE 0 END), 0)
                           AS success_count,
                       COALESCE(SUM(CASE WHEN UPPER(n.notify_status) IN ('FAILED', 'CLOSED') THEN 1 ELSE 0 END), 0)
                           AS failed_count,
                       COALESCE(SUM(CASE WHEN UPPER(n.notify_status) IN ('INIT', 'PROCESSING') THEN 1 ELSE 0 END), 0)
                           AS retrying_count,
                       COALESCE(SUM(CASE WHEN UPPER(n.notify_status) = 'CLOSED' THEN 1 ELSE 0 END), 0)
                           AS final_failed_count
                FROM transaction_merchant_notification n
                LEFT JOIN transaction_merchant_notification_log latest
                  ON latest.notify_id = n.notify_id
                 AND latest.transaction_date_time = n.transaction_date_time
                 AND latest.attempt_no = n.last_attempt_no
                WHERE %s
                """.formatted(webhookNotificationWhere()), params);
        long notificationCount = longValue(totals, "notification_count");
        long successCount = longValue(totals, "success_count");
        long failedCount = longValue(totals, "failed_count");
        long retryingCount = longValue(totals, "retrying_count");
        long finalFailedCount = longValue(totals, "final_failed_count");
        long pageNo = query.safePageNo();
        long pageSize = query.safePageSize();
        long offset = Math.max((pageNo - 1L) * pageSize, 0L);
        List<WebhookItem> items = offset < notificationCount
                ? loadWebhookPage(params, offset, pageSize)
                : List.of();

        WebhookMonitorResponse response = new WebhookMonitorResponse();
        String successRateStatus = notificationCount == 0L
                ? "UNKNOWN" : failedCount == 0L ? "HEALTHY" : "WARNING";
        response.setSummaries(List.of(
                summary("sent", "发送任务", decimal(notificationCount), "count", "INFO", null),
                summary("successRate", "成功率", rate(successCount, notificationCount), "percent",
                        successRateStatus, null),
                summary("failed", "失败", decimal(failedCount), "count", failedCount > 0 ? "ERROR" : "HEALTHY", null),
                summary("retrying", "重试中", decimal(retryingCount), "count", retryingCount > 0 ? "WARNING" : "HEALTHY", null),
                summary("finalFailed", "最终失败", decimal(finalFailedCount), "count",
                        finalFailedCount > 0 ? "ERROR" : "HEALTHY", null)
        ));
        response.setSuccessRateTrend(loadWebhookSuccessRateTrend(range, params));
        response.setRetryTrend(loadWebhookRetryTrend(range, params));
        response.setHttpStatusDistribution(loadCategoryTop("""
                SELECT COALESCE(CAST(l.http_status AS CHAR), 'UNKNOWN') AS category_key,
                       COUNT(*) AS metric_value
                FROM transaction_merchant_notification_log l
                JOIN transaction_merchant_notification n
                  ON n.notify_id = l.notify_id
                 AND n.transaction_date_time = l.transaction_date_time
                 AND n.deleted = 0
                WHERE %s
                GROUP BY category_key
                ORDER BY metric_value DESC, category_key
                LIMIT 10
                """.formatted(webhookAttemptWhere()), params));
        response.setPage(PageResult.of(notificationCount, pageNo, pageSize, items));
        return response;
    }

    /**
     * 构造 Webhook 监控命名参数，主表和尝试表查询复用同一业务筛选。
     *
     * @param query Webhook 筛选条件
     * @param range 已规范化时间范围
     * @return Webhook 查询共用参数
     */
    private MapSqlParameterSource webhookMonitorParams(WebhookMonitorQuery query, NormalizedRange range) {
        return rangeParams(range)
                .addValue("merchantId", trim(query.getMerchantId()))
                .addValue("transactionId", trim(query.getTransactionId()))
                .addValue("eventType", trim(query.getEventType()))
                .addValue("notifyStatus", trim(query.getNotifyStatus()))
                .addValue("httpStatus", query.getHttpStatus())
                .addValue("bucketMinutes", range.bucketMinutes());
    }

    /**
     * 返回 Webhook 通知任务主表筛选条件。
     *
     * <p>HTTP 状态筛选作用于主表记录对应的最后一次尝试。</p>
     *
     * @return 仅包含命名参数的任务 WHERE 条件
     */
    private String webhookNotificationWhere() {
        return """
                n.transaction_date_time >= :beginTime AND n.transaction_date_time <= :endTime
                  AND n.deleted = 0
                  AND (:merchantId IS NULL OR n.merchant_id = :merchantId)
                  AND (:transactionId IS NULL OR n.transaction_id = :transactionId)
                  AND (:eventType IS NULL OR n.event_type = :eventType)
                  AND (:notifyStatus IS NULL OR n.notify_status = :notifyStatus)
                  AND (:httpStatus IS NULL OR latest.http_status = :httpStatus)
                """.strip();
    }

    /**
     * 返回 Webhook 尝试日志筛选条件。
     *
     * <p>主表和日志表同时限制交易日期分片键，避免跨逻辑表关联退化为全分片扫描。</p>
     *
     * @return 仅包含命名参数的尝试 WHERE 条件
     */
    private String webhookAttemptWhere() {
        return """
                l.transaction_date_time >= :beginTime AND l.transaction_date_time <= :endTime
                  AND n.transaction_date_time >= :beginTime AND n.transaction_date_time <= :endTime
                  AND (:merchantId IS NULL OR n.merchant_id = :merchantId)
                  AND (:transactionId IS NULL OR n.transaction_id = :transactionId)
                  AND (:eventType IS NULL OR n.event_type = :eventType)
                  AND (:notifyStatus IS NULL OR n.notify_status = :notifyStatus)
                  AND (:httpStatus IS NULL OR l.http_status = :httpStatus)
                """.strip();
    }

    /**
     * 分页读取 Webhook 任务和最后一次尝试摘要。
     *
     * @param params Webhook 查询参数
     * @param offset 从零开始的分页偏移
     * @param limit 最大返回条数
     * @return 按任务更新时间倒序排列的通知任务
     */
    private List<WebhookItem> loadWebhookPage(MapSqlParameterSource params, long offset, long limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT n.notify_id, n.transaction_id, n.transaction_date_time, n.merchant_id,
                       n.event_type, n.notify_status, n.last_attempt_no, n.next_retry_time,
                       n.fail_reason, latest.http_status, latest.duration_millis,
                       latest.notify_time, latest.error_message
                FROM transaction_merchant_notification n
                LEFT JOIN transaction_merchant_notification_log latest
                  ON latest.notify_id = n.notify_id
                 AND latest.transaction_date_time = n.transaction_date_time
                 AND latest.attempt_no = n.last_attempt_no
                WHERE %s
                ORDER BY n.update_time DESC, n.id DESC
                LIMIT :offset, :limit
                """.formatted(webhookNotificationWhere()), copyParams(params)
                .addValue("offset", offset)
                .addValue("limit", limit));
        List<WebhookItem> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            WebhookItem item = new WebhookItem();
            item.setEventId(string(row, "notify_id"));
            item.setMerchantId(string(row, "merchant_id"));
            item.setTransactionId(string(row, "transaction_id"));
            item.setTransactionDateTime(dateTime(row, "transaction_date_time"));
            item.setEventType(string(row, "event_type"));
            item.setAttemptCount(integer(row, "last_attempt_no"));
            item.setStatus(string(row, "notify_status"));
            item.setNextRetryTime(dateTime(row, "next_retry_time"));
            item.setLastError(safeText(firstText(row, "error_message", row, "fail_reason"), 512));
            item.setHttpStatus(nullableInteger(row, "http_status"));
            item.setLastDurationMillis(nullableInteger(row, "duration_millis"));
            item.setLastAttemptTime(dateTime(row, "notify_time"));
            items.add(item);
        }
        return items;
    }

    /**
     * 按尝试时间聚合 Webhook 投递成功率；无尝试时间桶保持 null。
     *
     * @param range 时间范围与桶宽度
     * @param params Webhook 查询参数
     * @return successRate 时间序列
     */
    private List<TimeBucket> loadWebhookSuccessRateTrend(NormalizedRange range,
                                                         MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT %s AS bucket_time,
                       COUNT(*) AS attempt_count,
                       COALESCE(SUM(CASE WHEN COALESCE(l.success, 0) = 1 THEN 1 ELSE 0 END), 0)
                           AS success_count
                FROM transaction_merchant_notification_log l
                JOIN transaction_merchant_notification n
                  ON n.notify_id = l.notify_id
                 AND n.transaction_date_time = l.transaction_date_time
                 AND n.deleted = 0
                WHERE %s
                GROUP BY bucket_time
                ORDER BY bucket_time
                """.formatted(databaseBucketExpression("l.notify_time"), webhookAttemptWhere()), params);
        Map<LocalDateTime, Map<String, BigDecimal>> buckets = initializeNullableBuckets(
                range, List.of("successRate"));
        for (Map<String, Object> row : rows) {
            Map<String, BigDecimal> values = buckets.get(dateTime(row, "bucket_time"));
            if (values != null) {
                values.put("successRate", rate(longValue(row, "success_count"), longValue(row, "attempt_count")));
            }
        }
        return toTimeBuckets(buckets);
    }

    /**
     * 按尝试时间聚合 Webhook 重试次数。
     *
     * <p>当前数据库没有可归属到该查询维度的 RocketMQ DLQ 事实，因此 dlq 明确返回 null，
     * 前端应展示为不可用而不是零。</p>
     *
     * @param range 时间范围与桶宽度
     * @param params Webhook 查询参数
     * @return retry 时间序列和不可用的 dlq 序列
     */
    private List<TimeBucket> loadWebhookRetryTrend(NormalizedRange range,
                                                   MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT %s AS bucket_time,
                       COALESCE(SUM(CASE WHEN l.attempt_no > 1 THEN 1 ELSE 0 END), 0) AS retry_count
                FROM transaction_merchant_notification_log l
                JOIN transaction_merchant_notification n
                  ON n.notify_id = l.notify_id
                 AND n.transaction_date_time = l.transaction_date_time
                 AND n.deleted = 0
                WHERE %s
                GROUP BY bucket_time
                ORDER BY bucket_time
                """.formatted(databaseBucketExpression("l.notify_time"), webhookAttemptWhere()), params);
        Map<LocalDateTime, Map<String, BigDecimal>> buckets = initializeBuckets(range, List.of("retry"));
        for (Map<String, Object> row : rows) {
            Map<String, BigDecimal> values = buckets.get(dateTime(row, "bucket_time"));
            if (values != null) {
                values.put("retry", decimal(longValue(row, "retry_count")));
            }
        }
        List<TimeBucket> result = toTimeBuckets(buckets);
        result.forEach(bucket -> {
            Map<String, BigDecimal> values = new LinkedHashMap<>(bucket.getValues());
            values.put("dlq", null);
            bucket.setValues(values);
        });
        return result;
    }

    /**
     * 在固定时间桶内计算平均耗时、P95 和 P99。
     *
     * <p>时间桶边界由查询范围和桶宽度控制且不会跨季度，因此每个窗口函数分区只落在
     * 单个物理分片内；列名和表表达式仅由服务内部常量传入，禁止接收用户输入。</p>
     *
     * @param fromSql 固定表或带别名表表达式
     * @param whereSql 固定筛选条件
     * @param timeColumn 时间列
     * @param durationColumn 耗时列，单位毫秒
     * @param rowIdColumn 稳定排序主键列
     * @param params 命名查询参数
     * @param range 时间范围与桶宽度
     * @return avg、p95、p99 时间序列；无样本时间桶为 null
     */
    private List<TimeBucket> loadDatabaseLatencyTrend(String fromSql,
                                                      String whereSql,
                                                      String timeColumn,
                                                      String durationColumn,
                                                      String rowIdColumn,
                                                      MapSqlParameterSource params,
                                                      NormalizedRange range) {
        String bucketExpression = databaseBucketExpression(timeColumn);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT bucket_time,
                       AVG(duration_millis) AS average_millis,
                       MAX(CASE WHEN duration_rank = CEILING(duration_count * 0.95)
                                THEN duration_millis END) AS p95_millis,
                       MAX(CASE WHEN duration_rank = CEILING(duration_count * 0.99)
                                THEN duration_millis END) AS p99_millis
                FROM (
                    SELECT bucket_time, duration_millis,
                           ROW_NUMBER() OVER (
                               PARTITION BY bucket_time
                               ORDER BY duration_millis, row_id
                           ) AS duration_rank,
                           COUNT(*) OVER (PARTITION BY bucket_time) AS duration_count
                    FROM (
                        SELECT %s AS row_id,
                               %s AS duration_millis,
                               %s AS bucket_time
                        FROM %s
                        WHERE %s AND %s IS NOT NULL
                    ) bucketed
                ) ranked
                GROUP BY bucket_time
                ORDER BY bucket_time
                """.formatted(rowIdColumn, durationColumn, bucketExpression, fromSql, whereSql, durationColumn), params);
        Map<LocalDateTime, Map<String, BigDecimal>> buckets = new LinkedHashMap<>();
        initializeBuckets(range, List.of("avg", "p95", "p99")).forEach((timestamp, ignored) -> {
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            values.put("avg", null);
            values.put("p95", null);
            values.put("p99", null);
            buckets.put(timestamp, values);
        });
        for (Map<String, Object> row : rows) {
            Map<String, BigDecimal> values = buckets.get(dateTime(row, "bucket_time"));
            if (values != null) {
                values.put("avg", nullableDecimalValue(row, "average_millis"));
                values.put("p95", nullableDecimalValue(row, "p95_millis"));
                values.put("p99", nullableDecimalValue(row, "p99_millis"));
            }
        }
        return toTimeBuckets(buckets);
    }

    /**
     * 生成与前端时间轴一致的 MySQL 分钟桶表达式。
     *
     * @param timeColumn 服务内部固定时间列名
     * @return 使用 bucketMinutes 命名参数的 SQL 表达式
     */
    private String databaseBucketExpression(String timeColumn) {
        return """
                TIMESTAMPADD(
                    MINUTE,
                    FLOOR(TIMESTAMPDIFF(MINUTE, '1970-01-01 00:00:00', %s) / :bucketMinutes)
                        * :bucketMinutes,
                    '1970-01-01 00:00:00'
                )
                """.formatted(timeColumn).strip();
    }

    /**
     * 执行服务内部固定 TopN SQL 并转换为分类指标。
     *
     * @param sql 必须返回 category_key 和 metric_value 的固定 SQL
     * @param params 命名查询参数
     * @return 分类指标列表
     */
    private List<CategoryMetric> loadCategoryTop(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.queryForList(sql, params).stream()
                .map(row -> category(
                        string(row, "category_key"),
                        string(row, "category_key"),
                        decimal(longValue(row, "metric_value"))))
                .toList();
    }

    /**
     * 使用最近秩定义计算跨分片精确分位数。
     *
     * <p>排序同时使用耗时和唯一主键保证重复耗时下结果稳定；偏移量由已统计的非空样本数
     * 计算。表名、列名和筛选 SQL 都必须来自服务内部固定值。</p>
     *
     * @param fromSql 固定表或表表达式
     * @param whereSql 固定筛选条件
     * @param durationColumn 耗时列，单位毫秒
     * @param rowIdColumn 稳定排序主键列
     * @param params 命名查询参数
     * @param count 非空耗时样本数
     * @param percentile 0 到 1 之间的目标分位数
     * @return 分位数值；没有样本时返回 null
     */
    private BigDecimal nearestRankPercentile(String fromSql,
                                             String whereSql,
                                             String durationColumn,
                                             String rowIdColumn,
                                             MapSqlParameterSource params,
                                             long count,
                                             double percentile) {
        if (count <= 0L) {
            return null;
        }
        long offset = Math.max((long) Math.ceil(percentile * count) - 1L, 0L);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT %s AS percentile_value
                FROM %s
                WHERE %s AND %s IS NOT NULL
                ORDER BY %s, %s
                LIMIT :percentileOffset, 1
                """.formatted(durationColumn, fromSql, whereSql, durationColumn, durationColumn, rowIdColumn),
                copyParams(params).addValue("percentileOffset", offset));
        return rows.isEmpty() ? null : nullableDecimalValue(rows.get(0), "percentile_value");
    }

    /**
     * 判断查询范围是否位于同一个季度物理分片。
     *
     * @param range 已规范化时间范围
     * @return 起止时间属于同一自然季度时返回 true
     */
    private boolean sameQuarter(NormalizedRange range) {
        return quarterKey(range.beginTime()).equals(quarterKey(range.endTime()));
    }

    private String quarterKey(LocalDateTime value) {
        return value.getYear() + "Q" + ((value.getMonthValue() - 1) / 3 + 1);
    }

    private MapSqlParameterSource copyParams(MapSqlParameterSource source) {
        return new MapSqlParameterSource(source.getValues());
    }

    private ApiMonitorResponse emptyApiResponse(ApiMonitorQuery query, NormalizedRange range) {
        ApiMonitorResponse response = new ApiMonitorResponse();
        response.setSummaries(List.of(
                summary("requests", "请求量", BigDecimal.ZERO, "count", "INFO", null),
                summary("successRate", "成功率", null, "percent", "UNKNOWN", null),
                summary("average", "平均耗时", null, "milliseconds", "UNKNOWN", null),
                summary("p95", "P95", null, "milliseconds", "UNKNOWN", null),
                summary("p99", "P99", null, "milliseconds", "UNKNOWN", null)
        ));
        response.setRequestTrend(toTimeBuckets(initializeBuckets(range, List.of("total", "failed"))));
        response.setLatencyTrend(emptyLatencyTrend(range));
        response.setResponseCodeTop(List.of());
        response.setMerchantTop(List.of());
        response.setPage(page(List.of(), query.safePageNo(), query.safePageSize()));
        response.setHttpMetricCapability(capability("API_HTTP_METRICS", STATUS_NOT_CONFIGURED,
                "当前业务日志仅来自 service-openapi，所选服务没有可用 API 指标"));
        return response;
    }

    /**
     * 读取每个渠道当前最高活动告警等级。
     *
     * <p>只使用规则引擎已生成且未恢复的告警，不根据当前页面查询结果临时推导健康阈值。</p>
     *
     * @return 渠道编码到 WARNING 或 ERROR 的映射
     */
    private Map<String, String> currentChannelAlertStatuses() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT channel_code,
                       MAX(CASE
                               WHEN UPPER(alert_level) IN ('L3_CIRCUIT_BREAK', 'CRITICAL') THEN 3
                               WHEN UPPER(alert_level) IN ('L2_DEGRADED', 'HIGH', 'ERROR') THEN 2
                               ELSE 1
                           END) AS severity_rank
                FROM channel_alert_event
                WHERE event_status <> 'RESOLVED' AND deleted = 0
                GROUP BY channel_code
                """, new MapSqlParameterSource());
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            int severity = integer(row, "severity_rank");
            result.put(string(row, "channel_code"), severity >= 2 ? "ERROR" : "WARNING");
        }
        return result;
    }

    private String channelStatus(String channelCode, Map<String, String> alertStatuses) {
        String alert = alertStatuses.get(channelCode);
        if ("ERROR".equals(alert)) {
            return "ERROR";
        }
        if ("WARNING".equals(alert)) {
            return "WARNING";
        }
        return "HEALTHY";
    }

    private List<TimeBucket> aggregatedCountTrend(List<Map<String, Object>> rows,
                                                  NormalizedRange range,
                                                  String valueKey) {
        Map<LocalDateTime, Map<String, BigDecimal>> values = initializeBuckets(range, List.of(valueKey));
        for (Map<String, Object> row : rows) {
            LocalDateTime bucketTime = dateTime(row, "bucket_time");
            Map<String, BigDecimal> bucket = values.get(bucketTime);
            if (bucket != null) {
                bucket.put(valueKey, decimal(longValue(row, "metric_value")));
            }
        }
        return toTimeBuckets(values);
    }

    private List<TimeBucket> emptyLatencyTrend(NormalizedRange range) {
        Map<LocalDateTime, Map<String, BigDecimal>> buckets = initializeBuckets(
                range, List.of("avg", "p95", "p99"));
        buckets.values().forEach(values -> values.replaceAll((key, value) -> null));
        return toTimeBuckets(buckets);
    }

    private CategoryMetric category(String key, String label, BigDecimal value) {
        CategoryMetric metric = new CategoryMetric();
        metric.setKey(key);
        metric.setLabel(label);
        metric.setValue(value);
        return metric;
    }

    private Map<LocalDateTime, Map<String, BigDecimal>> initializeBuckets(NormalizedRange range,
                                                                          List<String> valueKeys) {
        Map<LocalDateTime, Map<String, BigDecimal>> result = new LinkedHashMap<>();
        LocalDateTime cursor = bucketStart(range.beginTime(), range.bucketMinutes());
        LocalDateTime end = bucketStart(range.endTime(), range.bucketMinutes());
        while (!cursor.isAfter(end)) {
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            valueKeys.forEach(key -> values.put(key, BigDecimal.ZERO));
            result.put(cursor, values);
            cursor = cursor.plusMinutes(range.bucketMinutes());
        }
        return result;
    }

    private Map<LocalDateTime, Map<String, BigDecimal>> initializeNullableBuckets(
            NormalizedRange range,
            List<String> valueKeys) {
        Map<LocalDateTime, Map<String, BigDecimal>> result = initializeBuckets(range, valueKeys);
        result.values().forEach(values -> values.replaceAll((key, value) -> null));
        return result;
    }

    private LocalDateTime bucketStart(LocalDateTime value, int bucketMinutes) {
        if (value == null) return null;
        LocalDateTime day = value.toLocalDate().atStartOfDay();
        long minuteOfDay = ChronoUnit.MINUTES.between(day, value);
        return day.plusMinutes((minuteOfDay / bucketMinutes) * bucketMinutes);
    }

    private List<TimeBucket> toTimeBuckets(Map<LocalDateTime, Map<String, BigDecimal>> source) {
        List<TimeBucket> result = new ArrayList<>();
        source.forEach((timestamp, values) -> {
            TimeBucket bucket = new TimeBucket();
            bucket.setTimestamp(timestamp);
            bucket.setValues(values);
            result.add(bucket);
        });
        return result;
    }

    /**
     * 汇总 Redis、MySQL、Nacos、清分日历和 RocketMQ 的依赖状态。
     *
     * <p>单个依赖探测异常只降级对应状态，不中断系统监控总览；异常说明仅保留类型或
     * 业务建议，不输出连接串、账号或底层响应正文。</p>
     *
     * @param services 已读取的服务发现摘要
     * @return 固定顺序的基础设施依赖状态
     */
    private List<DependencyStatus> dependencyStatuses(ServiceInventoryResponse services) {
        List<DependencyStatus> statuses = new ArrayList<>();
        try {
            Map<String, Object> redis = cacheApplicationService.info();
            boolean connected = Boolean.TRUE.equals(redis.get("connected"));
            statuses.add(dependency("redis", "Redis", connected ? "HEALTHY" : "ERROR",
                    connected ? "Connected" : "Unavailable", Objects.toString(redis.get("message"), null)));
        } catch (RuntimeException exception) {
            statuses.add(dependency("redis", "Redis", "ERROR", "Unavailable", exception.getClass().getSimpleName()));
        }
        try {
            DataSourceMonitorResponse datasource = datasourceApplicationService.snapshot();
            DataSourceMonitorResponse.Overview overview = datasource.getOverview();
            int healthy = overview == null || overview.getHealthyDataSourceCount() == null
                    ? 0 : overview.getHealthyDataSourceCount();
            int total = overview == null || overview.getRegisteredDataSourceCount() == null
                    ? 0 : overview.getRegisteredDataSourceCount();
            statuses.add(dependency("mysql", "MySQL", total > 0 && healthy == total ? "HEALTHY" : "WARNING",
                    healthy + "/" + total, datasource.getWarnings().isEmpty() ? null : datasource.getWarnings().get(0)));
        } catch (RuntimeException exception) {
            statuses.add(dependency("mysql", "MySQL", "ERROR", "Unavailable", exception.getClass().getSimpleName()));
        }
        statuses.add(dependency("nacos", "Nacos", services.getUnhealthyInstanceCount() == 0 ? "HEALTHY" : "WARNING",
                services.getHealthyInstanceCount() + "/" + services.getInstanceCount(), "DiscoveryClient service inventory"));
        int currentYear = LocalDate.now(ZoneId.of(MonitorTimeRangeNormalizer.DATABASE_ZONE_ID)).getYear();
        statuses.add(settlementCalendarDependencyStatus(currentYear));
        statuses.add(rocketMqDependencyStatus());
        return statuses;
    }

    /**
     * 将 RocketMQ Admin 提供器快照映射为总览依赖状态。
     *
     * @return RocketMQ 集群摘要；未配置时为 UNKNOWN，探测失败时为 ERROR
     */
    DependencyStatus rocketMqDependencyStatus() {
        Snapshot snapshot = rocketMqAdminMonitorProvider.snapshot();
        String status = switch (snapshot.status()) {
            case RocketMqAdminMonitorProvider.STATUS_AVAILABLE -> "HEALTHY";
            case RocketMqAdminMonitorProvider.STATUS_NOT_CONFIGURED -> "UNKNOWN";
            default -> "ERROR";
        };
        return dependency("rocketmq", "RocketMQ", status, snapshot.summary(), snapshot.reason());
    }

    /**
     * 校验中国大陆清分日历是否覆盖指定完整自然年且已确认生效。
     *
     * <p>清分任务只能使用 ACTIVE 且日明细数量与自然年天数一致的日历；DRAFT、缺年或
     * 明细不完整均作为错误依赖展示，直接解释 SETTLEMENT_CALENDAR_UNAVAILABLE 的根因。</p>
     *
     * @param year 待校验自然年
     * @return 清分日历依赖状态
     */
    DependencyStatus settlementCalendarDependencyStatus(int year) {
        try {
            MapSqlParameterSource parameters = new MapSqlParameterSource("calendarYear", year);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT y.calendar_year,
                           y.year_status,
                           y.total_days,
                           COUNT(d.id) AS day_count
                    FROM settlement_calendar_year y
                    LEFT JOIN settlement_holiday_calendar d
                      ON d.calendar_year_id = y.id
                     AND d.deleted = 0
                    WHERE y.calendar_year = :calendarYear
                      AND y.region_code = 'CN_MAINLAND'
                      AND y.deleted = 0
                    GROUP BY y.calendar_year, y.year_status, y.total_days
                    """, parameters);
            if (rows.isEmpty()) {
                return dependency("settlementCalendar", "Settlement Calendar", "ERROR", "Not initialized",
                        "Initialize and confirm the " + year + " settlement calendar before clearing T+N transactions");
            }
            Map<String, Object> row = rows.get(0);
            String yearStatus = upper(string(row, "year_status"));
            int expectedDays = Year.of(year).length();
            int configuredDays = integer(row, "total_days");
            Long dayCountValue = longValue(row, "day_count");
            long dayCount = dayCountValue == null ? 0L : dayCountValue;
            String value = Objects.toString(yearStatus, "UNKNOWN") + " · " + dayCount + "/" + expectedDays;
            if (!"ACTIVE".equals(yearStatus)) {
                return dependency("settlementCalendar", "Settlement Calendar", "ERROR", value,
                        "Confirm the " + year + " settlement calendar; DRAFT calendars are excluded from clearing");
            }
            if (configuredDays != expectedDays || dayCount != expectedDays) {
                return dependency("settlementCalendar", "Settlement Calendar", "ERROR", value,
                        "The " + year + " settlement calendar is incomplete; expected " + expectedDays + " days");
            }
            return dependency("settlementCalendar", "Settlement Calendar", "HEALTHY", value,
                    year + " calendar is active and complete");
        } catch (RuntimeException exception) {
            return dependency("settlementCalendar", "Settlement Calendar", "ERROR", "Unavailable",
                    "Settlement calendar status query failed: " + exception.getClass().getSimpleName());
        }
    }

    private DependencyStatus dependency(String key, String label, String status, String value, String description) {
        DependencyStatus item = new DependencyStatus();
        item.setKey(key);
        item.setLabel(label);
        item.setStatus(status);
        item.setValue(value);
        item.setDescription(safeText(description, 256));
        return item;
    }

    private MetricSummary findSummary(List<MetricSummary> summaries, String key, String fallbackLabel) {
        return summaries.stream().filter(item -> key.equals(item.getKey())).findFirst()
                .orElseGet(() -> summary(key, fallbackLabel, null, "count", "UNKNOWN", null));
    }

    private MetricSummary summary(String key,
                                  String label,
                                  BigDecimal value,
                                  String unit,
                                  String status,
                                  String description) {
        MetricSummary summary = new MetricSummary();
        summary.setKey(key);
        summary.setLabel(label);
        summary.setValue(value);
        summary.setUnit(unit);
        summary.setStatus(status);
        summary.setDescription(description);
        return summary;
    }

    private ProviderCapability capability(String provider, String status, String reason) {
        ProviderCapability capability = new ProviderCapability();
        capability.setProvider(provider);
        capability.setStatus(status);
        capability.setReason(reason);
        return capability;
    }

    private <T extends TimeRangeQuery> T copyRange(TimeRangeQuery source, T target) {
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        target.setQueryTimeZone(source.getQueryTimeZone());
        target.setPageNo(source.getPageNo());
        target.setPageSize(source.getPageSize());
        return target;
    }

    private MapSqlParameterSource rangeParams(NormalizedRange range) {
        return new MapSqlParameterSource()
                .addValue("beginTime", range.beginTime())
                .addValue("endTime", range.endTime());
    }

    private MapSqlParameterSource baseParams(NormalizedRange range) {
        return rangeParams(range).addValue("limit", MAX_RESULT_ROWS);
    }

    private <T> PageResult<T> page(List<T> source, long pageNo, long pageSize) {
        long offset = Math.max((pageNo - 1L) * pageSize, 0L);
        int from = (int) Math.min(offset, source.size());
        int to = (int) Math.min(offset + pageSize, source.size());
        return PageResult.of(source.size(), pageNo, pageSize, source.subList(from, to));
    }

    /**
     * 创建统一链路事件，并对错误信息和业务备注执行集中脱敏与长度限制。
     *
     * @param eventId 事件标识
     * @param transactionId 平台交易号
     * @param traceId 分布式链路标识
     * @param serviceName 事件所属服务
     * @param eventType 事件类型
     * @param eventName 事件名称
     * @param result 处理结果
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param durationMillis 耗时，单位毫秒，允许为空
     * @param errorCode 错误码，允许为空
     * @param errorMessage 错误摘要，允许为空但可能含敏感文本
     * @param businessRemark 业务备注，允许为空但可能含敏感文本
     * @return 可安全返回管理端的链路事件
     */
    private TraceEvent traceEvent(String eventId,
                                  String transactionId,
                                  String traceId,
                                  String serviceName,
                                  String eventType,
                                  String eventName,
                                  String result,
                                  LocalDateTime startTime,
                                  LocalDateTime endTime,
                                  Integer durationMillis,
                                  String errorCode,
                                  String errorMessage,
                                  String businessRemark) {
        TraceEvent event = new TraceEvent();
        event.setEventId(eventId);
        event.setTransactionId(transactionId);
        event.setTraceId(traceId);
        event.setServiceName(serviceName);
        event.setEventType(eventType);
        event.setEventName(eventName);
        event.setResult(result);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setDurationMillis(durationMillis);
        event.setErrorCode(errorCode);
        event.setErrorMessage(safeText(errorMessage, 512));
        event.setBusinessRemark(safeText(businessRemark, 512));
        return event;
    }

    /**
     * 创建统一结构化日志项，并对消息正文执行集中脱敏与长度限制。
     *
     * @param id 日志事实标识
     * @param timestamp 事件时间
     * @param level 日志级别
     * @param serviceName 服务名称
     * @param eventType 结构化事件类型
     * @param message 可能包含外部错误文本的日志摘要
     * @param traceId 分布式链路标识
     * @param transactionId 平台交易号
     * @param merchantId 商户标识
     * @param referenceId 订单、请求或通知关联标识
     * @return 可安全返回管理端的结构化日志
     */
    private StructuredLogItem logItem(String id,
                                      LocalDateTime timestamp,
                                      String level,
                                      String serviceName,
                                      String eventType,
                                      String message,
                                      String traceId,
                                      String transactionId,
                                      String merchantId,
                                      String referenceId) {
        StructuredLogItem item = new StructuredLogItem();
        item.setId(id);
        item.setTimestamp(timestamp);
        item.setLevel(level);
        item.setServiceName(serviceName);
        item.setEventType(eventType);
        item.setMessage(safeText(message, 512));
        item.setTraceId(traceId);
        item.setTransactionId(transactionId);
        item.setMerchantId(merchantId);
        item.setReferenceId(referenceId);
        return item;
    }

    private String searchableText(StructuredLogItem item) {
        return lower(String.join(" ",
                Objects.toString(item.getServiceName(), ""), Objects.toString(item.getEventType(), ""),
                Objects.toString(item.getMessage(), ""), Objects.toString(item.getTraceId(), ""),
                Objects.toString(item.getTransactionId(), ""), Objects.toString(item.getMerchantId(), ""),
                Objects.toString(item.getReferenceId(), "")));
    }

    /**
     * 清理服务发现元数据，移除可能携带地址、账号、密钥或令牌的字段。
     *
     * <p>键名按敏感词拒绝，保留项的键和值仍执行限长和通用脱敏。</p>
     *
     * @param metadata 注册中心实例元数据；允许为空
     * @return 可展示的元数据副本
     */
    private Map<String, String> sanitizeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String lowerKey = lower(key);
            if (lowerKey != null && !Set.of("password", "secret", "token", "credential", "authorization",
                    "username", "url", "key").stream().anyMatch(lowerKey::contains)) {
                result.put(safeText(key, 64), safeText(value, 256));
            }
        });
        return result;
    }

    /**
     * 清理告警来源指标中的字符串值，数值和时间类型保持原始精度。
     *
     * @param source 来源指标查询行
     * @return 可安全返回前端的有序副本
     */
    private Map<String, Object> sanitizeMetricMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (value instanceof String text) result.put(key, safeText(text, 256));
            else result.put(key, value);
        });
        return result;
    }

    /**
     * 解析当前告警操作人；无内部认证上下文的系统调用使用 system 身份。
     *
     * <p>只保存账号标识和展示名称，不保存 Token、权限或其他认证上下文。</p>
     *
     * @return 审计历史使用的操作人
     */
    private Operator currentOperator() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) return new Operator("system", "system");
        String accountId = account.getAccountId() == null ? account.getLoginAccount() : String.valueOf(account.getAccountId());
        String name = StringUtils.hasText(account.getRealName()) ? account.getRealName() : account.getLoginAccount();
        return new Operator(Objects.toString(accountId, "system"), Objects.toString(name, "system"));
    }

    private String serviceForStage(String stage) {
        String normalized = upper(stage);
        if (normalized == null) {
            return "service-payment";
        }
        return switch (normalized) {
            case "API", "VALIDATION", "MERCHANT", "CONFIG" -> "service-openapi";
            case "RISK" -> "service-gateway";
            case "ROUTE", "CHANNEL", "CALLBACK" -> "service-payment";
            case "MQ" -> "component-mq";
            default -> "service-payment";
        };
    }

    private String interactionResult(Map<String, Object> row) {
        if ("EXCEPTION".equals(upper(string(row, "interaction_type")))) return "FAILED";
        Integer status = nullableInteger(row, "http_status");
        return status != null && status >= 400 ? "FAILED" : "SUCCESS";
    }

    private boolean isFailedApi(Map<String, Object> row) {
        return isFailure(string(row, "request_result")) || isFailure(string(row, "response_result"));
    }

    private boolean isFailure(String value) {
        String normalized = upper(value);
        return normalized != null && (Set.of("FAILED", "FAIL", "ERROR", "REJECTED", "TIMEOUT", "CLOSED")
                .contains(normalized) || normalized.startsWith("F") || normalized.startsWith("Z"));
    }

    private String sourceAlertStatus(String value) {
        String normalized = upper(value);
        if (normalized == null) {
            return ALERT_OPEN;
        }
        return switch (normalized) {
            case "ACKNOWLEDGED" -> ALERT_PROCESSING;
            case "RESOLVED" -> ALERT_RECOVERED;
            default -> ALERT_OPEN;
        };
    }

    /**
     * 合并来源状态和人工覆盖状态，并保护来源终态不被覆盖。
     *
     * @param sourceStatus 渠道或安全来源表状态
     * @param overlayStatus 管理端人工处理状态，允许为空
     * @return 当前有效统一状态
     */
    private String effectiveAlertStatus(String sourceStatus, String overlayStatus) {
        String normalizedSource = upper(sourceStatus);
        if (ALERT_RECOVERED.equals(normalizedSource) || ALERT_CLOSED.equals(normalizedSource)) {
            return normalizedSource;
        }
        String normalizedOverlay = upper(overlayStatus);
        return normalizedOverlay == null ? normalizedSource : normalizedOverlay;
    }

    private String normalizeAlertLevel(String value) {
        String normalized = upper(value);
        if (normalized == null) {
            return "WARNING";
        }
        return switch (normalized) {
            case "CRITICAL", "L3_CIRCUIT_BREAK" -> "CRITICAL";
            case "HIGH", "ERROR", "L2_DEGRADED" -> "ERROR";
            default -> "WARNING";
        };
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) return null;
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(long value) {
        return BigDecimal.valueOf(value);
    }

    private BigDecimal nullableDecimalValue(Map<String, Object> row, String field) {
        if (row == null) return null;
        Object value = row.get(field);
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        String text = trim(Objects.toString(value, null));
        if (text == null) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String firstText(Map<String, Object> first, String firstKey,
                             Map<String, Object> second, String secondKey) {
        String value = first == null ? null : trim(string(first, firstKey));
        return value == null && second != null ? trim(string(second, secondKey)) : value;
    }

    private String string(Map<String, Object> row, String field) {
        if (row == null) return null;
        Object value = row.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private int integer(Map<String, Object> row, String field) {
        Integer value = nullableInteger(row, field);
        return value == null ? 0 : value;
    }

    private Integer nullableInteger(Map<String, Object> row, String field) {
        if (row == null) return null;
        Object value = row.get(field);
        if (value instanceof Number number) return number.intValue();
        String text = trim(Objects.toString(value, null));
        if (text == null) return null;
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long longValue(Map<String, Object> row, String field) {
        if (row == null) return null;
        Object value = row.get(field);
        if (value instanceof Number number) return number.longValue();
        String text = trim(Objects.toString(value, null));
        if (text == null) return null;
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean bool(Map<String, Object> row, String field) {
        if (row == null) return false;
        Object value = row.get(field);
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String text = upper(Objects.toString(value, null));
        return text != null && Set.of("TRUE", "Y", "YES", "1", "SUCCESS").contains(text);
    }

    private LocalDateTime dateTime(Map<String, Object> row, String field) {
        if (row == null) return null;
        Object value = row.get(field);
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof CharSequence text) {
            String normalized = trim(text.toString());
            if (normalized == null) return null;
            try {
                return Timestamp.valueOf(normalized.replace('T', ' ')).toLocalDateTime();
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String lower(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String lowerOrUnknown(String value) {
        String normalized = lower(value);
        return normalized == null ? "unknown" : normalized;
    }

    private String safeText(String value, int maxLength) {
        return sensitiveTextSanitizer.sanitize(value, maxLength);
    }

    private String alertKey(String sourceType, String sourceId) {
        return sourceType + "|" + sourceId;
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    private record Operator(String accountId, String name) {
    }
}
