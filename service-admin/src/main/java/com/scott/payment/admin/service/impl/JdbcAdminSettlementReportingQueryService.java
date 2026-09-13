package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReconciliationRecord;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.TransactionSettlementSummary;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminSettlementReportingQueryService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminSettlementReportingQueryService
 * @date : 2026-09-01 23:15
 * @email : scott_x@163.com
 * @description : 在 transaction 逻辑读数据源内查询不可变结算结果、保证金动作和资金流水；强制叠加 Admin 商户数据范围及查询预算。
 * @status : update
 */
@Service
public class JdbcAdminSettlementReportingQueryService implements AdminSettlementReportingQueryService {

    /**
     * 默认页大小，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int DEFAULT_PAGE_SIZE = 10;
    /**
     * {@code MAX_DATE_SPAN_DAYS}常量，统一 {@code JdbcAdminSettlementReportingQueryService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long MAX_DATE_SPAN_DAYS = 92;
    private static final Set<String> RESULT_ITEM_TYPES = Set.of(
            "PRINCIPAL", "FEE_COMPONENT", "FEE_GROUP_FINAL", "RESERVE_HOLD",
            "RESERVE_RETURN", "RESERVE_RELEASE", "ADJUSTMENT", "REVERSAL", "NET_SETTLEMENT");
    private static final Set<String> RESULT_ROLES = Set.of("TRACE", "FINANCIAL_COMPONENT", "LEDGER_POSTING");
    private static final Set<String> DIRECTIONS = Set.of("CREDIT", "DEBIT");
    private static final Set<String> OPERATION_MODES = Set.of("AUTO", "MANUAL");
    private static final Set<String> SOURCE_DETAIL_TYPES = Set.of("TRANSACTION_CLEARING", "RESERVE_CLEARING");
    private static final Set<String> RESERVE_ACTION_TYPES = Set.of(
            "HOLD", "RETURN", "RELEASE", "ADJUSTMENT",
            "REVERSAL_HOLD", "REVERSAL_RETURN", "REVERSAL_RELEASE", "REVERSAL_ADJUSTMENT");
    private static final Set<String> RESERVE_STATUSES = Set.of(
            "HELD", "PARTIALLY_RETURNED", "RELEASABLE", "FROZEN",
            "RETURNED", "RELEASED", "ADJUSTED", "REVERSED");
    private static final String RESULT_COLUMNS = """
            ri.id, ri.settlement_result_item_no, ri.settlement_batch_no, ri.candidate_id,
            ri.result_line_no, ri.merchant_id, ri.settlement_account_id, ri.source_detail_type,
            ri.source_detail_no, ri.reversal_of_result_item_id, ri.source_transaction_id,
            ri.source_transaction_date_time, ri.fee_group_no, ri.result_item_type, ri.result_role,
            ri.payment_type, ri.payment_method, ri.transaction_type, ri.fee_category, ri.direction,
            ri.source_amount, ri.source_currency, ri.source_currency_exponent,
            ri.settlement_batch_rate_id, rate.direct_rate, ri.unrounded_target_amount,
            ri.target_amount, ri.target_currency, ri.target_currency_exponent, ri.applied_limit,
            ri.minimum_target_amount, ri.maximum_target_amount, ri.rounding_mode,
            ri.formula_snapshot, ri.ledger_idempotency_key, batch.business_date, ri.create_time
            """;
    private static final String TRANSACTION_SETTLEMENT_COLUMNS = """
            batch.settlement_batch_no, batch.business_date, batch.batch_status,
            candidate.id AS candidate_id, candidate.candidate_no, candidate.merchant_id,
            locator.merchant_order_no, candidate.source_transaction_id,
            COALESCE(locator.transaction_date_time, candidate.source_transaction_date_time)
                AS source_transaction_date_time,
            MAX(item.payment_type) AS payment_type,
            MAX(item.payment_method) AS payment_method,
            COALESCE(MAX(item.transaction_type), locator.transaction_type) AS transaction_type,
            COALESCE(
                MAX(CASE WHEN item.result_item_type = 'PRINCIPAL' THEN item.source_amount END),
                MAX(item.source_amount)
            ) AS source_amount,
            COALESCE(
                MAX(CASE WHEN item.result_item_type = 'PRINCIPAL' THEN item.source_currency END),
                MAX(item.source_currency)
            ) AS source_currency,
            COALESCE(
                MAX(CASE WHEN item.result_item_type = 'PRINCIPAL' THEN item.source_currency_exponent END),
                MAX(item.source_currency_exponent)
            ) AS source_currency_exponent,
            COUNT(item.id) AS component_count,
            CASE WHEN SUM(CASE WHEN item.direction = 'CREDIT'
                               THEN item.target_amount ELSE -item.target_amount END) < 0
                 THEN 'DEBIT' ELSE 'CREDIT' END AS net_direction,
            ABS(SUM(CASE WHEN item.direction = 'CREDIT'
                         THEN item.target_amount ELSE -item.target_amount END)) AS net_target_amount,
            MAX(item.target_currency) AS target_currency,
            MAX(item.target_currency_exponent) AS target_currency_exponent,
            batch.posted_time, MAX(item.create_time) AS create_time
            """;
    private static final String POSTING_COLUMNS = """
            ledger.id, ledger.ledger_no, ledger.ledger_group_no, ledger.account_id,
            ledger.merchant_id, ledger.business_type, ledger.summary, ledger.business_no,
            ledger.settlement_batch_no, ledger.currency,
            COALESCE(NULLIF(currency.fraction_digits, -1), 2) AS currency_exponent,
            ledger.direction, ledger.amount,
            ledger.balance_before, ledger.balance_after, ledger.account_sequence,
            ledger.operation_mode, ledger.operator_id, ledger.operator_name, ledger.reviewer_id,
            ledger.reviewer_name, ledger.operation_reason, ledger.review_comment,
            ledger.business_time, ledger.submit_time, ledger.review_time, ledger.posted_time,
            ledger.request_id, ledger.idempotency_key, ledger.reversal_of_ledger_id,
            ledger.create_time
            """;
    private static final String RECONCILIATION_COLUMNS = """
            operation.transaction_id, operation.merchant_id, operation.merchant_order_no,
            operation.transaction_type, operation.reconciliation_status,
            operation.settlement_status, operation.accounting_status,
            operation.transaction_date_time, operation.operation_time
            """;
    private static final String RESERVE_COLUMN_PREFIX = """
            action.id AS action_id, action.reserve_action_no, action.reserve_item_id,
            action.reserve_no, action.settlement_batch_no, batch.business_date,
            reserve.merchant_id, reserve.account_id, fund_account.account_no AS account_no,
            """;
    private static final String EFFECTIVE_RESERVE_STATUS_SQL = """
            CASE
                WHEN reserve.reserve_status = 'HELD'
                 AND reserve.release_batch_no IS NOT NULL
                 AND reserve.released_amount > 0
                 AND reserve.retained_amount + reserve.debit_adjustment_amount
                     - reserve.returned_amount - reserve.released_amount
                     - reserve.credit_adjustment_amount - reserve.reversed_amount = 0
                THEN 'RELEASED'
                ELSE reserve.reserve_status
            END
            """;
    private static final String RESERVE_COLUMN_SUFFIX = """
            reserve.source_business_no,
            action.source_reserve_detail_no, action.action_type,
            action.direction, action.currency,
            COALESCE(NULLIF(currency.fraction_digits, -1), 2) AS currency_exponent,
            action.amount, reserve.retained_amount,
            reserve.returned_amount, reserve.released_amount,
            reserve.debit_adjustment_amount, reserve.credit_adjustment_amount,
            reserve.reversed_amount,
            (reserve.retained_amount + reserve.debit_adjustment_amount
             - reserve.returned_amount - reserve.released_amount
             - reserve.credit_adjustment_amount - reserve.reversed_amount) AS remaining_amount,
            """ + EFFECTIVE_RESERVE_STATUS_SQL + " AS reserve_status,\n" + """
            reserve.expected_release_date, action.action_time
            """;
    private static final String RESERVE_COLUMNS = RESERVE_COLUMN_PREFIX + """
            reserve_detail.original_transaction_id AS source_transaction_id,
            locator.merchant_order_no,
            COALESCE(locator.transaction_date_time, reserve_detail.original_transaction_date_time)
                AS source_transaction_date_time,
            """ + RESERVE_COLUMN_SUFFIX;
    private static final String RESERVE_HISTORY_COLUMNS = RESERVE_COLUMN_PREFIX + """
            reserve_state.original_transaction_id AS source_transaction_id,
            locator.merchant_order_no,
            COALESCE(locator.transaction_date_time, reserve_state.transaction_date_time)
                AS source_transaction_date_time,
            """ + RESERVE_COLUMN_SUFFIX;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionLogicalReadExecutor readExecutor;
    private final int maxResultRows;

    @Autowired
    public JdbcAdminSettlementReportingQueryService(DataSource dataSource,
                                                    TransactionLogicalReadExecutor readExecutor,
                                                    TransactionShardingProperties shardingProperties,
                                                    TransactionQueryJdbcTemplateFactory factory) {
        this(factory.create(dataSource, shardingProperties), readExecutor, shardingProperties);
    }

    public JdbcAdminSettlementReportingQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                                    TransactionLogicalReadExecutor readExecutor,
                                                    TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.readExecutor = readExecutor;
        this.maxResultRows = shardingProperties.getQueryBudget().getMaxResultRows();
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<TransactionSettlementSummary> searchResultItems(ResultItemSearchRequest request,
                                                                      AdminMerchantDataScope dataScope) {
        ResultItemSearchRequest query = normalizeResultQuery(request);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> resultPage(query, scope));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<ResultItemSummary> searchResultItemComponents(String settlementBatchNo,
                                                                    String transactionId,
                                                                    Integer pageNo,
                                                                    Integer pageSize,
                                                                    AdminMerchantDataScope dataScope) {
        TransactionComponentQuery query = normalizeTransactionComponentQuery(
                settlementBatchNo, transactionId, pageNo, pageSize);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> resultComponentPage(query, scope));
    }

    /** {@inheritDoc} */
    @Override
    public List<ReconciliationRecord> findReconciliationRecordsByTransaction(
            String transactionId,
            LocalDateTime transactionDateTime,
            AdminMerchantDataScope dataScope) {
        TransactionIdentityQuery query = normalizeTransactionIdentity(transactionId, transactionDateTime);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> reconciliationRecords(query, scope));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<ResultItemSummary> searchResultItemsByTransaction(String transactionId,
                                                                        LocalDateTime transactionDateTime,
                                                                        Integer pageNo,
                                                                        Integer pageSize,
                                                                        AdminMerchantDataScope dataScope) {
        TransactionHistoryQuery query = normalizeTransactionHistoryQuery(
                transactionId, transactionDateTime, pageNo, pageSize);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> resultHistoryPage(query, scope));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<PostingSummary> searchPostings(PostingSearchRequest request,
                                                     AdminMerchantDataScope dataScope) {
        PostingSearchRequest query = normalizePostingQuery(request);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> postingPage(query, scope));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<ReserveItemSummary> searchReserveItems(ReserveItemSearchRequest request,
                                                             AdminMerchantDataScope dataScope) {
        ReserveItemSearchRequest query = normalizeReserveQuery(request);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> reservePage(query, scope));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<ReserveItemSummary> searchReserveItemsByTransaction(String transactionId,
                                                                          LocalDateTime transactionDateTime,
                                                                          Integer pageNo,
                                                                          Integer pageSize,
                                                                          AdminMerchantDataScope dataScope) {
        TransactionHistoryQuery query = normalizeTransactionHistoryQuery(
                transactionId, transactionDateTime, pageNo, pageSize);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> reserveHistoryPage(query, scope));
    }

    /**
     * 查询结算结果并关联同批次锁定直接汇率；不重新计算金额、币种、限额或舍入结果。
     */
    private PageResult<TransactionSettlementSummary> resultPage(ResultItemSearchRequest query,
                                                                AdminMerchantDataScope scope) {
        if (scope.empty()) return emptyPage(query.getPageNo(), query.getPageSize());
        StringBuilder where = new StringBuilder("""
                WHERE batch.business_date BETWEEN :beginDate AND :endDate
                  AND candidate.source_type = 'CLEARING_REVISION'
                  AND candidate.source_transaction_id IS NOT NULL
                  AND item.source_detail_type = 'TRANSACTION_CLEARING'
                  AND item.result_role = 'FINANCIAL_COMPONENT'
                """);
        if (query.getSettlementBatchNo() != null) where.append(" AND candidate.settlement_batch_no = :batchNo\n");
        if (query.getMerchantId() != null) where.append(" AND candidate.merchant_id = :merchantId\n");
        if (query.getSourceTransactionId() != null) where.append(" AND candidate.source_transaction_id = :transactionId\n");
        if (query.getMerchantOrderNo() != null) where.append(" AND locator.merchant_order_no = :merchantOrderNo\n");
        if (query.getBeginTransactionTime() != null) {
            where.append(" AND COALESCE(locator.transaction_date_time, candidate.source_transaction_date_time) >= :beginTransactionTime\n");
        }
        if (query.getEndTransactionTime() != null) {
            where.append(" AND COALESCE(locator.transaction_date_time, candidate.source_transaction_date_time) < :endTransactionTime\n");
        }
        appendResultItemExistsFilter(where, query);
        where.append(scopeSql(scope, "candidate.merchant_id"));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("beginDate", query.getBeginBusinessDate()).addValue("endDate", query.getEndBusinessDate())
                .addValue("batchNo", query.getSettlementBatchNo()).addValue("merchantId", query.getMerchantId())
                .addValue("transactionId", query.getSourceTransactionId()).addValue("itemType", query.getResultItemType())
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("beginTransactionTime", query.getBeginTransactionTime())
                .addValue("endTransactionTime", query.getEndTransactionTime())
                .addValue("direction", query.getDirection())
                .addValue("currency", query.getTargetCurrency())
                .addValue("permittedMerchantIds", scope.merchantIds());
        String from = """
                FROM settlement_candidate candidate
                JOIN settlement_batch batch
                  ON batch.settlement_batch_no = candidate.settlement_batch_no
                 AND batch.merchant_id = candidate.merchant_id
                JOIN settlement_result_item item
                  ON item.settlement_batch_no = candidate.settlement_batch_no
                 AND item.candidate_id = candidate.id
                 AND item.merchant_id = candidate.merchant_id
                LEFT JOIN transaction_locator locator
                  ON locator.transaction_id = candidate.source_transaction_id
                 AND locator.merchant_id = candidate.merchant_id
                """;
        String groupBy = """
                GROUP BY batch.settlement_batch_no, batch.business_date, batch.batch_status,
                         batch.posted_time, candidate.id, candidate.candidate_no,
                         candidate.merchant_id, locator.merchant_order_no,
                         candidate.source_transaction_id, locator.transaction_date_time,
                         candidate.source_transaction_date_time, locator.transaction_type
                """;
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT candidate.id) " + from + where,
                parameters, Long.class);
        long total = count == null ? 0L : count;
        long offset = (long) (query.getPageNo() - 1) * query.getPageSize();
        List<TransactionSettlementSummary> rows = offset < total ? jdbcTemplate.query(
                "SELECT " + TRANSACTION_SETTLEMENT_COLUMNS + from + where + groupBy
                        + " ORDER BY source_transaction_date_time DESC, candidate.id DESC LIMIT :offset, :limit",
                new MapSqlParameterSource(parameters.getValues())
                        .addValue("offset", offset).addValue("limit", query.getPageSize()),
                BeanPropertyRowMapper.newInstance(TransactionSettlementSummary.class)) : List.of();
        return PageResult.of(total, query.getPageNo(), query.getPageSize(), rows);
    }

    /** 正式批次内交易组件使用独立分页，列表汇总不再重复展示同一交易。 */
    private PageResult<ResultItemSummary> resultComponentPage(TransactionComponentQuery query,
                                                              AdminMerchantDataScope scope) {
        if (scope.empty()) return emptyPage(query.pageNo(), query.pageSize());
        String where = """
                WHERE ri.settlement_batch_no = :batchNo
                  AND ri.source_transaction_id = :transactionId
                  AND ri.source_detail_type = 'TRANSACTION_CLEARING'
                  AND ri.result_role = 'FINANCIAL_COMPONENT'
                """ + scopeSql(scope, "ri.merchant_id");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("batchNo", query.settlementBatchNo())
                .addValue("transactionId", query.transactionId())
                .addValue("permittedMerchantIds", scope.merchantIds());
        String from = """
                FROM settlement_result_item ri
                JOIN settlement_batch batch ON batch.settlement_batch_no = ri.settlement_batch_no
                JOIN settlement_batch_rate rate ON rate.id = ri.settlement_batch_rate_id
                """;
        return page(RESULT_COLUMNS, from, where, parameters,
                "ri.result_line_no ASC, ri.id ASC", query.pageNo(), query.pageSize(),
                ResultItemSummary.class);
    }

    /** 按候选真实交易身份读取结算结果，不依赖结算业务日期窗口。 */
    private PageResult<ResultItemSummary> resultHistoryPage(TransactionHistoryQuery query,
                                                            AdminMerchantDataScope scope) {
        if (scope.empty()) return emptyPage(query.pageNo(), query.pageSize());
        String where = """
                WHERE candidate.source_type = 'CLEARING_REVISION'
                  AND candidate.source_transaction_id = :transactionId
                  AND candidate.source_transaction_date_time = :transactionDateTime
                """ + scopeSql(scope, "candidate.merchant_id");
        MapSqlParameterSource parameters = transactionHistoryParameters(query, scope);
        String from = """
                FROM settlement_candidate candidate
                JOIN settlement_result_item ri
                  ON ri.settlement_batch_no = candidate.settlement_batch_no
                 AND ri.candidate_id = candidate.id
                 AND ri.merchant_id = candidate.merchant_id
                 AND ri.source_detail_type = 'TRANSACTION_CLEARING'
                JOIN settlement_batch batch ON batch.settlement_batch_no = ri.settlement_batch_no
                JOIN settlement_batch_rate rate ON rate.id = ri.settlement_batch_rate_id
                """;
        return page(RESULT_COLUMNS, from, where, parameters,
                "ri.create_time DESC, ri.id DESC", query.pageNo(), query.pageSize(),
                ResultItemSummary.class);
    }

    /** 按交易精确身份读取动作状态，权限范围不足与记录不存在都返回空集合。 */
    private List<ReconciliationRecord> reconciliationRecords(TransactionIdentityQuery query,
                                                              AdminMerchantDataScope scope) {
        if (scope.empty()) return List.of();
        String where = """
                WHERE operation.transaction_id = :transactionId
                  AND operation.transaction_date_time = :transactionDateTime
                  AND operation.deleted = 0
                """ + scopeSql(scope, "operation.merchant_id");
        return jdbcTemplate.query(
                "SELECT " + RECONCILIATION_COLUMNS + " FROM transaction_operation operation "
                        + where + " ORDER BY operation.id DESC LIMIT 1",
                transactionIdentityParameters(query, scope),
                BeanPropertyRowMapper.newInstance(ReconciliationRecord.class));
    }

    /**
     * 查询具有结算批次号的资金流水，保留余额前后值、账户序列、人工审计和冲正关联。
     */
    private PageResult<PostingSummary> postingPage(PostingSearchRequest query,
                                                   AdminMerchantDataScope scope) {
        if (scope.empty()) return emptyPage(query.getPageNo(), query.getPageSize());
        StringBuilder where = new StringBuilder("""
                WHERE ledger.settlement_batch_no IS NOT NULL
                  AND ledger.posted_time BETWEEN :beginTime AND :endTime
                """);
        if (query.getSettlementBatchNo() != null) where.append(" AND ledger.settlement_batch_no = :batchNo\n");
        if (query.getMerchantId() != null) where.append(" AND ledger.merchant_id = :merchantId\n");
        if (query.getLedgerNo() != null) where.append(" AND ledger.ledger_no = :ledgerNo\n");
        if (query.getDirection() != null) where.append(" AND ledger.direction = :direction\n");
        if (query.getOperationMode() != null) where.append(" AND ledger.operation_mode = :operationMode\n");
        if (query.getCurrency() != null) where.append(" AND ledger.currency = :currency\n");
        where.append(scopeSql(scope, "ledger.merchant_id"));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("beginTime", query.getBeginPostedTime()).addValue("endTime", query.getEndPostedTime())
                .addValue("batchNo", query.getSettlementBatchNo()).addValue("merchantId", query.getMerchantId())
                .addValue("ledgerNo", query.getLedgerNo()).addValue("direction", query.getDirection())
                .addValue("operationMode", query.getOperationMode()).addValue("currency", query.getCurrency())
                .addValue("permittedMerchantIds", scope.merchantIds());
        String from = """
                FROM merchant_fund_ledger ledger
                LEFT JOIN base_iso_currency currency
                  ON currency.alpha3_code = ledger.currency AND currency.deleted = 0
                """;
        return page(POSTING_COLUMNS, from, where.toString(), parameters,
                "ledger.posted_time DESC, ledger.id DESC", query.getPageNo(), query.getPageSize(),
                PostingSummary.class);
    }

    /**
     * 查询原标签币种保证金动作，并按扣留 + 借方调整 - 返还 - 释放 - 贷方调整 - 冲正计算当前责任。
     */
    private PageResult<ReserveItemSummary> reservePage(ReserveItemSearchRequest query,
                                                       AdminMerchantDataScope scope) {
        if (scope.empty()) return emptyPage(query.getPageNo(), query.getPageSize());
        StringBuilder where = new StringBuilder("""
                WHERE batch.business_date BETWEEN :beginDate AND :endDate
                """);
        if (query.getSettlementBatchNo() != null) where.append(" AND action.settlement_batch_no = :batchNo\n");
        if (query.getMerchantId() != null) where.append(" AND reserve.merchant_id = :merchantId\n");
        if (query.getReserveNo() != null) where.append(" AND action.reserve_no = :reserveNo\n");
        if (query.getReserveActionNo() != null) where.append(" AND action.reserve_action_no = :reserveActionNo\n");
        if (query.getSourceTransactionId() != null) {
            where.append(" AND reserve_detail.original_transaction_id = :transactionId\n");
        }
        if (query.getMerchantOrderNo() != null) where.append(" AND locator.merchant_order_no = :merchantOrderNo\n");
        if (query.getReserveStatus() != null) {
            where.append(" AND (").append(EFFECTIVE_RESERVE_STATUS_SQL).append(") = :reserveStatus\n");
        }
        if (query.getActionType() != null) where.append(" AND action.action_type = :actionType\n");
        if (query.getCurrency() != null) where.append(" AND action.currency = :currency\n");
        if (query.getBeginTransactionTime() != null) {
            where.append(" AND COALESCE(locator.transaction_date_time, reserve_detail.original_transaction_date_time) >= :beginTransactionTime\n")
                    .append(" AND COALESCE(locator.transaction_date_time, reserve_detail.original_transaction_date_time) < :endTransactionTime\n");
        }
        if (query.getBeginExpectedReleaseDate() != null) {
            where.append(" AND reserve.expected_release_date BETWEEN :beginExpectedReleaseDate AND :endExpectedReleaseDate\n");
        }
        where.append(scopeSql(scope, "reserve.merchant_id"));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("beginDate", query.getBeginBusinessDate()).addValue("endDate", query.getEndBusinessDate())
                .addValue("batchNo", query.getSettlementBatchNo()).addValue("merchantId", query.getMerchantId())
                .addValue("reserveNo", query.getReserveNo()).addValue("reserveActionNo", query.getReserveActionNo())
                .addValue("transactionId", query.getSourceTransactionId())
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("reserveStatus", query.getReserveStatus())
                .addValue("actionType", query.getActionType()).addValue("currency", query.getCurrency())
                .addValue("beginTransactionTime", query.getBeginTransactionTime())
                .addValue("endTransactionTime", query.getEndTransactionTime())
                .addValue("beginExpectedReleaseDate", query.getBeginExpectedReleaseDate())
                .addValue("endExpectedReleaseDate", query.getEndExpectedReleaseDate())
                .addValue("permittedMerchantIds", scope.merchantIds());
        String from = """
                FROM merchant_reserve_action action
                JOIN merchant_reserve_item reserve
                  ON reserve.id = action.reserve_item_id AND reserve.reserve_no = action.reserve_no
                JOIN settlement_candidate candidate
                  ON candidate.id = action.candidate_id AND candidate.merchant_id = reserve.merchant_id
                LEFT JOIN merchant_reserve_action source_action
                  ON source_action.id = action.reversal_of_action_id
                JOIN transaction_reserve_clearing_detail reserve_detail
                  ON reserve_detail.transaction_id = candidate.source_transaction_id
                 AND reserve_detail.transaction_date_time = candidate.source_transaction_date_time
                 AND reserve_detail.clearing_revision = candidate.source_revision
                 AND reserve_detail.reserve_clearing_detail_no = COALESCE(
                         source_action.source_reserve_detail_no,
                         action.source_reserve_detail_no)
                 AND reserve_detail.record_status = 'ACTIVE'
                JOIN settlement_batch batch ON batch.settlement_batch_no = action.settlement_batch_no
                LEFT JOIN merchant_fund_account fund_account
                  ON fund_account.id = reserve.account_id
                 AND fund_account.merchant_id = reserve.merchant_id
                 AND fund_account.deleted = 0
                LEFT JOIN transaction_locator locator
                  ON locator.transaction_id = reserve_detail.original_transaction_id
                 AND locator.merchant_id = reserve.merchant_id
                LEFT JOIN base_iso_currency currency
                  ON currency.alpha3_code = action.currency AND currency.deleted = 0
                """;
        return page(RESERVE_COLUMNS, from, where.toString(), parameters,
                "action.action_time DESC, action.id DESC", query.getPageNo(), query.getPageSize(),
                ReserveItemSummary.class);
    }

    /** 按原支付保证金状态的原交易分片键定位聚合，再读取跨批次完整动作历史。 */
    private PageResult<ReserveItemSummary> reserveHistoryPage(TransactionHistoryQuery query,
                                                              AdminMerchantDataScope scope) {
        if (scope.empty()) return emptyPage(query.pageNo(), query.pageSize());
        String where = """
                WHERE reserve_state.original_transaction_id = :transactionId
                  AND reserve_state.transaction_date_time = :transactionDateTime
                """ + scopeSql(scope, "reserve_state.merchant_id");
        MapSqlParameterSource parameters = transactionHistoryParameters(query, scope);
        String from = """
                FROM transaction_reserve_clearing_state reserve_state
                JOIN merchant_reserve_item reserve
                  ON reserve.source_business_no = reserve_state.original_hold_detail_no
                 AND reserve.source_transaction_id = reserve_state.original_transaction_id
                 AND reserve.merchant_id = reserve_state.merchant_id
                JOIN merchant_reserve_action action
                  ON action.reserve_item_id = reserve.id
                 AND action.reserve_no = reserve.reserve_no
                JOIN settlement_batch batch ON batch.settlement_batch_no = action.settlement_batch_no
                LEFT JOIN merchant_fund_account fund_account
                  ON fund_account.id = reserve.account_id
                 AND fund_account.merchant_id = reserve.merchant_id
                 AND fund_account.deleted = 0
                LEFT JOIN transaction_locator locator
                  ON locator.transaction_id = reserve_state.original_transaction_id
                 AND locator.merchant_id = reserve_state.merchant_id
                LEFT JOIN base_iso_currency currency
                  ON currency.alpha3_code = action.currency AND currency.deleted = 0
                """;
        return page(RESERVE_HISTORY_COLUMNS, from, where, parameters,
                "action.action_time DESC, action.id DESC", query.pageNo(), query.pageSize(),
                ReserveItemSummary.class);
    }

    private <T> PageResult<T> page(String columns,
                                   String from,
                                   String where,
                                   MapSqlParameterSource parameters,
                                   String orderBy,
                                   int pageNo,
                                   int pageSize,
                                   Class<T> type) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) " + from + where, parameters, Long.class);
        long total = count == null ? 0L : count;
        long offset = (long) (pageNo - 1) * pageSize;
        List<T> rows = offset < total ? jdbcTemplate.query(
                "SELECT " + columns + from + where + " ORDER BY " + orderBy + " LIMIT :offset, :limit",
                new MapSqlParameterSource(parameters.getValues()).addValue("offset", offset).addValue("limit", pageSize),
                BeanPropertyRowMapper.newInstance(type)) : List.of();
        return PageResult.of(total, pageNo, pageSize, rows);
    }

    private ResultItemSearchRequest normalizeResultQuery(ResultItemSearchRequest request) {
        if (request == null || !validDateRange(request.getBeginBusinessDate(), request.getEndBusinessDate())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        request.setSettlementBatchNo(trim(request.getSettlementBatchNo()));
        if (request.getSettlementBatchNo() != null && !request.getSettlementBatchNo().matches("SB\\d{8}-\\d{8}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        request.setMerchantId(trimMax(request.getMerchantId(), 64));
        request.setSourceTransactionId(trimMax(request.getSourceTransactionId(), 64));
        request.setMerchantOrderNo(trimMax(request.getMerchantOrderNo(), 128));
        validateOptionalTimeRange(request.getBeginTransactionTime(), request.getEndTransactionTime());
        request.setResultItemType(enumValue(request.getResultItemType(), RESULT_ITEM_TYPES));
        request.setResultRole(enumValue(request.getResultRole(), RESULT_ROLES));
        request.setDirection(enumValue(request.getDirection(), DIRECTIONS));
        request.setTargetCurrency(currency(request.getTargetCurrency()));
        request.setSourceDetailType(enumValue(request.getSourceDetailType(), SOURCE_DETAIL_TYPES));
        normalizePage(request.getPageNo(), request.getPageSize(), request::setPageNo, request::setPageSize);
        return request;
    }

    private TransactionComponentQuery normalizeTransactionComponentQuery(String settlementBatchNo,
                                                                          String transactionId,
                                                                          Integer pageNo,
                                                                          Integer pageSize) {
        String batchNo = trimMax(settlementBatchNo, 32);
        String normalizedTransactionId = trimMax(transactionId, 64);
        if (batchNo == null || !batchNo.matches("SB\\d{8}-\\d{8}") || normalizedTransactionId == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        PageSpec page = normalizePageValues(pageNo, pageSize);
        return new TransactionComponentQuery(batchNo, normalizedTransactionId,
                page.pageNo(), page.pageSize());
    }

    private ReserveItemSearchRequest normalizeReserveQuery(ReserveItemSearchRequest request) {
        if (request == null || !validDateRange(request.getBeginBusinessDate(), request.getEndBusinessDate())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        request.setSettlementBatchNo(trim(request.getSettlementBatchNo()));
        if (request.getSettlementBatchNo() != null
                && !request.getSettlementBatchNo().matches("SB\\d{8}-\\d{8}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        request.setMerchantId(trimMax(request.getMerchantId(), 64));
        request.setReserveNo(trimMax(request.getReserveNo(), 64));
        request.setReserveActionNo(trimMax(request.getReserveActionNo(), 64));
        request.setSourceTransactionId(trimMax(request.getSourceTransactionId(), 64));
        request.setMerchantOrderNo(trimMax(request.getMerchantOrderNo(), 128));
        request.setReserveStatus(enumValue(request.getReserveStatus(), RESERVE_STATUSES));
        request.setActionType(enumValue(request.getActionType(), RESERVE_ACTION_TYPES));
        request.setCurrency(currency(request.getCurrency()));
        validateOptionalTimeRange(request.getBeginTransactionTime(), request.getEndTransactionTime());
        validateOptionalDateRange(request.getBeginExpectedReleaseDate(), request.getEndExpectedReleaseDate());
        normalizePage(request.getPageNo(), request.getPageSize(), request::setPageNo, request::setPageSize);
        return request;
    }

    private void appendResultItemExistsFilter(StringBuilder where, ResultItemSearchRequest query) {
        if (query.getResultItemType() == null && query.getDirection() == null && query.getTargetCurrency() == null) {
            return;
        }
        where.append("""
                 AND EXISTS (
                     SELECT 1
                     FROM settlement_result_item filter_item
                     WHERE filter_item.settlement_batch_no = candidate.settlement_batch_no
                       AND filter_item.candidate_id = candidate.id
                       AND filter_item.merchant_id = candidate.merchant_id
                       AND filter_item.source_detail_type = 'TRANSACTION_CLEARING'
                       AND filter_item.result_role = 'FINANCIAL_COMPONENT'
                """);
        if (query.getResultItemType() != null) where.append(" AND filter_item.result_item_type = :itemType\n");
        if (query.getDirection() != null) where.append(" AND filter_item.direction = :direction\n");
        if (query.getTargetCurrency() != null) where.append(" AND filter_item.target_currency = :currency\n");
        where.append(" )\n");
    }

    private void validateOptionalDateRange(java.time.LocalDate begin, java.time.LocalDate end) {
        if ((begin == null) != (end == null)
                || begin != null && (!validDateRange(begin, end))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private TransactionHistoryQuery normalizeTransactionHistoryQuery(String transactionId,
                                                                      LocalDateTime transactionDateTime,
                                                                      Integer pageNo,
                                                                      Integer pageSize) {
        String normalizedTransactionId = trimMax(transactionId, 64);
        if (normalizedTransactionId == null || transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        PageSpec page = normalizePageValues(pageNo, pageSize);
        return new TransactionHistoryQuery(
                normalizedTransactionId, transactionDateTime, page.pageNo(), page.pageSize());
    }

    private TransactionIdentityQuery normalizeTransactionIdentity(String transactionId,
                                                                    LocalDateTime transactionDateTime) {
        String normalizedTransactionId = trimMax(transactionId, 64);
        if (normalizedTransactionId == null || transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return new TransactionIdentityQuery(normalizedTransactionId, transactionDateTime);
    }

    private PostingSearchRequest normalizePostingQuery(PostingSearchRequest request) {
        if (request == null || request.getBeginPostedTime() == null || request.getEndPostedTime() == null
                || request.getBeginPostedTime().isAfter(request.getEndPostedTime())
                || Duration.between(request.getBeginPostedTime(), request.getEndPostedTime()).toDays() > MAX_DATE_SPAN_DAYS) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        request.setSettlementBatchNo(trim(request.getSettlementBatchNo()));
        if (request.getSettlementBatchNo() != null && !request.getSettlementBatchNo().matches("SB\\d{8}-\\d{8}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        request.setMerchantId(trimMax(request.getMerchantId(), 64));
        request.setLedgerNo(trimMax(request.getLedgerNo(), 64));
        request.setDirection(enumValue(request.getDirection(), DIRECTIONS));
        request.setOperationMode(enumValue(request.getOperationMode(), OPERATION_MODES));
        request.setCurrency(currency(request.getCurrency()));
        normalizePage(request.getPageNo(), request.getPageSize(), request::setPageNo, request::setPageSize);
        return request;
    }

    private boolean validDateRange(java.time.LocalDate begin, java.time.LocalDate end) {
        return begin != null && end != null && !begin.isAfter(end)
                && ChronoUnit.DAYS.between(begin, end) <= MAX_DATE_SPAN_DAYS;
    }

    private void validateOptionalTimeRange(LocalDateTime begin, LocalDateTime end) {
        if ((begin == null) != (end == null)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        if (begin != null && (begin.isAfter(end) || Duration.between(begin, end).toDays() > MAX_DATE_SPAN_DAYS)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private void normalizePage(Integer rawPageNo,
                               Integer rawPageSize,
                               java.util.function.IntConsumer pageNoSetter,
                               java.util.function.IntConsumer pageSizeSetter) {
        PageSpec page = normalizePageValues(rawPageNo, rawPageSize);
        pageNoSetter.accept(page.pageNo());
        pageSizeSetter.accept(page.pageSize());
    }

    private PageSpec normalizePageValues(Integer rawPageNo, Integer rawPageSize) {
        int pageNo = rawPageNo == null ? 1 : rawPageNo;
        int pageSize = rawPageSize == null ? DEFAULT_PAGE_SIZE : rawPageSize;
        if (pageNo < 1 || pageSize < 1 || pageSize > maxResultRows) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return new PageSpec(pageNo, pageSize);
    }

    private MapSqlParameterSource transactionHistoryParameters(TransactionHistoryQuery query,
                                                                AdminMerchantDataScope scope) {
        return new MapSqlParameterSource()
                .addValue("transactionId", query.transactionId())
                .addValue("transactionDateTime", query.transactionDateTime())
                .addValue("permittedMerchantIds", scope.merchantIds());
    }

    private MapSqlParameterSource transactionIdentityParameters(TransactionIdentityQuery query,
                                                                 AdminMerchantDataScope scope) {
        return new MapSqlParameterSource()
                .addValue("transactionId", query.transactionId())
                .addValue("transactionDateTime", query.transactionDateTime())
                .addValue("permittedMerchantIds", scope.merchantIds());
    }

    private String enumValue(String value, Set<String> allowed) {
        String normalized = trim(value);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        return normalized;
    }

    private String currency(String value) {
        String normalized = trim(value);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        return normalized;
    }

    private String trimMax(String value, int maxLength) {
        String normalized = trim(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return normalized;
    }

    /** 根据 ALL/SCOPED/EMPTY 数据范围生成固定白名单 SQL 片段，列名只允许调用方传入的常量。 */
    private String scopeSql(AdminMerchantDataScope scope, String merchantColumn) {
        return scope.allMerchants() ? "" : " AND " + merchantColumn + " IN (:permittedMerchantIds)\n";
    }

    private AdminMerchantDataScope requireScope(AdminMerchantDataScope scope) {
        if (scope == null) throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        return scope;
    }

    private <T> PageResult<T> emptyPage(int pageNo, int pageSize) {
        return PageResult.of(0L, pageNo, pageSize, List.of());
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record PageSpec(int pageNo, int pageSize) {
    }

    private record TransactionHistoryQuery(String transactionId,
                                           LocalDateTime transactionDateTime,
                                           int pageNo,
                                           int pageSize) {
    }

    private record TransactionComponentQuery(String settlementBatchNo,
                                             String transactionId,
                                             int pageNo,
                                             int pageSize) {
    }

    private record TransactionIdentityQuery(String transactionId,
                                            LocalDateTime transactionDateTime) {
    }
}
