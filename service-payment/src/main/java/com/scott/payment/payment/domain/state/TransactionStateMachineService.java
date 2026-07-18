package com.scott.payment.payment.domain.state;

import com.scott.payment.payment.entity.TransactionOrderDO;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionStateMachineService
 * @date : 2026-07-14 19:35
 * @email : scott_x@163.com
 * @description : 收单交易状态机服务，位于 service-payment 领域状态层，负责后续动作发起前的状态、终态和可用金额校验。
 * @status : create
 */
public interface TransactionStateMachineService {

    /**
     * 校验后续交易动作是否允许发起。
     *
     * @param sourceOrderDO 原交易生命周期主单
     * @param nextTransactionType 后续交易类型，对齐字典 transaction_type
     * @param requestAmount 本次请求金额，主币种单位；撤销和查询可为空
     * @param requestCurrency 本次请求币种；金额类后续动作必须与原交易交易币种一致
     */
    void validateFollowUpAction(TransactionOrderDO sourceOrderDO,
                                PaymentTransactionTypeEnum nextTransactionType,
                                BigDecimal requestAmount,
                                String requestCurrency);
}
