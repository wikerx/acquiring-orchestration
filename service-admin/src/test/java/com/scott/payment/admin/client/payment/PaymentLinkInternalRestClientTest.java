package com.scott.payment.admin.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.admin.config.PaymentInternalClientProperties;
import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.Command;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证真实应用客户端的动作边界、请求签名与泛型响应解包。 */
class PaymentLinkInternalRestClientTest {
    private static final String TEST_SECRET = "payment-link-client-contract-test-secret";
    private final RestTemplate direct = mock(RestTemplate.class);
    private final RestTemplate discovered = mock(RestTemplate.class);
    private final PaymentInternalClientProperties properties = new PaymentInternalClientProperties();

    @Test
    void filterCurrenciesUsesSignedOwnerEndpointAndDecodesCurrencyList() {
        properties.setInternalSecret(TEST_SECRET);
        PaymentInternalRestClient client = new PaymentInternalRestClient(direct, discovered, properties);
        URI target = URI.create("http://service-payment/internal/payment/payment-links/filter-currencies");
        when(discovered.exchange(eq(target), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(CommonResult.success(List.of("EUR", "USD")))));

        List<String> result = client.executePaymentLink("filter-currencies", new Command(),
                new TypeReference<CommonResult<List<String>>>() { });

        assertThat(result).containsExactly("EUR", "USD");
        ArgumentCaptor<HttpEntity<String>> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(discovered).exchange(eq(target), eq(HttpMethod.POST), entity.capture(), eq(String.class));
        var headers = entity.getValue().getHeaders();
        assertThat(headers.getFirst(InternalServiceSignature.HEADER_CALLER)).isEqualTo("service-admin");
        String signature = InternalServiceSignature.sign("POST", target.getRawPath(),
                Long.parseLong(headers.getFirst(InternalServiceSignature.HEADER_TIMESTAMP)),
                headers.getFirst(InternalServiceSignature.HEADER_NONCE), "service-admin",
                InternalServiceSignature.payloadSha256(entity.getValue().getBody()), TEST_SECRET);
        assertThat(headers.getFirst(InternalServiceSignature.HEADER_SIGNATURE)).isEqualTo(signature);
        verifyNoInteractions(direct);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../filter-currencies", "filter-currencies?all=true", "unknown"})
    void unsupportedActionsFailBeforeHttpInvocation(String action) {
        PaymentInternalRestClient client = new PaymentInternalRestClient(direct, discovered, properties);
        assertThatThrownBy(() -> client.executePaymentLink(action, new Command(),
                new TypeReference<CommonResult<List<String>>>() { })).isInstanceOf(ServiceException.class);
        verifyNoInteractions(direct, discovered);
    }
}
