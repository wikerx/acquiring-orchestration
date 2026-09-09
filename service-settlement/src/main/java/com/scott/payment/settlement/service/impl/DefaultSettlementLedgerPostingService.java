package com.scott.payment.settlement.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementFailureStage;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.entity.MerchantFundAccountDO;
import com.scott.payment.settlement.entity.MerchantFundLedgerDO;
import com.scott.payment.settlement.entity.MerchantReserveActionDO;
import com.scott.payment.settlement.entity.MerchantReserveItemDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementProjectionTaskDO;
import com.scott.payment.settlement.entity.SettlementReserveClearingDetailDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementFundMapper;
import com.scott.payment.settlement.mapper.SettlementProjectionMapper;
import com.scott.payment.settlement.mapper.SettlementReserveMapper;
import com.scott.payment.settlement.mapper.SettlementResultMapper;
import com.scott.payment.settlement.service.SettlementLedgerPostingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementLedgerPostingService
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 原子提交结算净额和保证金动作；固定先锁 NORMAL 资金账户，以流水/动作唯一键及余额、聚合版本 CAS 防重，Redis、MQ 和异步投影不参与余额事务成功判定。
 * @status : create
 */
@Service
public class DefaultSettlementLedgerPostingService implements SettlementLedgerPostingService {

    /**
     * {@code ZERO}常量，统一 {@code DefaultSettlementLedgerPostingService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    /**
     * {@code SYSTEM_OPERATOR}常量，统一 {@code DefaultSettlementLedgerPostingService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String SYSTEM_OPERATOR = "service-settlement";

    private final SettlementBatchMapper batchMapper;
    private final SettlementResultMapper resultMapper;
    private final SettlementFundMapper fundMapper;
    private final SettlementReserveMapper reserveMapper;
    private final SettlementCandidateMapper candidateMapper;
    private final SettlementBatchCandidateMapper relationMapper;
    private final SettlementProjectionMapper projectionMapper;

    public DefaultSettlementLedgerPostingService(SettlementBatchMapper batchMapper,
                                                 SettlementResultMapper resultMapper,
                                                 SettlementFundMapper fundMapper,
                                                 SettlementReserveMapper reserveMapper,
                                                 SettlementCandidateMapper candidateMapper,
                                                 SettlementBatchCandidateMapper relationMapper,
                                                 SettlementProjectionMapper projectionMapper) {
        this.batchMapper = batchMapper;
        this.resultMapper = resultMapper;
        this.fundMapper = fundMapper;
        this.reserveMapper = reserveMapper;
        this.candidateMapper = candidateMapper;
        this.relationMapper = relationMapper;
        this.projectionMapper = projectionMapper;
    }

    /**
     * 批次状态、净额结果、余额流水、保证金、候选和投影任务在 transaction 主库同提交或同回滚。
     *
     * @param leasedBatch 调度阶段已领取且携带租约版本的批次
     * @param facts 已冻结汇率和逐笔结果的批次事实
     * @param owner 当前处理租约所有者
     * @param now 入账、资金流水、保证金动作和投影任务的统一时间
     * @return 实际入账并消费的候选数
     * @throws IllegalArgumentException 入参或租约所有者不合法时抛出
     * @throws IllegalStateException 批次、净额、余额、保证金责任或候选事实不一致时抛出并回滚
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public int post(SettlementBatchDO leasedBatch,
                    SettlementBatchFacts facts,
                    String owner,
                    LocalDateTime now) {
        Objects.requireNonNull(leasedBatch, "leased settlement batch is required");
        Objects.requireNonNull(facts, "settlement posting facts are required");
        Objects.requireNonNull(now, "settlement posting time is required");
        String normalizedOwner = requireOwner(owner);
        SettlementBatchDO batch = batchMapper.selectByBatchNoForUpdate(leasedBatch.getSettlementBatchNo());
        validateBatch(batch, facts, normalizedOwner, now);
        if (batchMapper.beginPosting(batch.getSettlementBatchNo(), normalizedOwner,
                batch.getVersion(), now) != 1) {
            throw failure("SETTLEMENT_POSTING_STATE_CAS_FAILED", true,
                    "settlement batch posting state CAS failed");
        }
        batch.setBatchStatus(SettlementBatchStatus.POSTING.name());
        batch.setVersion(batch.getVersion() + 1);

        List<SettlementResultItemDO> financialItems = resultMapper.selectFinancialItemsByBatch(
                batch.getSettlementBatchNo());
        if (financialItems == null || financialItems.isEmpty()) {
            throw failure("SETTLEMENT_POSTING_RESULT_EMPTY", false,
                    "settlement batch has no financial result items");
        }
        NetAmount net = net(batch, financialItems);
        insertNetResult(batch, net, now);
        postFundLedger(batch, net, now);
        materializeReserve(batch, facts, now);

        List<SettlementProjectionTaskDO> tasks = projectionTasks(batch, facts, financialItems, now);
        long projectableCandidateCount = facts.candidates().stream()
                .filter(candidate -> "CLEARING_REVISION".equals(candidate.getSourceType()))
                .count();
        if ("MANUAL_REVIEW".equals(batch.getCreateMode())
                && !Objects.equals(batch.getProjectableCandidateCount(),
                Math.toIntExact(projectableCandidateCount))) {
            throw failure("SETTLEMENT_PROJECTABLE_COUNT_CHANGED", false,
                    "formal projectable candidate count differs from the approved review snapshot");
        }
        if (tasks.size() != projectableCandidateCount
                || !tasks.isEmpty() && projectionMapper.insertTasksIdempotent(tasks) != tasks.size()) {
            throw failure("SETTLEMENT_PROJECTION_TASK_COUNT_MISMATCH", true,
                    "settlement projection task count is inconsistent");
        }
        if (candidateMapper.markBatchPosted(batch.getSettlementBatchNo(), now) != batch.getCandidateCount()
                || relationMapper.markBatchPosted(batch.getSettlementBatchNo(), now) != batch.getCandidateCount()) {
            throw failure("SETTLEMENT_POSTED_CANDIDATE_COUNT_MISMATCH", false,
                    "settlement posted candidate count is inconsistent");
        }
        if (batchMapper.markPosted(batch.getSettlementBatchNo(), normalizedOwner,
                batch.getVersion(), now) != 1) {
            throw failure("SETTLEMENT_POSTED_STATE_CAS_FAILED", true,
                    "settlement batch posted state CAS failed");
        }
        leasedBatch.setBatchStatus(SettlementBatchStatus.POSTED.name());
        leasedBatch.setPostedTime(now);
        leasedBatch.setProcessingOwner(null);
        leasedBatch.setProcessingDeadline(null);
        leasedBatch.setVersion(batch.getVersion() + 1);
        return batch.getCandidateCount();
    }

    /** 汇总 FINANCIAL_COMPONENT 的目标币种有符号金额，得到唯一非负净额和 CREDIT/DEBIT 方向。 */
    private NetAmount net(SettlementBatchDO batch, List<SettlementResultItemDO> items) {
        BigDecimal signed = ZERO;
        for (SettlementResultItemDO item : items) {
            if (!batch.getTargetCurrency().equals(item.getTargetCurrency())
                    || item.getTargetAmount() == null || item.getTargetAmount().signum() < 0) {
                throw failure("SETTLEMENT_POSTING_CURRENCY_OR_AMOUNT_INVALID", false,
                        "settlement financial result currency or amount is invalid");
            }
            signed = switch (item.getDirection()) {
                case "CREDIT" -> signed.add(item.getTargetAmount());
                case "DEBIT" -> signed.subtract(item.getTargetAmount());
                default -> throw failure("SETTLEMENT_POSTING_DIRECTION_INVALID", false,
                        "settlement financial result direction is invalid");
            };
        }
        String direction = signed.signum() < 0 ? "DEBIT" : "CREDIT";
        BigDecimal amount = signed.abs().setScale(batch.getTargetCurrencyExponent());
        return new NetAmount(direction, amount, signed);
    }

    /** 追加批次唯一 NET_POSTING 结果行，并通过回读身份校验区分合法重放与唯一键碰撞。 */
    private void insertNetResult(SettlementBatchDO batch, NetAmount net, LocalDateTime now) {
        Long identityRateId = resultMapper.selectIdentityRateId(
                batch.getSettlementBatchNo(), batch.getTargetCurrency());
        if (identityRateId == null || identityRateId <= 0) {
            throw failure("SETTLEMENT_IDENTITY_RATE_MISSING", false,
                    "settlement target-currency identity rate is missing");
        }
        SettlementResultItemDO item = new SettlementResultItemDO();
        item.setSettlementResultItemNo(stableId("SRN", batch.getSettlementBatchNo()));
        item.setSettlementBatchNo(batch.getSettlementBatchNo());
        item.setCandidateId(null);
        item.setResultLineNo(1);
        item.setMerchantId(batch.getMerchantId());
        item.setSettlementAccountId(batch.getSettlementAccountId());
        item.setSourceDetailType("BATCH_NET");
        item.setResultItemType("NET_SETTLEMENT");
        item.setResultRole("LEDGER_POSTING");
        item.setDirection(net.direction());
        item.setSourceAmount(net.amount());
        item.setSourceCurrency(batch.getTargetCurrency());
        item.setSourceCurrencyExponent(batch.getTargetCurrencyExponent());
        item.setSettlementBatchRateId(identityRateId);
        item.setUnroundedTargetAmount(net.amount());
        item.setTargetAmount(net.amount());
        item.setTargetCurrency(batch.getTargetCurrency());
        item.setTargetCurrencyExponent(batch.getTargetCurrencyExponent());
        item.setAppliedLimit("NONE");
        item.setRoundingMode("HALF_UP");
        item.setFormulaSnapshot("signed sum of FINANCIAL_COMPONENT target amounts in the locked batch currency");
        item.setLedgerIdempotencyKey(ledgerIdempotencyKey(batch.getSettlementBatchNo()));
        item.setCreateTime(now);
        int inserted = resultMapper.insertItemsIdempotent(List.of(item));
        if ((inserted != 0 && inserted != 1)
                || resultMapper.countLedgerPostingByBatch(batch.getSettlementBatchNo()) != 1) {
            throw failure("SETTLEMENT_NET_RESULT_COUNT_INVALID", false,
                    "settlement batch must contain exactly one net ledger result");
        }
    }

    /** 固定先锁 NORMAL 账户，追加批次唯一资金流水，再用旧余额和版本双条件 CAS 更新可用余额。 */
    private void postFundLedger(SettlementBatchDO batch, NetAmount net, LocalDateTime now) {
        MerchantFundAccountDO account = fundMapper.selectAccountForUpdate(batch.getSettlementAccountId());
        validateAccount(account, batch);
        if (net.amount().signum() == 0) {
            return;
        }
        String idempotencyKey = ledgerIdempotencyKey(batch.getSettlementBatchNo());
        MerchantFundLedgerDO existing = fundMapper.selectLedgerByIdempotencyForUpdate(idempotencyKey);
        if (existing != null) {
            validateExistingLedger(existing, batch, net);
            return;
        }
        long sequence = Objects.requireNonNullElse(
                fundMapper.selectMaxAccountSequence(account.getId()), 0L) + 1L;
        BigDecimal before = account.getAvailableBalance();
        BigDecimal after = "CREDIT".equals(net.direction())
                ? before.add(net.amount()) : before.subtract(net.amount());
        MerchantFundLedgerDO ledger = ledger(batch, net, before, after, sequence, idempotencyKey, now);
        if (fundMapper.insertLedger(ledger) != 1
                || fundMapper.updateAccountBalance(account.getId(), after, before,
                account.getAccountVersion(), now) != 1) {
            throw failure("SETTLEMENT_FUND_POSTING_FAILED", true,
                    "settlement fund ledger and account update failed");
        }
    }

    /** 构造包含账户序号、余额前后值及完整人工/系统审计的不可变净入账流水。 */
    private MerchantFundLedgerDO ledger(SettlementBatchDO batch,
                                        NetAmount net,
                                        BigDecimal before,
                                        BigDecimal after,
                                        long sequence,
                                        String idempotencyKey,
                                        LocalDateTime now) {
        MerchantFundLedgerDO row = new MerchantFundLedgerDO();
        row.setLedgerNo(stableId("SL", batch.getSettlementBatchNo()));
        row.setLedgerGroupNo(batch.getSettlementBatchNo());
        row.setAccountId(batch.getSettlementAccountId());
        row.setMerchantId(batch.getMerchantId());
        row.setBusinessType(("RESERVE_RELEASE".equals(batch.getBatchType())
                || "ADJUSTMENT".equals(batch.getBatchType()))
                ? "RESERVE_SETTLEMENT" : "TRANSACTION_SETTLEMENT");
        boolean manual = "MANUAL_REVIEW".equals(batch.getCreateMode());
        row.setSummary(manual ? "Approved manual settlement batch net posting"
                : "Settlement batch net posting");
        row.setBusinessNo(batch.getSettlementBatchNo());
        row.setSettlementBatchNo(batch.getSettlementBatchNo());
        row.setCurrency(batch.getTargetCurrency());
        row.setDirection(net.direction());
        row.setAmount(net.amount());
        row.setBalanceBefore(before);
        row.setBalanceAfter(after);
        row.setAccountSequence(sequence);
        row.setOperationMode(manual ? "MANUAL" : "AUTO");
        row.setOperatorId(manual ? batch.getMakerAccountId() : null);
        row.setOperatorName(manual ? batch.getMakerAccountName() : SYSTEM_OPERATOR);
        row.setReviewerId(manual ? batch.getCheckerAccountId() : null);
        row.setReviewerName(manual ? batch.getCheckerAccountName() : null);
        row.setOperationReason(manual ? batch.getMakerReason()
                : "Automatic settlement for business date " + batch.getBusinessDate());
        row.setReviewComment(manual ? batch.getCheckerComment() : null);
        row.setBusinessTime(manual ? batch.getMakerTime() : now);
        row.setSubmitTime(manual ? batch.getMakerTime() : null);
        row.setReviewTime(manual ? batch.getCheckerTime() : null);
        row.setPostedTime(now);
        row.setRequestId(manual ? batch.getReviewOrderNo() : null);
        row.setIdempotencyKey(idempotencyKey);
        row.setCreateTime(now);
        return row;
    }

    /** 按候选和清分行稳定顺序资金化保证金，动作唯一键决定聚合是否需要首次更新。 */
    private void materializeReserve(SettlementBatchDO batch,
                                    SettlementBatchFacts facts,
                                    LocalDateTime now) {
        Map<FactKey, Long> candidateIds = new HashMap<>();
        for (SettlementCandidateDO candidate : facts.candidates()) {
            candidateIds.put(new FactKey(candidate.getSourceTransactionId(),
                    candidate.getSourceTransactionDateTime(), candidate.getSourceRevision()), candidate.getId());
        }
        for (SettlementReserveClearingDetailDO detail : facts.reserveDetails().stream()
                .sorted(Comparator.comparing(SettlementReserveClearingDetailDO::getTransactionDateTime)
                        .thenComparing(SettlementReserveClearingDetailDO::getLineNo,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()) {
            Long candidateId = candidateIds.get(new FactKey(detail.getTransactionId(),
                    detail.getTransactionDateTime(), detail.getClearingRevision()));
            if (candidateId == null) {
                throw failure("SETTLEMENT_RESERVE_CANDIDATE_MISSING", false,
                        "reserve clearing fact has no batch candidate");
            }
            applyReserveDetail(batch, candidateId, detail, now);
        }
    }

    /** 将 HOLD、RETURN、RELEASE 或 ADJUSTMENT 清分事实应用到原标签币种聚合，拒绝未知动作。 */
    private void applyReserveDetail(SettlementBatchDO batch,
                                    Long candidateId,
                                    SettlementReserveClearingDetailDO detail,
                                    LocalDateTime now) {
        BigDecimal amount = reserveAmount(detail);
        if (amount.signum() <= 0) {
            throw failure("SETTLEMENT_RESERVE_AMOUNT_INVALID", false,
                    "reserve action amount must be positive");
        }
        if ("HOLD".equals(detail.getReserveActionType())) {
            MerchantReserveItemDO expected = reserveItem(batch, detail, amount, now);
            int inserted = reserveMapper.insertItemIdempotent(expected);
            if (inserted != 0 && inserted != 1) {
                throw failure("SETTLEMENT_RESERVE_HOLD_INSERT_FAILED", true,
                        "reserve hold insert affected an unexpected row count");
            }
            MerchantReserveItemDO stored = reserveMapper.selectBySourceForUpdate(
                    detail.getMerchantId(), detail.getReserveClearingDetailNo());
            validateReserveIdentity(stored, expected);
            insertReserveAction(batch, candidateId, detail, stored, amount, now);
            return;
        }
        if (!"RETURN".equals(detail.getReserveActionType())
                && !"RELEASE".equals(detail.getReserveActionType())
                && !"ADJUSTMENT".equals(detail.getReserveActionType())) {
            throw failure("SETTLEMENT_RESERVE_ACTION_INVALID", false,
                    "reserve action is unsupported during posting");
        }
        if (detail.getSourceReserveDetailNo() == null || detail.getSourceReserveDetailNo().isBlank()) {
            throw failure("SETTLEMENT_RESERVE_SOURCE_MISSING", false,
                    "reserve return, release or adjustment must reference the original hold detail");
        }
        MerchantReserveItemDO stored = reserveMapper.selectBySourceForUpdate(
                detail.getMerchantId(), detail.getSourceReserveDetailNo());
        if (stored == null || stored.getVersion() == null
                || !detail.getReserveCurrency().equals(stored.getCurrency())
                || !Objects.equals(batch.getSettlementAccountId(), stored.getAccountId())
                || !Objects.equals(detail.getMerchantId(), stored.getMerchantId())) {
            throw failure("SETTLEMENT_RESERVE_SOURCE_INVALID", false,
                    "reserve source is missing or mismatched");
        }
        boolean inserted = insertReserveAction(batch, candidateId, detail, stored, amount, now);
        if (!inserted) {
            return;
        }
        int affected = switch (detail.getReserveActionType()) {
            case "RETURN" -> reserveMapper.applyReturn(stored.getId(), stored.getCurrency(), amount,
                    stored.getVersion(), now);
            case "RELEASE" -> reserveMapper.applyRelease(stored.getId(), stored.getCurrency(), amount,
                    batch.getSettlementBatchNo(), stored.getVersion(), now);
            case "ADJUSTMENT" -> applyAdjustment(stored, detail, amount, now);
            default -> 0;
        };
        if (affected != 1) {
            throw failure("SETTLEMENT_RESERVE_BALANCE_CAS_FAILED", false,
                    "reserve action exceeds the remaining liability or lost its version");
        }
    }

    /** 按 CREDIT/DEBIT 调整方向更新保证金责任，贷方调整不得超过当前剩余责任。 */
    private int applyAdjustment(MerchantReserveItemDO stored,
                                SettlementReserveClearingDetailDO detail,
                                BigDecimal amount,
                                LocalDateTime now) {
        return switch (detail.getDirection()) {
            case "DEBIT" -> reserveMapper.applyDebitAdjustment(stored.getId(), stored.getCurrency(),
                    amount, stored.getVersion(), now);
            case "CREDIT" -> reserveMapper.applyCreditAdjustment(stored.getId(), stored.getCurrency(),
                    amount, stored.getVersion(), now);
            default -> throw failure("SETTLEMENT_RESERVE_ADJUSTMENT_DIRECTION_INVALID", false,
                    "reserve adjustment direction is invalid");
        };
    }

    /** 构造保证金聚合首次 HOLD 行；保留原交易和标签币种，不写结算目标币种金额。 */
    private MerchantReserveItemDO reserveItem(SettlementBatchDO batch,
                                              SettlementReserveClearingDetailDO detail,
                                              BigDecimal amount,
                                              LocalDateTime now) {
        MerchantReserveItemDO row = new MerchantReserveItemDO();
        row.setReserveNo(stableId("RS", detail.getReserveClearingDetailNo()));
        row.setAccountId(batch.getSettlementAccountId());
        row.setMerchantId(detail.getMerchantId());
        row.setSourceTransactionId(detail.getOriginalTransactionId());
        row.setSourceBusinessNo(detail.getReserveClearingDetailNo());
        row.setCurrency(detail.getReserveCurrency());
        row.setRetainedAmount(amount);
        row.setReturnedAmount(ZERO.setScale(detail.getReserveCurrencyExponent()));
        row.setReleasedAmount(ZERO.setScale(detail.getReserveCurrencyExponent()));
        row.setDebitAdjustmentAmount(ZERO.setScale(detail.getReserveCurrencyExponent()));
        row.setCreditAdjustmentAmount(ZERO.setScale(detail.getReserveCurrencyExponent()));
        row.setReversedAmount(ZERO.setScale(detail.getReserveCurrencyExponent()));
        row.setReserveStatus("HELD");
        row.setExpectedReleaseDate(detail.getExpectedReserveReleaseDate());
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** @return true 表示本次首次插入并需要改变聚合，false 表示已验证的合法重放。 */
    private boolean insertReserveAction(SettlementBatchDO batch,
                                        Long candidateId,
                                        SettlementReserveClearingDetailDO detail,
                                        MerchantReserveItemDO item,
                                        BigDecimal amount,
                                        LocalDateTime now) {
        MerchantReserveActionDO action = new MerchantReserveActionDO();
        action.setReserveActionNo(stableId("RA", detail.getReserveClearingDetailNo()));
        action.setReserveItemId(item.getId());
        action.setReserveNo(item.getReserveNo());
        action.setSettlementBatchNo(batch.getSettlementBatchNo());
        action.setCandidateId(candidateId);
        action.setSourceReserveDetailNo(detail.getReserveClearingDetailNo());
        action.setActionType(detail.getReserveActionType());
        action.setDirection(detail.getDirection());
        action.setCurrency(detail.getReserveCurrency());
        action.setAmount(amount);
        action.setActionTime(now);
        action.setCreateTime(now);
        int inserted = reserveMapper.insertActionIdempotent(action);
        if (inserted == 1) {
            return true;
        }
        MerchantReserveActionDO stored = reserveMapper.selectActionForUpdate(action.getReserveActionNo());
        boolean matches = inserted == 0 && stored != null
                && Objects.equals(stored.getReserveItemId(), action.getReserveItemId())
                && Objects.equals(stored.getSettlementBatchNo(), action.getSettlementBatchNo())
                && Objects.equals(stored.getSourceReserveDetailNo(), action.getSourceReserveDetailNo())
                && Objects.equals(stored.getActionType(), action.getActionType())
                && Objects.equals(stored.getDirection(), action.getDirection())
                && Objects.equals(stored.getCurrency(), action.getCurrency())
                && stored.getAmount() != null && stored.getAmount().compareTo(action.getAmount()) == 0;
        if (!matches) {
            throw failure("SETTLEMENT_RESERVE_ACTION_IDEMPOTENCY_CONFLICT", false,
                    "reserve action idempotency identity is inconsistent");
        }
        return false;
    }

    /** 仅为真实 CLEARING_REVISION 候选创建 SETTLE 任务，RESERVE_RELEASE 和纯 ADJUSTMENT 不生成伪交易。 */
    private List<SettlementProjectionTaskDO> projectionTasks(SettlementBatchDO batch,
                                                             SettlementBatchFacts facts,
                                                             List<SettlementResultItemDO> items,
                                                             LocalDateTime now) {
        Map<Long, BigDecimal> signedByCandidate = new HashMap<>();
        for (SettlementResultItemDO item : items) {
            BigDecimal signed = "CREDIT".equals(item.getDirection())
                    ? item.getTargetAmount() : item.getTargetAmount().negate();
            signedByCandidate.merge(item.getCandidateId(), signed, BigDecimal::add);
        }
        Map<FactKey, String> operationIds = new HashMap<>();
        facts.transactionDetails().forEach(row -> operationIds.putIfAbsent(
                new FactKey(row.getTransactionId(), row.getTransactionDateTime(), row.getClearingRevision()),
                row.getOperationId()));
        facts.reserveDetails().forEach(row -> operationIds.putIfAbsent(
                new FactKey(row.getTransactionId(), row.getTransactionDateTime(), row.getClearingRevision()),
                row.getOperationId()));
        List<SettlementProjectionTaskDO> tasks = new ArrayList<>(facts.candidates().size());
        for (SettlementCandidateDO candidate : facts.candidates()) {
            if (!"CLEARING_REVISION".equals(candidate.getSourceType())) {
                continue;
            }
            FactKey key = new FactKey(candidate.getSourceTransactionId(),
                    candidate.getSourceTransactionDateTime(), candidate.getSourceRevision());
            String operationId = operationIds.get(key);
            if (candidate.getSourceTransactionId() == null
                    || candidate.getSourceTransactionDateTime() == null
                    || operationId == null || operationId.isBlank()) {
                throw failure("SETTLEMENT_PROJECTION_IDENTITY_MISSING", false,
                        "settlement projection identity is incomplete");
            }
            SettlementProjectionTaskDO task = new SettlementProjectionTaskDO();
            task.setTaskNo(stableId("SP", batch.getSettlementBatchNo() + "|" + candidate.getId()));
            task.setSettlementBatchNo(batch.getSettlementBatchNo());
            task.setProjectionAction("SETTLE");
            task.setCandidateId(candidate.getId());
            task.setTransactionId(candidate.getSourceTransactionId());
            task.setTransactionDateTime(candidate.getSourceTransactionDateTime());
            task.setClearingRevision(candidate.getSourceRevision());
            task.setOperationId(operationId);
            task.setMerchantId(candidate.getMerchantId());
            task.setSettlementCurrency(batch.getTargetCurrency());
            task.setSettlementAmount(signedByCandidate.getOrDefault(candidate.getId(), ZERO)
                    .setScale(batch.getTargetCurrencyExponent()));
            task.setSettlementDate(batch.getBusinessDate());
            task.setTaskStatus("INIT");
            task.setRetryCount(0);
            task.setNextRetryTime(now);
            task.setVersion(0L);
            task.setCreateTime(now);
            task.setUpdateTime(now);
            tasks.add(task);
        }
        return tasks;
    }

    /** 从清分保证金行提取唯一非负动作金额，并校验动作类型与对应金额列一致。 */
    private BigDecimal reserveAmount(SettlementReserveClearingDetailDO row) {
        BigDecimal amount = switch (row.getReserveActionType()) {
            case "HOLD" -> row.getRetainedAmount();
            case "RETURN" -> row.getReturnedAmount();
            case "RELEASE" -> row.getReleasedAmount();
            case "ADJUSTMENT" -> row.getAdjustmentAmount();
            default -> null;
        };
        return amount == null ? ZERO : amount;
    }

    /** 校验租约、批次状态、候选/事实数量、净结果和投影计数均与锁读批次一致。 */
    private void validateBatch(SettlementBatchDO batch,
                               SettlementBatchFacts facts,
                               String owner,
                               LocalDateTime now) {
        if (batch == null || batch.getVersion() == null || batch.getCandidateCount() == null
                || batch.getCandidateCount() <= 0 || batch.getCandidateCount() != facts.candidates().size()
                || !owner.equals(batch.getProcessingOwner())
                || batch.getProcessingDeadline() == null || !batch.getProcessingDeadline().isAfter(now)) {
            throw failure("SETTLEMENT_POSTING_LEASE_OR_COUNT_INVALID", true,
                    "settlement posting lease or candidate count is invalid");
        }
        SettlementBatchStatus status;
        try {
            status = SettlementBatchStatus.valueOf(batch.getBatchStatus());
        } catch (RuntimeException exception) {
            throw failure("SETTLEMENT_POSTING_STATUS_INVALID", false,
                    "settlement posting status is invalid");
        }
        boolean retry = status == SettlementBatchStatus.FAILED_RETRYABLE
                && SettlementFailureStage.LEDGER_POSTING.name().equals(batch.getLastFailureStage());
        if (status != SettlementBatchStatus.CALCULATED && !retry) {
            throw failure("SETTLEMENT_POSTING_STATUS_INVALID", false,
                    "settlement batch is not ready for posting");
        }
        if ("MANUAL_REVIEW".equals(batch.getCreateMode())
                && (batch.getMakerAccountId() == null || batch.getMakerAccountName() == null
                || batch.getMakerReason() == null || batch.getMakerTime() == null
                || batch.getCheckerAccountId() == null || batch.getCheckerAccountName() == null
                || batch.getCheckerComment() == null || batch.getCheckerTime() == null
                || Objects.equals(batch.getMakerAccountId(), batch.getCheckerAccountId()))) {
            throw failure("SETTLEMENT_MANUAL_AUDIT_INCOMPLETE", false,
                    "manual settlement batch has an incomplete Maker-Checker audit snapshot");
        }
    }

    /** 资金入账只接受批次商户、目标币种均匹配且状态为 NORMAL 的账户。 */
    private void validateAccount(MerchantFundAccountDO account, SettlementBatchDO batch) {
        if (account == null || account.getAvailableBalance() == null || account.getAccountVersion() == null
                || !Objects.equals(account.getId(), batch.getSettlementAccountId())
                || !Objects.equals(account.getMerchantId(), batch.getMerchantId())
                || !Objects.equals(account.getSettlementCurrency(), batch.getTargetCurrency())
                || !"NORMAL".equals(account.getAccountStatus())) {
            throw failure("SETTLEMENT_FUND_ACCOUNT_INVALID", false,
                    "settlement fund account is missing, frozen or mismatched");
        }
    }

    /** 对流水幂等键重放核对账户、商户、批次、币种、方向、金额和余额前后值。 */
    private void validateExistingLedger(MerchantFundLedgerDO ledger,
                                        SettlementBatchDO batch,
                                        NetAmount net) {
        boolean manual = "MANUAL_REVIEW".equals(batch.getCreateMode());
        String expectedBusinessType = ("RESERVE_RELEASE".equals(batch.getBatchType())
                || "ADJUSTMENT".equals(batch.getBatchType()))
                ? "RESERVE_SETTLEMENT" : "TRANSACTION_SETTLEMENT";
        boolean matches = Objects.equals(ledger.getAccountId(), batch.getSettlementAccountId())
                && Objects.equals(ledger.getMerchantId(), batch.getMerchantId())
                && Objects.equals(ledger.getBusinessType(), expectedBusinessType)
                && Objects.equals(ledger.getBusinessNo(), batch.getSettlementBatchNo())
                && Objects.equals(ledger.getSettlementBatchNo(), batch.getSettlementBatchNo())
                && Objects.equals(ledger.getCurrency(), batch.getTargetCurrency())
                && Objects.equals(ledger.getDirection(), net.direction())
                && Objects.equals(ledger.getIdempotencyKey(),
                ledgerIdempotencyKey(batch.getSettlementBatchNo()))
                && auditMatches(ledger, batch, manual)
                && ledger.getAmount() != null && ledger.getAmount().compareTo(net.amount()) == 0;
        if (!matches) {
            throw failure("SETTLEMENT_LEDGER_IDEMPOTENCY_CONFLICT", false,
                    "settlement ledger idempotency identity is inconsistent");
        }
    }

    /** 核对重放资金流水中的人工 Maker-Checker 或系统操作审计与批次冻结快照一致。 */
    private boolean auditMatches(MerchantFundLedgerDO ledger,
                                 SettlementBatchDO batch,
                                 boolean manual) {
        if (manual) {
            return "MANUAL".equals(ledger.getOperationMode())
                    && Objects.equals(ledger.getOperatorId(), batch.getMakerAccountId())
                    && Objects.equals(ledger.getOperatorName(), batch.getMakerAccountName())
                    && Objects.equals(ledger.getReviewerId(), batch.getCheckerAccountId())
                    && Objects.equals(ledger.getReviewerName(), batch.getCheckerAccountName())
                    && Objects.equals(ledger.getOperationReason(), batch.getMakerReason())
                    && Objects.equals(ledger.getReviewComment(), batch.getCheckerComment())
                    && Objects.equals(ledger.getBusinessTime(), batch.getMakerTime())
                    && Objects.equals(ledger.getSubmitTime(), batch.getMakerTime())
                    && Objects.equals(ledger.getReviewTime(), batch.getCheckerTime())
                    && Objects.equals(ledger.getRequestId(), batch.getReviewOrderNo());
        }
        return "AUTO".equals(ledger.getOperationMode())
                && ledger.getOperatorId() == null
                && SYSTEM_OPERATOR.equals(ledger.getOperatorName())
                && ledger.getReviewerId() == null
                && ledger.getReviewerName() == null
                && Objects.equals(ledger.getOperationReason(),
                "Automatic settlement for business date " + batch.getBusinessDate())
                && ledger.getReviewComment() == null
                && ledger.getSubmitTime() == null
                && ledger.getReviewTime() == null
                && ledger.getRequestId() == null;
    }

    /** 对保证金来源唯一键重放核对商户、账户、原交易和原标签币种，拒绝身份碰撞。 */
    private void validateReserveIdentity(MerchantReserveItemDO actual, MerchantReserveItemDO expected) {
        boolean matches = actual != null && actual.getId() != null
                && Objects.equals(actual.getReserveNo(), expected.getReserveNo())
                && Objects.equals(actual.getAccountId(), expected.getAccountId())
                && Objects.equals(actual.getMerchantId(), expected.getMerchantId())
                && Objects.equals(actual.getSourceBusinessNo(), expected.getSourceBusinessNo())
                && Objects.equals(actual.getCurrency(), expected.getCurrency())
                && actual.getRetainedAmount() != null
                && actual.getRetainedAmount().compareTo(expected.getRetainedAmount()) == 0;
        if (!matches) {
            throw failure("SETTLEMENT_RESERVE_HOLD_IDEMPOTENCY_CONFLICT", false,
                    "reserve hold idempotency identity is inconsistent");
        }
    }

    /** 规范化并限制租约所有者长度，防止空主体参与资金状态 CAS。 */
    private String requireOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("settlement posting owner is required");
        }
        return owner.trim();
    }

    /** 生成正式批次唯一净入账资金流水幂等键。 */
    private String ledgerIdempotencyKey(String batchNo) {
        return "SETTLEMENT:" + batchNo;
    }

    /** 以稳定业务身份生成固定长度 SHA-256 派生号，保证任务重放结果一致。 */
    private String stableId(String prefix, String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private SettlementProcessingException failure(String code, boolean retryable, String message) {
        return new SettlementProcessingException(SettlementFailureStage.LEDGER_POSTING,
                code, retryable, message);
    }

    private record NetAmount(String direction, BigDecimal amount, BigDecimal signedAmount) {
    }

    private record FactKey(String transactionId,
                           LocalDateTime transactionDateTime,
                           Integer revision) {
    }
}
