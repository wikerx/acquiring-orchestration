package com.scott.payment.component.db.sharding;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.scott.payment.data.entity.DataMerchantNotificationLogDO;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.mapper.DataMerchantNotificationLogMapper;
import com.scott.payment.data.mapper.DataMerchantNotificationMapper;
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
 * @classname : DataMerchantNotificationLogicalMapperPocTest
 * @date : 2026-08-02 02:18
 * @email : scott_x@163.com
 * @description : 通过真实 Data Mapper 验证通知逻辑表的单分片抢占、终态 CAS、季度恢复和日志路由。
 * @status : create
 */
class DataMerchantNotificationLogicalMapperPocTest {

    /** 同时承载两个季度物理表的隔离主库连接池。 */
    private HikariDataSource primary;
    /** 由三表测试规则创建、测试结束后关闭的 ShardingSphere 数据源。 */
    private DataSource shardingDataSource;
    /** 绕过逻辑路由验证真实物理表状态的只读测试入口。 */
    private JdbcTemplate direct;

    @BeforeEach
    void setUp() throws Exception {
        primary = new HikariDataSource();
        primary.setJdbcUrl("jdbc:h2:mem:data_notification_mapper_poc_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        primary.setUsername("sa");
        primary.setPassword("");
        primary.setMaximumPoolSize(2);
        primary.setMinimumIdle(0);
        direct = new JdbcTemplate(primary);
        createPhysicalTables();

        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPrimaryDataSource("master");
        properties.setReplicaDataSources(List.of());
        properties.setPhysicalNodes(List.of("202603", "202604"));
        properties.setLogicTables(List.of(
                "transaction_merchant_notification",
                "transaction_merchant_notification_log"));
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
    void shouldAllowOnlyOneClaimAndProtectTerminalStateAcrossQuarterRecovery() {
        LocalDateTime q3Time = LocalDateTime.of(2026, 8, 2, 2, 0);
        LocalDateTime q4Time = LocalDateTime.of(2026, 11, 2, 2, 0);
        LocalDateTime now = LocalDateTime.of(2026, 12, 1, 0, 0);
        seedNotification(301L, "notify-q3", "transaction-q3", q3Time, "INIT", 0, now.minusHours(2));
        seedNotification(401L, "notify-q4", "transaction-q4", q4Time, "PROCESSING", 1, now.minusHours(2));

        try (SqlSession session = sqlSessionFactory().openSession(true)) {
            DataMerchantNotificationMapper notificationMapper = session.getMapper(DataMerchantNotificationMapper.class);
            DataMerchantNotificationLogMapper logMapper = session.getMapper(DataMerchantNotificationLogMapper.class);

            List<DataMerchantNotificationTaskDO> due = notificationMapper.selectDueForNotify(
                    LocalDateTime.of(2026, 7, 1, 0, 0),
                    LocalDateTime.of(2026, 10, 1, 0, 0),
                    now,
                    10);
            assertThat(due).extracting(DataMerchantNotificationTaskDO::getNotifyId)
                    .containsExactly("notify-q3");
            DataMerchantNotificationTaskDO ready = notificationMapper.selectReadyByTransactionId(
                    "transaction-q3", q3Time, now);
            assertThat(ready).isNotNull();
            assertThat(notificationMapper.markProcessing(ready.getId(), q3Time, ready.getVersion(), now))
                    .isEqualTo(1);
            assertThat(notificationMapper.markProcessing(ready.getId(), q3Time, ready.getVersion(), now))
                    .isZero();
            assertThat(notificationMapper.markSuccess(ready.getId(), q3Time, 1, now.plusSeconds(1)))
                    .isEqualTo(1);
            List<DataMerchantNotificationTaskDO> q3Stale = notificationMapper.selectStaleProcessing(
                    LocalDateTime.of(2026, 7, 1, 0, 0),
                    LocalDateTime.of(2026, 10, 1, 0, 0),
                    now.minusMinutes(30),
                    10);
            assertThat(q3Stale).isEmpty();
            List<DataMerchantNotificationTaskDO> q4Stale = notificationMapper.selectStaleProcessing(
                    LocalDateTime.of(2026, 10, 1, 0, 0),
                    LocalDateTime.of(2027, 1, 1, 0, 0),
                    now.minusMinutes(30),
                    10);
            assertThat(q4Stale).singleElement().satisfies(candidate -> {
                assertThat(notificationMapper.recoverStaleProcessingCas(
                        candidate.getId(),
                        candidate.getTransactionDateTime(),
                        candidate.getVersion(),
                        now.minusMinutes(30),
                        now)).isEqualTo(1);
                assertThat(notificationMapper.recoverStaleProcessingCas(
                        candidate.getId(),
                        candidate.getTransactionDateTime(),
                        candidate.getVersion(),
                        now.minusMinutes(30),
                        now)).isZero();
            });

            DataMerchantNotificationLogDO logDO = notificationLog(q3Time, now);
            assertThat(logMapper.insert(logDO)).isEqualTo(1);
        }

        assertThat(status(301L, "202603")).isEqualTo("SUCCESS");
        assertThat(status(401L, "202604")).isEqualTo("FAILED");
        assertThat(direct.queryForObject(
                "SELECT COUNT(*) FROM transaction_merchant_notification_log_202603", Integer.class)).isEqualTo(1);
        assertThat(direct.queryForObject(
                "SELECT COUNT(*) FROM transaction_merchant_notification_log_202604", Integer.class)).isZero();
    }

    private SqlSessionFactory sqlSessionFactory() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment(
                "data-notification-logical-mapper-poc",
                new JdbcTransactionFactory(),
                shardingDataSource));
        configuration.addMapper(DataMerchantNotificationMapper.class);
        configuration.addMapper(DataMerchantNotificationLogMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private void seedNotification(Long id,
                                  String notifyId,
                                  String transactionId,
                                  LocalDateTime transactionDateTime,
                                  String status,
                                  int version,
                                  LocalDateTime updateTime) {
        String suffix = transactionDateTime.isBefore(LocalDateTime.of(2026, 10, 1, 0, 0))
                ? "202603" : "202604";
        direct.update("""
                INSERT INTO transaction_merchant_notification_%s
                    (id, notify_id, transaction_id, merchant_id, notify_status,
                     last_attempt_no, max_retry_count, next_retry_time,
                     transaction_date_time, version, deleted, update_time)
                VALUES (?, ?, ?, 'merchant-poc', ?, 0, 3, ?, ?, ?, 0, ?)
                """.formatted(suffix),
                id, notifyId, transactionId, status, updateTime.minusMinutes(1),
                transactionDateTime, version, updateTime);
    }

    private DataMerchantNotificationLogDO notificationLog(LocalDateTime transactionDateTime, LocalDateTime now) {
        DataMerchantNotificationLogDO result = new DataMerchantNotificationLogDO();
        result.setNotifyLogId("notify-log-q3");
        result.setNotifyId("notify-q3");
        result.setDeliveryMode("AUTO");
        result.setTransactionId("transaction-q3");
        result.setMerchantId("merchant-poc");
        result.setAttemptNo(1);
        result.setSuccess(1);
        result.setNotifyTime(now);
        result.setDurationMillis(10);
        result.setTransactionDateTime(transactionDateTime);
        result.setTransactionUtcTime(transactionDateTime.minusHours(8));
        result.setTransactionTimeZone("Asia/Shanghai");
        result.setCreateTime(now);
        return result;
    }

    private String status(Long id, String suffix) {
        return direct.queryForObject(
                "SELECT notify_status FROM transaction_merchant_notification_" + suffix + " WHERE id = ?",
                String.class,
                id);
    }

    private void createPhysicalTables() {
        for (String suffix : List.of("202603", "202604")) {
            direct.execute("CREATE TABLE transaction_merchant_notification_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, notify_id VARCHAR(64) NOT NULL, "
                    + "transaction_id VARCHAR(64) NOT NULL, operation_id VARCHAR(64), merchant_id VARCHAR(64), "
                    + "merchant_order_no VARCHAR(128), callback_url VARCHAR(512), payload_json VARCHAR(4096), "
                    + "target_url_hash VARCHAR(128), target_url_masked VARCHAR(512), payload_json_masked VARCHAR(1024), "
                    + "sign_type VARCHAR(32), notify_status VARCHAR(32) NOT NULL, last_attempt_no INT NOT NULL, "
                    + "max_retry_count INT NOT NULL, next_retry_time TIMESTAMP(3), success_time TIMESTAMP(3), "
                    + "fail_reason VARCHAR(512), processing_mode VARCHAR(16), processing_event_id VARCHAR(128), "
                    + "transaction_date_time TIMESTAMP(3) NOT NULL, "
                    + "version INT NOT NULL, deleted INT NOT NULL, update_time TIMESTAMP(3) NOT NULL)");
            direct.execute("CREATE TABLE transaction_merchant_notification_log_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, notify_log_id VARCHAR(64) NOT NULL, "
                    + "notify_id VARCHAR(64) NOT NULL, callback_event_id VARCHAR(128), "
                    + "delivery_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO', "
                    + "transaction_id VARCHAR(64) NOT NULL, operation_id VARCHAR(64), "
                    + "merchant_id VARCHAR(64), attempt_no INT, target_url_hash VARCHAR(128), http_status INT, "
                    + "request_header_json_masked VARCHAR(1024), request_body_json_masked VARCHAR(1024), "
                    + "response_body_json_masked VARCHAR(1024), success INT, error_message VARCHAR(1024), "
                    + "notify_time TIMESTAMP(3), duration_millis INT, transaction_date_time TIMESTAMP(3) NOT NULL, "
                    + "transaction_utc_time TIMESTAMP(3), transaction_time_zone VARCHAR(64), create_time TIMESTAMP(3))");
        }
    }
}
