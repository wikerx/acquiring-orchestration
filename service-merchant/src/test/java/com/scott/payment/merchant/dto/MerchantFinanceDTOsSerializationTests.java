package com.scott.payment.merchant.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证商户资金响应使用精确十进制字符串，避免浏览器解析时丢失金额精度。
 */
class MerchantFinanceDTOsSerializationTests {

    private static final BigDecimal LARGE_AMOUNT = new BigDecimal("123456789012345678.90");
    private static final BigDecimal NEGATIVE_AMOUNT = new BigDecimal("-123456789012345678.90");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 账户余额和流水金额必须以字符串形式交付商户前端。
     */
    @Test
    void shouldSerializeFundResponseAmountsAsStrings() {
        MerchantFinanceDTOs.CurrencyBalanceResponse balance = new MerchantFinanceDTOs.CurrencyBalanceResponse();
        balance.setAmount(LARGE_AMOUNT);

        MerchantFinanceDTOs.FundAccountResponse account = new MerchantFinanceDTOs.FundAccountResponse();
        account.setAvailableBalance(NEGATIVE_AMOUNT);

        MerchantFinanceDTOs.FundLedgerResponse ledger = new MerchantFinanceDTOs.FundLedgerResponse();
        ledger.setAmount(LARGE_AMOUNT);
        ledger.setBalanceBefore(NEGATIVE_AMOUNT);
        ledger.setBalanceAfter(LARGE_AMOUNT);

        JsonNode json = objectMapper.valueToTree(Map.of(
                "balance", balance,
                "account", account,
                "ledger", ledger));

        assertTextAmount(json.path("balance"), "amount", LARGE_AMOUNT.toPlainString());
        assertTextAmount(json.path("account"), "availableBalance", NEGATIVE_AMOUNT.toPlainString());
        assertTextAmount(json.path("ledger"), "amount", LARGE_AMOUNT.toPlainString());
        assertTextAmount(json.path("ledger"), "balanceBefore", NEGATIVE_AMOUNT.toPlainString());
        assertTextAmount(json.path("ledger"), "balanceAfter", LARGE_AMOUNT.toPlainString());
    }

    /**
     * 同时校验 JSON 类型和值，防止精确金额被序列化成数值节点。
     */
    private void assertTextAmount(JsonNode parent, String fieldName, String expected) {
        JsonNode amount = parent.path(fieldName);
        assertThat(amount.isTextual()).isTrue();
        assertThat(amount.textValue()).isEqualTo(expected);
    }
}
