package com.scott.payment.component.db.sharding;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.scott.payment.component.db.constant.DataSourceName;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.algorithm.core.config.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.infra.datasource.pool.props.creator.DataSourcePoolPropertiesCreator;
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
 * 将底层主从连接池组装为 transaction 与 riskAudit 复合数据源，并注册到 dynamic-datasource 外层路由。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({TransactionShardingProperties.class, RiskAuditShardingProperties.class})
@Slf4j
public class TransactionShardingDataSourceConfiguration {

    /**
     * ShardingSphere 读写分离组名，逻辑表节点仅引用该组而不直接绑定连接池。
     */
    private static final String READWRITE_GROUP = "transaction_rw";
    /**
     * 正式交易逻辑表共享的季度分片算法注册名。
     */
    private static final String ALGORITHM_NAME = "transaction_quarter";
    /**
     * 只读副本轮询负载均衡器注册名。
     */
    private static final String LOAD_BALANCER_NAME = "transaction_round_robin";

    /**
     * 风控审计读写分离组名。
     */
    private static final String RISK_AUDIT_READWRITE_GROUP = "risk_audit_rw";

    /**
     * 风控审计季度路由算法注册名。
     */
    private static final String RISK_AUDIT_ALGORITHM_NAME = "risk_audit_quarter";

    /**
     * 风控审计只读副本轮询负载均衡器注册名。
     */
    private static final String RISK_AUDIT_LOAD_BALANCER_NAME = "risk_audit_round_robin";

    /**
     * 在所有单例初始化后注册 transaction 逻辑数据源，避免业务 Bean 提前取得半成品路由。
     *
     * @param routingDataSourceProvider dynamic-datasource 外层路由
     * @param properties          已绑定的交易版本化规则
     * @param riskAuditProperties 已绑定的风控审计版本化规则
     * @param environment 当前服务身份
     * @return 单例初始化回调
     */
    @Bean
    public SmartInitializingSingleton transactionShardingDataSourceRegistrar(
            ObjectProvider<DynamicRoutingDataSource> routingDataSourceProvider,
            TransactionShardingProperties properties,
            RiskAuditShardingProperties riskAuditProperties,
            Environment environment) {
        return () -> {
            DynamicRoutingDataSource routingDataSource = routingDataSourceProvider.getIfAvailable();
            register(routingDataSource, properties, environment);
            registerRiskAudit(routingDataSource, riskAuditProperties, environment);
        };
    }

    /**
     * 按服务身份注册 ShardingSphere 复合数据源；任一规则不完整时在启动阶段失败。
     */
    private void register(DynamicRoutingDataSource routingDataSource,
                          TransactionShardingProperties properties,
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
                    resources, buildRules(properties, replicas), buildSystemProperties(properties));
            registerCompositeDataSource(routingDataSource, routingResources, transactionDataSource);
            log.info("交易 ShardingSphere 数据源注册完成, event: TRANSACTION_SHARDING_DATASOURCE_REGISTERED, service: {}, ruleVersion: {}, checksumPrefix: {}",
                    applicationName, properties.getRuleVersion(), checksumPrefix(properties.getRuleChecksum()));
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to build transaction sharding datasource for rule "
                    + properties.getRuleVersion(), exception);
        }
    }

    /**
     * 按服务白名单注册风控审计逻辑数据源；配置关闭时注册主库兼容别名，
     * 配置启用时才装载两张季度分表和非分片定位表。
     *
     * @param routingDataSource 外层动态数据源
     * @param properties        风控审计分片规则
     * @param environment       当前服务环境
     */
    private void registerRiskAudit(DynamicRoutingDataSource routingDataSource,
                                   RiskAuditShardingProperties properties,
                                   Environment environment) {
        String applicationName = environment.getProperty("spring.application.name", "");
        if (!properties.getDirectAccessServices().contains(applicationName)) {
            return;
        }
        if (routingDataSource == null) {
            throw new IllegalStateException("dynamic datasource is required by risk audit service " + applicationName);
        }
        if (routingDataSource.getDataSources().containsKey(DataSourceName.RISK_AUDIT)) {
            throw new IllegalStateException("risk audit datasource is already registered");
        }
        DataSource primary = requiredDataSource(routingDataSource, properties.getPrimaryDataSource());
        if (!properties.isEnabled()) {
            routingDataSource.addDataSource(DataSourceName.RISK_AUDIT, new NonClosingDelegatingDataSource(primary));
            log.info("风控审计分片未启用，使用主库兼容别名, event: RISK_AUDIT_SHARDING_DISABLED, service: {}",
                    applicationName);
            return;
        }
        properties.validateForActivation();
        Map<String, DataSource> resources = new LinkedHashMap<>();
        resources.put(properties.getPrimaryDataSource(),
                new NonClosingDelegatingDataSource(shardingResource(primary, properties.getPrimaryDataSource())));
        List<String> replicas = new ArrayList<>();
        for (String replicaName : properties.getReplicaDataSources()) {
            DataSource replica = requiredDataSource(routingDataSource, replicaName);
            resources.put(replicaName,
                    new NonClosingDelegatingDataSource(shardingResource(replica, replicaName)));
            replicas.add(replicaName);
        }
        try {
            DataSource riskAuditDataSource = ShardingSphereDataSourceFactory.createDataSource(
                    resources,
                    buildRiskAuditRules(properties, replicas),
                    buildRiskAuditSystemProperties(properties));
            registerAdditionalCompositeDataSource(
                    routingDataSource, DataSourceName.RISK_AUDIT, riskAuditDataSource);
            log.info("风控审计 ShardingSphere 数据源注册完成, event: RISK_AUDIT_SHARDING_DATASOURCE_REGISTERED, service: {}, ruleVersion: {}, checksumPrefix: {}",
                    applicationName, properties.getRuleVersion(), checksumPrefix(properties.getRuleChecksum()));
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to build risk audit sharding datasource for rule "
                    + properties.getRuleVersion(), exception);
        }
    }

    /**
     * 返回可公开记录的规则 checksum 前缀。
     */
    private String checksumPrefix(String checksum) {
        return checksum.substring(0, Math.min(checksum.length(), 19));
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

    /**
     * 注册不接管底层连接池生命周期的附加复合数据源，注册失败时只关闭新建的 ShardingSphere 外壳。
     *
     * @param routingDataSource 外层动态数据源
     * @param dataSourceName    附加数据源名称
     * @param compositeDataSource 新建复合数据源
     */
    private void registerAdditionalCompositeDataSource(DynamicRoutingDataSource routingDataSource,
                                                       String dataSourceName,
                                                       DataSource compositeDataSource) {
        try {
            routingDataSource.addDataSource(dataSourceName, compositeDataSource);
        } catch (RuntimeException exception) {
            routingDataSource.getDataSources().remove(dataSourceName, compositeDataSource);
            closeAfterRegistrationFailure(compositeDataSource, exception);
            throw exception;
        }
    }

    /**
     * 关闭注册失败后无人持有的复合数据源，并保留关闭异常作为 suppressed 诊断信息。
     */
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
        if (dataSource instanceof NonClosingDelegatingDataSource nonClosingDataSource) {
            return shardingResource(nonClosingDataSource.delegate, name);
        }
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

    /**
     * 根据已验证配置构建分表、单表和可选读写分离规则。
     *
     * @param properties 已通过激活校验的交易分片配置
     * @param replicas 已注册副本数据源名称
     * @return 交给 ShardingSphere 的规则集合
     */
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

    /**
     * 构建风控审计主记录、执行明细和定位表的独立 ShardingSphere 规则。
     *
     * @param properties 已通过激活校验的风控审计规则
     * @param replicas   已注册的只读副本名称
     * @return 风控审计分表、单表和可选读写分离规则
     */
    Collection<RuleConfiguration> buildRiskAuditRules(
            RiskAuditShardingProperties properties,
            List<String> replicas) {
        ShardingRuleConfiguration shardingRule = new ShardingRuleConfiguration();
        shardingRule.setTables(properties.getLogicTables().stream()
                .map(logicTable -> riskAuditTableRule(properties, logicTable))
                .toList());
        shardingRule.setBindingTableGroups(List.of(new ShardingTableReferenceRuleConfiguration(
                "risk_audit_table_family", String.join(",", properties.getLogicTables()))));
        Properties algorithmProperties = new Properties();
        algorithmProperties.setProperty("strategy", "STANDARD");
        algorithmProperties.setProperty("algorithmClassName", QuarterTableShardingAlgorithm.class.getName());
        algorithmProperties.setProperty("database-zone-id", properties.getDatabaseZoneId());
        algorithmProperties.setProperty("rule-version", properties.getRuleVersion());
        shardingRule.setShardingAlgorithms(Map.of(
                RISK_AUDIT_ALGORITHM_NAME,
                new AlgorithmConfiguration("CLASS_BASED", algorithmProperties)));

        String defaultDataSource = replicas.isEmpty()
                ? properties.getPrimaryDataSource() : RISK_AUDIT_READWRITE_GROUP;
        SingleRuleConfiguration singleRule = new SingleRuleConfiguration(
                List.of(defaultDataSource + "." + RiskAuditShardingProperties.EVALUATION_LOCATOR_TABLE),
                defaultDataSource);
        if (replicas.isEmpty()) {
            return List.of(shardingRule, singleRule);
        }
        ReadwriteSplittingDataSourceGroupRuleConfiguration group =
                new ReadwriteSplittingDataSourceGroupRuleConfiguration(
                        RISK_AUDIT_READWRITE_GROUP,
                        properties.getPrimaryDataSource(),
                        replicas,
                        RISK_AUDIT_LOAD_BALANCER_NAME);
        ReadwriteSplittingRuleConfiguration readwriteRule = new ReadwriteSplittingRuleConfiguration(
                List.of(group),
                Map.of(RISK_AUDIT_LOAD_BALANCER_NAME,
                        new AlgorithmConfiguration("ROUND_ROBIN", new Properties())));
        return List.of(shardingRule, readwriteRule, singleRule);
    }

    /**
     * 为单张逻辑表生成只包含已建且已校验季度的 actualDataNodes。
     */
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

    /**
     * 为一张风控审计逻辑表生成只包含已迁移并验收季度的实际节点规则。
     *
     * @param properties 风控审计分片配置
     * @param logicTable 风控审计逻辑表
     * @return ShardingSphere 表规则
     */
    private ShardingTableRuleConfiguration riskAuditTableRule(
            RiskAuditShardingProperties properties,
            String logicTable) {
        String dataSourceName = properties.getReplicaDataSources().isEmpty()
                ? properties.getPrimaryDataSource() : RISK_AUDIT_READWRITE_GROUP;
        String actualDataNodes = properties.getPhysicalNodes().stream()
                .map(suffix -> dataSourceName + "." + logicTable + "_" + suffix)
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
        ShardingTableRuleConfiguration result =
                new ShardingTableRuleConfiguration(logicTable, actualDataNodes);
        result.setTableShardingStrategy(new StandardShardingStrategyConfiguration(
                properties.getShardingColumn(), RISK_AUDIT_ALGORITHM_NAME));
        return result;
    }

    /**
     * 构造 ShardingSphere 运行属性。SQL 输出由环境配置显式控制，代码默认保持关闭。
     *
     * @param properties 当前交易分片配置
     * @return 传递给 ShardingSphere 数据源工厂的系统属性
     */
    Properties buildSystemProperties(TransactionShardingProperties properties) {
        Properties result = new Properties();
        result.setProperty("sql-show", Boolean.toString(properties.isSqlShow()));
        result.setProperty("check-table-metadata-enabled", "true");
        return result;
    }

    /**
     * 构造风控审计 ShardingSphere 运行属性，默认关闭实际 SQL 输出。
     *
     * @param properties 风控审计运行配置
     * @return ShardingSphere 系统属性
     */
    Properties buildRiskAuditSystemProperties(RiskAuditShardingProperties properties) {
        Properties result = new Properties();
        result.setProperty("sql-show", Boolean.toString(properties.isSqlShow()));
        result.setProperty("check-table-metadata-enabled", "true");
        return result;
    }

    /**
     * 从外层路由取得指定连接池，缺失时拒绝服务启动。
     */
    private DataSource requiredDataSource(DynamicRoutingDataSource routingDataSource, String name) {
        DataSource result = routingDataSource.getDataSources().get(name);
        if (result == null) {
            throw new IllegalStateException("required datasource is not registered: " + name);
        }
        return result;
    }

    /**
     * dynamic-datasource 保留的代理不拥有底层连接池生命周期，避免与 ShardingSphere 重复关闭。
     */
    public static final class NonClosingDelegatingDataSource implements DataSource {
        /**
         * 仍由原 owner 或 ShardingSphere 管理生命周期的真实数据源。
         */
        private final DataSource delegate;

        /**
         * ShardingSphere 识别存储单元所需的 JDBC URL；仅用于元数据解析，不改变连接获取路径。
         */
        private final String url;

        /**
         * ShardingSphere 解析数据库目录时使用的连接用户名，不包含密码等安全配置。
         */
        private final String username;

        /**
         * 创建只转发 JDBC 操作、不接管 close 生命周期的代理。
         */
        private NonClosingDelegatingDataSource(DataSource delegate) {
            this.delegate = delegate;
            Map<String, Object> connectionProperties = DataSourcePoolPropertiesCreator.create(delegate)
                    .getConnectionPropertySynonyms()
                    .getStandardProperties();
            Object configuredUrl = connectionProperties.get("url");
            Object configuredUsername = connectionProperties.get("username");
            this.url = configuredUrl == null ? null : configuredUrl.toString();
            this.username = configuredUsername == null ? null : configuredUsername.toString();
        }

        /**
         * 返回 ShardingSphere 构建存储单元所需的 JDBC URL。
         *
         * @return JDBC URL；外层路由包装器无法提供时返回 {@code null}
         */
        public String getUrl() {
            return url;
        }

        /**
         * 返回 ShardingSphere 解析连接属性所需的用户名。
         *
         * @return 数据库用户名；未配置时返回 {@code null}
         */
        public String getUsername() {
            return username;
        }

        /**
         * 从真实数据源获取默认连接。
         *
         * @return 真实数据源创建的连接
         */
        @Override
        public Connection getConnection() throws SQLException {
            return delegate.getConnection();
        }

        /**
         * 使用显式凭证从真实数据源获取连接，凭证不会被记录。
         *
         * @param username 数据库用户名
         * @param password 数据库密码
         * @return 使用显式凭证创建的连接
         */
        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return delegate.getConnection(username, password);
        }

        /**
         * 获取真实数据源的 JDBC 日志输出器。
         *
         * @return 真实数据源日志输出器
         */
        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        /**
         * 将 JDBC 日志输出器设置转发给真实数据源。
         */
        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        /**
         * 将登录超时设置转发给真实数据源。
         */
        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        /**
         * 获取真实数据源登录超时秒数。
         *
         * @return 登录超时秒数
         */
        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        /**
         * 获取真实数据源的父日志器。
         *
         * @return 父日志器
         */
        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        /**
         * 将当前代理或真实数据源解包为调用方请求的接口。
         *
         * @param iface 目标接口类型
         * @param <T> 目标接口类型参数
         * @return 当前代理或真实数据源提供的接口实例
         */
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return iface.isInstance(this) ? iface.cast(this) : delegate.unwrap(iface);
        }

        /**
         * 判断当前代理或真实数据源是否支持指定接口。
         *
         * @param iface 待检查接口类型
         * @return 支持指定接口时返回 true
         */
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || delegate.isWrapperFor(iface);
        }
    }
}
