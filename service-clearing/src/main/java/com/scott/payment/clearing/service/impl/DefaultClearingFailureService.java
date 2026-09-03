package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.config.ClearingProperties;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingAnomalyTypeEnum;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.clearing.domain.state.ClearingTransitionOrigin;
import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.clearing.dto.ClearingFailureResult;
import com.scott.payment.clearing.entity.ClearingTransactionEventOutboxDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.mapper.ClearingTransactionEventOutboxMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.service.ClearingFailureService;
import com.scott.payment.clearing.service.ClearingAnomalyService;
import com.scott.payment.clearing.service.ClearingProjectionService;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.ClearingRetryDueMessage;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingFailureService
 * @date : 2026-08-26 16:00
 * @email : scott_x@163.com
 * @description : 清分受控失败默认实现，以 finance state 行锁和租约版本为权威，按固定阶梯生成 RocketMQ 绝对定时重试 Outbox。
 * @status : create
 */
@Service
@Slf4j
public class DefaultClearingFailureService implements ClearingFailureService {

    /**
     * {@code FAILURE_SUMMARY_MAX_LENGTH}常量，统一 {@code DefaultClearingFailureService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int FAILURE_SUMMARY_MAX_LENGTH = 512;
    /**
     * {@code OUTBOX_MAX_RETRY_COUNT}，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int OUTBOX_MAX_RETRY_COUNT = 10;
    /**
     * {@code EVENT_STATUS_INIT}，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String EVENT_STATUS_INIT = "INIT";
    private static final List<Long> RETRY_DELAY_MINUTES = List.of(1L, 5L, 15L, 60L, 360L);
    private static final Set<ClearingFailureCodeEnum> SOURCE_WAIT_FAILURES = Set.of(
            ClearingFailureCodeEnum.SOURCE_CLEARING_PENDING,
            ClearingFailureCodeEnum.SOURCE_CLEARING_NOT_FOUND,
            ClearingFailureCodeEnum.SOURCE_SETTLEMENT_PENDING,
            ClearingFailureCodeEnum.RESERVE_SOURCE_NOT_FOUND);
    private static final Set<ClearingFailureCodeEnum> FINANCIAL_FAILURES = Set.of(
            ClearingFailureCodeEnum.FEE_SNAPSHOT_HASH_MISMATCH,
            ClearingFailureCodeEnum.AMOUNT_INVALID,
            ClearingFailureCodeEnum.FEE_COMPONENT_CURRENCY_INVALID,
            ClearingFailureCodeEnum.TIER_ACCUMULATOR_CONFLICT,
            ClearingFailureCodeEnum.RESERVE_RETURN_EXCEEDED,
            ClearingFailureCodeEnum.RESERVE_STATE_CONFLICT);

    private final ClearingTransactionFinanceStateMapper financeStateMapper;
    private final ClearingTransactionEventOutboxMapper outboxMapper;
    private final ClearingProjectionService projectionService;
    private final GlobalIdGenerator idGenerator;
    private final ClearingProperties properties;
    private final ClearingAnomalyService anomalyService;

    /**
     * 创建清分失败持久化服务。
     *
     * @param financeStateMapper 动作清分状态 Mapper
     * @param outboxMapper 交易 Outbox Mapper
     * @param projectionService 动作和生命周期查询投影服务
     * @param idGenerator 平台唯一编号生成器
     * @param properties 清分重试上限配置
     */
    public DefaultClearingFailureService(ClearingTransactionFinanceStateMapper financeStateMapper,
                                         ClearingTransactionEventOutboxMapper outboxMapper,
                                         ClearingProjectionService projectionService,
                                         GlobalIdGenerator idGenerator,
                                         ClearingProperties properties,
                                         ClearingAnomalyService anomalyService) {
        this.financeStateMapper = financeStateMapper;
        this.outboxMapper = outboxMapper;
        this.projectionService = projectionService;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.anomalyService = anomalyService;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ClearingFailureResult recordFailure(PaymentTransactionEventMessage message,
                                               ClearingClaimResult claim,
                                               String processingOwner,
                                               ClearingProcessingException failure,
                                               LocalDateTime nowUtc) {
        requireArguments(message, claim, processingOwner, failure, nowUtc);
        ClearingTransactionFinanceStateDO state = financeStateMapper.selectForUpdate(
                message.getTransactionId(), message.getTransactionDateTime());
        validateLease(message, claim, processingOwner, state);

        int currentRetryCount = requiredRetryCount(state.getClearingRetryCount());
        FailureDecision decision = decide(failure.getFailureCode(), currentRetryCount, nowUtc);
        String failureSummary = failureSummary(failure, decision);
        int updated = financeStateMapper.recordFailure(
                message.getTransactionId(), message.getTransactionDateTime(), processingOwner,
                claim.financeStateVersion(), decision.targetState().name(), decision.retryCount(),
                decision.nextRetryTime(), decision.recordedCode().name(), failureSummary, nowUtc);
        if (updated != 1) {
            throw new IllegalStateException("clearing failure state CAS did not update the acquired lease");
        }
        anomalyService.record(claim.operation(), state.getFinanceStateId(), claim.clearingRevision(),
                FINANCIAL_FAILURES.contains(failure.getFailureCode())
                        ? ClearingAnomalyTypeEnum.FINANCIAL_MISMATCH
                        : ClearingAnomalyTypeEnum.CONTROLLED_FAILURE,
                decision.recordedCode().name(), failureSummary, nowUtc);
        updateFailureProjection(claim, decision, nowUtc);
        if (decision.nextRetryTime() != null) {
            persistRetryOutbox(message, claim, decision, nowUtc);
        }
        return new ClearingFailureResult(
                decision.targetState().name(), decision.recordedCode().name(), decision.retryCount(),
                decision.nextRetryTime(), decision.nextRetryTime() != null);
    }

    /** 失败状态提交后尽力刷新查询投影；投影延迟不得回滚已持久化失败事实。 */
    private void updateFailureProjection(ClearingClaimResult claim,
                                         FailureDecision decision,
                                         LocalDateTime nowUtc) {
        try {
            projectionService.updateResolvingLocator(
                    claim.operation(), decision.targetState(), decision.recordedCode().name(), nowUtc);
        } catch (ClearingProcessingException projectionFailure) {
            log.warn("event: CLEARING_FAILURE_PROJECTION_DEFERRED transactionId: {} operationId: {} targetStatus: {} projectionFailureCode: {}",
                    claim.operation().transactionId(), claim.operation().operationId(),
                    decision.targetState(), projectionFailure.getFailureCode());
        }
    }

    /**
     * 根据受控失败码、当前次数和固定退避表决定重试时间或转人工复核。
     * <p>
     * 等待源交易的失败保持 WAITING_SOURCE，其余可重试失败进入 FAILED；达到上限后必须进入 MANUAL_REVIEW，禁止无限自动重试。
     */
    private FailureDecision decide(ClearingFailureCodeEnum code,
                                   int currentRetryCount,
                                   LocalDateTime nowUtc) {
        if (!code.isRetryable()) {
            return manualDecision(code, currentRetryCount);
        }
        if (currentRetryCount >= properties.getMaxRetryCount()) {
            return manualDecision(ClearingFailureCodeEnum.CLEARING_RETRY_EXHAUSTED, currentRetryCount);
        }
        int nextRetryCount = currentRetryCount + 1;
        long delayMinutes = RETRY_DELAY_MINUTES.get(
                Math.min(nextRetryCount, RETRY_DELAY_MINUTES.size()) - 1);
        ClearingStateEnum targetState = SOURCE_WAIT_FAILURES.contains(code)
                ? ClearingStateEnum.WAITING_SOURCE : ClearingStateEnum.FAILED;
        requireTransition(targetState);
        return new FailureDecision(targetState, code, nextRetryCount,
                nowUtc.plusMinutes(delayMinutes).truncatedTo(ChronoUnit.MILLIS));
    }

    /** 将不可重试或已耗尽重试的失败收敛为人工复核终点，不再安排下次投递。 */
    private FailureDecision manualDecision(ClearingFailureCodeEnum code, int retryCount) {
        requireTransition(ClearingStateEnum.MANUAL_REVIEW);
        return new FailureDecision(ClearingStateEnum.MANUAL_REVIEW, code, retryCount, null);
    }

    /** 自动失败流转必须经过状态机白名单，禁止直接覆盖终态。 */
    private void requireTransition(ClearingStateEnum target) {
        if (!ClearingStateEnum.PROCESSING.canTransitionTo(target, ClearingTransitionOrigin.AUTOMATIC)) {
            throw new IllegalStateException("unsupported automatic clearing failure transition");
        }
    }

    /** 与失败状态在同一事务写入延时 Outbox，避免先发 MQ 后提交状态。 */
    private void persistRetryOutbox(PaymentTransactionEventMessage source,
                                    ClearingClaimResult claim,
                                    FailureDecision decision,
                                    LocalDateTime nowUtc) {
        ClearingOperationFacts operation = claim.operation();
        String eventNo = "CR" + idGenerator.nextId();
        ClearingRetryDueMessage message = new ClearingRetryDueMessage();
        message.setMessageId(eventNo);
        message.setCreatedAt(nowUtc);
        message.setTraceId(source.getTraceId());
        message.setRetryCount(0);
        message.setTransactionId(operation.transactionId());
        message.setOperationId(operation.operationId());
        message.setMerchantId(operation.merchantId());
        message.setMerchantOrderNo(operation.merchantOrderNo());
        message.setTransactionType(operation.transactionType());
        message.setTransactionStatus(operation.transactionStatus());
        message.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        message.setTransactionDateTime(operation.transactionDateTime());
        message.setSourceEventNo(sourceEventNo(source));
        message.setExpectedClearingRevision(claim.clearingRevision());
        message.setClearingRetryCount(decision.retryCount());
        message.setRetryReasonCode(decision.recordedCode().name());
        message.setDeliverAt(decision.nextRetryTime().toInstant(ZoneOffset.UTC));

        ClearingTransactionEventOutboxDO outbox = new ClearingTransactionEventOutboxDO();
        outbox.setEventNo(eventNo);
        outbox.setAggregateType("TRANSACTION_CLEARING_RETRY");
        outbox.setAggregateNo(claim.financeStateId() + ":" + decision.retryCount());
        outbox.setTransactionId(operation.transactionId());
        outbox.setOperationId(operation.operationId());
        outbox.setMerchantId(operation.merchantId());
        outbox.setMerchantOrderNo(operation.merchantOrderNo());
        outbox.setTransactionType(operation.transactionType());
        outbox.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        outbox.setEventStatus(EVENT_STATUS_INIT);
        outbox.setTopic(MqTopic.PAYMENT_CLEARING_DELAY);
        outbox.setTag(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        outbox.setMessageKey(eventNo);
        outbox.setDeliveryMode("SCHEDULED");
        outbox.setDeliverAt(decision.nextRetryTime());
        outbox.setPayloadJson(JsonUtils.toJsonString(message));
        outbox.setRetryCount(0);
        outbox.setMaxRetryCount(OUTBOX_MAX_RETRY_COUNT);
        outbox.setNextRetryTime(nowUtc);
        outbox.setEventTime(nowUtc);
        outbox.setTransactionDateTime(operation.transactionDateTime());
        outbox.setTransactionUtcTime(operation.transactionUtcTime());
        outbox.setTransactionTimeZone(operation.transactionTimeZone());
        outbox.setVersion(0);
        outbox.setDeleted(0);
        outbox.setCreateTime(nowUtc);
        outbox.setUpdateTime(nowUtc);
        if (outboxMapper.insertLogical(outbox) != 1) {
            throw new IllegalStateException("clearing retry outbox insert did not affect one row");
        }
    }

    /** 追溯首次交易事件号，使多轮延时重试仍共享同一来源审计链。 */
    private String sourceEventNo(PaymentTransactionEventMessage source) {
        if (source instanceof ClearingRetryDueMessage retryMessage
                && StringUtils.hasText(retryMessage.getSourceEventNo())) {
            return retryMessage.getSourceEventNo();
        }
        return source.getMessageId();
    }

    /**
     * 生成可持久化的脱敏失败摘要，去除换行并限制长度，避免保存异常堆栈或敏感原文。
     *
     * @param failure 清分受控异常
     * @param decision 本次重试或人工复核决策
     * @return 不超过数据库上限的单行失败摘要
     */
    private String failureSummary(ClearingProcessingException failure, FailureDecision decision) {
        String summary;
        if (decision.recordedCode() == ClearingFailureCodeEnum.CLEARING_RETRY_EXHAUSTED) {
            summary = "clearing retry exhausted after " + decision.retryCount()
                    + " attempts; last failure=" + failure.getFailureCode().name();
        } else {
            summary = StringUtils.hasText(failure.getMessage())
                    ? failure.getMessage().replace('\r', ' ').replace('\n', ' ')
                    : failure.getFailureCode().name();
        }
        return summary.length() <= FAILURE_SUMMARY_MAX_LENGTH
                ? summary : summary.substring(0, FAILURE_SUMMARY_MAX_LENGTH);
    }

    /**
     * 校验失败事务仍携带原消息、已领取财务状态、租约所有者和统一 UTC 时间。
     *
     * @param message 原交易或重试事件
     * @param claim 当前已成功领取的清分状态
     * @param processingOwner 当前数据库处理租约所有者
     * @param failure 待记录受控异常
     * @param nowUtc 失败事实统一 UTC 时间
     */
    private void requireArguments(PaymentTransactionEventMessage message,
                                  ClearingClaimResult claim,
                                  String processingOwner,
                                  ClearingProcessingException failure,
                                  LocalDateTime nowUtc) {
        if (message == null || !StringUtils.hasText(message.getMessageId())
                || !StringUtils.hasText(message.getTransactionId())
                || message.getTransactionDateTime() == null) {
            throw new IllegalArgumentException("clearing failure message identity and shard time are required");
        }
        if (claim == null || !claim.acquired() || claim.operation() == null
                || !StringUtils.hasText(claim.financeStateId())) {
            throw new IllegalArgumentException("an acquired clearing claim is required");
        }
        if (!StringUtils.hasText(processingOwner) || failure == null || nowUtc == null) {
            throw new IllegalArgumentException("clearing failure owner, exception and UTC time are required");
        }
    }

    /** 失败提交必须仍持有同一 PROCESSING owner 和版本。 */
    private void validateLease(PaymentTransactionEventMessage message,
                               ClearingClaimResult claim,
                               String processingOwner,
                               ClearingTransactionFinanceStateDO state) {
        ClearingOperationFacts operation = claim.operation();
        boolean valid = state != null
                && Objects.equals(state.getFinanceStateId(), claim.financeStateId())
                && Objects.equals(state.getTransactionId(), operation.transactionId())
                && Objects.equals(state.getTransactionId(), message.getTransactionId())
                && Objects.equals(state.getOperationId(), operation.operationId())
                && Objects.equals(state.getMerchantId(), operation.merchantId())
                && Objects.equals(state.getTransactionDateTime(), operation.transactionDateTime())
                && ClearingStateEnum.PROCESSING.name().equals(state.getClearingStatus())
                && Objects.equals(state.getProcessingOwner(), processingOwner)
                && Objects.equals(state.getVersion(), claim.financeStateVersion());
        if (!valid) {
            throw new IllegalStateException("clearing failure lease identity or version is stale");
        }
    }

    /** 已持久化重试次数必须为非负数，消息值不能替代数据库事实。 */
    private int requiredRetryCount(Integer retryCount) {
        if (retryCount == null || retryCount < 0) {
            throw new IllegalStateException("clearing failure retry count is invalid");
        }
        return retryCount;
    }

    private record FailureDecision(ClearingStateEnum targetState,
                                   ClearingFailureCodeEnum recordedCode,
                                   int retryCount,
                                   LocalDateTime nextRetryTime) {
    }
}
