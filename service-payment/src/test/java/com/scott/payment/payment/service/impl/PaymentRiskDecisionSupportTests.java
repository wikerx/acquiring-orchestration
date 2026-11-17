package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 人工复核暂不开放时的支付阻断回归，保留 3DS 的独立交互流程。
 */
class PaymentRiskDecisionSupportTests {

    /**
     * 遗留规则或基础设施异常返回复核结论时应拒绝本次支付，不生成等待人工审核的孤立交易。
     */
    @Test
    void reviewMustFailClosedWithoutPendingReason() {
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        PaymentRiskDecisionSupport.fillStoppedResult(result, PaymentRiskDecisionEnum.REVIEW);
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getProcessStage()).isEqualTo("FINISHED");
        assertThat(result.getFailReasonCode()).isEqualTo("RISK_REJECTED");
        assertThat(result.getPendingReasonCode()).isNull();
        assertThat(result.getMerchantResponseMessage()).isEqualTo("Risk blocked");
        assertThat(PaymentRiskDecisionSupport.flowEventStatus(PaymentRiskDecisionEnum.REVIEW)).isEqualTo("FAILED");
    }

    /**
     * 禁用人工复核不能把正常 3DS 交互误改成拒绝。
     */
    @Test
    void threeDsStillWaitsForCustomerInteraction() {
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        PaymentRiskDecisionSupport.fillStoppedResult(result, PaymentRiskDecisionEnum.REQUIRE_3DS);
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getProcessStage()).isEqualTo("WAITING_3DS");
        assertThat(PaymentRiskDecisionSupport.flowEventStatus(PaymentRiskDecisionEnum.REQUIRE_3DS)).isEqualTo("PENDING");
    }
}
