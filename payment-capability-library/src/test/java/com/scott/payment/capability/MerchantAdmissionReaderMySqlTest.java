package com.scott.payment.capability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/** 在专用 MySQL 的连接级临时表中核对严格分组、规范化和多语言启停。 */
@EnabledIfEnvironmentVariable(named = "REQ001_DIRECTORY_TEST_JDBC_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/req001_[A-Za-z0-9_]+(?:\\?.*)?")
class MerchantAdmissionReaderMySqlTest {

    @Test
    void strictGroupingEvaluatesOneNormalizedKeyAcrossAllLocales() throws Exception {
        String url = System.getenv("REQ001_DIRECTORY_TEST_JDBC_URL");
        try (var connection = DriverManager.getConnection(url, System.getenv("REQ001_DIRECTORY_TEST_MYSQL_USER"),
                System.getenv("REQ001_DIRECTORY_TEST_MYSQL_PASSWORD"))) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            assertThat(jdbc.queryForObject("SELECT @@session.sql_mode", String.class)).contains("ONLY_FULL_GROUP_BY");
            jdbc.execute("""
                    CREATE TEMPORARY TABLE sys_dict_type (
                        dict_type VARCHAR(100) NOT NULL, status TINYINT NOT NULL, deleted TINYINT NOT NULL
                    )
                    """);
            jdbc.execute("""
                    CREATE TEMPORARY TABLE sys_dict_data (
                        dict_type VARCHAR(100) NOT NULL, dict_value VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
                        status TINYINT NOT NULL, deleted TINYINT NOT NULL
                    ) DEFAULT CHARSET=utf8mb4
                    """);
            jdbc.update("""
                    INSERT INTO sys_dict_type VALUES ('card_brand', 1, 0), ('acquiring_payment_method', 1, 0)
                    """);
            jdbc.update("""
                    INSERT INTO sys_dict_data VALUES
                        ('card_brand', 'visa', 1, 0), ('card_brand', 'VISA', 1, 0),
                        ('card_brand', 'mastercard', 1, 0), ('card_brand', 'MASTERCARD', 0, 0),
                        ('acquiring_payment_method', 'BANK_CARD', 1, 0),
                        ('acquiring_payment_method', 'Apple_Pay', 1, 0)
                    """);

            var reader = new MerchantAdmissionReader(jdbc);
            assertThat(reader.enabledDirectory()).containsExactlyInAnyOrder("CARD_BRAND:VISA", "METHOD:APPLE_PAY");

            jdbc.update("UPDATE sys_dict_data SET status = 0 WHERE dict_value = 'BANK_CARD'");
            assertThat(reader.enabledDirectory()).containsExactly("METHOD:APPLE_PAY");
        }
    }
}
