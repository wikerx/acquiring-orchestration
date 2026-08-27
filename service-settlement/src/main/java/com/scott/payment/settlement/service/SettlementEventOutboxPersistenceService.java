package com.scott.payment.settlement.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import com.scott.payment.settlement.mapper.SettlementEventOutboxMapper;
import com.scott.payment.settlement.support.SettlementWorkerIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/** 结算事件 Outbox 的短事务状态边界；网络发送不持有数据库锁。 */
@Service
public class SettlementEventOutboxPersistenceService {

    private static final int MAX_RETRY_BACKOFF_MINUTES = 30;

    private final SettlementEventOutboxMapper outboxMapper;
    private final SettlementWorkerIdentity workerIdentity;

    public SettlementEventOutboxPersistenceService(SettlementEventOutboxMapper outboxMapper,
                                                    SettlementWorkerIdentity workerIdentity) {
        this.outboxMapper = outboxMapper;
        this.workerIdentity = workerIdentity;
    }

    /** 领取两分钟发送租约。 */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public Optional<SettlementEventOutboxDO> claimNext(LocalDateTime now) {
        SettlementEventOutboxDO row = outboxMapper.selectNextDueForUpdate(now);
        if (row == null) {
            return Optional.empty();
        }
        String owner = workerIdentity.value();
        if (row.getVersion() == null || outboxMapper.claim(row.getEventNo(), owner,
                now.plusMinutes(2), row.getVersion(), now) != 1) {
            throw new IllegalStateException("settlement event outbox claim CAS failed");
        }
        row.setProcessingOwner(owner);
        row.setProcessingDeadline(now.plusMinutes(2));
        row.setEventStatus("PROCESSING");
        row.setVersion(row.getVersion() + 1);
        return Optional.of(row);
    }

    /** MQ 成功后提交 SENT；CAS 失败时保留至少一次重发语义。 */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public boolean markSent(SettlementEventOutboxDO row, LocalDateTime now) {
        return outboxMapper.markSent(row.getEventNo(), row.getProcessingOwner(),
                row.getVersion(), now) == 1;
    }

    /** MQ 失败后释放租约并按 1、2、4 分钟指数退避，最多等待30分钟。 */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public boolean markFailed(SettlementEventOutboxDO row, String failureCode, LocalDateTime now) {
        int retryCount = row.getRetryCount() == null ? 0 : Math.max(0, row.getRetryCount());
        long delayMinutes = Math.min(1L << Math.min(retryCount, 5), MAX_RETRY_BACKOFF_MINUTES);
        return outboxMapper.markFailed(row.getEventNo(), row.getProcessingOwner(), row.getVersion(),
                failureCode, now.plusMinutes(delayMinutes), now) == 1;
    }
}
