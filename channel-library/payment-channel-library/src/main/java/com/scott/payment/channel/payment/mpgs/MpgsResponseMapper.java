package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsResponseMapper
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 响应映射器，位于 payment-channel-library 渠道实现层，负责保留渠道真实失败原因并映射统一渠道状态；不决定商户或付款人展示文案。
 * @status : create
 */
@Component
public class MpgsResponseMapper {

    private final MpgsTradeStatusMapper tradeStatusMapper;

    private final MpgsErrorCodeMapper errorCodeMapper;

    /**
     * 创建 MPGS 响应映射器。
     */
    public MpgsResponseMapper() {
        this(new MpgsTradeStatusMapper(), new MpgsErrorCodeMapper());
    }

    /**
     * 创建 MPGS 响应映射器。
     *
     * @param tradeStatusMapper 交易状态映射器
     * @param errorCodeMapper   错误码映射器
     */
    public MpgsResponseMapper(MpgsTradeStatusMapper tradeStatusMapper, MpgsErrorCodeMapper errorCodeMapper) {
        this.tradeStatusMapper = tradeStatusMapper;
        this.errorCodeMapper = errorCodeMapper;
    }

    /**
     * 映射 MPGS 响应。
     *
     * @param request  渠道统一请求
     * @param response MPGS 原始响应字段
     * @return 渠道统一响应
     */
    public ChannelPaymentResponse toChannelResponse(com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest request,
                                                     MpgsResponsePayload response) {
        ChannelPaymentResponse target = new ChannelPaymentResponse();
        target.setChannelCode(PaymentChannelCode.MPGS.getCode());
        target.setOperationId(request.getOperationId());
        target.setTransactionId(request.getTransactionId());
        target.setChannelOrderNo(request.getChannelOrderNo());
        target.setChannelTransactionId(request.getChannelTransactionId());
        if (response == null) {
            target.setChannelTradeStatus(tradeStatusMapper.map(null));
            target.setRawChannelStatus(null);
            target.setChannelResponseCode(errorCodeMapper.responseCode(null));
            target.setChannelResponseMessage(errorCodeMapper.responseMessage(null));
            return target;
        }
        target.setChannelTradeStatus(tradeStatusMapper.map(response));
        target.setRawChannelStatus(response.getResult());
        target.setChannelResponseCode(errorCodeMapper.responseCode(response));
        target.setChannelResponseMessage(errorCodeMapper.responseMessage(response));
        if (response.getOrder() != null && response.getOrder().getId() != null) {
            target.setChannelOrderNo(response.getOrder().getId());
        }
        if (response.getTransaction() != null && response.getTransaction().getId() != null) {
            target.setChannelTransactionId(response.getTransaction().getId());
        }
        target.setRawResponse(responseSummary(response).toRawResponseMap());
        return target;
    }

    /**
     * 提取 MPGS 类型化响应摘要。
     * <p>
     * Map 只作为渠道公共扩展字段向后兼容；MPGS 自身字段在这里先落入类型化对象，避免业务代码散落字符串 key。
     *
     * @param response MPGS 响应载荷
     * @return MPGS 响应摘要
     */
    MpgsResponseSummary responseSummary(MpgsResponsePayload response) {
        MpgsResponsePayload.Response gatewayResponse = response.getResponse();
        MpgsResponsePayload.ErrorPayload error = response.getError();
        MpgsResponsePayload.Order order = response.getOrder();
        MpgsResponsePayload.Transaction transaction = response.getTransaction();
        return MpgsResponseSummary.builder()
                .result(response.getResult())
                .gatewayEntryPoint(response.getGatewayEntryPoint())
                .merchant(response.getMerchant())
                .version(response.getVersion())
                .gatewayCode(gatewayResponse == null ? null : gatewayResponse.getGatewayCode())
                .gatewayRecommendation(gatewayResponse == null ? null : gatewayResponse.getGatewayRecommendation())
                .acquirerCode(gatewayResponse == null ? null : gatewayResponse.getAcquirerCode())
                .acquirerMessage(gatewayResponse == null ? null : gatewayResponse.getAcquirerMessage())
                .errorCause(error == null ? null : error.getCause())
                .errorExplanation(error == null ? null : error.getExplanation())
                .errorField(error == null ? null : error.getField())
                .errorValidationType(error == null ? null : error.getValidationType())
                .orderId(order == null ? null : order.getId())
                .orderStatus(order == null ? null : order.getStatus())
                .orderReference(order == null ? null : order.getReference())
                .transactionId(transaction == null ? null : transaction.getId())
                .transactionType(transaction == null ? null : transaction.getType())
                .authorizationCode(transaction == null ? null : transaction.getAuthorizationCode())
                .acquirerReference(transaction == null ? null : transaction.getReference())
                .receipt(transaction == null ? null : transaction.getReceipt())
                .build();
    }
}
