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
     * 查询支付交易，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 渠道适配库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PaymentChannelResult queryPayment(PaymentChannelRequest request);

    /**
     * 发送refund消息或请求，补齐目标地址、链路标识和业务载荷。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    PaymentChannelResult submitRefund(PaymentChannelRequest request);
}
