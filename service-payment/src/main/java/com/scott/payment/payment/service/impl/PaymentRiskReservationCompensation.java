package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRiskReservationCompensation
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 支付本地准备事务回滚后的风控预占补偿。
 * @status : create
 */
@Slf4j
public class PaymentRiskReservationCompensation {

    /** 支付本地事务未提交时发送给风控预占回滚接口的稳定原因。 */
    private static final String ROLLBACK_REASON = "payment local preparation rolled back";

    /** 风控评估与商户累计限额预占补偿入口。 */
    private final PaymentRiskInvokeService paymentRiskInvokeService;

    /**
     * 创建风控预占事务补偿器。
     *
     * @param paymentRiskInvokeService 风控调用服务
     */
    public PaymentRiskReservationCompensation(PaymentRiskInvokeService paymentRiskInvokeService) {
        this.paymentRiskInvokeService = paymentRiskInvokeService;
    }

    /**
     * 为已成功预占的风控限额注册本地事务完成回调。
     *
     * <p>本地事务提交后保留预占；回滚或异常完成时调用 service-risk 幂等撤销预占。
     * 若当前没有事务同步上下文，则先尝试撤销并立即失败，避免无补偿保护地继续支付。</p>
     *
     * @param commandDTO  支付创建命令
     * @param decisionDTO 含预占结果的风控决策
     */
    public void register(PaymentCreateCommandDTO commandDTO,
                         PaymentRiskDecisionDTO decisionDTO) {
        if (decisionDTO == null || !decisionDTO.isMerchantLimitReserved()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            paymentRiskInvokeService.cancelMerchantLimitReservation(
                    commandDTO,
                    decisionDTO,
                    "payment transaction synchronization unavailable");
            throw new IllegalStateException(
                    "transaction synchronization is required for risk reservation");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    paymentRiskInvokeService.cancelMerchantLimitReservation(
                            commandDTO,
                            decisionDTO,
                            ROLLBACK_REASON);
                } catch (RuntimeException exception) {
                    log.error("event: PAYMENT_RISK_RESERVATION_COMPENSATION_FAILED transactionId: {} riskRecordNo: {} exceptionType: {}",
                            commandDTO == null ? null : commandDTO.getTransactionId(),
                            decisionDTO.getRiskRecordNo(),
                            exception.getClass().getSimpleName());
                }
            }
        });
    }
}
