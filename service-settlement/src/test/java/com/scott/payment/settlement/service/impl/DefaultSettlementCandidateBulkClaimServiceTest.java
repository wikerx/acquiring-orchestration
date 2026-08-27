package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementCandidateStatus;
import com.scott.payment.settlement.entity.SettlementBatchCandidateDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementCandidateBulkClaimServiceTest
 * @date : 2026-08-26 22:20
 * @email : scott_x@163.com
 * @description : 验证自动批次使用候选版本批量 CAS、批量审计关系和批次计数 CAS，并在无更多候选后封为 CLAIMED。
 * @status : create
 */
class DefaultSettlementCandidateBulkClaimServiceTest {

    private SettlementBatchMapper batchMapper;
    private SettlementCandidateMapper candidateMapper;
    private SettlementBatchCandidateMapper relationMapper;
    private DefaultSettlementCandidateBulkClaimService service;

    @BeforeEach
    void setUp() {
        batchMapper = mock(SettlementBatchMapper.class);
        candidateMapper = mock(SettlementCandidateMapper.class);
        relationMapper = mock(SettlementBatchCandidateMapper.class);
        service = new DefaultSettlementCandidateBulkClaimService(
                batchMapper, candidateMapper, relationMapper);
    }

    /** 一页候选应原子认领，下一次空页后把非空批次封为 CLAIMED。 */
    @Test
    void shouldClaimPageAndSealNonEmptyBatch() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 1, 0);
        SettlementBatchDO batch = batch();
        List<SettlementCandidateDO> candidates = List.of(candidate(101L), candidate(102L));
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(candidateMapper.selectClaimableByBatchForUpdate(batch.getSettlementBatchNo(), 200))
                .thenReturn(candidates, List.of());
        when(candidateMapper.claimBatch(candidates, batch.getSettlementBatchNo(),
                batch.getSettlementProfileId(), now)).thenReturn(2);
        when(relationMapper.insertBatchIdempotent(anyList())).thenReturn(2);
        when(batchMapper.incrementCandidateCountBy(
                batch.getSettlementBatchNo(), 2, 0L)).thenReturn(1);
        when(batchMapper.sealClaimedBatch(
                batch.getSettlementBatchNo(), 2, 1L, now)).thenReturn(1);

        service.claimAndSeal(batch.getSettlementBatchNo(), now);

        assertThat(batch.getCandidateCount()).isEqualTo(2);
        assertThat(batch.getVersion()).isEqualTo(1L);
        verify(relationMapper).insertBatchIdempotent(anyList());
        verify(batchMapper).sealClaimedBatch(batch.getSettlementBatchNo(), 2, 1L, now);
    }

    /** 持续有积压时单批只能消费五页共1000条，剩余候选必须留给下一批。 */
    @Test
    void shouldSealBatchAtOneThousandCandidateHardLimit() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 2, 0);
        SettlementBatchDO batch = batch();
        List<SettlementCandidateDO> page = java.util.stream.LongStream.rangeClosed(1, 200)
                .mapToObj(this::candidate).toList();
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(candidateMapper.selectClaimableByBatchForUpdate(batch.getSettlementBatchNo(), 200))
                .thenReturn(page);
        when(candidateMapper.claimBatch(page, batch.getSettlementBatchNo(),
                batch.getSettlementProfileId(), now)).thenReturn(200);
        when(relationMapper.insertBatchIdempotent(anyList())).thenReturn(200);
        for (long version = 0; version < 5; version++) {
            when(batchMapper.incrementCandidateCountBy(
                    batch.getSettlementBatchNo(), 200, version)).thenReturn(1);
        }
        when(batchMapper.sealClaimedBatch(
                batch.getSettlementBatchNo(), 1000, 5L, now)).thenReturn(1);

        service.claimAndSeal(batch.getSettlementBatchNo(), now);

        assertThat(batch.getCandidateCount()).isEqualTo(1000);
        verify(candidateMapper, org.mockito.Mockito.times(5))
                .selectClaimableByBatchForUpdate(batch.getSettlementBatchNo(), 200);
        verify(batchMapper).sealClaimedBatch(batch.getSettlementBatchNo(), 1000, 5L, now);
    }

    private SettlementBatchDO batch() {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setSettlementBatchNo("SB20260826-00000008");
        row.setMerchantId("240001");
        row.setSettlementProfileId(11L);
        row.setSettlementAccountId(21L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setBusinessDate(LocalDate.of(2026, 8, 26));
        row.setBatchStatus(SettlementBatchStatus.CREATED.name());
        row.setCandidateCount(0);
        row.setVersion(0L);
        return row;
    }

    private SettlementCandidateDO candidate(Long id) {
        SettlementCandidateDO row = new SettlementCandidateDO();
        row.setId(id);
        row.setCandidateNo("SC" + id);
        row.setSourceType("CLEARING_REVISION");
        row.setSourceBusinessId("FS" + id);
        row.setSourceRevision(1);
        row.setMerchantId("240001");
        row.setSettlementProfileId(11L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setSettlementEligibleDate(LocalDate.of(2026, 8, 26));
        row.setCandidateStatus(SettlementCandidateStatus.READY.name());
        row.setShadowMode(0);
        row.setVersion(0L);
        return row;
    }
}
