package com.scott.payment.component.db.reference.cache;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.db.reference.model.IpLookupCacheEntry;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupSnapshot;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLookupCacheReader
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 跨服务共享的 IP 精确查询缓存，每个 IP 使用独立 String Key 和物理 TTL
 * @status : create
 */
@Slf4j
@Service
public class IpLookupCacheReader {

    private final ReferenceDataLookupService lookupService;
    private final IpShardRoutingSnapshot routingSnapshot;
    private final StringRedisTemplate redisTemplate;
    private final PaymentRedisProperties redisProperties;
    private final Duration hitTtl;
    private final Duration missTtl;
    private final AtomicLong lastWarningMillis = new AtomicLong();

    /**
     * 创建共享 IP 查询缓存。
     */
    public IpLookupCacheReader(
            ReferenceDataLookupService lookupService,
            IpShardRoutingSnapshot routingSnapshot,
            StringRedisTemplate redisTemplate,
            PaymentRedisProperties redisProperties,
            @Value("${payment.ip.lookup.hit-ttl:PT6H}") Duration hitTtl,
            @Value("${payment.ip.lookup.miss-ttl:PT2M}") Duration missTtl) {
        if (hitTtl == null || hitTtl.isZero() || hitTtl.isNegative()
                || missTtl == null || missTtl.isZero() || missTtl.isNegative()) {
            throw new IllegalArgumentException("IP lookup cache limits are invalid");
        }
        this.lookupService = lookupService;
        this.routingSnapshot = routingSnapshot;
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        this.hitTtl = hitTtl;
        this.missTtl = missTtl;
    }

    /**
     * 查询精确 IP 归属。
     *
     * <p>命中与未命中都使用独立 String Key 保存 JSON，并分别设置物理 TTL。缓存值携带当前
     * 路由版本，分片或数据版本变化后旧值不会继续复用。Redis 不可用或缓存失效时，使用本地
     * 路由快照定位分表并从 SLAVE 回源。</p>
     *
     * @param ipAddress IPv4 或 IPv6 字面量
     * @return 命中或合法未命中的 IP 归属结果
     */
    public IpLookupResult lookup(String ipAddress) {
        return lookupEntry(ipAddress).result();
    }

    /**
     * 查询精确 IP 的共享缓存详情。
     *
     * <p>OpenAPI 使用 {@link IpLookupCacheEntry#result()}，管理端使用同一缓存值中的区间与审计字段，
     * 两条入口因此保持完全一致的缓存、路由和 SLAVE 回源策略。</p>
     *
     * @param ipAddress IPv4 或 IPv6 字面量
     * @return 包含路由版本、归属结果和管理审计字段的缓存条目
     */
    public IpLookupCacheEntry lookupEntry(String ipAddress) {
        IpAddressNormalizer.NormalizedIp normalized = IpAddressNormalizer.normalizeExact(ipAddress);
        String normalizedIp = normalized.ipValue();
        String key = cacheKey(normalized);
        String routingVersion = routingSnapshot.currentVersion();
        try {
            String payload = redisTemplate.opsForValue().get(key);
            IpLookupCacheEntry cached = JsonUtils.parseObject(payload, IpLookupCacheEntry.class);
            if (isUsable(cached, routingVersion, normalized)) {
                return cached;
            }
        } catch (RuntimeException exception) {
            warn("READ", exception);
        }

        IpLookupSnapshot snapshot = lookupService.lookupIpSnapshot(normalizedIp);
        IpLookupResult result = snapshot.result();
        Duration ttl = Boolean.TRUE.equals(result.matched()) ? hitTtl : missTtl;
        IpLookupCacheEntry entry = new IpLookupCacheEntry(snapshot.routingVersion(), snapshot.detail());
        try {
            redisTemplate.opsForValue().set(key, JsonUtils.toJsonString(entry), ttl);
        } catch (RuntimeException exception) {
            warn("WRITE", exception);
        }
        return entry;
    }

    private String cacheKey(IpAddressNormalizer.NormalizedIp normalizedIp) {
        String ipVersion = normalizedIp.ipv4() ? "v4" : "v6";
        return redisProperties.businessKey(
                "ip", ipVersion, IpAddressNormalizer.toUnsignedNumber(normalizedIp));
    }

    /**
     * 校验缓存值同时满足版本、IP 身份和管理端字段完整性要求。
     *
     * <p>未命中结果不包含区间字段；命中结果必须保留主键、数值区间和数据版本，避免 OpenAPI
     * 可读取但管理端在转换同一缓存值时失败。</p>
     */
    private boolean isUsable(IpLookupCacheEntry entry,
                             String routingVersion,
                             IpAddressNormalizer.NormalizedIp normalizedIp) {
        if (entry == null || entry.detail() == null || entry.result() == null
                || !routingVersion.equals(entry.routingVersion())
                || !normalizedIp.ipValue().equals(entry.result().ipAddress())) {
            return false;
        }
        String expectedType = normalizedIp.ipv4() ? "IPV4" : "IPV6";
        if (!expectedType.equals(entry.result().ipType())) {
            return false;
        }
        if (!Boolean.TRUE.equals(entry.result().matched())) {
            return true;
        }
        return entry.detail().id() != null
                && hasText(entry.detail().ipNumberStart())
                && hasText(entry.detail().ipNumberEnd())
                && hasText(entry.detail().dataVersion());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void warn(String operation, RuntimeException exception) {
        long now = System.currentTimeMillis();
        long previous = lastWarningMillis.get();
        if (now - previous >= 60_000 && lastWarningMillis.compareAndSet(previous, now)) {
            log.warn("event: IP_LOOKUP_CACHE_FALLBACK operation: {} exceptionType: {}",
                    operation, exception.getClass().getSimpleName(), exception);
        }
    }
}
