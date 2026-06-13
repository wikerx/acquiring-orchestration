package com.scott.payment.admin.controller;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * Redis 缓存监控控制器。
 */
@RestController
@RequestMapping("/admin/monitor/cache")
public class MonitorCacheController {

    /**
     * 最大扫描 Key 数，避免监控页误扫过大 Redis 实例。
     */
    private static final int MAX_SCAN_KEYS = 1000;

    private final StringRedisTemplate stringRedisTemplate;

    public MonitorCacheController(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    /**
     * 查询 Redis 基础运行信息。
     *
     * @return Redis 基础信息、命令统计和内存信息
     */
    @GetMapping("/info")
    @RequiresPermission("system:cache:list")
    public CommonResult<Map<String, Object>> info() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (stringRedisTemplate == null) {
            result.put("connected", false);
            result.put("message", "RedisTemplate unavailable");
            return success(result);
        }
        try {
            Properties properties = stringRedisTemplate.execute((RedisCallback<Properties>) connection -> connection.serverCommands().info());
            result.put("connected", true);
            result.put("info", toMap(properties));
            return success(result);
        } catch (RuntimeException exception) {
            result.put("connected", false);
            result.put("message", exception.getMessage());
            return success(result);
        }
    }

    /**
     * 分页查询缓存 Key。
     *
     * @param keyPattern Key 模式，默认 *
     * @param pageNo     页码
     * @param pageSize   每页大小
     * @return 缓存 Key 分页
     */
    @GetMapping("/keys")
    @RequiresPermission("system:cache:query")
    public CommonResult<Map<String, Object>> keys(@RequestParam(value = "keyPattern", required = false) String keyPattern,
                                                  @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                  @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
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
        return success(result);
    }

    /**
     * 查询指定缓存值。
     *
     * @param key Redis Key
     * @return 缓存详情
     */
    @GetMapping("/value")
    @RequiresPermission("system:cache:query")
    public CommonResult<Map<String, Object>> value(@RequestParam("key") String key) {
        Map<String, Object> result = toKeyRow(key);
        result.put("value", readValue(key));
        return success(result);
    }

    /**
     * 删除指定缓存 Key。
     *
     * @param key Redis Key
     * @return 删除结果
     */
    @DeleteMapping("/key")
    @RequiresPermission("system:cache:clear")
    public CommonResult<Boolean> delete(@RequestParam("key") String key) {
        if (stringRedisTemplate == null || !StringUtils.hasText(key)) {
            return success(false);
        }
        return success(Boolean.TRUE.equals(stringRedisTemplate.delete(key)));
    }

    private List<String> scanKeys(String pattern) {
        if (stringRedisTemplate == null) {
            return List.of();
        }
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .sorted(Comparator.naturalOrder())
                .limit(MAX_SCAN_KEYS)
                .toList();
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
