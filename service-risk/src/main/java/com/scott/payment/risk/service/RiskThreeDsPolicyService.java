package com.scott.payment.risk.service;

import com.scott.payment.risk.api.internal.dto.RiskThreeDsPolicyRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskThreeDsPolicyResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskThreeDsPolicyService
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 路由后 3DS 策略只读服务契约，与会产生预占副作用的完整支付风控评估隔离。
 * @status : create
 */
public interface RiskThreeDsPolicyService {

    /**
     * 查询当前路由和交易维度适用的 3DS 策略。
     *
     * @param requestDTO 已完成路由的交易维度
     * @return 3DS 强制、跳过或未配置结果
     */
    RiskThreeDsPolicyResultDTO evaluate(RiskThreeDsPolicyRequestDTO requestDTO);
}
