package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.DeliveryLog;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationDetail;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationQuery;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationRecord;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationRetryTarget;
import com.scott.payment.merchant.service.MerchantNotificationQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 商户端交易通知 JDBC 查询实现。
 *
 * <p>所有列表和详情 SQL 都同时绑定认证商户号与交易分片时间。响应仅映射脱敏投影，
 * 不读取回调地址明文、通知载荷明文、地址哈希或管理端诊断字段。</p>
 */
@Service
public class JdbcMerchantNotificationQueryService implements MerchantNotificationQueryService {

    private static final String NOTIFICATION_TABLE = "transaction_merchant_notification";
    private static final String DELIVERY_LOG_TABLE = "transaction_merchant_notification_log";
    private static final String DEFAULT_QUERY_TIME_ZONE = "Asia/Shanghai";
    private static final String NOTIFICATION_LIST_PROJECTION = """
            notify_id, transaction_id, operation_id, merchant_order_no,
            notify_type, event_type, notify_status,
            last_attempt_no, max_retry_count, next_retry_time, success_time, fail_reason,
            transaction_date_time, create_time, update_time
            """;
    private static final String NOTIFICATION_DETAIL_PROJECTION = """
            notify_id, transaction_id, operation_id, merchant_order_no,
            notify_type, event_type, notify_status, target_url_masked,
            payload_json_masked, sign_type, last_attempt_no, max_retry_count,
            next_retry_time, success_time, fail_reason,
            transaction_date_time, create_time, update_time
            """;
    private static final String DELIVERY_LOG_PROJECTION = """
            notify_log_id, notify_id, delivery_mode, attempt_no, http_status,
            request_header_json_masked, request_body_json_masked, response_body_json_masked,
            success, error_message, notify_time, duration_millis, create_time
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionLogicalReadExecutor transactionLogicalReadExecutor;
    private final int maxResultRows;
    private final LocalDateTime registeredNodeBegin;

    /**
     * 创建测试或本地调用使用的商户通知查询服务。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     */
    public JdbcMerchantNotificationQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new TransactionLogicalReadExecutor(), new TransactionShardingProperties());
    }

    /**
     * 创建生产环境商户通知查询服务，并应用交易查询超时与结果行数预算。
     *
     * @param dataSource dynamic-datasource 外层路由数据源
     * @param transactionLogicalReadExecutor 交易逻辑数据源只读执行器
     * @param shardingProperties 交易分片和查询预算配置
     * @param queryJdbcTemplateFactory 查询专用 JDBC 模板工厂
     */
    @Autowired
    public JdbcMerchantNotificationQueryService(
            DataSource dataSource,
            TransactionLogicalReadExecutor transactionLogicalReadExecutor,
            TransactionShardingProperties shardingProperties,
            TransactionQueryJdbcTemplateFactory queryJdbcTemplateFactory) {
        this(queryJdbcTemplateFactory.create(dataSource, shardingProperties),
                transactionLogicalReadExecutor, shardingProperties);
    }

    /**
     * 创建可注入测试依赖的商户通知查询服务。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     * @param transactionLogicalReadExecutor 交易逻辑数据源只读执行器
     * @param shardingProperties 交易分片和查询预算配置
     */
    public JdbcMerchantNotificationQueryService(
            NamedParameterJdbcTemplate jdbcTemplate,
            TransactionLogicalReadExecutor transactionLogicalReadExecutor,
            TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionLogicalReadExecutor = transactionLogicalReadExecutor;
        this.maxResultRows = shardingProperties.getQueryBudget().getMaxResultRows();
        this.registeredNodeBegin = resolveRegisteredNodeBegin(shardingProperties.getPhysicalNodes());
    }

    /**
     * 分页查询认证商户的交易通知任务。
     */
    @Override
    public PageResult<NotificationRecord> page(String merchantId, NotificationQuery query) {
        String safeMerchantId = requireMerchantId(merchantId);
        NotificationQuery safeQuery = normalize(query);
        if (safeQuery.getBeginTime().isAfter(safeQuery.getEndTime())) {
            return PageResult.of(0L, safeQuery.safePageNo(), safeQuery.safePageSize(), List.of());
        }
        return executeRead(false, () -> {
            MapSqlParameterSource parameters = parameters(safeMerchantId, safeQuery);
            String filters = filters(safeQuery);
            Long total = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM %s n
                    WHERE n.deleted = 0
                      AND n.merchant_id = :merchantId
                      AND n.transaction_date_time >= :beginTime
                      AND n.transaction_date_time < :endTime
                    %s
                    """.formatted(NOTIFICATION_TABLE, filters), parameters, Long.class);
            if (total == null || total == 0L) {
                return PageResult.of(0L, safeQuery.safePageNo(), safeQuery.safePageSize(), List.of());
            }
            parameters.addValue("offset", (safeQuery.safePageNo() - 1) * safeQuery.safePageSize())
                    .addValue("limit", safeQuery.safePageSize());
            List<NotificationRecord> records = jdbcTemplate.query("""
                    SELECT %s
                    FROM %s n
                    WHERE n.deleted = 0
                      AND n.merchant_id = :merchantId
                      AND n.transaction_date_time >= :beginTime
                      AND n.transaction_date_time < :endTime
                    %s
                    ORDER BY n.transaction_date_time DESC, n.id DESC
                    LIMIT :offset, :limit
                    """.formatted(NOTIFICATION_LIST_PROJECTION, NOTIFICATION_TABLE, filters),
                    parameters, notificationRecordMapper());
            return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), records);
        });
    }

    /**
     * 按认证商户、通知任务号和精确分片时间读取通知任务快照。
     */
    @Override
    public NotificationDetailResponse detail(
            String merchantId,
            String notifyId,
            LocalDateTime transactionDateTime) {
        String safeMerchantId = requireMerchantId(merchantId);
        if (!StringUtils.hasText(notifyId) || transactionDateTime == null) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID);
        }
        return executeRead(true, () -> {
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("merchantId", safeMerchantId)
                    .addValue("notifyId", notifyId.trim())
                    .addValue("transactionDateTime", transactionDateTime);
            List<NotificationDetail> notifications = jdbcTemplate.query("""
                    SELECT %s
                    FROM %s n
                    WHERE n.deleted = 0
                      AND n.merchant_id = :merchantId
                      AND n.notify_id = :notifyId
                      AND n.transaction_date_time = :transactionDateTime
                    LIMIT 1
                    """.formatted(NOTIFICATION_DETAIL_PROJECTION, NOTIFICATION_TABLE),
                    parameters, notificationDetailMapper());
            if (notifications.isEmpty()) {
                throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
            }
            NotificationDetailResponse response = new NotificationDetailResponse();
            response.setNotification(notifications.get(0));
            return response;
        });
    }

    /**
     * 强一致分页读取认证商户的通知投递日志，保证人工重发后最新尝试立即可见。
     */
    @Override
    public PageResult<DeliveryLog> pageDeliveryLogs(
            String merchantId,
            String notifyId,
            LocalDateTime transactionDateTime,
            long pageNo,
            long pageSize) {
        String safeMerchantId = requireMerchantId(merchantId);
        validateDeliveryLogPage(notifyId, transactionDateTime, pageNo, pageSize);
        return executeRead(true, () -> {
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("merchantId", safeMerchantId)
                    .addValue("notifyId", notifyId.trim())
                    .addValue("transactionDateTime", transactionDateTime)
                    .addValue("offset", (pageNo - 1) * pageSize)
                    .addValue("limit", pageSize);
            Long notificationCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM %s n
                    WHERE n.deleted = 0
                      AND n.merchant_id = :merchantId
                      AND n.notify_id = :notifyId
                      AND n.transaction_date_time = :transactionDateTime
                    """.formatted(NOTIFICATION_TABLE), parameters, Long.class);
            if (notificationCount == null || notificationCount == 0L) {
                throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
            }
            Long total = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM %s l
                    WHERE l.merchant_id = :merchantId
                      AND l.notify_id = :notifyId
                      AND l.transaction_date_time = :transactionDateTime
                    """.formatted(DELIVERY_LOG_TABLE), parameters, Long.class);
            if (total == null || total == 0L) {
                return PageResult.of(0L, pageNo, pageSize, List.of());
            }
            List<DeliveryLog> records = jdbcTemplate.query("""
                    SELECT %s
                    FROM %s l
                    WHERE l.merchant_id = :merchantId
                      AND l.notify_id = :notifyId
                      AND l.transaction_date_time = :transactionDateTime
                    ORDER BY l.attempt_no DESC, l.id DESC
                    LIMIT :offset, :limit
                    """.formatted(DELIVERY_LOG_PROJECTION, DELIVERY_LOG_TABLE),
                    parameters, deliveryLogMapper());
            return PageResult.of(total, pageNo, pageSize, records);
        });
    }

    /**
     * 校验通知投递日志的精确定位与分页预算。
     */
    private void validateDeliveryLogPage(
            String notifyId,
            LocalDateTime transactionDateTime,
            long pageNo,
            long pageSize) {
        long maxPageSize = Math.min(100L, maxResultRows);
        if (!StringUtils.hasText(notifyId)
                || transactionDateTime == null
                || pageNo < 1L
                || pageSize < 1L
                || pageSize > maxPageSize) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /**
     * 强一致读取认证商户可人工重发的精确通知任务。
     *
     * <p>与管理端重发规则保持一致：交易动作必须已经进入成功或失败终态，通知自动计划必须已经
     * 成功、关闭，或者失败且不存在下次自动重试时间。查询同时绑定商户号和任务号，避免跨商户
     * 重发以及同一交易多任务时选错回调快照。</p>
     *
     * @param merchantId 认证上下文中的商户号
     * @param notifyId 通知任务号
     * @param transactionDateTime 原交易真实分片时间
     * @return 可人工重发的精确任务定位
     */
    @Override
    public NotificationRetryTarget retryTarget(String merchantId,
                                               String notifyId,
                                               LocalDateTime transactionDateTime) {
        String safeMerchantId = requireMerchantId(merchantId);
        if (!StringUtils.hasText(notifyId) || transactionDateTime == null) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID);
        }
        return executeRead(true, () -> {
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("merchantId", safeMerchantId)
                    .addValue("notifyId", notifyId.trim())
                    .addValue("transactionDateTime", transactionDateTime);
            List<NotificationRetryTarget> targets = jdbcTemplate.query("""
                    SELECT notification.notify_id,
                           notification.transaction_id,
                           notification.transaction_date_time
                    FROM transaction_merchant_notification notification
                    JOIN transaction_operation operation_record
                      ON operation_record.transaction_id = notification.transaction_id
                     AND operation_record.transaction_date_time = notification.transaction_date_time
                     AND operation_record.merchant_id = notification.merchant_id
                     AND operation_record.deleted = 0
                    WHERE notification.deleted = 0
                      AND notification.merchant_id = :merchantId
                      AND notification.notify_id = :notifyId
                      AND notification.transaction_date_time = :transactionDateTime
                      AND operation_record.transaction_status IN ('SUCCESS', 'FAILED')
                      AND (
                            notification.notify_status IN ('SUCCESS', 'CLOSED')
                            OR (notification.notify_status = 'FAILED'
                                AND notification.next_retry_time IS NULL)
                          )
                    LIMIT 1
                    """, parameters, (resultSet, rowNumber) -> {
                NotificationRetryTarget target = new NotificationRetryTarget();
                target.setNotifyId(resultSet.getString("notify_id"));
                target.setTransactionId(resultSet.getString("transaction_id"));
                target.setTransactionDateTime(localDateTime(resultSet, "transaction_date_time"));
                return target;
            });
            if (targets.isEmpty()) {
                throw new ApiException(
                        ApiResultEnum.ORDER_NOT_FOUND,
                        "notification is not available for manual resend");
            }
            return targets.get(0);
        });
    }

    /**
     * 规范化查询时间、时区和分页预算。
     */
    private NotificationQuery normalize(NotificationQuery query) {
        NotificationQuery safeQuery = query == null ? new NotificationQuery() : query;
        ZoneId queryZone = resolveQueryZone(safeQuery.getQueryTimeZone());
        LocalDateTime queryEnd = safeQuery.getEndTime() == null
                ? LocalDateTime.now(queryZone) : safeQuery.getEndTime();
        LocalDateTime queryBegin = safeQuery.getBeginTime() == null
                ? queryEnd.toLocalDate().atStartOfDay() : safeQuery.getBeginTime();
        if (queryBegin.isAfter(queryEnd)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "beginTime must not be after endTime");
        }
        ZoneId storageZone = ZoneId.of(DEFAULT_QUERY_TIME_ZONE);
        LocalDateTime storageBegin = convertBetweenZones(queryBegin, queryZone, storageZone);
        LocalDateTime storageEnd = convertBetweenZones(queryEnd, queryZone, storageZone);
        LocalDateTime now = LocalDateTime.now(storageZone);
        safeQuery.setBeginTime(storageBegin.isBefore(registeredNodeBegin) ? registeredNodeBegin : storageBegin);
        safeQuery.setEndTime(storageEnd.isAfter(now) ? now : storageEnd);
        safeQuery.setQueryTimeZone(queryZone.getId());
        safeQuery.setPageSize((int) Math.min(safeQuery.safePageSize(), maxResultRows));
        return safeQuery;
    }

    /**
     * 构建始终包含认证商户和半开时间范围的查询参数。
     */
    private MapSqlParameterSource parameters(String merchantId, NotificationQuery query) {
        return new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("beginTime", query.getBeginTime())
                .addValue("endTime", exclusiveEnd(query.getEndTime()))
                .addValue("transactionId", query.getTransactionId())
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("notifyType", query.getNotifyType())
                .addValue("notifyStatus", query.getNotifyStatus());
    }

    /**
     * 构建可选通知筛选谓词，商户和时间谓词固定写在主体 SQL 中。
     */
    private String filters(NotificationQuery query) {
        StringBuilder sql = new StringBuilder();
        append(sql, query.getTransactionId(), "AND n.transaction_id = :transactionId");
        append(sql, query.getMerchantOrderNo(), "AND n.merchant_order_no = :merchantOrderNo");
        append(sql, query.getNotifyType(), "AND n.notify_type = :notifyType");
        append(sql, query.getNotifyStatus(), "AND n.notify_status = :notifyStatus");
        return sql.toString();
    }

    /**
     * 仅为非空文本追加 SQL 筛选条件。
     */
    private void append(StringBuilder sql, String value, String fragment) {
        if (StringUtils.hasText(value)) {
            sql.append(' ').append(fragment);
        }
    }

    /**
     * 映射通知列表行，不把明文地址、载荷和内部失败详情放入响应。
     */
    private RowMapper<NotificationRecord> notificationRecordMapper() {
        return (resultSet, rowNumber) -> {
            NotificationRecord response = new NotificationRecord();
            mapCommonNotificationFields(resultSet, response);
            return response;
        };
    }

    /**
     * 映射通知详情的脱敏快照。
     */
    private RowMapper<NotificationDetail> notificationDetailMapper() {
        return (resultSet, rowNumber) -> {
            NotificationDetail response = new NotificationDetail();
            response.setNotifyId(resultSet.getString("notify_id"));
            response.setTransactionId(resultSet.getString("transaction_id"));
            response.setOperationId(resultSet.getString("operation_id"));
            response.setMerchantOrderNo(resultSet.getString("merchant_order_no"));
            response.setNotifyType(resultSet.getString("notify_type"));
            response.setEventType(resultSet.getString("event_type"));
            response.setNotifyStatus(resultSet.getString("notify_status"));
            response.setTargetUrlMasked(resultSet.getString("target_url_masked"));
            response.setPayloadJsonMasked(resultSet.getString("payload_json_masked"));
            response.setSignType(resultSet.getString("sign_type"));
            response.setLastAttemptNo(nullableInt(resultSet, "last_attempt_no"));
            response.setMaxRetryCount(nullableInt(resultSet, "max_retry_count"));
            response.setNextRetryTime(localDateTime(resultSet, "next_retry_time"));
            response.setSuccessTime(localDateTime(resultSet, "success_time"));
            response.setFailureCode(taskFailureCode(
                    response.getNotifyStatus(), resultSet.getString("fail_reason")));
            response.setTransactionDateTime(localDateTime(resultSet, "transaction_date_time"));
            response.setCreateTime(localDateTime(resultSet, "create_time"));
            response.setUpdateTime(localDateTime(resultSet, "update_time"));
            return response;
        };
    }

    /**
     * 映射单次投递日志，只返回脱敏报文和稳定失败分类。
     */
    private RowMapper<DeliveryLog> deliveryLogMapper() {
        return (resultSet, rowNumber) -> {
            DeliveryLog response = new DeliveryLog();
            response.setNotifyLogId(resultSet.getString("notify_log_id"));
            response.setNotifyId(resultSet.getString("notify_id"));
            response.setDeliveryMode(resultSet.getString("delivery_mode"));
            response.setAttemptNo(nullableInt(resultSet, "attempt_no"));
            response.setHttpStatus(nullableInt(resultSet, "http_status"));
            response.setRequestHeaderJsonMasked(resultSet.getString("request_header_json_masked"));
            response.setRequestBodyJsonMasked(resultSet.getString("request_body_json_masked"));
            response.setResponseBodyJsonMasked(resultSet.getString("response_body_json_masked"));
            response.setSuccess(nullableBoolean(resultSet, "success"));
            response.setFailureCode(deliveryFailureCode(
                    response.getSuccess(), response.getHttpStatus(), resultSet.getString("error_message")));
            response.setNotifyTime(localDateTime(resultSet, "notify_time"));
            response.setResponseTime(localDateTime(resultSet, "create_time"));
            response.setDurationMillis(nullableInt(resultSet, "duration_millis"));
            response.setHttpMethod("POST");
            return response;
        };
    }

    /**
     * 映射列表和详情共同使用的商户可见通知字段。
     */
    private void mapCommonNotificationFields(ResultSet resultSet, NotificationRecord response) throws SQLException {
        response.setNotifyId(resultSet.getString("notify_id"));
        response.setTransactionId(resultSet.getString("transaction_id"));
        response.setOperationId(resultSet.getString("operation_id"));
        response.setMerchantOrderNo(resultSet.getString("merchant_order_no"));
        response.setNotifyType(resultSet.getString("notify_type"));
        response.setEventType(resultSet.getString("event_type"));
        response.setNotifyStatus(resultSet.getString("notify_status"));
        response.setLastAttemptNo(nullableInt(resultSet, "last_attempt_no"));
        response.setMaxRetryCount(nullableInt(resultSet, "max_retry_count"));
        response.setNextRetryTime(localDateTime(resultSet, "next_retry_time"));
        response.setSuccessTime(localDateTime(resultSet, "success_time"));
        response.setFailureCode(taskFailureCode(response.getNotifyStatus(), resultSet.getString("fail_reason")));
        response.setTransactionDateTime(localDateTime(resultSet, "transaction_date_time"));
        response.setCreateTime(localDateTime(resultSet, "create_time"));
        response.setUpdateTime(localDateTime(resultSet, "update_time"));
    }

    /**
     * 将内部任务失败摘要压缩为商户可见的稳定分类编码。
     */
    private String taskFailureCode(String notifyStatus, String failReason) {
        if (!"FAILED".equalsIgnoreCase(notifyStatus) && !"CLOSED".equalsIgnoreCase(notifyStatus)) {
            return null;
        }
        return classifyFailure(failReason, null);
    }

    /**
     * 将投递错误压缩为商户可见分类，不直接返回内部异常类、网络拓扑或堆栈摘要。
     */
    private String deliveryFailureCode(Boolean success, Integer httpStatus, String errorMessage) {
        if (Boolean.TRUE.equals(success)) {
            return null;
        }
        if (httpStatus != null && (httpStatus < 200 || httpStatus >= 300)) {
            return "ENDPOINT_HTTP_ERROR";
        }
        return classifyFailure(errorMessage, httpStatus);
    }

    /**
     * 根据有限关键词生成稳定失败分类，原始文本只用于分类且不会写入响应。
     */
    private String classifyFailure(String errorMessage, Integer httpStatus) {
        if (httpStatus != null && (httpStatus < 200 || httpStatus >= 300)) {
            return "ENDPOINT_HTTP_ERROR";
        }
        String message = errorMessage == null ? "" : errorMessage.toLowerCase(Locale.ROOT);
        if (message.contains("timeout") || message.contains("timed out")) {
            return "ENDPOINT_TIMEOUT";
        }
        if (message.contains("connect") || message.contains("dns") || message.contains("host")) {
            return "ENDPOINT_UNREACHABLE";
        }
        return "DELIVERY_FAILED";
    }

    /**
     * 校验认证上下文中的商户号。
     */
    private String requireMerchantId(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant context missing");
        }
        return merchantId.trim();
    }

    /**
     * 解析查询时区并拒绝非法时区标识。
     */
    private ZoneId resolveQueryZone(String queryTimeZone) {
        String zone = StringUtils.hasText(queryTimeZone) ? queryTimeZone.trim() : DEFAULT_QUERY_TIME_ZONE;
        try {
            return ZoneId.of(normalizeZoneId(zone));
        } catch (DateTimeException exception) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "queryTimeZone is invalid");
        }
    }

    /**
     * 兼容 UTC、GMT 与常用小时偏移格式。
     */
    private String normalizeZoneId(String zone) {
        String normalized = zone.trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
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

    /**
     * 将用户查询时区中的具体时间转换为交易存储时区。
     */
    private LocalDateTime convertBetweenZones(
            LocalDateTime sourceTime,
            ZoneId sourceZone,
            ZoneId targetZone) {
        ZonedDateTime source = sourceTime.atZone(sourceZone);
        return source.withZoneSameInstant(targetZone).toLocalDateTime();
    }

    /**
     * 在交易逻辑数据源执行普通读或主库强一致读。
     */
    private <T> T executeRead(boolean primaryOnly, Supplier<T> query) {
        return primaryOnly
                ? transactionLogicalReadExecutor.readPrimary(query)
                : transactionLogicalReadExecutor.read(query);
    }

    /**
     * 将包含式结束时间转换为 DATETIME(3) 半开区间上界。
     */
    private LocalDateTime exclusiveEnd(LocalDateTime endTime) {
        return endTime.plusNanos(1_000_000L);
    }

    /**
     * 安全读取可空整数列。
     */
    private Integer nullableInt(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    /**
     * 安全读取可空布尔列。
     */
    private Boolean nullableBoolean(ResultSet resultSet, String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? null : value;
    }

    /**
     * 安全读取可空本地时间列。
     */
    private LocalDateTime localDateTime(ResultSet resultSet, String column) throws SQLException {
        java.sql.Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * 将最早已登记季度转换为 ShardingSphere 可路由的范围起点。
     */
    private static LocalDateTime resolveRegisteredNodeBegin(List<String> physicalNodes) {
        return physicalNodes == null ? LocalDateTime.of(1970, 1, 1, 0, 0)
                : physicalNodes.stream()
                .filter(node -> node != null && node.matches("\\d{4}0[1-4]"))
                .min(String::compareTo)
                .map(node -> LocalDateTime.of(
                        Integer.parseInt(node.substring(0, 4)),
                        (Character.digit(node.charAt(5), 10) - 1) * 3 + 1,
                        1, 0, 0))
                .orElse(LocalDateTime.of(1970, 1, 1, 0, 0));
    }
}
