package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 交易查询 JDBC 语句超时工厂测试。
 */
class TransactionQueryJdbcTemplateFactoryTest {

    @Test
    void shouldRoundConfiguredMillisecondsUpToJdbcTimeoutSeconds() {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.getQueryBudget().setSynchronousTimeoutMillis(2501L);
        TransactionQueryJdbcTemplateFactory factory = new TransactionQueryJdbcTemplateFactory();

        var template = factory.create(new SingleConnectionDataSource(), properties);

        assertThat(template.getJdbcTemplate().getQueryTimeout()).isEqualTo(3);
    }
}
