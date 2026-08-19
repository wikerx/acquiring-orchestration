package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.CurrentFeeResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FeeRuleResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FeeTierResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFinanceCacheSerializationTests
 * @date : 2026-08-18 20:20
 * @email : scott_x@163.com
 * @description : 验证商户当前生效费率满足统一 Redis 精确类型白名单和金额精度约束。
 * @status : create
 */
@Slf4j
class MerchantFinanceCacheSerializationTests {

    /**
     * 当前费率缓存必须保留版本、生效时间、费率金额和阶梯规则的精确值。
     */
    @Test
    void shouldRoundTripCurrentFeeThroughRegisteredRedisSerializer() {
        log.info("测试商户当前费率 Redis 往返序列化，版本：v2，费率：2.3000%");
        FeeTierResponse tier = new FeeTierResponse();
        tier.setLowerBound(new BigDecimal("1000.00"));
        tier.setPercentageRate(new BigDecimal("2.3000"));
        FeeRuleResponse rule = new FeeRuleResponse();
        rule.setFeeCategory("TRANSACTION_FEE");
        rule.setPercentageRate(new BigDecimal("2.3000"));
        rule.setFixedAmountUsd(new BigDecimal("1.00"));
        rule.setTiers(new ArrayList<>());
        rule.getTiers().add(tier);
        CurrentFeeResponse source = new CurrentFeeResponse();
        source.setDisplayName("Merchant fee v2");
        source.setVersionNo(2);
        source.setEffectiveTime(LocalDateTime.of(2026, 8, 18, 20, 20));
        source.setRules(new ArrayList<>());
        source.getRules().add(rule);
        RedisSerializer<Object> serializer = PaymentRedisSerializerFactory.create();

        Object restored = serializer.deserialize(serializer.serialize(source));

        assertThat(restored).usingRecursiveComparison().isEqualTo(source);
        log.info("商户当前费率 Redis 往返序列化测试通过，规则数量：{}", source.getRules().size());
    }
}
