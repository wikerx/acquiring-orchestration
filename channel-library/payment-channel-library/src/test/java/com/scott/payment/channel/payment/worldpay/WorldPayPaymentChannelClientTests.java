package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayPaymentChannelClientTests
 * @date : 2026-07-19 23:30
 * @email : scott_x@163.com
 * @description : WorldPay 渠道客户端边界测试，验证 WPGXML/WPGJSON 当前只是独立 SPI 占位入口，真实交易请求未接通时必须明确阻断调用。
 * @status : create
 */
class WorldPayPaymentChannelClientTests {

    /**
     * WPGXML 当前只注册独立渠道编码和计划能力，真实 XML 请求未实现前不能发起支付。
     */
    @Test
    void shouldBlockWorldPayXmlRealPaymentBeforeApiConnected() {
        WorldPayXmlPaymentChannelClient client = new WorldPayXmlPaymentChannelClient();
        ChannelPaymentRequest request = request(PaymentChannelCode.WPGXML.getCode(), "PAYMENT");

        assertThat(client.channelCode()).isEqualTo(PaymentChannelCode.WPGXML.getCode());
        assertThatThrownBy(() -> client.payment(request))
                .isInstanceOf(WorldPayChannelNotImplementedException.class)
                .hasMessageContaining("WorldPay渠道[WPGXML]真实请求尚未接通")
                .hasMessageContaining("禁止用于生产交易能力[PAYMENT]");
    }

    /**
     * WPGJSON 当前只注册独立渠道编码和计划能力，真实 JSON 请求未实现前不能发起请款。
     */
    @Test
    void shouldBlockWorldPayJsonRealCaptureBeforeApiConnected() {
        WorldPayJsonPaymentChannelClient client = new WorldPayJsonPaymentChannelClient();
        ChannelCaptureRequest request = new ChannelCaptureRequest();
        request.setChannelCode(PaymentChannelCode.WPGJSON.getCode());
        request.setTransactionType("CAPTURE");

        assertThat(client.channelCode()).isEqualTo(PaymentChannelCode.WPGJSON.getCode());
        assertThatThrownBy(() -> client.capture(request))
                .isInstanceOf(WorldPayChannelNotImplementedException.class)
                .hasMessageContaining("WorldPay渠道[WPGJSON]真实请求尚未接通")
                .hasMessageContaining("禁止用于生产交易能力[CAPTURE]");
    }

    /**
     * 查询勾兑任务存在不代表 WPGXML/WPGJSON Inquiry 已接通，查询入口也必须在真实实现前阻断。
     */
    @Test
    void shouldBlockWorldPayInquiryBeforeQueryApiConnected() {
        WorldPayXmlPaymentChannelClient client = new WorldPayXmlPaymentChannelClient();
        ChannelQueryRequest request = new ChannelQueryRequest();
        request.setChannelCode(PaymentChannelCode.WPGXML.getCode());
        request.setTransactionType("QUERY");

        assertThatThrownBy(() -> client.query(request))
                .isInstanceOf(WorldPayChannelNotImplementedException.class)
                .hasMessageContaining("真实请求尚未接通")
                .hasMessageContaining("生产交易能力[QUERY]");
    }

    private ChannelPaymentRequest request(String channelCode, String transactionType) {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(channelCode);
        request.setTransactionType(transactionType);
        return request;
    }
}
