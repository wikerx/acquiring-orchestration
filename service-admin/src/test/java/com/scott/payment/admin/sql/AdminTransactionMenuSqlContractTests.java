package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理端交易菜单和商户支付接入菜单的 SQL 契约测试。
 */
class AdminTransactionMenuSqlContractTests {

    private static final Set<String> REFUND_PERMISSIONS = Set.of(
            "transaction:refund:list",
            "transaction:refund:detail",
            "transaction:refund:export",
            "transaction:refund:approve",
            "transaction:refund:reject"
    );

    private static final Set<String> MATCH_ABNORMAL_PERMISSIONS = Set.of(
            "transaction:match-abnormal:list",
            "transaction:match-abnormal:detail",
            "transaction:match-abnormal:export",
            "transaction:match-abnormal:assign",
            "transaction:match-abnormal:requery",
            "transaction:match-abnormal:batch-requery",
            "transaction:match-abnormal:resolve"
    );

    private static final Set<String> MERCHANT_REFUND_PERMISSIONS = Set.of(
            "merchant:transaction:refund:list",
            "merchant:transaction:refund:detail",
            "merchant:transaction:refund:export"
    );

    @Test
    void refundAndMatchAbnormalMenusShouldCoverControllerPermissions() throws IOException {
        String schema = readRepositoryFile("service-admin/src/main/resources/sql/admin-system-schema.sql");
        String migration = readRepositoryFile(
                "service-admin/src/main/resources/sql/refund-channel-match-abnormal-menu.sql"
        );
        String refundController = readRepositoryFile(
                "service-admin/src/main/java/com/scott/payment/admin/api/transaction/AdminRefundController.java"
        );
        String matchController = readRepositoryFile(
                "service-admin/src/main/java/com/scott/payment/admin/api/transaction/AdminChannelMatchAbnormalController.java"
        );

        assertThat(schema).contains("admin_transaction_refund_v1", "admin_transaction_match_abnormal_v1");
        assertThat(migration).contains(
                "START TRANSACTION",
                "COMMIT",
                "role.role_code = 'SUPER_ADMIN'",
                "NOT EXISTS",
                "INSERT IGNORE INTO sys_role_menu",
                "INSERT IGNORE INTO sys_role_permission"
        );
        for (String permission : REFUND_PERMISSIONS) {
            assertThat(schema).contains(permission);
            assertThat(migration).contains(permission);
            assertThat(refundController).contains("@RequiresPermission(\"" + permission + "\")");
        }
        for (String permission : MATCH_ABNORMAL_PERMISSIONS) {
            assertThat(schema).contains(permission);
            assertThat(migration).contains(permission);
            assertThat(matchController).contains("@RequiresPermission(\"" + permission + "\")");
        }

        assertThat(migration.toUpperCase()).doesNotContain("DELETE FROM", "DROP TABLE", "TRUNCATE TABLE");
    }

    @Test
    void merchantPaymentIntegrationNamesShouldBeAlignedInBaselineAndMigration() throws IOException {
        String schema = readRepositoryFile("service-admin/src/main/resources/sql/admin-system-schema.sql");
        String migration = readRepositoryFile(
                "service-admin/src/main/resources/sql/merchant-payment-integration-menu.sql"
        );

        assertThat(schema).contains(
                "'merchant_access_config_catalog_v1', '支付接入管理'",
                "'merchant_source_url_v1', '店铺网址'",
                "'merchant_ip_whitelist_v1', 'IP 白名单'",
                "'merchant:access-config:source-url:list', '店铺网址查询'"
        );
        assertThat(migration).contains(
                "app.app_code = 'MERCHANT'",
                "WHEN 'merchant_access_config_catalog_v1' THEN '支付接入管理'",
                "WHEN 'merchant_source_url_v1' THEN '店铺网址'",
                "WHEN 'merchant_ip_whitelist_v1' THEN 'IP 白名单'"
        );
        assertThat(migration.toUpperCase()).doesNotContain("DELETE FROM", "DROP TABLE", "TRUNCATE TABLE");
    }

    @Test
    void merchantRefundMenuShouldBeAvailableInBaselineAndIdempotentMigration() throws IOException {
        String schema = readRepositoryFile("service-admin/src/main/resources/sql/admin-system-schema.sql");
        String migration = readRepositoryFile(
                "service-merchant/src/main/resources/sql/merchant-refund-menu.sql"
        );
        String controller = readRepositoryFile(
                "service-merchant/src/main/java/com/scott/payment/merchant/api/transaction/MerchantRefundController.java"
        );

        assertThat(schema).contains(
                "merchant_transaction_refund_v1",
                "merchant_transaction_refund_detail_v1",
                "merchant_transaction_refund_export_v1"
        );
        assertThat(migration).contains(
                "START TRANSACTION",
                "COMMIT",
                "app.app_code = 'MERCHANT'",
                "merchant_transaction_catalog_v1",
                "INSERT IGNORE INTO sys_role_menu",
                "INSERT IGNORE INTO sys_role_permission",
                "INSERT IGNORE INTO sys_merchant_menu_grant",
                "INSERT IGNORE INTO sys_merchant_permission_grant"
        );
        for (String permission : MERCHANT_REFUND_PERMISSIONS) {
            assertThat(schema).contains(permission);
            assertThat(migration).contains(permission);
            assertThat(controller).contains("@RequiresPermission(\"" + permission + "\")");
        }

        assertThat(migration.toUpperCase()).doesNotContain("DELETE FROM", "DROP TABLE", "TRUNCATE TABLE");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct);
        }
        return Files.readString(Path.of("..").resolve(relativePath).normalize());
    }
}
