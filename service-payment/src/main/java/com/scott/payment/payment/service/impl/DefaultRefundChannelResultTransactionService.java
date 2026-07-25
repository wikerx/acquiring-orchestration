package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.service.RefundChannelResultTransactionService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.RefundPreparationResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRefundChannelResultTransactionService
 * @date : 2026-07-24 00:00
 * @description : Refund 渠道结果默认事务实现，使用 REQUIRES_NEW 保存同步结果并通过 CAS 推进退款动作。
 * @status : create
 */
@Service
public class DefaultRefundChannelResultTransactionService implements RefundChannelResultTransactionService {

    private final TransactionRecordService transactionRecordService;

    public DefaultRefundChannelResultTransactionService(TransactionRecordService transactionRecordService) {
        this.transactionRecordService = transactionRecordService;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordRefundChannelResult(RefundPreparationResultDTO preparationResultDTO,
                                          PaymentChannelInvokeResultDTO invokeResultDTO) {
        if (preparationResultDTO == null || preparationResultDTO.getResultDTO() == null) {
            return;
        }
        PaymentCreateResultDTO resultDTO = preparationResultDTO.getResultDTO();
        TransactionOperationDO operationDO = transactionRecordService.findSourceOperationByTransactionId(resultDTO.getTransactionId());
        if (operationDO == null) {
            return;
        }
        transactionRecordService.completeRefundChannelResult(
                operationDO,
                preparationResultDTO.getSourceOrderDO(),
                preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getRouteResultDTO(),
                invokeResultDTO,
                resultDTO,
                preparationResultDTO.getCurrencyExponent());
    }
}
