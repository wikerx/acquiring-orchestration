package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 历史标签本金差额调整权限迁移合同。
 */
class SettlementPrincipalCorrectionPermissionSqlContractTest {

    @Test
    void migrationShouldCreateThreeHiddenSuperAdminOnlyPermissions() throws IOException {
        String migration = read("service-admin/src/main/resources/sql/settlement-principal-correction-permission-migration.sql");

        assertThat(migration).contains(
                "settlement:principal-correction:submit",
                "settlement:principal-correction:approve",
                "settlement:principal-correction:reject",
                "admin_settlement_reversal_order_v1",
                "menu.visible = 0",
                "role.role_code = 'SUPER_ADMIN'");
    }

    @Test
    void postcheckAndRollbackShouldCoverAllPermissionFacts() throws IOException {
        String postcheck = read("service-admin/src/main/resources/sql/settlement-principal-correction-permission-postcheck.sql");
        String rollback = read("service-admin/src/main/resources/sql/settlement-principal-correction-permission-rollback.sql");

        assertThat(postcheck).contains(
                "missing_principal_correction_permission_count",
                "missing_principal_correction_super_admin_grant_count",
                "unauthorized_principal_correction_role_grant_count");
        assertThat(rollback).contains(
                "settlement:principal-correction:submit",
                "settlement:principal-correction:approve",
                "settlement:principal-correction:reject");
    }

    private String read(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }
}
