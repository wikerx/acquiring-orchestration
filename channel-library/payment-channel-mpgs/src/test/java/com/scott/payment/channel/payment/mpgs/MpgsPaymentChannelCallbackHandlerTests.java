package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.ChannelCallbackKind;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsPaymentChannelCallbackHandlerTests
 * @date : 2026-07-14 23:55
 * @email : scott_x@163.com
 * @description : MPGS 渠道回调处理器测试，验证 order.id、transaction.id、result 和 acquirerCode 的解析与渠道统一状态映射。
 * @status : create
 */
class MpgsPaymentChannelCallbackHandlerTests {

    private final MpgsPaymentChannelCallbackHandler handler = new MpgsPaymentChannelCallbackHandler();

    /**
     * MPGS 回调只有 result=SUCCESS 且 response.acquirerCode=00 时才映射为渠道成功。
     */
    @Test
    void shouldParseApprovedMpgsCallback() {
        ChannelCallbackResult result = handler.handle(request("""
                {
                  "result": "SUCCESS",
                  "order": {"id": "TX202607141000000000001", "status": "AUTHORIZED", "amount": "1.00", "currency": "USD"},
                  "transaction": {"id": "CH202607141000000000001", "type": "AUTHORIZATION"},
                  "response": {"gatewayCode": "APPROVED", "acquirerCode": "00", "acquirerMessage": "Approved"}
                }
                """));

        assertThat(result.getChannelOrderNo()).isEqualTo("TX202607141000000000001");
        assertThat(result.getChannelTransactionId()).isEqualTo("CH202607141000000000001");
        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.SUCCESS.getCode());
        assertThat(result.getChannelResponseCode()).isEqualTo("00");
        assertThat(result.getRawChannelStatus()).isEqualTo("AUTHORIZED");
        assertThat(result.getAmount()).isEqualByComparingTo("1.00");
        assertThat(result.getCurrency()).isEqualTo("USD");
    }

    /**
     * MPGS 顶层 result=SUCCESS 但收单响应非 00 时，必须映射为渠道失败。
     */
    @Test
    void shouldMapResultSuccessWithDeclinedAcquirerCodeAsFailed() {
        ChannelCallbackResult result = handler.handle(request("""
                {
                  "result": "SUCCESS",
                  "order": {"id": "TX202607141000000000002", "status": "DECLINED"},
                  "transaction": {"id": "CH202607141000000000002", "type": "AUTHORIZATION"},
                  "response": {"gatewayCode": "DECLINED", "acquirerCode": "14", "acquirerMessage": "Invalid card number"}
                }
                """));

        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.FAILED.getCode());
        assertThat(result.getChannelResponseCode()).isEqualTo("14");
        assertThat(result.getChannelResponseMessage()).isEqualTo("Invalid card number");
        assertThat(result.getExtension()).containsEntry("gatewayCode", "DECLINED");
        assertThat(result.getExtension()).containsEntry("acquirerCode", "14");
    }

    /**
     * MPGS 3DS Method completion callback 可能以表单 POST 方式发送，只表示 3DS Method 已完成，不代表支付终态。
     */
    @Test
    void shouldParseMpgsThreeDsMethodCompletionFormCallbackAsPending() {
        ChannelCallbackResult result = handler.handle(request(
                "threeDSServerTransID=7f880d1d-6d8d-4d7a-83af-7465d3f0c1b8"
                        + "&threeDSSessionData=encrypted-session-data"
                        + "&orderId=TX202607141000000000003"));

        assertThat(result.getCallbackEventId()).isEqualTo("7f880d1d-6d8d-4d7a-83af-7465d3f0c1b8");
        assertThat(result.getChannelOrderNo()).isEqualTo("TX202607141000000000003");
        assertThat(result.getChannelTransactionId()).isEqualTo("7f880d1d-6d8d-4d7a-83af-7465d3f0c1b8");
        assertThat(result.getRawChannelStatus()).isEqualTo("3DS_METHOD_COMPLETED");
        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.PENDING.getCode());
        assertThat(result.getChannelResponseCode()).isEqualTo("3DS_METHOD_COMPLETED");
        assertThat(result.getExtension()).containsEntry("threeDsServerTransactionId", "7f880d1d-6d8d-4d7a-83af-7465d3f0c1b8");
        assertThat(result.getExtension()).containsEntry("callbackKind", "3DS_METHOD_COMPLETION");
        assertThat(result.getCallbackKind()).isEqualTo(ChannelCallbackKind.THREE_DS_AUTHENTICATION);
    }

    /**
     * MPGS challenge 完成后的回跳/回调可能携带 result 与 gatewayRecommendation，仍应等待后续认证/支付结果确认。
     */
    @Test
    void shouldParseMpgsThreeDsChallengeReturnJsonCallbackAsPending() {
        ChannelCallbackResult result = handler.handle(request("""
                {
                  "orderId": "TX202607141000000000004",
                  "transactionId": "AUTHENTICATE_PAYER_001",
                  "result": "SUCCESS",
                  "response": {"gatewayRecommendation": "PROCEED"}
                }
                """));

        assertThat(result.getChannelOrderNo()).isEqualTo("TX202607141000000000004");
        assertThat(result.getChannelTransactionId()).isEqualTo("AUTHENTICATE_PAYER_001");
        assertThat(result.getRawChannelStatus()).isEqualTo("PROCEED");
        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.PENDING.getCode());
        assertThat(result.getChannelResponseCode()).isEqualTo("PROCEED");
        assertThat(result.getChannelResponseMessage()).isEqualTo("3DS payer authentication callback received");
        assertThat(result.getExtension()).containsEntry("callbackKind", "3DS_PAYER_AUTHENTICATION");
    }

    @Test
    void shouldUseNotificationIdAsWebhookIdempotencyKey() {
        ChannelCallbackRequest request = request("""
                {
                  "order": {"id": "TX202607141000000000005", "status": "AUTHENTICATION_INITIATED"},
                  "transaction": {"id": "3DS-TX-005"},
                  "response": {"gatewayRecommendation": "PROCEED"}
                }
                """);
        request.setHeaders(Map.of(
                MpgsCallbackVerifier.NOTIFICATION_ID_HEADER, "notification-005",
                MpgsCallbackVerifier.NOTIFICATION_ATTEMPT_HEADER, "2"));

        ChannelCallbackResult result = handler.handle(request);

        assertThat(result.getCallbackEventId()).isEqualTo("notification-005");
        assertThat(result.getExtension()).containsEntry("notificationAttempt", "2");
    }

    /**
     * order.notificationUrl 属于订单级 Webhook，同一路径也会收到后续 PAY 通知；
     * transaction.type=PAYMENT 时必须按资金事件解析，不能仅因 URL 包含 /3ds 而停在认证阶段。
     */
    @Test
    void shouldParsePaymentWebhookOnThreeDsNotificationUrlAsFinancialCallback() {
        ChannelCallbackRequest request = request("""
                {
                  "result": "SUCCESS",
                  "order": {"id": "TX202607141000000000006", "status": "CAPTURED", "amount": "44.37", "currency": "USD"},
                  "transaction": {"id": "CH202607141000000000006", "type": "PAYMENT"},
                  "response": {"gatewayCode": "APPROVED", "acquirerCode": "00", "acquirerMessage": "Approved"}
                }
                """);
        request.setRequestUri("/channel/v1/callbacks/MPGS/3ds");

        ChannelCallbackResult result = handler.handle(request);

        assertThat(result.getChannelOrderNo()).isEqualTo("TX202607141000000000006");
        assertThat(result.getChannelTransactionId()).isEqualTo("CH202607141000000000006");
        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.SUCCESS.getCode());
        assertThat(result.getChannelResponseCode()).isEqualTo("00");
        assertThat(result.getExtension()).containsEntry("transactionType", "PAYMENT");
        assertThat(result.getExtension()).doesNotContainEntry("callbackKind", "3DS_PAYER_AUTHENTICATION");
        assertThat(result.getCallbackKind()).isEqualTo(ChannelCallbackKind.FINANCIAL_TRANSACTION);
    }

    private ChannelCallbackRequest request(String body) {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setChannelCode("MPGS");
        request.setRequestUri("/channel/v1/callbacks/MPGS");
        request.setClientIp("127.0.0.1");
        request.setBody(body);
        return request;
    }
}
