package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalSummary;
import com.scott.payment.admin.service.AdminChannelMatchAbnormalQueryService;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminChannelMatchAbnormalQueryService
 * @date : 2026-08-08 00:30
 * @email : scott_x@163.com
 * @description : 管理端勾兑异常 JDBC 查询实现，通过 transaction 逻辑数据源读取副本分片并在 service-admin 内完成案件分页、统计和详情装配。
 * @status : create
 */
@Service
public class JdbcAdminChannelMatchAbnormalQueryService
        implements AdminChannelMatchAbnormalQueryService {

    /** 交易数据统一以 Asia/Shanghai 解释并路由季度分片。 */
    private static final String STORAGE_TIME_ZONE = TransactionShardingProperties.REQUIRED_ZONE_ID;

    /** 案件列表与详情固定投影，不接受外部字段名或表名。 */
    private static final String ABNORMAL_COLUMNS = """
            abnormal_event_id, transaction_id, operation_id, abnormal_type, abnormal_level,
            event_status, source_record_type, source_record_id, abnormal_description,
            raw_reference_json, first_seen_time, last_seen_time, resolved_time,
            transaction_date_time, source_transaction_date_time, root_transaction_date_time,
            merchant_id, merchant_order_no, source_transaction_id, transaction_type,
            platform_status, channel_code, channel_order_no, channel_transaction_id,
            channel_status, channel_match_result, detect_source, platform_currency,
            platform_amount, channel_currency, channel_amount, amount_difference,
            currency_exponent, occurrence_count, assigned_to_id, assigned_to_name,
            assigned_time, resolution_type, resolution_reference_id, merchant_notify_required,
            version, create_time, update_time
            """;

    /** JDBC 查询模板，生产环境应用统一交易查询超时。 */
    private final NamedParameterJdbcTemplate jdbcTemplate;
    /** 交易逻辑数据源普通读执行器，由 ShardingSphere 决定副本节点。 */
    private final TransactionLogicalReadExecutor transactionLogicalReadExecutor;
    /** 管理端现有交易详情查询服务，用于复用生命周期时间线。 */
    private final AdminTransactionQueryService transactionQueryService;
    /** 单页允许返回的最大记录数。 */
    private final int maxResultRows;
    /** 当前已发布交易分片的最早起始时间。 */
    private final LocalDateTime registeredNodeBegin;

    /**
     * 创建生产环境管理端勾兑异常查询服务。
     *
     * @param dataSource dynamic-datasource 外层路由数据源
     * @param transactionLogicalReadExecutor 交易逻辑数据源普通读执行器
     * @param transactionQueryService 管理端交易详情查询服务
     * @param shardingProperties 已发布分片与查询预算配置
     * @param queryJdbcTemplateFactory 交易查询 JDBC 模板工厂
     */
    @Autowired
    public JdbcAdminChannelMatchAbnormalQueryService(
            DataSource dataSource,
            TransactionLogicalReadExecutor transactionLogicalReadExecutor,
            AdminTransactionQueryService transactionQueryService,
            TransactionShardingProperties shardingProperties,
            TransactionQueryJdbcTemplateFactory queryJdbcTemplateFactory) {
        this(queryJdbcTemplateFactory.create(dataSource, shardingProperties),
                transactionLogicalReadExecutor, transactionQueryService, shardingProperties);
    }

    /**
     * 创建可注入 JDBC 模板的管理端勾兑异常查询服务。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     * @param transactionLogicalReadExecutor 交易逻辑数据源普通读执行器
     * @param transactionQueryService 管理端交易详情查询服务
     * @param shardingProperties 已发布分片与查询预算配置
     */
    public JdbcAdminChannelMatchAbnormalQueryService(
            NamedParameterJdbcTemplate jdbcTemplate,
            TransactionLogicalReadExecutor transactionLogicalReadExecutor,
            AdminTransactionQueryService transactionQueryService,
            TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionLogicalReadExecutor = transactionLogicalReadExecutor;
        this.transactionQueryService = transactionQueryService;
        this.maxResultRows = shardingProperties.getQueryBudget().getMaxResultRows();
        this.registeredNodeBegin = resolveRegisteredNodeBegin(shardingProperties.getPhysicalNodes());
    }

    /**
     * 查询勾兑异常案件分页及当前筛选条件下的状态统计。
     *
     * @param query 案件筛选、时间范围和分页条件
     * @return 案件分页和状态统计
     */
    @Override
    public AbnormalSearchResponse search(AbnormalQuery query) {
        AbnormalQuery safeQuery = normalize(query);
        return transactionLogicalReadExecutor.read(() -> searchNormalized(safeQuery));
    }

    /**
     * 使用案件号和真实分片时间查询案件聚合详情。
     *
     * @param eventId 勾兑异常案件号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 案件记录和交易生命周期详情
     */
    @Override
    public AbnormalDetailResponse detail(String eventId, LocalDateTime transactionDateTime) {
        if (!StringUtils.hasText(eventId) || transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return transactionLogicalReadExecutor.read(
                () -> detailNormalized(eventId, transactionDateTime));
    }

    /** 在已归一化的存储时区范围内执行案件分页和统计查询。 */
    private AbnormalSearchResponse searchNormalized(AbnormalQuery query) {
        String whereSql = whereSql(query);
        MapSqlParameterSource parameters = parameters(query);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM transaction_abnormal_event " + whereSql,
                parameters, Long.class);
        long total = count == null ? 0L : count;
        long pageNo = query.safePageNo();
        long pageSize = query.safePageSize();
        long offset = (pageNo - 1L) * pageSize;
        List<AbnormalRecord> records = offset < total
                ? jdbcTemplate.query(
                        "SELECT " + ABNORMAL_COLUMNS + " FROM transaction_abnormal_event "
                                + whereSql + """
                                ORDER BY first_seen_time DESC, id DESC
                                LIMIT :offset, :limit
                                """,
                        new MapSqlParameterSource(parameters.getValues())
                                .addValue("offset", offset)
                                .addValue("limit", pageSize),
                        abnormalRecordMapper())
                : List.of();
        AbnormalSearchResponse response = new AbnormalSearchResponse();
        response.setPage(PageResult.of(total, pageNo, pageSize, records));
        response.setSummary(loadSummary(whereSql, parameters));
        return response;
    }

    /** 使用案件号和精确分片时间查询详情，并复用现有管理端交易时间线。 */
    @SuppressWarnings("unchecked")
    private AbnormalDetailResponse detailNormalized(String eventId,
                                                    LocalDateTime transactionDateTime) {
        List<AbnormalRecord> records = jdbcTemplate.query(
                "SELECT " + ABNORMAL_COLUMNS + """
                        FROM transaction_abnormal_event
                        WHERE abnormal_event_id = :eventId
                          AND transaction_date_time = :transactionDateTime
                          AND deleted = 0
                        LIMIT 1
                        """,
                new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("transactionDateTime", transactionDateTime),
                abnormalRecordMapper());
        if (records.isEmpty()) {
            throw new ServiceException(ApiResultEnum.ABNORMAL_CASE_NOT_FOUND);
        }
        AbnormalRecord record = records.get(0);
        AbnormalDetailResponse response = new AbnormalDetailResponse();
        response.setAbnormality(record);
        if (record.getRootTransactionDateTime() != null) {
            Object transactionDetail = transactionQueryService.detail(
                    record.getTransactionId(), record.getTransactionDateTime(),
                    record.getRootTransactionDateTime());
            response.setTransactionDetail(JsonUtils.parseObject(
                    JsonUtils.toJsonString(transactionDetail), Map.class));
        }
        return response;
    }

    /** 读取当前筛选条件下的案件状态和高风险活动案件数量。 */
    private AbnormalSummary loadSummary(String whereSql,
                                        MapSqlParameterSource parameters) {
        AbnormalSummary summary = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) AS total_count,
                       SUM(CASE WHEN event_status = 'OPEN' THEN 1 ELSE 0 END) AS open_count,
                       SUM(CASE WHEN event_status = 'PROCESSING' THEN 1 ELSE 0 END) AS processing_count,
                       SUM(CASE WHEN event_status = 'RESOLVED' THEN 1 ELSE 0 END) AS resolved_count,
                       SUM(CASE WHEN event_status = 'IGNORED' THEN 1 ELSE 0 END) AS ignored_count,
                       SUM(CASE WHEN abnormal_level IN ('HIGH', 'CRITICAL')
                                AND event_status IN ('OPEN', 'PROCESSING') THEN 1 ELSE 0 END)
                           AS high_or_critical_count
                FROM transaction_abnormal_event
                """ + whereSql, parameters, abnormalSummaryMapper());
        return summary == null ? new AbnormalSummary() : summary;
    }

    /** 归一化分页预算、案件时间范围和查询时区。 */
    private AbnormalQuery normalize(AbnormalQuery query) {
        AbnormalQuery safeQuery = query == null ? new AbnormalQuery() : query;
        safeQuery.setPageSize((int) Math.min(safeQuery.safePageSize(), maxResultRows));
        if (safeQuery.getMinimumOccurrenceCount() != null
                && safeQuery.getMinimumOccurrenceCount() <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        ZoneId queryZone = resolveQueryZone(safeQuery.getQueryTimeZone());
        ZoneId storageZone = ZoneId.of(STORAGE_TIME_ZONE);
        boolean useRegisteredNodeBegin = safeQuery.getBeginTime() == null;
        LocalDateTime queryBegin = safeQuery.getBeginTime();
        LocalDateTime queryEnd = safeQuery.getEndTime() == null
                ? LocalDateTime.now(queryZone) : safeQuery.getEndTime();
        if (!useRegisteredNodeBegin && queryBegin.isAfter(queryEnd)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "abnormal time range is invalid");
        }
        safeQuery.setBeginTime(useRegisteredNodeBegin
                ? registeredNodeBegin : convertBetweenZones(queryBegin, queryZone, storageZone));
        safeQuery.setEndTime(convertBetweenZones(queryEnd, queryZone, storageZone));
        safeQuery.setQueryTimeZone(storageZone.getId());
        return safeQuery;
    }

    /** 构建固定字段白名单下的案件筛选 SQL。 */
    private String whereSql(AbnormalQuery query) {
        StringBuilder sql = new StringBuilder("""
                WHERE deleted = 0
                  AND transaction_date_time >= :beginTime
                  AND transaction_date_time < :endTimeExclusive
                """);
        appendTextFilter(sql, query.getEventId(), "AND abnormal_event_id = :eventId");
        appendTextFilter(sql, query.getTransactionId(), "AND transaction_id = :transactionId");
        appendTextFilter(sql, query.getMerchantId(), "AND merchant_id = :merchantId");
        appendTextFilter(sql, query.getMerchantOrderNo(), "AND merchant_order_no = :merchantOrderNo");
        appendTextFilter(sql, query.getAbnormalType(), "AND abnormal_type = :abnormalType");
        appendTextFilter(sql, query.getAbnormalLevel(), "AND abnormal_level = :abnormalLevel");
        appendTextFilter(sql, query.getEventStatus(), "AND event_status = :eventStatus");
        appendTextFilter(sql, query.getTransactionType(), "AND transaction_type = :transactionType");
        appendTextFilter(sql, query.getPlatformStatus(), "AND platform_status = :platformStatus");
        appendTextFilter(sql, query.getChannelCode(), "AND channel_code = :channelCode");
        appendTextFilter(sql, query.getChannelOrderNo(), "AND channel_order_no = :channelOrderNo");
        appendTextFilter(sql, query.getAssignedToId(), "AND assigned_to_id = :assignedToId");
        appendTextFilter(sql, query.getDetectSource(), "AND detect_source = :detectSource");
        if (query.getMinimumOccurrenceCount() != null) {
            sql.append(" AND occurrence_count >= :minimumOccurrenceCount");
        }
        return sql.toString();
    }

    /** 构造案件查询全部命名参数，未启用的可空参数不会拼入 SQL。 */
    private MapSqlParameterSource parameters(AbnormalQuery query) {
        return new MapSqlParameterSource()
                .addValue("beginTime", query.getBeginTime())
                .addValue("endTimeExclusive", exclusiveEnd(query.getEndTime()))
                .addValue("eventId", query.getEventId())
                .addValue("transactionId", query.getTransactionId())
                .addValue("merchantId", query.getMerchantId())
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("abnormalType", query.getAbnormalType())
                .addValue("abnormalLevel", query.getAbnormalLevel())
                .addValue("eventStatus", query.getEventStatus())
                .addValue("transactionType", query.getTransactionType())
                .addValue("platformStatus", query.getPlatformStatus())
                .addValue("channelCode", query.getChannelCode())
                .addValue("channelOrderNo", query.getChannelOrderNo())
                .addValue("assignedToId", query.getAssignedToId())
                .addValue("detectSource", query.getDetectSource())
                .addValue("minimumOccurrenceCount", query.getMinimumOccurrenceCount());
    }

    /** 返回按下划线列名映射案件 DTO 的行映射器。 */
    private RowMapper<AbnormalRecord> abnormalRecordMapper() {
        return BeanPropertyRowMapper.newInstance(AbnormalRecord.class);
    }

    /** 返回案件状态统计行映射器，并把 SQL NULL 归一化为零。 */
    private RowMapper<AbnormalSummary> abnormalSummaryMapper() {
        return (resultSet, rowNumber) -> {
            AbnormalSummary summary = new AbnormalSummary();
            summary.setTotalCount(resultSet.getLong("total_count"));
            summary.setOpenCount(resultSet.getLong("open_count"));
            summary.setProcessingCount(resultSet.getLong("processing_count"));
            summary.setResolvedCount(resultSet.getLong("resolved_count"));
            summary.setIgnoredCount(resultSet.getLong("ignored_count"));
            summary.setHighOrCriticalCount(resultSet.getLong("high_or_critical_count"));
            return summary;
        };
    }

    /** 解析页面查询时区，非法时区在访问数据库前拒绝。 */
    private ZoneId resolveQueryZone(String queryTimeZone) {
        String zone = StringUtils.hasText(queryTimeZone) ? queryTimeZone.trim() : STORAGE_TIME_ZONE;
        try {
            return ZoneId.of(normalizeZoneId(zone));
        } catch (DateTimeException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "queryTimeZone is invalid", exception);
        }
    }

    /** 兼容页面提交的 UTC+8、GMT+8 等短偏移格式。 */
    private String normalizeZoneId(String zone) {
        String normalized = zone.trim();
        String upper = normalized.toUpperCase();
        if ("UTC".equals(upper) || "GMT".equals(upper)) {
            return upper;
        }
        if (upper.startsWith("UTC+") || upper.startsWith("UTC-")
                || upper.startsWith("GMT+") || upper.startsWith("GMT-")) {
            String prefix = upper.substring(0, 3);
            String offset = upper.substring(3);
            if (offset.matches("[+-]\\d{1,2}")) {
                return prefix + String.format("%+03d:00", Integer.parseInt(offset));
            }
            if (offset.matches("[+-]\\d{1,2}:\\d{2}")) {
                String[] parts = offset.substring(1).split(":");
                return prefix + offset.charAt(0)
                        + String.format("%02d:%s", Integer.parseInt(parts[0]), parts[1]);
            }
        }
        return normalized;
    }

    /** 将页面时区时间转换为交易库存储时区时间。 */
    private LocalDateTime convertBetweenZones(LocalDateTime value,
                                              ZoneId sourceZone,
                                              ZoneId targetZone) {
        return value == null ? null
                : value.atZone(sourceZone).withZoneSameInstant(targetZone).toLocalDateTime();
    }

    /** 从已发布季度节点解析查询允许访问的最早时间。 */
    private LocalDateTime resolveRegisteredNodeBegin(List<String> physicalNodes) {
        if (physicalNodes == null || physicalNodes.isEmpty()) {
            return LocalDate.now().withDayOfMonth(1).atStartOfDay();
        }
        return physicalNodes.stream()
                .filter(value -> value != null && value.matches("\\d{4}0[1-4]"))
                .map(this::quarterBegin)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDate.now().withDayOfMonth(1).atStartOfDay());
    }

    /** 将 yyyy0Q 节点后缀转换为季度起始时间。 */
    private LocalDateTime quarterBegin(String suffix) {
        int year = Integer.parseInt(suffix.substring(0, 4));
        int quarter = Integer.parseInt(suffix.substring(5, 6));
        return LocalDateTime.of(year, (quarter - 1) * 3 + 1, 1, 0, 0);
    }

    private void appendTextFilter(StringBuilder sql, String value, String fragment) {
        if (StringUtils.hasText(value)) {
            sql.append(' ').append(fragment);
        }
    }

    private LocalDateTime exclusiveEnd(LocalDateTime value) {
        return value.plusNanos(1_000_000L);
    }
}
