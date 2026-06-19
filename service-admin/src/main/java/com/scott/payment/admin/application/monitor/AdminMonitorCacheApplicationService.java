package com.scott.payment.admin.application.monitor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Redis 缓存监控应用服务。
 */
@Service
public class AdminMonitorCacheApplicationService {

    private static final int MAX_SCAN_KEYS = 1000;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建 Redis 缓存监控应用服务。
     *
     * @param stringRedisTemplateProvider RedisTemplate 提供者
     */
    public AdminMonitorCacheApplicationService(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    /**
     * 查询 Redis 运行信息。
     *
     * @return Redis 连接状态与运行信息
     */
    public Map<String, Object> info() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (stringRedisTemplate == null) {
            result.put("connected", false);
            result.put("message", "RedisTemplate unavailable");
            return result;
        }
        try {
            Properties properties = stringRedisTemplate.execute(
                    (RedisCallback<Properties>) connection -> connection.serverCommands().info());
            result.put("connected", true);
            result.put("info", toMap(properties));
            return result;
        } catch (RuntimeException exception) {
            result.put("connected", false);
            result.put("message", exception.getMessage());
            return result;
        }
    }

    /**
     * 分页查询 Redis Key 信息。
     *
     * @param keyPattern Key 模式
     * @param pageNo     页码
     * @param pageSize   每页大小
     * @return Key 列表与分页摘要
     */
    public Map<String, Object> keys(String keyPattern, int pageNo, int pageSize) {
        String pattern = StringUtils.hasText(keyPattern) ? keyPattern.trim() : "*";
        List<String> keys = scanKeys(pattern);
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, keys.size());
        int toIndex = Math.min(fromIndex + safePageSize, keys.size());
        List<Map<String, Object>> records = keys.subList(fromIndex, toIndex).stream()
                .map(this::toKeyRow)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", keys.size());
        result.put("truncated", keys.size() >= MAX_SCAN_KEYS);
        return result;
    }

    /**
     * 查询指定 Key 的元数据和值。
     *
     * @param key Redis Key
     * @return Key 详情
     */
    public Map<String, Object> value(String key) {
        Map<String, Object> result = toKeyRow(key);
        result.put("value", readValue(key));
        return result;
    }

    /**
     * 删除指定 Redis Key。
     *
     * @param key Redis Key
     * @return 是否删除成功
     */
    public boolean delete(String key) {
        if (stringRedisTemplate == null || !StringUtils.hasText(key)) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key));
    }

    private List<String> scanKeys(String pattern) {
        if (stringRedisTemplate == null) {
            return List.of();
        }
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream().sorted(Comparator.naturalOrder()).limit(MAX_SCAN_KEYS).toList();
    }

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

    private long sizeOf(String key, DataType type) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            case STRING -> {
                String value = stringRedisTemplate.opsForValue().get(key);
                yield value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
            }
            case LIST -> defaultLong(stringRedisTemplate.opsForList().size(key));
            case SET -> defaultLong(stringRedisTemplate.opsForSet().size(key));
            case ZSET -> defaultLong(stringRedisTemplate.opsForZSet().size(key));
            case HASH -> defaultLong(stringRedisTemplate.opsForHash().size(key));
            default -> 0;
        };
    }

    private Object readValue(String key) {
        if (stringRedisTemplate == null || !StringUtils.hasText(key)) {
            return null;
        }
        DataType type = stringRedisTemplate.type(key);
        if (type == null) {
            return null;
        }
        return switch (type) {
            case STRING -> stringRedisTemplate.opsForValue().get(key);
            case LIST -> stringRedisTemplate.opsForList().range(key, 0, 100);
            case SET -> stringRedisTemplate.opsForSet().members(key);
            case ZSET -> stringRedisTemplate.opsForZSet().rangeWithScores(key, 0, 100);
            case HASH -> stringRedisTemplate.opsForHash().entries(key);
            default -> null;
        };
    }

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

    private long defaultLong(Long value) {
        return value == null ? 0 : value;
    }
}
