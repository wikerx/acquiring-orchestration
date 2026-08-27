package com.scott.payment.settlement.service;

import com.scott.payment.settlement.entity.SettlementBatchDO;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchLeaseService
 * @date : 2026-08-26 22:30
 * @email : scott_x@163.com
 * @description : 结算批次数据库处理租约边界，提供跨实例排他获取和续租，不使用 Redis 或 JVM 锁代替主库状态。
 * @status : create
 */
public interface SettlementBatchLeaseService {

    /**
     * 锁定并获取下一条可处理批次。
     *
     * @param owner 当前服务实例稳定所有者标识
     * @param now 当前数据库语义时间
     * @param deadline 新租约截止时间，必须晚于 now
     * @return 获得租约的批次，没有可处理批次时为空
     */
    Optional<SettlementBatchDO> acquireNext(String owner, LocalDateTime now, LocalDateTime deadline);

    /**
     * 当前所有者在租约未过期前续租。
     *
     * @param settlementBatchNo 批次号
     * @param owner 当前租约所有者
     * @param now 当前时间
     * @param deadline 新截止时间
     */
    void renew(String settlementBatchNo, String owner, LocalDateTime now, LocalDateTime deadline);
}
