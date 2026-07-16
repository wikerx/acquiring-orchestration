package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    private ChannelCallbackRequest request(String body) {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setChannelCode("MPGS");
        request.setRequestUri("/channel/v1/callbacks/MPGS");
        request.setClientIp("127.0.0.1");
        request.setBody(body);
        return request;
    }
}
