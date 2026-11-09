package com.scott.payment.merchant.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户交易通知记录菜单迁移契约，保护查询、详情、人工重发权限和默认授权的幂等行为。
 */
class MerchantTransactionNotificationMenuSqlContractTest {

    /**
     * 菜单脚本必须匹配控制器权限，并把人工重发作为独立按钮权限注册。
     */
    @Test
    void migrationShouldGrantNotificationPermissionsIdempotently() throws IOException {
        String migration = readRepositoryFile(
                "service-merchant/src/main/resources/sql/merchant-transaction-notification-menu.sql");
        String controller = readRepositoryFile(
                "service-merchant/src/main/java/com/scott/payment/merchant/api/transaction/"
                        + "MerchantTransactionNotificationController.java");

        assertThat(migration).contains(
                "START TRANSACTION", "COMMIT", "NOT EXISTS",
                "app.app_code = 'MERCHANT'",
                "merchant_transaction_catalog_v1",
                "merchant_transaction_notification_v1",
                "merchant_transaction_notification_detail_v1",
                "merchant_transaction_notification_retry_v1",
                "/transaction/notification",
                "transaction/notification",
                "merchant:transaction:notification:list",
                "merchant:transaction:notification:detail",
                "merchant:transaction:notification:retry",
                "'重发交易通知', 'BUTTON', 'POST', '/merchant/transactions/notifications/retry'",
                "INSERT IGNORE INTO sys_role_menu",
                "INSERT IGNORE INTO sys_role_permission",
                "INSERT IGNORE INTO sys_merchant_menu_grant",
                "INSERT IGNORE INTO sys_merchant_permission_grant",
                "role.role_code = 'MERCHANT_ADMIN'");
        assertThat(controller).contains(
                "@RequiresPermission(\"merchant:transaction:notification:list\")",
                "@RequiresPermission(\"merchant:transaction:notification:detail\")",
                "@RequiresPermission(\"merchant:transaction:notification:retry\")");
        assertThat(migration).doesNotContain(
                "service-admin",
                "/internal/");
        assertThat(migration.toUpperCase()).doesNotContain(
                "DELETE FROM", "DROP TABLE", "TRUNCATE TABLE");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct);
        }
        return Files.readString(Path.of("..").resolve(relativePath).normalize());
    }
}
