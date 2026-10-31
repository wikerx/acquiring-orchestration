package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.IncrementalAuthorizationChannelResultTransactionService;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.IncrementalAuthorizationPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 增额授权渠道结果的独立事务服务。
 *
 * <p>服务重新读取主库动作与原交易主单后执行状态 CAS。只有 CAS 获胜的成功动作才能增加累计
 * 授权金额，并与终态 Outbox 共同提交；重复或晚到结果不得重复增加授权额度。</p>
 */
@Service
public class DefaultIncrementalAuthorizationChannelResultTransactionService implements IncrementalAuthorizationChannelResultTransactionService {

    private final TransactionRecordService transactionRecordService;

    /**
     * 交易状态变更 Outbox 服务，与增量授权渠道结果在同一数据库事务内写入。
     */
    private final TransactionLifecycleEventService lifecycleEventService;

    /**
     * 创建增量授权渠道结果事务服务。
     *
     * @param transactionRecordService 交易事实记录服务
     * @param lifecycleEventService    交易终态 Outbox 服务
     */
    public DefaultIncrementalAuthorizationChannelResultTransactionService(
            TransactionRecordService transactionRecordService,
            TransactionLifecycleEventService lifecycleEventService) {
        this.transactionRecordService = transactionRecordService;
        this.lifecycleEventService = lifecycleEventService;
    }

    /**
     * 在独立事务中持久化增量授权渠道同步结果。
     *
     * <p>仅对已落库的增量授权动作执行 CAS 完成；动作不存在时不根据瞬时渠道响应伪造交易事实。</p>
     *
     * @param preparationResultDTO 本地准备事务结果
     * @param invokeResultDTO      渠道调用结果
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean recordIncrementalAuthorizationChannelResult(
            IncrementalAuthorizationPreparationResultDTO preparationResultDTO,
            PaymentChannelInvokeResultDTO invokeResultDTO) {
        if (preparationResultDTO == null || preparationResultDTO.getResultDTO() == null) {
            return false;
        }
        PaymentCreateResultDTO resultDTO = preparationResultDTO.getResultDTO();
        // 结果落库以当前主库事实为准，不能继续信任渠道调用前的准备快照。
        TransactionOperationDO operationDO = transactionRecordService.findSourceOperationByTransactionIdPrimary(
                resultDTO.getTransactionId(), preparationResultDTO.getCommandDTO().getTransactionDateTime());
        TransactionOrderDO sourceOrderDO = transactionRecordService.findOrderPrimary(
                resultDTO.getRootTransactionDateTime(), resultDTO.getOperationId());
        if (operationDO == null || sourceOrderDO == null) {
            return false;
        }
        // 累计授权金额的变化封装在状态 CAS 内，确保并发结果至多生效一次。
        boolean statusChanged = transactionRecordService.completeIncrementalAuthorizationChannelResult(
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
    private void saveStatusChanged(IncrementalAuthorizationPreparationResultDTO preparationResultDTO,
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
