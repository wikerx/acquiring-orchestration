package com.scott.payment.settlement.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.service.SettlementBatchLeaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementBatchLeaseService
 * @date : 2026-08-26 22:30
 * @email : scott_x@163.com
 * @description : 使用批次行锁、租约截止时间和 version CAS 获取处理权，实例崩溃后仅允许在数据库租约过期后接管。
 * @status : create
 */
@Service
public class DefaultSettlementBatchLeaseService implements SettlementBatchLeaseService {

    private final SettlementBatchMapper batchMapper;

    public DefaultSettlementBatchLeaseService(SettlementBatchMapper batchMapper) {
        this.batchMapper = batchMapper;
    }

    /**
     * 获取租约和更新批次 version 位于同一 transaction 主库事务。
     *
     * @param owner 实例所有者标识
     * @param now 当前时间
     * @param deadline 租约截止时间
     * @return 已租用批次
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public Optional<SettlementBatchDO> acquireNext(String owner,
                                                   LocalDateTime now,
                                                   LocalDateTime deadline) {
        String normalizedOwner = requireLeaseArguments(owner, now, deadline);
        SettlementBatchDO batch = batchMapper.selectNextProcessableForUpdate(now);
        if (batch == null) {
            return Optional.empty();
        }
        if (batch.getVersion() == null || batch.getCandidateCount() == null
                || batch.getCandidateCount() <= 0) {
            throw new IllegalStateException("processable settlement batch is incomplete or empty");
        }
        if (batchMapper.acquireProcessingLease(batch.getSettlementBatchNo(), normalizedOwner,
                now, deadline, batch.getVersion()) != 1) {
            throw new IllegalStateException("settlement batch processing lease CAS failed");
        }
        batch.setProcessingOwner(normalizedOwner);
        batch.setProcessingDeadline(deadline);
        batch.setVersion(batch.getVersion() + 1);
        return Optional.of(batch);
    }

    /** 续租只允许当前所有者在旧租约尚未过期时执行。 */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public void renew(String settlementBatchNo,
                      String owner,
                      LocalDateTime now,
                      LocalDateTime deadline) {
        if (settlementBatchNo == null || settlementBatchNo.isBlank()) {
            throw new IllegalArgumentException("settlement batch number is required");
        }
        String normalizedOwner = requireLeaseArguments(owner, now, deadline);
        if (batchMapper.renewProcessingLease(settlementBatchNo.trim(), normalizedOwner, now, deadline) != 1) {
            throw new IllegalStateException("settlement batch processing lease renewal failed");
        }
    }

    private String requireLeaseArguments(String owner, LocalDateTime now, LocalDateTime deadline) {
        if (owner == null || owner.isBlank() || owner.trim().length() > 128) {
            throw new IllegalArgumentException("settlement lease owner is invalid");
        }
        Objects.requireNonNull(now, "settlement lease current time is required");
        Objects.requireNonNull(deadline, "settlement lease deadline is required");
        if (!deadline.isAfter(now)) {
            throw new IllegalArgumentException("settlement lease deadline must be after current time");
        }
        return owner.trim();
    }
}
