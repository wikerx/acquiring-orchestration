package com.scott.payment.channel.payout.scottchannela.model.response;

import lombok.Data;

/**
 * ScottChannelA 代付 API 统一响应外层结构。
 *
 * <p>Fastjson2 默认忽略未声明字段，以兼容渠道新增响应参数；禁止启用
 * {@code JSONReader.Feature.ErrorOnUnknownProperties}。关键交易字段由响应映射器单独校验。</p>
 **/
@Data
public final class ApiResponsePayload {

    /**
     * 渠道统一响应码；部分处理中响应使用该字段承载状态。
     **/
    private String code;

    /**
     * 渠道统一响应描述，可选。
     **/
    private String message;

    /**
     * 渠道响应时间，可选，仅保留协议兼容。
     **/
    private String responseTime;

    /**
     * 代付交易响应数据；成功 HTTP 响应必须完整。
     **/
    private PayoutResponsePayload data;
}
