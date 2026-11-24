package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ChannelDiagnosisQuery;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.core.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用独立内存数据库验证最终渠道、独立请求和商户权限的实际聚合结果。 */
class JdbcAdminChannelDiagnosticsTests {
    private EmbeddedDatabase database;
    private JdbcAdminTransactionAnalyticsQueryService service;
    private final AdminMerchantDataScope visible = AdminMerchantDataScope.limited(Set.of("M_A", "M_B"));

    @BeforeEach
    void seed() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        var jdbc = new NamedParameterJdbcTemplate(database);
        var sql = jdbc.getJdbcTemplate();
        sql.execute("""
                CREATE TABLE transaction_operation (
                    transaction_id VARCHAR(64), transaction_date_time TIMESTAMP, merchant_id VARCHAR(32),
                    merchant_order_no VARCHAR(64), channel_code VARCHAR(32), transaction_type VARCHAR(32),
                    transaction_currency VARCHAR(3), transaction_status VARCHAR(32), fail_reason_code VARCHAR(64),
                    deleted INT DEFAULT 0, operation_sequence INT DEFAULT 1)
                """);
        sql.execute("""
                CREATE TABLE transaction_payment_method_info (
                    id BIGINT, transaction_id VARCHAR(64), transaction_date_time TIMESTAMP,
                    payment_method VARCHAR(32), payment_brand VARCHAR(32), issuer_country VARCHAR(3))
                """);
        sql.execute("""
                CREATE TABLE transaction_channel_request (
                    transaction_id VARCHAR(64), transaction_date_time TIMESTAMP, channel_code VARCHAR(32),
                    request_status VARCHAR(32), platform_success INT, deleted INT DEFAULT 0,
                    channel_match_flag INT DEFAULT 0)
                """);
        sql.execute("""
                INSERT INTO transaction_operation
                    (transaction_id, transaction_date_time, merchant_id, merchant_order_no, channel_code,
                     transaction_type, transaction_currency, transaction_status, fail_reason_code)
                VALUES ('T1', TIMESTAMP '2026-11-17 12:00:00', 'M_A', 'A1', 'C2', 'PAYMENT', 'USD', 'SUCCESS', NULL),
                       ('T2', TIMESTAMP '2026-11-17 12:00:00', 'M_A', 'A2', 'C1', 'PAYMENT', 'USD', 'FAILED', 'CHANNEL_TIMEOUT'),
                       ('T3', TIMESTAMP '2026-11-17 12:00:00', 'M_B', 'B3', 'C1', 'PAYMENT', 'USD', 'SUCCESS', NULL),
                       ('T4', TIMESTAMP '2026-11-17 12:00:00', 'M_C', 'C4', 'C1', 'PAYMENT', 'USD', 'FAILED', 'OTHER'),
                       ('T5', TIMESTAMP '2026-11-17 12:00:00', 'M_A', 'A5', 'C1', 'PAYMENT', 'USD', 'PENDING', NULL)
                """);
        sql.execute("""
                INSERT INTO transaction_payment_method_info VALUES
                    (1, 'T1', TIMESTAMP '2026-11-17 12:00:00', 'APPLE_PAY', NULL, NULL),
                    (2, 'T1', TIMESTAMP '2026-11-17 12:00:00', 'BANK_CARD', 'VISA', NULL),
                    (3, 'T2', TIMESTAMP '2026-11-17 12:00:00', 'BANK_CARD', 'VISA', NULL),
                    (4, 'T3', TIMESTAMP '2026-11-17 12:00:00', 'BANK_CARD', 'VISA', NULL),
                    (5, 'T4', TIMESTAMP '2026-11-17 12:00:00', 'BANK_CARD', 'VISA', NULL),
                    (6, 'T5', TIMESTAMP '2026-11-17 12:00:00', 'BANK_CARD', 'VISA', NULL)
                """);
        sql.execute("""
                INSERT INTO transaction_channel_request VALUES
                    ('T1', TIMESTAMP '2026-11-17 12:00:00', 'C1', 'FAILED', 0, 0, 0),
                    ('T1', TIMESTAMP '2026-11-17 12:00:00', 'C2', 'SUCCESS', 1, 0, 0),
                    ('T2', TIMESTAMP '2026-11-17 12:00:00', 'C1', 'TIMEOUT', 0, 0, 0),
                    ('T2', TIMESTAMP '2026-11-17 12:00:00', 'C1', 'SUCCESS', 1, 0, 1),
                    ('T3', TIMESTAMP '2026-11-17 12:00:00', 'C1', 'SUCCESS', 1, 0, 0),
                    ('T4', TIMESTAMP '2026-11-17 12:00:00', 'C1', 'FAILED', 0, 0, 0),
                    ('T5', TIMESTAMP '2026-11-17 12:00:00', 'C1', 'SENT', 0, 0, 0)
                """);
        service = new JdbcAdminTransactionAnalyticsQueryService(jdbc);
    }

    @AfterEach
    void shutdown() {
        if (database != null) database.shutdown();
    }

    @Test
    void keepsFinalChannelAndRequestChannelSeparateAndScopesPeers() {
        var result = service.channelBreakdown(filters(new AnalyticsQuery()), visible);
        assertThat(result.comparisonAvailable()).isTrue();
        assertThat(result.rows()).hasSize(2);
        var first = result.rows().get(0);
        assertThat(first.channelCode()).isEqualTo("C1");
        assertThat(first.paymentMethod()).isEqualTo("BANK_CARD");
        assertThat(first.paymentBrand()).isEqualTo("VISA");
        assertThat(first.selected().transactions().totalCount()).isEqualTo(2);
        assertThat(first.selected().transactions().completedCount()).isEqualTo(1);
        assertThat(first.selected().transactions().failedCount()).isEqualTo(1);
        assertThat(first.selected().requests().totalCount()).isEqualTo(3);
        assertThat(first.selected().requests().completedCount()).isEqualTo(2);
        assertThat(first.selected().requests().failedCount()).isEqualTo(2);
        assertThat(first.comparison().transactions().totalCount()).isEqualTo(1);
        assertThat(first.comparison().transactions().successRate()).isEqualByComparingTo("100.00");
        assertThat(result.rows().get(1).selected().transactions().successCount()).isEqualTo(1);
        assertThat(result.rows().get(1).comparison().transactions().successRate()).isNull();
    }

    @Test
    void drilldownUsesTheSameFinalChannelAndReasonFilter() {
        var query = filters(new ChannelDiagnosisQuery());
        query.setChannelCode("C1");
        query.setGroupPaymentMethod("BANK_CARD");
        query.setGroupPaymentBrand("VISA");
        query.setReasonCode("CHANNEL_TIMEOUT");
        var result = service.channelFailedTransactions(query, visible);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.transactionId()).isEqualTo("T2");
            assertThat(row.merchantId()).isEqualTo("M_A");
        });
    }

    @Test
    void avoidsComparisonWhenTheSpecifiedMerchantIsTheOnlyVisibleMerchant() {
        var result = service.channelBreakdown(filters(new AnalyticsQuery()), AdminMerchantDataScope.limited(Set.of("M_A")));
        assertThat(result.comparisonAvailable()).isFalse();
        assertThat(result.rows()).allSatisfy(row -> assertThat(row.comparison()).isNull());
    }

    @Test
    void rejectsExplicitForeignMerchantAndKeepsEmptyScopeEmpty() {
        var query = filters(new AnalyticsQuery());
        query.setMerchantIds(List.of("M_C"));
        assertThatThrownBy(() -> service.channelBreakdown(query, visible)).isInstanceOf(ApiException.class);
        query.setMerchantIds(List.of());
        assertThat(service.channelBreakdown(query, AdminMerchantDataScope.limited(Set.of())).rows()).isEmpty();
    }

    private <T extends AnalyticsQuery> T filters(T query) {
        query.setBeginTime(LocalDateTime.of(2026, 11, 17, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 11, 18, 0, 0));
        query.setQueryTimeZone("Asia/Shanghai");
        query.setCurrency("USD");
        query.setMerchantIds(List.of("M_A"));
        return query;
    }
}
