package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCapabilityDefaultCurrencySchemaContractTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 渠道支付能力默认交易币种数据库契约测试。
 * @status : create
 */
class ChannelCapabilityDefaultCurrencySchemaContractTests {

    @Test
    void schemasAndMigrationShouldDeclareDefaultTransactionCurrency() throws IOException {
        String adminSchema = Files.readString(modulePath("src/main/resources/sql/admin-system-schema.sql"));
        String repositorySchema = Files.readString(repositoryPath("docs/sql/payment_acquiring_表结构.sql"));
        String migration = Files.readString(repositoryPath(
                "docs/sql/20260901_03_channel_capability_default_currency_migration.sql"));
        String postcheck = Files.readString(repositoryPath(
                "docs/sql/20260901_04_channel_capability_default_currency_postcheck.sql"));

        assertThat(adminSchema).contains("default_transaction_currency CHAR(3) NOT NULL");
        assertThat(repositorySchema).contains("`default_transaction_currency` char(3) NOT NULL");
        assertThat(migration).contains(
                "ADD COLUMN default_transaction_currency CHAR(3) NULL",
                "channel_code = 'MPGS'",
                "currency_code = 'USD'",
                "MODIFY COLUMN default_transaction_currency CHAR(3) NOT NULL"
        );
        assertThat(postcheck).contains(
                "invalid_default_transaction_currency_count",
                "missing_default_transaction_currency_column_count"
        );
    }

    private Path modulePath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("service-admin").resolve(relativePath);
    }

    private Path repositoryPath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("..").resolve(relativePath);
    }
}
