package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.NetPosting;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.OperationalState;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.RateLine;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultSummaryLine;
import com.scott.payment.admin.service.AdminSettlementQueryService;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminSettlementQueryService
 * @date : 2026-08-27 14:10
 * @email : scott_x@163.com
 * @description : Admin 结算 JDBC 查询实现；在 transaction 普通读作用域完成标准分页和有界详情装配。
 * @status : create
 */
@Service
public class JdbcAdminSettlementQueryService implements AdminSettlementQueryService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final long MAX_DATE_SPAN_DAYS = 92;
    private static final Set<String> BATCH_TYPES = Set.of(
            "REGULAR", "RESERVE_RELEASE", "REVERSAL", "ADJUSTMENT");
    private static final Set<String> BATCH_STATUSES = Set.of(
            "CREATED", "CLAIMING", "CLAIMED", "RATE_LOCKED", "CALCULATING",
            "CALCULATED", "POSTING", "POSTED", "FAILED_RETRYABLE", "MANUAL_REVIEW",
            "CANCELLED", "REVERSING", "REVERSED");

    private static final String BATCH_COLUMNS = """
            id, settlement_batch_no, business_date, business_time_zone, daily_sequence,
            merchant_id, settlement_profile_id, settlement_account_id, target_currency,
            target_currency_exponent, batch_type, original_batch_no, batch_status,
            candidate_count, retry_count, last_failure_stage, last_failure_code,
            last_failure_message, rate_locked_time, calculated_time, posted_time,
            cancelled_time, version, create_time, update_time
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionLogicalReadExecutor transactionLogicalReadExecutor;
    private final int maxResultRows;

    /** 创建生产查询服务，并继承交易查询超时与结果预算。 */
    @Autowired
    public JdbcAdminSettlementQueryService(DataSource dataSource,
                                           TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                           TransactionShardingProperties shardingProperties,
                                           TransactionQueryJdbcTemplateFactory queryJdbcTemplateFactory) {
        this(queryJdbcTemplateFactory.create(dataSource, shardingProperties),
                transactionLogicalReadExecutor, shardingProperties);
    }

    /** 创建可注入 JDBC 模板的查询服务，供聚焦测试验证 SQL 和路由。 */
    public JdbcAdminSettlementQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                           TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                           TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionLogicalReadExecutor = transactionLogicalReadExecutor;
        this.maxResultRows = shardingProperties.getQueryBudget().getMaxResultRows();
    }

    /** 在 transaction 普通读作用域按业务日期和主键稳定倒序分页。 */
    @Override
    public PageResult<BatchSummary> search(BatchSearchRequest request) {
        BatchSearchRequest query = normalize(request);
        return transactionLogicalReadExecutor.read(() -> searchNormalized(query));
    }

    /** 按全局唯一批次号读取运营详情，不获取资金写锁。 */
    @Override
    public BatchDetailResponse detail(String settlementBatchNo) {
        String batchNo = requiredBatchNo(settlementBatchNo);
        return transactionLogicalReadExecutor.read(() -> detailNormalized(batchNo));
    }

    private PageResult<BatchSummary> searchNormalized(BatchSearchRequest query) {
        String whereSql = whereSql(query);
        MapSqlParameterSource parameters = parameters(query);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM settlement_batch " + whereSql,
                parameters, Long.class);
        long total = count == null ? 0L : count;
        long pageNo = query.getPageNo();
        long pageSize = query.getPageSize();
        long offset = (pageNo - 1L) * pageSize;
        List<BatchSummary> records = offset < total
                ? jdbcTemplate.query("SELECT " + BATCH_COLUMNS + """
                                FROM settlement_batch
                                """ + whereSql + """
                                ORDER BY business_date DESC, id DESC
                                LIMIT :offset, :limit
                                """,
                        new MapSqlParameterSource(parameters.getValues())
                                .addValue("offset", offset)
                                .addValue("limit", pageSize),
                        BeanPropertyRowMapper.newInstance(BatchSummary.class))
                : List.of();
        records.forEach(this::populateDisplayBatchNo);
        return PageResult.of(total, pageNo, pageSize, records);
    }

    private BatchDetailResponse detailNormalized(String settlementBatchNo) {
        MapSqlParameterSource parameters = new MapSqlParameterSource(
                "settlementBatchNo", settlementBatchNo);
        List<BatchSummary> batches = jdbcTemplate.query(
                "SELECT " + BATCH_COLUMNS + """
                        FROM settlement_batch
                        WHERE settlement_batch_no = :settlementBatchNo
                        LIMIT 1
                        """, parameters, BeanPropertyRowMapper.newInstance(BatchSummary.class));
        if (batches.isEmpty()) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        BatchSummary batch = batches.get(0);
        populateDisplayBatchNo(batch);
        BatchDetailResponse response = new BatchDetailResponse();
        response.setBatch(batch);
        response.setRates(jdbcTemplate.query("""
                SELECT id, source_currency, target_currency, rate_type, direct_rate,
                       source_currency_exponent, target_currency_exponent, rate_source,
                       quote_id, source_quote_direction, effective_time, locked_time, locked_by
                FROM settlement_batch_rate
                WHERE settlement_batch_no = :settlementBatchNo
                  AND rate_type = 'SETTLEMENT'
                  AND rate_status = 'LOCKED'
                ORDER BY source_currency ASC, id ASC
                """, parameters, BeanPropertyRowMapper.newInstance(RateLine.class)));
        response.setResultSummaries(jdbcTemplate.query("""
                SELECT payment_type, payment_method, transaction_type, result_item_type,
                       fee_category, direction, source_currency, target_currency,
                       transaction_count, source_amount, target_amount
                FROM settlement_result_summary
                WHERE settlement_batch_no = :settlementBatchNo
                ORDER BY payment_type, payment_method, transaction_type, result_item_type,
                         fee_category, direction, source_currency, target_currency, id
                """, parameters, BeanPropertyRowMapper.newInstance(ResultSummaryLine.class)));
        List<NetPosting> postings = jdbcTemplate.query("""
                SELECT id, settlement_result_item_no, reversal_of_result_item_id, direction,
                       target_amount, target_currency, target_currency_exponent,
                       ledger_idempotency_key, formula_snapshot, create_time
                FROM settlement_result_item
                WHERE settlement_batch_no = :settlementBatchNo
                  AND result_role = 'LEDGER_POSTING'
                  AND result_item_type = 'NET_SETTLEMENT'
                LIMIT 1
                """, parameters, BeanPropertyRowMapper.newInstance(NetPosting.class));
        response.setNetPosting(postings.isEmpty() ? null : postings.get(0));
        OperationalState state = jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(1) FROM settlement_projection_task
                     WHERE settlement_batch_no = :settlementBatchNo) AS projection_task_count,
                    (SELECT COUNT(1) FROM settlement_projection_task
                     WHERE settlement_batch_no = :settlementBatchNo AND task_status = 'COMPLETED')
                        AS projection_completed_count,
                    (SELECT COUNT(1) FROM settlement_projection_task
                     WHERE settlement_batch_no = :settlementBatchNo AND task_status = 'FAILED')
                        AS projection_failed_count,
                    (SELECT COUNT(1) FROM settlement_event_outbox
                     WHERE settlement_batch_no = :settlementBatchNo) AS outbox_event_count,
                    (SELECT COUNT(1) FROM settlement_event_outbox
                     WHERE settlement_batch_no = :settlementBatchNo AND event_status = 'SENT')
                        AS outbox_sent_count,
                    (SELECT COUNT(1) FROM settlement_event_outbox
                     WHERE settlement_batch_no = :settlementBatchNo AND event_status = 'FAILED')
                        AS outbox_failed_count
                """, parameters, BeanPropertyRowMapper.newInstance(OperationalState.class));
        response.setOperationalState(state == null ? emptyOperationalState() : state);
        return response;
    }

    private BatchSearchRequest normalize(BatchSearchRequest request) {
        if (request == null || request.getBeginBusinessDate() == null
                || request.getEndBusinessDate() == null
                || request.getBeginBusinessDate().isAfter(request.getEndBusinessDate())
                || ChronoUnit.DAYS.between(request.getBeginBusinessDate(),
                request.getEndBusinessDate()) > MAX_DATE_SPAN_DAYS) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        int pageNo = request.getPageNo() == null ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
        if (pageNo < 1 || pageSize < 1 || pageSize > maxResultRows) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        request.setSettlementBatchNo(trim(request.getSettlementBatchNo()));
        if (request.getSettlementBatchNo() != null) {
            requiredBatchNo(request.getSettlementBatchNo());
        }
        request.setMerchantId(trim(request.getMerchantId()));
        if (request.getMerchantId() != null && request.getMerchantId().length() > 64) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        request.setBatchType(normalizedEnum(request.getBatchType(), BATCH_TYPES));
        request.setBatchStatus(normalizedEnum(request.getBatchStatus(), BATCH_STATUSES));
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        return request;
    }

    private String whereSql(BatchSearchRequest query) {
        StringBuilder sql = new StringBuilder("""
                WHERE business_date BETWEEN :beginBusinessDate AND :endBusinessDate
                """);
        if (query.getSettlementBatchNo() != null) {
            sql.append(" AND settlement_batch_no = :settlementBatchNo\n");
        }
        if (query.getMerchantId() != null) {
            sql.append(" AND merchant_id = :merchantId\n");
        }
        if (query.getBatchType() != null) {
            sql.append(" AND batch_type = :batchType\n");
        }
        if (query.getBatchStatus() != null) {
            sql.append(" AND batch_status = :batchStatus\n");
        }
        return sql.toString();
    }

    private MapSqlParameterSource parameters(BatchSearchRequest query) {
        return new MapSqlParameterSource()
                .addValue("beginBusinessDate", query.getBeginBusinessDate())
                .addValue("endBusinessDate", query.getEndBusinessDate())
                .addValue("settlementBatchNo", query.getSettlementBatchNo())
                .addValue("merchantId", query.getMerchantId())
                .addValue("batchType", query.getBatchType())
                .addValue("batchStatus", query.getBatchStatus());
    }

    private void populateDisplayBatchNo(BatchSummary batch) {
        LocalDate date = batch.getBusinessDate();
        Integer sequence = batch.getDailySequence();
        if (date != null && sequence != null && sequence > 0) {
            batch.setDisplayBatchNo(date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    + " " + String.format("%08d", sequence));
        }
    }

    private OperationalState emptyOperationalState() {
        OperationalState state = new OperationalState();
        state.setProjectionTaskCount(0L);
        state.setProjectionCompletedCount(0L);
        state.setProjectionFailedCount(0L);
        state.setOutboxEventCount(0L);
        state.setOutboxSentCount(0L);
        state.setOutboxFailedCount(0L);
        return state;
    }

    private String requiredBatchNo(String value) {
        String batchNo = trim(value);
        if (batchNo == null || !batchNo.matches("SB\\d{8}-\\d{8}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return batchNo;
    }

    private String normalizedEnum(String value, Set<String> values) {
        String normalized = trim(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!values.contains(normalized)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return normalized;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
