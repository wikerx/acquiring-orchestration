package com.scott.payment.payout.service;

import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutTransactionService
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : Payout Transaction Service 服务契约，位于 代付服务，声明当前业务能力的输入、返回结果和异常边界，由实现类保持一致。
 * @status : create
 */
public interface PayoutTransactionService {

    /**
     * 创建代付交易。
     *
     * @param commandDTO 创建代付命令
     * @return 代付创建结果
     */
    PayoutCreateResultDTO createPayout(PayoutCreateCommandDTO commandDTO);
}
