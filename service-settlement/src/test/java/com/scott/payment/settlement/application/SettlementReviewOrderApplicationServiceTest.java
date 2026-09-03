package com.scott.payment.settlement.application;

import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import com.scott.payment.settlement.domain.model.SettlementBatchType;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementCalculationPreview;
import com.scott.payment.settlement.dto.SettlementCurrency;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.dto.SettlementReviewCommandResult;
import com.scott.payment.settlement.dto.SettlementReviewCreateCommand;
import com.scott.payment.settlement.dto.SettlementReviewDecisionCommand;
import com.scott.payment.settlement.entity.MerchantSettlementProfileDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementReviewCandidateDO;
import com.scott.payment.settlement.entity.SettlementReviewDailySequenceDO;
import com.scott.payment.settlement.entity.SettlementReviewOrderDO;
import com.scott.payment.settlement.entity.SettlementReviewRateDO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewOrderApplicationServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证结算预审锁定、幂等和 Maker-Checker 终态状态机。
 * @status : create
 */
class SettlementReviewOrderApplicationServiceTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 31);
    private static final LocalDateTime PROCESSING_TIME = LocalDateTime.of(2026, 8, 31, 10, 0);
    private static final String REVIEW_ORDER_NO = "SO20260831-00000001";

    private SettlementReviewDailySequenceMapper sequenceMapper;
    private SettlementReviewOrderMapper orderMapper;
    private SettlementReviewCandidateMapper reviewCandidateMapper;
    private SettlementReviewRateMapper reviewRateMapper;
    private SettlementReviewSummaryMapper reviewSummaryMapper;
    private SettlementCandidateMapper candidateMapper;
    private MerchantSettlementProfileMapper profileMapper;
    private SettlementBatchCreationService batchCreationService;
    private SettlementBatchMapper batchMapper;
    private SettlementBatchCandidateMapper batchCandidateMapper;
    private SettlementBatchRateMapper batchRateMapper;
    private SettlementClearingFactService factService;
    private SettlementRateResolutionService rateResolutionService;
    private SettlementResultCalculationService calculationService;
    private SettlementReviewFingerprintService fingerprintService;
    private SettlementReviewOrderApplicationService service;

    @BeforeEach
    void setUp() {
        sequenceMapper = mock(SettlementReviewDailySequenceMapper.class);
        orderMapper = mock(SettlementReviewOrderMapper.class);
        reviewCandidateMapper = mock(SettlementReviewCandidateMapper.class);
        reviewRateMapper = mock(SettlementReviewRateMapper.class);
        reviewSummaryMapper = mock(SettlementReviewSummaryMapper.class);
        candidateMapper = mock(SettlementCandidateMapper.class);
        profileMapper = mock(MerchantSettlementProfileMapper.class);
        batchCreationService = mock(SettlementBatchCreationService.class);
        batchMapper = mock(SettlementBatchMapper.class);
        batchCandidateMapper = mock(SettlementBatchCandidateMapper.class);
        batchRateMapper = mock(SettlementBatchRateMapper.class);
        factService = mock(SettlementClearingFactService.class);
        rateResolutionService = mock(SettlementRateResolutionService.class);
        calculationService = mock(SettlementResultCalculationService.class);
        fingerprintService = mock(SettlementReviewFingerprintService.class);
        Clock clock = Clock.fixed(PROCESSING_TIME.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new SettlementReviewOrderApplicationService(
                sequenceMapper, orderMapper, reviewCandidateMapper, reviewRateMapper,
                reviewSummaryMapper, candidateMapper, profileMapper, batchCreationService,
                batchMapper, batchCandidateMapper, batchRateMapper, factService,
                rateResolutionService, calculationService, fingerprintService,
                new SettlementReviewNumberFormatter(), clock);
    }

    /** 提交时必须冻结可信 Admin 操作时间，不能用 settlement 处理时钟覆盖审计快照。 */
    @Test
    void shouldSubmitOnceAndPreserveTrustedOperatorTime() {
        SettlementReviewCreateCommand command = createCommand("CREATE-1", 1L);
        SettlementCandidateDO candidate = readyCandidate(1L);
        MerchantSettlementProfileDO profile = profile();
        SettlementBatchFacts facts = facts(candidate);
        SettlementCalculationPreview preview = preview();
        AtomicReference<SettlementReviewOrderDO> stored = new AtomicReference<>();

        when(sequenceMapper.selectForUpdate(BUSINESS_DATE)).thenReturn(sequence());
        when(sequenceMapper.increment(BUSINESS_DATE, 0, 0L)).thenReturn(1);
        when(orderMapper.selectByCreateRequestKeyForUpdate("CREATE-1"))
                .thenAnswer(invocation -> stored.get());
        when(candidateMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(candidate));
        when(profileMapper.selectReviewEligibleProfileForUpdate(11L, BUSINESS_DATE)).thenReturn(profile);
        when(candidateMapper.countUnresolvedReviewDependencies(List.of(1L))).thenReturn(0L);
        when(candidateMapper.lockForReview(anyList(), any(), any(), any())).thenReturn(1);
        when(factService.loadReviewSelection(any(), anyList())).thenReturn(facts);
        when(rateResolutionService.resolve(any(), any(), any(Integer.class), any()))
                .thenReturn(identityRate());
        when(calculationService.preview(any(), any(), any(), any())).thenReturn(preview);
        when(fingerprintService.selection(anyList())).thenReturn("selection-fingerprint");
        when(fingerprintService.source(facts)).thenReturn("source-fingerprint");
        when(fingerprintService.rates(anyList())).thenReturn("rate-fingerprint");
        when(fingerprintService.result(preview)).thenReturn("result-fingerprint");
        when(fingerprintService.candidateSource(facts, candidate)).thenReturn("candidate-fingerprint");
        when(orderMapper.insertIdempotent(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(reviewCandidateMapper.insertBatchIdempotent(anyList())).thenReturn(1);
        when(reviewRateMapper.insertBatchIdempotent(anyList())).thenReturn(1);

        SettlementReviewCommandResult result = service.submit(command);

        assertThat(result.reviewOrderNo()).isEqualTo(REVIEW_ORDER_NO);
        assertThat(result.reviewStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(stored.get().getSubmittedTime()).isEqualTo(command.submitter().operationTime());
        assertThat(stored.get().getCreateTime()).isEqualTo(PROCESSING_TIME);
        assertThat(candidate.getCandidateStatus()).isEqualTo("REVIEW_LOCKED");
        assertThat(candidate.getVersion()).isEqualTo(2L);
    }

    /** 自动预审必须冻结固定系统主体并明确标记 AUTO_REVIEW，不能伪装成人工制单。 */
    @Test
    void shouldSubmitAutomaticReviewWithTrustedSystemIdentity() {
        SettlementReviewCreateCommand command = automaticCreateCommand("AUTO-REVIEW-1", 1L);
        SettlementCandidateDO candidate = readyCandidate(1L);
        SettlementBatchFacts facts = facts(candidate);
        SettlementCalculationPreview preview = preview();
        AtomicReference<SettlementReviewOrderDO> stored = new AtomicReference<>();

        when(sequenceMapper.selectForUpdate(BUSINESS_DATE)).thenReturn(sequence());
        when(sequenceMapper.increment(BUSINESS_DATE, 0, 0L)).thenReturn(1);
        when(orderMapper.selectByCreateRequestKeyForUpdate("AUTO-REVIEW-1"))
                .thenAnswer(invocation -> stored.get());
        when(candidateMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(candidate));
        when(profileMapper.selectReviewEligibleProfileForUpdate(11L, BUSINESS_DATE)).thenReturn(profile());
        when(candidateMapper.countUnresolvedReviewDependencies(List.of(1L))).thenReturn(0L);
        when(candidateMapper.lockForReview(anyList(), any(), any(), any())).thenReturn(1);
        when(factService.loadReviewSelection(any(), anyList())).thenReturn(facts);
        when(rateResolutionService.resolve(any(), any(), any(Integer.class), any()))
                .thenReturn(identityRate());
        when(calculationService.preview(any(), any(), any(), any())).thenReturn(preview);
        when(fingerprintService.selection(anyList())).thenReturn("selection-fingerprint");
        when(fingerprintService.source(facts)).thenReturn("source-fingerprint");
        when(fingerprintService.rates(anyList())).thenReturn("rate-fingerprint");
        when(fingerprintService.result(preview)).thenReturn("result-fingerprint");
        when(fingerprintService.candidateSource(facts, candidate)).thenReturn("candidate-fingerprint");
        when(orderMapper.insertIdempotent(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(reviewCandidateMapper.insertBatchIdempotent(anyList())).thenReturn(1);
        when(reviewRateMapper.insertBatchIdempotent(anyList())).thenReturn(1);

        service.submitAutomatic(command);

        assertThat(stored.get().getCreateMode()).isEqualTo("AUTO_REVIEW");
        assertThat(stored.get().getSubmittedByAccountId()).isZero();
        assertThat(stored.get().getSubmittedByAccountName()).isEqualTo("service-settlement");
        assertThat(stored.get().getSubmittedRoleSnapshot()).isEqualTo("SYSTEM");
    }

    /** 相同请求键不能从人工制单切换为自动预审，防止跨模式幂等重放。 */
    @Test
    void shouldRejectCreateRequestKeyReplayAcrossCreateModes() {
        SettlementReviewCreateCommand command = automaticCreateCommand("CREATE-MODE-MISMATCH", 1L);
        SettlementReviewOrderDO existing = pendingOrder();
        existing.setCreateRequestKey("CREATE-MODE-MISMATCH");
        existing.setCreateMode("MANUAL");
        existing.setSelectionFingerprint("selection-fingerprint");
        existing.setCutoffBeginTime(command.cutoffBeginTime());
        existing.setCutoffEndTime(command.cutoffEndTime());
        existing.setSubmittedByAccountId(0L);
        when(sequenceMapper.selectForUpdate(BUSINESS_DATE)).thenReturn(sequence());
        when(orderMapper.selectByCreateRequestKeyForUpdate("CREATE-MODE-MISMATCH")).thenReturn(existing);
        when(fingerprintService.selection(command.candidates())).thenReturn("selection-fingerprint");

        assertThatThrownBy(() -> service.submitAutomatic(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mismatched immutable identity");

        verify(candidateMapper, never()).selectByIdsForUpdate(anyList());
        verify(orderMapper, never()).insertIdempotent(any());
    }

    /** 账号 0 仅允许固定服务身份，Admin 不能伪造系统制单人。 */
    @Test
    void shouldRejectForgedSystemOperatorSnapshot() {
        assertThatThrownBy(() -> new SettlementOperatorSnapshot(
                0L, "Admin Operator", "[\"SUPER_ADMIN\"]", "127.0.0.1", "Browser", PROCESSING_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator snapshot is invalid");
    }

    /** 相同创建请求键必须直接返回既有预审，不得再次锁候选或分配序号。 */
    @Test
    void shouldReplayIdenticalSubmissionWithoutRelockingCandidates() {
        SettlementReviewCreateCommand command = createCommand("CREATE-REPLAY", 1L);
        SettlementReviewOrderDO existing = pendingOrder();
        existing.setCreateRequestKey("CREATE-REPLAY");
        existing.setSelectionFingerprint("selection-fingerprint");
        existing.setCutoffBeginTime(command.cutoffBeginTime());
        existing.setCutoffEndTime(command.cutoffEndTime());
        when(sequenceMapper.selectForUpdate(BUSINESS_DATE)).thenReturn(sequence());
        when(orderMapper.selectByCreateRequestKeyForUpdate("CREATE-REPLAY")).thenReturn(existing);
        when(fingerprintService.selection(command.candidates())).thenReturn("selection-fingerprint");

        SettlementReviewCommandResult result = service.submit(command);

        assertThat(result.reviewOrderNo()).isEqualTo(REVIEW_ORDER_NO);
        verify(sequenceMapper, never()).increment(any(), any(Integer.class), any(Long.class));
        verify(candidateMapper, never()).selectByIdsForUpdate(anyList());
        verify(candidateMapper, never()).lockForReview(anyList(), any(), any(), any());
    }

    /** 候选 READY 版本锁竞争失败时必须整单失败，不得留下预审快照。 */
    @Test
    void shouldRejectCompetingCandidateReviewLock() {
        SettlementCandidateDO candidate = readyCandidate(1L);
        when(sequenceMapper.selectForUpdate(BUSINESS_DATE)).thenReturn(sequence());
        when(sequenceMapper.increment(BUSINESS_DATE, 0, 0L)).thenReturn(1);
        when(candidateMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(candidate));
        when(profileMapper.selectReviewEligibleProfileForUpdate(11L, BUSINESS_DATE)).thenReturn(profile());
        when(candidateMapper.countUnresolvedReviewDependencies(List.of(1L))).thenReturn(0L);
        when(candidateMapper.lockForReview(anyList(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.submit(createCommand("CREATE-RACE", 1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lock CAS");

        verify(orderMapper, never()).insertIdempotent(any());
        verify(reviewCandidateMapper, never()).insertBatchIdempotent(anyList());
    }

    /** 同一账号即使同时拥有制单和审批权限，也不能审批自己的预审单。 */
    @Test
    void shouldRejectMakerSelfApproval() {
        SettlementReviewOrderDO order = pendingOrder();
        when(orderMapper.selectByReviewOrderNoForUpdate(REVIEW_ORDER_NO)).thenReturn(order);

        SettlementReviewDecisionCommand command = decision(
                "DECIDE-SELF", "APPROVE", 101L, LocalDateTime.of(2026, 8, 31, 9, 40));

        assertThatThrownBy(() -> service.decide(REVIEW_ORDER_NO, command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ");

        verify(reviewCandidateMapper, never()).selectByOrderNoForUpdate(any());
        verify(batchCreationService, never()).create(any());
    }

    /** 过期的 expectedVersion 必须在读取候选前拒绝，避免旧页面覆盖新状态。 */
    @Test
    void shouldRejectStaleDecisionExpectedVersionBeforeLockingCandidates() {
        SettlementReviewOrderDO order = pendingOrder();
        order.setVersion(1L);
        when(orderMapper.selectByReviewOrderNoForUpdate(REVIEW_ORDER_NO)).thenReturn(order);

        assertThatThrownBy(() -> service.decide(REVIEW_ORDER_NO,
                decision("DECIDE-STALE", "APPROVE", 202L,
                        LocalDateTime.of(2026, 8, 31, 9, 42))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected version is stale");

        verify(reviewCandidateMapper, never()).selectByOrderNoForUpdate(any());
        verify(candidateMapper, never()).selectByIdsForUpdate(anyList());
        verify(batchCreationService, never()).create(any());
    }

    /** Maker 可以取消自己的待审批单，取消必须释放全部独占候选。 */
    @Test
    void shouldAllowMakerToCancelAndReleaseAllCandidates() {
        SettlementReviewOrderDO order = pendingOrder();
        SettlementReviewCandidateDO relation = lockedRelation();
        SettlementCandidateDO candidate = reviewLockedCandidate();
        LocalDateTime cancelTime = LocalDateTime.of(2026, 8, 31, 9, 43);
        when(orderMapper.selectByReviewOrderNoForUpdate(REVIEW_ORDER_NO)).thenReturn(order);
        when(reviewCandidateMapper.selectByOrderNoForUpdate(REVIEW_ORDER_NO)).thenReturn(List.of(relation));
        when(candidateMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(candidate));
        when(candidateMapper.releaseReviewLock(List.of(candidate), REVIEW_ORDER_NO, PROCESSING_TIME)).thenReturn(1);
        when(reviewCandidateMapper.markReleased(REVIEW_ORDER_NO, PROCESSING_TIME)).thenReturn(1);
        when(orderMapper.terminate(any(), any(), any(Long.class))).thenReturn(1);

        SettlementReviewCommandResult result = service.decide(REVIEW_ORDER_NO,
                decision("DECIDE-CANCEL", "CANCEL", 101L, cancelTime));

        assertThat(result.reviewStatus()).isEqualTo("CANCELLED");
        ArgumentCaptor<SettlementReviewOrderDO> captor = ArgumentCaptor.forClass(SettlementReviewOrderDO.class);
        verify(orderMapper).terminate(captor.capture(), org.mockito.ArgumentMatchers.eq("CANCELLED"),
                org.mockito.ArgumentMatchers.eq(0L));
        assertThat(captor.getValue().getDecisionTime()).isEqualTo(cancelTime);
        verify(candidateMapper).releaseReviewLock(List.of(candidate), REVIEW_ORDER_NO, PROCESSING_TIME);
        verify(batchCreationService, never()).create(any());
    }

    /** 审批时清分事实指纹变化必须拒绝建批。 */
    @Test
    void shouldRejectApprovalWhenClearingFactFingerprintChanges() {
        SettlementReviewOrderDO order = pendingOrder();
        SettlementReviewCandidateDO relation = lockedRelation();
        SettlementCandidateDO candidate = reviewLockedCandidate();
        SettlementBatchFacts facts = facts(candidate);
        stubApprovalOwnership(order, relation, candidate, facts);
        when(fingerprintService.source(facts)).thenReturn("changed-source-fingerprint");

        assertThatThrownBy(() -> service.decide(REVIEW_ORDER_NO,
                decision("DECIDE-SOURCE-CHANGED", "APPROVE", 202L,
                        LocalDateTime.of(2026, 8, 31, 9, 46))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("clearing facts changed");

        verify(reviewRateMapper, never()).selectByOrderNo(any());
        verify(batchCreationService, never()).create(any());
    }

    /** 审批时冻结汇率矩阵指纹变化必须拒绝建批。 */
    @Test
    void shouldRejectApprovalWhenLockedRateFingerprintChanges() {
        SettlementReviewOrderDO order = pendingOrder();
        SettlementReviewCandidateDO relation = lockedRelation();
        SettlementCandidateDO candidate = reviewLockedCandidate();
        SettlementBatchFacts facts = facts(candidate);
        SettlementReviewRateDO reviewRate = persistedReviewRate();
        stubApprovalOwnership(order, relation, candidate, facts);
        when(fingerprintService.source(facts)).thenReturn("source-fingerprint");
        when(reviewRateMapper.selectByOrderNo(REVIEW_ORDER_NO)).thenReturn(List.of(reviewRate));
        when(fingerprintService.rates(List.of(reviewRate))).thenReturn("changed-rate-fingerprint");

        assertThatThrownBy(() -> service.decide(REVIEW_ORDER_NO,
                decision("DECIDE-RATE-CHANGED", "APPROVE", 202L,
                        LocalDateTime.of(2026, 8, 31, 9, 47))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked rate matrix changed");

        verify(calculationService, never()).preview(any(), any(), any(), any());
        verify(batchCreationService, never()).create(any());
    }

    /** 审批重算结果指纹变化必须拒绝建批，不能静默使用新金额。 */
    @Test
    void shouldRejectApprovalWhenFinancialResultFingerprintChanges() {
        SettlementReviewOrderDO order = pendingOrder();
        SettlementReviewCandidateDO relation = lockedRelation();
        SettlementCandidateDO candidate = reviewLockedCandidate();
        SettlementBatchFacts facts = facts(candidate);
        SettlementReviewRateDO reviewRate = persistedReviewRate();
        SettlementCalculationPreview preview = preview();
        stubApprovalOwnership(order, relation, candidate, facts);
        when(fingerprintService.source(facts)).thenReturn("source-fingerprint");
        when(reviewRateMapper.selectByOrderNo(REVIEW_ORDER_NO)).thenReturn(List.of(reviewRate));
        when(fingerprintService.rates(List.of(reviewRate))).thenReturn("rate-fingerprint");
        when(calculationService.preview(any(), any(), any(), any())).thenReturn(preview);
        when(fingerprintService.result(preview)).thenReturn("changed-result-fingerprint");

        assertThatThrownBy(() -> service.decide(REVIEW_ORDER_NO,
                decision("DECIDE-RESULT-CHANGED", "APPROVE", 202L,
                        LocalDateTime.of(2026, 8, 31, 9, 48))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("financial result changed");

        verify(batchCreationService, never()).create(any());
        verify(candidateMapper, never()).consumeReviewLock(anyList(), any(), any(), any());
    }

    /** 拒绝必须释放全部独占候选，并保存 Checker 的可信操作时间。 */
    @Test
    void shouldRejectAndReleaseAllCandidates() {
        SettlementReviewOrderDO order = pendingOrder();
        SettlementReviewCandidateDO relation = lockedRelation();
        SettlementCandidateDO candidate = reviewLockedCandidate();
        LocalDateTime checkerTime = LocalDateTime.of(2026, 8, 31, 9, 45);
        when(orderMapper.selectByReviewOrderNoForUpdate(REVIEW_ORDER_NO)).thenReturn(order);
        when(reviewCandidateMapper.selectByOrderNoForUpdate(REVIEW_ORDER_NO)).thenReturn(List.of(relation));
        when(candidateMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(candidate));
        when(candidateMapper.releaseReviewLock(List.of(candidate), REVIEW_ORDER_NO, PROCESSING_TIME)).thenReturn(1);
        when(reviewCandidateMapper.markReleased(REVIEW_ORDER_NO, PROCESSING_TIME)).thenReturn(1);
        when(orderMapper.terminate(any(), any(), any(Long.class))).thenReturn(1);

        SettlementReviewCommandResult result = service.decide(REVIEW_ORDER_NO,
                decision("DECIDE-REJECT", "REJECT", 202L, checkerTime));

        assertThat(result.reviewStatus()).isEqualTo("REJECTED");
        ArgumentCaptor<SettlementReviewOrderDO> captor = ArgumentCaptor.forClass(SettlementReviewOrderDO.class);
        verify(orderMapper).terminate(captor.capture(), org.mockito.ArgumentMatchers.eq("REJECTED"),
                org.mockito.ArgumentMatchers.eq(0L));
        assertThat(captor.getValue().getDecisionTime()).isEqualTo(checkerTime);
        verify(candidateMapper).releaseReviewLock(List.of(candidate), REVIEW_ORDER_NO, PROCESSING_TIME);
    }

    /** 审批通过只创建一个正式批次，并冻结 Maker-Checker 和继承汇率身份。 */
    @Test
    void shouldApproveOnceAndBindTrustedMakerCheckerAudit() {
        SettlementReviewOrderDO order = pendingOrder();
        SettlementReviewCandidateDO relation = lockedRelation();
        SettlementCandidateDO candidate = reviewLockedCandidate();
        SettlementBatchFacts facts = facts(candidate);
        SettlementCalculationPreview preview = preview();
        SettlementReviewRateDO reviewRate = persistedReviewRate();
        SettlementBatchDO batch = createdBatch();
        LocalDateTime checkerTime = LocalDateTime.of(2026, 8, 31, 9, 50);

        when(orderMapper.selectByReviewOrderNoForUpdate(REVIEW_ORDER_NO)).thenReturn(order);
        when(reviewCandidateMapper.selectByOrderNoForUpdate(REVIEW_ORDER_NO)).thenReturn(List.of(relation));
        when(candidateMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(candidate));
        when(profileMapper.selectReviewEligibleProfileForUpdate(11L, BUSINESS_DATE)).thenReturn(profile());
        when(factService.loadReview(order)).thenReturn(facts);
        when(fingerprintService.source(facts)).thenReturn("source-fingerprint");
        when(reviewRateMapper.selectByOrderNo(REVIEW_ORDER_NO)).thenReturn(List.of(reviewRate));
        when(fingerprintService.rates(List.of(reviewRate))).thenReturn("rate-fingerprint");
        when(calculationService.preview(any(), any(), any(), any())).thenReturn(preview);
        when(fingerprintService.result(preview)).thenReturn("result-fingerprint");
        when(batchCreationService.create(any())).thenReturn(new SettlementBatchCreateResult(
                31L, batch.getSettlementBatchNo(), "2026-08-31 00000002", false));
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(batchMapper.bindApprovedReview(any(), any(Long.class))).thenReturn(1);
        when(batchRateMapper.insertBatchIdempotent(anyList())).thenReturn(1);
        when(batchRateMapper.selectByBatchNo(batch.getSettlementBatchNo()))
                .thenReturn(List.of(new com.scott.payment.settlement.entity.SettlementBatchRateDO()));
        when(candidateMapper.consumeReviewLock(List.of(candidate), REVIEW_ORDER_NO,
                batch.getSettlementBatchNo(), PROCESSING_TIME)).thenReturn(1);
        when(batchCandidateMapper.insertBatchIdempotent(anyList())).thenReturn(1);
        when(reviewCandidateMapper.markConsumed(REVIEW_ORDER_NO, PROCESSING_TIME)).thenReturn(1);
        when(orderMapper.approve(any(), any(Long.class))).thenReturn(1);

        SettlementReviewCommandResult result = service.decide(REVIEW_ORDER_NO,
                decision("DECIDE-APPROVE", "APPROVE", 202L, checkerTime));

        assertThat(result.reviewStatus()).isEqualTo("APPROVED");
        assertThat(result.settlementBatchNo()).isEqualTo(batch.getSettlementBatchNo());
        ArgumentCaptor<SettlementBatchDO> batchCaptor = ArgumentCaptor.forClass(SettlementBatchDO.class);
        verify(batchMapper).bindApprovedReview(batchCaptor.capture(), org.mockito.ArgumentMatchers.eq(0L));
        SettlementBatchDO approved = batchCaptor.getValue();
        assertThat(approved.getMakerAccountId()).isEqualTo(101L);
        assertThat(approved.getMakerTime()).isEqualTo(order.getSubmittedTime());
        assertThat(approved.getCheckerAccountId()).isEqualTo(202L);
        assertThat(approved.getCheckerTime()).isEqualTo(checkerTime);
        verify(batchCreationService).create(any());
    }

    /** 相同终态请求键重放必须返回原结果，不得再次创建批次。 */
    @Test
    void shouldReplayApprovalDecisionWithoutCreatingAnotherBatch() {
        SettlementReviewOrderDO approved = pendingOrder();
        approved.setReviewStatus("APPROVED");
        approved.setDecisionAction("APPROVE");
        approved.setDecisionRequestKey("DECIDE-REPLAY");
        approved.setDecidedByAccountId(202L);
        approved.setReviewComment("approve after financial review");
        approved.setSettlementBatchNo("SB20260831-00000002");
        approved.setVersion(1L);
        when(orderMapper.selectByDecisionRequestKeyForUpdate("DECIDE-REPLAY")).thenReturn(approved);

        SettlementReviewCommandResult result = service.decide(REVIEW_ORDER_NO,
                decision("DECIDE-REPLAY", "APPROVE", 202L,
                        LocalDateTime.of(2026, 8, 31, 9, 50)));

        assertThat(result.settlementBatchNo()).isEqualTo("SB20260831-00000002");
        verify(orderMapper, never()).selectByReviewOrderNoForUpdate(any());
        verify(batchCreationService, never()).create(any());
    }

    /** 决策请求键不能被相同操作人复用于不同审批意见。 */
    @Test
    void shouldRejectDecisionReplayWithDifferentComment() {
        SettlementReviewOrderDO approved = pendingOrder();
        approved.setReviewStatus("APPROVED");
        approved.setDecisionAction("APPROVE");
        approved.setDecisionRequestKey("DECIDE-COMMENT-MISMATCH");
        approved.setDecidedByAccountId(202L);
        approved.setReviewComment("original approval comment");
        approved.setSettlementBatchNo("SB20260831-00000002");
        approved.setVersion(1L);
        when(orderMapper.selectByDecisionRequestKeyForUpdate("DECIDE-COMMENT-MISMATCH"))
                .thenReturn(approved);

        SettlementReviewDecisionCommand replay = new SettlementReviewDecisionCommand(
                "DECIDE-COMMENT-MISMATCH", 0L, "APPROVE", "changed approval comment",
                operator(202L, LocalDateTime.of(2026, 8, 31, 9, 55)));

        assertThatThrownBy(() -> service.decide(REVIEW_ORDER_NO, replay))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mismatched immutable identity");

        verify(orderMapper, never()).selectByReviewOrderNoForUpdate(any());
        verify(batchCreationService, never()).create(any());
    }

    /** 决策重放必须携带原请求的预期版本，不能使用终态版本复用请求键。 */
    @Test
    void shouldRejectDecisionReplayWithDifferentExpectedVersion() {
        SettlementReviewOrderDO approved = pendingOrder();
        approved.setReviewStatus("APPROVED");
        approved.setDecisionAction("APPROVE");
        approved.setDecisionRequestKey("DECIDE-VERSION-MISMATCH");
        approved.setDecidedByAccountId(202L);
        approved.setReviewComment("approve after financial review");
        approved.setSettlementBatchNo("SB20260831-00000002");
        approved.setVersion(1L);
        when(orderMapper.selectByDecisionRequestKeyForUpdate("DECIDE-VERSION-MISMATCH"))
                .thenReturn(approved);

        SettlementReviewDecisionCommand replay = new SettlementReviewDecisionCommand(
                "DECIDE-VERSION-MISMATCH", 1L, "APPROVE", "approve after financial review",
                operator(202L, LocalDateTime.of(2026, 8, 31, 9, 55)));

        assertThatThrownBy(() -> service.decide(REVIEW_ORDER_NO, replay))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mismatched immutable identity");

        verify(orderMapper, never()).selectByReviewOrderNoForUpdate(any());
        verify(batchCreationService, never()).create(any());
    }

    private SettlementReviewCreateCommand createCommand(String requestKey, long expectedVersion) {
        return new SettlementReviewCreateCommand(requestKey, SettlementBatchType.REGULAR, BUSINESS_DATE,
                LocalDateTime.of(2026, 8, 30, 0, 0), LocalDateTime.of(2026, 8, 31, 0, 0),
                List.of(new SettlementReviewCreateCommand.CandidateReference(1L, expectedVersion)),
                "manual settlement requested", operator(101L, LocalDateTime.of(2026, 8, 31, 9, 15)));
    }

    private SettlementReviewCreateCommand automaticCreateCommand(String requestKey, long expectedVersion) {
        return new SettlementReviewCreateCommand(requestKey, SettlementBatchType.REGULAR, BUSINESS_DATE,
                LocalDateTime.of(2026, 8, 30, 0, 0), LocalDateTime.of(2026, 8, 31, 0, 0),
                List.of(new SettlementReviewCreateCommand.CandidateReference(1L, expectedVersion)),
                "automatic review generated by settlement profile",
                new SettlementOperatorSnapshot(0L, "service-settlement", "SYSTEM", "127.0.0.1",
                        "service-settlement-scheduler", LocalDateTime.of(2026, 8, 31, 9, 15)));
    }

    private SettlementReviewDecisionCommand decision(String requestKey,
                                                       String action,
                                                       long accountId,
                                                       LocalDateTime operationTime) {
        return new SettlementReviewDecisionCommand(requestKey, 0L, action,
                action.toLowerCase() + " after financial review", operator(accountId, operationTime));
    }

    private void stubApprovalOwnership(SettlementReviewOrderDO order,
                                       SettlementReviewCandidateDO relation,
                                       SettlementCandidateDO candidate,
                                       SettlementBatchFacts facts) {
        when(orderMapper.selectByReviewOrderNoForUpdate(REVIEW_ORDER_NO)).thenReturn(order);
        when(reviewCandidateMapper.selectByOrderNoForUpdate(REVIEW_ORDER_NO)).thenReturn(List.of(relation));
        when(candidateMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(candidate));
        when(profileMapper.selectReviewEligibleProfileForUpdate(11L, BUSINESS_DATE)).thenReturn(profile());
        when(factService.loadReview(order)).thenReturn(facts);
    }

    private SettlementOperatorSnapshot operator(long accountId, LocalDateTime operationTime) {
        return new SettlementOperatorSnapshot(accountId, "Operator " + accountId,
                "[\"SUPER_ADMIN\"]", "127.0.0.1", "JUnit", operationTime);
    }

    private SettlementReviewDailySequenceDO sequence() {
        SettlementReviewDailySequenceDO row = new SettlementReviewDailySequenceDO();
        row.setBusinessDate(BUSINESS_DATE);
        row.setCurrentSequence(0);
        row.setVersion(0L);
        return row;
    }

    private SettlementCandidateDO readyCandidate(long version) {
        SettlementCandidateDO row = baseCandidate(version);
        row.setCandidateStatus("READY");
        return row;
    }

    private SettlementCandidateDO reviewLockedCandidate() {
        SettlementCandidateDO row = baseCandidate(2L);
        row.setCandidateStatus("REVIEW_LOCKED");
        row.setReviewOrderNo(REVIEW_ORDER_NO);
        row.setReviewLockedTime(LocalDateTime.of(2026, 8, 31, 9, 15));
        return row;
    }

    private SettlementCandidateDO baseCandidate(long version) {
        SettlementCandidateDO row = new SettlementCandidateDO();
        row.setId(1L);
        row.setCandidateNo("SC-1");
        row.setSourceType("CLEARING_REVISION");
        row.setSourceBusinessId("CLR-1");
        row.setSourceRevision(1);
        row.setSourceTransactionId("TX-1");
        row.setSourceTransactionDateTime(LocalDateTime.of(2026, 8, 30, 8, 0));
        row.setMerchantId("M1001");
        row.setSettlementProfileId(11L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setSettlementEligibleDate(BUSINESS_DATE);
        row.setShadowMode(0);
        row.setVersion(version);
        row.setCreateTime(LocalDateTime.of(2026, 8, 30, 9, 0));
        return row;
    }

    private MerchantSettlementProfileDO profile() {
        MerchantSettlementProfileDO row = new MerchantSettlementProfileDO();
        row.setId(11L);
        row.setMerchantId("M1001");
        row.setSettlementAccountId(21L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setBusinessTimeZone("Asia/Shanghai");
        return row;
    }

    private SettlementBatchFacts facts(SettlementCandidateDO candidate) {
        return new SettlementBatchFacts(List.of(candidate), List.of(), List.of(),
                Set.of(new SettlementCurrency("USD", 2)));
    }

    private RateMatrix identityRate() {
        return RateMatrix.of(List.of(new LockedRate(new CurrencyPair("USD", "USD"),
                new BigDecimal("1.000000000000"), 2, 2, "SYSTEM_IDENTITY", null,
                QuoteDirection.DIRECT, PROCESSING_TIME)));
    }

    private SettlementCalculationPreview preview() {
        return new SettlementCalculationPreview(List.of(), List.of(), "CREDIT", new BigDecimal("10.00"));
    }

    private SettlementReviewOrderDO pendingOrder() {
        SettlementReviewOrderDO row = new SettlementReviewOrderDO();
        row.setReviewOrderNo(REVIEW_ORDER_NO);
        row.setCreateRequestKey("CREATE-1");
        row.setSelectionFingerprint("selection-fingerprint");
        row.setReviewType("REGULAR");
        row.setCreateMode("MANUAL");
        row.setMerchantId("M1001");
        row.setSettlementProfileId(11L);
        row.setSettlementAccountId(21L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setBusinessDate(BUSINESS_DATE);
        row.setBusinessTimeZone("Asia/Shanghai");
        row.setCutoffBeginTime(LocalDateTime.of(2026, 8, 30, 0, 0));
        row.setCutoffEndTime(LocalDateTime.of(2026, 8, 31, 0, 0));
        row.setCandidateCount(1);
        row.setProjectableCandidateCount(1);
        row.setSourceFingerprint("source-fingerprint");
        row.setRateFingerprint("rate-fingerprint");
        row.setResultFingerprint("result-fingerprint");
        row.setNetDirection("CREDIT");
        row.setNetAmount(new BigDecimal("10.00"));
        row.setReviewStatus("PENDING_APPROVAL");
        row.setSubmittedByAccountId(101L);
        row.setSubmittedByAccountName("Operator 101");
        row.setSubmittedRoleSnapshot("[\"SUPER_ADMIN\"]");
        row.setSubmitClientIp("127.0.0.1");
        row.setSubmitUserAgent("JUnit");
        row.setSubmitReason("manual settlement requested");
        row.setSubmittedTime(LocalDateTime.of(2026, 8, 31, 9, 15));
        row.setVersion(0L);
        return row;
    }

    private SettlementReviewCandidateDO lockedRelation() {
        SettlementReviewCandidateDO row = new SettlementReviewCandidateDO();
        row.setReviewOrderNo(REVIEW_ORDER_NO);
        row.setCandidateId(1L);
        row.setLockedCandidateVersion(2L);
        row.setRelationStatus("LOCKED");
        return row;
    }

    private SettlementReviewRateDO persistedReviewRate() {
        SettlementReviewRateDO row = new SettlementReviewRateDO();
        row.setId(91L);
        row.setReviewOrderNo(REVIEW_ORDER_NO);
        row.setSourceCurrency("USD");
        row.setTargetCurrency("USD");
        row.setDirectRate(new BigDecimal("1.000000000000"));
        row.setSourceCurrencyExponent(2);
        row.setTargetCurrencyExponent(2);
        row.setRateSource("SYSTEM_IDENTITY");
        row.setSourceQuoteDirection("DIRECT");
        row.setEffectiveTime(PROCESSING_TIME);
        return row;
    }

    private SettlementBatchDO createdBatch() {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setId(31L);
        row.setSettlementBatchNo("SB20260831-00000002");
        row.setBatchStatus("CREATED");
        row.setCandidateCount(0);
        row.setVersion(0L);
        return row;
    }
}
