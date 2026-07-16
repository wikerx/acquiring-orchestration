package com.scott.payment.component.core.util.identity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentOrderNoGeneratorTests
 * @date : 2026-07-15 15:12
 * @email : scott_x@163.com
 * @description : 支付订单号生成器测试，验证商户可见平台交易 ID 不带业务前缀，内部订单号仍保留业务前缀。
 * @status : create
 */
class PaymentOrderNoGeneratorTests {

    /**
     * 平台交易 ID 应只包含可解析时间片和序列，不再携带 TX 等内部业务前缀。
     */
    @Test
    void shouldGeneratePrefixlessTransactionId() {
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(LocalDateTime.of(2026, 7, 15, 15, 12, 13, 456_000_000));

        assertThat(transactionId).startsWith("20260715151213456");
        assertThat(transactionId).doesNotStartWith("TX");
        assertThat(transactionId).hasSize(21);
    }

    /**
     * 内部业务编号仍按调用方指定前缀生成，用于 operation_id、事件号和后台动作幂等号等内部场景。
     */
    @Test
    void shouldKeepBusinessPrefixForInternalOrderNo() {
        String orderNo = PaymentOrderNoGenerator.nextOrderNo("OP", LocalDateTime.of(2026, 7, 15, 15, 12, 13, 456_000_000));

        assertThat(orderNo).startsWith("OP20260715151213456");
        assertThat(orderNo).hasSize(23);
    }
}
