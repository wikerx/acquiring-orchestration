package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.client.risk.RiskInternalClient;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientResponseDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskPaymentRiskInvokeServiceTests
 * @date : 2026-07-12 22:43
 * @email : scott_x@163.com
 * @description : Risk Payment Risk Invoke Service Tests 自动化测试类，位于 支付核心服务，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
class RiskPaymentRiskInvokeServiceTests {

    @Test
    void shouldSendOnlyCardBinAndLast4ToRiskService() {
        CapturingRiskInternalClient riskInternalClient = new CapturingRiskInternalClient(PaymentRiskDecisionEnum.PASS.getCode());
        RiskPaymentRiskInvokeService service = new RiskPaymentRiskInvokeService(riskInternalClient);
        PaymentCreateCommandDTO commandDTO = baseCommand();
        PaymentCreateCommandDTO.CardInfoDTO cardInfoDTO = new PaymentCreateCommandDTO.CardInfoDTO();
        cardInfoDTO.setCardNo("4111-1111 1111-1234");
        cardInfoDTO.setSecurityCode("123");
        commandDTO.setCardInfo(cardInfoDTO);

        PaymentRiskDecisionDTO decisionDTO = service.checkPreRoute(commandDTO);

        assertThat(decisionDTO.isPassed()).isTrue();
        assertThat(riskInternalClient.requestDTO.getCardBin()).isEqualTo("411111");
        assertThat(riskInternalClient.requestDTO.getCardLast4()).isEqualTo("1234");
        assertThat(RiskPaymentEvaluateClientRequestDTO.class.getDeclaredFields())
                .extracting("name")
                .doesNotContain("cardNo", "securityCode", "cvv");
    }

    @Test
    void shouldDenyUnknownRiskDecision() {
        CapturingRiskInternalClient riskInternalClient = new CapturingRiskInternalClient("UNKNOWN_REMOTE_DECISION");
        RiskPaymentRiskInvokeService service = new RiskPaymentRiskInvokeService(riskInternalClient);

        PaymentRiskDecisionDTO decisionDTO = service.checkPreRoute(baseCommand());

        assertThat(decisionDTO.isPassed()).isFalse();
        assertThat(decisionDTO.getDecision()).isEqualTo(PaymentRiskDecisionEnum.UNKNOWN.getCode());
    }

    private PaymentCreateCommandDTO baseCommand() {
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId("200001");
        commandDTO.setMerchantOrderNo("M202607120001");
        commandDTO.setTransactionType("AUTHORIZATION");
        commandDTO.setAmount(new BigDecimal("12.34"));
        commandDTO.setCurrency("USD");
        return commandDTO;
    }

    private static class CapturingRiskInternalClient implements RiskInternalClient {

        /**
         * decision，用于保存 Capturing Risk Internal Client 中与 结论 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final String decision;

        /**
         * request DTO 依赖，用于 Capturing Risk Internal Client 调用对应的数据访问、远程调用或领域服务能力。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private RiskPaymentEvaluateClientRequestDTO requestDTO;

        private CapturingRiskInternalClient(String decision) {
            this.decision = decision;
        }

        @Override
        public RiskPaymentEvaluateClientResponseDTO evaluatePayment(RiskPaymentEvaluateClientRequestDTO requestDTO) {
            this.requestDTO = requestDTO;
            RiskPaymentEvaluateClientResponseDTO responseDTO = new RiskPaymentEvaluateClientResponseDTO();
            responseDTO.setRiskRecordNo("RK202607120001");
            responseDTO.setDecision(decision);
            responseDTO.setReasonCode("NONE");
            responseDTO.setReasonMessage("risk rule not hit");
            return responseDTO;
        }
    }
}
