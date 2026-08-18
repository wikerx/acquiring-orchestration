package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ChannelPerformanceResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.FailureResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.OverviewResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ThreeDsResponse;
import com.scott.payment.component.core.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminTransactionAnalyticsQueryServiceTests
 * @date : 2026-08-07 10:00
 * @email : scott_x@163.com
 * @description : 管理端交易分析查询口径测试，验证首笔交易、终态成功率、币种分组和 31 天扫描边界。
 * @status : create
 */
@Slf4j
class JdbcAdminTransactionAnalyticsQueryServiceTests {

    /** 管理端总览必须使用首笔交易口径，并将处理中状态排除在成功率分母之外。 */
    @Test
    @SuppressWarnings("unchecked")
    void overviewShouldUseFirstOperationTerminalRateAndCurrencyGrouping() throws Exception {
        log.info("用例：验证管理端总览首笔交易、终态成功率和分币种聚合口径");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("total_count")).thenReturn(12L);
        when(resultSet.getLong("success_count")).thenReturn(8L);
        when(resultSet.getLong("failed_count")).thenReturn(2L);
        when(resultSet.getLong("pending_count")).thenReturn(1L);
        when(resultSet.getLong("processing_count")).thenReturn(1L);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<OverviewResponse>>getArgument(2).mapRow(resultSet, 0));
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcAdminTransactionAnalyticsQueryService service =
                new JdbcAdminTransactionAnalyticsQueryService(jdbcTemplate);
        AnalyticsQuery query = query(LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 8, 0, 0));

        OverviewResponse response = service.overview(query);

        assertThat(response.getSuccessRate()).isEqualByComparingTo("80.00");
        assertThat(response.getPendingCount()).isEqualTo(1L);
        assertThat(response.getProcessingCount()).isEqualTo(1L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
                "o.operation_sequence = :operationSequence",
                "o.transaction_type IN (:transactionTypes)",
                "o.transaction_date_time >= :beginTime",
                "o.transaction_date_time < :endTime");
        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(5)).query(
                listSqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(listSqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql).contains(
                "GROUP BY COALESCE(o.transaction_currency, 'UNKNOWN'), o.currency_exponent",
                "COUNT(1) AS success_count"));
        assertThat(listSqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql).contains(
                "COALESCE(NULLIF(p.payment_brand, ''), COALESCE(p.payment_method, 'UNKNOWN')) AS dimension_key",
                "GROUP BY p.payment_method, p.payment_brand"));
        log.info("结果：成功率为80.00%，处理中2笔未进入终态分母，金额和支付工具SQL均使用兼容严格模式的分组");
    }

    /** 多商户、支付品牌和查询时区必须转换为受控 SQL 条件及存储时区参数。 */
    @Test
    @SuppressWarnings("unchecked")
    void overviewShouldBindMerchantListPaymentBrandAndQueryTimezone() throws Exception {
        log.info("用例：验证多商户、Visa品牌和UTC查询时间绑定");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong(anyString())).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<OverviewResponse>>getArgument(2).mapRow(resultSet, 0));
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcAdminTransactionAnalyticsQueryService service =
                new JdbcAdminTransactionAnalyticsQueryService(jdbcTemplate);
        AnalyticsQuery query = query(LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 8, 0, 0));
        query.setMerchantIds(List.of("merchant-a", "merchant-b", "merchant-a"));
        query.setPaymentMethod("bank_card");
        query.setPaymentBrand("visa");
        query.setQueryTimeZone("UTC");

        service.overview(query);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains(
                "o.merchant_id IN (:merchantIds)",
                "p_filter.payment_method = :paymentMethod",
                "p_filter.payment_brand = :paymentBrand");
        MapSqlParameterSource parameters = paramsCaptor.getValue();
        assertThat(parameters.getValue("merchantIds")).isEqualTo(List.of("merchant-a", "merchant-b"));
        assertThat(parameters.getValue("paymentMethod")).isEqualTo("BANK_CARD");
        assertThat(parameters.getValue("paymentBrand")).isEqualTo("VISA");
        assertThat(parameters.getValue("beginTime")).isEqualTo(LocalDateTime.of(2026, 8, 1, 8, 0));
        assertThat(parameters.getValue("querySqlTimeZone")).isEqualTo("+00:00");
        log.info("结果：多商户去重后绑定2项，UTC时间转换为Asia/Shanghai存储时间，支付品牌为VISA");
    }

    /** 失败分析必须使用终态失败率，并将稳定失败原因码归入后台类别。 */
    @Test
    @SuppressWarnings("unchecked")
    void failuresShouldCalculateTerminalRateAndClassifyReasons() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet summary = mock(ResultSet.class);
        when(summary.getLong("success_count")).thenReturn(8L);
        when(summary.getLong("failed_count")).thenReturn(2L);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<OverviewResponse>>getArgument(2).mapRow(summary, 0));
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (!sql.contains("reason_code")) {
                        return Collections.emptyList();
                    }
                    ResultSet reason = mock(ResultSet.class);
                    when(reason.getString("reason_code")).thenReturn("RISK_REJECTED");
                    when(reason.getString("reason_message")).thenReturn("Risk rejected");
                    when(reason.getLong("total_count")).thenReturn(2L);
                    return List.of(invocation.<RowMapper<Object>>getArgument(2).mapRow(reason, 0));
                });
        JdbcAdminTransactionAnalyticsQueryService service =
                new JdbcAdminTransactionAnalyticsQueryService(jdbcTemplate);

        FailureResponse response = service.failures(query(
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 8, 0, 0)));

        assertThat(response.getTerminalCount()).isEqualTo(10L);
        assertThat(response.getFailureRate()).isEqualByComparingTo("20.00");
        assertThat(response.getAffectedMerchantCount()).isEqualTo(1L);
        assertThat(response.getCategories()).singleElement().satisfies(category -> {
            assertThat(category.getKey()).isEqualTo("RISK");
            assertThat(category.getTotalCount()).isEqualTo(2L);
        });
        assertThat(response.getReasons()).singleElement().satisfies(reason ->
                assertThat(reason.getPercentage()).isEqualByComparingTo("100.00"));
    }

    /** 渠道表现必须排除勾兑请求，且非终态请求不进入渠道成功率分母。 */
    @Test
    @SuppressWarnings("unchecked")
    void channelPerformanceShouldExcludeMatchingAndInFlightFromTerminalRate() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet summary = mock(ResultSet.class);
        when(summary.getLong("total_request_count")).thenReturn(10L);
        when(summary.getLong("completed_request_count")).thenReturn(8L);
        when(summary.getLong("successful_request_count")).thenReturn(6L);
        when(summary.getLong("failed_request_count")).thenReturn(1L);
        when(summary.getLong("timeout_request_count")).thenReturn(1L);
        when(summary.getLong("in_flight_request_count")).thenReturn(2L);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<ChannelPerformanceResponse>>getArgument(2)
                        .mapRow(summary, 0));
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcAdminTransactionAnalyticsQueryService service =
                new JdbcAdminTransactionAnalyticsQueryService(jdbcTemplate);

        ChannelPerformanceResponse response = service.channelPerformance(query(
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 8, 0, 0)));

        assertThat(response.getRequestSuccessRate()).isEqualByComparingTo("75.00");
        ArgumentCaptor<String> summarySql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                summarySql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(summarySql.getValue()).contains(
                "r.channel_match_flag = 0",
                "r.request_status IN (:channelTerminalStatuses)",
                "r.request_status NOT IN (:channelTerminalStatuses)",
                "r.request_status IN ('SUCCESS', 'FAILED') AND r.platform_success = 0");
    }

    /** 3DS分析必须将同一交易多个认证阶段聚合为一行，并保持成功优先于失败。 */
    @Test
    @SuppressWarnings("unchecked")
    void threeDsShouldDeduplicateStagesAndPreferAuthenticatedStatus() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet summary = mock(ResultSet.class);
        when(summary.getLong("authentication_transaction_count")).thenReturn(4L);
        when(summary.getLong("authenticated_count")).thenReturn(3L);
        when(summary.getLong("failed_count")).thenReturn(1L);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<ThreeDsResponse>>getArgument(2).mapRow(summary, 0));
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.eq(Long.class)))
                .thenReturn(8L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcAdminTransactionAnalyticsQueryService service =
                new JdbcAdminTransactionAnalyticsQueryService(jdbcTemplate);

        ThreeDsResponse response = service.threeDs(query(
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 8, 0, 0)));

        assertThat(response.getCoverageRate()).isEqualByComparingTo("50.00");
        assertThat(response.getAuthenticationSuccessRate()).isEqualByComparingTo("75.00");
        ArgumentCaptor<String> summarySql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                summarySql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(summarySql.getValue()).contains(
                "GROUP BY a.transaction_id, a.transaction_date_time",
                "WHEN SUM(CASE WHEN a.authentication_status = 'AUTHENTICATED'",
                "WHEN SUM(CASE WHEN a.authentication_status = 'FAILED'");
        ArgumentCaptor<String> dimensionSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(6)).query(
                dimensionSql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(dimensionSql.getAllValues()).allSatisfy(sql ->
                assertThat(sql).contains("GROUP BY a.transaction_id, a.transaction_date_time"));
    }

    /** 3DS分析只接受支付、授权和预授权，二次交易类型必须在执行SQL前被拒绝。 */
    @Test
    void threeDsShouldRejectFollowUpTransactionTypesBeforeSql() {
        for (String transactionType : List.of("REFUND", "VOID", "CAPTURE")) {
            NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
            JdbcAdminTransactionAnalyticsQueryService service =
                    new JdbcAdminTransactionAnalyticsQueryService(jdbcTemplate);
            AnalyticsQuery query = query(LocalDateTime.of(2026, 8, 1, 0, 0),
                    LocalDateTime.of(2026, 8, 8, 0, 0));
            query.setTransactionType(transactionType);

            assertThatThrownBy(() -> service.threeDs(query))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("unsupported analytics transaction type");
            verify(jdbcTemplate, never()).queryForObject(
                    anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
        }
    }

    /** 超过 31 天的分析请求必须在执行 SQL 前失败。 */
    @Test
    void overviewShouldRejectRangeLongerThanThirtyOneDaysBeforeSql() {
        log.info("用例：验证管理端交易分析拒绝超过31天的查询范围");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionAnalyticsQueryService service =
                new JdbcAdminTransactionAnalyticsQueryService(jdbcTemplate);
        AnalyticsQuery query = query(LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 7, 3, 0, 0));

        assertThatThrownBy(() -> service.overview(query)).isInstanceOf(ApiException.class);

        verify(jdbcTemplate, never()).queryForObject(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
        log.info("结果：超范围请求在数据库访问前被拒绝");
    }

    /** 非法查询时区必须在执行 SQL 前失败。 */
    @Test
    void overviewShouldRejectInvalidTimezoneBeforeSql() {
        log.info("用例：验证非法查询时区在数据库访问前被拒绝");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionAnalyticsQueryService service =
                new JdbcAdminTransactionAnalyticsQueryService(jdbcTemplate);
        AnalyticsQuery query = query(LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 8, 0, 0));
        query.setQueryTimeZone("Not/A-Timezone");

        assertThatThrownBy(() -> service.overview(query)).isInstanceOf(ApiException.class);

        verify(jdbcTemplate, never()).queryForObject(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
        log.info("结果：非法查询时区未触发数据库访问");
    }

    private AnalyticsQuery query(LocalDateTime beginTime, LocalDateTime endTime) {
        AnalyticsQuery query = new AnalyticsQuery();
        query.setBeginTime(beginTime);
        query.setEndTime(endTime);
        return query;
    }
}
