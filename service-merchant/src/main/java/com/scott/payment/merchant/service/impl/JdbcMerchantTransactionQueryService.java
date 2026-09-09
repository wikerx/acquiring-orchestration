package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcMerchantTransactionQueryService
 * @date : 2026-07-20 00:00
 * @email : scott_x@163.com
 * @description : 商户后台交易只读查询实现，仅访问 ShardingSphere 交易逻辑表，并在主查询和富化查询中强制 merchant_id 与分片时间。
 * @status : create
 */
@Service
public class JdbcMerchantTransactionQueryService implements MerchantTransactionQueryService {

    /**
     * 交易订单表常量，统一 {@code JdbcMerchantTransactionQueryService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String TRANSACTION_ORDER_TABLE = "transaction_order";
    /**
     * 交易动作表常量，统一 {@code JdbcMerchantTransactionQueryService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String TRANSACTION_OPERATION_TABLE = "transaction_operation";
    /**
     * 交易支付方式信息表，表示支付方式、通知方式或调用方式。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String TRANSACTION_PAYMENT_METHOD_INFO_TABLE = "transaction_payment_method_info";
    /**
     * 交易主单查询仅投影商户页面实际使用的字段。禁止使用 SELECT *，避免 ShardingSphere
     * 在运行期间表结构新增结算字段后继续按旧元数据列序号读取，导致时间、网址等字段串列。
     */
    private static final String ORDER_QUERY_COLUMNS = """
            operation_id, root_transaction_id, latest_transaction_id, merchant_id,
            merchant_order_no, merchant_order_id, payment_method, payment_brand,
            transaction_type, transaction_status, process_stage,
            label_currency, label_amount, transaction_currency, transaction_amount,
            currency_exponent, transaction_rate, dcc_enabled, edc_enabled,
            merchant_visible_message, authorized_amount, captured_amount, refunded_amount,
            available_capture_amount, available_refund_amount,
            settlement_currency, settlement_amount, settlement_rate, settlement_date,
            settlement_batch_no, settlement_transaction_id, settlement_transaction_date_time,
            settlement_status, reconciliation_status, accounting_status,
            channel_code, channel_order_no, transaction_date_time, transaction_time_zone
            """;
    /** 交易动作查询的稳定显式投影，包含页面展示所需的交易、汇率与结算状态字段。 */
    private static final String OPERATION_QUERY_COLUMNS = """
            operation_id, transaction_id, source_transaction_id, merchant_id,
            merchant_order_no, merchant_order_id, operation_sequence,
            transaction_type, transaction_status, process_stage,
            label_currency, label_amount, transaction_currency, transaction_amount,
            currency_exponent, transaction_rate, dcc_enabled, edc_enabled,
            channel_code, channel_order_no, channel_transaction_id, auth_code,
            acquirer_reference_no, settlement_currency, settlement_amount, settlement_rate,
            settlement_date, settlement_batch_no,
            settlement_status, reconciliation_status, accounting_status,
            transaction_date_time, operation_time, request_source
            """;
    /**
     * {@code ACCESS_TYPE_DIRECT_API}，用于区分 {@code JdbcMerchantTransactionQueryService} 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String ACCESS_TYPE_DIRECT_API = "DIRECT_API";
    /**
     * {@code ACCESS_TYPE_HOSTED_CHECKOUT}，用于区分 {@code JdbcMerchantTransactionQueryService} 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String ACCESS_TYPE_HOSTED_CHECKOUT = "HOSTED_CHECKOUT";
    /**
     * 默认查询时间时区常量，统一 {@code JdbcMerchantTransactionQueryService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String DEFAULT_QUERY_TIME_ZONE = "Asia/Shanghai";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    /** 在 transaction 逻辑数据源上执行普通读和强一致读。 */
    private final TransactionLogicalReadExecutor transactionLogicalReadExecutor;
    /** 单次同步查询允许返回的最大记录数。 */
    private final int maxResultRows;
    /** 当前版本已登记物理节点中的最早季度，用于受控批量定位生命周期主单。 */
    private final LocalDateTime registeredNodeBegin;

    /**
     * 创建商户交易只读查询实现。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     */
    public JdbcMerchantTransactionQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new TransactionLogicalReadExecutor(),
                new TransactionShardingProperties());
    }

    /**
     * 创建生产环境商户交易查询服务，并为每条 JDBC Statement 应用同步查询超时。
     *
     * @param dataSource dynamic-datasource 外层路由数据源
     * @param transactionLogicalReadExecutor 交易逻辑数据源只读执行器
     * @param shardingProperties 查询资源预算配置
     * @param queryJdbcTemplateFactory 查询专用 JDBC 模板工厂
     */
    @Autowired
    public JdbcMerchantTransactionQueryService(DataSource dataSource,
                                               TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                               TransactionShardingProperties shardingProperties,
                                               TransactionQueryJdbcTemplateFactory queryJdbcTemplateFactory) {
        this(queryJdbcTemplateFactory.create(dataSource, shardingProperties),
                transactionLogicalReadExecutor, shardingProperties);
    }

    /**
     * 创建同时执行商户隔离、逻辑路由和结果行数预算的交易查询服务。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     * @param transactionLogicalReadExecutor 交易逻辑数据源只读执行器
     * @param shardingProperties 查询资源预算配置
     */
    public JdbcMerchantTransactionQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                               TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                               TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionLogicalReadExecutor = transactionLogicalReadExecutor;
        this.maxResultRows = shardingProperties.getQueryBudget().getMaxResultRows();
        this.registeredNodeBegin = resolveRegisteredNodeBegin(shardingProperties.getPhysicalNodes());
    }

    /**
     * 分页查询当前商户交易生命周期主单。
     *
     * @param query 已注入当前登录商户号的查询条件
     * @return 主单分页结果
     */
    @Override
    public PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        if (isEmptyTimeRange(safeQuery)) {
            return emptyPage(safeQuery);
        }
        return executeRead(false, () -> pageOrdersNormalized(safeQuery));
    }

    /**
     * 按已注入的 merchant_id 和分片时间范围执行分页，任何 SQL 分支均不得移除商户谓词。
     *
     * @param safeQuery 已校验且 merchantId 非空的查询
     * @return 仅包含当前商户数据的全季度分页结果
     */
    private PageResult<TransactionOrderResponse> pageOrdersNormalized(TransactionPageQuery safeQuery) {
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        long total = countOrders(TRANSACTION_ORDER_TABLE, safeQuery);
        List<TransactionOrderResponse> rows = offset < total
                ? selectOrders(TRANSACTION_ORDER_TABLE, safeQuery, offset, limit)
                : List.of();
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
    public TransactionOperationSearchResponse searchOperations(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        if (isEmptyTimeRange(safeQuery)) {
            TransactionOperationSearchResponse response = new TransactionOperationSearchResponse();
            response.setPage(emptyPage(safeQuery));
            response.setSummary(new TransactionOperationSummaryResponse());
            return response;
        }
        return executeRead(false, () -> {
            TransactionOperationSearchResponse response = new TransactionOperationSearchResponse();
            response.setPage(pageOperations(safeQuery));
            response.setSummary(operationSummary(safeQuery));
            return response;
        });
    }

    /**
     * 查询当前商户交易聚合详情。
     *
     * @param merchantId 当前登录商户号
     * @param transactionId 平台交易 ID
     * @param transactionDateTime 列表查询返回的真实交易分片时间
     * @return 商户可见交易详情
     */
    @Override
    public TransactionDetailResponse detail(String merchantId,
                                            String transactionId,
                                            LocalDateTime transactionDateTime,
                                            LocalDateTime rootTransactionDateTime) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant context missing");
        }
        if (!StringUtils.hasText(transactionId)
                || transactionDateTime == null
                || rootTransactionDateTime == null) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID);
        }
        return executeRead(true,
                () -> detailNormalized(
                        merchantId, transactionId, transactionDateTime, rootTransactionDateTime));
    }

    /**
     * 在主库读作用域装配商户交易详情，并在动作单和主单两层重复校验 merchant_id。
     *
     * @param merchantId 当前登录商户号
     * @param transactionId 平台交易号
     * @param transactionDateTime 列表查询返回的真实交易分片时间
     * @return 当前商户可见的聚合详情
     */
    private TransactionDetailResponse detailNormalized(String merchantId,
                                                        String transactionId,
                                                        LocalDateTime transactionDateTime,
                                                        LocalDateTime rootTransactionDateTime) {
        TransactionOperationResponse sourceOperation = selectOperationByTransactionId(
                TRANSACTION_OPERATION_TABLE, transactionId, transactionDateTime, merchantId);
        if (sourceOperation == null || !merchantId.equals(sourceOperation.getMerchantId())) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderResponse order = selectOrderByOperationId(
                TRANSACTION_ORDER_TABLE, sourceOperation.getOperationId(), rootTransactionDateTime, merchantId);
        if (order == null || !merchantId.equals(order.getMerchantId())) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime beginTime = order.getTransactionDateTime() == null ? sourceOperation.getTransactionDateTime() : order.getTransactionDateTime();
        List<TransactionOperationResponse> operations = selectOperationsByOperationId(
                sourceOperation.getOperationId(), merchantId, beginTime, LocalDateTime.now());
        operations.sort(Comparator.comparing(TransactionOperationResponse::getOperationSequence, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TransactionOperationResponse::getOperationTime, Comparator.nullsLast(LocalDateTime::compareTo)));
        enrichOperations(operations);
        enrichOrders(List.of(order));
        TransactionDetailResponse detail = new TransactionDetailResponse();
        detail.setOrder(order);
        detail.setOperations(operations);
        return detail;
    }

    /**
     * 通过交易逻辑表分页查询当前商户的交易操作单，由 ShardingSphere 完成跨季度路由和归并。
     *
     * @param safeQuery 已校验并绑定 merchantId 和时间范围的查询
     * @return 跨季度统一分页结果
     */
    private PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery safeQuery) {
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        long total = countOperations(
                TRANSACTION_OPERATION_TABLE, TRANSACTION_PAYMENT_METHOD_INFO_TABLE, safeQuery);
        List<TransactionOperationResponse> rows = offset < total
                ? selectOperations(
                        TRANSACTION_OPERATION_TABLE,
                        TRANSACTION_PAYMENT_METHOD_INFO_TABLE,
                        safeQuery,
                        offset,
                        limit)
                : List.of();
        enrichOperations(rows);
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), rows);
    }

    /**
     * 通过交易逻辑表汇总当前商户交易操作金额和支付方式。
     * 金额保持数据库 {@code BigDecimal} 精度，并始终按币种分别汇总。
     *
     * @param safeQuery 已校验并绑定商户和时间范围的查询
     * @return 跨季度操作统计
     */
    private TransactionOperationSummaryResponse operationSummary(TransactionPageQuery safeQuery) {
        SummaryAccumulator accumulator = new SummaryAccumulator();
        selectAmountSummary(
                TRANSACTION_OPERATION_TABLE,
                TRANSACTION_PAYMENT_METHOD_INFO_TABLE,
                safeQuery).forEach(accumulator::addAmount);
        selectPaymentMethodSummary(
                TRANSACTION_OPERATION_TABLE,
                TRANSACTION_PAYMENT_METHOD_INFO_TABLE,
                safeQuery).forEach(accumulator::addPaymentMethod);
        return accumulator.toResponse();
    }

    private long countOrders(String table, TransactionPageQuery query) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM %s
                WHERE deleted = 0
                  AND merchant_id = :merchantId
                  AND transaction_date_time >= :beginTime
                  AND transaction_date_time < :endTime
                %s
                """.formatted(table, orderWhereSql(query)), orderParams(query), Long.class);
    }

    private List<TransactionOrderResponse> selectOrders(String table, TransactionPageQuery query, long offset, long limit) {
        MapSqlParameterSource params = orderParams(query)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbcTemplate.query("""
                SELECT %s
                FROM %s
                WHERE deleted = 0
                  AND merchant_id = :merchantId
                  AND transaction_date_time >= :beginTime
                  AND transaction_date_time < :endTime
                %s
                ORDER BY transaction_date_time DESC, id DESC
                LIMIT :offset, :limit
                """.formatted(ORDER_QUERY_COLUMNS, table, orderWhereSql(query)), params, orderMapper());
    }

    private long countOperations(String table, String paymentTable, TransactionPageQuery query) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM %s o
                WHERE o.deleted = 0
                  AND o.merchant_id = :merchantId
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time < :endTime
                %s
                """.formatted(table, operationWhereSql(query, paymentTable)), operationParams(query), Long.class);
    }

    private List<TransactionOperationResponse> selectOperations(String table, String paymentTable, TransactionPageQuery query, long offset, long limit) {
        MapSqlParameterSource params = operationParams(query)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbcTemplate.query("""
                SELECT %s
                FROM %s o
                WHERE o.deleted = 0
                  AND o.merchant_id = :merchantId
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time < :endTime
                %s
                ORDER BY o.transaction_date_time DESC, o.id DESC
                LIMIT :offset, :limit
                """.formatted(OPERATION_QUERY_COLUMNS, table, operationWhereSql(query, paymentTable)), params, operationMapper());
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
                  AND o.transaction_date_time < :endTime
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
                  AND o.transaction_date_time < :endTime
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
                .addValue("reconciliationStatus", query.getReconciliationStatus())
                .addValue("settlementStatus", query.getSettlementStatus())
                .addValue("paymentMethod", query.getPaymentMethod())
                .addValue("paymentBrand", query.getPaymentBrand());
    }

    private MapSqlParameterSource baseParams(TransactionPageQuery query) {
        return new MapSqlParameterSource()
                .addValue("merchantId", query.getMerchantId())
                .addValue("beginTime", query.getBeginTime())
                .addValue("endTime", exclusiveEnd(query.getEndTime()));
    }

    private TransactionOperationResponse selectOperationByTransactionId(String table,
                                                                         String transactionId,
                                                                         LocalDateTime transactionDateTime,
                                                                         String merchantId) {
        List<TransactionOperationResponse> rows = jdbcTemplate.query("""
                SELECT %s
                FROM %s
                WHERE transaction_id = :transactionId
                  AND merchant_id = :merchantId
                  AND transaction_date_time = :transactionDateTime
                  AND deleted = 0
                LIMIT 1
                """.formatted(OPERATION_QUERY_COLUMNS, table), new MapSqlParameterSource()
                .addValue("transactionId", transactionId)
                .addValue("merchantId", merchantId)
                .addValue("transactionDateTime", transactionDateTime),
                operationMapper());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private TransactionOrderResponse selectOrderByOperationId(String table,
                                                               String operationId,
                                                               LocalDateTime transactionDateTime,
                                                               String merchantId) {
        List<TransactionOrderResponse> rows = jdbcTemplate.query("""
                SELECT %s
                FROM %s
                WHERE operation_id = :operationId
                  AND merchant_id = :merchantId
                  AND transaction_date_time = :transactionDateTime
                  AND deleted = 0
                LIMIT 1
                """.formatted(ORDER_QUERY_COLUMNS, table), new MapSqlParameterSource()
                .addValue("operationId", operationId)
                .addValue("merchantId", merchantId)
                .addValue("transactionDateTime", transactionDateTime), orderMapper());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<TransactionOperationResponse> selectOperationsByOperationId(String operationId,
                                                                              String merchantId,
                                                                              LocalDateTime beginTime,
                                                                              LocalDateTime endTime) {
        return jdbcTemplate.query("""
                    SELECT %s
                    FROM %s
                    WHERE operation_id = :operationId
                      AND merchant_id = :merchantId
                      AND transaction_date_time >= :beginTime
                      AND transaction_date_time < :endTime
                      AND deleted = 0
                    ORDER BY operation_sequence ASC, operation_time ASC
                    """.formatted(OPERATION_QUERY_COLUMNS, TRANSACTION_OPERATION_TABLE), new MapSqlParameterSource()
                    .addValue("operationId", operationId)
                    .addValue("merchantId", merchantId)
                    .addValue("beginTime", beginTime)
                    .addValue("endTime", exclusiveEnd(endTime)), operationMapper());
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
                row.setRootTransactionDateTime(order.getTransactionDateTime());
                row.setAuthorizedAmount(order.getAuthorizedAmount());
                row.setCapturedAmount(order.getCapturedAmount());
                row.setRefundedAmount(order.getRefundedAmount());
                row.setAvailableCaptureAmount(order.getAvailableCaptureAmount());
                row.setAvailableRefundAmount(order.getAvailableRefundAmount());
                row.setMerchantResponseMessage(resolveMerchantResponseMessage(
                        row.getTransactionStatus(),
                        order.getMerchantResponseMessage()));
            }
        }
        enrichAccessTypes(rows);
        enrichOperationThreeDs(rows);
    }

    /** 批量识别当前商户的历史收银台交易，避免逐条查询和跨商户读取。 */
    private void enrichAccessTypes(List<TransactionOperationResponse> rows) {
        String merchantId = rows.stream()
                .map(TransactionOperationResponse::getMerchantId)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        List<String> unresolvedTransactionIds = rows.stream()
                .filter(row -> !ACCESS_TYPE_HOSTED_CHECKOUT.equals(row.getAccessType()))
                .map(TransactionOperationResponse::getTransactionId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (!StringUtils.hasText(merchantId) || unresolvedTransactionIds.isEmpty()) {
            return;
        }
        Set<String> hostedTransactionIds = new HashSet<>(jdbcTemplate.queryForList("""
                SELECT DISTINCT transaction_id
                FROM payment_checkout_attempt
                WHERE merchant_id = :merchantId
                  AND transaction_id IN (:transactionIds)
                  AND deleted = 0
                """, new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("transactionIds", unresolvedTransactionIds), String.class));
        for (TransactionOperationResponse row : rows) {
            if (hostedTransactionIds.contains(row.getTransactionId())) {
                row.setAccessType(ACCESS_TYPE_HOSTED_CHECKOUT);
            } else if (!StringUtils.hasText(row.getAccessType())) {
                row.setAccessType(ACCESS_TYPE_DIRECT_API);
            }
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
        enrichOrderThreeDs(rows);
    }

    /** 按生命周期操作号批量补齐商户可见主单 3DS 标识。 */
    private void enrichOrderThreeDs(List<TransactionOrderResponse> rows) {
        Set<String> threeDsOperationIds = findThreeDsOperationIds(rows.stream()
                .map(TransactionOrderResponse::getOperationId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList(), rows.get(0).getMerchantId());
        rows.forEach(row -> row.setThreeDsEnabled(threeDsOperationIds.contains(row.getOperationId()) ? 1 : 0));
    }

    /** 按生命周期操作号批量补齐商户可见动作单 3DS 标识。 */
    private void enrichOperationThreeDs(List<TransactionOperationResponse> rows) {
        Set<String> threeDsOperationIds = findThreeDsOperationIds(rows.stream()
                .map(TransactionOperationResponse::getOperationId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList(), rows.get(0).getMerchantId());
        rows.forEach(row -> row.setThreeDsEnabled(threeDsOperationIds.contains(row.getOperationId()) ? 1 : 0));
    }

    /**
     * 在商户边界内优先读取平台支付方式 3DS 快照，并回退 Hosted Checkout 尝试记录。
     * 支付方式查询同时绑定操作号、登记分片范围和动作单商户归属，避免跨商户读取。
     */
    private Set<String> findThreeDsOperationIds(List<String> operationIds, String merchantId) {
        if (operationIds == null || operationIds.isEmpty() || !StringUtils.hasText(merchantId)) {
            return Set.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("operationIds", operationIds)
                .addValue("merchantId", merchantId)
                .addValue("registeredNodeBegin", registeredNodeBegin)
                .addValue("registeredNodeEnd", exclusiveEnd(LocalDateTime.now()));
        Set<String> result = new HashSet<>(jdbcTemplate.queryForList("""
                SELECT DISTINCT p.operation_id
                FROM transaction_payment_method_info p
                WHERE p.operation_id IN (:operationIds)
                  AND p.transaction_date_time >= :registeredNodeBegin
                  AND p.transaction_date_time < :registeredNodeEnd
                  AND NULLIF(TRIM(p.three_ds_indicator), '') IS NOT NULL
                  AND EXISTS (
                      SELECT 1
                      FROM transaction_operation o
                      WHERE o.operation_id = p.operation_id
                        AND o.transaction_date_time = p.transaction_date_time
                        AND o.merchant_id = :merchantId
                        AND o.deleted = 0
                  )
                """, params, String.class));
        if (result.size() < operationIds.size()) {
            result.addAll(jdbcTemplate.queryForList("""
                    SELECT DISTINCT operation_id
                    FROM payment_checkout_attempt
                    WHERE merchant_id = :merchantId
                      AND operation_id IN (:operationIds)
                      AND three_ds_required = 1
                      AND deleted = 0
                    """, new MapSqlParameterSource()
                    .addValue("merchantId", merchantId)
                    .addValue("operationIds", operationIds), String.class));
        }
        return result;
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
        Map<MerchantTimeScope, List<String>> idsByScope = new LinkedHashMap<>();
        for (TransactionOperationResponse row : rows) {
            if (row.getTransactionDateTime() != null
                    && StringUtils.hasText(row.getMerchantId())
                    && StringUtils.hasText(row.getTransactionId())) {
                MerchantTimeScope scope = new MerchantTimeScope(row.getMerchantId(), row.getTransactionDateTime());
                idsByScope.computeIfAbsent(scope, key -> new ArrayList<>()).add(row.getTransactionId());
            }
        }
        idsByScope.forEach((scope, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            jdbcTemplate.query("""
                    SELECT p.transaction_id, p.operation_id, p.payment_method, p.payment_brand,
                           p.card_bin, p.card_last4, p.card_number_masked
                    FROM %s p
                    WHERE p.transaction_id IN (:transactionIds)
                      AND p.transaction_date_time = :transactionDateTime
                      AND EXISTS (
                          SELECT 1
                          FROM %s o
                          WHERE o.transaction_id = p.transaction_id
                            AND o.transaction_date_time = p.transaction_date_time
                            AND o.merchant_id = :merchantId
                            AND o.deleted = 0
                      )
                    """.formatted(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, TRANSACTION_OPERATION_TABLE),
                    new MapSqlParameterSource()
                    .addValue("transactionIds", ids)
                    .addValue("transactionDateTime", scope.transactionDateTime())
                    .addValue("merchantId", scope.merchantId()), paymentInfoMapper())
                    .forEach(row -> result.putIfAbsent(row.transactionId(), row));
        });
        return result;
    }

    private Map<String, PaymentInfoRow> paymentInfoByOrderTransaction(List<TransactionOrderResponse> rows) {
        Map<String, PaymentInfoRow> result = new LinkedHashMap<>();
        Map<MerchantTimeScope, List<String>> idsByScope = new LinkedHashMap<>();
        for (TransactionOrderResponse row : rows) {
            if (row.getTransactionDateTime() == null || !StringUtils.hasText(row.getMerchantId())) {
                continue;
            }
            MerchantTimeScope scope = new MerchantTimeScope(row.getMerchantId(), row.getTransactionDateTime());
            List<String> transactionIds = idsByScope.computeIfAbsent(scope, key -> new ArrayList<>());
            addIfText(transactionIds, row.getLatestTransactionId());
            addIfText(transactionIds, row.getRootTransactionId());
        }
        idsByScope.forEach((scope, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            jdbcTemplate.query("""
                    SELECT p.transaction_id, p.operation_id, p.payment_method, p.payment_brand,
                           p.card_bin, p.card_last4, p.card_number_masked
                    FROM %s p
                    WHERE p.transaction_date_time = :transactionDateTime
                      AND p.transaction_id IN (:transactionIds)
                      AND EXISTS (
                          SELECT 1
                          FROM %s o
                          WHERE o.transaction_id = p.transaction_id
                            AND o.transaction_date_time = p.transaction_date_time
                            AND o.merchant_id = :merchantId
                            AND o.deleted = 0
                      )
                    """.formatted(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, TRANSACTION_OPERATION_TABLE),
                    new MapSqlParameterSource()
                    .addValue("transactionDateTime", scope.transactionDateTime())
                    .addValue("transactionIds", ids)
                    .addValue("merchantId", scope.merchantId()), paymentInfoMapper())
                    .forEach(row -> result.putIfAbsent(row.transactionId(), row));
        });
        return result;
    }

    private Map<String, PaymentInfoRow> paymentInfoByOrderOperation(List<TransactionOrderResponse> rows) {
        Map<String, PaymentInfoRow> result = new LinkedHashMap<>();
        Map<MerchantTimeScope, List<String>> operationIdsByScope = new LinkedHashMap<>();
        for (TransactionOrderResponse row : rows) {
            if (row.getTransactionDateTime() != null
                    && StringUtils.hasText(row.getMerchantId())
                    && StringUtils.hasText(row.getOperationId())) {
                MerchantTimeScope scope = new MerchantTimeScope(row.getMerchantId(), row.getTransactionDateTime());
                operationIdsByScope.computeIfAbsent(scope, key -> new ArrayList<>()).add(row.getOperationId());
            }
        }
        operationIdsByScope.forEach((scope, operationIds) -> {
            if (operationIds.isEmpty()) {
                return;
            }
            jdbcTemplate.query("""
                    SELECT p.transaction_id, p.operation_id, p.payment_method, p.payment_brand,
                           p.card_bin, p.card_last4, p.card_number_masked
                    FROM %s p
                    WHERE p.operation_id IN (:operationIds)
                      AND p.transaction_date_time = :transactionDateTime
                      AND EXISTS (
                          SELECT 1
                          FROM %s o
                          WHERE o.operation_id = p.operation_id
                            AND o.transaction_date_time = p.transaction_date_time
                            AND o.merchant_id = :merchantId
                            AND o.deleted = 0
                      )
                    ORDER BY p.transaction_date_time ASC, p.id ASC
                    """.formatted(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, TRANSACTION_OPERATION_TABLE),
                    new MapSqlParameterSource()
                    .addValue("operationIds", operationIds)
                    .addValue("transactionDateTime", scope.transactionDateTime())
                    .addValue("merchantId", scope.merchantId()), paymentInfoMapper())
                    .forEach(row -> result.putIfAbsent(row.operationId(), row));
        });
        return result;
    }

    private Map<String, OperationVisibleInfoRow> operationVisibleInfoByOperation(List<TransactionOrderResponse> rows) {
        Map<String, OperationVisibleInfoRow> result = new LinkedHashMap<>();
        Map<MerchantTimeScope, List<String>> operationIdsByScope = new LinkedHashMap<>();
        for (TransactionOrderResponse row : rows) {
            if (row.getTransactionDateTime() != null
                    && StringUtils.hasText(row.getMerchantId())
                    && StringUtils.hasText(row.getOperationId())) {
                MerchantTimeScope scope = new MerchantTimeScope(row.getMerchantId(), row.getTransactionDateTime());
                operationIdsByScope.computeIfAbsent(scope, key -> new ArrayList<>()).add(row.getOperationId());
            }
        }
        operationIdsByScope.forEach((scope, operationIds) -> {
            if (operationIds.isEmpty()) {
                return;
            }
            jdbcTemplate.query("""
                    SELECT o.operation_id,
                           MAX(NULLIF(o.auth_code, '')) AS auth_code,
                           MAX(NULLIF(p.card_bin, '')) AS card_bin,
                           MAX(NULLIF(p.card_last4, '')) AS card_last4,
                           MAX(NULLIF(p.card_number_masked, '')) AS card_number_masked
                    FROM %s o
                    LEFT JOIN %s p ON p.operation_id = o.operation_id
                                      AND p.transaction_date_time = o.transaction_date_time
                    WHERE o.operation_id IN (:operationIds)
                      AND o.transaction_date_time = :transactionDateTime
                      AND o.merchant_id = :merchantId
                      AND o.deleted = 0
                    GROUP BY o.operation_id
                    """.formatted(TRANSACTION_OPERATION_TABLE, TRANSACTION_PAYMENT_METHOD_INFO_TABLE),
                    new MapSqlParameterSource()
                    .addValue("operationIds", operationIds)
                    .addValue("transactionDateTime", scope.transactionDateTime())
                    .addValue("merchantId", scope.merchantId()), operationVisibleInfoMapper())
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
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        String merchantId = rows.stream()
                .map(TransactionOperationResponse::getMerchantId)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant context missing"));
        List<String> operationIds = rows.stream()
                .map(TransactionOperationResponse::getOperationId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (operationIds.isEmpty()) {
            return Map.of();
        }
        List<TransactionOrderResponse> orders = jdbcTemplate.query("""
                SELECT %s
                FROM transaction_order
                WHERE merchant_id = :merchantId
                  AND operation_id IN (:operationIds)
                  AND transaction_date_time >= :registeredNodeBegin
                  AND transaction_date_time < :registeredNodeEnd
                  AND deleted = 0
                """.formatted(ORDER_QUERY_COLUMNS), new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("operationIds", operationIds)
                .addValue("registeredNodeBegin", registeredNodeBegin)
                .addValue("registeredNodeEnd", exclusiveEnd(LocalDateTime.now())), orderMapper());
        return orders.stream().collect(java.util.stream.Collectors.toMap(
                TransactionOrderResponse::getOperationId,
                java.util.function.Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private TransactionPageQuery normalize(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = query == null ? new TransactionPageQuery() : query;
        if (!StringUtils.hasText(safeQuery.getMerchantId())) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant context missing");
        }
        fillDefaultTimeRange(safeQuery);
        normalizeMerchantResponseCode(safeQuery);
        safeQuery.setPageSize((int) Math.min(safeQuery.safePageSize(), maxResultRows));
        return safeQuery;
    }

    private void fillDefaultTimeRange(TransactionPageQuery query) {
        ZoneId queryZone = resolveQueryZone(query.getQueryTimeZone());
        LocalDateTime safeEnd = query.getEndTime() == null ? LocalDateTime.now(queryZone) : query.getEndTime();
        LocalDateTime safeBegin = query.getBeginTime() == null ? safeEnd.toLocalDate().atStartOfDay() : query.getBeginTime();
        if (safeBegin.isAfter(safeEnd)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "beginTime must not be after endTime");
        }
        ZoneId storageZone = ZoneId.of(DEFAULT_QUERY_TIME_ZONE);
        LocalDateTime storageBegin = convertBetweenZones(safeBegin, queryZone, storageZone);
        LocalDateTime storageEnd = convertBetweenZones(safeEnd, queryZone, storageZone);
        LocalDateTime currentStorageTime = LocalDateTime.now(storageZone);
        query.setBeginTime(storageBegin.isBefore(registeredNodeBegin) ? registeredNodeBegin : storageBegin);
        query.setEndTime(storageEnd.isAfter(currentStorageTime) ? currentStorageTime : storageEnd);
        query.setQueryTimeZone(queryZone.getId());
    }

    private boolean isEmptyTimeRange(TransactionPageQuery query) {
        return query.getBeginTime() != null && query.getEndTime() != null
                && query.getBeginTime().isAfter(query.getEndTime());
    }

    private <T> PageResult<T> emptyPage(PageRequest query) {
        return PageResult.of(0L, query.safePageNo(), query.safePageSize(), List.of());
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

    /**
     * 在 transaction 逻辑数据源执行普通读或主库强一致读。
     */
    private <T> T executeRead(boolean primaryOnly, Supplier<T> query) {
        return primaryOnly
                ? transactionLogicalReadExecutor.readPrimary(query)
                : transactionLogicalReadExecutor.read(query);
    }

    /** 将包含式结束时间转换为 MySQL DATETIME(3) 半开区间上界。 */
    private LocalDateTime exclusiveEnd(LocalDateTime endTime) {
        LocalDateTime actualEnd = endTime == null ? LocalDateTime.now() : endTime;
        return actualEnd.plusNanos(1_000_000L);
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
            row.setCurrentAmount(resolveCurrentAmount(
                    row.getTransactionType(),
                    row.getTransactionAmount(),
                    rs.getBigDecimal("authorized_amount")));
            row.setCurrencyExponent(nullableInt(rs, "currency_exponent"));
            row.setTransactionRate(defaultRate(rs.getBigDecimal("transaction_rate")));
            row.setThreeDsEnabled(0);
            row.setDccEnabled(nullableInt(rs, "dcc_enabled"));
            row.setEdcEnabled(nullableInt(rs, "edc_enabled"));
            row.setMerchantResponseCode(resolveMerchantResponseCode(row.getTransactionStatus()));
            row.setMerchantResponseMessage(resolveMerchantResponseMessage(
                    row.getTransactionStatus(),
                    rs.getString("merchant_visible_message")));
            row.setAuthorizedAmount(rs.getBigDecimal("authorized_amount"));
            row.setCapturedAmount(rs.getBigDecimal("captured_amount"));
            row.setRefundedAmount(rs.getBigDecimal("refunded_amount"));
            row.setAvailableCaptureAmount(rs.getBigDecimal("available_capture_amount"));
            row.setAvailableRefundAmount(rs.getBigDecimal("available_refund_amount"));
            row.setSettlementCurrency(rs.getString("settlement_currency"));
            row.setSettlementAmount(rs.getBigDecimal("settlement_amount"));
            row.setSettlementRate(rs.getBigDecimal("settlement_rate"));
            row.setSettlementDate(rs.getObject("settlement_date", java.time.LocalDate.class));
            row.setSettlementBatchNo(rs.getString("settlement_batch_no"));
            row.setSettlementTransactionId(rs.getString("settlement_transaction_id"));
            row.setSettlementTransactionDateTime(localDateTime(rs, "settlement_transaction_date_time"));
            row.setSettlementStatus(rs.getString("settlement_status"));
            row.setReconciliationStatus(rs.getString("reconciliation_status"));
            row.setAccountingStatus(rs.getString("accounting_status"));
            row.setChannelCode(rs.getString("channel_code"));
            row.setChannelOrderNo(rs.getString("channel_order_no"));
            row.setTransactionDateTime(localDateTime(rs, "transaction_date_time"));
            row.setRootTransactionDateTime(row.getTransactionDateTime());
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
            row.setThreeDsEnabled(0);
            row.setDccEnabled(nullableInt(rs, "dcc_enabled"));
            row.setEdcEnabled(nullableInt(rs, "edc_enabled"));
            row.setMerchantResponseCode(resolveMerchantResponseCode(row.getTransactionStatus()));
            row.setMerchantResponseMessage(resolveMerchantResponseMessage(row.getTransactionStatus()));
            row.setChannelCode(rs.getString("channel_code"));
            row.setChannelOrderNo(rs.getString("channel_order_no"));
            row.setChannelTransactionId(rs.getString("channel_transaction_id"));
            row.setAuthCode(rs.getString("auth_code"));
            row.setAcquirerReferenceNo(rs.getString("acquirer_reference_no"));
            row.setSettlementCurrency(rs.getString("settlement_currency"));
            row.setSettlementAmount(rs.getBigDecimal("settlement_amount"));
            row.setSettlementRate(rs.getBigDecimal("settlement_rate"));
            row.setSettlementDate(rs.getObject("settlement_date", java.time.LocalDate.class));
            row.setSettlementBatchNo(rs.getString("settlement_batch_no"));
            row.setSettlementStatus(rs.getString("settlement_status"));
            row.setReconciliationStatus(rs.getString("reconciliation_status"));
            row.setAccountingStatus(rs.getString("accounting_status"));
            row.setTransactionDateTime(localDateTime(rs, "transaction_date_time"));
            row.setOperationTime(localDateTime(rs, "operation_time"));
            row.setAccessType(resolveAccessType(getStringIfExists(rs, "request_source")));
            return row;
        };
    }

    /** 将支付核心持久化的请求来源映射为商户端接入类型。 */
    private String resolveAccessType(String requestSource) {
        return ACCESS_TYPE_HOSTED_CHECKOUT.equalsIgnoreCase(requestSource)
                ? ACCESS_TYPE_HOSTED_CHECKOUT
                : ACCESS_TYPE_DIRECT_API;
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

    /** 兼容历史结果集缺少新增来源字段的场景。 */
    private String getStringIfExists(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException exception) {
            return null;
        }
    }

    /**
     * 解析商户页面应展示的当前交易金额；授权类优先展示有效授权累计金额。
     *
     * @param transactionType 平台交易类型编码
     * @param transactionAmount 动作交易金额，单位由交易币种确定
     * @param authorizedAmount 主单授权累计金额，单位与交易币种一致
     * @return 当前可展示金额；授权累计金额不可用时回退到动作交易金额
     */
    static BigDecimal resolveCurrentAmount(String transactionType,
                                           BigDecimal transactionAmount,
                                           BigDecimal authorizedAmount) {
        if (isAuthorizationLike(transactionType)
                && authorizedAmount != null
                && authorizedAmount.signum() > 0) {
            return authorizedAmount;
        }
        return transactionAmount;
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

    private static boolean isAuthorizationLike(String transactionType) {
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
        return resolveMerchantResponseMessage(transactionStatus, null);
    }

    static String resolveMerchantResponseMessage(String transactionStatus, String persistedMessage) {
        if ("FAILED".equals(transactionStatus)
                && StringUtils.hasText(persistedMessage)
                && !isInternalFailureToken(persistedMessage)) {
            return persistedMessage.trim();
        }
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

    private static boolean isInternalFailureToken(String message) {
        return message != null && message.trim().matches("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+");
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

    /** 将最早已登记季度转换为 ShardingSphere 可路由的半开范围起点。 */
    private static LocalDateTime resolveRegisteredNodeBegin(List<String> physicalNodes) {
        return physicalNodes == null ? LocalDateTime.of(1970, 1, 1, 0, 0)
                : physicalNodes.stream()
                .filter(node -> node != null && node.matches("\\d{4}0[1-4]"))
                .min(String::compareTo)
                .map(node -> LocalDateTime.of(
                        Integer.parseInt(node.substring(0, 4)),
                        (Character.digit(node.charAt(5), 10) - 1) * 3 + 1,
                        1, 0, 0))
                .orElse(LocalDateTime.of(1970, 1, 1, 0, 0));
    }

    /** 商户归属和真实分片时间共同限定富化查询范围。 */
    private record MerchantTimeScope(String merchantId, LocalDateTime transactionDateTime) {
    }

    private record SummaryRow(String transactionStatus, String paymentMethod, String paymentBrand, String currency,
                              Integer currencyExponent, long count, BigDecimal amount) {
    }

    private static class SummaryAccumulator {
        /**
         * 合计计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private long totalCount;
        /**
         * 成功计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private long successCount;
        /**
         * 失败计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private long failedCount;
        /**
         * 按币种拆分的金额汇总集合，禁止直接跨币种相加。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private final Map<String, TransactionAmountSummaryResponse> amountSummaries = new LinkedHashMap<>();
        /**
         * 按币种拆分的成功金额汇总集合，禁止直接跨币种相加。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private final Map<String, TransactionAmountSummaryResponse> successAmountSummaries = new LinkedHashMap<>();
        /**
         * 按币种拆分的失败金额汇总集合，禁止直接跨币种相加。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private final Map<String, TransactionAmountSummaryResponse> failedAmountSummaries = new LinkedHashMap<>();
        /**
         * {@code paymentMethodSummaries}，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
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
