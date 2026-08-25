package com.scott.payment.payment.service;

import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.model.TransactionEventOutboxMetricsSnapshot;

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
     * 查询指定交易时间所在季度中待投递的本地事件。
     *
     * @param eventTime 交易时间，用于 ShardingSphere 精确定位季度
     * @param now       当前时间
     * @param limit     最大返回条数
     * @return 待投递事件列表
     */
    List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit);

    /**
     * 使用版本号 CAS 抢占待投递事件。
     *
     * @param eventDO 查询得到的待投递事件
     * @param claimedTime 抢占时间
     * @return true 表示当前实例取得投递权
     */
    default boolean claimForPublish(TransactionEventOutboxDO eventDO, LocalDateTime claimedTime) {
        return false;
    }

    /**
     * 恢复指定季度中因进程退出遗留的超时 PROCESSING 事件。
     *
     * @param eventTime 交易分片季度锚点
     * @param staleBefore PROCESSING 超时边界
     * @param now 恢复时间
     * @return 恢复或关闭的记录数
     */
    default int recoverStaleProcessing(LocalDateTime eventTime,
                                       LocalDateTime staleBefore,
                                       LocalDateTime now) {
        return 0;
    }

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

    /**
     * 恢复稳定退款执行事件的投递；不存在时返回 false，由上层按原事件号补建。
     *
     * @param eventNo 稳定事件号
     * @param transactionDateTime 退款动作分片时间
     * @param eventType 退款执行事件类型
     * @param now 恢复时间
     * @return true 表示事件已存在；已处于 INIT 时同样返回 true
     */
    default boolean recoverForRedelivery(String eventNo,
                                         LocalDateTime transactionDateTime,
                                         String eventType,
                                         LocalDateTime now) {
        return false;
    }

    /** 查询指定季度的交易 Outbox 运维聚合快照。 */
    default TransactionEventOutboxMetricsSnapshot metricsSnapshot(LocalDateTime eventTime) {
        return null;
    }

    /**
     * 使用事件号、分片时间和版本 CAS 人工恢复一条 Outbox CLOSED 事件。
     *
     * @return true 表示成功进入 FAILED 待重试；状态或版本变化时返回 false
     */
    default boolean recoverClosed(String eventNo,
                                  LocalDateTime transactionDateTime,
                                  Integer expectedVersion,
                                  String recoveryReason,
                                  LocalDateTime now) {
        return false;
    }
}
