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
     * MAX SCAN KEYS，用于保存 Admin Monitor Cache Application Service 中与 maxscan密钥 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int MAX_SCAN_KEYS = 1000;

    /**
     * string Redis Template，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 整理scan密钥，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param pattern pattern 输入值，参与 pattern 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 构造密钥row对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 构造、转换或解析后的业务值
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
     * 规范化sizeof，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param type type 输入值，参与 type 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 整理值，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 构造map对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param properties properties 输入值，参与 properties 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 整理默认long，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long defaultLong(Long value) {
        return value == null ? 0 : value;
    }
}
