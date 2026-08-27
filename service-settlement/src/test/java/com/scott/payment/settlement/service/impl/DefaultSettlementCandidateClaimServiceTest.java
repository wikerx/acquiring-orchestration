package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementCandidateClaimOutcome;
import com.scott.payment.settlement.domain.model.SettlementCandidateStatus;
import com.scott.payment.settlement.dto.SettlementCandidateClaimCommand;
import com.scott.payment.settlement.entity.SettlementBatchCandidateDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementCandidateClaimServiceTest
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 验证真实候选按批次维度独占认领、依赖门禁、影子隔离和重复调用幂等。
 * @status : create
 */
@ExtendWith(MockitoExtension.class)
class DefaultSettlementCandidateClaimServiceTest {

    @Mock
    private SettlementBatchMapper batchMapper;
    @Mock
    private SettlementCandidateMapper candidateMapper;
    @Mock
    private SettlementBatchCandidateMapper relationMapper;

    private DefaultSettlementCandidateClaimService service;

    @BeforeEach
    void setUp() {
        service = new DefaultSettlementCandidateClaimService(batchMapper, candidateMapper, relationMapper);
    }

    @Test
    void shouldClaimRealReadyCandidateAndPersistAuditRelation() {
        SettlementBatchDO batch = batch();
        SettlementCandidateDO candidate = candidate();
        AtomicReference<SettlementBatchCandidateDO> storedRelation = new AtomicReference<>();
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(candidateMapper.selectByIdForUpdate(candidate.getId())).thenReturn(candidate);
        when(candidateMapper.countUnresolvedDependencies(candidate.getId(), batch.getSettlementBatchNo())).thenReturn(0L);
        when(candidateMapper.claim(candidate.getId(), batch.getSettlementBatchNo(), candidate.getSettlementProfileId(),
                candidate.getVersion(), command().claimedTime())).thenReturn(1);
        when(relationMapper.insertIdempotent(any(SettlementBatchCandidateDO.class))).thenAnswer(invocation -> {
            storedRelation.set(invocation.getArgument(0));
            return 1;
        });
        when(relationMapper.selectByBatchAndCandidateForUpdate(batch.getSettlementBatchNo(), candidate.getId()))
                .thenAnswer(invocation -> storedRelation.get());
        when(batchMapper.incrementCandidateCount(batch.getSettlementBatchNo(), batch.getVersion())).thenReturn(1);

        var result = service.claim(command());

        assertThat(result.outcome()).isEqualTo(SettlementCandidateClaimOutcome.CLAIMED);
        assertThat(result.candidateId()).isEqualTo(candidate.getId());
        verify(relationMapper).insertIdempotent(any(SettlementBatchCandidateDO.class));
    }

    @Test
    void shouldAcknowledgeCandidateAlreadyClaimedBySameBatchWithoutDoubleCounting() {
        SettlementBatchDO batch = batch();
        SettlementCandidateDO candidate = candidate();
        candidate.setCandidateStatus(SettlementCandidateStatus.CLAIMED.name());
        candidate.setSettlementBatchNo(batch.getSettlementBatchNo());
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(candidateMapper.selectByIdForUpdate(candidate.getId())).thenReturn(candidate);
        when(relationMapper.selectByBatchAndCandidateForUpdate(batch.getSettlementBatchNo(), candidate.getId()))
                .thenReturn(relation(candidate));

        var result = service.claim(command());

        assertThat(result.outcome()).isEqualTo(SettlementCandidateClaimOutcome.ALREADY_CLAIMED);
        verify(candidateMapper, never()).claim(any(Long.class), any(), any(Long.class), any(Long.class), any());
        verify(batchMapper, never()).incrementCandidateCount(any(), any(Long.class));
    }

    @Test
    void shouldRejectShadowCandidateAndUnresolvedDependency() {
        SettlementBatchDO batch = batch();
        SettlementCandidateDO shadow = candidate();
        shadow.setShadowMode(1);
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(candidateMapper.selectByIdForUpdate(shadow.getId())).thenReturn(shadow);

        assertThatThrownBy(() -> service.claim(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shadow");

        SettlementCandidateDO real = candidate();
        when(candidateMapper.selectByIdForUpdate(real.getId())).thenReturn(real);
        when(candidateMapper.countUnresolvedDependencies(real.getId(), batch.getSettlementBatchNo())).thenReturn(1L);

        assertThatThrownBy(() -> service.claim(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dependency");
        verify(candidateMapper, never()).claim(any(Long.class), any(), any(Long.class), any(Long.class), any());
    }

    @Test
    void shouldRejectCandidateBeforeEligibleBusinessDate() {
        SettlementBatchDO batch = batch();
        SettlementCandidateDO candidate = candidate();
        candidate.setSettlementEligibleDate(batch.getBusinessDate().plusDays(1));
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(candidateMapper.selectByIdForUpdate(candidate.getId())).thenReturn(candidate);

        assertThatThrownBy(() -> service.claim(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eligible");
        verify(candidateMapper, never()).claim(any(Long.class), any(), any(Long.class), any(Long.class), any());
    }

    @Test
    void shouldRejectReserveReleaseCandidateFromRegularBatch() {
        SettlementBatchDO batch = batch();
        SettlementCandidateDO candidate = candidate();
        candidate.setSourceType("RESERVE_RELEASE");
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(candidateMapper.selectByIdForUpdate(candidate.getId())).thenReturn(candidate);

        assertThatThrownBy(() -> service.claim(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("batch type");
        verify(candidateMapper, never()).claim(any(Long.class), any(), any(Long.class), any(Long.class), any());
    }

    private SettlementCandidateClaimCommand command() {
        return new SettlementCandidateClaimCommand(
                "SB20260826-00000008", 200L, 4L, LocalDateTime.of(2026, 8, 26, 12, 30));
    }

    private SettlementBatchDO batch() {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setId(100L);
        row.setSettlementBatchNo("SB20260826-00000008");
        row.setBusinessDate(LocalDate.of(2026, 8, 26));
        row.setMerchantId("M1001");
        row.setSettlementProfileId(11L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setBatchType("REGULAR");
        row.setBatchStatus(SettlementBatchStatus.CREATED.name());
        row.setCandidateCount(0);
        row.setVersion(2L);
        return row;
    }

    private SettlementCandidateDO candidate() {
        SettlementCandidateDO row = new SettlementCandidateDO();
        row.setId(200L);
        row.setCandidateNo("SC-200");
        row.setSourceType("CLEARING_REVISION");
        row.setSourceBusinessId("FS-1");
        row.setSourceRevision(1);
        row.setMerchantId("M1001");
        row.setSettlementProfileId(11L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setSettlementEligibleDate(LocalDate.of(2026, 8, 26));
        row.setCandidateStatus(SettlementCandidateStatus.READY.name());
        row.setShadowMode(0);
        row.setVersion(4L);
        return row;
    }

    private SettlementBatchCandidateDO relation(SettlementCandidateDO candidate) {
        SettlementBatchCandidateDO row = new SettlementBatchCandidateDO();
        row.setSettlementBatchNo("SB20260826-00000008");
        row.setCandidateId(candidate.getId());
        row.setSourceType(candidate.getSourceType());
        row.setSourceBusinessId(candidate.getSourceBusinessId());
        row.setSourceRevision(candidate.getSourceRevision());
        row.setRelationStatus("CLAIMED");
        return row;
    }
}
