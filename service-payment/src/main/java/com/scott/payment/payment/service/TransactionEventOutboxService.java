package com.scott.payment.payment.service;

import com.scott.payment.payment.entity.TransactionEventOutboxDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionEventOutboxService
 * @date : 2026-07-12 18:20
 * @email : scott_x@163.com
 * @description : 交易本地消息服务，位于 service-payment 服务层，负责在本地事务内记录待投递 RocketMQ 事件。
 * @status : create
 */
public interface TransactionEventOutboxService {

    /**
     * 保存交易本地事件。
     *
     * @param eventDO 本地事件记录
     */
    void save(TransactionEventOutboxDO eventDO);

    /**
     * 查询指定交易时间所在物理分表中待投递的本地事件。
     *
     * @param eventTime 交易时间，用于定位物理分表；保留参数名兼容现有调用方
     * @param now       当前时间
     * @param limit     最大返回条数
     * @return 待投递事件列表
     */
    List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit);

    /**
     * 标记本地事件已投递。
     *
     * @param eventDO  待更新事件
     * @param sentTime 投递成功时间
     * @return true 表示更新成功
     */
    boolean markSent(TransactionEventOutboxDO eventDO, LocalDateTime sentTime);

    /**
     * 标记本地事件投递失败并安排下一次重试。
     *
     * @param eventDO       待更新事件
     * @param nextRetryTime 下次重试时间
     * @param failReason    失败原因摘要
     * @param now           当前时间
     * @return true 表示更新成功
     */
    boolean markFailed(TransactionEventOutboxDO eventDO, LocalDateTime nextRetryTime, String failReason, LocalDateTime now);
}
