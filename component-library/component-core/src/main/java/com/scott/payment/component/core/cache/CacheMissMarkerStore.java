package com.scott.payment.component.core.cache;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CacheMissMarkerStore
 * @date : 2026-07-30 21:20
 * @email : scott_x@163.com
 * @description : 缓存空结果标记存储契约，用三态查询结果区分业务未命中与 Redis 读取故障，避免把基础设施异常误判为数据不存在
 * @status : create
 */
public interface CacheMissMarkerStore {

    /**
     * 查询 miss marker 当前状态。
     *
     * @param domain      业务域，不允许为空或包含 Redis 分隔符
     * @param business    miss marker 业务用途，不允许为空或包含 Redis 分隔符
     * @param businessKey 业务唯一键，不允许为空
     * @return PRESENT 表示 marker 存在，ABSENT 表示 Redis 明确返回不存在，UNAVAILABLE 表示无法可靠读取
     */
    LookupStatus lookup(String domain, String business, String businessKey);

    /**
     * 写入短生命周期 miss marker。
     *
     * <p>调用方必须先从数据库事实源确认记录不存在。缓存读取异常、反序列化异常或回源限流均不得调用本方法，
     * 否则会把暂时性基础设施故障固化为业务不存在。</p>
     *
     * @param domain        业务域
     * @param business      miss marker 业务用途
     * @param businessKey   业务唯一键
     * @param baseTtl       基础有效期，必须大于零
     * @param jitterPercent TTL 抖动百分比，取值范围为 0 至 50
     */
    void markMissing(String domain,
                     String business,
                     String businessKey,
                     Duration baseTtl,
                     int jitterPercent);

    /**
     * 删除指定 miss marker。
     *
     * <p>该操作用于数据库变更后的可靠失效链，Redis 删除失败必须向调用方抛出异常，
     * 由事务后 Outbox 保留失效门禁并重试，不能静默接受陈旧的“不存在”结论。</p>
     *
     * @param domain      业务域
     * @param business    miss marker 业务用途
     * @param businessKey 业务唯一键
     */
    void evict(String domain, String business, String businessKey);

    /**
     * miss marker 查询三态。
     */
    enum LookupStatus {

        /**
         * Redis 明确返回 marker 存在，可以在失效门禁稳定时跳过数据库查询。
         */
        PRESENT,

        /**
         * Redis 明确返回 marker 不存在，允许继续查询正缓存或数据库。
         */
        ABSENT,

        /**
         * Redis 未配置、超时或读取失败，必须继续查询数据库且不得据此写入 marker。
         */
        UNAVAILABLE
    }
}
