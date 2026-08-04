package com.scott.payment.admin.application.monitor;

import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.cache.PlatformConfigCachePolicy;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.redis.cache.PaymentCacheProperties;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
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
@Service
public class AdminMonitorCacheApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AdminMonitorCacheApplicationService.class);

    /** 单次 Redis SCAN 请求建议返回的 Key 数量，不代表结果硬上限。 */
    private static final int SCAN_COUNT = 100;

    /** 单次管理端查询最多检查的 Key 数，防止误用通配符拖垮 Redis。 */
    private static final int MAX_SCAN_KEYS = 1000;

    /** 管理端 Key 元数据查询允许的最大分页大小。 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 仅用于读取 Redis 运行信息、Key 元数据和删除受控缓存 Key 的模板。 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 提供平台缓存 Key 前缀和允许管理的缓存名称。 */
    private final PaymentCacheProperties cacheProperties;

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
            result.put("message", exception.getMessage());
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
     * 校验单 Key 是已登记的平台公开配置数据，而不是 pending 门禁或同前缀未知 Key。
     *
     * <p>仅检查命名空间不足以保护 {@code config:public:pending:*} 控制 Key，因此必须同时
     * 使用 {@link PlatformConfigCachePolicy} 校验物理 Key 的业务后缀。</p>
     *
     * @param key 待查询或删除的完整 Redis Key
     */
    private void requireManagedKey(String key) {
        if (!isManagedDataKey(key)) {
            throw invalidManagedKey();
        }
    }

    /**
     * 判断物理 Key 是否对应四个已登记的非敏感平台公开配置。
     *
     * @param key 待检查的完整 Redis Key
     * @return 业务后缀属于公开配置白名单时返回 true
     */
    private boolean isManagedDataKey(String key) {
        String managedPrefix = managedKeyPrefix();
        if (!StringUtils.hasText(key)
                || key.length() <= managedPrefix.length()
                || !key.startsWith(managedPrefix)) {
            return false;
        }
        return PlatformConfigCachePolicy.isCacheable(key.substring(managedPrefix.length()));
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
        return configuredPrefix + ":" + PaymentCacheNames.PLATFORM_CONFIG + ":";
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
}
