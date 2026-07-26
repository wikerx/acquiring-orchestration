package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentOrderShardingAlgorithmTest
 * @date : 2026-05-29 18:36
 * @email : scott_x@163.com
 * @description : Payment Order Sharding Algorithm Test 自动化测试类，位于 公共组件库，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
class PaymentOrderShardingAlgorithmTest {

    /**
     * 验证交易时间在配置范围内时可以路由到正确季度表。
     */
    @Test
    void shouldRouteByTransactionDateTimeQuarter() {
        PaymentOrderShardingAlgorithm algorithm = new PaymentOrderShardingAlgorithm();
        PaymentQuarterShardingProperties properties = buildProperties();

        String tableName = algorithm.tableName(properties,
                "test_transaction",
                LocalDateTime.of(2026, 5, 29, 10, 30, 0));

        assertThat(tableName).isEqualTo("test_transaction_202602");
    }

    /**
     * 验证交易时间早于环境配置起始表时直接失败，避免误写不存在的历史表。
     */
    @Test
    void shouldRejectBeforeConfiguredStartQuarter() {
        PaymentOrderShardingAlgorithm algorithm = new PaymentOrderShardingAlgorithm();
        PaymentQuarterShardingProperties properties = buildProperties();

        assertThatThrownBy(() -> algorithm.tableName(properties,
                "test_transaction",
                LocalDateTime.of(2026, 1, 1, 0, 0, 0)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("outside sharding table range");
    }

    /**
     * 验证分表配置可以生成当前环境应该存在的全部物理表。
     */
    @Test
    void shouldGeneratePhysicalTablesByConfiguredRange() {
        PaymentOrderShardingAlgorithm algorithm = new PaymentOrderShardingAlgorithm();
        PaymentQuarterShardingProperties properties = buildProperties();

        List<String> tableNames = algorithm.physicalTables(properties, "test_transaction");

        assertThat(tableNames).containsExactly(
                "test_transaction_202602",
                "test_transaction_202603",
                "test_transaction_202604",
                "test_transaction_202701"
        );
    }

    /**
     * 验证默认分表配置使用测试逻辑表和 yyyyQQ 命名。
     */
    @Test
    void shouldUseDefaultTestShardingRules() {
        PaymentOrderShardingAlgorithm algorithm = new PaymentOrderShardingAlgorithm();

        String tableName = algorithm.tableName("test_transaction_info", LocalDateTime.of(2026, 10, 1, 0, 0));

        assertThat(tableName).isEqualTo("test_transaction_info_202604");
    }

    private PaymentQuarterShardingProperties buildProperties() {
        PaymentQuarterShardingProperties properties = new PaymentQuarterShardingProperties();
        PaymentQuarterShardingProperties.TableRule tableRule = new PaymentQuarterShardingProperties.TableRule();
        tableRule.setLogicalTable("test_transaction");
        tableRule.setStartYear(2026);
        tableRule.setStartQuarter(2);
        tableRule.setEndYear(2027);
        tableRule.setEndQuarter(1);
        tableRule.setDescription("测试交易主表");
        properties.getTables().put("test-transaction", tableRule);
        return properties;
    }
}
