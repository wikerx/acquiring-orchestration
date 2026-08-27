package com.scott.payment.clearing.service;

import com.scott.payment.clearing.entity.ClearingCompensationCandidateDO;

import java.time.LocalDateTime;

/** 单个清分候选的独立短事务恢复边界。 */
public interface ClearingRecoveryService {

    /** 恢复一条候选并返回稳定处置码。 */
    String recover(ClearingCompensationCandidateDO candidate, LocalDateTime now);
}
