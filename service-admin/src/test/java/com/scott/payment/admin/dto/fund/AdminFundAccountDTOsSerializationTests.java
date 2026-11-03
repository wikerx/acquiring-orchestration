package com.scott.payment.admin.dto.fund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证管理端资金响应不会把精确十进制金额降级为 JSON 浮点数。
 */
class AdminFundAccountDTOsSerializationTests {

    private static final BigDecimal LARGE_AMOUNT = new BigDecimal("123456789012345678.90");
    private static final BigDecimal NEGATIVE_AMOUNT = new BigDecimal("-123456789012345678.90");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 账户、流水、充值和扣款的全部响应金额必须保持十进制字符串。
     */
    @Test
    void shouldSerializeFundResponseAmountsAsStrings() {
        AdminFundAccountDTOs.CurrencyBalanceResponse balance = new AdminFundAccountDTOs.CurrencyBalanceResponse();
        balance.setAmount(LARGE_AMOUNT);

        AdminFundAccountDTOs.FundAccountResponse account = new AdminFundAccountDTOs.FundAccountResponse();
        account.setAvailableBalance(NEGATIVE_AMOUNT);

        AdminFundAccountDTOs.FundLedgerResponse ledger = new AdminFundAccountDTOs.FundLedgerResponse();
        ledger.setAmount(LARGE_AMOUNT);
        ledger.setBalanceBefore(NEGATIVE_AMOUNT);
        ledger.setBalanceAfter(LARGE_AMOUNT);

        AdminFundAccountDTOs.FundRechargeResponse recharge = new AdminFundAccountDTOs.FundRechargeResponse();
        recharge.setAmount(LARGE_AMOUNT);

        AdminFundAccountDTOs.FundDeductionResponse deduction = new AdminFundAccountDTOs.FundDeductionResponse();
        deduction.setAmount(LARGE_AMOUNT);

        JsonNode json = objectMapper.valueToTree(Map.of(
                "balance", balance,
                "account", account,
                "ledger", ledger,
                "recharge", recharge,
                "deduction", deduction));

        assertTextAmount(json.path("balance"), "amount", LARGE_AMOUNT.toPlainString());
        assertTextAmount(json.path("account"), "availableBalance", NEGATIVE_AMOUNT.toPlainString());
        assertTextAmount(json.path("ledger"), "amount", LARGE_AMOUNT.toPlainString());
        assertTextAmount(json.path("ledger"), "balanceBefore", NEGATIVE_AMOUNT.toPlainString());
        assertTextAmount(json.path("ledger"), "balanceAfter", LARGE_AMOUNT.toPlainString());
        assertTextAmount(json.path("recharge"), "amount", LARGE_AMOUNT.toPlainString());
        assertTextAmount(json.path("deduction"), "amount", LARGE_AMOUNT.toPlainString());
    }

    /**
     * 同时校验 JSON 类型和值，避免仅比较文本时漏掉数值节点转换。
     */
    private void assertTextAmount(JsonNode parent, String fieldName, String expected) {
        JsonNode amount = parent.path(fieldName);
        assertThat(amount.isTextual()).isTrue();
        assertThat(amount.textValue()).isEqualTo(expected);
    }
}
