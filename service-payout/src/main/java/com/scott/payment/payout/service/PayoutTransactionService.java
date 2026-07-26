package com.scott.payment.payout.service;

import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutTransactionService
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : PayoutTransactionService 服务契约，用于声明业务能力、调用边界和返回结果约束，位于 代付服务层，输入输出边界由所在包和公开方法契约限定。
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
