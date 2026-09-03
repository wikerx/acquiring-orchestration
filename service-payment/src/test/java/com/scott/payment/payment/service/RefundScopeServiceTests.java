package com.scott.payment.payment.service;

import com.scott.payment.payment.entity.TransactionOrderDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundScopeServiceTests
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 退款范围金额规则测试，确保 FULL 相对原始本金判断而不是相对当前剩余可退额度判断。
 * @status : create
 */
class RefundScopeServiceTests {

    private final RefundScopeService service = new RefundScopeService();

    @Test
    void firstRefundForOriginalPrincipalIsFull() {
        TransactionOrderDO order = order("100.00", "100.00", "0.00");

        assertThat(service.resolve(order, new BigDecimal("100.00"), BigDecimal.ZERO))
                .isEqualTo("FULL");
    }

    @Test
    void refundingAllRemainingAmountAfterPriorRefundIsStillPartial() {
        TransactionOrderDO order = order("100.00", "30.00", "70.00");

        assertThat(service.resolve(order, new BigDecimal("30.00"), BigDecimal.ZERO))
                .isEqualTo("PARTIAL");
    }

    @Test
    void concurrentPendingRefundPreventsFullClassification() {
        TransactionOrderDO order = order("100.00", "100.00", "0.00");

        assertThat(service.resolve(order, new BigDecimal("90.00"), new BigDecimal("10.00")))
                .isEqualTo("PARTIAL");
    }

    private TransactionOrderDO order(String principal, String available, String refunded) {
        TransactionOrderDO order = new TransactionOrderDO();
        order.setTransactionAmount(new BigDecimal(principal));
        order.setAvailableRefundAmount(new BigDecimal(available));
        order.setRefundedAmount(new BigDecimal(refunded));
        return order;
    }
}
