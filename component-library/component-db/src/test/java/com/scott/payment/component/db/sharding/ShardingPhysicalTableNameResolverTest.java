package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingPhysicalTableNameResolverTest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 分表物理表名解析器测试。 <p>表名会进入 DDL 拼接，因此必须覆盖正常生成和非法字符拦截。</p>
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
