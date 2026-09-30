package com.scott.payment.component.db.reference.cache;

import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.reference.model.CardBinLookupCacheEntry;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinCacheReader
 * @date : 2026-09-30
 * @email : scott_x@163.com
 * @description : 支付与 OpenAPI 共用的 11 位 BIN 缓存读写层；复用管理端发布代际，
 *                委托基础数据服务从库查询，不承担支付卡品牌降级或对外响应转换。
 * @status : create
 */
@Service
@Slf4j
@ConditionalOnClass(name = "com.scott.payment.component.redis.generation.RedisCacheGenerationStore")
public class CardBinCacheReader {

    /** 管理端发布 BIN 区间时切换的代际命名空间；不包含卡号或 BIN 明文。 */
    private static final String CACHE_NAMESPACE = "card-bin-range";

    /** 从库查询入口，提供当前归属与下一次可能改变结果的时间边界；必须装配。 */
    private final ReferenceDataLookupService lookupService;
    /** 仅保存数据库命中结果的 Spring 缓存入口；必须装配，故障时允许回源。 */
    private final CacheManager cacheManager;
    /** 未装配 Redis 代际存储时为空，此时每次查询均回源且不读写旧缓存。 */
    private final Optional<RedisCacheGenerationStore> generationStore;
    /** 缓存故障告警最近输出时刻，毫秒；避免 Redis 故障时按请求量刷屏。 */
    private final AtomicLong lastCacheWarningMillis = new AtomicLong();

    /**
     * 创建共享 BIN 缓存读取器；缓存不可用不影响从库查询。
     *
     * @param lookupService BIN 数据库查询服务，不可为空
     * @param cacheManager Spring 缓存入口，不可为空
     * @param generationStore 管理端发布代际存储；未装配时为 Optional.empty()
     */
    public CardBinCacheReader(ReferenceDataLookupService lookupService,
                              CacheManager cacheManager,
                              Optional<RedisCacheGenerationStore> generationStore) {
        this.lookupService = lookupService;
        this.cacheManager = cacheManager;
        this.generationStore = generationStore;
    }

    /**
     * 按支付业务的 11 位前缀策略读取 BIN。发布中或缓存不可用时直接从库回源，
     * 正向命中使用 11 位短键且不设 Redis TTL，缓存值受 generation、格式和区间时间边界约束；
     * 数据库未命中不写缓存，由支付或 OpenAPI 上层执行品牌规则兜底。
     *
     * @param cardBinPrefix 十一位数字前缀
     * @return 当前时间有效的命中或未命中条目；未命中仅返回本次结果
     * @throws IllegalArgumentException 输入不是十一位纯数字时抛出
     */
    public CardBinLookupCacheEntry findByPrefix(String cardBinPrefix) {
        if (cardBinPrefix == null || !cardBinPrefix.matches("^[0-9]{11}$")) {
            throw new IllegalArgumentException("cardBinPrefix must be 11 digits");
        }
        String generation = currentGeneration();
        if (generation == null) {
            // 管理端发布中或 Redis 异常时不能读取旧代际；本次只信任从库结果，不写缓存。
            return loadFromDatabase(cardBinPrefix, null);
        }
        // 物理 Key 由 CacheManager 拼接环境前缀和 cardBin 缓存名，业务键只保留 11 位 BIN。
        String cacheKey = cardBinPrefix;
        CardBinLookupCacheEntry cached = get(PaymentCacheNames.CARD_BIN, cacheKey);
        LocalDateTime now = LocalDateTime.now();
        // Redis 无物理 TTL；读取时必须检查值内代际、格式和业务时间边界。
        if (cached != null && cached.usableAt(generation, cardBinPrefix, now)) {
            return cached;
        }
        if (cached != null) {
            evict(cacheKey);
        }
        CardBinLookupCacheEntry loaded = loadFromDatabase(cardBinPrefix, generation);
        // 未命中不写缓存；时间边界已到时也不保存，避免永久保存一个不可复用的结果。
        if (loaded.usableAt(generation, cardBinPrefix, LocalDateTime.now())) {
            writeCache(cacheKey, loaded);
        }
        return loaded;
    }

    /**
     * 支付侧统一的查询精度：不足 6 位拒绝，6 至 10 位右补零，超过 11 位只取前 11 位。
     * 调用方不得把原始完整卡号写入日志或缓存。
     *
     * @param cardDigits 纯数字 BIN 或卡号
     * @return 用于共享缓存和数据库查询的 11 位前缀
     * @throws IllegalArgumentException 输入不足 6 位或包含非数字字符时抛出
     */
    public static String toElevenDigitPrefix(String cardDigits) {
        if (cardDigits == null || cardDigits.length() < 6
                || !cardDigits.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("cardBin must contain at least 6 digits");
        }
        return cardDigits.length() >= 11
                ? cardDigits.substring(0, 11)
                : cardDigits + "0".repeat(11 - cardDigits.length());
    }

    /**
     * 由基础数据服务在独立只读事务中从从库取得结果及时间边界。
     */
    private CardBinLookupCacheEntry loadFromDatabase(String cardBinPrefix, String generation) {
        return CardBinLookupCacheEntry.from(
                lookupService.lookupCardBinSnapshot(cardBinPrefix), generation);
    }

    /**
     * 管理端发布期间或 Redis 不可读时返回 null，阻止本次查询使用上一代缓存。
     */
    private String currentGeneration() {
        if (generationStore.isEmpty()) {
            return null;
        }
        try {
            RedisCacheGenerationState state = generationStore.get().current(CACHE_NAMESPACE);
            return state.cacheReadable() ? state.generation() : null;
        } catch (RuntimeException exception) {
            warnCacheFailure("GENERATION_READ", CACHE_NAMESPACE, exception);
            return null;
        }
    }

    /**
     * 缓存不可读时按未命中处理，由调用方回源；数据库异常不在此处吞掉。
     */
    private CardBinLookupCacheEntry get(String cacheName, String cacheKey) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                return null;
            }
            Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper == null) {
                return null;
            }
            if (wrapper.get() instanceof CardBinLookupCacheEntry entry) {
                return entry;
            }
            // 旧短键可能存有支付侧旧格式；不记录值内容，清理后按新格式回源。
            log.debug("event: CARD_BIN_CACHE_INCOMPATIBLE cacheName: {}", cacheName);
            evict(cacheKey);
            return null;
        } catch (RuntimeException exception) {
            warnCacheFailure("READ", cacheName, exception);
            // 反序列化失败的旧值不应永久占据短键；Redis 不可用时删除也只会失败并降级回源。
            evict(cacheKey);
            return null;
        }
    }

    /**
     * 只写正向命中缓存；写入失败不影响本次查询结果。
     */
    private void writeCache(String cacheKey, CardBinLookupCacheEntry entry) {
        try {
            Cache cache = cacheManager.getCache(PaymentCacheNames.CARD_BIN);
            if (cache != null) {
                cache.put(cacheKey, entry);
            }
        } catch (RuntimeException exception) {
            // 缓存故障不阻断支付或 OpenAPI 的本次基础数据查询。
            warnCacheFailure("WRITE", PaymentCacheNames.CARD_BIN, exception);
        }
    }

    /**
     * 删除已失效的短键；即使删除失败，读路径也会拒绝不匹配的条目。
     */
    private void evict(String cacheKey) {
        try {
            Cache cache = cacheManager.getCache(PaymentCacheNames.CARD_BIN);
            if (cache != null) {
                cache.evict(cacheKey);
            }
        } catch (RuntimeException exception) {
            // 读路径已拒绝过期条目；删除失败不会让它重新生效。
            warnCacheFailure("EVICT", PaymentCacheNames.CARD_BIN, exception);
        }
    }

    /**
     * 每分钟最多记录一次缓存故障；保留异常栈和 traceId 以供排障，不输出 BIN 或缓存键。
     */
    private void warnCacheFailure(String operation, String cacheName, RuntimeException exception) {
        long now = System.currentTimeMillis();
        long previous = lastCacheWarningMillis.get();
        if (now - previous >= 60_000 && lastCacheWarningMillis.compareAndSet(previous, now)) {
            // 不记录 BIN 明文或缓存键；traceId 由日志上下文提供。
            log.warn("event: CARD_BIN_CACHE_FALLBACK operation: {} cacheName: {} exceptionType: {}",
                    operation, cacheName, exception.getClass().getSimpleName(), exception);
        }
    }
}
