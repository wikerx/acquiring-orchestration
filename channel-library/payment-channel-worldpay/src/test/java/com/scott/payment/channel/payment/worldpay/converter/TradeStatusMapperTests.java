package com.scott.payment.channel.payment.worldpay.converter;

import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Worldpay 原始交易状态到平台渠道状态的稳定映射。
 */
class TradeStatusMapperTests {

    private final TradeStatusMapper mapper = new TradeStatusMapper();

    /**
     * 已提交退款仅表示渠道受理，不能提前标记退款成功。
     */
    @Test
    void shouldKeepSentForRefundPending() {
        assertThat(mapper.map("SENT_FOR_REFUND")).isEqualTo(ChannelTradeStatus.PENDING.getCode());
        assertThat(mapper.map("refundRequested")).isEqualTo(ChannelTradeStatus.PENDING.getCode());
    }

    /**
     * 只有明确退款完成状态才允许映射为退款成功。
     */
    @Test
    void shouldMapCompletedRefundAsRefunded() {
        assertThat(mapper.map("REFUNDED")).isEqualTo(ChannelTradeStatus.REFUNDED.getCode());
    }

    /**
     * 缺失或未识别状态必须保持处理中，等待查询或回调确认。
     */
    @Test
    void shouldKeepMissingAndUnknownStatusProcessing() {
        assertThat(mapper.map(null)).isEqualTo(ChannelTradeStatus.PROCESSING.getCode());
        assertThat(mapper.map("providerAddedState")).isEqualTo(ChannelTradeStatus.PROCESSING.getCode());
    }
}
