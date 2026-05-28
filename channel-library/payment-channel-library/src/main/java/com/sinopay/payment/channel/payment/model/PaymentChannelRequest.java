package com.sinopay.payment.channel.payment.model;

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
public class PaymentChannelRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String channelCode;
    private String merchantOrderNo;
    private String paymentOrderNo;
    private Map<String, String> parameters;

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String merchantOrderNo) {
        this.merchantOrderNo = merchantOrderNo;
    }

    public String getPaymentOrderNo() {
        return paymentOrderNo;
    }

    public void setPaymentOrderNo(String paymentOrderNo) {
        this.paymentOrderNo = paymentOrderNo;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }
}
