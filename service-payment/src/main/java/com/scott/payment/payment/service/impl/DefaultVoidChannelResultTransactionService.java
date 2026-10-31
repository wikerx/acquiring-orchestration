package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.VoidChannelResultTransactionService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.VoidPreparationResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 撤销渠道结果的独立事务服务。
 *
 * <p>撤销结果只推进本地已准备动作，并通过当前持久化状态 CAS 防止重复或晚到结果覆盖终态。
 * 终态变化与生命周期 Outbox 在同一事务提交。</p>
 */
@Service
public class DefaultVoidChannelResultTransactionService implements VoidChannelResultTransactionService {

    private final TransactionRecordService transactionRecordService;

    /**
     * 交易状态变更 Outbox 服务，与 Void 渠道结果在同一数据库事务内写入。
     */
    private final TransactionLifecycleEventService lifecycleEventService;

    /**
     * 创建 Void 渠道结果事务服务。
     *
     * @param transactionRecordService 交易事实记录服务
     * @param lifecycleEventService    交易终态 Outbox 服务
     */
    public DefaultVoidChannelResultTransactionService(
            TransactionRecordService transactionRecordService,
            TransactionLifecycleEventService lifecycleEventService) {
        this.transactionRecordService = transactionRecordService;
        this.lifecycleEventService = lifecycleEventService;
    }

    /**
     * 在独立事务中持久化撤销渠道同步结果。
     *
     * <p>仅对已落库的撤销动作执行 CAS 完成，迟到或重复结果不得覆盖动作终态。</p>
     *
     * @param preparationResultDTO 本地准备事务结果
     * @param invokeResultDTO      渠道调用结果
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean recordVoidChannelResult(VoidPreparationResultDTO preparationResultDTO,
                                           PaymentChannelInvokeResultDTO invokeResultDTO) {
        if (preparationResultDTO == null || preparationResultDTO.getResultDTO() == null) {
            return false;
        }
        PaymentCreateResultDTO resultDTO = preparationResultDTO.getResultDTO();
        // 重新读取主库动作与原交易主单，避免准备阶段快照在并发回调后失效。
        TransactionOperationDO operationDO = transactionRecordService.findSourceOperationByTransactionIdPrimary(
                resultDTO.getTransactionId(), preparationResultDTO.getCommandDTO().getTransactionDateTime());
        TransactionOrderDO sourceOrderDO = transactionRecordService.findOrderPrimary(
                resultDTO.getRootTransactionDateTime(), resultDTO.getOperationId());
        if (operationDO == null || sourceOrderDO == null) {
            return false;
        }
        // CAS 返回 false 表示其他路径已经收敛该动作，本次结果不得再次推进状态。
        boolean statusChanged = transactionRecordService.completeVoidChannelResult(
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

    /** 终态 CAS 成功后，在当前事务内写入可恢复分片时间的状态变更事件。 */
    private void saveStatusChanged(VoidPreparationResultDTO preparationResultDTO,
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

    /** 判断渠道映射结果是否已经进入不可逆成功或失败终态。 */
    private boolean isTerminal(PaymentCreateResultDTO resultDTO) {
        return PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus());
    }
}
