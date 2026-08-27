package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.entity.SettlementBatchGroupDO;
import com.scott.payment.settlement.mapper.MerchantSettlementProfileMapper;
import com.scott.payment.settlement.service.SettlementBatchCreationService;
import com.scott.payment.settlement.service.SettlementCandidateBulkClaimService;
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
    private SettlementBatchCreationService batchCreationService;
    private SettlementCandidateBulkClaimService bulkClaimService;
    private DefaultSettlementAutomaticBatchService service;

    @BeforeEach
    void setUp() {
        profileMapper = mock(MerchantSettlementProfileMapper.class);
        batchCreationService = mock(SettlementBatchCreationService.class);
        bulkClaimService = mock(SettlementCandidateBulkClaimService.class);
        service = new DefaultSettlementAutomaticBatchService(
                profileMapper, batchCreationService, bulkClaimService);
    }

    /** 上海零点日切在 09:00 已成熟，应创建当天唯一常规批次。 */
    @Test
    void shouldCreateDeterministicMaturedDailyBatchAndClaimCandidates() {
        Instant now = Instant.parse("2026-08-26T01:00:00Z");
        SettlementBatchGroupDO group = group(LocalDate.of(2026, 8, 26));
        when(profileMapper.selectReadyBatchGroups(100)).thenReturn(List.of(group));
        when(batchCreationService.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SettlementBatchCreateResult(
                        1L, "SB20260826-00000008", "2026-08-26 00000008", false));

        int processed = service.createAndClaimMaturedBatches(now);

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<SettlementBatchCreateCommand> captor =
                ArgumentCaptor.forClass(SettlementBatchCreateCommand.class);
        verify(batchCreationService).create(captor.capture());
        SettlementBatchCreateCommand command = captor.getValue();
        assertThat(command.createRequestKey()).isEqualTo("AUTO:REGULAR:11:2026-08-26:101");
        assertThat(command.batchType()).isEqualTo(
                com.scott.payment.settlement.domain.model.SettlementBatchType.REGULAR);
        assertThat(command.businessDate()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(command.cutoffBeginTime()).isEqualTo(LocalDateTime.of(2026, 8, 24, 16, 0));
        assertThat(command.cutoffEndTime()).isEqualTo(LocalDateTime.of(2026, 8, 25, 16, 0));
        verify(bulkClaimService).claimAndSeal(
                "SB20260826-00000008", java.time.LocalDateTime.of(2026, 8, 26, 1, 0));
    }

    /** 候选结算日期晚于当前成熟业务日时不得提前建批。 */
    @Test
    void shouldSkipCandidatesThatAreNotEligibleOnMaturedBusinessDate() {
        Instant now = Instant.parse("2026-08-26T01:00:00Z");
        when(profileMapper.selectReadyBatchGroups(100))
                .thenReturn(List.of(group(LocalDate.of(2026, 8, 27))));

        assertThat(service.createAndClaimMaturedBatches(now)).isZero();
    }

    /** 保证金释放来源必须进入独立释放批次，不能混入常规交易日批。 */
    @Test
    void shouldCreateReserveReleaseBatchForReleaseGroup() {
        Instant now = Instant.parse("2026-08-26T01:00:00Z");
        SettlementBatchGroupDO group = group(LocalDate.of(2026, 8, 26));
        group.setBatchType("RESERVE_RELEASE");
        when(profileMapper.selectReadyBatchGroups(100)).thenReturn(List.of(group));
        when(batchCreationService.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SettlementBatchCreateResult(
                        2L, "SB20260826-00000009", "2026-08-26 00000009", false));

        service.createAndClaimMaturedBatches(now);

        ArgumentCaptor<SettlementBatchCreateCommand> captor =
                ArgumentCaptor.forClass(SettlementBatchCreateCommand.class);
        verify(batchCreationService).create(captor.capture());
        assertThat(captor.getValue().createRequestKey())
                .isEqualTo("AUTO:RESERVE_RELEASE:11:2026-08-26:101");
        assertThat(captor.getValue().batchType()).isEqualTo(
                com.scott.payment.settlement.domain.model.SettlementBatchType.RESERVE_RELEASE);
    }

    private SettlementBatchGroupDO group(LocalDate earliestEligibleDate) {
        SettlementBatchGroupDO row = new SettlementBatchGroupDO();
        row.setSettlementProfileId(11L);
        row.setAnchorCandidateId(101L);
        row.setMerchantId("240001");
        row.setSettlementAccountId(21L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setBusinessTimeZone("Asia/Shanghai");
        row.setDailyCutoffTime(LocalTime.MIDNIGHT);
        row.setEarliestEligibleDate(earliestEligibleDate);
        row.setBatchType("REGULAR");
        return row;
    }
}
