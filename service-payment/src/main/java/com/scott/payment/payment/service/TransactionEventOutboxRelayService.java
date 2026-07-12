package com.scott.payment.payment.service;

import java.time.LocalDateTime;

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
     * @param eventTime 事件时间，用于定位物理分表
     * @param limit     最大投递条数
     * @return 本次成功投递数量
     */
    int publishDueEvents(LocalDateTime eventTime, int limit);
}
