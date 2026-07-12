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

    @Override
    public PaymentRiskDecisionDTO checkPreRoute(PaymentCreateCommandDTO commandDTO) {
        return PaymentRiskDecisionDTO.skip();
    }
}
