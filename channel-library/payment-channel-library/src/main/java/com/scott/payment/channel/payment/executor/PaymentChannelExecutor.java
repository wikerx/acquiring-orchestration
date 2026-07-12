package com.scott.payment.channel.payment.executor;

import com.scott.payment.channel.payment.api.PaymentChannelClient;
import com.scott.payment.channel.payment.dto.request.ChannelAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelIncrementalAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPreAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.request.ChannelRefundRequest;
import com.scott.payment.channel.payment.dto.request.ChannelReversalRequest;
import com.scott.payment.channel.payment.dto.request.ChannelVoidRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelUnsupportedOperationException;
import com.scott.payment.channel.payment.registry.PaymentChannelRegistry;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelExecutor
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道执行器，位于 payment-channel-library 执行层，用于按交易类型分发到对应渠道能力，禁止 service-payment 直接依赖具体渠道实现。
 * @status : create
 */
@Component
public class PaymentChannelExecutor {

    private final PaymentChannelRegistry channelRegistry;

    /**
     * 创建渠道执行器。
     *
     * @param channelRegistry 渠道注册器
     */
    public PaymentChannelExecutor(PaymentChannelRegistry channelRegistry) {
        this.channelRegistry = channelRegistry;
    }

    /**
     * 按 transactionType 执行渠道交易。
     *
     * @param request 渠道请求
     * @return 渠道统一响应
     */
    public ChannelPaymentResponse execute(ChannelPaymentRequest request) {
        PaymentChannelClient client = channelRegistry.getRequired(request.getChannelCode());
        String transactionType = request.getTransactionType();
        if (ChannelCapability.PAYMENT.getCode().equals(transactionType)) {
            return client.payment(request);
        }
        if (ChannelCapability.AUTHORIZATION.getCode().equals(transactionType)) {
            return client.authorize(copy(request, new ChannelAuthorizeRequest()));
        }
        if (ChannelCapability.PRE_AUTHORIZATION.getCode().equals(transactionType)) {
            return client.preAuthorize(copy(request, new ChannelPreAuthorizeRequest()));
        }
        if (ChannelCapability.INCREMENTAL_AUTHORIZATION.getCode().equals(transactionType)) {
            return client.incrementalAuthorize(copy(request, new ChannelIncrementalAuthorizeRequest()));
        }
        if (ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)) {
            return client.capture(copy(request, new ChannelCaptureRequest()));
        }
        if (ChannelCapability.REFUND.getCode().equals(transactionType)) {
            return client.refund(copy(request, new ChannelRefundRequest()));
        }
        if (ChannelCapability.VOID.getCode().equals(transactionType)) {
            return client.voidPayment(copy(request, new ChannelVoidRequest()));
        }
        if (ChannelCapability.REVERSAL.getCode().equals(transactionType)) {
            return client.reversal(copy(request, new ChannelReversalRequest()));
        }
        if (ChannelCapability.QUERY.getCode().equals(transactionType)) {
            return client.query(copy(request, new ChannelQueryRequest()));
        }
        throw new ChannelUnsupportedOperationException(client.channelCode(), transactionType);
    }

    private <T extends ChannelPaymentRequest> T copy(ChannelPaymentRequest source, T target) {
        target.setChannelCode(source.getChannelCode());
        target.setTransactionOrderNo(source.getTransactionOrderNo());
        target.setTransactionNo(source.getTransactionNo());
        target.setOriginalTransactionNo(source.getOriginalTransactionNo());
        target.setMerchantId(source.getMerchantId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setTransactionType(source.getTransactionType());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setAmount(source.getAmount());
        target.setCurrency(source.getCurrency());
        target.setTransactionDateTime(source.getTransactionDateTime());
        target.setCardNo(source.getCardNo());
        target.setExpirationMonth(source.getExpirationMonth());
        target.setExpirationYear(source.getExpirationYear());
        target.setSecurityCode(source.getSecurityCode());
        target.setCardBrand(source.getCardBrand());
        target.setBillingInfo(source.getBillingInfo());
        target.setThreeDsInfo(source.getThreeDsInfo());
        target.setExtension(source.getExtension());
        return target;
    }
}
