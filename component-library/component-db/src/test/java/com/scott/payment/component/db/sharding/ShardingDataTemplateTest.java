package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingDataTemplateTest
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 分表数据访问统一入口测试，覆盖读写数据源约束、上下文校验和安全物理表解析。
 * @status : create
 */
class ShardingDataTemplateTest {

    /**
     * 验证单表查询通过统一入口获得安全物理表名。
     */
    @Test
    void shouldQueryOneWithResolvedPhysicalTable() {
        ShardingDataTemplate template = template(propertiesWithRule("transaction_order"));

        String result = template.queryOne(
                ShardingSingleTableContext.of("transaction_order", LocalDateTime.of(2026, 5, 1, 0, 0), DataSourceName.SLAVE),
                table -> table);

        assertThat(result).isEqualTo("transaction_order_202602");
    }

    /**
     * 验证范围查询通过统一入口获得按季度倒序排列的物理表。
     */
    @Test
    void shouldQueryRangeWithResolvedPhysicalTables() {
        ShardingDataTemplate template = template(propertiesWithRule("transaction_operation"));

        List<String> result = template.queryRange(
                ShardingRangeTableContext.of(
                        "transaction_operation",
                        LocalDateTime.of(2026, 3, 31, 23, 59, 59),
                        LocalDateTime.of(2026, 7, 1, 0, 0),
                        DataSourceName.SLAVE),
                tables -> tables);

        assertThat(result).containsExactly(
                "transaction_operation_202603",
                "transaction_operation_202602",
                "transaction_operation_202601");
    }

    /**
     * 验证写操作只能显式走主库，避免在从库执行资金状态变更。
     */
    @Test
    void shouldRejectWriteOperationOnSlaveDataSource() {
        ShardingDataTemplate template = template(propertiesWithRule("transaction_order"));

        assertThatThrownBy(() -> template.update(
                ShardingSingleTableContext.of("transaction_order", LocalDateTime.of(2026, 5, 1, 0, 0), DataSourceName.SLAVE),
                table -> 1))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("write operation must use master");
    }

    /**
     * 验证非法数据源名称不会进入分表数据访问回调。
     */
    @Test
    void shouldRejectInvalidDataSource() {
        ShardingDataTemplate template = template(propertiesWithRule("transaction_order"));

        assertThatThrownBy(() -> template.queryOne(
                ShardingSingleTableContext.of("transaction_order", LocalDateTime.of(2026, 5, 1, 0, 0), "reporting"),
                table -> table))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("data source is invalid");
    }

    /**
     * 验证单表操作必须传分表时间，禁止按交易号或商户号猜测物理表。
     */
    @Test
    void shouldRequireShardingTimeForSingleTableOperation() {
        ShardingDataTemplate template = template(propertiesWithRule("transaction_order"));

        assertThatThrownBy(() -> template.queryOne(
                ShardingSingleTableContext.of("transaction_order", null, DataSourceName.SLAVE),
                table -> table))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("sharding time is required");
    }

    /**
     * 验证空回调会被统一入口拦截，避免解析成功后出现空指针。
     */
    @Test
    void shouldRequireCallback() {
        ShardingDataTemplate template = template(propertiesWithRule("transaction_order"));

        assertThatThrownBy(() -> template.queryRange(
                ShardingRangeTableContext.of("transaction_order", null, LocalDateTime.of(2026, 5, 1, 0, 0)),
                null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("sharding callback is required");
    }

    private ShardingDataTemplate template(PaymentQuarterShardingProperties properties) {
        ShardingTableRangeResolver rangeResolver = new ShardingTableRangeResolver(
                properties,
                new ShardingQuarterResolver(),
                new ShardingPhysicalTableNameResolver());
        return new ShardingDataTemplate(rangeResolver);
    }

    private PaymentQuarterShardingProperties propertiesWithRule(String logicalTable) {
        PaymentQuarterShardingProperties properties = new PaymentQuarterShardingProperties();
        PaymentQuarterShardingProperties.TableRule rule = new PaymentQuarterShardingProperties.TableRule();
        rule.setLogicalTable(logicalTable);
        rule.setTemplateTable(logicalTable);
        rule.setStartYear(2026);
        rule.setStartQuarter(1);
        rule.setEndYear(2026);
        rule.setEndQuarter(4);
        rule.setTableNameFormat("%s_%d%02d");
        properties.getTables().put(logicalTable, rule);
        return properties;
    }
}
