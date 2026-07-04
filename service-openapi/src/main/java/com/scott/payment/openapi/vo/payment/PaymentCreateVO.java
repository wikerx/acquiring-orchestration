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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateVO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIPayment Create 视图对象，位于 service-openapi 的页面视图层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class PaymentCreateVO implements Serializable {

    /**
     * 序列化版本号，用于保证响应对象在网关、日志、缓存等链路中的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 平台支付订单号，service-payment 创建交易后返回，用作系统内部交易主键。
     */
    private String paymentOrderNo;

    /**
     * 商户订单号，原样返回给商户，方便商户侧将响应结果与自身订单系统关联。
     */
    private String merchantOrderNo;

    /**
     * 交易币种，使用 ISO 4217 三位大写币种代码。
     */
    private String currency;

    /**
     * 交易状态，例如 RECEIVED、PENDING、SUCCESS、FAILED，用于商户侧判断后续查询或等待通知。
     */
    private String status;

    /**
     * 交易金额，当前基础接口使用最小币种单位返回，与请求金额保持一致。
     */
    private Long amount;
}
