package com.scott.payment.component.db.sharding;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.baomidou.dynamic.datasource.enums.SeataMode;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.scott.payment.component.db.constant.DataSourceName;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
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
 * 验证 riskAudit 数据源的兼容别名、季度路由和定位表同事务回滚行为。
 */
class RiskAuditShardingDataSourceConfigurationTest {

    /**
     * 验证未发布分片配置时仍注册主库兼容别名，不阻断 service-data 启动。
     */
    @Test
    void shouldRegisterPrimaryCompatibilityAliasWhenDisabled() {
        HikariDataSource primary = dataSource("risk_alias_primary");

        contextRunner(Map.of(DataSourceName.MASTER, primary))
                .withPropertyValues(
                        "spring.application.name=service-data",
                        "transaction-sharding.direct-access-services[0]=service-payment",
                        "risk-audit-sharding.replica-data-sources=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DynamicRoutingDataSource routing = context.getBean(DynamicRoutingDataSource.class);
                    assertThat(routing.getDataSources()).containsKey(DataSourceName.RISK_AUDIT);
                    assertThat(routing.getDataSources().get(DataSourceName.RISK_AUDIT))
                            .isNotInstanceOf(ShardingSphereDataSource.class);
                });

        assertThat(primary.isClosed()).isTrue();
    }

    /**
     * 验证启用分片后通过 dynamic-datasource 注册真实 Hikari 连接池，确保 ShardingSphere
     * 能识别 JDBC URL 等连接属性并完成 riskAudit 复合数据源初始化。
     */
    @Test
    void shouldRegisterEnabledRiskAuditWithDynamicHikariResources() {
        HikariDataSource primary = dataSource("risk_enabled_primary");
        HikariDataSource replica = dataSource("risk_enabled_replica");
        createTransactionTables(primary);
        createTransactionTables(replica);
        createRiskTables(primary);
        createRiskTables(replica);
        String transactionRuleVersion = "2026.11.08-transaction-001";
        String ruleVersion = "2026.11.08-risk-audit-001";

        contextRunner(Map.of(DataSourceName.MASTER, primary, DataSourceName.SLAVE, replica))
                .withPropertyValues(
                        "spring.application.name=service-data",
                        "transaction-sharding.rule-version=" + transactionRuleVersion,
                        "transaction-sharding.rule-checksum=" + transactionChecksum(transactionRuleVersion),
                        "transaction-sharding.replica-data-sources[0]=" + DataSourceName.SLAVE,
                        "transaction-sharding.physical-nodes[0]=202603",
                        "transaction-sharding.physical-nodes[1]=202604",
                        "risk-audit-sharding.enabled=true",
                        "risk-audit-sharding.rule-version=" + ruleVersion,
                        "risk-audit-sharding.rule-checksum=" + checksum(ruleVersion),
                        "risk-audit-sharding.physical-nodes[0]=202603",
                        "risk-audit-sharding.physical-nodes[1]=202604")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DynamicRoutingDataSource routing = context.getBean(DynamicRoutingDataSource.class);
                    assertThat(routing.getDataSources().get(DataSourceName.TRANSACTION))
                            .isInstanceOf(ShardingSphereDataSource.class);
                    assertThat(routing.getDataSources().get(DataSourceName.RISK_AUDIT))
                            .isInstanceOf(ShardingSphereDataSource.class);
                });

        assertThat(primary.isClosed()).isTrue();
        assertThat(replica.isClosed()).isTrue();
    }

    /**
     * 验证定位表、主记录和执行明细在 riskAudit 本地事务中共同回滚。
     */
    @Test
    void shouldRouteBothAuditTablesAndLocatorInOneTransaction() throws Exception {
        HikariDataSource primary = dataSource("risk_sharding_primary");
        HikariDataSource replica = dataSource("risk_sharding_replica");
        createRiskTables(primary);
        createRiskTables(replica);
        String ruleVersion = "2026.11.08-risk-audit-001";
        String checksum = checksum(ruleVersion);
        RiskAuditShardingProperties properties = new RiskAuditShardingProperties();
        properties.setEnabled(true);
        properties.setRuleVersion(ruleVersion);
        properties.setRuleChecksum(checksum);
        properties.setPhysicalNodes(List.of("202603", "202604"));
        properties.validateForActivation();
        TransactionShardingDataSourceConfiguration configuration =
                new TransactionShardingDataSourceConfiguration();

        DataSource shardingDataSource = null;
        try {
            shardingDataSource = ShardingSphereDataSourceFactory.createDataSource(
                    Map.of(DataSourceName.MASTER, primary, DataSourceName.SLAVE, replica),
                    configuration.buildRiskAuditRules(properties, properties.getReplicaDataSources()),
                    configuration.buildRiskAuditSystemProperties(properties));
            TransactionTemplate transaction = new TransactionTemplate(
                    new DataSourceTransactionManager(shardingDataSource));
            DataSource finalShardingDataSource = shardingDataSource;
            transaction.executeWithoutResult(status -> {
                LocalDateTime evaluationTime = LocalDateTime.of(2026, 11, 8, 10, 30);
                JdbcTemplate jdbc = new JdbcTemplate(finalShardingDataSource);
                jdbc.update("INSERT INTO risk_evaluation_locator(risk_record_no, evaluation_time) VALUES (?, ?)",
                        "RK-ROLLBACK", evaluationTime);
                jdbc.update("INSERT INTO risk_evaluation_record(risk_record_no, evaluation_time) VALUES (?, ?)",
                        "RK-ROLLBACK", evaluationTime);
                jdbc.update("INSERT INTO risk_evaluation_hit_detail(risk_record_no, evaluation_time) VALUES (?, ?)",
                        "RK-ROLLBACK", evaluationTime);
                status.setRollbackOnly();
            });
            JdbcTemplate primaryJdbc = new JdbcTemplate(primary);
            assertThat(primaryJdbc.queryForObject(
                    "SELECT COUNT(*) FROM risk_evaluation_locator", Integer.class)).isZero();
            assertThat(primaryJdbc.queryForObject(
                    "SELECT COUNT(*) FROM risk_evaluation_record_202604", Integer.class)).isZero();
            assertThat(primaryJdbc.queryForObject(
                    "SELECT COUNT(*) FROM risk_evaluation_hit_detail_202604", Integer.class)).isZero();
        } finally {
            if (shardingDataSource instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }

        assertThat(primary.isClosed()).isTrue();
        assertThat(replica.isClosed()).isTrue();
    }

    /**
     * 创建仅包含本测试所需 Bean 的动态数据源容器。
     */
    private ApplicationContextRunner contextRunner(Map<String, DataSource> resources) {
        Map<String, DataSource> dynamicResources = new LinkedHashMap<>();
        resources.forEach((name, dataSource) -> dynamicResources.put(name,
                new ItemDataSource(name, dataSource, dataSource, false, false, SeataMode.AT)));
        DynamicDataSourceProvider provider = () -> dynamicResources;
        return new ApplicationContextRunner()
                .withBean(DynamicRoutingDataSource.class,
                        () -> new DynamicRoutingDataSource(List.of(provider)))
                .withUserConfiguration(TransactionShardingDataSourceConfiguration.class);
    }

    /**
     * 计算测试发布规则的 checksum。
     */
    private String checksum(String ruleVersion) {
        RiskAuditShardingProperties properties = new RiskAuditShardingProperties();
        properties.setEnabled(true);
        properties.setRuleVersion(ruleVersion);
        properties.setPhysicalNodes(List.of("202603", "202604"));
        return RiskAuditShardingRuleChecksum.calculate(properties);
    }

    /**
     * 计算与正式完整交易拓扑一致的发布规则 checksum。
     */
    private String transactionChecksum(String ruleVersion) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setRuleVersion(ruleVersion);
        properties.setReplicaDataSources(List.of(DataSourceName.SLAVE));
        properties.setPhysicalNodes(List.of("202603", "202604"));
        return TransactionShardingRuleChecksum.calculate(properties);
    }

    /**
     * 创建隔离的 MySQL 兼容 H2 数据源。
     */
    private HikariDataSource dataSource(String name) {
        HikariDataSource result = new HikariDataSource();
        result.setJdbcUrl("jdbc:h2:mem:" + name + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        result.setUsername("sa");
        result.setPassword("");
        result.setMaximumPoolSize(2);
        result.setMinimumIdle(0);
        return result;
    }

    /**
     * 创建两个季度的风控审计物理表和非分片定位表。
     */
    private void createRiskTables(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String suffix : List.of("202603", "202604")) {
            jdbc.execute("CREATE TABLE risk_evaluation_record_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, risk_record_no VARCHAR(64) NOT NULL, "
                    + "evaluation_time TIMESTAMP(3) NOT NULL)");
            jdbc.execute("CREATE TABLE risk_evaluation_hit_detail_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, risk_record_no VARCHAR(64) NOT NULL, "
                    + "evaluation_time TIMESTAMP(3) NOT NULL)");
        }
        jdbc.execute("CREATE TABLE risk_evaluation_locator ("
                + "id BIGINT AUTO_INCREMENT NOT NULL, risk_record_no VARCHAR(64) NOT NULL, "
                + "evaluation_time TIMESTAMP(3) NOT NULL, UNIQUE(risk_record_no))");
    }

    /**
     * 创建交易复合数据源初始化所需的季度物理表和定位表。
     */
    private void createTransactionTables(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String logicTable : TransactionShardingProperties.defaultLogicTables()) {
            for (String suffix : List.of("202603", "202604")) {
                jdbc.execute("CREATE TABLE " + logicTable + "_" + suffix + " ("
                        + "id BIGINT AUTO_INCREMENT NOT NULL, transaction_date_time TIMESTAMP(3) NOT NULL)");
            }
        }
        jdbc.execute("CREATE TABLE transaction_locator ("
                + "id BIGINT AUTO_INCREMENT NOT NULL, transaction_id VARCHAR(64) NOT NULL)");
    }
}
