package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementBatchType;
import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementBatchDailySequenceDO;
import com.scott.payment.settlement.mapper.SettlementBatchDailySequenceMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.support.SettlementBatchNumberFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementBatchCreationServiceTest
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 验证数据库日序列发号、create_request_key 重试复用和幂等身份冲突保护。
 * @status : create
 */
@ExtendWith(MockitoExtension.class)
class DefaultSettlementBatchCreationServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 26, 8, 15, 30, 123_000_000);

    @Mock
    private SettlementBatchDailySequenceMapper sequenceMapper;
    @Mock
    private SettlementBatchMapper batchMapper;

    private DefaultSettlementBatchCreationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultSettlementBatchCreationService(
                sequenceMapper,
                batchMapper,
                new SettlementBatchNumberFormatter(),
                Clock.fixed(Instant.parse("2026-08-26T08:15:30.123Z"), ZoneOffset.UTC));
    }

    @Test
    void shouldAllocateDatabaseSequenceAndCreateBatch() {
        SettlementBatchCreateCommand command = command("REQ-1");
        SettlementBatchDailySequenceDO sequence = new SettlementBatchDailySequenceDO();
        sequence.setBusinessDate(command.businessDate());
        sequence.setCurrentSequence(7);
        sequence.setVersion(3L);
        SettlementBatchDO stored = storedBatch(command, "SB20260826-00000008", 8);
        when(sequenceMapper.selectForUpdate(command.businessDate())).thenReturn(sequence);
        when(batchMapper.selectByCreateRequestKeyForUpdate(command.createRequestKey()))
                .thenReturn(null, stored);
        when(sequenceMapper.increment(command.businessDate(), 7, 3L)).thenReturn(1);

        var result = service.create(command);

        assertThat(result.settlementBatchNo()).isEqualTo("SB20260826-00000008");
        assertThat(result.displayBatchNo()).isEqualTo("2026-08-26 00000008");
        assertThat(result.reused()).isFalse();
        ArgumentCaptor<SettlementBatchDO> rowCaptor = ArgumentCaptor.forClass(SettlementBatchDO.class);
        verify(batchMapper).insertIdempotent(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getCreateTime()).isEqualTo(FIXED_NOW);
        assertThat(rowCaptor.getValue().getUpdateTime()).isEqualTo(FIXED_NOW);
    }

    @Test
    void shouldReturnExistingBatchForSameCreateRequestWithoutConsumingSequence() {
        SettlementBatchCreateCommand command = command("REQ-1");
        SettlementBatchDailySequenceDO sequence = new SettlementBatchDailySequenceDO();
        sequence.setBusinessDate(command.businessDate());
        sequence.setCurrentSequence(8);
        sequence.setVersion(4L);
        SettlementBatchDO stored = storedBatch(command, "SB20260826-00000008", 8);
        when(sequenceMapper.selectForUpdate(command.businessDate())).thenReturn(sequence);
        when(batchMapper.selectByCreateRequestKeyForUpdate(command.createRequestKey())).thenReturn(stored);

        var result = service.create(command);

        assertThat(result.reused()).isTrue();
        verify(sequenceMapper, never()).increment(any(), any(Integer.class), any(Long.class));
        verify(batchMapper, never()).insertIdempotent(any());
    }

    @Test
    void shouldRejectCreateRequestKeyReusedWithDifferentImmutableIdentity() {
        SettlementBatchCreateCommand command = command("REQ-1");
        SettlementBatchDailySequenceDO sequence = new SettlementBatchDailySequenceDO();
        sequence.setBusinessDate(command.businessDate());
        sequence.setCurrentSequence(8);
        sequence.setVersion(4L);
        SettlementBatchDO stored = storedBatch(command, "SB20260826-00000008", 8);
        stored.setMerchantId("OTHER-MERCHANT");
        when(sequenceMapper.selectForUpdate(command.businessDate())).thenReturn(sequence);
        when(batchMapper.selectByCreateRequestKeyForUpdate(command.createRequestKey())).thenReturn(stored);

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempotency");
        verify(sequenceMapper, never()).increment(any(), any(Integer.class), any(Long.class));
    }

    private SettlementBatchCreateCommand command(String requestKey) {
        return new SettlementBatchCreateCommand(
                requestKey,
                LocalDate.of(2026, 8, 26),
                "Asia/Shanghai",
                "M1001",
                11L,
                21L,
                "USD",
                2,
                SettlementBatchType.REGULAR,
                null,
                LocalDateTime.of(2026, 8, 25, 0, 0),
                LocalDateTime.of(2026, 8, 26, 0, 0));
    }

    private SettlementBatchDO storedBatch(SettlementBatchCreateCommand command, String batchNo, int sequence) {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setId(100L);
        row.setSettlementBatchNo(batchNo);
        row.setCreateRequestKey(command.createRequestKey());
        row.setBusinessDate(command.businessDate());
        row.setBusinessTimeZone(command.businessTimeZone());
        row.setDailySequence(sequence);
        row.setMerchantId(command.merchantId());
        row.setSettlementProfileId(command.settlementProfileId());
        row.setSettlementAccountId(command.settlementAccountId());
        row.setTargetCurrency(command.targetCurrency());
        row.setTargetCurrencyExponent(command.targetCurrencyExponent());
        row.setBatchType(command.batchType().name());
        row.setOriginalBatchNo(command.originalBatchNo());
        row.setCutoffBeginTime(command.cutoffBeginTime());
        row.setCutoffEndTime(command.cutoffEndTime());
        row.setBatchStatus(SettlementBatchStatus.CREATED.name());
        row.setCandidateCount(0);
        row.setVersion(0L);
        return row;
    }
}
