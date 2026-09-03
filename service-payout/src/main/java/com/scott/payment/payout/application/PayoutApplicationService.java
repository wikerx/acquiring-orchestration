package com.scott.payment.payout.application;

import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;
import com.scott.payment.payout.service.PayoutTransactionService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutApplicationService
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : 代付交易应用服务，位于 代付服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@Service
public class PayoutApplicationService {

    /**
     * 代付交易服务。
     */
    private final PayoutTransactionService payoutTransactionService;

    /**
     * 创建代付应用服务。
     *
     * @param payoutTransactionService 代付交易服务
     */
    public PayoutApplicationService(PayoutTransactionService payoutTransactionService) {
        this.payoutTransactionService = payoutTransactionService;
    }

    /**
     * 创建代付交易。
     *
     * @param commandDTO 创建代付命令
     * @return 代付创建结果
     */
    public PayoutCreateResultDTO createPayout(PayoutCreateCommandDTO commandDTO) {
        return payoutTransactionService.createPayout(commandDTO);
    }
}
