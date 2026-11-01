package com.scott.payment.risk.service.impl;

import com.scott.payment.risk.api.internal.dto.RiskThreeDsPolicyRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskThreeDsPolicyResultDTO;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.repository.RiskListRuntimeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 路由后 3DS 策略只读评估测试，验证渠道维度命中且不引入累计限额或频控预占。
 */
class DefaultRiskThreeDsPolicyServiceTests {

    /**
     * 验证路由至 MPGS 且规则强制认证时返回 3DS 必需结论。
     */
    @Test
    void shouldRequireThreeDsWhenRoutedMpgsRuleForcesAuthentication() {
        RiskListRuntimeRepository repository = mock(RiskListRuntimeRepository.class);
        RiskListMatch rule = new RiskListMatch();
        rule.setRuleId(30L);
        rule.setHitElement("FORCE_3DS");
        rule.setDecisionAction("REQUIRE_3DS");
        rule.setDecisionReason("30 USD and above requires MPGS 3DS");
        RiskThreeDsPolicyRequestDTO request = request("MPGS", "31.00");
        when(repository.findThreeDsRule(
                "M202607290001", "MPGS", "BANK_CARD", "VISA",
                new BigDecimal("31.00"), "USD", "LOW"))
                .thenReturn(Optional.of(rule));
        DefaultRiskThreeDsPolicyService service = new DefaultRiskThreeDsPolicyService(repository);

        RiskThreeDsPolicyResultDTO result = service.evaluate(request);

        assertThat(result.isRequired()).isTrue();
        assertThat(result.getAction()).isEqualTo("FORCE_3DS");
        assertThat(result.getRuleId()).isEqualTo(30L);
        verify(repository).findThreeDsRule(
                "M202607290001", "MPGS", "BANK_CARD", "VISA",
                new BigDecimal("31.00"), "USD", "LOW");
    }

    /**
     * 构造包含测试所需商户、交易和支付字段的风控评估请求。
     */
    private RiskThreeDsPolicyRequestDTO request(String channelCode, String amount) {
        RiskThreeDsPolicyRequestDTO request = new RiskThreeDsPolicyRequestDTO();
        request.setMerchantId("M202607290001");
        request.setChannelCode(channelCode);
        request.setPaymentMethod("BANK_CARD");
        request.setCardBrand("VISA");
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("USD");
        request.setCurrentRiskLevel("LOW");
        return request;
    }
}
