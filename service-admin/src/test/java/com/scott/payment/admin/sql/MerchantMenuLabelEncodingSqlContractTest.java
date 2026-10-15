package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户资料菜单与权限名称的 UTF-8 数据迁移契约测试。
 */
class MerchantMenuLabelEncodingSqlContractTest {

    @Test
    void migrationsShouldDeclareUtf8AndUseCanonicalChineseLabels() throws IOException {
        String foundation = Files.readString(modulePath(
                "src/main/resources/sql/merchant-profile-change-request-migration.sql"));
        String repair = Files.readString(modulePath(
                "src/main/resources/sql/merchant-menu-label-encoding-repair.sql"));

        assertThat(foundation).contains(
                "SET NAMES utf8mb4",
                "'merchant_info_catalog_v1', '商户信息'",
                "'merchant_profile_maintenance_v1', '资料维护'",
                "'merchant:info:view' AS permission_code",
                "'商户资料查询' AS permission_name",
                "'merchant:info:edit', '商户资料维护'");
        assertThat(repair).contains(
                "SET NAMES utf8mb4",
                "app.app_code = 'MERCHANT'",
                "WHEN 'merchant_info_catalog_v1' THEN '商户信息'",
                "WHEN 'merchant_profile_maintenance_v1' THEN '资料维护'",
                "WHEN 'merchant:info:view' THEN '商户资料查询'",
                "WHEN 'merchant:info:edit' THEN '商户资料维护'");
        assertThat(foundation + repair).doesNotContain("å•", "æˆ", "èµ", "ç»", "�");
    }

    private Path modulePath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("service-admin").resolve(relativePath);
    }
}
