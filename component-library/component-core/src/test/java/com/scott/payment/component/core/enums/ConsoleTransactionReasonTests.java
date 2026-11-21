package com.scott.payment.component.core.enums;

import com.scott.payment.component.core.exception.ApiException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;

class ConsoleTransactionReasonTests {
    @Test void allApprovedCaptureAndVoidReasonsRemainAvailableInBothLanguages() {
        for (String group : List.of("CAPTURE", "VOID")) {
            List<String> codes = group.equals("CAPTURE") ? List.of("GOODS_SHIPPED", "SERVICE_COMPLETED", "MERCHANT_CONFIRMED",
                    "PARTIAL_FULFILLMENT", "ORDER_COMPLETED", "ACTUAL_AMOUNT_CONFIRMED", "CUSTOMER_CONFIRMED_SETTLEMENT", "OTHER")
                    : List.of("CUSTOMER_CANCELLED", "AUTHORIZATION_EXPIRED", "MERCHANT_CONFIRMED", "ORDER_CLOSED", "PRODUCT_UNAVAILABLE",
                    "DUPLICATE_AUTHORIZATION", "INCORRECT_AMOUNT", "PAYMENT_METHOD_CHANGED", "RISK_REVIEW", "OTHER");
            for (String code : codes) for (Locale locale : List.of(Locale.CHINESE, Locale.ENGLISH)) {
                assertThat(ConsoleTransactionReason.resolve(group, code, "OTHER".equals(code) ? "Details" : "", null, locale)).isNotBlank();
            }
            assertThatThrownBy(() -> ConsoleTransactionReason.resolve(group, "OTHER", "  ", null, Locale.CHINESE)).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> ConsoleTransactionReason.resolve(group, "OTHER", "a".repeat(201), null, Locale.ENGLISH)).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> ConsoleTransactionReason.resolve(group, null, null, null, Locale.CHINESE)).isInstanceOf(ApiException.class);
            assertThat(ConsoleTransactionReason.resolve(group, "OTHER", "a".repeat(200), null, Locale.ENGLISH)).endsWith("a".repeat(200));
        }
    }
    @Test void reasonGroupsCannotBeInterchangedAndRefundCatalogStaysUnchanged() {
        assertThatThrownBy(() -> ConsoleTransactionReason.resolve("CAPTURE", "DUPLICATE_AUTHORIZATION", null, null, Locale.ENGLISH)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ConsoleTransactionReason.resolve("VOID", "GOODS_SHIPPED", null, null, Locale.ENGLISH)).isInstanceOf(ApiException.class);
        assertThat(RefundReasonEnum.values()).hasSize(11);
    }
}
