package com.scott.payment.clearing.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证清分新事实使用专业中文名称，稳定英文编码和金额规则保持不变。 */
class ClearingItemNameResolverTest {

    @Test
    void shouldResolveProfessionalTransactionAndReserveNames() {
        assertThat(ClearingItemNameResolver.transaction("PRINCIPAL", null, null))
                .isEqualTo("交易本金");
        assertThat(ClearingItemNameResolver.transaction("PLATFORM_FEE", "TRANSACTION_FEE", "NONE"))
                .isEqualTo("交易手续费");
        assertThat(ClearingItemNameResolver.transaction("PLATFORM_FEE", "RISK_FEE", "INTERNAL"))
                .isEqualTo("内风控手续费");
        assertThat(ClearingItemNameResolver.transaction("PLATFORM_FEE", "RISK_FEE", "EXTERNAL"))
                .isEqualTo("外风控手续费");
        assertThat(ClearingItemNameResolver.transaction("PLATFORM_FEE", "RISK_FEE", "THREE_DS"))
                .isEqualTo("3DS手续费");
        assertThat(ClearingItemNameResolver.transaction("FEE_REVERSAL", "TRANSACTION_FEE", "NONE"))
                .isEqualTo("对应手续费冲回");
        assertThat(ClearingItemNameResolver.reserve("HOLD")).isEqualTo("保证金扣留");
        assertThat(ClearingItemNameResolver.reserve("RETURN")).isEqualTo("保证金返还");
        assertThat(ClearingItemNameResolver.reserve("RELEASE")).isEqualTo("保证金释放");
        assertThat(ClearingItemNameResolver.reserve("ADJUSTMENT")).isEqualTo("保证金调整");
    }
}
