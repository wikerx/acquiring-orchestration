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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMonitorCacheApplicationService
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台 Redis 缓存监控应用服务
 * @status : create
 */
@Service
public class AdminMonitorCacheApplicationService {

    /**
     * MAX SCAN KEYS 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int MAX_SCAN_KEYS = 1000;

    /**
     * string Redis Template 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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

    /**
     * 完成 scan Keys 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param pattern pattern 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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

    /**
     * 转换生成 to Key Row 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param key key 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 完成 size Of 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param key key 输入值，含义由调用方法名称和所属业务对象限定
     * @param type type 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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

    /**
     * 完成 read Value 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param key key 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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

    /**
     * 转换生成 to Map 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param properties properties 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 完成 default Long 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private long defaultLong(Long value) {
        return value == null ? 0 : value;
    }
}
