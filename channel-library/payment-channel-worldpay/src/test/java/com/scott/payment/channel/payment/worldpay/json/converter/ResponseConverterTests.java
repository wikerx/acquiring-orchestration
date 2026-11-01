package com.scott.payment.channel.payment.worldpay.json.converter;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.worldpay.json.model.response.ResponsePayload;
import com.scott.payment.component.core.json.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Worldpay JSON 响应的兼容解析、响应码优先级和状态映射。
 */
class ResponseConverterTests {

    private final ResponseConverter converter = new ResponseConverter();

    /**
     * 渠道新增未知字段时应忽略该字段并继续映射已知授权结果。
     */
    @Test
    void shouldIgnoreUnknownResponseFields() {
        ResponsePayload payload = JsonUtils.parseObject("""
                {
                  "status": "authorized",
                  "responseCode": "00",
                  "resultMessage": "Approved",
                  "paymentId": "PAY-001",
                  "providerAddedField": {"nested": true}
                }
                """, ResponsePayload.class);

        ChannelPaymentResponse response = converter.toChannelResponse(request(), payload);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.AUTHORIZED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("00");
        assertThat(response.getChannelTransactionId()).isEqualTo("PAY-001");
    }

    /**
     * 可选节点缺失时应保留未知结果，不得因空字段抛出异常或确认失败。
     */
    @Test
    void shouldKeepMissingOptionalFieldsProcessing() {
        ResponsePayload payload = JsonUtils.parseObject("{}", ResponsePayload.class);

        ChannelPaymentResponse response = converter.toChannelResponse(request(), payload);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.PROCESSING.getCode());
        assertThat(response.getChannelResponseCode()).isNull();
    }

    /**
     * error 节点必须优先于顶层通用响应码，并明确映射为失败。
     */
    @Test
    void shouldPrioritizeErrorCodeAndMessage() {
        ResponsePayload payload = JsonUtils.parseObject("""
                {
                  "responseCode": "GENERIC_ERROR",
                  "outcome": "unknown",
                  "error": {"code": "AUTHENTICATION_FAILED", "message": "Invalid credentials"}
                }
                """, ResponsePayload.class);

        ChannelPaymentResponse response = converter.toChannelResponse(request(), payload);

        assertThat(response.getRawChannelStatus()).isEqualTo("UNKNOWN");
        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.PENDING.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Invalid credentials");
    }

    /**
     * 明确拒绝码必须优先于通用结果码并映射为失败。
     */
    @Test
    void shouldPrioritizeRefusalCodeAndDescription() {
        ResponsePayload payload = JsonUtils.parseObject("""
                {
                  "resultCode": "FAILED",
                  "refusalCode": "DO_NOT_HONOUR",
                  "refusalDescription": "Issuer declined"
                }
                """, ResponsePayload.class);

        ChannelPaymentResponse response = converter.toChannelResponse(request(), payload);

        assertThat(response.getRawChannelStatus()).isEqualTo("REFUSED");
        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.FAILED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("DO_NOT_HONOUR");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Issuer declined");
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
