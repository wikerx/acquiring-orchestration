package com.scott.payment.component.db.auth.support;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class GeneratedAuthCodeTests {
    @Test
    void retriesCodeCollisionAndReturnsOnlyInsertedCode() {
        AtomicInteger attempts = new AtomicInteger();
        String generated = GeneratedAuthCode.insert("ADMIN_ROLE_", code -> {
            assertThat(code).matches("ADMIN_ROLE_[0-9A-Z]{6}");
            if (attempts.incrementAndGet() < 3) throw new DuplicateKeyException("code collision");
        });
        assertThat(generated).matches("ADMIN_ROLE_[0-9A-Z]{6}");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void stopsAfterFiveCollisionsAndDoesNotRetryOtherDatabaseFailures() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> GeneratedAuthCode.insert("ADMIN_POST_", code -> {
            attempts.incrementAndGet();
            throw new DuplicateKeyException("code collision");
        })).isInstanceOf(DuplicateKeyException.class);
        assertThat(attempts.get()).isEqualTo(5);
        attempts.set(0);
        assertThatThrownBy(() -> GeneratedAuthCode.insert("ADMIN_POST_", code -> {
            attempts.incrementAndGet();
            throw new DataAccessResourceFailureException("database unavailable");
        })).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void preservesFullMerchantNumberAndRejectsOversizeBeforeInsert() {
        for (String merchantId : new String[]{"260001", "12345678", "1".repeat(64)}) {
            String code = GeneratedAuthCode.insert("MERCHANT_" + merchantId + "_", value -> {});
            assertThat(code).startsWith("MERCHANT_" + merchantId + "_").hasSize(merchantId.length() + 16);
        }
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> GeneratedAuthCode.insert("MERCHANT_" + "1".repeat(65) + "_", code -> attempts.incrementAndGet()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(attempts.get()).isZero();
    }

    @Test
    void concurrentCreationUsesDatabaseUniqueConstraint() throws Exception {
        JdbcTemplate jdbc = database();
        var executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Callable<String>>();
            for (int i = 0; i < 100; i++) tasks.add(() -> GeneratedAuthCode.insert("MERCHANT_260001_",
                    code -> jdbc.update("INSERT INTO auth_code(code) VALUES (?)", code)));
            for (var future : executor.invokeAll(tasks)) assertThat(future.get()).startsWith("MERCHANT_260001_");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_code", Integer.class)).isEqualTo(100);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT code) FROM auth_code", Integer.class)).isEqualTo(100);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void laterFailureRollsBackGeneratedObjectWithItsOwnerTransaction() {
        JdbcTemplate jdbc = database();
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        assertThatThrownBy(() -> transaction.execute(status -> {
            GeneratedAuthCode.insert("ADMIN_ROLE_", code -> jdbc.update("INSERT INTO auth_code(code) VALUES (?)", code));
            throw new IllegalStateException("grant persistence failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_code", Integer.class)).isZero();
    }

    private JdbcTemplate database() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE auth_code (code VARCHAR(80) NOT NULL UNIQUE)");
        return jdbc;
    }
}
