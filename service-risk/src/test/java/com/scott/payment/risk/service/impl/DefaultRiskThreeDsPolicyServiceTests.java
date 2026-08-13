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
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRiskThreeDsPolicyServiceTests
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 路由后 3DS 策略只读评估测试，验证渠道维度命中且不引入累计限额或频控预占。
 * @status : create
 */
class DefaultRiskThreeDsPolicyServiceTests {

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
