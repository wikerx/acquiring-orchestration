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

    /**
     * 序列化版本号，用于保证渠道响应对象在服务间传输时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 支付渠道编码，用于标识本次响应来自哪个收单渠道适配器。
     */
    private String channelCode;

    /**
     * 渠道侧订单号或流水号，用于后续查单、退款、对账和问题排查。
     */
    private String channelOrderNo;

    /**
     * 渠道侧交易状态，进入 service-payment 后会映射为系统内部统一交易状态。
     */
    private String channelStatus;

    /**
     * 渠道侧响应码，保留原始语义，便于错误归因和渠道规则分析。
     */
    private String channelResponseCode;

    /**
     * 渠道侧响应描述，保留原始文本，返回商户前应根据产品规则做必要脱敏和标准化。
     */
    private String channelResponseMessage;

    /**
     * 渠道原始响应字段集合，用于审计、排错和后续补充解析，不建议直接暴露给商户。
     */
    private Map<String, String> rawResponse;
}
