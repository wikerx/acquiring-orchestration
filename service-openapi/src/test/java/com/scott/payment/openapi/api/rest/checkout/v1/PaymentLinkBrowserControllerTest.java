package com.scott.payment.openapi.api.rest.checkout.v1;

import com.scott.payment.openapi.application.checkout.OpenApiPaymentLinkApplicationService;
import com.scott.payment.openapi.dto.paymentlink.PaymentLinkDTOs.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 匿名浏览器契约验证：只接收不透明凭据，金额/状态来自服务端，响应禁止缓存。
 */
class PaymentLinkBrowserControllerTest {
    private final OpenApiPaymentLinkApplicationService application=mock(OpenApiPaymentLinkApplicationService.class);
    private final MockMvc mvc=mvc();
    private MockMvc mvc() {
        var converters = new java.util.ArrayList<org.springframework.http.converter.HttpMessageConverter<?>>();
        new com.scott.payment.component.web.config.FastJsonWebMvcConfig().extendMessageConverters(converters);
        return MockMvcBuilders.standaloneSetup(new PaymentLinkBrowserController(application,mock(com.scott.payment.openapi.service.paymentlink.PaymentLinkBrowserAccessService.class)))
                .setControllerAdvice(new PaymentLinkRequestBodyAdvice())
                .setMessageConverters(converters.toArray(org.springframework.http.converter.HttpMessageConverter<?>[]::new)).build();
    }
    private final String token="a".repeat(32)+"."+"b".repeat(64);
    @Test void anonymousResolveHasNoPayerOrCredentialProjectionAndCannotBeCached() throws Exception {
        PublicView view=new PublicView();view.setMerchantDisplayName("QA Merchant");view.setCurrency("USD");view.setAmount("99.00");view.setStatus("ACTIVE");view.setPayable(true);when(application.resolve(token)).thenReturn(view);
        mvc.perform(post("/checkout/api/v1/payment-links/resolve").contentType(MediaType.APPLICATION_JSON).content("{\"opaqueToken\":\""+token+"\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("Referrer-Policy","no-referrer")).andExpect(jsonPath("$.data.amount").value("99.00"))
                .andExpect(jsonPath("$.data.opaqueToken").doesNotExist()).andExpect(jsonPath("$.data.merchantOrderNo").doesNotExist()).andExpect(jsonPath("$.data.email").doesNotExist());
    }
    @Test void malformedTokenRejectsBeforeOwnerCall() throws Exception {
        mvc.perform(post("/checkout/api/v1/payment-links/resolve").contentType(MediaType.APPLICATION_JSON).content("{\"opaqueToken\":\"invalid\"}")).andExpect(status().isBadRequest());verifyNoInteractions(application);
    }
    @Test void payerCannotSupplyAnAmountOverride() throws Exception {
        mvc.perform(post("/checkout/api/v1/payment-links/start").contentType(MediaType.APPLICATION_JSON).content("{\"opaqueToken\":\""+token+"\",\"requestId\":\"req-1\",\"amount\":\"0.01\"}")).andExpect(status().isBadRequest());verifyNoInteractions(application);
    }
    @Test void startRequiresStableRequestIdentity() throws Exception {
        mvc.perform(post("/checkout/api/v1/payment-links/start").contentType(MediaType.APPLICATION_JSON).content("{\"opaqueToken\":\""+token+"\"}")).andExpect(status().isBadRequest());verifyNoInteractions(application);
    }
    @Test void resolveDoesNotAcceptLegacyQueryParametersOrGet() throws Exception {
        mvc.perform(get("/checkout/api/v1/payment-links/resolve").param("opaqueToken",token)).andExpect(status().isMethodNotAllowed());verifyNoInteractions(application);
    }


    @Test void newPrefixedAndPreviouslyIssuedIdentifiersKeepTheSameCredentialBoundary() throws Exception {
        for (String id : java.util.List.of("L2026111810000000000001", "2026111810000000000001", "a".repeat(32))) {
            String value = id + "." + "b".repeat(64);
            mvc.perform(post("/checkout/api/v1/payment-links/resolve").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"opaqueToken\":\"" + value + "\"}")).andExpect(status().isOk());
            verify(application).resolve(value);
        }
    }
}
