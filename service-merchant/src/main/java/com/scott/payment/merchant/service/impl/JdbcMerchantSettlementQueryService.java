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
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.RateLine;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.SummaryLine;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;
import com.scott.payment.merchant.service.MerchantSettlementQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.time.LocalDate;
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
    private static final String BATCH_COLUMNS = """
            batch.settlement_batch_no, batch.business_date, batch.business_time_zone,
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
    private static final String RESERVE_COLUMNS = """
            action.reserve_action_no, action.reserve_no, action.settlement_batch_no,
            batch.business_date, candidate.source_transaction_id,
            candidate.source_transaction_date_time, action.action_type,
            action.direction, action.currency,
            COALESCE(NULLIF(currency.fraction_digits, -1), 2) AS currency_exponent,
            action.amount, reserve.retained_amount,
            reserve.returned_amount, reserve.released_amount,
            reserve.debit_adjustment_amount, reserve.credit_adjustment_amount,
            reserve.reversed_amount,
            (reserve.retained_amount + reserve.debit_adjustment_amount
             - reserve.returned_amount - reserve.released_amount
             - reserve.credit_adjustment_amount - reserve.reversed_amount) AS remaining_amount,
            reserve.reserve_status, reserve.expected_release_date, action.action_time
            """;

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
        return readExecutor.read(() -> batchDetail(scopedMerchantId, batchNo));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<TransactionItem> searchTransactionItems(String merchantId,
                                                              TransactionItemQuery request) {
        String scopedMerchantId = requireMerchantId(merchantId);
        TransactionItemQuery query = normalizeTransactionQuery(request);
        return readExecutor.read(() -> transactionPage(scopedMerchantId, query));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<ReserveItem> searchReserveItems(String merchantId, ReserveItemQuery request) {
        String scopedMerchantId = requireMerchantId(merchantId);
        ReserveItemQuery query = normalizeReserveQuery(request);
        return readExecutor.read(() -> reservePage(scopedMerchantId, query));
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
    private BatchDetail batchDetail(String merchantId, String batchNo) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("merchantId", merchantId).addValue("batchNo", batchNo);
        String from = """
                FROM settlement_batch batch
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
        detail.setSummaries(jdbcTemplate.query("""
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
                """, parameters, BeanPropertyRowMapper.newInstance(SummaryLine.class)));
        return detail;
    }

    /**
     * 只返回 TRANSACTION_CLEARING 的真实交易财务组件，排除保证金动作和任何伪交易投影。
     */
    private PageResult<TransactionItem> transactionPage(String merchantId, TransactionItemQuery query) {
        StringBuilder where = new StringBuilder("""
                WHERE item.merchant_id = :merchantId
                  AND batch.merchant_id = :merchantId
                  AND batch.business_date BETWEEN :beginDate AND :endDate
                  AND batch.batch_status IN ('POSTED', 'REVERSED')
                  AND item.source_detail_type = 'TRANSACTION_CLEARING'
                  AND item.result_role = 'FINANCIAL_COMPONENT'
                  AND item.source_transaction_id IS NOT NULL
                """);
        if (query.getSettlementBatchNo() != null) where.append(" AND item.settlement_batch_no = :batchNo\n");
        if (query.getSourceTransactionId() != null) where.append(" AND item.source_transaction_id = :transactionId\n");
        if (query.getPaymentType() != null) where.append(" AND item.payment_type = :paymentType\n");
        if (query.getPaymentMethod() != null) where.append(" AND item.payment_method = :paymentMethod\n");
        if (query.getTransactionType() != null) where.append(" AND item.transaction_type = :transactionType\n");
        if (query.getFeeCategory() != null) where.append(" AND item.fee_category = :feeCategory\n");
        MapSqlParameterSource parameters = commonParameters(merchantId, query.getBeginBusinessDate(),
                query.getEndBusinessDate()).addValue("batchNo", query.getSettlementBatchNo())
                .addValue("transactionId", query.getSourceTransactionId())
                .addValue("paymentType", query.getPaymentType()).addValue("paymentMethod", query.getPaymentMethod())
                .addValue("transactionType", query.getTransactionType()).addValue("feeCategory", query.getFeeCategory());
        String from = """
                FROM settlement_result_item item
                JOIN settlement_batch batch ON batch.settlement_batch_no = item.settlement_batch_no
                JOIN settlement_batch_rate rate ON rate.id = item.settlement_batch_rate_id
                """;
        return page(TRANSACTION_COLUMNS, from, where.toString(), parameters,
                "item.source_transaction_date_time DESC, item.id DESC", query.getPageNo(), query.getPageSize(),
                TransactionItem.class);
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
        if (query.getSourceTransactionId() != null) where.append(" AND candidate.source_transaction_id = :transactionId\n");
        if (query.getActionType() != null) where.append(" AND action.action_type = :actionType\n");
        if (query.getCurrency() != null) where.append(" AND action.currency = :currency\n");
        MapSqlParameterSource parameters = commonParameters(merchantId, query.getBeginBusinessDate(),
                query.getEndBusinessDate()).addValue("batchNo", query.getSettlementBatchNo())
                .addValue("reserveNo", query.getReserveNo()).addValue("transactionId", query.getSourceTransactionId())
                .addValue("actionType", query.getActionType()).addValue("currency", query.getCurrency());
        String from = """
                FROM merchant_reserve_action action
                JOIN merchant_reserve_item reserve
                  ON reserve.id = action.reserve_item_id AND reserve.reserve_no = action.reserve_no
                JOIN settlement_candidate candidate
                  ON candidate.id = action.candidate_id AND candidate.merchant_id = reserve.merchant_id
                JOIN settlement_batch batch ON batch.settlement_batch_no = action.settlement_batch_no
                LEFT JOIN base_iso_currency currency
                  ON currency.alpha3_code = action.currency AND currency.deleted = 0
                """;
        return page(RESERVE_COLUMNS, from, where.toString(), parameters,
                "action.action_time DESC, action.id DESC", query.getPageNo(), query.getPageSize(), ReserveItem.class);
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
        query.setPaymentType(optionalText(query.getPaymentType(), 32));
        query.setPaymentMethod(optionalText(query.getPaymentMethod(), 64));
        query.setTransactionType(optionalText(query.getTransactionType(), 32));
        query.setFeeCategory(optionalText(query.getFeeCategory(), 32));
        return query;
    }

    private ReserveItemQuery normalizeReserveQuery(ReserveItemQuery request) {
        ReserveItemQuery query = request == null ? new ReserveItemQuery() : request;
        normalizeRangeAndPage(query.getBeginBusinessDate(), query.getEndBusinessDate(), query.getPageNo(), query.getPageSize(),
                query::setBeginBusinessDate, query::setEndBusinessDate, query::setPageNo, query::setPageSize);
        query.setSettlementBatchNo(optionalBatchNo(query.getSettlementBatchNo()));
        query.setReserveNo(optionalText(query.getReserveNo(), 64));
        query.setSourceTransactionId(optionalText(query.getSourceTransactionId(), 64));
        query.setActionType(optionalEnum(query.getActionType(), RESERVE_ACTION_TYPES));
        query.setCurrency(optionalCurrency(query.getCurrency()));
        return query;
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

    private MapSqlParameterSource commonParameters(String merchantId, LocalDate begin, LocalDate end) {
        return new MapSqlParameterSource().addValue("merchantId", merchantId)
                .addValue("beginDate", begin).addValue("endDate", end);
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
}
