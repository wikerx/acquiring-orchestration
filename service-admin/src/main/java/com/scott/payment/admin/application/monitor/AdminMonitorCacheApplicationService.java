package com.scott.payment.admin.application.monitor;

import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.CacheMetricsResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.CategoryMetric;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ProviderCapability;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TimeBucket;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.redis.cache.PaymentCacheProperties;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMonitorCacheApplicationService
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台 Redis 缓存监控应用服务，仅允许查看和清理非敏感平台配置缓存的 Key 元数据。
 * @status : create
 */
@Slf4j
@Service
public class AdminMonitorCacheApplicationService {

    /** 单次 Redis SCAN 请求建议返回的 Key 数量，不代表结果硬上限。 */
    private static final int SCAN_COUNT = 100;

    /** 单次管理端查询最多检查的 Key 数，防止误用通配符拖垮 Redis。 */
    private static final int MAX_SCAN_KEYS = 1000;

    /** 管理端 Key 元数据查询允许的最大分页大小。 */
    private static final int MAX_PAGE_SIZE = 100;

    /** Redis 图表最多保留七天、每分钟一条的进程内采样。 */
    static final int MAX_METRIC_SAMPLES = 7 * 24 * 60;

    /** 接口刷新和定时任务共享一分钟最小采样间隔。 */
    private static final long MIN_SAMPLE_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    /** 仅用于读取 Redis 运行信息、Key 元数据和删除受控缓存 Key 的模板。 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 提供平台缓存 Key 前缀和允许管理的缓存名称。 */
    private final PaymentCacheProperties cacheProperties;

    /** Redis 内存指标进程内历史，元素按采样时间升序保存。 */
    private final Deque<TimeBucket> memorySamples = new ArrayDeque<>(MAX_METRIC_SAMPLES);
    /** Redis 吞吐指标进程内历史，元素按采样时间升序保存。 */
    private final Deque<TimeBucket> opsSamples = new ArrayDeque<>(MAX_METRIC_SAMPLES);
    /** Redis 命中率进程内历史，元素按采样时间升序保存。 */
    private final Deque<TimeBucket> hitRateSamples = new ArrayDeque<>(MAX_METRIC_SAMPLES);

    /** 最近一次有界扫描得到的 Key 类型分布；未采样时为空集合。 */
    private List<CategoryMetric> keyTypeDistribution = List.of();
    /** Redis INFO 历史采样能力状态，不包含连接凭据。 */
    private ProviderCapability historyCapability = capability(
            "REDIS_HISTORY_METRICS", "UNAVAILABLE", "No Redis metric sample is available yet");
    /** Redis Key 类型扫描能力状态，不包含 Key 对应的 Value。 */
    private ProviderCapability keyTypeCapability = capability(
            "REDIS_KEY_TYPE_METRICS", "UNAVAILABLE", "No Redis key type sample is available yet");
    /** 上一次累计命中数，用于计算当前采样区间命中率；首个样本时为空。 */
    private Long previousHits;
    /** 上一次累计未命中数，用于计算当前采样区间命中率；首个样本时为空。 */
    private Long previousMisses;
    /** 最近一次采样尝试的单调时钟值，单位纳秒。 */
    private long lastSampleAttemptNanos = Long.MIN_VALUE;

    /**
     * 创建 Redis 缓存监控应用服务。
     *
     * @param stringRedisTemplateProvider RedisTemplate 提供者
     * @param cacheProperties             Spring Cache 配置
     */
    public AdminMonitorCacheApplicationService(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                               PaymentCacheProperties cacheProperties) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.cacheProperties = cacheProperties;
    }

    /** 在 Spring Bean 初始化完成后尝试采集首个 Redis 指标点。 */
    @PostConstruct
    public void initializeMetrics() {
        sampleMetrics();
    }

    /** 每分钟采集一次 Redis 内存、吞吐、命中率和有界 Key 类型分布。 */
    @Scheduled(fixedRate = 60_000L)
    public synchronized void sampleMetrics() {
        long nowNanos = System.nanoTime();
        if (lastSampleAttemptNanos != Long.MIN_VALUE
                && nowNanos - lastSampleAttemptNanos < MIN_SAMPLE_INTERVAL_NANOS) {
            return;
        }
        lastSampleAttemptNanos = nowNanos;
        if (stringRedisTemplate == null) {
            historyCapability = capability(
                    "REDIS_HISTORY_METRICS", "UNAVAILABLE", "RedisTemplate unavailable");
            keyTypeCapability = capability(
                    "REDIS_KEY_TYPE_METRICS", "UNAVAILABLE", "RedisTemplate unavailable");
            return;
        }
        try {
            RedisMetricsSnapshot snapshot = stringRedisTemplate.execute(
                    (RedisCallback<RedisMetricsSnapshot>) this::readMetricsSnapshot);
            if (snapshot == null || snapshot.runtimeMetrics() == null) {
                historyCapability = capability(
                        "REDIS_HISTORY_METRICS", "UNAVAILABLE", "Redis INFO metrics unavailable");
                keyTypeCapability = capability(
                        "REDIS_KEY_TYPE_METRICS", "UNAVAILABLE", "Redis key type metrics unavailable");
                return;
            }
            recordMetrics(LocalDateTime.now(), snapshot.runtimeMetrics());
            keyTypeDistribution = snapshot.keyTypes().metrics();
            historyCapability = capability(
                    "REDIS_HISTORY_METRICS", "AVAILABLE", snapshot.runtimeReason());
            keyTypeCapability = capability(
                    "REDIS_KEY_TYPE_METRICS", "AVAILABLE", snapshot.keyTypes().reason());
        } catch (RuntimeException exception) {
            historyCapability = capability(
                    "REDIS_HISTORY_METRICS", "UNAVAILABLE",
                    "Redis INFO metrics unavailable: " + exception.getClass().getSimpleName());
            keyTypeCapability = capability(
                    "REDIS_KEY_TYPE_METRICS", "UNAVAILABLE",
                    "Redis key type metrics unavailable: " + exception.getClass().getSimpleName());
            log.warn("event: ADMIN_REDIS_METRICS_FAILED exceptionType: {}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 返回 Redis 七天进程内趋势和最近一次有界 Key 类型分布。
     *
     * @return Redis 趋势、Key 类型分布和能力状态
     */
    public synchronized CacheMetricsResponse metrics() {
        sampleMetrics();
        CacheMetricsResponse response = new CacheMetricsResponse();
        response.setMemoryTrend(new ArrayList<>(memorySamples));
        response.setOpsTrend(new ArrayList<>(opsSamples));
        response.setHitRateTrend(new ArrayList<>(hitRateSamples));
        response.setKeyTypeDistribution(new ArrayList<>(keyTypeDistribution));
        response.setHistoryCapability(historyCapability);
        response.setKeyTypeCapability(keyTypeCapability);
        response.setGeneratedAt(LocalDateTime.now());
        return response;
    }

    /** 读取一次 Redis 指标快照；Cluster 模式只聚合可用 Master。 */
    private RedisMetricsSnapshot readMetricsSnapshot(RedisConnection connection) {
        RedisInfoResult info = readInfo(connection);
        RuntimeMetrics runtime = aggregateRuntimeMetrics(info.nodeInfo());
        KeyTypeMetrics keyTypes = scanKeyTypes(connection);
        String runtimeReason = info.failedNodes().isEmpty()
                ? "Aggregated from " + info.nodeInfo().size() + " Redis node(s)"
                : "Partial metrics; failed nodes: " + String.join(",", info.failedNodes());
        return new RedisMetricsSnapshot(runtime, keyTypes, runtimeReason);
    }

    /** 聚合 Redis INFO 中可相加的 Master 指标。 */
    private RuntimeMetrics aggregateRuntimeMetrics(Map<String, Map<String, String>> nodeInfo) {
        Long usedBytes = sumInfo(nodeInfo, "used_memory", false);
        Long maxBytes = sumInfo(nodeInfo, "maxmemory", true);
        Long opsPerSecond = sumInfo(nodeInfo, "instantaneous_ops_per_sec", false);
        Long hits = sumInfo(nodeInfo, "keyspace_hits", false);
        Long misses = sumInfo(nodeInfo, "keyspace_misses", false);
        if (usedBytes == null && opsPerSecond == null && hits == null && misses == null) {
            throw new IllegalStateException("Redis INFO does not contain runtime metrics");
        }
        return new RuntimeMetrics(usedBytes, maxBytes, opsPerSecond, hits, misses);
    }

    /** 对全部 Master 做有界 SCAN，并且只读取 TYPE，不读取缓存值。 */
    private KeyTypeMetrics scanKeyTypes(RedisConnection connection) {
        ScanOptions options = ScanOptions.scanOptions().match("*").count(SCAN_COUNT).build();
        List<byte[]> keys = new ArrayList<>(MAX_SCAN_KEYS);
        boolean truncated;
        if (connection instanceof RedisClusterConnection clusterConnection) {
            truncated = scanClusterKeys(clusterConnection, options, keys);
        } else {
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                truncated = collectPhysicalKeys(cursor, keys);
            }
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (byte[] key : keys) {
            DataType type = connection.keyCommands().type(key);
            if (type == null || type == DataType.NONE) {
                continue;
            }
            counts.merge(type.code(), 1L, Long::sum);
        }
        List<CategoryMetric> metrics = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> category(entry.getKey(), entry.getValue()))
                .toList();
        String reason = truncated
                ? "Key type distribution sampled from the first " + MAX_SCAN_KEYS + " physical keys"
                : "Key type distribution sampled from " + keys.size() + " physical keys";
        return new KeyTypeMetrics(metrics, reason);
    }

    /**
     * 依次扫描 Redis Cluster 中可用 Master，并对整个集群共享物理 Key 检查上限。
     *
     * <p>任一节点达到上限立即停止，防止节点数增长后把单次监控采样放大为无界操作。</p>
     *
     * @param connection Redis Cluster 连接
     * @param options 固定 SCAN 条件
     * @param keys 已收集的物理 Key 字节，仅用于后续 TYPE 查询
     * @return 达到集群级扫描上限时返回 true
     */
    private boolean scanClusterKeys(RedisClusterConnection connection,
                                    ScanOptions options,
                                    List<byte[]> keys) {
        for (RedisClusterNode masterNode : masterNodes(connection)) {
            try (Cursor<byte[]> cursor = connection.scan(masterNode, options)) {
                if (collectPhysicalKeys(cursor, keys)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 消费单节点游标并执行共享的物理 Key 上限。
     *
     * @param cursor Redis SCAN 游标
     * @param keys 跨节点共享的收集结果
     * @return 达到扫描上限时返回 true
     */
    private boolean collectPhysicalKeys(Cursor<byte[]> cursor, List<byte[]> keys) {
        while (cursor.hasNext()) {
            if (keys.size() >= MAX_SCAN_KEYS) {
                return true;
            }
            keys.add(cursor.next());
        }
        return false;
    }

    /**
     * 把一次 Redis INFO 快照拆分为内存、吞吐和区间命中率三个同步历史点。
     *
     * @param timestamp 当前 Admin 进程采样时间
     * @param metrics Redis Master 聚合指标
     */
    private void recordMetrics(LocalDateTime timestamp, RuntimeMetrics metrics) {
        Map<String, BigDecimal> memory = new LinkedHashMap<>();
        memory.put("used", decimal(metrics.usedBytes()));
        memory.put("max", decimal(metrics.maxBytes()));
        Map<String, BigDecimal> ops = new LinkedHashMap<>();
        ops.put("ops", decimal(metrics.opsPerSecond()));
        Map<String, BigDecimal> hitRate = new LinkedHashMap<>();
        hitRate.put("hitRate", calculateHitRate(metrics.hits(), metrics.misses()));
        addHistorySample(bucket(timestamp, memory), bucket(timestamp, ops), bucket(timestamp, hitRate));
    }

    /**
     * 原子追加同一采样时刻的三类历史指标，并统一执行七天有界保留。
     *
     * @param memory Redis 内存指标
     * @param ops Redis 每秒操作数指标
     * @param hitRate Redis 区间命中率指标
     */
    void addHistorySample(TimeBucket memory, TimeBucket ops, TimeBucket hitRate) {
        addBounded(memorySamples, memory);
        addBounded(opsSamples, ops);
        addBounded(hitRateSamples, hitRate);
    }

    /**
     * 使用相邻 Redis 累计计数差值计算当前采样区间命中率。
     *
     * <p>首个样本使用当前累计值；Redis 重启或计数回退时同样重新建立基线。没有请求样本
     * 时返回 null，避免页面把“无访问”展示为 0% 命中率。</p>
     *
     * @param hits Redis 累计 keyspace_hits，单位次，允许为空
     * @param misses Redis 累计 keyspace_misses，单位次，允许为空
     * @return 0 到 100 的百分比，保留两位小数；无法计算时返回 null
     */
    private BigDecimal calculateHitRate(Long hits, Long misses) {
        if (hits == null || misses == null) {
            return null;
        }
        long sampleHits = hits;
        long sampleMisses = misses;
        if (previousHits != null && previousMisses != null && hits >= previousHits && misses >= previousMisses) {
            sampleHits = hits - previousHits;
            sampleMisses = misses - previousMisses;
        }
        previousHits = hits;
        previousMisses = misses;
        long total = sampleHits + sampleMisses;
        return total <= 0L ? null : BigDecimal.valueOf(sampleHits)
                .multiply(BigDecimal.valueOf(100L))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /**
     * 汇总各 Redis Master 的非负 INFO 指标。
     *
     * @param nodeInfo 按节点保存的 INFO
     * @param property 待读取的固定 INFO 属性名
     * @param ignoreZero 是否把零视为“未配置”，用于 maxmemory 等可选指标
     * @return 至少一个节点存在有效值时返回总和，否则返回 null
     */
    private Long sumInfo(Map<String, Map<String, String>> nodeInfo, String property, boolean ignoreZero) {
        long total = 0L;
        boolean present = false;
        for (Map<String, String> info : nodeInfo.values()) {
            Long value = nonNegativeLong(info.get(property));
            if (value == null || (ignoreZero && value == 0L)) {
                continue;
            }
            total += value;
            present = true;
        }
        return present ? total : null;
    }

    private Long nonNegativeLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed < 0L ? null : parsed;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal decimal(Long value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private TimeBucket bucket(LocalDateTime timestamp, Map<String, BigDecimal> values) {
        TimeBucket bucket = new TimeBucket();
        bucket.setTimestamp(timestamp);
        bucket.setValues(values);
        return bucket;
    }

    private void addBounded(Deque<TimeBucket> samples, TimeBucket sample) {
        samples.addLast(sample);
        while (samples.size() > MAX_METRIC_SAMPLES) {
            samples.removeFirst();
        }
    }

    private CategoryMetric category(String key, long value) {
        CategoryMetric metric = new CategoryMetric();
        metric.setKey(key);
        metric.setLabel(key);
        metric.setValue(BigDecimal.valueOf(value));
        return metric;
    }

    private static ProviderCapability capability(String provider, String status, String reason) {
        ProviderCapability capability = new ProviderCapability();
        capability.setProvider(provider);
        capability.setStatus(status);
        capability.setReason(reason);
        return capability;
    }

    /**
     * 查询 Redis 运行信息。
     *
     * @return Redis 连接状态、部署模式、页面摘要与各节点运行信息
     */
    public Map<String, Object> info() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (stringRedisTemplate == null) {
            result.put("connected", false);
            result.put("message", "RedisTemplate unavailable");
            return result;
        }
        try {
            RedisInfoResult redisInfo = stringRedisTemplate.execute(
                    (RedisCallback<RedisInfoResult>) this::readInfo);
            if (redisInfo == null) {
                result.put("connected", false);
                result.put("message", "Redis INFO unavailable");
                return result;
            }
            Map<String, String> summary = firstAvailableInfo(redisInfo.nodeInfo());
            result.put("connected", redisInfo.failedNodes().isEmpty() && !summary.isEmpty());
            result.put("deploymentMode", redisInfo.deploymentMode());
            result.put("masterCount", redisInfo.nodeInfo().size() + redisInfo.failedNodes().size());
            result.put("info", summary);
            result.put("nodes", redisInfo.nodeInfo());
            if (!redisInfo.failedNodes().isEmpty()) {
                result.put("failedNodes", redisInfo.failedNodes());
                result.put("message", "Redis INFO unavailable for one or more nodes");
            }
            return result;
        } catch (RuntimeException exception) {
            result.put("connected", false);
            result.put("message", "Redis INFO unavailable: " + exception.getClass().getSimpleName());
            return result;
        }
    }

    /**
     * 分页查询允许监控的 Redis Key 元数据。
     *
     * @param keyPattern Key 模式；相对模式会自动限定在平台配置缓存命名空间
     * @param pageNo     页码
     * @param pageSize   每页大小，最大 100
     * @return Key 列表与分页摘要
     */
    public Map<String, Object> keys(String keyPattern, int pageNo, int pageSize) {
        ScanResult scanResult = scanKeys(toManagedScanPattern(keyPattern));
        List<String> keys = scanResult.keys();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long requestedOffset = (long) (safePageNo - 1) * safePageSize;
        int fromIndex = (int) Math.min(requestedOffset, keys.size());
        int toIndex = Math.min(fromIndex + safePageSize, keys.size());
        List<Map<String, Object>> records = keys.subList(fromIndex, toIndex).stream()
                .map(this::toKeyRow)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", keys.size());
        result.put("truncated", scanResult.truncated());
        return result;
    }

    /**
     * 查询指定平台配置缓存 Key 的元数据。
     *
     * <p>管理端不得读取 Redis Value；返回值固定声明 {@code valueReadable=false}。</p>
     *
     * @param key Redis Key
     * @return Key 类型、TTL、大小及 Value 可读性
     */
    public Map<String, Object> value(String key) {
        requireManagedKey(key);
        Map<String, Object> result = toKeyRow(key);
        result.put("valueReadable", false);
        result.put("value", null);
        return result;
    }

    /**
     * 删除指定平台配置缓存 Key。
     *
     * @param key Redis Key
     * @return 是否删除成功
     */
    public boolean delete(String key) {
        requireManagedKey(key);
        if (stringRedisTemplate == null) {
            return false;
        }
        boolean deleted = Boolean.TRUE.equals(stringRedisTemplate.delete(key));
        log.info("event: ADMIN_REDIS_CACHE_KEY_DELETE keyDigest: {} deleted: {}",
                RedisKeyDigest.sha256(key), deleted);
        return deleted;
    }

    /**
     * 使用有界 SCAN 遍历单节点 Redis 或全部 Cluster Master，最多检查 {@link #MAX_SCAN_KEYS} 条物理 Key。
     *
     * @param pattern 已限定到平台配置命名空间的匹配模式
     * @return 按字典序排列的已登记公开配置 Key 和扫描截断状态
     */
    private ScanResult scanKeys(String pattern) {
        if (stringRedisTemplate == null) {
            return new ScanResult(List.of(), false);
        }
        ScanResult result = stringRedisTemplate.execute(
                (RedisCallback<ScanResult>) connection -> scanConnection(connection, pattern));
        if (result == null || result.keys().isEmpty()) {
            return new ScanResult(List.of(), result != null && result.truncated());
        }
        return new ScanResult(
                result.keys().stream().distinct().sorted(Comparator.naturalOrder()).toList(),
                result.truncated()
        );
    }

    /**
     * 按连接类型执行有界 SCAN；Cluster 遍历 Master，单节点只扫描当前连接。
     *
     * @param connection Spring Data Redis 连接
     * @param pattern    已限定到公开配置缓存命名空间的模式
     * @return 经过业务白名单过滤的物理 Key 和截断状态
     */
    private ScanResult scanConnection(RedisConnection connection, String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build();
        List<String> scannedKeys = new ArrayList<>(Math.min(SCAN_COUNT, MAX_SCAN_KEYS));
        if (!(connection instanceof RedisClusterConnection clusterConnection)) {
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                return collectScannedKeys(cursor, scannedKeys);
            }
        }
        int inspectedCount = 0;
        boolean truncated = false;
        for (RedisClusterNode masterNode : masterNodes(clusterConnection)) {
            if (inspectedCount >= MAX_SCAN_KEYS) {
                truncated = true;
                break;
            }
            try (Cursor<byte[]> cursor = clusterConnection.scan(masterNode, options)) {
                while (cursor.hasNext()) {
                    if (inspectedCount >= MAX_SCAN_KEYS) {
                        truncated = true;
                        break;
                    }
                    inspectedCount++;
                    addManagedKey(scannedKeys, cursor.next());
                }
            }
            if (truncated) {
                break;
            }
        }
        return new ScanResult(scannedKeys, truncated);
    }

    /**
     * 消费单节点 SCAN 游标并执行统一的检查上限与公开配置白名单。
     *
     * @param cursor      Redis SCAN 游标
     * @param scannedKeys 已收集的公开配置 Key
     * @return 单节点扫描结果
     */
    private ScanResult collectScannedKeys(Cursor<byte[]> cursor, List<String> scannedKeys) {
        int inspectedCount = 0;
        while (cursor.hasNext()) {
            if (inspectedCount >= MAX_SCAN_KEYS) {
                return new ScanResult(scannedKeys, true);
            }
            inspectedCount++;
            addManagedKey(scannedKeys, cursor.next());
        }
        return new ScanResult(scannedKeys, false);
    }

    /**
     * 将 SCAN 返回的物理 Key 转换为字符串，并且只保留已登记的公开配置 Key。
     *
     * @param scannedKeys 收集结果
     * @param physicalKey Redis 返回的物理 Key 字节
     */
    private void addManagedKey(List<String> scannedKeys, byte[] physicalKey) {
        String key = new String(physicalKey, StandardCharsets.UTF_8);
        if (isManagedDataKey(key)) {
            scannedKeys.add(key);
        }
    }

    /**
     * 读取单节点或 Cluster Redis INFO；Cluster 中单节点失败不阻断其余 Master 的采集。
     *
     * @param connection Spring Data Redis 当前连接
     * @return 部署模式、节点 INFO 与失败节点摘要
     */
    private RedisInfoResult readInfo(RedisConnection connection) {
        if (!(connection instanceof RedisClusterConnection clusterConnection)) {
            Map<String, String> rawInfo = toMap(connection.serverCommands().info());
            Map<String, Map<String, String>> aggregatedClusterInfo = aggregatedClusterInfo(rawInfo);
            if (!aggregatedClusterInfo.isEmpty()) {
                return new RedisInfoResult("cluster", aggregatedClusterInfo, List.of());
            }
            Map<String, Map<String, String>> nodeInfo = new LinkedHashMap<>();
            nodeInfo.put("standalone", rawInfo);
            return new RedisInfoResult("standalone", nodeInfo, List.of());
        }
        Map<String, Map<String, String>> nodeInfo = new LinkedHashMap<>();
        List<String> failedNodes = new ArrayList<>();
        for (RedisClusterNode masterNode : masterNodes(clusterConnection)) {
            String nodeName = nodeName(masterNode);
            try {
                nodeInfo.put(nodeName, toMap(clusterConnection.serverCommands().info(masterNode)));
            } catch (RuntimeException exception) {
                failedNodes.add(nodeName);
                log.warn("event: ADMIN_REDIS_CLUSTER_INFO_FAILED node: {}", nodeName, exception);
            }
        }
        return new RedisInfoResult("cluster", nodeInfo, failedNodes);
    }

    /**
     * 获取当前拓扑中未标记故障的 Master，并按地址排序以稳定管理端输出和测试结果。
     *
     * @param connection Redis Cluster 连接
     * @return 活跃 Master 节点
     */
    private List<RedisClusterNode> masterNodes(RedisClusterConnection connection) {
        Iterable<RedisClusterNode> clusterNodes = connection.clusterCommands().clusterGetNodes();
        if (clusterNodes == null) {
            throw new IllegalStateException("Redis Cluster topology unavailable");
        }
        List<RedisClusterNode> masterNodes = new ArrayList<>();
        for (RedisClusterNode node : clusterNodes) {
            Set<RedisClusterNode.Flag> flags = node.getFlags();
            if (flags.contains(RedisClusterNode.Flag.MASTER) && !node.isMarkedAsFail()) {
                masterNodes.add(node);
            }
        }
        if (masterNodes.isEmpty()) {
            throw new IllegalStateException("Redis Cluster has no available master nodes");
        }
        masterNodes.sort(Comparator.comparing(this::nodeName));
        return masterNodes;
    }

    /**
     * 生成不含凭据的稳定节点标识。
     *
     * @param node Redis Cluster 节点
     * @return host:port
     */
    private String nodeName(RedisClusterNode node) {
        return node.getHost() + ":" + node.getPort();
    }

    /**
     * 把用户输入收敛到平台配置缓存命名空间，拒绝跨命名空间的完整 acquiring Key。
     *
     * @param keyPattern 用户输入的局部模式或受管完整模式
     * @return 仅能命中平台配置缓存的 SCAN 模式
     */
    private String toManagedScanPattern(String keyPattern) {
        String managedPrefix = managedKeyPrefix();
        if (!StringUtils.hasText(keyPattern) || "*".equals(keyPattern.trim())) {
            return managedPrefix + "*";
        }
        String pattern = keyPattern.trim();
        if (pattern.startsWith(managedPrefix)) {
            return pattern;
        }
        if (pattern.startsWith("acquiring:")) {
            throw invalidManagedKey();
        }
        return managedPrefix + pattern;
    }

    /**
     * 校验单 Key 属于统一系统参数数据命名空间。
     *
     * <p>pending 门禁使用独立的 {@code system:configPending:*} 命名空间，因此这里可以安全
     * 管理全部全局唯一系统参数缓存，而不会误删一致性控制 Key。</p>
     *
     * @param key 待查询或删除的完整 Redis Key
     */
    private void requireManagedKey(String key) {
        if (!isManagedDataKey(key)) {
            throw invalidManagedKey();
        }
    }

    /**
     * 判断物理 Key 是否对应统一系统参数缓存中的实际数据 Key。
     *
     * @param key 待检查的完整 Redis Key
     * @return Key 位于受管命名空间且配置键后缀非空时返回 true
     */
    private boolean isManagedDataKey(String key) {
        String managedPrefix = managedKeyPrefix();
        if (!StringUtils.hasText(key)
                || key.length() <= managedPrefix.length()
                || !key.startsWith(managedPrefix)) {
            return false;
        }
        return StringUtils.hasText(key.substring(managedPrefix.length()));
    }

    /**
     * 构造受管命名空间校验异常，避免向调用方返回实际 Redis 数据或连接细节。
     *
     * @return 参数非法异常
     */
    private ServiceException invalidManagedKey() {
        return new ServiceException(
                ApiResultEnum.PARAM_INVALID.getCode(),
                "Redis Key is outside the managed platform configuration cache namespace"
        );
    }

    /**
     * 根据环境 Cache 前缀和登记的 Cache Name 构造平台配置物理 Key 前缀。
     *
     * @return 以冒号结尾的受管前缀
     */
    private String managedKeyPrefix() {
        String configuredPrefix = StringUtils.hasText(cacheProperties.getKeyPrefix())
                ? cacheProperties.getKeyPrefix().trim()
                : "acquiring:local";
        while (configuredPrefix.endsWith(":")) {
            configuredPrefix = configuredPrefix.substring(0, configuredPrefix.length() - 1);
        }
        return configuredPrefix + ":" + PaymentCacheNames.SYSTEM_CONFIG + ":";
    }

    /**
     * 读取 Key 的类型、剩余 TTL 和集合基数，不读取或返回缓存 Value。
     *
     * @param key 已通过命名空间校验的 Redis Key
     * @return 管理端可展示的脱敏元数据
     */
    private Map<String, Object> toKeyRow(String key) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        if (stringRedisTemplate == null || !StringUtils.hasText(key)) {
            row.put("type", "NONE");
            row.put("ttl", -2);
            row.put("size", 0);
            return row;
        }
        DataType type = stringRedisTemplate.type(key);
        row.put("type", type == null ? "NONE" : type.code());
        row.put("ttl", stringRedisTemplate.getExpire(key));
        row.put("size", sizeOf(key, type));
        return row;
    }

    /**
     * 按 Redis 数据结构读取元素数；String 返回字节长度，集合返回成员数。
     *
     * @param key  已通过命名空间校验的 Redis Key
     * @param type Redis 数据结构类型
     * @return 非负大小；未知类型返回 0
     */
    private long sizeOf(String key, DataType type) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            case STRING -> defaultLong(stringRedisTemplate.opsForValue().size(key));
            case LIST -> defaultLong(stringRedisTemplate.opsForList().size(key));
            case SET -> defaultLong(stringRedisTemplate.opsForSet().size(key));
            case ZSET -> defaultLong(stringRedisTemplate.opsForZSet().size(key));
            case HASH -> defaultLong(stringRedisTemplate.opsForHash().size(key));
            default -> 0;
        };
    }

    /**
     * 将 Redis INFO 属性按名称排序，保证管理端输出稳定且便于审计差异。
     *
     * @param properties Redis INFO 属性
     * @return 有序属性映射
     */
    private Map<String, String> toMap(Properties properties) {
        Map<String, String> result = new LinkedHashMap<>();
        if (properties == null) {
            return result;
        }
        properties.stringPropertyNames().stream()
                .sorted()
                .forEach(name -> result.put(name, properties.getProperty(name)));
        return result;
    }

    /**
     * 将 Lettuce 普通连接返回的“节点.属性”Cluster INFO 拆成逐节点结构。
     *
     * <p>节点标识可能包含 IPv4 或 IPv6 分隔符，因此以 {@code .redis_mode=cluster}
     * 的完整后缀识别节点前缀，不能按首个句点截断。</p>
     *
     * @param rawInfo 带节点前缀的扁平 INFO
     * @return 逐节点 INFO；不是聚合 Cluster 格式时返回空映射
     */
    private Map<String, Map<String, String>> aggregatedClusterInfo(Map<String, String> rawInfo) {
        String modeSuffix = ".redis_mode";
        List<String> nodeNames = rawInfo.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith(modeSuffix))
                .filter(entry -> "cluster".equalsIgnoreCase(entry.getValue()))
                .map(entry -> entry.getKey().substring(0, entry.getKey().length() - modeSuffix.length()))
                .distinct()
                .sorted()
                .toList();
        if (nodeNames.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, String>> nodeInfo = new LinkedHashMap<>();
        for (String nodeName : nodeNames) {
            String propertyPrefix = nodeName + ".";
            Map<String, String> properties = new LinkedHashMap<>();
            rawInfo.forEach((name, value) -> {
                if (name.startsWith(propertyPrefix)) {
                    properties.put(name.substring(propertyPrefix.length()), value);
                }
            });
            nodeInfo.put(nodeName, properties);
        }
        return nodeInfo;
    }

    /**
     * 选择首个成功节点的 INFO 作为管理页面摘要；完整节点信息仍保留在 nodes 字段。
     *
     * @param nodeInfo 按稳定节点顺序排列的 INFO
     * @return 页面可直接展示的扁平 INFO
     */
    private Map<String, String> firstAvailableInfo(Map<String, Map<String, String>> nodeInfo) {
        return nodeInfo.values().stream()
                .filter(info -> info != null && !info.isEmpty())
                .findFirst()
                .map(LinkedHashMap::new)
                .orElseGet(LinkedHashMap::new);
    }

    /**
     * 将 Redis 客户端可能返回的 null 大小转换为 0。
     *
     * @param value Redis 大小结果
     * @return 原值或 0
     */
    private long defaultLong(Long value) {
        return value == null ? 0 : value;
    }

    /**
     * 有界 SCAN 结果。
     *
     * @param keys      已过滤控制 Key 和未登记配置后的公开缓存 Key
     * @param truncated 是否达到单次最多检查 1000 个物理 Key 的边界
     */
    private record ScanResult(List<String> keys, boolean truncated) {
    }

    /**
     * Redis INFO 聚合结果。
     *
     * @param deploymentMode standalone 或 cluster
     * @param nodeInfo    成功读取的节点 INFO
     * @param failedNodes 读取失败的节点标识
     */
    private record RedisInfoResult(String deploymentMode,
                                   Map<String, Map<String, String>> nodeInfo,
                                   List<String> failedNodes) {
    }

    /**
     * Redis INFO 运行指标。
     *
     * @param usedBytes 已使用内存，单位字节，允许为空
     * @param maxBytes 配置的最大内存，单位字节，未设置时为空
     * @param opsPerSecond 每秒操作数，允许为空
     * @param hits 累计命中数，单位次，允许为空
     * @param misses 累计未命中数，单位次，允许为空
     */
    private record RuntimeMetrics(Long usedBytes,
                                  Long maxBytes,
                                  Long opsPerSecond,
                                  Long hits,
                                  Long misses) {
    }

    /**
     * Redis Key 类型扫描结果。
     *
     * @param metrics Key 类型分布，默认空集合
     * @param reason 扫描范围和截断状态说明，不包含 Key 内容
     */
    private record KeyTypeMetrics(List<CategoryMetric> metrics, String reason) {
    }

    /**
     * Redis 单次完整监控快照。
     *
     * @param runtimeMetrics INFO 运行指标
     * @param keyTypes Key 类型扫描结果
     * @param runtimeReason 节点聚合范围说明，不包含凭据
     */
    private record RedisMetricsSnapshot(RuntimeMetrics runtimeMetrics,
                                        KeyTypeMetrics keyTypes,
                                        String runtimeReason) {
    }
}
