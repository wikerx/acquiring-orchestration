package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordDetailResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordSearchRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordSearchResponse;
import com.scott.payment.clearing.entity.ClearingReserveDetailDO;
import com.scott.payment.clearing.entity.ClearingTransactionDetailDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.mapper.ClearingReserveMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionDetailMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingQueryServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证清分详情精确分片查询、交易与保证金明细隔离及季度范围游标分页
 * @status : create
 */
class DefaultClearingQueryServiceTest {

    @Test
    void detailUsesExactShardTimeAndReturnsSeparateTransactionAndReserveLines() {
        ClearingTransactionFinanceStateMapper financeStateMapper = mock(ClearingTransactionFinanceStateMapper.class);
        ClearingTransactionDetailMapper detailMapper = mock(ClearingTransactionDetailMapper.class);
        ClearingReserveMapper reserveMapper = mock(ClearingReserveMapper.class);
        DefaultClearingQueryService service = new DefaultClearingQueryService(
                financeStateMapper, detailMapper, reserveMapper);
        LocalDateTime shardTime = LocalDateTime.of(2026, 8, 26, 10, 30);

        ClearingTransactionFinanceStateDO state = new ClearingTransactionFinanceStateDO();
        state.setFinanceStateId("FS1001");
        state.setTransactionId("TX1001");
        state.setOperationId("OP1001");
        state.setMerchantId("M1001");
        state.setClearingStatus("CLEARED");
        state.setClearingRevision(2);
        state.setTransactionDateTime(shardTime);
        ClearingTransactionDetailDO fee = new ClearingTransactionDetailDO();
        fee.setClearingDetailNo("CD1001");
        ClearingReserveDetailDO reserve = new ClearingReserveDetailDO();
        reserve.setReserveClearingDetailNo("RD1001");

        when(financeStateMapper.selectByTransaction("TX1001", shardTime)).thenReturn(state);
        when(detailMapper.selectActiveRevision("TX1001", shardTime, 2)).thenReturn(List.of(fee));
        when(reserveMapper.selectActiveRevision("TX1001", shardTime, 2)).thenReturn(List.of(reserve));

        ClearingRecordDetailResponse result = service.detail("TX1001", shardTime);

        assertThat(result.getSummary().getFinanceStateId()).isEqualTo("FS1001");
        assertThat(result.getTransactionDetails()).extracting("clearingDetailNo").containsExactly("CD1001");
        assertThat(result.getReserveDetails()).extracting("reserveClearingDetailNo").containsExactly("RD1001");
        verify(financeStateMapper).selectByTransaction("TX1001", shardTime);
        verify(detailMapper).selectActiveRevision("TX1001", shardTime, 2);
        verify(reserveMapper).selectActiveRevision("TX1001", shardTime, 2);
    }

    @Test
    void searchUsesHalfOpenQuarterRangeAndKeysetCursor() {
        ClearingTransactionFinanceStateMapper financeStateMapper = mock(ClearingTransactionFinanceStateMapper.class);
        DefaultClearingQueryService service = new DefaultClearingQueryService(
                financeStateMapper, mock(ClearingTransactionDetailMapper.class), mock(ClearingReserveMapper.class));
        LocalDateTime begin = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime cursorTime = LocalDateTime.of(2026, 8, 1, 12, 0);
        ClearingRecordSearchRequest request = new ClearingRecordSearchRequest();
        request.setMerchantId("M1001");
        request.setClearingStatus("FAILED");
        request.setBeginTime(begin);
        request.setEndTime(end);
        request.setCursorTransactionDateTime(cursorTime);
        request.setCursorId(100L);
        request.setLimit(2);

        ClearingTransactionFinanceStateDO first = state(101L, "TX101", cursorTime.plusSeconds(1));
        ClearingTransactionFinanceStateDO second = state(102L, "TX102", cursorTime.plusSeconds(2));
        ClearingTransactionFinanceStateDO lookAhead = state(103L, "TX103", cursorTime.plusSeconds(3));
        when(financeStateMapper.selectForManagementSearch(
                "M1001", null, "FAILED", begin, end, cursorTime, 100L, 3))
                .thenReturn(List.of(first, second, lookAhead));

        ClearingRecordSearchResponse result = service.search(request);

        assertThat(result.getRecords()).extracting("transactionId").containsExactly("TX101", "TX102");
        assertThat(result.isHasMore()).isTrue();
        assertThat(result.getNextCursorTransactionDateTime()).isEqualTo(second.getTransactionDateTime());
        assertThat(result.getNextCursorId()).isEqualTo(102L);
        verify(financeStateMapper).selectForManagementSearch(
                "M1001", null, "FAILED", begin, end, cursorTime, 100L, 3);
    }

    private ClearingTransactionFinanceStateDO state(Long id, String transactionId, LocalDateTime shardTime) {
        ClearingTransactionFinanceStateDO state = new ClearingTransactionFinanceStateDO();
        state.setId(id);
        state.setFinanceStateId("FS" + id);
        state.setTransactionId(transactionId);
        state.setOperationId("OP" + id);
        state.setMerchantId("M1001");
        state.setClearingStatus("FAILED");
        state.setClearingRevision(1);
        state.setTransactionDateTime(shardTime);
        return state;
    }
}
