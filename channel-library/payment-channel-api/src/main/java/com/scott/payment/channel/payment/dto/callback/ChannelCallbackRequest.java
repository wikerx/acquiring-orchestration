package com.scott.payment.channel.payment.dto.callback;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackRequest
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道回调统一请求，位于 payment-channel-api DTO 层，用于承载渠道回调原文、请求头和来源信息；原文进入日志或落库前必须加密或脱敏。
 * @status : create
 */
@Data
public class ChannelCallbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 渠道编码。
     */
    private String channelCode;

    /**
     * 回调请求 URI。
     */
    private String requestUri;

    /**
     * 来源 IP，用于渠道 IP 白名单校验。
     */
    private String clientIp;

    /**
     * 回调请求头。
     */
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * 回调原文。
     */
    private String body;
}
