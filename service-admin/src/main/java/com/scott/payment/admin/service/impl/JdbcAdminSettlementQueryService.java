package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.NetPosting;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.OperationalState;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.RateLine;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultSummaryLine;
import com.scott.payment.admin.service.AdminSettlementQueryService;
import com.scott.payment.admin.service.AdminMerchantDataScope;
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
     * {@code MAX_DATE_SPAN_DAYS}常量，统一 {@code JdbcAdminSettlementQueryService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
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
            target_currency_exponent, batch_type, original_batch_no, review_order_no, create_mode,
            batch_status,
            (SELECT COUNT(DISTINCT item.source_transaction_id)
             FROM settlement_result_item item
             WHERE item.settlement_batch_no = settlement_batch.settlement_batch_no
               AND item.source_transaction_id IS NOT NULL) AS transaction_count,
            candidate_count, projectable_candidate_count,
            (SELECT item.direction
             FROM settlement_result_item item
             WHERE item.settlement_batch_no = settlement_batch.settlement_batch_no
               AND item.result_role = 'LEDGER_POSTING'
               AND item.result_item_type = 'NET_SETTLEMENT'
             LIMIT 1) AS net_direction,
            (SELECT item.target_amount
             FROM settlement_result_item item
             WHERE item.settlement_batch_no = settlement_batch.settlement_batch_no
               AND item.result_role = 'LEDGER_POSTING'
               AND item.result_item_type = 'NET_SETTLEMENT'
             LIMIT 1) AS net_amount,
            result_fingerprint,
            maker_account_id, maker_account_name, maker_reason, maker_time,
            checker_account_id, checker_account_name, checker_comment, checker_time,
            retry_count, last_failure_stage, last_failure_code,
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

    /** {@inheritDoc} 本实现只在 transaction 普通读作用域按业务日期和主键稳定倒序分页。 */
    @Override
    public PageResult<BatchSummary> search(BatchSearchRequest request,
                                           AdminMerchantDataScope dataScope) {
        BatchSearchRequest query = normalize(request);
        AdminMerchantDataScope scope = requiredScope(dataScope);
        return transactionLogicalReadExecutor.read(() -> searchNormalized(query, scope));
    }

    /** {@inheritDoc} 本实现按全局唯一批次号读取运营详情，不获取资金写锁。 */
    @Override
    public BatchDetailResponse detail(String settlementBatchNo,
                                      AdminMerchantDataScope dataScope) {
        String batchNo = requiredBatchNo(settlementBatchNo);
        AdminMerchantDataScope scope = requiredScope(dataScope);
        return transactionLogicalReadExecutor.read(() -> detailNormalized(batchNo, scope));
    }

    /** {@inheritDoc} */
    @Override
    public void requireBatchAccess(String settlementBatchNo, AdminMerchantDataScope dataScope) {
        String batchNo = requiredBatchNo(settlementBatchNo);
        AdminMerchantDataScope scope = requiredScope(dataScope);
        transactionLogicalReadExecutor.read(() -> {
            requireAccessibleBatch(batchNo, scope);
            return null;
        });
    }

    private PageResult<BatchSummary> searchNormalized(BatchSearchRequest query,
                                                      AdminMerchantDataScope dataScope) {
        if (dataScope.empty()) {
            return PageResult.of(0L, query.getPageNo(), query.getPageSize(), List.of());
        }
        String whereSql = whereSql(query, dataScope);
        MapSqlParameterSource parameters = parameters(query, dataScope);
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

    private BatchDetailResponse detailNormalized(String settlementBatchNo,
                                                 AdminMerchantDataScope dataScope) {
        if (dataScope.empty()) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource(
                "settlementBatchNo", settlementBatchNo)
                .addValue("permittedMerchantIds", dataScope.merchantIds());
        List<BatchSummary> batches = jdbcTemplate.query(
                "SELECT " + BATCH_COLUMNS + """
                        FROM settlement_batch
                        WHERE settlement_batch_no = :settlementBatchNo
                        """ + merchantScopeSql(dataScope) + """
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
                SELECT summary.payment_type, summary.payment_method, summary.transaction_type,
                       summary.result_item_type, summary.fee_category, summary.direction,
                       summary.source_currency,
                       COALESCE(NULLIF(source_currency.fraction_digits, -1), 2)
                           AS source_currency_exponent,
                       summary.target_currency, batch.target_currency_exponent,
                       summary.transaction_count, summary.source_amount, summary.target_amount
                FROM settlement_result_summary summary
                JOIN settlement_batch batch
                  ON batch.settlement_batch_no = summary.settlement_batch_no
                LEFT JOIN base_iso_currency source_currency
                  ON source_currency.alpha3_code = summary.source_currency
                 AND source_currency.deleted = 0
                WHERE summary.settlement_batch_no = :settlementBatchNo
                ORDER BY summary.payment_type, summary.payment_method, summary.transaction_type,
                         summary.result_item_type, summary.fee_category, summary.direction,
                         summary.source_currency, summary.target_currency, summary.id
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

    /**
     * 在本地 transaction 数据源验证批次属于当前数据范围；越权与不存在统一返回资源不存在。
     *
     * @param settlementBatchNo 正式结算批次号
     * @param dataScope 当前 Admin 商户数据范围
     */
    private void requireAccessibleBatch(String settlementBatchNo,
                                        AdminMerchantDataScope dataScope) {
        if (dataScope.empty()) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource(
                "settlementBatchNo", settlementBatchNo)
                .addValue("permittedMerchantIds", dataScope.merchantIds());
        List<String> merchants = jdbcTemplate.queryForList("""
                SELECT merchant_id
                FROM settlement_batch
                WHERE settlement_batch_no = :settlementBatchNo
                """ + merchantScopeSql(dataScope) + """
                LIMIT 1
                """, parameters, String.class);
        if (merchants.isEmpty()) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
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

    private String whereSql(BatchSearchRequest query, AdminMerchantDataScope dataScope) {
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
        sql.append(merchantScopeSql(dataScope));
        return sql.toString();
    }

    private MapSqlParameterSource parameters(BatchSearchRequest query,
                                             AdminMerchantDataScope dataScope) {
        return new MapSqlParameterSource()
                .addValue("beginBusinessDate", query.getBeginBusinessDate())
                .addValue("endBusinessDate", query.getEndBusinessDate())
                .addValue("settlementBatchNo", query.getSettlementBatchNo())
                .addValue("merchantId", query.getMerchantId())
                .addValue("batchType", query.getBatchType())
                .addValue("batchStatus", query.getBatchStatus())
                .addValue("permittedMerchantIds", dataScope.merchantIds());
    }

    /** @return 全商户时为空，否则返回使用参数化 merchantId 集合的数据范围谓词。 */
    private String merchantScopeSql(AdminMerchantDataScope dataScope) {
        return dataScope.allMerchants() ? "" : " AND merchant_id IN (:permittedMerchantIds)\n";
    }

    /** @return 非空可信数据范围；缺失上下文时按未授权拒绝。 */
    private AdminMerchantDataScope requiredScope(AdminMerchantDataScope dataScope) {
        if (dataScope == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return dataScope;
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
