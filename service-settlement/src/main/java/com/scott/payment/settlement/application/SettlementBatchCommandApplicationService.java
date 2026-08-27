package com.scott.payment.settlement.application;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementBatchType;
import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.entity.MerchantFundAccountDO;
import com.scott.payment.settlement.entity.MerchantFundLedgerDO;
import com.scott.payment.settlement.entity.MerchantReserveActionDO;
import com.scott.payment.settlement.entity.MerchantReserveItemDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
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

/** 结算批次受控命令：入账前取消释放候选，入账后仅以独立冲正批次反向记账。 */
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

    /** 入账前取消，返回实际释放候选数；重复取消返回0。 */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public int cancelBeforePosting(String settlementBatchNo, long expectedVersion, LocalDateTime now) {
        SettlementBatchDO batch = requireBatch(settlementBatchNo);
        if (batch.getVersion() != expectedVersion) {
            throw new IllegalStateException("settlement batch command uses a stale version");
        }
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
        return candidates;
    }

    /**
     * 对已入账批次创建并原子提交一条独立冲正批；重复请求返回已完成冲正批次号。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public String reversePostedBatch(String originalBatchNo,
                                     String requestKey,
                                     long expectedVersion,
                                     LocalDateTime now) {
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 64) {
            throw new IllegalArgumentException("settlement reversal request key is invalid");
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
        if (originalTasks.size() != original.getCandidateCount()
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
        reverseFund(original, reversal, originalNet, now);
        reverseReserve(original, reversal, now);

        List<SettlementProjectionTaskDO> reversalTasks = reversalTasks(
                original, reversal, originalTasks, now);
        if (projectionMapper.insertTasksIdempotent(reversalTasks) != reversalTasks.size()) {
            throw new IllegalStateException("settlement reversal projection task insert failed");
        }
        if (batchMapper.markReversalPosted(reversal.getSettlementBatchNo(), reversal.getVersion(), now) != 1
                || batchMapper.markReversed(original.getSettlementBatchNo(), original.getVersion(), now) != 1) {
            throw new IllegalStateException("settlement reversal completion state CAS failed");
        }
        return reversal.getSettlementBatchNo();
    }

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

    private void reverseFund(SettlementBatchDO original,
                             SettlementBatchDO reversal,
                             SettlementResultItemDO originalNet,
                             LocalDateTime now) {
        MerchantFundAccountDO account = fundMapper.selectAccountForUpdate(original.getSettlementAccountId());
        if (account == null || account.getAvailableBalance() == null || account.getAccountVersion() == null
                || !original.getMerchantId().equals(account.getMerchantId())
                || !original.getTargetCurrency().equals(account.getSettlementCurrency())) {
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
        ledger.setOperationMode("AUTO");
        ledger.setOperatorName("service-settlement");
        ledger.setBusinessTime(now);
        ledger.setPostedTime(now);
        ledger.setIdempotencyKey(ledgerKey(reversal.getSettlementBatchNo()));
        ledger.setReversalOfLedgerId(originalLedger.getId());
        ledger.setCreateTime(now);
        if (fundMapper.insertLedger(ledger) != 1
                || fundMapper.updateAccountBalance(account.getId(), after, before,
                account.getAccountVersion(), now) != 1) {
            throw new IllegalStateException("settlement reversal fund posting failed");
        }
    }

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
            reversalAction.setActionType("REVERSAL_" + originalAction.getActionType());
            reversalAction.setCurrency(originalAction.getCurrency());
            reversalAction.setAmount(originalAction.getAmount());
            reversalAction.setActionTime(now);
            reversalAction.setCreateTime(now);
            if (reserveMapper.insertActionIdempotent(reversalAction) != 1
                    || reverseReserveAggregate(originalAction, item, now) != 1) {
                throw new IllegalStateException("settlement reversal reserve update failed");
            }
        }
    }

    private int reverseReserveAggregate(MerchantReserveActionDO action,
                                        MerchantReserveItemDO item,
                                        LocalDateTime now) {
        return switch (action.getActionType()) {
            case "HOLD" -> reserveMapper.reverseHold(item.getId(), action.getAmount(), item.getVersion(), now);
            case "RETURN" -> reserveMapper.reverseReturn(item.getId(), action.getAmount(), item.getVersion(), now);
            case "RELEASE" -> reserveMapper.reverseRelease(item.getId(), action.getAmount(), item.getVersion(), now);
            default -> throw new IllegalStateException("unsupported reserve action in settlement reversal");
        };
    }

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
            row.setSettlementAmount(source.getSettlementAmount().negate());
            row.setSettlementDate(reversal.getBusinessDate());
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

    private String opposite(String direction) {
        return switch (direction) {
            case "CREDIT" -> "DEBIT";
            case "DEBIT" -> "CREDIT";
            default -> throw new IllegalStateException("settlement direction is invalid");
        };
    }

    private String ledgerKey(String batchNo) {
        return "SETTLEMENT:" + batchNo;
    }

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
