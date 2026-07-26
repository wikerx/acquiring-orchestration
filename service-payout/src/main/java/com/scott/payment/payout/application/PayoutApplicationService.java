package com.scott.payment.payout.application;

import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;
import com.scott.payment.payout.service.PayoutTransactionService;
import org.springframework.stereotype.Service;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutApplicationService
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : PayoutApplicationService 应用服务，用于编排接口请求、权限上下文、领域服务和外部依赖，位于 代付服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
