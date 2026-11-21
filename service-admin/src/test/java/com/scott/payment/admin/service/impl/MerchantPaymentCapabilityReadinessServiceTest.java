package com.scott.payment.admin.service.impl;

import com.scott.payment.capability.governance.MerchantPaymentCapabilityReadinessService;
import com.scott.payment.capability.governance.MerchantPaymentReadinessFacts;
import com.scott.payment.capability.governance.MerchantPaymentReadinessFacts.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 商户实际支付能力门禁测试，覆盖路由唯一性和费用规则覆盖。
 */
@ExtendWith(MockitoExtension.class)
class MerchantPaymentCapabilityReadinessServiceTest {

    @Mock
    private MerchantPaymentReadinessFacts facts;

    private MerchantPaymentCapabilityReadinessService service;

    @BeforeEach
    void setUp() {
        service = new MerchantPaymentCapabilityReadinessService(facts);
        useSingleRoute();
        lenient().when(facts.feeRules(8L)).thenReturn(List.of(
                feeRule("TRANSACTION_FEE", "PAYMENT"),
                feeRule("REFUND_FEE", "REFUND")));
    }

    /** 完整的支付和退款能力均有唯一路由及费率时允许开通。 */
    @Test
    void shouldPassWhenRoutesAndFeesCoverEveryChargeableCapability() {
        var result = service.evaluate("MTEST0001", 8L, LocalDateTime.now());

        assertThat(result.issueCodes()).isEmpty();
        assertThat(result.capabilities()).extracting(
                MerchantPaymentCapabilityReadinessService.CapabilityKey::transactionType)
                .containsExactly("PAYMENT", "REFUND");
        assertThat(result.activeBindingCount()).isEqualTo(1);
    }

    /** 退款能力没有退款费规则时必须报告覆盖缺口。 */
    @Test
    void shouldRejectMissingFeeCoverage() {
        when(facts.feeRules(8L)).thenReturn(List.of(
                feeRule("TRANSACTION_FEE", "PAYMENT")));

        var result = service.evaluate("MTEST0001", 8L, LocalDateTime.now());

        assertThat(result.issueCodes()).containsExactly("FEE_CAPABILITY_NOT_COVERED");
        assertThat(result.feeGaps()).extracting(
                MerchantPaymentCapabilityReadinessService.CapabilityKey::transactionType)
                .containsExactly("REFUND");
    }

    @Test
    void initialFeeCoverageUsesOriginalRulesWithoutRequiringMid() {
        var wildcardCard = feeRule("TRANSACTION_FEE", "ALL");
        wildcardCard.setPaymentMethod("ALL");
        var walletRefundOnly = feeRule("REFUND_FEE", "REFUND");
        walletRefundOnly.setPaymentType("APPLE_PAY");
        walletRefundOnly.setPaymentMethod("ALL");
        var walletCaptureOnly = feeRule("TRANSACTION_FEE", "CAPTURE");
        walletCaptureOnly.setPaymentType("GOOGLE_PAY");
        walletCaptureOnly.setPaymentMethod("ALL");
        var riskOnly = feeRule("TRANSACTION_FEE", "PAYMENT");
        riskOnly.setPaymentType("ACHPAY");
        riskOnly.setPaymentMethod("ALL");
        riskOnly.setRiskServiceType("THREEDS");
        when(facts.feeRules(8L)).thenReturn(List.of(wildcardCard, walletRefundOnly, walletCaptureOnly, riskOnly));

        var coverage = service.initialFeeCoverage(8L);

        assertThat(coverage.test("BANK_CARD", "VISA")).isTrue();
        assertThat(coverage.test("BANK_CARD", "MASTERCARD")).isTrue();
        assertThat(coverage.test("APPLE_PAY", "ALL")).isFalse();
        assertThat(coverage.test("GOOGLE_PAY", "ALL")).isFalse();
        assertThat(coverage.test("ACHPAY", "ALL")).isFalse();
        verify(facts).feeRules(8L);
        verifyNoMoreInteractions(facts);
    }

    /** 同一能力同时命中通用和精确费率时必须拒绝开通，避免清分费用规则出现歧义。 */
    @Test
    void shouldRejectAmbiguousFeeCoverage() {
        when(facts.feeRules(8L)).thenReturn(List.of(
                feeRule("TRANSACTION_FEE", "PAYMENT"),
                feeRule("TRANSACTION_FEE", "ALL"),
                feeRule("REFUND_FEE", "REFUND")));

        var result = service.evaluate("MTEST0001", 8L, LocalDateTime.now());

        assertThat(result.issueCodes()).containsExactly("FEE_CAPABILITY_AMBIGUOUS");
        assertThat(result.feeAmbiguities()).extracting(
                MerchantPaymentCapabilityReadinessService.CapabilityKey::transactionType)
                .containsExactly("PAYMENT");
    }

    /** 两个 MID 对同一能力使用相同最高优先级时必须与支付运行时一致地失败关闭。 */
    @Test
    void shouldRejectEqualPriorityRouteConflict() {
        MerchantChannelMidBindingDO secondBinding = binding(2L, 11L);
        when(facts.activeBindings(any(), any())).thenReturn(List.of(binding(1L, 10L), secondBinding));
        ChannelMidConfigDO secondMid = mid(11L);
        when(facts.mids(any())).thenReturn(List.of(mid(10L), secondMid));

        var result = service.evaluate("MTEST0001", 8L, LocalDateTime.now());

        assertThat(result.issueCodes()).containsExactly("CHANNEL_ROUTE_CONFLICT");
        assertThat(result.routeConflicts()).hasSize(2);
    }

    /** 生效绑定引用停用 MID 时既要标识不可用，也不能伪造任何支付能力。 */
    @Test
    void shouldRejectUnavailableMid() {
        ChannelMidConfigDO disabled = mid(10L);
        disabled.setMidStatus(0);
        when(facts.mids(any())).thenReturn(List.of(disabled));

        var result = service.evaluate("MTEST0001", 8L, LocalDateTime.now());

        assertThat(result.issueCodes())
                .containsExactly("CHANNEL_MID_UNAVAILABLE", "CHANNEL_PAYMENT_CAPABILITY_EMPTY");
        assertThat(result.capabilities()).isEmpty();
    }

    @Test
    void currentDetailCountsEachBusinessAndUsesItsOwnActions() {
        useMixedBusinessRoutes();

        var result = service.evaluateCurrent("200045", LocalDateTime.now(), () -> 8L);

        assertThat(result.acquiring().activeBindingCount()).isEqualTo(1);
        assertThat(result.acquiring().unavailableBindingCount()).isZero();
        assertThat(result.acquiring().capabilities())
                .extracting(MerchantPaymentCapabilityReadinessService.CapabilityKey::transactionType)
                .containsExactly("PAYMENT", "REFUND");
        assertThat(result.payout().activeBindingCount()).isEqualTo(1);
        assertThat(result.payout().unavailableBindingCount()).isZero();
        assertThat(result.payout().capabilities())
                .extracting(MerchantPaymentCapabilityReadinessService.CapabilityKey::transactionType)
                .containsExactly("PAYOUT");
    }

    @Test
    void currentDetailKeepsPayoutWhenAcquiringReadFails() {
        useMixedBusinessRoutes();

        var result = service.evaluateCurrent("200045", LocalDateTime.now(),
                () -> { throw new IllegalStateException("fee source unavailable"); });

        assertThat(result.acquiring()).isNull();
        assertThat(result.payout().activeBindingCount()).isEqualTo(1);
        assertThat(result.payout().capabilities()).hasSize(1);
    }

    @Test
    void currentDetailDistinguishesEmptyBusinessFromDisabledMid() {
        ChannelMidConfigDO disabled = mid(10L);
        disabled.setMidStatus(0);
        when(facts.mids(any())).thenReturn(List.of(disabled));

        var result = service.evaluateCurrent("200045", LocalDateTime.now(), () -> 8L);

        assertThat(result.acquiring().activeBindingCount()).isEqualTo(1);
        assertThat(result.acquiring().unavailableBindingCount()).isEqualTo(1);
        assertThat(result.acquiring().capabilities()).isEmpty();
        assertThat(result.payout().activeBindingCount()).isZero();
        assertThat(result.payout().unavailableBindingCount()).isZero();
    }

    @Test
    void currentDetailDoesNotGuessBusinessForMissingMid() {
        when(facts.mids(any())).thenReturn(List.of());

        var result = service.evaluateCurrent("200045", LocalDateTime.now(), () -> 8L);

        assertThat(result.acquiring()).isNull();
        assertThat(result.payout()).isNull();
    }

    private void useMixedBusinessRoutes() {
        ChannelMidConfigDO payoutMid = mid(11L);
        payoutMid.setBusinessType("PAYOUT");
        payoutMid.setTransactionTypeScope("PAYOUT,PAYMENT");
        ChannelInfoDO channel = channel();
        channel.setSupportPayout(1);
        ChannelPaymentCapabilityDO payoutCapability = capability();
        payoutCapability.setTransactionType("PAYOUT,PAYMENT");
        when(facts.activeBindings(any(), any())).thenReturn(List.of(binding(1L, 10L), binding(2L, 11L)));
        when(facts.mids(any())).thenReturn(List.of(mid(10L), payoutMid));
        when(facts.channels(any())).thenReturn(List.of(channel));
        when(facts.capabilities(any(), any())).thenAnswer(invocation -> {
            return "PAYOUT".equals(invocation.getArgument(1))
                    ? List.of(payoutCapability) : List.of(capability());
        });
    }

    /** 配置单条银行卡能力和对应的启用币种、卡品牌。 */
    private void useSingleRoute() {
        lenient().when(facts.activeBindings(any(), any())).thenReturn(List.of(binding(1L, 10L)));
        lenient().when(facts.mids(any())).thenReturn(List.of(mid(10L)));
        lenient().when(facts.channels(any())).thenReturn(List.of(channel()));
        lenient().when(facts.capabilities(any(), any())).thenReturn(List.of(capability()));
        lenient().when(facts.currencies(any())).thenReturn(List.of(currency()));
        lenient().when(facts.cardBrands(any())).thenReturn(List.of(cardBrand()));
    }

    private MerchantChannelMidBindingDO binding(Long id, Long midId) {
        MerchantChannelMidBindingDO row = new MerchantChannelMidBindingDO();
        row.setId(id);
        row.setMidConfigId(midId);
        return row;
    }

    private ChannelMidConfigDO mid(Long id) {
        ChannelMidConfigDO row = new ChannelMidConfigDO();
        row.setId(id);
        row.setChannelId(5L);
        row.setBusinessType("ACQUIRING");
        row.setPaymentMethodScope("BANK_CARD");
        row.setCardBrandScope("VISA");
        row.setTransactionTypeScope("PAYMENT,REFUND");
        row.setCurrencyScope("USD");
        row.setMidStatus(1);
        return row;
    }

    private ChannelInfoDO channel() {
        ChannelInfoDO row = new ChannelInfoDO();
        row.setId(5L);
        row.setChannelCode("TEST_CHANNEL");
        row.setChannelStatus(1);
        row.setSupportAcquiring(1);
        return row;
    }

    private ChannelPaymentCapabilityDO capability() {
        ChannelPaymentCapabilityDO row = new ChannelPaymentCapabilityDO();
        row.setId(16L);
        row.setChannelId(5L);
        row.setPaymentMethod("BANK_CARD");
        row.setTransactionType("PAYMENT,REFUND");
        row.setSupportIncrementalAuthorization(0);
        row.setSortOrder(10);
        return row;
    }

    private ChannelCapabilityCurrencyDO currency() {
        ChannelCapabilityCurrencyDO row = new ChannelCapabilityCurrencyDO();
        row.setCapabilityId(16L);
        row.setCurrencyCode("USD");
        return row;
    }

    private ChannelCapabilityCardBrandDO cardBrand() {
        ChannelCapabilityCardBrandDO row = new ChannelCapabilityCardBrandDO();
        row.setCapabilityId(16L);
        row.setCardBrand("VISA");
        return row;
    }

    private FeeRuleDO feeRule(String category, String transactionType) {
        FeeRuleDO row = new FeeRuleDO();
        row.setFeeCategory(category);
        row.setTransactionType(transactionType);
        row.setPaymentType("BANK_CARD");
        row.setPaymentMethod("VISA");
        row.setRiskServiceType("NONE");
        return row;
    }
}
