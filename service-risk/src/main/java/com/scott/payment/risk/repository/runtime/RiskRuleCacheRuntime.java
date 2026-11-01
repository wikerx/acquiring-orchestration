package com.scott.payment.risk.repository.runtime;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.string.RedisStringService;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.RiskRuntimeCacheEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 统一执行风控规则的 generation Cache-Aside 读写。
 *
 * <p>该组件只负责规则查询结果的缓存加速。Redis、generation 或反序列化异常时返回数据库
 * 加载结果，不改变规则决策；已确认的空命中使用短 TTL 缓存以抑制缓存穿透。</p>
 */
@Slf4j
public class RiskRuleCacheRuntime {

    /**
     * 与现有规则发布流程共享的 generation 命名空间。
     */
    private static final String RULE_CACHE_NAMESPACE = "risk-runtime-rule";

    private final RedisStringService redisStringService;

    private final RedisCacheGenerationStore cacheGenerationStore;

    private final RiskEvaluationProperties properties;

    private final PaymentRedisProperties redisProperties;

    /**
     * 创建风控规则 Cache-Aside 运行时。
     *
     * @param redisStringService 可空的 Redis String 服务，缺失时直接执行加载函数
     * @param cacheGenerationStore 可空的规则缓存代际存储，缺失时直接执行加载函数
     * @param properties 缓存命中和未命中的 TTL 配置
     * @param redisProperties 带环境隔离的 Redis 业务 Key 配置
     */
    public RiskRuleCacheRuntime(RedisStringService redisStringService,
                                RedisCacheGenerationStore cacheGenerationStore,
                                RiskEvaluationProperties properties,
                                PaymentRedisProperties redisProperties) {
        this.redisStringService = redisStringService;
        this.cacheGenerationStore = cacheGenerationStore;
        this.properties = properties;
        this.redisProperties = redisProperties;
    }

    /**
     * 使用当前规则 generation 缓存单条规则命中。
     *
     * @param loader 数据库回源函数
     * @param segments 不含敏感原文的业务 Key 维度
     * @return 缓存结果或数据库加载结果
     */
    public Optional<RiskListMatch> findMatch(Supplier<Optional<RiskListMatch>> loader,
                                             String... segments) {
        Optional<RuleCacheKeys> cacheKeys = currentRuleCacheKeys(segments);
        return cacheKeys.map(keys -> findMatch(keys, loader)).orElseGet(loader);
    }

    /**
     * 使用当前规则 generation 缓存规则存在性。
     *
     * @param loader 数据库回源函数
     * @param segments 不含敏感原文的业务 Key 维度
     * @return 缓存值或数据库加载结果
     */
    public boolean resolveBoolean(Supplier<Boolean> loader, String... segments) {
        Optional<RuleCacheKeys> cacheKeys = currentRuleCacheKeys(segments);
        return cacheKeys.map(keys -> resolveBoolean(keys, loader))
                .orElseGet(() -> Boolean.TRUE.equals(loader.get()));
    }

    /**
     * 使用当前规则 generation 缓存规则集合。
     *
     * @param loader 数据库回源函数
     * @param segments 不含敏感原文的业务 Key 维度
     * @return 不可变缓存列表或数据库加载结果
     */
    public List<RiskListMatch> findMatches(Supplier<List<RiskListMatch>> loader,
                                           String... segments) {
        Optional<RuleCacheKeys> cacheKeys = currentRuleCacheKeys(segments);
        return cacheKeys.map(keys -> findMatches(keys, loader))
                .orElseGet(() -> immutableList(loader.get()));
    }

    /**
     * 按读 Key 优先级查询单规则缓存，全部未缓存时仅回源一次。
     *
     * @param cacheKeys 当前 generation 的读写 Key
     * @param loader 数据库回源函数
     * @return 缓存结果或数据库加载结果
     */
    private Optional<RiskListMatch> findMatch(RuleCacheKeys cacheKeys,
                                              Supplier<Optional<RiskListMatch>> loader) {
        for (String readKey : cacheKeys.readKeys()) {
            CachedMatchLookup cached = readMatch(readKey);
            if (cached.cached()) {
                writeMatch(backfillTargets(cacheKeys, readKey), cached.match());
                return cached.match();
            }
        }
        Optional<RiskListMatch> loaded = loader.get();
        writeMatch(cacheKeys.writeKeys(), loaded);
        return loaded;
    }

    /**
     * 读取当前 generation 并构造隔离环境的规则缓存 Key。
     *
     * @param segments 不含敏感原文的业务 Key 维度
     * @return 可读 generation 对应的 Key；基础设施不可用时返回空
     */
    private Optional<RuleCacheKeys> currentRuleCacheKeys(String... segments) {
        if (cacheGenerationStore == null || redisProperties == null) {
            return Optional.empty();
        }
        try {
            RedisCacheGenerationState state = cacheGenerationStore.current(RULE_CACHE_NAMESPACE);
            if (!state.cacheReadable() || !StringUtils.hasText(state.generation())) {
                return Optional.empty();
            }
            String[] keySegments = new String[segments.length + 1];
            keySegments[0] = state.generation();
            System.arraycopy(segments, 0, keySegments, 1, segments.length);
            String cacheKey = redisProperties.businessKey("risk", "runtime-rule", keySegments);
            return Optional.of(new RuleCacheKeys(List.of(cacheKey), List.of(cacheKey)));
        } catch (RuntimeException exception) {
            log.warn("风控规则缓存代际读取失败, event: RISK_RULE_CACHE_GENERATION_FAILED, exceptionType: {}",
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 计算缓存命中后需要回填的写 Key，不刷新本次命中 Key 的 TTL。
     *
     * @param cacheKeys 当前 generation 的读写 Key
     * @param sourceKey 本次命中的物理 Key
     * @return 排除命中源后的回填 Key
     */
    private List<String> backfillTargets(RuleCacheKeys cacheKeys, String sourceKey) {
        return cacheKeys.writeKeys().stream()
                .filter(key -> !Objects.equals(key, sourceKey))
                .toList();
    }

    /**
     * 读取单规则缓存并区分未缓存和已缓存空命中。
     *
     * @param cacheKey 目标物理 Key
     * @return 带缓存状态的单规则结果
     */
    private CachedMatchLookup readMatch(String cacheKey) {
        if (redisStringService == null) {
            return CachedMatchLookup.notCached();
        }
        try {
            Object cached = redisStringService.get(cacheKey);
            if (cached instanceof RiskListMatch match) {
                return CachedMatchLookup.cached(Optional.of(match));
            }
            if (cached instanceof String text && StringUtils.hasText(text)) {
                RiskRuntimeCacheEntry entry = JsonUtils.parseObject(text, RiskRuntimeCacheEntry.class);
                if (entry != null) {
                    return CachedMatchLookup.cached(entry.isFound()
                            ? Optional.ofNullable(entry.getMatch())
                            : Optional.empty());
                }
            }
        } catch (RuntimeException exception) {
            log.warn("风控运行时缓存读取失败, event: RISK_RUNTIME_CACHE_READ_FAILED, cacheKeyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(cacheKey), exception.getClass().getSimpleName());
        }
        return CachedMatchLookup.notCached();
    }

    /**
     * 向全部目标 Key 写入单规则缓存结果。
     *
     * @param cacheKeys 有序写 Key
     * @param match 规则命中或已确认的空命中
     */
    private void writeMatch(List<String> cacheKeys, Optional<RiskListMatch> match) {
        for (String cacheKey : cacheKeys) {
            writeMatch(cacheKey, match);
        }
    }

    /**
     * 写入单规则命中或空命中包装对象。
     *
     * @param cacheKey 目标物理 Key
     * @param match 规则命中或已确认的空命中
     */
    private void writeMatch(String cacheKey, Optional<RiskListMatch> match) {
        if (redisStringService == null || properties == null) {
            return;
        }
        try {
            RiskRuntimeCacheEntry entry = match.isPresent()
                    ? RiskRuntimeCacheEntry.match(match.get())
                    : RiskRuntimeCacheEntry.match(null);
            long ttlSeconds = match.isPresent()
                    ? properties.getCacheHitTtlSeconds()
                    : properties.getCacheMissTtlSeconds();
            redisStringService.set(
                    cacheKey,
                    JsonUtils.toJsonString(entry),
                    Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException exception) {
            log.warn("风控运行时缓存写入失败, event: RISK_RUNTIME_CACHE_WRITE_FAILED, cacheKeyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(cacheKey), exception.getClass().getSimpleName());
        }
    }

    /**
     * 按读 Key 优先级查询布尔缓存，全部未缓存时仅回源一次。
     *
     * @param cacheKeys 当前 generation 的读写 Key
     * @param loader 数据库回源函数
     * @return 缓存值或数据库加载结果
     */
    private boolean resolveBoolean(RuleCacheKeys cacheKeys, Supplier<Boolean> loader) {
        for (String readKey : cacheKeys.readKeys()) {
            Optional<Boolean> cached = readBoolean(readKey);
            if (cached.isPresent()) {
                writeBoolean(backfillTargets(cacheKeys, readKey), cached.get());
                return cached.get();
            }
        }
        boolean loaded = Boolean.TRUE.equals(loader.get());
        writeBoolean(cacheKeys.writeKeys(), loaded);
        return loaded;
    }

    /**
     * 读取布尔规则缓存。
     *
     * @param cacheKey 目标物理 Key
     * @return 已缓存布尔值；未缓存或读取失败时为空
     */
    private Optional<Boolean> readBoolean(String cacheKey) {
        if (redisStringService == null) {
            return Optional.empty();
        }
        try {
            Object cached = redisStringService.get(cacheKey);
            if (cached instanceof String text && StringUtils.hasText(text)) {
                return Optional.of(Boolean.parseBoolean(text));
            }
            if (cached instanceof Boolean value) {
                return Optional.of(value);
            }
        } catch (RuntimeException exception) {
            log.warn("风控运行时缓存读取失败, event: RISK_RUNTIME_CACHE_READ_FAILED, cacheKeyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(cacheKey), exception.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    /**
     * 向全部目标 Key 写入布尔规则缓存。
     *
     * @param cacheKeys 有序写 Key
     * @param value 布尔规则结果
     */
    private void writeBoolean(List<String> cacheKeys, boolean value) {
        for (String cacheKey : cacheKeys) {
            writeBoolean(cacheKey, value);
        }
    }

    /**
     * 写入布尔规则缓存，false 使用较短的未命中 TTL。
     *
     * @param cacheKey 目标物理 Key
     * @param value 布尔规则结果
     */
    private void writeBoolean(String cacheKey, boolean value) {
        if (redisStringService == null || properties == null) {
            return;
        }
        try {
            long ttlSeconds = value
                    ? properties.getCacheHitTtlSeconds()
                    : properties.getCacheMissTtlSeconds();
            redisStringService.set(cacheKey, String.valueOf(value), Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException exception) {
            log.warn("风控运行时缓存写入失败, event: RISK_RUNTIME_CACHE_WRITE_FAILED, cacheKeyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(cacheKey), exception.getClass().getSimpleName());
        }
    }

    /**
     * 按读 Key 优先级查询规则列表，全部未缓存时仅回源一次。
     *
     * @param cacheKeys 当前 generation 的读写 Key
     * @param loader 数据库回源函数
     * @return 不可变缓存列表或数据库加载结果
     */
    private List<RiskListMatch> findMatches(RuleCacheKeys cacheKeys,
                                            Supplier<List<RiskListMatch>> loader) {
        for (String readKey : cacheKeys.readKeys()) {
            CachedListLookup cached = readList(readKey);
            if (cached.cached()) {
                writeList(backfillTargets(cacheKeys, readKey), cached.matches());
                return cached.matches();
            }
        }
        List<RiskListMatch> loaded = immutableList(loader.get());
        writeList(cacheKeys.writeKeys(), loaded);
        return loaded;
    }

    /**
     * 读取规则列表缓存并区分未缓存和已缓存空列表。
     *
     * @param cacheKey 目标物理 Key
     * @return 带缓存状态的不可变规则列表
     */
    private CachedListLookup readList(String cacheKey) {
        if (redisStringService == null) {
            return CachedListLookup.notCached();
        }
        try {
            Object cached = redisStringService.get(cacheKey);
            if (cached instanceof String text && StringUtils.hasText(text)) {
                RiskRuntimeCacheEntry entry = JsonUtils.parseObject(text, RiskRuntimeCacheEntry.class);
                if (entry != null) {
                    return CachedListLookup.cached(entry.isFound()
                            ? immutableList(entry.getMatches())
                            : List.of());
                }
            }
        } catch (RuntimeException exception) {
            log.warn("风控运行时缓存读取失败, event: RISK_RUNTIME_CACHE_READ_FAILED, cacheKeyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(cacheKey), exception.getClass().getSimpleName());
        }
        return CachedListLookup.notCached();
    }

    /**
     * 向全部目标 Key 写入规则列表缓存。
     *
     * @param cacheKeys 有序写 Key
     * @param matches 不可变规则列表
     */
    private void writeList(List<String> cacheKeys, List<RiskListMatch> matches) {
        for (String cacheKey : cacheKeys) {
            writeList(cacheKey, matches);
        }
    }

    /**
     * 写入规则列表缓存，空列表使用较短的未命中 TTL。
     *
     * @param cacheKey 目标物理 Key
     * @param matches 不可变规则列表
     */
    private void writeList(String cacheKey, List<RiskListMatch> matches) {
        if (redisStringService == null || properties == null) {
            return;
        }
        try {
            long ttlSeconds = matches.isEmpty()
                    ? properties.getCacheMissTtlSeconds()
                    : properties.getCacheHitTtlSeconds();
            redisStringService.set(
                    cacheKey,
                    JsonUtils.toJsonString(RiskRuntimeCacheEntry.matches(matches)),
                    Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException exception) {
            log.warn("风控运行时缓存写入失败, event: RISK_RUNTIME_CACHE_WRITE_FAILED, cacheKeyDigest: {}, exceptionType: {}",
                    RedisKeyDigest.sha256(cacheKey), exception.getClass().getSimpleName());
        }
    }

    /**
     * 将空值和空集合统一为不可变空列表。
     *
     * @param matches 数据库或缓存返回的规则集合
     * @return 不可变规则列表
     */
    private List<RiskListMatch> immutableList(List<RiskListMatch> matches) {
        return matches == null || matches.isEmpty() ? List.of() : List.copyOf(matches);
    }

    /**
     * 保存单次规则缓存访问的有序读写 Key。
     *
     * @param readKeys 按优先级排列的读 Key
     * @param writeKeys 按回填顺序排列的写 Key
     */
    private record RuleCacheKeys(List<String> readKeys, List<String> writeKeys) {

        /**
         * 复制 Key 集合并拒绝无读写目标的错误配置。
         *
         * @param readKeys 按优先级排列的读 Key
         * @param writeKeys 按回填顺序排列的写 Key
         */
        private RuleCacheKeys {
            readKeys = readKeys == null ? List.of() : List.copyOf(readKeys);
            writeKeys = writeKeys == null ? List.of() : List.copyOf(writeKeys);
            if (readKeys.isEmpty() || writeKeys.isEmpty()) {
                throw new IllegalArgumentException("Risk rule cache requires read and write keys");
            }
        }
    }

    /**
     * 区分单规则未缓存和已缓存空命中。
     *
     * @param cached Redis 是否提供了确定结果
     * @param match 规则命中；已缓存空命中时为空
     */
    private record CachedMatchLookup(boolean cached, Optional<RiskListMatch> match) {

        /**
         * 创建未缓存状态。
         *
         * @return 允许调用方继续回源的状态
         */
        private static CachedMatchLookup notCached() {
            return new CachedMatchLookup(false, Optional.empty());
        }

        /**
         * 创建已缓存状态。
         *
         * @param match 规则命中或已确认的空命中
         * @return 不再回源数据库的缓存状态
         */
        private static CachedMatchLookup cached(Optional<RiskListMatch> match) {
            return new CachedMatchLookup(true, match == null ? Optional.empty() : match);
        }
    }

    /**
     * 区分规则列表未缓存和已缓存空列表。
     *
     * @param cached Redis 是否提供了确定结果
     * @param matches 不可变规则列表
     */
    private record CachedListLookup(boolean cached, List<RiskListMatch> matches) {

        /**
         * 创建未缓存状态。
         *
         * @return 允许调用方继续回源的状态
         */
        private static CachedListLookup notCached() {
            return new CachedListLookup(false, List.of());
        }

        /**
         * 创建已缓存状态。
         *
         * @param matches 规则列表或已确认的空列表
         * @return 不再回源数据库的缓存状态
         */
        private static CachedListLookup cached(List<RiskListMatch> matches) {
            return new CachedListLookup(
                    true,
                    matches == null || matches.isEmpty() ? List.of() : List.copyOf(matches));
        }
    }
}
