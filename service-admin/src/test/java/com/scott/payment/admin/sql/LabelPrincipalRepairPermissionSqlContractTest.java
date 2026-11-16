package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证历史标签本金修复权限是独立、幂等、只授予超级管理员且可单独回滚的高风险权限。
 */
class LabelPrincipalRepairPermissionSqlContractTest {

    @Test
    void migrationShouldBindTwoDedicatedPermissionsToClearingMenuAndSuperAdmin() throws IOException {
        String migration = read("label-principal-repair-permission-migration.sql");

        assertThat(migration)
                .contains("admin_transaction_clearing_v1")
                .contains("admin_transaction_label_principal_repair_submit_v1")
                .contains("admin_transaction_label_principal_repair_review_v1")
                .contains("clearing:principal-repair:submit")
                .contains("clearing:principal-repair:review")
                .contains("/admin/clearing/principal-repairs")
                .contains("/admin/clearing/principal-repairs/*/review")
                .contains("role.role_code = 'SUPER_ADMIN'")
                .contains("NOT EXISTS")
                .contains("INSERT IGNORE INTO sys_role_menu")
                .contains("INSERT IGNORE INTO sys_role_permission")
                .doesNotContain("ADMIN_OPERATOR", "role.role_code IN");
    }

    @Test
    void postcheckAndRollbackShouldCoverGrantsWithoutTouchingClearingPage() throws IOException {
        String postcheck = read("label-principal-repair-permission-postcheck.sql");
        String rollback = read("label-principal-repair-permission-rollback.sql");

        assertThat(postcheck)
                .contains("missing_permission_contract_count")
                .contains("missing_super_admin_grant_count")
                .contains("unauthorized_role_grant_count")
                .contains("role.role_code <> 'SUPER_ADMIN'");
        assertThat(rollback)
                .contains("DELETE role_permission")
                .contains("DELETE role_menu")
                .contains("clearing:principal-repair:submit")
                .contains("clearing:principal-repair:review")
                .doesNotContain("menu_code = 'admin_transaction_clearing_v1'");
    }

    private String read(String fileName) throws IOException {
        return Files.readString(Path.of("src/main/resources/sql", fileName));
    }
}
