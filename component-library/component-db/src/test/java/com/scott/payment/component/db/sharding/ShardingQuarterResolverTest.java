package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingQuarterResolverTest
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingQuarterResolverTest 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
class ShardingQuarterResolverTest {

    /**
     * 验证交易时间可以解析为正确季度后缀。
     */
    @Test
    void shouldResolveQuarterSuffix() {
        ShardingQuarterResolver resolver = new ShardingQuarterResolver();

        ShardingQuarter quarter = resolver.fromDateTime(LocalDateTime.of(2026, 5, 29, 10, 30));

        assertThat(quarter.suffix()).isEqualTo("202602");
        assertThat(quarter.displayName()).isEqualTo("2026-Q2");
    }

    /**
     * 验证第四季度下一个季度为下一年第一季度。
     */
    @Test
    void shouldMoveQ4ToNextYearQ1() {
        ShardingQuarter next = new ShardingQuarter(2026, 4).next();

        assertThat(next.year()).isEqualTo(2027);
        assertThat(next.quarter()).isEqualTo(1);
        assertThat(next.suffix()).isEqualTo("202701");
    }

    /**
     * 验证配置范围可以展开为连续季度。
     */
    @Test
    void shouldListQuartersInRange() {
        ShardingQuarterResolver resolver = new ShardingQuarterResolver();
        PaymentQuarterShardingProperties.TableRule rule = new PaymentQuarterShardingProperties.TableRule();
        rule.setStartYear(2026);
        rule.setStartQuarter(3);
        rule.setEndYear(2027);
        rule.setEndQuarter(1);

        List<ShardingQuarter> quarters = resolver.quartersInRange(rule);

        assertThat(quarters).containsExactly(
                new ShardingQuarter(2026, 3),
                new ShardingQuarter(2026, 4),
                new ShardingQuarter(2027, 1)
        );
    }
}
