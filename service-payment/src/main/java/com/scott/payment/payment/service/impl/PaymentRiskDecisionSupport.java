package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentPendingReasonEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;

/**
 * 支付风控决策映射工具，统一交易准备链路对风控返回值和拦截状态的解释。
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
        if (!riskDecisionDTO.isPassed() && decisionEnum.isAllowProceed()) {
            return PaymentRiskDecisionEnum.UNKNOWN;
        }
        return decisionEnum;
    }

    static void fillStoppedResult(PaymentCreateResultDTO resultDTO, PaymentRiskDecisionEnum riskDecisionEnum) {
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
