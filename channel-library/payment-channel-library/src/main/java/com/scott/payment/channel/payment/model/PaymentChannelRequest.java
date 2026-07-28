package com.scott.payment.channel.payment.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;


@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelRequest
 * @date : 2026-05-28 11:10
 * @email : scott_x@163.com
 * @description : Payment Channel Request 传输模型，位于 渠道适配库，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
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
     * 平台当前交易唯一标识，每一笔授权、请款、退款、撤销都不同。
     */
    private String transactionId;

    /**
     * 平台内部生命周期关联标识，不返回商户。
     */
    private String operationId;

    /**
     * 渠道扩展参数，用于承载不同收单渠道的差异化字段，核心通用字段应优先使用显式属性。
     */
    private Map<String, String> parameters;
}
