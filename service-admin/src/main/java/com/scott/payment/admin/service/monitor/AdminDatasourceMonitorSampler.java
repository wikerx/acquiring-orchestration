package com.scott.payment.admin.service.monitor;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.scott.payment.admin.config.MonitorDynamicDataSourceProperties;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.CategoryMetric;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.DataSourceMetricsResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ProviderCapability;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TimeBucket;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDatasourceMonitorSampler
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 数据源指标采样器，按分钟聚合物理 Hikari 连接池和 MySQL performance_schema 指标，并在当前 Admin 进程内保留七天有界历史。
 * @status : create
 */
@Slf4j
@Component
public class AdminDatasourceMonitorSampler {

    /** 七天、每分钟一条采样对应的最大历史容量。 */
    static final int MAX_SAMPLES = 7 * 24 * 60;

    /** 页面刷新和定时任务共享的一分钟最小采样间隔。 */
    private static final long MIN_SAMPLE_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    /** MySQL statement digest 平均耗时查询，结果单位转换为毫秒。 */
    private static final String SQL_AVERAGE_LATENCY = """
            SELECT SUM(SUM_TIMER_WAIT) / NULLIF(SUM(COUNT_STAR), 0) / 1000000000 AS avg_millis
            FROM performance_schema.events_statements_summary_by_digest
            WHERE SCHEMA_NAME = DATABASE()
              AND DIGEST_TEXT IS NOT NULL
            """;

    /** MySQL 全局语句直方图 P95 和 P99 查询，结果单位转换为毫秒。 */
    private static final String SQL_PERCENTILE_LATENCY = """
            SELECT
              MIN(CASE WHEN BUCKET_QUANTILE >= 0.95 THEN BUCKET_TIMER_HIGH END) / 1000000000 AS p95_millis,
              MIN(CASE WHEN BUCKET_QUANTILE >= 0.99 THEN BUCKET_TIMER_HIGH END) / 1000000000 AS p99_millis
            FROM performance_schema.events_statements_histogram_global
            """;

    /** MySQL 平均耗时最高的十个 SQL 指纹查询，不读取绑定参数。 */
    private static final String SQL_SLOW_TOP = """
            SELECT DIGEST, DIGEST_TEXT, AVG_TIMER_WAIT / 1000000000 AS avg_millis
            FROM performance_schema.events_statements_summary_by_digest
            WHERE SCHEMA_NAME = DATABASE()
              AND DIGEST_TEXT IS NOT NULL
              AND COUNT_STAR > 0
            ORDER BY AVG_TIMER_WAIT DESC
            LIMIT 10
            """;

    /** 可选的动态数据源运行时入口；未装配时为空。 */
    private final DynamicRoutingDataSource dynamicRoutingDataSource;
    /** 动态数据源主库名称等静态配置。 */
    private final MonitorDynamicDataSourceProperties dataSourceProperties;
    /** 标准 JDBC Wrapper 连接池解析器。 */
    private final DataSourcePoolInspector poolInspector;
    /** 慢 SQL 指纹脱敏和长度限制组件。 */
    private final MonitorSensitiveTextSanitizer textSanitizer;
    /** Hikari 连接池进程内历史，元素按采样时间升序保存。 */
    private final Deque<TimeBucket> poolSamples = new ArrayDeque<>(MAX_SAMPLES);
    /** MySQL 延迟进程内历史，元素按采样时间升序保存。 */
    private final Deque<TimeBucket> latencySamples = new ArrayDeque<>(MAX_SAMPLES);

    /** 最近一次慢 SQL 指纹 TopN；未采样时为空集合。 */
    private List<CategoryMetric> slowSqlTop = List.of();
    /** Hikari 连接池采样能力状态，不包含连接地址或凭据。 */
    private ProviderCapability poolCapability = capability(
            "HIKARI_POOL_METRICS", "UNAVAILABLE", "No physical Hikari pool sample is available yet");
    /** MySQL performance_schema 能力状态，不包含 SQL 参数。 */
    private ProviderCapability sqlCapability = capability(
            "MYSQL_PERFORMANCE_SCHEMA", "UNAVAILABLE", "No performance_schema sample is available yet");
    /** 最近一次采样尝试的单调时钟值，单位纳秒。 */
    private long lastSampleAttemptNanos = Long.MIN_VALUE;

    /**
     * 创建数据源指标采样器。
     *
     * @param dynamicRoutingDataSourceProvider 动态数据源提供器；未装配时允许为空
     * @param dataSourceProperties 动态数据源静态配置
     * @param poolInspector 标准 JDBC Wrapper 连接池解析器
     * @param textSanitizer 监控文本脱敏器
     */
    public AdminDatasourceMonitorSampler(
            ObjectProvider<DynamicRoutingDataSource> dynamicRoutingDataSourceProvider,
            MonitorDynamicDataSourceProperties dataSourceProperties,
            DataSourcePoolInspector poolInspector,
            MonitorSensitiveTextSanitizer textSanitizer) {
        this.dynamicRoutingDataSource = dynamicRoutingDataSourceProvider.getIfAvailable();
        this.dataSourceProperties = dataSourceProperties;
        this.poolInspector = poolInspector;
        this.textSanitizer = textSanitizer;
    }

    /** 在 Spring Bean 初始化完成后尝试采集首个数据点。 */
    @PostConstruct
    public void initialize() {
        sample();
    }

    /** 每分钟采集一次连接池和 SQL 指标；一分钟内的重复调用直接复用现有历史。 */
    @Scheduled(fixedRate = 60_000L)
    public synchronized void sample() {
        long nowNanos = System.nanoTime();
        if (lastSampleAttemptNanos != Long.MIN_VALUE
                && nowNanos - lastSampleAttemptNanos < MIN_SAMPLE_INTERVAL_NANOS) {
            return;
        }
        lastSampleAttemptNanos = nowNanos;
        LocalDateTime timestamp = LocalDateTime.now();
        Map<String, DataSource> dataSources = runtimeDataSources();
        capturePoolMetrics(timestamp, dataSources);
        captureSqlMetrics(timestamp, dataSources);
    }

    /**
     * 返回数据源趋势、慢 SQL 指纹和能力状态。
     *
     * @return 当前 Admin 进程内的数据源监控指标
     */
    public synchronized DataSourceMetricsResponse metrics() {
        sample();
        DataSourceMetricsResponse response = new DataSourceMetricsResponse();
        response.setPoolTrend(new ArrayList<>(poolSamples));
        response.setLatencyTrend(new ArrayList<>(latencySamples));
        response.setSlowSqlTop(new ArrayList<>(slowSqlTop));
        response.setPoolMetricsCapability(poolCapability);
        response.setSqlMetricsCapability(sqlCapability);
        response.setGeneratedAt(LocalDateTime.now());
        return response;
    }

    /**
     * 读取当前 Admin JVM 已注册的物理数据源；动态数据源未装配时返回空集合。
     *
     * @return 数据源键到运行时 DataSource 的只读快照
     */
    private Map<String, DataSource> runtimeDataSources() {
        return dynamicRoutingDataSource == null ? Map.of() : dynamicRoutingDataSource.getDataSources();
    }

    /**
     * 聚合全部已初始化 Hikari 连接池的活跃、空闲和等待连接数，并追加一个有界历史点。
     *
     * @param timestamp 本次采样时间
     * @param dataSources 当前 JVM 注册的数据源集合
     */
    private void capturePoolMetrics(LocalDateTime timestamp, Map<String, DataSource> dataSources) {
        long active = 0L;
        long idle = 0L;
        long pending = 0L;
        int sampledPools = 0;
        for (DataSource dataSource : dataSources.values()) {
            HikariDataSource hikariDataSource = poolInspector.unwrapHikari(dataSource);
            if (hikariDataSource == null) {
                continue;
            }
            HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
            if (pool == null) {
                continue;
            }
            active += Math.max(pool.getActiveConnections(), 0);
            idle += Math.max(pool.getIdleConnections(), 0);
            pending += Math.max(pool.getThreadsAwaitingConnection(), 0);
            sampledPools++;
        }
        if (sampledPools == 0) {
            poolCapability = capability(
                    "HIKARI_POOL_METRICS", "UNAVAILABLE", "No initialized physical Hikari pool was found");
            return;
        }
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("active", BigDecimal.valueOf(active));
        values.put("idle", BigDecimal.valueOf(idle));
        values.put("pending", BigDecimal.valueOf(pending));
        addPoolSample(bucket(timestamp, values));
        poolCapability = capability(
                "HIKARI_POOL_METRICS", "AVAILABLE", "Aggregated from " + sampledPools + " physical Hikari pools");
    }

    /**
     * 从主数据源读取 MySQL performance_schema 延迟与慢 SQL 指纹；失败只更新能力状态，不中断调度线程。
     *
     * @param timestamp 本次采样时间
     * @param dataSources 当前 JVM 注册的数据源集合
     */
    private void captureSqlMetrics(LocalDateTime timestamp, Map<String, DataSource> dataSources) {
        DataSource dataSource = dataSources.get(dataSourceProperties.getPrimary());
        if (dataSource == null) {
            sqlCapability = capability(
                    "MYSQL_PERFORMANCE_SCHEMA", "UNAVAILABLE", "Primary datasource is not registered");
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            SqlMetrics metrics = readSqlMetrics(connection);
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            values.put("avg", metrics.averageMillis());
            values.put("p95", metrics.p95Millis());
            values.put("p99", metrics.p99Millis());
            addLatencySample(bucket(timestamp, values));
            slowSqlTop = metrics.slowSqlTop();
            sqlCapability = capability(
                    "MYSQL_PERFORMANCE_SCHEMA", "AVAILABLE", "MySQL statement digest and histogram metrics are available");
        } catch (SQLException | RuntimeException exception) {
            sqlCapability = capability(
                    "MYSQL_PERFORMANCE_SCHEMA", "UNAVAILABLE",
                    "performance_schema metrics unavailable: " + exception.getClass().getSimpleName());
            log.warn("event: ADMIN_DATASOURCE_SQL_METRICS_FAILED exceptionType: {}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 使用同一数据库连接读取平均延迟、直方图分位数和慢 SQL TopN，不读取绑定参数或结果集正文。
     *
     * @param connection 主数据源只读采样连接
     * @return 单次 SQL 性能指标快照，延迟单位为毫秒
     * @throws SQLException performance_schema 不可用、权限不足或查询失败时抛出
     */
    private SqlMetrics readSqlMetrics(Connection connection) throws SQLException {
        BigDecimal averageMillis;
        try (PreparedStatement statement = connection.prepareStatement(SQL_AVERAGE_LATENCY);
             ResultSet resultSet = statement.executeQuery()) {
            averageMillis = resultSet.next() ? resultSet.getBigDecimal("avg_millis") : null;
        }

        BigDecimal p95Millis;
        BigDecimal p99Millis;
        try (PreparedStatement statement = connection.prepareStatement(SQL_PERCENTILE_LATENCY);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                p95Millis = resultSet.getBigDecimal("p95_millis");
                p99Millis = resultSet.getBigDecimal("p99_millis");
            } else {
                p95Millis = null;
                p99Millis = null;
            }
        }

        List<CategoryMetric> slowSql = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_SLOW_TOP);
             ResultSet resultSet = statement.executeQuery()) {
            int index = 0;
            while (resultSet.next()) {
                CategoryMetric metric = new CategoryMetric();
                String digest = resultSet.getString("DIGEST");
                metric.setKey(digest == null ? "sql-" + index : digest);
                metric.setLabel(textSanitizer.sanitize(resultSet.getString("DIGEST_TEXT"), 160));
                metric.setValue(resultSet.getBigDecimal("avg_millis"));
                slowSql.add(metric);
                index++;
            }
        }
        return new SqlMetrics(averageMillis, p95Millis, p99Millis, slowSql);
    }

    /**
     * 构造统一图表时间桶。
     *
     * @param timestamp 桶时间
     * @param values 指标键值，单位由指标定义声明
     * @return 可直接写入历史队列的时间桶
     */
    private TimeBucket bucket(LocalDateTime timestamp, Map<String, BigDecimal> values) {
        TimeBucket bucket = new TimeBucket();
        bucket.setTimestamp(timestamp);
        bucket.setValues(values);
        return bucket;
    }

    /**
     * 向历史队列追加样本并淘汰超过七天容量的最旧数据。
     *
     * @param samples 目标历史队列
     * @param sample 新采样点
     */
    private void addBounded(Deque<TimeBucket> samples, TimeBucket sample) {
        samples.addLast(sample);
        while (samples.size() > MAX_SAMPLES) {
            samples.removeFirst();
        }
    }

    /** 测试支撑入口：追加一个连接池样本并执行容量淘汰。 */
    synchronized void addPoolSample(TimeBucket sample) {
        addBounded(poolSamples, sample);
    }

    /** 测试支撑入口：追加一个 SQL 延迟样本并执行容量淘汰。 */
    synchronized void addLatencySample(TimeBucket sample) {
        addBounded(latencySamples, sample);
    }

    /**
     * 构造不包含连接信息和凭据的监控能力状态。
     *
     * @param provider 能力提供方稳定编码
     * @param status 能力状态
     * @param reason 脱敏后的状态原因
     * @return 监控能力响应对象
     */
    private static ProviderCapability capability(String provider, String status, String reason) {
        ProviderCapability capability = new ProviderCapability();
        capability.setProvider(provider);
        capability.setStatus(status);
        capability.setReason(reason);
        return capability;
    }

    /** MySQL 单次 SQL 指标读取结果；延迟单位均为毫秒。 */
    private record SqlMetrics(BigDecimal averageMillis,
                              BigDecimal p95Millis,
                              BigDecimal p99Millis,
                              List<CategoryMetric> slowSqlTop) {
    }
}
