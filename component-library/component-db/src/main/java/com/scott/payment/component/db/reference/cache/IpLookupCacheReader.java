package com.scott.payment.component.db.reference.cache;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.db.reference.model.IpLookupCacheEntry;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupSnapshot;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLookupCacheReader
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 跨服务共享的有界 IP 精确查询缓存，单次 Lua 读取命中/未命中并在 Redis 服务端维护逻辑过期
 * @status : create
 */
@Slf4j
@Service
public class IpLookupCacheReader {

    private static final String SLOT_IDENTITY = "ip-exact-lookup";
    private static final int CLEANUP_BATCH_SIZE = 128;

    private final ReferenceDataLookupService lookupService;
    private final IpShardRoutingSnapshot routingSnapshot;
    private final StringRedisTemplate redisTemplate;
    private final PaymentRedisProperties redisProperties;
    private final Duration hitTtl;
    private final Duration missTtl;
    private final Duration namespaceTtl;
    private final int maxEntries;
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
            @Value("${payment.ip.lookup.miss-ttl:PT2M}") Duration missTtl,
            @Value("${payment.ip.lookup.namespace-ttl:PT24H}") Duration namespaceTtl,
            @Value("${payment.ip.lookup.max-entries:500000}") int maxEntries) {
        if (hitTtl == null || hitTtl.isZero() || hitTtl.isNegative()
                || missTtl == null || missTtl.isZero() || missTtl.isNegative()
                || namespaceTtl == null || namespaceTtl.compareTo(hitTtl) <= 0
                || namespaceTtl.compareTo(missTtl) <= 0
                || maxEntries <= 0) {
            throw new IllegalArgumentException("IP lookup cache limits are invalid");
        }
        this.lookupService = lookupService;
        this.routingSnapshot = routingSnapshot;
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        this.hitTtl = hitTtl;
        this.missTtl = missTtl;
        this.namespaceTtl = namespaceTtl;
        this.maxEntries = maxEntries;
    }

    /**
     * 查询精确 IP 归属。
     *
     * <p>Redis Hash 同时保存命中和未命中，ZSet 保存逻辑过期时间；Lua 使用 Redis TIME 完成
     * 清理和读取，因此一次请求只做一次缓存网络往返。Key 按不可变路由版本隔离并设置物理过期，
     * 旧版本无需全量删除即可自然回收；缓存故障时使用同一路由上下文执行单次 SLAVE 数据表查询。</p>
     *
     * @param ipAddress IPv4 或 IPv6 字面量
     * @return 命中或合法未命中的 IP 归属结果
     */
    public IpLookupResult lookup(String ipAddress) {
        String normalizedIp = IpAddressNormalizer.normalizeExact(ipAddress).ipValue();
        String digest = digest(normalizedIp);
        String routingVersion = routingSnapshot.currentVersion();
        try {
            String payload = redisTemplate.execute(
                    PaymentRedisScripts.ipLookupReadV1(),
                    List.of(valueKey(routingVersion), expiryKey(routingVersion)),
                    digest,
                    String.valueOf(CLEANUP_BATCH_SIZE)
            );
            IpLookupCacheEntry cached = JsonUtils.parseObject(payload, IpLookupCacheEntry.class);
            if (cached != null && routingVersion.equals(cached.routingVersion())
                    && cached.result() != null
                    && normalizedIp.equals(cached.result().ipAddress())) {
                return cached.result();
            }
        } catch (RuntimeException exception) {
            warn("READ", exception);
        }

        IpLookupSnapshot snapshot = lookupService.lookupIpSnapshot(normalizedIp);
        IpLookupResult result = snapshot.result();
        Duration ttl = Boolean.TRUE.equals(result.matched()) ? hitTtl : missTtl;
        try {
            redisTemplate.execute(
                    PaymentRedisScripts.ipLookupWriteV1(),
                    List.of(valueKey(snapshot.routingVersion()), expiryKey(snapshot.routingVersion())),
                    digest,
                    JsonUtils.toJsonString(new IpLookupCacheEntry(snapshot.routingVersion(), result)),
                    String.valueOf(ttl.toMillis()),
                    String.valueOf(maxEntries),
                    String.valueOf(CLEANUP_BATCH_SIZE),
                    String.valueOf(namespaceTtl.toMillis())
            );
        } catch (RuntimeException exception) {
            warn("WRITE", exception);
        }
        return result;
    }

    private String valueKey(String routingVersion) {
        return redisProperties.coLocatedBusinessKey(
                "ip", "lookup", slotIdentity(routingVersion), "values");
    }

    private String expiryKey(String routingVersion) {
        return redisProperties.coLocatedBusinessKey(
                "ip", "lookup", slotIdentity(routingVersion), "expiry");
    }

    private String slotIdentity(String routingVersion) {
        if (routingVersion == null || routingVersion.isBlank()) {
            throw new IllegalStateException("IP routing version is unavailable");
        }
        return SLOT_IDENTITY + ':' + routingVersion;
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
