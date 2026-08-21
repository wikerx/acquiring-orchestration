package com.scott.payment.admin.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 邮件模板初始化、迁移、触发变量和敏感字段的静态合同测试。
 */
class AdminEmailTemplateContractTests {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");
    private static final Pattern SQL_CODE_PATTERN = Pattern.compile("'([A-Z][A-Z0-9_]+)'");
    private static final List<String> THEME_TOKENS = List.of(
            "#F3F7FF", "#FFFFFF", "#2563EB", "#0F172A", "#DBEAFE", "#64748B"
    );
    private static final Set<String> RETIRED_TEMPLATE_CODES = Set.of(
            "ADMIN_LOGIN_OTP",
            "MERCHANT_LOGIN_OTP",
            "ADMIN_PASSWORD_RESET",
            "MERCHANT_PASSWORD_RESET",
            "MERCHANT_ONBOARDING_APPROVED",
            "MERCHANT_ONBOARDING_REJECTED",
            "MERCHANT_MFA_EXEMPT_NOTICE_COPY_1785241605402"
    );
    private static final Set<String> EXISTING_ACTIVE_TEMPLATE_CODES = Set.of(
            "ADMIN_ACCOUNT_CREATED",
            "MERCHANT_ACCOUNT_CREATED",
            "ADMIN_PASSWORD_CHANGED_BY_ADMIN",
            "MERCHANT_PASSWORD_CHANGED_BY_ADMIN",
            "API_KEY_CREATED",
            "API_KEY_RESET",
            "API_KEY_ENABLED",
            "API_KEY_DISABLED",
            "ADMIN_MFA_BIND_NOTICE",
            "ADMIN_MFA_ENABLED_NOTICE",
            "ADMIN_MFA_RESET_NOTICE",
            "ADMIN_MFA_DISABLED_NOTICE",
            "ADMIN_MFA_EXEMPT_NOTICE",
            "MERCHANT_MFA_BIND_NOTICE",
            "MERCHANT_MFA_ENABLED_NOTICE",
            "MERCHANT_MFA_RESET_NOTICE",
            "MERCHANT_MFA_DISABLED_NOTICE",
            "MERCHANT_MFA_EXEMPT_NOTICE",
            "CHANNEL_ALERT_DEFAULT"
    );
    private static final Set<String> ACTIVE_TEMPLATE_CODES = Set.of(
            "ADMIN_ACCOUNT_CREATED",
            "MERCHANT_ACCOUNT_CREATED",
            "ADMIN_PASSWORD_CHANGED_BY_ADMIN",
            "MERCHANT_PASSWORD_CHANGED_BY_ADMIN",
            "API_KEY_CREATED",
            "API_KEY_RESET",
            "API_KEY_ENABLED",
            "API_KEY_DISABLED",
            "ADMIN_MFA_BIND_NOTICE",
            "ADMIN_MFA_ENABLED_NOTICE",
            "ADMIN_MFA_RESET_NOTICE",
            "ADMIN_MFA_DISABLED_NOTICE",
            "ADMIN_MFA_EXEMPT_NOTICE",
            "MERCHANT_MFA_BIND_NOTICE",
            "MERCHANT_MFA_ENABLED_NOTICE",
            "MERCHANT_MFA_RESET_NOTICE",
            "MERCHANT_MFA_DISABLED_NOTICE",
            "MERCHANT_MFA_EXEMPT_NOTICE",
            "CHANNEL_ALERT_DEFAULT",
            "MERCHANT_FROZEN",
            "MERCHANT_UNFROZEN",
            "FEE_CONFIG_PENDING_REVIEW",
            "FEE_CONFIG_REJECTED",
            "FEE_RULE_MISSING",
            "SETTLEMENT_RATE_MISSING",
            "NEGATIVE_BALANCE_INTERNAL",
            "NEGATIVE_BALANCE_MERCHANT",
            "BALANCE_RESTORED",
            "HOLIDAY_CALENDAR_MISSING",
            "FUND_RECHARGE_POSTED",
            "FUND_RECHARGE_REJECTED",
            "MERCHANT_SOURCE_URL_APPROVED",
            "MERCHANT_SOURCE_URL_REJECTED",
            "MERCHANT_IP_WHITELIST_APPROVED",
            "MERCHANT_IP_WHITELIST_REJECTED"
    );

    private static final Set<String> RECENT_SYSTEM_TEMPLATE_CODES = Set.of(
            "FEE_CONFIG_PENDING_REVIEW", "FEE_CONFIG_REJECTED", "FEE_RULE_MISSING",
            "SETTLEMENT_RATE_MISSING", "NEGATIVE_BALANCE_INTERNAL", "NEGATIVE_BALANCE_MERCHANT",
            "BALANCE_RESTORED", "HOLIDAY_CALENDAR_MISSING", "FUND_RECHARGE_POSTED",
            "FUND_RECHARGE_REJECTED", "MERCHANT_SOURCE_URL_APPROVED",
            "MERCHANT_SOURCE_URL_REJECTED", "MERCHANT_IP_WHITELIST_APPROVED",
            "MERCHANT_IP_WHITELIST_REJECTED"
    );

    @Test
    void shouldKeepAdminTemplateVariablesAlignedWithTriggerCode() throws IOException {
        String schema = readRepositoryFile("service-admin/src/main/resources/sql/admin-system-schema.sql");
        String adminMfa = readRepositoryFile("service-admin/src/main/resources/sql/admin-mfa-management.sql");

        assertTemplateContract(schema, "ADMIN_ACCOUNT_CREATED", Set.of(
                "systemName", "userName", "loginAccount", "initialPassword", "loginUrl",
                "verifyCodeGuide", "mfaGuide"
        ));
        assertTemplateContract(schema, "ADMIN_PASSWORD_CHANGED_BY_ADMIN", Set.of(
                "systemName", "userName", "loginAccount", "temporaryPassword", "operatorName",
                "operationTime", "loginUrl"
        ));
        assertTemplateContract(adminMfa, "ADMIN_MFA_BIND_NOTICE", Set.of("loginAccount", "bindUrl", "reason"));
        assertTemplateContract(adminMfa, "ADMIN_MFA_ENABLED_NOTICE", Set.of("loginAccount", "bindUrl", "reason"));
        assertTemplateContract(adminMfa, "ADMIN_MFA_RESET_NOTICE", Set.of("loginAccount", "bindUrl", "reason"));
        assertTemplateContract(adminMfa, "ADMIN_MFA_DISABLED_NOTICE", Set.of("loginAccount", "reason"));
        assertTemplateContract(adminMfa, "ADMIN_MFA_EXEMPT_NOTICE", Set.of("loginAccount", "reason", "exemptUntil"));

        String userService = readRepositoryFile(
                "service-admin/src/main/java/com/scott/payment/admin/service/impl/AdminUserServiceImpl.java"
        );
        assertTriggerContract(userService, "ADMIN_ACCOUNT_CREATED", Set.of(
                "systemName", "userName", "loginAccount", "initialPassword", "loginUrl",
                "verifyCodeGuide", "mfaGuide"
        ));
        assertTriggerContract(userService, "ADMIN_PASSWORD_CHANGED_BY_ADMIN", Set.of(
                "systemName", "userName", "loginAccount", "temporaryPassword", "operatorName",
                "operationTime", "loginUrl"
        ));

        String mfaService = readRepositoryFile(
                "service-admin/src/main/java/com/scott/payment/admin/service/impl/AdminUserMfaServiceImpl.java"
        );
        assertThat(mfaService).contains(
                "ADMIN_MFA_BIND_NOTICE",
                "ADMIN_MFA_ENABLED_NOTICE",
                "ADMIN_MFA_RESET_NOTICE",
                "ADMIN_MFA_DISABLED_NOTICE",
                "ADMIN_MFA_EXEMPT_NOTICE"
        );
        for (String variable : Set.of("loginAccount", "bindUrl", "reason", "exemptUntil")) {
            assertThat(mfaService).contains("variables.put(\"" + variable + "\"");
        }
    }

    @Test
    void shouldKeepMerchantAndApiKeyVariablesAlignedWithTriggerCode() throws IOException {
        String schema = readRepositoryFile("service-admin/src/main/resources/sql/admin-system-schema.sql");
        String merchantMfa = readRepositoryFile(
                "service-merchant/src/main/resources/sql/merchant-mfa-management.sql"
        );
        Set<String> accountVariables = Set.of(
                "systemName", "userName", "merchantId", "merchantName", "loginAccount",
                "initialPassword", "loginUrl", "verifyCodeGuide", "mfaGuide"
        );
        Set<String> passwordChangedVariables = Set.of(
                "systemName", "userName", "merchantId", "merchantName", "loginAccount",
                "temporaryPassword", "operatorName", "operationTime", "loginUrl"
        );
        Set<String> apiKeyVariables = Set.of(
                "systemName", "merchantName", "merchantNo", "keyName", "keyLast4",
                "operatorName", "operationTime"
        );

        assertTemplateContract(schema, "MERCHANT_ACCOUNT_CREATED", accountVariables);
        assertTemplateContract(schema, "MERCHANT_PASSWORD_CHANGED_BY_ADMIN", passwordChangedVariables);
        assertTemplateContract(merchantMfa, "MERCHANT_MFA_BIND_NOTICE", Set.of(
                "merchantName", "merchantId", "loginAccount", "bindUrl", "reason"
        ));
        assertTemplateContract(merchantMfa, "MERCHANT_MFA_ENABLED_NOTICE", Set.of(
                "merchantName", "merchantId", "loginAccount", "bindUrl", "reason"
        ));
        assertTemplateContract(merchantMfa, "MERCHANT_MFA_RESET_NOTICE", Set.of(
                "merchantName", "merchantId", "loginAccount", "bindUrl", "reason"
        ));
        assertTemplateContract(merchantMfa, "MERCHANT_MFA_DISABLED_NOTICE", Set.of(
                "merchantName", "merchantId", "loginAccount", "reason"
        ));
        assertTemplateContract(merchantMfa, "MERCHANT_MFA_EXEMPT_NOTICE", Set.of(
                "merchantName", "merchantId", "loginAccount", "reason", "exemptUntil"
        ));
        for (String code : List.of("API_KEY_CREATED", "API_KEY_RESET", "API_KEY_ENABLED", "API_KEY_DISABLED")) {
            assertTemplateContract(schema, code, apiKeyVariables);
            assertThat(extractVariables(templateBlock(schema, code))).doesNotContain(
                    "apiKey", "privateKey", "jwtKey", "secret", "merchantPrivateKey", "merchantPayloadPrivateKey"
            );
        }

        String provisioningService = readRepositoryFile(
                "service-admin/src/main/java/com/scott/payment/admin/service/impl/AdminMerchantPrimaryAccountProvisioningService.java"
        );
        assertTriggerContract(provisioningService, "MERCHANT_ACCOUNT_CREATED", accountVariables);

        String merchantSystemService = readRepositoryFile(
                "service-merchant/src/main/java/com/scott/payment/merchant/service/impl/MerchantSystemServiceImpl.java"
        );
        assertTriggerContract(merchantSystemService, "MERCHANT_ACCOUNT_CREATED", accountVariables);
        assertTriggerContract(merchantSystemService, "MERCHANT_PASSWORD_CHANGED_BY_ADMIN", passwordChangedVariables);
        assertThat(merchantSystemService).contains(
                "MERCHANT_MFA_BIND_NOTICE",
                "MERCHANT_MFA_ENABLED_NOTICE",
                "MERCHANT_MFA_RESET_NOTICE",
                "MERCHANT_MFA_DISABLED_NOTICE",
                "MERCHANT_MFA_EXEMPT_NOTICE"
        );
        for (String variable : Set.of(
                "merchantName", "merchantId", "loginAccount", "bindUrl", "reason", "exemptUntil"
        )) {
            assertThat(merchantSystemService).contains("variables.put(\"" + variable + "\"");
        }

        String keyNotificationService = readRepositoryFile(
                "service-admin/src/main/java/com/scott/payment/admin/service/impl/AdminMerchantSecurityNotificationService.java"
        );
        assertThat(keyNotificationService).contains(
                "API_KEY_CREATED", "API_KEY_RESET", "API_KEY_ENABLED", "API_KEY_DISABLED"
        );
        for (String variable : apiKeyVariables) {
            assertThat(keyNotificationService).contains("variables.put(\"" + variable + "\"");
        }
        assertThat(keyNotificationService).doesNotContain(
                "variables.put(\"apiKey\"",
                "variables.put(\"privateKey\"",
                "variables.put(\"jwtKey\""
        );

        String merchantKeyNotificationService = readRepositoryFile(
                "service-merchant/src/main/java/com/scott/payment/merchant/service/impl/MerchantOpenApiKeyNotificationService.java"
        );
        assertThat(merchantKeyNotificationService).contains(
                "API_KEY_RESET", "API_KEY_ENABLED", "API_KEY_DISABLED"
        );
        for (String variable : apiKeyVariables) {
            assertThat(merchantKeyNotificationService).contains("variables.put(\"" + variable + "\"");
        }
        assertThat(merchantKeyNotificationService).doesNotContain(
                "variables.put(\"apiKey\"",
                "variables.put(\"privateKey\"",
                "variables.put(\"jwtKey\""
        );
    }

    @Test
    void shouldPhysicallyDeleteOnlyRetiredDefinitionsAndPreserveSendHistory() throws IOException {
        String migration = readRepositoryFile(
                "service-admin/src/main/resources/sql/email-template-governance-migration.sql"
        );
        int deleteStart = migration.indexOf("DELETE FROM msg_email_template");
        int deleteEnd = migration.indexOf(");", deleteStart) + 2;
        String deleteStatement = migration.substring(deleteStart, deleteEnd);

        assertThat(extractSqlCodes(deleteStatement)).containsExactlyInAnyOrderElementsOf(RETIRED_TEMPLATE_CODES);
        assertThat(migration).doesNotContain("DELETE FROM msg_email_send_record");
        assertThat(migration).contains("information_schema.COLUMNS", "information_schema.STATISTICS");
    }

    @Test
    void shouldKeepActiveTemplatesOnTheWhiteBlueThemeAndMarkPasswordsSensitive() throws IOException {
        String schema = readRepositoryFile("service-admin/src/main/resources/sql/admin-system-schema.sql");
        String migration = readRepositoryFile(
                "service-admin/src/main/resources/sql/email-template-governance-migration.sql"
        );
        String adminMfa = readRepositoryFile("service-admin/src/main/resources/sql/admin-mfa-management.sql");
        String merchantMfa = readRepositoryFile("service-merchant/src/main/resources/sql/merchant-mfa-management.sql");
        String channelAlert = readRepositoryFile(
                "service-admin/src/main/resources/sql/channel-alert-management-schema.sql"
        );

        assertTemplateContract(channelAlert, "CHANNEL_ALERT_DEFAULT", Set.of(
                "ruleName", "channelName", "channelCode", "businessType", "paymentMethod",
                "cardBrand", "ruleType", "alertLevel", "triggerTime", "triggerValue", "ruleDescription"
        ));

        assertThat(migration).contains(EXISTING_ACTIVE_TEMPLATE_CODES.toArray(String[]::new));
        for (String token : THEME_TOKENS) {
            assertThat(schema).contains(token);
            assertThat(migration).contains(token);
            assertThat(adminMfa).contains(token);
            assertThat(merchantMfa).contains(token);
            assertThat(channelAlert).contains(token);
        }

        assertThat(templateBlock(schema, "ADMIN_ACCOUNT_CREATED")).contains("[\"initialPassword\"]");
        assertThat(templateBlock(schema, "MERCHANT_ACCOUNT_CREATED")).contains("[\"initialPassword\"]");
        assertThat(schema).contains("'[\"temporaryPassword\"]', 1, 1, 2");
        assertThat(migration).contains(
                "WHEN template_code IN ('ADMIN_PASSWORD_CHANGED_BY_ADMIN', 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN') "
                        + "THEN '[\"temporaryPassword\"]'"
        );
    }

    @Test
    void shouldKeepEveryActiveTemplateBilingualAndPhysicallyDeleteOnlyRetiredBuiltIns() throws IOException {
        String migration = readRepositoryFile(
                "service-admin/src/main/resources/sql/merchant-freeze-bilingual-email-migration.sql"
        );
        Set<String> freezeVariables = Set.of(
                "systemName", "merchantName", "merchantId", "operatorName", "operationTime"
        );

        assertThat(extractVariables(templateBlock(migration, "MERCHANT_FROZEN")))
                .containsExactlyInAnyOrderElementsOf(freezeVariables);
        assertThat(extractVariables(templateBlock(migration, "MERCHANT_UNFROZEN")))
                .containsExactlyInAnyOrderElementsOf(freezeVariables);
        assertThat(migration).contains(
                "'zh-CN' locale",
                "'Merchant Frozen Notice', 'en-US'",
                "'Merchant Unfrozen Notice', 'en-US'",
                "source.locale = 'zh-CN'",
                "'en-US'",
                "resource_method = 'PUT'",
                "resource_path = '/admin/merchants/**/status'"
        ).doesNotContain("request_method = 'PUT'", "request_path = '/admin/merchants/**/status'");
        for (String token : THEME_TOKENS) {
            assertThat(migration).contains(token);
        }

        int deleteStart = migration.indexOf("DELETE FROM msg_email_template");
        int deleteEnd = migration.indexOf(");", deleteStart) + 2;
        String deleteStatement = migration.substring(deleteStart, deleteEnd);
        assertThat(extractSqlCodes(deleteStatement))
                .containsExactlyInAnyOrderElementsOf(ACTIVE_TEMPLATE_CODES);
        assertThat(deleteStatement).contains("system_builtin = 1");
        assertThat(migration).doesNotContain("DELETE FROM msg_email_send_record");
    }

    /** 近期新增系统模板必须使用蓝白主题，并提供有边界的可回滚迁移。 */
    @Test
    void shouldMigrateRecentSystemTemplatesToBlueWhiteThemeWithRollback() throws IOException {
        String migration = readRepositoryFile(
                "service-admin/src/main/resources/sql/system-email-template-blue-white-migration.sql");
        String rollback = readRepositoryFile(
                "service-admin/src/main/resources/sql/system-email-template-blue-white-rollback.sql");
        String foundation = readRepositoryFile(
                "service-admin/src/main/resources/sql/fee-account-foundation-schema.sql");
        String schema = readRepositoryFile("service-admin/src/main/resources/sql/admin-system-schema.sql");
        String accessMigration = readRepositoryFile(
                "service-admin/src/main/resources/sql/merchant-access-config-approval-migration.sql");

        assertThat(migration).contains(RECENT_SYSTEM_TEMPLATE_CODES.toArray(String[]::new));
        assertThat(migration).contains(
                "template.system_builtin = 1",
                "template.locale IN ('zh-CN', 'en-US')",
                "data-template-theme=\"vexra-blue-white-v1\"",
                "template.version_no = template.version_no + 1",
                "msg_email_template_blue_white_backup_20260820");
        assertThat(rollback).contains(
                "JOIN msg_email_template_blue_white_backup_20260820",
                "data-template-theme=\"vexra-blue-white-v1\"",
                "DROP TABLE msg_email_template_blue_white_backup_20260820");
        for (String token : THEME_TOKENS) {
            assertThat(migration).contains(token);
            assertThat(foundation).contains(token);
            assertThat(schema).contains(token);
            assertThat(accessMigration).contains(token);
        }
    }

    @Test
    void shouldDeclareMerchantOwnershipOnRoleSchemaAndMigration() throws IOException {
        String schema = readRepositoryFile("service-admin/src/main/resources/sql/admin-system-schema.sql");
        String migration = readRepositoryFile(
                "service-admin/src/main/resources/sql/email-template-governance-migration.sql"
        );

        assertThat(schema).contains(
                "merchant_id VARCHAR(32) NULL COMMENT '商户号，商户系统角色必须绑定当前商户'",
                "KEY idx_sys_role_merchant (merchant_id, status, deleted)",
                "INSERT IGNORE INTO sys_role (app_id, role_code, role_name, merchant_id"
        );
        assertThat(migration).contains(
                "ADD COLUMN merchant_id VARCHAR(32) NULL",
                "ADD INDEX idx_sys_role_merchant (merchant_id, status, deleted)",
                "SET role_row.merchant_id = merchant.merchant_id",
                "'Password Changed', 'PASSWORD_CHANGED', 'en-US'",
                "'密码变更通知', 'PASSWORD_CHANGED', 'zh-CN'"
        );
    }

    private void assertTemplateContract(String sql, String templateCode, Set<String> expectedVariables) {
        String block = templateBlock(sql, templateCode);
        assertThat(extractVariables(block)).containsExactlyInAnyOrderElementsOf(expectedVariables);
        for (String variable : expectedVariables) {
            assertThat(block).contains("\"" + variable + "\"");
        }
    }

    private void assertTriggerContract(String source, String templateCode, Set<String> variables) {
        assertThat(source).contains(templateCode);
        for (String variable : variables) {
            assertThat(source).contains("variables.put(\"" + variable + "\"");
        }
    }

    private String templateBlock(String sql, String templateCode) {
        int start = sql.indexOf("'" + templateCode + "'");
        assertThat(start).as("template %s must exist", templateCode).isGreaterThanOrEqualTo(0);
        int nextTemplate = sql.indexOf("UNION ALL SELECT '", start + templateCode.length() + 2);
        int derivedTableEnd = sql.indexOf("\n) item", start);
        int end = nextTemplate >= 0 && nextTemplate < derivedTableEnd ? nextTemplate : derivedTableEnd;
        assertThat(end).as("template %s block must terminate", templateCode).isGreaterThan(start);
        return sql.substring(start, end);
    }

    private Set<String> extractVariables(String source) {
        Set<String> variables = new LinkedHashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(source);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private Set<String> extractSqlCodes(String source) {
        Set<String> codes = new LinkedHashSet<>();
        Matcher matcher = SQL_CODE_PATTERN.matcher(source);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct);
        }
        Path fromModule = Path.of("..").resolve(relativePath).normalize();
        return Files.readString(fromModule);
    }
}
