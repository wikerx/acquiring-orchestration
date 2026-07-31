package com.scott.payment.risk.service;

import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商户累计限额预占状态事实服务。
 */
public interface MerchantLimitReservationStateService {

    /**
     * 幂等创建或读取一笔累计限额预占事实，初始状态为准备中。
     *
     * @param candidate 包含交易、规则、周期和最小金额单位的预占候选
     * @return 已持久化的预占事实；同一幂等键已存在时返回既有记录
     */
    MerchantLimitReservationDO prepare(MerchantLimitReservationDO candidate);

    /**
     * 将准备中的预占标记为已完成 Redis 原子扣占。
     *
     * @param reservation 待更新的持久化预占记录
     * @return 状态成功从准备中迁移到已预占时返回 {@code true}
     */
    boolean markReserved(MerchantLimitReservationDO reservation);

    /**
     * 确认指定交易的全部非终态预占，使其不可再被取消。
     *
     * @param transactionId 平台交易号
     * @return 各状态迁移数量汇总
     */
    MerchantLimitReservationTransitionSummary confirm(String transactionId);

    /**
     * 取消指定交易的全部非终态预占，并记录业务原因。
     *
     * @param transactionId 平台交易号
     * @param reason 取消原因，不得包含敏感请求数据
     * @return 各状态迁移数量汇总
     */
    MerchantLimitReservationTransitionSummary cancel(String transactionId, String reason);

    /**
     * 取消指定预占集合中的非终态记录，并记录业务原因。
     *
     * @param reservations 待取消的持久化预占记录
     * @param reason 取消原因，不得包含敏感请求数据
     * @return 各状态迁移数量汇总
     */
    MerchantLimitReservationTransitionSummary cancel(List<MerchantLimitReservationDO> reservations, String reason);

    /**
     * 查询指定交易的全部累计限额预占事实。
     *
     * @param transactionId 平台交易号
     * @return 按持久化结果返回的预占记录；不存在时返回空集合
     */
    List<MerchantLimitReservationDO> findByTransactionId(String transactionId);

    /**
     * 在当前数据库事务中锁定并返回指定交易的预占事实。
     *
     * @param transactionId 平台交易号
     * @return 已加行锁的预占记录；不存在时返回空集合
     */
    List<MerchantLimitReservationDO> lockByTransactionId(String transactionId);

    /**
     * 取消调用方已持有数据库行锁的预占记录，避免并发确认覆盖取消。
     *
     * @param reservations 已在当前事务中加锁的预占记录
     * @param reason 取消原因，不得包含敏感请求数据
     * @return 各状态迁移数量汇总
     */
    MerchantLimitReservationTransitionSummary cancelLocked(
            List<MerchantLimitReservationDO> reservations,
            String reason);

    /**
     * 分页查询更新时间早于阈值的非终态预占，供补偿任务核对 Redis 状态。
     *
     * @param updatedBefore 最晚更新时间阈值
     * @param limit 单批最大返回数，必须为受控正整数
     * @return 待核对的非终态预占记录
     */
    List<MerchantLimitReservationDO> findStaleNonTerminal(LocalDateTime updatedBefore, int limit);
}
