package com.scott.payment.admin.application.cache;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户安全缓存失效 Outbox 数据库契约测试。
 */
class MerchantSecurityCacheInvalidationPersistenceContractTests {

    @Test
    void shouldDeclareDurableRetryableOutboxMigration() throws IOException {
        String migration = Files.readString(modulePath(
                "src/main/resources/sql/merchant-security-cache-invalidation-outbox-migration.sql"
        ));

        assertThat(migration).contains(
                "CREATE TABLE IF NOT EXISTS merchant_security_cache_invalidation_outbox",
                "event_id VARCHAR(64) NOT NULL",
                "cache_name VARCHAR(64) NOT NULL",
                "business_key VARCHAR(128) NOT NULL",
                "gate_token VARCHAR(128) NOT NULL",
                "event_status VARCHAR(16) NOT NULL DEFAULT 'INIT'",
                "retry_count INT NOT NULL DEFAULT 0",
                "next_retry_time DATETIME(3) NULL",
                "version INT NOT NULL DEFAULT 0",
                "UNIQUE KEY uk_merchant_security_cache_event (event_id)",
                "KEY idx_merchant_security_cache_due "
                        + "(event_status, next_retry_time, create_time, id)"
        );
    }

    private Path modulePath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("service-admin").resolve(relativePath);
    }
}
