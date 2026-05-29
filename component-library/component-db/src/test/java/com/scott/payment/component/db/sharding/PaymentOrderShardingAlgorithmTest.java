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
 * @date : 2026-05-29 00:00
 * @email : scott_x@163.com
 * @description : 季度分表算法测试入口，验证 transaction_date_time 路由、起始表范围和物理表清单
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
                "transaction",
                LocalDateTime.of(2026, 5, 29, 10, 30, 0));

        assertThat(tableName).isEqualTo("transaction_2026_q2");
    }

    /**
     * 验证交易时间早于环境配置起始表时直接失败，避免误写不存在的历史表。
     */
    @Test
    void shouldRejectBeforeConfiguredStartQuarter() {
        PaymentOrderShardingAlgorithm algorithm = new PaymentOrderShardingAlgorithm();
        PaymentQuarterShardingProperties properties = buildProperties();

        assertThatThrownBy(() -> algorithm.tableName(properties,
                "transaction",
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

        List<String> tableNames = algorithm.physicalTables(properties, "transaction");

        assertThat(tableNames).containsExactly(
                "transaction_2026_q2",
                "transaction_2026_q3",
                "transaction_2026_q4",
                "transaction_2027_q1"
        );
    }

    private PaymentQuarterShardingProperties buildProperties() {
        PaymentQuarterShardingProperties properties = new PaymentQuarterShardingProperties();
        PaymentQuarterShardingProperties.TableRule tableRule = new PaymentQuarterShardingProperties.TableRule();
        tableRule.setLogicalTable("transaction");
        tableRule.setStartYear(2026);
        tableRule.setStartQuarter(2);
        tableRule.setEndYear(2027);
        tableRule.setEndQuarter(1);
        tableRule.setDescription("交易流水主表");
        properties.getTables().put("transaction", tableRule);
        return properties;
    }
}
