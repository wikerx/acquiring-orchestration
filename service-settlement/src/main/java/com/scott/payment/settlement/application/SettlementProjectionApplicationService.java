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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算后交易状态投影与事件 Outbox 编排。
 *
 * <p>只有真实 CLEARING_REVISION 候选可以更新交易财务状态；动作单、生命周期主单和冻结消息
 * 在同一 transaction 主库事务提交，失败状态则在主事务回滚后独立记录。</p>
 */
@Service
public class SettlementProjectionApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SettlementProjectionApplicationService.class);

    /** 投影事务失败时写入任务表的稳定失败码。 */
    private static final String FAILURE_CODE = "SETTLEMENT_PROJECTION_EXECUTION_FAILED";
    /** 投影失败指数退避的最大分钟数。 */
    private static final int MAX_BACKOFF_MINUTES = 60;

    private final SettlementProjectionMapper projectionMapper;

    public SettlementProjectionApplicationService(SettlementProjectionMapper projectionMapper) {
        this.projectionMapper = projectionMapper;
    }

    /**
     * 认领一条到期任务，在同一交易库事务内同步财务状态、动作单、主单并冻结 Outbox。
     *
     * @param now 本轮任务认领和状态更新时间
     * @return true 表示处理了一条任务，false 表示当前没有到期任务
     * @throws SettlementProjectionProcessingException 投影事务失败时携带回滚后的 CAS 身份抛出
     */
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
            LOGGER.info("结算交易投影事务写入完成, event: SETTLEMENT_PROJECTION_WRITE_END, taskNo: {}, settlementBatchNo: {}, projectionAction: {}, transactionId: {}",
                    task.getTaskNo(), task.getSettlementBatchNo(),
                    task.getProjectionAction(), task.getTransactionId());
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
        boolean recorded = projectionMapper.recordFailure(
                failure.getTaskNo(), retryCount, failure.getExpectedVersion(),
                failure.getFailureCode(), now.plusMinutes(delayMinutes), now) == 1;
        if (recorded) {
            LOGGER.warn("结算交易投影失败已进入重试退避, event: SETTLEMENT_PROJECTION_RETRY_SCHEDULED, taskNo: {}, failureCode: {}, retryCount: {}, delayMinutes: {}",
                    failure.getTaskNo(), failure.getFailureCode(), retryCount + 1, delayMinutes);
        }
        return recorded;
    }

    /** 从冻结任务和最小交易身份构造非敏感结算事件，消息组固定使用 operationId 保序。 */
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

    /** 校验投影任务与交易动作的交易号、动作号、商户、币种和分片身份完全一致。 */
    private void validateOperation(SettlementProjectionTaskDO task,
                                   SettlementOperationIdentityDO operation) {
        boolean valid = operation != null
                && task.getClearingRevision() != null && task.getClearingRevision() > 0
                && task.getOperationId().equals(operation.getOperationId())
                && task.getMerchantId().equals(operation.getMerchantId())
                && operation.getMerchantOrderNo() != null && !operation.getMerchantOrderNo().isBlank()
                && operation.getTransactionCurrency() != null
                && operation.getTransactionCurrency().matches("[A-Z]{3}")
                && operation.getRootTransactionDateTime() != null
                && operation.getTransactionStatus() != null
                && !operation.getTransactionStatus().isBlank()
                && ("SETTLE".equals(task.getProjectionAction())
                    || ("REVERSE".equals(task.getProjectionAction())
                        && task.getOriginalBatchNo() != null));
        if (!valid) {
            throw new IllegalStateException("settlement operation projection identity is invalid");
        }
    }

    /** 在同一 transaction 主库事务按 SETTLE/REVERSE CAS 同步财务状态、动作单和生命周期主单。 */
    private void projectState(SettlementProjectionTaskDO task,
                              SettlementOperationIdentityDO operation,
                              LocalDateTime now) {
        if ("REVERSE".equals(task.getProjectionAction())) {
            if (!("SETTLED".equals(operation.getSettlementStatus())
                    || "REVERSED".equals(operation.getSettlementStatus()))) {
                throw new IllegalStateException("settlement reversal operation status is invalid");
            }
            BigDecimal settlementRate = requireSettlementRate(
                    task.getOriginalBatchNo(), operation.getTransactionCurrency(), task.getSettlementCurrency());
            int financeUpdated = projectionMapper.markFinanceStateReversed(
                    task.getTransactionId(), task.getTransactionDateTime(), task.getClearingRevision(),
                    task.getSettlementCurrency(), task.getSettlementAmount(), settlementRate,
                    task.getSettlementDate(), task.getOriginalBatchNo(), task.getSettlementBatchNo(), now);
            if (financeUpdated == 0 && projectionMapper.countMatchingReversedFinanceState(
                    task.getTransactionId(), task.getTransactionDateTime(), task.getClearingRevision(),
                    task.getSettlementCurrency(), task.getSettlementAmount(), settlementRate,
                    task.getSettlementDate(), task.getSettlementBatchNo()) != 1) {
                throw new IllegalStateException("settlement reversal finance projection identity is inconsistent");
            }
            int operationUpdated = projectionMapper.markOperationReversed(
                    task.getTransactionId(), task.getTransactionDateTime(), task.getSettlementCurrency(),
                    task.getSettlementAmount(), settlementRate, task.getSettlementDate(),
                    task.getOriginalBatchNo(), task.getSettlementBatchNo(), now);
            if (operationUpdated == 0 && projectionMapper.countMatchingReversedOperation(
                    task.getTransactionId(), task.getTransactionDateTime(), task.getSettlementCurrency(),
                    task.getSettlementAmount(), settlementRate, task.getSettlementDate(),
                    task.getSettlementBatchNo()) != 1) {
                throw new IllegalStateException("settlement reversal operation projection identity is inconsistent");
            }
            int orderUpdated = projectionMapper.markOrderReversed(
                    task.getOperationId(), operation.getRootTransactionDateTime(), task.getTransactionId(),
                    task.getTransactionDateTime(), task.getSettlementCurrency(), task.getSettlementAmount(),
                    settlementRate, task.getSettlementDate(), task.getOriginalBatchNo(),
                    task.getSettlementBatchNo(), now);
            if (orderUpdated == 0 && projectionMapper.countMatchingReversedOrder(
                    task.getOperationId(), operation.getRootTransactionDateTime(), task.getTransactionId(),
                    task.getTransactionDateTime(), task.getSettlementCurrency(), task.getSettlementAmount(),
                    settlementRate, task.getSettlementDate(), task.getSettlementBatchNo()) != 1
                    && projectionMapper.countNewerOrderSettlement(
                    task.getOperationId(), operation.getRootTransactionDateTime(), task.getTransactionId(),
                    task.getTransactionDateTime()) != 1) {
                throw new IllegalStateException("settlement reversal order projection identity is inconsistent");
            }
            return;
        }
        BigDecimal settlementRate = requireSettlementRate(
                task.getSettlementBatchNo(), operation.getTransactionCurrency(), task.getSettlementCurrency());
        int financeUpdated = projectionMapper.markFinanceStateSettled(
                task.getTransactionId(), task.getTransactionDateTime(), task.getClearingRevision(),
                task.getSettlementCurrency(), settlementRate, task.getSettlementAmount(), task.getSettlementDate(),
                task.getSettlementBatchNo(), now);
        if (financeUpdated == 0 && projectionMapper.countMatchingSettledFinanceState(
                task.getTransactionId(), task.getTransactionDateTime(), task.getClearingRevision(),
                task.getSettlementCurrency(), settlementRate, task.getSettlementAmount(), task.getSettlementDate(),
                task.getSettlementBatchNo()) != 1) {
            throw new IllegalStateException("settlement finance projection identity is inconsistent");
        }
        int operationUpdated = projectionMapper.markOperationSettled(
                task.getTransactionId(), task.getTransactionDateTime(), task.getSettlementCurrency(),
                task.getSettlementAmount(), settlementRate, task.getSettlementDate(),
                task.getSettlementBatchNo(), now);
        if (operationUpdated == 0 && projectionMapper.countMatchingSettledOperation(
                task.getTransactionId(), task.getTransactionDateTime(), task.getSettlementCurrency(),
                task.getSettlementAmount(), settlementRate, task.getSettlementDate(),
                task.getSettlementBatchNo()) != 1) {
            throw new IllegalStateException("settlement operation projection identity is inconsistent");
        }
        int orderUpdated = projectionMapper.markOrderSettled(
                task.getOperationId(), operation.getRootTransactionDateTime(), task.getTransactionId(),
                task.getTransactionDateTime(), task.getSettlementCurrency(), task.getSettlementAmount(),
                settlementRate, task.getSettlementDate(), task.getSettlementBatchNo(), now);
        if (orderUpdated == 0 && projectionMapper.countMatchingSettledOrder(
                task.getOperationId(), operation.getRootTransactionDateTime(), task.getTransactionId(),
                task.getTransactionDateTime(), task.getSettlementCurrency(), task.getSettlementAmount(),
                settlementRate, task.getSettlementDate(), task.getSettlementBatchNo()) != 1
                && projectionMapper.countNewerOrderSettlement(
                task.getOperationId(), operation.getRootTransactionDateTime(), task.getTransactionId(),
                task.getTransactionDateTime()) != 1) {
            throw new IllegalStateException("settlement order projection identity is inconsistent");
        }
    }

    /** 读取批次冻结的交易币种到结算币种直接汇率，缺失或非正值时阻断投影。 */
    private BigDecimal requireSettlementRate(String batchNo,
                                             String sourceCurrency,
                                             String targetCurrency) {
        BigDecimal settlementRate = projectionMapper.selectDirectSettlementRate(
                batchNo, sourceCurrency, targetCurrency);
        if (settlementRate == null || settlementRate.signum() <= 0 || settlementRate.scale() > 12) {
            throw new IllegalStateException("settlement projection direct rate is missing or invalid");
        }
        return settlementRate;
    }
}
