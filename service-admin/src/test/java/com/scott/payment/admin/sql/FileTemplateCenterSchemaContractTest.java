package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 模板中心数据库、权限、不可变约束与回滚合同。 */
class FileTemplateCenterSchemaContractTest {

    @Test
    void shouldDefineThreeTableModelAndImmutableAssets() throws IOException {
        String migration = read("system-file-template-center-migration.sql");

        assertThat(migration).contains(
                "CREATE TABLE IF NOT EXISTS sys_file_template (",
                "CREATE TABLE IF NOT EXISTS sys_file_template_version (",
                "CREATE TABLE IF NOT EXISTS sys_file_template_asset (",
                "UNIQUE KEY uk_sys_file_template_version_request (template_id, request_id)",
                "UNIQUE KEY uk_sys_file_template_asset_locale (template_version_id, locale)",
                "trg_sys_file_template_stable_identity",
                "trg_sys_file_template_version_immutable",
                "trg_sys_file_template_asset_immutable_update",
                "trg_sys_file_template_asset_immutable_delete",
                "template asset is immutable",
                "template asset cannot be deleted");
    }

    @Test
    void shouldRegisterMenuAndSeparateConsumerPermissions() throws IOException {
        String migration = read("system-file-template-center-migration.sql");

        assertThat(migration).contains(
                "admin_system_file_template_center_v1",
                "system:file-template:list",
                "system:file-template:upload",
                "system:file-template:publish",
                "system:file-template:rollback",
                "system:file-template:download",
                "merchant:file-template:download",
                "admin_permission_code",
                "merchant_permission_code",
                "owner_system = 'SHARED' AND admin_permission_code IS NOT NULL AND merchant_permission_code IS NOT NULL",
                "vexra-template-files");
    }

    @Test
    void shouldProvidePrecheckPostcheckAndDataSafeRollback() throws IOException {
        String precheck = read("system-file-template-center-precheck.sql");
        String postcheck = read("system-file-template-center-postcheck.sql");
        String rollback = read("system-file-template-center-rollback.sql");

        assertThat(precheck).contains(
                "admin_system_catalog_missing",
                "admin_system_catalog_ambiguous",
                "menu.menu_code IN ('system_manage', 'admin_system_catalog_v3', 'admin_system_center')",
                "sys_file_template_asset");
        assertThat(postcheck).contains(
                "admin_template_menu_parent",
                "invalid_current_pointer",
                "published_version_missing_locale",
                "invalid_permission_scope");
        assertThat(rollback).contains(
                "assert_template_center_empty",
                "template center contains data; archive or migrate assets before rollback",
                "DROP TRIGGER IF EXISTS trg_sys_file_template_asset_immutable_delete");
    }

    @Test
    void shouldDeclareDedicatedTemplateBucketInSharedConfiguration() throws IOException {
        Path direct = Path.of("docs/deployment/nacos/common-dev.yaml");
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(direct).normalize();
        assertThat(Files.readString(path)).contains(
                "template-bucket: ${OBJECT_STORAGE_TEMPLATE_BUCKET:vexra-template-files}");
    }

    private String read(String fileName) throws IOException {
        Path direct = Path.of("src/main/resources/sql", fileName);
        Path path = Files.exists(direct) ? direct : Path.of("service-admin").resolve(direct);
        return Files.readString(path);
    }
}
