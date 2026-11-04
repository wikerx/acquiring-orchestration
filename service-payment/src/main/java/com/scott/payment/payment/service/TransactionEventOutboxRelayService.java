package com.scott.payment.payment.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易本地消息投递服务，位于 service-payment 服务层，负责把已提交本地事务中的 outbox 事件可靠投递到 RocketMQ。
 */
public interface TransactionEventOutboxRelayService {

    /**
     * 投递指定事件时间所在季度分表中的到期事件。
     *
     * @param eventTime 事件时间，用于 ShardingSphere 精确定位季度
     * @param limit     最大投递条数
     * @return 本次成功投递数量
     */
    int publishDueEvents(LocalDateTime eventTime, int limit);

    /**
     * 在有界批次数内连续投递指定季度的到期事件。
     *
     * <p>实现必须以数据库持久化状态判断是否到期，并保留 claim/CAS 和至少一次投递语义；
     * {@code maxBatches} 仅控制单次调用的资源预算，不能替代消息重试状态机。</p>
     *
     * @param eventTime 事件时间，用于 ShardingSphere 精确定位季度
     * @param limit 每批最大投递条数
     * @param maxBatches 单次调用允许处理的最大批次数
     * @return 本次成功投递总数
     */
    int publishDueEvents(LocalDateTime eventTime, int limit, int maxBatches);

    /**
     * 跨指定已发布季度汇总并刷新交易 Outbox 运维指标。
     */
    default void refreshMetrics(List<LocalDateTime> publishedQuarters) {
        // 仅默认兼容测试替身；生产实现必须从数据库聚合。
    }
}
