package com.scott.payment.component.db.sharding;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.entity.TransactionAuthenticationInfoDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.mapper.TransactionAuthenticationInfoMapper;
import com.scott.payment.payment.mapper.TransactionChannelRequestMapper;
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
                "transaction_merchant_notification",
                "transaction_channel_request"));
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

    @Test
    void shouldConvergePreChannelFailureAndKeepAuthenticationTerminalState() {
        LocalDateTime transactionTime = LocalDateTime.of(2026, 9, 1, 12, 30, 0, 123_000_000);
        LocalDateTime now = transactionTime.plusMinutes(1);
        direct.update("""
                INSERT INTO transaction_channel_request_202603
                (request_id, transaction_id, operation_id, request_status,
                 transaction_date_time, version, deleted)
                VALUES (?, ?, ?, 'INIT', ?, 0, 0)
                """, "REQ-FAIL-001", "TX-FAIL-001", "OP-FAIL-001", transactionTime);
        direct.update("""
                INSERT INTO transaction_operation_202603
                (operation_id, transaction_id, transaction_status, process_stage,
                 transaction_date_time, version, deleted)
                VALUES (?, ?, 'PROCESSING', 'CHANNEL_REQUESTING', ?, 0, 0)
                """, "OP-FAIL-001", "TX-FAIL-001", transactionTime);
        direct.update("""
                INSERT INTO transaction_order_202603
                (operation_id, transaction_status, process_stage,
                 transaction_date_time, version, deleted)
                VALUES (?, 'PROCESSING', 'CHANNEL_REQUESTING', ?, 0, 0)
                """, "OP-FAIL-001", transactionTime);

        try (SqlSession session = sqlSessionFactory().openSession(false)) {
            TransactionChannelRequestMapper requestMapper =
                    session.getMapper(TransactionChannelRequestMapper.class);
            TransactionOperationMapper operationMapper = session.getMapper(TransactionOperationMapper.class);
            TransactionOrderMapper orderMapper = session.getMapper(TransactionOrderMapper.class);
            TransactionAuthenticationInfoMapper authenticationMapper =
                    session.getMapper(TransactionAuthenticationInfoMapper.class);

            assertThat(requestMapper.claimPreChannelFailureLogical(
                    "REQ-FAIL-001", transactionTime, now)).isEqualTo(1);
            assertThat(requestMapper.claimPreChannelFailureLogical(
                    "REQ-FAIL-001", transactionTime, now.plusSeconds(1))).isZero();

            TransactionOperationDO operation = operationMapper.selectByTransactionId("TX-FAIL-001", transactionTime);
            assertThat(operationMapper.completeStatus(operation.getId(), transactionTime, operation.getVersion(),
                    "FAILED", "FINISHED", "THREE_DS_AUTHENTICATION_FAILED", "3DS authentication failed",
                    null, null, null, null, null, null, "NOT_REQUIRED")).isEqualTo(1);
            assertThat(operationMapper.completeStatus(operation.getId(), transactionTime, operation.getVersion() + 1,
                    "SUCCESS", "FINISHED", null, null,
                    null, null, null, null, null, null, "NOT_REQUIRED")).isZero();

            TransactionOrderDO order = orderMapper.selectByOperationId("OP-FAIL-001", transactionTime);
            assertThat(orderMapper.completeStatus("OP-FAIL-001", transactionTime, "TX-FAIL-001", order.getVersion(),
                    "FAILED", "FINISHED", "THREE_DS_AUTHENTICATION_FAILED", "3DS authentication failed",
                    "The transaction was declined", "Payment could not be completed", "NOT_REQUIRED")).isEqualTo(1);
            assertThat(orderMapper.completeStatus("OP-FAIL-001", transactionTime, "TX-FAIL-001", order.getVersion() + 1,
                    "SUCCESS", "FINISHED", null, null, null, null, "NOT_REQUIRED")).isZero();

            TransactionAuthenticationInfoDO authenticated = authentication(
                    "AUTH-VERIFY-001", "AUTHENTICATED", "PASSED", transactionTime, now);
            TransactionAuthenticationInfoDO lateProcessing = authentication(
                    "AUTH-VERIFY-001", "ATTEMPTED", "PROCESSING", transactionTime, now.plusSeconds(1));
            assertThat(authenticationMapper.upsertPhase(authenticated)).isEqualTo(1);
            authenticationMapper.upsertPhase(lateProcessing);
            session.commit();

            TransactionAuthenticationInfoDO stored = authenticationMapper.selectByAuthenticationInfoId(
                    "AUTH-VERIFY-001", transactionTime);
            assertThat(stored.getAuthenticationStatus()).isEqualTo("AUTHENTICATED");
            assertThat(stored.getAuthenticationResultCode()).isEqualTo("PASSED");
        }

        assertThat(direct.queryForObject("""
                SELECT request_status FROM transaction_channel_request_202603
                WHERE request_id = 'REQ-FAIL-001'
                """, String.class)).isEqualTo("FAILED");
        assertThat(direct.queryForObject("""
                SELECT transaction_status FROM transaction_operation_202603
                WHERE transaction_id = 'TX-FAIL-001'
                """, String.class)).isEqualTo("FAILED");
        assertThat(direct.queryForObject("""
                SELECT process_stage FROM transaction_order_202603
                WHERE operation_id = 'OP-FAIL-001'
                """, String.class)).isEqualTo("FINISHED");
    }

    private TransactionAuthenticationInfoDO authentication(String authenticationInfoId,
                                                            String status,
                                                            String resultCode,
                                                            LocalDateTime transactionTime,
                                                            LocalDateTime now) {
        TransactionAuthenticationInfoDO row = new TransactionAuthenticationInfoDO();
        row.setAuthenticationInfoId(authenticationInfoId);
        row.setTransactionId("TX-FAIL-001");
        row.setOperationId("OP-FAIL-001");
        row.setAuthenticationType("3DS");
        row.setAuthenticationStatus(status);
        row.setAuthenticationSource("CHANNEL");
        row.setAuthenticationResultCode(resultCode);
        row.setAuthenticationResultMessage("safe summary");
        row.setAuthenticationTime(now);
        row.setAuthenticationExtraJson("{\"phase\":\"VERIFY\"}");
        row.setTransactionDateTime(transactionTime);
        row.setTransactionUtcTime(transactionTime.minusHours(8));
        row.setTransactionTimeZone("Asia/Shanghai");
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
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
        configuration.addMapper(TransactionChannelRequestMapper.class);
        configuration.addMapper(TransactionAuthenticationInfoMapper.class);
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
        result.setCallbackUrl("https://merchant.example/callback");
        result.setPayloadJson("{}");
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
        direct.execute("CREATE TABLE transaction_authentication_info ("
                + "id BIGINT AUTO_INCREMENT NOT NULL, authentication_info_id VARCHAR(64) NOT NULL, "
                + "transaction_id VARCHAR(64) NOT NULL, operation_id VARCHAR(64), "
                + "authentication_type VARCHAR(32) NOT NULL, authentication_status VARCHAR(64), "
                + "authentication_source VARCHAR(64), three_ds_version VARCHAR(32), "
                + "three_ds_transaction_id VARCHAR(128), three_ds_server_transaction_id VARCHAR(128), "
                + "acs_transaction_id VARCHAR(128), ds_transaction_id VARCHAR(128), eci VARCHAR(8), "
                + "cavv VARCHAR(256), xid VARCHAR(256), liability_shift INT, challenge_required INT, "
                + "challenge_status VARCHAR(32), authentication_redirect_url_hash VARCHAR(64), "
                + "authentication_result_code VARCHAR(64), authentication_result_message VARCHAR(512), "
                + "authentication_time TIMESTAMP(3), authentication_extra_json VARCHAR(1024), "
                + "transaction_date_time TIMESTAMP(3) NOT NULL, transaction_utc_time TIMESTAMP(3) NOT NULL, "
                + "transaction_time_zone VARCHAR(64) NOT NULL, create_time TIMESTAMP(3) NOT NULL, "
                + "update_time TIMESTAMP(3) NOT NULL, "
                + "CONSTRAINT uk_authentication_info_id UNIQUE(authentication_info_id))");
        for (String suffix : List.of("202603", "202604")) {
            direct.execute("CREATE TABLE transaction_order_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, operation_id VARCHAR(64) NOT NULL, "
                    + "latest_transaction_id VARCHAR(64), transaction_status VARCHAR(32), "
                    + "process_stage VARCHAR(64), fail_reason_code VARCHAR(64), fail_reason_message VARCHAR(512), "
                    + "merchant_visible_message VARCHAR(512), payer_visible_message VARCHAR(512), "
                    + "channel_match_status VARCHAR(32), channel_match_result VARCHAR(32), "
                    + "next_channel_match_time TIMESTAMP(3), channel_match_fail_reason VARCHAR(512), "
                    + "last_status_time TIMESTAMP(3), update_time TIMESTAMP(3), "
                    + "transaction_date_time TIMESTAMP(3) NOT NULL, "
                    + "version INT NOT NULL, deleted INT NOT NULL)");
            direct.execute("CREATE TABLE transaction_operation_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, operation_id VARCHAR(64) NOT NULL, "
                    + "transaction_id VARCHAR(64) NOT NULL, transaction_status VARCHAR(32), process_stage VARCHAR(64), "
                    + "fail_reason_code VARCHAR(64), fail_reason_message VARCHAR(512), channel_status VARCHAR(64), "
                    + "channel_response_code VARCHAR(64), channel_response_message VARCHAR(512), "
                    + "auth_code VARCHAR(64), rrn VARCHAR(64), acquirer_reference_no VARCHAR(128), "
                    + "channel_match_status VARCHAR(32), channel_match_result VARCHAR(32), "
                    + "next_channel_match_time TIMESTAMP(3), channel_match_fail_reason VARCHAR(512), "
                    + "complete_time TIMESTAMP(3), update_time TIMESTAMP(3), "
                    + "transaction_date_time TIMESTAMP(3) NOT NULL, version INT NOT NULL, deleted INT NOT NULL)");
            direct.execute("CREATE TABLE transaction_merchant_notification_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, notify_id VARCHAR(64) NOT NULL, "
                    + "transaction_id VARCHAR(64) NOT NULL, notify_status VARCHAR(32) NOT NULL, "
                    + "callback_url VARCHAR(512), payload_json VARCHAR(4096), "
                    + "payload_json_masked VARCHAR(512), next_retry_time TIMESTAMP(3), update_time TIMESTAMP(3), "
                    + "transaction_date_time TIMESTAMP(3) NOT NULL, version INT NOT NULL, deleted INT NOT NULL)");
            direct.execute("CREATE TABLE transaction_channel_request_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, request_id VARCHAR(64) NOT NULL, "
                    + "transaction_id VARCHAR(64) NOT NULL, operation_id VARCHAR(64), request_status VARCHAR(32), "
                    + "update_time TIMESTAMP(3), transaction_date_time TIMESTAMP(3) NOT NULL, "
                    + "version INT NOT NULL, deleted INT NOT NULL)");
        }
    }
}
