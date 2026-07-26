package com.scott.payment.channel.payment.adapter;

import com.scott.payment.channel.payment.model.PaymentChannelRequest;
import com.scott.payment.channel.payment.model.PaymentChannelResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelAdapter
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 收单支付旧版渠道适配器接口，位于 payment-channel-library 渠道适配层，保留用于兼容早期调用；新接入渠道应优先使用 PaymentChannelClient SPI。
 * @status : create
 */
@Deprecated
public interface PaymentChannelAdapter {

    String supportChannelCode();

    PaymentChannelResult submitPayment(PaymentChannelRequest request);

    /**
     * 完成 query Payment 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：渠道适配层；输入来源、输出结构和异常语义由 PaymentChannelAdapter 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PaymentChannelResult queryPayment(PaymentChannelRequest request);

    /**
     * 完成 submit Refund 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：渠道适配层；输入来源、输出结构和异常语义由 PaymentChannelAdapter 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PaymentChannelResult submitRefund(PaymentChannelRequest request);
}
