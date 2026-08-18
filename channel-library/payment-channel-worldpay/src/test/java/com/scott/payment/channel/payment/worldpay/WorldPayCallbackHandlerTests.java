package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayCallbackHandlerTests
 * @date : 2026-07-19 23:05
 * @email : scott_x@163.com
 * @description : WorldPay 回调处理器测试，验证 WPGXML/WPGJSON 通知基础字段解析和渠道统一状态归一。
 * @status : create
 */
class WorldPayCallbackHandlerTests {

    /**
     * WorldPay XML 通知应从 orderStatusEvent、payment、ISO8583 和 amount 节点解析订单号、交易号、金额和 captured 状态。
     */
    @Test
    void shouldParseWorldPayXmlCapturedNotification() {
        WorldPayXmlCallbackHandler handler = new WorldPayXmlCallbackHandler();

        ChannelCallbackResult result = handler.handle(request("WPGXML", """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE paymentService PUBLIC "-//WorldPay//DTD WorldPay PaymentService v1//EN" "http://dtd.worldpay.com/paymentService_v1.dtd">
                <paymentService version="1.4" merchantCode="MERCHANT">
                  <notify>
                    <orderStatusEvent orderCode="TX202607190001" eventId="EVT-XML-001">
                      <payment id="WP-PAY-001" lastEvent="CAPTURED">
                        <amount value="1234" currencyCode="USD" exponent="2"/>
                        <AuthorisationId id="AUTH-XML-001"/>
                        <ISO8583ReturnCode code="0" description="Captured"/>
                        <stan>654321</stan>
                        <CVCResultCode description="APPROVED"/>
                      </payment>
                    </orderStatusEvent>
                  </notify>
                </paymentService>
                """));

        assertThat(result.getChannelCode()).isEqualTo("WPGXML");
        assertThat(result.getChannelOrderNo()).isEqualTo("TX202607190001");
        assertThat(result.getChannelTransactionId()).isEqualTo("WP-PAY-001");
        assertThat(result.getRawChannelStatus()).isEqualTo("CAPTURED");
        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.CAPTURED.getCode());
        assertThat(result.getChannelResponseCode()).isEqualTo("0");
        assertThat(result.getChannelResponseMessage()).isEqualTo("Captured");
        assertThat(result.getAmount()).isEqualByComparingTo("12.34");
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.isSignatureValid()).isFalse();
        assertThat(result.getExtension())
                .containsEntry("orderCode", "TX202607190001")
                .containsEntry("eventId", "EVT-XML-001")
                .containsEntry("authorizationCode", "AUTH-XML-001")
                .containsEntry("stan", "654321");
    }

    /**
     * WorldPay JSON 通知中 AUTHORISED 归一为统一授权完成状态，平台是否终态由 service-payment 结合交易类型决定。
     */
    @Test
    void shouldParseWorldPayJsonAuthorisedNotification() {
        WorldPayJsonCallbackHandler handler = new WorldPayJsonCallbackHandler();

        ChannelCallbackResult result = handler.handle(request("WPGJSON", """
                {
                  "orderCode": "TX202607190002",
                  "paymentId": "WP-PAY-002",
                  "lastEvent": "AUTHORISED",
                  "responseCode": "0",
                  "responseMessage": "Authorised",
                  "amount": "10.25",
                  "currencyCode": "EUR"
                }
                """));

        assertThat(result.getChannelCode()).isEqualTo("WPGJSON");
        assertThat(result.getChannelOrderNo()).isEqualTo("TX202607190002");
        assertThat(result.getChannelTransactionId()).isEqualTo("WP-PAY-002");
        assertThat(result.getRawChannelStatus()).isEqualTo("AUTHORISED");
        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.AUTHORIZED.getCode());
        assertThat(result.getChannelResponseCode()).isEqualTo("0");
        assertThat(result.getChannelResponseMessage()).isEqualTo("Authorised");
        assertThat(result.getAmount()).isEqualByComparingTo("10.25");
        assertThat(result.getCurrency()).isEqualTo("EUR");
        assertThat(result.isSignatureValid()).isFalse();
        assertThat(result.getExtension()).containsEntry("lastEvent", "AUTHORISED");
    }

    /**
     * Access Worldpay JSON 事件通知应从嵌套 summary/payment/value 节点解析交易引用、渠道交易号、金额和资金确认状态。
     */
    @Test
    void shouldParseAccessWorldpayNestedJsonEvent() {
        WorldPayJsonCallbackHandler handler = new WorldPayJsonCallbackHandler();

        ChannelCallbackResult result = handler.handle(request("WPGJSON", """
                {
                  "eventId": "evt_202607260001",
                  "eventType": "sentForSettlement",
                  "merchant": {"entity": "AWAPGTEST"},
                  "summary": {
                    "transactionReference": "TX202607260000000000001",
                    "paymentId": "WP-PAY-202607260001",
                    "outcome": "sentForSettlement",
                    "value": {
                      "amount": "1250",
                      "currency": "USD",
                      "exponent": "2"
                    }
                  }
                }
                """));

        assertThat(result.getChannelCode()).isEqualTo("WPGJSON");
        assertThat(result.getChannelOrderNo()).isEqualTo("TX202607260000000000001");
        assertThat(result.getChannelTransactionId()).isEqualTo("WP-PAY-202607260001");
        assertThat(result.getRawChannelStatus()).isEqualTo("CAPTURED");
        assertThat(result.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.CAPTURED.getCode());
        assertThat(result.getAmount()).isEqualByComparingTo("12.50");
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getExtension())
                .containsEntry("eventId", "evt_202607260001")
                .containsEntry("merchantEntity", "AWAPGTEST")
                .containsEntry("transactionReference", "TX202607260000000000001")
                .containsEntry("paymentId", "WP-PAY-202607260001");
    }

    private ChannelCallbackRequest request(String channelCode, String body) {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setChannelCode(channelCode);
        request.setRequestUri("/channel/v1/callbacks/" + channelCode);
        request.setClientIp("127.0.0.1");
        request.setBody(body);
        return request;
    }
}
