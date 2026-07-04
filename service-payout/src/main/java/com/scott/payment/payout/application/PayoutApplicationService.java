package com.scott.payment.payout.application;

import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;
import com.scott.payment.payout.service.PayoutTransactionService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Payout Application 服务契约，位于 service-payout 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
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
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param commandDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public PayoutCreateResultDTO createPayout(PayoutCreateCommandDTO commandDTO) {
        return payoutTransactionService.createPayout(commandDTO);
    }
}
