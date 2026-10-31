package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentPendingReasonEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;

/**
 * 支付准备链路的风控决策映射规则。
 *
 * <p>风控原始原因与支付平台状态保持分离。REQUIRE_3DS、REVIEW 分别进入受控待处理阶段，
 * 明确拒绝进入失败；任何 decision 与 passed 标志矛盾的结果按 UNKNOWN 处理，禁止放行。</p>
 */
final class PaymentRiskDecisionSupport {

    /** 对商户统一暴露的风控阻断说明，避免泄露内部规则、名单或评分细节。 */
    static final String MERCHANT_RISK_BLOCKED_MESSAGE = "Risk blocked";

    private PaymentRiskDecisionSupport() {
    }

    static PaymentRiskDecisionEnum resolve(PaymentRiskDecisionDTO riskDecisionDTO) {
        if (riskDecisionDTO == null) {
            return PaymentRiskDecisionEnum.UNKNOWN;
        }
        PaymentRiskDecisionEnum decisionEnum = PaymentRiskDecisionEnum.of(riskDecisionDTO.getDecision());
        // 风控返回自相矛盾时降级为 UNKNOWN，不能仅凭 ALLOW 类 decision 绕过 passed=false。
        if (!riskDecisionDTO.isPassed() && decisionEnum.isAllowProceed()) {
            return PaymentRiskDecisionEnum.UNKNOWN;
        }
        return decisionEnum;
    }

    static void fillStoppedResult(PaymentCreateResultDTO resultDTO, PaymentRiskDecisionEnum riskDecisionEnum) {
        // 需要交互或人工审核的决策保持 PENDING，不得提前落成失败终态。
        if (PaymentRiskDecisionEnum.REQUIRE_3DS == riskDecisionEnum) {
            resultDTO.setStatus(PaymentTransactionStatusEnum.PENDING.getCode());
            resultDTO.setProcessStage(PaymentProcessStageEnum.WAITING_3DS.getCode());
            resultDTO.setPendingReasonCode(PaymentPendingReasonEnum.NEED_REDIRECT.getCode());
            return;
        }
        if (PaymentRiskDecisionEnum.REVIEW == riskDecisionEnum) {
            resultDTO.setStatus(PaymentTransactionStatusEnum.PENDING.getCode());
            resultDTO.setProcessStage(PaymentProcessStageEnum.WAITING_RISK_REVIEW.getCode());
            resultDTO.setPendingReasonCode(PaymentPendingReasonEnum.RISK_REVIEW.getCode());
            return;
        }
        // 商户只接收稳定的风控阻断说明，不暴露规则、名单、评分或风控服务原文。
        resultDTO.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        resultDTO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        resultDTO.setFailReasonCode(PaymentFailureReasonEnum.RISK_REJECTED.getCode());
        resultDTO.setFailReasonMessage(MERCHANT_RISK_BLOCKED_MESSAGE);
        resultDTO.setMerchantResponseMessage(MERCHANT_RISK_BLOCKED_MESSAGE);
    }

    static boolean isRiskRejected(String failReasonCode) {
        return PaymentFailureReasonEnum.RISK_REJECTED.getCode().equals(failReasonCode);
    }

    static String flowEventStatus(PaymentRiskDecisionEnum riskDecisionEnum) {
        if (riskDecisionEnum != null && riskDecisionEnum.isAllowProceed()) {
            return PaymentTransactionStatusEnum.SUCCESS.getCode();
        }
        if (PaymentRiskDecisionEnum.REVIEW == riskDecisionEnum || PaymentRiskDecisionEnum.REQUIRE_3DS == riskDecisionEnum) {
            return PaymentTransactionStatusEnum.PENDING.getCode();
        }
        return PaymentTransactionStatusEnum.FAILED.getCode();
    }
}
