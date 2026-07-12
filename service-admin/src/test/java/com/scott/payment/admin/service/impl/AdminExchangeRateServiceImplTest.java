package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeRateRuleDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminExchangeRateServiceImplTest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Exchange Rate Service Impl Test，位于 service-admin 的测试层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
class AdminExchangeRateServiceImplTest {

    @Test
    void shouldCalculateFinalRateWithBpMarkup() {
        AdminExchangeRateServiceImpl service = newService();
        ExchangeRateRuleDO rule = rule("UP", "BP", "20", 8, "ROUND_HALF_UP");

        BigDecimal finalRate = service.calculateFinalRate(new BigDecimal("7.83250000"), rule);

        assertThat(finalRate).isEqualByComparingTo("7.84816500");
    }

    @Test
    void shouldCalculateFinalRateWithPercentDiscountAndRoundDown() {
        AdminExchangeRateServiceImpl service = newService();
        ExchangeRateRuleDO rule = rule("DOWN", "PERCENT", "0.30", 8, "ROUND_DOWN");

        BigDecimal finalRate = service.calculateFinalRate(new BigDecimal("7.83256789"), rule);

        assertThat(finalRate).isEqualByComparingTo("7.80907018");
    }

    @Test
    void shouldKeepOriginalRateWhenAdjustDirectionIsNone() {
        AdminExchangeRateServiceImpl service = newService();
        ExchangeRateRuleDO rule = rule("NONE", "BP", "0", 8, "ROUND_HALF_UP");

        BigDecimal finalRate = service.calculateFinalRate(new BigDecimal("1.234567891"), rule);

        assertThat(finalRate).isEqualByComparingTo("1.23456789");
    }

    private AdminExchangeRateServiceImpl newService() {
        return new AdminExchangeRateServiceImpl(null, null, null, null, null);
    }

    private ExchangeRateRuleDO rule(String direction, String method, String value, int scale, String roundingMode) {
        ExchangeRateRuleDO rule = new ExchangeRateRuleDO();
        rule.setAdjustDirection(direction);
        rule.setAdjustMethod(method);
        rule.setAdjustValue(new BigDecimal(value));
        rule.setDecimalScale(scale);
        rule.setRoundingMode(roundingMode);
        return rule;
    }
}
