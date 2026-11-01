package com.scott.payment.risk.service;

import com.scott.payment.risk.api.internal.dto.RiskThreeDsPolicyRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskThreeDsPolicyResultDTO;

/**
 * 路由后 3DS 策略只读服务契约，与会产生预占副作用的完整支付风控评估隔离。
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
