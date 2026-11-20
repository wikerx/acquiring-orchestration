package com.scott.payment.merchant.client.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.merchant.dto.merchantpayment.MerchantPaymentModels.Apply;
import com.scott.payment.merchant.dto.merchantpayment.MerchantPaymentModels.BatchReceipt;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MerchantPaymentGovernanceClientTests {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final RestTemplate direct = new RestTemplate();
    private final MockEnvironment environment = new MockEnvironment()
            .withProperty("merchant.admin-payment-method-client.base-url", "https://admin.test");

    @Test
    void signsTrustedScopeAndExactBody() throws Exception {
        final String syntheticSecret = "test-only-signing-fixture-for-admin-edge";
        environment.withProperty("merchant.admin-payment-method-client.internal-secret", syntheticSecret);
        var mock = MockRestServiceServer.bindTo(direct).build();
        var response = CommonResult.success(new BatchReceipt("new-application", 1));
        mock.expect(requestTo("https://admin.test/internal/merchant/200045/payment-methods/applications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    byte[] body = ((MockClientHttpRequest) request).getBodyAsBytes();
                    var value = json.readTree(body);
                    assertThat(value.path("actor").path("accountId").asLong()).isEqualTo(17);
                    assertThat(value.path("actor").path("userId").asLong()).isEqualTo(19);
                    assertThat(value.path("command").path("items").get(0).asText()).isEqualTo("METHOD:APPLE_PAY");
                    var headers = request.getHeaders();
                    String expected = InternalServiceSignature.sign("POST", request.getURI().getRawPath(),
                            Long.parseLong(headers.getFirst(InternalServiceSignature.HEADER_TIMESTAMP)),
                            headers.getFirst(InternalServiceSignature.HEADER_NONCE), "service-merchant",
                            InternalServiceSignature.payloadSha256(body), syntheticSecret);
                    assertThat(headers.getFirst(InternalServiceSignature.HEADER_SIGNATURE)).isEqualTo(expected);
                })
                .andRespond(withSuccess(json.writeValueAsString(response), MediaType.APPLICATION_JSON));

        var result = client().apply("200045", new MerchantPaymentGovernanceClient.Identity(17, 19),
                new Apply("same-key", List.of("METHOD:APPLE_PAY"), null));

        assertThat(result).isEqualTo(new BatchReceipt("new-application", 1));
        mock.verify();
    }

    @Test
    void absentSigningCredentialDoesNotSendRequest() {
        var mock = MockRestServiceServer.bindTo(direct).build();
        assertThatThrownBy(() -> client().methods("200045", new MerchantPaymentGovernanceClient.Identity(17, 19), null))
                .isInstanceOfSatisfying(ServiceException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ApiResultEnum.BAD_GATEWAY.getCode()));
        mock.verify();
    }

    private MerchantPaymentGovernanceClient client() {
        return new MerchantPaymentGovernanceClient(new RestTemplate(), direct, json, environment);
    }
}
