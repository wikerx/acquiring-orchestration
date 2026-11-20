package com.scott.payment.component.db.sharding;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.LocalGlobalIdGenerator;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.mapper.TransactionIdempotencyMapper;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService.CancellationDecision;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService.Lease;
import com.scott.payment.payment.service.impl.DefaultTransactionIdempotencyService;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用隔离 MySQL 和生产分片规则验证准备归属与两个季度交易表的事务边界。 */
@EnabledIfEnvironmentVariable(named = "REQ001_FIXTURE_MYSQL_PORT", matches = "[0-9]{1,5}")
class PaymentPreparationOwnershipMySqlTest {
    private static final String MERCHANT = "FIXTURE-GUARD-RACE";
    private static final LocalDateTime ORIGINAL_TIME = LocalDateTime.of(2026, 9, 30, 23, 59, 59, 999_000_000);
    private HikariDataSource primary;
    private DataSource shard;
    private JdbcTemplate direct;
    private JdbcTemplate logical;
    private AnnotationConfigApplicationContext context;
    private TransactionTemplate transactions;
    private TransactionIdempotencyMapper mapper;
    private DefaultTransactionIdempotencyService idempotency;
    private PaymentPreparationOwnershipService ownership;

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {
    }

    @BeforeEach
    void setup() throws Exception {
        int port = Integer.parseInt(System.getenv("REQ001_FIXTURE_MYSQL_PORT"));
        assertThat(port).isBetween(1024, 65535).isNotEqualTo(3306);
        primary = new HikariDataSource();
        primary.setJdbcUrl("jdbc:mysql://127.0.0.1:" + port
                + "/req001_fixture?sslMode=DISABLED&connectTimeout=5000&socketTimeout=15000");
        primary.setUsername("root");
        primary.setPassword("");
        primary.setMaximumPoolSize(4);
        primary.setMinimumIdle(0);
        direct = new JdbcTemplate(primary);
        assertThat(direct.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("req001_fixture");
        assertThat(direct.queryForObject("SELECT merchant_name FROM base_merchant_info WHERE merchant_id = ?",
                String.class, MERCHANT)).isEqualTo("Synthetic Guard Race");

        var properties = new TransactionShardingProperties();
        properties.setPrimaryDataSource("master");
        properties.setReplicaDataSources(List.of());
        properties.setPhysicalNodes(List.of("202603", "202604"));
        properties.setLogicTables(List.of("transaction_order", "transaction_operation"));
        var config = new TransactionShardingDataSourceConfiguration();
        shard = ShardingSphereDataSourceFactory.createDataSource(Map.of("master", primary),
                config.buildRules(properties, List.of()), config.buildSystemProperties(properties));
        logical = new JdbcTemplate(shard);
        var manager = new DataSourceTransactionManager(shard);
        transactions = new TransactionTemplate(manager);

        var mybatis = new MybatisConfiguration();
        mybatis.setMapUnderscoreToCamelCase(true);
        mybatis.setEnvironment(new Environment("req001-isolated", new SpringManagedTransactionFactory(), shard));
        mybatis.addMapper(TransactionIdempotencyMapper.class);
        mapper = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(mybatis))
                .getMapper(TransactionIdempotencyMapper.class);
        idempotency = new DefaultTransactionIdempotencyService(mapper);
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(PlatformTransactionManager.class, () -> manager);
        context.registerBean(PaymentPreparationOwnershipService.class,
                () -> new PaymentPreparationOwnershipService(mapper, idempotency, new LocalGlobalIdGenerator()));
        context.refresh();
        ownership = context.getBean(PaymentPreparationOwnershipService.class);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (context != null) {
            context.close();
        }
        if (shard instanceof AutoCloseable closeable) {
            closeable.close();
        }
        if (primary != null && !primary.isClosed()) {
            primary.close();
        }
    }

    @Test
    void takeoverKeepsFirstQuarterAndPreventsTheOldOwnershipFromCommitting() {
        var command = command();
        String key = idempotency.buildTransactionOperationKey(MERCHANT, command.getMerchantOrderId(), "PAYMENT");
        Lease previous = ownership.acquire(command, key, "PAYMENT", "Asia/Shanghai", ownership.nextCandidate()).lease();
        assertThat(reconcile(previous)).isEqualTo(CancellationDecision.RETAIN);
        assertThat(ownership.terminateUncommitted(previous)).isEqualTo(CancellationDecision.RELEASE_ALLOWED);

        command.setTransactionDateTime(ORIGINAL_TIME.plusDays(1));
        Lease next = ownership.acquire(command, key, "PAYMENT", "Asia/Shanghai", ownership.nextCandidate()).lease();
        assertThat(next.generation()).isGreaterThan(previous.generation());
        assertThat(next.candidateTransactionId()).isNotEqualTo(previous.candidateTransactionId());
        assertThat(next.transactionDateTime()).isEqualTo(ORIGINAL_TIME);
        assertThat(reconcile(previous)).isEqualTo(CancellationDecision.RELEASE_ALLOWED);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> ownership.requireCurrent(previous)))
                .isInstanceOf(ServiceException.class);

        transactions.executeWithoutResult(status -> {
            ownership.requireCurrent(next);
            storeSyntheticFacts(next, command, key);
            ownership.attach(next);
        });

        assertThat(reconcile(next)).isEqualTo(CancellationDecision.RETAIN);
        assertThat(ownership.terminateUncommitted(next)).isEqualTo(CancellationDecision.RETAIN);
        var known = ownership.acquire(command, key, "PAYMENT", "Asia/Shanghai", ownership.nextCandidate()).committedOperation();
        assertThat(known.getTransactionId()).isEqualTo(next.candidateTransactionId());
        assertThat(known.getTransactionDateTime()).isEqualTo(ORIGINAL_TIME);
        assertThat(countFor("transaction_order_202603", command.getMerchantOrderId())).isEqualTo(1);
        assertThat(countFor("transaction_order_202604", command.getMerchantOrderId())).isZero();
    }

    @Test
    void rollbackDoesNotLeaveAttachedOwnershipOrFiscalFacts() {
        var command = command();
        String key = idempotency.buildTransactionOperationKey(MERCHANT, command.getMerchantOrderId(), "PAYMENT");
        Lease lease = ownership.acquire(command, key, "PAYMENT", "Asia/Shanghai", ownership.nextCandidate()).lease();

        transactions.executeWithoutResult(status -> {
            ownership.requireCurrent(lease);
            storeSyntheticFacts(lease, command, key);
            ownership.attach(lease);
            status.setRollbackOnly();
        });

        assertThat(countFor("transaction_order_202603", command.getMerchantOrderId())).isZero();
        assertThat(ownership.readCommittedOperation(lease)).isNull();
        assertThat(reconcile(lease)).isEqualTo(CancellationDecision.RETAIN);
        assertThat(ownership.terminateUncommitted(lease)).isEqualTo(CancellationDecision.RELEASE_ALLOWED);
    }

    private void storeSyntheticFacts(Lease lease, PaymentCreateCommandDTO command, String key) {
        String tx = lease.candidateTransactionId();
        var time = lease.transactionDateTime();
        var utc = time.minusHours(8);
        assertThat(logical.update("""
                INSERT INTO transaction_order (operation_id, root_transaction_id, latest_transaction_id, merchant_id,
                  merchant_order_no, merchant_order_id, payment_method, transaction_type, transaction_status, process_stage,
                  label_currency, label_amount, label_currency_exponent, transaction_currency, transaction_amount,
                  currency_exponent, transaction_date_time, transaction_utc_time, last_status_time)
                VALUES (?, ?, ?, ?, ?, ?, 'BANK_CARD', 'PAYMENT', 'PROCESSING', 'CHANNEL_REQUESTING',
                  'USD', 1, 2, 'USD', 1, 2, ?, ?, ?)
                """, command.getMerchantOrderId(), tx, tx, MERCHANT, command.getMerchantOrderNo(),
                command.getMerchantOrderId(), time, utc, time)).isEqualTo(1);
        var operation = idempotency.newProcessingRecord("TRANSACTION_OPERATION", key, MERCHANT,
                command.getMerchantOrderNo(), command.getMerchantOrderId(), "PAYMENT", time, "Asia/Shanghai",
                command.getRequestFingerprint(), LocalDateTime.now());
        operation.setTransactionId(tx);
        operation.setOperationId(command.getMerchantOrderId());
        assertThat(mapper.insert(operation)).isEqualTo(1);
    }

    private int countFor(String table, String orderId) {
        return direct.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE merchant_id = ? AND merchant_order_id = ?",
                Integer.class, MERCHANT, orderId);
    }

    private CancellationDecision reconcile(Lease lease) {
        return ownership.reconcileCancellation(MERCHANT, lease.key(), lease.generation(),
                lease.candidateTransactionId(), lease.transactionDateTime());
    }

    private PaymentCreateCommandDTO command() {
        var command = new PaymentCreateCommandDTO();
        String orderId = "FIXTURE-" + UUID.randomUUID();
        command.setMerchantId(MERCHANT);
        command.setMerchantOrderNo(orderId);
        command.setMerchantOrderId(orderId);
        command.setTransactionDateTime(ORIGINAL_TIME);
        command.setRequestFingerprint("fixture-request-fingerprint");
        return command;
    }
}
