package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchDetail;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchSummary;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ClearingDetail;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ClearingReserveLine;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ClearingSummary;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ClearingTransactionLine;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.RateLine;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReconciliationRecord;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.SummaryLine;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionSettlement;
import com.scott.payment.merchant.service.MerchantSettlementQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcMerchantSettlementQueryService
 * @date : 2026-09-01 22:35
 * @email : scott_x@163.com
 * @description : 使用交易逻辑数据源本地查询 Merchant 结算数据；所有主表和关联表均绑定可信 merchantId，并限制日期跨度、分页预算和可见终态。
 * @status : update
 */
@Service
public class JdbcMerchantSettlementQueryService implements MerchantSettlementQueryService {

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
     * {@code MAX_DATE_SPAN_DAYS}常量，统一 {@code JdbcMerchantSettlementQueryService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long MAX_DATE_SPAN_DAYS = 92;
    private static final Set<String> BATCH_TYPES = Set.of(
            "REGULAR", "RESERVE_RELEASE", "REVERSAL", "ADJUSTMENT");
    private static final Set<String> BATCH_STATUSES = Set.of("POSTED", "REVERSED");
    private static final Set<String> RESERVE_ACTION_TYPES = Set.of(
            "HOLD", "RETURN", "RELEASE", "ADJUSTMENT",
            "REVERSAL_HOLD", "REVERSAL_RETURN", "REVERSAL_RELEASE", "REVERSAL_ADJUSTMENT");
    private static final Set<String> RESERVE_STATUSES = Set.of(
            "HELD", "PARTIALLY_RETURNED", "RELEASABLE", "FROZEN",
            "RETURNED", "RELEASED", "ADJUSTED", "REVERSED");
    private static final String BATCH_COLUMNS = """
            batch.settlement_batch_no, batch.merchant_id, merchant.merchant_name,
            batch.settlement_account_id, account.account_no AS settlement_account_no,
            batch.business_date, batch.business_time_zone,
            batch.target_currency, batch.target_currency_exponent, batch.batch_type,
            batch.batch_status,
            (SELECT COUNT(DISTINCT item.source_transaction_id)
             FROM settlement_result_item item
             WHERE item.settlement_batch_no = batch.settlement_batch_no
               AND item.source_transaction_id IS NOT NULL) AS transaction_count,
            batch.candidate_count, net.direction AS net_direction,
            net.target_amount AS net_amount, batch.posted_time, batch.create_time
            """;
    private static final String TRANSACTION_COLUMNS = """
            item.settlement_result_item_no, item.settlement_batch_no, batch.business_date,
            item.source_transaction_id, item.source_transaction_date_time,
            item.source_detail_no, item.result_item_type, item.payment_type,
            item.payment_method, item.transaction_type, item.fee_category, item.direction,
            item.source_amount, item.source_currency, item.source_currency_exponent,
            rate.direct_rate, item.target_amount, item.target_currency,
            item.target_currency_exponent, item.applied_limit, item.create_time
            """;
    private static final String TRANSACTION_SETTLEMENT_COLUMNS = """
            batch.settlement_batch_no, batch.business_date, batch.batch_status,
            candidate.id AS candidate_id, locator.merchant_order_no,
            candidate.source_transaction_id,
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
    private static final String RECONCILIATION_COLUMNS = """
            operation.transaction_id, operation.merchant_order_no,
            operation.transaction_type, operation.reconciliation_status,
            operation.settlement_status, operation.accounting_status,
            operation.transaction_date_time, operation.operation_time
            """;
    private static final String CLEARING_SUMMARY_COLUMNS = """
            finance.transaction_id, finance.transaction_type, finance.label_currency,
            operation.label_amount AS label_amount, finance.clearing_status,
            finance.gross_label_amount, finance.platform_fee_amount,
            finance.reserve_amount, finance.settlement_status,
            finance.settlement_eligible_date, finance.transaction_date_time
            """;
    private static final String RESERVE_COLUMN_PREFIX = """
            action.reserve_action_no, action.reserve_no, action.settlement_batch_no,
            batch.business_date,
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
            action.action_type,
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

    /** 创建生产查询服务，沿用交易逻辑数据源的超时和结果预算。 */
    @Autowired
    public JdbcMerchantSettlementQueryService(DataSource dataSource,
                                              TransactionLogicalReadExecutor readExecutor,
                                              TransactionShardingProperties shardingProperties,
                                              TransactionQueryJdbcTemplateFactory factory) {
        this(factory.create(dataSource, shardingProperties), readExecutor, shardingProperties);
    }

    /** 创建可注入 JDBC 模板的查询服务，供定向契约测试使用。 */
    public JdbcMerchantSettlementQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                              TransactionLogicalReadExecutor readExecutor,
                                              TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.readExecutor = readExecutor;
        this.maxResultRows = shardingProperties.getQueryBudget().getMaxResultRows();
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<BatchSummary> searchBatches(String merchantId, BatchQuery request) {
        String scopedMerchantId = requireMerchantId(merchantId);
        BatchQuery query = normalizeBatchQuery(request);
        return readExecutor.read(() -> batchPage(scopedMerchantId, query));
    }

    /** {@inheritDoc} */
    @Override
    public BatchDetail getBatch(String merchantId, String settlementBatchNo) {
        String scopedMerchantId = requireMerchantId(merchantId);
        String batchNo = requireBatchNo(settlementBatchNo);
        return readExecutor.read(() -> batchDetail(scopedMerchantId, batchNo, false));
    }

    /** {@inheritDoc} */
    @Override
    public BatchDetail getVoucher(String merchantId, String settlementBatchNo) {
        String scopedMerchantId = requireMerchantId(merchantId);
        String batchNo = requireBatchNo(settlementBatchNo);
        return readExecutor.read(() -> batchDetail(scopedMerchantId, batchNo, true));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<SummaryLine> searchResultSummaries(String merchantId,
                                                         String settlementBatchNo,
                                                         Integer pageNo,
                                                         Integer pageSize) {
        String scopedMerchantId = requireMerchantId(merchantId);
        String batchNo = requireBatchNo(settlementBatchNo);
        PageWindow page = normalizePage(pageNo, pageSize);
        return readExecutor.read(() -> resultSummaryPage(scopedMerchantId, batchNo, page));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<TransactionSettlement> searchTransactionItems(String merchantId,
                                                                    TransactionItemQuery request) {
        String scopedMerchantId = requireMerchantId(merchantId);
        TransactionItemQuery query = normalizeTransactionQuery(request);
        return readExecutor.read(() -> transactionPage(scopedMerchantId, query));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<TransactionItem> searchTransactionComponents(String merchantId,
                                                                   String settlementBatchNo,
                                                                   String transactionId,
                                                                   Integer pageNo,
                                                                   Integer pageSize) {
        String scopedMerchantId = requireMerchantId(merchantId);
        TransactionComponentQuery query = normalizeTransactionComponentQuery(
                settlementBatchNo, transactionId, pageNo, pageSize);
        return readExecutor.read(() -> transactionComponentPage(scopedMerchantId, query));
    }

    /** {@inheritDoc} */
    @Override
    public List<ReconciliationRecord> findReconciliationRecordsByTransaction(
            String merchantId,
            String transactionId,
            LocalDateTime transactionDateTime) {
        String scopedMerchantId = requireMerchantId(merchantId);
        TransactionIdentityQuery query = normalizeTransactionIdentity(transactionId, transactionDateTime);
        return readExecutor.read(() -> reconciliationRecords(scopedMerchantId, query));
    }

    /** {@inheritDoc} */
    @Override
    public ClearingDetail findClearingDetailByTransaction(String merchantId,
                                                           String transactionId,
                                                           LocalDateTime transactionDateTime) {
        String scopedMerchantId = requireMerchantId(merchantId);
        TransactionIdentityQuery query = normalizeTransactionIdentity(transactionId, transactionDateTime);
        return readExecutor.read(() -> clearingDetail(scopedMerchantId, query));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<TransactionItem> searchTransactionItemsByTransaction(String merchantId,
                                                                           String transactionId,
                                                                           LocalDateTime transactionDateTime,
                                                                           Integer pageNo,
                                                                           Integer pageSize) {
        String scopedMerchantId = requireMerchantId(merchantId);
        TransactionHistoryQuery query = normalizeTransactionHistoryQuery(
                transactionId, transactionDateTime, pageNo, pageSize);
        return readExecutor.read(() -> transactionHistoryPage(scopedMerchantId, query));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<ReserveItem> searchReserveItems(String merchantId, ReserveItemQuery request) {
        String scopedMerchantId = requireMerchantId(merchantId);
        ReserveItemQuery query = normalizeReserveQuery(request);
        return readExecutor.read(() -> reservePage(scopedMerchantId, query));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<ReserveItem> searchReserveItemsByTransaction(String merchantId,
                                                                   String transactionId,
                                                                   LocalDateTime transactionDateTime,
                                                                   Integer pageNo,
                                                                   Integer pageSize) {
        String scopedMerchantId = requireMerchantId(merchantId);
        TransactionHistoryQuery query = normalizeTransactionHistoryQuery(
                transactionId, transactionDateTime, pageNo, pageSize);
        return readExecutor.read(() -> reserveHistoryPage(scopedMerchantId, query));
    }

    /** 查询 POSTED/REVERSED 批次，并从净入账结果行提取商户最关心的交易数和净额。 */
    private PageResult<BatchSummary> batchPage(String merchantId, BatchQuery query) {
        StringBuilder where = new StringBuilder("""
                WHERE batch.merchant_id = :merchantId
                  AND batch.batch_status IN ('POSTED', 'REVERSED')
                  AND batch.business_date BETWEEN :beginDate AND :endDate
                """);
        if (query.getSettlementBatchNo() != null) where.append(" AND batch.settlement_batch_no = :batchNo\n");
        if (query.getBatchType() != null) where.append(" AND batch.batch_type = :batchType\n");
        if (query.getBatchStatus() != null) where.append(" AND batch.batch_status = :batchStatus\n");
        MapSqlParameterSource parameters = commonParameters(merchantId, query.getBeginBusinessDate(),
                query.getEndBusinessDate()).addValue("batchNo", query.getSettlementBatchNo())
                .addValue("batchType", query.getBatchType()).addValue("batchStatus", query.getBatchStatus());
        String from = """
                FROM settlement_batch batch
                LEFT JOIN base_merchant_info merchant
                  ON merchant.merchant_id = batch.merchant_id AND merchant.deleted = 0
                LEFT JOIN merchant_fund_account account
                  ON account.id = batch.settlement_account_id
                 AND account.merchant_id = batch.merchant_id
                 AND account.deleted = 0
                LEFT JOIN settlement_result_item net
                  ON net.settlement_batch_no = batch.settlement_batch_no
                 AND net.result_role = 'LEDGER_POSTING'
                 AND net.result_item_type = 'NET_SETTLEMENT'
                """;
        return page(BATCH_COLUMNS, from, where.toString(), parameters,
                "batch.business_date DESC, batch.id DESC", query.getPageNo(), query.getPageSize(),
                BatchSummary.class);
    }

    /**
     * 在同一商户条件下加载批次、已锁定结算汇率和结果汇总；不存在或越权统一按订单不存在处理。
     */
    private BatchDetail batchDetail(String merchantId, String batchNo, boolean includeSummaries) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("merchantId", merchantId).addValue("batchNo", batchNo);
        String from = """
                FROM settlement_batch batch
                LEFT JOIN base_merchant_info merchant
                  ON merchant.merchant_id = batch.merchant_id AND merchant.deleted = 0
                LEFT JOIN merchant_fund_account account
                  ON account.id = batch.settlement_account_id
                 AND account.merchant_id = batch.merchant_id
                 AND account.deleted = 0
                LEFT JOIN settlement_result_item net
                  ON net.settlement_batch_no = batch.settlement_batch_no
                 AND net.result_role = 'LEDGER_POSTING'
                 AND net.result_item_type = 'NET_SETTLEMENT'
                """;
        List<BatchSummary> batches = jdbcTemplate.query("SELECT " + BATCH_COLUMNS + from + """
                        WHERE batch.merchant_id = :merchantId
                          AND batch.settlement_batch_no = :batchNo
                          AND batch.batch_status IN ('POSTED', 'REVERSED')
                        LIMIT 1
                        """, parameters, BeanPropertyRowMapper.newInstance(BatchSummary.class));
        if (batches.isEmpty()) throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        BatchDetail detail = new BatchDetail();
        detail.setBatch(batches.get(0));
        detail.setRates(jdbcTemplate.query("""
                SELECT rate.source_currency, rate.target_currency, rate.direct_rate,
                       rate.effective_time, rate.locked_time,
                       'PLATFORM_SETTLEMENT_RATE' AS display_source
                FROM settlement_batch_rate rate
                JOIN settlement_batch batch ON batch.settlement_batch_no = rate.settlement_batch_no
                WHERE batch.merchant_id = :merchantId
                  AND rate.settlement_batch_no = :batchNo
                  AND rate.rate_type = 'SETTLEMENT'
                  AND rate.rate_status = 'LOCKED'
                ORDER BY rate.source_currency, rate.id
                """, parameters, BeanPropertyRowMapper.newInstance(RateLine.class)));
        if (includeSummaries) {
            detail.setSummaries(resultSummaries(merchantId, batchNo));
        }
        return detail;
    }

    /** 凭证使用完整不可变汇总快照，普通详情和列表仍使用分页查询。 */
    private List<SummaryLine> resultSummaries(String merchantId, String batchNo) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("batchNo", batchNo);
        return jdbcTemplate.query("""
                SELECT summary.payment_type, summary.payment_method, summary.transaction_type,
                       summary.result_item_type, summary.fee_category, summary.direction,
                       summary.source_currency,
                       COALESCE(NULLIF(source_currency.fraction_digits, -1), 2)
                           AS source_currency_exponent,
                       summary.target_currency, batch.target_currency_exponent,
                       summary.transaction_count, summary.source_amount, summary.target_amount
                FROM settlement_result_summary summary
                JOIN settlement_batch batch ON batch.settlement_batch_no = summary.settlement_batch_no
                LEFT JOIN base_iso_currency source_currency
                  ON source_currency.alpha3_code = summary.source_currency
                 AND source_currency.deleted = 0
                WHERE batch.merchant_id = :merchantId
                  AND summary.settlement_batch_no = :batchNo
                ORDER BY summary.payment_type, summary.payment_method, summary.transaction_type,
                         summary.result_item_type, summary.fee_category, summary.direction,
                         summary.source_currency, summary.target_currency, summary.id
                """, parameters, BeanPropertyRowMapper.newInstance(SummaryLine.class));
    }

    /** 使用批次维度唯一索引分页读取商户可见的结算汇总。 */
    private PageResult<SummaryLine> resultSummaryPage(String merchantId,
                                                      String batchNo,
                                                      PageWindow page) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("batchNo", batchNo);
        String from = """
                FROM settlement_result_summary summary
                JOIN settlement_batch batch
                  ON batch.settlement_batch_no = summary.settlement_batch_no
                 AND batch.merchant_id = summary.merchant_id
                LEFT JOIN base_iso_currency source_currency
                  ON source_currency.alpha3_code = summary.source_currency
                 AND source_currency.deleted = 0
                """;
        String where = """
                WHERE summary.settlement_batch_no = :batchNo
                  AND summary.merchant_id = :merchantId
                  AND batch.merchant_id = :merchantId
                  AND batch.batch_status IN ('POSTED', 'REVERSED')
                """;
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) " + from + where,
                parameters, Long.class);
        long total = count == null ? 0L : count;
        long offset = (long) (page.pageNo() - 1) * page.pageSize();
        List<SummaryLine> records = offset < total ? jdbcTemplate.query("""
                SELECT summary.payment_type, summary.payment_method, summary.transaction_type,
                       summary.result_item_type, summary.fee_category, summary.direction,
                       summary.source_currency,
                       COALESCE(NULLIF(source_currency.fraction_digits, -1), 2)
                           AS source_currency_exponent,
                       summary.target_currency, batch.target_currency_exponent,
                       summary.transaction_count, summary.source_amount, summary.target_amount
                """ + from + where + """
                ORDER BY summary.merchant_id, summary.payment_type, summary.payment_method,
                         summary.transaction_type, summary.result_item_type, summary.fee_category,
                         summary.direction, summary.source_currency, summary.target_currency, summary.id
                LIMIT :offset, :limit
                """, new MapSqlParameterSource(parameters.getValues())
                .addValue("offset", offset)
                .addValue("limit", page.pageSize()),
                BeanPropertyRowMapper.newInstance(SummaryLine.class)) : List.of();
        if (total == 0L) {
            requireAccessibleBatch(merchantId, batchNo);
        }
        return PageResult.of(total, page.pageNo(), page.pageSize(), records);
    }

    /**
     * 只返回 TRANSACTION_CLEARING 的真实交易财务组件，排除保证金动作和任何伪交易投影。
     */
    private PageResult<TransactionSettlement> transactionPage(String merchantId, TransactionItemQuery query) {
        StringBuilder where = new StringBuilder("""
                WHERE candidate.merchant_id = :merchantId
                  AND batch.merchant_id = :merchantId
                  AND batch.business_date BETWEEN :beginDate AND :endDate
                  AND batch.batch_status IN ('POSTED', 'REVERSED')
                  AND candidate.source_type = 'CLEARING_REVISION'
                  AND candidate.source_transaction_id IS NOT NULL
                  AND item.source_detail_type = 'TRANSACTION_CLEARING'
                  AND item.result_role = 'FINANCIAL_COMPONENT'
                """);
        if (query.getSettlementBatchNo() != null) where.append(" AND candidate.settlement_batch_no = :batchNo\n");
        if (query.getSourceTransactionId() != null) where.append(" AND candidate.source_transaction_id = :transactionId\n");
        if (query.getMerchantOrderNo() != null) where.append(" AND locator.merchant_order_no = :merchantOrderNo\n");
        if (query.getBeginTransactionTime() != null) {
            where.append(" AND COALESCE(locator.transaction_date_time, candidate.source_transaction_date_time) >= :beginTransactionTime\n");
        }
        if (query.getEndTransactionTime() != null) {
            where.append(" AND COALESCE(locator.transaction_date_time, candidate.source_transaction_date_time) < :endTransactionTime\n");
        }
        appendTransactionExistsFilter(where, query);
        MapSqlParameterSource parameters = commonParameters(merchantId, query.getBeginBusinessDate(),
                query.getEndBusinessDate()).addValue("batchNo", query.getSettlementBatchNo())
                .addValue("transactionId", query.getSourceTransactionId())
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("beginTransactionTime", query.getBeginTransactionTime())
                .addValue("endTransactionTime", query.getEndTransactionTime())
                .addValue("paymentType", query.getPaymentType()).addValue("paymentMethod", query.getPaymentMethod())
                .addValue("transactionType", query.getTransactionType()).addValue("feeCategory", query.getFeeCategory());
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
                         batch.posted_time, candidate.id, locator.merchant_order_no,
                         candidate.source_transaction_id, locator.transaction_date_time,
                         candidate.source_transaction_date_time, locator.transaction_type
                """;
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT candidate.id) " + from + where,
                parameters, Long.class);
        long total = count == null ? 0L : count;
        long offset = (long) (query.getPageNo() - 1) * query.getPageSize();
        List<TransactionSettlement> records = offset < total ? jdbcTemplate.query(
                "SELECT " + TRANSACTION_SETTLEMENT_COLUMNS + from + where + groupBy
                        + " ORDER BY source_transaction_date_time DESC, candidate.id DESC LIMIT :offset, :limit",
                new MapSqlParameterSource(parameters.getValues())
                        .addValue("offset", offset).addValue("limit", query.getPageSize()),
                BeanPropertyRowMapper.newInstance(TransactionSettlement.class)) : List.of();
        return PageResult.of(total, query.getPageNo(), query.getPageSize(), records);
    }

    /** 批次内一笔交易的财务组件独立分页，供列表展开核对。 */
    private PageResult<TransactionItem> transactionComponentPage(String merchantId,
                                                                 TransactionComponentQuery query) {
        String where = """
                WHERE item.merchant_id = :merchantId
                  AND batch.merchant_id = :merchantId
                  AND item.settlement_batch_no = :batchNo
                  AND item.source_transaction_id = :transactionId
                  AND item.source_detail_type = 'TRANSACTION_CLEARING'
                  AND item.result_role = 'FINANCIAL_COMPONENT'
                  AND batch.batch_status IN ('POSTED', 'REVERSED')
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("batchNo", query.settlementBatchNo())
                .addValue("transactionId", query.transactionId());
        String from = """
                FROM settlement_result_item item
                JOIN settlement_batch batch ON batch.settlement_batch_no = item.settlement_batch_no
                JOIN settlement_batch_rate rate ON rate.id = item.settlement_batch_rate_id
                """;
        return page(TRANSACTION_COLUMNS, from, where, parameters,
                "item.result_line_no ASC, item.id ASC", query.pageNo(), query.pageSize(),
                TransactionItem.class);
    }

    /** 按候选真实交易身份查询已入账或已冲正的商户财务组件。 */
    private PageResult<TransactionItem> transactionHistoryPage(String merchantId,
                                                               TransactionHistoryQuery query) {
        String where = """
                WHERE candidate.source_type = 'CLEARING_REVISION'
                  AND candidate.source_transaction_id = :transactionId
                  AND candidate.source_transaction_date_time = :transactionDateTime
                  AND candidate.merchant_id = :merchantId
                  AND item.merchant_id = :merchantId
                  AND batch.merchant_id = :merchantId
                  AND batch.batch_status IN ('POSTED', 'REVERSED')
                  AND item.source_detail_type = 'TRANSACTION_CLEARING'
                  AND item.result_role = 'FINANCIAL_COMPONENT'
                  AND item.source_transaction_id IS NOT NULL
                """;
        String from = """
                FROM settlement_candidate candidate
                JOIN settlement_result_item item
                  ON item.settlement_batch_no = candidate.settlement_batch_no
                 AND item.candidate_id = candidate.id
                 AND item.merchant_id = candidate.merchant_id
                 AND item.source_detail_type = 'TRANSACTION_CLEARING'
                JOIN settlement_batch batch ON batch.settlement_batch_no = item.settlement_batch_no
                JOIN settlement_batch_rate rate ON rate.id = item.settlement_batch_rate_id
                """;
        return page(TRANSACTION_COLUMNS, from, where, transactionHistoryParameters(merchantId, query),
                "item.create_time DESC, item.id DESC", query.pageNo(), query.pageSize(), TransactionItem.class);
    }

    /** 按交易精确身份读取当前认证商户的动作状态。 */
    private List<ReconciliationRecord> reconciliationRecords(String merchantId,
                                                              TransactionIdentityQuery query) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("transactionId", query.transactionId())
                .addValue("transactionDateTime", query.transactionDateTime());
        return jdbcTemplate.query("""
                SELECT %s
                FROM transaction_operation operation
                WHERE operation.transaction_id = :transactionId
                  AND operation.transaction_date_time = :transactionDateTime
                  AND operation.merchant_id = :merchantId
                  AND operation.deleted = 0
                ORDER BY operation.id DESC
                LIMIT 1
                """.formatted(RECONCILIATION_COLUMNS), parameters,
                BeanPropertyRowMapper.newInstance(ReconciliationRecord.class));
    }

    /** 商户清分详情只返回商户核账所需字段，并在摘要和明细查询中重复绑定 merchantId。 */
    private ClearingDetail clearingDetail(String merchantId, TransactionIdentityQuery query) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("transactionId", query.transactionId())
                .addValue("transactionDateTime", query.transactionDateTime());
        List<ClearingSummary> summaries = jdbcTemplate.query("""
                SELECT %s
                FROM transaction_finance_state finance
                LEFT JOIN transaction_operation operation
                  ON operation.transaction_id = finance.transaction_id
                 AND operation.transaction_date_time = finance.transaction_date_time
                 AND operation.merchant_id = finance.merchant_id
                 AND (finance.operation_id IS NULL OR operation.operation_id = finance.operation_id)
                 AND operation.deleted = 0
                WHERE finance.transaction_id = :transactionId
                  AND finance.transaction_date_time = :transactionDateTime
                  AND finance.merchant_id = :merchantId
                  AND finance.deleted = 0
                LIMIT 1
                """.formatted(CLEARING_SUMMARY_COLUMNS), parameters,
                BeanPropertyRowMapper.newInstance(ClearingSummary.class));
        if (summaries.isEmpty()) {
            return null;
        }
        ClearingDetail detail = new ClearingDetail();
        ClearingSummary summary = summaries.get(0);
        detail.setSummary(summary);
        Integer clearingRevision = jdbcTemplate.queryForObject("""
                SELECT finance.clearing_revision
                FROM transaction_finance_state finance
                WHERE finance.transaction_id = :transactionId
                  AND finance.transaction_date_time = :transactionDateTime
                  AND finance.merchant_id = :merchantId
                  AND finance.deleted = 0
                LIMIT 1
                """, parameters, Integer.class);
        if (clearingRevision == null || clearingRevision < 1) {
            return detail;
        }
        parameters.addValue("clearingRevision", clearingRevision);
        detail.setTransactionDetails(jdbcTemplate.query("""
                SELECT clearing_detail_no, line_no, item_type, fee_category,
                       item_code, item_name, direction, basis_currency, basis_amount,
                       amount, currency, currency_exponent, record_status
                FROM transaction_clearing_detail
                WHERE transaction_id = :transactionId
                  AND transaction_date_time = :transactionDateTime
                  AND merchant_id = :merchantId
                  AND clearing_revision = :clearingRevision
                  AND record_status = 'ACTIVE'
                ORDER BY line_no ASC, id ASC
                """, parameters, BeanPropertyRowMapper.newInstance(ClearingTransactionLine.class)));
        detail.setReserveDetails(jdbcTemplate.query("""
                SELECT reserve_clearing_detail_no, line_no, reserve_action_type,
                       item_code, item_name, direction, reserve_currency,
                       reserve_currency_exponent, retained_amount, returned_amount,
                       released_amount, adjustment_amount, remaining_amount,
                       expected_reserve_release_date, record_status
                FROM transaction_reserve_clearing_detail
                WHERE transaction_id = :transactionId
                  AND transaction_date_time = :transactionDateTime
                  AND merchant_id = :merchantId
                  AND clearing_revision = :clearingRevision
                  AND record_status = 'ACTIVE'
                ORDER BY line_no ASC, id ASC
                """, parameters, BeanPropertyRowMapper.newInstance(ClearingReserveLine.class)));
        return detail;
    }

    /**
     * 查询不可变保证金动作，并按 retained + debitAdjustment - return - release - creditAdjustment - reversal
     * 计算动作对应责任余额；保证金币种保持原标签币种且不使用汇率。
     */
    private PageResult<ReserveItem> reservePage(String merchantId, ReserveItemQuery query) {
        StringBuilder where = new StringBuilder("""
                WHERE reserve.merchant_id = :merchantId
                  AND batch.merchant_id = :merchantId
                  AND batch.business_date BETWEEN :beginDate AND :endDate
                  AND batch.batch_status IN ('POSTED', 'REVERSED')
                """);
        if (query.getSettlementBatchNo() != null) where.append(" AND action.settlement_batch_no = :batchNo\n");
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
        MapSqlParameterSource parameters = commonParameters(merchantId, query.getBeginBusinessDate(),
                query.getEndBusinessDate()).addValue("batchNo", query.getSettlementBatchNo())
                .addValue("reserveNo", query.getReserveNo()).addValue("reserveActionNo", query.getReserveActionNo())
                .addValue("transactionId", query.getSourceTransactionId())
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("reserveStatus", query.getReserveStatus())
                .addValue("actionType", query.getActionType()).addValue("currency", query.getCurrency())
                .addValue("beginTransactionTime", query.getBeginTransactionTime())
                .addValue("endTransactionTime", query.getEndTransactionTime())
                .addValue("beginExpectedReleaseDate", query.getBeginExpectedReleaseDate())
                .addValue("endExpectedReleaseDate", query.getEndExpectedReleaseDate());
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
                 AND reserve_detail.merchant_id = reserve.merchant_id
                 AND reserve_detail.record_status = 'ACTIVE'
                JOIN settlement_batch batch ON batch.settlement_batch_no = action.settlement_batch_no
                LEFT JOIN transaction_locator locator
                  ON locator.transaction_id = reserve_detail.original_transaction_id
                 AND locator.merchant_id = reserve.merchant_id
                LEFT JOIN base_iso_currency currency
                  ON currency.alpha3_code = action.currency AND currency.deleted = 0
                """;
        return page(RESERVE_COLUMNS, from, where.toString(), parameters,
                "action.action_time DESC, action.id DESC", query.getPageNo(), query.getPageSize(), ReserveItem.class);
    }

    /** 按原支付交易身份查询当前商户跨批次的完整保证金动作。 */
    private PageResult<ReserveItem> reserveHistoryPage(String merchantId, TransactionHistoryQuery query) {
        String where = """
                WHERE reserve_state.original_transaction_id = :transactionId
                  AND reserve_state.transaction_date_time = :transactionDateTime
                  AND reserve_state.merchant_id = :merchantId
                  AND reserve.merchant_id = :merchantId
                  AND batch.merchant_id = :merchantId
                  AND batch.batch_status IN ('POSTED', 'REVERSED')
                """;
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
                LEFT JOIN transaction_locator locator
                  ON locator.transaction_id = reserve_state.original_transaction_id
                 AND locator.merchant_id = reserve_state.merchant_id
                LEFT JOIN base_iso_currency currency
                  ON currency.alpha3_code = action.currency AND currency.deleted = 0
                """;
        return page(RESERVE_HISTORY_COLUMNS, from, where, transactionHistoryParameters(merchantId, query),
                "action.action_time DESC, action.id DESC", query.pageNo(), query.pageSize(), ReserveItem.class);
    }

    private <T> PageResult<T> page(String columns, String from, String where,
                                   MapSqlParameterSource parameters, String orderBy,
                                   int pageNo, int pageSize, Class<T> type) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) " + from + where, parameters, Long.class);
        long total = count == null ? 0L : count;
        long offset = (long) (pageNo - 1) * pageSize;
        List<T> rows = offset < total ? jdbcTemplate.query(
                "SELECT " + columns + from + where + " ORDER BY " + orderBy + " LIMIT :offset, :limit",
                new MapSqlParameterSource(parameters.getValues()).addValue("offset", offset).addValue("limit", pageSize),
                BeanPropertyRowMapper.newInstance(type)) : List.of();
        return PageResult.of(total, pageNo, pageSize, rows);
    }

    private BatchQuery normalizeBatchQuery(BatchQuery request) {
        BatchQuery query = request == null ? new BatchQuery() : request;
        normalizeRangeAndPage(query.getBeginBusinessDate(), query.getEndBusinessDate(), query.getPageNo(), query.getPageSize(),
                query::setBeginBusinessDate, query::setEndBusinessDate, query::setPageNo, query::setPageSize);
        query.setSettlementBatchNo(optionalBatchNo(query.getSettlementBatchNo()));
        query.setBatchType(optionalEnum(query.getBatchType(), BATCH_TYPES));
        query.setBatchStatus(optionalEnum(query.getBatchStatus(), BATCH_STATUSES));
        return query;
    }

    private TransactionItemQuery normalizeTransactionQuery(TransactionItemQuery request) {
        TransactionItemQuery query = request == null ? new TransactionItemQuery() : request;
        normalizeRangeAndPage(query.getBeginBusinessDate(), query.getEndBusinessDate(), query.getPageNo(), query.getPageSize(),
                query::setBeginBusinessDate, query::setEndBusinessDate, query::setPageNo, query::setPageSize);
        query.setSettlementBatchNo(optionalBatchNo(query.getSettlementBatchNo()));
        query.setSourceTransactionId(optionalText(query.getSourceTransactionId(), 64));
        query.setMerchantOrderNo(optionalText(query.getMerchantOrderNo(), 128));
        validateOptionalTimeRange(query.getBeginTransactionTime(), query.getEndTransactionTime());
        query.setPaymentType(optionalText(query.getPaymentType(), 32));
        query.setPaymentMethod(optionalText(query.getPaymentMethod(), 64));
        query.setTransactionType(optionalText(query.getTransactionType(), 32));
        query.setFeeCategory(optionalText(query.getFeeCategory(), 32));
        return query;
    }

    private TransactionComponentQuery normalizeTransactionComponentQuery(String settlementBatchNo,
                                                                          String transactionId,
                                                                          Integer pageNo,
                                                                          Integer pageSize) {
        String batchNo = requireBatchNo(settlementBatchNo);
        String normalizedTransactionId = optionalText(transactionId, 64);
        if (normalizedTransactionId == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        PageWindow page = normalizePage(pageNo, pageSize);
        return new TransactionComponentQuery(batchNo, normalizedTransactionId,
                page.pageNo(), page.pageSize());
    }

    private ReserveItemQuery normalizeReserveQuery(ReserveItemQuery request) {
        ReserveItemQuery query = request == null ? new ReserveItemQuery() : request;
        normalizeRangeAndPage(query.getBeginBusinessDate(), query.getEndBusinessDate(), query.getPageNo(), query.getPageSize(),
                query::setBeginBusinessDate, query::setEndBusinessDate, query::setPageNo, query::setPageSize);
        query.setSettlementBatchNo(optionalBatchNo(query.getSettlementBatchNo()));
        query.setReserveNo(optionalText(query.getReserveNo(), 64));
        query.setReserveActionNo(optionalText(query.getReserveActionNo(), 64));
        query.setSourceTransactionId(optionalText(query.getSourceTransactionId(), 64));
        query.setMerchantOrderNo(optionalText(query.getMerchantOrderNo(), 128));
        query.setReserveStatus(optionalEnum(query.getReserveStatus(), RESERVE_STATUSES));
        query.setActionType(optionalEnum(query.getActionType(), RESERVE_ACTION_TYPES));
        query.setCurrency(optionalCurrency(query.getCurrency()));
        validateOptionalTimeRange(query.getBeginTransactionTime(), query.getEndTransactionTime());
        validateOptionalDateRange(query.getBeginExpectedReleaseDate(), query.getEndExpectedReleaseDate());
        return query;
    }

    private void appendTransactionExistsFilter(StringBuilder where, TransactionItemQuery query) {
        if (query.getPaymentType() == null && query.getPaymentMethod() == null
                && query.getTransactionType() == null && query.getFeeCategory() == null) {
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
        if (query.getPaymentType() != null) where.append(" AND filter_item.payment_type = :paymentType\n");
        if (query.getPaymentMethod() != null) where.append(" AND filter_item.payment_method = :paymentMethod\n");
        if (query.getTransactionType() != null) where.append(" AND filter_item.transaction_type = :transactionType\n");
        if (query.getFeeCategory() != null) where.append(" AND filter_item.fee_category = :feeCategory\n");
        where.append(" )\n");
    }

    private void validateOptionalDateRange(LocalDate begin, LocalDate end) {
        if ((begin == null) != (end == null)
                || begin != null && (begin.isAfter(end)
                || ChronoUnit.DAYS.between(begin, end) > MAX_DATE_SPAN_DAYS)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private TransactionHistoryQuery normalizeTransactionHistoryQuery(String transactionId,
                                                                      LocalDateTime transactionDateTime,
                                                                      Integer pageNo,
                                                                      Integer pageSize) {
        String normalizedTransactionId = optionalText(transactionId, 64);
        if (normalizedTransactionId == null || transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        int normalizedPageNo = pageNo == null ? 1 : pageNo;
        int normalizedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (normalizedPageNo < 1 || normalizedPageSize < 1 || normalizedPageSize > maxResultRows) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return new TransactionHistoryQuery(
                normalizedTransactionId, transactionDateTime, normalizedPageNo, normalizedPageSize);
    }

    private PageWindow normalizePage(Integer pageNo, Integer pageSize) {
        int normalizedPageNo = pageNo == null ? 1 : pageNo;
        int normalizedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (normalizedPageNo < 1 || normalizedPageSize < 1 || normalizedPageSize > maxResultRows) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return new PageWindow(normalizedPageNo, normalizedPageSize);
    }

    private TransactionIdentityQuery normalizeTransactionIdentity(String transactionId,
                                                                    LocalDateTime transactionDateTime) {
        String normalizedTransactionId = optionalText(transactionId, 64);
        if (normalizedTransactionId == null || transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return new TransactionIdentityQuery(normalizedTransactionId, transactionDateTime);
    }

    /**
     * 统一补齐默认 30 天范围和分页值，并把最大日期跨度限制为 92 天、页大小限制在逻辑数据源预算内。
     */
    private void normalizeRangeAndPage(LocalDate begin, LocalDate end, Integer pageNo, Integer pageSize,
                                       java.util.function.Consumer<LocalDate> beginSetter,
                                       java.util.function.Consumer<LocalDate> endSetter,
                                       java.util.function.Consumer<Integer> pageNoSetter,
                                       java.util.function.Consumer<Integer> pageSizeSetter) {
        LocalDate normalizedEnd = end == null ? LocalDate.now() : end;
        LocalDate normalizedBegin = begin == null ? normalizedEnd.minusDays(30) : begin;
        int normalizedPageNo = pageNo == null ? 1 : pageNo;
        int normalizedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (normalizedBegin.isAfter(normalizedEnd)
                || ChronoUnit.DAYS.between(normalizedBegin, normalizedEnd) > MAX_DATE_SPAN_DAYS
                || normalizedPageNo < 1 || normalizedPageSize < 1 || normalizedPageSize > maxResultRows) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        beginSetter.accept(normalizedBegin);
        endSetter.accept(normalizedEnd);
        pageNoSetter.accept(normalizedPageNo);
        pageSizeSetter.accept(normalizedPageSize);
    }

    private void validateOptionalTimeRange(LocalDateTime begin, LocalDateTime end) {
        if ((begin == null) != (end == null)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        if (begin != null && (begin.isAfter(end) || ChronoUnit.DAYS.between(begin, end) > MAX_DATE_SPAN_DAYS)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private MapSqlParameterSource commonParameters(String merchantId, LocalDate begin, LocalDate end) {
        return new MapSqlParameterSource().addValue("merchantId", merchantId)
                .addValue("beginDate", begin).addValue("endDate", end);
    }

    private MapSqlParameterSource transactionHistoryParameters(String merchantId,
                                                                TransactionHistoryQuery query) {
        return new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("transactionId", query.transactionId())
                .addValue("transactionDateTime", query.transactionDateTime());
    }

    private String requireMerchantId(String value) {
        String merchantId = optionalText(value, 64);
        if (merchantId == null) throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        return merchantId;
    }

    private String requireBatchNo(String value) {
        String batchNo = optionalBatchNo(value);
        if (batchNo == null) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        return batchNo;
    }

    private void requireAccessibleBatch(String merchantId, String batchNo) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM settlement_batch
                WHERE settlement_batch_no = :batchNo
                  AND merchant_id = :merchantId
                  AND batch_status IN ('POSTED', 'REVERSED')
                """, new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("batchNo", batchNo), Integer.class);
        if (count == null || count == 0) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
    }

    private String optionalBatchNo(String value) {
        String batchNo = optionalText(value, 32);
        if (batchNo != null && !batchNo.matches("SB\\d{8}-\\d{8}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return batchNo;
    }

    private String optionalCurrency(String value) {
        String currency = optionalText(value, 3);
        if (currency == null) return null;
        currency = currency.toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        return currency;
    }

    private String optionalEnum(String value, Set<String> values) {
        String normalized = optionalText(value, 32);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!values.contains(normalized)) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        return normalized;
    }

    private record TransactionHistoryQuery(String transactionId,
                                           LocalDateTime transactionDateTime,
                                           int pageNo,
                                           int pageSize) {
    }

    private record TransactionIdentityQuery(String transactionId,
                                            LocalDateTime transactionDateTime) {
    }

    private record TransactionComponentQuery(String settlementBatchNo,
                                             String transactionId,
                                             int pageNo,
                                             int pageSize) {
    }

    private record PageWindow(int pageNo, int pageSize) {
    }
}
