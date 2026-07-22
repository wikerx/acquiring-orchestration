package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingRangeTableContext;
import com.scott.payment.component.db.sharding.ShardingSingleTableContext;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionAmountSummaryResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationSummaryResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPaymentMethodSummaryResponse;
import com.scott.payment.merchant.service.MerchantTransactionQueryService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcMerchantTransactionQueryService
 * @date : 2026-07-20 00:00
 * @email : scott_x@163.com
 * @description : 商户后台交易只读 JDBC 查询实现，位于 service-merchant 服务实现层，直接读取交易分表并强制 merchant_id 过滤。
 * @status : create
 */
@Service
public class JdbcMerchantTransactionQueryService implements MerchantTransactionQueryService {

    private static final String TRANSACTION_ORDER_TABLE = "transaction_order";
    private static final String TRANSACTION_OPERATION_TABLE = "transaction_operation";
    private static final String TRANSACTION_PAYMENT_METHOD_INFO_TABLE = "transaction_payment_method_info";
    private static final String DEFAULT_QUERY_TIME_ZONE = "Asia/Shanghai";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ShardingDataTemplate shardingDataTemplate;
    private final TransactionShardingKeyParser transactionShardingKeyParser;

    /**
     * 创建商户交易只读查询实现。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     * @param shardingDataTemplate 分表数据访问统一入口
     * @param transactionShardingKeyParser 交易分表键解析器
     */
    public JdbcMerchantTransactionQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                               ShardingDataTemplate shardingDataTemplate,
                                               TransactionShardingKeyParser transactionShardingKeyParser) {
        this.jdbcTemplate = jdbcTemplate;
        this.shardingDataTemplate = shardingDataTemplate;
        this.transactionShardingKeyParser = transactionShardingKeyParser;
    }

    /**
     * 分页查询当前商户交易生命周期主单。
     *
     * @param query 已注入当前登录商户号的查询条件
     * @return 主单分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        long total = 0L;
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        List<TransactionOrderResponse> rows = new ArrayList<>();
        for (String table : physicalTablesInRange(TRANSACTION_ORDER_TABLE, safeQuery.getBeginTime(), safeQuery.getEndTime())) {
            long count = countOrders(table, safeQuery);
            total += count;
            if (rows.size() < limit && offset < count) {
                rows.addAll(selectOrders(table, safeQuery, offset, limit - rows.size()));
                offset = 0;
            } else {
                offset = Math.max(0, offset - count);
            }
        }
        enrichOrders(rows);
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), rows);
    }

    /**
     * 分页查询当前商户交易动作单并聚合统计。
     *
     * @param query 已注入当前登录商户号的查询条件
     * @return 动作单分页与统计
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public TransactionOperationSearchResponse searchOperations(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        TransactionOperationSearchResponse response = new TransactionOperationSearchResponse();
        response.setPage(pageOperations(safeQuery));
        response.setSummary(operationSummary(safeQuery));
        return response;
    }

    /**
     * 查询当前商户交易聚合详情。
     *
     * @param merchantId 当前登录商户号
     * @param transactionId 平台交易 ID
     * @return 商户可见交易详情
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public TransactionDetailResponse detail(String merchantId, String transactionId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant context missing");
        }
        if (!StringUtils.hasText(transactionId)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime transactionDateTime = parseTransactionDateTime(transactionId);
        if (transactionDateTime == null) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        String operationTable = physicalTable(TRANSACTION_OPERATION_TABLE, transactionDateTime);
        TransactionOperationResponse sourceOperation = selectOperationByTransactionId(operationTable, transactionId);
        if (sourceOperation == null || !merchantId.equals(sourceOperation.getMerchantId())) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime orderTime = parseOperationDateTime(sourceOperation.getOperationId());
        if (orderTime == null) {
            orderTime = sourceOperation.getTransactionDateTime();
        }
        TransactionOrderResponse order = selectOrderByOperationId(physicalTable(TRANSACTION_ORDER_TABLE, orderTime), sourceOperation.getOperationId());
        if (order == null || !merchantId.equals(order.getMerchantId())) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime beginTime = order.getTransactionDateTime() == null ? sourceOperation.getTransactionDateTime() : order.getTransactionDateTime();
        List<TransactionOperationResponse> operations = selectOperationsByOperationId(sourceOperation.getOperationId(), beginTime, LocalDateTime.now());
        operations.removeIf(operation -> !merchantId.equals(operation.getMerchantId()));
        operations.sort(Comparator.comparing(TransactionOperationResponse::getOperationSequence, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TransactionOperationResponse::getOperationTime, Comparator.nullsLast(LocalDateTime::compareTo)));
        enrichOperations(operations);
        enrichOrders(List.of(order));
        TransactionDetailResponse detail = new TransactionDetailResponse();
        detail.setOrder(order);
        detail.setOperations(operations);
        return detail;
    }

    private PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery safeQuery) {
        long total = 0L;
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        List<TransactionOperationResponse> rows = new ArrayList<>();
        for (String table : physicalTablesInRange(TRANSACTION_OPERATION_TABLE, safeQuery.getBeginTime(), safeQuery.getEndTime())) {
            String paymentTable = paymentInfoTableForOperationTable(table);
            long count = countOperations(table, paymentTable, safeQuery);
            total += count;
            if (rows.size() < limit && offset < count) {
                rows.addAll(selectOperations(table, paymentTable, safeQuery, offset, limit - rows.size()));
                offset = 0;
            } else {
                offset = Math.max(0, offset - count);
            }
        }
        enrichOperations(rows);
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), rows);
    }

    private TransactionOperationSummaryResponse operationSummary(TransactionPageQuery safeQuery) {
        SummaryAccumulator accumulator = new SummaryAccumulator();
        for (String table : physicalTablesInRange(TRANSACTION_OPERATION_TABLE, safeQuery.getBeginTime(), safeQuery.getEndTime())) {
            String paymentTable = paymentInfoTableForOperationTable(table);
            selectAmountSummary(table, paymentTable, safeQuery).forEach(accumulator::addAmount);
            selectPaymentMethodSummary(table, paymentTable, safeQuery).forEach(accumulator::addPaymentMethod);
        }
        return accumulator.toResponse();
    }

    private long countOrders(String table, TransactionPageQuery query) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM %s
                WHERE deleted = 0
                  AND merchant_id = :merchantId
                  AND transaction_date_time >= :beginTime
                  AND transaction_date_time <= :endTime
                %s
                """.formatted(table, orderWhereSql(query)), orderParams(query), Long.class);
    }

    private List<TransactionOrderResponse> selectOrders(String table, TransactionPageQuery query, long offset, long limit) {
        MapSqlParameterSource params = orderParams(query)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbcTemplate.query("""
                SELECT *
                FROM %s
                WHERE deleted = 0
                  AND merchant_id = :merchantId
                  AND transaction_date_time >= :beginTime
                  AND transaction_date_time <= :endTime
                %s
                ORDER BY transaction_date_time DESC, id DESC
                LIMIT :offset, :limit
                """.formatted(table, orderWhereSql(query)), params, orderMapper());
    }

    private long countOperations(String table, String paymentTable, TransactionPageQuery query) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM %s o
                WHERE o.deleted = 0
                  AND o.merchant_id = :merchantId
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time <= :endTime
                %s
                """.formatted(table, operationWhereSql(query, paymentTable)), operationParams(query), Long.class);
    }

    private List<TransactionOperationResponse> selectOperations(String table, String paymentTable, TransactionPageQuery query, long offset, long limit) {
        MapSqlParameterSource params = operationParams(query)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbcTemplate.query("""
                SELECT o.*
                FROM %s o
                WHERE o.deleted = 0
                  AND o.merchant_id = :merchantId
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time <= :endTime
                %s
                ORDER BY o.transaction_date_time DESC, o.id DESC
                LIMIT :offset, :limit
                """.formatted(table, operationWhereSql(query, paymentTable)), params, operationMapper());
    }

    private List<SummaryRow> selectAmountSummary(String table, String paymentTable, TransactionPageQuery query) {
        return jdbcTemplate.query("""
                SELECT o.transaction_status AS transaction_status,
                       COALESCE(o.transaction_currency, 'UNKNOWN') AS currency,
                       o.currency_exponent AS currency_exponent,
                       COUNT(1) AS count,
                       COALESCE(SUM(COALESCE(o.transaction_amount, 0)), 0) AS amount
                FROM %s o
                WHERE o.deleted = 0
                  AND o.merchant_id = :merchantId
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time <= :endTime
                %s
                GROUP BY o.transaction_status, COALESCE(o.transaction_currency, 'UNKNOWN'), o.currency_exponent
                """.formatted(table, operationWhereSql(query, paymentTable)), operationParams(query), summaryRowMapper());
    }

    private List<SummaryRow> selectPaymentMethodSummary(String table, String paymentTable, TransactionPageQuery query) {
        return jdbcTemplate.query("""
                SELECT COALESCE(p.payment_method, 'UNKNOWN') AS payment_method,
                       p.payment_brand AS payment_brand,
                       COALESCE(o.transaction_currency, 'UNKNOWN') AS currency,
                       o.currency_exponent AS currency_exponent,
                       COUNT(1) AS count,
                       COALESCE(SUM(COALESCE(o.transaction_amount, 0)), 0) AS amount
                FROM %s o
                LEFT JOIN %s p ON p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time
                WHERE o.deleted = 0
                  AND o.merchant_id = :merchantId
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time <= :endTime
                %s
                GROUP BY COALESCE(p.payment_method, 'UNKNOWN'), p.payment_brand, COALESCE(o.transaction_currency, 'UNKNOWN'), o.currency_exponent
                """.formatted(table, paymentTable, operationWhereSql(query, paymentTable)), operationParams(query), paymentSummaryRowMapper());
    }

    private String orderWhereSql(TransactionPageQuery query) {
        StringBuilder sql = new StringBuilder();
        appendTextFilter(sql, query.getMerchantOrderNo(), "AND merchant_order_no = :merchantOrderNo");
        appendTextFilter(sql, query.getTransactionId(), "AND (root_transaction_id = :transactionId OR latest_transaction_id = :transactionId)");
        appendTextFilter(sql, query.getSourceTransactionId(), "AND source_transaction_id = :sourceTransactionId");
        appendTextFilter(sql, query.getTransactionType(), "AND transaction_type = :transactionType");
        appendTextFilter(sql, query.getTransactionStatus(), "AND transaction_status = :transactionStatus");
        appendTextFilter(sql, query.getPaymentMethod(), "AND payment_method = :paymentMethod");
        appendTextFilter(sql, query.getPaymentBrand(), "AND payment_brand = :paymentBrand");
        appendTextFilter(sql, query.getChannelOrderNo(), "AND channel_order_no = :channelOrderNo");
        appendTextFilter(sql, query.getChannelMatchStatus(), "AND channel_match_status = :channelMatchStatus");
        appendTextFilter(sql, query.getReconciliationStatus(), "AND reconciliation_status = :reconciliationStatus");
        appendTextFilter(sql, query.getSettlementStatus(), "AND settlement_status = :settlementStatus");
        return sql.toString();
    }

    private String operationWhereSql(TransactionPageQuery query, String paymentTable) {
        StringBuilder sql = new StringBuilder();
        appendTextFilter(sql, query.getMerchantOrderNo(), "AND o.merchant_order_no = :merchantOrderNo");
        appendTextFilter(sql, query.getTransactionId(), "AND o.transaction_id = :transactionId");
        appendTextFilter(sql, query.getSourceTransactionId(), "AND o.source_transaction_id = :sourceTransactionId");
        appendTextFilter(sql, query.getTransactionType(), "AND o.transaction_type = :transactionType");
        appendTextFilter(sql, query.getTransactionStatus(), "AND o.transaction_status = :transactionStatus");
        appendTextFilter(sql, query.getChannelOrderNo(), "AND o.channel_order_no = :channelOrderNo");
        appendTextFilter(sql, query.getChannelMatchStatus(), "AND o.channel_match_status = :channelMatchStatus");
        appendTextFilter(sql, query.getReconciliationStatus(), "AND o.reconciliation_status = :reconciliationStatus");
        appendTextFilter(sql, query.getSettlementStatus(), "AND o.settlement_status = :settlementStatus");
        if (StringUtils.hasText(query.getPaymentMethod())) {
            sql.append(" AND EXISTS (SELECT 1 FROM ").append(paymentTable)
                    .append(" p WHERE p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time AND p.payment_method = :paymentMethod)");
        }
        if (StringUtils.hasText(query.getPaymentBrand())) {
            sql.append(" AND EXISTS (SELECT 1 FROM ").append(paymentTable)
                    .append(" p WHERE p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time AND p.payment_brand = :paymentBrand)");
        }
        return sql.toString();
    }

    private void appendTextFilter(StringBuilder sql, String value, String fragment) {
        if (StringUtils.hasText(value)) {
            sql.append(' ').append(fragment);
        }
    }

    private MapSqlParameterSource orderParams(TransactionPageQuery query) {
        return baseParams(query)
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("transactionId", query.getTransactionId())
                .addValue("sourceTransactionId", query.getSourceTransactionId())
                .addValue("transactionType", query.getTransactionType())
                .addValue("transactionStatus", query.getTransactionStatus())
                .addValue("paymentMethod", query.getPaymentMethod())
                .addValue("paymentBrand", query.getPaymentBrand())
                .addValue("channelOrderNo", query.getChannelOrderNo())
                .addValue("channelMatchStatus", query.getChannelMatchStatus())
                .addValue("reconciliationStatus", query.getReconciliationStatus())
                .addValue("settlementStatus", query.getSettlementStatus());
    }

    private MapSqlParameterSource operationParams(TransactionPageQuery query) {
        return baseParams(query)
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("transactionId", query.getTransactionId())
                .addValue("sourceTransactionId", query.getSourceTransactionId())
                .addValue("transactionType", query.getTransactionType())
                .addValue("transactionStatus", query.getTransactionStatus())
                .addValue("channelOrderNo", query.getChannelOrderNo())
                .addValue("channelMatchStatus", query.getChannelMatchStatus())
                .addValue("reconciliationStatus", query.getReconciliationStatus())
                .addValue("settlementStatus", query.getSettlementStatus())
                .addValue("paymentMethod", query.getPaymentMethod())
                .addValue("paymentBrand", query.getPaymentBrand());
    }

    private MapSqlParameterSource baseParams(TransactionPageQuery query) {
        return new MapSqlParameterSource()
                .addValue("merchantId", query.getMerchantId())
                .addValue("beginTime", query.getBeginTime())
                .addValue("endTime", query.getEndTime());
    }

    private TransactionOperationResponse selectOperationByTransactionId(String table, String transactionId) {
        List<TransactionOperationResponse> rows = jdbcTemplate.query("""
                SELECT *
                FROM %s
                WHERE transaction_id = :transactionId
                  AND deleted = 0
                LIMIT 1
                """.formatted(table), new MapSqlParameterSource("transactionId", transactionId), operationMapper());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private TransactionOrderResponse selectOrderByOperationId(String table, String operationId) {
        List<TransactionOrderResponse> rows = jdbcTemplate.query("""
                SELECT *
                FROM %s
                WHERE operation_id = :operationId
                  AND deleted = 0
                LIMIT 1
                """.formatted(table), new MapSqlParameterSource("operationId", operationId), orderMapper());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<TransactionOperationResponse> selectOperationsByOperationId(String operationId, LocalDateTime beginTime, LocalDateTime endTime) {
        List<TransactionOperationResponse> rows = new ArrayList<>();
        for (String table : physicalTablesInRange(TRANSACTION_OPERATION_TABLE, beginTime, endTime)) {
            rows.addAll(jdbcTemplate.query("""
                    SELECT *
                    FROM %s
                    WHERE operation_id = :operationId
                      AND deleted = 0
                    ORDER BY operation_sequence ASC, operation_time ASC
                    """.formatted(table), new MapSqlParameterSource("operationId", operationId), operationMapper()));
        }
        return rows;
    }

    private void enrichOperations(List<TransactionOperationResponse> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, PaymentInfoRow> paymentInfoByTransaction = paymentInfoByTransaction(rows);
        Map<String, TransactionOrderResponse> orderByOperation = orderByOperation(rows);
        for (TransactionOperationResponse row : rows) {
            PaymentInfoRow info = paymentInfoByTransaction.get(row.getTransactionId());
            if (info != null) {
                row.setPaymentMethod(info.paymentMethod());
                row.setPaymentBrand(info.paymentBrand());
                row.setCardBin(info.cardBin());
                row.setCardNumberMasked(info.cardNumberMasked());
            }
            TransactionOrderResponse order = orderByOperation.get(row.getOperationId());
            if (order != null) {
                row.setAuthorizedAmount(order.getAuthorizedAmount());
                row.setCapturedAmount(order.getCapturedAmount());
                row.setRefundedAmount(order.getRefundedAmount());
                row.setAvailableCaptureAmount(order.getAvailableCaptureAmount());
                row.setAvailableRefundAmount(order.getAvailableRefundAmount());
            }
            row.setAccessType("DIRECT_API");
        }
    }

    private void enrichOrders(List<TransactionOrderResponse> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, PaymentInfoRow> paymentInfoByTransaction = paymentInfoByOrderTransaction(rows);
        Map<String, PaymentInfoRow> paymentInfoByOperation = paymentInfoByOrderOperation(rows);
        Map<String, OperationVisibleInfoRow> operationInfoByOperation = operationVisibleInfoByOperation(rows);
        for (TransactionOrderResponse row : rows) {
            PaymentInfoRow info = firstPaymentInfo(row, paymentInfoByTransaction);
            if (!hasCardInfo(info)) {
                info = paymentInfoByOperation.get(row.getOperationId());
            }
            if (info != null) {
                row.setPaymentMethod(info.paymentMethod());
                row.setPaymentBrand(info.paymentBrand());
                row.setCardBin(info.cardBin());
                row.setCardNumberMasked(info.cardNumberMasked());
            }
            OperationVisibleInfoRow operationInfo = operationInfoByOperation.get(row.getOperationId());
            if (operationInfo != null) {
                row.setAuthCode(operationInfo.authCode());
                if (!StringUtils.hasText(row.getCardBin())) {
                    row.setCardBin(operationInfo.cardBin());
                }
                if (!StringUtils.hasText(row.getCardNumberMasked())) {
                    row.setCardNumberMasked(operationInfo.cardNumberMasked());
                }
            }
        }
    }

    private PaymentInfoRow firstPaymentInfo(TransactionOrderResponse row, Map<String, PaymentInfoRow> paymentInfoByTransaction) {
        PaymentInfoRow latest = paymentInfoByTransaction.get(row.getLatestTransactionId());
        if (hasCardInfo(latest)) {
            return latest;
        }
        PaymentInfoRow root = paymentInfoByTransaction.get(row.getRootTransactionId());
        if (hasCardInfo(root)) {
            return root;
        }
        return latest != null ? latest : root;
    }

    private boolean hasCardInfo(PaymentInfoRow row) {
        return row != null && (StringUtils.hasText(row.cardBin()) || StringUtils.hasText(row.cardNumberMasked()));
    }

    private Map<String, PaymentInfoRow> paymentInfoByTransaction(List<TransactionOperationResponse> rows) {
        Map<String, PaymentInfoRow> result = new LinkedHashMap<>();
        Map<LocalDateTime, List<String>> idsByTime = new LinkedHashMap<>();
        for (TransactionOperationResponse row : rows) {
            if (row.getTransactionDateTime() != null && StringUtils.hasText(row.getTransactionId())) {
                idsByTime.computeIfAbsent(row.getTransactionDateTime(), key -> new ArrayList<>()).add(row.getTransactionId());
            }
        }
        idsByTime.forEach((time, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            String table = physicalTable(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, time);
            jdbcTemplate.query("""
                    SELECT transaction_id, operation_id, payment_method, payment_brand, card_bin, card_last4, card_number_masked
                    FROM %s
                    WHERE transaction_id IN (:transactionIds)
                      AND transaction_date_time = :transactionDateTime
                    """.formatted(table), new MapSqlParameterSource()
                    .addValue("transactionIds", ids)
                    .addValue("transactionDateTime", time), paymentInfoMapper())
                    .forEach(row -> result.putIfAbsent(row.transactionId(), row));
        });
        return result;
    }

    private Map<String, PaymentInfoRow> paymentInfoByOrderTransaction(List<TransactionOrderResponse> rows) {
        Map<String, PaymentInfoRow> result = new LinkedHashMap<>();
        Map<LocalDateTime, List<String>> idsByTime = new LinkedHashMap<>();
        for (TransactionOrderResponse row : rows) {
            if (row.getTransactionDateTime() == null) {
                continue;
            }
            List<String> transactionIds = idsByTime.computeIfAbsent(row.getTransactionDateTime(), key -> new ArrayList<>());
            addIfText(transactionIds, row.getLatestTransactionId());
            addIfText(transactionIds, row.getRootTransactionId());
        }
        idsByTime.forEach((time, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            String table = physicalTable(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, time);
            jdbcTemplate.query("""
                    SELECT transaction_id, operation_id, payment_method, payment_brand, card_bin, card_last4, card_number_masked
                    FROM %s
                    WHERE transaction_date_time = :transactionDateTime
                      AND transaction_id IN (:transactionIds)
                    """.formatted(table), new MapSqlParameterSource()
                    .addValue("transactionDateTime", time)
                    .addValue("transactionIds", ids), paymentInfoMapper())
                    .forEach(row -> result.putIfAbsent(row.transactionId(), row));
        });
        return result;
    }

    private Map<String, PaymentInfoRow> paymentInfoByOrderOperation(List<TransactionOrderResponse> rows) {
        Map<String, PaymentInfoRow> result = new LinkedHashMap<>();
        Map<LocalDateTime, List<String>> operationIdsByTime = new LinkedHashMap<>();
        for (TransactionOrderResponse row : rows) {
            if (row.getTransactionDateTime() != null && StringUtils.hasText(row.getOperationId())) {
                operationIdsByTime.computeIfAbsent(row.getTransactionDateTime(), key -> new ArrayList<>()).add(row.getOperationId());
            }
        }
        operationIdsByTime.forEach((time, operationIds) -> {
            if (operationIds.isEmpty()) {
                return;
            }
            String table = physicalTable(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, time);
            jdbcTemplate.query("""
                    SELECT transaction_id, operation_id, payment_method, payment_brand, card_bin, card_last4, card_number_masked
                    FROM %s
                    WHERE operation_id IN (:operationIds)
                    ORDER BY transaction_date_time ASC, id ASC
                    """.formatted(table), new MapSqlParameterSource("operationIds", operationIds), paymentInfoMapper())
                    .forEach(row -> result.putIfAbsent(row.operationId(), row));
        });
        return result;
    }

    private Map<String, OperationVisibleInfoRow> operationVisibleInfoByOperation(List<TransactionOrderResponse> rows) {
        Map<String, OperationVisibleInfoRow> result = new LinkedHashMap<>();
        Map<LocalDateTime, List<String>> operationIdsByTime = new LinkedHashMap<>();
        for (TransactionOrderResponse row : rows) {
            if (row.getTransactionDateTime() != null && StringUtils.hasText(row.getOperationId())) {
                operationIdsByTime.computeIfAbsent(row.getTransactionDateTime(), key -> new ArrayList<>()).add(row.getOperationId());
            }
        }
        operationIdsByTime.forEach((time, operationIds) -> {
            if (operationIds.isEmpty()) {
                return;
            }
            String operationTable = physicalTable(TRANSACTION_OPERATION_TABLE, time);
            String paymentTable = physicalTable(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, time);
            jdbcTemplate.query("""
                    SELECT o.operation_id,
                           MAX(NULLIF(o.auth_code, '')) AS auth_code,
                           MAX(NULLIF(p.card_bin, '')) AS card_bin,
                           MAX(NULLIF(p.card_last4, '')) AS card_last4,
                           MAX(NULLIF(p.card_number_masked, '')) AS card_number_masked
                    FROM %s o
                    LEFT JOIN %s p ON p.operation_id = o.operation_id
                    WHERE o.operation_id IN (:operationIds)
                      AND o.deleted = 0
                    GROUP BY o.operation_id
                    """.formatted(operationTable, paymentTable), new MapSqlParameterSource("operationIds", operationIds), operationVisibleInfoMapper())
                    .forEach(row -> result.putIfAbsent(row.operationId(), row));
        });
        return result;
    }

    private void addIfText(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private Map<String, TransactionOrderResponse> orderByOperation(List<TransactionOperationResponse> rows) {
        Map<String, TransactionOrderResponse> result = new LinkedHashMap<>();
        for (TransactionOperationResponse row : rows) {
            if (!StringUtils.hasText(row.getOperationId()) || result.containsKey(row.getOperationId())) {
                continue;
            }
            LocalDateTime orderTime = parseOperationDateTime(row.getOperationId());
            if (orderTime == null) {
                orderTime = row.getTransactionDateTime();
            }
            if (orderTime == null) {
                continue;
            }
            TransactionOrderResponse order = selectOrderByOperationId(physicalTable(TRANSACTION_ORDER_TABLE, orderTime), row.getOperationId());
            if (order != null) {
                result.put(row.getOperationId(), order);
            }
        }
        return result;
    }

    private TransactionPageQuery normalize(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = query == null ? new TransactionPageQuery() : query;
        if (!StringUtils.hasText(safeQuery.getMerchantId())) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant context missing");
        }
        fillDefaultTimeRange(safeQuery);
        normalizeMerchantResponseCode(safeQuery);
        return safeQuery;
    }

    private void fillDefaultTimeRange(TransactionPageQuery query) {
        ZoneId queryZone = resolveQueryZone(query.getQueryTimeZone());
        LocalDateTime safeEnd = query.getEndTime() == null ? LocalDateTime.now(queryZone) : query.getEndTime();
        LocalDateTime safeBegin = query.getBeginTime() == null ? safeEnd.toLocalDate().atStartOfDay() : query.getBeginTime();
        if (safeBegin.isAfter(safeEnd)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "beginTime must not be after endTime");
        }
        query.setBeginTime(convertBetweenZones(safeBegin, queryZone, ZoneId.of(DEFAULT_QUERY_TIME_ZONE)));
        query.setEndTime(convertBetweenZones(safeEnd, queryZone, ZoneId.of(DEFAULT_QUERY_TIME_ZONE)));
        query.setQueryTimeZone(queryZone.getId());
    }

    private void normalizeMerchantResponseCode(TransactionPageQuery query) {
        if (!StringUtils.hasText(query.getMerchantResponseCode())) {
            return;
        }
        String mappedStatus = resolveStatusByMerchantResponseCode(query.getMerchantResponseCode());
        if (!StringUtils.hasText(mappedStatus)) {
            query.setTransactionStatus("__NO_MATCH__");
            return;
        }
        if (StringUtils.hasText(query.getTransactionStatus()) && !Objects.equals(query.getTransactionStatus(), mappedStatus)) {
            query.setTransactionStatus("__NO_MATCH__");
            return;
        }
        query.setTransactionStatus(mappedStatus);
    }

    private ZoneId resolveQueryZone(String queryTimeZone) {
        String zone = StringUtils.hasText(queryTimeZone) ? queryTimeZone.trim() : DEFAULT_QUERY_TIME_ZONE;
        try {
            return ZoneId.of(normalizeZoneId(zone));
        } catch (DateTimeException exception) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "queryTimeZone is invalid");
        }
    }

    private String normalizeZoneId(String zone) {
        if (!StringUtils.hasText(zone)) {
            return DEFAULT_QUERY_TIME_ZONE;
        }
        String normalized = zone.trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("UTC".equals(upper) || "GMT".equals(upper)) {
            return upper;
        }
        if (upper.startsWith("UTC+") || upper.startsWith("UTC-") || upper.startsWith("GMT+") || upper.startsWith("GMT-")) {
            String prefix = upper.substring(0, 3);
            String offset = upper.substring(3);
            if (offset.matches("[+-]\\d{1,2}")) {
                return prefix + String.format("%+03d:00", Integer.parseInt(offset));
            }
            if (offset.matches("[+-]\\d{1,2}:\\d{2}")) {
                String[] parts = offset.substring(1).split(":");
                return prefix + offset.charAt(0) + String.format("%02d:%s", Integer.parseInt(parts[0]), parts[1]);
            }
        }
        return normalized;
    }

    private LocalDateTime convertBetweenZones(LocalDateTime sourceTime, ZoneId sourceZone, ZoneId targetZone) {
        ZonedDateTime source = sourceTime.atZone(sourceZone);
        return source.withZoneSameInstant(targetZone).toLocalDateTime();
    }

    private long offset(PageRequest query) {
        return (query.safePageNo() - 1) * query.safePageSize();
    }

    private List<String> physicalTablesInRange(String logicalTable, LocalDateTime beginTime, LocalDateTime endTime) {
        return shardingDataTemplate.resolvePhysicalTables(
                ShardingRangeTableContext.of(logicalTable, beginTime, endTime, DataSourceName.SLAVE));
    }

    private String physicalTable(String logicalTable, LocalDateTime transactionDateTime) {
        return shardingDataTemplate.resolvePhysicalTable(
                ShardingSingleTableContext.of(logicalTable, transactionDateTime, DataSourceName.SLAVE));
    }

    private LocalDateTime parseTransactionDateTime(String transactionId) {
        return transactionShardingKeyParser.parseTransactionDateTime(transactionId);
    }

    private LocalDateTime parseOperationDateTime(String operationId) {
        return transactionShardingKeyParser.parseOperationDateTime(operationId);
    }

    private String paymentInfoTableForOperationTable(String operationPhysicalTable) {
        return operationPhysicalTable.replaceFirst("^" + TRANSACTION_OPERATION_TABLE, TRANSACTION_PAYMENT_METHOD_INFO_TABLE);
    }

    private RowMapper<TransactionOrderResponse> orderMapper() {
        return (rs, rowNum) -> {
            TransactionOrderResponse row = new TransactionOrderResponse();
            row.setOperationId(rs.getString("operation_id"));
            row.setRootTransactionId(rs.getString("root_transaction_id"));
            row.setLatestTransactionId(rs.getString("latest_transaction_id"));
            row.setMerchantId(rs.getString("merchant_id"));
            row.setMerchantOrderNo(rs.getString("merchant_order_no"));
            row.setMerchantOrderId(rs.getString("merchant_order_id"));
            row.setPaymentMethod(rs.getString("payment_method"));
            row.setPaymentBrand(rs.getString("payment_brand"));
            row.setCardBin(null);
            row.setCardNumberMasked(null);
            row.setAuthCode(null);
            row.setTransactionType(rs.getString("transaction_type"));
            row.setTransactionStatus(rs.getString("transaction_status"));
            row.setLifecycleStatus(resolveLifecycleStatus(row, rs));
            row.setLifecycleStatusMessage(row.getLifecycleStatus());
            row.setProcessStage(rs.getString("process_stage"));
            row.setLabelCurrency(rs.getString("label_currency"));
            row.setLabelAmount(rs.getBigDecimal("label_amount"));
            row.setTransactionCurrency(rs.getString("transaction_currency"));
            row.setTransactionAmount(rs.getBigDecimal("transaction_amount"));
            row.setCurrentCurrency(row.getTransactionCurrency());
            row.setCurrentAmount(resolveCurrentAmount(row, rs));
            row.setCurrencyExponent(nullableInt(rs, "currency_exponent"));
            row.setTransactionRate(defaultRate(rs.getBigDecimal("transaction_rate")));
            row.setDccEnabled(nullableInt(rs, "dcc_enabled"));
            row.setEdcEnabled(nullableInt(rs, "edc_enabled"));
            row.setMerchantResponseCode(resolveMerchantResponseCode(row.getTransactionStatus()));
            row.setMerchantResponseMessage(resolveMerchantResponseMessage(row.getTransactionStatus()));
            row.setAuthorizedAmount(rs.getBigDecimal("authorized_amount"));
            row.setCapturedAmount(rs.getBigDecimal("captured_amount"));
            row.setRefundedAmount(rs.getBigDecimal("refunded_amount"));
            row.setAvailableCaptureAmount(rs.getBigDecimal("available_capture_amount"));
            row.setAvailableRefundAmount(rs.getBigDecimal("available_refund_amount"));
            row.setSettlementStatus(rs.getString("settlement_status"));
            row.setReconciliationStatus(rs.getString("reconciliation_status"));
            row.setAccountingStatus(rs.getString("accounting_status"));
            row.setChannelMatchStatus(rs.getString("channel_match_status"));
            row.setChannelCode(rs.getString("channel_code"));
            row.setChannelOrderNo(rs.getString("channel_order_no"));
            row.setTransactionDateTime(localDateTime(rs, "transaction_date_time"));
            row.setTransactionTimeZone(rs.getString("transaction_time_zone"));
            return row;
        };
    }

    private RowMapper<TransactionOperationResponse> operationMapper() {
        return (rs, rowNum) -> {
            TransactionOperationResponse row = new TransactionOperationResponse();
            row.setOperationId(rs.getString("operation_id"));
            row.setTransactionId(rs.getString("transaction_id"));
            row.setSourceTransactionId(rs.getString("source_transaction_id"));
            row.setMerchantId(rs.getString("merchant_id"));
            row.setMerchantOrderNo(rs.getString("merchant_order_no"));
            row.setMerchantOrderId(rs.getString("merchant_order_id"));
            row.setOperationSequence(nullableInt(rs, "operation_sequence"));
            row.setTransactionType(rs.getString("transaction_type"));
            row.setTransactionStatus(rs.getString("transaction_status"));
            row.setProcessStage(rs.getString("process_stage"));
            row.setLabelCurrency(rs.getString("label_currency"));
            row.setLabelAmount(rs.getBigDecimal("label_amount"));
            row.setTransactionCurrency(rs.getString("transaction_currency"));
            row.setTransactionAmount(rs.getBigDecimal("transaction_amount"));
            row.setCurrencyExponent(nullableInt(rs, "currency_exponent"));
            row.setTransactionRate(defaultRate(rs.getBigDecimal("transaction_rate")));
            row.setDccEnabled(nullableInt(rs, "dcc_enabled"));
            row.setEdcEnabled(nullableInt(rs, "edc_enabled"));
            row.setMerchantResponseCode(resolveMerchantResponseCode(row.getTransactionStatus()));
            row.setMerchantResponseMessage(resolveMerchantResponseMessage(row.getTransactionStatus()));
            row.setChannelCode(rs.getString("channel_code"));
            row.setChannelOrderNo(rs.getString("channel_order_no"));
            row.setChannelTransactionId(rs.getString("channel_transaction_id"));
            row.setAuthCode(rs.getString("auth_code"));
            row.setAcquirerReferenceNo(rs.getString("acquirer_reference_no"));
            row.setSettlementStatus(rs.getString("settlement_status"));
            row.setReconciliationStatus(rs.getString("reconciliation_status"));
            row.setAccountingStatus(rs.getString("accounting_status"));
            row.setChannelMatchStatus(rs.getString("channel_match_status"));
            row.setTransactionDateTime(localDateTime(rs, "transaction_date_time"));
            row.setOperationTime(localDateTime(rs, "operation_time"));
            return row;
        };
    }

    private RowMapper<SummaryRow> summaryRowMapper() {
        return (rs, rowNum) -> new SummaryRow(
                rs.getString("transaction_status"),
                null,
                null,
                rs.getString("currency"),
                nullableInt(rs, "currency_exponent"),
                rs.getLong("count"),
                rs.getBigDecimal("amount"));
    }

    private RowMapper<SummaryRow> paymentSummaryRowMapper() {
        return (rs, rowNum) -> new SummaryRow(
                null,
                rs.getString("payment_method"),
                rs.getString("payment_brand"),
                rs.getString("currency"),
                nullableInt(rs, "currency_exponent"),
                rs.getLong("count"),
                rs.getBigDecimal("amount"));
    }

    private RowMapper<PaymentInfoRow> paymentInfoMapper() {
        return (rs, rowNum) -> {
            String masked = rs.getString("card_number_masked");
            String cardBin = normalizeCardBin(rs.getString("card_bin"), masked);
            return new PaymentInfoRow(
                    rs.getString("transaction_id"),
                    rs.getString("operation_id"),
                    rs.getString("payment_method"),
                    rs.getString("payment_brand"),
                    cardBin,
                    normalizeCardNumberMasked(cardBin, rs.getString("card_last4"), masked));
        };
    }

    private RowMapper<OperationVisibleInfoRow> operationVisibleInfoMapper() {
        return (rs, rowNum) -> {
            String masked = rs.getString("card_number_masked");
            String cardBin = normalizeCardBin(rs.getString("card_bin"), masked);
            return new OperationVisibleInfoRow(
                    rs.getString("operation_id"),
                    rs.getString("auth_code"),
                    cardBin,
                    normalizeCardNumberMasked(cardBin, rs.getString("card_last4"), masked));
        };
    }

    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private BigDecimal resolveCurrentAmount(TransactionOrderResponse row, ResultSet rs) throws SQLException {
        if (isAuthorizationLike(row.getTransactionType())) {
            return rs.getBigDecimal("authorized_amount");
        }
        return row.getTransactionAmount();
    }

    private String resolveLifecycleStatus(TransactionOrderResponse row, ResultSet rs) throws SQLException {
        if (!"SUCCESS".equals(row.getTransactionStatus())) {
            return row.getTransactionStatus();
        }
        BigDecimal authorized = zeroIfNull(rs.getBigDecimal("authorized_amount"));
        BigDecimal captured = zeroIfNull(rs.getBigDecimal("captured_amount"));
        BigDecimal refunded = zeroIfNull(rs.getBigDecimal("refunded_amount"));
        BigDecimal availableCapture = zeroIfNull(rs.getBigDecimal("available_capture_amount"));
        BigDecimal availableRefund = zeroIfNull(rs.getBigDecimal("available_refund_amount"));
        if (isAuthorizationLike(row.getTransactionType()) && captured.signum() == 0 && refunded.signum() == 0
                && authorized.signum() > 0 && availableCapture.signum() == 0) {
            return "VOIDED";
        }
        if (refunded.signum() > 0 && availableRefund.signum() == 0) {
            return "FULLY_REFUNDED";
        }
        if (refunded.signum() > 0) {
            return "PARTIALLY_REFUNDED";
        }
        if (captured.signum() > 0 && availableCapture.signum() == 0) {
            return "CAPTURED";
        }
        if (captured.signum() > 0) {
            return "PARTIALLY_CAPTURED";
        }
        return row.getTransactionStatus();
    }

    private boolean isAuthorizationLike(String transactionType) {
        return "AUTHORIZATION".equals(transactionType) || "PRE_AUTHORIZATION".equals(transactionType) || "PRE_AUTH_COMPLETION".equals(transactionType);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal defaultRate(BigDecimal value) {
        return value == null ? new BigDecimal("1.00000000") : value;
    }

    private String resolveMerchantResponseCode(String transactionStatus) {
        if ("SUCCESS".equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_SUCCESS.getCode();
        }
        if ("FAILED".equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_REJECTED.getCode();
        }
        if ("PENDING".equals(transactionStatus)) {
            return ApiResultEnum.PENDING.getCode();
        }
        return ApiResultEnum.PROCESSING.getCode();
    }

    private String resolveMerchantResponseMessage(String transactionStatus) {
        if ("SUCCESS".equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_SUCCESS.getMessage();
        }
        if ("FAILED".equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_REJECTED.getMessage();
        }
        if ("PENDING".equals(transactionStatus)) {
            return ApiResultEnum.PENDING.getMessage();
        }
        return ApiResultEnum.PROCESSING.getMessage();
    }

    private String resolveStatusByMerchantResponseCode(String merchantResponseCode) {
        String code = merchantResponseCode == null ? "" : merchantResponseCode.trim();
        if (ApiResultEnum.PAYMENT_SUCCESS.getCode().equalsIgnoreCase(code) || ApiResultEnum.SUCCESS.getCode().equalsIgnoreCase(code)) {
            return "SUCCESS";
        }
        if (ApiResultEnum.PAYMENT_REJECTED.getCode().equalsIgnoreCase(code)
                || ApiResultEnum.PAYMENT_REJECTED_BY_ISSUER.getCode().equalsIgnoreCase(code)) {
            return "FAILED";
        }
        if (ApiResultEnum.PENDING.getCode().equalsIgnoreCase(code)) {
            return "PENDING";
        }
        if (ApiResultEnum.PROCESSING.getCode().equalsIgnoreCase(code)) {
            return "PROCESSING";
        }
        return null;
    }

    private String normalizeCardNumberMasked(String cardBin, String cardLast4, String masked) {
        if (StringUtils.hasText(cardBin) && cardBin.length() >= 6 && StringUtils.hasText(cardLast4)) {
            return cardBin.substring(0, 6) + "****" + cardLast4;
        }
        if (!StringUtils.hasText(masked)) {
            return null;
        }
        String digits = masked.replaceAll("\\D", "");
        if (digits.length() >= 10) {
            return digits.substring(0, 6) + "****" + digits.substring(digits.length() - 4);
        }
        return masked;
    }

    private String normalizeCardBin(String cardBin, String masked) {
        if (StringUtils.hasText(cardBin)) {
            String digits = cardBin.replaceAll("\\D", "");
            if (digits.length() >= 6) {
                return digits;
            }
        }
        if (!StringUtils.hasText(masked)) {
            return null;
        }
        String digits = masked.replaceAll("\\D", "");
        return digits.length() >= 6 ? digits.substring(0, 6) : null;
    }

    private record PaymentInfoRow(String transactionId, String operationId, String paymentMethod, String paymentBrand,
                                  String cardBin, String cardNumberMasked) {
    }

    private record OperationVisibleInfoRow(String operationId, String authCode, String cardBin, String cardNumberMasked) {
    }

    private record SummaryRow(String transactionStatus, String paymentMethod, String paymentBrand, String currency,
                              Integer currencyExponent, long count, BigDecimal amount) {
    }

    private static class SummaryAccumulator {
        private long totalCount;
        private long successCount;
        private long failedCount;
        private final Map<String, TransactionAmountSummaryResponse> amountSummaries = new LinkedHashMap<>();
        private final Map<String, TransactionAmountSummaryResponse> successAmountSummaries = new LinkedHashMap<>();
        private final Map<String, TransactionAmountSummaryResponse> failedAmountSummaries = new LinkedHashMap<>();
        private final Map<String, TransactionPaymentMethodSummaryResponse> paymentMethodSummaries = new LinkedHashMap<>();

        void addAmount(SummaryRow row) {
            totalCount += row.count();
            if ("SUCCESS".equals(row.transactionStatus())) {
                successCount += row.count();
                mergeAmount(successAmountSummaries, row);
            } else if ("FAILED".equals(row.transactionStatus())) {
                failedCount += row.count();
                mergeAmount(failedAmountSummaries, row);
            }
            mergeAmount(amountSummaries, row);
        }

        void addPaymentMethod(SummaryRow row) {
            String key = (row.paymentMethod() == null ? "UNKNOWN" : row.paymentMethod()) + "|" + (row.paymentBrand() == null ? "" : row.paymentBrand());
            TransactionPaymentMethodSummaryResponse summary = paymentMethodSummaries.computeIfAbsent(key, ignored -> {
                TransactionPaymentMethodSummaryResponse value = new TransactionPaymentMethodSummaryResponse();
                value.setPaymentMethod(row.paymentMethod());
                value.setPaymentBrand(row.paymentBrand());
                value.setAmountSummaries(new ArrayList<>());
                return value;
            });
            summary.setCount(summary.getCount() + row.count());
            Map<String, TransactionAmountSummaryResponse> amounts = new LinkedHashMap<>();
            summary.getAmountSummaries().forEach(item -> amounts.put(item.getCurrency(), item));
            mergeAmount(amounts, row);
            summary.setAmountSummaries(new ArrayList<>(amounts.values()));
        }

        TransactionOperationSummaryResponse toResponse() {
            TransactionOperationSummaryResponse response = new TransactionOperationSummaryResponse();
            response.setTotalCount(totalCount);
            response.setSuccessCount(successCount);
            response.setFailedCount(failedCount);
            response.setAmountSummaries(new ArrayList<>(amountSummaries.values()));
            response.setSuccessAmountSummaries(new ArrayList<>(successAmountSummaries.values()));
            response.setFailedAmountSummaries(new ArrayList<>(failedAmountSummaries.values()));
            response.setPaymentMethodSummaries(new ArrayList<>(paymentMethodSummaries.values()));
            return response;
        }

        private void mergeAmount(Map<String, TransactionAmountSummaryResponse> target, SummaryRow row) {
            TransactionAmountSummaryResponse amount = target.computeIfAbsent(row.currency(), ignored -> {
                TransactionAmountSummaryResponse value = new TransactionAmountSummaryResponse();
                value.setCurrency(row.currency());
                value.setCurrencyExponent(row.currencyExponent());
                value.setAmount(BigDecimal.ZERO);
                return value;
            });
            amount.setAmount(amount.getAmount().add(row.amount() == null ? BigDecimal.ZERO : row.amount()));
        }
    }
}
