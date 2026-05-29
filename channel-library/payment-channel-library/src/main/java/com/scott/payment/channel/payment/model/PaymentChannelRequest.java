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

    /**
     * 序列化版本号，用于保证渠道请求对象在服务间传输时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 支付渠道编码，用于路由到具体收单渠道适配器，例如 mastercard、visa 或其他渠道标识。
     */
    private String channelCode;

    /**
     * 商户订单号，来自开放接口入参，用于渠道请求与商户订单维度的关联。
     */
    private String merchantOrderNo;

    /**
     * 系统内部支付订单号，由 service-payment 生成，用于渠道调用、交易状态推进和对账。
     */
    private String paymentOrderNo;

    /**
     * 渠道扩展参数，用于承载不同收单渠道的差异化字段，核心通用字段应优先使用显式属性。
     */
    private Map<String, String> parameters;
}
