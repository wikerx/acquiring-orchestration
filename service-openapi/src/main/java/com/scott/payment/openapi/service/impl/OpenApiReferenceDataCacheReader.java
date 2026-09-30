package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.db.reference.model.CardBrandRuleMatcher;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.cache.CardBinCacheReader;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiReferenceDataCacheReader
 * @date : 2026-09-30
 * @email : scott_x@163.com
 * @description : 商户 IP 查询缓存与 BIN 结果适配；BIN 使用支付共享的 11 位缓存、从库回源和品牌兜底规则。
 * @status : create
 */
@Slf4j
@Service
public class OpenApiReferenceDataCacheReader {

    private static final Duration IP_HIT_TTL = Duration.ofSeconds(30);
    private static final Duration IP_MISS_TTL = Duration.ofSeconds(10);

    private final ReferenceDataLookupService lookupService;
    private final CardBinCacheReader cardBinCacheReader;
    private final CacheManager cacheManager;
    private final AtomicLong lastCacheWarningMillis = new AtomicLong();

    public OpenApiReferenceDataCacheReader(ReferenceDataLookupService lookupService,
                                           CardBinCacheReader cardBinCacheReader,
                                           CacheManager cacheManager) {
        this.lookupService = lookupService;
        this.cardBinCacheReader = cardBinCacheReader;
        this.cacheManager = cacheManager;
    }

    /**
     * 查询精确 IP 归属，并缓存有效命中与合法未命中。
     * <p>等价的 IP 写法共用一个缓存键；Redis 键只保存规范化地址的摘要，不暴露原始 IP。
     * IP 库目前没有版本发布时的缓存失效通知，因此命中最多复用 30 秒、未命中最多复用 10 秒。
     * 缓存不可用时回源从库；非法地址、分片配置错误和数据库异常均不写入缓存。</p>
     *
     * @param ipAddress IPv4 或 IPv6 字面量，不接受域名和 CIDR
     * @return 当前有效的归属结果，合法地址未命中时返回 matched=false
     * @throws IllegalArgumentException IP 字面量不合法时抛出
     */
    public IpLookupResult lookupIp(String ipAddress) {
        // 先规范化再生成键，避免同一地址的不同文本写法产生不同缓存条目。
        String normalizedIp = IpAddressNormalizer.normalizeExact(ipAddress).ipValue();
        String key = digest(normalizedIp);
        LocalDateTime now = LocalDateTime.now();
        // read 会校验条目的业务有效期；命中后再确认结果确实属于当前 IP。
        OpenApiReferenceDataCacheEntry cached = read(key, now,
                PaymentCacheNames.OPENAPI_IP_LOOKUP, PaymentCacheNames.OPENAPI_IP_LOOKUP_MISS);
        if (cached != null && cached.ipResult() != null
                && normalizedIp.equals(cached.ipResult().ipAddress())) {
            return cached.ipResult();
        }
        // 只缓存正常查询得到的结果；查询抛出的配置或数据访问异常直接上抛。
        IpLookupResult result = lookupService.lookupIp(normalizedIp);
        String cacheName = Boolean.TRUE.equals(result.matched())
                ? PaymentCacheNames.OPENAPI_IP_LOOKUP : PaymentCacheNames.OPENAPI_IP_LOOKUP_MISS;
        Duration ttl = Boolean.TRUE.equals(result.matched()) ? IP_HIT_TTL : IP_MISS_TTL;
        // validUntil 是独立于 Redis 物理 TTL 的上限，避免配置延长 TTL 后复用过期结果。
        write(cacheName, key, new OpenApiReferenceDataCacheEntry(result, now.plus(ttl)));
        return result;
    }

    /**
     * 与支付侧共用 11 位 BIN 查询策略和缓存：短 BIN 右补零、长输入截取前 11 位。
     * <p>缓存只保存数据库真实命中。数据库未命中后，使用支付侧公开 IIN 规则
     * 补充品牌，但 matched 仍为 false，发卡行和国家等归属字段仍为空。
     * 超过 11 位的原始输入不进入缓存或响应。数据库异常继续上抛，不伪装成正常未命中。</p>
     *
     * @param cardBin 至少 6 位纯数字，可能包含完整卡号；不得记录原文
     * @return 当前有效归属结果，未命中时返回 matched=false 和规则识别的品牌
     * @throws IllegalArgumentException BIN 不足 6 位或含非数字字符时抛出
     */
    public CardBinLookupResult lookupCardBin(String cardBin) {
        String prefix = CardBinCacheReader.toElevenDigitPrefix(cardBin);
        CardBinLookupResult databaseResult = cardBinCacheReader.findByPrefix(prefix).result();
        // 品牌规则只补充本次商户响应，不能污染共享缓存中的数据库命中状态。
        String brand = Boolean.TRUE.equals(databaseResult.matched())
                ? databaseResult.cardBrand() : CardBrandRuleMatcher.resolve(prefix);
        String responseBin = cardBin.length() <= 11 ? cardBin : prefix;
        return new CardBinLookupResult(databaseResult.matched(), responseBin,
                databaseResult.binLength(), brand, databaseResult.cardSubBrand(),
                databaseResult.cardType(), databaseResult.cardLevel(),
                databaseResult.issuerCountryName(), databaseResult.issuerCountryAlpha2(),
                databaseResult.issuerCountryAlpha3(), databaseResult.issuerCountryNumeric(),
                databaseResult.issuerBank());
    }

    private OpenApiReferenceDataCacheEntry read(String key, LocalDateTime now,
                                                String hitName, String missName) {
        for (String name : new String[]{hitName, missName}) {
            try {
                Cache cache = cacheManager.getCache(name);
                if (cache == null) {
                    continue;
                }
                OpenApiReferenceDataCacheEntry entry = cache.get(key, OpenApiReferenceDataCacheEntry.class);
                if (entry != null && entry.usableAt(now)) {
                    return entry;
                }
                if (entry != null) {
                    cache.evict(key);
                }
            } catch (RuntimeException exception) {
                warnCacheFailure("READ", name, exception);
            }
        }
        return null;
    }

    private void write(String cacheName, String key, OpenApiReferenceDataCacheEntry entry) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(key, entry);
            }
        } catch (RuntimeException exception) {
            warnCacheFailure("WRITE", cacheName, exception);
        }
    }

    private void warnCacheFailure(String operation, String cacheName, RuntimeException exception) {
        long now = System.currentTimeMillis();
        long previous = lastCacheWarningMillis.get();
        if (now - previous >= Duration.ofMinutes(1).toMillis()
                && lastCacheWarningMillis.compareAndSet(previous, now)) {
            log.warn("event: OPENAPI_REFERENCE_CACHE_FALLBACK operation: {} cacheName: {} exceptionType: {}",
                    operation, cacheName, exception.getClass().getSimpleName(), exception);
        }
    }

    private String digest(String value) {
        try {
            // IP 缓存键不暴露查询原文；BIN 使用支付业务的共享键。
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
