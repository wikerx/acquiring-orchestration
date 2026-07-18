package com.scott.payment.channel.payment.mpgs;

import lombok.Builder;
import lombok.Value;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsResponseSummary
 * @date : 2026-07-16 00:00
 * @email : scott_x@163.com
 * @description : MPGS 响应摘要值对象，位于 payment-channel-library 渠道实现层，用类型化字段承载后台排查和平台落库需要的核心响应信息。
 * @status : create
 */
@Value
@Builder
public class MpgsResponseSummary {

    /**
     * MPGS 顶层 result，例如 SUCCESS、ERROR、UNKNOWN。
     */
    String result;

    /**
     * MPGS 网关入口。
     */
    String gatewayEntryPoint;

    /**
     * MPGS 商户号。
     */
    String merchant;

    /**
     * MPGS API 版本。
     */
    String version;

    /**
     * MPGS 网关响应码。
     */
    String gatewayCode;

    /**
     * MPGS 网关建议。
     */
    String gatewayRecommendation;

    /**
     * 收单机构响应码。
     */
    String acquirerCode;

    /**
     * 收单机构响应描述。
     */
    String acquirerMessage;

    /**
     * MPGS 错误原因。
     */
    String errorCause;

    /**
     * MPGS 错误说明。
     */
    String errorExplanation;

    /**
     * MPGS 校验错误字段。
     */
    String errorField;

    /**
     * MPGS 校验错误类型。
     */
    String errorValidationType;

    /**
     * MPGS orderId。
     */
    String orderId;

    /**
     * MPGS 订单状态。
     */
    String orderStatus;

    /**
     * MPGS 订单参考号。
     */
    String orderReference;

    /**
     * MPGS transactionId。
     */
    String transactionId;

    /**
     * MPGS 交易类型。
     */
    String transactionType;

    /**
     * 授权码。
     */
    String authorizationCode;

    /**
     * 收单参考号。
     */
    String acquirerReference;

    /**
     * 渠道回单号。
     */
    String receipt;

    /**
     * 转为渠道公共响应的扩展 Map，保持既有 service-payment 落库和查询兼容。
     *
     * @return 非空字段组成的有序 Map
     */
    public Map<String, String> toRawResponseMap() {
        Map<String, String> raw = new LinkedHashMap<>();
        put(raw, "result", result);
        put(raw, "gatewayEntryPoint", gatewayEntryPoint);
        put(raw, "merchant", merchant);
        put(raw, "version", version);
        put(raw, "gatewayCode", gatewayCode);
        put(raw, "gatewayRecommendation", gatewayRecommendation);
        put(raw, "acquirerCode", acquirerCode);
        put(raw, "acquirerMessage", acquirerMessage);
        put(raw, "errorCause", errorCause);
        put(raw, "errorExplanation", errorExplanation);
        put(raw, "errorField", errorField);
        put(raw, "errorValidationType", errorValidationType);
        put(raw, "orderId", orderId);
        put(raw, "orderStatus", orderStatus);
        put(raw, "orderReference", orderReference);
        put(raw, "transactionId", transactionId);
        put(raw, "transactionType", transactionType);
        put(raw, "authorizationCode", authorizationCode);
        put(raw, "acquirerReference", acquirerReference);
        put(raw, "receipt", receipt);
        return raw;
    }

    private void put(Map<String, String> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
