package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.mapper.MerchantActivationReadinessMapper;
import com.scott.payment.admin.mapper.MerchantActivationReadinessMapper.ChannelLimitRow;
import com.scott.payment.admin.mapper.MerchantActivationReadinessMapper.MerchantLimitRow;
import com.scott.payment.admin.mapper.MerchantActivationReadinessMapper.ThreeDsRuleRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 商户风险、限额和 3DS 开通门禁测试。
 */
@ExtendWith(MockitoExtension.class)
class MerchantRiskConfigurationReadinessServiceTest {

    @Mock
    private MerchantActivationReadinessMapper readinessMapper;

    private MerchantRiskConfigurationReadinessService service;

    /** 初始化被测服务和默认空配置。 */
    @BeforeEach
    void setUp() {
        service = new MerchantRiskConfigurationReadinessService(readinessMapper);
        when(readinessMapper.selectActiveMerchantLimits(any(), any())).thenReturn(List.of());
        when(readinessMapper.selectActiveChannelLimits(any())).thenReturn(List.of());
        when(readinessMapper.selectActiveThreeDsRules(any(), any())).thenReturn(List.of());
    }

    /** 商户、渠道限额及全量 3DS 兜底均覆盖实际能力时允许开通。 */
    @Test
    void shouldPassWhenRiskControlsCoverRoutedCapability() {
        when(readinessMapper.countEnabledRiskFunction("MTEST0001", "merchantLimit")).thenReturn(1L);
        when(readinessMapper.selectActiveMerchantLimits(any(), any())).thenReturn(List.of(
                merchantLimit("SINGLE_MIN", "USD"),
                merchantLimit("SINGLE_MAX", "USD")));
        when(readinessMapper.selectActiveChannelLimits(any())).thenReturn(List.of(
                channelLimit("SINGLE_MIN"),
                channelLimit("SINGLE_MAX")));
        when(readinessMapper.selectActiveThreeDsRules(any(), any())).thenReturn(List.of(threeDsRule()));

        var issues = service.evaluate("MTEST0001", paymentReadiness(), LocalDateTime.now());

        assertThat(issues).isEmpty();
    }

    /** 缺少限额和银行卡 3DS 兜底时必须一次返回全部稳定阻塞编码。 */
    @Test
    void shouldReportEveryMissingRiskControl() {
        var issues = service.evaluate("MTEST0001", paymentReadiness(), LocalDateTime.now());

        assertThat(issues).containsExactly(
                "RISK_MERCHANT_LIMIT_NOT_ENABLED",
                "RISK_MERCHANT_LIMIT_INCOMPLETE",
                "CHANNEL_LIMIT_INCOMPLETE",
                "THREE_DS_POLICY_NOT_COVERED");
    }

    private MerchantPaymentCapabilityReadinessService.ReadinessResult paymentReadiness() {
        var capability = new MerchantPaymentCapabilityReadinessService.CapabilityKey(
                "PAYMENT", "BANK_CARD", "VISA", "USD");
        var routed = new MerchantPaymentCapabilityReadinessService.RoutedCapability(
                5L, "MPGS", capability);
        return new MerchantPaymentCapabilityReadinessService.ReadinessResult(
                1, 0, List.of(capability), List.of(), List.of(), List.of(), List.of(routed), List.of());
    }

    private MerchantLimitRow merchantLimit(String type, String currency) {
        MerchantLimitRow row = new MerchantLimitRow();
        row.setMerchantScope("MERCHANT");
        row.setMerchantId("MTEST0001");
        row.setLimitType(type);
        row.setCurrency(currency);
        return row;
    }

    private ChannelLimitRow channelLimit(String type) {
        ChannelLimitRow row = new ChannelLimitRow();
        row.setChannelId(5L);
        row.setPaymentMethod("BANK_CARD");
        row.setCardBrand("VISA");
        row.setLimitType(type);
        row.setLimitCurrency("USD");
        return row;
    }

    private ThreeDsRuleRow threeDsRule() {
        ThreeDsRuleRow row = new ThreeDsRuleRow();
        row.setMerchantScope("GLOBAL");
        row.setChannelCode("MPGS");
        row.setPaymentMethod("BANK_CARD");
        row.setCardBrand("ALL");
        row.setAmountMatchType("ALL");
        row.setCurrency("USD");
        row.setRiskCondition("ANY");
        row.setTriggerAction("FORCE_3DS");
        return row;
    }
}
