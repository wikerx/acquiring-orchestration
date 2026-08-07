package com.scott.payment.component.db.sharding;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.scott.payment.payment.mapper.RefundManagementMapper;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundQuery;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefundManagementLogicalMapperTests {

    private HikariDataSource primary;
    private DataSource shardingDataSource;
    private JdbcTemplate direct;

    @BeforeEach
    void setUp() throws Exception {
        primary = new HikariDataSource();
        primary.setJdbcUrl("jdbc:h2:mem:refund_management_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        primary.setUsername("sa");
        primary.setPassword("");
        primary.setMaximumPoolSize(2);
        primary.setMinimumIdle(0);
        direct = new JdbcTemplate(primary);
        createTables();

        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPrimaryDataSource("master");
        properties.setReplicaDataSources(List.of());
        properties.setPhysicalNodes(List.of("202603", "202604"));
        properties.setLogicTables(List.of("transaction_operation", "transaction_payment_method_info"));
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
    void shouldCountRefundsWhenJoiningShardedAndSingleTables() {
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 4, 10, 43, 14);
        direct.update("""
                INSERT INTO transaction_operation_202603
                  (transaction_id, merchant_id, transaction_type, transaction_date_time, deleted)
                VALUES (?, ?, 'REFUND', ?, 0)
                """, "refund-001", "merchant-001", transactionTime);

        RefundQuery query = new RefundQuery();
        try (SqlSession session = sqlSessionFactory().openSession()) {
            long count = session.getMapper(RefundManagementMapper.class).count(
                    query,
                    LocalDateTime.of(2026, 8, 1, 0, 0),
                    LocalDateTime.of(2026, 8, 7, 23, 59));
            assertThat(count).isEqualTo(1L);
        }
    }

    @Test
    void shouldMatchLivePhysicalRefundCountThroughLogicalMapper() throws Exception {
        String liveUrl = System.getenv("REFUND_LIVE_MYSQL_URL");
        Assumptions.assumeTrue(liveUrl != null && !liveUrl.isBlank());
        try (HikariDataSource live = new HikariDataSource()) {
            live.setJdbcUrl(liveUrl);
            live.setUsername(System.getenv("REFUND_LIVE_MYSQL_USERNAME"));
            live.setPassword(System.getenv("REFUND_LIVE_MYSQL_PASSWORD"));
            live.setMaximumPoolSize(2);
            live.setMinimumIdle(0);
            LocalDateTime begin = LocalDateTime.of(2026, 8, 1, 0, 0);
            LocalDateTime endExclusive = LocalDateTime.of(2026, 8, 8, 0, 0);
            long physicalCount = new JdbcTemplate(live).queryForObject("""
                    SELECT COUNT(DISTINCT o.id)
                    FROM transaction_operation_202603 o
                    LEFT JOIN transaction_refund_approval a
                      ON a.refund_transaction_id = o.transaction_id
                     AND a.merchant_id = o.merchant_id
                    LEFT JOIN transaction_payment_method_info_202603 p
                      ON p.transaction_id = o.transaction_id
                     AND p.transaction_date_time = o.transaction_date_time
                    WHERE o.deleted = 0
                      AND o.transaction_type IN ('REFUND', 'VOID')
                      AND o.transaction_date_time >= ?
                      AND o.transaction_date_time < ?
                    """, Long.class, begin, endExclusive);

            TransactionShardingProperties properties = new TransactionShardingProperties();
            properties.setPrimaryDataSource("master");
            properties.setReplicaDataSources(List.of());
            properties.setPhysicalNodes(List.of("202603", "202604"));
            properties.setLogicTables(List.of("transaction_operation", "transaction_payment_method_info"));
            TransactionShardingDataSourceConfiguration configuration =
                    new TransactionShardingDataSourceConfiguration();
            try (AutoCloseable liveSharding = (AutoCloseable) ShardingSphereDataSourceFactory.createDataSource(
                    Map.of("master", live),
                    configuration.buildRules(properties, List.of()),
                    configuration.buildSystemProperties())) {
                RefundQuery query = new RefundQuery();
                try (SqlSession session = sqlSessionFactory((DataSource) liveSharding).openSession()) {
                    long logicalCount = session.getMapper(RefundManagementMapper.class)
                            .count(query, begin, endExclusive);
                    assertThat(logicalCount).isEqualTo(physicalCount).isPositive();
                }
            }
        }
    }

    private SqlSessionFactory sqlSessionFactory() {
        return sqlSessionFactory(shardingDataSource);
    }

    private SqlSessionFactory sqlSessionFactory(DataSource dataSource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment(
                "refund-management-logical-mapper",
                new JdbcTransactionFactory(),
                dataSource));
        configuration.addMapper(RefundManagementMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private void createTables() {
        for (String suffix : List.of("202603", "202604")) {
            direct.execute("CREATE TABLE transaction_operation_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, transaction_id VARCHAR(64) NOT NULL, "
                    + "merchant_id VARCHAR(64) NOT NULL, transaction_type VARCHAR(32) NOT NULL, "
                    + "transaction_date_time TIMESTAMP(3) NOT NULL, deleted INT NOT NULL)");
            direct.execute("CREATE TABLE transaction_payment_method_info_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, transaction_id VARCHAR(64) NOT NULL, "
                    + "transaction_date_time TIMESTAMP(3) NOT NULL)");
        }
        direct.execute("""
                CREATE TABLE transaction_refund_approval (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  refund_transaction_id VARCHAR(64) NOT NULL,
                  merchant_id VARCHAR(64) NOT NULL
                )
                """);
    }
}
