package com.scott.payment.payment.application;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.service.PaymentTransactionService;
import org.springframework.stereotype.Service;

/**
 * 收单授权交易应用服务。
 * <p>
 * 当前负责衔接内部接口与交易领域服务，后续可继续在这里汇总路由、风控、幂等和事件编排。
 */
@Service
public class PaymentAuthorizationApplicationService {

    /**
     * 收单支付交易服务。
     */
    private final PaymentTransactionService paymentTransactionService;

    /**
     * 创建收单授权交易应用服务。
     *
     * @param paymentTransactionService 收单支付交易服务
     */
    public PaymentAuthorizationApplicationService(PaymentTransactionService paymentTransactionService) {
        this.paymentTransactionService = paymentTransactionService;
    }

    /**
     * 创建收单授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    public PaymentCreateResultDTO createAuthorization(PaymentCreateCommandDTO commandDTO) {
        return paymentTransactionService.createAuthorization(commandDTO);
    }
}
