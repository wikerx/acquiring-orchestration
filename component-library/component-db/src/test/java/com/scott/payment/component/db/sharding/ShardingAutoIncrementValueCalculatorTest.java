package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingAutoIncrementValueCalculatorTest
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Auto Increment Value Calculator Test 自动化测试类，位于 公共组件库，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
class ShardingAutoIncrementValueCalculatorTest {

    /**
     * 验证 2026 年第二季度的起始值和最大安全值。
     */
    @Test
    void shouldCalculateAutoIncrementRange() {
        ShardingAutoIncrementValueCalculator calculator = new ShardingAutoIncrementValueCalculator();

        ShardingAutoIncrementRange range = calculator.calculate(new TransactionShardingGovernanceProperties(), new ShardingQuarter(2026, 2));

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
        TransactionShardingGovernanceProperties properties = new TransactionShardingGovernanceProperties();
        properties.getIdGenerator().setSequenceWidth(2);
        properties.getIdGenerator().setMaxSequence(100L);

        assertThatThrownBy(() -> calculator.calculate(properties, new ShardingQuarter(2026, 2)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("sequence width");
    }
}
