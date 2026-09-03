package com.scott.payment.admin.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRoleDataScopeMapperContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 锁定 Admin 商户数据范围查询的有效角色、应用隔离和索引前提。
 * @status : create
 */
class AdminRoleDataScopeMapperContractTest {

    @Test
    void merchantScopeQueryShouldJoinWithinAppAndIgnoreInactiveOrDeletedAssignments() throws Exception {
        Select select = AdminRoleDataScopeMapper.class
                .getDeclaredMethod("selectActiveMerchantScopes", Long.class, Long.class)
                .getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertThat(sql).contains(
                "FROM sys_account_role account_role",
                "role_row.app_id = account_role.app_id",
                "role_row.id = account_role.role_id",
                "role_row.status = 1",
                "role_row.deleted = 0",
                "scope_row.app_id = role_row.app_id",
                "scope_row.role_id = role_row.id",
                "scope_row.scope_type = 'MERCHANT'",
                "scope_row.deleted = 0",
                "account_role.app_id = #{appId}",
                "account_role.account_id = #{accountId}",
                "account_role.deleted = 0")
                .doesNotContain("${");
    }

    @Test
    void adminSchemaShouldKeepRoleScopeLookupIndexes() throws IOException {
        String schema = readRepositoryFile("service-admin/src/main/resources/sql/admin-system-schema.sql");

        assertThat(schema).contains(
                "KEY idx_sys_account_role_account (app_id, account_id, deleted)",
                "KEY idx_sys_role_app_status (app_id, status, deleted)",
                "KEY idx_sys_role_scope_role (app_id, role_id, scope_type, deleted)");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IllegalStateException(relativePath + " is missing");
    }
}
