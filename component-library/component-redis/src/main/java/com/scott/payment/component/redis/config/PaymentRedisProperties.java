package com.scott.payment.component.redis.config;

import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRedisProperties
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 支付系统 Redis 物理 Key 配置。
 * @status : create
 *
 *
 * <p>所有直接访问 Redis 的业务 Key 都必须经过本配置拼接系统和环境前缀，
 * 避免同一 Redis 集群中的不同环境、系统或用途相互覆盖。</p>
 */
@ConfigurationProperties(prefix = "payment.redis")
public class PaymentRedisProperties implements PaymentRedisKeyResolver {

    /**
     * Redis Cluster Hash Tag 原始身份最大长度，单位为字符；超长身份必须先摘要化。
     */
    private static final int MAX_SLOT_IDENTITY_LENGTH = 512;

    /**
     * 最终 Redis 物理 Key 最大长度，单位为字符。
     */
    private static final int MAX_PHYSICAL_KEY_LENGTH = 512;

    private static final Pattern SAFE_KEY_SEGMENT =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    /**
     * Redis Key 顶级前缀，建议格式为 acquiring:{environment}。
     */
    private String keyPrefix = "acquiring:local";

    /**
     * MQ 辅助去重的容量与有效期边界；不包含消息业务键或其他敏感数据。
     */
    private final MqDedup mqDedup = new MqDedup();

    /**
     * 获取直连 Redis 业务 Key 的系统与环境前缀。
     *
     * @return 格式为 acquiring:{environment} 的前缀
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * 配置直连 Redis 业务 Key 的系统与环境前缀。
     *
     * @param keyPrefix 格式必须为 acquiring:{environment}
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * 获取 MQ 辅助去重边界配置。
     *
     * @return 单桶容量和最大有效期配置
     */
    public MqDedup getMqDedup() {
        return mqDedup;
    }

    /**
     * 按系统、环境和业务片段构造规范化 Redis Key，忽略空片段并使用安全默认值。
     * @param segments 有界参数集合或键值片段，空元素按当前方法约定忽略或拒绝
     * @return 当前方法生成或规范化后的文本值
     */
    public String key(String... segments) {
        String prefix = normalizeSegment(keyPrefix, "acquiring:local");
        String suffix = Arrays.stream(segments == null ? new String[0] : segments)
                .filter(StringUtils::hasText)
                .map(segment -> normalizeSegment(segment, "unknown"))
                .collect(Collectors.joining(":"));
        return suffix.isEmpty() ? prefix : prefix + ":" + suffix;
    }

    /**
     * 按系统、环境、业务域和业务用途构造精简 Redis Key。
     *
     * <p>标准格式为 {@code acquiring:{environment}:{domain}:{business}[:{businessKey}]}。
     * 服务名和兼容版本不进入物理 Key。</p>
     *
     * @param domain          业务域
     * @param business        业务用途
     * @param businessSegments 可选业务唯一性片段
     * @return 使用精简命名规则的物理 Redis Key
     */
    @Override
    public String businessKey(String domain, String business, String... businessSegments) {
        StringBuilder keyBuilder = new StringBuilder(strictPrefix())
                .append(':').append(requireSafeSegment(domain, "domain"))
                .append(':').append(requireSafeSegment(business, "business"));
        if (businessSegments != null) {
            for (String segment : businessSegments) {
                keyBuilder.append(':').append(requireSafeSegment(segment, "business segment"));
            }
        }
        return validatedPhysicalKey(keyBuilder);
    }

    /**
     * 构造使用精简命名规则的 Redis Cluster 同槽 Key。
     *
     * <p>Hash Tag 只保存组件生成的槽身份摘要，调用方不能把原始业务值或自定义
     * {@code {}} 写入物理 Key。</p>
     *
     * @param domain           业务域
     * @param business         业务用途
     * @param slotIdentity     原子操作范围的稳定身份，不直接写入物理 Key
     * @param businessSegments Hash Tag 之后的业务片段
     * @return 使用精简命名和组件摘要 Hash Tag 的物理 Key
     */
    public String coLocatedBusinessKey(String domain,
                                       String business,
                                       String slotIdentity,
                                       String... businessSegments) {
        if (!StringUtils.hasText(slotIdentity) || slotIdentity.length() > MAX_SLOT_IDENTITY_LENGTH) {
            throw new IllegalArgumentException("Redis Cluster slot identity is invalid");
        }
        if (businessSegments == null || businessSegments.length == 0) {
            throw new IllegalArgumentException(
                    "Redis Cluster co-located Key requires at least one business segment");
        }
        StringBuilder keyBuilder = new StringBuilder(strictPrefix())
                .append(':').append(requireSafeSegment(domain, "domain"))
                .append(':').append(requireSafeSegment(business, "business"))
                .append(":{").append(RedisKeyDigest.sha256(slotIdentity.trim())).append('}');
        for (String segment : businessSegments) {
            keyBuilder.append(':').append(requireSafeSegment(segment, "business segment"));
        }
        return validatedPhysicalKey(keyBuilder);
    }

    /**
     * 把 Key 构造器转换为最终物理 Key，并执行统一长度上限校验。
     *
     * @param keyBuilder 已完成系统、环境和业务层级拼接的 Key 构造器
     * @return 长度不超过 512 个字符的物理 Key
     */
    private String validatedPhysicalKey(StringBuilder keyBuilder) {
        String physicalKey = keyBuilder.toString();
        if (physicalKey.length() > MAX_PHYSICAL_KEY_LENGTH) {
            throw new IllegalArgumentException("Redis physical Key exceeds "
                    + MAX_PHYSICAL_KEY_LENGTH + " characters");
        }
        return physicalKey;
    }

    private String strictPrefix() {
        String prefix = normalizeSegment(keyPrefix, "acquiring:local");
        String[] segments = prefix.split(":", -1);
        if (segments.length != 2 || !"acquiring".equals(segments[0])) {
            throw new IllegalArgumentException(
                    "Redis Key prefix must use acquiring:{environment}");
        }
        requireSafeSegment(segments[0], "key prefix");
        requireSafeSegment(segments[1], "environment");
        return String.join(":", segments);
    }

    private String requireSafeSegment(String value, String label) {
        if (!StringUtils.hasText(value) || !SAFE_KEY_SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException("Redis " + label + " contains unsafe characters or length");
        }
        return value;
    }

    private String normalizeSegment(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String normalized = value.trim();
        while (normalized.startsWith(":")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    /**
     * MQ 去重 ZSet 的资源边界配置。
     *
     * <p>单桶时间跨度等于调用方 TTL，物理 Key 最多保留当前桶、前一桶和短暂等待过期的旧桶。
     * 达到容量上限时 Redis 层返回降级结果，最终重复保护由数据库唯一约束承担。</p>
     */
    public static class MqDedup {

        /**
         * 单个时间桶允许保存的最大消息摘要数量，单位为个；成员是 SHA-256 摘要，不含消息明文。
         */
        private int maxMembersPerBucket = 100_000;

        /**
         * MQ 去重允许配置的最大有效期，单位秒；默认 30 天，防止误配置产生长期大 Key。
         */
        private long maxTtlSeconds = 2_592_000L;

        /**
         * 获取单个时间桶允许保存的最大摘要数量。
         *
         * @return 最大成员数，单位为个
         */
        public int getMaxMembersPerBucket() {
            return maxMembersPerBucket;
        }

        /**
         * 设置单个时间桶允许保存的最大摘要数量。
         *
         * @param maxMembersPerBucket 最大成员数，单位为个
         */
        public void setMaxMembersPerBucket(int maxMembersPerBucket) {
            this.maxMembersPerBucket = maxMembersPerBucket;
        }

        /**
         * 获取 MQ 去重允许配置的最大有效期。
         *
         * @return 最大有效期，单位秒
         */
        public long getMaxTtlSeconds() {
            return maxTtlSeconds;
        }

        /**
         * 设置 MQ 去重允许配置的最大有效期。
         *
         * @param maxTtlSeconds 最大有效期，单位秒
         */
        public void setMaxTtlSeconds(long maxTtlSeconds) {
            this.maxTtlSeconds = maxTtlSeconds;
        }
    }
}
