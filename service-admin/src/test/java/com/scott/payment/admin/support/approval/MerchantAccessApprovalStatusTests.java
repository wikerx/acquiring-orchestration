package com.scott.payment.admin.support.approval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAccessApprovalStatusTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户访问配置审批状态与交易状态绑定规则测试。
 * @status : create
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
