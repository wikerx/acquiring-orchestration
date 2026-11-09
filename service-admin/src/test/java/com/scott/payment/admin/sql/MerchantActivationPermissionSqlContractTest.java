package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户激活权限迁移契约，保护幂等性、最小授权和可回滚边界。
 */
class MerchantActivationPermissionSqlContractTest {

    @Test
    void migrationShouldGrantDedicatedPermissionOnlyToSuperAdmin() throws IOException {
        String migration = readRepositoryFile(
                "service-admin/src/main/resources/sql/merchant-activation-permission-migration.sql");
        String rollback = readRepositoryFile(
                "service-admin/src/main/resources/sql/merchant-activation-permission-rollback.sql");

        assertThat(migration).contains(
                "START TRANSACTION", "COMMIT", "NOT EXISTS", "INSERT IGNORE",
                "app_code = 'ADMIN'", "menu_code = 'merchant_info_manage'",
                "'merchant:info:activate'", "'POST', '/admin/merchants/*/activate'",
                "role.role_code = 'SUPER_ADMIN'");
        assertThat(migration).doesNotContain("role.role_code IN", "ADMIN_OPERATOR");
        assertThat(rollback).contains(
                "permission_code = 'merchant:info:activate'",
                "menu_code = 'merchant_info_activate'",
                "DELETE FROM sys_role_permission", "DELETE FROM sys_role_menu",
                "DELETE FROM sys_permission", "DELETE FROM sys_menu");
        assertThat(rollback).doesNotContain("merchant:info:changeStatus");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }
}
