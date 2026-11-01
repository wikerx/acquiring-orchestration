package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.CaptureChannelResultTransactionService;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.CapturePreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 请款渠道结果的独立事务服务。
 *
 * <p>只对本地已准备的请款动作执行状态 CAS。CAS 成功后才允许更新累计请款事实并同事务写入
 * 终态 Outbox；动作缺失、重复响应或晚到响应均不得根据瞬时渠道结果补造交易。</p>
 */
@Service
public class DefaultCaptureChannelResultTransactionService implements CaptureChannelResultTransactionService {

    private final TransactionRecordService transactionRecordService;

    /**
     * 交易状态变更 Outbox 服务，与 Capture 渠道结果在同一数据库事务内写入。
     */
    private final TransactionLifecycleEventService lifecycleEventService;

    /**
     * 创建 Capture 渠道结果事务服务。
     *
     * @param transactionRecordService 交易事实记录服务
     * @param lifecycleEventService    交易终态 Outbox 服务
     */
    public DefaultCaptureChannelResultTransactionService(
            TransactionRecordService transactionRecordService,
            TransactionLifecycleEventService lifecycleEventService) {
        this.transactionRecordService = transactionRecordService;
        this.lifecycleEventService = lifecycleEventService;
    }

    /**
     * 在独立事务中持久化请款渠道同步结果。
     *
     * <p>仅对已落库的请款动作执行 CAS 完成，累计已请款金额由数据库动作记录计算。</p>
     *
     * @param preparationResultDTO 本地准备事务结果
     * @param invokeResultDTO      渠道调用结果
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean recordCaptureChannelResult(CapturePreparationResultDTO preparationResultDTO,
                                              PaymentChannelInvokeResultDTO invokeResultDTO) {
        if (preparationResultDTO == null || preparationResultDTO.getResultDTO() == null) {
            return false;
        }
        PaymentCreateResultDTO resultDTO = preparationResultDTO.getResultDTO();
        // 重新读取主库动作与原交易主单，后续 CAS 必须基于当前持久化状态而不是准备阶段快照。
        TransactionOperationDO operationDO = transactionRecordService.findSourceOperationByTransactionIdPrimary(
                resultDTO.getTransactionId(), preparationResultDTO.getCommandDTO().getTransactionDateTime());
        TransactionOrderDO sourceOrderDO = transactionRecordService.findOrderPrimary(
                resultDTO.getRootTransactionDateTime(), resultDTO.getOperationId());
        if (operationDO == null || sourceOrderDO == null) {
            return false;
        }
        // 累计请款金额由持久化成功动作计算，只有该状态更新获胜时才会改变。
        boolean statusChanged = transactionRecordService.completeCaptureChannelResult(
                operationDO,
                sourceOrderDO,
                preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getRouteResultDTO(),
                invokeResultDTO,
                resultDTO,
                preparationResultDTO.getCurrencyExponent());
        if (statusChanged && isTerminal(resultDTO)) {
            saveStatusChanged(preparationResultDTO, resultDTO);
        }
        return statusChanged;
    }

    /**
     * 终态 CAS 成功后，在当前事务内写入可恢复分片时间的状态变更事件。
     */
    private void saveStatusChanged(CapturePreparationResultDTO preparationResultDTO,
                                   PaymentCreateResultDTO resultDTO) {
        lifecycleEventService.saveStatusChanged(
                resultDTO.getTransactionId(),
                resultDTO.getOperationId(),
                preparationResultDTO.getCommandDTO().getMerchantId(),
                preparationResultDTO.getCommandDTO().getMerchantOrderNo(),
                resultDTO.getTransactionType(),
                resultDTO.getStatus(),
                preparationResultDTO.getCommandDTO().getTransactionDateTime());
    }

    /**
     * 判断渠道映射结果是否已经进入不可逆成功或失败终态。
     */
    private boolean isTerminal(PaymentCreateResultDTO resultDTO) {
        return PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus());
    }
}
