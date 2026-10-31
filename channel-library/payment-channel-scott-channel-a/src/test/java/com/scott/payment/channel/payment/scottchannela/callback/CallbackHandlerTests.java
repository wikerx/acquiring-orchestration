package com.scott.payment.channel.payment.scottchannela.callback;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.scottchannela.support.ChannelCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScottChannelA 回调身份校验、状态映射和扩展证据测试。
 **/
class CallbackHandlerTests {

    /**
     * 验证渠道回调状态和核心交易字段映射。
     **/
    @Test
    void shouldMapCallbackPayload() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("""
                {
                  "eventId":"EVENT-001",
                  "merchantId":"MID-TEST",
                  "merchantOrderNo":"ORDER-001",
                  "transactionId":"TX-001",
                  "status":"succeeded",
                  "amount":10.25,
                  "currency":"USD",
                  "paymentMethod":"CARD",
                  "responseCode":"SUCCESS"
                }
                """);
        request.setHeaders(Map.of(
                CallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                CallbackVerifier.EVENT_ID_HEADER, "EVENT-001"));

        ChannelCallbackResult result = new CallbackHandler().handle(request);

        assertThat(result.getChannelCode()).isEqualTo(ChannelCode.VALUE);
        assertThat(result.getCallbackEventId()).isEqualTo("EVENT-001");
        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.SUCCESS.getCode());
        assertThat(result.getChannelOrderNo()).isEqualTo("ORDER-001");
        assertThat(result.getAmount()).isEqualByComparingTo("10.25");
        assertThat(result.getExtension()).containsEntry("paymentMethod", "CARD");
    }

    /**
     * 渠道以平台处理中码回调时，应保留处理中状态等待查询或后续终态回调。
     **/
    @Test
    void shouldMapProcessingCallbackCode() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("{\"eventId\":\"EVENT-202\",\"merchantId\":\"MID-TEST\",\"merchantOrderNo\":\"CMT-001\",\"transactionId\":\"TX-202\",\"status\":\"T202\",\"responseCode\":\"T202\"}");
        request.setHeaders(Map.of(
                CallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                CallbackVerifier.EVENT_ID_HEADER, "EVENT-202"));

        ChannelCallbackResult result = new CallbackHandler().handle(request);

        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.PROCESSING.getCode());
    }

    /**
     * 退款回调必须使用 refundId 关联退款动作，不能把原支付 transactionId 当作退款交易号。
     **/
    @Test
    void shouldUseRefundIdForRefundCallbackIdentity() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("""
                {
                  "eventId":"REFUND-EVENT-001",
                  "eventType":"REFUND_CALLBACK",
                  "merchantId":"MID-TEST",
                  "merchantOrderNo":"CMT-ORIGINAL-PAYMENT",
                  "transactionId":"CMT-ORIGINAL-PAYMENT",
                  "merchantRefundNo":"REFUND-001",
                  "refundId":"CMR-001",
                  "status":"succeeded",
                  "amount":10.25,
                  "currency":"USD"
                }
                """);
        request.setHeaders(Map.of(
                CallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                CallbackVerifier.EVENT_ID_HEADER, "REFUND-EVENT-001"));

        ChannelCallbackResult result = new CallbackHandler().handle(request);

        assertThat(result.getChannelOrderNo()).isEqualTo("CMT-ORIGINAL-PAYMENT");
        assertThat(result.getChannelTransactionId()).isEqualTo("CMR-001");
        assertThat(result.getExtension()).containsEntry("merchantRefundNo", "REFUND-001");
        assertThat(result.getExtension()).containsEntry("refundId", "CMR-001");
    }

    @Test
    void shouldMapCardAuthorizationCallbackIdentityAndReferences() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("""
                {
                  "eventId":"CMT-CHANNEL-001:SUCCESS",
                  "eventType":"CARD_AUTH_CALLBACK",
                  "merchantId":"MID-TEST",
                  "merchantOrderNo":"CMT-CAPTURE-001",
                  "authorizationId":"CMA-001",
                  "transactionId":"CMT-CHANNEL-001",
                  "operationType":"CAPTURE",
                  "status":"succeeded",
                  "amount":10.25,
                  "currency":"USD",
                  "authorizationStatus":"PARTIALLY_CAPTURED",
                  "responseCode":"SUCCESS",
                  "authorizationCode":"000101",
                  "acquirerReferenceNumber":"20261031000000000000101"
                }
                """);
        request.setHeaders(Map.of(
                CallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                CallbackVerifier.EVENT_ID_HEADER, "CMT-CHANNEL-001:SUCCESS"));

        ChannelCallbackResult result = new CallbackHandler().handle(request);

        assertThat(result.getChannelOrderNo()).isEqualTo("CMA-001");
        assertThat(result.getChannelTransactionId()).isEqualTo("CMT-CHANNEL-001");
        assertThat(result.getExtension())
                .containsEntry("merchantOrderNo", "CMT-CAPTURE-001")
                .containsEntry("authorizationId", "CMA-001")
                .containsEntry("operationType", "CAPTURE")
                .containsEntry("authorizationCode", "000101")
                .containsEntry("acquirerReferenceNumber", "20261031000000000000101");
    }

    /**
     * Header 与正文事件号不一致时必须拒绝，避免同一报文被伪造成另一条回调事件。
     **/
    @Test
    void shouldRejectMismatchedCallbackEventId() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("{\"eventId\":\"EVENT-BODY\",\"merchantId\":\"MID-TEST\",\"status\":\"succeeded\"}");
        request.setHeaders(Map.of(
                CallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                CallbackVerifier.EVENT_ID_HEADER, "EVENT-HEADER"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CallbackHandler().handle(request))
                .isInstanceOf(com.scott.payment.channel.payment.exception.ChannelRequestException.class)
                .hasMessageContaining("event id");
    }

    /**
     * 正文缺少渠道要求的 eventId 时必须拒绝，避免无法建立回调幂等键。
     **/
    @Test
    void shouldRejectMissingBodyEventId() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("{\"merchantId\":\"MID-TEST\",\"status\":\"succeeded\"}");
        request.setHeaders(Map.of(
                CallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                CallbackVerifier.EVENT_ID_HEADER, "EVENT-HEADER"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CallbackHandler().handle(request))
                .isInstanceOf(com.scott.payment.channel.payment.exception.ChannelRequestException.class)
                .hasMessageContaining("eventId");
    }

    /**
     * Header 与正文 MID 不一致时必须拒绝，避免跨 MID 回调污染交易。
     **/
    @Test
    void shouldRejectMismatchedMerchantId() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("{\"eventId\":\"EVENT-001\",\"merchantId\":\"MID-BODY\",\"status\":\"succeeded\"}");
        request.setHeaders(Map.of(
                CallbackVerifier.MERCHANT_ID_HEADER, "MID-HEADER",
                CallbackVerifier.EVENT_ID_HEADER, "EVENT-001"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CallbackHandler().handle(request))
                .isInstanceOf(com.scott.payment.channel.payment.exception.ChannelRequestException.class)
                .hasMessageContaining("merchant id");
    }

    @Test
    void shouldRejectMalformedCallbackJsonAsChannelRequestError() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("not-json");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CallbackHandler().handle(request))
                .isInstanceOf(com.scott.payment.channel.payment.exception.ChannelRequestException.class)
                .hasMessageContaining("body is invalid");
    }

    @Test
    void shouldIgnoreUnknownCallbackFields() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("{\"eventId\":\"EVENT-UNKNOWN\",\"merchantId\":\"MID-TEST\",\"merchantOrderNo\":\"ORDER-001\",\"transactionId\":\"TX-001\",\"status\":\"succeeded\",\"futureField\":{\"nested\":true}}");
        request.setHeaders(Map.of(
                CallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                CallbackVerifier.EVENT_ID_HEADER, "EVENT-UNKNOWN"));

        ChannelCallbackResult result = new CallbackHandler().handle(request);

        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.SUCCESS.getCode());
        assertThat(result.getChannelTransactionId()).isEqualTo("TX-001");
    }
}
