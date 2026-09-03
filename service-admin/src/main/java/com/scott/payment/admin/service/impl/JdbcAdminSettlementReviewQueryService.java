package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.CandidateSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.CandidateSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultSummaryLine;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCandidateLine;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewRateLine;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSummary;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminSettlementReviewQueryService;
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
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminSettlementReviewQueryService
 * @date : 2026-09-01 23:15
 * @email : scott_x@163.com
 * @description : 从 transaction 逻辑读数据源查询交易/保证金候选和预审单；按来源类型选择季度关联、强制数据范围并提供命令前访问校验。
 * @status : update
 */
@Service
public class JdbcAdminSettlementReviewQueryService implements AdminSettlementReviewQueryService {

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
     * {@code MAX_DATE_SPAN_DAYS}常量，统一 {@code JdbcAdminSettlementReviewQueryService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long MAX_DATE_SPAN_DAYS = 92;
    private static final Set<String> CANDIDATE_STATUSES = Set.of(
            "READY", "REPLAY_HOLD", "REVIEW_LOCKED", "SUPERSEDED",
            "CLAIMED", "POSTED", "MANUAL_REVIEW", "CANCELLED");
    private static final Set<String> REVIEW_TYPES = Set.of("REGULAR", "RESERVE_RELEASE", "ADJUSTMENT");
    private static final Set<String> RESERVE_SOURCE_TYPES = Set.of("RESERVE_RELEASE", "ADJUSTMENT");
    private static final Set<String> REVIEW_STATUSES = Set.of(
            "PENDING_APPROVAL", "APPROVED", "REJECTED", "CANCELLED", "EXPIRED");
    private static final Set<String> RESERVE_STATUSES = Set.of(
            "HELD", "PARTIALLY_RETURNED", "RELEASABLE", "FROZEN",
            "RETURNED", "RELEASED", "ADJUSTED", "REVERSED");
    private static final String REMAINING_RESERVE_AMOUNT_SQL = """
            (reserve_item.retained_amount + reserve_item.debit_adjustment_amount
             - reserve_item.returned_amount - reserve_item.released_amount
             - reserve_item.credit_adjustment_amount - reserve_item.reversed_amount)
            """;
    private static final String CANDIDATE_COLUMNS = """
            candidate.id, candidate.candidate_no, candidate.source_type,
            candidate.source_business_id, candidate.source_revision,
            CASE WHEN candidate.source_type IN ('RESERVE_RELEASE', 'ADJUSTMENT')
                 THEN reserve_detail.original_transaction_id
                 ELSE candidate.source_transaction_id
            END AS source_transaction_id,
            CASE WHEN candidate.source_type IN ('RESERVE_RELEASE', 'ADJUSTMENT')
                 THEN reserve_detail.original_transaction_date_time
                 ELSE candidate.source_transaction_date_time
            END AS source_transaction_date_time,
            candidate.merchant_id, merchant.merchant_name, operation.merchant_order_no,
            COALESCE(clearing_detail.payment_type, reserve_detail.payment_type) AS payment_type,
            COALESCE(clearing_detail.payment_method, reserve_detail.payment_method) AS payment_method,
            COALESCE(operation.transaction_type, clearing_detail.transaction_type,
                     reserve_detail.transaction_type) AS transaction_type,
            COALESCE(operation.label_currency, clearing_detail.label_currency,
                     reserve_detail.reserve_currency) AS label_currency,
            COALESCE(operation.label_amount, clearing_detail.label_amount,
                     reserve_detail.basis_amount) AS label_amount,
            COALESCE(clearing_detail.label_currency_exponent,
                     reserve_detail.reserve_currency_exponent,
                     operation.currency_exponent) AS label_currency_exponent,
            finance.gross_label_amount, finance.platform_fee_amount, finance.reserve_amount,
            finance.net_settlement_amount, finance.fee_evaluation_status,
            reserve_detail.reserve_action_type,
            reserve_detail.direction AS reserve_direction,
            CASE reserve_detail.reserve_action_type
                WHEN 'HOLD' THEN reserve_detail.retained_amount
                WHEN 'RETURN' THEN reserve_detail.returned_amount
                WHEN 'RELEASE' THEN reserve_detail.released_amount
                WHEN 'ADJUSTMENT' THEN reserve_detail.adjustment_amount
                ELSE NULL
            END AS reserve_action_amount,
            reserve_item.reserve_no, reserve_item.reserve_status,
            reserve_detail.expected_reserve_release_date,
            (reserve_item.retained_amount + reserve_item.debit_adjustment_amount
             - reserve_item.returned_amount - reserve_item.released_amount
             - reserve_item.credit_adjustment_amount - reserve_item.reversed_amount)
                AS remaining_amount,
            candidate.settlement_profile_id, candidate.target_currency,
            candidate.target_currency_exponent, candidate.settlement_eligible_date,
            candidate.candidate_status, candidate.settlement_batch_no,
            candidate.review_order_no, candidate.shadow_mode, candidate.claimed_time,
            candidate.posted_time, candidate.version, candidate.create_time, candidate.update_time
            """;
    private static final String CANDIDATE_FROM_SQL_TEMPLATE = """
            FROM settlement_candidate candidate
            LEFT JOIN base_merchant_info merchant
              ON merchant.merchant_id = candidate.merchant_id AND merchant.deleted = 0
            %s transaction_finance_state finance
              ON candidate.source_type = 'CLEARING_REVISION'
             AND finance.finance_state_id = candidate.source_business_id
             AND finance.transaction_id = candidate.source_transaction_id
             AND finance.transaction_date_time = candidate.source_transaction_date_time
             AND finance.clearing_revision = candidate.source_revision
             AND finance.deleted = 0
            %s transaction_clearing_detail clearing_detail
              ON candidate.source_type = 'CLEARING_REVISION'
             AND clearing_detail.transaction_id = candidate.source_transaction_id
             AND clearing_detail.transaction_date_time = candidate.source_transaction_date_time
             AND clearing_detail.clearing_revision = candidate.source_revision
             AND clearing_detail.line_no = 1 AND clearing_detail.record_status = 'ACTIVE'
            %s transaction_reserve_clearing_detail reserve_detail
              ON candidate.source_type IN ('RESERVE_RELEASE', 'ADJUSTMENT')
             AND reserve_detail.transaction_id = candidate.source_transaction_id
             AND reserve_detail.transaction_date_time = candidate.source_transaction_date_time
             AND reserve_detail.clearing_revision = candidate.source_revision
             AND reserve_detail.line_no = 1 AND reserve_detail.record_status = 'ACTIVE'
            LEFT JOIN merchant_reserve_item reserve_item
              ON candidate.source_type IN ('RESERVE_RELEASE', 'ADJUSTMENT')
             AND reserve_item.merchant_id = candidate.merchant_id
             AND reserve_item.source_business_no = COALESCE(
                     reserve_detail.source_reserve_detail_no,
                     reserve_detail.reserve_clearing_detail_no)
            LEFT JOIN transaction_operation operation
              ON operation.operation_id = COALESCE(finance.operation_id, reserve_detail.operation_id)
             AND operation.transaction_date_time = CASE
                     WHEN candidate.source_type IN ('RESERVE_RELEASE', 'ADJUSTMENT')
                         THEN reserve_detail.original_transaction_date_time
                     ELSE candidate.source_transaction_date_time
                 END
             AND operation.deleted = 0
            """;
    private static final String REVIEW_COLUMNS = """
            id, review_order_no, review_type, create_mode, merchant_id, settlement_profile_id,
            settlement_account_id, target_currency, target_currency_exponent, business_date,
            business_time_zone, candidate_count, projectable_candidate_count, net_direction,
            net_amount, review_status, submitted_by_account_id, submitted_by_account_name,
            submit_reason, submitted_time, decided_by_account_id, decided_by_account_name,
            decision_action, review_comment, decision_time, settlement_batch_no, version,
            create_time, update_time
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionLogicalReadExecutor readExecutor;
    private final int maxResultRows;

    @Autowired
    public JdbcAdminSettlementReviewQueryService(DataSource dataSource,
                                                 TransactionLogicalReadExecutor readExecutor,
                                                 TransactionShardingProperties shardingProperties,
                                                 TransactionQueryJdbcTemplateFactory factory) {
        this(factory.create(dataSource, shardingProperties), readExecutor, shardingProperties);
    }

    public JdbcAdminSettlementReviewQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                                 TransactionLogicalReadExecutor readExecutor,
                                                 TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.readExecutor = readExecutor;
        this.maxResultRows = shardingProperties.getQueryBudget().getMaxResultRows();
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<CandidateSummary> searchCandidates(CandidateSearchRequest request,
                                                         Set<String> sourceTypes,
                                                         AdminMerchantDataScope dataScope) {
        CandidateSearchRequest query = normalizeCandidate(request);
        Set<String> normalizedSources = normalizeSources(sourceTypes);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> candidatePage(query, normalizedSources, scope));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<ReviewSummary> searchReviews(ReviewSearchRequest request,
                                                   AdminMerchantDataScope dataScope) {
        ReviewSearchRequest query = normalizeReview(request);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> reviewPage(query, scope));
    }

    /** {@inheritDoc} */
    @Override
    public CandidateSummary candidateDetail(String candidateNo,
                                            Set<String> sourceTypes,
                                            AdminMerchantDataScope dataScope) {
        String normalizedCandidateNo = requireCandidateNo(candidateNo);
        Set<String> normalizedSources = normalizeSources(sourceTypes);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> requireCandidate(normalizedCandidateNo, normalizedSources, scope));
    }

    /** {@inheritDoc} */
    @Override
    public ReviewDetailResponse reviewDetail(String reviewOrderNo, AdminMerchantDataScope dataScope) {
        String orderNo = requireReviewNo(reviewOrderNo);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> detail(orderNo, scope));
    }

    /**
     * 命令提交前校验全部候选均在当前数据范围内；任一缺失或越权均按资源不存在处理。
     *
     * @param candidateIds 去重后的候选主键，最多 1000 个
     * @param dataScope 当前 Admin 商户数据范围
     */
    @Override
    public void requireCandidateAccess(List<Long> candidateIds, AdminMerchantDataScope dataScope) {
        if (candidateIds == null || candidateIds.isEmpty() || candidateIds.size() > 1000
                || candidateIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(candidateIds).size() != candidateIds.size()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        AdminMerchantDataScope scope = requireScope(dataScope);
        readExecutor.read(() -> {
            if (scope.empty()) {
                throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
            }
            MapSqlParameterSource parameters = new MapSqlParameterSource("candidateIds", candidateIds)
                    .addValue("permittedMerchantIds", scope.merchantIds());
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM settlement_candidate
                    WHERE id IN (:candidateIds)
                    """ + scopeSql(scope), parameters, Long.class);
            if (count == null || count != candidateIds.size()) {
                throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
            }
            return null;
        });
    }

    /**
     * 决策命令提交前校验预审单处于当前 Admin 数据范围。
     *
     * @param reviewOrderNo 预审单号
     * @param dataScope 当前 Admin 商户数据范围
     */
    @Override
    public void requireReviewAccess(String reviewOrderNo, AdminMerchantDataScope dataScope) {
        String orderNo = requireReviewNo(reviewOrderNo);
        AdminMerchantDataScope scope = requireScope(dataScope);
        readExecutor.read(() -> {
            requireReview(orderNo, scope);
            return null;
        });
    }

    /**
     * 按候选来源构造只读关联；仅 CLEARING_REVISION 读取交易清分事实，保证金来源保持原交易引用且不生成伪投影。
     */
    private PageResult<CandidateSummary> candidatePage(CandidateSearchRequest query,
                                                       Set<String> sourceTypes,
                                                       AdminMerchantDataScope scope) {
        if (scope.empty()) {
            return PageResult.of(0L, query.getPageNo(), query.getPageSize(), List.of());
        }
        String fromSql = candidateFromSql(sourceTypes);
        StringBuilder where = new StringBuilder("""
                WHERE candidate.source_type IN (:sourceTypes)
                  AND candidate.settlement_eligible_date BETWEEN :beginDate AND :endDate
                  AND candidate.shadow_mode = 0
                """);
        if (trim(query.getCandidateNo()) != null) where.append(" AND candidate.candidate_no = :candidateNo\n");
        if (trim(query.getMerchantId()) != null) where.append(" AND candidate.merchant_id = :merchantId\n");
        boolean transactionCandidates = sourceTypes.equals(Set.of("CLEARING_REVISION"));
        if (query.getSourceTransactionId() != null) where.append(transactionCandidates
                ? " AND candidate.source_transaction_id = :sourceTransactionId\n"
                : " AND reserve_detail.original_transaction_id = :sourceTransactionId\n");
        if (query.getMerchantOrderNo() != null) where.append(" AND operation.merchant_order_no = :merchantOrderNo\n");
        if (query.getBeginTransactionTime() != null) where.append(transactionCandidates
                ? " AND candidate.source_transaction_date_time BETWEEN :beginTransactionTime AND :endTransactionTime\n"
                : " AND reserve_detail.original_transaction_date_time BETWEEN :beginTransactionTime AND :endTransactionTime\n");
        if (query.getPaymentType() != null) where.append(transactionCandidates
                ? " AND clearing_detail.payment_type = :paymentType\n"
                : " AND reserve_detail.payment_type = :paymentType\n");
        if (query.getPaymentMethod() != null) where.append(transactionCandidates
                ? " AND clearing_detail.payment_method = :paymentMethod\n"
                : " AND reserve_detail.payment_method = :paymentMethod\n");
        if (query.getTransactionType() != null) where.append(transactionCandidates
                ? " AND clearing_detail.transaction_type = :transactionType\n"
                : " AND reserve_detail.transaction_type = :transactionType\n");
        if (query.getLabelCurrency() != null) where.append(transactionCandidates
                ? " AND clearing_detail.label_currency = :labelCurrency\n"
                : " AND reserve_detail.reserve_currency = :labelCurrency\n");
        if (query.getTargetCurrency() != null) where.append(" AND candidate.target_currency = :targetCurrency\n");
        if (query.getSourceRevision() != null) where.append(" AND candidate.source_revision = :sourceRevision\n");
        if (query.getReserveNo() != null) where.append(" AND reserve_item.reserve_no = :reserveNo\n");
        if (query.getReserveStatus() != null) where.append(" AND reserve_item.reserve_status = :reserveStatus\n");
        if (query.getBeginExpectedReleaseDate() != null) where.append(
                " AND reserve_detail.expected_reserve_release_date BETWEEN :beginExpectedReleaseDate AND :endExpectedReleaseDate\n");
        if (query.getDue() != null) where.append(Boolean.TRUE.equals(query.getDue())
                ? " AND reserve_detail.expected_reserve_release_date <= CURRENT_DATE()\n"
                : " AND (reserve_detail.expected_reserve_release_date IS NULL OR reserve_detail.expected_reserve_release_date > CURRENT_DATE())\n");
        if (query.getFrozen() != null) where.append(Boolean.TRUE.equals(query.getFrozen())
                ? " AND reserve_item.reserve_status = 'FROZEN'\n"
                : " AND (reserve_item.reserve_status IS NULL OR reserve_item.reserve_status <> 'FROZEN')\n");
        if (query.getMinRemainingAmount() != null) where.append(
                " AND " + REMAINING_RESERVE_AMOUNT_SQL + " >= :minRemainingAmount\n");
        if (query.getMaxRemainingAmount() != null) where.append(
                " AND " + REMAINING_RESERVE_AMOUNT_SQL + " <= :maxRemainingAmount\n");
        if (trim(query.getCandidateStatus()) != null) where.append(" AND candidate.candidate_status = :candidateStatus\n");
        where.append(candidateScopeSql(scope));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("sourceTypes", sourceTypes).addValue("beginDate", query.getBeginEligibleDate())
                .addValue("endDate", query.getEndEligibleDate()).addValue("candidateNo", trim(query.getCandidateNo()))
                .addValue("merchantId", trim(query.getMerchantId()))
                .addValue("sourceTransactionId", query.getSourceTransactionId())
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("beginTransactionTime", query.getBeginTransactionTime())
                .addValue("endTransactionTime", query.getEndTransactionTime())
                .addValue("paymentType", query.getPaymentType())
                .addValue("paymentMethod", query.getPaymentMethod())
                .addValue("transactionType", query.getTransactionType())
                .addValue("labelCurrency", query.getLabelCurrency())
                .addValue("targetCurrency", query.getTargetCurrency())
                .addValue("sourceRevision", query.getSourceRevision())
                .addValue("reserveNo", query.getReserveNo())
                .addValue("reserveStatus", query.getReserveStatus())
                .addValue("beginExpectedReleaseDate", query.getBeginExpectedReleaseDate())
                .addValue("endExpectedReleaseDate", query.getEndExpectedReleaseDate())
                .addValue("minRemainingAmount", query.getMinRemainingAmount())
                .addValue("maxRemainingAmount", query.getMaxRemainingAmount())
                .addValue("candidateStatus", trim(query.getCandidateStatus()))
                .addValue("permittedMerchantIds", scope.merchantIds());
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) " + fromSql + where,
                parameters, Long.class);
        long total = count == null ? 0 : count;
        long offset = (long) (query.getPageNo() - 1) * query.getPageSize();
        String orderBy = candidateOrderBy(sourceTypes);
        List<CandidateSummary> rows = offset < total
                ? jdbcTemplate.query("SELECT " + CANDIDATE_COLUMNS + fromSql + where
                        + " ORDER BY " + orderBy + " LIMIT :offset, :limit", new MapSqlParameterSource(parameters.getValues())
                        .addValue("offset", offset).addValue("limit", query.getPageSize()),
                BeanPropertyRowMapper.newInstance(CandidateSummary.class))
                : List.of();
        return PageResult.of(total, query.getPageNo(), query.getPageSize(), rows);
    }

    private String candidateOrderBy(Set<String> sourceTypes) {
        if (sourceTypes.equals(Set.of("CLEARING_REVISION"))) {
            return "candidate.source_transaction_date_time DESC, candidate.id DESC";
        }
        if (RESERVE_SOURCE_TYPES.containsAll(sourceTypes)) {
            return "reserve_detail.expected_reserve_release_date ASC, candidate.id ASC";
        }
        return "candidate.settlement_eligible_date DESC, candidate.id DESC";
    }

    /**
     * 仅在查询源集合需要时拼接季度分片关联，避免保证金纯查询无界扫描交易分表。
     */
    private String candidateFromSql(Set<String> sourceTypes) {
        if (sourceTypes.equals(Set.of("CLEARING_REVISION"))) {
            return CANDIDATE_FROM_SQL_TEMPLATE.formatted("JOIN", "JOIN", "LEFT JOIN");
        }
        if (sourceTypes.equals(RESERVE_SOURCE_TYPES)) {
            return CANDIDATE_FROM_SQL_TEMPLATE.formatted("LEFT JOIN", "LEFT JOIN", "JOIN");
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID);
    }

    private PageResult<ReviewSummary> reviewPage(ReviewSearchRequest query,
                                                 AdminMerchantDataScope scope) {
        if (scope.empty()) {
            return PageResult.of(0L, query.getPageNo(), query.getPageSize(), List.of());
        }
        StringBuilder where = new StringBuilder("""
                WHERE business_date BETWEEN :beginDate AND :endDate
                """);
        if (trim(query.getReviewOrderNo()) != null) where.append(" AND review_order_no = :reviewOrderNo\n");
        if (trim(query.getMerchantId()) != null) where.append(" AND merchant_id = :merchantId\n");
        if (trim(query.getReviewType()) != null) where.append(" AND review_type = :reviewType\n");
        if (trim(query.getReviewStatus()) != null) where.append(" AND review_status = :reviewStatus\n");
        where.append(scopeSql(scope));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("beginDate", query.getBeginBusinessDate()).addValue("endDate", query.getEndBusinessDate())
                .addValue("reviewOrderNo", trim(query.getReviewOrderNo()))
                .addValue("merchantId", trim(query.getMerchantId()))
                .addValue("reviewType", trim(query.getReviewType()))
                .addValue("reviewStatus", trim(query.getReviewStatus()))
                .addValue("permittedMerchantIds", scope.merchantIds());
        return page("settlement_review_order", REVIEW_COLUMNS, where.toString(), parameters,
                "business_date DESC, id DESC", query.getPageNo(), query.getPageSize(), ReviewSummary.class);
    }

    /** 加载预审主单、选择候选、锁定汇率和结果汇总，所有子查询继承主单已验证的数据范围。 */
    private ReviewDetailResponse detail(String reviewOrderNo, AdminMerchantDataScope scope) {
        ReviewSummary review = requireReview(reviewOrderNo, scope);
        MapSqlParameterSource parameters = new MapSqlParameterSource("reviewOrderNo", reviewOrderNo);
        ReviewDetailResponse response = new ReviewDetailResponse();
        response.setReview(review);
        response.setCandidates(jdbcTemplate.query("""
                SELECT review_candidate_no, candidate_id, candidate_no, source_type, source_business_id,
                       source_revision, source_transaction_id, source_transaction_date_time,
                       relation_status, locked_time, consumed_time, released_time
                FROM settlement_review_candidate
                WHERE review_order_no = :reviewOrderNo
                ORDER BY candidate_id ASC, id ASC
                """, parameters, BeanPropertyRowMapper.newInstance(ReviewCandidateLine.class)));
        response.setRates(jdbcTemplate.query("""
                SELECT source_currency, target_currency, direct_rate, source_currency_exponent,
                       target_currency_exponent, rate_source, quote_id, source_quote_direction,
                       effective_time, locked_time
                FROM settlement_review_rate
                WHERE review_order_no = :reviewOrderNo
                ORDER BY source_currency ASC, id ASC
                """, parameters, BeanPropertyRowMapper.newInstance(ReviewRateLine.class)));
        response.setSummaries(jdbcTemplate.query("""
                SELECT summary.payment_type, summary.payment_method, summary.transaction_type,
                       summary.result_item_type, summary.fee_category, summary.direction,
                       summary.source_currency,
                       COALESCE(NULLIF(source_currency.fraction_digits, -1), 2)
                           AS source_currency_exponent,
                       summary.target_currency, review.target_currency_exponent,
                       summary.transaction_count, summary.source_amount, summary.target_amount
                FROM settlement_review_summary summary
                JOIN settlement_review_order review
                  ON review.review_order_no = summary.review_order_no
                LEFT JOIN base_iso_currency source_currency
                  ON source_currency.alpha3_code = summary.source_currency
                 AND source_currency.deleted = 0
                WHERE summary.review_order_no = :reviewOrderNo
                ORDER BY summary.payment_type, summary.payment_method, summary.transaction_type,
                         summary.result_item_type, summary.fee_category, summary.direction,
                         summary.source_currency, summary.target_currency, summary.id
                """, parameters, BeanPropertyRowMapper.newInstance(ResultSummaryLine.class)));
        return response;
    }

    /** 在数据范围内锁定唯一预审单视图；不存在或越权统一抛出资源不存在。 */
    private ReviewSummary requireReview(String reviewOrderNo, AdminMerchantDataScope scope) {
        if (scope.empty()) throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        MapSqlParameterSource parameters = new MapSqlParameterSource("reviewOrderNo", reviewOrderNo)
                .addValue("permittedMerchantIds", scope.merchantIds());
        List<ReviewSummary> rows = jdbcTemplate.query("SELECT " + REVIEW_COLUMNS + """
                FROM settlement_review_order
                WHERE review_order_no = :reviewOrderNo
                """ + scopeSql(scope) + " LIMIT 1", parameters,
                BeanPropertyRowMapper.newInstance(ReviewSummary.class));
        if (rows.isEmpty()) throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        return rows.get(0);
    }

    /** 在来源类型和数据范围内查询唯一候选；不存在或越权统一抛出资源不存在。 */
    private CandidateSummary requireCandidate(String candidateNo,
                                              Set<String> sourceTypes,
                                              AdminMerchantDataScope scope) {
        if (scope.empty()) throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        MapSqlParameterSource parameters = new MapSqlParameterSource("candidateNo", candidateNo)
                .addValue("sourceTypes", sourceTypes)
                .addValue("permittedMerchantIds", scope.merchantIds());
        List<CandidateSummary> rows = jdbcTemplate.query("SELECT " + CANDIDATE_COLUMNS
                + candidateFromSql(sourceTypes) + """
                WHERE candidate.candidate_no = :candidateNo
                  AND candidate.source_type IN (:sourceTypes)
                  AND candidate.shadow_mode = 0
                """ + candidateScopeSql(scope) + " LIMIT 1", parameters,
                BeanPropertyRowMapper.newInstance(CandidateSummary.class));
        if (rows.isEmpty()) throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        return rows.get(0);
    }

    private <T> PageResult<T> page(String table,
                                   String columns,
                                   String where,
                                   MapSqlParameterSource parameters,
                                   String orderBy,
                                   int pageNo,
                                   int pageSize,
                                   Class<T> type) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + table + " " + where,
                parameters, Long.class);
        long total = count == null ? 0 : count;
        long offset = (long) (pageNo - 1) * pageSize;
        List<T> rows = offset < total ? jdbcTemplate.query("SELECT " + columns + " FROM " + table
                        + " " + where + " ORDER BY " + orderBy + " LIMIT :offset, :limit",
                new MapSqlParameterSource(parameters.getValues()).addValue("offset", offset)
                        .addValue("limit", pageSize), BeanPropertyRowMapper.newInstance(type)) : List.of();
        return PageResult.of(total, pageNo, pageSize, rows);
    }

    /** @return 候选表别名下参数化的 Admin 商户数据范围谓词。 */
    private String candidateScopeSql(AdminMerchantDataScope scope) {
        return scope.allMerchants() ? "" : " AND candidate.merchant_id IN (:permittedMerchantIds)\n";
    }

    private CandidateSearchRequest normalizeCandidate(CandidateSearchRequest request) {
        if (request == null || request.getBeginEligibleDate() == null || request.getEndEligibleDate() == null
                || request.getBeginEligibleDate().isAfter(request.getEndEligibleDate())
                || ChronoUnit.DAYS.between(request.getBeginEligibleDate(), request.getEndEligibleDate())
                > MAX_DATE_SPAN_DAYS) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        request.setCandidateNo(trim(request.getCandidateNo()));
        request.setMerchantId(trim(request.getMerchantId()));
        request.setSourceTransactionId(textValue(request.getSourceTransactionId(), 64));
        request.setMerchantOrderNo(textValue(request.getMerchantOrderNo(), 128));
        validateDateTimeRange(request.getBeginTransactionTime(), request.getEndTransactionTime());
        request.setPaymentType(codeValue(request.getPaymentType(), 32));
        request.setPaymentMethod(codeValue(request.getPaymentMethod(), 32));
        request.setTransactionType(codeValue(request.getTransactionType(), 32));
        request.setLabelCurrency(currencyValue(request.getLabelCurrency()));
        request.setTargetCurrency(currencyValue(request.getTargetCurrency()));
        if (request.getSourceRevision() != null && request.getSourceRevision() <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        request.setReserveNo(textValue(request.getReserveNo(), 64));
        request.setReserveStatus(enumValue(request.getReserveStatus(), RESERVE_STATUSES));
        validateDateRange(request.getBeginExpectedReleaseDate(), request.getEndExpectedReleaseDate());
        validateAmountRange(request.getMinRemainingAmount(), request.getMaxRemainingAmount());
        request.setCandidateStatus(enumValue(request.getCandidateStatus(), CANDIDATE_STATUSES));
        normalizePage(request.getPageNo(), request.getPageSize(), request::setPageNo, request::setPageSize);
        return request;
    }

    private ReviewSearchRequest normalizeReview(ReviewSearchRequest request) {
        if (request == null || request.getBeginBusinessDate() == null || request.getEndBusinessDate() == null
                || request.getBeginBusinessDate().isAfter(request.getEndBusinessDate())
                || ChronoUnit.DAYS.between(request.getBeginBusinessDate(), request.getEndBusinessDate())
                > MAX_DATE_SPAN_DAYS) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        request.setReviewOrderNo(trim(request.getReviewOrderNo()));
        if (request.getReviewOrderNo() != null) requireReviewNo(request.getReviewOrderNo());
        request.setMerchantId(trim(request.getMerchantId()));
        request.setReviewType(enumValue(request.getReviewType(), REVIEW_TYPES));
        request.setReviewStatus(enumValue(request.getReviewStatus(), REVIEW_STATUSES));
        normalizePage(request.getPageNo(), request.getPageSize(), request::setPageNo, request::setPageSize);
        return request;
    }

    private void normalizePage(Integer rawPageNo, Integer rawPageSize,
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

    private Set<String> normalizeSources(Set<String> sourceTypes) {
        if (sourceTypes == null || sourceTypes.isEmpty()) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        Set<String> normalized = sourceTypes.stream().map(value -> value.toUpperCase(Locale.ROOT)).collect(
                java.util.stream.Collectors.toUnmodifiableSet());
        if (!Set.of("CLEARING_REVISION", "RESERVE_RELEASE", "ADJUSTMENT").containsAll(normalized)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return normalized;
    }

    private String requireReviewNo(String value) {
        String normalized = trim(value);
        if (normalized == null || !normalized.matches("SO\\d{8}-\\d{8}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return normalized;
    }

    private String requireCandidateNo(String value) {
        String normalized = trim(value);
        if (normalized == null || normalized.length() > 64) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return normalized;
    }

    private String enumValue(String value, Set<String> allowed) {
        String normalized = trim(value);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        return normalized;
    }

    private String textValue(String value, int maxLength) {
        String normalized = trim(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return normalized;
    }

    private String codeValue(String value, int maxLength) {
        String normalized = textValue(value, maxLength);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_:-]+")) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        return normalized;
    }

    private String currencyValue(String value) {
        String normalized = codeValue(value, 3);
        if (normalized != null && !normalized.matches("[A-Z]{3}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return normalized;
    }

    private void validateDateTimeRange(java.time.LocalDateTime begin, java.time.LocalDateTime end) {
        if ((begin == null) != (end == null) || (begin != null && (begin.isAfter(end)
                || ChronoUnit.DAYS.between(begin.toLocalDate(), end.toLocalDate()) > MAX_DATE_SPAN_DAYS))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private void validateDateRange(java.time.LocalDate begin, java.time.LocalDate end) {
        if ((begin == null) != (end == null) || (begin != null && (begin.isAfter(end)
                || ChronoUnit.DAYS.between(begin, end) > MAX_DATE_SPAN_DAYS))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private void validateAmountRange(java.math.BigDecimal minimum, java.math.BigDecimal maximum) {
        if (!validAmount(minimum) || !validAmount(maximum)
                || (minimum != null && maximum != null && minimum.compareTo(maximum) > 0)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private boolean validAmount(java.math.BigDecimal value) {
        return value == null || (value.signum() >= 0 && value.scale() <= 8 && value.precision() <= 24);
    }

    /** @return 无表别名场景下参数化的 Admin 商户数据范围谓词。 */
    private String scopeSql(AdminMerchantDataScope scope) {
        return scope.allMerchants() ? "" : " AND merchant_id IN (:permittedMerchantIds)\n";
    }

    /** @return 非空可信数据范围；缺失上下文时按未授权拒绝。 */
    private AdminMerchantDataScope requireScope(AdminMerchantDataScope scope) {
        if (scope == null) throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        return scope;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
