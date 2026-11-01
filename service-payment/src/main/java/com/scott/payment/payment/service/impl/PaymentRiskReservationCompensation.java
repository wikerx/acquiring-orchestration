package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 支付本地准备事务与远程风控限额预占之间的补偿协调器。
 *
 * <p>远程预占先于本地事务提交完成，因此本地事务未提交时必须撤销预占。补偿只绑定事务完成结果，
 * 不能在业务方法返回时提前判断；远程撤销由 service-risk 按交易号提供最终幂等保护。</p>
 */
@Slf4j
public class PaymentRiskReservationCompensation {

    /**
     * 支付本地事务未提交时发送给风控预占回滚接口的稳定原因。
     */
    private static final String ROLLBACK_REASON = "payment local preparation rolled back";

    /**
     * 风控评估与商户累计限额预占补偿入口。
     */
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
        // 没有事务同步就无法判断最终提交结果；先撤销远程预占，再阻止支付继续执行。
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            paymentRiskInvokeService.cancelMerchantLimitReservation(
                    commandDTO,
                    decisionDTO,
                    "payment transaction synchronization unavailable");
            throw new IllegalStateException(
                    "transaction synchronization is required for risk reservation");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 在事务完成后执行风险预占补偿。
             */
            @Override
            public void afterCompletion(int status) {
                // 只有明确提交才保留预占；回滚和未知完成状态都按未提交处理。
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    paymentRiskInvokeService.cancelMerchantLimitReservation(
                            commandDTO,
                            decisionDTO,
                            ROLLBACK_REASON);
                } catch (RuntimeException exception) {
                    // afterCompletion 已脱离原事务，补偿失败不能改变提交结果，只能告警并依赖幂等重试/人工处置。
                    log.error("风控限额预占补偿失败, event: PAYMENT_RISK_RESERVATION_COMPENSATION_FAILED, traceId: {}, transactionId: {}, riskRecordNo: {}, exceptionType: {}",
                            TraceContext.getTraceId(),
                            commandDTO == null ? null : commandDTO.getTransactionId(),
                            decisionDTO.getRiskRecordNo(),
                            exception.getClass().getSimpleName());
                }
            }
        });
    }
}
