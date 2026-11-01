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
import com.scott.payment.channel.payment.dto.request.ChannelThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.dto.request.ChannelVoidRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.dto.response.ChannelThreeDsAuthenticationResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelUnsupportedOperationException;
import com.scott.payment.channel.payment.registry.PaymentChannelRegistry;
import org.springframework.stereotype.Component;

/**
 * 收单渠道执行器，位于 payment-channel-core 执行层，用于按交易类型分发到对应渠道能力，禁止 service-payment 直接依赖具体渠道实现。
 */
@Component
public class PaymentChannelExecutor {

    /**
     * 已注册收单渠道客户端的只读索引。
     */
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

    /**
     * 按渠道编码执行统一 3DS 认证。
     *
     * @param request 渠道统一 3DS 认证请求
     * @return 渠道统一 3DS 认证响应
     */
    public ChannelThreeDsAuthenticationResponse authenticateThreeDs(ChannelThreeDsAuthenticationRequest request) {
        PaymentChannelClient client = channelRegistry.getRequired(request.getChannelCode());
        return client.authenticateThreeDs(request);
    }

    /**
     * 判断指定渠道实现是否声明支持某项统一能力。
     *
     * @param channelCode 渠道编码
     * @param capability 统一渠道能力
     * @return 注册实现存在且声明能力时返回 true
     */
    public boolean supports(String channelCode, ChannelCapability capability) {
        return channelRegistry.getRequired(channelCode).supports(capability);
    }

    /**
     * 判断渠道是否支持使用当前查询请求中的持久化身份。
     *
     * @param request 渠道查询请求
     * @return true 表示当前查询引用可被渠道识别
     */
    public boolean supportsQueryReference(ChannelPaymentRequest request) {
        PaymentChannelClient client = channelRegistry.getRequired(request.getChannelCode());
        ChannelQueryRequest queryRequest = copy(request, new ChannelQueryRequest());
        queryRequest.setRequestId(request.getExtension() == null ? null : request.getExtension().get("requestId"));
        return client.supportsQueryReference(queryRequest);
    }

    /**
     * 将统一渠道请求复制为具体交易能力请求，保持持久化渠道身份和扩展字段不变。
     *
     * @param source 原统一请求
     * @param target 具体能力请求实例
     * @param <T>    具体请求类型
     * @return 填充后的具体能力请求
     */
    private <T extends ChannelPaymentRequest> T copy(ChannelPaymentRequest source, T target) {
        target.setChannelCode(source.getChannelCode());
        target.setOperationId(source.getOperationId());
        target.setTransactionId(source.getTransactionId());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setChannelOrderNo(source.getChannelOrderNo());
        target.setChannelTransactionId(source.getChannelTransactionId());
        target.setMerchantId(source.getMerchantId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setMerchantOrderId(source.getMerchantOrderId());
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
        if (target instanceof ChannelQueryRequest queryRequest) {
            queryRequest.setRequestId(source.getExtension() == null ? null : source.getExtension().get("requestId"));
        }
        return target;
    }
}
