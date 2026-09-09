package com.scott.payment.payment.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionEventOutboxRelayService
 * @date : 2026-07-12 18:45
 * @email : scott_x@163.com
 * @description : 交易本地消息投递服务，位于 service-payment 服务层，负责把已提交本地事务中的 outbox 事件可靠投递到 RocketMQ。
 * @status : create
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

    /** 跨指定已发布季度汇总并刷新交易 Outbox 运维指标。 */
    default void refreshMetrics(List<LocalDateTime> publishedQuarters) {
        // 仅默认兼容测试替身；生产实现必须从数据库聚合。
    }
}
