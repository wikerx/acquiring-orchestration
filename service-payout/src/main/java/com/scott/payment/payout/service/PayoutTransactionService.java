package com.scott.payment.payout.service;

import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutTransactionService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 代付交易服务。
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
