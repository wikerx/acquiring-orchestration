package com.scott.payment.admin.support.approval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 商户访问配置审批状态与交易状态绑定规则测试。
 */
class MerchantAccessApprovalStatusTests {

    @Test
    void shouldForcePendingAndRejectedTransactionsToProhibited() {
        assertThat(MerchantAccessApprovalStatus.PENDING.transactionStatus(1)).isZero();
        assertThat(MerchantAccessApprovalStatus.REJECTED.transactionStatus(1)).isZero();
    }

    @Test
    void shouldDefaultApprovedTransactionToAllowedAndHonorManualChoice() {
        assertThat(MerchantAccessApprovalStatus.APPROVED.transactionStatus(null)).isOne();
        assertThat(MerchantAccessApprovalStatus.APPROVED.transactionStatus(0)).isZero();
        assertThat(MerchantAccessApprovalStatus.APPROVED.transactionStatus(1)).isOne();
    }

    @Test
    void shouldRejectUnsupportedApprovedTransactionStatus() {
        assertThatThrownBy(() -> MerchantAccessApprovalStatus.APPROVED.transactionStatus(2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status must be 0 or 1");
    }
}
