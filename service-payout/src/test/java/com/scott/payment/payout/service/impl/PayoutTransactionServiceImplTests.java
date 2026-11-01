package com.scott.payment.payout.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证代付核心闭环启用前，创建入口不会返回无法追溯的虚假受理结果。
 */
class PayoutTransactionServiceImplTests {

    /**
     * 验证完整请求也会被明确拒绝，避免生成未持久化且未送渠道的代付单号。
     */
    @Test
    void shouldFailClosedBeforePayoutCoreIsAvailable() {
        PayoutCreateCommandDTO command = new PayoutCreateCommandDTO();
        command.setMerchantId("200001");
        command.setMerchantOrderNo("PAYOUT-20261101-001");
        command.setAmount(new BigDecimal("100.00"));
        command.setCurrency("USD");

        assertThatThrownBy(() -> new PayoutTransactionServiceImpl().createPayout(command))
                .isInstanceOf(ServiceException.class)
                .extracting(exception -> ((ServiceException) exception).getCode())
                .isEqualTo(ApiResultEnum.PAYOUT_CORE_UNAVAILABLE.getCode());
    }
}
