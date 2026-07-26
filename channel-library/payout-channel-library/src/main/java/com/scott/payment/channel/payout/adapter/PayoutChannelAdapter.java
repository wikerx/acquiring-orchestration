package com.scott.payment.channel.payout.adapter;

import com.scott.payment.channel.payout.model.PayoutChannelRequest;
import com.scott.payment.channel.payout.model.PayoutChannelResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelAdapter
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 代付渠道适配器接口
 * @status : create
 */
public interface PayoutChannelAdapter {

    /**
     * 整理渠道编码支持判断，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    String supportChannelCode();

    /**
     * 发送代付交易消息或请求，补齐目标地址、链路标识和业务载荷。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    PayoutChannelResult submitPayout(PayoutChannelRequest request);

    /**
     * 查询代付交易，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 渠道适配库 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PayoutChannelResult queryPayout(PayoutChannelRequest request);
}
