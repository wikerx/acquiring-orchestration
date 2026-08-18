package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.AmountMetric;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.DimensionMetric;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.FailureResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.OverviewResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.TrendMetric;
import com.scott.payment.merchant.service.MerchantTransactionAnalyticsQueryService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcMerchantTransactionAnalyticsQueryService
 * @date : 2026-08-07 10:00
 * @email : scott_x@163.com
 * @description : 商户端交易分析 JDBC 实现，在每条统计 SQL 中强制 merchant_id，并仅从商户可见消息生成失败原因聚合。
 * @status : create
 */
@Service
public class JdbcMerchantTransactionAnalyticsQueryService implements MerchantTransactionAnalyticsQueryService {

    /** 交易操作逻辑表名，仅用于受控静态 SQL 拼装。 */
    private static final String TRANSACTION_OPERATION_TABLE = "transaction_operation";
    /** 商户可见失败描述所在的交易订单逻辑表名。 */
    private static final String TRANSACTION_ORDER_TABLE = "transaction_order";
    /** 支付工具摘要逻辑表名，仅用于受控静态 SQL 拼装。 */
    private static final String TRANSACTION_PAYMENT_METHOD_TABLE = "transaction_payment_method_info";
    /** 单次统计允许扫描的最大自然日跨度。 */
    private static final int MAX_QUERY_DAYS = 31;
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
    /** 商户可见失败描述缺失时使用的合并维度编码。 */
    private static final String OTHER_FAILURE_REASON = "OTHER";
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
     * 创建测试和轻量集成场景使用的商户统计查询服务。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     */
    public JdbcMerchantTransactionAnalyticsQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new TransactionLogicalReadExecutor());
    }

    /**
     * 创建生产环境商户交易统计查询服务。
     *
     * @param dataSource dynamic-datasource 外层路由数据源
     * @param transactionLogicalReadExecutor 交易逻辑数据源只读执行器
     * @param shardingProperties 交易查询资源预算配置
     * @param queryJdbcTemplateFactory 查询专用 JDBC 模板工厂
     */
    @Autowired
    public JdbcMerchantTransactionAnalyticsQueryService(DataSource dataSource,
                                                         TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                                         TransactionShardingProperties shardingProperties,
                                                         TransactionQueryJdbcTemplateFactory queryJdbcTemplateFactory) {
        this(queryJdbcTemplateFactory.create(dataSource, shardingProperties), transactionLogicalReadExecutor);
    }

    /**
     * 创建指定 JDBC 模板和逻辑数据源执行器的商户统计查询服务。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     * @param transactionLogicalReadExecutor 交易逻辑数据源只读执行器
     */
    public JdbcMerchantTransactionAnalyticsQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                                         TransactionLogicalReadExecutor transactionLogicalReadExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionLogicalReadExecutor = transactionLogicalReadExecutor;
    }

    /**
     * 查询当前商户交易总览。
     *
     * @param merchantId 认证上下文中的商户号
     * @param query 页面分析条件
     * @return 当前商户交易总览
     */
    @Override
    public OverviewResponse overview(String merchantId, AnalyticsQuery query) {
        String safeMerchantId = requireMerchantId(merchantId);
        AnalyticsQuery safeQuery = normalize(query);
        return transactionLogicalReadExecutor.read(() -> overviewNormalized(safeMerchantId, safeQuery));
    }

    /**
     * 查询当前商户失败分析。
     *
     * @param merchantId 认证上下文中的商户号
     * @param query 页面分析条件
     * @return 仅包含商户可见失败原因的分析结果
     */
    @Override
    public FailureResponse failures(String merchantId, AnalyticsQuery query) {
        String safeMerchantId = requireMerchantId(merchantId);
        AnalyticsQuery safeQuery = normalize(query);
        return transactionLogicalReadExecutor.read(() -> failuresNormalized(safeMerchantId, safeQuery));
    }

    /** 在同一商户和逻辑数据源上下文完成总览的全部聚合。 */
    private OverviewResponse overviewNormalized(String merchantId, AnalyticsQuery query) {
        OverviewResponse response = querySummary(merchantId, query);
        response.setGeneratedAt(LocalDateTime.now(resolveQueryZone(query.getQueryTimeZone())));
        response.setSuccessAmounts(querySuccessAmounts(merchantId, query));
        response.setTrend(fillTrendDates(query, queryTrend(merchantId, query, false)));
        response.setStatusDistribution(queryDimension(merchantId, query, "status", false));
        response.setPaymentMethods(queryDimension(merchantId, query, "paymentMethod", false));
        response.setIssuerCountries(queryDimension(merchantId, query, "issuerCountry", false));
        return response;
    }

    /** 失败分析只读取 merchant_visible_message，不读取渠道响应或内部失败原因。 */
    private FailureResponse failuresNormalized(String merchantId, AnalyticsQuery query) {
        FailureResponse response = new FailureResponse();
        response.setGeneratedAt(LocalDateTime.now(resolveQueryZone(query.getQueryTimeZone())));
        response.setFailedCount(querySummary(merchantId, query).getFailedCount());
        response.setTrend(fillTrendDates(query, queryTrend(merchantId, query, true)));
        response.setReasons(queryFailureReasons(merchantId, query));
        response.setPaymentMethods(queryDimension(merchantId, query, "paymentMethod", true));
        return response;
    }

    /** 查询核心计数指标，商户谓词是不可选条件。 */
    private OverviewResponse querySummary(String merchantId, AnalyticsQuery query) {
        String sql = """
                SELECT COUNT(1) AS total_count,
                       SUM(CASE WHEN o.transaction_status = :successStatus THEN 1 ELSE 0 END) AS success_count,
                       SUM(CASE WHEN o.transaction_status = :failedStatus THEN 1 ELSE 0 END) AS failed_count,
                       SUM(CASE WHEN o.transaction_status = :pendingStatus THEN 1 ELSE 0 END) AS pending_count,
                       SUM(CASE WHEN o.transaction_status = :processingStatus THEN 1 ELSE 0 END) AS processing_count
                FROM %s o
                WHERE %s
                """.formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query));
        return jdbcTemplate.queryForObject(sql, params(merchantId, query), (rs, rowNum) -> {
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

    /** 查询成功金额，按币种和精度分组，禁止跨币种相加。 */
    private List<AmountMetric> querySuccessAmounts(String merchantId, AnalyticsQuery query) {
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
        return jdbcTemplate.query(sql, params(merchantId, query), (rs, rowNum) -> {
            AmountMetric metric = new AmountMetric();
            metric.setCurrency(rs.getString("currency"));
            metric.setCurrencyExponent(nullableInteger(rs.getObject("currency_exponent")));
            metric.setAmount(rs.getBigDecimal("amount"));
            metric.setSuccessCount(rs.getLong("success_count"));
            return metric;
        });
    }

    /** 查询自然日趋势；失败分析模式只保留失败交易，其他模式返回全部状态。 */
    private List<TrendMetric> queryTrend(String merchantId, AnalyticsQuery query, boolean failedOnly) {
        String failedPredicate = failedOnly ? " AND o.transaction_status = :failedStatus" : "";
        String sql = """
                SELECT DATE_FORMAT(CONVERT_TZ(o.transaction_date_time, :storageTimeZone, :querySqlTimeZone), '%%Y-%%m-%%d') AS stat_date,
                       COUNT(1) AS total_count,
                       SUM(CASE WHEN o.transaction_status = :successStatus THEN 1 ELSE 0 END) AS success_count,
                       SUM(CASE WHEN o.transaction_status = :failedStatus THEN 1 ELSE 0 END) AS failed_count,
                       SUM(CASE WHEN o.transaction_status = :pendingStatus THEN 1 ELSE 0 END) AS pending_count,
                       SUM(CASE WHEN o.transaction_status = :processingStatus THEN 1 ELSE 0 END) AS processing_count
                FROM %s o
                WHERE %s
                %s
                GROUP BY DATE_FORMAT(CONVERT_TZ(o.transaction_date_time, :storageTimeZone, :querySqlTimeZone), '%%Y-%%m-%%d')
                ORDER BY stat_date ASC
                """.formatted(TRANSACTION_OPERATION_TABLE, baseWhere(query), failedPredicate);
        return jdbcTemplate.query(sql, params(merchantId, query), (rs, rowNum) -> {
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

    /** 根据受控维度选择统计 SQL，调用方不能传入任意列名或表达式。 */
    private List<DimensionMetric> queryDimension(String merchantId,
                                                 AnalyticsQuery query,
                                                 String dimension,
                                                 boolean failedOnly) {
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
        String failedPredicate = failedOnly ? " AND o.transaction_status = :failedStatus" : "";
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
                %s
                GROUP BY %s
                ORDER BY total_count DESC
                LIMIT 12
                """.formatted(select, metadataSelect, TRANSACTION_OPERATION_TABLE, join, baseWhere(query), failedPredicate, groupBy);
        return jdbcTemplate.query(sql, params(merchantId, query), (rs, rowNum) -> {
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

    /** 按商户可见消息统计失败原因，缺失消息统一使用 OTHER，避免回退到内部失败字段。 */
    private List<DimensionMetric> queryFailureReasons(String merchantId, AnalyticsQuery query) {
        String sql = """
                SELECT COALESCE(NULLIF(t.merchant_visible_message, ''), :otherFailureReason) AS dimension_key,
                       COUNT(1) AS total_count
                FROM %s o
                LEFT JOIN %s t
                  ON t.operation_id = o.operation_id
                 AND t.transaction_date_time = o.transaction_date_time
                 AND t.merchant_id = o.merchant_id
                 AND t.deleted = 0
                WHERE %s
                  AND o.transaction_status = :failedStatus
                GROUP BY COALESCE(NULLIF(t.merchant_visible_message, ''), :otherFailureReason)
                ORDER BY total_count DESC
                LIMIT 10
                """.formatted(TRANSACTION_OPERATION_TABLE, TRANSACTION_ORDER_TABLE, baseWhere(query));
        return jdbcTemplate.query(sql, params(merchantId, query), (rs, rowNum) -> {
            DimensionMetric metric = new DimensionMetric();
            metric.setKey(rs.getString("dimension_key"));
            metric.setTotalCount(rs.getLong("total_count"));
            metric.setFailedCount(metric.getTotalCount());
            return metric;
        });
    }

    /** 构造首笔交易统一过滤条件，merchant_id 始终作为首个业务谓词。 */
    private String baseWhere(AnalyticsQuery query) {
        StringBuilder where = new StringBuilder("""
                o.deleted = 0
                  AND o.merchant_id = :merchantId
                  AND o.operation_sequence = :operationSequence
                  AND o.transaction_type IN (:transactionTypes)
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time < :endTime
                """);
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

    /** 构造命名参数，商户号只来自方法参数而非页面请求。 */
    private MapSqlParameterSource params(String merchantId, AnalyticsQuery query) {
        List<String> transactionTypes = StringUtils.hasText(query.getTransactionType())
                ? List.of(query.getTransactionType()) : new ArrayList<>(SUPPORTED_TRANSACTION_TYPES);
        return new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("operationSequence", FIRST_OPERATION_SEQUENCE)
                .addValue("transactionTypes", transactionTypes)
                .addValue("beginTime", query.getBeginTime())
                .addValue("endTime", query.getEndTime())
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
                .addValue("otherFailureReason", OTHER_FAILURE_REASON);
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

    /** 将稀疏数据库趋势补齐为连续自然日序列。 */
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

    /** 校验认证上下文商户号，缺失时在数据库访问前拒绝查询。 */
    private String requireMerchantId(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant context missing");
        }
        return merchantId.trim();
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
