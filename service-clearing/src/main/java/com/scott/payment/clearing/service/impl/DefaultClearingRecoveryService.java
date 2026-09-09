package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.config.ClearingProperties;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.domain.state.ClearingAnomalyTypeEnum;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.clearing.entity.ClearingCompensationCandidateDO;
import com.scott.payment.clearing.entity.ClearingTransactionEventOutboxDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.mapper.ClearingTransactionEventOutboxMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.service.ClearingProjectionService;
import com.scott.payment.clearing.service.ClearingAnomalyService;
import com.scott.payment.clearing.service.ClearingRecoveryService;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.ClearingRetryDueMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingRecoveryService
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分补偿恢复实现。数据库 finance state 和交易 Outbox 在同一短事务内更新， 确定性 event_no 唯一键负责重复扫描去重，Redis 不参与财务幂等。
 * @status : update
 */
@Service
@Slf4j
public class DefaultClearingRecoveryService implements ClearingRecoveryService {

    /**
     * 处理失败码，用于补偿策略、告警聚合和后台排障，不直接暴露底层异常。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String FAILURE_CODE = ClearingFailureCodeEnum.CLEARING_COMPENSATION_DUE.name();
    /**
     * {@code OUTBOX_MAX_RETRY_COUNT}，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int OUTBOX_MAX_RETRY_COUNT = 10;
    /**
     * {@code RESCHEDULE_DELAY_MINUTES}常量，统一 {@code DefaultClearingRecoveryService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long RESCHEDULE_DELAY_MINUTES = 1L;
    private static final Set<String> RETRYABLE_STATUSES = Set.of(
            "NOT_CLEARED", "PENDING", "PROCESSING", "FAILED", "WAITING_SOURCE");

    private final ClearingTransactionFinanceStateMapper financeStateMapper;
    private final ClearingTransactionEventOutboxMapper outboxMapper;
    private final ClearingProjectionService projectionService;
    private final GlobalIdGenerator idGenerator;
    private final ClearingProperties properties;
    private final ClearingAnomalyService anomalyService;

    public DefaultClearingRecoveryService(ClearingTransactionFinanceStateMapper financeStateMapper,
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
    public String recover(ClearingCompensationCandidateDO candidate, LocalDateTime now) {
        requireCandidate(candidate, now);
        ClearingTransactionFinanceStateDO state = financeStateMapper.selectForUpdate(
                candidate.getTransactionId(), candidate.getTransactionDateTime());
        if (state == null) {
            if (!"MISSING_FINANCE_STATE".equals(candidate.getReason())) {
                return "SKIPPED_STALE";
            }
            financeStateMapper.insertIfAbsent(
                    "FS" + idGenerator.nextId(), toOperation(candidate), now);
            state = financeStateMapper.selectForUpdate(
                    candidate.getTransactionId(), candidate.getTransactionDateTime());
        }
        if (!validIdentity(candidate, state)) {
            return "SKIPPED_STALE";
        }

        ClearingStateEnum status = parseStatus(state.getClearingStatus());
        if (status.isCompletedTerminal()) {
            return repairCompletedProjection(candidate, state, status, now);
        }
        if (status == ClearingStateEnum.MANUAL_REVIEW) {
            return "MANUAL_REVIEW";
        }
        if (!RETRYABLE_STATUSES.contains(status.name()) || !stillEligible(candidate, state, now)) {
            return "SKIPPED_STALE";
        }

        int retryCount = value(state.getClearingRetryCount());
        if (retryCount >= properties.getMaxRetryCount()) {
            int affected = financeStateMapper.escalateCompensationReview(
                    state.getTransactionId(), state.getTransactionDateTime(), status.name(),
                    requiredVersion(state), expectedDeadline(status, state),
                    "clearing compensation exhausted after " + retryCount + " retries", now);
            if (affected != 1) {
                return "SKIPPED_STALE";
            }
            updateProjection(candidate, ClearingStateEnum.MANUAL_REVIEW,
                    ClearingFailureCodeEnum.CLEARING_RETRY_EXHAUSTED.name(), now);
            anomalyService.record(toFacts(candidate), state.getFinanceStateId(), value(state.getClearingRevision()),
                    ClearingAnomalyTypeEnum.MANUAL_REVIEW,
                    ClearingFailureCodeEnum.CLEARING_RETRY_EXHAUSTED.name(),
                    "clearing compensation retry limit reached", now);
            return "MANUAL_REVIEW";
        }

        int nextRetryCount = retryCount + 1;
        LocalDateTime deliverAt = now.plusMinutes(RESCHEDULE_DELAY_MINUTES)
                .truncatedTo(ChronoUnit.MILLIS);
        String previousReason = StringUtils.hasText(state.getLastFailureCode())
                ? state.getLastFailureCode() : candidate.getReason();
        String summary = "compensation retry for " + previousReason;
        int affected = financeStateMapper.scheduleCompensationRetry(
                state.getTransactionId(), state.getTransactionDateTime(), status.name(),
                requiredVersion(state), expectedDeadline(status, state), nextRetryCount, deliverAt,
                FAILURE_CODE, summary, now);
        if (affected != 1) {
            return "SKIPPED_STALE";
        }

        ClearingTransactionEventOutboxDO outbox = retryOutbox(
                candidate, state, nextRetryCount, deliverAt, now);
        boolean inserted = persistRetryOutbox(outbox);
        updateProjection(candidate, ClearingStateEnum.FAILED, FAILURE_CODE, now);
        return inserted ? "RETRY_SCHEDULED" : "ALREADY_SCHEDULED";
    }

    /**
     * 在 finance state 行锁事务内核对稳定事件身份；非同一事件的唯一键冲突必须使事务回滚。
     */
    private boolean persistRetryOutbox(ClearingTransactionEventOutboxDO expected) {
        ClearingTransactionEventOutboxDO existing = outboxMapper.selectByEventNoForUpdate(
                expected.getEventNo(), expected.getTransactionDateTime());
        if (existing != null) {
            if (!sameOutboxIdentity(existing, expected)) {
                throw new IllegalStateException("clearing retry outbox identity is inconsistent");
            }
            return false;
        }
        if (outboxMapper.insertLogical(expected) != 1) {
            throw new IllegalStateException("clearing retry outbox insert did not affect one row");
        }
        return true;
    }

    /** 确定性事件号冲突后核对完整消息路由身份，防止错误幂等。 */
    private boolean sameOutboxIdentity(ClearingTransactionEventOutboxDO actual,
                                       ClearingTransactionEventOutboxDO expected) {
        return Objects.equals(actual.getEventNo(), expected.getEventNo())
                && Objects.equals(actual.getAggregateType(), expected.getAggregateType())
                && Objects.equals(actual.getAggregateNo(), expected.getAggregateNo())
                && Objects.equals(actual.getTransactionId(), expected.getTransactionId())
                && Objects.equals(actual.getOperationId(), expected.getOperationId())
                && Objects.equals(actual.getMerchantId(), expected.getMerchantId())
                && Objects.equals(actual.getMerchantOrderNo(), expected.getMerchantOrderNo())
                && Objects.equals(actual.getTransactionType(), expected.getTransactionType())
                && Objects.equals(actual.getEventType(), expected.getEventType())
                && Objects.equals(actual.getTopic(), expected.getTopic())
                && Objects.equals(actual.getTag(), expected.getTag())
                && Objects.equals(actual.getMessageKey(), expected.getMessageKey())
                && Objects.equals(actual.getMessageGroup(), expected.getMessageGroup())
                && Objects.equals(actual.getDeliveryMode(), expected.getDeliveryMode())
                && Objects.equals(actual.getDeliverAt(), expected.getDeliverAt())
                && Objects.equals(actual.getPayloadJson(), expected.getPayloadJson());
    }

    /** 已完成财务事实只修复缺失查询投影，不重复写清分明细或幂等记录。 */
    private String repairCompletedProjection(ClearingCompensationCandidateDO candidate,
                                             ClearingTransactionFinanceStateDO state,
                                             ClearingStateEnum status,
                                             LocalDateTime now) {
        if (!"PROJECTION_MISMATCH".equals(candidate.getReason())) {
            return "ALREADY_COMPLETED";
        }
        anomalyService.record(toFacts(candidate), state.getFinanceStateId(), value(state.getClearingRevision()),
                ClearingAnomalyTypeEnum.PROJECTION_MISMATCH, "PROJECTION_MISMATCH",
                "clearing finance state and transaction projection are inconsistent", now);
        try {
            projectionService.updateResolvingLocator(toFacts(candidate), status, null, now);
            anomalyService.resolve(state.getTransactionId(), state.getTransactionDateTime(),
                    state.getFinanceStateId() + ":" + value(state.getClearingRevision()), now);
            return "PROJECTION_REPAIRED";
        } catch (ClearingProcessingException | IllegalStateException exception) {
            log.warn("event: CLEARING_COMPENSATION_PROJECTION_STALE transactionId: {} status: {} reason: {}",
                    state.getTransactionId(), status, exception.getMessage());
            return "SKIPPED_STALE";
        }
    }

    /** 补偿写入前重新核对状态、版本、截止时间和失败分类。 */
    private boolean stillEligible(ClearingCompensationCandidateDO candidate,
                                  ClearingTransactionFinanceStateDO state,
                                  LocalDateTime now) {
        if (candidate.getFinanceStateId() != null
                && (!Objects.equals(candidate.getFinanceStateVersion(), state.getVersion())
                    || !Objects.equals(candidate.getClearingStatus(), state.getClearingStatus()))) {
            return false;
        }
        return switch (state.getClearingStatus()) {
            case "PROCESSING" -> state.getProcessingDeadline() != null
                    && !state.getProcessingDeadline().isAfter(now)
                    && Objects.equals(candidate.getProcessingDeadline(), state.getProcessingDeadline());
            case "FAILED", "WAITING_SOURCE" -> state.getNextRetryTime() != null
                    && !state.getNextRetryTime().isAfter(now);
            case "NOT_CLEARED", "PENDING" -> true;
            default -> false;
        };
    }

    /** 仅 PROCESSING 补偿要求扫描租约截止点与锁读状态一致，其他可恢复状态不携带租约身份。 */
    private LocalDateTime expectedDeadline(ClearingStateEnum status,
                                           ClearingTransactionFinanceStateDO state) {
        return status == ClearingStateEnum.PROCESSING ? state.getProcessingDeadline() : null;
    }

    /** 补偿状态提交后刷新动作及生命周期投影，使用扫描时冻结的真实分片时间。 */
    private void updateProjection(ClearingCompensationCandidateDO candidate,
                                  ClearingStateEnum status,
                                  String failureCode,
                                  LocalDateTime now) {
        try {
            projectionService.updateResolvingLocator(toFacts(candidate), status, failureCode, now);
        } catch (ClearingProcessingException | IllegalStateException exception) {
            log.warn("event: CLEARING_COMPENSATION_PROJECTION_DEFERRED transactionId: {} status: {} reason: {}",
                    candidate.getTransactionId(), status, exception.getMessage());
        }
    }

    /** 使用财务状态号和下一重试序号构造确定性补偿 Outbox。 */
    private ClearingTransactionEventOutboxDO retryOutbox(ClearingCompensationCandidateDO candidate,
                                                          ClearingTransactionFinanceStateDO state,
                                                          int retryCount,
                                                          LocalDateTime deliverAt,
                                                          LocalDateTime now) {
        int revision = value(state.getClearingRevision());
        String eventNo = deterministicEventNo(
                state.getFinanceStateId(), revision, retryCount, FAILURE_CODE, deliverAt);
        ClearingRetryDueMessage message = new ClearingRetryDueMessage();
        message.setMessageId(eventNo);
        message.setCreatedAt(now);
        message.setRetryCount(0);
        message.setTransactionId(candidate.getTransactionId());
        message.setOperationId(candidate.getOperationId());
        message.setMerchantId(candidate.getMerchantId());
        message.setMerchantOrderNo(candidate.getMerchantOrderNo());
        message.setTransactionType(candidate.getTransactionType());
        message.setTransactionStatus(candidate.getTransactionStatus());
        message.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        message.setTransactionDateTime(candidate.getTransactionDateTime());
        message.setSourceEventNo("COMPENSATION:" + state.getFinanceStateId());
        message.setExpectedClearingRevision(revision);
        message.setClearingRetryCount(retryCount);
        message.setRetryReasonCode(FAILURE_CODE);
        message.setDeliverAt(deliverAt.toInstant(ZoneOffset.UTC));

        ClearingTransactionEventOutboxDO outbox = new ClearingTransactionEventOutboxDO();
        outbox.setEventNo(eventNo);
        outbox.setAggregateType("TRANSACTION_CLEARING_RETRY");
        outbox.setAggregateNo(state.getFinanceStateId() + ":" + retryCount);
        outbox.setTransactionId(candidate.getTransactionId());
        outbox.setOperationId(candidate.getOperationId());
        outbox.setMerchantId(candidate.getMerchantId());
        outbox.setMerchantOrderNo(candidate.getMerchantOrderNo());
        outbox.setTransactionType(candidate.getTransactionType());
        outbox.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        outbox.setEventStatus("INIT");
        outbox.setTopic(MqTopic.PAYMENT_CLEARING_DELAY);
        outbox.setTag(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        outbox.setMessageKey(eventNo);
        outbox.setDeliveryMode("SCHEDULED");
        outbox.setDeliverAt(deliverAt);
        outbox.setPayloadJson(JsonUtils.toJsonString(message));
        outbox.setRetryCount(0);
        outbox.setMaxRetryCount(OUTBOX_MAX_RETRY_COUNT);
        outbox.setNextRetryTime(now);
        outbox.setEventTime(now);
        outbox.setTransactionDateTime(candidate.getTransactionDateTime());
        outbox.setTransactionUtcTime(candidate.getTransactionUtcTime());
        outbox.setTransactionTimeZone(candidate.getTransactionTimeZone());
        outbox.setVersion(0);
        outbox.setDeleted(0);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        return outbox;
    }

    /**
     * 由资金状态、修订号、重试轮次、失败码和投递时间派生补偿事件号。
     * <p>
     * 相同补偿决策必须生成相同 Outbox 唯一键，防止扫描任务重复创建延时消息。
     */
    private String deterministicEventNo(String financeStateId,
                                        int revision,
                                        int retryCount,
                                        String failureCode,
                                        LocalDateTime deliverAt) {
        String material = financeStateId + "|" + revision + "|" + retryCount
                + "|" + failureCode + "|" + deliverAt;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return "CC" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 校验补偿扫描快照与主库财务状态仍指向同一动作、商户和分片，防止跨分片误恢复。
     *
     * @param candidate 补偿扫描候选
     * @param state 主库当前财务状态
     * @return 身份和版本完整且完全一致时返回 true
     */
    private boolean validIdentity(ClearingCompensationCandidateDO candidate,
                                  ClearingTransactionFinanceStateDO state) {
        return state != null
                && StringUtils.hasText(state.getFinanceStateId())
                && Objects.equals(candidate.getTransactionId(), state.getTransactionId())
                && Objects.equals(candidate.getOperationId(), state.getOperationId())
                && Objects.equals(candidate.getMerchantId(), state.getMerchantId())
                && Objects.equals(candidate.getTransactionDateTime(), state.getTransactionDateTime())
                && state.getVersion() != null;
    }

    /**
     * 将数据库清分状态转换为受控枚举，未知状态必须阻断补偿而非猜测迁移。
     *
     * @param value 数据库状态值
     * @return 支持的清分状态枚举
     */
    private ClearingStateEnum parseStatus(String value) {
        try {
            return ClearingStateEnum.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("unsupported clearing state during compensation", exception);
        }
    }

    private ClearingTransactionOperationDO toOperation(ClearingCompensationCandidateDO row) {
        ClearingTransactionOperationDO operation = new ClearingTransactionOperationDO();
        operation.setTransactionId(row.getTransactionId());
        operation.setOperationId(row.getOperationId());
        operation.setSourceTransactionId(row.getSourceTransactionId());
        operation.setMerchantId(row.getMerchantId());
        operation.setMerchantOrderNo(row.getMerchantOrderNo());
        operation.setTransactionType(row.getTransactionType());
        operation.setTransactionStatus(row.getTransactionStatus());
        operation.setLabelCurrency(row.getLabelCurrency());
        operation.setLabelAmount(row.getLabelAmount());
        operation.setApprovedCurrency(row.getApprovedCurrency());
        operation.setApprovedAmount(row.getApprovedAmount());
        operation.setTransactionCurrency(row.getTransactionCurrency());
        operation.setTransactionAmount(row.getTransactionAmount());
        operation.setCurrencyExponent(row.getCurrencyExponent());
        operation.setTransactionDateTime(row.getTransactionDateTime());
        operation.setTransactionUtcTime(row.getTransactionUtcTime());
        operation.setTransactionTimeZone(row.getTransactionTimeZone());
        operation.setVersion(row.getOperationVersion());
        return operation;
    }

    private ClearingOperationFacts toFacts(ClearingCompensationCandidateDO row) {
        return new ClearingOperationFacts(
                row.getTransactionId(), row.getOperationId(), row.getSourceTransactionId(), row.getMerchantId(),
                row.getMerchantOrderNo(), row.getTransactionType(), row.getTransactionStatus(),
                row.getLabelCurrency(), row.getLabelAmount(), row.getApprovedCurrency(), row.getApprovedAmount(),
                row.getTransactionCurrency(), row.getTransactionAmount(), row.getCurrencyExponent(),
                row.getTransactionDateTime(), row.getTransactionUtcTime(), row.getTransactionTimeZone(),
                row.getOperationVersion());
    }

    private int requiredVersion(ClearingTransactionFinanceStateDO state) {
        if (state.getVersion() == null || state.getVersion() < 0) {
            throw new IllegalStateException("clearing finance state version is invalid");
        }
        return state.getVersion();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 校验补偿候选具备交易、动作、商户、分片时间和原因等最小恢复身份。
     *
     * @param candidate 补偿扫描候选
     * @param now 本轮补偿统一时间
     */
    private void requireCandidate(ClearingCompensationCandidateDO candidate, LocalDateTime now) {
        if (candidate == null || now == null || !StringUtils.hasText(candidate.getTransactionId())
                || !StringUtils.hasText(candidate.getOperationId()) || !StringUtils.hasText(candidate.getMerchantId())
                || candidate.getTransactionDateTime() == null || !StringUtils.hasText(candidate.getReason())) {
            throw new IllegalArgumentException("clearing compensation candidate identity and time are required");
        }
    }
}
