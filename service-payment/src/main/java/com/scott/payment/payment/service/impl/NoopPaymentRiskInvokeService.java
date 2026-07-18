package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NoopPaymentRiskInvokeService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单交易风控默认实现，位于 service-payment 服务实现层，仅用于框架搭建阶段返回 PASS；生产接真实 service-risk 前必须关闭或替换。
 * @status : create
 */
@Service
@ConditionalOnProperty(prefix = "payment.risk-client", name = "remote-enabled", havingValue = "false", matchIfMissing = true)
public class NoopPaymentRiskInvokeService implements PaymentRiskInvokeService {

    /**
     * 执行路由前内风控检查。
     * <p>
     * 当前实现只用于框架搭建和本地联调，返回 SKIP/PASS 语义让交易继续进入路由和渠道调用；
     * UAT/生产接入 service-risk 后必须通过配置关闭本实现，避免真实交易绕过风控决策。
     *
     * @param commandDTO 支付核心交易命令
     * @return 跳过风控的决策结果
     */
    @Override
    public PaymentRiskDecisionDTO checkPreRoute(PaymentCreateCommandDTO commandDTO) {
        return PaymentRiskDecisionDTO.skip();
    }
}
