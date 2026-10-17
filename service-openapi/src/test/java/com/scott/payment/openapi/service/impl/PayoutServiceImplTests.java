package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.client.payout.PayoutInternalClient;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientRequestDTO;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientResponseDTO;
import com.scott.payment.openapi.dto.body.PayoutCreateRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.support.OpenApiRequestAttributes;
import com.scott.payment.openapi.support.OpenApiRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutServiceImplTests
 * @date : 2026-10-17 14:40
 * @email : scott_x@163.com
 * @description : 验证 OpenAPI 代付请求始终转发至 service-payout，禁止生成本地模拟代付单。
 * @status : create
 */
class PayoutServiceImplTests {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldAlwaysCreatePayoutThroughInternalClient() {
        CapturingPayoutInternalClient client = new CapturingPayoutInternalClient();
        PayoutServiceImpl service = new PayoutServiceImpl(
                client,
                new OpenApiKeyMaterialFactory(),
                new OpenApiRequestContext());
        bindMerchant("200001");
        PayoutCreateRequestDTO requestDTO = new PayoutCreateRequestDTO();
        requestDTO.setMerchantOrderNo("PO-MERCHANT-001");
        requestDTO.setAmount(12345L);
        requestDTO.setCurrency("USD");
        requestDTO.setReceiverAccountNo("receiver-account-001");

        String payoutOrderNo = service.createPayout("encrypted-payout-request", requestDTO);

        assertThat(payoutOrderNo).isEqualTo("PAYOUT-CORE-001");
        assertThat(client.request).isNotNull();
        assertThat(client.request.getMerchantId()).isEqualTo("200001");
        assertThat(client.request.getMerchantOrderNo()).isEqualTo("PO-MERCHANT-001");
        assertThat(client.request.getAmount()).isEqualByComparingTo(new BigDecimal("12345"));
        assertThat(client.request.getCurrency()).isEqualTo("USD");
        assertThat(client.request.getTransactionDateTime()).isNotNull();
        assertThat(client.request.getRequestFingerprint()).isNotBlank();
    }

    private void bindMerchant(String merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        OpenApiRequestHeaderDTO headerDTO = new OpenApiRequestHeaderDTO();
        headerDTO.setMerchantId(merchantId);
        request.setAttribute(OpenApiRequestAttributes.REQUEST_HEADER, headerDTO);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static final class CapturingPayoutInternalClient implements PayoutInternalClient {

        private PayoutCreateClientRequestDTO request;

        @Override
        public PayoutCreateClientResponseDTO createPayout(PayoutCreateClientRequestDTO requestDTO) {
            this.request = requestDTO;
            PayoutCreateClientResponseDTO responseDTO = new PayoutCreateClientResponseDTO();
            responseDTO.setPayoutOrderNo("PAYOUT-CORE-001");
            responseDTO.setMerchantOrderNo(requestDTO.getMerchantOrderNo());
            responseDTO.setStatus("PROCESSING");
            return responseDTO;
        }
    }
}
