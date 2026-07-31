package com.scott.payment.admin.application.risk.cache;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 风控缓存失效 outbox 数据库契约测试。
 */
class RiskCacheInvalidationPersistenceContractTests {

    @Test
    void shouldDeclareDurableRetryableOutboxInSchemaAndMigration() throws IOException {
        String schema = Files.readString(modulePath(
                "src/main/resources/sql/risk-management-schema.sql"
        ));
        String migration = Files.readString(modulePath(
                "src/main/resources/sql/risk-cache-invalidation-outbox-migration.sql"
        ));

        assertOutboxDefinition(schema);
        assertOutboxDefinition(migration);
    }

    private void assertOutboxDefinition(String sql) {
        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS risk_cache_invalidation_outbox",
                "event_id VARCHAR(64) NOT NULL",
                "publication_token VARCHAR(128) NOT NULL",
                "generation VARCHAR(128) NOT NULL",
                "event_status VARCHAR(16) NOT NULL DEFAULT 'INIT'",
                "retry_count INT NOT NULL DEFAULT 0",
                "next_retry_time DATETIME(3) NULL",
                "version INT NOT NULL DEFAULT 0",
                "UNIQUE KEY uk_risk_cache_invalidation_event (event_id)",
                "KEY idx_risk_cache_invalidation_due "
                        + "(event_status, next_retry_time, create_time, id)"
        );
    }

    private Path modulePath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("service-admin").resolve(relativePath);
    }
}
