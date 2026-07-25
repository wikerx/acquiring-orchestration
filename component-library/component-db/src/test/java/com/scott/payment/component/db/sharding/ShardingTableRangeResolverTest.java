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
 * @classname : ShardingTableRangeResolverTest
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 季度分表物理表范围解析测试，覆盖交易查询跨季度倒序访问和物理表越界保护。
 * @status : create
 */
class ShardingTableRangeResolverTest {

    /**
     * 验证跨季度查询会按季度倒序返回安全物理表名。
     */
    @Test
    void shouldResolvePhysicalTablesInReverseQuarterOrder() {
        ShardingTableRangeResolver resolver = resolver(propertiesWithRule("transaction_operation"));

        List<String> tables = resolver.physicalTablesInRange(
                "transaction_operation",
                LocalDateTime.of(2026, 3, 31, 23, 59, 59),
                LocalDateTime.of(2026, 7, 1, 0, 0, 0));

        assertThat(tables).containsExactly(
                "transaction_operation_202603",
                "transaction_operation_202602",
                "transaction_operation_202601"
        );
    }

    /**
     * 验证开发环境真实启用季度为 2026-Q3 时，范围查询不会解析出未启用的 2026-Q1/Q2 物理表。
     */
    @Test
    void shouldClipOpenBeginTimeToConfiguredDevelopmentStartQuarter() {
        ShardingTableRangeResolver resolver = resolver(propertiesWithRule("transaction_operation", 3));

        List<String> tables = resolver.physicalTablesInRange(
                "transaction_operation",
                null,
                LocalDateTime.of(2026, 7, 25, 12, 0, 0));

        assertThat(tables).containsExactly("transaction_operation_202603");
        assertThat(tables).doesNotContain(
                "transaction_operation_202601",
                "transaction_operation_202602");
    }

    /**
     * 验证当前季度和下一季度单表路由仍可按 yyyyQQ 季度后缀生成目标物理表。
     */
    @Test
    void shouldRouteCurrentNextAndNextYearQuarterWhenStartIsDevelopmentQuarter() {
        ShardingTableRangeResolver resolver = resolver(propertiesWithRule("transaction_order", 3));

        assertThat(resolver.physicalTable("transaction_order", LocalDateTime.of(2026, 7, 1, 0, 0, 0)))
                .isEqualTo("transaction_order_202603");
        assertThat(resolver.physicalTable("transaction_order", LocalDateTime.of(2026, 10, 1, 0, 0, 0)))
                .isEqualTo("transaction_order_202604");
        assertThat(resolver.physicalTable("transaction_order", LocalDateTime.of(2027, 1, 1, 0, 0, 0)))
                .isEqualTo("transaction_order_202701");
    }

    /**
     * 验证配置 key 与 logicalTable 不一致时仍可通过规则中的 logicalTable 查找。
     */
    @Test
    void shouldResolveRuleByLogicalTableValue() {
        PaymentQuarterShardingProperties properties = propertiesWithRule("transaction_order");
        PaymentQuarterShardingProperties.TableRule rule = properties.getTables().remove("transaction_order");
        properties.getTables().put("order-rule", rule);
        ShardingTableRangeResolver resolver = resolver(properties);

        String table = resolver.physicalTable("transaction_order", LocalDateTime.of(2026, 5, 1, 0, 0, 0));

        assertThat(table).isEqualTo("transaction_order_202602");
    }

    /**
     * 验证单表路由不会允许访问配置范围之外的物理表。
     */
    @Test
    void shouldRejectSingleTableOutsideConfiguredRange() {
        ShardingTableRangeResolver resolver = resolver(propertiesWithRule("transaction_order"));

        assertThatThrownBy(() -> resolver.physicalTable("transaction_order", LocalDateTime.of(2025, 12, 31, 23, 59, 59)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("outside table range");
    }

    private ShardingTableRangeResolver resolver(PaymentQuarterShardingProperties properties) {
        return new ShardingTableRangeResolver(
                properties,
                new ShardingQuarterResolver(),
                new ShardingPhysicalTableNameResolver());
    }

    private PaymentQuarterShardingProperties propertiesWithRule(String logicalTable) {
        return propertiesWithRule(logicalTable, 1);
    }

    private PaymentQuarterShardingProperties propertiesWithRule(String logicalTable, int startQuarter) {
        PaymentQuarterShardingProperties properties = new PaymentQuarterShardingProperties();
        PaymentQuarterShardingProperties.TableRule rule = new PaymentQuarterShardingProperties.TableRule();
        rule.setLogicalTable(logicalTable);
        rule.setTemplateTable(logicalTable);
        rule.setStartYear(2026);
        rule.setStartQuarter(startQuarter);
        rule.setEndYear(2027);
        rule.setEndQuarter(4);
        rule.setTableNameFormat("%s_%d%02d");
        properties.getTables().put(logicalTable, rule);
        return properties;
    }
}
