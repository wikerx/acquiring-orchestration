package com.scott.payment.settlement.application;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import com.scott.payment.settlement.entity.SettlementOperationIdentityDO;
import com.scott.payment.settlement.entity.SettlementProjectionTaskDO;
import com.scott.payment.settlement.exception.SettlementProjectionProcessingException;
import com.scott.payment.settlement.mapper.SettlementProjectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;

/** 结算后交易状态投影编排；交易状态和结算完成 Outbox 在同一 transaction 主库事务提交。 */
@Service
public class SettlementProjectionApplicationService {

    private static final String FAILURE_CODE = "SETTLEMENT_PROJECTION_EXECUTION_FAILED";
    private static final int MAX_BACKOFF_MINUTES = 60;

    private final SettlementProjectionMapper projectionMapper;

    public SettlementProjectionApplicationService(SettlementProjectionMapper projectionMapper) {
        this.projectionMapper = projectionMapper;
    }

    /** @return true 表示处理了一条任务，false 表示当前没有到期任务。 */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public boolean processNext(LocalDateTime now) {
        SettlementProjectionTaskDO task = projectionMapper.selectNextDueForUpdate(now);
        if (task == null) {
            return false;
        }
        if (task.getVersion() == null || projectionMapper.markProcessing(
                task.getTaskNo(), task.getVersion(), now) != 1) {
            throw new IllegalStateException("settlement projection task claim CAS failed");
        }
        long rollbackVersion = task.getVersion();
        int rollbackRetryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        task.setVersion(rollbackVersion + 1);
        try {
            SettlementOperationIdentityDO operation = projectionMapper.selectOperationIdentity(
                    task.getTransactionId(), task.getTransactionDateTime());
            validateOperation(task, operation);

            projectState(task, operation, now);

            SettlementEventOutboxDO outbox = outbox(task, operation, now);
            if (projectionMapper.insertOutboxIdempotent(outbox) != 1
                    || projectionMapper.markCompleted(task.getTaskNo(), task.getVersion(), now) != 1) {
                throw new IllegalStateException("settlement projection outbox or completion write failed");
            }
            return true;
        } catch (RuntimeException exception) {
            throw new SettlementProjectionProcessingException(
                    task.getTaskNo(), rollbackVersion, rollbackRetryCount, FAILURE_CODE, exception);
        }
    }

    /**
     * 在投影主事务完成回滚后，使用独立事务持久化失败次数和有上限指数退避。
     *
     * @param failure 投影事务携带的回滚后 CAS 身份
     * @param now 失败记录时间
     * @return true 表示本次失败状态成功落库，false 表示任务已被其它实例推进
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean recordFailure(SettlementProjectionProcessingException failure, LocalDateTime now) {
        int retryCount = Math.max(0, failure.getExpectedRetryCount());
        long delayMinutes = Math.min(1L << Math.min(retryCount, 6), MAX_BACKOFF_MINUTES);
        return projectionMapper.recordFailure(
                failure.getTaskNo(), retryCount, failure.getExpectedVersion(),
                failure.getFailureCode(), now.plusMinutes(delayMinutes), now) == 1;
    }

    private SettlementEventOutboxDO outbox(SettlementProjectionTaskDO task,
                                            SettlementOperationIdentityDO operation,
                                            LocalDateTime now) {
        String eventNo = "SETTLEMENT:" + task.getTaskNo();
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId(eventNo);
        message.setCreatedAt(now);
        message.setTraceId(task.getSettlementBatchNo());
        message.setRetryCount(0);
        message.setTransactionId(task.getTransactionId());
        message.setOperationId(task.getOperationId());
        message.setMerchantId(task.getMerchantId());
        message.setMerchantOrderNo(operation.getMerchantOrderNo());
        message.setTransactionType(operation.getTransactionType());
        message.setTransactionStatus(operation.getTransactionStatus());
        String eventTag = "REVERSE".equals(task.getProjectionAction())
                ? MqTag.TRANSACTION_SETTLEMENT_REVERSED : MqTag.TRANSACTION_SETTLEMENT_COMPLETED;
        message.setEventType(eventTag);
        message.setTransactionDateTime(task.getTransactionDateTime());

        SettlementEventOutboxDO row = new SettlementEventOutboxDO();
        row.setEventNo(eventNo);
        row.setSettlementBatchNo(task.getSettlementBatchNo());
        row.setCandidateId(task.getCandidateId());
        row.setTopic(MqTopic.PAYMENT_TRANSACTION_FIFO);
        row.setTag(eventTag);
        row.setMessageKey(eventNo);
        row.setMessageGroup(task.getOperationId());
        row.setPayloadJson(JsonUtils.toJsonString(message));
        row.setEventStatus("INIT");
        row.setRetryCount(0);
        row.setNextRetryTime(now);
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private void validateOperation(SettlementProjectionTaskDO task,
                                   SettlementOperationIdentityDO operation) {
        boolean valid = operation != null
                && task.getClearingRevision() != null && task.getClearingRevision() > 0
                && task.getOperationId().equals(operation.getOperationId())
                && task.getMerchantId().equals(operation.getMerchantId())
                && operation.getMerchantOrderNo() != null && !operation.getMerchantOrderNo().isBlank()
                && "SUCCESS".equals(operation.getTransactionStatus())
                && ("SETTLE".equals(task.getProjectionAction())
                    || ("REVERSE".equals(task.getProjectionAction())
                        && task.getOriginalBatchNo() != null));
        if (!valid) {
            throw new IllegalStateException("settlement operation projection identity is invalid");
        }
    }

    private void projectState(SettlementProjectionTaskDO task,
                              SettlementOperationIdentityDO operation,
                              LocalDateTime now) {
        if ("REVERSE".equals(task.getProjectionAction())) {
            if (!"SETTLED".equals(operation.getSettlementStatus())
                    || projectionMapper.markFinanceStateReversed(
                    task.getTransactionId(), task.getTransactionDateTime(), task.getClearingRevision(),
                    task.getOriginalBatchNo(), task.getSettlementBatchNo(), now) != 1
                    || projectionMapper.markOperationReversed(
                    task.getTransactionId(), task.getTransactionDateTime(), now) != 1) {
                throw new IllegalStateException("settlement reversal projection CAS failed");
            }
            return;
        }
        int financeUpdated = projectionMapper.markFinanceStateSettled(
                task.getTransactionId(), task.getTransactionDateTime(), task.getClearingRevision(),
                task.getSettlementCurrency(), task.getSettlementAmount(), task.getSettlementDate(),
                task.getSettlementBatchNo(), now);
        if (financeUpdated == 0 && projectionMapper.countMatchingSettledFinanceState(
                task.getTransactionId(), task.getTransactionDateTime(), task.getClearingRevision(),
                task.getSettlementCurrency(), task.getSettlementAmount(), task.getSettlementDate(),
                task.getSettlementBatchNo()) != 1) {
            throw new IllegalStateException("settlement finance projection identity is inconsistent");
        }
        int operationUpdated = projectionMapper.markOperationSettled(
                task.getTransactionId(), task.getTransactionDateTime(), now);
        if (operationUpdated == 0 && !"SETTLED".equals(operation.getSettlementStatus())) {
            throw new IllegalStateException("settlement operation projection CAS failed");
        }
    }
}
