package com.scott.payment.channel.payment.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelRequest
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 收单支付渠道请求模型
 * @status : create
 */
@Data
public class PaymentChannelRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String channelCode;
    private String merchantOrderNo;
    private String paymentOrderNo;
    private Map<String, String> parameters;
}
