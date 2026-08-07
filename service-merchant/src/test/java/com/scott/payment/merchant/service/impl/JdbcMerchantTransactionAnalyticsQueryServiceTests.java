package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.OverviewResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcMerchantTransactionAnalyticsQueryServiceTests
 * @date : 2026-08-07 10:00
 * @email : scott_x@163.com
 * @description : 商户端交易分析隔离测试，验证所有统计绑定认证商户号且失败分析不读取内部或渠道失败字段。
 * @status : create
 */
@Slf4j
class JdbcMerchantTransactionAnalyticsQueryServiceTests {

    /** 商户总览的全部 SQL 必须包含 merchant_id 谓词并绑定认证商户号。 */
    @Test
    @SuppressWarnings("unchecked")
    void overviewShouldBindAuthenticatedMerchantInEveryQuery() throws Exception {
        log.info("用例：验证商户交易总览每条SQL均绑定认证商户号");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong(anyString())).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<OverviewResponse>>getArgument(2).mapRow(resultSet, 0));
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcMerchantTransactionAnalyticsQueryService service =
                new JdbcMerchantTransactionAnalyticsQueryService(jdbcTemplate);

        service.overview("merchant-a", query());

        ArgumentCaptor<String> summarySql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> summaryParams = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                summarySql.capture(), summaryParams.capture(), any(RowMapper.class));
        assertThat(summarySql.getValue()).contains("o.merchant_id = :merchantId");
        assertThat(summaryParams.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        ArgumentCaptor<String> listSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> listParams = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(5)).query(
                listSql.capture(), listParams.capture(), any(RowMapper.class));
        assertThat(listSql.getAllValues()).allSatisfy(sql ->
                assertThat(sql).contains("o.merchant_id = :merchantId"));
        assertThat(listSql.getAllValues()).anySatisfy(sql -> assertThat(sql).contains(
                "COALESCE(NULLIF(p.payment_brand, ''), COALESCE(p.payment_method, 'UNKNOWN')) AS dimension_key",
                "GROUP BY p.payment_method, p.payment_brand"));
        assertThat(listParams.getAllValues()).allSatisfy(params ->
                assertThat(params.getValue("merchantId")).isEqualTo("merchant-a"));
        log.info("结果：总览主查询和5条维度查询均绑定merchant-a，支付工具分组兼容MySQL严格模式");
    }

    /** 失败原因聚合只能读取商户可见消息，不得引用内部原因或渠道响应。 */
    @Test
    @SuppressWarnings("unchecked")
    void failuresShouldOnlyUseMerchantVisibleMessage() throws Exception {
        log.info("用例：验证失败分析仅使用商户可见失败消息");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong(anyString())).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<OverviewResponse>>getArgument(2).mapRow(resultSet, 0));
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcMerchantTransactionAnalyticsQueryService service =
                new JdbcMerchantTransactionAnalyticsQueryService(jdbcTemplate);

        service.failures("merchant-a", query());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(3)).query(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql).contains("merchant_visible_message"));
        assertThat(sqlCaptor.getAllValues()).allSatisfy(sql -> assertThat(sql).doesNotContain(
                "fail_reason_code", "fail_reason_message", "channel_response", "channel_code", "channel_mid"));
        log.info("结果：失败原因SQL仅使用merchant_visible_message，未读取内部或渠道字段");
    }

    /** 支付品牌和查询时区必须绑定到商户隔离 SQL，页面时间转换为存储时区。 */
    @Test
    @SuppressWarnings("unchecked")
    void overviewShouldBindPaymentBrandAndConvertQueryTimezone() throws Exception {
        log.info("用例：验证商户统计绑定Visa品牌并转换UTC查询时间");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong(anyString())).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<OverviewResponse>>getArgument(2).mapRow(resultSet, 0));
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcMerchantTransactionAnalyticsQueryService service =
                new JdbcMerchantTransactionAnalyticsQueryService(jdbcTemplate);
        AnalyticsQuery query = query();
        query.setPaymentMethod("bank_card");
        query.setPaymentBrand("visa");
        query.setQueryTimeZone("UTC");

        service.overview("merchant-a", query);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
                "o.merchant_id = :merchantId",
                "p_filter.payment_brand = :paymentBrand");
        assertThat(paramsCaptor.getValue().getValue("paymentBrand")).isEqualTo("VISA");
        assertThat(paramsCaptor.getValue().getValue("beginTime")).isEqualTo(LocalDateTime.of(2026, 8, 1, 8, 0));
        assertThat(paramsCaptor.getValue().getValue("querySqlTimeZone")).isEqualTo("+00:00");
        log.info("结果：认证商户隔离条件保留，支付品牌为VISA，UTC时间转换为存储时区");
    }

    /** 缺少认证商户号时必须在执行 SQL 前失败。 */
    @Test
    void overviewShouldRejectMissingMerchantBeforeSql() {
        log.info("用例：验证缺少认证商户号时拒绝执行统计查询");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcMerchantTransactionAnalyticsQueryService service =
                new JdbcMerchantTransactionAnalyticsQueryService(jdbcTemplate);

        assertThatThrownBy(() -> service.overview(" ", query())).isInstanceOf(ApiException.class);

        verify(jdbcTemplate, never()).queryForObject(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
        log.info("结果：认证商户号缺失时未访问数据库");
    }

    private AnalyticsQuery query() {
        AnalyticsQuery query = new AnalyticsQuery();
        query.setBeginTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 8, 8, 0, 0));
        return query;
    }
}
