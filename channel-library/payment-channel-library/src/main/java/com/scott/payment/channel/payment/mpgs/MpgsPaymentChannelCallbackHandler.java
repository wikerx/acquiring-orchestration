package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsPaymentChannelCallbackHandler
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 渠道回调处理器，位于 payment-channel-library 渠道实现层，负责解析 MPGS 回调中的 order.id、transaction.id、result 和收单响应码。
 * @status : create
 */
@Component
public class MpgsPaymentChannelCallbackHandler implements PaymentChannelCallbackHandler {

    private final MpgsTradeStatusMapper tradeStatusMapper;

    private final MpgsErrorCodeMapper errorCodeMapper;

    /**
     * 创建 MPGS 回调处理器。
     */
    public MpgsPaymentChannelCallbackHandler() {
        this(new MpgsTradeStatusMapper(), new MpgsErrorCodeMapper());
    }

    /**
     * 创建 MPGS 回调处理器。
     *
     * @param tradeStatusMapper MPGS 状态映射器
     * @param errorCodeMapper MPGS 错误码映射器
     */
    public MpgsPaymentChannelCallbackHandler(MpgsTradeStatusMapper tradeStatusMapper,
                                             MpgsErrorCodeMapper errorCodeMapper) {
        this.tradeStatusMapper = tradeStatusMapper;
        this.errorCodeMapper = errorCodeMapper;
    }

    @Override
    public String channelCode() {
        return PaymentChannelCode.MPGS.getCode();
    }

    /**
     * 解析 MPGS 回调。
     *
     * @param request 渠道回调请求
     * @return 渠道回调解析结果
     */
    @Override
    public ChannelCallbackResult handle(ChannelCallbackRequest request) {
        if (request == null || !StringUtils.hasText(request.getBody())) {
            throw new ChannelRequestException("MPGS callback body can not be empty");
        }
        MpgsResponsePayload payload = JsonUtils.parseObject(request.getBody(), MpgsResponsePayload.class);
        if (payload == null) {
            throw new ChannelRequestException("MPGS callback body can not be parsed");
        }
        ChannelCallbackResult result = new ChannelCallbackResult();
        result.setChannelCode(PaymentChannelCode.MPGS.getCode());
        result.setCallbackEventId(callbackEventId(payload));
        result.setChannelOrderNo(payload.getOrder() == null ? null : payload.getOrder().getId());
        result.setChannelTransactionId(payload.getTransaction() == null ? null : payload.getTransaction().getId());
        result.setRawChannelStatus(firstText(payload.getOrder() == null ? null : payload.getOrder().getStatus(), payload.getResult()));
        result.setChannelTradeStatus(tradeStatusMapper.map(payload));
        result.setAmount(parseAmount(payload));
        result.setCurrency(firstText(
                payload.getTransaction() == null ? null : payload.getTransaction().getCurrency(),
                payload.getOrder() == null ? null : payload.getOrder().getCurrency()));
        result.setSignatureValid(true);
        result.setChannelResponseCode(channelResponseCode(payload));
        result.setChannelResponseMessage(errorCodeMapper.responseMessage(payload));
        put(result, "result", payload.getResult());
        put(result, "orderStatus", payload.getOrder() == null ? null : payload.getOrder().getStatus());
        put(result, "transactionType", payload.getTransaction() == null ? null : payload.getTransaction().getType());
        if (payload.getResponse() != null) {
            put(result, "gatewayCode", payload.getResponse().getGatewayCode());
            put(result, "acquirerCode", payload.getResponse().getAcquirerCode());
            put(result, "acquirerMessage", payload.getResponse().getAcquirerMessage());
        }
        return result;
    }

    private String callbackEventId(MpgsResponsePayload payload) {
        String orderId = payload.getOrder() == null ? null : payload.getOrder().getId();
        String transactionId = payload.getTransaction() == null ? null : payload.getTransaction().getId();
        return firstText(transactionId, orderId);
    }

    private String channelResponseCode(MpgsResponsePayload payload) {
        if (payload.getResponse() != null && StringUtils.hasText(payload.getResponse().getAcquirerCode())) {
            return payload.getResponse().getAcquirerCode();
        }
        return errorCodeMapper.responseCode(payload);
    }

    private BigDecimal parseAmount(MpgsResponsePayload payload) {
        if (payload.getTransaction() != null && payload.getTransaction().getAmount() != null) {
            return payload.getTransaction().getAmount();
        }
        return payload.getOrder() == null ? null : payload.getOrder().getAmount();
    }

    private void put(ChannelCallbackResult result, String key, String value) {
        if (StringUtils.hasText(value)) {
            result.getExtension().put(key, value);
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
