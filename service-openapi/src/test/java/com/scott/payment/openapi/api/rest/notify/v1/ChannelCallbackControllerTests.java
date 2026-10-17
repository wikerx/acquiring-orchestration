package com.scott.payment.openapi.api.rest.notify.v1;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackProtocol;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackAcknowledgement;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackProtocolDescriptor;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.VerifiedChannelEvent;
import com.scott.payment.channel.payment.enums.ChannelCallbackAuthentication;
import com.scott.payment.channel.payment.enums.ChannelCallbackKind;
import com.scott.payment.channel.payment.enums.ChannelCallbackScene;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import com.scott.payment.channel.payment.registry.PaymentChannelCallbackProtocolRegistry;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentQueryClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionCallbackInboxAcceptClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionMerchantApiResponseLogUpdateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.checkout.PaymentCheckoutClientDTOs;
import com.scott.payment.openapi.config.OpenApiCallbackProperties;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackControllerTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 渠道回调控制器测试。
 * @status : create
 */
class ChannelCallbackControllerTests {

    /** 用于构造签名范围和回调路由的测试渠道编码。 */
    private static final String CHANNEL_CODE = "EVENT_PROVIDER";

    /**
     * 3DS callback must use a provider-neutral callback type and stay under channel callback security.
     */
    @Test
    void shouldReceiveThreeDsCallbackThroughDedicatedEndpoint() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        FixedProtocol protocol = new FixedProtocol(false, ChannelCallbackAcknowledgement.plainText("ACK"));
        ChannelCallbackController controller = new ChannelCallbackController(
                securitySupport(protocol),
                paymentInternalClient);
        String rawBody = "threeDSServerTransID=7f880d1d-6d8d-4d7a-83af-7465d3f0c1b8"
                + "&threeDSSessionData=encrypted-session-data"
                + "&orderId=TX202607141000000000001";
        MockHttpServletRequest request = request("/channel/v1/callbacks/EVENT_PROVIDER/3ds");

        ResponseEntity<?> response = controller.receiveThreeDs(
                CHANNEL_CODE, request, rawBody.getBytes(StandardCharsets.UTF_8));

        TransactionChannelCallbackClientRequestDTO captured = paymentInternalClient.callbackRequestDTO;
        assertThat(captured.getChannelCode()).isEqualTo(CHANNEL_CODE);
        assertThat(captured.getCallbackType()).isEqualTo("THREE_DS_AUTHENTICATION_CALLBACK");
        assertThat(captured.getChannelEventType()).isEqualTo("THREE_DS_CALLBACK");
        assertThat(captured.getRequestUri()).isEqualTo("/channel/v1/callbacks/EVENT_PROVIDER/3ds");
        assertThat(captured.getRequestBodyLength()).isEqualTo(rawBody.getBytes(StandardCharsets.UTF_8).length);
        assertThat(captured.getRequestBodySha256()).hasSize(64);
        assertThat(captured.getCallbackEventId()).isEqualTo("event-3ds-001");
        assertThat(captured.getCallbackKind()).isEqualTo(ChannelCallbackKind.THREE_DS_AUTHENTICATION.name());
        assertThat(captured.getChannelOrderNo()).isEqualTo("TX202607141000000000001");
        assertThat(captured.getSignatureValid()).isTrue();
        assertThat(captured.getIpAllowed()).isTrue();
        assertThat(response.getBody()).isEqualTo("ACK");
    }

    /** 验签失败时不得把回调转发到支付核心，避免伪造通知推进交易状态。 */
    @Test
    void shouldNotCallPaymentCoreWhenSignatureVerificationFails() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        ChannelCallbackController controller = new ChannelCallbackController(
                securitySupport(new FixedProtocol(true, ChannelCallbackAcknowledgement.plainText("ACK"))),
                paymentInternalClient);
        MockHttpServletRequest request = request("/channel/v1/callbacks/EVENT_PROVIDER");

        assertThatThrownBy(() -> controller.receive(
                CHANNEL_CODE, request, "{}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(com.scott.payment.component.core.exception.ApiException.class);
        assertThat(paymentInternalClient.callbackRequestDTO).isNull();
    }

    /** Inbox 持久化未确认时控制器必须抛错，不得提前生成渠道成功 ACK。 */
    @Test
    void shouldNotAcknowledgeWhenInboxAcceptanceFails() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        paymentInternalClient.failAcceptance = true;
        ChannelCallbackController controller = new ChannelCallbackController(
                securitySupport(new FixedProtocol(false, ChannelCallbackAcknowledgement.plainText("ACK"))),
                paymentInternalClient);
        MockHttpServletRequest request = request("/channel/v1/callbacks/EVENT_PROVIDER");

        assertThatThrownBy(() -> controller.receive(
                CHANNEL_CODE, request, "{}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inbox unavailable");
    }

    private OpenApiCallbackSecuritySupport securitySupport(PaymentChannelCallbackProtocol protocol) {
        OpenApiCallbackProperties properties = new OpenApiCallbackProperties();
        properties.getChannelSecrets().put(CHANNEL_CODE, "test-secret");
        return new OpenApiCallbackSecuritySupport(
                properties,
                mock(SecurityInterceptEventRecorder.class),
                new PaymentChannelCallbackProtocolRegistry(List.of(protocol)),
                (channelCode, merchantId) -> null);
    }

    private MockHttpServletRequest request(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", requestUri);
        request.setContentType("application/x-www-form-urlencoded");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static class FixedProtocol implements PaymentChannelCallbackProtocol {

        private final boolean failVerification;
        private final ChannelCallbackAcknowledgement acknowledgement;

        private FixedProtocol(boolean failVerification, ChannelCallbackAcknowledgement acknowledgement) {
            this.failVerification = failVerification;
            this.acknowledgement = acknowledgement;
        }

        @Override
        public Set<String> channelCodes() {
            return Set.of(CHANNEL_CODE);
        }

        @Override
        public ChannelCallbackProtocolDescriptor descriptor() {
            return new ChannelCallbackProtocolDescriptor(
                    Set.of("POST"),
                    Set.of("application/x-www-form-urlencoded"),
                    Set.of(ChannelCallbackScene.STANDARD, ChannelCallbackScene.THREE_DS),
                    4096,
                    ChannelCallbackAuthentication.REQUIRED);
        }

        @Override
        public VerifiedChannelEvent verifyAndParse(RawChannelCallbackRequest request,
                                                   ChannelCallbackVerificationContext context) {
            if (failVerification) {
                throw new ChannelCallbackVerificationException(
                        ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID,
                        "invalid provider signature");
            }
            return new VerifiedChannelEvent(
                    CHANNEL_CODE, null, "event-3ds-001",
                    request.scene() == ChannelCallbackScene.THREE_DS
                            ? ChannelCallbackKind.THREE_DS_AUTHENTICATION
                            : ChannelCallbackKind.FINANCIAL_TRANSACTION,
                    "TX202607141000000000001", "channel-transaction-001",
                    "3DS_METHOD_COMPLETED", "PENDING", null, "USD",
                    "3DS_METHOD_COMPLETED", "received", Map.of());
        }

        @Override
        public ChannelCallbackAcknowledgement successAcknowledgement(RawChannelCallbackRequest request,
                                                                      VerifiedChannelEvent event) {
            return acknowledgement;
        }
    }

    private static class CapturingPaymentInternalClient implements PaymentInternalClient {

        /** 最近一次转发到支付核心的回调请求，用于断言回调分类、安全结论和原文。 */
        private TransactionChannelCallbackClientRequestDTO callbackRequestDTO;

        /** 模拟 Inbox 数据库受理失败，验证控制器不会提前返回渠道 ACK。 */
        private boolean failAcceptance;

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
        public TransactionCallbackInboxAcceptClientResponseDTO acceptChannelCallback(
                TransactionChannelCallbackClientRequestDTO requestDTO) {
            if (failAcceptance) {
                throw new IllegalStateException("inbox unavailable");
            }
            this.callbackRequestDTO = requestDTO;
            TransactionCallbackInboxAcceptClientResponseDTO responseDTO =
                    new TransactionCallbackInboxAcceptClientResponseDTO();
            responseDTO.setInboxStatus("RECEIVED");
            responseDTO.setAcceptResult("ACCEPTED");
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
