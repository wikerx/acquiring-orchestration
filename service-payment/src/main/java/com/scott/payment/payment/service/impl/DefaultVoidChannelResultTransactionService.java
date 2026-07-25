package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.VoidChannelResultTransactionService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.VoidPreparationResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultVoidChannelResultTransactionService
 * @date : 2026-07-24 00:00
 * @description : Void 渠道结果默认事务实现，使用 REQUIRES_NEW 保存同步结果并通过 CAS 推进撤销动作。
 * @status : create
 */
@Service
public class DefaultVoidChannelResultTransactionService implements VoidChannelResultTransactionService {

    private final TransactionRecordService transactionRecordService;

    public DefaultVoidChannelResultTransactionService(TransactionRecordService transactionRecordService) {
        this.transactionRecordService = transactionRecordService;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordVoidChannelResult(VoidPreparationResultDTO preparationResultDTO,
                                        PaymentChannelInvokeResultDTO invokeResultDTO) {
        if (preparationResultDTO == null || preparationResultDTO.getResultDTO() == null) {
            return;
        }
        PaymentCreateResultDTO resultDTO = preparationResultDTO.getResultDTO();
        TransactionOperationDO operationDO = transactionRecordService.findSourceOperationByTransactionId(resultDTO.getTransactionId());
        if (operationDO == null) {
            return;
        }
        transactionRecordService.completeVoidChannelResult(
                operationDO,
                preparationResultDTO.getSourceOrderDO(),
                preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getRouteResultDTO(),
                invokeResultDTO,
                resultDTO,
                preparationResultDTO.getCurrencyExponent());
    }
}
