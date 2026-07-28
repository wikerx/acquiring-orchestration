package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingPhysicalTableNameResolverTest
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Physical Table Name Resolver Test 自动化测试类，位于 公共组件库，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
class ShardingPhysicalTableNameResolverTest {

    /**
     * 验证物理表名使用 logical_table_yyyyQQ 格式。
     */
    @Test
    void shouldResolvePhysicalTableName() {
        ShardingPhysicalTableNameResolver resolver = new ShardingPhysicalTableNameResolver();
        PaymentQuarterShardingProperties.TableRule rule = new PaymentQuarterShardingProperties.TableRule();
        rule.setLogicalTable("test_transaction");

        String physicalTableName = resolver.physicalTableName(rule, new ShardingQuarter(2026, 2));

        assertThat(physicalTableName).isEqualTo("test_transaction_202602");
    }

    /**
     * 验证危险表名会被拒绝，避免污染 DDL。
     */
    @Test
    void shouldRejectUnsafeIdentifier() {
        ShardingPhysicalTableNameResolver resolver = new ShardingPhysicalTableNameResolver();

        assertThatThrownBy(() -> resolver.requireSafeIdentifier("test_transaction;drop table x", "table"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("unsafe");
    }
}
