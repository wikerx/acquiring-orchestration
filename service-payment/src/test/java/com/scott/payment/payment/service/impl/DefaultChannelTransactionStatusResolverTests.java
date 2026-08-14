package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.payment.domain.state.PaymentPendingReasonEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultChannelTransactionStatusResolverTests
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 验证支付核心只根据渠道统一动作状态和平台交易类型解析终态，不识别具体 provider 编码或原始协议状态。
 * @status : create
 */
class DefaultChannelTransactionStatusResolverTests {

    /** 一步支付只有统一 AUTHORIZED 时仍需等待资金捕获结果。 */
    @Test
    void shouldKeepPaymentPendingWhenUnifiedChannelStatusIsAuthorized() {
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCode("PROVIDER_A");
        response.setChannelTradeStatus("AUTHORIZED");
        response.setRawChannelStatus("provider-specific-authorized");

        ChannelTransactionStatusResolution resolution = new DefaultChannelTransactionStatusResolver()
                .resolveSync("PROVIDER_A", PaymentTransactionTypeEnum.PAYMENT.getCode(), response);

        assertThat(resolution.getTargetStatus()).isEqualTo(PaymentTransactionStatusEnum.PENDING.getCode());
        assertThat(resolution.getProcessStage()).isEqualTo(PaymentProcessStageEnum.WAITING_CALLBACK.getCode());
        assertThat(resolution.getPendingReasonCode())
                .isEqualTo(PaymentPendingReasonEnum.WAITING_CHANNEL_CALLBACK.getCode());
    }

    /** 渠道统一 CAPTURED 已确认资金捕获，可推进平台成功终态。 */
    @Test
    void shouldCompletePaymentWhenUnifiedChannelStatusIsCaptured() {
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCode("PROVIDER_B");
        response.setChannelTradeStatus("CAPTURED");
        response.setRawChannelStatus("provider-specific-captured");

        ChannelTransactionStatusResolution resolution = new DefaultChannelTransactionStatusResolver()
                .resolveSync("PROVIDER_B", PaymentTransactionTypeEnum.PAYMENT.getCode(), response);

        assertThat(resolution.getTargetStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(resolution.getProcessStage()).isEqualTo(PaymentProcessStageEnum.FINISHED.getCode());
    }

    /** 渠道统一 REFUNDED 只对退款动作构成成功终态。 */
    @Test
    void shouldCompleteRefundWhenUnifiedChannelStatusIsRefunded() {
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCode("PROVIDER_C");
        response.setChannelTradeStatus("REFUNDED");
        response.setRawChannelStatus("provider-specific-refunded");

        ChannelTransactionStatusResolution resolution = new DefaultChannelTransactionStatusResolver()
                .resolveSync("PROVIDER_C", PaymentTransactionTypeEnum.REFUND.getCode(), response);

        assertThat(resolution.getTargetStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(resolution.getProcessStage()).isEqualTo(PaymentProcessStageEnum.FINISHED.getCode());
    }
}
