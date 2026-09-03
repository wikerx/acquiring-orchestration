package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.AmountComparisonMetric;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.AmountMetric;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.ComparisonDirection;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.OverviewResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).queryForObject(
                summarySql.capture(), summaryParams.capture(), any(RowMapper.class));
        assertThat(summarySql.getAllValues()).allSatisfy(sql ->
                assertThat(sql).contains("o.merchant_id = :merchantId"));
        assertThat(summaryParams.getAllValues()).allSatisfy(params ->
                assertThat(params.getValue("merchantId")).isEqualTo("merchant-a"));
        ArgumentCaptor<String> listSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> listParams = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(6)).query(
                listSql.capture(), listParams.capture(), any(RowMapper.class));
        assertThat(listSql.getAllValues()).allSatisfy(sql ->
                assertThat(sql).contains("o.merchant_id = :merchantId"));
        assertThat(listSql.getAllValues()).anySatisfy(sql -> assertThat(sql).contains(
                "COALESCE(NULLIF(p.payment_brand, ''), COALESCE(p.payment_method, 'UNKNOWN')) AS dimension_key",
                "GROUP BY p.payment_method, p.payment_brand"));
        assertThat(listParams.getAllValues()).allSatisfy(params ->
                assertThat(params.getValue("merchantId")).isEqualTo("merchant-a"));
        log.info("结果：本期、上期主查询和6条列表查询均绑定merchant-a，支付工具分组兼容MySQL严格模式");
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
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).queryForObject(
                sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getAllValues()).allSatisfy(sql -> assertThat(sql).contains(
                "o.merchant_id = :merchantId",
                "p_filter.payment_brand = :paymentBrand"));
        assertThat(paramsCaptor.getAllValues()).allSatisfy(params -> {
            assertThat(params.getValue("paymentBrand")).isEqualTo("VISA");
            assertThat(params.getValue("querySqlTimeZone")).isEqualTo("+00:00");
        });
        assertThat(paramsCaptor.getAllValues()).extracting(params -> params.getValue("beginTime"))
                .containsExactly(
                        LocalDateTime.of(2026, 8, 1, 8, 0),
                        LocalDateTime.of(2026, 7, 25, 8, 0));
        assertThat(paramsCaptor.getAllValues()).extracting(params -> params.getValue("endTime"))
                .containsExactly(
                        LocalDateTime.of(2026, 8, 8, 8, 0),
                        LocalDateTime.of(2026, 8, 1, 8, 0));
        log.info("结果：认证商户隔离条件保留，UTC本期和向前平移7天的上期区间均转换为存储时区");
    }

    /** 今天和近三十天必须分别向前平移一日、三十日，并保留当前时刻进度。 */
    @Test
    void overviewShouldAlignTodayAndThirtyDayComparisonWindows() throws Exception {
        log.info("用例：验证今天和近三十天使用等长同进度上一周期");
        assertComparisonWindow(
                LocalDateTime.of(2026, 9, 3, 0, 0),
                LocalDateTime.of(2026, 9, 3, 11, 30),
                1,
                LocalDateTime.of(2026, 9, 2, 0, 0),
                LocalDateTime.of(2026, 9, 2, 11, 30));
        assertComparisonWindow(
                LocalDateTime.of(2026, 8, 5, 0, 0),
                LocalDateTime.of(2026, 9, 3, 11, 30),
                30,
                LocalDateTime.of(2026, 7, 6, 0, 0),
                LocalDateTime.of(2026, 8, 4, 11, 30));
        log.info("结果：今天向前平移1日，近三十天向前平移30日，结束时刻进度保持一致");
    }

    /** 上一期金额必须按币种和精度分别比较，并正确处理新增及归零场景。 */
    @Test
    @SuppressWarnings("unchecked")
    void overviewShouldCompareAlignedPeriodAmountsWithoutCrossCurrencyAggregation() {
        log.info("用例：验证商户首页按币种比较本期和上期成功金额");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        OverviewResponse currentSummary = summary(12L, 10L, 2L, 0L, 0L, "83.33");
        OverviewResponse previousSummary = summary(9L, 8L, 1L, 0L, 0L, "88.89");
        AtomicInteger summaryCalls = new AtomicInteger();
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> summaryCalls.getAndIncrement() == 0 ? currentSummary : previousSummary);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    MapSqlParameterSource params = invocation.getArgument(1);
                    if (!sql.contains("SUM(COALESCE(o.transaction_amount, 0))")) {
                        return Collections.emptyList();
                    }
                    LocalDateTime beginTime = (LocalDateTime) params.getValue("beginTime");
                    if (LocalDateTime.of(2026, 8, 1, 0, 0).equals(beginTime)) {
                        return List.of(
                                amount("USD", 2, "100.00", 10L),
                                amount("JPY", 0, "1200", 1L));
                    }
                    return List.of(
                            amount("USD", 2, "80.00", 8L),
                            amount("EUR", 2, "50.00", 2L));
                });
        JdbcMerchantTransactionAnalyticsQueryService service =
                new JdbcMerchantTransactionAnalyticsQueryService(jdbcTemplate);

        OverviewResponse result = service.overview("merchant-a", query());

        assertThat(result.getComparison().getPeriodDays()).isEqualTo(7);
        assertThat(result.getComparison().getPreviousTotalCount()).isEqualTo(9L);
        assertThat(result.getComparison().getPreviousSuccessRate()).isEqualByComparingTo("88.89");
        assertThat(result.getComparison().getSuccessAmounts()).hasSize(3);
        AmountComparisonMetric usd = result.getComparison().getSuccessAmounts().get(0);
        assertThat(usd.getCurrency()).isEqualTo("USD");
        assertThat(usd.getCurrentAmount()).isEqualByComparingTo("100.00");
        assertThat(usd.getPreviousAmount()).isEqualByComparingTo("80.00");
        assertThat(usd.getChangeAmount()).isEqualByComparingTo("20.00");
        assertThat(usd.getChangeRate()).isEqualByComparingTo("25.00");
        assertThat(usd.getChangeDirection()).isEqualTo(ComparisonDirection.INCREASE);
        AmountComparisonMetric jpy = result.getComparison().getSuccessAmounts().get(1);
        assertThat(jpy.getCurrency()).isEqualTo("JPY");
        assertThat(jpy.getCurrentAmount()).isEqualByComparingTo("1200");
        assertThat(jpy.getPreviousAmount()).isEqualByComparingTo("0");
        assertThat(jpy.getChangeRate()).isNull();
        assertThat(jpy.getChangeDirection()).isEqualTo(ComparisonDirection.NEW);
        AmountComparisonMetric eur = result.getComparison().getSuccessAmounts().get(2);
        assertThat(eur.getCurrency()).isEqualTo("EUR");
        assertThat(eur.getCurrentAmount()).isEqualByComparingTo("0");
        assertThat(eur.getPreviousAmount()).isEqualByComparingTo("50.00");
        assertThat(eur.getChangeRate()).isEqualByComparingTo("100.00");
        assertThat(eur.getChangeDirection()).isEqualTo(ComparisonDirection.DECREASE);
        log.info("结果：USD独立上涨25%，JPY识别为新增，EUR独立下降至零，未发生跨币种合并");
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

    /** 执行指定区间总览并校验服务端生成的上一周期查询边界。 */
    @SuppressWarnings("unchecked")
    private void assertComparisonWindow(LocalDateTime beginTime,
                                        LocalDateTime endTime,
                                        int expectedDays,
                                        LocalDateTime expectedPreviousBegin,
                                        LocalDateTime expectedPreviousEnd) throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong(anyString())).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<OverviewResponse>>getArgument(2).mapRow(resultSet, 0));
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcMerchantTransactionAnalyticsQueryService service =
                new JdbcMerchantTransactionAnalyticsQueryService(jdbcTemplate);
        AnalyticsQuery range = new AnalyticsQuery();
        range.setBeginTime(beginTime);
        range.setEndTime(endTime);

        OverviewResponse result = service.overview("merchant-a", range);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).queryForObject(
                anyString(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(result.getComparison().getPeriodDays()).isEqualTo(expectedDays);
        assertThat(paramsCaptor.getAllValues()).extracting(params -> params.getValue("beginTime"))
                .containsExactly(beginTime, expectedPreviousBegin);
        assertThat(paramsCaptor.getAllValues()).extracting(params -> params.getValue("endTime"))
                .containsExactly(endTime, expectedPreviousEnd);
    }

    /** 构造测试使用的总览计数结果。 */
    private OverviewResponse summary(long total,
                                     long success,
                                     long failed,
                                     long pending,
                                     long processing,
                                     String successRate) {
        OverviewResponse response = new OverviewResponse();
        response.setTotalCount(total);
        response.setSuccessCount(success);
        response.setFailedCount(failed);
        response.setPendingCount(pending);
        response.setProcessingCount(processing);
        response.setSuccessRate(new BigDecimal(successRate));
        return response;
    }

    /** 构造单币种成功金额测试数据。 */
    private AmountMetric amount(String currency, int exponent, String amount, long successCount) {
        AmountMetric metric = new AmountMetric();
        metric.setCurrency(currency);
        metric.setCurrencyExponent(exponent);
        metric.setAmount(new BigDecimal(amount));
        metric.setSuccessCount(successCount);
        return metric;
    }
}
