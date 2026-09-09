package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingGovernancePropertiesTest
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 验证交易物理表治理配置使用独立前缀，避免重新依赖旧业务路由配置。
 * @status : create
 */
class TransactionShardingGovernancePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldBindGovernancePrefixWithoutConsumingLegacyRoutingRules() {
        contextRunner.withPropertyValues(
                        "transaction-sharding.governance.expiry-warning-quarters=8",
                        "transaction-sharding.governance.planning-horizon-quarters=12",
                        "transaction-sharding.governance.tables[transaction_order].logical-table=transaction_order",
                        "transaction-sharding.governance.tables[transaction_order].template-table=transaction_order",
                        "transaction-sharding.governance.tables[transaction_order].start-year=2026",
                        "transaction-sharding.governance.tables[transaction_order].start-quarter=3",
                        "transaction-sharding.governance.tables[transaction_order].end-year=2030",
                        "transaction-sharding.governance.tables[transaction_order].end-quarter=4",
                        "global-payment.sharding.tables[legacy_only].logical-table=legacy_only")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TransactionShardingGovernanceProperties properties =
                            context.getBean(TransactionShardingGovernanceProperties.class);
                    assertThat(properties.getExpiryWarningQuarters()).isEqualTo(8);
                    assertThat(properties.getPlanningHorizonQuarters()).isEqualTo(12);
                    assertThat(properties.getTables()).containsOnlyKeys("transaction_order");
                    assertThat(properties.getTables().get("transaction_order").getShardingColumn())
                            .isEqualTo(TransactionShardingProperties.REQUIRED_SHARDING_COLUMN);
                    assertThat(properties.getTables().get("transaction_order").getActualDataSource())
                            .isEqualTo("master");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TransactionShardingGovernanceProperties.class)
    static class TestConfiguration {
    }
}
