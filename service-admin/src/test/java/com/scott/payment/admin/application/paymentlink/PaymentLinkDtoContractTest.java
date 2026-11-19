package com.scott.payment.admin.application.paymentlink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 固定重构前的公开字段、默认值和校验边界，验证继承不会改变双端 JSON 契约。 */
class PaymentLinkDtoContractTest {
    private final ObjectMapper json = new ObjectMapper();

    static Stream<Arguments> publicModels() {
        return Stream.of(
                Arguments.of(new PaymentLinkDTOs.Query(), "linkId,merchantOrderNo,merchantId,keyword,status,currency,channelCode,createTimeFrom,createTimeTo,pageNo,pageSize"),
                Arguments.of(new PaymentLinkDTOs.View(), "linkId,merchantId,merchantOrderNo,linkName,description,merchantDisplayName,currency,amount,currencyExponent,locale,cardBrands,status,paymentStatus,attemptCount,transactionId,transactionDateTime,expireTime,createTime,paidTime,createdAppCode,createdBy,createdByName,disableReason,version,notifyUrl,redirectUrl"),
                Arguments.of(new PaymentLinkDTOs.Summary(), "total,active,completed,processing,failedAttempts,successfulAmounts"),
                Arguments.of(new PaymentLinkDTOs.Collection(), "collectionId,linkId,merchantOrderNo,checkoutSessionId,paymentStatus,submissionDeadline,createTime"),
                Arguments.of(new PaymentLinkDTOs.Attempt(), "checkoutAttemptId,attemptNo,transactionId,transactionDateTime,status,paymentMethod,cardBrand,currency,amount,currencyExponent,createTime,completeTime"),
                Arguments.of(new PaymentLinkDTOs.Event(), "eventId,eventType,actorAppCode,actorId,actorName,reason,createTime"),
                Arguments.of(new PaymentLinkDTOs.Delivery(), "requestId,recipientMasked,locale,sendStatus,emailRecordId,createdAppCode,createdBy,createdByName,createTime")
        );
    }

    @ParameterizedTest
    @MethodSource("publicModels")
    void inheritedModelsKeepOriginalPublicFieldSet(Object model, String originalFields) {
        var keys = new HashSet<String>();
        json.valueToTree(model).fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrderElementsOf(Arrays.asList(originalFields.split(",")));
    }

    @Test
    void queryDefaultsAndInheritedValidationRemainEffective() {
        var query = new PaymentLinkDTOs.Query();
        assertThat(query.getPageNo()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(10);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(query)).isEmpty();
            query.setPageSize(101);
            query.setStatus("UNKNOWN");
            query.setCurrency("usd");
            assertThat(factory.getValidator().validate(query))
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("pageSize", "status", "currency");
        }
    }

    @Test
    void responseRoundTripPreservesMoneyStringsAndLocalType() throws Exception {
        var view = json.readValue("{\"linkId\":\"L123\",\"amount\":\"10.00\",\"currency\":\"USD\",\"cardBrands\":[\"VISA\"],\"attemptCount\":2}", PaymentLinkDTOs.View.class);
        assertThat(view.getClass()).isEqualTo(PaymentLinkDTOs.View.class);
        assertThat(view.getAmount()).isEqualTo("10.00");
        assertThat(json.readTree(json.writeValueAsString(view)).get("amount").isTextual()).isTrue();
        assertThat(view.getCardBrands()).containsExactly("VISA");
        assertThat(view.getAttemptCount()).isEqualTo(2);
        assertThat(new PaymentLinkDTOs.Summary().getSuccessfulAmounts()).isEmpty();
    }

    @Test
    void inheritedDiagnosticStringStillExcludesCallbackAddresses() {
        var view = new PaymentLinkDTOs.View();
        view.setNotifyUrl("https://example.invalid/private-notify");
        view.setRedirectUrl("https://example.invalid/private-return");
        assertThat(view.toString()).doesNotContain("private-notify", "private-return");
    }
}
