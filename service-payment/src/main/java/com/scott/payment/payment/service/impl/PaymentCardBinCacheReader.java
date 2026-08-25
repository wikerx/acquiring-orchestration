package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.payment.entity.PaymentCardBinRangeDO;
import com.scott.payment.payment.mapper.PaymentCardBinRangeMapper;
import com.scott.payment.payment.model.PaymentCardBinCacheEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

/** 使用 Spring Cache 按 11 位卡号前缀分别保存有限期 BIN 命中与未命中结果。 */
@Service
public class PaymentCardBinCacheReader {

    private static final int MIN_BIN_LENGTH = 6;
    private static final int MAX_BIN_LENGTH = 11;
    static final String CACHE_NAMESPACE = "card-bin-range";

    private final PaymentCardBinRangeMapper cardBinRangeMapper;
    private final CacheManager cacheManager;
    private final Optional<RedisCacheGenerationStore> generationStore;

    @Autowired
    public PaymentCardBinCacheReader(PaymentCardBinRangeMapper cardBinRangeMapper,
                                     CacheManager cacheManager,
                                     Optional<RedisCacheGenerationStore> generationStore) {
        this.cardBinRangeMapper = cardBinRangeMapper;
        this.cacheManager = cacheManager;
        this.generationStore = generationStore;
    }

    /**
     * 按当前全局 generation 读取 Card BIN；generation 发布中或 Redis 异常时直接回源主库。
     *
     * @param cardBinPrefix 十一位 BIN 查询前缀
     * @return 当前时间有效的命中或未命中条目
     */
    @DS(DataSourceName.MASTER)
    public PaymentCardBinCacheEntry findByPrefix(String cardBinPrefix) {
        String generation = currentGeneration();
        if (generation == null) {
            return loadFromDatabase(cardBinPrefix);
        }
        String cacheKey = generation + ":" + cardBinPrefix;
        PaymentCardBinCacheEntry cached = readCache(cacheKey);
        LocalDateTime now = LocalDateTime.now();
        if (cached != null && cached.usableAt(now)) {
            return cached;
        }
        if (cached != null) {
            evict(cacheKey, cached);
        }
        PaymentCardBinCacheEntry loaded = loadFromDatabase(cardBinPrefix);
        writeCache(cacheKey, loaded);
        return loaded;
    }

    private PaymentCardBinCacheEntry loadFromDatabase(String cardBinPrefix) {
        long numericValue = Long.parseLong(cardBinPrefix);
        List<Long> candidates = candidateStarts(cardBinPrefix);
        PaymentCardBinRangeDO matched = cardBinRangeMapper.selectBestMatch(
                candidates, numericValue);
        if (matched == null) {
            PaymentCardBinCacheEntry miss = PaymentCardBinCacheEntry.miss(cardBinPrefix);
            miss.setNextEffectiveTime(cardBinRangeMapper.selectNextEffectiveTime(candidates, numericValue));
            return miss;
        }
        PaymentCardBinCacheEntry entry = new PaymentCardBinCacheEntry();
        entry.setCardBinPrefix(cardBinPrefix);
        entry.setMatched(Boolean.TRUE);
        entry.setRangeId(matched.getId());
        entry.setBinLength(matched.getBinLength());
        entry.setCardBrand(matched.getCardBrand());
        entry.setIssuerCountryAlpha2(matched.getIssuerCountryAlpha2());
        entry.setIssuerCountryAlpha3(matched.getIssuerCountryAlpha3());
        entry.setIssuerCountryName(matched.getIssuerCountryName());
        entry.setEffectiveTime(matched.getEffectiveTime());
        entry.setExpireTime(matched.getExpireTime());
        return entry;
    }

    private String currentGeneration() {
        if (generationStore.isEmpty()) {
            return null;
        }
        try {
            RedisCacheGenerationState state = generationStore.get().current(CACHE_NAMESPACE);
            return state.cacheReadable() ? state.generation() : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private PaymentCardBinCacheEntry readCache(String cacheKey) {
        try {
            PaymentCardBinCacheEntry hit = get(PaymentCacheNames.CARD_BIN, cacheKey);
            return hit == null ? get(PaymentCacheNames.CARD_BIN_MISS, cacheKey) : hit;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private PaymentCardBinCacheEntry get(String cacheName, String cacheKey) {
        Cache cache = cacheManager.getCache(cacheName);
        return cache == null ? null : cache.get(cacheKey, PaymentCardBinCacheEntry.class);
    }

    private void writeCache(String cacheKey, PaymentCardBinCacheEntry entry) {
        try {
            String cacheName = Boolean.TRUE.equals(entry.getMatched())
                    ? PaymentCacheNames.CARD_BIN : PaymentCacheNames.CARD_BIN_MISS;
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(cacheKey, entry);
            }
        } catch (RuntimeException ignored) {
            // Card BIN 缓存失败只影响性能，交易继续使用本次主库结果。
        }
    }

    private void evict(String cacheKey, PaymentCardBinCacheEntry entry) {
        try {
            String cacheName = Boolean.TRUE.equals(entry.getMatched())
                    ? PaymentCacheNames.CARD_BIN : PaymentCacheNames.CARD_BIN_MISS;
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(cacheKey);
            }
        } catch (RuntimeException ignored) {
            // 时间边界已在读路径 fail-safe，删除失败不会继续返回过期条目。
        }
    }

    private List<Long> candidateStarts(String cardBinPrefix) {
        List<Long> candidates = new ArrayList<>(MAX_BIN_LENGTH - MIN_BIN_LENGTH + 1);
        for (int binLength = MIN_BIN_LENGTH; binLength <= MAX_BIN_LENGTH; binLength++) {
            String candidate = cardBinPrefix.substring(0, binLength)
                    + "0".repeat(MAX_BIN_LENGTH - binLength);
            candidates.add(Long.parseLong(candidate));
        }
        return candidates;
    }
}
