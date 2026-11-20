package com.scott.payment.merchant.application.merchantpayment;

import com.scott.payment.merchant.client.admin.MerchantPaymentGovernanceClient;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FeeRuleResponse;
import com.scott.payment.merchant.dto.merchantpayment.MerchantPaymentModels.AuthorizationCoverage;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MerchantPaymentLinkedProjectionTests {

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
    @SuppressWarnings("unchecked")
    void preExpandMissingModeColumnsUseHistoricCompatibilityWithoutCallingAdmin() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        MerchantPaymentGovernanceClient admin = mock(MerchantPaymentGovernanceClient.class);
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenThrow(
                new BadSqlGrammarException("mode", "SQL", new SQLException("unknown column", "42S22", 1054)));

        var summary = new MerchantPaymentLinkedProjection(jdbc, admin).overview("200045");

        assertThat(summary.assessmentStatus()).isEqualTo("HISTORY_PENDING");
        assertThat(summary.authorizedCount()).isNull();
        verifyNoInteractions(admin);
    }
}
