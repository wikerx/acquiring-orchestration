package com.scott.payment.component.db.sharding;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationMapper;
import com.scott.payment.payment.mapper.TransactionOperationMapper;
import com.scott.payment.payment.mapper.TransactionOrderMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionLogicalMapperPocTest
 * @date : 2026-08-02 02:15
 * @email : scott_x@163.com
 * @description : 通过真实 Payment Mapper 验证三表逻辑 SQL 的季度路由、自增回填、Binding 和单分片锁。
 * @status : create
 */
public class PaymentTransactionLogicalMapperPocTest {

    /** 同时承载两个季度物理表的隔离主库连接池。 */
    private HikariDataSource primary;
    /** 使用生产 Mapper 执行逻辑 SQL 的 ShardingSphere 数据源。 */
    private DataSource shardingDataSource;
    /** 绕过逻辑路由核对自增回填和季度落表结果的测试入口。 */
    private JdbcTemplate direct;

    @BeforeEach
    void setUp() throws Exception {
        primary = new HikariDataSource();
        primary.setJdbcUrl("jdbc:h2:mem:payment_mapper_poc_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        primary.setUsername("sa");
        primary.setPassword("");
        primary.setMaximumPoolSize(2);
        primary.setMinimumIdle(0);
        direct = new JdbcTemplate(primary);
        direct.execute("CREATE ALIAS IF NOT EXISTS JSON_SET FOR \""
                + PaymentTransactionLogicalMapperPocTest.class.getName()
                + ".jsonSet\"");
        createPhysicalTables();

        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPrimaryDataSource("master");
        properties.setReplicaDataSources(List.of());
        properties.setPhysicalNodes(List.of("202603", "202604"));
        properties.setLogicTables(List.of(
                "transaction_order",
                "transaction_operation",
                "transaction_merchant_notification"));
        TransactionShardingDataSourceConfiguration configuration = new TransactionShardingDataSourceConfiguration();
        shardingDataSource = ShardingSphereDataSourceFactory.createDataSource(
                Map.of("master", primary),
                configuration.buildRules(properties, List.of()),
                configuration.buildSystemProperties());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (shardingDataSource instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    @Test
    void shouldUseProductionMappersForExactRoutingBindingLockAndGeneratedIds() {
        LocalDateTime q3Time = LocalDateTime.of(2026, 9, 30, 23, 59, 59, 999_000_000);
        LocalDateTime q4Time = LocalDateTime.of(2026, 10, 1, 0, 0, 0, 1_000_000);
        TransactionOrderDO q3Order = order("poc-operation-q3", q3Time);
        TransactionOrderDO q4Order = order("poc-operation-q4", q4Time);
        TransactionOperationDO operation = operation("poc-operation-q3", "poc-transaction-q3", q3Time);
        TransactionMerchantNotificationDO notification = notification("poc-notify-q3", "poc-transaction-q3", q3Time);

        try (SqlSession session = sqlSessionFactory().openSession(false)) {
            TransactionOrderMapper orderMapper = session.getMapper(TransactionOrderMapper.class);
            TransactionOperationMapper operationMapper = session.getMapper(TransactionOperationMapper.class);
            TransactionMerchantNotificationMapper notificationMapper =
                    session.getMapper(TransactionMerchantNotificationMapper.class);

            assertThat(orderMapper.insert(q3Order)).isEqualTo(1);
            assertThat(orderMapper.insert(q4Order)).isEqualTo(1);
            assertThat(operationMapper.insert(operation)).isEqualTo(1);
            assertThat(notificationMapper.insert(notification)).isEqualTo(1);
            assertThat(orderMapper.selectByOperationId("poc-operation-q3", q3Time)).isNotNull();
            assertThat(orderMapper.selectByOperationIdForUpdate("poc-operation-q3", q3Time)).isNotNull();
            assertThat(operationMapper.selectByTransactionId("poc-transaction-q3", q3Time)).isNotNull();
            assertThat(notificationMapper.activateByTransactionId(
                    "poc-transaction-q3", q3Time, 0, "{}", "{}", q3Time.plusMinutes(1), q3Time)).isEqualTo(1);
            session.commit();
        }

        assertThat(q3Order.getId()).isNotNull();
        assertThat(q4Order.getId()).isNotNull();
        assertThat(operation.getId()).isNotNull();
        assertThat(notification.getId()).isNotNull();
        assertThat(count("transaction_order_202603")).isEqualTo(1);
        assertThat(count("transaction_order_202604")).isEqualTo(1);
        assertThat(count("transaction_operation_202603")).isEqualTo(1);
        assertThat(count("transaction_merchant_notification_202603")).isEqualTo(1);
        assertThat(new JdbcTemplate(shardingDataSource).queryForObject("""
                SELECT COUNT(*)
                FROM transaction_order o
                JOIN transaction_operation p
                  ON p.operation_id = o.operation_id
                 AND p.transaction_date_time = o.transaction_date_time
                WHERE o.transaction_date_time = ?
                """, Integer.class, q3Time)).isEqualTo(1);
        assertThat(direct.queryForObject("""
                SELECT payload_json_masked
                FROM transaction_merchant_notification_202603
                WHERE notify_id = 'poc-notify-q3'
                """, String.class)).isEqualTo("{}");
    }

    private SqlSessionFactory sqlSessionFactory() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment(
                "payment-transaction-logical-mapper-poc",
                new JdbcTransactionFactory(),
                shardingDataSource));
        configuration.addMapper(TransactionOrderMapper.class);
        configuration.addMapper(TransactionOperationMapper.class);
        configuration.addMapper(TransactionMerchantNotificationMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private TransactionOrderDO order(String operationId, LocalDateTime transactionDateTime) {
        TransactionOrderDO result = new TransactionOrderDO();
        result.setOperationId(operationId);
        result.setTransactionStatus("PROCESSING");
        result.setTransactionDateTime(transactionDateTime);
        result.setVersion(0);
        result.setDeleted(0);
        return result;
    }

    private TransactionOperationDO operation(String operationId,
                                             String transactionId,
                                             LocalDateTime transactionDateTime) {
        TransactionOperationDO result = new TransactionOperationDO();
        result.setOperationId(operationId);
        result.setTransactionId(transactionId);
        result.setTransactionStatus("PROCESSING");
        result.setTransactionDateTime(transactionDateTime);
        result.setVersion(0);
        result.setDeleted(0);
        return result;
    }

    private TransactionMerchantNotificationDO notification(String notifyId,
                                                            String transactionId,
                                                            LocalDateTime transactionDateTime) {
        TransactionMerchantNotificationDO result = new TransactionMerchantNotificationDO();
        result.setNotifyId(notifyId);
        result.setTransactionId(transactionId);
        result.setNotifyStatus("INIT");
        result.setNotifyConfigSnapshotJson("{}");
        result.setTransactionDateTime(transactionDateTime);
        result.setVersion(0);
        result.setDeleted(0);
        return result;
    }

    private int count(String table) {
        return direct.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    /**
     * 为 H2 POC 提供 MySQL JSON_SET 的最小等价行为，避免测试绕过生产 Mapper SQL。
     *
     * @param sourceJson 当前 JSON 快照
     * @param path       本用例固定使用的 payloadJson 路径
     * @param valueJson  待写入的回调 JSON
     * @return 更新后的 JSON 快照
     */
    public static String jsonSet(String sourceJson, String path, String valueJson) {
        return "{\"payloadJson\":" + valueJson + "}";
    }

    private void createPhysicalTables() {
        for (String suffix : List.of("202603", "202604")) {
            direct.execute("CREATE TABLE transaction_order_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, operation_id VARCHAR(64) NOT NULL, "
                    + "transaction_status VARCHAR(32), transaction_date_time TIMESTAMP(3) NOT NULL, "
                    + "version INT NOT NULL, deleted INT NOT NULL)");
            direct.execute("CREATE TABLE transaction_operation_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, operation_id VARCHAR(64) NOT NULL, "
                    + "transaction_id VARCHAR(64) NOT NULL, transaction_status VARCHAR(32), "
                    + "transaction_date_time TIMESTAMP(3) NOT NULL, version INT NOT NULL, deleted INT NOT NULL)");
            direct.execute("CREATE TABLE transaction_merchant_notification_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, notify_id VARCHAR(64) NOT NULL, "
                    + "transaction_id VARCHAR(64) NOT NULL, notify_status VARCHAR(32) NOT NULL, "
                    + "notify_config_snapshot_json VARCHAR(1024), "
                    + "payload_json_masked VARCHAR(512), next_retry_time TIMESTAMP(3), update_time TIMESTAMP(3), "
                    + "transaction_date_time TIMESTAMP(3) NOT NULL, version INT NOT NULL, deleted INT NOT NULL)");
        }
    }
}
