package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付路由阶段 1A 的结构、迁移、权限和管理端接口契约测试。
 */
class PaymentRouteAdminSchemaContractTest {

    private static final Set<String> TABLES = Set.of(
            "payment_route_rule",
            "payment_route_strategy",
            "payment_route_method",
            "payment_route_transaction_type",
            "payment_route_candidate",
            "payment_route_change_log");

    private static final Set<String> PERMISSIONS = Set.of(
            "channel:route:list",
            "channel:route:detail",
            "channel:route:add",
            "channel:route:edit",
            "channel:route:copy",
            "channel:route:publish",
            "channel:route:disable",
            "channel:route:remove",
            "channel:route:validate",
            "channel:route:history");

    /** 两份主结构源必须同时包含路由配置表及关键唯一约束。 */
    @Test
    void schemaSourcesShouldContainRouteTablesAndDurableConstraints() throws IOException {
        String moduleSchema = readRepositoryFile("service-admin/src/main/resources/sql/admin-system-schema.sql");
        String masterSchema = readRepositoryFile("docs/sql/payment_acquiring_表结构.sql");
        String normalizedModuleSchema = normalizeSql(moduleSchema);
        String normalizedMasterSchema = normalizeSql(masterSchema);

        TABLES.forEach(table -> {
            assertThat(moduleSchema).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(masterSchema).contains("CREATE TABLE `" + table + "`");
        });
        assertThat(moduleSchema).contains("CREATE TABLE IF NOT EXISTS payment_route_rule_merchant");
        assertThat(masterSchema).contains("CREATE TABLE `payment_route_rule_merchant`");
        for (String contract : new String[]{
                "uk_payment_route_rule_code_deleted",
                "uk_payment_route_rule_merchant_deleted",
                "idx_payment_route_rule_merchant_lookup",
                "uk_payment_route_strategy_rule_type_deleted",
                "uk_payment_route_method_rule_scope_deleted",
                "uk_payment_route_transaction_rule_type_deleted",
                "uk_payment_route_candidate_mid_deleted",
                "uk_payment_route_candidate_priority_deleted",
                "idx_payment_route_method_scope_rule",
                "idx_payment_route_transaction_type_rule",
                "idx_payment_route_candidate_scope",
                "payment_method varchar(64) not null",
                "card_brand varchar(64) not null",
                "default_transaction_currency char(3) not null",
                "history_lookback_months int",
                "candidate_weight decimal(5,2)",
                "(rule_id,payment_method,card_brand,mid_config_id,deleted)",
                "(rule_id,payment_method,card_brand,candidate_priority,deleted)",
                "decimal(24,8)",
                "before_snapshot_json json",
                "after_snapshot_json json"
        }) {
            assertThat(normalizedModuleSchema).contains(contract);
            assertThat(normalizedMasterSchema).contains(contract);
        }
    }

    /** 菜单迁移、权限资源与控制器鉴权编码必须逐项一致。 */
    @Test
    void migrationPermissionsShouldMatchControllerContract() throws IOException {
        String migration = readRepositoryFile("docs/sql/20261112_02_payment_route_admin_migration.sql");
        String controller = readRepositoryFile(
                "service-admin/src/main/java/com/scott/payment/admin/api/channel/AdminPaymentRouteController.java");

        assertThat(migration).contains(
                "'admin_payment_route_v1'",
                "'/channel/route'",
                "'channel/route'",
                "'ADMIN_OPERATOR'");
        PERMISSIONS.forEach(permission -> {
            assertThat(migration).contains("'" + permission + "'");
            assertThat(controller).contains("@RequiresPermission(\"" + permission + "\")");
        });
    }

    /** 上线脚本必须具备只读前检、字段/索引后检和部分迁移可执行回滚。 */
    @Test
    void migrationSetShouldProvidePrecheckPostcheckAndSafeRollback() throws IOException {
        String precheck = readRepositoryFile("docs/sql/20261112_01_payment_route_admin_precheck.sql");
        String migration = readRepositoryFile("docs/sql/20261112_02_payment_route_admin_migration.sql");
        String postcheck = readRepositoryFile("docs/sql/20261112_03_payment_route_admin_postcheck.sql");
        String rollback = readRepositoryFile("docs/sql/20261112_04_payment_route_admin_rollback.sql");

        assertThat(precheck.toUpperCase()).doesNotContain("INSERT ", "UPDATE ", "DELETE ", "DROP ", "ALTER ");
        assertThat(precheck).contains("missing_prerequisite_table", "missing_channel_catalog_count");
        TABLES.forEach(table -> assertThat(migration).contains("CREATE TABLE IF NOT EXISTS " + table));
        assertThat(postcheck).contains(
                "missing_table",
                "missing_column",
                "missing_index",
                "invalid_permission_resource",
                "invalid_enabled_rule_count",
                "missing_admin_role_permission");
        TABLES.forEach(table -> assertThat(rollback).contains("DROP TABLE IF EXISTS " + table));
        assertThat(rollback).contains(
                "information_schema.tables",
                "payment route configuration exists; export and clear it before rollback");
    }

    /** 已执行首版 SQL 的开发库必须能通过独立增量脚本完成四类路由结构升级。 */
    @Test
    void typeSplitMigrationShouldProvideIncrementalPrecheckPostcheckAndRollback() throws IOException {
        String precheck = readRepositoryFile("docs/sql/20261112_05_payment_route_type_split_precheck.sql");
        String migration = readRepositoryFile("docs/sql/20261112_06_payment_route_type_split_migration.sql");
        String postcheck = readRepositoryFile("docs/sql/20261112_07_payment_route_type_split_postcheck.sql");
        String rollback = readRepositoryFile("docs/sql/20261112_08_payment_route_type_split_rollback.sql");

        assertThat(precheck.toUpperCase()).doesNotContain("INSERT ", "UPDATE ", "DELETE ", "DROP ", "ALTER ");
        assertThat(precheck).contains("legacy_standard_rule_count", "legacy_all_candidate_count");
        assertThat(migration).contains(
                "CREATE TABLE IF NOT EXISTS payment_route_method",
                "ADD COLUMN history_lookback_months",
                "ADD COLUMN candidate_weight",
                "SET rule_type = 'PRIORITY'",
                "INSERT IGNORE INTO payment_route_method");
        assertThat(postcheck).contains(
                "missing_method_table_count",
                "missing_column",
                "invalid_all_candidate_count",
                "missing_rule_payment_method_count",
                "rule_record.rule_type = 'WEIGHT'",
                "HAVING enabled_weight <> 100.00 OR enabled_weight IS NULL");
        assertThat(rollback).contains(
                "SET rule_type = 'STANDARD'",
                "DROP COLUMN candidate_weight",
                "DROP COLUMN history_lookback_months",
                "DROP TABLE payment_route_method");
    }

    /** 交易类型多选迁移必须保留旧单值事实，并拒绝有损回滚。 */
    @Test
    void transactionDimensionMigrationShouldBeIdempotentAndSafelyRollbackable() throws IOException {
        String precheck = readRepositoryFile("docs/sql/20261112_09_payment_route_transaction_dimension_precheck.sql");
        String migration = readRepositoryFile("docs/sql/20261112_10_payment_route_transaction_dimension_migration.sql");
        String postcheck = readRepositoryFile("docs/sql/20261112_11_payment_route_transaction_dimension_postcheck.sql");
        String rollback = readRepositoryFile("docs/sql/20261112_12_payment_route_transaction_dimension_rollback.sql");

        assertThat(precheck.toUpperCase()).doesNotContain("INSERT ", "UPDATE ", "DELETE ", "DROP ", "ALTER ");
        assertThat(precheck).contains(
                "legacy_transaction_type_count",
                "invalid_legacy_transaction_type_count",
                "legacy_obsolete_dimension_count");
        assertThat(migration).contains(
                "CREATE TABLE IF NOT EXISTS payment_route_transaction_type",
                "INSERT IGNORE INTO payment_route_transaction_type",
                "UPPER(TRIM(rule_record.transaction_type))");
        assertThat(postcheck).contains(
                "missing_transaction_type_table_count",
                "unmigrated_legacy_transaction_type_count",
                "invalid_active_transaction_type_count",
                "orphan_active_transaction_type_count",
                "duplicate_active_transaction_type_count");
        assertThat(rollback).contains(
                "HAVING COUNT(*) > 1",
                "multi-value rules exist",
                "SET rule_record.transaction_type = transaction_record.transaction_type",
                "DROP TABLE payment_route_transaction_type");
    }

    /** MID dual-currency and route scope migration must be configuration-only and reject lossy rollback. */
    @Test
    void routeMidDualCurrencyMigrationShouldProtectTransactionFacts() throws IOException {
        String precheck = readRepositoryFile("docs/sql/20261112_13_route_mid_dual_currency_precheck.sql");
        String migration = readRepositoryFile("docs/sql/20261112_14_route_mid_dual_currency_migration.sql");
        String postcheck = readRepositoryFile("docs/sql/20261112_15_route_mid_dual_currency_postcheck.sql");
        String rollback = readRepositoryFile("docs/sql/20261112_16_route_mid_dual_currency_rollback.sql");

        assertThat(precheck.toUpperCase()).doesNotContain("INSERT ", "UPDATE ", "DELETE ", "DROP ", "ALTER ");
        assertThat(precheck).contains(
                "invalid_backfill_transaction_currency_scope_count",
                "transaction_order_count",
                "transaction_currency_conversion_count",
                "protected_fact_table_name",
                "CHECKSUM TABLE");
        assertThat(precheck).doesNotContain("FIND_IN_SET(UPPER(TRIM(default_settlement_currency))");
        assertThat(migration).contains(
                "DATABASE() <> 'payment_acquiring'",
                "ADD COLUMN default_transaction_currency",
                "MID default transaction currency is outside transaction currency scope",
                "ADD COLUMN card_brand",
                "LEAVE route_mid_dual_currency",
                "CREATE TEMPORARY TABLE tmp_route_candidate_scope",
                "INSERT INTO tmp_route_candidate_scope",
                "uk_payment_route_candidate_mid_deleted");
        assertThat(migration).doesNotContain(
                "UPDATE transaction_",
                "INSERT INTO transaction_",
                "DELETE FROM transaction_",
                "ALTER TABLE transaction_");
        assertThat(postcheck).contains(
                "invalid_mid_default_transaction_currency_count",
                "invalid_active_route_method_scope_count",
                "invalid_active_route_candidate_scope_count",
                "transaction_order_count",
                "protected_fact_table_name",
                "CHECKSUM TABLE");
        assertThat(rollback).contains(
                "rollback refused: multi-brand route configurations exist",
                "rollback refused: MID transaction and settlement currencies differ");
    }

    /** 指定商户多选迁移必须无损回填旧单值，并拒绝有损回滚多商户配置。 */
    @Test
    void multiMerchantMigrationShouldBackfillAndRejectLossyRollback() throws IOException {
        String precheck = readRepositoryFile("docs/sql/20261113_17_payment_route_multi_merchant_precheck.sql");
        String migration = readRepositoryFile("docs/sql/20261113_18_payment_route_multi_merchant_migration.sql");
        String postcheck = readRepositoryFile("docs/sql/20261113_19_payment_route_multi_merchant_postcheck.sql");
        String rollback = readRepositoryFile("docs/sql/20261113_20_payment_route_multi_merchant_rollback.sql");

        assertThat(precheck.toUpperCase()).doesNotContain("INSERT ", "UPDATE ", "DELETE ", "DROP ", "ALTER ");
        assertThat(precheck).contains(
                "merchant_scope_without_merchant_count",
                "global_scope_with_merchant_count",
                "legacy_merchant_route_count");
        assertThat(migration).contains(
                "CREATE TABLE IF NOT EXISTS payment_route_rule_merchant",
                "INSERT IGNORE INTO payment_route_rule_merchant",
                "TRIM(rule_record.merchant_id)");
        assertThat(migration).doesNotContain("UPDATE transaction_", "INSERT INTO transaction_", "DELETE FROM transaction_");
        assertThat(postcheck).contains(
                "unmigrated_legacy_merchant_count",
                "merchant_rule_without_relation_count",
                "global_rule_with_relation_count",
                "orphan_rule_merchant_relation_count",
                "duplicate_active_rule_merchant_count");
        assertThat(rollback).contains(
                "HAVING COUNT(*) > 1",
                "multi-merchant rules exist",
                "SET rule_record.merchant_id = relation_record.merchant_id",
                "DROP TABLE payment_route_rule_merchant");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }

    private String normalizeSql(String sql) {
        return sql.replace("`", "").replaceAll("\\s*,\\s*", ",").toLowerCase();
    }
}
