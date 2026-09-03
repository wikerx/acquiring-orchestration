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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementProjectionApplicationService
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 编排结算后交易投影与事件 Outbox；仅真实 CLEARING_REVISION 候选更新交易主单/动作单，状态投影和冻结消息在同一交易库事务提交。
 * @status : create
 */
@Service
public class SettlementProjectionApplicationService {

    /**
     * 处理失败码，用于补偿策略、告警聚合和后台排障，不直接暴露底层异常。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String FAILURE_CODE = "SETTLEMENT_PROJECTION_EXECUTION_FAILED";
    /**
     * {@code MAX_BACKOFF_MINUTES}常量，统一 {@code SettlementProjectionApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
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
