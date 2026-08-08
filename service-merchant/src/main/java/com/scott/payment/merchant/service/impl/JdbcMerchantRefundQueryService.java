package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundCurrencySummary;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundRecord;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundSearchResponse;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundSummary;
import com.scott.payment.merchant.service.MerchantRefundQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcMerchantRefundQueryService
 * @date : 2026-08-08 00:40
 * @email : scott_x@163.com
 * @description : 商户端退款 JDBC 查询实现，通过 transaction 逻辑数据源读取副本分片并在每条 SQL 中强制绑定认证商户号。
 * @status : create
 */
@Service
public class JdbcMerchantRefundQueryService implements MerchantRefundQueryService {

    /** 交易数据统一以 Asia/Shanghai 解释并路由季度分片。 */
    private static final String STORAGE_TIME_ZONE = TransactionShardingProperties.REQUIRED_ZONE_ID;

    /** 商户退款列表固定查询的逻辑表关联，表名不接受外部输入。 */
    private static final String REFUND_FROM_SQL = """
            FROM transaction_operation o
            LEFT JOIN transaction_refund_approval a
              ON a.refund_transaction_id = o.transaction_id
             AND a.merchant_id = o.merchant_id
            LEFT JOIN transaction_payment_method_info p
              ON p.transaction_id = o.transaction_id
             AND p.transaction_date_time = o.transaction_date_time
            """;

    /** 商户退款记录投影，排除渠道、内部失败原因、申请人和审批操作人字段。 */
    private static final String REFUND_COLUMNS = """
            o.transaction_id AS refund_transaction_id,
            o.source_transaction_id, o.merchant_order_no, o.merchant_operation_no,
            o.transaction_type, o.refund_scope, o.request_source, o.request_reason,
            o.transaction_status, o.process_stage, o.label_currency, o.label_amount,
            o.transaction_currency, o.transaction_amount, o.currency_exponent,
            p.payment_method, p.payment_brand, NULL AS merchant_notification_status,
            o.transaction_date_time, o.complete_time,
            a.approval_id,
            CASE WHEN o.transaction_type = 'VOID' THEN 'NOT_APPLICABLE'
                 WHEN a.id IS NULL THEN 'NOT_REQUIRED'
                 ELSE a.approval_status END AS approval_status,
            a.approval_time, a.approval_reason, a.expire_time AS approval_expire_time,
            a.version AS approval_version,
            o.operation_id
            """;

    /** JDBC 查询模板，生产环境应用统一交易查询超时。 */
    private final NamedParameterJdbcTemplate jdbcTemplate;
    /** 交易逻辑数据源普通读执行器，由 ShardingSphere 决定副本节点。 */
    private final TransactionLogicalReadExecutor transactionLogicalReadExecutor;
    /** 单页允许返回的最大记录数。 */
    private final int maxResultRows;
    /** 当前已发布交易分片的最早起始时间。 */
    private final LocalDateTime registeredNodeBegin;

    /**
     * 创建生产环境商户退款查询服务。
     *
     * @param dataSource dynamic-datasource 外层路由数据源
     * @param transactionLogicalReadExecutor 交易逻辑数据源普通读执行器
     * @param shardingProperties 已发布分片与查询预算配置
     * @param queryJdbcTemplateFactory 交易查询 JDBC 模板工厂
     */
    @Autowired
    public JdbcMerchantRefundQueryService(DataSource dataSource,
                                          TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                          TransactionShardingProperties shardingProperties,
                                          TransactionQueryJdbcTemplateFactory queryJdbcTemplateFactory) {
        this(queryJdbcTemplateFactory.create(dataSource, shardingProperties),
                transactionLogicalReadExecutor, shardingProperties);
    }

    /**
     * 创建可注入 JDBC 模板的商户退款查询服务。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     * @param transactionLogicalReadExecutor 交易逻辑数据源普通读执行器
     * @param shardingProperties 已发布分片与查询预算配置
     */
    public JdbcMerchantRefundQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                          TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                          TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionLogicalReadExecutor = transactionLogicalReadExecutor;
        this.maxResultRows = shardingProperties.getQueryBudget().getMaxResultRows();
        this.registeredNodeBegin = resolveRegisteredNodeBegin(shardingProperties.getPhysicalNodes());
    }

    /**
     * 查询当前认证商户的退款与撤销分页和统计。
     *
     * @param query 已由应用层覆盖认证商户号的查询条件
     * @return 商户可见退款分页和统计
     */
    @Override
    public RefundSearchResponse search(RefundQuery query) {
        RefundQuery safeQuery = normalize(query);
        requireMerchant(safeQuery.getMerchantId());
        return transactionLogicalReadExecutor.read(() -> searchNormalized(safeQuery));
    }

    /**
     * 查询当前认证商户的单笔退款详情。
     *
     * @param merchantId 当前认证商户号
     * @param transactionId 退款或撤销交易号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 商户可见退款详情
     */
    @Override
    public RefundDetailResponse detail(String merchantId,
                                       String transactionId,
                                       LocalDateTime transactionDateTime) {
        requireMerchant(merchantId);
        if (!StringUtils.hasText(transactionId) || transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return transactionLogicalReadExecutor.read(
                () -> detailNormalized(merchantId, transactionId, transactionDateTime));
    }

    /** 在已归一化的存储时区范围内完成商户隔离分页和统计查询。 */
    private RefundSearchResponse searchNormalized(RefundQuery query) {
        String whereSql = whereSql(query);
        MapSqlParameterSource parameters = parameters(query);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT o.id) " + REFUND_FROM_SQL + whereSql,
                parameters, Long.class);
        long total = count == null ? 0L : count;
        long pageNo = query.safePageNo();
        long pageSize = query.safePageSize();
        long offset = (pageNo - 1L) * pageSize;
        List<RefundQueryRow> queryRows = offset < total
                ? jdbcTemplate.query(
                        "SELECT " + REFUND_COLUMNS + REFUND_FROM_SQL + whereSql + """
                                ORDER BY o.transaction_date_time DESC, o.id DESC
                                LIMIT :offset, :limit
                                """,
                        new MapSqlParameterSource(parameters.getValues())
                                .addValue("offset", offset)
                                .addValue("limit", pageSize),
                        refundRecordMapper())
                : List.of();
        List<RefundRecord> records = queryRows.stream().map(RefundQueryRow::record).toList();
        enrichHistoricalRefundScope(query.getMerchantId(), queryRows);

        RefundSearchResponse response = new RefundSearchResponse();
        response.setPage(PageResult.of(total, pageNo, pageSize, records));
        response.setSummary(loadSummary(whereSql, parameters));
        return response;
    }

    /** 使用认证商户号和精确分片时间读取单笔退款。 */
    private RefundDetailResponse detailNormalized(String merchantId,
                                                  String transactionId,
                                                  LocalDateTime transactionDateTime) {
        List<RefundQueryRow> queryRows = jdbcTemplate.query(
                "SELECT " + REFUND_COLUMNS + REFUND_FROM_SQL + """
                        WHERE o.merchant_id = :merchantId
                          AND o.transaction_id = :transactionId
                          AND o.transaction_date_time = :transactionDateTime
                          AND o.transaction_type IN ('REFUND', 'VOID')
                          AND o.deleted = 0
                        LIMIT 1
                        """,
                new MapSqlParameterSource()
                        .addValue("merchantId", merchantId)
                        .addValue("transactionId", transactionId)
                        .addValue("transactionDateTime", transactionDateTime),
                refundRecordMapper());
        if (queryRows.isEmpty()) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        enrichHistoricalRefundScope(merchantId, queryRows);
        RefundDetailResponse response = new RefundDetailResponse();
        response.setRefund(queryRows.get(0).record());
        return response;
    }

    /** 读取商户隔离的状态统计和分币种金额统计。 */
    private RefundSummary loadSummary(String whereSql, MapSqlParameterSource parameters) {
        RefundSummary summary = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT o.id) AS total_count,
                       COUNT(DISTINCT CASE WHEN a.approval_status = 'PENDING' THEN o.id END)
                           AS pending_approval_count,
                       COUNT(DISTINCT CASE WHEN o.transaction_status IN ('PENDING', 'PROCESSING') THEN o.id END)
                           AS processing_count,
                       COUNT(DISTINCT CASE WHEN o.transaction_status = 'SUCCESS' THEN o.id END)
                           AS success_count,
                       COUNT(DISTINCT CASE WHEN o.transaction_status = 'FAILED'
                             OR a.approval_status IN ('REJECTED', 'EXPIRED') THEN o.id END)
                           AS failed_or_rejected_count
                """ + REFUND_FROM_SQL + whereSql, parameters, refundSummaryMapper());
        RefundSummary safeSummary = summary == null ? new RefundSummary() : summary;
        safeSummary.setCurrencyAmounts(jdbcTemplate.query("""
                SELECT o.transaction_currency AS currency,
                       COALESCE(SUM(o.transaction_amount), 0) AS total_amount,
                       COALESCE(SUM(CASE WHEN a.approval_status = 'PENDING'
                                        THEN o.transaction_amount ELSE 0 END), 0)
                           AS pending_approval_amount,
                       COALESCE(SUM(CASE WHEN o.transaction_status = 'SUCCESS'
                                        THEN o.transaction_amount ELSE 0 END), 0)
                           AS successful_amount,
                       COALESCE(SUM(CASE WHEN o.transaction_status IN ('PENDING', 'PROCESSING')
                                        THEN o.transaction_amount ELSE 0 END), 0)
                           AS pending_amount
                """ + REFUND_FROM_SQL + whereSql + """
                GROUP BY o.transaction_currency
                ORDER BY o.transaction_currency
                """, parameters, refundCurrencySummaryMapper()));
        return safeSummary;
    }

    /**
     * 按认证商户和生命周期操作号批量读取根主单金额，兼容历史未保存 refund_scope 的动作。
     */
    private void enrichHistoricalRefundScope(String merchantId, List<RefundQueryRow> rows) {
        List<String> operationIds = rows.stream()
                .map(RefundQueryRow::operationId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (operationIds.isEmpty()) {
            return;
        }
        Map<String, BigDecimal> rootAmounts = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT operation_id, transaction_amount
                FROM transaction_order
                WHERE deleted = 0
                  AND merchant_id = :merchantId
                  AND operation_id IN (:operationIds)
                  AND transaction_date_time >= :registeredNodeBegin
                  AND transaction_date_time < :registeredNodeEnd
                """,
                new MapSqlParameterSource()
                        .addValue("merchantId", merchantId)
                        .addValue("operationIds", operationIds)
                        .addValue("registeredNodeBegin", registeredNodeBegin)
                        .addValue("registeredNodeEnd", LocalDateTime.now(ZoneId.of(STORAGE_TIME_ZONE)).plusDays(1)),
                (resultSet, rowNumber) -> new RootAmount(
                        resultSet.getString("operation_id"),
                        resultSet.getBigDecimal("transaction_amount")))
                .forEach(root -> rootAmounts.put(root.operationId(), root.transactionAmount()));
        rows.forEach(row -> enrichRefundScope(
                row.record(), rootAmounts.get(row.operationId())));
    }

    /** 根据根主单金额补充历史退款范围，不覆盖已经保存的退款范围。 */
    private void enrichRefundScope(RefundRecord record, BigDecimal rootAmount) {
        if (StringUtils.hasText(record.getRefundScope())) {
            return;
        }
        if ("VOID".equals(record.getTransactionType())) {
            record.setRefundScope("VOID");
        } else if ("REFUND".equals(record.getTransactionType())
                && record.getTransactionAmount() != null && rootAmount != null) {
            record.setRefundScope(record.getTransactionAmount().compareTo(rootAmount) == 0
                    ? "FULL" : "PARTIAL");
        }
    }

    /** 归一化分页预算、时间范围、金额范围和查询时区。 */
    private RefundQuery normalize(RefundQuery query) {
        RefundQuery safeQuery = query == null ? new RefundQuery() : query;
        safeQuery.setPageSize((int) Math.min(safeQuery.safePageSize(), maxResultRows));
        if (safeQuery.getMinimumTransactionAmount() != null
                && safeQuery.getMaximumTransactionAmount() != null
                && safeQuery.getMinimumTransactionAmount().compareTo(
                        safeQuery.getMaximumTransactionAmount()) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "refund amount range is invalid");
        }
        ZoneId queryZone = resolveQueryZone(safeQuery.getQueryTimeZone());
        ZoneId storageZone = ZoneId.of(STORAGE_TIME_ZONE);
        LocalDateTime queryNow = LocalDateTime.now(queryZone);
        boolean pendingApprovalQueue = safeQuery.getBeginTime() == null
                && "PENDING".equals(safeQuery.getApprovalStatus());
        LocalDateTime queryBegin = safeQuery.getBeginTime() == null
                ? queryNow.toLocalDate().atStartOfDay() : safeQuery.getBeginTime();
        LocalDateTime queryEnd = safeQuery.getEndTime() == null ? queryNow : safeQuery.getEndTime();
        if (queryBegin.isAfter(queryEnd)
                || isInvalidRange(safeQuery.getCompleteBeginTime(), safeQuery.getCompleteEndTime())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "refund time range is invalid");
        }
        safeQuery.setBeginTime(pendingApprovalQueue
                ? registeredNodeBegin : convertBetweenZones(queryBegin, queryZone, storageZone));
        safeQuery.setEndTime(convertBetweenZones(queryEnd, queryZone, storageZone));
        safeQuery.setCompleteBeginTime(convertBetweenZones(
                safeQuery.getCompleteBeginTime(), queryZone, storageZone));
        safeQuery.setCompleteEndTime(convertBetweenZones(
                safeQuery.getCompleteEndTime(), queryZone, storageZone));
        safeQuery.setQueryTimeZone(storageZone.getId());
        return safeQuery;
    }

    /** 构建强制认证商户谓词和固定字段白名单下的筛选 SQL。 */
    private String whereSql(RefundQuery query) {
        StringBuilder sql = new StringBuilder("""
                WHERE o.deleted = 0
                  AND o.merchant_id = :merchantId
                  AND o.transaction_type IN ('REFUND', 'VOID')
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time < :endTimeExclusive
                """);
        appendTextFilter(sql, query.getRefundTransactionId(), "AND o.transaction_id = :refundTransactionId");
        appendTextFilter(sql, query.getSourceTransactionId(), "AND o.source_transaction_id = :sourceTransactionId");
        appendTextFilter(sql, query.getMerchantOrderNo(), "AND o.merchant_order_no = :merchantOrderNo");
        appendTextFilter(sql, query.getMerchantOperationNo(), "AND o.merchant_operation_no = :merchantOperationNo");
        appendTextFilter(sql, query.getTransactionType(), "AND o.transaction_type = :transactionType");
        appendTextFilter(sql, query.getRefundScope(), "AND o.refund_scope = :refundScope");
        appendTextFilter(sql, query.getTransactionStatus(), "AND o.transaction_status = :transactionStatus");
        appendTextFilter(sql, query.getRequestSource(), "AND o.request_source = :requestSource");
        appendTextFilter(sql, query.getLabelCurrency(), "AND o.label_currency = :labelCurrency");
        appendTextFilter(sql, query.getTransactionCurrency(), "AND o.transaction_currency = :transactionCurrency");
        if (query.getMinimumTransactionAmount() != null) {
            sql.append(" AND o.transaction_amount >= :minimumTransactionAmount");
        }
        if (query.getMaximumTransactionAmount() != null) {
            sql.append(" AND o.transaction_amount <= :maximumTransactionAmount");
        }
        if (query.getCompleteBeginTime() != null) {
            sql.append(" AND o.complete_time >= :completeBeginTime");
        }
        if (query.getCompleteEndTime() != null) {
            sql.append(" AND o.complete_time < :completeEndTime");
        }
        if (StringUtils.hasText(query.getApprovalStatus())) {
            sql.append("""
                     AND (CASE WHEN o.transaction_type = 'VOID' THEN 'NOT_APPLICABLE'
                               WHEN a.id IS NULL THEN 'NOT_REQUIRED'
                               ELSE a.approval_status END) = :approvalStatus
                    """);
        }
        return sql.toString();
    }

    /** 构造商户退款查询全部命名参数。 */
    private MapSqlParameterSource parameters(RefundQuery query) {
        return new MapSqlParameterSource()
                .addValue("merchantId", query.getMerchantId())
                .addValue("beginTime", query.getBeginTime())
                .addValue("endTimeExclusive", exclusiveEnd(query.getEndTime()))
                .addValue("refundTransactionId", query.getRefundTransactionId())
                .addValue("sourceTransactionId", query.getSourceTransactionId())
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("merchantOperationNo", query.getMerchantOperationNo())
                .addValue("transactionType", query.getTransactionType())
                .addValue("refundScope", query.getRefundScope())
                .addValue("approvalStatus", query.getApprovalStatus())
                .addValue("transactionStatus", query.getTransactionStatus())
                .addValue("requestSource", query.getRequestSource())
                .addValue("labelCurrency", query.getLabelCurrency())
                .addValue("transactionCurrency", query.getTransactionCurrency())
                .addValue("minimumTransactionAmount", query.getMinimumTransactionAmount())
                .addValue("maximumTransactionAmount", query.getMaximumTransactionAmount())
                .addValue("completeBeginTime", query.getCompleteBeginTime())
                .addValue("completeEndTime", query.getCompleteEndTime());
    }

    /** 返回商户退款 DTO 行映射器，并以内部投影保留生命周期 operation_id。 */
    private RowMapper<RefundQueryRow> refundRecordMapper() {
        BeanPropertyRowMapper<RefundRecord> mapper =
                BeanPropertyRowMapper.newInstance(RefundRecord.class);
        return (resultSet, rowNumber) -> new RefundQueryRow(
                mapper.mapRow(resultSet, rowNumber), resultSet.getString("operation_id"));
    }

    /** 返回退款状态统计行映射器，并把 SQL NULL 归一化为零。 */
    private RowMapper<RefundSummary> refundSummaryMapper() {
        return (resultSet, rowNumber) -> {
            RefundSummary summary = new RefundSummary();
            summary.setTotalCount(resultSet.getLong("total_count"));
            summary.setPendingApprovalCount(resultSet.getLong("pending_approval_count"));
            summary.setProcessingCount(resultSet.getLong("processing_count"));
            summary.setSuccessCount(resultSet.getLong("success_count"));
            summary.setFailedOrRejectedCount(resultSet.getLong("failed_or_rejected_count"));
            return summary;
        };
    }

    /** 返回退款分币种金额统计行映射器。 */
    private RowMapper<RefundCurrencySummary> refundCurrencySummaryMapper() {
        return (resultSet, rowNumber) -> {
            RefundCurrencySummary summary = new RefundCurrencySummary();
            summary.setCurrency(resultSet.getString("currency"));
            summary.setTotalAmount(defaultAmount(resultSet.getBigDecimal("total_amount")));
            summary.setPendingApprovalAmount(defaultAmount(
                    resultSet.getBigDecimal("pending_approval_amount")));
            summary.setSuccessfulAmount(defaultAmount(resultSet.getBigDecimal("successful_amount")));
            summary.setPendingAmount(defaultAmount(resultSet.getBigDecimal("pending_amount")));
            return summary;
        };
    }

    /** 校验认证商户号，禁止缺失数据边界时执行任何 SQL。 */
    private void requireMerchant(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
    }

    /** 解析页面查询时区，非法时区在访问数据库前拒绝。 */
    private ZoneId resolveQueryZone(String queryTimeZone) {
        String zone = StringUtils.hasText(queryTimeZone) ? queryTimeZone.trim() : STORAGE_TIME_ZONE;
        try {
            return ZoneId.of(normalizeZoneId(zone));
        } catch (DateTimeException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "queryTimeZone is invalid", exception);
        }
    }

    /** 兼容页面提交的 UTC+8、GMT+8 等短偏移格式。 */
    private String normalizeZoneId(String zone) {
        String normalized = zone.trim();
        String upper = normalized.toUpperCase();
        if ("UTC".equals(upper) || "GMT".equals(upper)) {
            return upper;
        }
        if (upper.startsWith("UTC+") || upper.startsWith("UTC-")
                || upper.startsWith("GMT+") || upper.startsWith("GMT-")) {
            String prefix = upper.substring(0, 3);
            String offset = upper.substring(3);
            if (offset.matches("[+-]\\d{1,2}")) {
                return prefix + String.format("%+03d:00", Integer.parseInt(offset));
            }
            if (offset.matches("[+-]\\d{1,2}:\\d{2}")) {
                String[] parts = offset.substring(1).split(":");
                return prefix + offset.charAt(0)
                        + String.format("%02d:%s", Integer.parseInt(parts[0]), parts[1]);
            }
        }
        return normalized;
    }

    /** 将页面时区时间转换为交易库存储时区时间。 */
    private LocalDateTime convertBetweenZones(LocalDateTime value,
                                              ZoneId sourceZone,
                                              ZoneId targetZone) {
        return value == null ? null
                : value.atZone(sourceZone).withZoneSameInstant(targetZone).toLocalDateTime();
    }

    /** 从已发布季度节点解析查询允许访问的最早时间。 */
    private LocalDateTime resolveRegisteredNodeBegin(List<String> physicalNodes) {
        if (physicalNodes == null || physicalNodes.isEmpty()) {
            return LocalDate.now().withDayOfMonth(1).atStartOfDay();
        }
        return physicalNodes.stream()
                .filter(value -> value != null && value.matches("\\d{4}0[1-4]"))
                .map(this::quarterBegin)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDate.now().withDayOfMonth(1).atStartOfDay());
    }

    /** 将 yyyy0Q 节点后缀转换为季度起始时间。 */
    private LocalDateTime quarterBegin(String suffix) {
        int year = Integer.parseInt(suffix.substring(0, 4));
        int quarter = Integer.parseInt(suffix.substring(5, 6));
        return LocalDateTime.of(year, (quarter - 1) * 3 + 1, 1, 0, 0);
    }

    private void appendTextFilter(StringBuilder sql, String value, String fragment) {
        if (StringUtils.hasText(value)) {
            sql.append(' ').append(fragment);
        }
    }

    private boolean isInvalidRange(LocalDateTime beginTime, LocalDateTime endTime) {
        return beginTime != null && endTime != null && beginTime.isAfter(endTime);
    }

    private LocalDateTime exclusiveEnd(LocalDateTime value) {
        return value.plusNanos(1_000_000L);
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RefundQueryRow
     * @date : 2026-08-08 00:40
     * @email : scott_x@163.com
     * @description : 商户退款接口记录与内部生命周期操作号的查询组合投影，operationId 不进入接口 DTO。
     * @status : create
     */
    private record RefundQueryRow(RefundRecord record, String operationId) {
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RootAmount
     * @date : 2026-08-08 00:40
     * @email : scott_x@163.com
     * @description : 生命周期根主单金额投影，用于兼容历史退款范围且允许交易金额为空。
     * @status : create
     */
    private record RootAmount(String operationId, BigDecimal transactionAmount) {
    }
}
