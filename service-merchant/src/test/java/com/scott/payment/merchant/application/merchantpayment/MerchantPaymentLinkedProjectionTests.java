package com.scott.payment.merchant.application.merchantpayment;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.capability.governance.MerchantPaymentGovernanceService;
import com.scott.payment.capability.governance.MerchantPaymentGovernanceService.Scope;
import com.scott.payment.capability.governance.MerchantPaymentRepository.Actor;
import com.scott.payment.capability.governance.MerchantPaymentScope;
import java.util.Set;
import java.util.function.Function;
import static org.mockito.ArgumentMatchers.eq;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.CurrentFeeResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FeeRuleResponse;
import com.scott.payment.capability.governance.MerchantPaymentModels.AuthorizationCoverage;
import com.scott.payment.capability.governance.MerchantPaymentModels.FeeAuthorizationProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import static com.scott.payment.merchant.application.merchantpayment.MerchantPaymentLinkedProjection.AtomicFeeDimension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MerchantPaymentLinkedProjectionTests {
    private static final Scope SCOPE = new Scope(MerchantPaymentScope.limited(Set.of("200045")),
            new Actor("MERCHANT", 19L, 8L, null));
    private final MerchantPaymentGovernanceService governance = mock(MerchantPaymentGovernanceService.class);
    private final MerchantPaymentReads reads = mock(MerchantPaymentReads.class);
    @AfterEach
    void clearAccount() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void feeCoverageUsesOriginalAtomicPairsAndDoesNotChangeOriginalZeroRate() {
        FeeRuleResponse source = new FeeRuleResponse();
        source.setId(1L);
        source.setPercentageRate(BigDecimal.ZERO);
        source.setPaymentTypes(List.of("BANK_CARD", "APPLE_PAY"));
        source.setPaymentMethods(List.of("MASTERCARD"));
        var groups = MerchantPaymentLinkedProjection.groupDimensions(List.of(
                new AtomicFeeDimension("1", "G", "BANK_CARD", "MASTERCARD"),
                new AtomicFeeDimension("2", "G", "APPLE_PAY", "ALL"),
                new AtomicFeeDimension("3", null, "BANK_CARD", "VISA")));
        var mastercard = new AuthorizationCoverage("CARD_BRAND:MASTERCARD", "Mastercard", "ACTIVE");
        var visa = new AuthorizationCoverage("CARD_BRAND:VISA", "Visa", "ACTIVE");
        var wallet = new AuthorizationCoverage("METHOD:APPLE_PAY", "Apple Pay", "NOT_AUTHORIZED");

        var response = MerchantPaymentLinkedProjection.decorate(source, groups.get("1"), List.of(visa, mastercard, wallet));

        assertThat(response.getPaymentMethodCoverage()).containsExactly(mastercard, wallet);
        assertThat(response.getPercentageRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(source.getPaymentMethodCoverage()).isNull();
        assertThat(groups).containsKeys("1", "3").doesNotContainKey("2");
    }

    @Test
    void preExpandMissingModeColumnsUseHistoricCompatibility() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(reads.read(eq("200045"), any())).thenThrow(
                new BadSqlGrammarException("mode", "SQL", new SQLException("unknown column", "42S22", 1054)));

        var projection = new MerchantPaymentLinkedProjection(jdbc, governance, reads);
        var summary = projection.overview("200045");
        var fee = projection.currentFee("200045", fee());

        assertThat(summary.assessmentStatus()).isEqualTo("HISTORY_PENDING");
        assertThat(summary.authorizedCount()).isNull();
        assertThat(fee.getAuthorizationAssessmentStatus()).isEqualTo("HISTORY_PENDING");
        assertThat(fee.getRules().get(0).getPaymentMethodCoverage()).isNull();
        verifyNoInteractions(governance);
    }

    @Test
    @SuppressWarnings("unchecked")
    void displaysFormalCoverageForCurrentRuleWhileKeepingHistoryPending() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        useMerchantAccount();
        useLocalReads();
        when(jdbc.query(contains("FROM fee_plan p"), anyMap(), any(RowMapper.class))).thenReturn(List.of(
                new AtomicFeeDimension("1", "G", "BANK_CARD", "ALL")));
        var mastercard = new AuthorizationCoverage("CARD_BRAND:MASTERCARD", "Mastercard", "ACTIVE");
        var visa = new AuthorizationCoverage("CARD_BRAND:VISA", "Visa", "NOT_AUTHORIZED");
        var unrelatedWallet = new AuthorizationCoverage("METHOD:APPLE_PAY", "Apple Pay", "ACTIVE");
        when(governance.feeAuthorizations(SCOPE, "200045"))
                .thenReturn(new FeeAuthorizationProjection("HISTORY_PENDING", List.of(mastercard, visa, unrelatedWallet)));
        var source = fee();

        var response = new MerchantPaymentLinkedProjection(jdbc, governance, reads).currentFee("200045", source);

        assertThat(response.getAuthorizationAssessmentStatus()).isEqualTo("HISTORY_PENDING");
        assertThat(response.getVersionNo()).isEqualTo(5);
        assertThat(response.getRules().get(0).getPaymentMethodCoverage()).containsExactly(mastercard, visa);
        assertThat(source.getRules().get(0).getPaymentMethodCoverage()).isNull();
    }

    @Test
    void keepsPendingHistoryWithoutCoverageAndReportsUnavailableWhenLocalQueryFails() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        useMerchantAccount();
        useLocalReads();
        when(governance.feeAuthorizations(SCOPE, "200045"))
                .thenReturn(new FeeAuthorizationProjection("HISTORY_PENDING", List.of()))
                .thenThrow(new IllegalStateException("test owner unavailable"));
        var projection = new MerchantPaymentLinkedProjection(jdbc, governance, reads);

        var existing = projection.currentFee("200045", fee());
        var unavailable = projection.currentFee("200045", fee());

        assertThat(existing.getAuthorizationAssessmentStatus()).isEqualTo("HISTORY_PENDING");
        assertThat(unavailable.getAuthorizationAssessmentStatus()).isEqualTo("UNAVAILABLE");
        assertThat(existing.getRules().get(0).getPaymentMethodCoverage()).isNull();
        assertThat(unavailable.getRules().get(0).getPaymentMethodCoverage()).isNull();
    }

    private void useLocalReads() {
        when(reads.read(eq("200045"), any())).thenAnswer(call -> {
            Function<Scope, ?> read = call.getArgument(1);
            return read.apply(SCOPE);
        });
    }

    private static CurrentFeeResponse fee() {
        var rule = new FeeRuleResponse();
        rule.setId(1L);
        var fee = new CurrentFeeResponse();
        fee.setVersionNo(5);
        fee.setRules(List.of(rule));
        return fee;
    }

    private static void useMerchantAccount() {
        var account = new InternalAuthAccount();
        account.setAppCode("MERCHANT");
        account.setMerchantId("200045");
        account.setAccountId(19L);
        account.setUserId(8L);
        InternalAuthContextHolder.set(account);
    }
}
