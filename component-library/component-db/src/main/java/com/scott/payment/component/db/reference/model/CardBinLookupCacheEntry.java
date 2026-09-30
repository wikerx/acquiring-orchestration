package com.scott.payment.component.db.reference.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinLookupCacheEntry
 * @date : 2026-09-30
 * @email : scott_x@163.com
 * @description : 支付与 OpenAPI 共享的正向 BIN 缓存值；物理常驻，按发布代际和区间时间边界判断是否可用。
 * @status : create
 *
 * @param generation 管理端当前可读代际；未装配 Redis 时为空且不缓存
 * @param formatVersion 缓存值格式版本，防止旧支付值被当作完整归属结果
 * @param result 完整查询结果，未命中时也保留 11 位输入但不写缓存
 * @param validUntil 区间失效或更具体区间生效的最早时刻；均不存在时为空
 */
public record CardBinLookupCacheEntry(String generation,
                                      int formatVersion,
                                      CardBinLookupResult result,
                                      LocalDateTime validUntil) implements Serializable {

    private static final long serialVersionUID = 1L;
    /** 缓存值结构版本；旧短键或旧部署写入的值不能作为完整归属结果复用。 */
    public static final int FORMAT_VERSION = 3;

    /**
     * 从数据库快照构造当前代际的完整 BIN 结果；只有命中记录会写入 Redis。
     *
     * @param snapshot 当前查询结果及可能改变结果的时间边界
     * @param generation 管理端发布后的可读代际
     * @return 带代际和时间边界的查询条目
     */
    public static CardBinLookupCacheEntry from(CardBinLookupSnapshot snapshot, String generation) {
        LocalDateTime until = snapshot.expireTime();
        if (snapshot.nextEffectiveTime() != null
                && (until == null || snapshot.nextEffectiveTime().isBefore(until))) {
            until = snapshot.nextEffectiveTime();
        }
        return new CardBinLookupCacheEntry(generation, FORMAT_VERSION, snapshot.result(), until);
    }

    /**
     * 校验代际、格式、正向命中及业务时间边界；无时间边界的命中可长期复用。
     *
     * @param currentGeneration 本次从管理端代际存储读取的可用代际
     * @param cardBinPrefix 当前查询前缀
     * @param now 当前业务时间
     * @return 允许直接复用时返回 true
     */
    public boolean usableAt(String currentGeneration, String cardBinPrefix, LocalDateTime now) {
        return generation != null && generation.equals(currentGeneration)
                && formatVersion == FORMAT_VERSION
                && result != null && Boolean.TRUE.equals(result.matched())
                && cardBinPrefix.equals(result.cardBin())
                && (validUntil == null || now.isBefore(validUntil));
    }
}
