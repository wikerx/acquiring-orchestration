package com.scott.payment.risk.service.impl;

import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;
import com.scott.payment.risk.domain.state.RiskDecisionEnum;
import com.scott.payment.risk.domain.state.RiskReasonCodeEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRiskEvaluationServiceTests
 * @date : 2026-07-12 22:43
 * @email : scott_x@163.com
 * @description : DefaultRiskEvaluationServiceTests 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 风控服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
class DefaultRiskEvaluationServiceTests {

    /**
     * service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final DefaultRiskEvaluationService service = new DefaultRiskEvaluationService();

    @Test
    void shouldPassNormalPaymentRiskEvaluation() {
        RiskPaymentEvaluateResultDTO resultDTO = service.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.NONE.getCode());
        assertThat(resultDTO.getRiskRecordNo()).startsWith("RK");
    }

    @Test
    void shouldRejectBlockedSource() {
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setSourceUrl("https://blocked.example.test/pay");

        RiskPaymentEvaluateResultDTO resultDTO = service.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.BLOCKED_SOURCE.getCode());
    }

    @Test
    void shouldRequireThreeDsForLargePaymentWithoutThreeDsProof() {
        RiskPaymentEvaluateResultDTO resultDTO = service.evaluatePayment(baseRequest(new BigDecimal("1000.00")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REQUIRE_3DS.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.THREE_DS_REQUIRED.getCode());
    }

    @Test
    void shouldReviewVeryLargePayment() {
        RiskPaymentEvaluateResultDTO resultDTO = service.evaluatePayment(baseRequest(new BigDecimal("5000.00")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REVIEW.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.MANUAL_REVIEW_REQUIRED.getCode());
    }

    private RiskPaymentEvaluateRequestDTO baseRequest(BigDecimal amount) {
        RiskPaymentEvaluateRequestDTO requestDTO = new RiskPaymentEvaluateRequestDTO();
        requestDTO.setMerchantId("200001");
        requestDTO.setMerchantOrderNo("M202607120001");
        requestDTO.setAmount(amount);
        requestDTO.setCurrency("USD");
        return requestDTO;
    }
}
