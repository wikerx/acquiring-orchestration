package com.scott.payment.settlement.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.entity.SettlementCandidateActivationDO;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.service.SettlementCandidateActivationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementCandidateActivationService
 * @date : 2026-08-26 22:00
 * @email : scott_x@163.com
 * @description : 在 transaction 主库事务内联表锁定合法候选，并以候选版本和全部冻结维度批量 CAS 完成真实激活。
 * @status : create
 */
@Service
public class DefaultSettlementCandidateActivationService implements SettlementCandidateActivationService {

    private static final int MAX_ACTIVATION_SIZE = 200;

    private final SettlementCandidateMapper candidateMapper;

    public DefaultSettlementCandidateActivationService(SettlementCandidateMapper candidateMapper) {
        this.candidateMapper = candidateMapper;
    }

    /**
     * 激活时同时校验商户档案、正常账户、币种、exponent、日历和候选版本，任一行异常整页回滚。
     *
     * @param limit 单页候选数
     * @param activatedTime 统一激活时间
     * @return 激活数量
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public int activateEligibleCandidates(int limit, LocalDateTime activatedTime) {
        if (limit < 1 || limit > MAX_ACTIVATION_SIZE) {
            throw new IllegalArgumentException("settlement activation limit must be between 1 and 200");
        }
        Objects.requireNonNull(activatedTime, "settlement activation time is required");
        List<SettlementCandidateActivationDO> candidates = candidateMapper.selectActivatableForUpdate(limit);
        if (candidates.isEmpty()) {
            return 0;
        }
        validate(candidates);
        int affected = candidateMapper.activateBatch(candidates, activatedTime);
        if (affected != candidates.size()) {
            throw new IllegalStateException("settlement candidate activation CAS affected an unexpected row count");
        }
        return affected;
    }

    private void validate(List<SettlementCandidateActivationDO> candidates) {
        Set<Long> candidateIds = new HashSet<>();
        for (SettlementCandidateActivationDO row : candidates) {
            if (row == null || row.getCandidateId() == null || row.getCandidateId() <= 0
                    || row.getCandidateVersion() == null || row.getCandidateVersion() < 0
                    || row.getSettlementProfileId() == null || row.getSettlementProfileId() <= 0
                    || row.getSettlementAccountId() == null || row.getSettlementAccountId() <= 0
                    || row.getSettlementEligibleDate() == null) {
                throw new IllegalStateException("settlement candidate activation identity is incomplete");
            }
            if (!candidateIds.add(row.getCandidateId())) {
                throw new IllegalStateException("settlement candidate activation contains duplicate candidates");
            }
            if (!Objects.equals(row.getCandidateTargetCurrency(), row.getProfileTargetCurrency())) {
                throw new IllegalStateException("settlement candidate target currency differs from active profile");
            }
            if (!Objects.equals(row.getCandidateTargetCurrencyExponent(),
                    row.getProfileTargetCurrencyExponent())) {
                throw new IllegalStateException("settlement candidate target currency exponent differs from active profile");
            }
            if (row.getBusinessTimeZone() == null || row.getDailyCutoffTime() == null) {
                throw new IllegalStateException("settlement profile calendar is incomplete");
            }
            ZoneId.of(row.getBusinessTimeZone());
        }
    }
}
