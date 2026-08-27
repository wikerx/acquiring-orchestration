package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.entity.ClearingSettlementCandidateDO;
import com.scott.payment.clearing.mapper.ClearingSettlementCandidateMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultClearingSettlementCandidateServiceTest {

    @Test
    void createShouldPersistDeterministicShadowCandidateAndAcceptExactDuplicate() {
        ClearingSettlementCandidateMapper mapper = mock(ClearingSettlementCandidateMapper.class);
        DefaultClearingSettlementCandidateService service = service(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 13, 0);
        LocalDate eligibleDate = LocalDate.of(2026, 8, 27);
        AtomicReference<ClearingSettlementCandidateDO> persisted = new AtomicReference<>();
        when(mapper.insertIdempotent(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectForUpdate("FS-1", 2)).thenAnswer(invocation -> persisted.get());

        service.create("FS-1", 2, operation(), "USD", eligibleDate, now);

        ArgumentCaptor<ClearingSettlementCandidateDO> captor =
                ArgumentCaptor.forClass(ClearingSettlementCandidateDO.class);
        verify(mapper).insertIdempotent(captor.capture());
        ClearingSettlementCandidateDO inserted = captor.getValue();
        assertThat(inserted.getCandidateNo()).startsWith("SC").hasSize(34);
        assertThat(inserted.getSourceBusinessId()).isEqualTo("FS-1");
        assertThat(inserted.getSourceRevision()).isEqualTo(2);
        assertThat(inserted.getSourceTransactionDateTime()).isEqualTo(operation().transactionDateTime());
        assertThat(inserted.getTargetCurrency()).isEqualTo("USD");
        assertThat(inserted.getTargetCurrencyExponent()).isEqualTo(2);
        assertThat(inserted.getCandidateStatus()).isEqualTo("READY");
        assertThat(inserted.getShadowMode()).isEqualTo(1);

        ClearingSettlementCandidateMapper duplicateMapper = mock(ClearingSettlementCandidateMapper.class);
        DefaultClearingSettlementCandidateService duplicateService = service(duplicateMapper);
        when(duplicateMapper.insertIdempotent(any())).thenReturn(0);
        when(duplicateMapper.selectForUpdate("FS-1", 2)).thenReturn(inserted);

        duplicateService.create("FS-1", 2, operation(), "USD", eligibleDate, now);
    }

    @Test
    void createShouldRejectMismatchedDuplicate() {
        ClearingSettlementCandidateMapper mapper = mock(ClearingSettlementCandidateMapper.class);
        DefaultClearingSettlementCandidateService service = service(mapper);
        when(mapper.insertIdempotent(any())).thenReturn(0);
        ClearingSettlementCandidateDO mismatch = new ClearingSettlementCandidateDO();
        mismatch.setCandidateNo("OTHER");
        when(mapper.selectForUpdate("FS-1", 1)).thenReturn(mismatch);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.create("FS-1", 1, operation(), "USD",
                        LocalDate.of(2026, 8, 27), LocalDateTime.of(2026, 8, 26, 13, 0)))
                .withMessageContaining("mismatched");
    }

    @Test
    void replaceShouldSupersedeOnlyUnclaimedReadyRevisionBeforeCreatingNextRevision() {
        ClearingSettlementCandidateMapper mapper = mock(ClearingSettlementCandidateMapper.class);
        DefaultClearingSettlementCandidateService service = service(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 13, 0);
        ClearingSettlementCandidateDO old = new ClearingSettlementCandidateDO();
        old.setCandidateStatus("READY");
        old.setVersion(3L);
        AtomicReference<ClearingSettlementCandidateDO> persisted = new AtomicReference<>();
        when(mapper.selectForUpdate("FS-1", 1)).thenReturn(old);
        when(mapper.selectForUpdate("FS-1", 2)).thenAnswer(invocation -> persisted.get());
        when(mapper.supersedeReady("FS-1", 1, 3L, now)).thenReturn(1);
        when(mapper.insertIdempotent(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return 1;
        });

        service.replace("FS-1", 1, 2, operation(), "USD", LocalDate.of(2026, 8, 27), now);

        verify(mapper).supersedeReady("FS-1", 1, 3L, now);
        ArgumentCaptor<ClearingSettlementCandidateDO> captor =
                ArgumentCaptor.forClass(ClearingSettlementCandidateDO.class);
        verify(mapper).insertIdempotent(captor.capture());
        assertThat(captor.getValue().getSourceRevision()).isEqualTo(2);
    }

    @Test
    void replaceReplayHeldShouldOnlySupersedeReplayHeldRevision() {
        ClearingSettlementCandidateMapper mapper = mock(ClearingSettlementCandidateMapper.class);
        DefaultClearingSettlementCandidateService service = service(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 13, 0);
        ClearingSettlementCandidateDO old = new ClearingSettlementCandidateDO();
        old.setCandidateStatus("REPLAY_HOLD");
        old.setVersion(4L);
        AtomicReference<ClearingSettlementCandidateDO> persisted = new AtomicReference<>();
        when(mapper.selectForUpdate("FS-1", 1)).thenReturn(old);
        when(mapper.selectForUpdate("FS-1", 2)).thenAnswer(invocation -> persisted.get());
        when(mapper.supersedeReplayHeld("FS-1", 1, 4L, now)).thenReturn(1);
        when(mapper.insertIdempotent(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return 1;
        });

        service.replaceReplayHeld(
                "FS-1", 1, 2, operation(), "USD", LocalDate.of(2026, 8, 27), now);

        verify(mapper).supersedeReplayHeld("FS-1", 1, 4L, now);
        verify(mapper, org.mockito.Mockito.never()).supersedeReady(
                anyString(), anyInt(), anyLong(), any());
        assertThat(persisted.get().getSourceRevision()).isEqualTo(2);
        assertThat(persisted.get().getCandidateStatus()).isEqualTo("READY");
    }

    @Test
    void replaceReplayHeldShouldRejectReadyOrClaimedCandidate() {
        ClearingSettlementCandidateMapper mapper = mock(ClearingSettlementCandidateMapper.class);
        DefaultClearingSettlementCandidateService service = service(mapper);
        ClearingSettlementCandidateDO old = new ClearingSettlementCandidateDO();
        old.setCandidateStatus("READY");
        old.setVersion(4L);
        when(mapper.selectForUpdate("FS-1", 1)).thenReturn(old);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.replaceReplayHeld(
                        "FS-1", 1, 2, operation(), "USD", LocalDate.of(2026, 8, 27),
                        LocalDateTime.of(2026, 8, 26, 13, 0)))
                .withMessageContaining("replay-held");
        verify(mapper, org.mockito.Mockito.never()).supersedeReplayHeld(
                anyString(), anyInt(), anyLong(), any());
    }

    @Test
    void createReserveReleaseShouldPersistSourceTypedStableCandidate() {
        ClearingSettlementCandidateMapper mapper = mock(ClearingSettlementCandidateMapper.class);
        DefaultClearingSettlementCandidateService service = service(mapper);
        LocalDateTime releaseTime = LocalDateTime.of(2026, 8, 26, 18, 30);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 10, 30);
        AtomicReference<ClearingSettlementCandidateDO> persisted = new AtomicReference<>();
        when(mapper.insertIdempotent(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectSourceForUpdate("RESERVE_RELEASE", "RS-1", 4))
                .thenAnswer(invocation -> persisted.get());

        service.createReserveRelease("RS-1", 4, "RRL-1", releaseTime,
                "M-1", "USD", LocalDate.of(2026, 8, 26), now);

        ClearingSettlementCandidateDO inserted = persisted.get();
        assertThat(inserted.getCandidateNo()).startsWith("SC").hasSize(34);
        assertThat(inserted.getSourceType()).isEqualTo("RESERVE_RELEASE");
        assertThat(inserted.getSourceBusinessId()).isEqualTo("RS-1");
        assertThat(inserted.getSourceRevision()).isEqualTo(4);
        assertThat(inserted.getSourceTransactionId()).isEqualTo("RRL-1");
        assertThat(inserted.getSourceTransactionDateTime()).isEqualTo(releaseTime);
        assertThat(inserted.getMerchantId()).isEqualTo("M-1");
        assertThat(inserted.getTargetCurrency()).isEqualTo("USD");
        assertThat(inserted.getCandidateStatus()).isEqualTo("READY");
    }

    private DefaultClearingSettlementCandidateService service(ClearingSettlementCandidateMapper mapper) {
        return new DefaultClearingSettlementCandidateService(mapper);
    }

    private ClearingOperationFacts operation() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 9, 0);
        return new ClearingOperationFacts(
                "TX-1", "OP-1", null, "M-1", "ORDER-1", "PAYMENT", "SUCCESS",
                "EUR", new BigDecimal("100.00"), "EUR", new BigDecimal("100.00"),
                "EUR", new BigDecimal("100.00"), 2, time, time.minusHours(8),
                "Asia/Shanghai", 5);
    }
}
