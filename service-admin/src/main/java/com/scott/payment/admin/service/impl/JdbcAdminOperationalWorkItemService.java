package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.config.AdminOperationalWorkItemProperties;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.ActionRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.AssigneeOption;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchResponse;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SyncRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SyncResponse;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.WorkItemDetail;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.WorkItemHistory;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.WorkItemMetrics;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.WorkItemSummary;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminOperationalWorkItemService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.RiskAuditLogicalReadExecutor;
import com.scott.payment.component.db.sharding.RiskAuditShardingProperties;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 统一运营待办 JDBC 实现。
 *
 * <p>运营表只保存负责人、SLA、状态和轨迹；退款、结算、汇率、通知、渠道异常、清分、结算异常
 * 与高风险事件事实始终实时读取来源表。</p>
 */
@Slf4j
@Service
public class JdbcAdminOperationalWorkItemService implements AdminOperationalWorkItemService {

    private static final String REFUND_APPROVAL = "REFUND_APPROVAL";
    private static final String SETTLEMENT_REVIEW = "SETTLEMENT_REVIEW";
    private static final String EXCHANGE_RATE_ALERT = "EXCHANGE_RATE_ALERT";
    private static final String MERCHANT_NOTIFICATION_FAILURE = "MERCHANT_NOTIFICATION_FAILURE";
    private static final String CHANNEL_MATCH_ABNORMAL = "CHANNEL_MATCH_ABNORMAL";
    private static final String CLEARING_BACKLOG = "CLEARING_BACKLOG";
    private static final String SETTLEMENT_BACKLOG = "SETTLEMENT_BACKLOG";
    private static final String HIGH_RISK_EVENT = "HIGH_RISK_EVENT";
    private static final String ADMIN_ACCOUNT_PREFIX = "admin-account:";
    private static final String OPEN = "OPEN";
    private static final String PROCESSING = "PROCESSING";
    private static final String CLOSED = "CLOSED";
    private static final Set<String> SOURCE_TYPES = Set.of(
            REFUND_APPROVAL, SETTLEMENT_REVIEW, EXCHANGE_RATE_ALERT,
            MERCHANT_NOTIFICATION_FAILURE, CHANNEL_MATCH_ABNORMAL, CLEARING_BACKLOG,
            SETTLEMENT_BACKLOG, HIGH_RISK_EVENT);
    private static final Set<String> WORK_STATUSES = Set.of(OPEN, PROCESSING, CLOSED);
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "NORMAL", "LOW");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_HISTORY_PAGE_SIZE = 10;
    private static final int SOURCE_PROJECTION_VERSION = 1;
    private static final int KEY_BATCH_SIZE = 500;
    private static final String WORK_METADATA_COLUMNS = """
            id, work_item_no, source_type, source_key, work_status, priority,
            owner_account_id, owner_account_name, sla_due_at, first_detected_at,
            last_synced_at, closed_at, close_reason, latest_remark, version,
            source_projection_version, merchant_id, merchant_name, business_no,
            related_business_no, transaction_id, transaction_date_time,
            source_occurred_at, create_time, update_time
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionLogicalReadExecutor transactionReadExecutor;
    private final RiskAuditLogicalReadExecutor riskAuditReadExecutor;
    private final RiskAuditShardingProperties riskAuditShardingProperties;
    private final AdminOperationalWorkItemProperties properties;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建同时访问管理主库和交易逻辑只读数据源的待办服务。
     *
     * @param jdbcTemplate 管理主库访问组件
     * @param transactionReadExecutor 交易逻辑分片只读执行器
     * @param riskAuditReadExecutor 风控审计逻辑分片只读执行器
     * @param riskAuditShardingProperties 风控审计分片运行配置
     * @param properties 统一待办配置
     * @param transactionManager 管理主库事务管理器
     */
    public JdbcAdminOperationalWorkItemService(NamedParameterJdbcTemplate jdbcTemplate,
                                               TransactionLogicalReadExecutor transactionReadExecutor,
                                               RiskAuditLogicalReadExecutor riskAuditReadExecutor,
                                               RiskAuditShardingProperties riskAuditShardingProperties,
                                               AdminOperationalWorkItemProperties properties,
                                               PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionReadExecutor = transactionReadExecutor;
        this.riskAuditReadExecutor = riskAuditReadExecutor;
        this.riskAuditShardingProperties = riskAuditShardingProperties;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 查询经过来源事实和商户数据范围双重校验的待办分页。
     */
    @Override
    public SearchResponse search(SearchRequest request,
                                 AdminMerchantDataScope dataScope,
                                 Long currentAccountId) {
        SearchRequest query = normalize(request);
        return searchPage(query, requireScope(dataScope), currentAccountId);
    }

    /**
     * 导出完整可访问结果，仍受统一查询预算和商户数据范围保护。
     */
    @Override
    public List<WorkItemSummary> listForExport(SearchRequest request,
                                               AdminMerchantDataScope dataScope,
                                               Long currentAccountId) {
        SearchRequest query = normalize(request);
        AdminMerchantDataScope scope = requireScope(dataScope);
        query.setPageNo(1);
        query.setPageSize(MAX_PAGE_SIZE);
        SearchResponse firstPage = searchPage(query, scope, currentAccountId);
        long total = firstPage.getPage().getTotal();
        if (total > properties.getMaxQueryRows()) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(),
                    "work item export exceeds the configured result budget");
        }
        List<WorkItemSummary> rows = new ArrayList<>(firstPage.getPage().getRecords());
        int pageCount = (int) ((total + MAX_PAGE_SIZE - 1L) / MAX_PAGE_SIZE);
        for (int pageNo = 2; pageNo <= pageCount; pageNo++) {
            query.setPageNo(pageNo);
            rows.addAll(searchPage(query, scope, currentAccountId).getPage().getRecords());
        }
        return rows;
    }

    /**
     * 按调用方给定页码读取异步导出数据，复用列表数据库分页和来源事实复核。
     */
    @Override
    public PageResult<WorkItemSummary> pageForExport(SearchRequest request,
                                                     AdminMerchantDataScope dataScope,
                                                     Long currentAccountId) {
        SearchRequest query = normalize(request);
        if (query.getPageSize() > MAX_PAGE_SIZE) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return searchPage(query, requireScope(dataScope), currentAccountId).getPage();
    }

    /**
     * 查询来源实时摘要和待办完整处理轨迹。
     */
    @Override
    public WorkItemDetail detail(String workItemNo, AdminMerchantDataScope dataScope) {
        WorkMetadata metadata = requiredMetadata(workItemNo, false);
        SourceSnapshot source = loadSingleSource(metadata, requireScope(dataScope));
        if (source == null && !dataScope.allMerchants() && !EXCHANGE_RATE_ALERT.equals(metadata.sourceType())) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND);
        }
        WorkItemDetail detail = new WorkItemDetail();
        detail.setWorkItem(toSummary(metadata, source));
        PageResult<WorkItemHistory> history = loadHistoryPage(
                metadata.id(), 1, DEFAULT_HISTORY_PAGE_SIZE);
        detail.setHistory(history.getRecords());
        detail.setHistoryTotal(history.getTotal());
        return detail;
    }

    /**
     * 在校验待办详情访问范围后分页读取处理轨迹。
     */
    @Override
    public PageResult<WorkItemHistory> searchHistory(String workItemNo,
                                                     Integer pageNo,
                                                     Integer pageSize,
                                                     AdminMerchantDataScope dataScope) {
        WorkMetadata metadata = requiredMetadata(workItemNo, false);
        AdminMerchantDataScope scope = requireScope(dataScope);
        SourceSnapshot source = loadSingleSource(metadata, scope);
        if (source == null && !scope.allMerchants() && !isSystemSource(metadata.sourceType())) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND);
        }
        int normalizedPageNo = pageNo == null ? 1 : pageNo;
        int normalizedPageSize = pageSize == null ? DEFAULT_HISTORY_PAGE_SIZE : pageSize;
        if (normalizedPageNo < 1 || normalizedPageSize < 1 || normalizedPageSize > MAX_PAGE_SIZE) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return loadHistoryPage(metadata.id(), normalizedPageNo, normalizedPageSize);
    }

    /**
     * 在人工命令前按来源事实重新校验当前操作人的商户数据范围。
     */
    @Override
    public void requireAccess(String workItemNo, AdminMerchantDataScope dataScope) {
        WorkMetadata metadata = requiredMetadata(workItemNo, false);
        SourceSnapshot source = loadSingleSource(metadata, requireScope(dataScope));
        if (source == null && !dataScope.allMerchants() && !EXCHANGE_RATE_ALERT.equals(metadata.sourceType())) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND);
        }
    }

    /**
     * 未分派活动待办由当前操作人认领。
     */
    @Override
    public WorkItemDetail claim(String workItemNo,
                                ActionRequest request,
                                Long operatorAccountId,
                                String operatorAccountName,
                                AdminMerchantDataScope dataScope) {
        requireAccess(workItemNo, dataScope);
        executeCommand(workItemNo, "CLAIM", requireAction(request), operatorAccountId,
                operatorAccountName, operatorAccountId, operatorAccountName);
        return detail(workItemNo, dataScope);
    }

    /**
     * 将活动待办分派给同一管理应用中的有效账号。
     */
    @Override
    public WorkItemDetail assign(String workItemNo,
                                 ActionRequest request,
                                 Long operatorAccountId,
                                 String operatorAccountName,
                                 Long appId,
                                 AdminMerchantDataScope dataScope) {
        requireAccess(workItemNo, dataScope);
        ActionRequest command = requireTarget(request);
        Assignee assignee = requiredAssignee(appId, command.getTargetAccountId());
        executeCommand(workItemNo, "ASSIGN", command, operatorAccountId,
                operatorAccountName, assignee.accountId(), assignee.accountName());
        return detail(workItemNo, dataScope);
    }

    /**
     * 将已分派活动待办转交给其他有效管理账号。
     */
    @Override
    public WorkItemDetail transfer(String workItemNo,
                                   ActionRequest request,
                                   Long operatorAccountId,
                                   String operatorAccountName,
                                   Long appId,
                                   AdminMerchantDataScope dataScope) {
        requireAccess(workItemNo, dataScope);
        ActionRequest command = requireTarget(request);
        Assignee assignee = requiredAssignee(appId, command.getTargetAccountId());
        executeCommand(workItemNo, "TRANSFER", command, operatorAccountId,
                operatorAccountName, assignee.accountId(), assignee.accountName());
        return detail(workItemNo, dataScope);
    }

    /**
     * 人工完成活动待办，处理结论、操作人和完成时间必须完整留痕。
     */
    @Override
    public WorkItemDetail complete(String workItemNo,
                                   ActionRequest request,
                                   Long operatorAccountId,
                                   String operatorAccountName,
                                   AdminMerchantDataScope dataScope) {
        requireAccess(workItemNo, dataScope);
        ActionRequest command = requireAction(request);
        if (!StringUtils.hasText(command.getRemark())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "remark must not be blank");
        }
        executeCommand(workItemNo, "COMPLETE", command, operatorAccountId,
                operatorAccountName, null, null);
        return detail(workItemNo, dataScope);
    }

    /**
     * 追加人工处理备注并递增版本，避免并发备注覆盖。
     */
    @Override
    public WorkItemDetail remark(String workItemNo,
                                 ActionRequest request,
                                 Long operatorAccountId,
                                 String operatorAccountName,
                                 AdminMerchantDataScope dataScope) {
        requireAccess(workItemNo, dataScope);
        ActionRequest command = requireAction(request);
        if (!StringUtils.hasText(command.getRemark())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "remark must not be blank");
        }
        executeCommand(workItemNo, "REMARK", command, operatorAccountId,
                operatorAccountName, null, null);
        return detail(workItemNo, dataScope);
    }

    /**
     * 查询当前管理应用中可接收待办的激活账号。
     */
    @Override
    public List<AssigneeOption> listAssignees(Long appId, String keyword) {
        if (appId == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("appId", appId)
                .addValue("keyword", normalizedKeyword == null ? null : "%" + normalizedKeyword + "%");
        String keywordSql = normalizedKeyword == null ? "" : """
                AND (account.login_account LIKE :keyword OR user.real_name LIKE :keyword)
                """;
        return jdbcTemplate.query("""
                SELECT account.id AS account_id, account.login_account,
                       COALESCE(NULLIF(user.real_name, ''), account.login_account) AS account_name
                FROM sys_account account
                JOIN sys_app app ON app.id = account.app_id AND app.deleted = 0
                LEFT JOIN sys_user user ON user.id = account.user_id AND user.deleted = 0
                WHERE account.app_id = :appId
                  AND app.app_code = 'ADMIN' AND app.status = 1
                  AND account.status = 1 AND account.locked = 0
                  AND account.activation_status = 'ACTIVE' AND account.deleted = 0
                  AND (user.id IS NULL OR user.status = 1)
                """ + keywordSql + " ORDER BY account_name ASC, account.id ASC LIMIT 200", params,
                (rs, rowNum) -> {
                    AssigneeOption option = new AssigneeOption();
                    option.setAccountId(rs.getLong("account_id"));
                    option.setAccountName(rs.getString("account_name"));
                    option.setLoginAccount(rs.getString("login_account"));
                    return option;
                });
    }

    /**
     * 同步七类来源的活动集合；活动集合完整读取成功后才允许自动关闭缺失来源，防止截断扫描误关闭。
     */
    @Override
    public SyncResponse synchronize(SyncRequest request) {
        if (request == null || !StringUtils.hasText(request.getRequestKey()) || request.getSyncTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        Map<SourceIdentity, SourceSnapshot> activeSources = loadActiveSources(request.getSyncTime());
        Map<SourceIdentity, WorkMetadata> existing = loadSynchronizationMetadata(activeSources.keySet());
        SyncCounters counters = transactionTemplate.execute(status ->
                applySynchronization(request, activeSources, existing));
        if (counters == null) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        SyncResponse response = new SyncResponse();
        response.setRequestKey(request.getRequestKey().trim());
        response.setScannedSourceCount(activeSources.size());
        response.setCreatedCount(counters.created());
        response.setReopenedCount(counters.reopened());
        response.setClosedCount(counters.closed());
        response.setRefreshedCount(counters.refreshed());
        response.setSyncTime(request.getSyncTime());
        log.info("统一运营待办同步完成, event: ADMIN_OPERATIONAL_WORK_ITEM_SYNC_COMPLETED, requestKey: {}, scannedSourceCount: {}, createdCount: {}, reopenedCount: {}, closedCount: {}, refreshedCount: {}",
                request.getRequestKey(), activeSources.size(), counters.created(), counters.reopened(),
                counters.closed(), counters.refreshed());
        return response;
    }

    /**
     * 使用运营检索投影完成数据库分页，再仅对当前页回查来源权威事实。
     */
    private SearchResponse searchPage(SearchRequest request,
                                      AdminMerchantDataScope dataScope,
                                      Long currentAccountId) {
        SearchSql searchSql = searchSql(request, dataScope, currentAccountId);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM admin_operational_work_item" + searchSql.whereClause(),
                searchSql.parameters(), Long.class);
        long total = count == null ? 0L : count;
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        List<WorkMetadata> metadataRows = offset >= total ? List.of() : jdbcTemplate.query(
                "SELECT " + WORK_METADATA_COLUMNS + " FROM admin_operational_work_item"
                        + searchSql.whereClause() + workItemOrderSql() + " LIMIT :offset, :limit",
                new MapSqlParameterSource(searchSql.parameters().getValues())
                        .addValue("offset", offset)
                        .addValue("limit", request.getPageSize()),
                this::mapMetadata);
        Map<SourceIdentity, SourceSnapshot> sources = loadSourceSnapshots(metadataRows, dataScope);
        List<WorkItemSummary> rows = new ArrayList<>(metadataRows.size());
        for (WorkMetadata metadata : metadataRows) {
            SourceSnapshot source = sources.get(metadata.identity());
            if (!sourceWithinScope(metadata, source, dataScope)) {
                log.error("统一待办来源归属校验失败, event: ADMIN_OPERATIONAL_WORK_ITEM_SOURCE_SCOPE_MISMATCH, workItemNo: {}, sourceType: {}, projectionMerchantId: {}",
                        metadata.workItemNo(), metadata.sourceType(), metadata.merchantId());
                continue;
            }
            rows.add(toSummary(metadata, source));
        }

        SearchResponse response = new SearchResponse();
        response.setPage(PageResult.of(total, request.getPageNo(), request.getPageSize(), rows));
        response.setMetrics(loadMetrics(searchSql, currentAccountId));
        return response;
    }

    /**
     * 生成列表、总数和指标共用的过滤条件，避免三类查询口径漂移。
     */
    private SearchSql searchSql(SearchRequest request,
                                AdminMerchantDataScope dataScope,
                                Long currentAccountId) {
        StringBuilder where = new StringBuilder(" WHERE source_projection_version = :projectionVersion");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectionVersion", SOURCE_PROJECTION_VERSION);
        addEquals(where, params, "work_item_no", "workItemNo", request.getWorkItemNo());
        addEquals(where, params, "source_type", "sourceType", request.getSourceType());
        addEquals(where, params, "work_status", "workStatus", request.getWorkStatus());
        addEquals(where, params, "priority", "priority", request.getPriority());
        if (Boolean.TRUE.equals(request.getMineOnly())) {
            if (currentAccountId == null) {
                where.append(" AND 1 = 0");
            } else {
                where.append(" AND owner_account_id = :currentAccountId");
                params.addValue("currentAccountId", currentAccountId);
            }
        } else if (request.getOwnerAccountId() != null) {
            where.append(" AND owner_account_id = :ownerAccountId");
            params.addValue("ownerAccountId", request.getOwnerAccountId());
        }
        if (Boolean.TRUE.equals(request.getUnassignedOnly())) {
            where.append(" AND owner_account_id IS NULL");
        }
        if (Boolean.TRUE.equals(request.getOverdueOnly())) {
            where.append(" AND work_status <> 'CLOSED' AND sla_due_at < NOW(3)");
        }
        if (request.getDetectedStartTime() != null) {
            where.append(" AND first_detected_at >= :detectedStartTime");
            params.addValue("detectedStartTime", request.getDetectedStartTime());
        }
        if (request.getDetectedEndTime() != null) {
            where.append(" AND first_detected_at < :detectedEndTime");
            params.addValue("detectedEndTime", request.getDetectedEndTime());
        }
        addKeywordFilter(where, params, request.getKeyword());
        addDataScopeFilter(where, params, dataScope);
        return new SearchSql(where.toString(), params);
    }

    private void addKeywordFilter(StringBuilder where,
                                  MapSqlParameterSource params,
                                  String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        params.addValue("keyword", "%" + escaped + "%");
        where.append("""
                 AND (LOWER(work_item_no) LIKE :keyword ESCAPE '!'
                   OR LOWER(source_key) LIKE :keyword ESCAPE '!'
                   OR LOWER(COALESCE(merchant_id, '')) LIKE :keyword ESCAPE '!'
                   OR LOWER(COALESCE(merchant_name, '')) LIKE :keyword ESCAPE '!'
                   OR LOWER(COALESCE(business_no, '')) LIKE :keyword ESCAPE '!'
                   OR LOWER(COALESCE(related_business_no, '')) LIKE :keyword ESCAPE '!'
                   OR LOWER(COALESCE(transaction_id, '')) LIKE :keyword ESCAPE '!')
                """);
    }

    private void addDataScopeFilter(StringBuilder where,
                                    MapSqlParameterSource params,
                                    AdminMerchantDataScope dataScope) {
        if (dataScope.allMerchants()) {
            return;
        }
        params.addValue("systemSourceType", EXCHANGE_RATE_ALERT);
        where.append(" AND (source_type = :systemSourceType");
        if (!dataScope.merchantIds().isEmpty()) {
            where.append(" OR merchant_id IN (:scopeMerchantIds)");
            params.addValue("scopeMerchantIds", dataScope.merchantIds());
        }
        where.append(')');
    }

    private WorkItemMetrics loadMetrics(SearchSql searchSql, Long currentAccountId) {
        MapSqlParameterSource params = new MapSqlParameterSource(searchSql.parameters().getValues())
                .addValue("metricsCurrentAccountId", currentAccountId);
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN work_status = 'OPEN' THEN 1 ELSE 0 END), 0) AS open_count,
                       COALESCE(SUM(CASE WHEN work_status = 'PROCESSING' THEN 1 ELSE 0 END), 0) AS processing_count,
                       COALESCE(SUM(CASE WHEN work_status <> 'CLOSED' AND sla_due_at < NOW(3)
                                         THEN 1 ELSE 0 END), 0) AS overdue_count,
                       COALESCE(SUM(CASE WHEN work_status <> 'CLOSED' AND owner_account_id IS NULL
                                         THEN 1 ELSE 0 END), 0) AS unassigned_count,
                       COALESCE(SUM(CASE WHEN work_status <> 'CLOSED'
                                             AND owner_account_id = :metricsCurrentAccountId
                                         THEN 1 ELSE 0 END), 0) AS mine_count
                FROM admin_operational_work_item
                """ + searchSql.whereClause(), params, (rs, rowNum) -> {
            WorkItemMetrics metrics = new WorkItemMetrics();
            metrics.setOpenCount(rs.getLong("open_count"));
            metrics.setProcessingCount(rs.getLong("processing_count"));
            metrics.setOverdueCount(rs.getLong("overdue_count"));
            metrics.setUnassignedCount(rs.getLong("unassigned_count"));
            metrics.setMineCount(rs.getLong("mine_count"));
            return metrics;
        });
    }

    private String workItemOrderSql() {
        return """
                 ORDER BY CASE WHEN work_status = 'CLOSED' THEN 1 ELSE 0 END ASC,
                          CASE WHEN work_status <> 'CLOSED' AND sla_due_at < NOW(3) THEN 0 ELSE 1 END ASC,
                          CASE priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1
                                        WHEN 'NORMAL' THEN 2 ELSE 3 END ASC,
                          sla_due_at ASC, first_detected_at DESC, id DESC
                """;
    }

    private boolean sourceWithinScope(WorkMetadata metadata,
                                      SourceSnapshot source,
                                      AdminMerchantDataScope dataScope) {
        if (dataScope.allMerchants() || isSystemSource(metadata.sourceType())) {
            return true;
        }
        return source != null
                && dataScope.merchantIds().contains(source.merchantId())
                && Objects.equals(metadata.merchantId(), source.merchantId());
    }

    private boolean isSystemSource(String sourceType) {
        return EXCHANGE_RATE_ALERT.equals(sourceType);
    }

    private Map<SourceIdentity, SourceSnapshot> loadSourceSnapshots(List<WorkMetadata> metadataRows,
                                                                    AdminMerchantDataScope dataScope) {
        Map<String, Set<String>> groupedKeys = metadataRows.stream().collect(Collectors.groupingBy(
                WorkMetadata::sourceType, LinkedHashMap::new,
                Collectors.mapping(WorkMetadata::sourceKey, Collectors.toCollection(LinkedHashSet::new))));
        Map<SourceIdentity, SourceSnapshot> result = new HashMap<>();
        transactionReadExecutor.read(() -> {
            loadRefundSnapshots(groupedKeys.get(REFUND_APPROVAL), dataScope, false, result);
            loadSettlementSnapshots(groupedKeys.get(SETTLEMENT_REVIEW), dataScope, false, result);
            loadNotificationSnapshots(groupedKeys.get(MERCHANT_NOTIFICATION_FAILURE), dataScope, false, result);
            loadChannelAbnormalSnapshots(groupedKeys.get(CHANNEL_MATCH_ABNORMAL), dataScope, false, result);
            loadClearingBacklogSnapshots(groupedKeys.get(CLEARING_BACKLOG), dataScope, false, result);
            loadSettlementBacklogSnapshots(groupedKeys.get(SETTLEMENT_BACKLOG), dataScope, false, result);
            return null;
        });
        riskAuditReadExecutor.read(() -> {
            loadHighRiskEventSnapshots(groupedKeys.get(HIGH_RISK_EVENT), dataScope, false, result);
            return null;
        });
        loadAlertSnapshots(groupedKeys.get(EXCHANGE_RATE_ALERT), false, result);
        return result;
    }

    private SourceSnapshot loadSingleSource(WorkMetadata metadata, AdminMerchantDataScope dataScope) {
        Map<SourceIdentity, SourceSnapshot> result = loadSourceSnapshots(List.of(metadata), dataScope);
        return result.get(new SourceIdentity(metadata.sourceType(), metadata.sourceKey()));
    }

    private void loadRefundSnapshots(Set<String> keys,
                                     AdminMerchantDataScope scope,
                                     boolean activeOnly,
                                     Map<SourceIdentity, SourceSnapshot> target) {
        forEachKeyBatch(keys, batch -> {
            MapSqlParameterSource params = sourceParams(batch, scope);
            String activeSql = activeOnly ? " AND approval.approval_status = 'PENDING'" : "";
            String scopeSql = merchantScopeSql(scope, "approval.merchant_id");
            List<SourceSnapshot> rows = jdbcTemplate.query("""
                    SELECT approval.approval_id AS source_key, approval.approval_status AS source_status,
                           approval.merchant_id, merchant.merchant_name,
                           approval.refund_transaction_id AS business_no,
                           approval.source_transaction_id AS related_business_no,
                           approval.refund_transaction_id AS transaction_id,
                           approval.refund_transaction_date_time AS transaction_date_time,
                           operation.transaction_currency AS currency,
                           operation.transaction_amount AS amount,
                           operation.currency_exponent,
                           approval.create_time AS source_occurred_at,
                           approval.expire_time AS source_due_at,
                           NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                           NULL AS alert_level, NULL AS attempt_count, NULL AS failure_reason
                    FROM transaction_refund_approval approval
                    LEFT JOIN transaction_operation operation
                      ON operation.transaction_id = approval.refund_transaction_id
                     AND operation.transaction_date_time = approval.refund_transaction_date_time
                     AND operation.deleted = 0
                    LEFT JOIN base_merchant_info merchant
                      ON merchant.merchant_id = approval.merchant_id AND merchant.deleted = 0
                    WHERE approval.approval_id IN (:sourceKeys)
                    """ + activeSql + scopeSql, params,
                    (rs, rowNum) -> snapshot(REFUND_APPROVAL, rs));
            rows.forEach(row -> target.put(row.identity(), row));
        });
    }

    private void loadSettlementSnapshots(Set<String> keys,
                                         AdminMerchantDataScope scope,
                                         boolean activeOnly,
                                         Map<SourceIdentity, SourceSnapshot> target) {
        forEachKeyBatch(keys, batch -> {
            MapSqlParameterSource params = sourceParams(batch, scope);
            String activeSql = activeOnly ? " AND review.review_status = 'PENDING_APPROVAL'" : "";
            String scopeSql = merchantScopeSql(scope, "review.merchant_id");
            List<SourceSnapshot> rows = jdbcTemplate.query("""
                    SELECT review.review_order_no AS source_key, review.review_status AS source_status,
                           review.merchant_id, merchant.merchant_name,
                           review.review_order_no AS business_no,
                           review.settlement_batch_no AS related_business_no,
                           NULL AS transaction_id, NULL AS transaction_date_time,
                           review.target_currency AS currency, review.net_amount AS amount,
                           review.target_currency_exponent AS currency_exponent,
                           review.submitted_time AS source_occurred_at,
                           NULL AS source_due_at,
                           NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                           NULL AS alert_level, NULL AS attempt_count, NULL AS failure_reason
                    FROM settlement_review_order review
                    LEFT JOIN base_merchant_info merchant
                      ON merchant.merchant_id = review.merchant_id AND merchant.deleted = 0
                    WHERE review.review_order_no IN (:sourceKeys)
                    """ + activeSql + scopeSql, params,
                    (rs, rowNum) -> snapshot(SETTLEMENT_REVIEW, rs));
            rows.forEach(row -> target.put(row.identity(), row));
        });
    }

    private void loadNotificationSnapshots(Set<String> keys,
                                           AdminMerchantDataScope scope,
                                           boolean activeOnly,
                                           Map<SourceIdentity, SourceSnapshot> target) {
        forEachKeyBatch(keys, batch -> {
            MapSqlParameterSource params = sourceParams(batch, scope);
            String activeSql = activeOnly ? """
                    AND (notification.notify_status = 'CLOSED'
                         OR (notification.notify_status = 'FAILED'
                             AND notification.next_retry_time IS NULL))
                    """ : "";
            String scopeSql = merchantScopeSql(scope, "notification.merchant_id");
            List<SourceSnapshot> rows = jdbcTemplate.query("""
                    SELECT notification.notify_id AS source_key,
                           notification.notify_status AS source_status,
                           notification.merchant_id, merchant.merchant_name,
                           notification.notify_id AS business_no,
                           notification.merchant_order_no AS related_business_no,
                           notification.transaction_id,
                           notification.transaction_date_time,
                           NULL AS currency, NULL AS amount, NULL AS currency_exponent,
                           notification.create_time AS source_occurred_at,
                           NULL AS source_due_at,
                           NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                           NULL AS alert_level,
                           notification.last_attempt_no AS attempt_count,
                           notification.fail_reason AS failure_reason
                    FROM transaction_merchant_notification notification
                    LEFT JOIN base_merchant_info merchant
                      ON merchant.merchant_id = notification.merchant_id AND merchant.deleted = 0
                    WHERE notification.notify_id IN (:sourceKeys)
                      AND notification.deleted = 0
                    """ + activeSql + scopeSql, params,
                    (rs, rowNum) -> snapshot(MERCHANT_NOTIFICATION_FAILURE, rs));
            rows.forEach(row -> target.put(row.identity(), row));
        });
    }

    /**
     * 按案件号读取渠道勾兑异常实时事实，并在 SQL 层落实管理账号的商户数据范围。
     *
     * <p>负责人和处理状态由支付核心案件状态机维护；统一待办只投影这些字段，不能反向覆盖。</p>
     */
    private void loadChannelAbnormalSnapshots(Set<String> keys,
                                              AdminMerchantDataScope scope,
                                              boolean activeOnly,
                                              Map<SourceIdentity, SourceSnapshot> target) {
        forEachKeyBatch(keys, batch -> {
            MapSqlParameterSource params = sourceParams(batch, scope);
            String activeSql = activeOnly ? " AND event.event_status IN ('OPEN', 'PROCESSING')" : "";
            String scopeSql = merchantScopeSql(scope, "event.merchant_id");
            List<SourceSnapshot> rows = jdbcTemplate.query("""
                    SELECT event.abnormal_event_id AS source_key,
                           event.event_status AS source_status,
                           event.merchant_id, merchant.merchant_name,
                           event.abnormal_event_id AS business_no,
                           event.merchant_order_no AS related_business_no,
                           event.transaction_id, event.transaction_date_time,
                           event.platform_currency AS currency,
                           event.platform_amount AS amount,
                           event.currency_exponent,
                           event.first_seen_time AS source_occurred_at,
                           NULL AS source_due_at,
                           NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                           event.abnormal_level AS alert_level,
                           event.occurrence_count AS attempt_count,
                           event.abnormal_description AS failure_reason,
                           event.assigned_to_id, event.assigned_to_name
                    FROM transaction_abnormal_event event
                    LEFT JOIN base_merchant_info merchant
                      ON merchant.merchant_id = event.merchant_id AND merchant.deleted = 0
                    WHERE event.abnormal_event_id IN (:sourceKeys) AND event.deleted = 0
                    """ + activeSql + scopeSql, params,
                    (rs, rowNum) -> channelAbnormalSnapshot(rs));
            rows.forEach(row -> target.put(row.identity(), row));
        });
    }

    /**
     * 按清分状态编号读取权威清分状态，并在 SQL 层落实管理账号的商户数据范围。
     *
     * <p>清分状态机负责重试、人工复核与终态推进；统一待办只提供运营入口和备注。</p>
     */
    private void loadClearingBacklogSnapshots(Set<String> keys,
                                              AdminMerchantDataScope scope,
                                              boolean activeOnly,
                                              Map<SourceIdentity, SourceSnapshot> target) {
        forEachKeyBatch(keys, batch -> {
            MapSqlParameterSource params = sourceParams(batch, scope);
            String activeSql = activeOnly ? """
                    AND (finance.clearing_status = 'MANUAL_REVIEW'
                         OR (finance.clearing_status IN ('FAILED', 'WAITING_SOURCE')
                             AND finance.next_retry_time IS NULL))
                    """ : "";
            String scopeSql = merchantScopeSql(scope, "finance.merchant_id");
            List<SourceSnapshot> rows = jdbcTemplate.query("""
                    SELECT finance.finance_state_id AS source_key,
                           finance.clearing_status AS source_status,
                           finance.merchant_id, merchant.merchant_name,
                           finance.transaction_id AS business_no,
                           finance.operation_id AS related_business_no,
                           finance.transaction_id, finance.transaction_date_time,
                           COALESCE(finance.label_currency, operation.label_currency) AS currency,
                           operation.label_amount AS amount,
                           operation.currency_exponent,
                           finance.update_time AS source_occurred_at,
                           NULL AS source_due_at,
                           NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                           NULL AS alert_level,
                           finance.clearing_retry_count AS attempt_count,
                           COALESCE(NULLIF(finance.last_failure_message, ''),
                                    finance.last_failure_code) AS failure_reason
                    FROM transaction_finance_state finance
                    LEFT JOIN transaction_operation operation
                      ON operation.operation_id = finance.operation_id
                     AND operation.transaction_date_time = finance.transaction_date_time
                     AND operation.deleted = 0
                    LEFT JOIN base_merchant_info merchant
                      ON merchant.merchant_id = finance.merchant_id AND merchant.deleted = 0
                    WHERE finance.finance_state_id IN (:sourceKeys)
                      AND finance.deleted = 0
                    """ + activeSql + scopeSql, params,
                    (rs, rowNum) -> snapshot(CLEARING_BACKLOG, rs));
            rows.forEach(row -> target.put(row.identity(), row));
        });
    }

    /**
     * 按结算批次号读取权威结算批次，并在 SQL 层落实管理账号的商户数据范围。
     *
     * <p>统一待办只投影人工复核状态及失败摘要；重试、取消和终态推进仍由结算状态机负责。</p>
     */
    private void loadSettlementBacklogSnapshots(Set<String> keys,
                                                 AdminMerchantDataScope scope,
                                                 boolean activeOnly,
                                                 Map<SourceIdentity, SourceSnapshot> target) {
        forEachKeyBatch(keys, batchKeys -> {
            MapSqlParameterSource params = sourceParams(batchKeys, scope);
            String activeSql = activeOnly ? " AND batch.batch_status = 'MANUAL_REVIEW'" : "";
            String scopeSql = merchantScopeSql(scope, "batch.merchant_id");
            List<SourceSnapshot> rows = jdbcTemplate.query("""
                    SELECT batch.settlement_batch_no AS source_key,
                           batch.batch_status AS source_status,
                           batch.merchant_id, merchant.merchant_name,
                           batch.settlement_batch_no AS business_no,
                           COALESCE(batch.review_order_no, batch.original_batch_no) AS related_business_no,
                           NULL AS transaction_id, NULL AS transaction_date_time,
                           batch.target_currency AS currency,
                           (SELECT item.target_amount
                            FROM settlement_result_item item
                            WHERE item.settlement_batch_no = batch.settlement_batch_no
                              AND item.result_role = 'LEDGER_POSTING'
                              AND item.result_item_type = 'NET_SETTLEMENT'
                            ORDER BY item.id ASC LIMIT 1) AS amount,
                           batch.target_currency_exponent AS currency_exponent,
                           batch.update_time AS source_occurred_at,
                           NULL AS source_due_at,
                           NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                           NULL AS alert_level, batch.retry_count AS attempt_count,
                           batch.last_failure_stage AS failure_stage,
                           batch.last_failure_code AS failure_code,
                           batch.last_failure_message AS failure_reason
                    FROM settlement_batch batch
                    LEFT JOIN base_merchant_info merchant
                      ON merchant.merchant_id = batch.merchant_id AND merchant.deleted = 0
                    WHERE batch.settlement_batch_no IN (:sourceKeys)
                    """ + activeSql + scopeSql, params,
                    (rs, rowNum) -> settlementBacklogSnapshot(rs));
            rows.forEach(row -> target.put(row.identity(), row));
        });
    }

    /**
     * 按风控记录号读取高风险事件事实，并在列表查询时继续执行商户数据范围校验。
     */
    private void loadHighRiskEventSnapshots(Set<String> keys,
                                            AdminMerchantDataScope scope,
                                            boolean activeOnly,
                                            Map<SourceIdentity, SourceSnapshot> target) {
        forEachKeyBatch(keys, batch -> {
            MapSqlParameterSource params = sourceParams(batch, scope);
            String routeSql = "";
            if (riskAuditShardingProperties.isEnabled()) {
                List<LocalDateTime> evaluationTimes = jdbcTemplate.queryForList("""
                        SELECT evaluation_time
                        FROM risk_evaluation_locator
                        WHERE risk_record_no IN (:sourceKeys)
                        """, params, LocalDateTime.class);
                if (evaluationTimes.isEmpty()) {
                    return;
                }
                params.addValue("evaluationTimes", evaluationTimes);
                routeSql = " AND evaluation.evaluation_time IN (:evaluationTimes)";
            }
            String activeSql = activeOnly
                    ? " AND (evaluation.decision_result = 'REJECT' OR evaluation.risk_level IN ('HIGH', 'CRITICAL'))"
                    : "";
            String scopeSql = merchantScopeSql(scope, "evaluation.merchant_id");
            List<SourceSnapshot> rows = jdbcTemplate.query("""
                    SELECT evaluation.risk_record_no AS source_key,
                           evaluation.decision_result AS source_status,
                           evaluation.merchant_id, evaluation.merchant_name,
                           evaluation.risk_record_no AS business_no,
                           COALESCE(NULLIF(evaluation.payment_order_no, ''),
                                    evaluation.merchant_order_no) AS related_business_no,
                           evaluation.payment_order_no AS transaction_id,
                           NULL AS transaction_date_time,
                           evaluation.transaction_currency AS currency,
                           evaluation.transaction_amount AS amount,
                           NULL AS currency_exponent,
                           evaluation.evaluation_time AS source_occurred_at,
                           NULL AS source_due_at,
                           NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                           evaluation.risk_level AS alert_level,
                           evaluation.hit_count AS attempt_count,
                           evaluation.decision_reason AS failure_reason
                    FROM risk_evaluation_record evaluation
                    WHERE evaluation.risk_record_no IN (:sourceKeys)
                    """ + routeSql + activeSql + scopeSql, params,
                    (rs, rowNum) -> snapshot(HIGH_RISK_EVENT, rs));
            rows.forEach(row -> target.put(row.identity(), row));
        });
    }

    private void loadAlertSnapshots(Set<String> keys,
                                    boolean activeOnly,
                                    Map<SourceIdentity, SourceSnapshot> target) {
        forEachKeyBatch(keys, batch -> {
            MapSqlParameterSource params = new MapSqlParameterSource("sourceKeys", batch);
            String activeSql = activeOnly ? " AND event.event_status IN ('OPEN', 'ACKNOWLEDGED')" : "";
            List<SourceSnapshot> rows = jdbcTemplate.query("""
                    SELECT event.event_no AS source_key, event.event_status AS source_status,
                           NULL AS merchant_id, NULL AS merchant_name,
                           event.event_no AS business_no, event.rule_code AS related_business_no,
                           NULL AS transaction_id, NULL AS transaction_date_time,
                           NULL AS currency, NULL AS amount, NULL AS currency_exponent,
                           event.first_trigger_time AS source_occurred_at,
                           NULL AS source_due_at,
                           event.rate_type, event.base_currency, event.quote_currency,
                           event.alert_level, NULL AS attempt_count, NULL AS failure_reason
                    FROM exchange_rate_alert_event event
                    WHERE event.event_no IN (:sourceKeys) AND event.deleted = 0
                    """ + activeSql, params,
                    (rs, rowNum) -> snapshot(EXCHANGE_RATE_ALERT, rs));
            rows.forEach(row -> target.put(row.identity(), row));
        });
    }

    private Map<SourceIdentity, SourceSnapshot> loadActiveSources(LocalDateTime syncTime) {
        Map<SourceIdentity, SourceSnapshot> sources = new LinkedHashMap<>();
        transactionReadExecutor.read(() -> {
            loadBoundedActiveRefunds(sources);
            loadBoundedActiveSettlements(sources);
            loadBoundedActiveNotifications(sources);
            loadBoundedActiveChannelAbnormalities(sources);
            loadBoundedActiveClearingBacklogs(sources);
            loadBoundedActiveSettlementBacklogs(sources);
            return null;
        });
        riskAuditReadExecutor.read(() -> {
            loadBoundedActiveHighRiskEvents(sources);
            return null;
        });
        loadBoundedActiveAlerts(sources);
        Map<SourceIdentity, SourceSnapshot> enriched = new LinkedHashMap<>();
        for (SourceSnapshot source : sources.values()) {
            enriched.put(source.identity(), withSla(source, syncTime));
        }
        return enriched;
    }

    private void loadBoundedActiveRefunds(Map<SourceIdentity, SourceSnapshot> target) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", properties.getMaxSyncRows() + 1);
        List<SourceSnapshot> rows = jdbcTemplate.query("""
                SELECT approval_id AS source_key, approval_status AS source_status,
                       merchant_id, NULL AS merchant_name,
                       refund_transaction_id AS business_no,
                       source_transaction_id AS related_business_no,
                       refund_transaction_id AS transaction_id,
                       refund_transaction_date_time AS transaction_date_time,
                       NULL AS currency, NULL AS amount, NULL AS currency_exponent,
                       create_time AS source_occurred_at, expire_time AS source_due_at,
                       NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                       NULL AS alert_level, NULL AS attempt_count, NULL AS failure_reason
                FROM transaction_refund_approval
                WHERE approval_status = 'PENDING'
                ORDER BY create_time ASC, id ASC LIMIT :limit
                """, params, (rs, rowNum) -> snapshot(REFUND_APPROVAL, rs));
        putBounded(target, rows, REFUND_APPROVAL);
    }

    private void loadBoundedActiveSettlements(Map<SourceIdentity, SourceSnapshot> target) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", properties.getMaxSyncRows() + 1);
        List<SourceSnapshot> rows = jdbcTemplate.query("""
                SELECT review_order_no AS source_key, review_status AS source_status,
                       merchant_id, NULL AS merchant_name,
                       review_order_no AS business_no,
                       settlement_batch_no AS related_business_no,
                       NULL AS transaction_id, NULL AS transaction_date_time,
                       target_currency AS currency, net_amount AS amount,
                       target_currency_exponent AS currency_exponent,
                       submitted_time AS source_occurred_at, NULL AS source_due_at,
                       NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                       NULL AS alert_level, NULL AS attempt_count, NULL AS failure_reason
                FROM settlement_review_order
                WHERE review_status = 'PENDING_APPROVAL'
                ORDER BY submitted_time ASC, id ASC LIMIT :limit
                """, params, (rs, rowNum) -> snapshot(SETTLEMENT_REVIEW, rs));
        putBounded(target, rows, SETTLEMENT_REVIEW);
    }

    private void loadBoundedActiveNotifications(Map<SourceIdentity, SourceSnapshot> target) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", properties.getMaxSyncRows() + 1);
        List<SourceSnapshot> rows = jdbcTemplate.query("""
                SELECT notify_id AS source_key, notify_status AS source_status,
                       merchant_id, NULL AS merchant_name,
                       notify_id AS business_no, merchant_order_no AS related_business_no,
                       transaction_id, transaction_date_time,
                       NULL AS currency, NULL AS amount, NULL AS currency_exponent,
                       create_time AS source_occurred_at, NULL AS source_due_at,
                       NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                       NULL AS alert_level, last_attempt_no AS attempt_count,
                       fail_reason AS failure_reason
                FROM transaction_merchant_notification
                WHERE deleted = 0
                  AND (notify_status = 'CLOSED'
                       OR (notify_status = 'FAILED' AND next_retry_time IS NULL))
                ORDER BY create_time ASC, id ASC LIMIT :limit
                """, params, (rs, rowNum) -> snapshot(MERCHANT_NOTIFICATION_FAILURE, rs));
        putBounded(target, rows, MERCHANT_NOTIFICATION_FAILURE);
    }

    /**
     * 扫描支付核心中仍需人工处理的渠道勾兑异常案件。
     *
     * <p>查询预算按单一来源独立限制，超过预算时整轮同步失败，避免部分扫描误关闭既有待办。</p>
     */
    private void loadBoundedActiveChannelAbnormalities(Map<SourceIdentity, SourceSnapshot> target) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", properties.getMaxSyncRows() + 1);
        List<SourceSnapshot> rows = jdbcTemplate.query("""
                SELECT abnormal_event_id AS source_key, event_status AS source_status,
                       merchant_id, NULL AS merchant_name,
                       abnormal_event_id AS business_no,
                       merchant_order_no AS related_business_no,
                       transaction_id, transaction_date_time,
                       platform_currency AS currency, platform_amount AS amount,
                       currency_exponent, first_seen_time AS source_occurred_at,
                       NULL AS source_due_at,
                       NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                       abnormal_level AS alert_level, occurrence_count AS attempt_count,
                       abnormal_description AS failure_reason,
                       assigned_to_id, assigned_to_name
                FROM transaction_abnormal_event
                WHERE event_status IN ('OPEN', 'PROCESSING') AND deleted = 0
                ORDER BY first_seen_time ASC, id ASC LIMIT :limit
                """, params, (rs, rowNum) -> channelAbnormalSnapshot(rs));
        putBounded(target, rows, CHANNEL_MATCH_ABNORMAL);
    }

    /**
     * 扫描已经需要人工介入的清分状态。
     *
     * <p>带有下次重试时间的 FAILED 或 WAITING_SOURCE 仍由清分补偿任务负责，不进入运营待办。</p>
     */
    private void loadBoundedActiveClearingBacklogs(Map<SourceIdentity, SourceSnapshot> target) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", properties.getMaxSyncRows() + 1);
        List<SourceSnapshot> rows = jdbcTemplate.query("""
                SELECT finance.finance_state_id AS source_key,
                       finance.clearing_status AS source_status,
                       finance.merchant_id, NULL AS merchant_name,
                       finance.transaction_id AS business_no,
                       finance.operation_id AS related_business_no,
                       finance.transaction_id, finance.transaction_date_time,
                       COALESCE(finance.label_currency, operation.label_currency) AS currency,
                       operation.label_amount AS amount,
                       operation.currency_exponent,
                       finance.update_time AS source_occurred_at,
                       NULL AS source_due_at,
                       NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                       NULL AS alert_level,
                       finance.clearing_retry_count AS attempt_count,
                       COALESCE(NULLIF(finance.last_failure_message, ''),
                                finance.last_failure_code) AS failure_reason
                FROM transaction_finance_state finance
                LEFT JOIN transaction_operation operation
                  ON operation.operation_id = finance.operation_id
                 AND operation.transaction_date_time = finance.transaction_date_time
                 AND operation.deleted = 0
                WHERE finance.deleted = 0
                  AND (finance.clearing_status = 'MANUAL_REVIEW'
                       OR (finance.clearing_status IN ('FAILED', 'WAITING_SOURCE')
                           AND finance.next_retry_time IS NULL))
                ORDER BY finance.update_time ASC, finance.id ASC LIMIT :limit
                """, params, (rs, rowNum) -> snapshot(CLEARING_BACKLOG, rs));
        putBounded(target, rows, CLEARING_BACKLOG);
    }

    /**
     * 扫描已经由结算状态机升级为人工复核的批次。
     *
     * <p>{@code FAILED_RETRYABLE} 仍由自动调度器有界重试，不进入人工运营队列。</p>
     */
    private void loadBoundedActiveSettlementBacklogs(Map<SourceIdentity, SourceSnapshot> target) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", properties.getMaxSyncRows() + 1);
        List<SourceSnapshot> rows = jdbcTemplate.query("""
                SELECT batch.settlement_batch_no AS source_key,
                       batch.batch_status AS source_status,
                       batch.merchant_id, NULL AS merchant_name,
                       batch.settlement_batch_no AS business_no,
                       COALESCE(batch.review_order_no, batch.original_batch_no) AS related_business_no,
                       NULL AS transaction_id, NULL AS transaction_date_time,
                       batch.target_currency AS currency,
                       (SELECT item.target_amount
                        FROM settlement_result_item item
                        WHERE item.settlement_batch_no = batch.settlement_batch_no
                          AND item.result_role = 'LEDGER_POSTING'
                          AND item.result_item_type = 'NET_SETTLEMENT'
                        ORDER BY item.id ASC LIMIT 1) AS amount,
                       batch.target_currency_exponent AS currency_exponent,
                       batch.update_time AS source_occurred_at,
                       NULL AS source_due_at,
                       NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                       NULL AS alert_level, batch.retry_count AS attempt_count,
                       batch.last_failure_stage AS failure_stage,
                       batch.last_failure_code AS failure_code,
                       batch.last_failure_message AS failure_reason
                FROM settlement_batch batch
                WHERE batch.batch_status = 'MANUAL_REVIEW'
                ORDER BY batch.update_time ASC, batch.id ASC LIMIT :limit
                """, params, (rs, rowNum) -> settlementBacklogSnapshot(rs));
        putBounded(target, rows, SETTLEMENT_BACKLOG);
    }

    /**
     * 扫描需要运营人员跟进的高风险评估结果。
     *
     * <p>风控评估记录不可变，本查询只建立运营投影，不修改风控决策或支付交易状态。</p>
     */
    private void loadBoundedActiveHighRiskEvents(Map<SourceIdentity, SourceSnapshot> target) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", properties.getMaxSyncRows() + 1);
        List<SourceSnapshot> rows = jdbcTemplate.query("""
                SELECT risk_record_no AS source_key, decision_result AS source_status,
                       merchant_id, merchant_name,
                       risk_record_no AS business_no,
                       COALESCE(NULLIF(payment_order_no, ''), merchant_order_no) AS related_business_no,
                       payment_order_no AS transaction_id, NULL AS transaction_date_time,
                       transaction_currency AS currency, transaction_amount AS amount,
                       NULL AS currency_exponent,
                       evaluation_time AS source_occurred_at, NULL AS source_due_at,
                       NULL AS rate_type, NULL AS base_currency, NULL AS quote_currency,
                       risk_level AS alert_level, hit_count AS attempt_count,
                       decision_reason AS failure_reason
                FROM risk_evaluation_record
                WHERE decision_result = 'REJECT' OR risk_level IN ('HIGH', 'CRITICAL')
                ORDER BY evaluation_time ASC, id ASC LIMIT :limit
                """, params, (rs, rowNum) -> snapshot(HIGH_RISK_EVENT, rs));
        putBounded(target, rows, HIGH_RISK_EVENT);
    }

    private void loadBoundedActiveAlerts(Map<SourceIdentity, SourceSnapshot> target) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", properties.getMaxSyncRows() + 1);
        List<SourceSnapshot> rows = jdbcTemplate.query("""
                SELECT event_no AS source_key, event_status AS source_status,
                       NULL AS merchant_id, NULL AS merchant_name,
                       event_no AS business_no, rule_code AS related_business_no,
                       NULL AS transaction_id, NULL AS transaction_date_time,
                       NULL AS currency, NULL AS amount, NULL AS currency_exponent,
                       first_trigger_time AS source_occurred_at, NULL AS source_due_at,
                       rate_type, base_currency, quote_currency, alert_level,
                       NULL AS attempt_count, NULL AS failure_reason
                FROM exchange_rate_alert_event
                WHERE event_status IN ('OPEN', 'ACKNOWLEDGED') AND deleted = 0
                ORDER BY first_trigger_time ASC, id ASC LIMIT :limit
                """, params, (rs, rowNum) -> snapshot(EXCHANGE_RATE_ALERT, rs));
        putBounded(target, rows, EXCHANGE_RATE_ALERT);
    }

    private void putBounded(Map<SourceIdentity, SourceSnapshot> target,
                            List<SourceSnapshot> rows,
                            String sourceType) {
        if (rows.size() > properties.getMaxSyncRows()) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(),
                    sourceType + " active source count exceeds the configured sync budget");
        }
        rows.forEach(row -> target.put(row.identity(), row));
    }

    private SourceSnapshot withSla(SourceSnapshot source, LocalDateTime syncTime) {
        LocalDateTime detected = source.sourceOccurredAt() == null ? syncTime : source.sourceOccurredAt();
        LocalDateTime dueAt;
        String priority;
        switch (source.identity().sourceType()) {
            case REFUND_APPROVAL -> {
                dueAt = source.sourceDueAt() == null ? detected.plusHours(4) : source.sourceDueAt();
                priority = "HIGH";
            }
            case SETTLEMENT_REVIEW -> {
                dueAt = detected.plus(properties.getSettlementSla());
                priority = "NORMAL";
            }
            case EXCHANGE_RATE_ALERT -> {
                priority = alertPriority(source.alertLevel());
                if ("CRITICAL".equals(priority)) {
                    dueAt = detected.plus(properties.getExchangeCriticalSla());
                } else if ("HIGH".equals(priority)) {
                    dueAt = detected.plus(properties.getExchangeErrorSla());
                } else {
                    dueAt = detected.plus(properties.getExchangeDefaultSla());
                }
            }
            case MERCHANT_NOTIFICATION_FAILURE -> {
                dueAt = detected.plus(properties.getNotificationSla());
                priority = "HIGH";
            }
            case CHANNEL_MATCH_ABNORMAL -> {
                priority = channelAbnormalPriority(source.alertLevel());
                if ("CRITICAL".equals(priority)) {
                    dueAt = detected.plus(properties.getChannelAbnormalCriticalSla());
                } else if ("HIGH".equals(priority)) {
                    dueAt = detected.plus(properties.getChannelAbnormalHighSla());
                } else {
                    dueAt = detected.plus(properties.getChannelAbnormalWarningSla());
                }
            }
            case CLEARING_BACKLOG -> {
                dueAt = detected.plus(properties.getClearingBacklogSla());
                priority = "HIGH";
            }
            case SETTLEMENT_BACKLOG -> {
                dueAt = detected.plus(properties.getSettlementBacklogSla());
                priority = "HIGH";
            }
            case HIGH_RISK_EVENT -> {
                priority = highRiskEventPriority(source.alertLevel());
                dueAt = detected.plus("CRITICAL".equals(priority)
                        ? properties.getHighRiskCriticalSla()
                        : properties.getHighRiskEventSla());
            }
            default -> throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        return source.withOperationalFields(priority, detected, dueAt);
    }

    private Map<SourceIdentity, WorkMetadata> loadSynchronizationMetadata(Set<SourceIdentity> activeKeys) {
        Map<SourceIdentity, WorkMetadata> result = new LinkedHashMap<>();
        MapSqlParameterSource activeParams = new MapSqlParameterSource()
                .addValue("sourceTypes", SOURCE_TYPES)
                .addValue("limit", properties.getMaxSyncRows() * SOURCE_TYPES.size() + 1);
        List<WorkMetadata> activeRows = jdbcTemplate.query("SELECT " + WORK_METADATA_COLUMNS + """
                FROM admin_operational_work_item
                WHERE source_type IN (:sourceTypes) AND work_status <> 'CLOSED'
                ORDER BY id ASC LIMIT :limit
                """, activeParams, this::mapMetadata);
        if (activeRows.size() > properties.getMaxSyncRows() * SOURCE_TYPES.size()) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(),
                    "active work item count exceeds the configured sync budget");
        }
        activeRows.forEach(row -> result.put(row.identity(), row));

        Map<String, Set<String>> grouped = activeKeys.stream().collect(Collectors.groupingBy(
                SourceIdentity::sourceType, LinkedHashMap::new,
                Collectors.mapping(SourceIdentity::sourceKey, Collectors.toCollection(LinkedHashSet::new))));
        grouped.forEach((sourceType, keys) -> forEachKeyBatch(keys, batch -> {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("sourceType", sourceType)
                    .addValue("sourceKeys", batch);
            jdbcTemplate.query("SELECT " + WORK_METADATA_COLUMNS + """
                    FROM admin_operational_work_item
                    WHERE source_type = :sourceType AND source_key IN (:sourceKeys)
                    """, params, this::mapMetadata).forEach(row -> result.put(row.identity(), row));
        }));
        return result;
    }

    private SyncCounters applySynchronization(SyncRequest request,
                                              Map<SourceIdentity, SourceSnapshot> activeSources,
                                              Map<SourceIdentity, WorkMetadata> existing) {
        int created = 0;
        int reopened = 0;
        int closed = 0;
        int refreshed = 0;
        LocalDateTime syncTime = request.getSyncTime();
        for (SourceSnapshot source : activeSources.values()) {
            WorkMetadata current = existing.get(source.identity());
            if (current == null) {
                if (insertWorkItem(source, syncTime) == 1) {
                    created++;
                    WorkMetadata inserted = requiredMetadataBySource(source.identity());
                    insertHistory(inserted, syncRequestId(request.getRequestKey(), source.identity(), "CREATE"),
                            "CREATE", null, inserted.workStatus(), null, null,
                            inserted.ownerAccountId(), inserted.ownerAccountName(),
                            null, "SYSTEM", "Detected active source", syncTime);
                }
            } else if (CLOSED.equals(current.workStatus())
                    && "SOURCE_TERMINAL_OR_REMOVED".equals(current.closeReason())) {
                String desiredStatus = desiredWorkStatus(source);
                Long desiredOwnerId = desiredOwnerAccountId(source);
                String desiredOwnerName = desiredOwnerAccountName(source);
                int updated = jdbcTemplate.update("""
                        UPDATE admin_operational_work_item
                        SET work_status = :desiredStatus, priority = :priority, sla_due_at = :slaDueAt,
                            owner_account_id = :ownerAccountId, owner_account_name = :ownerAccountName,
                            source_projection_version = :sourceProjectionVersion,
                            merchant_id = :merchantId, merchant_name = :merchantName,
                            business_no = :businessNo, related_business_no = :relatedBusinessNo,
                            transaction_id = :transactionId, transaction_date_time = :transactionDateTime,
                            source_occurred_at = :sourceOccurredAt,
                            last_synced_at = :syncTime, closed_at = NULL, close_reason = NULL,
                            version = version + 1, update_time = :syncTime
                        WHERE id = :id AND work_status = 'CLOSED' AND version = :version
                        """, addSourceProjection(new MapSqlParameterSource(), source)
                        .addValue("desiredStatus", desiredStatus)
                        .addValue("priority", source.priority())
                        .addValue("slaDueAt", source.slaDueAt())
                        .addValue("ownerAccountId", desiredOwnerId)
                        .addValue("ownerAccountName", desiredOwnerName)
                        .addValue("syncTime", syncTime)
                        .addValue("id", current.id())
                        .addValue("version", current.version()));
                if (updated == 1) {
                    reopened++;
                    insertHistory(current, syncRequestId(request.getRequestKey(), source.identity(), "REOPEN"),
                            "REOPEN", CLOSED, desiredStatus,
                            current.ownerAccountId(), current.ownerAccountName(),
                            desiredOwnerId, desiredOwnerName,
                            null, "SYSTEM", "Source became active again", syncTime);
                }
            } else {
                refreshed += refreshActiveWorkItem(request, source, current, syncTime);
            }
        }
        for (WorkMetadata current : existing.values()) {
            if (CLOSED.equals(current.workStatus()) || activeSources.containsKey(current.identity())) {
                continue;
            }
            int updated = jdbcTemplate.update("""
                    UPDATE admin_operational_work_item
                    SET work_status = 'CLOSED', last_synced_at = :syncTime,
                        closed_at = :syncTime, close_reason = 'SOURCE_TERMINAL_OR_REMOVED',
                        version = version + 1, update_time = :syncTime
                    WHERE id = :id AND work_status = :workStatus AND version = :version
                    """, new MapSqlParameterSource()
                    .addValue("syncTime", syncTime)
                    .addValue("id", current.id())
                    .addValue("workStatus", current.workStatus())
                    .addValue("version", current.version()));
            if (updated == 1) {
                closed++;
                insertHistory(current, syncRequestId(request.getRequestKey(), current.identity(), "AUTO_CLOSE"),
                        "AUTO_CLOSE", current.workStatus(), CLOSED,
                        current.ownerAccountId(), current.ownerAccountName(),
                        current.ownerAccountId(), current.ownerAccountName(),
                        null, "SYSTEM", "Source entered a terminal state", syncTime);
            }
        }
        return new SyncCounters(created, reopened, closed, refreshed);
    }

    /**
     * 刷新活动来源的运营投影；仅来源托管型案件允许同步状态和负责人。
     */
    private int refreshActiveWorkItem(SyncRequest request,
                                      SourceSnapshot source,
                                      WorkMetadata current,
                                      LocalDateTime syncTime) {
        if (!isSourceManaged(source.identity().sourceType()) || CLOSED.equals(current.workStatus())) {
            return jdbcTemplate.update("""
                    UPDATE admin_operational_work_item
                    SET priority = :priority, sla_due_at = :slaDueAt,
                        source_projection_version = :sourceProjectionVersion,
                        merchant_id = :merchantId, merchant_name = :merchantName,
                        business_no = :businessNo, related_business_no = :relatedBusinessNo,
                        transaction_id = :transactionId, transaction_date_time = :transactionDateTime,
                        source_occurred_at = :sourceOccurredAt,
                        last_synced_at = :syncTime, update_time = :syncTime
                    WHERE id = :id AND work_status = :workStatus AND version = :version
                    """, addSourceProjection(new MapSqlParameterSource(), source)
                    .addValue("priority", source.priority())
                    .addValue("slaDueAt", source.slaDueAt())
                    .addValue("syncTime", syncTime)
                    .addValue("id", current.id())
                    .addValue("workStatus", current.workStatus())
                    .addValue("version", current.version()));
        }

        String desiredStatus = desiredWorkStatus(source);
        Long desiredOwnerId = desiredOwnerAccountId(source);
        String desiredOwnerName = desiredOwnerAccountName(source);
        boolean sourceStateChanged = !Objects.equals(current.workStatus(), desiredStatus)
                || !Objects.equals(current.ownerAccountId(), desiredOwnerId)
                || !Objects.equals(current.ownerAccountName(), desiredOwnerName);
        if (!sourceStateChanged) {
            return jdbcTemplate.update("""
                    UPDATE admin_operational_work_item
                    SET priority = :priority, sla_due_at = :slaDueAt,
                        source_projection_version = :sourceProjectionVersion,
                        merchant_id = :merchantId, merchant_name = :merchantName,
                        business_no = :businessNo, related_business_no = :relatedBusinessNo,
                        transaction_id = :transactionId, transaction_date_time = :transactionDateTime,
                        source_occurred_at = :sourceOccurredAt,
                        last_synced_at = :syncTime, update_time = :syncTime
                    WHERE id = :id AND work_status = :workStatus AND version = :version
                    """, addSourceProjection(new MapSqlParameterSource(), source)
                    .addValue("priority", source.priority())
                    .addValue("slaDueAt", source.slaDueAt())
                    .addValue("syncTime", syncTime)
                    .addValue("id", current.id())
                    .addValue("workStatus", current.workStatus())
                    .addValue("version", current.version()));
        }

        int updated = jdbcTemplate.update("""
                UPDATE admin_operational_work_item
                SET work_status = :desiredStatus,
                    owner_account_id = :ownerAccountId,
                    owner_account_name = :ownerAccountName,
                    priority = :priority,
                    sla_due_at = :slaDueAt,
                    source_projection_version = :sourceProjectionVersion,
                    merchant_id = :merchantId,
                    merchant_name = :merchantName,
                    business_no = :businessNo,
                    related_business_no = :relatedBusinessNo,
                    transaction_id = :transactionId,
                    transaction_date_time = :transactionDateTime,
                    source_occurred_at = :sourceOccurredAt,
                    last_synced_at = :syncTime,
                    version = version + 1,
                    update_time = :syncTime
                WHERE id = :id AND work_status = :workStatus AND version = :version
                """, addSourceProjection(new MapSqlParameterSource(), source)
                .addValue("desiredStatus", desiredStatus)
                .addValue("ownerAccountId", desiredOwnerId)
                .addValue("ownerAccountName", desiredOwnerName)
                .addValue("priority", source.priority())
                .addValue("slaDueAt", source.slaDueAt())
                .addValue("syncTime", syncTime)
                .addValue("id", current.id())
                .addValue("workStatus", current.workStatus())
                .addValue("version", current.version()));
        if (updated == 1) {
            insertHistory(current,
                    syncRequestId(request.getRequestKey(), source.identity(), "SOURCE_SYNC"),
                    "SOURCE_SYNC", current.workStatus(), desiredStatus,
                    current.ownerAccountId(), current.ownerAccountName(),
                    desiredOwnerId, desiredOwnerName,
                    null, "SYSTEM", "Source-managed state synchronized", syncTime);
        }
        return updated;
    }

    private int insertWorkItem(SourceSnapshot source, LocalDateTime syncTime) {
        String desiredStatus = desiredWorkStatus(source);
        Long desiredOwnerId = desiredOwnerAccountId(source);
        String desiredOwnerName = desiredOwnerAccountName(source);
        return jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operational_work_item
                (work_item_no, source_type, source_key, work_status, priority,
                 owner_account_id, owner_account_name, sla_due_at, first_detected_at,
                 last_synced_at, closed_at, close_reason, latest_remark, version,
                 source_projection_version, merchant_id, merchant_name, business_no,
                 related_business_no, transaction_id, transaction_date_time, source_occurred_at,
                 create_time, update_time)
                VALUES
                (:workItemNo, :sourceType, :sourceKey, :workStatus, :priority,
                 :ownerAccountId, :ownerAccountName, :slaDueAt, :firstDetectedAt,
                 :syncTime, NULL, NULL, NULL, 0,
                 :sourceProjectionVersion, :merchantId, :merchantName, :businessNo,
                 :relatedBusinessNo, :transactionId, :transactionDateTime, :sourceOccurredAt,
                 :syncTime, :syncTime)
                """, addSourceProjection(new MapSqlParameterSource(), source)
                .addValue("workItemNo", newWorkItemNo(syncTime))
                .addValue("sourceType", source.identity().sourceType())
                .addValue("sourceKey", source.identity().sourceKey())
                .addValue("workStatus", desiredStatus)
                .addValue("priority", source.priority())
                .addValue("ownerAccountId", desiredOwnerId)
                .addValue("ownerAccountName", desiredOwnerName)
                .addValue("slaDueAt", source.slaDueAt())
                .addValue("firstDetectedAt", source.firstDetectedAt())
                .addValue("syncTime", syncTime));
    }

    /**
     * 绑定列表检索和商户数据范围校验所需的最小来源投影。
     *
     * <p>金额、币种、费率、失败原因等业务事实仍只从来源表实时读取。</p>
     */
    private MapSqlParameterSource addSourceProjection(MapSqlParameterSource params,
                                                      SourceSnapshot source) {
        return params
                .addValue("sourceProjectionVersion", SOURCE_PROJECTION_VERSION)
                .addValue("merchantId", source.merchantId())
                .addValue("merchantName", source.merchantName())
                .addValue("businessNo", source.businessNo())
                .addValue("relatedBusinessNo", source.relatedBusinessNo())
                .addValue("transactionId", source.transactionId())
                .addValue("transactionDateTime", source.transactionDateTime())
                .addValue("sourceOccurredAt", source.sourceOccurredAt());
    }

    private void executeCommand(String workItemNo,
                                String action,
                                ActionRequest request,
                                Long operatorAccountId,
                                String operatorAccountName,
                                Long targetAccountId,
                                String targetAccountName) {
        if (operatorAccountId == null || !StringUtils.hasText(operatorAccountName)) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        transactionTemplate.executeWithoutResult(status -> {
            WorkMetadata current = requiredMetadata(workItemNo, true);
            RequestHistory existingRequest = findHistoryRequest(request.getRequestId());
            if (existingRequest != null) {
                if (current.workItemNo().equals(existingRequest.workItemNo())
                        && action.equals(existingRequest.actionType())) {
                    return;
                }
                throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT.getCode(),
                        "requestId is already associated with another work item action");
            }
            if (!current.version().equals(request.getExpectedVersion())) {
                throw conflict("work item version conflict");
            }
            validateCommand(action, current, targetAccountId);
            boolean preserveOwner = "REMARK".equals(action) || "COMPLETE".equals(action);
            String afterStatus = "REMARK".equals(action)
                    ? current.workStatus()
                    : "COMPLETE".equals(action) ? CLOSED : PROCESSING;
            Long afterOwnerId = preserveOwner ? current.ownerAccountId() : targetAccountId;
            String afterOwnerName = preserveOwner ? current.ownerAccountName() : targetAccountName;
            String remark = trimToNull(request.getRemark());
            LocalDateTime operatedAt = LocalDateTime.now();
            LocalDateTime closedAt = "COMPLETE".equals(action) ? operatedAt : current.closedAt();
            String closeReason = "COMPLETE".equals(action) ? "MANUAL_COMPLETE" : current.closeReason();
            int updated = jdbcTemplate.update("""
                    UPDATE admin_operational_work_item
                    SET work_status = :afterStatus,
                        owner_account_id = :afterOwnerAccountId,
                        owner_account_name = :afterOwnerAccountName,
                        closed_at = :closedAt,
                        close_reason = :closeReason,
                        latest_remark = COALESCE(:remark, latest_remark),
                        version = version + 1,
                        update_time = :operatedAt
                    WHERE id = :id AND work_status = :beforeStatus AND version = :expectedVersion
                    """, new MapSqlParameterSource()
                    .addValue("afterStatus", afterStatus)
                    .addValue("afterOwnerAccountId", afterOwnerId)
                    .addValue("afterOwnerAccountName", afterOwnerName)
                    .addValue("closedAt", closedAt)
                    .addValue("closeReason", closeReason)
                    .addValue("remark", remark)
                    .addValue("operatedAt", operatedAt)
                    .addValue("id", current.id())
                    .addValue("beforeStatus", current.workStatus())
                    .addValue("expectedVersion", current.version()));
            if (updated != 1) {
                throw conflict("work item state conflict");
            }
            insertHistory(current, request.getRequestId().trim(), action,
                    current.workStatus(), afterStatus,
                    current.ownerAccountId(), current.ownerAccountName(),
                    afterOwnerId, afterOwnerName,
                    operatorAccountId, operatorAccountName, remark, operatedAt);
        });
    }

    private void validateCommand(String action, WorkMetadata current, Long targetAccountId) {
        if (isSourceManaged(current.sourceType()) && !"REMARK".equals(action)) {
            throw conflict("source-managed work item must be handled in its source workflow");
        }
        if (!"REMARK".equals(action) && CLOSED.equals(current.workStatus())) {
            throw conflict("completed work item cannot be changed");
        }
        if ("CLAIM".equals(action) && current.ownerAccountId() != null) {
            throw conflict("work item is already assigned");
        }
        if ("TRANSFER".equals(action)) {
            if (current.ownerAccountId() == null) {
                throw conflict("unassigned work item must be assigned before transfer");
            }
            if (current.ownerAccountId().equals(targetAccountId)) {
                throw conflict("target account must differ from current owner");
            }
        }
    }

    private void insertHistory(WorkMetadata metadata,
                               String requestId,
                               String actionType,
                               String beforeStatus,
                               String afterStatus,
                               Long beforeOwnerAccountId,
                               String beforeOwnerAccountName,
                               Long afterOwnerAccountId,
                               String afterOwnerAccountName,
                               Long operatorAccountId,
                               String operatorAccountName,
                               String remark,
                               LocalDateTime operatedAt) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operational_work_item_history
                (work_item_id, work_item_no, request_id, action_type,
                 before_status, after_status,
                 before_owner_account_id, before_owner_account_name,
                 after_owner_account_id, after_owner_account_name,
                 operator_account_id, operator_account_name, remark, operated_at, create_time)
                VALUES
                (:workItemId, :workItemNo, :requestId, :actionType,
                 :beforeStatus, :afterStatus,
                 :beforeOwnerAccountId, :beforeOwnerAccountName,
                 :afterOwnerAccountId, :afterOwnerAccountName,
                 :operatorAccountId, :operatorAccountName, :remark, :operatedAt, :operatedAt)
                """, new MapSqlParameterSource()
                .addValue("workItemId", metadata.id())
                .addValue("workItemNo", metadata.workItemNo())
                .addValue("requestId", requestId)
                .addValue("actionType", actionType)
                .addValue("beforeStatus", beforeStatus)
                .addValue("afterStatus", afterStatus)
                .addValue("beforeOwnerAccountId", beforeOwnerAccountId)
                .addValue("beforeOwnerAccountName", beforeOwnerAccountName)
                .addValue("afterOwnerAccountId", afterOwnerAccountId)
                .addValue("afterOwnerAccountName", afterOwnerAccountName)
                .addValue("operatorAccountId", operatorAccountId)
                .addValue("operatorAccountName", operatorAccountName)
                .addValue("remark", remark)
                .addValue("operatedAt", operatedAt));
    }

    /**
     * 按最新操作优先的稳定顺序分页读取处理历史。
     */
    private PageResult<WorkItemHistory> loadHistoryPage(Long workItemId,
                                                        int pageNo,
                                                        int pageSize) {
        MapSqlParameterSource countParams = new MapSqlParameterSource("workItemId", workItemId);
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM admin_operational_work_item_history
                WHERE work_item_id = :workItemId
                """, countParams, Long.class);
        long total = count == null ? 0L : count;
        long offset = (long) (pageNo - 1) * pageSize;
        List<WorkItemHistory> records = offset >= total ? List.of() : jdbcTemplate.query("""
                SELECT id, action_type, before_status, after_status,
                       before_owner_account_id, before_owner_account_name,
                       after_owner_account_id, after_owner_account_name,
                       operator_account_id, operator_account_name, remark, operated_at
                FROM admin_operational_work_item_history
                WHERE work_item_id = :workItemId
                ORDER BY operated_at DESC, id DESC
                LIMIT :offset, :limit
                """, new MapSqlParameterSource()
                .addValue("workItemId", workItemId)
                .addValue("offset", offset)
                .addValue("limit", pageSize), (rs, rowNum) -> {
            WorkItemHistory history = new WorkItemHistory();
            history.setId(rs.getLong("id"));
            history.setActionType(rs.getString("action_type"));
            history.setBeforeStatus(rs.getString("before_status"));
            history.setAfterStatus(rs.getString("after_status"));
            history.setBeforeOwnerAccountId(nullableLong(rs, "before_owner_account_id"));
            history.setBeforeOwnerAccountName(rs.getString("before_owner_account_name"));
            history.setAfterOwnerAccountId(nullableLong(rs, "after_owner_account_id"));
            history.setAfterOwnerAccountName(rs.getString("after_owner_account_name"));
            history.setOperatorAccountId(nullableLong(rs, "operator_account_id"));
            history.setOperatorAccountName(rs.getString("operator_account_name"));
            history.setRemark(rs.getString("remark"));
            history.setOperatedAt(localDateTime(rs, "operated_at"));
            return history;
        });
        return PageResult.of(total, pageNo, pageSize, records);
    }

    private WorkMetadata requiredMetadata(String workItemNo, boolean forUpdate) {
        if (!StringUtils.hasText(workItemNo)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        List<WorkMetadata> rows = jdbcTemplate.query("SELECT " + WORK_METADATA_COLUMNS + """
                FROM admin_operational_work_item
                WHERE work_item_no = :workItemNo
                """ + (forUpdate ? " FOR UPDATE" : ""),
                new MapSqlParameterSource("workItemNo", workItemNo.trim()), this::mapMetadata);
        if (rows.isEmpty()) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND);
        }
        return rows.get(0);
    }

    private WorkMetadata requiredMetadataBySource(SourceIdentity identity) {
        List<WorkMetadata> rows = jdbcTemplate.query("SELECT " + WORK_METADATA_COLUMNS + """
                FROM admin_operational_work_item
                WHERE source_type = :sourceType AND source_key = :sourceKey
                """, new MapSqlParameterSource()
                .addValue("sourceType", identity.sourceType())
                .addValue("sourceKey", identity.sourceKey()), this::mapMetadata);
        if (rows.isEmpty()) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        return rows.get(0);
    }

    private WorkMetadata mapMetadata(ResultSet rs, int rowNum) throws SQLException {
        return new WorkMetadata(
                rs.getLong("id"), rs.getString("work_item_no"), rs.getString("source_type"),
                rs.getString("source_key"), rs.getString("work_status"), rs.getString("priority"),
                nullableLong(rs, "owner_account_id"), rs.getString("owner_account_name"),
                localDateTime(rs, "sla_due_at"), localDateTime(rs, "first_detected_at"),
                localDateTime(rs, "last_synced_at"), localDateTime(rs, "closed_at"),
                rs.getString("close_reason"), rs.getString("latest_remark"), rs.getLong("version"),
                rs.getInt("source_projection_version"), rs.getString("merchant_id"),
                rs.getString("merchant_name"), rs.getString("business_no"),
                rs.getString("related_business_no"), rs.getString("transaction_id"),
                localDateTime(rs, "transaction_date_time"), localDateTime(rs, "source_occurred_at"),
                localDateTime(rs, "create_time"), localDateTime(rs, "update_time"));
    }

    private SourceSnapshot snapshot(String sourceType, ResultSet rs) throws SQLException {
        String sourceKey = rs.getString("source_key");
        return new SourceSnapshot(new SourceIdentity(sourceType, sourceKey),
                rs.getString("source_status"), rs.getString("merchant_id"),
                rs.getString("merchant_name"), rs.getString("business_no"),
                rs.getString("related_business_no"), rs.getString("transaction_id"),
                localDateTime(rs, "transaction_date_time"), rs.getString("currency"),
                decimalString(rs, "amount"), nullableInteger(rs, "currency_exponent"),
                rs.getString("rate_type"), rs.getString("base_currency"),
                rs.getString("quote_currency"), rs.getString("alert_level"),
                nullableInteger(rs, "attempt_count"), rs.getString("failure_reason"),
                localDateTime(rs, "source_occurred_at"), null, null,
                localDateTime(rs, "source_due_at"), null, null, null, null, null);
    }

    /**
     * 将渠道案件负责人标识转换为统一待办可展示的管理账号投影。
     */
    private SourceSnapshot channelAbnormalSnapshot(ResultSet rs) throws SQLException {
        SourceSnapshot source = snapshot(CHANNEL_MATCH_ABNORMAL, rs);
        Long sourceOwnerAccountId = parseAdminAccountId(rs.getString("assigned_to_id"));
        String sourceOwnerAccountName = sourceOwnerAccountId == null
                ? null : trimToNull(rs.getString("assigned_to_name"));
        if (sourceOwnerAccountId != null && sourceOwnerAccountName == null) {
            sourceOwnerAccountName = String.valueOf(sourceOwnerAccountId);
        }
        return new SourceSnapshot(source.identity(), source.sourceStatus(), source.merchantId(),
                source.merchantName(), source.businessNo(), source.relatedBusinessNo(),
                source.transactionId(), source.transactionDateTime(), source.currency(), source.amount(),
                source.currencyExponent(), source.rateType(), source.baseCurrency(), source.quoteCurrency(),
                source.alertLevel(), source.attemptCount(), source.failureReason(), source.sourceOccurredAt(),
                source.failureStage(), source.failureCode(), source.sourceDueAt(), sourceOwnerAccountId,
                sourceOwnerAccountName, null, null, null);
    }

    /**
     * 将结算批次专属的失败阶段和失败码加入统一来源摘要。
     */
    private SourceSnapshot settlementBacklogSnapshot(ResultSet rs) throws SQLException {
        SourceSnapshot source = snapshot(SETTLEMENT_BACKLOG, rs);
        return new SourceSnapshot(source.identity(), source.sourceStatus(), source.merchantId(),
                source.merchantName(), source.businessNo(), source.relatedBusinessNo(),
                source.transactionId(), source.transactionDateTime(), source.currency(), source.amount(),
                source.currencyExponent(), source.rateType(), source.baseCurrency(), source.quoteCurrency(),
                source.alertLevel(), source.attemptCount(), source.failureReason(), source.sourceOccurredAt(),
                rs.getString("failure_stage"), rs.getString("failure_code"), source.sourceDueAt(),
                null, null, null, null, null);
    }

    private WorkItemSummary toSummary(WorkMetadata metadata, SourceSnapshot source) {
        WorkItemSummary row = new WorkItemSummary();
        row.setWorkItemNo(metadata.workItemNo());
        row.setSourceType(metadata.sourceType());
        row.setSourceKey(metadata.sourceKey());
        row.setWorkStatus(metadata.workStatus());
        row.setPriority(metadata.priority());
        row.setOwnerAccountId(metadata.ownerAccountId());
        row.setOwnerAccountName(metadata.ownerAccountName());
        row.setSlaDueAt(metadata.slaDueAt());
        row.setOverdue(!CLOSED.equals(metadata.workStatus()) && metadata.slaDueAt() != null
                && metadata.slaDueAt().isBefore(LocalDateTime.now()));
        row.setFirstDetectedAt(metadata.firstDetectedAt());
        row.setLastSyncedAt(metadata.lastSyncedAt());
        row.setClosedAt(metadata.closedAt());
        row.setCloseReason(metadata.closeReason());
        row.setLatestRemark(metadata.latestRemark());
        row.setVersion(metadata.version());
        row.setSourceAvailable(source != null);
        row.setSourceManaged(isSourceManaged(metadata.sourceType()));
        if (source != null) {
            row.setSourceStatus(source.sourceStatus());
            row.setMerchantId(source.merchantId());
            row.setMerchantName(source.merchantName());
            row.setBusinessNo(source.businessNo());
            row.setRelatedBusinessNo(source.relatedBusinessNo());
            row.setTransactionId(source.transactionId());
            row.setTransactionDateTime(source.transactionDateTime());
            row.setCurrency(source.currency());
            row.setAmount(source.amount());
            row.setCurrencyExponent(source.currencyExponent());
            row.setRateType(source.rateType());
            row.setBaseCurrency(source.baseCurrency());
            row.setQuoteCurrency(source.quoteCurrency());
            row.setAlertLevel(source.alertLevel());
            row.setAttemptCount(source.attemptCount());
            row.setFailureStage(source.failureStage());
            row.setFailureCode(source.failureCode());
            row.setFailureReason(source.failureReason());
            row.setSourceOccurredAt(source.sourceOccurredAt());
        }
        return row;
    }

    private SearchRequest normalize(SearchRequest request) {
        SearchRequest query = request == null ? new SearchRequest() : request;
        query.setSourceType(normalizedEnum(query.getSourceType(), SOURCE_TYPES, "sourceType"));
        query.setWorkStatus(normalizedEnum(query.getWorkStatus(), WORK_STATUSES, "workStatus"));
        query.setPriority(normalizedEnum(query.getPriority(), PRIORITIES, "priority"));
        int pageNo = query.getPageNo() == null ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null ? DEFAULT_PAGE_SIZE : query.getPageSize();
        if (pageNo < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE
                || query.getDetectedStartTime() != null && query.getDetectedEndTime() != null
                && !query.getDetectedStartTime().isBefore(query.getDetectedEndTime())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        if (Boolean.TRUE.equals(query.getMineOnly()) && Boolean.TRUE.equals(query.getUnassignedOnly())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "mineOnly and unassignedOnly cannot both be true");
        }
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return query;
    }

    private String normalizedEnum(String value, Set<String> allowed, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), field + " is invalid");
        }
        return normalized;
    }

    private void addEquals(StringBuilder where,
                           MapSqlParameterSource params,
                           String column,
                           String parameter,
                           String value) {
        if (StringUtils.hasText(value)) {
            where.append(" AND ").append(column).append(" = :").append(parameter);
            params.addValue(parameter, value.trim());
        }
    }

    private MapSqlParameterSource sourceParams(Collection<String> keys, AdminMerchantDataScope scope) {
        return new MapSqlParameterSource()
                .addValue("sourceKeys", keys)
                .addValue("merchantIds", scope.merchantIds());
    }

    private String merchantScopeSql(AdminMerchantDataScope scope, String column) {
        if (scope.allMerchants()) {
            return "";
        }
        if (scope.empty()) {
            return " AND 1 = 0";
        }
        return " AND " + column + " IN (:merchantIds)";
    }

    private void forEachKeyBatch(Set<String> keys, java.util.function.Consumer<List<String>> consumer) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<String> values = new ArrayList<>(keys);
        for (int from = 0; from < values.size(); from += KEY_BATCH_SIZE) {
            consumer.accept(values.subList(from, Math.min(from + KEY_BATCH_SIZE, values.size())));
        }
    }

    private Assignee requiredAssignee(Long appId, Long accountId) {
        if (appId == null || accountId == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        List<Assignee> rows = jdbcTemplate.query("""
                SELECT account.id AS account_id,
                       COALESCE(NULLIF(user.real_name, ''), account.login_account) AS account_name
                FROM sys_account account
                JOIN sys_app app ON app.id = account.app_id AND app.deleted = 0
                LEFT JOIN sys_user user ON user.id = account.user_id AND user.deleted = 0
                WHERE account.id = :accountId AND account.app_id = :appId
                  AND app.app_code = 'ADMIN' AND app.status = 1
                  AND account.status = 1 AND account.locked = 0
                  AND account.activation_status = 'ACTIVE' AND account.deleted = 0
                  AND (user.id IS NULL OR user.status = 1)
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("appId", appId),
                (rs, rowNum) -> new Assignee(rs.getLong("account_id"), rs.getString("account_name")));
        if (rows.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "target account is unavailable");
        }
        return rows.get(0);
    }

    private RequestHistory findHistoryRequest(String requestId) {
        List<RequestHistory> rows = jdbcTemplate.query("""
                SELECT work_item_no, action_type
                FROM admin_operational_work_item_history
                WHERE request_id = :requestId
                LIMIT 1
                """, new MapSqlParameterSource("requestId", requestId.trim()),
                (rs, rowNum) -> new RequestHistory(
                        rs.getString("work_item_no"), rs.getString("action_type")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ActionRequest requireAction(ActionRequest request) {
        if (request == null || !StringUtils.hasText(request.getRequestId())
                || request.getExpectedVersion() == null
                || request.getRequestId().trim().length() > 128
                || request.getRemark() != null && request.getRemark().trim().length() > 1000) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return request;
    }

    private ActionRequest requireTarget(ActionRequest request) {
        ActionRequest command = requireAction(request);
        if (command.getTargetAccountId() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "targetAccountId must not be null");
        }
        return command;
    }

    private AdminMerchantDataScope requireScope(AdminMerchantDataScope scope) {
        if (scope == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return scope;
    }

    private ServiceException conflict(String message) {
        return new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), message);
    }

    private String alertPriority(String alertLevel) {
        if ("CRITICAL".equalsIgnoreCase(alertLevel)) {
            return "CRITICAL";
        }
        if ("ERROR".equalsIgnoreCase(alertLevel) || "HIGH".equalsIgnoreCase(alertLevel)) {
            return "HIGH";
        }
        return "NORMAL";
    }

    /**
     * 按支付核心异常等级映射统一待办优先级。
     */
    private String channelAbnormalPriority(String abnormalLevel) {
        if ("CRITICAL".equalsIgnoreCase(abnormalLevel)) {
            return "CRITICAL";
        }
        if ("HIGH".equalsIgnoreCase(abnormalLevel)) {
            return "HIGH";
        }
        return "NORMAL";
    }

    /**
     * 高风险事件只使用紧急和高优先级；拒绝决策即使风险等级缺失也按高优先级处理。
     */
    private String highRiskEventPriority(String riskLevel) {
        return "CRITICAL".equalsIgnoreCase(riskLevel) ? "CRITICAL" : "HIGH";
    }

    /**
     * 判断来源是否由自身状态机托管负责人和处理状态。
     */
    private boolean isSourceManaged(String sourceType) {
        return CHANNEL_MATCH_ABNORMAL.equals(sourceType)
                || CLEARING_BACKLOG.equals(sourceType)
                || SETTLEMENT_BACKLOG.equals(sourceType);
    }

    /**
     * 解析来源状态在统一待办中的活动状态投影。
     */
    private String desiredWorkStatus(SourceSnapshot source) {
        if (isSourceManaged(source.identity().sourceType())
                && PROCESSING.equalsIgnoreCase(source.sourceStatus())) {
            return PROCESSING;
        }
        return OPEN;
    }

    /**
     * 来源托管型待办沿用来源负责人，其他来源保持统一待办独立分派。
     */
    private Long desiredOwnerAccountId(SourceSnapshot source) {
        return isSourceManaged(source.identity().sourceType()) ? source.sourceOwnerAccountId() : null;
    }

    /**
     * 来源托管型待办沿用来源负责人名称，其他来源保持统一待办独立分派。
     */
    private String desiredOwnerAccountName(SourceSnapshot source) {
        return isSourceManaged(source.identity().sourceType()) ? source.sourceOwnerAccountName() : null;
    }

    /**
     * 解析支付核心保存的可信管理账号标识；其他调用方或损坏标识不投影为待办负责人。
     */
    private Long parseAdminAccountId(String operatorId) {
        if (!StringUtils.hasText(operatorId) || !operatorId.startsWith(ADMIN_ACCOUNT_PREFIX)) {
            return null;
        }
        try {
            long accountId = Long.parseLong(operatorId.substring(ADMIN_ACCOUNT_PREFIX.length()));
            return accountId > 0L ? accountId : null;
        } catch (NumberFormatException ignored) {
            log.warn("渠道异常负责人标识格式无效, event: ADMIN_OPERATIONAL_WORK_ITEM_SOURCE_OWNER_INVALID, operatorId: {}",
                    operatorId);
            return null;
        }
    }

    private int priorityRank(String priority) {
        return switch (priority == null ? "" : priority) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "NORMAL" -> 2;
            default -> 3;
        };
    }

    private String newWorkItemNo(LocalDateTime syncTime) {
        return "OWI" + String.format("%04d%02d%02d", syncTime.getYear(), syncTime.getMonthValue(),
                syncTime.getDayOfMonth()) + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                .toUpperCase(Locale.ROOT);
    }

    private String syncRequestId(String requestKey, SourceIdentity identity, String action) {
        String seed = requestKey.trim() + ":" + identity.sourceType() + ":" + identity.sourceKey() + ":" + action;
        return "SYNC:" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String decimalString(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.toPlainString();
    }

    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private record SourceIdentity(String sourceType, String sourceKey) {
    }

    private record SearchSql(String whereClause, MapSqlParameterSource parameters) {
    }

    private record WorkMetadata(Long id,
                                String workItemNo,
                                String sourceType,
                                String sourceKey,
                                String workStatus,
                                String priority,
                                Long ownerAccountId,
                                String ownerAccountName,
                                LocalDateTime slaDueAt,
                                LocalDateTime firstDetectedAt,
                                LocalDateTime lastSyncedAt,
                                LocalDateTime closedAt,
                                String closeReason,
                                String latestRemark,
                                Long version,
                                Integer sourceProjectionVersion,
                                String merchantId,
                                String merchantName,
                                String businessNo,
                                String relatedBusinessNo,
                                String transactionId,
                                LocalDateTime transactionDateTime,
                                LocalDateTime sourceOccurredAt,
                                LocalDateTime createTime,
                                LocalDateTime updateTime) {
        private SourceIdentity identity() {
            return new SourceIdentity(sourceType, sourceKey);
        }
    }

    private record SourceSnapshot(SourceIdentity identity,
                                  String sourceStatus,
                                  String merchantId,
                                  String merchantName,
                                  String businessNo,
                                  String relatedBusinessNo,
                                  String transactionId,
                                  LocalDateTime transactionDateTime,
                                  String currency,
                                  String amount,
                                  Integer currencyExponent,
                                  String rateType,
                                  String baseCurrency,
                                  String quoteCurrency,
                                  String alertLevel,
                                  Integer attemptCount,
                                  String failureReason,
                                  LocalDateTime sourceOccurredAt,
                                  String failureStage,
                                  String failureCode,
                                  LocalDateTime sourceDueAt,
                                  Long sourceOwnerAccountId,
                                  String sourceOwnerAccountName,
                                  String priority,
                                  LocalDateTime firstDetectedAt,
                                  LocalDateTime slaDueAt) {
        private SourceSnapshot withOperationalFields(String newPriority,
                                                     LocalDateTime newFirstDetectedAt,
                                                     LocalDateTime newSlaDueAt) {
            return new SourceSnapshot(identity, sourceStatus, merchantId, merchantName, businessNo,
                    relatedBusinessNo, transactionId, transactionDateTime, currency, amount,
                    currencyExponent, rateType, baseCurrency, quoteCurrency, alertLevel, attemptCount,
                    failureReason, sourceOccurredAt, failureStage, failureCode, sourceDueAt, sourceOwnerAccountId,
                    sourceOwnerAccountName, newPriority, newFirstDetectedAt, newSlaDueAt);
        }
    }

    private record Assignee(Long accountId, String accountName) {
    }

    private record RequestHistory(String workItemNo, String actionType) {
    }

    private record SyncCounters(int created, int reopened, int closed, int refreshed) {
    }
}
