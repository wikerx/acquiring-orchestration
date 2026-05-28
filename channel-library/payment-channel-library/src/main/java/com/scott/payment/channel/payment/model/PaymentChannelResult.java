package com.scott.payment.channel.payment.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelResult
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 收单支付渠道响应模型
 * @status : create
 */
@Data
public class PaymentChannelResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String channelCode;
    private String channelOrderNo;
    private String channelStatus;
    private String channelResponseCode;
    private String channelResponseMessage;
    private Map<String, String> rawResponse;
}
