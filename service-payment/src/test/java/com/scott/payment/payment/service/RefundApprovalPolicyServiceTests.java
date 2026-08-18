package com.scott.payment.payment.service;

import com.scott.payment.payment.config.RefundManagementProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalPolicyServiceTests
 * @date : 2026-08-06 00:00
 * @description : 退款审批策略行为测试，验证默认兼容、部分退款策略和错误配置阻断。
 * @status : create
 */
class RefundApprovalPolicyServiceTests {

    @Test
    void defaultConfigurationKeepsExistingRefundFlow() {
        RefundManagementProperties properties = new RefundManagementProperties();
        RefundApprovalPolicyService service = new RefundApprovalPolicyService(properties);

        assertThat(service.requiresApproval("PARTIAL")).isFalse();
        assertThat(service.requiresApproval("FULL")).isFalse();
    }

    @Test
    void partialOnlyPolicyDoesNotTreatFinalPartialRefundAsFullRefund() {
        RefundManagementProperties properties = enabled("PARTIAL_ONLY");
        RefundApprovalPolicyService service = new RefundApprovalPolicyService(properties);

        assertThat(service.requiresApproval("PARTIAL")).isTrue();
        assertThat(service.requiresApproval("FULL")).isFalse();
    }

    @Test
    void invalidEnabledPolicyBlocksRefundInsteadOfBypassingApproval() {
        RefundManagementProperties properties = enabled("AMOUNT_THRESHOLD");
        RefundApprovalPolicyService service = new RefundApprovalPolicyService(properties);

        assertThatThrownBy(() -> service.requiresApproval("PARTIAL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approval policy");
    }

    private RefundManagementProperties enabled(String policy) {
        RefundManagementProperties properties = new RefundManagementProperties();
        properties.setEnabled(true);
        properties.setApprovalEnabled(true);
        properties.setApprovalPolicy(policy);
        return properties;
    }
}
