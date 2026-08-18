package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.AmountMetric;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ChannelMetric;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ChannelPerformanceResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ChannelTrendMetric;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.CountMetric;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.DimensionMetric;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.FailureReasonMetric;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.FailureResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.MerchantMetric;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.MerchantPerformanceResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.OverviewResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ThreeDsResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ThreeDsTrendMetric;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.TrendMetric;
import com.scott.payment.admin.service.AdminTransactionAnalyticsQueryService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminTransactionAnalyticsQueryService
 * @date : 2026-08-07 10:00
 * @email : scott_x@163.com
 * @description : 管理端交易分析 JDBC 实现，在 transaction 逻辑数据源按首笔交易、终态成功率和币种隔离口径执行最多 31 天的只读聚合。
 * @status : create
 */
@Service
public class JdbcAdminTransactionAnalyticsQueryService implements AdminTransactionAnalyticsQueryService {

    /** 交易操作逻辑表名，仅用于受控静态 SQL 拼装。 */
    private static final String TRANSACTION_OPERATION_TABLE = "transaction_operation";
    /** 支付工具摘要逻辑表名，仅用于受控静态 SQL 拼装。 */
    private static final String TRANSACTION_PAYMENT_METHOD_TABLE = "transaction_payment_method_info";
    /** 渠道请求逻辑表名，仅用于受控静态SQL拼装。 */
    private static final String TRANSACTION_CHANNEL_REQUEST_TABLE = "transaction_channel_request";
    /** 3DS认证逻辑表名，仅用于受控静态SQL拼装。 */
    private static final String TRANSACTION_AUTHENTICATION_TABLE = "transaction_authentication_info";
    /** 单次统计允许扫描的最大自然日跨度。 */
    private static final int MAX_QUERY_DAYS = 31;
    /** 管理端单次查询允许选择的最大商户数量。 */
    private static final int MAX_MERCHANT_FILTERS = 50;
    /** 商户表现接口允许返回的最大排行数量。 */
    private static final int MERCHANT_RESULT_LIMIT = 50;
    /** 首笔收单操作在交易操作表中的固定序号。 */
    private static final int FIRST_OPERATION_SEQUENCE = 1;
    /** 进入终态成功率分子的成功状态编码。 */
    private static final String STATUS_SUCCESS = "SUCCESS";
    /** 进入终态成功率分母的失败状态编码。 */
    private static final String STATUS_FAILED = "FAILED";
    /** 不进入终态成功率分母的待处理状态编码。 */
    private static final String STATUS_PENDING = "PENDING";
    /** 不进入终态成功率分母的处理中状态编码。 */
    private static final String STATUS_PROCESSING = "PROCESSING";
    /** 银行卡交易的统一支付方式编码。 */
    private static final String PAYMENT_METHOD_BANK_CARD = "BANK_CARD";
    /** 3DS认证类型编码。 */
    private static final String AUTHENTICATION_TYPE_THREE_DS = "3DS";
    /** 渠道请求终态集合；INIT和SENT不进入成功率分母。 */
    private static final List<String> CHANNEL_TERMINAL_STATUSES = List.of("SUCCESS", "FAILED", "TIMEOUT");
    /** 页面未指定时使用的查询时区，也是交易表时间解释时区。 */
    private static final String DEFAULT_QUERY_TIME_ZONE = "Asia/Shanghai";
    /** MySQL 统计 Asia/Shanghai 自然日时使用的固定偏移。 */
    private static final String STORAGE_SQL_TIME_ZONE = "+08:00";
    /** 允许进入首笔交易分析的收单交易类型集合。 */
    private static final Set<String> SUPPORTED_TRANSACTION_TYPES = Set.of(
            "PAYMENT", "AUTHORIZATION", "PRE_AUTHORIZATION");

    /** 查询专用 JDBC 模板，使用交易查询统一语句超时。 */
    private final NamedParameterJdbcTemplate jdbcTemplate;
    /** 在 transaction 逻辑数据源执行允许读写分离的只读聚合。 */
    private final TransactionLogicalReadExecutor transactionLogicalReadExecutor;

    /**
     * 创建测试和轻量集成场景使用的统计查询服务。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     */
    public JdbcAdminTransactionAnalyticsQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new TransactionLogicalReadExecutor());
    }

    /**
     * 创建生产环境管理端交易统计查询服务。
     *
     * @param dataSource dynamic-datasource 外层路由数据源
     * @param transactionLogicalReadExecutor 交易逻辑数据源只读执行器
     * @param shardingProperties 交易查询资源预算配置
     * @param queryJdbcTemplateFactory 查询专用 JDBC 模板工厂
     */
    @Autowired
    public JdbcAdminTransactionAnalyticsQueryService(DataSource dataSource,
                                                      TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                                      TransactionShardingProperties shardingProperties,
                                                      TransactionQueryJdbcTemplateFactory queryJdbcTemplateFactory) {
        this(queryJdbcTemplateFactory.create(dataSource, shardingProperties), transactionLogicalReadExecutor);
    }

    /**
     * 创建指定 JDBC 模板和逻辑数据源执行器的统计查询服务。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     * @param transactionLogicalReadExecutor 交易逻辑数据源只读执行器
     */
    public JdbcAdminTransactionAnalyticsQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                                      TransactionLogicalReadExecutor transactionLogicalReadExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionLogicalReadExecutor = transactionLogicalReadExecutor;
    }

    /**
     * 查询管理端交易总览。
     *
     * @param query 页面分析条件
     * @return 交易总览指标和图表序列
     */
    @Override
    public OverviewResponse overview(AnalyticsQuery query) {
        AnalyticsQuery safeQuery = normalize(query);
        return transactionLogicalReadExecutor.read(() -> overviewNormalized(safeQuery));
    }

    /**
     * 查询管理端商户表现。
     *
     * @param query 页面分析条件
     * @return 商户表现列表
     */
    @Override
    public MerchantPerformanceResponse merchantPerformance(AnalyticsQuery query) {
        AnalyticsQuery safeQuery = normalize(query);
        return transactionLogicalReadExecutor.read(() -> merchantPerformanceNormalized(safeQuery));
    }

    /**
     * 查询管理端失败分析。
     *
     * @param query 页面分析条件
     * @return 后台可见失败原因、趋势和渠道分布
     */
    @Override
    public FailureResponse failures(AnalyticsQuery query) {
        AnalyticsQuery safeQuery = normalize(query);
        return transactionLogicalReadExecutor.read(() -> failuresNormalized(safeQuery));
    }

    /**
     * 查询渠道请求和最终交易表现。
     *
     * @param query 页面分析条件
     * @return 渠道请求成功率、耗时和最终交易表现
     */
    @Override
    public ChannelPerformanceResponse channelPerformance(AnalyticsQuery query) {
        AnalyticsQuery safeQuery = normalize(query);
        return transactionLogicalReadExecutor.read(() -> channelPerformanceNormalized(safeQuery));
    }

    /**
     * 查询按交易去重的3DS认证分析。
     *
     * @param query 页面分析条件
     * @return 3DS覆盖率、认证、挑战和责任转移指标
     */
    @Override
    public ThreeDsResponse threeDs(AnalyticsQuery query) {
        AnalyticsQuery safeQuery = normalize(query);
        return transactionLogicalReadExecutor.read(() -> threeDsNormalized(safeQuery));
    }

    /** 在同一逻辑数据源上下文完成总览的全部聚合，避免跨数据源读取产生口径差异。 */
    private OverviewResponse overviewNormalized(AnalyticsQuery query) {
        OverviewResponse response = querySummary(query);
        response.setGeneratedAt(LocalDateTime.now(resolveQueryZone(query.getQueryTimeZone())));
        response.setSuccessAmounts(querySuccessAmounts(query));
        response.setTrend(fillTrendDates(query, queryTrend(query)));
        response.setStatusDistribution(queryDimension(query, "status"));
        response.setPaymentMethods(queryDimension(query, "paymentMethod"));
        response.setIssuerCountries(queryDimension(query, "issuerCountry"));
        return response;
    }

    /** 按总笔数截取商户排行，并仅为入榜商户查询分币种成功金额。 */
    private MerchantPerformanceResponse merchantPerformanceNormalized(AnalyticsQuery query) {
        MerchantPerformanceResponse response = new MerchantPerformanceResponse();
        response.setGeneratedAt(LocalDateTime.now(resolveQueryZone(query.getQueryTimeZone())));
        response.setMerchantCount(queryMerchantCount(query));
        List<MerchantMetric> merchants = queryMerchantMetrics(query);
        if (!merchants.isEmpty()) {
            Map<String, MerchantMetric> merchantMap = new LinkedHashMap<>();
            merchants.forEach(metric -> merchantMap.put(metric.getMerchantId(), metric));
            queryMerchantAmounts(query, new ArrayList<>(merchantMap.keySet())).forEach(entry -> {
                MerchantMetric metric = merchantMap.get(entry.getKey());
                if (metric != null) {
                    metric.getSuccessAmounts().add(entry.getValue());
                }
            });
        }
        response.setMerchants(merchants);
        return response;
    }

    /** 组合失败汇总、自然日趋势、原因分类和渠道分布。 */
    private FailureResponse failuresNormalized(AnalyticsQuery query) {
        OverviewResponse summary = querySummary(query);
        FailureResponse response = new FailureResponse();
        response.setGeneratedAt(LocalDateTime.now(resolveQueryZone(query.getQueryTimeZone())));
        response.setTerminalCount(summary.getSuccessCount() + summary.getFailedCount());
        response.setFailedCount(summary.getFailedCount());
        response.setFailureRate(percentage(summary.getFailedCount(), response.getTerminalCount()));
        response.setAffectedMerchantCount(queryAffectedMerchantCount(query));
        response.setTrend(fillTrendDates(query, queryFailureTrend(query)));
        List<FailureReasonMetric> reasons = queryFailureReasons(query);
        response.setCategories(failureCategories(reasons, response.getFailedCount()));
        reasons.stream()
                .sorted(Comparator.comparingLong(FailureReasonMetric::getTotalCount).reversed()
                        .thenComparing(FailureReasonMetric::getKey))
                .limit(10)
                .forEach(response.getReasons()::add);
        response.setChannels(queryFailureChannels(query, response.getFailedCount()));
        return response;
    }

    /** 组合请求级渠道指标，并合并最终交易口径，禁止混用两个成功率分母。 */
    private ChannelPerformanceResponse channelPerformanceNormalized(AnalyticsQuery query) {
        ChannelPerformanceResponse response = queryChannelSummary(query);
        response.setGeneratedAt(LocalDateTime.now(resolveQueryZone(query.getQueryTimeZone())));
        List<ChannelMetric> channels = queryChannelMetrics(query);
        mergeChannelTransactionMetrics(query, channels);
        response.setChannels(channels);
        response.setTrend(fillChannelTrendDates(query, queryChannelTrend(query)));
        response.setResponseCodes(queryChannelResponseCodes(query, response.getCompletedRequestCount()));
        return response;
    }

    /** 组合按交易去重的3DS指标，认证成功和交易成功分别计算。 */
    private ThreeDsResponse threeDsNormalized(AnalyticsQuery query) {
        ThreeDsResponse response = queryThreeDsSummary(query);
        response.setGeneratedAt(LocalDateTime.now(resolveQueryZone(query.getQueryTimeZone())));
        response.setEligibleCardTransactionCount(queryEligibleCardTransactionCount(query));
        response.setCoverageRate(percentage(response.getAuthenticationTransactionCount(),
                response.getEligibleCardTransactionCount()));
        response.setAuthenticationSuccessRate(percentage(response.getAuthenticatedCount(),
                response.getAuthenticatedCount() + response.getFailedCount()));
        response.setPaymentSuccessRate(percentage(response.getPaymentSuccessCount(),
                response.getPaymentSuccessCount() + response.getPaymentFailedCount()));
        response.setChallengeRate(percentage(response.getChallengeRequiredCount(),
                response.getAuthenticationTransactionCount()));
        response.setTrend(fillThreeDsTrendDates(query, queryThreeDsTrend(query)));
        response.setStatuses(queryThreeDsDimension(query, "authentication_status",
                response.getAuthenticationTransactionCount()));
        response.setVersions(queryThreeDsDimension(query, "three_ds_version",
                response.getAuthenticationTransactionCount()));
        response.setSources(queryThreeDsDimension(query, "authentication_source",
                response.getAuthenticationTransactionCount()));
        response.setChallenges(queryThreeDsDimension(query, "challenge_status",
                response.getAuthenticationTransactionCount()));
        response.setLiabilityShifts(queryThreeDsDimension(query, "liability_shift_status",
                response.getAuthenticationTransactionCount()));
        return response;
    }

    /** 查询核心计数指标，PENDING 和 PROCESSING 不进入终态成功率分母。 */
    private OverviewResponse querySummary(AnalyticsQuery query) {
        String sql = """
                SELECT COUNT(1) AS total_count,
                       SUM(CASE WHEN o.transaction_status = :successStatus THEN 1 ELSE 0 END) AS success_count,
                       SUM(CASE WHEN o.transaction_status = :failedStatus THEN 1 ELSE 0 END) AS failed_count,
                       SUM(CASE WHEN o.transaction_status = :pendingStatus THEN 1 ELSE 0 END) AS pending_count,
                       SUM(CASE WHEN o.transaction_status = :processingStatus THEN 1 ELSE 0 END) AS processing_count
                FROM %s o
                WHERE %s
                """.formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query));
        return jdbcTemplate.queryForObject(sql, params(query), (rs, rowNum) -> {
            OverviewResponse response = new OverviewResponse();
            response.setTotalCount(rs.getLong("total_count"));
            response.setSuccessCount(rs.getLong("success_count"));
            response.setFailedCount(rs.getLong("failed_count"));
            response.setPendingCount(rs.getLong("pending_count"));
            response.setProcessingCount(rs.getLong("processing_count"));
            response.setSuccessRate(successRate(response.getSuccessCount(), response.getFailedCount()));
            return response;
        });
    }

    /** 查询成功金额，SQL 按币种和精度分组，禁止跨币种相加。 */
    private List<AmountMetric> querySuccessAmounts(AnalyticsQuery query) {
        String sql = """
                SELECT COALESCE(o.transaction_currency, 'UNKNOWN') AS currency,
                       o.currency_exponent,
                       COALESCE(SUM(COALESCE(o.transaction_amount, 0)), 0) AS amount,
                       COUNT(1) AS success_count
                FROM %s o
                WHERE %s
                  AND o.transaction_status = :successStatus
                GROUP BY COALESCE(o.transaction_currency, 'UNKNOWN'), o.currency_exponent
                ORDER BY amount DESC
                """.formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query));
        return jdbcTemplate.query(sql, params(query), (rs, rowNum) -> {
            AmountMetric metric = new AmountMetric();
            metric.setCurrency(rs.getString("currency"));
            metric.setCurrencyExponent(nullableInteger(rs.getObject("currency_exponent")));
            metric.setAmount(rs.getBigDecimal("amount"));
            metric.setSuccessCount(rs.getLong("success_count"));
            return metric;
        });
    }

    /** 查询每日计数趋势；金额不进入趋势主轴，避免多币种形成伪总额。 */
    private List<TrendMetric> queryTrend(AnalyticsQuery query) {
        String sql = """
                SELECT DATE_FORMAT(CONVERT_TZ(o.transaction_date_time, :storageTimeZone, :querySqlTimeZone), '%%Y-%%m-%%d') AS stat_date,
                       COUNT(1) AS total_count,
                       SUM(CASE WHEN o.transaction_status = :successStatus THEN 1 ELSE 0 END) AS success_count,
                       SUM(CASE WHEN o.transaction_status = :failedStatus THEN 1 ELSE 0 END) AS failed_count,
                       SUM(CASE WHEN o.transaction_status = :pendingStatus THEN 1 ELSE 0 END) AS pending_count,
                       SUM(CASE WHEN o.transaction_status = :processingStatus THEN 1 ELSE 0 END) AS processing_count
                FROM %s o
                WHERE %s
                GROUP BY DATE_FORMAT(CONVERT_TZ(o.transaction_date_time, :storageTimeZone, :querySqlTimeZone), '%%Y-%%m-%%d')
                ORDER BY stat_date ASC
                """.formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query));
        return jdbcTemplate.query(sql, params(query), (rs, rowNum) -> {
            TrendMetric metric = new TrendMetric();
            metric.setDate(rs.getString("stat_date"));
            metric.setTotalCount(rs.getLong("total_count"));
            metric.setSuccessCount(rs.getLong("success_count"));
            metric.setFailedCount(rs.getLong("failed_count"));
            metric.setPendingCount(rs.getLong("pending_count"));
            metric.setProcessingCount(rs.getLong("processing_count"));
            metric.setSuccessRate(successRate(metric.getSuccessCount(), metric.getFailedCount()));
            return metric;
        });
    }

    /** 根据受控维度选择统计 SQL，调用方不能传入表名、列名或任意表达式。 */
    private List<DimensionMetric> queryDimension(AnalyticsQuery query, String dimension) {
        String select;
        String metadataSelect = "";
        String groupBy;
        String join = "";
        if ("paymentMethod".equals(dimension)) {
            select = "COALESCE(NULLIF(p.payment_brand, ''), COALESCE(p.payment_method, 'UNKNOWN'))";
            metadataSelect = ", COALESCE(p.payment_method, 'UNKNOWN') AS payment_method, NULLIF(p.payment_brand, '') AS payment_brand";
            groupBy = "p.payment_method, p.payment_brand";
            join = paymentMethodJoin();
        } else if ("issuerCountry".equals(dimension)) {
            select = "COALESCE(p.issuer_country, 'UNKNOWN')";
            groupBy = select;
            join = paymentMethodJoin();
        } else if ("status".equals(dimension)) {
            select = "COALESCE(o.transaction_status, 'UNKNOWN')";
            groupBy = select;
        } else {
            throw new IllegalArgumentException("unsupported analytics dimension");
        }
        String sql = """
                SELECT %s AS dimension_key%s,
                       COUNT(1) AS total_count,
                       SUM(CASE WHEN o.transaction_status = :successStatus THEN 1 ELSE 0 END) AS success_count,
                       SUM(CASE WHEN o.transaction_status = :failedStatus THEN 1 ELSE 0 END) AS failed_count,
                       SUM(CASE WHEN o.transaction_status = :pendingStatus THEN 1 ELSE 0 END) AS pending_count,
                       SUM(CASE WHEN o.transaction_status = :processingStatus THEN 1 ELSE 0 END) AS processing_count
                FROM %s o
                %s
                WHERE %s
                GROUP BY %s
                ORDER BY total_count DESC
                LIMIT 12
                """.formatted(select, metadataSelect, TRANSACTION_OPERATION_TABLE, join, baseWhere(query), groupBy);
        return jdbcTemplate.query(sql, params(query), (rs, rowNum) -> {
            DimensionMetric metric = new DimensionMetric();
            metric.setKey(rs.getString("dimension_key"));
            if ("paymentMethod".equals(dimension)) {
                metric.setPaymentMethod(rs.getString("payment_method"));
                metric.setPaymentBrand(rs.getString("payment_brand"));
            }
            metric.setTotalCount(rs.getLong("total_count"));
            metric.setSuccessCount(rs.getLong("success_count"));
            metric.setFailedCount(rs.getLong("failed_count"));
            metric.setPendingCount(rs.getLong("pending_count"));
            metric.setProcessingCount(rs.getLong("processing_count"));
            metric.setSuccessRate(successRate(metric.getSuccessCount(), metric.getFailedCount()));
            return metric;
        });
    }

    /** 查询筛选范围内产生交易的商户总数。 */
    private long queryMerchantCount(AnalyticsQuery query) {
        String sql = "SELECT COUNT(DISTINCT o.merchant_id) FROM %s o WHERE %s"
                .formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query));
        Long value = jdbcTemplate.queryForObject(sql, params(query), Long.class);
        return value == null ? 0L : value;
    }

    /** 查询最多 50 个商户的计数和终态成功率。 */
    private List<MerchantMetric> queryMerchantMetrics(AnalyticsQuery query) {
        String sql = """
                SELECT o.merchant_id,
                       COUNT(1) AS total_count,
                       SUM(CASE WHEN o.transaction_status = :successStatus THEN 1 ELSE 0 END) AS success_count,
                       SUM(CASE WHEN o.transaction_status = :failedStatus THEN 1 ELSE 0 END) AS failed_count,
                       SUM(CASE WHEN o.transaction_status IN (:inFlightStatuses) THEN 1 ELSE 0 END) AS in_flight_count
                FROM %s o
                WHERE %s
                GROUP BY o.merchant_id
                ORDER BY total_count DESC, o.merchant_id ASC
                LIMIT %d
                """.formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query), MERCHANT_RESULT_LIMIT);
        return jdbcTemplate.query(sql, params(query), (rs, rowNum) -> {
            MerchantMetric metric = new MerchantMetric();
            metric.setMerchantId(rs.getString("merchant_id"));
            metric.setTotalCount(rs.getLong("total_count"));
            metric.setSuccessCount(rs.getLong("success_count"));
            metric.setFailedCount(rs.getLong("failed_count"));
            metric.setInFlightCount(rs.getLong("in_flight_count"));
            metric.setSuccessRate(successRate(metric.getSuccessCount(), metric.getFailedCount()));
            return metric;
        });
    }

    /** 查询入榜商户的分币种成功金额，返回值的键为商户号。 */
    private List<Map.Entry<String, AmountMetric>> queryMerchantAmounts(AnalyticsQuery query,
                                                                       List<String> merchantIds) {
        String sql = """
                SELECT o.merchant_id,
                       COALESCE(o.transaction_currency, 'UNKNOWN') AS currency,
                       o.currency_exponent,
                       COALESCE(SUM(COALESCE(o.transaction_amount, 0)), 0) AS amount,
                       COUNT(1) AS success_count
                FROM %s o
                WHERE %s
                  AND o.transaction_status = :successStatus
                  AND o.merchant_id IN (:rankedMerchantIds)
                GROUP BY o.merchant_id, COALESCE(o.transaction_currency, 'UNKNOWN'), o.currency_exponent
                ORDER BY o.merchant_id ASC, amount DESC
                """.formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query));
        MapSqlParameterSource parameters = params(query).addValue("rankedMerchantIds", merchantIds);
        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> {
            AmountMetric metric = new AmountMetric();
            metric.setCurrency(rs.getString("currency"));
            metric.setCurrencyExponent(nullableInteger(rs.getObject("currency_exponent")));
            metric.setAmount(rs.getBigDecimal("amount"));
            metric.setSuccessCount(rs.getLong("success_count"));
            return Map.entry(rs.getString("merchant_id"), metric);
        });
    }

    /** 查询出现失败交易的商户数量。 */
    private long queryAffectedMerchantCount(AnalyticsQuery query) {
        String sql = "SELECT COUNT(DISTINCT o.merchant_id) FROM %s o WHERE %s AND o.transaction_status = :failedStatus"
                .formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query));
        Long value = jdbcTemplate.queryForObject(sql, params(query), Long.class);
        return value == null ? 0L : value;
    }

    /** 查询失败交易自然日趋势，只返回失败笔数并由公共方法补齐无数据日期。 */
    private List<TrendMetric> queryFailureTrend(AnalyticsQuery query) {
        String sql = """
                SELECT DATE_FORMAT(CONVERT_TZ(o.transaction_date_time, :storageTimeZone, :querySqlTimeZone), '%%Y-%%m-%%d') AS stat_date,
                       COUNT(1) AS failed_count
                FROM %s o
                WHERE %s
                  AND o.transaction_status = :failedStatus
                GROUP BY DATE_FORMAT(CONVERT_TZ(o.transaction_date_time, :storageTimeZone, :querySqlTimeZone), '%%Y-%%m-%%d')
                ORDER BY stat_date ASC
                """.formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query));
        return jdbcTemplate.query(sql, params(query), (rs, rowNum) -> {
            TrendMetric metric = new TrendMetric();
            metric.setDate(rs.getString("stat_date"));
            metric.setTotalCount(rs.getLong("failed_count"));
            metric.setFailedCount(metric.getTotalCount());
            return metric;
        });
    }

    /** 查询全部失败原因码后在Java中完成稳定分类，避免遗漏低频原因对类别总量的贡献。 */
    private List<FailureReasonMetric> queryFailureReasons(AnalyticsQuery query) {
        String sql = """
                SELECT COALESCE(NULLIF(o.fail_reason_code, ''), 'UNKNOWN') AS reason_code,
                       MAX(COALESCE(NULLIF(o.fail_reason_message, ''), 'No failure description')) AS reason_message,
                       COUNT(1) AS total_count
                FROM %s o
                WHERE %s
                  AND o.transaction_status = :failedStatus
                GROUP BY COALESCE(NULLIF(o.fail_reason_code, ''), 'UNKNOWN')
                ORDER BY total_count DESC, reason_code ASC
                """.formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query));
        return jdbcTemplate.query(sql, params(query), (rs, rowNum) -> {
            FailureReasonMetric metric = new FailureReasonMetric();
            metric.setKey(rs.getString("reason_code"));
            metric.setMessage(rs.getString("reason_message"));
            metric.setCategory(failureCategory(metric.getKey()));
            metric.setTotalCount(rs.getLong("total_count"));
            return metric;
        });
    }

    /** 按稳定失败原因码归类，未知或历史编码统一进入OTHER且不丢失数量。 */
    private String failureCategory(String reasonCode) {
        return switch (reasonCode == null ? "" : reasonCode) {
            case "RISK_REJECTED" -> "RISK";
            case "ROUTE_FAILED", "CHANNEL_UNSUPPORTED" -> "ROUTING";
            case "EXCHANGE_RATE_NOT_FOUND" -> "EXCHANGE_RATE";
            case "CHANNEL_REQUEST_FAILED", "CHANNEL_RESPONSE_INVALID", "CHANNEL_TIMEOUT" -> "CHANNEL";
            case "STATE_TRANSITION_DENIED" -> "STATE_MACHINE";
            default -> "OTHER";
        };
    }

    /** 汇总全部失败原因类别，并为原因排行补充失败总量占比。 */
    private List<CountMetric> failureCategories(List<FailureReasonMetric> reasons, long failedCount) {
        Map<String, Long> counts = new LinkedHashMap<>();
        reasons.forEach(reason -> {
            reason.setPercentage(percentage(reason.getTotalCount(), failedCount));
            counts.merge(reason.getCategory(), reason.getTotalCount(), Long::sum);
        });
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(entry -> countMetric(entry.getKey(), entry.getValue(), failedCount))
                .toList();
    }

    /** 查询失败交易渠道分布，路由前失败统一记为NO_CHANNEL。 */
    private List<CountMetric> queryFailureChannels(AnalyticsQuery query, long failedCount) {
        String sql = """
                SELECT COALESCE(NULLIF(o.channel_code, ''), 'NO_CHANNEL') AS dimension_key,
                       COUNT(1) AS total_count
                FROM %s o
                WHERE %s
                  AND o.transaction_status = :failedStatus
                GROUP BY COALESCE(NULLIF(o.channel_code, ''), 'NO_CHANNEL')
                ORDER BY total_count DESC, dimension_key ASC
                LIMIT 12
                """.formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query));
        return jdbcTemplate.query(sql, params(query), (rs, rowNum) ->
                countMetric(rs.getString("dimension_key"), rs.getLong("total_count"), failedCount));
    }

    /** 查询非勾兑业务渠道请求汇总，未完成请求不进入请求成功率分母。 */
    private ChannelPerformanceResponse queryChannelSummary(AnalyticsQuery query) {
        String sql = """
                SELECT COUNT(1) AS total_request_count,
                       SUM(CASE WHEN r.request_status IN (:channelTerminalStatuses) THEN 1 ELSE 0 END) AS completed_request_count,
                       SUM(CASE WHEN r.request_status IN (:channelTerminalStatuses) AND r.platform_success = 1 THEN 1 ELSE 0 END) AS successful_request_count,
                       SUM(CASE WHEN r.request_status IN ('SUCCESS', 'FAILED') AND r.platform_success = 0 THEN 1 ELSE 0 END) AS failed_request_count,
                       SUM(CASE WHEN r.request_status = 'TIMEOUT' THEN 1 ELSE 0 END) AS timeout_request_count,
                       SUM(CASE WHEN r.request_status NOT IN (:channelTerminalStatuses) THEN 1 ELSE 0 END) AS in_flight_request_count,
                       COALESCE(AVG(CASE WHEN r.request_status IN (:channelTerminalStatuses) THEN r.duration_millis END), 0) AS average_duration_millis,
                       COALESCE(MAX(CASE WHEN r.request_status IN (:channelTerminalStatuses) THEN r.duration_millis END), 0) AS maximum_duration_millis
                FROM %s r
                JOIN %s o ON o.transaction_id = r.transaction_id
                               AND o.transaction_date_time = r.transaction_date_time
                WHERE %s
                """.formatted(TRANSACTION_CHANNEL_REQUEST_TABLE, TRANSACTION_OPERATION_TABLE, channelWhere(query));
        return jdbcTemplate.queryForObject(sql, params(query), (rs, rowNum) -> {
            ChannelPerformanceResponse response = new ChannelPerformanceResponse();
            response.setTotalRequestCount(rs.getLong("total_request_count"));
            response.setCompletedRequestCount(rs.getLong("completed_request_count"));
            response.setSuccessfulRequestCount(rs.getLong("successful_request_count"));
            response.setFailedRequestCount(rs.getLong("failed_request_count"));
            response.setTimeoutRequestCount(rs.getLong("timeout_request_count"));
            response.setInFlightRequestCount(rs.getLong("in_flight_request_count"));
            response.setRequestSuccessRate(percentage(response.getSuccessfulRequestCount(),
                    response.getCompletedRequestCount()));
            response.setAverageDurationMillis(scaleDuration(rs.getBigDecimal("average_duration_millis")));
            response.setMaximumDurationMillis(rs.getLong("maximum_duration_millis"));
            return response;
        });
    }

    /** 查询各渠道请求量、成功率和耗时。 */
    private List<ChannelMetric> queryChannelMetrics(AnalyticsQuery query) {
        String sql = """
                SELECT COALESCE(NULLIF(r.channel_code, ''), 'UNKNOWN') AS channel_code,
                       COUNT(1) AS total_request_count,
                       SUM(CASE WHEN r.request_status IN (:channelTerminalStatuses) THEN 1 ELSE 0 END) AS completed_request_count,
                       SUM(CASE WHEN r.request_status IN (:channelTerminalStatuses) AND r.platform_success = 1 THEN 1 ELSE 0 END) AS successful_request_count,
                       SUM(CASE WHEN r.request_status IN ('SUCCESS', 'FAILED') AND r.platform_success = 0 THEN 1 ELSE 0 END) AS failed_request_count,
                       SUM(CASE WHEN r.request_status = 'TIMEOUT' THEN 1 ELSE 0 END) AS timeout_request_count,
                       SUM(CASE WHEN r.request_status NOT IN (:channelTerminalStatuses) THEN 1 ELSE 0 END) AS in_flight_request_count,
                       COALESCE(AVG(CASE WHEN r.request_status IN (:channelTerminalStatuses) THEN r.duration_millis END), 0) AS average_duration_millis,
                       COALESCE(MAX(CASE WHEN r.request_status IN (:channelTerminalStatuses) THEN r.duration_millis END), 0) AS maximum_duration_millis
                FROM %s r
                JOIN %s o ON o.transaction_id = r.transaction_id
                               AND o.transaction_date_time = r.transaction_date_time
                WHERE %s
                GROUP BY COALESCE(NULLIF(r.channel_code, ''), 'UNKNOWN')
                ORDER BY total_request_count DESC, channel_code ASC
                LIMIT 50
                """.formatted(TRANSACTION_CHANNEL_REQUEST_TABLE, TRANSACTION_OPERATION_TABLE, channelWhere(query));
        return jdbcTemplate.query(sql, params(query), (rs, rowNum) -> {
            ChannelMetric metric = new ChannelMetric();
            metric.setChannelCode(rs.getString("channel_code"));
            metric.setTotalRequestCount(rs.getLong("total_request_count"));
            metric.setCompletedRequestCount(rs.getLong("completed_request_count"));
            metric.setSuccessfulRequestCount(rs.getLong("successful_request_count"));
            metric.setFailedRequestCount(rs.getLong("failed_request_count"));
            metric.setTimeoutRequestCount(rs.getLong("timeout_request_count"));
            metric.setInFlightRequestCount(rs.getLong("in_flight_request_count"));
            metric.setRequestSuccessRate(percentage(metric.getSuccessfulRequestCount(),
                    metric.getCompletedRequestCount()));
            metric.setAverageDurationMillis(scaleDuration(rs.getBigDecimal("average_duration_millis")));
            metric.setMaximumDurationMillis(rs.getLong("maximum_duration_millis"));
            return metric;
        });
    }

    /** 为渠道请求表现补充首笔交易最终状态，两个口径独立计算并在DTO中分别命名。 */
    private void mergeChannelTransactionMetrics(AnalyticsQuery query, List<ChannelMetric> channels) {
        Map<String, ChannelMetric> indexed = new LinkedHashMap<>();
        channels.forEach(metric -> indexed.put(metric.getChannelCode(), metric));
        String sql = """
                SELECT COALESCE(NULLIF(o.channel_code, ''), 'UNKNOWN') AS channel_code,
                       COUNT(1) AS transaction_count,
                       SUM(CASE WHEN o.transaction_status = :successStatus THEN 1 ELSE 0 END) AS success_count,
                       SUM(CASE WHEN o.transaction_status = :failedStatus THEN 1 ELSE 0 END) AS failed_count
                FROM %s o
                WHERE %s
                  AND o.channel_code IS NOT NULL
                GROUP BY COALESCE(NULLIF(o.channel_code, ''), 'UNKNOWN')
                ORDER BY transaction_count DESC, channel_code ASC
                LIMIT 50
                """.formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query));
        jdbcTemplate.query(sql, params(query), rs -> {
            String channelCode = rs.getString("channel_code");
            ChannelMetric metric = indexed.computeIfAbsent(channelCode, key -> {
                ChannelMetric created = new ChannelMetric();
                created.setChannelCode(key);
                channels.add(created);
                return created;
            });
            metric.setTransactionCount(rs.getLong("transaction_count"));
            metric.setTransactionSuccessCount(rs.getLong("success_count"));
            metric.setTransactionFailedCount(rs.getLong("failed_count"));
            metric.setTransactionSuccessRate(percentage(metric.getTransactionSuccessCount(),
                    metric.getTransactionSuccessCount() + metric.getTransactionFailedCount()));
        });
        channels.sort(Comparator.comparingLong(ChannelMetric::getTotalRequestCount).reversed()
                .thenComparing(ChannelMetric::getChannelCode));
    }

    /** 查询渠道请求自然日趋势，超时与其他失败拆开以避免图表重复计数。 */
    private List<ChannelTrendMetric> queryChannelTrend(AnalyticsQuery query) {
        String sql = """
                SELECT DATE_FORMAT(CONVERT_TZ(r.request_start_time, :storageTimeZone, :querySqlTimeZone), '%%Y-%%m-%%d') AS stat_date,
                       COUNT(1) AS total_request_count,
                       SUM(CASE WHEN r.request_status IN (:channelTerminalStatuses) AND r.platform_success = 1 THEN 1 ELSE 0 END) AS successful_request_count,
                       SUM(CASE WHEN r.request_status IN ('SUCCESS', 'FAILED') AND r.platform_success = 0 THEN 1 ELSE 0 END) AS failed_request_count,
                       SUM(CASE WHEN r.request_status = 'TIMEOUT' THEN 1 ELSE 0 END) AS timeout_request_count,
                       SUM(CASE WHEN r.request_status NOT IN (:channelTerminalStatuses) THEN 1 ELSE 0 END) AS in_flight_request_count
                FROM %s r
                JOIN %s o ON o.transaction_id = r.transaction_id
                               AND o.transaction_date_time = r.transaction_date_time
                WHERE %s
                GROUP BY DATE_FORMAT(CONVERT_TZ(r.request_start_time, :storageTimeZone, :querySqlTimeZone), '%%Y-%%m-%%d')
                ORDER BY stat_date ASC
                """.formatted(TRANSACTION_CHANNEL_REQUEST_TABLE, TRANSACTION_OPERATION_TABLE, channelWhere(query));
        return jdbcTemplate.query(sql, params(query), (rs, rowNum) -> {
            ChannelTrendMetric metric = new ChannelTrendMetric();
            metric.setDate(rs.getString("stat_date"));
            metric.setTotalRequestCount(rs.getLong("total_request_count"));
            metric.setSuccessfulRequestCount(rs.getLong("successful_request_count"));
            metric.setFailedRequestCount(rs.getLong("failed_request_count"));
            metric.setTimeoutRequestCount(rs.getLong("timeout_request_count"));
            metric.setInFlightRequestCount(rs.getLong("in_flight_request_count"));
            long completed = metric.getSuccessfulRequestCount() + metric.getFailedRequestCount()
                    + metric.getTimeoutRequestCount();
            metric.setRequestSuccessRate(percentage(metric.getSuccessfulRequestCount(), completed));
            return metric;
        });
    }

    /** 查询渠道响应码分布，优先使用收单码，其次网关码和平台结果码。 */
    private List<CountMetric> queryChannelResponseCodes(AnalyticsQuery query, long completedCount) {
        String sql = """
                SELECT COALESCE(NULLIF(r.acquirer_code, ''), NULLIF(r.gateway_code, ''),
                                NULLIF(r.platform_result_code, ''), 'UNKNOWN') AS dimension_key,
                       COUNT(1) AS total_count
                FROM %s r
                JOIN %s o ON o.transaction_id = r.transaction_id
                               AND o.transaction_date_time = r.transaction_date_time
                WHERE %s
                  AND r.request_status IN (:channelTerminalStatuses)
                GROUP BY COALESCE(NULLIF(r.acquirer_code, ''), NULLIF(r.gateway_code, ''),
                                  NULLIF(r.platform_result_code, ''), 'UNKNOWN')
                ORDER BY total_count DESC, dimension_key ASC
                LIMIT 12
                """.formatted(TRANSACTION_CHANNEL_REQUEST_TABLE, TRANSACTION_OPERATION_TABLE, channelWhere(query));
        return jdbcTemplate.query(sql, params(query), (rs, rowNum) ->
                countMetric(rs.getString("dimension_key"), rs.getLong("total_count"), completedCount));
    }

    /** 构造渠道请求分析条件，显式携带两个逻辑表的分片时间谓词。 */
    private String channelWhere(AnalyticsQuery query) {
        return "r.deleted = 0 AND r.channel_match_flag = 0"
                + " AND r.transaction_date_time >= :beginTime AND r.transaction_date_time < :endTime"
                + " AND " + baseWhere(query);
    }

    /** 查询筛选范围内首笔银行卡交易数，作为3DS覆盖率分母。 */
    private long queryEligibleCardTransactionCount(AnalyticsQuery query) {
        String sql = """
                SELECT COUNT(1)
                FROM %s o
                WHERE %s
                  AND EXISTS (
                    SELECT 1 FROM %s p_card
                    WHERE p_card.transaction_id = o.transaction_id
                      AND p_card.transaction_date_time = o.transaction_date_time
                      AND p_card.payment_method = :cardPaymentMethod
                  )
                """.formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query), TRANSACTION_PAYMENT_METHOD_TABLE);
        Long value = jdbcTemplate.queryForObject(sql, params(query), Long.class);
        return value == null ? 0L : value;
    }

    /** 查询按交易去重后的3DS汇总，认证状态优先级为成功、失败、处理中。 */
    private ThreeDsResponse queryThreeDsSummary(AnalyticsQuery query) {
        String sql = """
                SELECT COUNT(1) AS authentication_transaction_count,
                       SUM(CASE WHEN d.authentication_status = 'AUTHENTICATED' THEN 1 ELSE 0 END) AS authenticated_count,
                       SUM(CASE WHEN d.authentication_status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,
                       SUM(CASE WHEN d.authentication_status = 'PROCESSING' THEN 1 ELSE 0 END) AS processing_count,
                       SUM(CASE WHEN d.transaction_status = :successStatus THEN 1 ELSE 0 END) AS payment_success_count,
                       SUM(CASE WHEN d.transaction_status = :failedStatus THEN 1 ELSE 0 END) AS payment_failed_count,
                       SUM(CASE WHEN d.challenge_required = 1 THEN 1 ELSE 0 END) AS challenge_required_count,
                       SUM(CASE WHEN d.challenge_status = 'COMPLETED' THEN 1 ELSE 0 END) AS challenge_completed_count,
                       SUM(CASE WHEN d.challenge_status = 'FAILED' THEN 1 ELSE 0 END) AS challenge_failed_count,
                       SUM(CASE WHEN d.liability_shift_status = 'SHIFTED' THEN 1 ELSE 0 END) AS liability_shifted_count,
                       SUM(CASE WHEN d.liability_shift_status = 'NOT_SHIFTED' THEN 1 ELSE 0 END) AS liability_not_shifted_count,
                       SUM(CASE WHEN d.liability_shift_status = 'UNKNOWN' THEN 1 ELSE 0 END) AS liability_unknown_count
                FROM (%s) d
                """.formatted(threeDsTransactionSubquery(query));
        return jdbcTemplate.queryForObject(sql, params(query), (rs, rowNum) -> {
            ThreeDsResponse response = new ThreeDsResponse();
            response.setAuthenticationTransactionCount(rs.getLong("authentication_transaction_count"));
            response.setAuthenticatedCount(rs.getLong("authenticated_count"));
            response.setFailedCount(rs.getLong("failed_count"));
            response.setProcessingCount(rs.getLong("processing_count"));
            response.setPaymentSuccessCount(rs.getLong("payment_success_count"));
            response.setPaymentFailedCount(rs.getLong("payment_failed_count"));
            response.setChallengeRequiredCount(rs.getLong("challenge_required_count"));
            response.setChallengeCompletedCount(rs.getLong("challenge_completed_count"));
            response.setChallengeFailedCount(rs.getLong("challenge_failed_count"));
            response.setLiabilityShiftedCount(rs.getLong("liability_shifted_count"));
            response.setLiabilityNotShiftedCount(rs.getLong("liability_not_shifted_count"));
            response.setLiabilityUnknownCount(rs.getLong("liability_unknown_count"));
            return response;
        });
    }

    /** 查询按交易去重的3DS自然日趋势。 */
    private List<ThreeDsTrendMetric> queryThreeDsTrend(AnalyticsQuery query) {
        String sql = """
                SELECT d.stat_date,
                       COUNT(1) AS total_count,
                       SUM(CASE WHEN d.authentication_status = 'AUTHENTICATED' THEN 1 ELSE 0 END) AS authenticated_count,
                       SUM(CASE WHEN d.authentication_status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,
                       SUM(CASE WHEN d.authentication_status = 'PROCESSING' THEN 1 ELSE 0 END) AS processing_count
                FROM (%s) d
                GROUP BY d.stat_date
                ORDER BY d.stat_date ASC
                """.formatted(threeDsTransactionSubquery(query));
        return jdbcTemplate.query(sql, params(query), (rs, rowNum) -> {
            ThreeDsTrendMetric metric = new ThreeDsTrendMetric();
            metric.setDate(rs.getString("stat_date"));
            metric.setTotalCount(rs.getLong("total_count"));
            metric.setAuthenticatedCount(rs.getLong("authenticated_count"));
            metric.setFailedCount(rs.getLong("failed_count"));
            metric.setProcessingCount(rs.getLong("processing_count"));
            metric.setAuthenticationSuccessRate(percentage(metric.getAuthenticatedCount(),
                    metric.getAuthenticatedCount() + metric.getFailedCount()));
            return metric;
        });
    }

    /** 查询受控3DS维度分布，调用方不能传入任意列或表达式。 */
    private List<CountMetric> queryThreeDsDimension(AnalyticsQuery query, String dimension, long totalCount) {
        String column = switch (dimension) {
            case "authentication_status" -> "d.authentication_status";
            case "three_ds_version" -> "d.three_ds_version";
            case "authentication_source" -> "d.authentication_source";
            case "challenge_status" -> "d.challenge_status";
            case "liability_shift_status" -> "d.liability_shift_status";
            default -> throw new IllegalArgumentException("unsupported 3DS analytics dimension");
        };
        String sql = """
                SELECT %s AS dimension_key, COUNT(1) AS total_count
                FROM (%s) d
                GROUP BY %s
                ORDER BY total_count DESC, dimension_key ASC
                LIMIT 12
                """.formatted(column, threeDsTransactionSubquery(query), column);
        return jdbcTemplate.query(sql, params(query), (rs, rowNum) ->
                countMetric(rs.getString("dimension_key"), rs.getLong("total_count"), totalCount));
    }

    /**
     * 构造3DS交易级事实子查询；同一交易的INITIALIZE、AUTHENTICATE和VERIFY阶段只输出一行。
     */
    private String threeDsTransactionSubquery(AnalyticsQuery query) {
        return """
                SELECT a.transaction_id,
                       a.transaction_date_time,
                       o.transaction_status,
                       DATE_FORMAT(CONVERT_TZ(a.transaction_date_time, :storageTimeZone, :querySqlTimeZone), '%%Y-%%m-%%d') AS stat_date,
                       CASE
                         WHEN SUM(CASE WHEN a.authentication_status = 'AUTHENTICATED' THEN 1 ELSE 0 END) > 0 THEN 'AUTHENTICATED'
                         WHEN SUM(CASE WHEN a.authentication_status = 'FAILED' THEN 1 ELSE 0 END) > 0 THEN 'FAILED'
                         ELSE 'PROCESSING'
                       END AS authentication_status,
                       COALESCE(MAX(NULLIF(a.three_ds_version, '')), 'UNKNOWN') AS three_ds_version,
                       CASE
                         WHEN SUM(CASE WHEN a.authentication_source = 'CHANNEL' THEN 1 ELSE 0 END) > 0 THEN 'CHANNEL'
                         WHEN SUM(CASE WHEN a.authentication_source = 'MERCHANT' THEN 1 ELSE 0 END) > 0 THEN 'MERCHANT'
                         ELSE COALESCE(MAX(NULLIF(a.authentication_source, '')), 'UNKNOWN')
                       END AS authentication_source,
                       MAX(CASE WHEN a.challenge_required = 1 THEN 1 ELSE 0 END) AS challenge_required,
                       CASE
                         WHEN SUM(CASE WHEN a.challenge_status = 'COMPLETED' THEN 1 ELSE 0 END) > 0 THEN 'COMPLETED'
                         WHEN SUM(CASE WHEN a.challenge_status = 'FAILED' THEN 1 ELSE 0 END) > 0 THEN 'FAILED'
                         WHEN MAX(CASE WHEN a.challenge_required = 1 OR a.challenge_status = 'REQUIRED' THEN 1 ELSE 0 END) = 1 THEN 'REQUIRED'
                         ELSE 'NOT_REQUIRED'
                       END AS challenge_status,
                       CASE
                         WHEN SUM(CASE WHEN a.liability_shift = 1 THEN 1 ELSE 0 END) > 0 THEN 'SHIFTED'
                         WHEN SUM(CASE WHEN a.liability_shift = 0 THEN 1 ELSE 0 END) > 0 THEN 'NOT_SHIFTED'
                         ELSE 'UNKNOWN'
                       END AS liability_shift_status
                FROM %s a
                JOIN %s o ON o.transaction_id = a.transaction_id
                               AND o.transaction_date_time = a.transaction_date_time
                WHERE a.authentication_type = :threeDsAuthenticationType
                  AND a.transaction_date_time >= :beginTime
                  AND a.transaction_date_time < :endTime
                  AND %s
                GROUP BY a.transaction_id, a.transaction_date_time, o.transaction_status,
                         DATE_FORMAT(CONVERT_TZ(a.transaction_date_time, :storageTimeZone, :querySqlTimeZone), '%%Y-%%m-%%d')
                """.formatted(TRANSACTION_AUTHENTICATION_TABLE, TRANSACTION_OPERATION_TABLE, baseWhere(query));
    }

    /** 补齐渠道趋势日期，避免切换筛选条件时横轴跳动。 */
    private List<ChannelTrendMetric> fillChannelTrendDates(AnalyticsQuery query, List<ChannelTrendMetric> rows) {
        Map<String, ChannelTrendMetric> indexed = new LinkedHashMap<>();
        rows.forEach(row -> indexed.put(row.getDate(), row));
        List<ChannelTrendMetric> result = new ArrayList<>();
        for (LocalDate date : analyticsDates(query)) {
            ChannelTrendMetric metric = indexed.get(date.toString());
            if (metric == null) {
                metric = new ChannelTrendMetric();
                metric.setDate(date.toString());
            }
            result.add(metric);
        }
        return result;
    }

    /** 补齐3DS趋势日期，空日期保持全部指标为零。 */
    private List<ThreeDsTrendMetric> fillThreeDsTrendDates(AnalyticsQuery query, List<ThreeDsTrendMetric> rows) {
        Map<String, ThreeDsTrendMetric> indexed = new LinkedHashMap<>();
        rows.forEach(row -> indexed.put(row.getDate(), row));
        List<ThreeDsTrendMetric> result = new ArrayList<>();
        for (LocalDate date : analyticsDates(query)) {
            ThreeDsTrendMetric metric = indexed.get(date.toString());
            if (metric == null) {
                metric = new ThreeDsTrendMetric();
                metric.setDate(date.toString());
            }
            result.add(metric);
        }
        return result;
    }

    /** 生成页面查询时区下连续自然日，用于全部趋势图统一补零。 */
    private List<LocalDate> analyticsDates(AnalyticsQuery query) {
        ZoneId queryZone = resolveQueryZone(query.getQueryTimeZone());
        ZoneId storageZone = ZoneId.of(DEFAULT_QUERY_TIME_ZONE);
        LocalDateTime displayBegin = convertBetweenZones(query.getBeginTime(), storageZone, queryZone);
        LocalDateTime displayEnd = convertBetweenZones(query.getEndTime(), storageZone, queryZone);
        LocalDate firstDate = displayBegin.toLocalDate();
        LocalDate lastDate = displayEnd.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? displayEnd.toLocalDate().minusDays(1) : displayEnd.toLocalDate();
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    /** 创建数量占比指标，所有百分比使用BigDecimal计算。 */
    private CountMetric countMetric(String key, long count, long total) {
        CountMetric metric = new CountMetric();
        metric.setKey(StringUtils.hasText(key) ? key : "UNKNOWN");
        metric.setTotalCount(count);
        metric.setPercentage(percentage(count, total));
        return metric;
    }

    /** 将渠道平均耗时保留两位小数，缺失值按零展示。 */
    private BigDecimal scaleDuration(BigDecimal duration) {
        return duration == null ? BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY)
                : duration.setScale(2, RoundingMode.HALF_UP);
    }

    /** 构造首笔交易统一过滤条件；支付方式和发卡国家通过摘要表存在性过滤。 */
    private String baseWhere(AnalyticsQuery query) {
        StringBuilder where = new StringBuilder("""
                o.deleted = 0
                  AND o.operation_sequence = :operationSequence
                  AND o.transaction_type IN (:transactionTypes)
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time < :endTime
                """);
        if (!query.getMerchantIds().isEmpty()) {
            where.append(" AND o.merchant_id IN (:merchantIds)");
        }
        appendTextFilter(where, query.getCurrency(), "AND o.transaction_currency = :currency");
        if (StringUtils.hasText(query.getPaymentMethod()) || StringUtils.hasText(query.getPaymentBrand())
                || StringUtils.hasText(query.getIssuerCountry())) {
            where.append(" AND EXISTS (SELECT 1 FROM ").append(TRANSACTION_PAYMENT_METHOD_TABLE).append(" p_filter")
                    .append(" WHERE p_filter.transaction_id = o.transaction_id")
                    .append(" AND p_filter.transaction_date_time = o.transaction_date_time");
            appendTextFilter(where, query.getPaymentMethod(), "AND p_filter.payment_method = :paymentMethod");
            appendTextFilter(where, query.getPaymentBrand(), "AND p_filter.payment_brand = :paymentBrand");
            appendTextFilter(where, query.getIssuerCountry(), "AND p_filter.issuer_country = :issuerCountry");
            where.append(')');
        }
        return where.toString();
    }

    /** 构造支付工具摘要表连接条件，交易 ID 与分片时间必须同时匹配。 */
    private String paymentMethodJoin() {
        return "LEFT JOIN " + TRANSACTION_PAYMENT_METHOD_TABLE + " p"
                + " ON p.transaction_id = o.transaction_id"
                + " AND p.transaction_date_time = o.transaction_date_time";
    }

    /** 构造命名参数，状态和类型均集中在本服务常量中，避免散落魔法值。 */
    private MapSqlParameterSource params(AnalyticsQuery query) {
        List<String> transactionTypes = StringUtils.hasText(query.getTransactionType())
                ? List.of(query.getTransactionType()) : new ArrayList<>(SUPPORTED_TRANSACTION_TYPES);
        return new MapSqlParameterSource()
                .addValue("operationSequence", FIRST_OPERATION_SEQUENCE)
                .addValue("transactionTypes", transactionTypes)
                .addValue("beginTime", query.getBeginTime())
                .addValue("endTime", query.getEndTime())
                .addValue("merchantIds", query.getMerchantIds())
                .addValue("currency", query.getCurrency())
                .addValue("paymentMethod", query.getPaymentMethod())
                .addValue("paymentBrand", query.getPaymentBrand())
                .addValue("issuerCountry", query.getIssuerCountry())
                .addValue("storageTimeZone", STORAGE_SQL_TIME_ZONE)
                .addValue("querySqlTimeZone", sqlTimeZone(resolveQueryZone(query.getQueryTimeZone()), query.getBeginTime()))
                .addValue("successStatus", STATUS_SUCCESS)
                .addValue("failedStatus", STATUS_FAILED)
                .addValue("pendingStatus", STATUS_PENDING)
                .addValue("processingStatus", STATUS_PROCESSING)
                .addValue("inFlightStatuses", List.of(STATUS_PENDING, STATUS_PROCESSING))
                .addValue("channelTerminalStatuses", CHANNEL_TERMINAL_STATUSES)
                .addValue("cardPaymentMethod", PAYMENT_METHOD_BANK_CARD)
                .addValue("threeDsAuthenticationType", AUTHENTICATION_TYPE_THREE_DS);
    }

    /** 归一化查询时间和枚举编码，并在访问数据库前拒绝超过 31 天的扫描。 */
    private AnalyticsQuery normalize(AnalyticsQuery source) {
        AnalyticsQuery query = source == null ? new AnalyticsQuery() : source;
        ZoneId queryZone = resolveQueryZone(query.getQueryTimeZone());
        LocalDateTime endTime = query.getEndTime() == null ? LocalDateTime.now(queryZone) : query.getEndTime();
        LocalDateTime beginTime = query.getBeginTime() == null
                ? endTime.toLocalDate().minusDays(6).atStartOfDay() : query.getBeginTime();
        if (!endTime.isAfter(beginTime) || endTime.isAfter(beginTime.plusDays(MAX_QUERY_DAYS))) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "analytics time range must be greater than 0 and no more than 31 days");
        }
        ZoneId storageZone = ZoneId.of(DEFAULT_QUERY_TIME_ZONE);
        query.setBeginTime(convertBetweenZones(beginTime, queryZone, storageZone));
        query.setEndTime(convertBetweenZones(endTime, queryZone, storageZone));
        query.setQueryTimeZone(queryZone.getId());
        query.setMerchantIds(normalizeMerchantIds(query.getMerchantIds(), query.getMerchantId()));
        query.setMerchantId(null);
        query.setTransactionType(upperToNull(query.getTransactionType()));
        query.setCurrency(upperToNull(query.getCurrency()));
        query.setPaymentMethod(upperToNull(query.getPaymentMethod()));
        query.setPaymentBrand(upperToNull(query.getPaymentBrand()));
        query.setIssuerCountry(upperToNull(query.getIssuerCountry()));
        if (query.getTransactionType() != null && !SUPPORTED_TRANSACTION_TYPES.contains(query.getTransactionType())) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "unsupported analytics transaction type");
        }
        return query;
    }

    /** 将稀疏数据库趋势补齐为连续自然日序列，保证折线图时间轴稳定。 */
    private List<TrendMetric> fillTrendDates(AnalyticsQuery query, List<TrendMetric> rows) {
        Map<String, TrendMetric> indexed = new LinkedHashMap<>();
        rows.forEach(row -> indexed.put(row.getDate(), row));
        ZoneId queryZone = resolveQueryZone(query.getQueryTimeZone());
        ZoneId storageZone = ZoneId.of(DEFAULT_QUERY_TIME_ZONE);
        LocalDateTime displayBegin = convertBetweenZones(query.getBeginTime(), storageZone, queryZone);
        LocalDateTime displayEnd = convertBetweenZones(query.getEndTime(), storageZone, queryZone);
        LocalDate firstDate = displayBegin.toLocalDate();
        LocalDate lastDate = displayEnd.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? displayEnd.toLocalDate().minusDays(1) : displayEnd.toLocalDate();
        List<TrendMetric> result = new ArrayList<>();
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            String key = date.toString();
            TrendMetric metric = indexed.get(key);
            if (metric == null) {
                metric = new TrendMetric();
                metric.setDate(key);
            }
            result.add(metric);
        }
        return result;
    }

    /** 按终态交易计算百分比，结果保留两位小数且不使用浮点数。 */
    private BigDecimal successRate(long successCount, long failedCount) {
        long terminalCount = successCount + failedCount;
        if (terminalCount == 0L) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        return BigDecimal.valueOf(successCount)
                .multiply(BigDecimal.valueOf(100L))
                .divide(BigDecimal.valueOf(terminalCount), 2, RoundingMode.HALF_UP);
    }

    /** 计算任意非负计数的百分比，分母为零时返回0.00。 */
    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0L) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        return BigDecimal.valueOf(Math.max(0L, numerator))
                .multiply(BigDecimal.valueOf(100L))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    /** 合并新旧商户筛选字段并去重，避免超大 IN 条件放大分片扫描。 */
    private List<String> normalizeMerchantIds(List<String> merchantIds, String legacyMerchantId) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (merchantIds != null) {
            merchantIds.stream().map(this::trimToNull).filter(StringUtils::hasText).forEach(normalized::add);
        }
        String legacy = trimToNull(legacyMerchantId);
        if (legacy != null) {
            normalized.add(legacy);
        }
        if (normalized.size() > MAX_MERCHANT_FILTERS) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "analytics merchant filter must not exceed 50 items");
        }
        return new ArrayList<>(normalized);
    }

    /** 解析页面查询时区，非法时区在访问数据库前返回参数错误。 */
    private ZoneId resolveQueryZone(String queryTimeZone) {
        String zone = StringUtils.hasText(queryTimeZone) ? queryTimeZone.trim() : DEFAULT_QUERY_TIME_ZONE;
        try {
            return ZoneId.of(normalizeZoneId(zone));
        } catch (DateTimeException exception) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "queryTimeZone is invalid");
        }
    }

    /** 兼容字典中的 UTC+8、GMT-5 等简写格式。 */
    private String normalizeZoneId(String zone) {
        String normalized = zone == null ? DEFAULT_QUERY_TIME_ZONE : zone.trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("UTC".equals(upper) || "GMT".equals(upper)) {
            return upper;
        }
        if (upper.matches("(UTC|GMT)[+-]\\d{1,2}")) {
            return upper.substring(0, 3) + String.format("%+03d:00", Integer.parseInt(upper.substring(3)));
        }
        if (upper.matches("(UTC|GMT)[+-]\\d{1,2}:\\d{2}")) {
            String offset = upper.substring(3);
            String[] parts = offset.substring(1).split(":");
            return upper.substring(0, 3) + offset.charAt(0)
                    + String.format("%02d:%s", Integer.parseInt(parts[0]), parts[1]);
        }
        return normalized;
    }

    /** 将页面本地时间转换到交易表的 Asia/Shanghai 存储时区。 */
    private LocalDateTime convertBetweenZones(LocalDateTime sourceTime, ZoneId sourceZone, ZoneId targetZone) {
        return sourceTime.atZone(sourceZone).withZoneSameInstant(targetZone).toLocalDateTime();
    }

    /** 为 MySQL 自然日聚合选择时区参数，常用固定时区不依赖系统时区表。 */
    private String sqlTimeZone(ZoneId queryZone, LocalDateTime storageReferenceTime) {
        if (DEFAULT_QUERY_TIME_ZONE.equals(queryZone.getId())) {
            return STORAGE_SQL_TIME_ZONE;
        }
        if (queryZone.getRules().isFixedOffset()) {
            ZonedDateTime reference = storageReferenceTime.atZone(ZoneId.of(DEFAULT_QUERY_TIME_ZONE));
            String offset = queryZone.getRules().getOffset(reference.toInstant()).getId();
            return "Z".equals(offset) ? "+00:00" : offset;
        }
        return queryZone.getId();
    }

    /** 仅在筛选值非空时追加预定义 SQL 片段，片段不接受页面输入。 */
    private void appendTextFilter(StringBuilder sql, String value, String clause) {
        if (StringUtils.hasText(value)) {
            sql.append(' ').append(clause);
        }
    }

    /** 去除查询文本首尾空白，并将空文本归一化为 null。 */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 将枚举类查询文本归一化为大写编码，空文本保持 null。 */
    private String upperToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    /** 将 JDBC 可空数值转换为币种精度，数据库 null 不设置默认精度。 */
    private Integer nullableInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
