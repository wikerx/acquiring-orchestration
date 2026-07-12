package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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
        target.setTransactionOrderNo(request.getTransactionOrderNo());
        target.setTransactionNo(request.getTransactionNo());
        target.setChannelOrderNo(request.getMerchantOrderNo());
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
        target.setRawResponse(rawResponse(response));
        return target;
    }

    private Map<String, String> rawResponse(MpgsResponsePayload response) {
        Map<String, String> raw = new LinkedHashMap<>();
        put(raw, "result", response.getResult());
        put(raw, "gatewayEntryPoint", response.getGatewayEntryPoint());
        put(raw, "merchant", response.getMerchant());
        put(raw, "version", response.getVersion());
        if (response.getResponse() != null) {
            put(raw, "gatewayCode", response.getResponse().getGatewayCode());
            put(raw, "gatewayRecommendation", response.getResponse().getGatewayRecommendation());
            put(raw, "acquirerCode", response.getResponse().getAcquirerCode());
            put(raw, "acquirerMessage", response.getResponse().getAcquirerMessage());
        }
        if (response.getError() != null) {
            put(raw, "errorCause", response.getError().getCause());
            put(raw, "errorExplanation", response.getError().getExplanation());
            put(raw, "errorField", response.getError().getField());
            put(raw, "errorValidationType", response.getError().getValidationType());
        }
        if (response.getOrder() != null) {
            put(raw, "orderId", response.getOrder().getId());
            put(raw, "orderStatus", response.getOrder().getStatus());
            put(raw, "orderReference", response.getOrder().getReference());
        }
        if (response.getTransaction() != null) {
            put(raw, "transactionId", response.getTransaction().getId());
            put(raw, "transactionType", response.getTransaction().getType());
            put(raw, "authorizationCode", response.getTransaction().getAuthorizationCode());
            put(raw, "acquirerReference", response.getTransaction().getReference());
            put(raw, "receipt", response.getTransaction().getReceipt());
        }
        return raw;
    }

    private void put(Map<String, String> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
