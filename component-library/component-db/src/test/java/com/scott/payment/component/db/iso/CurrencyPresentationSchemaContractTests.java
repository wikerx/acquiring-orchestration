package com.scott.payment.component.db.iso;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 币种展示字段、迁移、检查和回滚脚本契约测试；只读取脚本，不执行数据库操作。
 */
class CurrencyPresentationSchemaContractTests {

    @Test
    void schemasAndMigrationShouldDeclareControlledCurrencyIconKey() throws IOException {
        String schema = readRepositoryFile("docs/sql/payment_acquiring_表结构.sql");
        String seedSchema = readRepositoryFile("docs/sql/payment_acquiring_基础数据.sql");
        String migration = readRepositoryFile("docs/sql/20260913_01_currency_icon_key_migration.sql");
        String postcheck = readRepositoryFile("docs/sql/20260913_02_currency_icon_key_postcheck.sql");
        String rollback = readRepositoryFile("docs/sql/20260913_03_currency_icon_key_rollback.sql");

        assertThat(schema).contains("`icon_key` varchar(64) DEFAULT NULL");
        assertThat(seedSchema).contains(
                "`icon_key` varchar(64) DEFAULT NULL",
                "WHEN currency_row.alpha3_code = 'USD' THEN 'flag:US'",
                "WHEN currency_row.alpha3_code = 'CNY' THEN 'flag:CN'",
                "THEN CONCAT('currency:', currency_row.alpha3_code)"
        );
        assertThat(migration).contains(
                "ADD COLUMN icon_key VARCHAR(64) NULL",
                "WHEN currency_row.alpha3_code = 'EUR' THEN 'flag:EU'",
                "HAVING COUNT(*) = 1",
                "ELSE NULL"
        );
        assertThat(migration.toLowerCase()).doesNotContain("http://", "https://", "<img");
        assertThat(postcheck).contains(
                "missing_currency_icon_key_column_count",
                "invalid_currency_icon_key_count",
                "mismatched_currency_avatar_count"
        );
        assertThat(rollback).contains("ALTER TABLE base_iso_currency DROP COLUMN icon_key");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct);
        }
        return Files.readString(Path.of("../..").resolve(relativePath).normalize());
    }
}
