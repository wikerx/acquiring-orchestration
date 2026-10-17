package com.scott.payment.openapi;

import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCountryResolver;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared MySQL test boundary for OpenAPI integration tests that require real MySQL semantics.
 */
@ContextConfiguration(initializers = OpenApiIsolatedMysqlIntegrationTest.Initializer.class)
abstract class OpenApiIsolatedMysqlIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("openapi_integration")
            .withUsername("openapi_test")
            .withPassword("openapi_test_password");

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:6.2.23"))
            .withExposedPorts(REDIS_PORT);

    private static boolean referenceDataInitialized;

    /**
     * Starts one process-wide container and registers both logical data sources before Spring creates beans.
     */
    public static final class Initializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            startContainer();
            initializeReferenceData();
            TestPropertyValues.of(
                    "spring.datasource.dynamic.primary=master",
                    "spring.datasource.dynamic.strict=true",
                    "spring.datasource.dynamic.datasource.master.type=com.zaxxer.hikari.HikariDataSource",
                    "spring.datasource.dynamic.datasource.master.driver-class-name=com.mysql.cj.jdbc.Driver",
                    "spring.datasource.dynamic.datasource.master.url=" + MYSQL.getJdbcUrl(),
                    "spring.datasource.dynamic.datasource.master.username=" + MYSQL.getUsername(),
                    "spring.datasource.dynamic.datasource.master.password=" + MYSQL.getPassword(),
                    "spring.datasource.dynamic.datasource.slave.type=com.zaxxer.hikari.HikariDataSource",
                    "spring.datasource.dynamic.datasource.slave.driver-class-name=com.mysql.cj.jdbc.Driver",
                    "spring.datasource.dynamic.datasource.slave.url=" + MYSQL.getJdbcUrl(),
                    "spring.datasource.dynamic.datasource.slave.username=" + MYSQL.getUsername(),
                    "spring.datasource.dynamic.datasource.slave.password=" + MYSQL.getPassword(),
                    "spring.data.redis.host=" + REDIS.getHost(),
                    "spring.data.redis.port=" + REDIS.getMappedPort(REDIS_PORT),
                    "spring.data.redis.database=0",
                    "spring.data.redis.password="
            ).applyTo(applicationContext);
        }
    }

    private static synchronized void startContainer() {
        if (!MYSQL.isRunning()) {
            MYSQL.start();
        }
        if (!REDIS.isRunning()) {
            REDIS.start();
        }
    }

    private static synchronized void initializeReferenceData() {
        if (referenceDataInitialized) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            createReferenceTables(connection);
            insertCountries(connection);
            insertCurrencies(connection);
            referenceDataInitialized = true;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to initialize isolated OpenAPI MySQL test database", exception);
        }
    }

    private static void createReferenceTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS base_iso_country (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        continent_code CHAR(2) NOT NULL DEFAULT '',
                        continent_name VARCHAR(32) NOT NULL DEFAULT '',
                        english_name VARCHAR(160) NOT NULL,
                        short_english_name VARCHAR(128) NOT NULL,
                        chinese_name VARCHAR(128) NOT NULL,
                        alpha2_code CHAR(2) NOT NULL,
                        alpha3_code CHAR(3) NOT NULL,
                        numeric_code CHAR(3) NOT NULL,
                        flag_emoji VARCHAR(16) NOT NULL DEFAULT '',
                        primary_language_code VARCHAR(16) NOT NULL DEFAULT '',
                        primary_language_english VARCHAR(64) NOT NULL DEFAULT '',
                        primary_language_chinese VARCHAR(64) NOT NULL DEFAULT '',
                        currency_alpha3_code CHAR(3) NOT NULL DEFAULT '',
                        status TINYINT NOT NULL DEFAULT 1,
                        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                            ON UPDATE CURRENT_TIMESTAMP(3),
                        deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_base_iso_country_alpha2 (alpha2_code),
                        UNIQUE KEY uk_base_iso_country_alpha3 (alpha3_code),
                        UNIQUE KEY uk_base_iso_country_numeric (numeric_code),
                        KEY idx_base_iso_country_continent (continent_code),
                        KEY idx_base_iso_country_currency (currency_alpha3_code)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS base_iso_currency (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        alpha3_code CHAR(3) NOT NULL,
                        numeric_code CHAR(3) NOT NULL,
                        english_name VARCHAR(128) NOT NULL,
                        chinese_name VARCHAR(128) NOT NULL,
                        currency_symbol VARCHAR(16) NOT NULL DEFAULT '',
                        icon_key VARCHAR(64) NULL,
                        fraction_digits TINYINT NOT NULL,
                        minor_unit_multiplier BIGINT NOT NULL DEFAULT 0,
                        minimum_amount DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
                        status TINYINT NOT NULL DEFAULT 1,
                        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                            ON UPDATE CURRENT_TIMESTAMP(3),
                        deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_base_iso_currency_alpha3 (alpha3_code),
                        KEY idx_base_iso_currency_numeric (numeric_code)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ip_library_split_model (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        ip_type VARCHAR(16) NOT NULL,
                        shard_no INT NOT NULL,
                        range_start VARBINARY(16) NOT NULL,
                        range_end VARBINARY(16) NOT NULL,
                        table_name VARCHAR(128) NOT NULL,
                        data_version BIGINT NOT NULL,
                        active_flag TINYINT NOT NULL DEFAULT 1,
                        load_status VARCHAR(16) NOT NULL,
                        PRIMARY KEY (id),
                        KEY idx_ip_split_ready (active_flag, load_status, ip_type, range_start)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS merchant_security_cache_invalidation_outbox (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        event_id VARCHAR(64) NOT NULL,
                        cache_name VARCHAR(64) NOT NULL,
                        business_key VARCHAR(128) NOT NULL,
                        gate_token VARCHAR(128) NOT NULL,
                        event_status VARCHAR(16) NOT NULL DEFAULT 'INIT',
                        retry_count INT NOT NULL DEFAULT 0,
                        next_retry_time DATETIME(3) NULL,
                        published_time DATETIME(3) NULL,
                        failure_reason VARCHAR(512) NULL,
                        version INT NOT NULL DEFAULT 0,
                        create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                            ON UPDATE CURRENT_TIMESTAMP(3),
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_merchant_security_cache_event (event_id),
                        KEY idx_merchant_security_cache_due (
                            event_status, next_retry_time, create_time, id
                        ),
                        KEY idx_merchant_security_cache_target (
                            cache_name, business_key, create_time
                        )
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                    """);
        }
    }

    private static void insertCountries(Connection connection) throws SQLException {
        String sql = """
                INSERT IGNORE INTO base_iso_country (
                    continent_code, continent_name, english_name, short_english_name, chinese_name,
                    alpha2_code, alpha3_code, numeric_code, flag_emoji,
                    primary_language_code, primary_language_english, primary_language_chinese,
                    currency_alpha3_code, status, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (IsoCountryInfo country : IsoCountryResolver.listIndexedCountries()) {
                statement.setString(1, country.continentCode());
                statement.setString(2, country.continentName());
                statement.setString(3, country.englishName());
                statement.setString(4, country.shortEnglishName());
                statement.setString(5, country.chineseName());
                statement.setString(6, country.alpha2());
                statement.setString(7, country.alpha3());
                statement.setString(8, country.numeric());
                statement.setString(9, country.flagEmoji());
                statement.setString(10, country.primaryLanguageCode());
                statement.setString(11, country.primaryLanguageEnglish());
                statement.setString(12, country.primaryLanguageChinese());
                statement.setString(13, country.currencyAlpha3Code());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertCurrencies(Connection connection) throws SQLException {
        String sql = """
                INSERT IGNORE INTO base_iso_currency (
                    alpha3_code, numeric_code, english_name, chinese_name, currency_symbol,
                    fraction_digits, minor_unit_multiplier, minimum_amount, status, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 0)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (IsoCurrencyInfo currency : IsoCurrencyResolver.listIndexedCurrencies()) {
                statement.setString(1, currency.alphabeticCode());
                statement.setString(2, currency.numericCode());
                statement.setString(3, currency.englishName());
                statement.setString(4, currency.chineseName());
                statement.setString(5, currency.currencySymbol());
                statement.setInt(6, currency.defaultFractionDigits());
                statement.setLong(7, currency.minorUnitMultiplier());
                statement.setBigDecimal(8, currency.minimumAmount());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
