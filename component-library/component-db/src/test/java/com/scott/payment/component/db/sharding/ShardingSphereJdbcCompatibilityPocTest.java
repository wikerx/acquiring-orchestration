package com.scott.payment.component.db.sharding;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.db.config.MybatisPlusMapperScanConfig;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
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

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingSphereJdbcCompatibilityPocTest
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 三表 POC，验证 ShardingSphere 5.5.3 的精确/范围路由、自增回填、Binding、事务、锁、读写分离和通知 CAS。
 * @status : create
 */
class ShardingSphereJdbcCompatibilityPocTest {

    /** 模拟写库的 H2 连接池。 */
    private HikariDataSource primary;
    /** 模拟只读副本的 H2 连接池。 */
    private HikariDataSource replica;
    /** 被测 ShardingSphere 分片与读写分离复合数据源。 */
    private DataSource shardingDataSource;
    /** 通过逻辑表执行 SQL 的测试入口。 */
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        String databaseSuffix = UUID.randomUUID().toString().replace("-", "");
        primary = hikariDataSource("jdbc:h2:mem:sharding_poc_primary_" + databaseSuffix + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        replica = hikariDataSource("jdbc:h2:mem:sharding_poc_replica_" + databaseSuffix + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
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
    void shouldRouteInsertAndRangeSelectAndBackfillGeneratedIdThroughMybatis() {
        PocOrder order = new PocOrder();
        order.setOperationId("op-q3");
        order.setTransactionDateTime(LocalDateTime.of(2026, 8, 2, 1, 2, 3, 123_000_000));
        order.setStatus("PROCESSING");

        try (SqlSession session = sqlSessionFactory().openSession(true)) {
            int affected = session.getMapper(PocMapper.class).insertOrder(order);
            assertEquals(1, affected);
        }

        assertNotNull(order.getId());
        assertTrue(order.getId() > 0L);
        assertEquals(1, new JdbcTemplate(primary).queryForObject(
                "SELECT COUNT(*) FROM transaction_order_202603", Integer.class));
        assertEquals(0, new JdbcTemplate(replica).queryForObject(
                "SELECT COUNT(*) FROM transaction_order_202603", Integer.class));

        new JdbcTemplate(replica).update("""
                INSERT INTO transaction_order_202603(operation_id, transaction_date_time, status, version)
                VALUES (?, ?, ?, 0)
                """, "replica-copy", order.getTransactionDateTime(), "PROCESSING");
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM transaction_order
                WHERE transaction_date_time >= ? AND transaction_date_time < ?
                """, Integer.class,
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2027, 1, 1, 0, 0));
        assertEquals(1, count);
    }

    @Test
    void shouldKeepBindingJoinAndForUpdateOnOneQuarterAndPrimaryTransaction() {
        LocalDateTime shardingTime = LocalDateTime.of(2026, 8, 2, 2, 0);
        jdbcTemplate.update("INSERT INTO transaction_order(operation_id, transaction_date_time, status, version) VALUES (?, ?, ?, ?)",
                "op-bind", shardingTime, "PROCESSING", 0);
        jdbcTemplate.update("INSERT INTO transaction_operation(operation_id, transaction_date_time, status, version) VALUES (?, ?, ?, ?)",
                "op-bind", shardingTime, "PROCESSING", 0);

        Integer joined;
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            joined = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM transaction_order o
                    JOIN transaction_operation p
                      ON p.operation_id = o.operation_id
                     AND p.transaction_date_time = o.transaction_date_time
                    WHERE o.transaction_date_time = ?
                    """, Integer.class, shardingTime);
        }
        assertEquals(1, joined);

        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(shardingDataSource));
        Integer affected = transaction.execute(status -> {
            Long id = jdbcTemplate.queryForObject("""
                    SELECT id FROM transaction_order
                    WHERE operation_id = ? AND transaction_date_time = ?
                    FOR UPDATE
                    """, Long.class, "op-bind", shardingTime);
            assertNotNull(id);
            return jdbcTemplate.update("""
                    UPDATE transaction_order
                    SET status = ?, version = version + 1
                    WHERE id = ? AND transaction_date_time = ? AND status = ? AND version = ?
                    """, "SUCCESS", id, shardingTime, "PROCESSING", 0);
        });
        transaction.executeWithoutResult(status -> jdbcTemplate.update("""
                INSERT INTO payment_transaction_auxiliary(request_id, status)
                VALUES (?, ?)
                """, "aux-op-bind", "SUCCESS"));
        assertEquals(1, affected);
        assertEquals("SUCCESS", new JdbcTemplate(primary).queryForObject("""
                SELECT status FROM transaction_order_202603 WHERE operation_id = 'op-bind'
                """, String.class));
        assertEquals(0, new JdbcTemplate(replica).queryForObject(
                "SELECT COUNT(*) FROM transaction_order_202603 WHERE operation_id = 'op-bind'", Integer.class));
        assertEquals(1, new JdbcTemplate(primary).queryForObject(
                "SELECT COUNT(*) FROM payment_transaction_auxiliary WHERE request_id = 'aux-op-bind'", Integer.class));
    }

    @Test
    void shouldProtectMerchantNotificationCasAndPrimaryHint() {
        LocalDateTime shardingTime = LocalDateTime.of(2026, 11, 2, 3, 0);
        jdbcTemplate.update("""
                INSERT INTO transaction_merchant_notification
                    (notify_id, transaction_date_time, notify_status, version)
                VALUES (?, ?, ?, ?)
                """, "notify-q4", shardingTime, "INIT", 0);

        int claimed = jdbcTemplate.update("""
                UPDATE transaction_merchant_notification
                SET notify_status = 'PROCESSING', version = version + 1
                WHERE notify_id = ? AND transaction_date_time = ?
                  AND notify_status = 'INIT' AND version = 0
                """, "notify-q4", shardingTime);
        int duplicateClaim = jdbcTemplate.update("""
                UPDATE transaction_merchant_notification
                SET notify_status = 'PROCESSING', version = version + 1
                WHERE notify_id = ? AND transaction_date_time = ?
                  AND notify_status = 'INIT' AND version = 0
                """, "notify-q4", shardingTime);
        assertEquals(1, claimed);
        assertEquals(0, duplicateClaim);

        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            String status = jdbcTemplate.queryForObject("""
                    SELECT notify_status FROM transaction_merchant_notification
                    WHERE notify_id = ? AND transaction_date_time = ?
                    """, String.class, "notify-q4", shardingTime);
            assertEquals("PROCESSING", status);
        }
        assertEquals("PROCESSING", new JdbcTemplate(primary).queryForObject("""
                SELECT notify_status FROM transaction_merchant_notification_202604 WHERE notify_id = 'notify-q4'
                """, String.class));
        assertEquals(0, new JdbcTemplate(replica).queryForObject("""
                SELECT COUNT(*) FROM transaction_merchant_notification_202604 WHERE notify_id = 'notify-q4'
                """, Integer.class));
    }

    @Test
    void shouldMergeCrossQuarterCursorPageAndCountWithoutDuplicates() {
        JdbcTemplate replicaTemplate = new JdbcTemplate(replica);
        LocalDateTime q3Time = LocalDateTime.of(2026, 9, 30, 23, 59, 59, 999_000_000);
        LocalDateTime q4FirstTime = LocalDateTime.of(2026, 10, 1, 0, 0, 0, 1_000_000);
        LocalDateTime q4SecondTime = LocalDateTime.of(2026, 10, 2, 0, 0);
        insertOrder(replicaTemplate, "cursor-q3", q3Time);
        insertOrder(replicaTemplate, "cursor-q4-first", q4FirstTime);
        insertOrder(replicaTemplate, "cursor-q4-second", q4SecondTime);

        LocalDateTime beginTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTimeExclusive = LocalDateTime.of(2027, 1, 1, 0, 0);
        List<String> firstPage = jdbcTemplate.queryForList("""
                SELECT operation_id FROM transaction_order
                WHERE transaction_date_time >= ? AND transaction_date_time < ?
                ORDER BY transaction_date_time DESC, id DESC
                LIMIT 2
                """, String.class, beginTime, endTimeExclusive);
        Long cursorId = replicaTemplate.queryForObject("""
                SELECT id FROM transaction_order_202604 WHERE operation_id = 'cursor-q4-first'
                """, Long.class);
        List<String> secondPage = jdbcTemplate.queryForList("""
                SELECT operation_id FROM transaction_order
                WHERE transaction_date_time >= ? AND transaction_date_time < ?
                  AND (transaction_date_time < ? OR (transaction_date_time = ? AND id < ?))
                ORDER BY transaction_date_time DESC, id DESC
                LIMIT 2
                """, String.class, beginTime, endTimeExclusive, q4FirstTime, q4FirstTime, cursorId);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM transaction_order
                WHERE transaction_date_time >= ? AND transaction_date_time < ?
                """, Integer.class, beginTime, endTimeExclusive);

        assertEquals(List.of("cursor-q4-second", "cursor-q4-first"), firstPage);
        assertEquals(List.of("cursor-q3"), secondPage);
        assertEquals(3, count);
    }

    @Test
    void shouldKeepNotificationTerminalStateDuringQuarterScopedRecovery() {
        LocalDateTime shardingTime = LocalDateTime.of(2026, 11, 2, 3, 0);
        LocalDateTime staleTime = LocalDateTime.of(2026, 11, 2, 3, 5);
        jdbcTemplate.update("""
                INSERT INTO transaction_merchant_notification
                    (notify_id, transaction_date_time, notify_status, version, last_attempt_no,
                     max_retry_count, next_retry_time, update_time)
                VALUES (?, ?, 'PROCESSING', 1, 1, 3, NULL, ?)
                """, "stale-notify", shardingTime, staleTime);
        jdbcTemplate.update("""
                INSERT INTO transaction_merchant_notification
                    (notify_id, transaction_date_time, notify_status, version, last_attempt_no,
                     max_retry_count, next_retry_time, update_time)
                VALUES (?, ?, 'PROCESSING', 1, 1, 3, NULL, ?)
                """, "success-notify", shardingTime, staleTime);
        int success = jdbcTemplate.update("""
                UPDATE transaction_merchant_notification
                SET notify_status = 'SUCCESS', success_time = ?, version = version + 1, update_time = ?
                WHERE notify_id = ? AND transaction_date_time = ?
                  AND notify_status = 'PROCESSING' AND version = 1
                """, staleTime.plusMinutes(1), staleTime.plusMinutes(1), "success-notify", shardingTime);
        int recovered = jdbcTemplate.update("""
                UPDATE transaction_merchant_notification
                SET notify_status = 'FAILED', next_retry_time = ?, version = version + 1, update_time = ?
                WHERE transaction_date_time >= ? AND transaction_date_time < ?
                  AND notify_status = 'PROCESSING' AND update_time < ?
                  AND last_attempt_no < max_retry_count
                """, staleTime.plusMinutes(10), staleTime.plusMinutes(10),
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0),
                staleTime.plusMinutes(5));

        JdbcTemplate primaryTemplate = new JdbcTemplate(primary);
        assertEquals(1, success);
        assertEquals(1, recovered);
        assertEquals("FAILED", primaryTemplate.queryForObject("""
                SELECT notify_status FROM transaction_merchant_notification_202604 WHERE notify_id = 'stale-notify'
                """, String.class));
        assertEquals("SUCCESS", primaryTemplate.queryForObject("""
                SELECT notify_status FROM transaction_merchant_notification_202604 WHERE notify_id = 'success-notify'
                """, String.class));
    }

    @Test
    void shouldClearPrimaryHintAfterException() {
        LocalDateTime shardingTime = LocalDateTime.of(2026, 8, 2, 4, 0);
        new JdbcTemplate(primary).update("""
                INSERT INTO transaction_order_202603(operation_id, transaction_date_time, status, version)
                VALUES (?, ?, 'PRIMARY_ONLY', 0)
                """, "hint-cleanup", shardingTime);
        new JdbcTemplate(replica).update("""
                INSERT INTO transaction_order_202603(operation_id, transaction_date_time, status, version)
                VALUES (?, ?, 'REPLICA_ONLY', 0)
                """, "hint-cleanup", shardingTime);

        assertThrows(IllegalStateException.class, () -> {
            try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
                throw new IllegalStateException("poc failure");
            }
        });

        String routedStatus = jdbcTemplate.queryForObject("""
                SELECT status FROM transaction_order
                WHERE operation_id = ? AND transaction_date_time = ?
                """, String.class, "hint-cleanup", shardingTime);
        assertEquals("REPLICA_ONLY", routedStatus);
    }

    @Test
    void shouldRouteReadOnlyTransactionToPrimary() {
        LocalDateTime shardingTime = LocalDateTime.of(2026, 8, 2, 5, 0);
        new JdbcTemplate(primary).update("""
                INSERT INTO transaction_order_202603(operation_id, transaction_date_time, status, version)
                VALUES (?, ?, 'PRIMARY_ONLY', 0)
                """, "readonly-routing", shardingTime);
        new JdbcTemplate(replica).update("""
                INSERT INTO transaction_order_202603(operation_id, transaction_date_time, status, version)
                VALUES (?, ?, 'REPLICA_ONLY', 0)
                """, "readonly-routing", shardingTime);
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(shardingDataSource));
        transaction.setReadOnly(true);

        String routedStatus = transaction.execute(status -> jdbcTemplate.queryForObject("""
                SELECT status FROM transaction_order
                WHERE operation_id = ? AND transaction_date_time = ?
                """, String.class, "readonly-routing", shardingTime));

        assertEquals("PRIMARY_ONLY", routedStatus);
    }

    @Test
    void shouldApplyMybatisPlusPaginationOnceAcrossQuarters() {
        JdbcTemplate replicaTemplate = new JdbcTemplate(replica);
        insertOrder(replicaTemplate, "page-q3", LocalDateTime.of(2026, 9, 30, 23, 59, 59, 999_000_000));
        insertOrder(replicaTemplate, "page-q4-first", LocalDateTime.of(2026, 10, 1, 0, 0, 0, 1_000_000));
        insertOrder(replicaTemplate, "page-q4-second", LocalDateTime.of(2026, 10, 2, 0, 0));
        Page<PocOrder> page = new Page<>(1, 2);

        List<PocOrder> rows;
        try (SqlSession session = mybatisPlusSqlSessionFactory().openSession(true)) {
            rows = session.getMapper(PocPagingMapper.class).selectPage(
                    page, LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0));
        }

        assertEquals(3, page.getTotal());
        assertEquals(2, page.getPages());
        assertEquals(List.of("page-q4-second", "page-q4-first"),
                rows.stream().map(PocOrder::getOperationId).toList());
    }

    private SqlSessionFactory sqlSessionFactory() {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setEnvironment(new Environment("poc", new JdbcTransactionFactory(), shardingDataSource));
        configuration.addMapper(PocMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private SqlSessionFactory mybatisPlusSqlSessionFactory() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("mybatis-plus-poc", new JdbcTransactionFactory(), shardingDataSource));
        configuration.addInterceptor(new MybatisPlusMapperScanConfig().mybatisPlusInterceptor());
        configuration.addMapper(PocPagingMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private HikariDataSource hikariDataSource(String url) {
        HikariDataSource result = new HikariDataSource();
        result.setJdbcUrl(url);
        result.setUsername("sa");
        result.setPassword("");
        result.setMaximumPoolSize(2);
        result.setMinimumIdle(0);
        return result;
    }

    private void createPhysicalTables(DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.execute("CREATE TABLE IF NOT EXISTS payment_transaction_auxiliary ("
                + "id BIGINT AUTO_INCREMENT NOT NULL, request_id VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL)");
        for (String suffix : List.of("202603", "202604")) {
            template.execute("CREATE TABLE IF NOT EXISTS transaction_order_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, operation_id VARCHAR(64) NOT NULL, "
                    + "transaction_date_time TIMESTAMP(3) NOT NULL, status VARCHAR(32) NOT NULL, version INT NOT NULL)");
            template.execute("CREATE TABLE IF NOT EXISTS transaction_operation_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, operation_id VARCHAR(64) NOT NULL, "
                    + "transaction_date_time TIMESTAMP(3) NOT NULL, status VARCHAR(32) NOT NULL, version INT NOT NULL)");
            template.execute("CREATE TABLE IF NOT EXISTS transaction_merchant_notification_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, notify_id VARCHAR(64) NOT NULL, "
                    + "transaction_date_time TIMESTAMP(3) NOT NULL, notify_status VARCHAR(32) NOT NULL, version INT NOT NULL, "
                    + "last_attempt_no INT, max_retry_count INT, next_retry_time TIMESTAMP(3), "
                    + "success_time TIMESTAMP(3), fail_reason VARCHAR(512), update_time TIMESTAMP(3))");
        }
    }

    private void insertOrder(JdbcTemplate template, String operationId, LocalDateTime transactionDateTime) {
        String suffix = transactionDateTime.getMonthValue() <= 9 ? "202603" : "202604";
        template.update("INSERT INTO transaction_order_" + suffix
                + "(operation_id, transaction_date_time, status, version) VALUES (?, ?, 'PROCESSING', 0)",
                operationId, transactionDateTime);
    }

    interface PocMapper {
        /** 插入逻辑主单并验证自增主键回填。 */
        @Insert("""
                INSERT INTO transaction_order(operation_id, transaction_date_time, status, version)
                VALUES (#{row.operationId}, #{row.transactionDateTime}, #{row.status}, 0)
                """)
        @Options(useGeneratedKeys = true, keyProperty = "row.id")
        int insertOrder(@Param("row") PocOrder row);
    }

    interface PocPagingMapper {
        /** 验证 MyBatis-Plus 分页插件只在跨季度归并后执行一次。 */
        @Select("""
                SELECT id, operation_id, transaction_date_time, status
                FROM transaction_order
                WHERE transaction_date_time >= #{beginTime}
                  AND transaction_date_time < #{endTimeExclusive}
                ORDER BY transaction_date_time DESC, id DESC
                """)
        List<PocOrder> selectPage(Page<PocOrder> page,
                                  @Param("beginTime") LocalDateTime beginTime,
                                  @Param("endTimeExclusive") LocalDateTime endTimeExclusive);
    }

    static final class PocOrder {
        /** 物理表自增主键。 */
        private Long id;
        /** Binding 表关联使用的动作单号。 */
        private String operationId;
        /** 决定季度物理表的分片时间。 */
        private LocalDateTime transactionDateTime;
        /** 用于主从路由断言的交易状态。 */
        private String status;

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

        /** @param operationId 动作单号 */
        public void setOperationId(String operationId) {
            this.operationId = operationId;
        }

        /** @return 分片时间 */
        public LocalDateTime getTransactionDateTime() {
            return transactionDateTime;
        }

        /** @param transactionDateTime 分片时间 */
        public void setTransactionDateTime(LocalDateTime transactionDateTime) {
            this.transactionDateTime = transactionDateTime;
        }

        /** @return 交易状态 */
        public String getStatus() {
            return status;
        }

        /** @param status 交易状态 */
        public void setStatus(String status) {
            this.status = status;
        }
    }
}
