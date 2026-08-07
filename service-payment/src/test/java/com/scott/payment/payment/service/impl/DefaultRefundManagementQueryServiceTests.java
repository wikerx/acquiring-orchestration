package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.mapper.RefundManagementMapper;
import com.scott.payment.payment.mapper.TransactionOrderMapper;
import com.scott.payment.payment.service.TransactionQueryService;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundQuery;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRefundManagementQueryServiceTests
 * @date : 2026-08-06
 * @description : 退款管理查询服务测试，锁定页面时区到交易库存储时区的转换契约。
 * @status : create
 */
class DefaultRefundManagementQueryServiceTests {

    /** 页面查询时间必须按所选时区换算为交易库固定时区。 */
    @Test
    void shouldConvertQueryRangeToTransactionStorageTimezone() {
        RefundManagementMapper refundMapper = mock(RefundManagementMapper.class);
        DefaultRefundManagementQueryService service = new DefaultRefundManagementQueryService(
                refundMapper,
                mock(TransactionOrderMapper.class),
                mock(TransactionQueryService.class),
                new TransactionShardingProperties());
        RefundQuery query = new RefundQuery();
        query.setBeginTime(LocalDateTime.of(2026, 8, 6, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 8, 6, 1, 0));
        query.setQueryTimeZone("UTC");

        service.search(query);

        ArgumentCaptor<LocalDateTime> beginCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refundMapper).count(eq(query), beginCaptor.capture(), endCaptor.capture());
        assertThat(beginCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 6, 8, 0));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 6, 9, 0, 0, 1_000_000));
    }

    /** 非法页面时区必须返回参数错误，不得进入退款查询。 */
    @Test
    void shouldRejectInvalidQueryTimezone() {
        RefundManagementMapper refundMapper = mock(RefundManagementMapper.class);
        DefaultRefundManagementQueryService service = new DefaultRefundManagementQueryService(
                refundMapper,
                mock(TransactionOrderMapper.class),
                mock(TransactionQueryService.class),
                new TransactionShardingProperties());
        RefundQuery query = new RefundQuery();
        query.setQueryTimeZone("UTC+25:00");

        assertThatThrownBy(() -> service.search(query))
                .hasMessageContaining("queryTimeZone is invalid");
    }

    /** 历史空范围必须按原始本金补齐 FULL/PARTIAL，撤销固定补齐 VOID。 */
    @Test
    void shouldEnrichMissingHistoricalRefundScopesFromOriginalPrincipal() {
        RefundManagementMapper refundMapper = mock(RefundManagementMapper.class);
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        DefaultRefundManagementQueryService service = new DefaultRefundManagementQueryService(
                refundMapper,
                orderMapper,
                mock(TransactionQueryService.class),
                new TransactionShardingProperties());
        RefundRecord full = refundRecord("OP-FULL", "REFUND", "12.34");
        RefundRecord partial = refundRecord("OP-PARTIAL", "REFUND", "5.00");
        RefundRecord voidRecord = refundRecord("OP-VOID", "VOID", "12.34");
        when(refundMapper.count(any(), any(), any())).thenReturn(3L);
        when(refundMapper.selectPage(any(), any(), any(), any(Long.class), any(Long.class)))
                .thenReturn(List.of(full, partial, voidRecord));
        when(orderMapper.selectByOperationIds(anyList(), any(), any()))
                .thenReturn(List.of(order("OP-FULL", "12.34"), order("OP-PARTIAL", "12.34")));
        RefundQuery query = new RefundQuery();
        query.setBeginTime(LocalDateTime.of(2026, 8, 6, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 8, 6, 23, 59));

        service.search(query);

        assertThat(full.getRefundScope()).isEqualTo("FULL");
        assertThat(partial.getRefundScope()).isEqualTo("PARTIAL");
        assertThat(voidRecord.getRefundScope()).isEqualTo("VOID");
    }

    private RefundRecord refundRecord(String operationId, String type, String amount) {
        RefundRecord record = new RefundRecord();
        record.setOperationId(operationId);
        record.setTransactionType(type);
        record.setTransactionAmount(new BigDecimal(amount));
        return record;
    }

    private TransactionOrderDO order(String operationId, String amount) {
        TransactionOrderDO order = new TransactionOrderDO();
        order.setOperationId(operationId);
        order.setTransactionAmount(new BigDecimal(amount));
        order.setTransactionDateTime(LocalDateTime.of(2026, 8, 6, 0, 0));
        return order;
    }
}
