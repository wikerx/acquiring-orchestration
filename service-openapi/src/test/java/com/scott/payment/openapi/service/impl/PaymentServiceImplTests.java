package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.config.PaymentClientProperties;
import com.scott.payment.openapi.converter.OpenApiRequestConverter;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.support.OpenApiRequestAttributes;
import com.scott.payment.openapi.support.OpenApiRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceImplTests {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldPassFullPaymentContextToPaymentService() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        PaymentClientProperties properties = new PaymentClientProperties();
        properties.setRemoteEnabled(true);
        PaymentServiceImpl paymentService = new PaymentServiceImpl(
                Mappers.getMapper(OpenApiRequestConverter.class),
                paymentInternalClient,
                properties,
                new OpenApiKeyMaterialFactory(),
                new OpenApiRequestContext(),
                isoDictionaryService()
        );
        bindRequestContext();

        paymentService.createPayment("encrypted-body", buildRequest());

        PaymentCreateClientRequestDTO captured = paymentInternalClient.requestDTO;
        assertThat(captured.getMerchantId()).isEqualTo("200001");
        assertThat(captured.getMerchantOrderNo()).isEqualTo("M202607120001");
        assertThat(captured.getTransactionType()).isEqualTo("AUTHORIZATION");
        assertThat(captured.getPaymentMethod()).isEqualTo("BANK_CARD");
        assertThat(captured.getCardInfo().getCardNo()).isEqualTo("5387380678556554");
        assertThat(captured.getCardInfo().getSecurityCode()).isEqualTo("123");
        assertThat(captured.getBillingCardHolderInfo().getEmail()).isEqualTo("user@example.com");
        assertThat(captured.getThreeDsInfo().getDsTransactionId()).isEqualTo("b96c957d-daa1-4b7f-b8b4-373fb9dec47b");
        assertThat(captured.getTransactionInfo().getCardBrand()).isEqualTo("MASTERCARD");
        assertThat(captured.getCallbackUrl()).isEqualTo("https://merchant.example/callback");
        assertThat(captured.getSourceUrl()).isEqualTo("https://checkout.example");
        assertThat(captured.getPayerIp()).isEqualTo("203.0.113.1");
        assertThat(captured.getUserAgent()).isEqualTo("JUnit");
    }

    private void bindRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
        request.addHeader("Origin", "https://checkout.example");
        request.addHeader("User-Agent", "JUnit");
        OpenApiRequestHeaderDTO headerDTO = new OpenApiRequestHeaderDTO();
        headerDTO.setMerchantId("200001");
        request.setAttribute(OpenApiRequestAttributes.REQUEST_HEADER, headerDTO);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ApiMerchantPaymentRequestDTO buildRequest() {
        ApiMerchantPaymentRequestDTO requestDTO = new ApiMerchantPaymentRequestDTO();
        ApiMerchantPaymentRequestDTO.MerchantInfoDTO merchantInfo = new ApiMerchantPaymentRequestDTO.MerchantInfoDTO();
        ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO subMerchantInfo = new ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO();
        subMerchantInfo.setSubId("SUB001");
        subMerchantInfo.setMerchantCategory("5311");
        merchantInfo.setSubMerchantInfo(subMerchantInfo);
        requestDTO.setMerchantInfo(merchantInfo);

        ApiMerchantPaymentRequestDTO.OrderInfoDTO orderInfo = new ApiMerchantPaymentRequestDTO.OrderInfoDTO();
        orderInfo.setTradeNo("M202607120001");
        orderInfo.setAmount(new BigDecimal("12.34"));
        orderInfo.setCurrency("USD");
        orderInfo.setSourceReference("AUTH001");
        requestDTO.setOrderInfo(orderInfo);

        ApiMerchantPaymentRequestDTO.CardInfoDTO cardInfo = new ApiMerchantPaymentRequestDTO.CardInfoDTO();
        cardInfo.setCardNo("5387380678556554");
        cardInfo.setExpirationMonth("03");
        cardInfo.setExpirationYear("2028");
        cardInfo.setSecurityCode("123");
        requestDTO.setCardInfo(cardInfo);

        ApiMerchantPaymentRequestDTO.BillingCardHolderInfoDTO billing = new ApiMerchantPaymentRequestDTO.BillingCardHolderInfoDTO();
        billing.setFirstName("John");
        billing.setLastName("Tom");
        billing.setEmail("user@example.com");
        billing.setPhone("+1-555-0100");
        billing.setCountry("USA");
        billing.setCity("New York");
        billing.setStreet("1 Main St");
        billing.setPostal("10001");
        requestDTO.setBillingCardHolderInfo(billing);

        ApiMerchantPaymentRequestDTO.ThreeDsInfoDTO threeDsInfo = new ApiMerchantPaymentRequestDTO.ThreeDsInfoDTO();
        threeDsInfo.setEci("212");
        threeDsInfo.setCavv("abcdefghijklmnopqrstuvwx1234");
        threeDsInfo.setDsTransactionId("b96c957d-daa1-4b7f-b8b4-373fb9dec47b");
        threeDsInfo.setThreeDsVersion("2.2.0");
        requestDTO.setThreeDsInfo(threeDsInfo);

        ApiMerchantPaymentRequestDTO.TransactionInfoDTO transactionInfo = new ApiMerchantPaymentRequestDTO.TransactionInfoDTO();
        transactionInfo.setTransactionId("txn-001");
        transactionInfo.setSourceTransactionId("source-001");
        transactionInfo.setCallbackUrl("https://merchant.example/callback");
        transactionInfo.setCardBrand("MASTERCARD");
        requestDTO.setTransactionInfo(transactionInfo);
        return requestDTO;
    }

    private IsoDictionaryService isoDictionaryService() {
        return new IsoDictionaryService() {
            @Override
            public List<IsoCountryInfo> listCountries() {
                return List.of();
            }

            @Override
            public List<IsoCountryInfo> searchCountries(String keyword) {
                return List.of();
            }

            @Override
            public Optional<IsoCountryInfo> getCountry(String value) {
                return Optional.empty();
            }

            @Override
            public List<IsoCountryInfo> listCountriesByContinent(String continentCode) {
                return List.of();
            }

            @Override
            public List<IsoCountryInfo> listCountriesByCurrency(String currencyAlpha3Code) {
                return List.of();
            }

            @Override
            public List<IsoCurrencyInfo> listCurrencies() {
                return List.of();
            }

            @Override
            public List<IsoCurrencyInfo> searchCurrencies(String keyword) {
                return List.of();
            }

            @Override
            public Optional<IsoCurrencyInfo> getCurrency(String value) {
                return Optional.empty();
            }

            @Override
            public boolean isCurrencyFractionValid(BigDecimal amount, String currencyValue) {
                return true;
            }

            @Override
            public long toMinorUnit(BigDecimal amount, String currencyValue) {
                return amount.movePointRight(2).longValueExact();
            }
        };
    }

    private static class CapturingPaymentInternalClient implements PaymentInternalClient {

        private PaymentCreateClientRequestDTO requestDTO;

        @Override
        public PaymentCreateClientResponseDTO createAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            this.requestDTO = requestDTO;
            PaymentCreateClientResponseDTO responseDTO = new PaymentCreateClientResponseDTO();
            responseDTO.setPaymentOrderNo("TO202607120001");
            responseDTO.setMerchantOrderNo(requestDTO.getMerchantOrderNo());
            responseDTO.setStatus("PROCESSING");
            responseDTO.setCurrency(requestDTO.getCurrency());
            responseDTO.setAmount(1234L);
            return responseDTO;
        }
    }
}
