package com.scott.payment.payout.service;

import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;


/**
 * 定义代付交易受理的领域服务边界。
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
