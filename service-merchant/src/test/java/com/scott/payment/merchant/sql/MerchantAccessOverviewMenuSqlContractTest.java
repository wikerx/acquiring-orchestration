package com.scott.payment.merchant.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户支付接入概览菜单脚本契约，保护菜单、接口权限和默认授权的一致性。
 */
class MerchantAccessOverviewMenuSqlContractTest {

    @Test
    void migrationShouldRegisterOverviewPermissionIdempotently() throws IOException {
        String migration = readRepositoryFile(
                "service-merchant/src/main/resources/sql/merchant-access-overview-menu.sql");
        String controller = readRepositoryFile(
                "service-merchant/src/main/java/com/scott/payment/merchant/api/access/"
                        + "MerchantAccessOverviewController.java");

        assertThat(migration).contains(
                "START TRANSACTION", "COMMIT", "NOT EXISTS",
                "app.app_code = 'MERCHANT'",
                "merchant_access_config_catalog_v1",
                "merchant_access_overview_v1",
                "/access-config/overview",
                "access-config/overview",
                "merchant:access-config:overview:view",
                "'GET', '/merchant/access-config/overview'",
                "INSERT IGNORE INTO sys_role_menu",
                "INSERT IGNORE INTO sys_role_permission",
                "INSERT IGNORE INTO sys_merchant_menu_grant",
                "INSERT IGNORE INTO sys_merchant_permission_grant",
                "role.role_code = 'MERCHANT_ADMIN'");
        assertThat(controller).contains(
                "@RequiresPermission(\"merchant:access-config:overview:view\")");
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
