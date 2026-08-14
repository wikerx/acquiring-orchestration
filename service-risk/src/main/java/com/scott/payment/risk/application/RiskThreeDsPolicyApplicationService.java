package com.scott.payment.risk.application;

import com.scott.payment.risk.api.internal.dto.RiskThreeDsPolicyRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskThreeDsPolicyResultDTO;
import com.scott.payment.risk.service.RiskThreeDsPolicyService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskThreeDsPolicyApplicationService
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 路由后 3DS 策略应用服务，负责内部接口与只读领域规则服务之间的用例编排。
 * @status : create
 */
@Service
public class RiskThreeDsPolicyApplicationService {

    /** 3DS 策略领域服务。 */
    private final RiskThreeDsPolicyService riskThreeDsPolicyService;

    /**
     * 创建 3DS 策略应用服务。
     *
     * @param riskThreeDsPolicyService 只读策略服务
     */
    public RiskThreeDsPolicyApplicationService(RiskThreeDsPolicyService riskThreeDsPolicyService) {
        this.riskThreeDsPolicyService = riskThreeDsPolicyService;
    }

    /**
     * 执行路由后 3DS 策略只读评估。
     *
     * @param requestDTO 已路由交易维度
     * @return 3DS 策略结果
     */
    public RiskThreeDsPolicyResultDTO evaluate(RiskThreeDsPolicyRequestDTO requestDTO) {
        return riskThreeDsPolicyService.evaluate(requestDTO);
    }
}
