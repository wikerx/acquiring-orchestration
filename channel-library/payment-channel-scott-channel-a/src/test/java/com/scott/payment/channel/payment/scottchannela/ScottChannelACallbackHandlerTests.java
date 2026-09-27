package com.scott.payment.channel.payment.scottchannela;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelACallbackHandlerTests
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 回调正文解析测试，确认小写终态、订单标识、金额和事件 Header 映射到统一回调结果。
 * @status : create
 */
class ScottChannelACallbackHandlerTests {

    /** 验证渠道回调状态和核心交易字段映射。 */
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
                ScottChannelACallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                ScottChannelACallbackVerifier.EVENT_ID_HEADER, "EVENT-001"));

        ChannelCallbackResult result = new ScottChannelACallbackHandler().handle(request);

        assertThat(result.getChannelCode()).isEqualTo(ScottChannelACode.CODE);
        assertThat(result.getCallbackEventId()).isEqualTo("EVENT-001");
        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.SUCCESS.getCode());
        assertThat(result.getChannelOrderNo()).isEqualTo("ORDER-001");
        assertThat(result.getAmount()).isEqualByComparingTo("10.25");
        assertThat(result.getExtension()).containsEntry("paymentMethod", "CARD");
    }

    /** 渠道以平台处理中码回调时，应保留处理中状态等待查询或后续终态回调。 */
    @Test
    void shouldMapProcessingCallbackCode() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("{\"eventId\":\"EVENT-202\",\"merchantId\":\"MID-TEST\",\"merchantOrderNo\":\"CMT-001\",\"status\":\"T202\",\"responseCode\":\"T202\"}");
        request.setHeaders(Map.of(
                ScottChannelACallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                ScottChannelACallbackVerifier.EVENT_ID_HEADER, "EVENT-202"));

        ChannelCallbackResult result = new ScottChannelACallbackHandler().handle(request);

        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.PROCESSING.getCode());
    }

    /** 退款回调必须使用 refundId 关联退款动作，不能把原支付 transactionId 当作退款交易号。 */
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
                ScottChannelACallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                ScottChannelACallbackVerifier.EVENT_ID_HEADER, "REFUND-EVENT-001"));

        ChannelCallbackResult result = new ScottChannelACallbackHandler().handle(request);

        assertThat(result.getChannelOrderNo()).isEqualTo("CMT-ORIGINAL-PAYMENT");
        assertThat(result.getChannelTransactionId()).isEqualTo("CMR-001");
        assertThat(result.getExtension()).containsEntry("merchantRefundNo", "REFUND-001");
        assertThat(result.getExtension()).containsEntry("refundId", "CMR-001");
    }

    /** Header 与正文事件号不一致时必须拒绝，避免同一报文被伪造成另一条回调事件。 */
    @Test
    void shouldRejectMismatchedCallbackEventId() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("{\"eventId\":\"EVENT-BODY\",\"merchantId\":\"MID-TEST\",\"status\":\"succeeded\"}");
        request.setHeaders(Map.of(
                ScottChannelACallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                ScottChannelACallbackVerifier.EVENT_ID_HEADER, "EVENT-HEADER"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ScottChannelACallbackHandler().handle(request))
                .isInstanceOf(com.scott.payment.channel.payment.exception.ChannelRequestException.class)
                .hasMessageContaining("event id");
    }

    /** 正文缺少渠道要求的 eventId 时必须拒绝，避免无法建立回调幂等键。 */
    @Test
    void shouldRejectMissingBodyEventId() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("{\"merchantId\":\"MID-TEST\",\"status\":\"succeeded\"}");
        request.setHeaders(Map.of(
                ScottChannelACallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                ScottChannelACallbackVerifier.EVENT_ID_HEADER, "EVENT-HEADER"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ScottChannelACallbackHandler().handle(request))
                .isInstanceOf(com.scott.payment.channel.payment.exception.ChannelRequestException.class)
                .hasMessageContaining("eventId");
    }

    /** Header 与正文 MID 不一致时必须拒绝，避免跨 MID 回调污染交易。 */
    @Test
    void shouldRejectMismatchedMerchantId() {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setBody("{\"eventId\":\"EVENT-001\",\"merchantId\":\"MID-BODY\",\"status\":\"succeeded\"}");
        request.setHeaders(Map.of(
                ScottChannelACallbackVerifier.MERCHANT_ID_HEADER, "MID-HEADER",
                ScottChannelACallbackVerifier.EVENT_ID_HEADER, "EVENT-001"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ScottChannelACallbackHandler().handle(request))
                .isInstanceOf(com.scott.payment.channel.payment.exception.ChannelRequestException.class)
                .hasMessageContaining("merchant id");
    }
}
