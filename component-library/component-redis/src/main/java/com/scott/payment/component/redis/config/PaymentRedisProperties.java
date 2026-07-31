package com.scott.payment.component.redis.config;

import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 支付系统 Redis 物理 Key 配置。
 *
 * <p>所有直接访问 Redis 的业务 Key 都必须经过本配置拼接系统和环境前缀，
 * 避免同一 Redis 集群中的不同环境、系统或用途相互覆盖。</p>
 */
@ConfigurationProperties(prefix = "payment.redis")
public class PaymentRedisProperties implements PaymentRedisKeyResolver {

    /**
     * 兼容代际 Key 允许的最大版本号，防止无界版本片段扩大 Key 空间。
     */
    private static final int MAX_KEY_VERSION = 999;

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
     * 历史长 Key 到精简 Key 的迁移模式；默认只读写历史 Key，避免未配置环境自动切换。
     */
    private KeyMigrationMode keyMigrationMode = KeyMigrationMode.LEGACY_ONLY;

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
     * 获取历史长 Key 到精简 Key 的迁移模式。
     *
     * @return 当前迁移模式；配置为空时返回兼容优先的 {@link KeyMigrationMode#LEGACY_ONLY}
     */
    public KeyMigrationMode getKeyMigrationMode() {
        return keyMigrationMode == null ? KeyMigrationMode.LEGACY_ONLY : keyMigrationMode;
    }

    /**
     * 设置历史长 Key 到精简 Key 的迁移模式。
     *
     * @param keyMigrationMode 受控迁移模式；空值按 {@code LEGACY_ONLY} 处理
     */
    public void setKeyMigrationMode(KeyMigrationMode keyMigrationMode) {
        this.keyMigrationMode = keyMigrationMode;
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
     * 构造规范化的物理 Redis Key。
     *
     * @param segments 业务层级片段
     * @return 带系统和环境隔离前缀的物理 Key
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
     * 服务名不进入物理 Key；版本仅在发生不兼容迁移时作为显式业务片段传入。</p>
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
     * 构造包含服务、领域、业务和版本维度的 Redis v2 Key。
     *
     * @param service          服务名称，例如 service-risk
     * @param domain           业务领域，例如 risk
     * @param business         Redis 用途，例如 frequency
     * @param version          Key 结构版本，必须为正整数
     * @param businessSegments 业务唯一性片段
     * @return 带完整隔离维度的物理 Redis Key
     */
    public String versionedKey(String service,
                               String domain,
                               String business,
                               int version,
                               String... businessSegments) {
        return buildVersionedKey(service, domain, business, version, null, businessSegments);
    }

    /**
     * 构造 Redis Cluster 同槽 v2 Key，Hash Tag 仅由组件根据业务槽身份生成。
     *
     * @param service          服务名称
     * @param domain           业务领域
     * @param business         Redis 用途
     * @param version          Key 结构版本
     * @param slotIdentity     原子操作范围的稳定身份，不直接写入物理 Key
     * @param businessSegments Hash Tag 之后的业务片段
     * @return 带摘要 Hash Tag 的同槽物理 Redis Key
     */
    public String versionedCoLocatedKey(String service,
                                        String domain,
                                        String business,
                                        int version,
                                        String slotIdentity,
                                        String... businessSegments) {
        if (!StringUtils.hasText(slotIdentity) || slotIdentity.length() > MAX_SLOT_IDENTITY_LENGTH) {
            throw new IllegalArgumentException("Redis Cluster slot identity is invalid");
        }
        return buildVersionedKey(
                service,
                domain,
                business,
                version,
                RedisKeyDigest.sha256(slotIdentity),
                businessSegments
        );
    }

    private String buildVersionedKey(String service,
                                     String domain,
                                     String business,
                                     int version,
                                     String hashTag,
                                     String... businessSegments) {
        if (version <= 0 || version > MAX_KEY_VERSION) {
            throw new IllegalArgumentException("Redis Key version must be between 1 and " + MAX_KEY_VERSION);
        }
        if (businessSegments == null || businessSegments.length == 0) {
            throw new IllegalArgumentException("Redis versioned Key requires at least one business segment");
        }
        String prefix = strictPrefix();
        StringBuilder keyBuilder = new StringBuilder(prefix)
                .append(':').append(requireSafeSegment(service, "service"))
                .append(':').append(requireSafeSegment(domain, "domain"))
                .append(':').append(requireSafeSegment(business, "business"))
                .append(":v").append(version);
        if (hashTag != null) {
            keyBuilder.append(":{").append(hashTag).append('}');
        }
        if (businessSegments != null) {
            for (String segment : businessSegments) {
                keyBuilder.append(':').append(requireSafeSegment(segment, "business segment"));
            }
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
     * Redis Key 迁移模式。
     *
     * <p>迁移必须依次经过历史单写、双写历史优先、双写精简优先、精简单写历史回读和精简独占。
     * 任何环境不得跨过双写观察阶段直接切换，以免滚动发布实例读取不同物理 Key。</p>
     */
    public enum KeyMigrationMode {

        /** 仅使用历史长 Key，作为未配置环境的兼容默认值。 */
        LEGACY_ONLY(true, false, true, false),

        /** 双读双写，读取历史 Key 优先，用于首次建立精简 Key 数据。 */
        DUAL_LEGACY_FIRST(true, true, true, true),

        /** 双读双写，读取精简 Key 优先，用于切换前差异观察。 */
        DUAL_COMPACT_FIRST(true, true, true, true),

        /** 仅写精简 Key，精简未命中时允许回读历史 Key 并回填。 */
        COMPACT_WRITE_LEGACY_READ(true, true, false, true),

        /** 仅使用精简 Key，完成迁移后不再访问历史 Key。 */
        COMPACT_ONLY(false, true, false, true);

        /** 是否允许读取历史长 Key。 */
        private final boolean legacyReadEnabled;

        /** 是否允许读取精简 Key。 */
        private final boolean compactReadEnabled;

        /** 是否写入历史长 Key。 */
        private final boolean legacyWriteEnabled;

        /** 是否写入精简 Key。 */
        private final boolean compactWriteEnabled;

        KeyMigrationMode(boolean legacyReadEnabled,
                         boolean compactReadEnabled,
                         boolean legacyWriteEnabled,
                         boolean compactWriteEnabled) {
            this.legacyReadEnabled = legacyReadEnabled;
            this.compactReadEnabled = compactReadEnabled;
            this.legacyWriteEnabled = legacyWriteEnabled;
            this.compactWriteEnabled = compactWriteEnabled;
        }

        /**
         * 判断是否允许读取历史长 Key。
         *
         * @return 允许读取时为 true
         */
        public boolean legacyReadEnabled() {
            return legacyReadEnabled;
        }

        /**
         * 判断是否允许读取精简 Key。
         *
         * @return 允许读取时为 true
         */
        public boolean compactReadEnabled() {
            return compactReadEnabled;
        }

        /**
         * 判断是否写入历史长 Key。
         *
         * @return 需要写入时为 true
         */
        public boolean legacyWriteEnabled() {
            return legacyWriteEnabled;
        }

        /**
         * 判断是否写入精简 Key。
         *
         * @return 需要写入时为 true
         */
        public boolean compactWriteEnabled() {
            return compactWriteEnabled;
        }

        /**
         * 判断是否处于需要新旧代际严格一致的双写阶段。
         *
         * @return 双读双写阶段为 true
         */
        public boolean mirroredGenerationRequired() {
            return this == DUAL_LEGACY_FIRST || this == DUAL_COMPACT_FIRST;
        }

        /**
         * 判断双读场景是否以历史 Key 为首选。
         *
         * @return 历史 Key 优先时为 true
         */
        public boolean legacyReadPreferred() {
            return this == LEGACY_ONLY || this == DUAL_LEGACY_FIRST;
        }
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
