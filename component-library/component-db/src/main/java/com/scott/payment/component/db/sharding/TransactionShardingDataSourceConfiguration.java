package com.scott.payment.component.db.sharding;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.scott.payment.component.db.constant.DataSourceName;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.algorithm.core.config.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.config.ReadwriteSplittingRuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.config.rule.ReadwriteSplittingDataSourceGroupRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableReferenceRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.apache.shardingsphere.single.config.SingleRuleConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingDataSourceConfiguration
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 将底层主从连接池组装为单一 transaction 复合数据源，并注册到 dynamic-datasource 外层路由。
 * @status : create
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TransactionShardingProperties.class)
@Slf4j
public class TransactionShardingDataSourceConfiguration {

    /** ShardingSphere 读写分离组名，逻辑表节点仅引用该组而不直接绑定连接池。 */
    private static final String READWRITE_GROUP = "transaction_rw";
    /** 23 张逻辑表共享的季度分片算法注册名。 */
    private static final String ALGORITHM_NAME = "transaction_quarter";
    /** 只读副本轮询负载均衡器注册名。 */
    private static final String LOAD_BALANCER_NAME = "transaction_round_robin";

    /**
     * 创建数据源实际装载状态，供健康检查报告配置与运行结果是否一致。
     *
     * @return 初始为 Legacy 未激活状态的运行时对象
     */
    @Bean
    public TransactionShardingRuntimeState transactionShardingRuntimeState() {
        return new TransactionShardingRuntimeState();
    }

    /**
     * 在所有单例初始化后注册 transaction 逻辑数据源，避免业务 Bean 提前取得半成品路由。
     *
     * @param routingDataSourceProvider dynamic-datasource 外层路由
     * @param properties 已绑定的版本化规则
     * @param runtimeState 实际装载状态
     * @param environment 当前服务身份
     * @return 单例初始化回调
     */
    @Bean
    public SmartInitializingSingleton transactionShardingDataSourceRegistrar(
            ObjectProvider<DynamicRoutingDataSource> routingDataSourceProvider,
            TransactionShardingProperties properties,
            TransactionShardingRuntimeState runtimeState,
            Environment environment) {
        return () -> register(routingDataSourceProvider.getIfAvailable(), properties, runtimeState, environment);
    }

    /**
     * 按服务身份和切换模式注册别名或复合数据源；任一规则不完整时在启动阶段失败。
     */
    private void register(DynamicRoutingDataSource routingDataSource,
                          TransactionShardingProperties properties,
                          TransactionShardingRuntimeState runtimeState,
                          Environment environment) {
        String applicationName = environment.getProperty("spring.application.name", "");
        if (!properties.getDirectAccessServices().contains(applicationName)) {
            return;
        }
        if (routingDataSource == null) {
            throw new IllegalStateException("dynamic datasource is required by transaction service " + applicationName);
        }
        if (routingDataSource.getDataSources().containsKey(DataSourceName.TRANSACTION)) {
            throw new IllegalStateException("transaction datasource is already registered");
        }
        DataSource primary = requiredDataSource(routingDataSource, properties.getPrimaryDataSource());
        properties.validateForActivation();
        if (!properties.getMode().isCompositeDataSourceRequired()) {
            if (properties.hasPublishedRuleConfiguration()) {
                properties.getReplicaDataSources().forEach(name -> requiredDataSource(routingDataSource, name));
            }
            routingDataSource.addDataSource(DataSourceName.TRANSACTION, new NonClosingDelegatingDataSource(primary));
            runtimeState.loadLegacy(properties);
            log.info("Transaction datasource registered in LEGACY mode for service {}, ruleVersion={}, checksumPrefix={}",
                    applicationName, runtimeState.getRuleVersion(), runtimeState.getChecksumPrefix());
            return;
        }
        Map<String, DataSource> resources = new LinkedHashMap<>();
        Map<String, DataSource> routingResources = new LinkedHashMap<>();
        routingResources.put(properties.getPrimaryDataSource(), primary);
        resources.put(properties.getPrimaryDataSource(), shardingResource(primary, properties.getPrimaryDataSource()));
        List<String> replicas = new ArrayList<>();
        for (String replicaName : properties.getReplicaDataSources()) {
            DataSource replica = requiredDataSource(routingDataSource, replicaName);
            routingResources.put(replicaName, replica);
            resources.put(replicaName, shardingResource(replica, replicaName));
            replicas.add(replicaName);
        }
        try {
            DataSource transactionDataSource = ShardingSphereDataSourceFactory.createDataSource(
                    resources, buildRules(properties, replicas), buildSystemProperties());
            registerCompositeDataSource(routingDataSource, routingResources, transactionDataSource);
            runtimeState.activate(properties);
            log.info("Transaction datasource registered in {} mode, ruleVersion={}, checksumPrefix={}",
                    runtimeState.getMode(), runtimeState.getRuleVersion(), runtimeState.getChecksumPrefix());
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to build transaction sharding datasource for rule "
                    + properties.getRuleVersion(), exception);
        }
    }

    /**
     * 转移底层连接池所有权后原子注册复合数据源；注册失败时关闭新建的 ShardingSphere 资源。
     */
    private void registerCompositeDataSource(DynamicRoutingDataSource routingDataSource,
                                             Map<String, DataSource> routingResources,
                                             DataSource transactionDataSource) {
        try {
            transferResourceOwnership(routingDataSource, routingResources);
            routingDataSource.addDataSource(DataSourceName.TRANSACTION, transactionDataSource);
        } catch (RuntimeException exception) {
            routingDataSource.getDataSources().remove(DataSourceName.TRANSACTION, transactionDataSource);
            closeAfterRegistrationFailure(transactionDataSource, exception);
            throw exception;
        }
    }

    /** 关闭注册失败后无人持有的复合数据源，并保留关闭异常作为 suppressed 诊断信息。 */
    private void closeAfterRegistrationFailure(DataSource transactionDataSource, RuntimeException registrationFailure) {
        if (!(transactionDataSource instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception closeFailure) {
            registrationFailure.addSuppressed(closeFailure);
        }
    }

    /**
     * 解包 dynamic-datasource 包装器交由 ShardingSphere 管理，拒绝重复代理或分布式事务包装。
     */
    private DataSource shardingResource(DataSource dataSource, String name) {
        if (!(dataSource instanceof ItemDataSource itemDataSource)) {
            return dataSource;
        }
        if (Boolean.TRUE.equals(itemDataSource.getP6spy()) || Boolean.TRUE.equals(itemDataSource.getSeata())) {
            throw new IllegalStateException("transaction datasource " + name
                    + " cannot enable p6spy or Seata below ShardingSphere");
        }
        DataSource result = itemDataSource.getRealDataSource();
        if (result == null) {
            throw new IllegalStateException("transaction datasource " + name + " has no underlying connection pool");
        }
        return result;
    }

    /**
     * 将 dynamic-datasource 中原连接池替换为非关闭代理，使底层资源只由 ShardingSphere 关闭一次。
     */
    private void transferResourceOwnership(DynamicRoutingDataSource routingDataSource,
                                           Map<String, DataSource> routingResources) {
        Map<String, DataSource> registered = routingDataSource.getDataSources();
        routingResources.forEach((name, dataSource) -> {
            if (registered.get(name) != dataSource) {
                throw new IllegalStateException("transaction datasource changed during ShardingSphere registration: " + name);
            }
        });
        routingResources.forEach((name, dataSource) ->
                registered.put(name, new NonClosingDelegatingDataSource(dataSource)));
    }

    Collection<RuleConfiguration> buildRules(TransactionShardingProperties properties, List<String> replicas) {
        ShardingRuleConfiguration shardingRule = new ShardingRuleConfiguration();
        List<ShardingTableRuleConfiguration> tableRules = properties.getLogicTables().stream()
                .map(logicTable -> tableRule(properties, logicTable))
                .toList();
        shardingRule.setTables(tableRules);
        shardingRule.setBindingTableGroups(List.of(new ShardingTableReferenceRuleConfiguration(
                "transaction_table_family", String.join(",", properties.getLogicTables()))));
        Properties algorithmProperties = new Properties();
        algorithmProperties.setProperty("strategy", "STANDARD");
        algorithmProperties.setProperty("algorithmClassName", QuarterTableShardingAlgorithm.class.getName());
        algorithmProperties.setProperty("database-zone-id", properties.getDatabaseZoneId());
        algorithmProperties.setProperty("rule-version", properties.getRuleVersion());
        shardingRule.setShardingAlgorithms(Map.of(
                ALGORITHM_NAME, new AlgorithmConfiguration("CLASS_BASED", algorithmProperties)));
        String defaultDataSource = replicas.isEmpty() ? properties.getPrimaryDataSource() : READWRITE_GROUP;
        SingleRuleConfiguration singleRule = new SingleRuleConfiguration(
                List.of(defaultDataSource + ".*"), defaultDataSource);

        if (replicas.isEmpty()) {
            return List.of(shardingRule, singleRule);
        }
        ReadwriteSplittingDataSourceGroupRuleConfiguration group =
                new ReadwriteSplittingDataSourceGroupRuleConfiguration(
                        READWRITE_GROUP, properties.getPrimaryDataSource(), replicas, LOAD_BALANCER_NAME);
        ReadwriteSplittingRuleConfiguration readwriteRule = new ReadwriteSplittingRuleConfiguration(
                List.of(group), Map.of(LOAD_BALANCER_NAME, new AlgorithmConfiguration("ROUND_ROBIN", new Properties())));
        return List.of(shardingRule, readwriteRule, singleRule);
    }

    /** 为单张逻辑表生成只包含已建且已校验季度的 actualDataNodes。 */
    private ShardingTableRuleConfiguration tableRule(TransactionShardingProperties properties, String logicTable) {
        String dataSourceName = properties.getReplicaDataSources().isEmpty()
                ? properties.getPrimaryDataSource() : READWRITE_GROUP;
        String actualDataNodes = properties.getPhysicalNodes().stream()
                .map(suffix -> dataSourceName + "." + logicTable + "_" + suffix)
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
        ShardingTableRuleConfiguration result = new ShardingTableRuleConfiguration(logicTable, actualDataNodes);
        result.setTableShardingStrategy(new StandardShardingStrategyConfiguration(
                properties.getShardingColumn(), ALGORITHM_NAME));
        return result;
    }

    Properties buildSystemProperties() {
        Properties result = new Properties();
        result.setProperty("sql-show", "false");
        result.setProperty("check-table-metadata-enabled", "true");
        return result;
    }

    /** 从外层路由取得指定连接池，缺失时拒绝服务启动。 */
    private DataSource requiredDataSource(DynamicRoutingDataSource routingDataSource, String name) {
        DataSource result = routingDataSource.getDataSources().get(name);
        if (result == null) {
            throw new IllegalStateException("required datasource is not registered: " + name);
        }
        return result;
    }

    /**
     * Legacy 模式别名不拥有底层连接池生命周期，避免 dynamic-datasource 关闭同一个主库两次。
     */
    private static final class NonClosingDelegatingDataSource implements DataSource {
        /** 仍由原 owner 或 ShardingSphere 管理生命周期的真实数据源。 */
        private final DataSource delegate;

        /** 创建只转发 JDBC 操作、不接管 close 生命周期的代理。 */
        private NonClosingDelegatingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        /** @return 真实数据源创建的连接 */
        @Override
        public Connection getConnection() throws SQLException {
            return delegate.getConnection();
        }

        /** @return 使用显式凭证创建的连接；凭证不会被记录 */
        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return delegate.getConnection(username, password);
        }

        /** @return 真实数据源日志输出器 */
        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        /** 将 JDBC 日志输出器设置转发给真实数据源。 */
        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        /** 将登录超时设置转发给真实数据源。 */
        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        /** @return 真实数据源登录超时秒数 */
        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        /** @return 真实数据源的父日志器 */
        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return iface.isInstance(this) ? iface.cast(this) : delegate.unwrap(iface);
        }

        /** @return 当前代理或真实数据源是否支持指定接口 */
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || delegate.isWrapperFor(iface);
        }
    }
}
