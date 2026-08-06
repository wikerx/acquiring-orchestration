package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.payment.mapper.RefundManagementMapper;
import com.scott.payment.payment.mapper.TransactionOrderMapper;
import com.scott.payment.payment.service.TransactionQueryService;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
