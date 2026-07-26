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

    /**
     * channel Registry 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
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
