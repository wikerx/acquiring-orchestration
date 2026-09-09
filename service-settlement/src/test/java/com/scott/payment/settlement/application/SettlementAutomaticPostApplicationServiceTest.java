package com.scott.payment.settlement.application;

import com.scott.payment.settlement.domain.model.SettlementBatchType;
import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.dto.SettlementCommandAudit;
import com.scott.payment.settlement.entity.SettlementBatchGroupDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.service.SettlementBatchCreationService;
import com.scott.payment.settlement.service.SettlementCandidateBulkClaimService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementAutomaticPostApplicationServiceTest
 * @date : 2026-09-02 18:30
 * @email : scott_x@163.com
 * @description : 验证自动正式结算先锁定日切窗口内的真实候选，再执行幂等建批、认领和空批次审计兜底。
 * @status : create
 */
class SettlementAutomaticPostApplicationServiceTest {

    private SettlementCandidateMapper candidateMapper;
    private SettlementBatchCreationService batchCreationService;
    private SettlementCandidateBulkClaimService bulkClaimService;
    private SettlementBatchCommandApplicationService batchCommandService;
    private SettlementAutomaticPostApplicationService service;

    @BeforeEach
    void setUp() {
        candidateMapper = mock(SettlementCandidateMapper.class);
        batchCreationService = mock(SettlementBatchCreationService.class);
        bulkClaimService = mock(SettlementCandidateBulkClaimService.class);
        batchCommandService = mock(SettlementBatchCommandApplicationService.class);
        service = new SettlementAutomaticPostApplicationService(
                candidateMapper, batchCreationService, bulkClaimService, batchCommandService);
    }

    /** 日切窗口内没有真实可认领候选时不得创建零候选正式批次。 */
    @Test
    void shouldSkipBatchCreationWhenNoCandidateIsClaimableInCutoffWindow() {
        SettlementBatchGroupDO group = group();
        LocalDate businessDate = LocalDate.of(2026, 9, 2);
        LocalDateTime cutoffEnd = LocalDateTime.of(2026, 9, 1, 16, 0);
        when(candidateMapper.selectAutomaticPostAnchorForUpdate(
                "240001", 11L, 21L, "USD", 2, "REGULAR", businessDate, cutoffEnd))
                .thenReturn(null);

        boolean handled = service.createAndClaim(
                group, SettlementBatchType.REGULAR, businessDate,
                LocalDateTime.of(2026, 8, 31, 16, 0), cutoffEnd,
                LocalDateTime.of(2026, 9, 2, 10, 30));

        assertThat(handled).isFalse();
        verify(batchCreationService, never()).create(org.mockito.ArgumentMatchers.any());
        verify(bulkClaimService, never()).claimAndSeal(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    /** 自动批次幂等键必须引用已锁定候选的主键和版本，避免复用历史空批次身份。 */
    @Test
    void shouldCreateAndClaimUsingLockedCandidateIdentity() {
        SettlementBatchGroupDO group = group();
        LocalDate businessDate = LocalDate.of(2026, 9, 2);
        LocalDateTime cutoffBegin = LocalDateTime.of(2026, 8, 31, 16, 0);
        LocalDateTime cutoffEnd = LocalDateTime.of(2026, 9, 1, 16, 0);
        LocalDateTime operationTime = LocalDateTime.of(2026, 9, 2, 10, 30);
        SettlementCandidateDO anchor = new SettlementCandidateDO();
        anchor.setId(392L);
        anchor.setVersion(7L);
        when(candidateMapper.selectAutomaticPostAnchorForUpdate(
                "240001", 11L, 21L, "USD", 2, "REGULAR", businessDate, cutoffEnd))
                .thenReturn(anchor);
        when(batchCreationService.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SettlementBatchCreateResult(
                        9L, "SB20260902-00000003", "2026-09-02 00000003", false));
        when(bulkClaimService.claimAndSeal("SB20260902-00000003", operationTime)).thenReturn(1);

        boolean handled = service.createAndClaim(
                group, SettlementBatchType.REGULAR, businessDate,
                cutoffBegin, cutoffEnd, operationTime);

        assertThat(handled).isTrue();
        ArgumentCaptor<SettlementBatchCreateCommand> commandCaptor =
                ArgumentCaptor.forClass(SettlementBatchCreateCommand.class);
        verify(batchCreationService).create(commandCaptor.capture());
        SettlementBatchCreateCommand command = commandCaptor.getValue();
        assertThat(command.createRequestKey()).isEqualTo("AUTO:REGULAR:11:2026-09-02:392:V7");
        assertThat(command.businessDate()).isEqualTo(businessDate);
        assertThat(command.cutoffBeginTime()).isEqualTo(cutoffBegin);
        assertThat(command.cutoffEndTime()).isEqualTo(cutoffEnd);
        verify(bulkClaimService).claimAndSeal("SB20260902-00000003", operationTime);
    }

    /** 极端竞态下认领结果仍为空时，批次必须走正式取消状态机并写入可信系统审计。 */
    @Test
    void shouldCancelUnexpectedEmptyBatchWithTrustedSystemAudit() {
        SettlementBatchGroupDO group = group();
        LocalDate businessDate = LocalDate.of(2026, 9, 2);
        LocalDateTime cutoffEnd = LocalDateTime.of(2026, 9, 1, 16, 0);
        LocalDateTime operationTime = LocalDateTime.of(2026, 9, 2, 10, 30);
        SettlementCandidateDO anchor = new SettlementCandidateDO();
        anchor.setId(392L);
        anchor.setVersion(7L);
        when(candidateMapper.selectAutomaticPostAnchorForUpdate(
                "240001", 11L, 21L, "USD", 2, "REGULAR", businessDate, cutoffEnd))
                .thenReturn(anchor);
        when(batchCreationService.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SettlementBatchCreateResult(
                        9L, "SB20260902-00000003", "2026-09-02 00000003", false));
        when(bulkClaimService.claimAndSeal("SB20260902-00000003", operationTime)).thenReturn(0);

        boolean handled = service.createAndClaim(
                group, SettlementBatchType.REGULAR, businessDate,
                LocalDateTime.of(2026, 8, 31, 16, 0), cutoffEnd, operationTime);

        assertThat(handled).isFalse();
        ArgumentCaptor<SettlementCommandAudit> auditCaptor =
                ArgumentCaptor.forClass(SettlementCommandAudit.class);
        verify(batchCommandService).cancelBeforePosting(
                org.mockito.ArgumentMatchers.eq("SB20260902-00000003"),
                org.mockito.ArgumentMatchers.eq(0L), auditCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(operationTime));
        assertThat(auditCaptor.getValue().requestKey()).isEqualTo("AUTO_EMPTY:SB20260902-00000003");
        assertThat(auditCaptor.getValue().operator().accountId()).isZero();
        assertThat(auditCaptor.getValue().operator().accountName()).isEqualTo("service-settlement");
        assertThat(auditCaptor.getValue().operator().roleSnapshot()).isEqualTo("SYSTEM");
    }

    private SettlementBatchGroupDO group() {
        SettlementBatchGroupDO row = new SettlementBatchGroupDO();
        row.setSettlementProfileId(11L);
        row.setMerchantId("240001");
        row.setSettlementAccountId(21L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setBusinessTimeZone("Asia/Shanghai");
        row.setProcessingMode("AUTO_POST");
        return row;
    }
}
