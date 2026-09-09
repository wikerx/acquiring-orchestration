package com.scott.payment.settlement.application;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementReversalStatus;
import com.scott.payment.settlement.dto.SettlementReversalAudit;
import com.scott.payment.settlement.dto.SettlementReversalCommandResult;
import com.scott.payment.settlement.dto.SettlementReversalCreateCommand;
import com.scott.payment.settlement.dto.SettlementReversalDecisionCommand;
import com.scott.payment.settlement.entity.MerchantFundLedgerDO;
import com.scott.payment.settlement.entity.MerchantReserveActionDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementProjectionTaskDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.entity.SettlementReversalDailySequenceDO;
import com.scott.payment.settlement.entity.SettlementReversalOrderDO;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementFundMapper;
import com.scott.payment.settlement.mapper.SettlementProjectionMapper;
import com.scott.payment.settlement.mapper.SettlementReserveMapper;
import com.scott.payment.settlement.mapper.SettlementResultMapper;
import com.scott.payment.settlement.mapper.SettlementReversalDailySequenceMapper;
import com.scott.payment.settlement.mapper.SettlementReversalOrderMapper;
import com.scott.payment.settlement.support.SettlementReversalNumberFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalOrderApplicationService
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 编排结算冲正申请和异人复核；以请求键、版本 CAS 和冻结资金指纹保护终态，批准后委托批次命令创建独立反向流水。
 * @status : create
 */
@Service
public class SettlementReversalOrderApplicationService {

    /**
     * {@code MAX_DAILY_SEQUENCE}常量，统一 {@code SettlementReversalOrderApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int MAX_DAILY_SEQUENCE = 99_999_999;

    private final SettlementReversalDailySequenceMapper sequenceMapper;
    private final SettlementReversalOrderMapper orderMapper;
    private final SettlementBatchMapper batchMapper;
    private final SettlementBatchCandidateMapper batchCandidateMapper;
    private final SettlementResultMapper resultMapper;
    private final SettlementFundMapper fundMapper;
    private final SettlementProjectionMapper projectionMapper;
    private final SettlementReserveMapper reserveMapper;
    private final SettlementBatchCommandApplicationService commandService;
    private final SettlementReversalNumberFormatter numberFormatter;
    private final Clock clock;

    @Autowired
    public SettlementReversalOrderApplicationService(
            SettlementReversalDailySequenceMapper sequenceMapper,
            SettlementReversalOrderMapper orderMapper,
            SettlementBatchMapper batchMapper,
            SettlementBatchCandidateMapper batchCandidateMapper,
            SettlementResultMapper resultMapper,
            SettlementFundMapper fundMapper,
            SettlementProjectionMapper projectionMapper,
            SettlementReserveMapper reserveMapper,
            SettlementBatchCommandApplicationService commandService,
            SettlementReversalNumberFormatter numberFormatter) {
        this(sequenceMapper, orderMapper, batchMapper, batchCandidateMapper, resultMapper, fundMapper, projectionMapper,
                reserveMapper, commandService, numberFormatter, Clock.systemUTC());
    }

    SettlementReversalOrderApplicationService(
            SettlementReversalDailySequenceMapper sequenceMapper,
            SettlementReversalOrderMapper orderMapper,
            SettlementBatchMapper batchMapper,
            SettlementBatchCandidateMapper batchCandidateMapper,
            SettlementResultMapper resultMapper,
            SettlementFundMapper fundMapper,
            SettlementProjectionMapper projectionMapper,
            SettlementReserveMapper reserveMapper,
            SettlementBatchCommandApplicationService commandService,
            SettlementReversalNumberFormatter numberFormatter,
            Clock clock) {
        this.sequenceMapper = sequenceMapper;
        this.orderMapper = orderMapper;
        this.batchMapper = batchMapper;
        this.batchCandidateMapper = batchCandidateMapper;
        this.resultMapper = resultMapper;
        this.fundMapper = fundMapper;
        this.projectionMapper = projectionMapper;
        this.reserveMapper = reserveMapper;
        this.commandService = commandService;
        this.numberFormatter = numberFormatter;
        this.clock = clock;
    }

    /**
     * 冻结已入账原批次的净结果、资金流水、保证金动作和投影任务，并创建待复核冲正单。
     *
     * @param command 原批次号、期望版本、申请原因、幂等键及可信 Maker 快照
     * @return 新建或同幂等身份重放的冲正单结果
     * @throws IllegalArgumentException 命令缺失或字段不合法时抛出
     * @throws IllegalStateException 原批次非已入账、已有有效冲正或冻结事实不一致时抛出
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public SettlementReversalCommandResult submit(SettlementReversalCreateCommand command) {
        Objects.requireNonNull(command, "settlement reversal command is required");
        SettlementReversalOrderDO existing = orderMapper.selectByCreateRequestKeyForUpdate(command.requestKey());
        if (existing != null) {
            verifyCreateReplay(existing, command);
            return result(existing);
        }

        SettlementBatchDO batch = requirePostedBatch(command.originalBatchNo(), command.expectedBatchVersion());
        if (orderMapper.selectActiveByOriginalBatchForUpdate(batch.getSettlementBatchNo()) != null) {
            throw new IllegalStateException("settlement batch already has an active or approved reversal order");
        }
        FrozenSource source = freeze(batch);
        LocalDate businessDate = LocalDate.now(clock);
        sequenceMapper.insertIfAbsent(businessDate);
        SettlementReversalDailySequenceDO sequence = sequenceMapper.selectForUpdate(businessDate);
        if (sequence == null || sequence.getCurrentSequence() == null || sequence.getVersion() == null) {
            throw new IllegalStateException("settlement reversal daily sequence is incomplete");
        }
        int next = sequence.getCurrentSequence() + 1;
        if (next > MAX_DAILY_SEQUENCE || sequenceMapper.increment(businessDate,
                sequence.getCurrentSequence(), sequence.getVersion()) != 1) {
            throw new IllegalStateException("settlement reversal daily sequence CAS failed");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        SettlementReversalOrderDO order = new SettlementReversalOrderDO();
        order.setReversalOrderNo(numberFormatter.storageNumber(businessDate, next));
        order.setCreateRequestKey(command.requestKey());
        order.setOriginalBatchNo(batch.getSettlementBatchNo());
        order.setMerchantId(batch.getMerchantId());
        order.setSettlementAccountId(batch.getSettlementAccountId());
        order.setTargetCurrency(batch.getTargetCurrency());
        order.setTargetCurrencyExponent(batch.getTargetCurrencyExponent());
        order.setOriginalBatchVersion(batch.getVersion());
        order.setOriginalNetResultItemId(source.net().getId());
        order.setOriginalFundLedgerId(source.ledger().getId());
        order.setNetDirection(source.net().getDirection());
        order.setNetAmount(source.net().getTargetAmount());
        order.setSourceFingerprint(source.fingerprint());
        order.setReversalStatus(SettlementReversalStatus.PENDING_APPROVAL.name());
        applyMaker(order, command);
        order.setVersion(0L);
        order.setCreateTime(now);
        order.setUpdateTime(now);
        if (orderMapper.insertIdempotent(order) != 1) {
            throw new IllegalStateException("settlement reversal order insert failed");
        }
        return result(order);
    }

    /**
     * 对待复核冲正单执行批准或拒绝；批准前重新锁定原批次并比对提交时的 SHA-256 资金指纹。
     *
     * @param reversalOrderNo 冲正申请单号
     * @param command 决策、期望版本、请求幂等键及可信 Checker 快照
     * @return 终态冲正结果；批准时包含新建反向批次号
     * @throws IllegalArgumentException 单号或命令不合法时抛出
     * @throws IllegalStateException 状态/版本 CAS、Maker-Checker 或冻结事实校验失败时抛出
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public SettlementReversalCommandResult decide(String reversalOrderNo,
                                                   SettlementReversalDecisionCommand command) {
        if (reversalOrderNo == null || reversalOrderNo.isBlank()) {
            throw new IllegalArgumentException("settlement reversal order number is required");
        }
        Objects.requireNonNull(command, "settlement reversal decision is required");
        SettlementReversalOrderDO replay = orderMapper.selectByDecisionRequestKeyForUpdate(command.requestKey());
        if (replay != null) {
            verifyDecisionReplay(replay, reversalOrderNo.trim(), command);
            return result(replay);
        }
        SettlementReversalOrderDO order = orderMapper.selectByReversalOrderNoForUpdate(reversalOrderNo.trim());
        if (order == null || !SettlementReversalStatus.PENDING_APPROVAL.name().equals(order.getReversalStatus())
                || order.getVersion() == null || order.getVersion() != command.expectedVersion()) {
            throw new IllegalStateException("settlement reversal order is not pending at the expected version");
        }
        if (Objects.equals(order.getSubmittedByAccountId(), command.operator().accountId())) {
            throw new IllegalStateException("settlement reversal Maker and Checker must be different accounts");
        }
        applyChecker(order, command);
        if ("REJECT".equals(command.decision())) {
            if (orderMapper.reject(order, command.expectedVersion()) != 1) {
                throw new IllegalStateException("settlement reversal rejection CAS failed");
            }
            order.setReversalStatus(SettlementReversalStatus.REJECTED.name());
            order.setVersion(order.getVersion() + 1);
            return result(order);
        }

        SettlementBatchDO original = requirePostedBatch(
                order.getOriginalBatchNo(), order.getOriginalBatchVersion());
        FrozenSource current = freeze(original);
        if (!Objects.equals(order.getSourceFingerprint(), current.fingerprint())
                || !Objects.equals(order.getOriginalNetResultItemId(), current.net().getId())
                || !Objects.equals(order.getOriginalFundLedgerId(), current.ledger().getId())
                || order.getNetAmount().compareTo(current.net().getTargetAmount()) != 0) {
            throw new IllegalStateException("settlement reversal source changed after submission");
        }
        String reversalBatchNo = commandService.reversePostedBatch(
                original.getSettlementBatchNo(), order.getReversalOrderNo(), original.getVersion(),
                new SettlementReversalAudit(order.getReversalOrderNo(), order.getSubmitReason(),
                        maker(order), command.comment(), command.operator()), command.operator().operationTime());
        order.setReversalBatchNo(reversalBatchNo);
        if (orderMapper.approve(order, command.expectedVersion()) != 1) {
            throw new IllegalStateException("settlement reversal approval CAS failed");
        }
        order.setReversalStatus(SettlementReversalStatus.APPROVED.name());
        order.setVersion(order.getVersion() + 1);
        return result(order);
    }

    /** 锁读并校验原批次仍为完整 POSTED 状态且版本与冲正申请一致。 */
    private SettlementBatchDO requirePostedBatch(String batchNo, long expectedVersion) {
        SettlementBatchDO batch = batchMapper.selectByBatchNoForUpdate(batchNo);
        if (batch == null || batch.getVersion() == null || batch.getCandidateCount() == null
                || batch.getProjectableCandidateCount() == null
                || !SettlementBatchStatus.POSTED.name().equals(batch.getBatchStatus())) {
            throw new IllegalStateException("only a complete posted settlement batch can be reversed");
        }
        if (batch.getVersion() != expectedVersion) {
            throw new IllegalStateException("settlement reversal uses a stale original batch version");
        }
        return batch;
    }

    /** 锁定原净结果、资金流水并核对真实投影/保证金动作集合后生成来源指纹。 */
    private FrozenSource freeze(SettlementBatchDO batch) {
        SettlementResultItemDO net = resultMapper.selectNetPostingForUpdate(batch.getSettlementBatchNo());
        if (net == null || net.getId() == null || net.getTargetAmount() == null
                || net.getTargetAmount().signum() < 0 || net.getDirection() == null
                || !Objects.equals(batch.getTargetCurrency(), net.getTargetCurrency())
                || net.getLedgerIdempotencyKey() == null) {
            throw new IllegalStateException("settlement reversal original net result is invalid");
        }
        MerchantFundLedgerDO ledger = fundMapper.selectLedgerByIdempotencyForUpdate(net.getLedgerIdempotencyKey());
        if (ledger == null || ledger.getId() == null
                || !Objects.equals(net.getLedgerIdempotencyKey(), ledger.getIdempotencyKey())) {
            throw new IllegalStateException("settlement reversal original fund ledger is invalid");
        }
        List<SettlementProjectionTaskDO> tasks = safe(projectionMapper.selectTasksByBatch(batch.getSettlementBatchNo()))
                .stream().sorted(Comparator.comparing(SettlementProjectionTaskDO::getCandidateId,
                        Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(
                        SettlementProjectionTaskDO::getTaskNo, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        int projectableCandidateCount = batchCandidateMapper.countProjectableCandidates(
                batch.getSettlementBatchNo());
        if (projectableCandidateCount < 0
                || projectableCandidateCount != batch.getProjectableCandidateCount()
                || projectableCandidateCount != tasks.size()) {
            throw new IllegalStateException("settlement reversal projectable candidate identity is inconsistent");
        }
        if (tasks.stream().anyMatch(task -> !"COMPLETED".equals(task.getTaskStatus()))) {
            throw new IllegalStateException("settlement reversal transaction projection is not complete");
        }
        List<MerchantReserveActionDO> actions = safe(reserveMapper.selectActionsByBatchForUpdate(
                batch.getSettlementBatchNo())).stream().sorted(Comparator.comparing(
                MerchantReserveActionDO::getId, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        return new FrozenSource(net, ledger, fingerprint(batch, net, ledger, tasks, actions));
    }

    /** 以长度前缀规范编码批次、资金、投影和保证金事实，生成稳定 SHA-256 指纹。 */
    private String fingerprint(SettlementBatchDO batch,
                               SettlementResultItemDO net,
                               MerchantFundLedgerDO ledger,
                               List<SettlementProjectionTaskDO> tasks,
                               List<MerchantReserveActionDO> actions) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, batch.getSettlementBatchNo(), batch.getMerchantId(), batch.getSettlementAccountId(),
                batch.getTargetCurrency(), batch.getTargetCurrencyExponent(), batch.getCandidateCount(),
                net.getId(), net.getDirection(), net.getTargetAmount(), net.getTargetCurrency(),
                net.getLedgerIdempotencyKey(), ledger.getId(), ledger.getDirection(), ledger.getAmount(),
                ledger.getCurrency(), ledger.getIdempotencyKey());
        for (SettlementProjectionTaskDO task : tasks) {
            append(canonical, task.getTaskNo(), task.getCandidateId(), task.getTransactionId(),
                    task.getTransactionDateTime(), task.getClearingRevision(), task.getOperationId(),
                    task.getSettlementCurrency(), task.getSettlementAmount(), task.getSettlementDate(),
                    task.getTaskStatus());
        }
        for (MerchantReserveActionDO action : actions) {
            append(canonical, action.getId(), action.getReserveActionNo(), action.getReserveItemId(),
                    action.getCandidateId(), action.getActionType(), action.getDirection(),
                    action.getCurrency(), action.getAmount(), action.getReversalOfActionId());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** 使用 BigDecimal plain string 和长度前缀追加规范值，避免分隔符碰撞及科学计数差异。 */
    private void append(StringBuilder target, Object... values) {
        for (Object value : values) {
            String text = value instanceof BigDecimal amount ? amount.toPlainString() : String.valueOf(value);
            target.append(text.length()).append(':').append(text).append('|');
        }
    }

    /** 冻结可信 Maker 主体、客户端环境、申请原因和操作时间。 */
    private void applyMaker(SettlementReversalOrderDO order, SettlementReversalCreateCommand command) {
        order.setSubmittedByAccountId(command.operator().accountId());
        order.setSubmittedByAccountName(command.operator().accountName());
        order.setSubmittedRoleSnapshot(command.operator().roleSnapshot());
        order.setSubmitClientIp(command.operator().clientIp());
        order.setSubmitUserAgent(command.operator().userAgent());
        order.setSubmitReason(command.reason());
        order.setSubmittedTime(command.operator().operationTime());
    }

    /** 冻结可信 Checker 主体、决策请求键、意见和操作时间。 */
    private void applyChecker(SettlementReversalOrderDO order, SettlementReversalDecisionCommand command) {
        order.setDecidedByAccountId(command.operator().accountId());
        order.setDecidedByAccountName(command.operator().accountName());
        order.setDecidedRoleSnapshot(command.operator().roleSnapshot());
        order.setDecisionClientIp(command.operator().clientIp());
        order.setDecisionUserAgent(command.operator().userAgent());
        order.setDecisionAction(command.decision());
        order.setDecisionRequestKey(command.requestKey());
        order.setDecisionComment(command.comment());
        order.setDecisionTime(command.operator().operationTime());
    }

    /** 从冲正单重建不可变 Maker 快照供反向资金流水审计。 */
    private com.scott.payment.settlement.dto.SettlementOperatorSnapshot maker(SettlementReversalOrderDO order) {
        return new com.scott.payment.settlement.dto.SettlementOperatorSnapshot(
                order.getSubmittedByAccountId(), order.getSubmittedByAccountName(),
                order.getSubmittedRoleSnapshot(), order.getSubmitClientIp(), order.getSubmitUserAgent(),
                order.getSubmittedTime());
    }

    /** 对创建请求键重放核对原批次、Maker 和原因，拒绝幂等键碰撞。 */
    private void verifyCreateReplay(SettlementReversalOrderDO order, SettlementReversalCreateCommand command) {
        if (!Objects.equals(order.getOriginalBatchNo(), command.originalBatchNo())
                || !Objects.equals(order.getSubmittedByAccountId(), command.operator().accountId())
                || !Objects.equals(order.getSubmitReason(), command.reason())) {
            throw new IllegalStateException("settlement reversal create idempotency identity is inconsistent");
        }
    }

    /** 对决策请求键重放核对冲正单号、动作、Checker 和意见。 */
    private void verifyDecisionReplay(SettlementReversalOrderDO order,
                                      String orderNo,
                                      SettlementReversalDecisionCommand command) {
        if (!Objects.equals(order.getReversalOrderNo(), orderNo)
                || !Objects.equals(order.getDecisionAction(), command.decision())
                || !Objects.equals(order.getDecidedByAccountId(), command.operator().accountId())
                || !Objects.equals(order.getDecisionComment(), command.comment())) {
            throw new IllegalStateException("settlement reversal decision idempotency identity is inconsistent");
        }
    }

    /** 映射冲正主表为稳定命令结果，不暴露角色、IP 或 User-Agent 审计字段。 */
    private SettlementReversalCommandResult result(SettlementReversalOrderDO order) {
        return new SettlementReversalCommandResult(order.getReversalOrderNo(), order.getReversalStatus(),
                order.getOriginalBatchNo(), order.getReversalBatchNo(), order.getMerchantId(),
                order.getTargetCurrency(), order.getNetDirection(), order.getNetAmount(), order.getVersion());
    }

    private <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private record FrozenSource(SettlementResultItemDO net,
                                MerchantFundLedgerDO ledger,
                                String fingerprint) {
    }
}
