package com.scott.payment.openapi.vo.payment;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateVO
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 收单支付创建响应视图对象
 * @status : create
 */
@Data
public class PaymentCreateVO implements Serializable {

    /**
     * 序列化版本号，用于保证响应对象在网关、日志、缓存等链路中的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 商户订单号，原样返回给商户，方便商户侧将响应结果与自身订单系统关联。
     */
    private String merchantOrderNo;

    /**
     * 交易币种，使用 ISO 4217 三位大写币种代码。
     */
    private String currency;

    /**
     * 交易金额，当前基础接口使用最小币种单位返回，与请求金额保持一致。
     */
    private Long amount;
}
