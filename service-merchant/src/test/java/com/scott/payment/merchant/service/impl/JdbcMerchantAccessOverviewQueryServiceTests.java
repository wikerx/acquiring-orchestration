package com.scott.payment.merchant.service.impl;

import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService.AccessRuleFacts;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService.MerchantProfileFacts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接入概览 JDBC 查询测试，保护商户范围和有效配置统计条件。
 */
class JdbcMerchantAccessOverviewQueryServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void shouldScopeMerchantProfileToAuthenticatedMerchant() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantProfileFacts facts = new MerchantProfileFacts(
                "ACTIVE", "PASSED", "ACTIVE", "USD", 2,
                LocalDateTime.of(2026, 11, 4, 10, 0));
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(facts));
        JdbcMerchantAccessOverviewQueryService service =
                new JdbcMerchantAccessOverviewQueryService(jdbcTemplate);

        assertThat(service.findMerchantProfile("M10000001")).isEqualTo(facts);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
                "FROM base_merchant_info",
                "merchant_id = :merchantId",
                "deleted = 0");
        assertThat(paramsCaptor.getValue().getValue("merchantId")).isEqualTo("M10000001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnOnlyNonSensitiveEffectivePaymentCapabilities() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        JdbcMerchantAccessOverviewQueryService service =
                new JdbcMerchantAccessOverviewQueryService(jdbcTemplate);

        service.listPaymentCapabilities("M10000001", LocalDateTime.of(2026, 11, 4, 10, 0));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        String sql = sqlCaptor.getValue();
        String projection = sql.substring(0, sql.indexOf("FROM merchant_channel_mid_binding"));
        assertThat(sql).contains(
                "binding.merchant_id = :merchantId",
                "mid.business_type = 'ACQUIRING'",
                "channel.support_acquiring = 1",
                "mid.payment_method_scope",
                "mid.currency_scope",
                "mid.card_brand_scope");
        assertThat(projection).doesNotContain(
                "channel_code",
                "channel_id",
                "mid_config_id",
                "merchant_id");
        assertThat(paramsCaptor.getValue().getValue("merchantId")).isEqualTo("M10000001");
    }

    @Test
    void shouldCountOnlyBindingsBackedByEnabledMidAndChannel() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        JdbcMerchantAccessOverviewQueryService service =
                new JdbcMerchantAccessOverviewQueryService(jdbcTemplate);

        assertThat(service.countActivePaymentBindings(
                "M10000001", LocalDateTime.of(2026, 11, 4, 10, 0))).isEqualTo(1L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(), any(SqlParameterSource.class), eq(Long.class));
        assertThat(sqlCaptor.getValue()).contains(
                "JOIN channel_mid_config mid",
                "mid.mid_status = 1",
                "mid.business_type = 'ACQUIRING'",
                "JOIN channel_info channel",
                "channel.channel_status = 1",
                "channel.support_acquiring = 1",
                "binding.merchant_id = :merchantId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldScopeIpWhitelistSummaryToAuthenticatedMerchant() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        AccessRuleFacts facts = new AccessRuleFacts(3, 1, 1, 1, 0,
                LocalDateTime.of(2026, 11, 4, 10, 0));
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(facts);
        JdbcMerchantAccessOverviewQueryService service =
                new JdbcMerchantAccessOverviewQueryService(jdbcTemplate);

        assertThat(service.summarizeIpWhitelists("M10000001")).isEqualTo(facts);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
                "FROM merchant_ip_whitelist",
                "merchant_id = :merchantId",
                "approval_status = 1 AND status = 1",
                "deleted = 0");
        assertThat(paramsCaptor.getValue().getValue("merchantId")).isEqualTo("M10000001");
    }

}
