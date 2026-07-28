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
 * @description : Default Risk Evaluation Service Tests 自动化测试类，位于 风控服务，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
class DefaultRiskEvaluationServiceTests {

    /**
     * service 依赖，用于 Default Risk Evaluation Service Tests 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
