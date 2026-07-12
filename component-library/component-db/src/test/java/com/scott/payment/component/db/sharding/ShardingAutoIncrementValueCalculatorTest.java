package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingAutoIncrementValueCalculatorTest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 分表 AUTO_INCREMENT 起始值计算测试。 <p>验证 yyyyQQ + 12 位自增序号的边界，避免超过 MySQL BIGINT 有符号范围。</p>
 * @status : create
 */
class ShardingAutoIncrementValueCalculatorTest {

    /**
     * 验证 2026 年第二季度的起始值和最大安全值。
     */
    @Test
    void shouldCalculateAutoIncrementRange() {
        ShardingAutoIncrementValueCalculator calculator = new ShardingAutoIncrementValueCalculator();

        ShardingAutoIncrementRange range = calculator.calculate(new PaymentQuarterShardingProperties(), new ShardingQuarter(2026, 2));

        assertThat(range.prefix()).isEqualTo(202602L);
        assertThat(range.startValue()).isEqualTo(202602000000000001L);
        assertThat(range.maxValue()).isEqualTo(202602999999999999L);
    }

    /**
     * 验证超出序号宽度的最大值会被拒绝。
     */
    @Test
    void shouldRejectMaxSequenceOutsideWidth() {
        ShardingAutoIncrementValueCalculator calculator = new ShardingAutoIncrementValueCalculator();
        PaymentQuarterShardingProperties properties = new PaymentQuarterShardingProperties();
        properties.getIdGenerator().setSequenceWidth(2);
        properties.getIdGenerator().setMaxSequence(100L);

        assertThatThrownBy(() -> calculator.calculate(properties, new ShardingQuarter(2026, 2)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("sequence width");
    }
}
