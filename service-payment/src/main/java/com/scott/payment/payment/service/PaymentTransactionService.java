package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionService
 * @date : 2026-05-31 21:02
 * @email : scott_x@163.com
 * @description : 收单支付交易服务
 * @status : create
 */
public interface PaymentTransactionService {

    /**
     * 创建收单授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    PaymentCreateResultDTO createAuthorization(PaymentCreateCommandDTO commandDTO);
}
