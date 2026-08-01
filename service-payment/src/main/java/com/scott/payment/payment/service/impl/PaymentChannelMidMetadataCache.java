package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.entity.ChannelMidConfigDO;
import com.scott.payment.payment.mapper.PaymentChannelMidConfigMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelMidMetadataCache
 * @date : 2026-08-01 15:40
 * @email : scott_x@163.com
 * @description : 支付实例内短时渠道 MID 敏感元数据缓存，以 merchant:route 中的 MID 修改时间隔离版本
 * @status : create
 *
 * <p>{@code metadata_value_json} 可能包含渠道密码、API Key、证书或私钥，因此只允许从 MASTER
 * 加载到当前 JVM，最长保留两分钟，绝不写入 Redis 或业务日志。</p>
 */
@Service
public class PaymentChannelMidMetadataCache {

    /** 单条敏感元数据在进程内的最长驻留时间。 */
    private static final long TTL_NANOS = Duration.ofMinutes(2).toNanos();

    /** 单实例最多缓存的 MID 元数据版本数量。 */
    private static final int MAXIMUM_SIZE = 2048;

    /** 渠道 MID 主库 Mapper。 */
    private final PaymentChannelMidConfigMapper midConfigMapper;

    /** 版本维度的本地敏感元数据条目。 */
    private final ConcurrentHashMap<CacheKey, CacheEntry> entries = new ConcurrentHashMap<>();

    /** 最近访问顺序，用于容量淘汰。 */
    private final AtomicLong accessSequence = new AtomicLong();

    /** 容量整理互斥对象，不包裹数据库查询。 */
    private final Object evictionMonitor = new Object();

    /**
     * 创建渠道 MID 元数据本地缓存。
     *
     * @param midConfigMapper 渠道 MID 主库 Mapper
     */
    public PaymentChannelMidMetadataCache(PaymentChannelMidConfigMapper midConfigMapper) {
        this.midConfigMapper = midConfigMapper;
    }

    /**
     * 按 MID 记录主键和修改时间获取当前敏感元数据 JSON。
     *
     * @param midConfigId MID 配置主键
     * @param modifiedTime 路由永久快照中的 MID 修改时间
     * @return 敏感元数据 JSON；数据库字段为空时返回 null
     */
    @DS(DataSourceName.MASTER)
    public String getMetadataJson(Long midConfigId, LocalDateTime modifiedTime) {
        if (midConfigId == null) {
            return null;
        }
        // 旧记录缺少更新时间时不缓存，避免无法区分管理员刚轮换的渠道凭据。
        if (modifiedTime == null) {
            return load(midConfigId);
        }
        CacheKey key = new CacheKey(midConfigId, modifiedTime);
        long now = System.nanoTime();
        CacheEntry cached = entries.get(key);
        if (cached != null && !cached.expired(now)) {
            cached.touch(accessSequence.incrementAndGet());
            return cached.metadataJson();
        }
        CacheEntry loaded = entries.compute(key, (ignored, current) -> {
            long currentTime = System.nanoTime();
            if (current != null && !current.expired(currentTime)) {
                current.touch(accessSequence.incrementAndGet());
                return current;
            }
            return new CacheEntry(load(midConfigId), currentTime + TTL_NANOS,
                    accessSequence.incrementAndGet());
        });
        entries.keySet().removeIf(existing -> existing.midConfigId().equals(midConfigId) && !existing.equals(key));
        evictOverflow();
        return loaded.metadataJson();
    }

    /** 从主库读取敏感元数据正文。 */
    private String load(Long midConfigId) {
        ChannelMidConfigDO row = midConfigMapper.selectById(midConfigId);
        return row == null ? null : row.getMetadataValueJson();
    }

    /** 超出容量时按最近访问顺序淘汰最旧条目。 */
    private void evictOverflow() {
        if (entries.size() <= MAXIMUM_SIZE) {
            return;
        }
        synchronized (evictionMonitor) {
            while (entries.size() > MAXIMUM_SIZE) {
                entries.entrySet().stream()
                        .min(Comparator.comparingLong(entry -> entry.getValue().lastAccessSequence()))
                        .map(java.util.Map.Entry::getKey)
                        .ifPresent(entries::remove);
            }
        }
    }

    /** 本地缓存键，只包含 MID 主键和非敏感修改时间。 */
    private record CacheKey(Long midConfigId, LocalDateTime modifiedTime) {
        private CacheKey {
            Objects.requireNonNull(midConfigId, "midConfigId");
            Objects.requireNonNull(modifiedTime, "modifiedTime");
        }
    }

    /** 本地敏感元数据条目。 */
    private static final class CacheEntry {

        /** 渠道敏感元数据 JSON，只驻留当前 JVM。 */
        private final String metadataJson;

        /** 基于单调时钟的过期时间。 */
        private final long expiresAtNanos;

        /** 最近访问顺序。 */
        private volatile long lastAccessSequence;

        /**
         * 创建渠道敏感元数据本地缓存条目。
         *
         * @param metadataJson 渠道敏感元数据 JSON，只允许驻留 JVM
         * @param expiresAtNanos 基于单调时钟的到期点，单位纳秒
         * @param lastAccessSequence 初始访问顺序
         */
        private CacheEntry(String metadataJson, long expiresAtNanos, long lastAccessSequence) {
            this.metadataJson = metadataJson;
            this.expiresAtNanos = expiresAtNanos;
            this.lastAccessSequence = lastAccessSequence;
        }

        /** 返回只驻留 JVM 的渠道敏感元数据 JSON。 */
        private String metadataJson() {
            return metadataJson;
        }

        /** 判断条目是否达到单调时钟到期点。 */
        private boolean expired(long now) {
            return now >= expiresAtNanos;
        }

        /** 返回最近访问顺序，用于容量淘汰排序。 */
        private long lastAccessSequence() {
            return lastAccessSequence;
        }

        /** 使用新的全局访问顺序标记当前条目刚被读取。 */
        private void touch(long sequence) {
            this.lastAccessSequence = sequence;
        }
    }
}
