package com.scott.payment.component.db.sharding;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.baomidou.dynamic.datasource.enums.SeataMode;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.scott.payment.component.db.constant.DataSourceName;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.jdbc.core.datasource.ShardingSphereDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingDataSourceConfigurationTest
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 验证交易复合数据源在 Spring 容器中的注册、事务边界和底层连接池生命周期。
 * @status : create
 */
class TransactionShardingDataSourceConfigurationTest {

    @Test
    void shouldRegisterNonOwningLegacyAliasAndClosePrimary() {
        HikariDataSource primary = h2DataSource("legacy_primary");
        createPhysicalTables(primary, List.of("transaction_order"));
        new JdbcTemplate(primary).update("""
                INSERT INTO transaction_order_202603(id, transaction_date_time)
                VALUES (?, ?)
                """, 1L, LocalDateTime.of(2026, 8, 2, 1, 0));

        contextRunner(Map.of(DataSourceName.MASTER, primary))
                .withPropertyValues(
                        "spring.application.name=service-payment",
                        "transaction-sharding.mode=LEGACY")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DynamicRoutingDataSource.class);
                    assertThat(context).hasSingleBean(TransactionShardingRuntimeState.class);
                    DynamicRoutingDataSource routing = context.getBean(DynamicRoutingDataSource.class);
                    assertThat(routing.getDataSources()).containsKeys(DataSourceName.MASTER, DataSourceName.TRANSACTION);
                    assertThat(routing.getDataSources().get(DataSourceName.TRANSACTION)).isNotSameAs(primary);
                    assertThat(context.getBean(TransactionShardingRuntimeState.class).isActive()).isFalse();
                    assertThat(context.getBean(TransactionShardingRuntimeState.class).getMode()).isEqualTo("LEGACY");

                    DynamicDataSourceContextHolder.push(DataSourceName.TRANSACTION);
                    try {
                        Integer count = new JdbcTemplate(routing).queryForObject(
                                "SELECT COUNT(*) FROM transaction_order_202603", Integer.class);
                        assertThat(count).isEqualTo(1);
                    } finally {
                        DynamicDataSourceContextHolder.poll();
                    }
                });

        assertThat(primary.isClosed()).isTrue();
    }

    @Test
    void shouldValidateAndReportPublishedRuleWhileLegacyWritesRemainEnabled() {
        HikariDataSource primary = h2DataSource("legacy_published_primary");
        HikariDataSource replica = h2DataSource("legacy_published_replica");
        String ruleVersion = "2026.08.02-legacy";
        String checksum = checksum(ruleVersion, TransactionShardingMode.LEGACY, List.of(DataSourceName.SLAVE_1));
        Map<String, DataSource> resources = new LinkedHashMap<>();
        resources.put(DataSourceName.MASTER, primary);
        resources.put(DataSourceName.SLAVE_1, replica);

        contextRunner(resources)
                .withPropertyValues(
                        "spring.application.name=service-payment",
                        "transaction-sharding.mode=LEGACY",
                        "transaction-sharding.rule-version=" + ruleVersion,
                        "transaction-sharding.rule-checksum=" + checksum,
                        "transaction-sharding.replica-data-sources[0]=" + DataSourceName.SLAVE_1,
                        "transaction-sharding.physical-nodes[0]=202603",
                        "transaction-sharding.physical-nodes[1]=202604")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TransactionShardingRuntimeState state = context.getBean(TransactionShardingRuntimeState.class);
                    assertThat(state.isActive()).isFalse();
                    assertThat(state.getMode()).isEqualTo("LEGACY");
                    assertThat(state.getRuleVersion()).isEqualTo(ruleVersion);
                    assertThat(state.getChecksumPrefix()).isEqualTo(checksum.substring(0, 19));
                });

        assertThat(primary.isClosed()).isTrue();
        assertThat(replica.isClosed()).isTrue();
    }

    @Test
    void shouldRejectPartialPublishedRuleInLegacyMode() {
        HikariDataSource primary = h2DataSource("legacy_invalid_primary");

        contextRunner(Map.of(DataSourceName.MASTER, primary))
                .withPropertyValues(
                        "spring.application.name=service-payment",
                        "transaction-sharding.mode=LEGACY",
                        "transaction-sharding.rule-version=2026.08.02-invalid")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessage("transaction sharding physical nodes must contain verified existing quarters"));

        assertThat(primary.isClosed()).isTrue();
    }

    @Test
    void shouldRegisterCompositeDatasourceRollbackThroughOuterRouterAndTransferPoolOwnership() {
        HikariDataSource primary = h2DataSource("sharding_primary");
        HikariDataSource replica = h2DataSource("sharding_replica");
        createPhysicalTables(primary, TransactionShardingProperties.defaultLogicTables());
        createPhysicalTables(replica, TransactionShardingProperties.defaultLogicTables());
        String ruleVersion = "2026.08.02-poc";
        String checksum = checksum(ruleVersion);
        Map<String, DataSource> resources = new LinkedHashMap<>();
        resources.put(DataSourceName.MASTER, primary);
        resources.put(DataSourceName.SLAVE_1, replica);

        contextRunner(resources)
                .withPropertyValues(
                        "spring.application.name=service-payment",
                        "transaction-sharding.mode=SHARDINGSPHERE",
                        "transaction-sharding.rule-version=" + ruleVersion,
                        "transaction-sharding.rule-checksum=" + checksum,
                        "transaction-sharding.replica-data-sources[0]=" + DataSourceName.SLAVE_1,
                        "transaction-sharding.physical-nodes[0]=202603",
                        "transaction-sharding.physical-nodes[1]=202604")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DynamicRoutingDataSource.class);
                    DynamicRoutingDataSource routing = context.getBean(DynamicRoutingDataSource.class);
                    assertThat(routing.getDataSources()).containsKeys(
                            DataSourceName.MASTER, DataSourceName.SLAVE_1, DataSourceName.TRANSACTION);
                    assertThat(routing.getDataSources().get(DataSourceName.TRANSACTION))
                            .isInstanceOf(ShardingSphereDataSource.class);
                    assertThat(routing.getDataSources().get(DataSourceName.MASTER))
                            .isNotInstanceOf(ItemDataSource.class)
                            .isNotInstanceOf(AutoCloseable.class);
                    assertThat(routing.getDataSources().get(DataSourceName.SLAVE_1))
                            .isNotInstanceOf(ItemDataSource.class)
                            .isNotInstanceOf(AutoCloseable.class);
                    assertThat(context.getBean(TransactionShardingRuntimeState.class).isActive()).isTrue();
                    assertThat(context.getBean(TransactionShardingRuntimeState.class).isShardingWriteEnabled()).isTrue();
                    assertThat(context.getBean(TransactionShardingRuntimeState.class).isReadComparisonEnabled()).isFalse();

                    DynamicDataSourceContextHolder.push(DataSourceName.TRANSACTION);
                    try {
                        TransactionTemplate transaction = new TransactionTemplate(
                                new DataSourceTransactionManager(routing));
                        transaction.executeWithoutResult(status -> {
                            new JdbcTemplate(routing).update("""
                                    INSERT INTO transaction_order(id, transaction_date_time)
                                    VALUES (?, ?)
                                    """, 9L, LocalDateTime.of(2026, 11, 2, 1, 0));
                            status.setRollbackOnly();
                        });
                    } finally {
                        DynamicDataSourceContextHolder.poll();
                    }

                    assertThat(new JdbcTemplate(primary).queryForObject(
                            "SELECT COUNT(*) FROM transaction_order_202604 WHERE id = 9", Integer.class))
                            .isZero();
                });

        assertThat(primary.isClosed()).isTrue();
        assertThat(replica.isClosed()).isTrue();
    }

    @Test
    void shouldRegisterCompositeDatasourceForReadOnlyComparisonWithoutEnablingWrites() {
        HikariDataSource primary = h2DataSource("compare_primary");
        HikariDataSource replica = h2DataSource("compare_replica");
        createPhysicalTables(primary, TransactionShardingProperties.defaultLogicTables());
        createPhysicalTables(replica, TransactionShardingProperties.defaultLogicTables());
        String ruleVersion = "2026.08.02-compare";
        String checksum = checksum(ruleVersion, TransactionShardingMode.COMPARE, List.of(DataSourceName.SLAVE_1));
        Map<String, DataSource> resources = new LinkedHashMap<>();
        resources.put(DataSourceName.MASTER, primary);
        resources.put(DataSourceName.SLAVE_1, replica);

        contextRunner(resources)
                .withPropertyValues(
                        "spring.application.name=service-admin",
                        "transaction-sharding.mode=COMPARE",
                        "transaction-sharding.rule-version=" + ruleVersion,
                        "transaction-sharding.rule-checksum=" + checksum,
                        "transaction-sharding.replica-data-sources[0]=" + DataSourceName.SLAVE_1,
                        "transaction-sharding.physical-nodes[0]=202603",
                        "transaction-sharding.physical-nodes[1]=202604")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TransactionShardingRuntimeState state = context.getBean(TransactionShardingRuntimeState.class);
                    assertThat(state.isActive()).isTrue();
                    assertThat(state.getMode()).isEqualTo("COMPARE");
                    assertThat(state.isReadComparisonEnabled()).isTrue();
                    assertThat(state.isShardingWriteEnabled()).isFalse();
                });

        assertThat(primary.isClosed()).isTrue();
        assertThat(replica.isClosed()).isTrue();
    }

    @Test
    void shouldCloseCompositeDatasourceWhenOuterRegistrationFails() {
        HikariDataSource primary = h2DataSource("registration_failure_primary");
        HikariDataSource replica = h2DataSource("registration_failure_replica");
        createPhysicalTables(primary, TransactionShardingProperties.defaultLogicTables());
        createPhysicalTables(replica, TransactionShardingProperties.defaultLogicTables());
        String ruleVersion = "2026.08.02-registration-failure";
        String checksum = checksum(ruleVersion);
        Map<String, DataSource> resources = new LinkedHashMap<>();
        resources.put(DataSourceName.MASTER, primary);
        resources.put(DataSourceName.SLAVE_1, replica);

        contextRunner(resources, true)
                .withPropertyValues(
                        "spring.application.name=service-payment",
                        "transaction-sharding.mode=SHARDINGSPHERE",
                        "transaction-sharding.rule-version=" + ruleVersion,
                        "transaction-sharding.rule-checksum=" + checksum,
                        "transaction-sharding.replica-data-sources[0]=" + DataSourceName.SLAVE_1,
                        "transaction-sharding.physical-nodes[0]=202603",
                        "transaction-sharding.physical-nodes[1]=202604")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessage("synthetic transaction datasource registration failure"));

        assertThat(primary.isClosed()).isTrue();
        assertThat(replica.isClosed()).isTrue();
    }

    private ApplicationContextRunner contextRunner(Map<String, DataSource> resources) {
        return contextRunner(resources, false);
    }

    private ApplicationContextRunner contextRunner(Map<String, DataSource> resources,
                                                   boolean rejectTransactionRegistration) {
        Map<String, DataSource> dynamicResources = new LinkedHashMap<>();
        resources.forEach((name, dataSource) -> dynamicResources.put(name,
                new ItemDataSource(name, dataSource, dataSource, false, false, SeataMode.AT)));
        DynamicDataSourceProvider provider = () -> dynamicResources;
        return new ApplicationContextRunner()
                .withBean(DynamicRoutingDataSource.class,
                        () -> rejectTransactionRegistration
                                ? new RejectingDynamicRoutingDataSource(provider)
                                : new DynamicRoutingDataSource(List.of(provider)))
                .withUserConfiguration(TransactionShardingDataSourceConfiguration.class);
    }

    private static final class RejectingDynamicRoutingDataSource extends DynamicRoutingDataSource {

        private RejectingDynamicRoutingDataSource(DynamicDataSourceProvider provider) {
            super(List.of(provider));
        }

        /** 模拟 transaction 别名注册失败，用于验证新建复合数据源会释放底层连接池。 */
        @Override
        public synchronized void addDataSource(String name, DataSource dataSource) {
            if (DataSourceName.TRANSACTION.equals(name)) {
                throw new IllegalStateException("synthetic transaction datasource registration failure");
            }
            super.addDataSource(name, dataSource);
        }
    }

    private String checksum(String ruleVersion) {
        return checksum(ruleVersion, TransactionShardingMode.SHARDINGSPHERE, List.of(DataSourceName.SLAVE_1));
    }

    private String checksum(String ruleVersion,
                            TransactionShardingMode mode,
                            List<String> replicaDataSources) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setMode(mode);
        properties.setRuleVersion(ruleVersion);
        properties.setReplicaDataSources(replicaDataSources);
        properties.setPhysicalNodes(List.of("202603", "202604"));
        return TransactionShardingRuleChecksum.calculate(properties);
    }

    private HikariDataSource h2DataSource(String name) {
        HikariDataSource result = new HikariDataSource();
        result.setJdbcUrl("jdbc:h2:mem:" + name + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        result.setUsername("sa");
        result.setPassword("");
        result.setMaximumPoolSize(2);
        result.setMinimumIdle(0);
        return result;
    }

    private void createPhysicalTables(DataSource dataSource, List<String> logicTables) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        for (String logicTable : logicTables) {
            for (String suffix : List.of("202603", "202604")) {
                template.execute("CREATE TABLE " + logicTable + "_" + suffix + " ("
                        + "id BIGINT AUTO_INCREMENT NOT NULL, transaction_date_time TIMESTAMP(3) NOT NULL)");
            }
        }
    }

}
