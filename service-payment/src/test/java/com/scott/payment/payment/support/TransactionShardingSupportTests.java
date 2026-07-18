package com.scott.payment.payment.support;

import com.scott.payment.component.db.sharding.PaymentQuarterShardingProperties;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingSupportTests
 * @date : 2026-07-15 15:13
 * @email : scott_x@163.com
 * @description : 交易分表支撑组件测试，验证无前缀 transaction_id 和历史 TX 交易号都能解析交易时间片。
 * @status : create
 */
class TransactionShardingSupportTests {

    /**
     * 新平台交易 ID 不携带 TX 前缀，但仍能从前 17 位解析 transaction_date_time。
     */
    @Test
    void shouldParsePrefixlessTransactionId() {
        TransactionShardingSupport support = support();

        LocalDateTime parsed = support.parseTransactionDateTime("202607151512134560001");

        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 7, 15, 15, 12, 13, 456_000_000));
    }

    /**
     * 历史 TX 交易号仍需兼容解析，避免后台交易查询和后续动作找不到历史分表。
     */
    @Test
    void shouldParseLegacyTxTransactionId() {
        TransactionShardingSupport support = support();

        LocalDateTime parsed = support.parseTransactionDateTime("TX202607151512134560001");

        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 7, 15, 15, 12, 13, 456_000_000));
    }

    private TransactionShardingSupport support() {
        PaymentQuarterShardingProperties properties = new PaymentQuarterShardingProperties();
        PaymentQuarterShardingProperties.TableRule rule = new PaymentQuarterShardingProperties.TableRule();
        rule.setLogicalTable("transaction_operation");
        rule.setTemplateTable("transaction_operation");
        rule.setStartYear(2026);
        rule.setStartQuarter(1);
        rule.setEndYear(2035);
        rule.setEndQuarter(4);
        rule.setTableNameFormat("%s_%d%02d");
        properties.getTables().put("transaction_operation", rule);
        return new TransactionShardingSupport(properties, new ShardingQuarterResolver(), new ShardingPhysicalTableNameResolver());
    }
}
