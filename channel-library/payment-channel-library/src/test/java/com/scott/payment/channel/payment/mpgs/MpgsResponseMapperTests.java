package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsResponseMapperTests
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 响应映射测试，验证 result、response.acquirerCode、错误原因和 rawResponse 的统一映射规则。
 * @status : create
 */
class MpgsResponseMapperTests {

    private final MpgsResponseMapper mapper = new MpgsResponseMapper();

    /**
     * 验证 MPGS 交易成功响应映射：result=SUCCESS 且 response.acquirerCode=00 时，渠道状态才允许映射为 SUCCESS。
     */
    @Test
    void shouldMapSuccessResponse() {
        MpgsResponsePayload payload = successPayload("SUCCESS");

        ChannelPaymentResponse response = mapper.toChannelResponse(request(), payload);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.SUCCESS.getCode());
        assertThat(response.getRawChannelStatus()).isEqualTo("SUCCESS");
        assertThat(response.getChannelResponseCode()).isEqualTo("APPROVED");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Approved");
        assertThat(response.getAuthCode()).isEqualTo("123456");
        assertThat(response.getRrn()).isEqualTo("RCPT001");
        assertThat(response.getAcquirerReferenceNo()).isEqualTo("REF001");
        assertThat(response.getRawResponse()).containsEntry("transactionId", "TX-001");
        assertThat(response.getRawResponse()).containsEntry("acquirerCode", "00");
    }

    /**
     * 验证 MPGS 网关成功但收单拒绝的场景：result=SUCCESS 不能单独代表交易成功，acquirerCode 非 00 必须映射为 FAILED。
     */
    @Test
    void shouldMapResultSuccessWithNonApprovedAcquirerCodeAsFailed() {
        MpgsResponsePayload payload = successPayload("SUCCESS");
        payload.getResponse().setGatewayCode("DECLINED");
        payload.getResponse().setAcquirerCode("14");
        payload.getResponse().setAcquirerMessage("Invalid card number");

        ChannelPaymentResponse response = mapper.toChannelResponse(request(), payload);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.FAILED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("DECLINED");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Invalid card number");
        assertThat(response.getRawResponse()).containsEntry("acquirerCode", "14");
        assertThat(response.getRawResponse()).containsEntry("acquirerMessage", "Invalid card number");
    }

    /**
     * 验证 MPGS UNKNOWN 结果映射：渠道结果不明确时保持 PROCESSING，交由平台状态机和查询补偿继续推进。
     */
    @Test
    void shouldMapUnknownAsProcessing() {
        MpgsResponsePayload payload = successPayload("UNKNOWN");

        ChannelPaymentResponse response = mapper.toChannelResponse(request(), payload);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.PROCESSING.getCode());
    }

    /**
     * 验证 MPGS 错误响应映射：保留渠道真实失败原因，供后台排查；商户和付款人展示文案由上层服务再做模糊化。
     */
    @Test
    void shouldMapErrorResponse() {
        MpgsResponsePayload payload = new MpgsResponsePayload();
        payload.setResult("ERROR");
        MpgsResponsePayload.ErrorPayload error = new MpgsResponsePayload.ErrorPayload();
        error.setCause("INVALID_REQUEST");
        error.setExplanation("Invalid card");
        payload.setError(error);

        ChannelPaymentResponse response = mapper.toChannelResponse(request(), payload);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.FAILED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Invalid card");
        assertThat(response.getRawResponse()).containsEntry("errorCause", "INVALID_REQUEST");
        assertThat(response.getRawResponse()).containsEntry("errorExplanation", "Invalid card");
    }

    private MpgsResponsePayload successPayload(String result) {
        MpgsResponsePayload payload = new MpgsResponsePayload();
        payload.setResult(result);
        MpgsResponsePayload.Response response = new MpgsResponsePayload.Response();
        response.setGatewayCode("APPROVED");
        response.setAcquirerCode("00");
        response.setAcquirerMessage("Approved");
        payload.setResponse(response);
        MpgsResponsePayload.Transaction transaction = new MpgsResponsePayload.Transaction();
        transaction.setId("TX-001");
        transaction.setAuthorizationCode("123456");
        transaction.setReceipt("RCPT001");
        transaction.setReference("REF001");
        payload.setTransaction(transaction);
        return payload;
    }

    private ChannelPaymentRequest request() {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setOperationId("OP-001");
        request.setTransactionId("TX-001");
        request.setMerchantOrderNo("MER-ORDER-001");
        return request;
    }
}
