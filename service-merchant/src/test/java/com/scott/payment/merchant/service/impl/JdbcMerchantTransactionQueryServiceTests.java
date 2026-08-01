package com.scott.payment.merchant.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户交易查询展示规则测试。
 */
class JdbcMerchantTransactionQueryServiceTests {

    @Test
    void shouldKeepRequestedAmountForRejectedAuthorization() {
        BigDecimal amount = JdbcMerchantTransactionQueryService.resolveCurrentAmount(
                "AUTHORIZATION",
                new BigDecimal("28.50"),
                BigDecimal.ZERO
        );

        assertThat(amount).isEqualByComparingTo("28.50");
    }

    @Test
    void shouldExposePersistedRiskBlockedMessageToMerchant() {
        String message = JdbcMerchantTransactionQueryService.resolveMerchantResponseMessage(
                "FAILED",
                "Risk blocked"
        );

        assertThat(message).isEqualTo("Risk blocked");
    }
}
