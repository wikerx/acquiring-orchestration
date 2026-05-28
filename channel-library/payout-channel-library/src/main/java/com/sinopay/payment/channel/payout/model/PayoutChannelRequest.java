package com.sinopay.payment.channel.payout.model;

import java.io.Serializable;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelRequest
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 代付渠道请求模型
 * @status : create
 */
public class PayoutChannelRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String channelCode;
    private String merchantOrderNo;
    private String payoutOrderNo;
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

    public String getPayoutOrderNo() {
        return payoutOrderNo;
    }

    public void setPayoutOrderNo(String payoutOrderNo) {
        this.payoutOrderNo = payoutOrderNo;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }
}
