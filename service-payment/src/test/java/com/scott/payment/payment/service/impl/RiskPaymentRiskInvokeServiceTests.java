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
 * @description : RiskPaymentRiskInvokeServiceTests 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 支付核心服务层，输入输出边界由所在包和公开方法契约限定。
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
         * decision 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final String decision;

        /**
         * request DTO 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private RiskPaymentEvaluateClientRequestDTO requestDTO;

        private CapturingRiskInternalClient(String decision) {
            this.decision = decision;
        }

        @Override
        /**
         * 完成 evaluate Payment 分支的校验或转换，返回值供当前调用链继续组装结果。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param requestDTO 内部客户端请求 DTO，携带跨服务调用所需的交易、金额和商户维度字段
         * @return 当前方法计算或转换后的业务结果
         */
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
