package com.scott.payment.payment.service;

import com.scott.payment.channel.payment.dto.request.ChannelThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.dto.response.ChannelThreeDsAuthenticationResponse;
import com.scott.payment.channel.payment.enums.ChannelThreeDsStatus;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentAuthenticationRecordService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 平台 3DS 认证安全审计服务。
 * @status : create
 */
public interface PaymentAuthenticationRecordService {

    /**
     * 记录渠道返回的 3DS 或其它支付认证结果。
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     */
    void recordChannelResult(ChannelThreeDsAuthenticationRequest request,
                             ChannelThreeDsAuthenticationResponse response);

    /**
     * 记录渠道认证失败事实，保留可审计的失败码和脱敏摘要。
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param failureCode 渠道认证失败码，只保存受控编码或脱敏摘要
     */
    void recordChannelFailure(ChannelThreeDsAuthenticationRequest request,
                              ChannelThreeDsStatus status,
                              String failureCode);

    /**
     * 记录渠道认证超时事实，供状态机和运营排障使用。
     * @param attemptDO 已超时的 Hosted Checkout 认证尝试事实
     */
    void recordTimeout(PaymentCheckoutAttemptDO attemptDO);
}
