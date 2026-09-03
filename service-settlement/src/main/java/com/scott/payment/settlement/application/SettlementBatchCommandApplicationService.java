package com.scott.payment.settlement.application;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementBatchType;
import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.dto.SettlementCommandAudit;
import com.scott.payment.settlement.dto.SettlementReversalAudit;
import com.scott.payment.settlement.entity.MerchantFundAccountDO;
import com.scott.payment.settlement.entity.MerchantFundLedgerDO;
import com.scott.payment.settlement.entity.MerchantReserveActionDO;
import com.scott.payment.settlement.entity.MerchantReserveItemDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementBatchCancellationAuditDO;
import com.scott.payment.settlement.entity.SettlementProjectionTaskDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementFundMapper;
import com.scott.payment.settlement.mapper.SettlementProjectionMapper;
import com.scott.payment.settlement.mapper.SettlementReserveMapper;
import com.scott.payment.settlement.mapper.SettlementResultMapper;
import com.scott.payment.settlement.service.SettlementBatchCreationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchCommandApplicationService
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 编排正式结算批次的受控取消与冲正；入账前通过状态和版本 CAS 释放候选，入账后只创建独立反向批次、资金流水和真实交易投影任务。
 * @status : create
 */
@Service
public class SettlementBatchCommandApplicationService {

    private final SettlementBatchMapper batchMapper;
    private final SettlementCandidateMapper candidateMapper;
    private final SettlementBatchCandidateMapper relationMapper;
    private final SettlementBatchCreationService creationService;
    private final SettlementResultMapper resultMapper;
    private final SettlementFundMapper fundMapper;
    private final SettlementReserveMapper reserveMapper;
    private final SettlementProjectionMapper projectionMapper;

    public SettlementBatchCommandApplicationService(SettlementBatchMapper batchMapper,
                                                    SettlementCandidateMapper candidateMapper,
                                                    SettlementBatchCandidateMapper relationMapper,
                                                    SettlementBatchCreationService creationService,
                                                    SettlementResultMapper resultMapper,
                                                    SettlementFundMapper fundMapper,
                                                    SettlementReserveMapper reserveMapper,
                                                    SettlementProjectionMapper projectionMapper) {
        this.batchMapper = batchMapper;
        this.candidateMapper = candidateMapper;
        this.relationMapper = relationMapper;
        this.creationService = creationService;
        this.resultMapper = resultMapper;
        this.fundMapper = fundMapper;
        this.reserveMapper = reserveMapper;
        this.projectionMapper = projectionMapper;
    }

    /**
     * 入账前取消并保存不可变操作快照；同一请求键重放返回首次释放结果。
     *
     * @param settlementBatchNo 正式结算批次号
     * @param expectedVersion 页面读取的批次版本
     * @param audit Admin 注入的可信命令审计
     * @param now settlement 领域实际执行时间
     * @return 首次取消释放的候选数，或同一请求键的原结果
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public int cancelBeforePosting(String settlementBatchNo,
                                   long expectedVersion,
                                   SettlementCommandAudit audit,
                                   LocalDateTime now) {
        if (settlementBatchNo == null || settlementBatchNo.isBlank()
                || expectedVersion < 0 || audit == null || now == null) {
            throw new IllegalArgumentException("settlement cancellation command is invalid");
        }
        String batchNo = settlementBatchNo.trim();
        SettlementBatchCancellationAuditDO requestReplay =
                batchMapper.selectCancellationAuditByRequestKey(audit.requestKey());
        if (requestReplay != null) {
            return replayResult(batchNo, requestReplay);
        }

        SettlementBatchDO batch = requireBatch(batchNo);
        SettlementBatchCancellationAuditDO batchReplay =
                batchMapper.selectCancellationAuditByBatchNo(batchNo);
        if (batchReplay != null) {
            return replayResult(batchNo, audit.requestKey(), batchReplay);
        }
        if (batch.getVersion() != expectedVersion) {
            throw new IllegalStateException("settlement batch command uses a stale version");
        }
        // Pre-migration CANCELLED rows cannot be retroactively attributed to a new trusted operator.
        if (SettlementBatchStatus.CANCELLED.name().equals(batch.getBatchStatus())) {
            return 0;
        }
        if (batch.getProcessingDeadline() != null && batch.getProcessingDeadline().isAfter(now)) {
            throw new IllegalStateException("settlement batch has an active processing lease");
        }
        if (batchMapper.cancelBeforePosting(batch.getSettlementBatchNo(), batch.getVersion(), now) != 1) {
            throw new IllegalStateException("settlement pre-post cancellation state CAS failed");
        }
        int expected = Objects.requireNonNullElse(batch.getCandidateCount(), 0);
        int candidates = candidateMapper.releaseCancelledBatch(batch.getSettlementBatchNo(), now);
        int relations = relationMapper.releaseCancelledBatch(batch.getSettlementBatchNo(), now);
        if (candidates != expected || relations != expected) {
            throw new IllegalStateException("settlement cancellation release count is inconsistent");
        }
        SettlementBatchCancellationAuditDO row = cancellationAudit(
                batch, expectedVersion, candidates, audit, now);
        if (batchMapper.insertCancellationAudit(row) != 1) {
            throw new IllegalStateException("settlement cancellation audit insert failed");
        }
        return candidates;
    }

    /** 校验按请求键命中的取消审计确属当前批次后返回首次释放数量。 */
    private int replayResult(String batchNo, SettlementBatchCancellationAuditDO existing) {
        return replayResult(batchNo, existing.getRequestKey(), existing);
    }

    /** 校验同一批次的后续取消请求键与首次审计一致，拒绝用新身份冒充历史取消。 */
    private int replayResult(String batchNo,
                             String requestKey,
                             SettlementBatchCancellationAuditDO existing) {
        if (!batchNo.equals(existing.getSettlementBatchNo())
                || !requestKey.equals(existing.getRequestKey())) {
            throw new IllegalStateException("settlement cancellation request key is already in use");
        }
        if (existing.getReleasedCandidateCount() == null || existing.getReleasedCandidateCount() < 0) {
            throw new IllegalStateException("settlement cancellation audit result is incomplete");
        }
        return existing.getReleasedCandidateCount();
    }

    /** 将批次取消前状态、可信操作主体和实际释放数冻结为不可变审计行。 */
    private SettlementBatchCancellationAuditDO cancellationAudit(SettlementBatchDO batch,
                                                                  long expectedVersion,
                                                                  int releasedCandidates,
                                                                  SettlementCommandAudit audit,
                                                                  LocalDateTime now) {
        SettlementBatchCancellationAuditDO row = new SettlementBatchCancellationAuditDO();
        row.setSettlementBatchNo(batch.getSettlementBatchNo());
        row.setRequestKey(audit.requestKey());
        row.setExpectedVersion(expectedVersion);
        row.setMerchantId(batch.getMerchantId());
        row.setBatchStatusBefore(batch.getBatchStatus());
        row.setReleasedCandidateCount(releasedCandidates);
        row.setOperatorAccountId(audit.operator().accountId());
        row.setOperatorAccountName(audit.operator().accountName());
        row.setOperatorRoleSnapshot(audit.operator().roleSnapshot());
        row.setClientIp(audit.operator().clientIp());
        row.setUserAgent(audit.operator().userAgent());
        row.setReason(audit.reason());
        row.setOperationTime(audit.operator().operationTime());
        row.setCancelledTime(now);
        row.setCreateTime(now);
        return row;
    }

    /**
     * 对已入账批次创建并原子提交一条独立冲正批；重复请求返回已完成冲正批次号。
     *
     * @param originalBatchNo 被冲正的已入账正式批次号
     * @param requestKey 冲正请求数据库幂等键，必须与冲正审计单号一致
     * @param expectedVersion 页面读取到的原批次乐观锁版本
     * @param audit service-admin 注入的冲正单号、原因和可信 Maker-Checker 审计
     * @param now 冲正批次和资金事实的统一提交时间
     * @return 新建或幂等回放的逆向结算批次号
     * @throws IllegalArgumentException 请求键或审计身份不合法时抛出
     * @throws IllegalStateException 原批次非 POSTED、版本过期、投影未完成或冲正提交不完整时抛出
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public String reversePostedBatch(String originalBatchNo,
                                     String requestKey,
                                     long expectedVersion,
                                     SettlementReversalAudit audit,
                                     LocalDateTime now) {
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 64) {
            throw new IllegalArgumentException("settlement reversal request key is invalid");
        }
        Objects.requireNonNull(audit, "settlement reversal audit is required");
        if (!requestKey.trim().equals(audit.reversalOrderNo())) {
            throw new IllegalArgumentException("settlement reversal audit request id is inconsistent");
        }
        SettlementBatchDO original = requireBatch(originalBatchNo);
        if (original.getVersion() != expectedVersion) {
            throw new IllegalStateException("settlement batch command uses a stale version");
        }
        SettlementBatchDO existing = batchMapper.selectReversalByOriginalForUpdate(
                original.getSettlementBatchNo());
        if (existing != null) {
            if (SettlementBatchStatus.POSTED.name().equals(existing.getBatchStatus())
                    && SettlementBatchStatus.REVERSED.name().equals(original.getBatchStatus())) {
                return existing.getSettlementBatchNo();
            }
            throw new IllegalStateException("settlement batch already has an incomplete reversal");
        }
        if (!SettlementBatchStatus.POSTED.name().equals(original.getBatchStatus())) {
            throw new IllegalStateException("only a posted settlement batch can be reversed");
        }

        List<SettlementProjectionTaskDO> originalTasks = projectionMapper.selectTasksByBatch(
                original.getSettlementBatchNo());
        int projectableCandidateCount = relationMapper.countProjectableCandidates(
                original.getSettlementBatchNo());
        if (projectableCandidateCount < 0 || projectableCandidateCount > original.getCandidateCount()
                || originalTasks.size() != projectableCandidateCount
                || originalTasks.stream().anyMatch(task -> !"COMPLETED".equals(task.getTaskStatus()))) {
            throw new IllegalStateException("settlement batch transaction projection is not complete");
        }
        SettlementBatchCreateResult created = creationService.create(reversalCommand(
                original, requestKey.trim(), now));
        SettlementBatchDO reversal = batchMapper.selectByBatchNoForUpdate(created.settlementBatchNo());
        if (reversal == null || !SettlementBatchType.REVERSAL.name().equals(reversal.getBatchType())) {
            throw new IllegalStateException("settlement reversal batch identity is invalid");
        }
        if (batchMapper.markReversing(original.getSettlementBatchNo(), original.getVersion(), now) != 1
                || batchMapper.prepareReversalPosting(reversal.getSettlementBatchNo(),
                original.getCandidateCount(), reversal.getVersion(), now) != 1) {
            throw new IllegalStateException("settlement reversal state transition failed");
        }
        original.setVersion(original.getVersion() + 1);
        reversal.setVersion(reversal.getVersion() + 1);

        SettlementResultItemDO originalNet = resultMapper.selectNetPostingForUpdate(
                original.getSettlementBatchNo());
        validateOriginalNet(original, originalNet);
        SettlementResultItemDO reversalNet = reversalNet(reversal, originalNet, now);
        if (resultMapper.insertItemsIdempotent(List.of(reversalNet)) != 1
                || resultMapper.countLedgerPostingByBatch(reversal.getSettlementBatchNo()) != 1) {
            throw new IllegalStateException("settlement reversal net result insert failed");
        }
        reverseFund(original, reversal, originalNet, audit, now);
        reverseReserve(original, reversal, now);

        List<SettlementProjectionTaskDO> reversalTasks = reversalTasks(
                original, reversal, originalTasks, now);
        if (!reversalTasks.isEmpty()
                && projectionMapper.insertTasksIdempotent(reversalTasks) != reversalTasks.size()) {
            throw new IllegalStateException("settlement reversal projection task insert failed");
        }
        if (batchMapper.markReversalPosted(reversal.getSettlementBatchNo(), reversal.getVersion(), now) != 1
                || batchMapper.markReversed(original.getSettlementBatchNo(), original.getVersion(), now) != 1) {
            throw new IllegalStateException("settlement reversal completion state CAS failed");
        }
        return reversal.getSettlementBatchNo();
    }

    /** 以冲正单号构造独立 REVERSAL 批次命令，继承原批次商户、账户、币种和业务窗口。 */
    private SettlementBatchCreateCommand reversalCommand(SettlementBatchDO original,
                                                          String requestKey,
                                                          LocalDateTime now) {
        return new SettlementBatchCreateCommand(
                "REVERSAL:" + original.getSettlementBatchNo() + ":" + requestKey,
                now.toLocalDate(), original.getBusinessTimeZone(), original.getMerchantId(),
                original.getSettlementProfileId(), original.getSettlementAccountId(),
                original.getTargetCurrency(), original.getTargetCurrencyExponent(),
                SettlementBatchType.REVERSAL, original.getSettlementBatchNo(),
                original.getCutoffBeginTime(), original.getCutoffEndTime());
    }

    /** 复制并反转原净入账结果，金额和目标币种保持不变，引用原结果项供审计。 */
    private SettlementResultItemDO reversalNet(SettlementBatchDO reversal,
                                               SettlementResultItemDO original,
                                               LocalDateTime now) {
        SettlementResultItemDO row = new SettlementResultItemDO();
        row.setSettlementResultItemNo(stableId("SRV", reversal.getSettlementBatchNo()));
        row.setSettlementBatchNo(reversal.getSettlementBatchNo());
        row.setResultLineNo(1);
        row.setMerchantId(reversal.getMerchantId());
        row.setSettlementAccountId(reversal.getSettlementAccountId());
        row.setSourceDetailType("BATCH_NET");
        row.setReversalOfResultItemId(original.getId());
        row.setResultItemType("NET_SETTLEMENT");
        row.setResultRole("LEDGER_POSTING");
        row.setDirection(opposite(original.getDirection()));
        row.setSourceAmount(original.getTargetAmount());
        row.setSourceCurrency(original.getTargetCurrency());
        row.setSourceCurrencyExponent(original.getTargetCurrencyExponent());
        row.setSettlementBatchRateId(original.getSettlementBatchRateId());
        row.setUnroundedTargetAmount(original.getTargetAmount());
        row.setTargetAmount(original.getTargetAmount());
        row.setTargetCurrency(original.getTargetCurrency());
        row.setTargetCurrencyExponent(original.getTargetCurrencyExponent());
        row.setAppliedLimit("NONE");
        row.setRoundingMode("HALF_UP");
        row.setFormulaSnapshot("exact opposite of original immutable NET_SETTLEMENT result; no new FX rate");
        row.setLedgerIdempotencyKey(ledgerKey(reversal.getSettlementBatchNo()));
        row.setCreateTime(now);
        return row;
    }

    /** 锁定原资金流水，以相反方向追加唯一冲正流水并用余额/版本 CAS 更新同一 NORMAL 账户。 */
    private void reverseFund(SettlementBatchDO original,
                             SettlementBatchDO reversal,
                             SettlementResultItemDO originalNet,
                             SettlementReversalAudit audit,
                             LocalDateTime now) {
        MerchantFundAccountDO account = fundMapper.selectAccountForUpdate(original.getSettlementAccountId());
        if (account == null || account.getAvailableBalance() == null || account.getAccountVersion() == null
                || !Objects.equals(original.getSettlementAccountId(), account.getId())
                || !original.getMerchantId().equals(account.getMerchantId())
                || !original.getTargetCurrency().equals(account.getSettlementCurrency())
                || !"NORMAL".equals(account.getAccountStatus())) {
            throw new IllegalStateException("settlement reversal fund account identity is invalid");
        }
        BigDecimal amount = originalNet.getTargetAmount();
        if (amount.signum() == 0) {
            return;
        }
        MerchantFundLedgerDO originalLedger = fundMapper.selectLedgerByIdempotencyForUpdate(
                originalNet.getLedgerIdempotencyKey());
        if (originalLedger == null || originalLedger.getId() == null) {
            throw new IllegalStateException("settlement reversal original fund ledger is missing");
        }
        if (fundMapper.selectLedgerByIdempotencyForUpdate(ledgerKey(reversal.getSettlementBatchNo())) != null) {
            throw new IllegalStateException("settlement reversal fund ledger already exists");
        }
        BigDecimal before = account.getAvailableBalance();
        String direction = opposite(originalNet.getDirection());
        BigDecimal after = "CREDIT".equals(direction) ? before.add(amount) : before.subtract(amount);
        long sequence = Objects.requireNonNullElse(
                fundMapper.selectMaxAccountSequence(account.getId()), 0L) + 1;
        MerchantFundLedgerDO ledger = new MerchantFundLedgerDO();
        ledger.setLedgerNo(stableId("SLR", reversal.getSettlementBatchNo()));
        ledger.setLedgerGroupNo(reversal.getSettlementBatchNo());
        ledger.setAccountId(account.getId());
        ledger.setMerchantId(reversal.getMerchantId());
        ledger.setBusinessType(originalLedger.getBusinessType());
        ledger.setSummary("Settlement batch reversal");
        ledger.setBusinessNo(reversal.getSettlementBatchNo());
        ledger.setSettlementBatchNo(reversal.getSettlementBatchNo());
        ledger.setCurrency(reversal.getTargetCurrency());
        ledger.setDirection(direction);
        ledger.setAmount(amount);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);
        ledger.setAccountSequence(sequence);
        ledger.setOperationMode("MANUAL");
        ledger.setOperatorId(audit.maker().accountId());
        ledger.setOperatorName(audit.maker().accountName());
        ledger.setReviewerId(audit.checker().accountId());
        ledger.setReviewerName(audit.checker().accountName());
        ledger.setOperationReason(audit.makerReason());
        ledger.setReviewComment(audit.checkerComment());
        ledger.setBusinessTime(now);
        ledger.setSubmitTime(audit.maker().operationTime());
        ledger.setReviewTime(audit.checker().operationTime());
        ledger.setPostedTime(now);
        ledger.setRequestId(audit.reversalOrderNo());
        ledger.setIdempotencyKey(ledgerKey(reversal.getSettlementBatchNo()));
        ledger.setReversalOfLedgerId(originalLedger.getId());
        ledger.setCreateTime(now);
        if (fundMapper.insertLedger(ledger) != 1
                || fundMapper.updateAccountBalance(account.getId(), after, before,
                account.getAccountVersion(), now) != 1) {
            throw new IllegalStateException("settlement reversal fund posting failed");
        }
    }

    /** 逆序撤销原批全部保证金动作，每个 REVERSAL 动作引用原动作主键并保持原标签币种。 */
    private void reverseReserve(SettlementBatchDO original,
                                SettlementBatchDO reversal,
                                LocalDateTime now) {
        List<MerchantReserveActionDO> actions = reserveMapper.selectActionsByBatchForUpdate(
                original.getSettlementBatchNo());
        for (MerchantReserveActionDO originalAction : actions) {
            MerchantReserveItemDO item = reserveMapper.selectItemByIdForUpdate(
                    originalAction.getReserveItemId());
            if (item == null || item.getVersion() == null
                    || !originalAction.getCurrency().equals(item.getCurrency())) {
                throw new IllegalStateException("settlement reversal reserve identity is invalid");
            }
            MerchantReserveActionDO reversalAction = new MerchantReserveActionDO();
            reversalAction.setReserveActionNo(stableId("RAR",
                    reversal.getSettlementBatchNo() + "|" + originalAction.getReserveActionNo()));
            reversalAction.setReserveItemId(item.getId());
            reversalAction.setReserveNo(item.getReserveNo());
            reversalAction.setSettlementBatchNo(reversal.getSettlementBatchNo());
            reversalAction.setCandidateId(originalAction.getCandidateId());
            reversalAction.setSourceReserveDetailNo(originalAction.getReserveActionNo());
            reversalAction.setActionType("ADJUSTMENT".equals(originalAction.getActionType())
                    ? "REVERSAL_ADJUSTMENT" : "REVERSAL_" + originalAction.getActionType());
            reversalAction.setDirection(opposite(originalAction.getDirection()));
            reversalAction.setCurrency(originalAction.getCurrency());
            reversalAction.setAmount(originalAction.getAmount());
            reversalAction.setReversalOfActionId(originalAction.getId());
            reversalAction.setActionTime(now);
            reversalAction.setCreateTime(now);
            if (reserveMapper.insertActionIdempotent(reversalAction) != 1
                    || reverseReserveAggregate(originalAction, item, now) != 1) {
                throw new IllegalStateException("settlement reversal reserve update failed");
            }
        }
    }

    /** 按 HOLD、RETURN 或 RELEASE 类型调用对应聚合逆操作，受剩余责任金额和版本约束。 */
    private int reverseReserveAggregate(MerchantReserveActionDO action,
                                        MerchantReserveItemDO item,
                                        LocalDateTime now) {
        return switch (action.getActionType()) {
            case "HOLD" -> reserveMapper.reverseHold(item.getId(), action.getAmount(), item.getVersion(), now);
            case "RETURN" -> reserveMapper.reverseReturn(item.getId(), action.getAmount(), item.getVersion(), now);
            case "RELEASE" -> reserveMapper.reverseRelease(item.getId(), action.getAmount(), item.getVersion(), now);
            case "ADJUSTMENT" -> reverseAdjustment(action, item, now);
            default -> throw new IllegalStateException("unsupported reserve action in settlement reversal");
        };
    }

    /** 按原 ADJUSTMENT 方向执行相反聚合更新，防止冲正后保证金责任出现负数。 */
    private int reverseAdjustment(MerchantReserveActionDO action,
                                  MerchantReserveItemDO item,
                                  LocalDateTime now) {
        return switch (action.getDirection()) {
            case "DEBIT" -> reserveMapper.reverseDebitAdjustment(
                    item.getId(), action.getAmount(), item.getVersion(), now);
            case "CREDIT" -> reserveMapper.reverseCreditAdjustment(
                    item.getId(), action.getAmount(), item.getVersion(), now);
            default -> throw new IllegalStateException("reserve adjustment direction is invalid for reversal");
        };
    }

    /** 仅复制原批真实交易投影身份生成 REVERSE 任务，不从保证金动作伪造交易。 */
    private List<SettlementProjectionTaskDO> reversalTasks(SettlementBatchDO original,
                                                           SettlementBatchDO reversal,
                                                           List<SettlementProjectionTaskDO> originals,
                                                           LocalDateTime now) {
        List<SettlementProjectionTaskDO> rows = new ArrayList<>(originals.size());
        for (SettlementProjectionTaskDO source : originals) {
            SettlementProjectionTaskDO row = new SettlementProjectionTaskDO();
            row.setTaskNo(stableId("SPR", reversal.getSettlementBatchNo() + "|" + source.getCandidateId()));
            row.setSettlementBatchNo(reversal.getSettlementBatchNo());
            row.setProjectionAction("REVERSE");
            row.setOriginalBatchNo(original.getSettlementBatchNo());
            row.setCandidateId(source.getCandidateId());
            row.setTransactionId(source.getTransactionId());
            row.setTransactionDateTime(source.getTransactionDateTime());
            row.setClearingRevision(source.getClearingRevision());
            row.setOperationId(source.getOperationId());
            row.setMerchantId(source.getMerchantId());
            row.setSettlementCurrency(source.getSettlementCurrency());
            row.setSettlementAmount(source.getSettlementAmount());
            row.setSettlementDate(source.getSettlementDate());
            row.setTaskStatus("INIT");
            row.setRetryCount(0);
            row.setNextRetryTime(now);
            row.setVersion(0L);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            rows.add(row);
        }
        return rows;
    }

    /** 校验原净入账行属于原批次且具备非负金额、目标币种和资金幂等键。 */
    private void validateOriginalNet(SettlementBatchDO original, SettlementResultItemDO net) {
        boolean valid = net != null && net.getId() != null
                && net.getTargetAmount() != null && net.getTargetAmount().signum() >= 0
                && original.getTargetCurrency().equals(net.getTargetCurrency())
                && net.getSettlementBatchRateId() != null
                && net.getLedgerIdempotencyKey() != null;
        if (!valid) {
            throw new IllegalStateException("settlement reversal original net result is invalid");
        }
    }

    /** 锁读完整正式批次；不存在或版本字段缺失时立即阻断资金命令。 */
    private SettlementBatchDO requireBatch(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new IllegalArgumentException("settlement batch number is required");
        }
        SettlementBatchDO row = batchMapper.selectByBatchNoForUpdate(batchNo.trim());
        if (row == null || row.getVersion() == null) {
            throw new IllegalStateException("settlement batch does not exist or is incomplete");
        }
        return row;
    }

    /** 返回 CREDIT/DEBIT 的反向资金方向，拒绝未知方向字符串。 */
    private String opposite(String direction) {
        return switch (direction) {
            case "CREDIT" -> "DEBIT";
            case "DEBIT" -> "CREDIT";
            default -> throw new IllegalStateException("settlement direction is invalid");
        };
    }

    /** 生成批次级净入账资金流水最终幂等键。 */
    private String ledgerKey(String batchNo) {
        return "SETTLEMENT:" + batchNo;
    }

    /** 基于稳定业务身份生成固定长度 SHA-256 派生号，保证命令重放结果一致。 */
    private String stableId(String prefix, String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
