package com.sinopay.payment.channel.payout.model;

import java.io.Serializable;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelResult
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 代付渠道响应模型
 * @status : create
 */
public class PayoutChannelResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String channelCode;
    private String channelOrderNo;
    private String channelStatus;
    private String channelResponseCode;
    private String channelResponseMessage;
    private Map<String, String> rawResponse;

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getChannelOrderNo() {
        return channelOrderNo;
    }

    public void setChannelOrderNo(String channelOrderNo) {
        this.channelOrderNo = channelOrderNo;
    }

    public String getChannelStatus() {
        return channelStatus;
    }

    public void setChannelStatus(String channelStatus) {
        this.channelStatus = channelStatus;
    }

    public String getChannelResponseCode() {
        return channelResponseCode;
    }

    public void setChannelResponseCode(String channelResponseCode) {
        this.channelResponseCode = channelResponseCode;
    }

    public String getChannelResponseMessage() {
        return channelResponseMessage;
    }

    public void setChannelResponseMessage(String channelResponseMessage) {
        this.channelResponseMessage = channelResponseMessage;
    }

    public Map<String, String> getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(Map<String, String> rawResponse) {
        this.rawResponse = rawResponse;
    }
}
