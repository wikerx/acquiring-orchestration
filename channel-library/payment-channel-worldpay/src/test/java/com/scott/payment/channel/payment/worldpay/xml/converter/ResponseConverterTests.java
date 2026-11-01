package com.scott.payment.channel.payment.worldpay.xml.converter;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Worldpay XML 响应的兼容解析、错误优先级、状态映射和金额精度。
 */
class ResponseConverterTests {

    /**
     * Worldpay XML 响应转换器。
     */
    private final ResponseConverter converter = new ResponseConverter();

    /**
     * 渠道新增未知 XML 节点时应忽略该节点并继续映射已知支付结果。
     */
    @Test
    void shouldIgnoreUnknownResponseElements() {
        ChannelPaymentResponse response = converter.toChannelResponse(request(), """
                <paymentService version="1.4" merchantCode="MERCHANT-001">
                  <reply>
                    <providerAddedNode><nested value="true"/></providerAddedNode>
                    <orderStatus orderCode="ORDER-001">
                      <payment id="PAYMENT-001" lastEvent="AUTHORISED">
                        <ISO8583ReturnCode code="0" description="Approved"/>
                      </payment>
                    </orderStatus>
                  </reply>
                </paymentService>
                """);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.AUTHORIZED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("0");
        assertThat(response.getChannelTransactionId()).isEqualTo("PAYMENT-001");
    }

    /**
     * 可选节点缺失时应保留处理中状态，不得因空节点抛出异常或确认失败。
     */
    @Test
    void shouldKeepMissingOptionalElementsProcessing() {
        ChannelPaymentResponse response = converter.toChannelResponse(request(), """
                <paymentService version="1.4" merchantCode="MERCHANT-001">
                  <reply/>
                </paymentService>
                """);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.PROCESSING.getCode());
        assertThat(response.getChannelResponseCode()).isNull();
    }

    /**
     * error 节点必须优先于同时存在的支付结果，避免把协议错误误判为支付成功。
     */
    @Test
    void shouldPrioritizeErrorNode() {
        ChannelPaymentResponse response = converter.toChannelResponse(request(), """
                <paymentService version="1.4" merchantCode="MERCHANT-001">
                  <reply>
                    <orderStatus orderCode="ORDER-001">
                      <payment id="PAYMENT-001" lastEvent="AUTHORISED">
                        <ISO8583ReturnCode code="0" description="Approved"/>
                      </payment>
                    </orderStatus>
                    <error code="AUTHENTICATION_FAILED">Invalid credentials</error>
                  </reply>
                </paymentService>
                """);

        assertThat(response.getRawChannelStatus()).isEqualTo("ERROR");
        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.FAILED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Invalid credentials");
    }

    /**
     * 已提交退款只表示渠道受理，必须保持待处理状态等待查询或回调确认。
     */
    @Test
    void shouldKeepSentForRefundPending() {
        ChannelPaymentResponse response = converter.toChannelResponse(request(), """
                <paymentService version="1.4" merchantCode="MERCHANT-001">
                  <reply>
                    <orderStatus orderCode="ORDER-001">
                      <payment id="PAYMENT-001" lastEvent="SENT_FOR_REFUND"/>
                    </orderStatus>
                  </reply>
                </paymentService>
                """);

        assertThat(response.getRawChannelStatus()).isEqualTo("SENT_FOR_REFUND");
        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.PENDING.getCode());
    }

    /**
     * XML 最小辅币单位金额必须按 exponent 还原为平台主币单位金额。
     */
    @Test
    void shouldConvertMinorAmountByExponent() {
        ChannelPaymentResponse response = converter.toChannelResponse(request(), """
                <paymentService version="1.4" merchantCode="MERCHANT-001">
                  <reply>
                    <orderStatus orderCode="ORDER-001">
                      <payment id="PAYMENT-001" lastEvent="CAPTURED">
                        <amount value="12345" currencyCode="bhd" exponent="3"/>
                      </payment>
                    </orderStatus>
                  </reply>
                </paymentService>
                """);

        assertThat(response.getChannelCurrency()).isEqualTo("BHD");
        assertThat(response.getChannelAmount()).isEqualByComparingTo("12.345");
    }

    /**
     * 构造带平台与渠道订单标识的测试请求。
     *
     * @return 平台统一渠道请求
     */
    private ChannelPaymentRequest request() {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setOperationId("OP-001");
        request.setTransactionId("TX-001");
        request.setChannelOrderNo("ORDER-001");
        return request;
    }
}
