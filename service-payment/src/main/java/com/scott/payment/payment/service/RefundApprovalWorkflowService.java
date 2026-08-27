package com.scott.payment.payment.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.RefundExecutionMessage;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.refund.RefundApprovalStatusEnum;
import com.scott.payment.payment.domain.refund.RefundRequestSourceEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.entity.TransactionFlowEventDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.entity.TransactionRefundApprovalDO;
import com.scott.payment.payment.mapper.TransactionFlowEventMapper;
import com.scott.payment.payment.mapper.TransactionOperationMapper;
import com.scott.payment.payment.mapper.TransactionRefundApprovalMapper;
import com.scott.payment.payment.service.dto.RefundApprovalDecisionCommandDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalWorkflowService
 * @date : 2026-08-06 00:00
 * @description : 退款审批领域服务，在同一本地事务内完成审批 CAS、退款动作推进、流程审计和执行 Outbox 写入。
 * @status : create
 */
@Service
public class RefundApprovalWorkflowService {

    private static final String APPROVAL_ID_PREFIX = "RA";
    private static final String EXECUTION_EVENT_PREFIX = "RE";
    private static final String FLOW_EVENT_PREFIX = "FE";
    private static final String PAYMENT_TRANSACTION_AGGREGATE = "PAYMENT_TRANSACTION";
    private static final String EVENT_STATUS_INIT = "INIT";
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";
    private static final int INITIAL_VERSION = 0;
    private static final int NOT_DELETED = 0;
    private static final int DEFAULT_MAX_RETRY_COUNT = 200;

    private final TransactionRefundApprovalMapper approvalMapper;
    private final TransactionOperationMapper operationMapper;
    private final TransactionFlowEventMapper flowEventMapper;
    private final TransactionEventOutboxService outboxService;
    private final TransactionRecordService transactionRecordService;

    /**
     * 创建退款审批工作流服务。
     *
     * @param approvalMapper 审批普通表 Mapper
     * @param operationMapper 退款动作 Mapper
     * @param flowEventMapper 交易流程事件 Mapper
     * @param outboxService 交易 Outbox 服务
     * @param transactionRecordService 交易事实服务
     */
    public RefundApprovalWorkflowService(TransactionRefundApprovalMapper approvalMapper,
                                         TransactionOperationMapper operationMapper,
                                         TransactionFlowEventMapper flowEventMapper,
                                         TransactionEventOutboxService outboxService,
                                         TransactionRecordService transactionRecordService) {
        this.approvalMapper = approvalMapper;
        this.operationMapper = operationMapper;
        this.flowEventMapper = flowEventMapper;
        this.outboxService = outboxService;
        this.transactionRecordService = transactionRecordService;
    }

    /**
     * 为已落库且等待审批的退款动作创建唯一审批任务。
     *
     * @param commandDTO 退款内部命令
     * @param sourceOrderDO 原生命周期主单
     * @param resultDTO 已落库退款动作结果
     * @param policyCode 审批策略编码
     * @param expireMinutes 审批有效分钟数
     * @param now 当前业务时间
     * @return 新审批任务
     */
    @DS(DataSourceName.TRANSACTION)
    public TransactionRefundApprovalDO createPendingApproval(PaymentCreateCommandDTO commandDTO,
                                                              TransactionOrderDO sourceOrderDO,
                                                              PaymentCreateResultDTO resultDTO,
                                                              String policyCode,
                                                              long expireMinutes,
                                                              LocalDateTime now) {
        validateApprovalCreation(commandDTO, sourceOrderDO, resultDTO, policyCode);
        LocalDateTime actualNow = now == null ? LocalDateTime.now() : now;
        RefundRequestSourceEnum requestSource = RefundRequestSourceEnum.from(commandDTO.getRequestSource());
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfo = commandDTO.getTransactionInfo();
        TransactionRefundApprovalDO approval = new TransactionRefundApprovalDO();
        approval.setApprovalId(PaymentOrderNoGenerator.nextOrderNo(APPROVAL_ID_PREFIX, commandDTO.getTransactionDateTime()));
        approval.setRefundTransactionId(resultDTO.getTransactionId());
        approval.setRefundTransactionDateTime(commandDTO.getTransactionDateTime());
        approval.setSourceTransactionId(transactionInfo.getSourceTransactionId());
        approval.setSourceTransactionDateTime(transactionInfo.getSourceTransactionDateTime());
        approval.setRootTransactionDateTime(transactionInfo.getRootTransactionDateTime());
        approval.setMerchantId(commandDTO.getMerchantId());
        approval.setApprovalStatus(RefundApprovalStatusEnum.PENDING.getCode());
        approval.setApprovalPolicyCode(policyCode);
        approval.setApprovalPolicySnapshot(policySnapshot(policyCode, commandDTO));
        approval.setCurrentApprovalLevel(1);
        approval.setTotalApprovalLevels(1);
        approval.setApplicantType(requestSource.getApplicantType());
        approval.setApplicantId(firstText(commandDTO.getApplicantId(), commandDTO.getMerchantId()));
        approval.setApplicantName(commandDTO.getApplicantName());
        approval.setExpireTime(actualNow.plusMinutes(Math.max(1L, expireMinutes)));
        approval.setVersion(INITIAL_VERSION);
        approval.setCreateTime(actualNow);
        approval.setUpdateTime(actualNow);
        requireSingleRow(approvalMapper.insertApproval(approval), "refund approval insert failed");
        saveFlowEvent(approval, resultDTO.getOperationId(), "REFUND_APPROVAL_CREATED", "PENDING",
                "退款申请进入审批队列", approval.getApplicantType(), approval.getApplicantId(), actualNow);
        return approval;
    }

    /**
     * 审批通过退款，并生成稳定执行事件。
     *
     * @param commandDTO 审批决策命令
     * @return 已批准审批任务
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public TransactionRefundApprovalDO approve(RefundApprovalDecisionCommandDTO commandDTO) {
        validateDecisionCommand(commandDTO, false);
        LocalDateTime now = LocalDateTime.now();
        TransactionRefundApprovalDO approval = requireApproval(commandDTO.getApprovalId());
        TransactionRefundApprovalDO duplicate = resolveDuplicateDecision(
                approval, commandDTO, RefundApprovalStatusEnum.APPROVED);
        if (duplicate != null) {
            return duplicate;
        }
        validatePendingDecision(approval, commandDTO, now);
        TransactionOperationDO operation = requirePendingOperation(approval);
        String executionEventId = PaymentOrderNoGenerator.nextOrderNo(
                EXECUTION_EVENT_PREFIX, approval.getRefundTransactionDateTime());
        requireSingleRow(operationMapper.approveRefundExecution(
                operation.getTransactionId(), operation.getTransactionDateTime(), operation.getVersion(), now),
                "refund action approval state conflict");
        requireSingleRow(approvalMapper.decide(
                approval.getApprovalId(), approval.getVersion(), RefundApprovalStatusEnum.APPROVED.getCode(),
                commandDTO.getOperatorId(), commandDTO.getOperatorName(), safeReason(commandDTO.getReason()),
                commandDTO.getDecisionRequestId(), executionEventId, now),
                "refund approval decision state conflict");
        saveExecutionOutbox(approval, operation, executionEventId, operation.getVersion() + 1, now);
        saveFlowEvent(approval, operation.getOperationId(), "REFUND_APPROVAL_APPROVED", "SUCCESS",
                safeReason(commandDTO.getReason()), "ADMIN", commandDTO.getOperatorId(), now);
        applyDecisionSnapshot(approval, RefundApprovalStatusEnum.APPROVED, commandDTO, executionEventId, now);
        return approval;
    }

    /**
     * 拒绝待审批退款；只终结退款动作的隐式额度占用，不修改原主单可退金额。
     *
     * @param commandDTO 审批决策命令，原因必填
     * @return 已拒绝审批任务
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public TransactionRefundApprovalDO reject(RefundApprovalDecisionCommandDTO commandDTO) {
        validateDecisionCommand(commandDTO, true);
        LocalDateTime now = LocalDateTime.now();
        TransactionRefundApprovalDO approval = requireApproval(commandDTO.getApprovalId());
        TransactionRefundApprovalDO duplicate = resolveDuplicateDecision(
                approval, commandDTO, RefundApprovalStatusEnum.REJECTED);
        if (duplicate != null) {
            return duplicate;
        }
        validatePendingDecision(approval, commandDTO, now);
        TransactionOperationDO operation = requirePendingOperation(approval);
        String reason = safeReason(commandDTO.getReason());
        requireSingleRow(approvalMapper.decide(
                approval.getApprovalId(), approval.getVersion(), RefundApprovalStatusEnum.REJECTED.getCode(),
                commandDTO.getOperatorId(), commandDTO.getOperatorName(), reason,
                commandDTO.getDecisionRequestId(), null, now),
                "refund approval decision state conflict");
        if (!transactionRecordService.terminateRefundBeforeChannel(
                operation, "REFUND_APPROVAL_REJECTED", reason, "REFUND_APPROVAL",
                approval.getApprovalId(), "ADMIN", commandDTO.getOperatorId(), now)) {
            throw new ServiceException(ApiResultEnum.REFUND_APPROVAL_STATE_CONFLICT);
        }
        saveFlowEvent(approval, operation.getOperationId(), "REFUND_APPROVAL_REJECTED", "FAILED",
                reason, "ADMIN", commandDTO.getOperatorId(), now);
        applyDecisionSnapshot(approval, RefundApprovalStatusEnum.REJECTED, commandDTO, null, now);
        return approval;
    }

    /**
     * 将单个到期待审批退款推进为 EXPIRED，并激活既有终态通知任务。
     *
     * @param approvalId 审批单号
     * @param now 当前业务时间
     * @return true 表示本次实际完成过期处理
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public boolean expire(String approvalId, LocalDateTime now) {
        LocalDateTime actualNow = now == null ? LocalDateTime.now() : now;
        TransactionRefundApprovalDO approval = requireApproval(approvalId);
        if (!RefundApprovalStatusEnum.PENDING.getCode().equals(approval.getApprovalStatus())) {
            return RefundApprovalStatusEnum.EXPIRED.getCode().equals(approval.getApprovalStatus());
        }
        if (approval.getExpireTime() == null || approval.getExpireTime().isAfter(actualNow)) {
            return false;
        }
        TransactionOperationDO operation = requirePendingOperation(approval);
        String requestId = "EXPIRE:" + approval.getApprovalId();
        requireSingleRow(approvalMapper.decide(
                approval.getApprovalId(), approval.getVersion(), RefundApprovalStatusEnum.EXPIRED.getCode(),
                "SYSTEM", "SYSTEM", "refund approval expired", requestId, null, actualNow),
                "refund approval expiration state conflict");
        if (!transactionRecordService.terminateRefundBeforeChannel(
                operation, "REFUND_APPROVAL_EXPIRED", "refund approval expired", "REFUND_APPROVAL",
                approval.getApprovalId(), "SYSTEM", "SYSTEM", actualNow)) {
            throw new ServiceException(ApiResultEnum.REFUND_APPROVAL_STATE_CONFLICT);
        }
        saveFlowEvent(approval, operation.getOperationId(), "REFUND_APPROVAL_EXPIRED", "FAILED",
                "refund approval expired", "SYSTEM", "SYSTEM", actualNow);
        return true;
    }

    /**
     * 恢复已批准但仍未开始渠道调用的退款执行事件。
     *
     * <p>恢复只复用审批记录中的 execution_event_id。动作已经进入渠道处理中或终态时不重投，
     * 防止把未知渠道结果当成未发送请求。</p>
     *
     * @param approvalId 审批单号
     * @param now 恢复时间
     * @return true 表示事件已存在或已按原事件号补建
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public boolean recoverApprovedExecution(String approvalId, LocalDateTime now) {
        LocalDateTime actualNow = now == null ? LocalDateTime.now() : now;
        TransactionRefundApprovalDO approval = requireApproval(approvalId);
        if (!RefundApprovalStatusEnum.APPROVED.getCode().equals(approval.getApprovalStatus())
                || !StringUtils.hasText(approval.getExecutionEventId())) {
            return false;
        }
        TransactionOperationDO operation = operationMapper.selectByTransactionId(
                approval.getRefundTransactionId(), approval.getRefundTransactionDateTime());
        if (operation == null
                || !PaymentTransactionStatusEnum.PENDING.getCode().equals(operation.getTransactionStatus())
                || !PaymentProcessStageEnum.WAITING_EXECUTION.getCode().equals(operation.getProcessStage())) {
            return false;
        }
        if (outboxService.recoverForRedelivery(
                approval.getExecutionEventId(), operation.getTransactionDateTime(),
                MqTag.REFUND_EXECUTION_REQUESTED, actualNow)) {
            return true;
        }
        saveExecutionOutbox(
                approval, operation, approval.getExecutionEventId(), operation.getVersion(), actualNow);
        return true;
    }

    private TransactionRefundApprovalDO requireApproval(String approvalId) {
        TransactionRefundApprovalDO approval = approvalMapper.selectByApprovalIdForUpdate(approvalId);
        if (approval == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND.getCode(), "refund approval does not exist");
        }
        return approval;
    }

    private TransactionOperationDO requirePendingOperation(TransactionRefundApprovalDO approval) {
        TransactionOperationDO operation = operationMapper.selectByTransactionId(
                approval.getRefundTransactionId(), approval.getRefundTransactionDateTime());
        if (operation == null
                || !PaymentTransactionStatusEnum.PENDING.getCode().equals(operation.getTransactionStatus())
                || !PaymentProcessStageEnum.WAITING_APPROVAL.getCode().equals(operation.getProcessStage())) {
            throw new ServiceException(ApiResultEnum.REFUND_APPROVAL_STATE_CONFLICT);
        }
        return operation;
    }

    private void validatePendingDecision(TransactionRefundApprovalDO approval,
                                         RefundApprovalDecisionCommandDTO commandDTO,
                                         LocalDateTime now) {
        if (!RefundApprovalStatusEnum.PENDING.getCode().equals(approval.getApprovalStatus())) {
            throw new ServiceException(ApiResultEnum.REFUND_APPROVAL_STATE_CONFLICT);
        }
        if (approval.getExpireTime() != null && !approval.getExpireTime().isAfter(now)) {
            throw new ServiceException(ApiResultEnum.REFUND_APPROVAL_STATE_CONFLICT.getCode(),
                    "refund approval has expired");
        }
        if (!Objects.equals(approval.getVersion(), commandDTO.getExpectedVersion())) {
            throw new ServiceException(ApiResultEnum.REFUND_APPROVAL_STATE_CONFLICT.getCode(),
                    "refund approval version conflict");
        }
        if ("ADMIN".equals(approval.getApplicantType())
                && Objects.equals(approval.getApplicantId(), commandDTO.getOperatorId())) {
            throw new ServiceException(ApiResultEnum.REFUND_ACTION_NOT_ALLOWED.getCode(),
                    "admin can not approve own refund");
        }
    }

    private TransactionRefundApprovalDO resolveDuplicateDecision(TransactionRefundApprovalDO approval,
                                                                  RefundApprovalDecisionCommandDTO commandDTO,
                                                                  RefundApprovalStatusEnum targetStatus) {
        if (targetStatus.getCode().equals(approval.getApprovalStatus())
                && Objects.equals(commandDTO.getDecisionRequestId(), approval.getDecisionRequestId())) {
            return approval;
        }
        if (!RefundApprovalStatusEnum.PENDING.getCode().equals(approval.getApprovalStatus())) {
            throw new ServiceException(ApiResultEnum.REFUND_APPROVAL_STATE_CONFLICT);
        }
        return null;
    }

    private void saveExecutionOutbox(TransactionRefundApprovalDO approval,
                                     TransactionOperationDO operation,
                                     String eventId,
                                     int expectedOperationVersion,
                                     LocalDateTime now) {
        RefundExecutionMessage message = new RefundExecutionMessage();
        message.setMessageId(eventId);
        message.setCreatedAt(now);
        message.setTraceId(TraceContext.getOrCreateTraceId());
        message.setRetryCount(0);
        message.setApprovalId(approval.getApprovalId());
        message.setRefundTransactionId(approval.getRefundTransactionId());
        message.setRefundTransactionDateTime(approval.getRefundTransactionDateTime());
        message.setSourceTransactionId(approval.getSourceTransactionId());
        message.setSourceTransactionDateTime(approval.getSourceTransactionDateTime());
        message.setRootTransactionDateTime(approval.getRootTransactionDateTime());
        message.setExpectedOperationVersion(expectedOperationVersion);
        message.setEventType(MqTag.REFUND_EXECUTION_REQUESTED);

        TransactionEventOutboxDO outbox = new TransactionEventOutboxDO();
        outbox.setEventNo(eventId);
        outbox.setAggregateType(PAYMENT_TRANSACTION_AGGREGATE);
        outbox.setAggregateNo(operation.getOperationId());
        outbox.setTransactionId(operation.getTransactionId());
        outbox.setOperationId(operation.getOperationId());
        outbox.setMerchantId(operation.getMerchantId());
        outbox.setMerchantOrderNo(operation.getMerchantOrderNo());
        outbox.setTransactionType(operation.getTransactionType());
        outbox.setEventType(MqTag.REFUND_EXECUTION_REQUESTED);
        outbox.setEventStatus(EVENT_STATUS_INIT);
        outbox.setTopic(MqTopic.PAYMENT_TRANSACTION_FIFO);
        outbox.setTag(MqTag.REFUND_EXECUTION_REQUESTED);
        outbox.setMessageKey(eventId);
        outbox.setMessageGroup(operation.getOperationId());
        outbox.setPayloadJson(JsonUtils.toJsonString(message));
        outbox.setEventTime(now);
        outbox.setTransactionDateTime(operation.getTransactionDateTime());
        outbox.setTransactionUtcTime(toUtcTime(operation.getTransactionDateTime()));
        outbox.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        outbox.setRetryCount(0);
        outbox.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        outbox.setNextRetryTime(now);
        outbox.setVersion(INITIAL_VERSION);
        outbox.setDeleted(NOT_DELETED);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        outboxService.save(outbox);
    }

    private void saveFlowEvent(TransactionRefundApprovalDO approval,
                               String operationId,
                               String eventType,
                               String eventStatus,
                               String content,
                               String operatorType,
                               String operatorId,
                               LocalDateTime now) {
        TransactionFlowEventDO event = new TransactionFlowEventDO();
        event.setFlowEventId(PaymentOrderNoGenerator.nextOrderNo(FLOW_EVENT_PREFIX, approval.getRefundTransactionDateTime()));
        event.setTransactionId(approval.getRefundTransactionId());
        event.setOperationId(operationId);
        event.setEventType(eventType);
        event.setEventStage("REFUND_APPROVAL");
        event.setEventStatus(eventStatus);
        event.setEventName(eventType);
        event.setEventContent(safeReason(content));
        event.setPreviousStatus(RefundApprovalStatusEnum.PENDING.getCode());
        event.setCurrentStatus(eventType.substring("REFUND_APPROVAL_".length()));
        event.setOperatorType(operatorType);
        event.setOperatorId(operatorId);
        event.setReferenceType("REFUND_APPROVAL");
        event.setReferenceId(approval.getApprovalId());
        event.setEventTime(now);
        event.setTransactionDateTime(approval.getRefundTransactionDateTime());
        event.setTransactionUtcTime(toUtcTime(approval.getRefundTransactionDateTime()));
        event.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        event.setCreateTime(now);
        requireSingleRow(flowEventMapper.insertLogical(event), "refund approval flow event insert failed");
    }

    private String policySnapshot(String policyCode, PaymentCreateCommandDTO commandDTO) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyCode", policyCode);
        snapshot.put("refundScope", commandDTO.getRefundScope());
        snapshot.put("requestSource", RefundRequestSourceEnum.from(commandDTO.getRequestSource()).getCode());
        return JsonUtils.toJsonString(snapshot);
    }

    private void applyDecisionSnapshot(TransactionRefundApprovalDO approval,
                                       RefundApprovalStatusEnum status,
                                       RefundApprovalDecisionCommandDTO commandDTO,
                                       String executionEventId,
                                       LocalDateTime now) {
        approval.setApprovalStatus(status.getCode());
        approval.setApprovalOperatorId(commandDTO.getOperatorId());
        approval.setApprovalOperatorName(commandDTO.getOperatorName());
        approval.setApprovalReason(safeReason(commandDTO.getReason()));
        approval.setApprovalTime(now);
        approval.setDecisionRequestId(commandDTO.getDecisionRequestId());
        approval.setExecutionEventId(executionEventId);
        approval.setVersion(approval.getVersion() + 1);
        approval.setUpdateTime(now);
    }

    private void validateApprovalCreation(PaymentCreateCommandDTO commandDTO,
                                          TransactionOrderDO sourceOrderDO,
                                          PaymentCreateResultDTO resultDTO,
                                          String policyCode) {
        if (commandDTO == null || sourceOrderDO == null || resultDTO == null
                || commandDTO.getTransactionInfo() == null
                || commandDTO.getTransactionDateTime() == null
                || !StringUtils.hasText(resultDTO.getTransactionId())
                || !StringUtils.hasText(resultDTO.getOperationId())
                || !StringUtils.hasText(policyCode)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private void validateDecisionCommand(RefundApprovalDecisionCommandDTO commandDTO, boolean reasonRequired) {
        if (commandDTO == null
                || !StringUtils.hasText(commandDTO.getApprovalId())
                || !StringUtils.hasText(commandDTO.getDecisionRequestId())
                || commandDTO.getExpectedVersion() == null
                || !StringUtils.hasText(commandDTO.getOperatorId())
                || reasonRequired && !StringUtils.hasText(commandDTO.getReason())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        if (commandDTO.getReason() != null && commandDTO.getReason().length() > 512) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "approval reason is too long");
        }
    }

    private void requireSingleRow(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new ServiceException(ApiResultEnum.REFUND_APPROVAL_STATE_CONFLICT.getCode(), message);
        }
    }

    private String safeReason(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 512 ? trimmed.substring(0, 512) : trimmed;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private LocalDateTime toUtcTime(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime
                .atZone(ZoneId.of(DEFAULT_TIME_ZONE))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
