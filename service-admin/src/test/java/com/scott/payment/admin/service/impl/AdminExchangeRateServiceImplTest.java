package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeRateRuleDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminExchangeRateServiceImplTest
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : AdminExchangeRateServiceImplTest 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
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
