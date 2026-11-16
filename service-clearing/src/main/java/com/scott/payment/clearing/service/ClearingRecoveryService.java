package com.scott.payment.clearing.service;

import com.scott.payment.clearing.entity.ClearingCompensationCandidateDO;

import java.time.LocalDateTime;

/**
 * 单个清分候选的独立短事务恢复边界。
 */
public interface ClearingRecoveryService {

    /**
     * 在独立短事务内重新校验候选并修复状态、投影或延时 Outbox。
     *
     * @param candidate 扫描快照；不能替代事务内行锁和版本校验
     * @param now 本轮统一平台本地审计时间
     * @return 稳定处置码，供批量补偿统计
     * @throws IllegalStateException 唯一键身份冲突或持久化 CAS 异常时抛出
     */
    String recover(ClearingCompensationCandidateDO candidate, LocalDateTime now);
}
