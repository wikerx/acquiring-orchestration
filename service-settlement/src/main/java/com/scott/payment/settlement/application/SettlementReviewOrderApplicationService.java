package com.scott.payment.settlement.application;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementCandidateStatus;
import com.scott.payment.settlement.domain.model.SettlementReviewStatus;
import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementCalculationPreview;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.dto.SettlementReviewCommandResult;
import com.scott.payment.settlement.dto.SettlementReviewCreateCommand;
import com.scott.payment.settlement.dto.SettlementReviewDecisionCommand;
import com.scott.payment.settlement.entity.MerchantSettlementProfileDO;
import com.scott.payment.settlement.entity.SettlementBatchCandidateDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementBatchRateDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementResultSummaryDO;
import com.scott.payment.settlement.entity.SettlementReviewCandidateDO;
import com.scott.payment.settlement.entity.SettlementReviewDailySequenceDO;
import com.scott.payment.settlement.entity.SettlementReviewOrderDO;
import com.scott.payment.settlement.entity.SettlementReviewRateDO;
import com.scott.payment.settlement.entity.SettlementReviewSummaryDO;
import com.scott.payment.settlement.mapper.MerchantSettlementProfileMapper;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementBatchRateMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementReviewCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementReviewDailySequenceMapper;
import com.scott.payment.settlement.mapper.SettlementReviewOrderMapper;
import com.scott.payment.settlement.mapper.SettlementReviewRateMapper;
import com.scott.payment.settlement.mapper.SettlementReviewSummaryMapper;
import com.scott.payment.settlement.service.SettlementBatchCreationService;
import com.scott.payment.settlement.service.SettlementClearingFactService;
import com.scott.payment.settlement.service.SettlementRateResolutionService;
import com.scott.payment.settlement.service.SettlementResultCalculationService;
import com.scott.payment.settlement.support.SettlementReviewFingerprintService;
import com.scott.payment.settlement.support.SettlementReviewNumberFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewOrderApplicationService
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 编排人工/系统结算预审与 Maker-Checker 决策；冻结候选、统一汇率、清分事实和结果指纹，批准前复算并原子转入正式批次。
 * @status : create
 */
@Service
public class SettlementReviewOrderApplicationService {

    /**
     * {@code MAX_DAILY_SEQUENCE}常量，统一 {@code SettlementReviewOrderApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int MAX_DAILY_SEQUENCE = 99_999_999;

    private final SettlementReviewDailySequenceMapper sequenceMapper;
    private final SettlementReviewOrderMapper orderMapper;
    private final SettlementReviewCandidateMapper reviewCandidateMapper;
    private final SettlementReviewRateMapper reviewRateMapper;
    private final SettlementReviewSummaryMapper reviewSummaryMapper;
    private final SettlementCandidateMapper candidateMapper;
    private final MerchantSettlementProfileMapper profileMapper;
    private final SettlementBatchCreationService batchCreationService;
    private final SettlementBatchMapper batchMapper;
    private final SettlementBatchCandidateMapper batchCandidateMapper;
    private final SettlementBatchRateMapper batchRateMapper;
    private final SettlementClearingFactService factService;
    private final SettlementRateResolutionService rateResolutionService;
    private final SettlementResultCalculationService calculationService;
    private final SettlementReviewFingerprintService fingerprintService;
    private final SettlementReviewNumberFormatter numberFormatter;
    private final Clock clock;

    @Autowired
    public SettlementReviewOrderApplicationService(
            SettlementReviewDailySequenceMapper sequenceMapper,
            SettlementReviewOrderMapper orderMapper,
            SettlementReviewCandidateMapper reviewCandidateMapper,
            SettlementReviewRateMapper reviewRateMapper,
            SettlementReviewSummaryMapper reviewSummaryMapper,
            SettlementCandidateMapper candidateMapper,
            MerchantSettlementProfileMapper profileMapper,
            SettlementBatchCreationService batchCreationService,
            SettlementBatchMapper batchMapper,
            SettlementBatchCandidateMapper batchCandidateMapper,
            SettlementBatchRateMapper batchRateMapper,
            SettlementClearingFactService factService,
            SettlementRateResolutionService rateResolutionService,
            SettlementResultCalculationService calculationService,
            SettlementReviewFingerprintService fingerprintService,
            SettlementReviewNumberFormatter numberFormatter) {
        this(sequenceMapper, orderMapper, reviewCandidateMapper, reviewRateMapper, reviewSummaryMapper,
                candidateMapper, profileMapper, batchCreationService, batchMapper, batchCandidateMapper,
                batchRateMapper, factService, rateResolutionService, calculationService,
                fingerprintService, numberFormatter, Clock.systemUTC());
    }

    SettlementReviewOrderApplicationService(
            SettlementReviewDailySequenceMapper sequenceMapper,
            SettlementReviewOrderMapper orderMapper,
            SettlementReviewCandidateMapper reviewCandidateMapper,
            SettlementReviewRateMapper reviewRateMapper,
            SettlementReviewSummaryMapper reviewSummaryMapper,
            SettlementCandidateMapper candidateMapper,
            MerchantSettlementProfileMapper profileMapper,
            SettlementBatchCreationService batchCreationService,
            SettlementBatchMapper batchMapper,
            SettlementBatchCandidateMapper batchCandidateMapper,
            SettlementBatchRateMapper batchRateMapper,
            SettlementClearingFactService factService,
            SettlementRateResolutionService rateResolutionService,
            SettlementResultCalculationService calculationService,
            SettlementReviewFingerprintService fingerprintService,
            SettlementReviewNumberFormatter numberFormatter,
            Clock clock) {
        this.sequenceMapper = sequenceMapper;
        this.orderMapper = orderMapper;
        this.reviewCandidateMapper = reviewCandidateMapper;
        this.reviewRateMapper = reviewRateMapper;
        this.reviewSummaryMapper = reviewSummaryMapper;
        this.candidateMapper = candidateMapper;
        this.profileMapper = profileMapper;
        this.batchCreationService = batchCreationService;
        this.batchMapper = batchMapper;
        this.batchCandidateMapper = batchCandidateMapper;
        this.batchRateMapper = batchRateMapper;
        this.factService = factService;
        this.rateResolutionService = rateResolutionService;
        this.calculationService = calculationService;
        this.fingerprintService = fingerprintService;
        this.numberFormatter = numberFormatter;
        this.clock = clock;
    }

    /**
     * 创建人工预审单并原子冻结候选、清分事实、统一汇率矩阵和计算结果。
     *
     * @param command 候选集合、业务窗口、请求幂等键及可信 Maker 快照
     * @return 新建或相同幂等身份重放的待复核预审结果
     * @throws IllegalArgumentException 命令字段不合法时抛出
     * @throws IllegalStateException 候选混维、版本过期、依赖未解决或快照写入不完整时抛出
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public SettlementReviewCommandResult submit(SettlementReviewCreateCommand command) {
        return submit(command, "MANUAL");
    }

    /**
     * 系统按 AUTO_REVIEW 档案生成待审批单；系统主体固定为账号 0，不经过浏览器接口。
     *
     * @param command 已含系统主体、候选版本、业务日期和截止窗口的自动预审命令
     * @return 新建或相同幂等身份回放的待复核预审结果
     * @throws IllegalArgumentException 操作主体不是固定系统账号 0 时抛出
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public SettlementReviewCommandResult submitAutomatic(SettlementReviewCreateCommand command) {
        if (command == null || command.submitter().accountId() != 0L) {
            throw new IllegalArgumentException("automatic settlement review requires the system operator");
        }
        return submit(command, "AUTO_REVIEW");
    }

    /** 在单个 transaction 主库事务内分配预审号、锁定候选并冻结事实/汇率/结果快照。 */
    private SettlementReviewCommandResult submit(SettlementReviewCreateCommand command, String createMode) {
        Objects.requireNonNull(command, "settlement review command is required");
        sequenceMapper.insertIfAbsent(command.businessDate());
        SettlementReviewDailySequenceDO sequence = sequenceMapper.selectForUpdate(command.businessDate());
        requireSequence(sequence);
        SettlementReviewOrderDO existing = orderMapper.selectByCreateRequestKeyForUpdate(command.requestKey());
        if (existing != null) {
            verifyCreateIdentity(existing, command, createMode);
            return result(existing);
        }

        int next = sequence.getCurrentSequence() + 1;
        if (next > MAX_DAILY_SEQUENCE || sequenceMapper.increment(command.businessDate(),
                sequence.getCurrentSequence(), sequence.getVersion()) != 1) {
            throw new IllegalStateException("settlement review daily sequence CAS failed");
        }
        String reviewOrderNo = numberFormatter.storageNumber(command.businessDate(), next);
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> candidateIds = command.candidates().stream()
                .map(SettlementReviewCreateCommand.CandidateReference::candidateId).sorted().toList();
        List<SettlementCandidateDO> candidates = safe(candidateMapper.selectByIdsForUpdate(candidateIds));
        if (candidates.size() != candidateIds.size()) {
            throw new IllegalStateException("one or more settlement review candidates do not exist");
        }
        MerchantSettlementProfileDO profile = validateSelection(command, candidates);
        if (candidateMapper.countUnresolvedReviewDependencies(candidateIds) != 0) {
            throw new IllegalStateException("settlement review contains unresolved candidate dependencies");
        }
        if (candidateMapper.lockForReview(candidates, reviewOrderNo, profile.getId(), now)
                != candidates.size()) {
            throw new IllegalStateException("settlement review candidate lock CAS affected an unexpected row count");
        }
        candidates.forEach(row -> {
            row.setCandidateStatus(SettlementCandidateStatus.REVIEW_LOCKED.name());
            row.setReviewOrderNo(reviewOrderNo);
            row.setReviewLockedTime(now);
            row.setVersion(row.getVersion() + 1);
        });

        SettlementReviewOrderDO order = newOrder(command, createMode, reviewOrderNo, profile, candidates, now);
        SettlementBatchFacts facts = factService.loadReviewSelection(order, candidates);
        RateMatrix resolved = rateResolutionService.resolve(facts.currencies(), order.getTargetCurrency(),
                order.getTargetCurrencyExponent(), now);
        List<SettlementReviewRateDO> reviewRates = reviewRates(reviewOrderNo, resolved, command.submitter(), now);
        SettlementLockedRateMatrix lockedRates = toLockedRates(reviewRates, false);
        SettlementCalculationPreview preview = calculationService.preview(previewBatch(order), facts,
                lockedRates, now);
        order.setSourceFingerprint(fingerprintService.source(facts));
        order.setRateFingerprint(fingerprintService.rates(reviewRates));
        order.setResultFingerprint(fingerprintService.result(preview));
        order.setNetDirection(preview.netDirection());
        order.setNetAmount(preview.netAmount());

        List<SettlementReviewCandidateDO> reviewCandidates = reviewCandidates(
                reviewOrderNo, candidates, facts, now);
        List<SettlementReviewSummaryDO> summaries = reviewSummaries(reviewOrderNo, preview.summaries(), now);
        orderMapper.insertIdempotent(order);
        if (reviewCandidateMapper.insertBatchIdempotent(reviewCandidates) != reviewCandidates.size()
                || reviewRateMapper.insertBatchIdempotent(reviewRates) != reviewRates.size()
                || reviewSummaryMapper.insertBatchIdempotent(summaries) != summaries.size()) {
            throw new IllegalStateException("settlement review immutable snapshot insert count is inconsistent");
        }
        SettlementReviewOrderDO stored = orderMapper.selectByCreateRequestKeyForUpdate(command.requestKey());
        verifyStoredOrder(stored, order);
        return result(stored);
    }

    /**
     * 执行预审终态决策；批准前复核 Maker-Checker、候选所有权、档案、清分事实、汇率和结果指纹。
     *
     * @param reviewOrderNo 待决策结算预审单号
     * @param command 决策、期望版本、请求幂等键及可信 Checker 快照
     * @return 决策后的终态结果；批准时包含正式结算批次号
     * @throws IllegalArgumentException 单号或命令不合法时抛出
     * @throws IllegalStateException 状态/版本 CAS、Maker-Checker 或任一冻结事实一致性校验失败时抛出
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public SettlementReviewCommandResult decide(String reviewOrderNo,
                                                SettlementReviewDecisionCommand command) {
        if (reviewOrderNo == null || reviewOrderNo.isBlank()) {
            throw new IllegalArgumentException("settlement review order number is required");
        }
        Objects.requireNonNull(command, "settlement review decision command is required");
        SettlementReviewOrderDO replay = orderMapper.selectByDecisionRequestKeyForUpdate(command.requestKey());
        if (replay != null) {
            verifyDecisionReplay(replay, reviewOrderNo.trim(), command);
            return result(replay);
        }
        SettlementReviewOrderDO order = orderMapper.selectByReviewOrderNoForUpdate(reviewOrderNo.trim());
        requirePendingDecision(order, command);
        validateMakerChecker(order, command);
        List<SettlementReviewCandidateDO> relations = safe(
                reviewCandidateMapper.selectByOrderNoForUpdate(order.getReviewOrderNo()));
        List<SettlementCandidateDO> candidates = lockAndValidateReviewCandidates(order, relations);
        LocalDateTime now = LocalDateTime.now(clock);
        return switch (command.decision()) {
            case "APPROVE" -> approve(order, command, relations, candidates, now);
            case "REJECT" -> terminate(order, command, relations, candidates,
                    SettlementReviewStatus.REJECTED, now);
            case "CANCEL" -> terminate(order, command, relations, candidates,
                    SettlementReviewStatus.CANCELLED, now);
            default -> throw new IllegalArgumentException("unsupported settlement review decision");
        };
    }

    /** 复算四类指纹后，将预审锁原子转换为正式批次关系和冻结汇率，并进入 APPROVED 终态。 */
    private SettlementReviewCommandResult approve(SettlementReviewOrderDO order,
                                                  SettlementReviewDecisionCommand command,
                                                  List<SettlementReviewCandidateDO> relations,
                                                  List<SettlementCandidateDO> candidates,
                                                  LocalDateTime now) {
        MerchantSettlementProfileDO profile = profileMapper.selectReviewEligibleProfileForUpdate(
                order.getSettlementProfileId(), order.getBusinessDate());
        validateProfile(order, profile);
        SettlementBatchFacts facts = factService.loadReview(order);
        if (!Objects.equals(order.getSourceFingerprint(), fingerprintService.source(facts))) {
            throw new IllegalStateException("settlement review clearing facts changed after submission");
        }
        List<SettlementReviewRateDO> reviewRates = safe(reviewRateMapper.selectByOrderNo(order.getReviewOrderNo()));
        if (!Objects.equals(order.getRateFingerprint(), fingerprintService.rates(reviewRates))) {
            throw new IllegalStateException("settlement review locked rate matrix changed after submission");
        }
        SettlementCalculationPreview preview = calculationService.preview(previewBatch(order), facts,
                toLockedRates(reviewRates, true), now);
        if (!Objects.equals(order.getResultFingerprint(), fingerprintService.result(preview))
                || !Objects.equals(order.getNetDirection(), preview.netDirection())
                || order.getNetAmount().compareTo(preview.netAmount()) != 0) {
            throw new IllegalStateException("settlement review financial result changed after submission");
        }

        SettlementBatchCreateResult created = batchCreationService.create(new SettlementBatchCreateCommand(
                "review:" + order.getReviewOrderNo(), order.getBusinessDate(), order.getBusinessTimeZone(),
                order.getMerchantId(), order.getSettlementProfileId(), order.getSettlementAccountId(),
                order.getTargetCurrency(), order.getTargetCurrencyExponent(),
                com.scott.payment.settlement.domain.model.SettlementBatchType.valueOf(order.getReviewType()),
                null, order.getCutoffBeginTime(), order.getCutoffEndTime()));
        SettlementBatchDO batch = batchMapper.selectByBatchNoForUpdate(created.settlementBatchNo());
        bindReviewAudit(batch, order, command, now);
        if (batchMapper.bindApprovedReview(batch, batch.getVersion()) != 1) {
            throw new IllegalStateException("approved settlement review batch audit CAS failed");
        }
        batch.setVersion(batch.getVersion() + 1);
        batch.setBatchStatus(SettlementBatchStatus.CLAIMED.name());
        List<SettlementBatchRateDO> batchRates = batchRates(batch.getSettlementBatchNo(), reviewRates, now);
        if (batchRateMapper.insertBatchIdempotent(batchRates) != batchRates.size()
                || safe(batchRateMapper.selectByBatchNo(batch.getSettlementBatchNo())).size() != batchRates.size()) {
            throw new IllegalStateException("approved settlement review rate copy is incomplete");
        }
        if (candidateMapper.consumeReviewLock(candidates, order.getReviewOrderNo(),
                batch.getSettlementBatchNo(), now) != candidates.size()) {
            throw new IllegalStateException("approved settlement review candidate consume CAS failed");
        }
        List<SettlementBatchCandidateDO> batchRelations = batchRelations(
                batch.getSettlementBatchNo(), candidates, now);
        if (batchCandidateMapper.insertBatchIdempotent(batchRelations) != batchRelations.size()) {
            throw new IllegalStateException("approved settlement review batch candidate snapshot is incomplete");
        }
        if (reviewCandidateMapper.markConsumed(order.getReviewOrderNo(), now) != relations.size()) {
            throw new IllegalStateException("approved settlement review relation consume CAS failed");
        }
        applyDecision(order, command, now);
        order.setSettlementBatchNo(batch.getSettlementBatchNo());
        if (orderMapper.approve(order, command.expectedVersion()) != 1) {
            throw new IllegalStateException("settlement review approval state CAS failed");
        }
        order.setReviewStatus(SettlementReviewStatus.APPROVED.name());
        order.setVersion(order.getVersion() + 1);
        return result(order);
    }

    /** 拒绝或取消预审时原子释放全部候选锁和关系，并以期望版本写入不可逆终态。 */
    private SettlementReviewCommandResult terminate(SettlementReviewOrderDO order,
                                                    SettlementReviewDecisionCommand command,
                                                    List<SettlementReviewCandidateDO> relations,
                                                    List<SettlementCandidateDO> candidates,
                                                    SettlementReviewStatus terminalStatus,
                                                    LocalDateTime now) {
        if (candidateMapper.releaseReviewLock(candidates, order.getReviewOrderNo(), now)
                != candidates.size()
                || reviewCandidateMapper.markReleased(order.getReviewOrderNo(), now) != relations.size()) {
            throw new IllegalStateException("settlement review candidate release CAS failed");
        }
        applyDecision(order, command, now);
        if (orderMapper.terminate(order, terminalStatus.name(), command.expectedVersion()) != 1) {
            throw new IllegalStateException("settlement review terminal state CAS failed");
        }
        order.setReviewStatus(terminalStatus.name());
        order.setVersion(order.getVersion() + 1);
        return result(order);
    }

    /** 校验候选均为 READY、版本匹配且同商户/档案/币种/来源，并锁定有效 NORMAL 账户档案。 */
    private MerchantSettlementProfileDO validateSelection(SettlementReviewCreateCommand command,
                                                          List<SettlementCandidateDO> candidates) {
        Map<Long, Long> expectedVersions = new HashMap<>();
        command.candidates().forEach(row -> expectedVersions.put(row.candidateId(), row.expectedVersion()));
        SettlementCandidateDO first = candidates.get(0);
        for (SettlementCandidateDO candidate : candidates) {
            boolean valid = SettlementCandidateStatus.READY.name().equals(candidate.getCandidateStatus())
                    && candidate.getSettlementBatchNo() == null && candidate.getReviewOrderNo() == null
                    && Integer.valueOf(0).equals(candidate.getShadowMode())
                    && Objects.equals(candidate.getVersion(), expectedVersions.get(candidate.getId()))
                    && Objects.equals(candidate.getMerchantId(), first.getMerchantId())
                    && Objects.equals(candidate.getSettlementProfileId(), first.getSettlementProfileId())
                    && Objects.equals(candidate.getTargetCurrency(), first.getTargetCurrency())
                    && Objects.equals(candidate.getTargetCurrencyExponent(), first.getTargetCurrencyExponent())
                    && sourceMatches(command.reviewType().name(), candidate.getSourceType())
                    && candidate.getSettlementEligibleDate() != null
                    && !candidate.getSettlementEligibleDate().isAfter(command.businessDate())
                    && candidate.getCreateTime() != null
                    && candidate.getCreateTime().isBefore(command.cutoffEndTime());
            if (!valid) {
                throw new IllegalStateException("settlement review candidate is stale or has mixed dimensions");
            }
        }
        MerchantSettlementProfileDO profile = profileMapper.selectReviewEligibleProfileForUpdate(
                first.getSettlementProfileId(), command.businessDate());
        if (profile == null || !Objects.equals(profile.getMerchantId(), first.getMerchantId())
                || !Objects.equals(profile.getTargetCurrency(), first.getTargetCurrency())
                || !Objects.equals(profile.getTargetCurrencyExponent(), first.getTargetCurrencyExponent())) {
            throw new IllegalStateException("settlement review profile or NORMAL account is unavailable");
        }
        return profile;
    }

    /** 按候选 ID 稳定锁序回读，核对预审关系、候选版本和独占所有权未被改变。 */
    private List<SettlementCandidateDO> lockAndValidateReviewCandidates(
            SettlementReviewOrderDO order, List<SettlementReviewCandidateDO> relations) {
        if (relations.size() != order.getCandidateCount()
                || relations.stream().anyMatch(row -> !"LOCKED".equals(row.getRelationStatus()))) {
            throw new IllegalStateException("settlement review candidate relations are incomplete");
        }
        List<Long> ids = relations.stream().map(SettlementReviewCandidateDO::getCandidateId).sorted().toList();
        List<SettlementCandidateDO> candidates = safe(candidateMapper.selectByIdsForUpdate(ids));
        Map<Long, SettlementReviewCandidateDO> relationByCandidate = new HashMap<>();
        relations.forEach(row -> relationByCandidate.put(row.getCandidateId(), row));
        for (SettlementCandidateDO candidate : candidates) {
            SettlementReviewCandidateDO relation = relationByCandidate.get(candidate.getId());
            if (relation == null || !SettlementCandidateStatus.REVIEW_LOCKED.name()
                    .equals(candidate.getCandidateStatus())
                    || candidate.getSettlementBatchNo() != null
                    || !Objects.equals(candidate.getReviewOrderNo(), order.getReviewOrderNo())
                    || !Objects.equals(candidate.getVersion(), relation.getLockedCandidateVersion())) {
                throw new IllegalStateException("settlement review no longer exclusively owns all candidates");
            }
        }
        if (candidates.size() != relations.size()) {
            throw new IllegalStateException("settlement review candidate set is incomplete");
        }
        return candidates;
    }

    /** 创建待复核主表快照，并单独统计可生成真实交易投影的 CLEARING_REVISION 候选数。 */
    private SettlementReviewOrderDO newOrder(SettlementReviewCreateCommand command,
                                             String createMode,
                                             String reviewOrderNo,
                                             MerchantSettlementProfileDO profile,
                                             List<SettlementCandidateDO> candidates,
                                             LocalDateTime now) {
        SettlementOperatorSnapshot submitter = command.submitter();
        SettlementReviewOrderDO row = new SettlementReviewOrderDO();
        row.setReviewOrderNo(reviewOrderNo);
        row.setCreateRequestKey(command.requestKey());
        row.setSelectionFingerprint(fingerprintService.selection(command.candidates()));
        row.setReviewType(command.reviewType().name());
        row.setCreateMode(createMode);
        row.setMerchantId(profile.getMerchantId());
        row.setSettlementProfileId(profile.getId());
        row.setSettlementAccountId(profile.getSettlementAccountId());
        row.setTargetCurrency(profile.getTargetCurrency());
        row.setTargetCurrencyExponent(profile.getTargetCurrencyExponent());
        row.setBusinessDate(command.businessDate());
        row.setBusinessTimeZone(profile.getBusinessTimeZone());
        row.setCutoffBeginTime(command.cutoffBeginTime());
        row.setCutoffEndTime(command.cutoffEndTime());
        row.setCandidateCount(candidates.size());
        row.setProjectableCandidateCount((int) candidates.stream()
                .filter(candidate -> "CLEARING_REVISION".equals(candidate.getSourceType())).count());
        row.setReviewStatus(SettlementReviewStatus.PENDING_APPROVAL.name());
        row.setCreatedByAccountId(submitter.accountId());
        row.setCreatedByAccountName(submitter.accountName());
        row.setSubmittedByAccountId(submitter.accountId());
        row.setSubmittedByAccountName(submitter.accountName());
        row.setSubmittedRoleSnapshot(submitter.roleSnapshot());
        row.setSubmitClientIp(submitter.clientIp());
        row.setSubmitUserAgent(submitter.userAgent());
        row.setSubmitReason(command.reason());
        row.setSubmittedTime(submitter.operationTime());
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 将预审冻结维度映射为不落库批次对象，供同一结算计算内核预览。 */
    private SettlementBatchDO previewBatch(SettlementReviewOrderDO order) {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setSettlementBatchNo(order.getReviewOrderNo());
        row.setMerchantId(order.getMerchantId());
        row.setSettlementProfileId(order.getSettlementProfileId());
        row.setSettlementAccountId(order.getSettlementAccountId());
        row.setTargetCurrency(order.getTargetCurrency());
        row.setTargetCurrencyExponent(order.getTargetCurrencyExponent());
        row.setBatchType(order.getReviewType());
        row.setBusinessDate(order.getBusinessDate());
        row.setCandidateCount(order.getCandidateCount());
        return row;
    }

    /** 按来源币种稳定排序，将归一汇率和可信锁定主体冻结为预审汇率行。 */
    private List<SettlementReviewRateDO> reviewRates(String orderNo,
                                                    RateMatrix matrix,
                                                    SettlementOperatorSnapshot operator,
                                                    LocalDateTime now) {
        return matrix.rates().stream().sorted(Comparator.comparing(rate -> rate.pair().sourceCurrency()))
                .map(rate -> {
                    SettlementReviewRateDO row = new SettlementReviewRateDO();
                    row.setReviewOrderNo(orderNo);
                    row.setSourceCurrency(rate.pair().sourceCurrency());
                    row.setTargetCurrency(rate.pair().targetCurrency());
                    row.setDirectRate(rate.directRate());
                    row.setSourceCurrencyExponent(rate.sourceCurrencyExponent());
                    row.setTargetCurrencyExponent(rate.targetCurrencyExponent());
                    row.setRateSource(rate.rateSource());
                    row.setQuoteId(rate.quoteId());
                    row.setSourceQuoteDirection(rate.sourceQuoteDirection().name());
                    row.setEffectiveTime(rate.effectiveTime());
                    row.setLockedTime(now);
                    row.setLockedBy(operator.accountId() == 0L
                            ? "service-settlement:auto-review"
                            : "admin-account:" + operator.accountId());
                    row.setCreateTime(now);
                    return row;
                }).toList();
    }

    /** 将数据库预审汇率行还原为不可变矩阵；审批阶段强制要求持久化汇率行 ID。 */
    private SettlementLockedRateMatrix toLockedRates(List<SettlementReviewRateDO> rows,
                                                     boolean requirePersistentIds) {
        if (rows.isEmpty()) {
            throw new IllegalStateException("settlement review rate matrix is empty");
        }
        List<LockedRate> rates = new ArrayList<>(rows.size());
        Map<String, Long> ids = new LinkedHashMap<>();
        long transientId = 1L;
        for (SettlementReviewRateDO row : rows.stream()
                .sorted(Comparator.comparing(SettlementReviewRateDO::getSourceCurrency)).toList()) {
            Long id = row.getId();
            if (requirePersistentIds && (id == null || id <= 0)) {
                throw new IllegalStateException("settlement review rate row identity is missing");
            }
            rates.add(new LockedRate(new CurrencyPair(row.getSourceCurrency(), row.getTargetCurrency()),
                    row.getDirectRate(), row.getSourceCurrencyExponent(), row.getTargetCurrencyExponent(),
                    row.getRateSource(), row.getQuoteId(),
                    QuoteDirection.valueOf(row.getSourceQuoteDirection()), row.getEffectiveTime()));
            ids.put(row.getSourceCurrency(), id == null ? transientId++ : id);
        }
        return new SettlementLockedRateMatrix(RateMatrix.of(rates), ids);
    }

    /** 为每个锁定候选保存业务身份、锁后版本和精确清分来源指纹。 */
    private List<SettlementReviewCandidateDO> reviewCandidates(String orderNo,
                                                              List<SettlementCandidateDO> candidates,
                                                              SettlementBatchFacts facts,
                                                              LocalDateTime now) {
        List<SettlementReviewCandidateDO> rows = new ArrayList<>(candidates.size());
        for (SettlementCandidateDO candidate : candidates) {
            SettlementReviewCandidateDO row = new SettlementReviewCandidateDO();
            row.setReviewCandidateNo(stableId("RC", orderNo, candidate.getId()));
            row.setReviewOrderNo(orderNo);
            row.setCandidateId(candidate.getId());
            row.setCandidateNo(candidate.getCandidateNo());
            row.setSourceType(candidate.getSourceType());
            row.setSourceBusinessId(candidate.getSourceBusinessId());
            row.setSourceRevision(candidate.getSourceRevision());
            row.setSourceTransactionId(candidate.getSourceTransactionId());
            row.setSourceTransactionDateTime(candidate.getSourceTransactionDateTime());
            row.setLockedCandidateVersion(candidate.getVersion());
            row.setClearingFingerprint(fingerprintService.candidateSource(facts, candidate));
            row.setRelationStatus("LOCKED");
            row.setLockedTime(now);
            row.setVersion(0L);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            rows.add(row);
        }
        return rows;
    }

    /** 复制预览汇总为审批展示快照，保持支付、结果、方向及币种分组维度不变。 */
    private List<SettlementReviewSummaryDO> reviewSummaries(
            String orderNo, List<SettlementResultSummaryDO> summaries, LocalDateTime now) {
        return summaries.stream().map(summary -> {
            SettlementReviewSummaryDO row = new SettlementReviewSummaryDO();
            row.setReviewOrderNo(orderNo);
            row.setMerchantId(summary.getMerchantId());
            row.setPaymentType(summary.getPaymentType());
            row.setPaymentMethod(summary.getPaymentMethod());
            row.setTransactionType(summary.getTransactionType());
            row.setResultItemType(summary.getResultItemType());
            row.setFeeCategory(summary.getFeeCategory());
            row.setDirection(summary.getDirection());
            row.setSourceCurrency(summary.getSourceCurrency());
            row.setTargetCurrency(summary.getTargetCurrency());
            row.setTransactionCount(summary.getTransactionCount());
            row.setSourceAmount(summary.getSourceAmount());
            row.setTargetAmount(summary.getTargetAmount());
            row.setCreateTime(now);
            return row;
        }).toList();
    }

    /** 将 Maker、Checker、意见和预审结果指纹冻结到批准后的正式批次。 */
    private void bindReviewAudit(SettlementBatchDO batch,
                                 SettlementReviewOrderDO order,
                                 SettlementReviewDecisionCommand command,
                                 LocalDateTime now) {
        SettlementOperatorSnapshot checker = command.operator();
        batch.setReviewOrderNo(order.getReviewOrderNo());
        batch.setCreateMode("MANUAL_REVIEW");
        batch.setCandidateCount(order.getCandidateCount());
        batch.setProjectableCandidateCount(order.getProjectableCandidateCount());
        batch.setResultFingerprint(order.getResultFingerprint());
        batch.setMakerAccountId(order.getSubmittedByAccountId());
        batch.setMakerAccountName(order.getSubmittedByAccountName());
        batch.setMakerRoleSnapshot(order.getSubmittedRoleSnapshot());
        batch.setMakerClientIp(order.getSubmitClientIp());
        batch.setMakerUserAgent(order.getSubmitUserAgent());
        batch.setMakerReason(order.getSubmitReason());
        batch.setMakerTime(order.getSubmittedTime());
        batch.setCheckerAccountId(checker.accountId());
        batch.setCheckerAccountName(checker.accountName());
        batch.setCheckerRoleSnapshot(checker.roleSnapshot());
        batch.setCheckerClientIp(checker.clientIp());
        batch.setCheckerUserAgent(checker.userAgent());
        batch.setCheckerComment(command.comment());
        batch.setCheckerTime(checker.operationTime());
    }

    /** 将预审冻结汇率复制为正式批次 LOCKED 汇率，保留 reviewRateId 审计关联。 */
    private List<SettlementBatchRateDO> batchRates(String batchNo,
                                                  List<SettlementReviewRateDO> rates,
                                                  LocalDateTime now) {
        return rates.stream().map(rate -> {
            SettlementBatchRateDO row = new SettlementBatchRateDO();
            row.setSettlementBatchNo(batchNo);
            row.setReviewRateId(rate.getId());
            row.setSourceCurrency(rate.getSourceCurrency());
            row.setTargetCurrency(rate.getTargetCurrency());
            row.setRateType("SETTLEMENT");
            row.setDirectRate(rate.getDirectRate());
            row.setSourceCurrencyExponent(rate.getSourceCurrencyExponent());
            row.setTargetCurrencyExponent(rate.getTargetCurrencyExponent());
            row.setRateSource(rate.getRateSource());
            row.setQuoteId(rate.getQuoteId());
            row.setSourceQuoteDirection(rate.getSourceQuoteDirection());
            row.setEffectiveTime(rate.getEffectiveTime());
            row.setLockedTime(now);
            row.setLockedBy("settlement-review:" + rate.getReviewOrderNo());
            row.setRateStatus("LOCKED");
            row.setCreateTime(now);
            return row;
        }).toList();
    }

    /** 将已消费预审候选转换为正式批次不可删除关系，保持来源身份不变。 */
    private List<SettlementBatchCandidateDO> batchRelations(
            String batchNo, List<SettlementCandidateDO> candidates, LocalDateTime now) {
        return candidates.stream().map(candidate -> {
            SettlementBatchCandidateDO row = new SettlementBatchCandidateDO();
            row.setBatchCandidateNo(stableId("BC", batchNo, candidate.getId()));
            row.setSettlementBatchNo(batchNo);
            row.setCandidateId(candidate.getId());
            row.setSourceType(candidate.getSourceType());
            row.setSourceBusinessId(candidate.getSourceBusinessId());
            row.setSourceRevision(candidate.getSourceRevision());
            row.setRelationStatus("CLAIMED");
            row.setClaimedTime(now);
            row.setVersion(0L);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            return row;
        }).toList();
    }

    /** 将可信 Checker 决策主体、请求键和客户端环境写入预审终态快照。 */
    private void applyDecision(SettlementReviewOrderDO order,
                               SettlementReviewDecisionCommand command,
                               LocalDateTime now) {
        SettlementOperatorSnapshot operator = command.operator();
        order.setDecidedByAccountId(operator.accountId());
        order.setDecidedByAccountName(operator.accountName());
        order.setDecidedRoleSnapshot(operator.roleSnapshot());
        order.setDecisionClientIp(operator.clientIp());
        order.setDecisionUserAgent(operator.userAgent());
        order.setDecisionAction(command.decision());
        order.setDecisionRequestKey(command.requestKey());
        order.setReviewComment(command.comment());
        order.setDecisionTime(operator.operationTime());
    }

    /** 要求预审仍处于 PENDING_APPROVAL 且 version 等于页面期望值。 */
    private void requirePendingDecision(SettlementReviewOrderDO order,
                                        SettlementReviewDecisionCommand command) {
        if (order == null || !SettlementReviewStatus.PENDING_APPROVAL.name().equals(order.getReviewStatus())
                || order.getVersion() == null || order.getVersion() != command.expectedVersion()) {
            throw new IllegalStateException("settlement review state or expected version is stale");
        }
    }

    /** 强制 Maker 与 Checker 使用不同管理账户，系统自动预审同样不得自审。 */
    private void validateMakerChecker(SettlementReviewOrderDO order,
                                      SettlementReviewDecisionCommand command) {
        long operatorId = command.operator().accountId();
        if ("CANCEL".equals(command.decision())) {
            if (!Objects.equals(order.getSubmittedByAccountId(), operatorId)) {
                throw new IllegalStateException("only the settlement review maker may cancel it");
            }
        } else if (Objects.equals(order.getSubmittedByAccountId(), operatorId)) {
            throw new IllegalStateException("settlement review maker and checker accounts must differ");
        }
    }

    /** 审批时重新核对档案、商户、目标币种和 NORMAL 资金账户仍然有效。 */
    private void validateProfile(SettlementReviewOrderDO order, MerchantSettlementProfileDO profile) {
        if (profile == null || !Objects.equals(profile.getId(), order.getSettlementProfileId())
                || !Objects.equals(profile.getMerchantId(), order.getMerchantId())
                || !Objects.equals(profile.getSettlementAccountId(), order.getSettlementAccountId())
                || !Objects.equals(profile.getTargetCurrency(), order.getTargetCurrency())
                || !Objects.equals(profile.getTargetCurrencyExponent(), order.getTargetCurrencyExponent())) {
            throw new IllegalStateException("settlement review profile or NORMAL account is unavailable");
        }
    }

    /** 校验预审类型与候选来源严格对应，避免 REGULAR/保证金候选混批。 */
    private boolean sourceMatches(String reviewType, String sourceType) {
        return switch (reviewType) {
            case "REGULAR" -> "CLEARING_REVISION".equals(sourceType);
            case "RESERVE_RELEASE" -> "RESERVE_RELEASE".equals(sourceType);
            case "ADJUSTMENT" -> "ADJUSTMENT".equals(sourceType);
            default -> false;
        };
    }

    /** 要求日序列行包含当前值和 CAS 版本。 */
    private void requireSequence(SettlementReviewDailySequenceDO sequence) {
        if (sequence == null || sequence.getCurrentSequence() == null || sequence.getVersion() == null
                || sequence.getCurrentSequence() < 0 || sequence.getCurrentSequence() > MAX_DAILY_SEQUENCE) {
            throw new IllegalStateException("settlement review daily sequence could not be locked");
        }
    }

    /** 对创建请求键重放核对选择指纹、模式、日期和 Maker，拒绝幂等键碰撞。 */
    private void verifyCreateIdentity(SettlementReviewOrderDO actual,
                                      SettlementReviewCreateCommand command,
                                      String createMode) {
        if (!Objects.equals(actual.getCreateRequestKey(), command.requestKey())
                || !Objects.equals(actual.getCreateMode(), createMode)
                || !Objects.equals(actual.getSelectionFingerprint(),
                fingerprintService.selection(command.candidates()))
                || !Objects.equals(actual.getReviewType(), command.reviewType().name())
                || !Objects.equals(actual.getBusinessDate(), command.businessDate())
                || !Objects.equals(actual.getCutoffBeginTime(), command.cutoffBeginTime())
                || !Objects.equals(actual.getCutoffEndTime(), command.cutoffEndTime())
                || !Objects.equals(actual.getSubmittedByAccountId(), command.submitter().accountId())) {
            throw new IllegalStateException("settlement review request key has mismatched immutable identity");
        }
    }

    /** 新增后回读并核对关键不可变指纹和金额，防止唯一冲突被误当成合法重放。 */
    private void verifyStoredOrder(SettlementReviewOrderDO actual, SettlementReviewOrderDO expected) {
        if (actual == null || !Objects.equals(actual.getReviewOrderNo(), expected.getReviewOrderNo())
                || !Objects.equals(actual.getSelectionFingerprint(), expected.getSelectionFingerprint())
                || !Objects.equals(actual.getSourceFingerprint(), expected.getSourceFingerprint())
                || !Objects.equals(actual.getRateFingerprint(), expected.getRateFingerprint())
                || !Objects.equals(actual.getResultFingerprint(), expected.getResultFingerprint())
                || !Objects.equals(actual.getReviewStatus(), expected.getReviewStatus())) {
            throw new IllegalStateException("settlement review immutable snapshot is inconsistent");
        }
    }

    /** 对决策请求键重放核对预审单号、动作、Checker 和意见，拒绝跨命令复用。 */
    private void verifyDecisionReplay(SettlementReviewOrderDO actual,
                                      String reviewOrderNo,
                                      SettlementReviewDecisionCommand command) {
        if (!Objects.equals(actual.getDecisionRequestKey(), command.requestKey())
                || !Objects.equals(actual.getReviewOrderNo(), reviewOrderNo)
                || !Objects.equals(actual.getDecisionAction(), command.decision())
                || !Objects.equals(actual.getDecidedByAccountId(), command.operator().accountId())
                || !Objects.equals(actual.getReviewComment(), command.comment())
                || actual.getVersion() == null
                || actual.getVersion() != command.expectedVersion() + 1) {
            throw new IllegalStateException("settlement review decision key has mismatched immutable identity");
        }
    }

    /** 映射预审主表为稳定命令结果，不暴露角色、IP 或 User-Agent 审计字段。 */
    private SettlementReviewCommandResult result(SettlementReviewOrderDO row) {
        return new SettlementReviewCommandResult(row.getReviewOrderNo(), row.getReviewStatus(),
                row.getSettlementBatchNo(), row.getCandidateCount(), row.getTargetCurrency(),
                row.getTargetCurrencyExponent(), row.getNetDirection(), row.getNetAmount(), row.getVersion());
    }

    /** 由预审单和候选主键生成稳定关系号，保证事务重放结果一致。 */
    private String stableId(String prefix, String ownerNo, Long candidateId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((ownerNo + "|" + candidateId).getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : List.copyOf(rows);
    }
}
