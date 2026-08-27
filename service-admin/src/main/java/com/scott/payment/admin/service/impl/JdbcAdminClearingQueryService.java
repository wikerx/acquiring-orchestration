package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.DetailResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveLine;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchItem;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.Summary;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TransactionLine;
import com.scott.payment.admin.service.AdminClearingQueryService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminClearingQueryService
 * @date : 2026-08-27 14:00
 * @email : scott_x@163.com
 * @description : Admin 清分 JDBC 查询实现；在 transaction 逻辑数据源内完成单季度分页和详情装配，允许读写分离路由副本。
 * @status : create
 */
@Service
public class JdbcAdminClearingQueryService implements AdminClearingQueryService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final Set<String> CLEARING_STATUSES = Set.of(
            "NOT_CLEARED", "PENDING", "PROCESSING", "WAITING_SOURCE", "FAILED",
            "MANUAL_REVIEW", "CLEARED", "NOT_REQUIRED");

    private static final String FINANCE_FROM_SQL = "FROM transaction_finance_state f\n";

    private static final String SUMMARY_FROM_SQL = FINANCE_FROM_SQL + """
            LEFT JOIN transaction_operation o
              ON o.transaction_id = f.transaction_id
             AND o.transaction_date_time = f.transaction_date_time
             AND (f.operation_id IS NULL OR o.operation_id = f.operation_id)
             AND o.deleted = 0
            """;

    private static final String SUMMARY_COLUMNS = """
            f.id, f.finance_state_id, f.transaction_id, f.operation_id, f.merchant_id,
            f.source_transaction_id, f.transaction_type, f.label_currency,
            o.label_amount AS label_amount,
            f.clearing_status, f.clearing_revision, f.clearing_retry_count,
            f.next_retry_time, f.last_failure_code, f.last_failure_message,
            f.fee_plan_id, f.fee_plan_version_id, f.fee_plan_version_no,
            f.gross_label_amount, f.fee_evaluation_status, f.settlement_status,
            f.settlement_currency, f.settlement_eligible_date, f.platform_fee_amount,
            f.fee_reversal_amount, f.reserve_amount, f.reserve_reversal_amount,
            f.expected_reserve_release_date, f.transaction_date_time,
            f.transaction_utc_time, f.transaction_time_zone, f.version
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionLogicalReadExecutor transactionLogicalReadExecutor;
    private final int maxResultRows;

    /** 创建生产查询服务，并继承交易查询超时和结果预算。 */
    @Autowired
    public JdbcAdminClearingQueryService(DataSource dataSource,
                                         TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                         TransactionShardingProperties shardingProperties,
                                         TransactionQueryJdbcTemplateFactory queryJdbcTemplateFactory) {
        this(queryJdbcTemplateFactory.create(dataSource, shardingProperties),
                transactionLogicalReadExecutor, shardingProperties);
    }

    /** 创建可注入 JDBC 模板的查询服务，供聚焦测试验证 SQL 和路由。 */
    public JdbcAdminClearingQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                         TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                         TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionLogicalReadExecutor = transactionLogicalReadExecutor;
        this.maxResultRows = shardingProperties.getQueryBudget().getMaxResultRows();
    }

    /** 在 transaction 普通读作用域执行标准分页，稳定按交易时间和主键倒序。 */
    @Override
    public PageResult<Summary> search(SearchRequest request) {
        SearchRequest query = normalize(request);
        return transactionLogicalReadExecutor.read(() -> searchNormalized(query));
    }

    /** 使用动作号和真实分片时间读取当前修订，避免交易分表广播。 */
    @Override
    public DetailResponse detail(String transactionId, LocalDateTime transactionDateTime) {
        if (!StringUtils.hasText(transactionId) || transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        String normalizedId = transactionId.trim();
        return transactionLogicalReadExecutor.read(
                () -> detailNormalized(normalizedId, transactionDateTime));
    }

    /** 使用复合身份一次读取批量目标，实际重算仍由清分服务逐笔执行 CAS。 */
    @Override
    public List<Summary> findByReferences(List<RecalculateBatchItem> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        StringJoiner predicates = new StringJoiner(" OR ", "(", ")");
        for (int index = 0; index < references.size(); index++) {
            RecalculateBatchItem reference = references.get(index);
            if (reference == null || !StringUtils.hasText(reference.getTransactionId())
                    || reference.getTransactionDateTime() == null) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID);
            }
            String transactionIdKey = "transactionId" + index;
            String transactionTimeKey = "transactionDateTime" + index;
            predicates.add("(f.transaction_id = :" + transactionIdKey
                    + " AND f.transaction_date_time = :" + transactionTimeKey + ")");
            parameters.addValue(transactionIdKey, reference.getTransactionId().trim());
            parameters.addValue(transactionTimeKey, reference.getTransactionDateTime());
        }
        return transactionLogicalReadExecutor.read(() -> jdbcTemplate.query(
                "SELECT " + SUMMARY_COLUMNS + SUMMARY_FROM_SQL
                        + "WHERE f.deleted = 0 AND " + predicates + "\n"
                        + "ORDER BY f.transaction_date_time DESC, f.id DESC",
                parameters, BeanPropertyRowMapper.newInstance(Summary.class)));
    }

    private PageResult<Summary> searchNormalized(SearchRequest query) {
        String whereSql = whereSql(query);
        MapSqlParameterSource parameters = parameters(query);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) " + FINANCE_FROM_SQL + whereSql,
                parameters, Long.class);
        long total = count == null ? 0L : count;
        long pageNo = query.getPageNo();
        long pageSize = query.getPageSize();
        long offset = (pageNo - 1L) * pageSize;
        List<Summary> records = offset < total
                ? jdbcTemplate.query(
                        "SELECT " + SUMMARY_COLUMNS + SUMMARY_FROM_SQL + whereSql + """
                                ORDER BY f.transaction_date_time DESC, f.id DESC
                                LIMIT :offset, :limit
                                """,
                        new MapSqlParameterSource(parameters.getValues())
                                .addValue("offset", offset)
                                .addValue("limit", pageSize),
                        BeanPropertyRowMapper.newInstance(Summary.class))
                : List.of();
        return PageResult.of(total, pageNo, pageSize, records);
    }

    private DetailResponse detailNormalized(String transactionId,
                                            LocalDateTime transactionDateTime) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("transactionId", transactionId)
                .addValue("transactionDateTime", transactionDateTime);
        List<Summary> summaries = jdbcTemplate.query(
                "SELECT " + SUMMARY_COLUMNS + SUMMARY_FROM_SQL + """
                        WHERE f.transaction_id = :transactionId
                          AND f.transaction_date_time = :transactionDateTime
                          AND f.deleted = 0
                        LIMIT 1
                        """,
                parameters, BeanPropertyRowMapper.newInstance(Summary.class));
        if (summaries.isEmpty()) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        Summary summary = summaries.get(0);
        DetailResponse response = new DetailResponse();
        response.setSummary(summary);
        if (summary.getClearingRevision() == null || summary.getClearingRevision() < 1) {
            return response;
        }
        parameters.addValue("clearingRevision", summary.getClearingRevision());
        response.setTransactionDetails(jdbcTemplate.query("""
                SELECT clearing_detail_no, clearing_revision, line_no, item_type,
                       fee_category, risk_service_type, item_code, item_name, direction,
                       label_currency, label_amount, component_type, basis_currency,
                       basis_amount, amount, currency, currency_exponent, percentage_rate,
                       fixed_amount_usd, minimum_amount_usd, maximum_amount_usd,
                       limit_evaluation_status, applied_limit, formula_snapshot, record_status
                FROM transaction_clearing_detail
                WHERE transaction_id = :transactionId
                  AND transaction_date_time = :transactionDateTime
                  AND clearing_revision = :clearingRevision
                  AND record_status = 'ACTIVE'
                ORDER BY line_no ASC, id ASC
                """, parameters, BeanPropertyRowMapper.newInstance(TransactionLine.class)));
        response.setReserveDetails(jdbcTemplate.query("""
                SELECT reserve_clearing_detail_no, original_transaction_id,
                       source_reserve_detail_no, clearing_revision, line_no,
                       reserve_action_type, item_code, item_name, direction, reserve_currency,
                       reserve_currency_exponent, basis_amount, reserve_rate, retained_amount,
                       returned_amount, released_amount, adjustment_amount, remaining_amount,
                       expected_reserve_release_date, formula_snapshot, record_status
                FROM transaction_reserve_clearing_detail
                WHERE transaction_id = :transactionId
                  AND transaction_date_time = :transactionDateTime
                  AND clearing_revision = :clearingRevision
                  AND record_status = 'ACTIVE'
                ORDER BY line_no ASC, id ASC
                """, parameters, BeanPropertyRowMapper.newInstance(ReserveLine.class)));
        return response;
    }

    private SearchRequest normalize(SearchRequest request) {
        if (request == null || request.getBeginTime() == null || request.getEndTime() == null
                || !request.getBeginTime().isBefore(request.getEndTime())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime lastIncluded = request.getEndTime().minusNanos(1);
        if (quarterKey(request.getBeginTime()) != quarterKey(lastIncluded)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        int pageNo = request.getPageNo() == null ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
        if (pageNo < 1 || pageSize < 1 || pageSize > maxResultRows) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        request.setMerchantId(trim(request.getMerchantId()));
        request.setTransactionId(trim(request.getTransactionId()));
        request.setClearingStatus(normalizedStatus(request.getClearingStatus()));
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        return request;
    }

    private String whereSql(SearchRequest query) {
        StringBuilder sql = new StringBuilder("""
                WHERE f.transaction_date_time >= :beginTime
                  AND f.transaction_date_time < :endTime
                  AND f.deleted = 0
                """);
        if (query.getMerchantId() != null) {
            sql.append(" AND f.merchant_id = :merchantId\n");
        }
        if (query.getTransactionId() != null) {
            sql.append(" AND f.transaction_id = :transactionId\n");
        }
        if (query.getClearingStatus() != null) {
            sql.append(" AND f.clearing_status = :clearingStatus\n");
        }
        return sql.toString();
    }

    private MapSqlParameterSource parameters(SearchRequest query) {
        return new MapSqlParameterSource()
                .addValue("beginTime", query.getBeginTime())
                .addValue("endTime", query.getEndTime())
                .addValue("merchantId", query.getMerchantId())
                .addValue("transactionId", query.getTransactionId())
                .addValue("clearingStatus", query.getClearingStatus());
    }

    private int quarterKey(LocalDateTime value) {
        return value.getYear() * 10 + (value.getMonthValue() - 1) / 3 + 1;
    }

    private String normalizedStatus(String value) {
        String normalized = trim(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!CLEARING_STATUSES.contains(normalized)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return normalized;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
