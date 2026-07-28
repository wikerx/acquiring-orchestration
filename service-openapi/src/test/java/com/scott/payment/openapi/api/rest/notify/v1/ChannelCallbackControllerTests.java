package com.scott.payment.openapi.api.rest.notify.v1;

import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentQueryClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionMerchantApiResponseLogUpdateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.checkout.PaymentCheckoutClientDTOs;
import com.scott.payment.openapi.config.OpenApiCallbackProperties;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import static com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport.CHANNEL_NONCE_HEADER;
import static com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport.CHANNEL_SIGNATURE_HEADER;
import static com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport.CHANNEL_TIMESTAMP_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 渠道回调控制器测试。
 */
class ChannelCallbackControllerTests {

    private static final String CHANNEL_CODE = "MPGS";
    private static final String SECRET = "test-channel-callback-secret";

    /**
     * MPGS 3DS callback must use a dedicated callback type and stay under channel callback security.
     */
    @Test
    void shouldReceiveMpgsThreeDsCallbackThroughDedicatedEndpoint() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        ChannelCallbackController controller = new ChannelCallbackController(
                new OpenApiCallbackSecuritySupport(properties(), mock(SecurityInterceptEventRecorder.class)),
                paymentInternalClient);
        String rawBody = "threeDSServerTransID=7f880d1d-6d8d-4d7a-83af-7465d3f0c1b8"
                + "&threeDSSessionData=encrypted-session-data"
                + "&orderId=TX202607141000000000001";
        MockHttpServletRequest request = signedRequest(rawBody, "/channel/v1/callbacks/MPGS/3ds");

        controller.receiveThreeDs(CHANNEL_CODE, request, rawBody);

        TransactionChannelCallbackClientRequestDTO captured = paymentInternalClient.callbackRequestDTO;
        assertThat(captured.getChannelCode()).isEqualTo(CHANNEL_CODE);
        assertThat(captured.getCallbackType()).isEqualTo("MPGS_3DS_CALLBACK");
        assertThat(captured.getChannelEventType()).isEqualTo("THREE_DS_CALLBACK");
        assertThat(captured.getRequestUri()).isEqualTo("/channel/v1/callbacks/MPGS/3ds");
        assertThat(captured.getRequestBody()).isEqualTo(rawBody);
        assertThat(captured.getSignatureValid()).isTrue();
        assertThat(captured.getIpAllowed()).isTrue();
    }

    private OpenApiCallbackProperties properties() {
        OpenApiCallbackProperties properties = new OpenApiCallbackProperties();
        properties.setChannelSignatureRequired(true);
        properties.getChannelSecrets().put(CHANNEL_CODE, SECRET);
        return properties;
    }

    private MockHttpServletRequest signedRequest(String rawBody, String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", requestUri);
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String signature = InternalServiceSignature.sign(
                request.getMethod(),
                request.getRequestURI(),
                timestamp,
                nonce,
                CHANNEL_CODE.toLowerCase(),
                sha256Hex(rawBody),
                SECRET
        );
        request.addHeader(CHANNEL_TIMESTAMP_HEADER, String.valueOf(timestamp));
        request.addHeader(CHANNEL_NONCE_HEADER, nonce);
        request.addHeader(CHANNEL_SIGNATURE_HEADER, signature);
        return request;
    }

    private String sha256Hex(String rawBody) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static class CapturingPaymentInternalClient implements PaymentInternalClient {

        private TransactionChannelCallbackClientRequestDTO callbackRequestDTO;

        @Override
        public PaymentCreateClientResponseDTO createAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO createPayment(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO createPreAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO createIncrementalAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO capture(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO preAuthCompletion(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO refund(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO voidPayment(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentQueryClientResponseDTO query(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransactionChannelCallbackClientResponseDTO recordChannelCallback(
                TransactionChannelCallbackClientRequestDTO requestDTO) {
            this.callbackRequestDTO = requestDTO;
            TransactionChannelCallbackClientResponseDTO responseDTO = new TransactionChannelCallbackClientResponseDTO();
            responseDTO.setCallbackStatus("RECEIVED");
            responseDTO.setProcessResult("PENDING_STATE_MAPPING");
            return responseDTO;
        }

        @Override
        public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCheckoutClientDTOs.SessionCreateResponse createCheckoutSession(
                PaymentCheckoutClientDTOs.SessionCreateRequest requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCheckoutClientDTOs.SessionQueryResponse queryCheckoutSession(
                PaymentCheckoutClientDTOs.SessionQueryRequest requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse submitCheckoutPayment(
                PaymentCheckoutClientDTOs.PaymentSubmitRequest requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse queryCheckoutPaymentStatus(
                PaymentCheckoutClientDTOs.PaymentStatusRequest requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse handleCheckoutThreeDsReturn(
                PaymentCheckoutClientDTOs.ThreeDsReturnRequest requestDTO) {
            throw new UnsupportedOperationException();
        }
    }
}
