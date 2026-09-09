package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.application.SettlementAutomaticPostApplicationService;
import com.scott.payment.settlement.application.SettlementReviewOrderApplicationService;
import com.scott.payment.settlement.domain.model.SettlementBatchType;
import com.scott.payment.settlement.dto.SettlementReviewCommandResult;
import com.scott.payment.settlement.dto.SettlementReviewCreateCommand;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementBatchGroupDO;
import com.scott.payment.settlement.mapper.MerchantSettlementProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementAutomaticBatchServiceTest
 * @date : 2026-08-26 22:10
 * @email : scott_x@163.com
 * @description : 验证自动日批使用商户业务时区和日切生成确定性请求键，并在建批后完成候选认领封批。
 * @status : create
 */
class DefaultSettlementAutomaticBatchServiceTest {

    private MerchantSettlementProfileMapper profileMapper;
    private SettlementAutomaticPostApplicationService automaticPostService;
    private SettlementReviewOrderApplicationService reviewOrderService;
    private DefaultSettlementAutomaticBatchService service;

    @BeforeEach
    void setUp() {
        profileMapper = mock(MerchantSettlementProfileMapper.class);
        automaticPostService = mock(SettlementAutomaticPostApplicationService.class);
        reviewOrderService = mock(SettlementReviewOrderApplicationService.class);
        when(automaticPostService.createAndClaim(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        service = new DefaultSettlementAutomaticBatchService(
                profileMapper, automaticPostService, reviewOrderService);
    }

    /** 上海零点日切在 09:00 已成熟，应创建当天唯一常规批次。 */
    @Test
    void shouldCreateDeterministicMaturedDailyBatchAndClaimCandidates() {
        Instant now = Instant.parse("2026-08-26T01:00:00Z");
        SettlementBatchGroupDO group = group(LocalDate.of(2026, 8, 26));
        when(profileMapper.selectReadyBatchGroups(100)).thenReturn(List.of(group));
        int processed = service.createAndClaimMaturedBatches(now);

        assertThat(processed).isEqualTo(1);
        verify(automaticPostService).createAndClaim(
                group, SettlementBatchType.REGULAR, LocalDate.of(2026, 8, 26),
                LocalDateTime.of(2026, 8, 24, 16, 0),
                LocalDateTime.of(2026, 8, 25, 16, 0),
                LocalDateTime.of(2026, 8, 26, 1, 0));
    }

    /** 候选结算日期晚于当前成熟业务日时不得提前建批。 */
    @Test
    void shouldSkipCandidatesThatAreNotEligibleOnMaturedBusinessDate() {
        Instant now = Instant.parse("2026-08-26T01:00:00Z");
        when(profileMapper.selectReadyBatchGroups(100))
                .thenReturn(List.of(group(LocalDate.of(2026, 8, 27))));

        assertThat(service.createAndClaimMaturedBatches(now)).isZero();
        verify(automaticPostService, never()).createAndClaim(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 保证金释放来源必须进入独立释放批次，不能混入常规交易日批。 */
    @Test
    void shouldCreateReserveReleaseBatchForReleaseGroup() {
        Instant now = Instant.parse("2026-08-26T01:00:00Z");
        SettlementBatchGroupDO group = group(LocalDate.of(2026, 8, 26));
        group.setBatchType("RESERVE_RELEASE");
        when(profileMapper.selectReadyBatchGroups(100)).thenReturn(List.of(group));
        service.createAndClaimMaturedBatches(now);

        verify(automaticPostService).createAndClaim(
                org.mockito.ArgumentMatchers.eq(group),
                org.mockito.ArgumentMatchers.eq(SettlementBatchType.RESERVE_RELEASE),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 已复核保证金调整必须生成独立调整批次且不引用原结算批。 */
    @Test
    void shouldCreateIndependentAdjustmentBatchForAdjustmentGroup() {
        Instant now = Instant.parse("2026-08-26T01:00:00Z");
        SettlementBatchGroupDO group = group(LocalDate.of(2026, 8, 26));
        group.setBatchType("ADJUSTMENT");
        when(profileMapper.selectReadyBatchGroups(100)).thenReturn(List.of(group));
        service.createAndClaimMaturedBatches(now);

        verify(automaticPostService).createAndClaim(
                org.mockito.ArgumentMatchers.eq(group),
                org.mockito.ArgumentMatchers.eq(SettlementBatchType.ADJUSTMENT),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 自动预审档案应由系统锁定候选并生成待审批单，不得直接创建正式批次。 */
    @Test
    void shouldCreateSystemReviewWithoutPostingForAutoReviewProfile() {
        Instant now = Instant.parse("2026-08-26T01:00:00Z");
        SettlementBatchGroupDO group = group(LocalDate.of(2026, 8, 26));
        group.setProcessingMode("AUTO_REVIEW");
        SettlementCandidateDO candidate = new SettlementCandidateDO();
        candidate.setId(101L);
        candidate.setVersion(7L);
        when(profileMapper.selectReadyBatchGroups(100)).thenReturn(List.of(group));
        when(profileMapper.selectReadyReviewCandidates(
                11L, "REGULAR", LocalDate.of(2026, 8, 26),
                LocalDateTime.of(2026, 8, 25, 16, 0), 1000))
                .thenReturn(List.of(candidate));
        when(reviewOrderService.submitAutomatic(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SettlementReviewCommandResult(
                        "SO20260826-00000001", "PENDING_APPROVAL", null,
                        1, "USD", 2, "CREDIT", java.math.BigDecimal.TEN, 0L));

        assertThat(service.createAndClaimMaturedBatches(now)).isEqualTo(1);

        ArgumentCaptor<SettlementReviewCreateCommand> captor =
                ArgumentCaptor.forClass(SettlementReviewCreateCommand.class);
        verify(reviewOrderService).submitAutomatic(captor.capture());
        assertThat(captor.getValue().requestKey())
                .isEqualTo("AUTO_REVIEW:REGULAR:11:2026-08-26:101");
        assertThat(captor.getValue().candidates()).containsExactly(
                new SettlementReviewCreateCommand.CandidateReference(101L, 7L));
        assertThat(captor.getValue().submitter().accountId()).isZero();
        verify(automaticPostService, never()).createAndClaim(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 自动预审候选被并发占用时不得虚增已处理分组数。 */
    @Test
    void shouldNotCountAutoReviewGroupWhenNoCandidateRemainsReady() {
        Instant now = Instant.parse("2026-08-26T01:00:00Z");
        SettlementBatchGroupDO group = group(LocalDate.of(2026, 8, 26));
        group.setProcessingMode("AUTO_REVIEW");
        when(profileMapper.selectReadyBatchGroups(100)).thenReturn(List.of(group));
        when(profileMapper.selectReadyReviewCandidates(
                11L, "REGULAR", LocalDate.of(2026, 8, 26),
                LocalDateTime.of(2026, 8, 25, 16, 0), 1000))
                .thenReturn(List.of());

        assertThat(service.createAndClaimMaturedBatches(now)).isZero();

        verify(reviewOrderService, never()).submitAutomatic(org.mockito.ArgumentMatchers.any());
        verify(automaticPostService, never()).createAndClaim(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private SettlementBatchGroupDO group(LocalDate earliestEligibleDate) {
        SettlementBatchGroupDO row = new SettlementBatchGroupDO();
        row.setSettlementProfileId(11L);
        row.setMerchantId("240001");
        row.setSettlementAccountId(21L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setBusinessTimeZone("Asia/Shanghai");
        row.setDailyCutoffTime(LocalTime.MIDNIGHT);
        row.setEarliestEligibleDate(earliestEligibleDate);
        row.setBatchType("REGULAR");
        row.setProcessingMode("AUTO_POST");
        return row;
    }
}
