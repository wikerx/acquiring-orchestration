package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.AmountMetric;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.DimensionMetric;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.MerchantMetric;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.MerchantPerformanceResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.OverviewResponse;
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
                .addValue("inFlightStatuses", List.of(STATUS_PENDING, STATUS_PROCESSING));
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
