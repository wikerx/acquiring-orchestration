package com.scott.payment.payment.application;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.service.PaymentTransactionService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentAuthorizationApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单授权交易应用服务，位于 service-payment 应用编排层，负责承接内部创建命令并委托交易服务完成受理。
 * @status : create
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
