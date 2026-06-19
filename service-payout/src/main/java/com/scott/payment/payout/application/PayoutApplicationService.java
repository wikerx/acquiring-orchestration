package com.scott.payment.payout.application;

import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;
import com.scott.payment.payout.service.PayoutTransactionService;
import org.springframework.stereotype.Service;

/**
 * 代付应用服务。
 * <p>
 * 当前用于衔接内部接口与代付交易服务，后续可继续收敛审核、路由和幂等等应用编排。
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
