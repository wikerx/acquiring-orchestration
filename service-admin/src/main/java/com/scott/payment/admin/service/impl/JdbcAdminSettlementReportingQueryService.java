package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSummary;
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
    private static final String RESERVE_COLUMNS = """
            action.id AS action_id, action.reserve_action_no, action.reserve_item_id,
            action.reserve_no, action.settlement_batch_no, batch.business_date,
            reserve.merchant_id, reserve.account_id, candidate.source_transaction_id,
            candidate.source_transaction_date_time, reserve.source_business_no,
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
            reserve.reserve_status, reserve.expected_release_date, action.action_time
            """;

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
    public PageResult<ResultItemSummary> searchResultItems(ResultItemSearchRequest request,
                                                           AdminMerchantDataScope dataScope) {
        ResultItemSearchRequest query = normalizeResultQuery(request);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> resultPage(query, scope));
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

    /**
     * 查询结算结果并关联同批次锁定直接汇率；不重新计算金额、币种、限额或舍入结果。
     */
    private PageResult<ResultItemSummary> resultPage(ResultItemSearchRequest query,
                                                     AdminMerchantDataScope scope) {
        if (scope.empty()) return emptyPage(query.getPageNo(), query.getPageSize());
        StringBuilder where = new StringBuilder("""
                WHERE batch.business_date BETWEEN :beginDate AND :endDate
                """);
        if (query.getSettlementBatchNo() != null) where.append(" AND ri.settlement_batch_no = :batchNo\n");
        if (query.getMerchantId() != null) where.append(" AND ri.merchant_id = :merchantId\n");
        if (query.getSourceTransactionId() != null) where.append(" AND ri.source_transaction_id = :transactionId\n");
        if (query.getResultItemType() != null) where.append(" AND ri.result_item_type = :itemType\n");
        if (query.getResultRole() != null) where.append(" AND ri.result_role = :resultRole\n");
        if (query.getDirection() != null) where.append(" AND ri.direction = :direction\n");
        if (query.getTargetCurrency() != null) where.append(" AND ri.target_currency = :currency\n");
        if (query.getSourceDetailType() != null) where.append(" AND ri.source_detail_type = :sourceDetailType\n");
        where.append(scopeSql(scope, "ri.merchant_id"));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("beginDate", query.getBeginBusinessDate()).addValue("endDate", query.getEndBusinessDate())
                .addValue("batchNo", query.getSettlementBatchNo()).addValue("merchantId", query.getMerchantId())
                .addValue("transactionId", query.getSourceTransactionId()).addValue("itemType", query.getResultItemType())
                .addValue("resultRole", query.getResultRole()).addValue("direction", query.getDirection())
                .addValue("currency", query.getTargetCurrency()).addValue("sourceDetailType", query.getSourceDetailType())
                .addValue("permittedMerchantIds", scope.merchantIds());
        String from = """
                FROM settlement_result_item ri
                JOIN settlement_batch batch ON batch.settlement_batch_no = ri.settlement_batch_no
                JOIN settlement_batch_rate rate ON rate.id = ri.settlement_batch_rate_id
                """;
        return page(RESULT_COLUMNS, from, where.toString(), parameters,
                "COALESCE(ri.source_transaction_date_time, ri.create_time) DESC, ri.id DESC",
                query.getPageNo(), query.getPageSize(), ResultItemSummary.class);
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
        if (query.getSourceTransactionId() != null) where.append(" AND candidate.source_transaction_id = :transactionId\n");
        if (query.getActionType() != null) where.append(" AND action.action_type = :actionType\n");
        if (query.getCurrency() != null) where.append(" AND action.currency = :currency\n");
        where.append(scopeSql(scope, "reserve.merchant_id"));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("beginDate", query.getBeginBusinessDate()).addValue("endDate", query.getEndBusinessDate())
                .addValue("batchNo", query.getSettlementBatchNo()).addValue("merchantId", query.getMerchantId())
                .addValue("reserveNo", query.getReserveNo()).addValue("transactionId", query.getSourceTransactionId())
                .addValue("actionType", query.getActionType()).addValue("currency", query.getCurrency())
                .addValue("permittedMerchantIds", scope.merchantIds());
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
                "action.action_time DESC, action.id DESC", query.getPageNo(), query.getPageSize(),
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
        request.setResultItemType(enumValue(request.getResultItemType(), RESULT_ITEM_TYPES));
        request.setResultRole(enumValue(request.getResultRole(), RESULT_ROLES));
        request.setDirection(enumValue(request.getDirection(), DIRECTIONS));
        request.setTargetCurrency(currency(request.getTargetCurrency()));
        request.setSourceDetailType(enumValue(request.getSourceDetailType(), SOURCE_DETAIL_TYPES));
        normalizePage(request.getPageNo(), request.getPageSize(), request::setPageNo, request::setPageSize);
        return request;
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
        request.setSourceTransactionId(trimMax(request.getSourceTransactionId(), 64));
        request.setActionType(enumValue(request.getActionType(), RESERVE_ACTION_TYPES));
        request.setCurrency(currency(request.getCurrency()));
        normalizePage(request.getPageNo(), request.getPageSize(), request::setPageNo, request::setPageSize);
        return request;
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

    private void normalizePage(Integer rawPageNo,
                               Integer rawPageSize,
                               java.util.function.IntConsumer pageNoSetter,
                               java.util.function.IntConsumer pageSizeSetter) {
        int pageNo = rawPageNo == null ? 1 : rawPageNo;
        int pageSize = rawPageSize == null ? DEFAULT_PAGE_SIZE : rawPageSize;
        if (pageNo < 1 || pageSize < 1 || pageSize > maxResultRows) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        pageNoSetter.accept(pageNo);
        pageSizeSetter.accept(pageSize);
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
}
