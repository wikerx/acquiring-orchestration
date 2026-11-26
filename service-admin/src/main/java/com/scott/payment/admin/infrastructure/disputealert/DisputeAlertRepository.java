package com.scott.payment.admin.infrastructure.disputealert;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.admin.dto.risk.DisputeAlertDtos.*;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 预警配置及加密凭据的数据访问，所有修改由配置应用事务管理。 */
@Repository
public class DisputeAlertRepository {

    private static final String CURRENT_CREDENTIAL_JOIN = """
            LEFT JOIN dispute_alert_credential_version current_keys
              ON current_keys.account_id = a.id AND current_keys.version = a.credential_version
              AND current_keys.public_key_cipher IS NOT NULL AND current_keys.private_key_cipher IS NOT NULL
            """;
    private static final String PROVIDER_CREDENTIAL_JOIN = """
            LEFT JOIN dispute_alert_credential_version provider_keys
              ON provider_keys.account_id = a.id AND provider_keys.version = a.credential_version
            """;
    private static final String ACCOUNT_COLUMNS = """
            a.id, a.platform_id, p.code platform_code, p.name platform_name, p.enabled platform_enabled,
            a.environment, a.merchant_number, a.status, a.credential_version,
            (current_keys.version IS NOT NULL) credentials_registered, a.version, a.update_time
            """;
    private static final String ACCOUNT_FROM = """
            FROM dispute_alert_account a JOIN dispute_alert_platform p ON p.id = a.platform_id
            """ + CURRENT_CREDENTIAL_JOIN;
    private static final String SOURCE_COLUMNS = """
            ap.id account_product_id, a.id account_id, p.id platform_id, p.name platform_name, p.code platform_code,
            a.environment, a.merchant_number, a.status account_status, ap.status product_status,
            p.enabled platform_enabled, pp.enabled platform_product_enabled, pp.id platform_product_id,
            pp.product, pp.coverage, current_keys.version credential_version,
            a.provider_activation_validated
            """;
    private static final String SOURCE_FROM = """
            FROM dispute_alert_account_product ap
            JOIN dispute_alert_account a ON a.id = ap.account_id
            JOIN dispute_alert_platform p ON p.id = a.platform_id
            JOIN dispute_alert_platform_product pp ON pp.id = ap.platform_product_id
            """ + CURRENT_CREDENTIAL_JOIN;
    private static final String BINDING_FROM = SOURCE_FROM + """
            JOIN dispute_alert_mid_binding b ON b.account_product_id = ap.id
            LEFT JOIN channel_mid_config m ON m.id = b.mid_config_id
            LEFT JOIN channel_info ch ON ch.id = m.channel_id
            """;
    private static final String MID_COLUMNS = """
            m.channel_code, m.channel_mid, m.mid_name, m.business_type,
            (SELECT GROUP_CONCAT(cb.card_brand ORDER BY cb.card_brand SEPARATOR ',')
                FROM channel_payment_capability cap
                JOIN channel_capability_card_brand cb ON cb.capability_id = cap.id AND cb.brand_status = 1 AND cb.deleted = 0
                WHERE cap.channel_id = m.channel_id AND cap.business_type = 'ACQUIRING'
                    AND cap.payment_method = 'BANK_CARD' AND cap.capability_status = 1 AND cap.deleted = 0
                    AND (m.card_brand_scope = 'ALL' OR FIND_IN_SET(cb.card_brand, m.card_brand_scope) > 0)) effective_card_brands,
            IFNULL((m.mid_status = 1 AND m.deleted = 0 AND IFNULL(ch.channel_status, 0) = 1
              AND IFNULL(ch.support_acquiring, 0) = 1 AND IFNULL(ch.deleted, -1) = 0
              AND (m.payment_method_scope = 'ALL' OR FIND_IN_SET('BANK_CARD', m.payment_method_scope) > 0)
              AND (m.effective_time IS NULL OR m.effective_time <= CURRENT_TIMESTAMP(3))
              AND (m.expire_time IS NULL OR m.expire_time > CURRENT_TIMESTAMP(3))), 0) mid_available
            """;
    private static final String BINDING_COLUMNS = """
            b.id, b.mid_config_id, b.external_merchant_id, b.upstream_merchant_id, b.enrollment, b.enrollment_evidence,
            b.status, b.version, b.update_time,
            (SELECT COUNT(*) FROM dispute_alert_mid_identifier i WHERE i.binding_id = b.id) identifier_count,
            (SELECT COUNT(*) FROM dispute_alert_mid_descriptor d WHERE d.binding_id = b.id) descriptor_count,
            (EXISTS (SELECT 1 FROM dispute_alert_mid_identifier i WHERE i.binding_id = b.id AND i.enabled = 1) OR
             EXISTS (SELECT 1 FROM dispute_alert_mid_descriptor d WHERE d.binding_id = b.id AND d.enabled = 1)) has_mapping,
            """ + MID_COLUMNS + ", " + SOURCE_COLUMNS;

    private final JdbcClient jdbc;

    public DisputeAlertRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public record Platform(long id, String code, String name, PlatformType type, boolean enabled,
                           String evidence, String logoObjectKey, long version, LocalDateTime updateTime) {
    }

    public record PlatformProduct(long id, long platformId, Product product, boolean enabled, Coverage coverage) {
    }

    public record Brand(long platformProductId, String cardBrand) {
    }

    public record Account(long id, long platformId, String platformCode, String platformName, boolean platformEnabled,
                          Environment environment, String merchantNumber, Status status, Long credentialVersion,
                          boolean credentialsRegistered, long version, LocalDateTime updateTime) {
    }

    public record AccountProduct(long id, long accountId, long platformProductId, Product product, Status status,
                                 boolean platformProductEnabled, Transport transport, String host, String directory,
                                 String schedule, String sourceAuthentication, String outcomeUrl) {
        public Delivery delivery() {
            return new Delivery(transport, host, directory, schedule, sourceAuthentication, outcomeUrl);
        }
    }

    public record Credential(long accountId, long version, String publicSecretRef, String privateSecretRef,
                             String publicKeyCipher, String privateKeyCipher,
                             String publicKeyFingerprint, LocalDateTime createTime) {
        public boolean isRegistered() {
            return publicKeyCipher != null && privateKeyCipher != null;
        }

        @Override
        public String toString() {
            return "Credential[accountId=" + accountId + ", version=" + version + "]";
        }
    }

    public record ProviderConfigSummary(long accountId, String settingsJson, String registeredJson, boolean activationValidated) {
        public ProviderConfigView view() {
            var settingsType = new TypeReference<Map<String, Object>>() {};
            return new ProviderConfigView(settingsJson == null ? Map.of() : JsonUtils.parseObject(settingsJson, settingsType),
                    JsonUtils.parseArray(registeredJson, String.class), activationValidated);
        }
    }

    public record CredentialMetadata(long accountId, long version, String publicKeyFingerprint, LocalDateTime createTime) {
    }

    public record Mid(long midConfigId, String channelCode, String channelMid, String midName, String businessType,
                      String effectiveCardBrands, boolean midAvailable) {
        public MidOption option() {
            return new MidOption(midConfigId, channelCode, channelMid, midName);
        }
    }

    public record Source(long accountProductId, long accountId, long platformId, String platformName, String platformCode,
                         Environment environment, String merchantNumber, Status accountStatus, Status productStatus,
                         boolean platformEnabled, boolean platformProductEnabled, long platformProductId,
                         Product product, Coverage coverage, Long credentialVersion, boolean providerActivationValidated) {
        public SourceOption option() {
            return new SourceOption(accountProductId, accountId, platformId, platformName, merchantNumber, product);
        }
    }

    public record Binding(long id, long midConfigId, long accountProductId, String externalMerchantId,
                          String upstreamMerchantId, Enrollment enrollment, String enrollmentEvidence, Status status,
                          long version, LocalDateTime updateTime, int identifierCount, int descriptorCount, boolean hasMapping) {
    }

    public record BindingContext(Binding binding, Mid mid, Source source) {
    }

    public PageResult<Platform> platforms(PageRequest page, String keyword, PlatformType type, Product product) {
        String from = """
                FROM dispute_alert_platform p
                WHERE (:keyword IS NULL OR LOCATE(:keyword, p.code) > 0 OR LOCATE(:keyword, p.name) > 0)
                  AND (:type IS NULL OR p.type = :type)
                  AND (:product IS NULL OR EXISTS (
                      SELECT 1 FROM dispute_alert_platform_product pp WHERE pp.platform_id = p.id AND pp.product = :product))
                """;
        return page(page, "p.*", from, "p.id", new MapSqlParameterSource()
                .addValue("keyword", keyword).addValue("type", name(type)).addValue("product", name(product)), new DataClassRowMapper<>(Platform.class));
    }

    public Optional<Platform> platform(long id, boolean lock) {
        return jdbc.sql("SELECT * FROM dispute_alert_platform WHERE id = :id" + (lock ? " FOR UPDATE" : ""))
                .param("id", id).query(Platform.class).optional();
    }

    public long insertPlatform(String code, PlatformInput input, long actor) {
        return insert("""
                INSERT INTO dispute_alert_platform (code, name, type, enabled, evidence, create_by, update_by)
                VALUES (:code, :name, :type, :enabled, :evidence, :actor, :actor)
                """, platformParameters(input, actor).addValue("code", code));
    }

    public void updatePlatform(long id, PlatformInput input, long actor) {
        jdbc.sql("""
                UPDATE dispute_alert_platform SET name = :name, type = :type, enabled = :enabled,
                  evidence = :evidence, update_by = :actor, version = version + 1 WHERE id = :id
                """).paramSource(platformParameters(input, actor).addValue("id", id)).update();
    }

    public void updatePlatformLogo(long id, String key) {
        jdbc.sql("UPDATE dispute_alert_platform SET logo_object_key = :key WHERE id = :id")
                .param("key", key, Types.VARCHAR).param("id", id).update();
    }

    public List<PlatformProduct> platformProducts(List<Long> platformIds) {
        if (platformIds.isEmpty()) return List.of();
        return jdbc.sql("SELECT * FROM dispute_alert_platform_product WHERE platform_id IN (:ids) ORDER BY id")
                .param("ids", platformIds).query(PlatformProduct.class).list();
    }

    public Optional<PlatformProduct> platformProduct(long platformId, Product product) {
        return jdbc.sql("SELECT * FROM dispute_alert_platform_product WHERE platform_id = :id AND product = :product")
                .param("id", platformId).param("product", product.name()).query(PlatformProduct.class).optional();
    }

    public List<Brand> brands(List<Long> platformProductIds) {
        if (platformProductIds.isEmpty()) return List.of();
        return jdbc.sql("""
                SELECT platform_product_id, card_brand FROM dispute_alert_platform_product_brand
                WHERE platform_product_id IN (:ids) ORDER BY card_brand
                """).param("ids", platformProductIds).query(Brand.class).list();
    }

    public void savePlatformProduct(long platformId, PlatformProduct existing, PlatformProductInput input, List<String> brands) {
        long id;
        if (existing == null) {
            id = insert("""
                    INSERT INTO dispute_alert_platform_product (platform_id, product, enabled, coverage)
                    VALUES (:platform, :product, :enabled, :coverage)
                    """, new MapSqlParameterSource().addValue("platform", platformId).addValue("product", input.product().name())
                    .addValue("enabled", input.enabled()).addValue("coverage", input.coverage().name()));
        } else {
            id = existing.id();
            jdbc.sql("UPDATE dispute_alert_platform_product SET enabled = :enabled, coverage = :coverage WHERE id = :id")
                    .param("id", id).param("enabled", input.enabled()).param("coverage", input.coverage().name()).update();
            jdbc.sql("DELETE FROM dispute_alert_platform_product_brand WHERE platform_product_id = :id").param("id", id).update();
        }
        for (String brand : brands) {
            jdbc.sql("INSERT INTO dispute_alert_platform_product_brand (platform_product_id, card_brand) VALUES (:id, :brand)")
                    .param("id", id).param("brand", brand).update();
        }
    }

    public boolean platformProductInUse(long id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM dispute_alert_account_product WHERE platform_product_id = :id)")
                .param("id", id).query(Boolean.class).single();
    }

    public void removeUnassignedProduct(long id) {
        jdbc.sql("DELETE FROM dispute_alert_platform_product_brand WHERE platform_product_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM dispute_alert_platform_product WHERE id = :id").param("id", id).update();
    }

    public PageResult<Account> accounts(PageRequest page, Long platformId, Environment environment, Product product, String keyword) {
        String from = ACCOUNT_FROM + """
                WHERE (:platform IS NULL OR a.platform_id = :platform)
                  AND (:environment IS NULL OR a.environment = :environment)
                  AND (:keyword IS NULL OR LOCATE(:keyword, a.merchant_number) > 0 OR LOCATE(:keyword, p.name) > 0
                       OR LOCATE(:keyword, p.code) > 0)
                  AND (:product IS NULL OR EXISTS (
                      SELECT 1 FROM dispute_alert_account_product x
                      JOIN dispute_alert_platform_product pp ON pp.id = x.platform_product_id
                      WHERE x.account_id = a.id AND pp.product = :product))
                """;
        return page(page, ACCOUNT_COLUMNS, from, "a.id", new MapSqlParameterSource().addValue("platform", platformId)
                .addValue("environment", name(environment)).addValue("product", name(product)).addValue("keyword", keyword),
                new DataClassRowMapper<>(Account.class));
    }

    public Optional<Account> account(long id) {
        return jdbc.sql("SELECT " + ACCOUNT_COLUMNS + ACCOUNT_FROM + " WHERE a.id = :id")
                .param("id", id).query(Account.class).optional();
    }

    public void lockAccount(long id) {
        jdbc.sql("SELECT id FROM dispute_alert_account WHERE id = :id FOR UPDATE").param("id", id).query(Long.class).single();
    }

    public long insertAccount(AccountCreate input, Environment environment, long actor) {
        return insert("""
                INSERT INTO dispute_alert_account (platform_id, environment, merchant_number, status, create_by, update_by)
                VALUES (:platform, :environment, :number, :status, :actor, :actor)
                """, new MapSqlParameterSource().addValue("platform", input.platformId()).addValue("environment", environment.name())
                .addValue("number", input.merchantNumber().trim()).addValue("status", input.status().name()).addValue("actor", actor));
    }

    public void updateAccount(long id, AccountUpdate input, long actor) {
        jdbc.sql("""
                UPDATE dispute_alert_account SET merchant_number = :number, status = :status,
                  version = version + 1, update_by = :actor WHERE id = :id
                """).param("id", id).param("number", input.merchantNumber().trim()).param("status", input.status().name())
                .param("actor", actor).update();
    }

    public void touchAccount(long id, long actor) {
        jdbc.sql("UPDATE dispute_alert_account SET version = version + 1, update_by = :actor WHERE id = :id")
                .param("id", id).param("actor", actor).update();
    }

    public boolean accountHasBinding(long id) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM dispute_alert_mid_binding b
                    JOIN dispute_alert_account_product ap ON ap.id = b.account_product_id WHERE ap.account_id = :id)
                """).param("id", id).query(Boolean.class).single();
    }

    public List<AccountProduct> accountProducts(List<Long> accountIds) {
        if (accountIds.isEmpty()) return List.of();
        return jdbc.sql("""
                SELECT ap.*, pp.product, pp.enabled platform_product_enabled FROM dispute_alert_account_product ap
                JOIN dispute_alert_platform_product pp ON pp.id = ap.platform_product_id
                WHERE ap.account_id IN (:ids) ORDER BY ap.id
                """).param("ids", accountIds).query(AccountProduct.class).list();
    }

    public void saveAccountProduct(Account account, PlatformProduct product, AccountProduct existing, Status status, Delivery delivery) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("account", account.id()).addValue("platform", account.platformId())
                .addValue("product", product.id()).addValue("status", status.name()).addValue("transport", delivery.transport().name())
                .addValue("host", delivery.host()).addValue("directory", delivery.directory()).addValue("schedule", delivery.schedule())
                .addValue("authentication", delivery.sourceAuthentication()).addValue("outcome", delivery.outcomeUrl());
        if (existing == null) {
            jdbc.sql("""
                    INSERT INTO dispute_alert_account_product
                      (platform_id, account_id, platform_product_id, status, transport, host, directory, schedule, source_authentication, outcome_url)
                    VALUES (:platform, :account, :product, :status, :transport, :host, :directory, :schedule, :authentication, :outcome)
                    """).paramSource(parameters).update();
        } else {
            jdbc.sql("""
                    UPDATE dispute_alert_account_product SET status = :status, transport = :transport, host = :host,
                      directory = :directory, schedule = :schedule, source_authentication = :authentication, outcome_url = :outcome
                    WHERE id = :id
                    """).paramSource(parameters.addValue("id", existing.id())).update();
        }
    }

    public List<ProviderConfigSummary> providerConfigSummaries(List<Long> accountIds) {
        if (accountIds.isEmpty()) return List.of();
        return jdbc.sql("""
                SELECT a.id account_id, a.provider_settings_json settings_json,
                    JSON_KEYS(provider_keys.provider_credential_cipher_json) registered_json,
                    a.provider_activation_validated activation_validated
                FROM dispute_alert_account a
                """ + PROVIDER_CREDENTIAL_JOIN + "WHERE a.id IN (:ids)")
                .param("ids", accountIds).query(ProviderConfigSummary.class).list();
    }

    public boolean providerActivationValidated(long accountId) {
        return jdbc.sql("SELECT provider_activation_validated FROM dispute_alert_account WHERE id = :id")
                .param("id", accountId).query(Boolean.class).single();
    }

    public Map<String, String> providerCredentialCiphers(long accountId) {
        var cipherType = new TypeReference<Map<String, String>>() {};
        return jdbc.sql("""
                SELECT provider_keys.provider_credential_cipher_json
                FROM dispute_alert_account a
                """ + PROVIDER_CREDENTIAL_JOIN + """
                WHERE a.id = :id AND provider_keys.provider_credential_cipher_json IS NOT NULL
                """).param("id", accountId).query(String.class).optional()
                .map(json -> JsonUtils.parseObject(json, cipherType)).orElseGet(Map::of);
    }

    public void insertProviderCredential(long accountId, long version, Map<String, String> ciphers, long actor) {
        jdbc.sql("""
                INSERT INTO dispute_alert_credential_version
                    (account_id, version, provider_credential_cipher_json, create_by)
                VALUES (:account, :version, :ciphers, :actor)
                """).param("account", accountId).param("version", version)
                .param("ciphers", JsonUtils.toJsonString(ciphers)).param("actor", actor).update();
    }

    public void saveProviderConfig(long accountId, Map<String, Object> settings, Long credentialVersion, boolean activationValidated) {
        jdbc.sql("""
                UPDATE dispute_alert_account SET provider_settings_json = :settings,
                    provider_activation_validated = :validated, credential_version = :credentialVersion
                WHERE id = :id
                """).param("id", accountId).param("settings", JsonUtils.toJsonString(settings))
                .param("validated", activationValidated).param("credentialVersion", credentialVersion, Types.BIGINT).update();
    }

    public List<CredentialMetadata> currentCredentials(List<Long> accountIds) {
        if (accountIds.isEmpty()) return List.of();
        return jdbc.sql("""
                SELECT current_keys.account_id, current_keys.version, current_keys.public_key_fingerprint, current_keys.create_time
                FROM dispute_alert_account a
                """ + CURRENT_CREDENTIAL_JOIN + "WHERE a.id IN (:ids) AND current_keys.version IS NOT NULL")
                .param("ids", accountIds).query(CredentialMetadata.class).list();
    }

    public Optional<Credential> credential(long accountId, Long version) {
        if (version == null) return Optional.empty();
        return jdbc.sql("""
                SELECT account_id, version, public_secret_ref, private_secret_ref, public_key_cipher, private_key_cipher,
                       public_key_fingerprint, create_time
                FROM dispute_alert_credential_version WHERE account_id = :id AND version = :version
                """)
                .param("id", accountId).param("version", version).query(Credential.class).optional();
    }

    public void insertCredential(Credential value, long actor) {
        jdbc.sql("""
                INSERT INTO dispute_alert_credential_version
                  (account_id, version, public_secret_ref, private_secret_ref, public_key_cipher, private_key_cipher,
                   public_key_fingerprint, create_by)
                VALUES (:account, :version, :publicRef, :privateRef, :publicCipher, :privateCipher, :fingerprint, :actor)
                """).param("account", value.accountId()).param("version", value.version())
                .param("publicRef", value.publicSecretRef()).param("privateRef", value.privateSecretRef())
                .param("publicCipher", value.publicKeyCipher()).param("privateCipher", value.privateKeyCipher())
                .param("fingerprint", value.publicKeyFingerprint()).param("actor", actor).update();
        jdbc.sql("""
                UPDATE dispute_alert_account SET credential_version = :version, version = version + 1, update_by = :actor WHERE id = :account
                """).param("account", value.accountId()).param("version", value.version()).param("actor", actor).update();
    }

    public List<String> midChannels() {
        return jdbc.sql("""
                SELECT channel_code FROM channel_info
                WHERE channel_status = 1 AND support_acquiring = 1 AND deleted = 0
                ORDER BY channel_code
                """).query(String.class).list();
    }

    public List<Mid> mids(String channelCode, String keyword) {
        return jdbc.sql("SELECT m.id mid_config_id, " + MID_COLUMNS + """
                FROM channel_mid_config m JOIN channel_info ch ON ch.id = m.channel_id
                WHERE m.deleted = 0 AND m.business_type = 'ACQUIRING'
                  AND (:channelCode IS NULL OR m.channel_code = :channelCode)
                  AND (:keyword IS NULL OR LOCATE(:keyword, m.channel_code) > 0 OR LOCATE(:keyword, m.channel_mid) > 0
                       OR LOCATE(:keyword, m.mid_name) > 0)
                HAVING mid_available = 1 AND effective_card_brands IS NOT NULL
                ORDER BY m.id DESC LIMIT 50
                """).param("channelCode", channelCode).param("keyword", keyword).query(Mid.class).list();
    }

    public Optional<Mid> mid(long id) {
        return jdbc.sql("SELECT m.id mid_config_id, " + MID_COLUMNS + """
                FROM channel_mid_config m LEFT JOIN channel_info ch ON ch.id = m.channel_id WHERE m.id = :id
                """).param("id", id).query(Mid.class).optional();
    }

    public Optional<Source> source(long accountProductId) {
        return jdbc.sql("SELECT " + SOURCE_COLUMNS + SOURCE_FROM + " WHERE ap.id = :id")
                .param("id", accountProductId).query(Source.class).optional();
    }

    public List<Source> sources(Environment environment, Product product, String keyword) {
        return jdbc.sql("SELECT " + SOURCE_COLUMNS + SOURCE_FROM + """
                WHERE a.environment = :environment AND pp.product = :product
                  AND p.enabled = 1 AND pp.enabled = 1 AND a.status = 'ENABLED' AND ap.status = 'ENABLED'
                  AND (p.code <> 'VERXA_SHIELD' OR current_keys.version IS NOT NULL)
                  AND (:keyword IS NULL OR LOCATE(:keyword, p.name) > 0 OR LOCATE(:keyword, a.merchant_number) > 0)
                ORDER BY p.name, a.id
                """).param("environment", environment.name()).param("product", product.name()).param("keyword", keyword)
                .query(Source.class).list();
    }

    public PageResult<BindingContext> bindings(PageRequest page, Long midId, Product product, Status status, String keyword) {
        String from = BINDING_FROM + """
                WHERE (:mid IS NULL OR b.mid_config_id = :mid)
                  AND (:product IS NULL OR pp.product = :product)
                  AND (:status IS NULL OR b.status = :status)
                  AND (:keyword IS NULL OR LOCATE(:keyword, m.channel_code) > 0 OR LOCATE(:keyword, m.channel_mid) > 0)
                """;
        return page(page, BINDING_COLUMNS, from, "b.id", new MapSqlParameterSource().addValue("mid", midId)
                .addValue("product", name(product)).addValue("status", name(status)).addValue("keyword", keyword),
                DisputeAlertRepository::bindingRow);
    }

    public Optional<BindingContext> binding(long id) {
        return jdbc.sql("SELECT " + BINDING_COLUMNS + BINDING_FROM + " WHERE b.id = :id")
                .param("id", id).query(DisputeAlertRepository::bindingRow).optional();
    }

    public void lockBinding(long id) {
        jdbc.sql("SELECT id FROM dispute_alert_mid_binding WHERE id = :id FOR UPDATE").param("id", id).query(Long.class).single();
    }

    public long insertBinding(BindingInput input, String externalMerchantId, long actor) {
        return insert("""
                INSERT INTO dispute_alert_mid_binding
                  (mid_config_id, account_product_id, external_merchant_id, upstream_merchant_id, enrollment, enrollment_evidence,
                   status, create_by, update_by)
                VALUES (:mid, :source, :external, :upstream, :enrollment, :evidence, 'DRAFT', :actor, :actor)
                """, bindingParameters(input, actor).addValue("external", externalMerchantId));
    }

    public void updateBinding(long id, BindingInput input, Status status, long actor) {
        jdbc.sql("""
                UPDATE dispute_alert_mid_binding SET upstream_merchant_id = :upstream, enrollment = :enrollment,
                  enrollment_evidence = :evidence, status = :status, update_by = :actor, version = version + 1 WHERE id = :id
                """).paramSource(bindingParameters(input, actor).addValue("id", id).addValue("status", status.name())).update();
    }

    public void setBindingStatus(long id, Status status, long actor) {
        jdbc.sql("UPDATE dispute_alert_mid_binding SET status = :status, version = version + 1, update_by = :actor WHERE id = :id")
                .param("id", id).param("status", status.name()).param("actor", actor).update();
    }

    public List<IdentityInput> identifiers(long id) {
        return jdbc.sql("""
                SELECT type, acquirer, acquirer_mid, acquirer_bin, caid, enabled
                FROM dispute_alert_mid_identifier WHERE binding_id = :id ORDER BY id
                """).param("id", id).query(IdentityInput.class).list();
    }

    public List<DescriptorInput> descriptors(long id) {
        return jdbc.sql("SELECT descriptor AS `value`, enabled FROM dispute_alert_mid_descriptor WHERE binding_id = :id ORDER BY id")
                .param("id", id).query(DescriptorInput.class).list();
    }

    /** 列出同来源账号产品、同外部商户范围下共享有效标识的其他已启用 MID。 */
    public List<MidOption> overlappingMids(long id) {
        return jdbc.sql("""
                SELECT DISTINCT mid.id, mid.channel_code, mid.channel_mid, mid.mid_name
                FROM dispute_alert_mid_binding b
                JOIN dispute_alert_mid_binding other ON other.account_product_id = b.account_product_id
                    AND other.external_merchant_id = b.external_merchant_id
                    AND other.mid_config_id <> b.mid_config_id
                JOIN channel_mid_config mid ON mid.id = other.mid_config_id
                WHERE b.id = :id AND other.status = 'ENABLED' AND mid.deleted = 0
                    AND (EXISTS (
                        SELECT 1 FROM dispute_alert_mid_identifier original
                        JOIN dispute_alert_mid_identifier shared ON shared.binding_id = other.id
                            AND shared.enabled = 1 AND shared.type = original.type AND shared.acquirer = original.acquirer
                            AND shared.acquirer_mid = original.acquirer_mid AND shared.acquirer_bin = original.acquirer_bin
                            AND shared.caid = original.caid
                        WHERE original.binding_id = b.id AND original.enabled = 1
                    ) OR EXISTS (
                        SELECT 1 FROM dispute_alert_mid_descriptor original
                        JOIN dispute_alert_mid_descriptor shared ON shared.binding_id = other.id
                            AND shared.enabled = 1 AND shared.descriptor = original.descriptor
                        WHERE original.binding_id = b.id AND original.enabled = 1
                    ))
                ORDER BY mid.id
                """).param("id", id).query(MidOption.class).list();
    }

    public void replaceMappings(long id, List<IdentityInput> identifiers, List<DescriptorInput> descriptors) {
        jdbc.sql("DELETE FROM dispute_alert_mid_identifier WHERE binding_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM dispute_alert_mid_descriptor WHERE binding_id = :id").param("id", id).update();
        for (IdentityInput item : identifiers) {
            jdbc.sql("""
                    INSERT INTO dispute_alert_mid_identifier (binding_id, type, acquirer, acquirer_mid, acquirer_bin, caid, enabled)
                    VALUES (:id, :type, :acquirer, :mid, :bin, :caid, :enabled)
                    """).param("id", id).param("type", item.type().name()).param("acquirer", item.acquirer())
                    .param("mid", item.acquirerMid()).param("bin", item.acquirerBin()).param("caid", item.caid())
                    .param("enabled", item.enabled()).update();
        }
        for (DescriptorInput item : descriptors) {
            jdbc.sql("INSERT INTO dispute_alert_mid_descriptor (binding_id, descriptor, enabled) VALUES (:id, :value, :enabled)")
                    .param("id", id).param("value", item.value()).param("enabled", item.enabled()).update();
        }
    }

    private <T> PageResult<T> page(PageRequest request, String columns, String from, String sort,
                                   MapSqlParameterSource parameters, RowMapper<T> mapper) {
        long pageNo = request.safePageNo();
        long size = Math.min(request.safePageSize(), 100);
        long total = jdbc.sql("SELECT COUNT(*) " + from).paramSource(parameters).query(Long.class).single();
        String query = "SELECT " + columns + " " + from + " ORDER BY " + sort + " DESC LIMIT :limit OFFSET :offset";
        var statement = jdbc.sql(query).paramSource(parameters.addValue("limit", size).addValue("offset", (pageNo - 1) * size));
        List<T> rows = total == 0 ? List.of() : statement.query(mapper).list();
        return PageResult.of(total, pageNo, size, rows);
    }

    private long insert(String sql, MapSqlParameterSource parameters) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.sql(sql).paramSource(parameters).update(key, "id");
        return Objects.requireNonNull(key.getKey(), "missing dispute alert insert id").longValue();
    }

    private static MapSqlParameterSource platformParameters(PlatformInput input, long actor) {
        return new MapSqlParameterSource().addValue("name", input.name().trim()).addValue("type", input.type().name())
                .addValue("enabled", input.enabled()).addValue("evidence", input.evidence()).addValue("actor", actor);
    }

    private static MapSqlParameterSource bindingParameters(BindingInput input, long actor) {
        return new MapSqlParameterSource().addValue("mid", input.midConfigId()).addValue("source", input.accountProductId())
                .addValue("upstream", input.upstreamMerchantId()).addValue("enrollment", input.enrollment().name())
                .addValue("evidence", input.enrollmentEvidence()).addValue("actor", actor);
    }

    private static BindingContext bindingRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new BindingContext(BINDING_MAPPER.mapRow(rs, row), MID_MAPPER.mapRow(rs, row), SOURCE_MAPPER.mapRow(rs, row));
    }

    private static final DataClassRowMapper<Binding> BINDING_MAPPER = new DataClassRowMapper<>(Binding.class);
    private static final DataClassRowMapper<Mid> MID_MAPPER = new DataClassRowMapper<>(Mid.class);
    private static final DataClassRowMapper<Source> SOURCE_MAPPER = new DataClassRowMapper<>(Source.class);

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
