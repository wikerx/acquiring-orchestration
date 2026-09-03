package com.scott.payment.merchant.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSettlementMenuSqlContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Merchant 结算账单菜单迁移契约，保护本地只读接口、路由及默认授权边界。
 * @status : create
 */
class MerchantSettlementMenuSqlContractTest {

    private static final Set<String> READ_PERMISSIONS = Set.of(
            "merchant:settlement:batch:list",
            "merchant:settlement:batch:detail",
            "merchant:settlement:batch:export",
            "merchant:settlement:transaction-item:list",
            "merchant:settlement:transaction-item:export",
            "merchant:settlement:reserve-item:list",
            "merchant:settlement:reserve-item:export");

    @Test
    void migrationShouldMatchLocalControllerAndGrantOnlyMerchantAdminRoles() throws IOException {
        String migration = readRepositoryFile(
                "service-merchant/src/main/resources/sql/merchant-settlement-menu.sql");
        String controller = readRepositoryFile(
                "service-merchant/src/main/java/com/scott/payment/merchant/api/settlement/"
                        + "MerchantSettlementController.java");

        assertThat(migration).contains(
                "START TRANSACTION", "COMMIT", "NOT EXISTS",
                "app.app_code = 'MERCHANT'",
                "merchant_finance_catalog_v1",
                "'finance/settlement'", "'finance/reserve'",
                "menu.menu_name = BINARY item.menu_name",
                "INSERT IGNORE INTO sys_role_menu",
                "INSERT IGNORE INTO sys_role_permission",
                "INSERT IGNORE INTO sys_merchant_menu_grant",
                "INSERT IGNORE INTO sys_merchant_permission_grant",
                "role.role_code = 'MERCHANT_ADMIN'");
        READ_PERMISSIONS.forEach(permission -> {
            assertThat(migration).contains(permission);
            assertThat(controller).contains("@RequiresPermission(\"" + permission + "\")");
        });
        assertThat(migration.indexOf(") item\nJOIN sys_menu parent"))
                .isGreaterThan(migration.indexOf("FROM sys_app app", migration.indexOf("BUTTON")));
        assertThat(migration).doesNotContain("service-admin", "/internal/");
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
