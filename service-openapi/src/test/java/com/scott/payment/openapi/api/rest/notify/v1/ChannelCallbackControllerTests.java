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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 渠道回调控制器测试。
 */
class ChannelCallbackControllerTests {

    /** 用于构造签名范围和回调路由的测试渠道编码。 */
    private static final String CHANNEL_CODE = "EVENT_PROVIDER";

    /** 仅用于单元测试签名计算的渠道共享密钥，禁止用于任何运行环境。 */
    private static final String SECRET = "test-channel-callback-secret";

    /**
     * 3DS callback must use a provider-neutral callback type and stay under channel callback security.
     */
    @Test
    void shouldReceiveThreeDsCallbackThroughDedicatedEndpoint() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        ChannelCallbackController controller = new ChannelCallbackController(
                new OpenApiCallbackSecuritySupport(properties(), mock(SecurityInterceptEventRecorder.class)),
                paymentInternalClient);
        String rawBody = "threeDSServerTransID=7f880d1d-6d8d-4d7a-83af-7465d3f0c1b8"
                + "&threeDSSessionData=encrypted-session-data"
                + "&orderId=TX202607141000000000001";
        MockHttpServletRequest request = signedRequest(rawBody, "/channel/v1/callbacks/EVENT_PROVIDER/3ds");

        controller.receiveThreeDs(CHANNEL_CODE, request, rawBody);

        TransactionChannelCallbackClientRequestDTO captured = paymentInternalClient.callbackRequestDTO;
        assertThat(captured.getChannelCode()).isEqualTo(CHANNEL_CODE);
        assertThat(captured.getCallbackType()).isEqualTo("THREE_DS_AUTHENTICATION_CALLBACK");
        assertThat(captured.getChannelEventType()).isEqualTo("THREE_DS_CALLBACK");
        assertThat(captured.getRequestUri()).isEqualTo("/channel/v1/callbacks/EVENT_PROVIDER/3ds");
        assertThat(captured.getRequestBody()).isEqualTo(rawBody);
        assertThat(captured.getSignatureValid()).isTrue();
        assertThat(captured.getIpAllowed()).isTrue();
    }

    /** 验签失败时不得把回调转发到支付核心，避免伪造通知推进交易状态。 */
    @Test
    void shouldNotCallPaymentCoreWhenSignatureVerificationFails() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        ChannelCallbackController controller = new ChannelCallbackController(
                new OpenApiCallbackSecuritySupport(properties(), mock(SecurityInterceptEventRecorder.class)),
                paymentInternalClient);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/channel/v1/callbacks/EVENT_PROVIDER");

        assertThatThrownBy(() -> controller.receive(CHANNEL_CODE, request, "{}"))
                .isInstanceOf(com.scott.payment.component.core.exception.ApiException.class);
        assertThat(paymentInternalClient.callbackRequestDTO).isNull();
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

        /** 最近一次转发到支付核心的回调请求，用于断言回调分类、安全结论和原文。 */
        private TransactionChannelCallbackClientRequestDTO callbackRequestDTO;

        /** 本回调测试不允许进入授权交易路径，意外调用立即失败。 */
        @Override
        public PaymentCreateClientResponseDTO createAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本回调测试不允许进入一步支付路径，意外调用立即失败。 */
        @Override
        public PaymentCreateClientResponseDTO createPayment(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本回调测试不允许进入预授权路径，意外调用立即失败。 */
        @Override
        public PaymentCreateClientResponseDTO createPreAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本回调测试不允许进入增量授权路径，意外调用立即失败。 */
        @Override
        public PaymentCreateClientResponseDTO createIncrementalAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本回调测试不允许进入请款路径，意外调用立即失败。 */
        @Override
        public PaymentCreateClientResponseDTO capture(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本回调测试不允许进入预授权完成路径，意外调用立即失败。 */
        @Override
        public PaymentCreateClientResponseDTO preAuthCompletion(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本回调测试不允许进入退款路径，意外调用立即失败。 */
        @Override
        public PaymentCreateClientResponseDTO refund(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本回调测试不允许进入撤销路径，意外调用立即失败。 */
        @Override
        public PaymentCreateClientResponseDTO voidPayment(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本回调测试不允许进入交易查询路径，意外调用立即失败。 */
        @Override
        public PaymentQueryClientResponseDTO query(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 捕获控制器转发的回调请求，并返回尚待状态映射的固定受理结果。 */
        @Override
        public TransactionChannelCallbackClientResponseDTO recordChannelCallback(
                TransactionChannelCallbackClientRequestDTO requestDTO) {
            this.callbackRequestDTO = requestDTO;
            TransactionChannelCallbackClientResponseDTO responseDTO = new TransactionChannelCallbackClientResponseDTO();
            responseDTO.setCallbackStatus("RECEIVED");
            responseDTO.setProcessResult("PENDING_STATE_MAPPING");
            return responseDTO;
        }

        /** 本回调测试不允许回写商户响应日志，意外调用立即失败。 */
        @Override
        public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖 Hosted Checkout 会话创建，调用即表示测试路径越界。 */
        @Override
        public PaymentCheckoutClientDTOs.SessionCreateResponse createCheckoutSession(
                PaymentCheckoutClientDTOs.SessionCreateRequest requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖 Hosted Checkout 会话查询，调用即表示测试路径越界。 */
        @Override
        public PaymentCheckoutClientDTOs.SessionQueryResponse queryCheckoutSession(
                PaymentCheckoutClientDTOs.SessionQueryRequest requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖 Hosted Checkout 支付提交，调用即表示测试路径越界。 */
        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse submitCheckoutPayment(
                PaymentCheckoutClientDTOs.PaymentSubmitRequest requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖 Hosted Checkout 状态轮询，调用即表示测试路径越界。 */
        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse queryCheckoutPaymentStatus(
                PaymentCheckoutClientDTOs.PaymentStatusRequest requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖 3DS 返回处理，调用即表示测试路径越界。 */
        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse handleCheckoutThreeDsReturn(
                PaymentCheckoutClientDTOs.ThreeDsReturnRequest requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖收银台卡 BIN 解析，调用即表示测试路径越界。 */
        @Override
        public PaymentCheckoutClientDTOs.CardBinResponse resolveCheckoutCardBin(
                PaymentCheckoutClientDTOs.CardBinRequest requestDTO) {
            throw new UnsupportedOperationException();
        }
    }
}
