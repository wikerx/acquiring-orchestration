package com.scott.payment.payment.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退款管理和勾兑异常数据库迁移契约。
 */
class RefundAndChannelMatchSchemaMigrationContractTests {

    @Test
    void migrationShouldCoverApprovalAndAllTransactionTableVariants() throws IOException {
        String migration = readRepositoryFile(
                "docs/sql/refund-and-channel-match-abnormal-management-v1.sql"
        );

        assertThat(migration).contains(
                "CREATE TABLE IF NOT EXISTS `transaction_refund_approval`",
                "information_schema.COLUMNS",
                "information_schema.STATISTICS",
                "CALL migration_operation_table('transaction_operation')",
                "CALL migration_operation_table('transaction_operation_202603')",
                "CALL migration_operation_table('transaction_operation_202604')",
                "CALL migration_abnormal_table('transaction_abnormal_event')",
                "CALL migration_abnormal_table('transaction_abnormal_event_202603')",
                "CALL migration_abnormal_table('transaction_abnormal_event_202604')"
        );
    }

    @Test
    void migrationShouldRenameLegacyAbnormalColumnsWithoutDuplicatingAuditColumns() throws IOException {
        String migration = readRepositoryFile(
                "docs/sql/refund-and-channel-match-abnormal-management-v1.sql"
        );

        assertThat(migration).contains(
                "'severity', 'abnormal_level'",
                "'source_table', 'source_record_type'",
                "'source_id', 'source_record_id'",
                "'description', 'abnormal_description'",
                "create_time/update_time 已存在于旧模板，不能重复新增"
        );
        assertThat(migration).doesNotContain(
                "migration_add_column(p_table, 'create_time'",
                "migration_add_column(p_table, 'update_time'"
        );
    }

    @Test
    void migrationShouldNotMutateFinancialFactsOrDeleteBusinessData() throws IOException {
        String migration = readRepositoryFile(
                "docs/sql/refund-and-channel-match-abnormal-management-v1.sql"
        );
        String upper = migration.toUpperCase();

        assertThat(upper).doesNotContain(
                "DROP TABLE",
                "DELETE FROM",
                "TRUNCATE TABLE",
                "SET TRANSACTION_AMOUNT",
                "SET LABEL_AMOUNT",
                "SET SETTLEMENT_AMOUNT",
                "SET TRANSACTION_STATUS"
        );
        assertThat(migration).contains(
                "execution_mode = CASE WHEN transaction_type IN ('REFUND', 'VOID') THEN 'CHANNEL' ELSE execution_mode END,\n" +
                        "    update_time = update_time"
        );
        assertThat(migration).contains(
                "SET SESSION group_concat_max_len = 1048576",
                "COALESCE(COLUMN_DEFAULT, '<NULL>')"
        );
    }

    @Test
    void devConfigurationShouldEnableQueryAndAutomaticCaseCreationOnly() throws IOException {
        String configuration = readRepositoryFile("docs/deployment/nacos/service-payment-dev.yaml");

        assertThat(configuration).contains(
                "enabled: ${PAYMENT_REFUND_MANAGEMENT_ENABLED:true}",
                "approval-enabled: ${PAYMENT_REFUND_APPROVAL_ENABLED:false}",
                "execution-mq-enabled: ${PAYMENT_REFUND_EXECUTION_MQ_ENABLED:false}",
                "enabled: ${PAYMENT_CHANNEL_MATCH_ABNORMAL_ENABLED:true}",
                "review-required-threshold: ${PAYMENT_CHANNEL_MATCH_ABNORMAL_THRESHOLD:12}"
        );
    }

    @Test
    void refundScopeBackfillShouldBeRepeatableAndMustNotMutateFinancialFacts() throws IOException {
        String migration = readRepositoryFile("docs/sql/refund-scope-backfill-v2.sql");
        String upper = migration.toUpperCase();

        assertThat(migration).contains(
                "CREATE TEMPORARY TABLE `tmp_refund_scope_principal`",
                "o.refund_scope IS NULL",
                "WHEN o.transaction_type = 'VOID' THEN 'VOID'",
                "WHEN o.transaction_amount = principal.original_amount THEN 'FULL'",
                "ELSE 'PARTIAL'",
                "UPDATE `transaction_operation` o",
                "UPDATE `transaction_operation_202603` o",
                "UPDATE `transaction_operation_202604` o"
        );
        assertThat(upper).doesNotContain(
                "SET O.TRANSACTION_AMOUNT",
                "SET O.TRANSACTION_CURRENCY",
                "SET O.TRANSACTION_STATUS",
                "DELETE FROM",
                "TRUNCATE TABLE"
        );
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct);
        }
        return Files.readString(Path.of("..").resolve(relativePath).normalize());
    }
}
