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
 * @description : Admin Exchange Rate Service Impl Test 服务实现，位于 运营后台服务，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
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
