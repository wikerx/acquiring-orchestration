package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionQueryJdbcTemplateFactoryTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 交易查询 JDBC 语句超时工厂测试。
 * @status : create
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
