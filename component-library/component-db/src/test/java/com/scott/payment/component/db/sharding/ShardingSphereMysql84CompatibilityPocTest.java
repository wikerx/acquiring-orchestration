package com.scott.payment.component.db.sharding;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingSphereMysql84CompatibilityPocTest
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 使用隔离 MySQL 8.4 容器验证季度分片、自增回填、事务、锁、Binding 和读写分离。
 * @status : create
 */
@Testcontainers(disabledWithoutDocker = true)
class ShardingSphereMysql84CompatibilityPocTest {

    /** 容器内 MySQL 监听端口。 */
    private static final int MYSQL_PORT = 3306;
    /** 模拟写库的隔离 schema。 */
    private static final String PRIMARY_DATABASE = "sharding_poc_primary";
    /** 模拟只读副本的隔离 schema。 */
    private static final String REPLICA_DATABASE = "sharding_poc_replica";

    /** 按测试类生命周期复用的 MySQL 8.4 容器。 */
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    /** 写库连接池。 */
    private HikariDataSource primary;
    /** 只读副本连接池。 */
    private HikariDataSource replica;
    /** 被测 ShardingSphere 分片与读写分离复合数据源。 */
    private DataSource shardingDataSource;
    /** 通过逻辑表执行 SQL 的测试入口。 */
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        createDatabases();
        primary = mysqlDataSource(PRIMARY_DATABASE);
        replica = mysqlDataSource(REPLICA_DATABASE);
        createPhysicalTables(primary);
        createPhysicalTables(replica);

        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPrimaryDataSource("master");
        properties.setReplicaDataSources(List.of("slave_1"));
        properties.setPhysicalNodes(List.of("202603", "202604"));
        properties.setLogicTables(List.of(
                "transaction_order", "transaction_operation", "transaction_merchant_notification"));
        TransactionShardingDataSourceConfiguration configuration = new TransactionShardingDataSourceConfiguration();
        shardingDataSource = ShardingSphereDataSourceFactory.createDataSource(
                Map.of("master", primary, "slave_1", replica),
                configuration.buildRules(properties, properties.getReplicaDataSources()),
                configuration.buildSystemProperties());
        jdbcTemplate = new JdbcTemplate(shardingDataSource);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (shardingDataSource instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    @Test
    void shouldBackfillMysqlAutoIncrementAndRouteReadsAndBindingJoin() {
        LocalDateTime shardingTime = LocalDateTime.of(2026, 8, 2, 1, 2, 3, 123_000_000);
        PocOrder order = new PocOrder("mysql-op", shardingTime, "PROCESSING");
        try (SqlSession session = sqlSessionFactory().openSession(true)) {
            assertEquals(1, session.getMapper(PocMapper.class).insertOrder(order));
        }
        assertNotNull(order.getId());
        assertTrue(order.getId() > 0L);
        assertEquals(1, direct(primary).queryForObject(
                "SELECT COUNT(*) FROM transaction_order_202603 WHERE operation_id = 'mysql-op'", Integer.class));
        assertEquals(0, direct(replica).queryForObject(
                "SELECT COUNT(*) FROM transaction_order_202603 WHERE operation_id = 'mysql-op'", Integer.class));

        direct(replica).update("""
                INSERT INTO transaction_order_202603(operation_id, transaction_date_time, status, version)
                VALUES (?, ?, 'PROCESSING', 0)
                """, "replica-op", shardingTime);
        Integer replicaCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM transaction_order
                WHERE transaction_date_time >= ? AND transaction_date_time < ?
                """, Integer.class, LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 10, 1, 0, 0));
        assertEquals(1, replicaCount);

        jdbcTemplate.update("""
                INSERT INTO transaction_operation(operation_id, transaction_date_time, status, version)
                VALUES (?, ?, 'PROCESSING', 0)
                """, "mysql-op", shardingTime);
        TransactionTemplate transaction = transactionTemplate();
        Integer joined = transaction.execute(status -> jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM transaction_order o
                JOIN transaction_operation p
                  ON p.operation_id = o.operation_id
                 AND p.transaction_date_time = o.transaction_date_time
                WHERE o.transaction_date_time = ?
                """, Integer.class, shardingTime));
        assertEquals(1, joined);
        assertEquals("Asia/Shanghai", direct(primary).queryForObject("SELECT @@session.time_zone", String.class));
    }

    @Test
    void shouldRollbackAndKeepForUpdateAndCasOnPrimary() {
        LocalDateTime shardingTime = LocalDateTime.of(2026, 11, 2, 3, 0);
        TransactionTemplate transaction = transactionTemplate();
        transaction.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO transaction_order(operation_id, transaction_date_time, status, version)
                    VALUES (?, ?, 'PROCESSING', 0)
                    """, "rollback-op", shardingTime);
            jdbcTemplate.update("""
                    INSERT INTO payment_transaction_auxiliary(request_id, status)
                    VALUES (?, 'PROCESSING')
                    """, "rollback-op");
            status.setRollbackOnly();
        });
        assertEquals(0, direct(primary).queryForObject(
                "SELECT COUNT(*) FROM transaction_order_202604 WHERE operation_id = 'rollback-op'", Integer.class));
        assertEquals(0, direct(primary).queryForObject(
                "SELECT COUNT(*) FROM payment_transaction_auxiliary WHERE request_id = 'rollback-op'", Integer.class));

        jdbcTemplate.update("""
                INSERT INTO transaction_merchant_notification
                    (notify_id, transaction_date_time, notify_status, version)
                VALUES (?, ?, 'INIT', 0)
                """, "mysql-notify", shardingTime);
        Integer affected = transaction.execute(status -> {
            Long id = jdbcTemplate.queryForObject("""
                    SELECT id FROM transaction_merchant_notification
                    WHERE notify_id = ? AND transaction_date_time = ?
                    FOR UPDATE
                    """, Long.class, "mysql-notify", shardingTime);
            assertNotNull(id);
            return jdbcTemplate.update("""
                    UPDATE transaction_merchant_notification
                    SET notify_status = 'PROCESSING', version = version + 1
                    WHERE id = ? AND transaction_date_time = ? AND notify_status = 'INIT' AND version = 0
                    """, id, shardingTime);
        });
        assertEquals(1, affected);
        assertEquals("PROCESSING", direct(primary).queryForObject("""
                SELECT notify_status FROM transaction_merchant_notification_202604 WHERE notify_id = 'mysql-notify'
                """, String.class));
        assertEquals(0, direct(replica).queryForObject("""
                SELECT COUNT(*) FROM transaction_merchant_notification_202604 WHERE notify_id = 'mysql-notify'
                """, Integer.class));
    }

    private void createDatabases() {
        try (HikariDataSource server = mysqlDataSource("mysql")) {
            JdbcTemplate template = direct(server);
            template.execute("CREATE DATABASE IF NOT EXISTS " + PRIMARY_DATABASE);
            template.execute("CREATE DATABASE IF NOT EXISTS " + REPLICA_DATABASE);
        }
    }

    private HikariDataSource mysqlDataSource(String databaseName) {
        HikariDataSource result = new HikariDataSource();
        result.setJdbcUrl("jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(MYSQL_PORT) + "/" + databaseName
                + "?connectionTimeZone=Asia%2FShanghai&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true");
        result.setUsername("root");
        result.setPassword(MYSQL.getPassword());
        result.setMaximumPoolSize(2);
        result.setMinimumIdle(0);
        return result;
    }

    private void createPhysicalTables(DataSource dataSource) {
        JdbcTemplate template = direct(dataSource);
        template.execute("DROP TABLE IF EXISTS payment_transaction_auxiliary");
        template.execute("CREATE TABLE payment_transaction_auxiliary ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, request_id VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL)");
        for (String suffix : List.of("202603", "202604")) {
            template.execute("DROP TABLE IF EXISTS transaction_order_" + suffix);
            template.execute("DROP TABLE IF EXISTS transaction_operation_" + suffix);
            template.execute("DROP TABLE IF EXISTS transaction_merchant_notification_" + suffix);
            template.execute("CREATE TABLE transaction_order_" + suffix + " ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, operation_id VARCHAR(64) NOT NULL, "
                    + "transaction_date_time DATETIME(3) NOT NULL, status VARCHAR(32) NOT NULL, version INT NOT NULL)");
            template.execute("CREATE TABLE transaction_operation_" + suffix + " ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, operation_id VARCHAR(64) NOT NULL, "
                    + "transaction_date_time DATETIME(3) NOT NULL, status VARCHAR(32) NOT NULL, version INT NOT NULL)");
            template.execute("CREATE TABLE transaction_merchant_notification_" + suffix + " ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, notify_id VARCHAR(64) NOT NULL, "
                    + "transaction_date_time DATETIME(3) NOT NULL, notify_status VARCHAR(32) NOT NULL, version INT NOT NULL)");
        }
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new DataSourceTransactionManager(shardingDataSource));
    }

    private SqlSessionFactory sqlSessionFactory() {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setEnvironment(new Environment("mysql-poc", new JdbcTransactionFactory(), shardingDataSource));
        configuration.addMapper(PocMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private JdbcTemplate direct(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    interface PocMapper {
        /** 插入逻辑主单并验证 MySQL 自增主键回填。 */
        @Insert("""
                INSERT INTO transaction_order(operation_id, transaction_date_time, status, version)
                VALUES (#{row.operationId}, #{row.transactionDateTime}, #{row.status}, 0)
                """)
        @Options(useGeneratedKeys = true, keyProperty = "row.id")
        int insertOrder(@Param("row") PocOrder row);
    }

    static final class PocOrder {
        /** MySQL AUTO_INCREMENT 回填值。 */
        private Long id;
        /** Binding 表关联使用的动作单号。 */
        private final String operationId;
        /** 决定季度物理表的分片时间。 */
        private final LocalDateTime transactionDateTime;
        /** 用于事务和主从语义断言的状态。 */
        private final String status;

        private PocOrder(String operationId, LocalDateTime transactionDateTime, String status) {
            this.operationId = operationId;
            this.transactionDateTime = transactionDateTime;
            this.status = status;
        }

        /** @return 自增主键 */
        public Long getId() {
            return id;
        }

        /** @param id MyBatis 回填的自增主键 */
        public void setId(Long id) {
            this.id = id;
        }

        /** @return 动作单号 */
        public String getOperationId() {
            return operationId;
        }

        /** @return 分片时间 */
        public LocalDateTime getTransactionDateTime() {
            return transactionDateTime;
        }

        /** @return 交易状态 */
        public String getStatus() {
            return status;
        }
    }
}
