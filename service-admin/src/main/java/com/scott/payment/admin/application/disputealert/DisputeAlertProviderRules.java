package com.scott.payment.admin.application.disputealert;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.scott.payment.admin.application.disputealert.DisputeAlertRules.invalid;
import static com.scott.payment.admin.application.disputealert.DisputeAlertRules.trim;

/** 按平台、对接用途和接口版本约束账号配置，待确认的字段可暂留空。 */
final class DisputeAlertProviderRules {

    private static final Set<String> PROVIDERS = Set.of("AUSTREME", "NUVEI", "CHARGEFLOW", "CHARGEBACKHIT");

    private DisputeAlertProviderRules() {
    }

    static boolean supported(String code) {
        return PROVIDERS.contains(code);
    }

    static Configuration configuration(String code, Map<String, Object> settings, boolean enabling) {
        var form = new Form(settings == null ? Map.of() : settings, enabling);
        switch (code) {
            case "AUSTREME" -> austreme(form);
            case "NUVEI" -> nuvei(form);
            case "CHARGEFLOW" -> chargeflow(form);
            case "CHARGEBACKHIT" -> chargebackhit(form);
            default -> throw invalid("该平台使用现有的接入账号维护方式");
        }
        return form.complete();
    }

    private static void austreme(Form form) {
        String delivery = form.choice("deliveryMethod", false, "httpsPush", "sftp", "api");
        String auth = "sftp".equals(delivery)
                ? form.choice("authMethod", false, "sftpPassword", "sshPrivateKey")
                : form.choice("authMethod", false, "apiKey");
        if ("httpsPush".equals(delivery)) form.url("notificationUrl", false);
        if ("sftp".equals(delivery)) {
            form.text("sftpHost", false);
            form.text("sftpPort", false);
            form.text("sftpDirectory", false);
            form.text("sftpUsername", false);
        }
        if (auth != null) form.secret(auth, false);
    }

    private static void nuvei(Form form) {
        List<String> usage = form.choices("usage", true, "dmn", "ethocaOutcome");
        if (usage.contains("ethocaOutcome")) {
            form.text("merchantId", true);
            form.text("merchantSiteId", true);
        }
        if (usage.contains("dmn")) {
            form.url("dmnUrl", true);
            form.choices("dmnEvents", true, "ethocaAlert", "externalEthoca", "externalRdr");
            if (form.choice("dmnScope", false, "client", "multiClient") != null) form.text("dmnScopeId", true);
        }
        if (!usage.isEmpty()) form.secret("merchantSecretKey", true);
    }

    private static void chargeflow(Form form) {
        String version = form.choice("apiVersion", true, "2025-04-01", "v3");
        if (version == null) return;
        boolean legacy = "2025-04-01".equals(version);
        List<String> usage = legacy ? form.choices("usage", true, "api", "signedApi", "webhook")
                : form.choices("usage", true, "api", "webhook");
        boolean signed = usage.contains("signedApi");
        boolean webhook = usage.contains("webhook");
        if (signed && usage.contains("api")) throw invalid("请选择一种 Chargeflow API 请求认证方式");
        if (webhook) {
            form.url("webhookUrl", true);
            form.choices("webhookEvents", true, "alerts.created", "alerts.updated", "alerts.transaction.linked");
        }
        if (signed || usage.contains("api")) form.secret(legacy ? "apiAccessKey" : "bearerKey", true);
        if (legacy && (signed || webhook)) form.secret("apiSecretKey", true);
        if (!legacy && webhook) form.secret("webhookSigningSecret", true);
    }

    private static void chargebackhit(Form form) {
        List<String> usage = form.choices("usage", true, "api", "webhook");
        if (usage.contains("webhook")) form.url("notificationUrl", true);
        if (!usage.isEmpty()) {
            form.secret("publicKey", true);
            form.secret("secretKey", true);
        }
    }

    record Configuration(Map<String, Object> values, Set<String> credentialFields, Set<String> requiredCredentials) {

        Map<String, String> updates(Map<String, String> credentials) {
            if (credentials == null) return Map.of();
            Map<String, String> updates = new LinkedHashMap<>();
            credentials.forEach((field, raw) -> {
                if (!credentialFields.contains(field)) throw invalid("凭据字段不适用于所选对接方式");
                String value = trim(raw);
                if (value == null) return;
                if (value.length() > 16384) throw invalid("接入凭据超过允许的长度");
                updates.put(field, value);
            });
            return updates;
        }

        void validateCredentials(Set<String> available) {
            if (!available.containsAll(requiredCredentials)) {
                throw invalid("请先补齐当前对接方式所需的接入凭据");
            }
        }
    }

    private static final class Form {

        private final Map<String, Object> input;
        private final boolean enabling;
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final Set<String> fields = new LinkedHashSet<>();
        private final Set<String> credentialFields = new LinkedHashSet<>();
        private final Set<String> requiredCredentials = new LinkedHashSet<>();

        private Form(Map<String, Object> input, boolean enabling) {
            this.input = input;
            this.enabling = enabling;
        }

        private String text(String field, boolean required) {
            return text(field, required, 120);
        }

        private String text(String field, boolean required, int limit) {
            fields.add(field);
            Object raw = input.get(field);
            if (raw != null && !(raw instanceof String)) throw invalid("接入配置字段类型无效");
            String value = trim((String) raw);
            if (value == null) require(field, required);
            else {
                if (value.length() > limit) throw invalid("接入配置字段长度超过限制");
                values.put(field, value);
            }
            return value;
        }

        private String choice(String field, boolean required, String... options) {
            String value = text(field, required);
            if (value != null && !List.of(options).contains(value)) throw invalid("接入配置选项无效");
            return value;
        }

        private List<String> choices(String field, boolean required, String... options) {
            fields.add(field);
            Object raw = input.get(field);
            if (raw == null || raw instanceof List<?> list && list.isEmpty()) {
                require(field, required);
                return List.of();
            }
            if (!(raw instanceof List<?> list) || list.size() > options.length || !List.of(options).containsAll(list)) {
                throw invalid("接入配置选项无效");
            }
            List<String> selected = list.stream().map(String.class::cast).distinct().toList();
            values.put(field, selected);
            return selected;
        }

        private void url(String field, boolean required) {
            String value = text(field, required, 250);
            if (value == null) return;
            // 普通参数会进入详情响应。即使保存草稿，也不允许在地址中混入凭据或查询参数。
            if (value.contains("@") || value.contains("?") || value.contains("#")) throw invalid("接收地址须为不含凭据及查询参数的 HTTPS 地址");
            if (!enabling) return;
            URI address;
            try {
                address = URI.create(value);
            } catch (IllegalArgumentException ignored) {
                throw invalid("接收地址格式无效");
            }
            if (!"https".equalsIgnoreCase(address.getScheme()) || address.getHost() == null || address.getPort() > 65535) {
                throw invalid("接收地址须为有效的 HTTPS 地址");
            }
        }

        private void secret(String field, boolean required) {
            credentialFields.add(field);
            if (enabling && required) requiredCredentials.add(field);
        }

        private void require(String field, boolean required) {
            if (enabling && required) {
                String label = switch (field) {
                    case "usage" -> "对接用途";
                    case "apiVersion" -> "API 版本";
                    case "merchantId" -> "Merchant ID";
                    case "merchantSiteId" -> "Merchant Site ID";
                    case "dmnEvents", "webhookEvents" -> "订阅事件";
                    case "dmnScopeId" -> "Client / Multi-client ID";
                    default -> "接收地址";
                };
                throw invalid("启用前请补齐" + label);
            }
        }

        private Configuration complete() {
            if (!fields.containsAll(input.keySet())) throw invalid("接入配置包含与当前对接方式无关的字段");
            return new Configuration(Map.copyOf(values), Set.copyOf(credentialFields), Set.copyOf(requiredCredentials));
        }
    }
}
