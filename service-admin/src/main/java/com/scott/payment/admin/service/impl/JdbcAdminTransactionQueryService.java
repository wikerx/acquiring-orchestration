package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelLogQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionAmountSummaryResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationSummaryResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPaymentMethodSummaryResponse;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.admin.service.AdminRiskTimelineQueryService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminTransactionQueryService
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 管理后台交易只读查询实现，通过 ShardingSphere 逻辑表统一执行跨季度路由、强一致详情和查询预算。
 * @status : create
 */
@Service
public class JdbcAdminTransactionQueryService implements AdminTransactionQueryService {

    /**
     * TRANSACTION ORDER TABLE，用于保存 Jdbc Admin Transaction Query Service 中与 交易订单table 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_ORDER_TABLE = "transaction_order";
    /**
     * TRANSACTION OPERATION TABLE，用于保存 Jdbc Admin Transaction Query Service 中与 交易动作table 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_OPERATION_TABLE = "transaction_operation";
    /**
     * TRANSACTION PAYMENT METHOD INFO TABLE，表示支付方式、通知方式或调用方式。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_PAYMENT_METHOD_INFO_TABLE = "transaction_payment_method_info";
    /**
     * TRANSACTION CHANNEL INTERACTION LOG TABLE，用于保存 Jdbc Admin Transaction Query Service 中与 交易渠道interaction日志table 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE = "transaction_channel_interaction_log";
    /**
     * TRANSACTION CHANNEL CALLBACK TABLE，用于保存 Jdbc Admin Transaction Query Service 中与 交易渠道回调table 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
     * </p>
     */
    private static final String TRANSACTION_CHANNEL_CALLBACK_TABLE = "transaction_channel_callback";
    /**
     * TRANSACTION STATUS HISTORY TABLE，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String TRANSACTION_STATUS_HISTORY_TABLE = "transaction_status_history";
    /**
     * TRANSACTION FLOW EVENT TABLE，用于保存 Jdbc Admin Transaction Query Service 中与 交易floweventtable 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_FLOW_EVENT_TABLE = "transaction_flow_event";
    /**
     * TRANSACTION AMOUNT CHANGE LOG TABLE，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；不允许为空；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private static final String TRANSACTION_AMOUNT_CHANGE_LOG_TABLE = "transaction_amount_change_log";
    /**
     * TRANSACTION CHANNEL REQUEST TABLE，用于保存 Jdbc Admin Transaction Query Service 中与 交易渠道requesttable 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_CHANNEL_REQUEST_TABLE = "transaction_channel_request";
    /**
     * TRANSACTION CHANNEL CALLBACK LOG TABLE，用于保存 Jdbc Admin Transaction Query Service 中与 交易渠道回调日志table 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
     * </p>
     */
    private static final String TRANSACTION_CHANNEL_CALLBACK_LOG_TABLE = "transaction_channel_callback_log";
    /**
     * TRANSACTION MERCHANT NOTIFICATION TABLE，用于保存 Jdbc Admin Transaction Query Service 中与 交易商户通知table 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_MERCHANT_NOTIFICATION_TABLE = "transaction_merchant_notification";

    /** 管理端通知详情投影，明确排除包含完整回调地址的 notify_config_snapshot_json。 */
    private static final String TRANSACTION_MERCHANT_NOTIFICATION_ADMIN_PROJECTION = """
            id, notify_id, transaction_id, operation_id, merchant_id, merchant_order_no,
            notify_type, event_type, notify_status, notify_config_version,
            target_url_hash, target_url_masked, payload_json_masked, sign_type,
            last_attempt_no, max_retry_count, next_retry_time, success_time, fail_reason,
            transaction_date_time, transaction_utc_time, transaction_time_zone,
            version, deleted, create_time, update_time
            """;
    /**
     * TRANSACTION MERCHANT NOTIFICATION LOG TABLE，用于保存 Jdbc Admin Transaction Query Service 中与 交易商户通知日志table 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE = "transaction_merchant_notification_log";
    /**
     * TRANSACTION MERCHANT API INTERACTION LOG TABLE，用于保存 Jdbc Admin Transaction Query Service 中与 交易商户apiinteraction日志table 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_MERCHANT_API_INTERACTION_LOG_TABLE = "transaction_merchant_api_interaction_log";
    /**
     * DEFAULT QUERY TIME ZONE，用于保存 Jdbc Admin Transaction Query Service 中与 defaultquerytimezone 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_QUERY_TIME_ZONE = "Asia/Shanghai";
    private static final Set<String> SOFT_DELETE_LOGICAL_TABLES = Set.of(
            TRANSACTION_ORDER_TABLE,
            TRANSACTION_OPERATION_TABLE,
            TRANSACTION_CHANNEL_REQUEST_TABLE,
            TRANSACTION_CHANNEL_CALLBACK_TABLE,
            TRANSACTION_MERCHANT_NOTIFICATION_TABLE
    );

    /**
     * jdbc Template，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;
    /** 在 transaction 逻辑数据源上执行普通读和强一致读。 */
    private final TransactionLogicalReadExecutor transactionLogicalReadExecutor;
    /** 单次同步查询允许返回的最大记录数。 */
    private final int maxResultRows;
    /** 当前版本已登记物理节点中的最早季度，用于受控批量定位生命周期主单。 */
    private final LocalDateTime registeredNodeBegin;

    /**
     * 风控审计时间轴查询服务。
     */
    private final AdminRiskTimelineQueryService riskTimelineQueryService;

/**
 * 整理jdbcadmin交易查询service，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param jdbcTemplate jdbc Template 输入值，参与 jdbctemplate 的查询、校验、转换、写入或日志摘要
 */
    public JdbcAdminTransactionQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                            AdminRiskTimelineQueryService riskTimelineQueryService) {
        this(jdbcTemplate, riskTimelineQueryService, new TransactionLogicalReadExecutor(),
                new TransactionShardingProperties());
    }

    /**
     * 创建生产环境管理端交易查询服务，并为每条 JDBC Statement 应用同步查询超时。
     *
     * @param dataSource dynamic-datasource 外层路由数据源
     * @param riskTimelineQueryService 风控时间轴查询服务
     * @param transactionLogicalReadExecutor 交易逻辑数据源只读执行器
     * @param shardingProperties 查询资源预算配置
     * @param queryJdbcTemplateFactory 查询专用 JDBC 模板工厂
     */
    @Autowired
    public JdbcAdminTransactionQueryService(DataSource dataSource,
                                            AdminRiskTimelineQueryService riskTimelineQueryService,
                                            TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                            TransactionShardingProperties shardingProperties,
                                            TransactionQueryJdbcTemplateFactory queryJdbcTemplateFactory) {
        this(queryJdbcTemplateFactory.create(dataSource, shardingProperties),
                riskTimelineQueryService, transactionLogicalReadExecutor, shardingProperties);
    }

    /**
     * 创建同时执行逻辑路由和结果行数预算的管理端交易查询服务。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     * @param riskTimelineQueryService 风控时间轴查询服务
     * @param transactionLogicalReadExecutor 交易逻辑数据源只读执行器
     * @param shardingProperties 查询资源预算配置
     */
    public JdbcAdminTransactionQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                            AdminRiskTimelineQueryService riskTimelineQueryService,
                                            TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                            TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.riskTimelineQueryService = riskTimelineQueryService;
        this.transactionLogicalReadExecutor = transactionLogicalReadExecutor;
        this.maxResultRows = shardingProperties.getQueryBudget().getMaxResultRows();
        this.registeredNodeBegin = resolveRegisteredNodeBegin(shardingProperties.getPhysicalNodes());
    }

    /**
     * 在 transaction 逻辑数据源按时间范围分页查询交易主单。
     *
     * @param query 商户、交易号、状态、金额、时间范围和分页条件
     * @return ShardingSphere 归并后的交易主单分页结果
     */
    @Override
    public PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        return executeRead(false, () -> pageOrdersNormalized(safeQuery));
    }

    /**
     * 在已归一化时间范围内执行逻辑表分页。
     *
     * @param safeQuery 已校验页码、页大小和 transaction_date_time 范围的查询
     * @return 全季度总数和当前页记录
     */
    private PageResult<TransactionOrderResponse> pageOrdersNormalized(TransactionPageQuery safeQuery) {
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        long total = count(TRANSACTION_ORDER_TABLE, orderWhereSql(safeQuery), orderParams(safeQuery));
        List<TransactionOrderResponse> rows = offset < total
                ? selectOrders(TRANSACTION_ORDER_TABLE, safeQuery, offset, limit)
                : List.of();
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), rows);
    }

    /**
     * 在只读数据源分页查询交易操作单。
     *
     * @param query 交易筛选和分页条件
     * @return ShardingSphere 归并后的操作单分页结果
     */
    @Override
    public PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        return executeRead(false, () -> pageOperationsNormalized(safeQuery));
    }

    /**
     * 查询交易操作单分页结果及相同条件下的汇总指标。
     *
     * @param query 交易筛选和分页条件
     * @return 操作单分页数据与汇总信息
     */
    @Override
    public TransactionOperationSearchResponse searchOperations(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        return executeRead(false, () -> {
            TransactionOperationSearchResponse response = new TransactionOperationSearchResponse();
            response.setPage(pageOperationsNormalized(safeQuery));
            response.setSummary(operationSummary(safeQuery));
            return response;
        });
    }

    /**
     * 使用列表返回的真实交易时间聚合交易主单、操作单和全链路时间轴。
     *
     * <p>详情包含状态、金额、风控、渠道交互、回调和商户通知等记录；
     * 主记录不存在时统一返回订单不存在。</p>
     *
     * @param transactionId 平台交易号，仅作为交易业务标识
     * @param transactionDateTime 列表查询返回的真实交易分片时间
     * @return 管理端交易聚合详情
     */
    @Override
    public TransactionDetailResponse detail(String transactionId,
                                            LocalDateTime transactionDateTime,
                                            LocalDateTime rootTransactionDateTime) {
        if (!StringUtils.hasText(transactionId)
                || transactionDateTime == null
                || rootTransactionDateTime == null) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID);
        }
        return executeRead(true,
                () -> detailNormalized(transactionId, transactionDateTime, rootTransactionDateTime));
    }

    /**
     * 在强一致读作用域内按交易分片时间装配详情，避免回调后立即查询读到副本旧状态。
     *
     * @param transactionId 平台交易号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 主单、动作单和时间线聚合详情
     */
    private TransactionDetailResponse detailNormalized(String transactionId,
                                                       LocalDateTime transactionDateTime,
                                                       LocalDateTime rootTransactionDateTime) {
        TransactionOperationResponse sourceOperation = selectOperationByTransactionId(
                TRANSACTION_OPERATION_TABLE, transactionId, transactionDateTime);
        if (sourceOperation == null) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderResponse order = selectOrderByOperationId(
                TRANSACTION_ORDER_TABLE, sourceOperation.getOperationId(), rootTransactionDateTime);
        if (order == null) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime beginTime = order.getTransactionDateTime() == null ? sourceOperation.getTransactionDateTime() : order.getTransactionDateTime();
        LocalDateTime endTime = LocalDateTime.now();
        List<TransactionOperationResponse> operations = selectOperationsByOperationId(sourceOperation.getOperationId(), beginTime, endTime);
        if (operations.stream().noneMatch(item -> Objects.equals(item.getTransactionId(), sourceOperation.getTransactionId()))) {
            operations = new ArrayList<>(operations);
            operations.add(sourceOperation);
        }
        enrichOperationLifecycles(operations);
        TransactionDetailResponse detail = new TransactionDetailResponse();
        detail.setOrder(order);
        detail.setOperations(operations);
        detail.setStatusHistory(selectMapsByOperationId(TRANSACTION_STATUS_HISTORY_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setFlowEvents(selectMapsByOperationId(TRANSACTION_FLOW_EVENT_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setRiskEvents(riskTimelineQueryService.findRiskEvents(sourceOperation.getTransactionId()));
        detail.setAmountChanges(selectMapsByOperationId(TRANSACTION_AMOUNT_CHANGE_LOG_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setChannelRequests(selectMapsByOperationId(TRANSACTION_CHANNEL_REQUEST_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setChannelInteractionLogs(selectMapsByOperationId(TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setChannelCallbacks(selectMapsByOperationId(TRANSACTION_CHANNEL_CALLBACK_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setChannelCallbackLogs(selectMapsByOperationId(TRANSACTION_CHANNEL_CALLBACK_LOG_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setMerchantNotifications(selectMapsByOperationId(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setMerchantNotificationLogs(selectMapsByOperationId(TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setMerchantApiInteractionLogs(selectMapsByOperationId(
                TRANSACTION_MERCHANT_API_INTERACTION_LOG_TABLE, beginTime, endTime, order.getOperationId()));
        return detail;
    }

    /**
     * 分页查询渠道请求与响应交互日志。
     *
     * @param query 渠道、交易、状态、时间范围和分页条件
     * @return 渠道交互日志分页结果
     */
    @Override
    public PageResult<Map<String, Object>> pageChannelLogs(ChannelLogQuery query) {
        ChannelLogQuery safeQuery = normalize(query);
        return executeRead(false, () -> pageChannelLogsNormalized(safeQuery));
    }

    /**
     * 分页查询渠道回调记录。
     *
     * @param query 渠道回调筛选和分页条件
     * @return ShardingSphere 归并后的渠道回调分页结果
     */
    @Override
    public PageResult<Map<String, Object>> pageChannelCallbacks(ChannelCallbackQuery query) {
        ChannelCallbackQuery safeQuery = normalize(query);
        return executeRead(false,
                () -> pageMaps(TRANSACTION_CHANNEL_CALLBACK_TABLE, channelCallbackWhereSql(safeQuery),
                        channelCallbackParams(safeQuery), safeQuery));
    }

    /**
     * 分页查询向商户发送的交易通知任务。
     *
     * @param query 商户通知筛选和分页条件
     * @return ShardingSphere 归并后的商户通知分页结果
     */
    @Override
    public PageResult<Map<String, Object>> pageMerchantNotifications(MerchantNotificationQuery query) {
        MerchantNotificationQuery safeQuery = normalize(query);
        return executeRead(false,
                () -> pageMaps(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, merchantNotificationWhereSql(safeQuery),
                        merchantNotificationParams(safeQuery), safeQuery));
    }

    /**
     * 按通知任务号和精确分片时间读取通知任务与投递日志。
     *
     * @param notifyId 通知任务号
     * @param transactionDateTime 页面列表返回的真实交易分片时间
     * @return 包含脱敏任务快照和按尝试次数排序日志的详情视图
     */
    @Override
    public Map<String, Object> merchantNotificationDetail(String notifyId,
                                                          LocalDateTime transactionDateTime) {
        if (!StringUtils.hasText(notifyId) || transactionDateTime == null) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID);
        }
        return executeRead(true, () -> {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("notifyId", notifyId.trim())
                    .addValue("transactionDateTime", transactionDateTime);
            List<Map<String, Object>> notifications = toCamelCaseRows(jdbcTemplate.queryForList("""
                    SELECT %s
                    FROM transaction_merchant_notification
                    WHERE notify_id = :notifyId
                      AND transaction_date_time = :transactionDateTime
                      AND deleted = 0
                    LIMIT 1
                    """.formatted(TRANSACTION_MERCHANT_NOTIFICATION_ADMIN_PROJECTION), params));
            if (notifications.isEmpty()) {
                throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
            }
            List<Map<String, Object>> deliveryLogs = toCamelCaseRows(jdbcTemplate.queryForList("""
                    SELECT *
                    FROM transaction_merchant_notification_log
                    WHERE notify_id = :notifyId
                      AND transaction_date_time = :transactionDateTime
                    ORDER BY attempt_no ASC, id ASC
                    LIMIT 100
                    """, params));
            decorateMerchantNotificationLogRows(deliveryLogs);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("notification", notifications.get(0));
            detail.put("deliveryLogs", deliveryLogs);
            return detail;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsRetryableTerminalMerchantNotification(String transactionId,
                                                               LocalDateTime transactionDateTime) {
        if (!StringUtils.hasText(transactionId) || transactionDateTime == null) {
            return false;
        }
        return executeRead(true, () -> Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM transaction_operation operation_record
                    JOIN transaction_merchant_notification notification
                      ON notification.transaction_id = operation_record.transaction_id
                     AND notification.transaction_date_time = operation_record.transaction_date_time
                    WHERE operation_record.transaction_id = :transactionId
                      AND operation_record.transaction_date_time = :transactionDateTime
                      AND operation_record.transaction_status IN ('SUCCESS', 'FAILED')
                      AND operation_record.deleted = 0
                      AND notification.deleted = 0
                      AND (
                            notification.notify_status IN ('SUCCESS', 'FAILED', 'CLOSED')
                            OR (notification.notify_status = 'INIT'
                                AND notification.next_retry_time IS NOT NULL)
                          )
                )
                """, new MapSqlParameterSource()
                .addValue("transactionId", transactionId.trim())
                .addValue("transactionDateTime", transactionDateTime), Boolean.class)));
    }

    /**
     * 使用已规范化查询在操作单及支付信息逻辑表上执行分页。
     *
     * @param safeQuery 已校验页码并补齐时间范围的查询
     * @return ShardingSphere 归并后的操作单分页结果
     */
    private PageResult<TransactionOperationResponse> pageOperationsNormalized(TransactionPageQuery safeQuery) {
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        long total = countOperations(
                TRANSACTION_OPERATION_TABLE, TRANSACTION_PAYMENT_METHOD_INFO_TABLE, safeQuery);
        List<TransactionOperationResponse> rows = offset < total
                ? selectOperations(
                        TRANSACTION_OPERATION_TABLE,
                        TRANSACTION_PAYMENT_METHOD_INFO_TABLE,
                        safeQuery,
                        offset,
                        limit)
                : List.of();
        enrichOperationLifecycles(rows);
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), rows);
    }

    /**
     * 查询maps，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param logicalTable 逻辑表名，用于按交易时间解析真实物理分表
     * @param whereSql where Sql 输入值，参与 wheresql 的查询、校验、转换、写入或日志摘要
     * @param params params 输入值，参与 params 的查询、校验、转换、写入或日志摘要
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private PageResult<Map<String, Object>> pageMaps(String logicalTable, String whereSql, MapSqlParameterSource params, PageRequest query) {
        long offset = offset(query);
        long limit = query.safePageSize();
        boolean softDeleteTable = hasSoftDeleteColumn(logicalTable);
        long total = countMaps(logicalTable, whereSql, params, softDeleteTable);
        List<Map<String, Object>> rows = offset < total
                ? selectMaps(logicalTable, whereSql, params, offset, limit, softDeleteTable)
                : List.of();
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 查询渠道日志normalized，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private PageResult<Map<String, Object>> pageChannelLogsNormalized(ChannelLogQuery query) {
        long offset = offset(query);
        long limit = query.safePageSize();
        long total = countChannelLogs(
                TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE,
                TRANSACTION_CHANNEL_REQUEST_TABLE,
                TRANSACTION_OPERATION_TABLE,
                query);
        List<Map<String, Object>> rows = offset < total
                ? selectChannelLogs(
                        TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE,
                        TRANSACTION_CHANNEL_REQUEST_TABLE,
                        TRANSACTION_OPERATION_TABLE,
                        query,
                        offset,
                        limit)
                : List.of();
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 统计计数，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param whereSql where Sql 输入值，参与 wheresql 的查询、校验、转换、写入或日志摘要
     * @param params params 输入值，参与 参数 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long count(String table, String whereSql, MapSqlParameterSource params) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM %s
                WHERE deleted = 0
                %s
                """.formatted(table, whereSql), params, Long.class);
        return count == null ? 0L : count;
    }

    /**
     * 统计maps，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param whereSql where Sql 输入值，参与 wheresql 的查询、校验、转换、写入或日志摘要
     * @param params params 输入值，参与 参数 的查询、校验、转换、写入或日志摘要
     * @param softDeleteTable soft Delete Table 输入值，参与 softdelete表 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long countMaps(String table, String whereSql, MapSqlParameterSource params, boolean softDeleteTable) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM %s
                WHERE %s
                %s
                """.formatted(table, softDeleteCondition(softDeleteTable), whereSql), params, Long.class);
        return count == null ? 0L : count;
    }

/**
 * 查询maps，按调用方提供的过滤条件返回对应业务视图。
 * <p>
 * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
 * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
 * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
 * </p>
 * @param table 固定交易逻辑表名，只允许使用类内受控常量
 * @param whereSql where Sql 输入值，参与 wheresql 的查询、校验、转换、写入或日志摘要
 * @param params params 输入值，参与 params 的查询、校验、转换、写入或日志摘要
 * @param offset 分页或扫描窗口参数，用于限制单次查询范围
 * @param limit 分页或扫描窗口参数，用于限制单次查询范围
 * @param softDeleteTable soft Delete Table 输入值，参与 softdeletetable 的查询、校验、转换、写入或日志摘要
 * @return 查询得到的业务对象、分页结果或空结果
 */
    private List<Map<String, Object>> selectMaps(String table, String whereSql, MapSqlParameterSource params,
                                                 long offset, long limit, boolean softDeleteTable) {
        MapSqlParameterSource pageParams = copy(params).addValue("offset", offset).addValue("limit", limit);
        return toCamelCaseRows(jdbcTemplate.queryForList("""
                SELECT *
                FROM %s
                WHERE %s
                %s
                ORDER BY transaction_date_time DESC, id DESC
                LIMIT :offset, :limit
                """.formatted(table, softDeleteCondition(softDeleteTable), whereSql), pageParams));
    }

    /**
     * 统计渠道日志，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param requestTable request Table 输入值，参与 请求表 的查询、校验、转换、写入或日志摘要
     * @param operationTable operation Table 输入值，参与 动作表 的查询、校验、转换、写入或日志摘要
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long countChannelLogs(String table, String requestTable, String operationTable, ChannelLogQuery query) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM %s l
                LEFT JOIN %s r ON r.request_id = l.request_id AND r.deleted = 0
                LEFT JOIN %s o ON o.transaction_id = l.transaction_id AND o.deleted = 0
                WHERE l.transaction_date_time >= :beginTime
                  AND l.transaction_date_time < :endTime
                %s
                """.formatted(table, requestTable, operationTable, channelLogWhereSql(query)), channelLogParams(query), Long.class);
        return count == null ? 0L : count;
    }

/**
 * 查询渠道日志，按调用方提供的过滤条件返回对应业务视图。
 * <p>
 * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
 * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
 * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
 * </p>
 * @param table 固定交易逻辑表名，只允许使用类内受控常量
 * @param requestTable request Table 输入值，参与 请求table 的查询、校验、转换、写入或日志摘要
 * @param operationTable operation Table 输入值，参与 动作table 的查询、校验、转换、写入或日志摘要
 * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
 * @param offset 分页或扫描窗口参数，用于限制单次查询范围
 * @param limit 分页或扫描窗口参数，用于限制单次查询范围
 * @return 查询得到的业务对象、分页结果或空结果
 */
    private List<Map<String, Object>> selectChannelLogs(String table,
                                                        String requestTable,
                                                        String operationTable,
                                                        ChannelLogQuery query,
                                                        long offset,
                                                        long limit) {
        MapSqlParameterSource params = channelLogParams(query)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return toCamelCaseRows(jdbcTemplate.queryForList("""
                SELECT l.*,
                       COALESCE(r.transaction_type, o.transaction_type) AS transaction_type,
                       r.request_status AS request_status,
                       r.gateway_result AS gateway_result,
                       r.gateway_code AS gateway_code,
                       r.acquirer_code AS acquirer_code,
                       r.acquirer_message AS acquirer_message,
                       r.channel_status AS channel_trade_status,
                       COALESCE(r.channel_order_no, o.channel_order_no) AS channel_order_no,
                       COALESCE(r.channel_transaction_id, o.channel_transaction_id) AS channel_transaction_id,
                       COALESCE(r.platform_result_code, o.transaction_status) AS platform_result_code,
                       r.platform_fail_reason AS platform_fail_reason,
                       COALESCE(r.acquirer_code, r.gateway_code, o.channel_response_code) AS channel_response_code,
                       COALESCE(r.acquirer_message, o.channel_response_message, r.platform_fail_reason) AS channel_response_message
                FROM %s l
                LEFT JOIN %s r ON r.request_id = l.request_id AND r.deleted = 0
                LEFT JOIN %s o ON o.transaction_id = l.transaction_id AND o.deleted = 0
                WHERE l.transaction_date_time >= :beginTime
                  AND l.transaction_date_time < :endTime
                %s
                ORDER BY l.interaction_time DESC, l.id DESC
                LIMIT :offset, :limit
                """.formatted(table, requestTable, operationTable, channelLogWhereSql(query)), params));
    }

    /**
     * 查询交易主单，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @param offset 分页或扫描窗口参数，用于限制单次查询范围
     * @param limit 分页或扫描窗口参数，用于限制单次查询范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<TransactionOrderResponse> selectOrders(String table, TransactionPageQuery query, long offset, long limit) {
        MapSqlParameterSource params = orderParams(query).addValue("offset", offset).addValue("limit", limit);
        return jdbcTemplate.query("""
                SELECT *
                FROM %s
                WHERE deleted = 0
                %s
                ORDER BY transaction_date_time DESC, id DESC
                LIMIT :offset, :limit
                """.formatted(table, orderWhereSql(query)), params, orderMapper());
    }

    /**
     * 统计交易动作，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param paymentTable payment Table 输入值，参与 payment表 的查询、校验、转换、写入或日志摘要
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long countOperations(String table, String paymentTable, TransactionPageQuery query) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM %s o
                WHERE o.deleted = 0
                %s
                """.formatted(table, operationWhereSql(query, paymentTable)), operationParams(query), Long.class);
        return count == null ? 0L : count;
    }

    /**
     * 查询交易动作，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param paymentTable payment Table 输入值，参与 paymenttable 的查询、校验、转换、写入或日志摘要
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @param offset 分页或扫描窗口参数，用于限制单次查询范围
     * @param limit 分页或扫描窗口参数，用于限制单次查询范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<TransactionOperationResponse> selectOperations(String table, String paymentTable, TransactionPageQuery query, long offset, long limit) {
        MapSqlParameterSource params = operationParams(query).addValue("offset", offset).addValue("limit", limit);
        return jdbcTemplate.query("""
                SELECT o.*,
                       p.payment_method AS joined_payment_method,
                       p.payment_brand AS joined_payment_brand,
                       p.card_bin AS joined_card_bin,
                       p.card_number_masked AS joined_card_number_masked
                FROM %s o
                LEFT JOIN %s p ON p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time
                WHERE o.deleted = 0
                %s
                ORDER BY o.transaction_date_time DESC, o.id DESC
                LIMIT :offset, :limit
                """.formatted(table, paymentTable,
                operationWhereSql(query, paymentTable)), params, operationMapper(true));
    }

    /**
     * 批量补齐当前页动作所属的生命周期主单信息。
     * 查询范围只覆盖当前规则已登记节点，不依赖动作时间等于根主单时间，也不会产生无分片键 JOIN。
     */
    private void enrichOperationLifecycles(List<TransactionOperationResponse> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<String> operationIds = rows.stream()
                .map(TransactionOperationResponse::getOperationId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (operationIds.isEmpty()) {
            return;
        }
        List<TransactionOrderResponse> orders = jdbcTemplate.query("""
                SELECT *
                FROM transaction_order
                WHERE operation_id IN (:operationIds)
                  AND transaction_date_time >= :registeredNodeBegin
                  AND transaction_date_time < :registeredNodeEnd
                  AND deleted = 0
                """, new MapSqlParameterSource()
                .addValue("operationIds", operationIds)
                .addValue("registeredNodeBegin", registeredNodeBegin)
                .addValue("registeredNodeEnd", exclusiveEnd(LocalDateTime.now())), orderMapper());
        Map<String, TransactionOrderResponse> orderByOperation = orders.stream()
                .collect(java.util.stream.Collectors.toMap(
                        TransactionOrderResponse::getOperationId,
                        java.util.function.Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        for (TransactionOperationResponse row : rows) {
            TransactionOrderResponse order = orderByOperation.get(row.getOperationId());
            if (order == null) {
                continue;
            }
            row.setRootTransactionDateTime(order.getTransactionDateTime());
            row.setAuthorizedAmount(order.getAuthorizedAmount());
            row.setCapturedAmount(order.getCapturedAmount());
            row.setRefundedAmount(order.getRefundedAmount());
            row.setAvailableCaptureAmount(order.getAvailableCaptureAmount());
            row.setAvailableRefundAmount(order.getAvailableRefundAmount());
        }
    }

    /**
     * 整理动作汇总，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private TransactionOperationSummaryResponse operationSummary(TransactionPageQuery query) {
        SummaryAccumulator accumulator = new SummaryAccumulator();
        jdbcTemplate.query("""
                    SELECT o.transaction_status AS transaction_status,
                           COALESCE(o.transaction_currency, 'UNKNOWN') AS currency,
                           o.currency_exponent AS currency_exponent,
                           COUNT(1) AS count,
                           COALESCE(SUM(COALESCE(o.transaction_amount, 0)), 0) AS amount
                    FROM %s o
                    WHERE o.deleted = 0
                    %s
                    GROUP BY o.transaction_status, COALESCE(o.transaction_currency, 'UNKNOWN'), o.currency_exponent
                    """.formatted(TRANSACTION_OPERATION_TABLE,
                    operationWhereSql(query, TRANSACTION_PAYMENT_METHOD_INFO_TABLE)),
                    operationParams(query), summaryMapper())
                    .forEach(accumulator::addAmount);
        jdbcTemplate.query("""
                    SELECT COALESCE(p.payment_method, 'UNKNOWN') AS payment_method,
                           p.payment_brand AS payment_brand,
                           COALESCE(o.transaction_currency, 'UNKNOWN') AS currency,
                           o.currency_exponent AS currency_exponent,
                           COUNT(1) AS count,
                           COALESCE(SUM(COALESCE(o.transaction_amount, 0)), 0) AS amount
                    FROM %s o
                    LEFT JOIN %s p ON p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time
                    WHERE o.deleted = 0
                    %s
                    GROUP BY COALESCE(p.payment_method, 'UNKNOWN'), p.payment_brand, COALESCE(o.transaction_currency, 'UNKNOWN'), o.currency_exponent
                    """.formatted(
                    TRANSACTION_OPERATION_TABLE,
                    TRANSACTION_PAYMENT_METHOD_INFO_TABLE,
                    operationWhereSql(query, TRANSACTION_PAYMENT_METHOD_INFO_TABLE)),
                    operationParams(query), summaryMapper())
                    .forEach(accumulator::addPayment);
        return accumulator.toResponse();
    }

    /**
     * 整理订单wheresql，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String orderWhereSql(TransactionPageQuery query) {
        StringBuilder sql = new StringBuilder(" AND transaction_date_time >= :beginTime AND transaction_date_time < :endTime");
        append(sql, query.getMerchantId(), "AND merchant_id = :merchantId");
        append(sql, query.getMerchantOrderNo(), "AND merchant_order_no = :merchantOrderNo");
        append(sql, query.getTransactionId(), "AND (root_transaction_id = :transactionId OR latest_transaction_id = :transactionId)");
        append(sql, query.getTransactionType(), "AND transaction_type = :transactionType");
        append(sql, query.getTransactionStatus(), "AND transaction_status = :transactionStatus");
        append(sql, query.getChannelCode(), "AND channel_code = :channelCode");
        append(sql, query.getPaymentMethod(), "AND payment_method = :paymentMethod");
        append(sql, query.getPaymentBrand(), "AND payment_brand = :paymentBrand");
        append(sql, query.getChannelOrderNo(), "AND channel_order_no = :channelOrderNo");
        append(sql, query.getChannelMatchStatus(), "AND channel_match_status = :channelMatchStatus");
        append(sql, query.getReconciliationStatus(), "AND reconciliation_status = :reconciliationStatus");
        append(sql, query.getSettlementStatus(), "AND settlement_status = :settlementStatus");
        return sql.toString();
    }

    /**
     * 整理动作wheresql，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @param paymentTable payment Table 输入值，参与 payment表 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String operationWhereSql(TransactionPageQuery query, String paymentTable) {
        StringBuilder sql = new StringBuilder(" AND o.transaction_date_time >= :beginTime AND o.transaction_date_time < :endTime");
        append(sql, query.getMerchantId(), "AND o.merchant_id = :merchantId");
        append(sql, query.getMerchantOrderNo(), "AND o.merchant_order_no = :merchantOrderNo");
        append(sql, query.getTransactionId(), "AND o.transaction_id = :transactionId");
        append(sql, query.getSourceTransactionId(), "AND o.source_transaction_id = :sourceTransactionId");
        append(sql, query.getTransactionType(), "AND o.transaction_type = :transactionType");
        append(sql, query.getTransactionStatus(), "AND o.transaction_status = :transactionStatus");
        append(sql, query.getChannelCode(), "AND o.channel_code = :channelCode");
        append(sql, query.getChannelOrderNo(), "AND o.channel_order_no = :channelOrderNo");
        append(sql, query.getChannelResponseCode(), "AND o.channel_response_code = :channelResponseCode");
        append(sql, query.getAuthCode(), "AND o.auth_code = :authCode");
        append(sql, query.getAcquirerReferenceNo(), "AND o.acquirer_reference_no = :acquirerReferenceNo");
        append(sql, query.getChannelMatchStatus(), "AND o.channel_match_status = :channelMatchStatus");
        append(sql, query.getReconciliationStatus(), "AND o.reconciliation_status = :reconciliationStatus");
        append(sql, query.getSettlementStatus(), "AND o.settlement_status = :settlementStatus");
        if (StringUtils.hasText(query.getPaymentMethod())) {
            sql.append(" AND EXISTS (SELECT 1 FROM ").append(paymentTable)
                    .append(" p WHERE p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time AND p.payment_method = :paymentMethod)");
        }
        if (StringUtils.hasText(query.getPaymentBrand())) {
            sql.append(" AND EXISTS (SELECT 1 FROM ").append(paymentTable)
                    .append(" p WHERE p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time AND p.payment_brand = :paymentBrand)");
        }
        if (StringUtils.hasText(query.getCardBin())) {
            sql.append(" AND EXISTS (SELECT 1 FROM ").append(paymentTable)
                    .append(" p WHERE p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time AND p.card_bin LIKE :cardBinLike)");
        }
        return sql.toString();
    }

    /**
     * 整理渠道日志wheresql，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String channelLogWhereSql(ChannelLogQuery query) {
        StringBuilder sql = new StringBuilder();
        append(sql, query.getChannelCode(), "AND l.channel_code = :channelCode");
        append(sql, query.getTransactionId(), "AND l.transaction_id = :transactionId");
        append(sql, query.getChannelOrderNo(), "AND COALESCE(r.channel_order_no, o.channel_order_no) = :channelOrderNo");
        append(sql, query.getRequestStatus(), "AND r.request_status = :requestStatus");
        if (StringUtils.hasText(query.getInteractionType())) {
            if ("REQUEST".equals(query.getInteractionType()) || "RESPONSE".equals(query.getInteractionType())) {
                sql.append(" AND l.interaction_type IN (:interactionType, 'REQUEST_RESPONSE')");
            } else {
                sql.append(" AND l.interaction_type = :interactionType");
            }
        }
        return sql.toString();
    }

    /**
     * 整理渠道回调wheresql，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String channelCallbackWhereSql(ChannelCallbackQuery query) {
        StringBuilder sql = new StringBuilder(" AND transaction_date_time >= :beginTime AND transaction_date_time < :endTime");
        append(sql, query.getChannelCode(), "AND channel_code = :channelCode");
        append(sql, query.getTransactionId(), "AND transaction_id = :transactionId");
        append(sql, query.getChannelOrderNo(), "AND channel_order_no = :channelOrderNo");
        append(sql, query.getChannelTransactionId(), "AND channel_transaction_id = :channelTransactionId");
        append(sql, query.getCallbackStatus(), "AND callback_status = :callbackStatus");
        return sql.toString();
    }

    /**
     * 整理商户通知wheresql，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String merchantNotificationWhereSql(MerchantNotificationQuery query) {
        StringBuilder sql = new StringBuilder(" AND transaction_date_time >= :beginTime AND transaction_date_time < :endTime");
        append(sql, query.getMerchantId(), "AND merchant_id = :merchantId");
        append(sql, query.getTransactionId(), "AND transaction_id = :transactionId");
        append(sql, query.getNotifyStatus(), "AND notify_status = :notifyStatus");
        return sql.toString();
    }

    /**
     * 构造追加对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param sql sql 输入值，参与 sql 的查询、校验、转换、写入或日志摘要
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param fragment fragment 输入值，参与 fragment 的查询、校验、转换、写入或日志摘要
     */
    private void append(StringBuilder sql, String value, String fragment) {
        if (StringUtils.hasText(value)) {
            sql.append(' ').append(fragment);
        }
    }

    /**
     * 整理订单参数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private MapSqlParameterSource orderParams(TransactionPageQuery query) {
        return baseParams(query.getBeginTime(), query.getEndTime())
                .addValue("merchantId", query.getMerchantId())
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("transactionId", query.getTransactionId())
                .addValue("transactionType", query.getTransactionType())
                .addValue("transactionStatus", query.getTransactionStatus())
                .addValue("channelCode", query.getChannelCode())
                .addValue("paymentMethod", query.getPaymentMethod())
                .addValue("paymentBrand", query.getPaymentBrand())
                .addValue("channelOrderNo", query.getChannelOrderNo())
                .addValue("channelMatchStatus", query.getChannelMatchStatus())
                .addValue("reconciliationStatus", query.getReconciliationStatus())
                .addValue("settlementStatus", query.getSettlementStatus());
    }

    /**
     * 整理动作参数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private MapSqlParameterSource operationParams(TransactionPageQuery query) {
        return orderParams(query)
                .addValue("sourceTransactionId", query.getSourceTransactionId())
                .addValue("channelResponseCode", query.getChannelResponseCode())
                .addValue("authCode", query.getAuthCode())
                .addValue("acquirerReferenceNo", query.getAcquirerReferenceNo())
                .addValue("cardBinLike", StringUtils.hasText(query.getCardBin()) ? query.getCardBin() + "%" : null);
    }

    /**
     * 整理渠道日志参数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private MapSqlParameterSource channelLogParams(ChannelLogQuery query) {
        return baseParams(query.getBeginTime(), query.getEndTime())
                .addValue("channelCode", query.getChannelCode())
                .addValue("transactionId", query.getTransactionId())
                .addValue("channelOrderNo", query.getChannelOrderNo())
                .addValue("requestStatus", query.getRequestStatus())
                .addValue("interactionType", query.getInteractionType());
    }

    /**
     * 整理渠道回调参数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private MapSqlParameterSource channelCallbackParams(ChannelCallbackQuery query) {
        return baseParams(query.getBeginTime(), query.getEndTime())
                .addValue("channelCode", query.getChannelCode())
                .addValue("transactionId", query.getTransactionId())
                .addValue("channelOrderNo", query.getChannelOrderNo())
                .addValue("channelTransactionId", query.getChannelTransactionId())
                .addValue("callbackStatus", query.getCallbackStatus());
    }

    /**
     * 整理商户通知参数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private MapSqlParameterSource merchantNotificationParams(MerchantNotificationQuery query) {
        return baseParams(query.getBeginTime(), query.getEndTime())
                .addValue("merchantId", query.getMerchantId())
                .addValue("transactionId", query.getTransactionId())
                .addValue("notifyStatus", query.getNotifyStatus());
    }

    /**
     * 整理基础 SQL 参数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private MapSqlParameterSource baseParams(LocalDateTime beginTime, LocalDateTime endTime) {
        return new MapSqlParameterSource().addValue("beginTime", beginTime).addValue("endTime", exclusiveEnd(endTime));
    }

    /**
     * 解析normalize，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
     */
    private TransactionPageQuery normalize(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = query == null ? new TransactionPageQuery() : query;
        fillDefaultTimeRange(safeQuery);
        normalizeMerchantResponseCode(safeQuery);
        return applyResultRowBudget(safeQuery);
    }

    /**
     * 解析normalize，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
     */
    private ChannelLogQuery normalize(ChannelLogQuery query) {
        ChannelLogQuery safeQuery = query == null ? new ChannelLogQuery() : query;
        QueryTimeRange range = normalizeQueryTimeRange(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        safeQuery.setBeginTime(range.beginTime());
        safeQuery.setEndTime(range.endTime());
        safeQuery.setQueryTimeZone(range.queryTimeZone());
        return applyResultRowBudget(safeQuery);
    }

    /**
     * 解析normalize，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
     */
    private ChannelCallbackQuery normalize(ChannelCallbackQuery query) {
        ChannelCallbackQuery safeQuery = query == null ? new ChannelCallbackQuery() : query;
        QueryTimeRange range = normalizeQueryTimeRange(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        safeQuery.setBeginTime(range.beginTime());
        safeQuery.setEndTime(range.endTime());
        safeQuery.setQueryTimeZone(range.queryTimeZone());
        return applyResultRowBudget(safeQuery);
    }

    /**
     * 解析normalize，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
     */
    private MerchantNotificationQuery normalize(MerchantNotificationQuery query) {
        MerchantNotificationQuery safeQuery = query == null ? new MerchantNotificationQuery() : query;
        QueryTimeRange range = normalizeQueryTimeRange(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        safeQuery.setBeginTime(range.beginTime());
        safeQuery.setEndTime(range.endTime());
        safeQuery.setQueryTimeZone(range.queryTimeZone());
        return applyResultRowBudget(safeQuery);
    }

    /** 将所有管理端交易分页请求收敛到版本化规则声明的单次结果行数上限。 */
    private <T extends PageRequest> T applyResultRowBudget(T query) {
        query.setPageSize((int) Math.min(query.safePageSize(), maxResultRows));
        return query;
    }

    /**
     * 构造默认时间范围对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     */
    private void fillDefaultTimeRange(TransactionPageQuery query) {
        QueryTimeRange range = normalizeQueryTimeRange(query.getBeginTime(), query.getEndTime(), query.getQueryTimeZone());
        query.setBeginTime(range.beginTime());
        query.setEndTime(range.endTime());
        query.setQueryTimeZone(range.queryTimeZone());
    }

    /**
     * 解析normalize查询时间范围，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param queryTimeZone 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 构造、转换或解析后的业务值
     */
    private QueryTimeRange normalizeQueryTimeRange(LocalDateTime beginTime, LocalDateTime endTime, String queryTimeZone) {
        ZoneId queryZone = resolveQueryZone(queryTimeZone);
        LocalDateTime safeEnd = endTime == null ? LocalDateTime.now(queryZone) : endTime;
        LocalDateTime safeBegin = beginTime == null ? safeEnd.minusDays(30) : beginTime;
        if (safeBegin.isAfter(safeEnd)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "beginTime must not be after endTime");
        }
        ZoneId storageZone = ZoneId.of(DEFAULT_QUERY_TIME_ZONE);
        return new QueryTimeRange(convertBetweenZones(safeBegin, queryZone, storageZone),
                convertBetweenZones(safeEnd, queryZone, storageZone),
                queryZone.getId());
    }

    /**
     * 解析resolve查询zone，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param queryTimeZone 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 构造、转换或解析后的业务值
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
     * 解析normalizezoneID，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param zone zone 输入值，参与 zone 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeZoneId(String zone) {
        String normalized = zone == null ? DEFAULT_QUERY_TIME_ZONE : zone.trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("UTC".equals(upper) || "GMT".equals(upper)) {
            return upper;
        }
        if (upper.startsWith("UTC+") || upper.startsWith("UTC-") || upper.startsWith("GMT+") || upper.startsWith("GMT-")) {
            String prefix = upper.substring(0, 3);
            String offset = upper.substring(3);
            if (offset.matches("[+-]\\d{1,2}")) {
                return prefix + String.format("%+03d:00", Integer.parseInt(offset));
            }
            if (offset.matches("[+-]\\d{1,2}:\\d{2}")) {
                String[] parts = offset.substring(1).split(":");
                return prefix + offset.charAt(0) + String.format("%02d:%s", Integer.parseInt(parts[0]), parts[1]);
            }
        }
        return normalized;
    }

    /**
     * 构造betweenzones对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param sourceTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param sourceZone source Zone 输入值，参与 来源zone 的查询、校验、转换、写入或日志摘要
     * @param targetZone target Zone 输入值，参与 targetzone 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private LocalDateTime convertBetweenZones(LocalDateTime sourceTime, ZoneId sourceZone, ZoneId targetZone) {
        ZonedDateTime source = sourceTime.atZone(sourceZone);
        return source.withZoneSameInstant(targetZone).toLocalDateTime();
    }

    /**
     * 解析normalize商户响应编码，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     */
    private void normalizeMerchantResponseCode(TransactionPageQuery query) {
        if (!StringUtils.hasText(query.getMerchantResponseCode())) {
            return;
        }
        String mappedStatus = resolveStatusByMerchantResponseCode(query.getMerchantResponseCode());
        if (!StringUtils.hasText(mappedStatus)) {
            query.setTransactionStatus("__NO_MATCH__");
            return;
        }
        if (StringUtils.hasText(query.getTransactionStatus()) && !Objects.equals(query.getTransactionStatus(), mappedStatus)) {
            query.setTransactionStatus("__NO_MATCH__");
            return;
        }
        query.setTransactionStatus(mappedStatus);
    }

    /**
     * 解析resolve状态按商户响应编码，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String resolveStatusByMerchantResponseCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String safeCode = code.trim();
        if (ApiResultEnum.PAYMENT_SUCCESS.getCode().equalsIgnoreCase(safeCode)
                || ApiResultEnum.SUCCESS.getCode().equalsIgnoreCase(safeCode)) {
            return "SUCCESS";
        }
        if (ApiResultEnum.PAYMENT_REJECTED.getCode().equalsIgnoreCase(safeCode)
                || ApiResultEnum.PAYMENT_REJECTED_BY_ISSUER.getCode().equalsIgnoreCase(safeCode)) {
            return "FAILED";
        }
        if (ApiResultEnum.PENDING.getCode().equalsIgnoreCase(safeCode)) {
            return "PENDING";
        }
        if (ApiResultEnum.PROCESSING.getCode().equalsIgnoreCase(safeCode)) {
            return "PROCESSING";
        }
        return null;
    }

    /**
     * 整理订单映射器，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private RowMapper<TransactionOrderResponse> orderMapper() {
        return (rs, rowNum) -> {
            TransactionOrderResponse row = new TransactionOrderResponse();
            row.setOperationId(rs.getString("operation_id"));
            row.setRootTransactionId(rs.getString("root_transaction_id"));
            row.setLatestTransactionId(rs.getString("latest_transaction_id"));
            row.setMerchantId(rs.getString("merchant_id"));
            row.setMerchantOrderNo(rs.getString("merchant_order_no"));
            row.setMerchantOrderId(rs.getString("merchant_order_id"));
            row.setPaymentMethod(rs.getString("payment_method"));
            row.setPaymentBrand(rs.getString("payment_brand"));
            row.setTransactionType(rs.getString("transaction_type"));
            row.setTransactionStatus(rs.getString("transaction_status"));
            row.setLifecycleStatus(rs.getString("transaction_status"));
            row.setLifecycleStatusMessage(rs.getString("transaction_status"));
            row.setProcessStage(rs.getString("process_stage"));
            row.setLabelCurrency(rs.getString("label_currency"));
            row.setLabelAmount(rs.getBigDecimal("label_amount"));
            row.setTransactionCurrency(rs.getString("transaction_currency"));
            row.setTransactionAmount(rs.getBigDecimal("transaction_amount"));
            row.setCurrentAmount(rs.getBigDecimal("transaction_amount"));
            row.setCurrentCurrency(rs.getString("transaction_currency"));
            row.setCurrencyExponent(nullableInt(rs, "currency_exponent"));
            row.setTransactionRate(defaultRate(rs.getBigDecimal("transaction_rate")));
            row.setDccEnabled(nullableInt(rs, "dcc_enabled"));
            row.setEdcEnabled(nullableInt(rs, "edc_enabled"));
            row.setMerchantResponseCode(resolveMerchantResponseCode(row.getTransactionStatus()));
            row.setMerchantResponseMessage(resolveMerchantResponseMessage(row.getTransactionStatus()));
            row.setAuthorizedAmount(rs.getBigDecimal("authorized_amount"));
            row.setCapturedAmount(rs.getBigDecimal("captured_amount"));
            row.setRefundedAmount(rs.getBigDecimal("refunded_amount"));
            row.setAvailableCaptureAmount(rs.getBigDecimal("available_capture_amount"));
            row.setAvailableRefundAmount(rs.getBigDecimal("available_refund_amount"));
            row.setSettlementStatus(rs.getString("settlement_status"));
            row.setReconciliationStatus(rs.getString("reconciliation_status"));
            row.setAccountingStatus(rs.getString("accounting_status"));
            row.setChannelMatchStatus(rs.getString("channel_match_status"));
            row.setChannelCode(rs.getString("channel_code"));
            row.setChannelOrderNo(rs.getString("channel_order_no"));
            row.setTransactionDateTime(localDateTime(rs, "transaction_date_time"));
            row.setRootTransactionDateTime(row.getTransactionDateTime());
            row.setTransactionTimeZone(rs.getString("transaction_time_zone"));
            return row;
        };
    }

    /**
     * 整理动作映射器，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param joinedPaymentInfo joined Payment Info 输入值，参与 joinedpayment信息 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private RowMapper<TransactionOperationResponse> operationMapper(boolean joinedPaymentInfo) {
        return (rs, rowNum) -> {
            TransactionOperationResponse row = new TransactionOperationResponse();
            row.setOperationId(rs.getString("operation_id"));
            row.setTransactionId(rs.getString("transaction_id"));
            row.setSourceTransactionId(rs.getString("source_transaction_id"));
            row.setMerchantId(rs.getString("merchant_id"));
            row.setMerchantOrderNo(rs.getString("merchant_order_no"));
            row.setMerchantOrderId(rs.getString("merchant_order_id"));
            row.setOperationSequence(nullableInt(rs, "operation_sequence"));
            row.setTransactionType(rs.getString("transaction_type"));
            row.setTransactionStatus(rs.getString("transaction_status"));
            row.setProcessStage(rs.getString("process_stage"));
            row.setLabelCurrency(rs.getString("label_currency"));
            row.setLabelAmount(rs.getBigDecimal("label_amount"));
            row.setTransactionCurrency(rs.getString("transaction_currency"));
            row.setTransactionAmount(rs.getBigDecimal("transaction_amount"));
            row.setCurrencyExponent(nullableInt(rs, "currency_exponent"));
            row.setTransactionRate(defaultRate(rs.getBigDecimal("transaction_rate")));
            row.setDccEnabled(nullableInt(rs, "dcc_enabled"));
            row.setEdcEnabled(nullableInt(rs, "edc_enabled"));
            row.setMerchantResponseCode(resolveMerchantResponseCode(row.getTransactionStatus()));
            row.setMerchantResponseMessage(resolveMerchantResponseMessage(row.getTransactionStatus()));
            row.setPaymentMethod(joinedPaymentInfo ? rs.getString("joined_payment_method") : null);
            row.setPaymentBrand(joinedPaymentInfo ? rs.getString("joined_payment_brand") : null);
            row.setCardBin(joinedPaymentInfo ? rs.getString("joined_card_bin") : null);
            row.setCardNumberMasked(joinedPaymentInfo ? rs.getString("joined_card_number_masked") : null);
            row.setAuthorizedAmount(getBigDecimalIfExists(rs, "lifecycle_authorized_amount"));
            row.setCapturedAmount(getBigDecimalIfExists(rs, "lifecycle_captured_amount"));
            row.setRefundedAmount(getBigDecimalIfExists(rs, "lifecycle_refunded_amount"));
            row.setAvailableCaptureAmount(getBigDecimalIfExists(rs, "lifecycle_available_capture_amount"));
            row.setAvailableRefundAmount(getBigDecimalIfExists(rs, "lifecycle_available_refund_amount"));
            row.setChannelCode(rs.getString("channel_code"));
            row.setChannelOrderNo(rs.getString("channel_order_no"));
            row.setChannelTransactionId(rs.getString("channel_transaction_id"));
            row.setChannelResponseCode(rs.getString("channel_response_code"));
            row.setChannelResponseMessage(rs.getString("channel_response_message"));
            row.setAuthCode(rs.getString("auth_code"));
            row.setAcquirerReferenceNo(rs.getString("acquirer_reference_no"));
            row.setSettlementStatus(rs.getString("settlement_status"));
            row.setReconciliationStatus(rs.getString("reconciliation_status"));
            row.setAccountingStatus(rs.getString("accounting_status"));
            row.setChannelMatchStatus(rs.getString("channel_match_status"));
            row.setTransactionDateTime(localDateTime(rs, "transaction_date_time"));
            row.setOperationTime(localDateTime(rs, "operation_time"));
            row.setAccessType("DIRECT_API");
            return row;
        };
    }

    /**
     * 统计汇总映射器，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private RowMapper<SummaryRow> summaryMapper() {
        return (rs, rowNum) -> new SummaryRow(
                getStringIfExists(rs, "transaction_status"),
                getStringIfExists(rs, "payment_method"),
                getStringIfExists(rs, "payment_brand"),
                rs.getString("currency"),
                nullableInt(rs, "currency_exponent"),
                rs.getLong("count"),
                rs.getBigDecimal("amount"));
    }

    /**
     * 查询按交易号定位的动作单，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param transactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
     * @param transactionDateTime 列表返回的真实毫秒分片时间
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private TransactionOperationResponse selectOperationByTransactionId(String table,
                                                                         String transactionId,
                                                                         LocalDateTime transactionDateTime) {
        List<TransactionOperationResponse> rows = jdbcTemplate.query("""
                SELECT o.*,
                       p.payment_method AS joined_payment_method,
                       p.payment_brand AS joined_payment_brand,
                       p.card_bin AS joined_card_bin,
                       p.card_number_masked AS joined_card_number_masked
                FROM %s o
                LEFT JOIN %s p ON p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time
                WHERE o.deleted = 0
                  AND o.transaction_id = :transactionId
                  AND o.transaction_date_time = :transactionDateTime
                LIMIT 1
                """.formatted(table, TRANSACTION_PAYMENT_METHOD_INFO_TABLE),
                new MapSqlParameterSource()
                        .addValue("transactionId", transactionId)
                        .addValue("transactionDateTime", transactionDateTime),
                operationMapper(true));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 查询订单by动作ID，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param operationId 平台操作号，用于定位单次授权、请款、退款、撤销或通知动作
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private TransactionOrderResponse selectOrderByOperationId(String table,
                                                               String operationId,
                                                               LocalDateTime transactionDateTime) {
        List<TransactionOrderResponse> rows = jdbcTemplate.query("""
                SELECT *
                FROM %s
                WHERE deleted = 0
                  AND operation_id = :operationId
                  AND transaction_date_time = :transactionDateTime
                LIMIT 1
                """.formatted(table), new MapSqlParameterSource()
                .addValue("operationId", operationId)
                .addValue("transactionDateTime", transactionDateTime), orderMapper());
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 查询按操作号定位的动作单，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param operationId 平台操作号，用于定位单次授权、请款、退款、撤销或通知动作
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<TransactionOperationResponse> selectOperationsByOperationId(String operationId, LocalDateTime beginTime, LocalDateTime endTime) {
        return jdbcTemplate.query("""
                    SELECT o.*,
                           p.payment_method AS joined_payment_method,
                           p.payment_brand AS joined_payment_brand,
                           p.card_bin AS joined_card_bin,
                           p.card_number_masked AS joined_card_number_masked
                    FROM %s o
                    LEFT JOIN %s p ON p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time
                    WHERE o.deleted = 0
                      AND o.operation_id = :operationId
                      AND o.transaction_date_time >= :beginTime
                      AND o.transaction_date_time < :endTime
                    ORDER BY o.operation_sequence ASC, o.operation_time ASC
                    """.formatted(TRANSACTION_OPERATION_TABLE, TRANSACTION_PAYMENT_METHOD_INFO_TABLE),
                    new MapSqlParameterSource()
                            .addValue("operationId", operationId)
                            .addValue("beginTime", beginTime)
                            .addValue("endTime", exclusiveEnd(endTime)), operationMapper(true));
    }

    /**
     * 查询mapsby动作ID，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param logicalTable 逻辑表名，用于按交易时间解析真实物理分表
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param operationId 平台操作号，用于定位单次授权、请款、退款、撤销或通知动作
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<Map<String, Object>> selectMapsByOperationId(String logicalTable, LocalDateTime beginTime, LocalDateTime endTime, String operationId) {
        boolean softDeleteTable = hasSoftDeleteColumn(logicalTable);
        MapSqlParameterSource params = baseParams(beginTime, endTime).addValue("operationId", operationId);
        List<Map<String, Object>> rows = toCamelCaseRows(jdbcTemplate.queryForList("""
                    SELECT %s
                    FROM %s
                    WHERE %s
                      AND operation_id = :operationId
                      AND transaction_date_time >= :beginTime
                      AND transaction_date_time < :endTime
                    ORDER BY transaction_date_time ASC, id ASC
                    """.formatted(adminDetailProjection(logicalTable), logicalTable,
                    softDeleteCondition(softDeleteTable)),
                    params));
        if (TRANSACTION_MERCHANT_NOTIFICATION_TABLE.equals(logicalTable)) {
            rows.forEach(row -> row.remove("notifyConfigSnapshotJson"));
        } else if (TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE.equals(logicalTable)) {
            decorateMerchantNotificationLogRows(rows);
        }
        return rows;
    }

    /** 补齐商户通知投递日志的固定协议字段和 HTTP 响应完成时间。 */
    private void decorateMerchantNotificationLogRows(List<Map<String, Object>> rows) {
        rows.forEach(row -> {
            row.put("httpMethod", "POST");
            // 投递日志在 HTTP 完成后落库，create_time 即本次请求的响应完成时间。
            row.putIfAbsent("responseTime", row.get("createTime"));
        });
    }

    /** 返回管理端详情允许读取的字段投影。 */
    private String adminDetailProjection(String logicalTable) {
        return TRANSACTION_MERCHANT_NOTIFICATION_TABLE.equals(logicalTable)
                ? TRANSACTION_MERCHANT_NOTIFICATION_ADMIN_PROJECTION
                : "*";
    }

    /**
     * 在 transaction 逻辑数据源执行普通读或主库强一致读。
     */
    private <T> T executeRead(boolean primaryOnly, Supplier<T> query) {
        return primaryOnly
                ? transactionLogicalReadExecutor.readPrimary(query)
                : transactionLogicalReadExecutor.read(query);
    }

    /** 将包含式结束时间转换为 MySQL DATETIME(3) 半开区间上界。 */
    private LocalDateTime exclusiveEnd(LocalDateTime endTime) {
        LocalDateTime actualEnd = endTime == null ? LocalDateTime.now() : endTime;
        return actualEnd.plusNanos(1_000_000L);
    }

    /**
     * 判断交易逻辑表是否包含软删除字段。
     *
     * @param logicalTable 交易逻辑表名
     * @return true 表示查询时需要追加 deleted = 0
     */
    private boolean hasSoftDeleteColumn(String logicalTable) {
        return SOFT_DELETE_LOGICAL_TABLES.contains(logicalTable);
    }

    /**
     * 生成 Map 型交易日志查询的基础过滤条件。
     *
     * @param softDeleteTable 当前逻辑表是否有 deleted 字段
     * @return 可直接拼入 WHERE 后的条件
     */
    private String softDeleteCondition(boolean softDeleteTable) {
        return softDeleteTable ? "deleted = 0" : "1 = 1";
    }

    /**
     * 规范化offset，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long offset(PageRequest query) {
        return (query.safePageNo() - 1) * query.safePageSize();
    }

    /**
     * 整理时间值，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param params params 输入值，参与 参数 的查询、校验、转换、写入或日志摘要
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LocalDateTime timeValue(MapSqlParameterSource params, String key) {
        Object value = params.getValue(key);
        return value instanceof LocalDateTime localDateTime ? localDateTime : null;
    }

    /**
     * 构造copy对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private MapSqlParameterSource copy(MapSqlParameterSource source) {
        MapSqlParameterSource target = new MapSqlParameterSource();
        for (String name : source.getValues().keySet()) {
            target.addValue(name, source.getValue(name));
        }
        return target;
    }

    /**
     * 将 JDBC Map 结果中的数据库字段名转换为前端统一使用的 lowerCamelCase。
     * <p>
     * 分表详情聚合使用 SELECT * 读取日志表，MySQL 返回的 key 是 transaction_id、request_body_json_masked
     * 这类下划线字段；若原样返回，前端会因读取 transactionId、requestBodyJsonMasked 失败而展示为空。
     *
     * @param rows JDBC 原始 Map 行
     * @return 字段名转换后的 Map 行
     */
    private List<Map<String, Object>> toCamelCaseRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toCamelCaseRow).toList();
    }

    /**
     * 转换单行 Map 字段名，并保留字段原始顺序，方便管理端日志按表结构阅读。
     *
     * @param row JDBC 原始 Map 行
     * @return lowerCamelCase 字段名 Map
     */
    private Map<String, Object> toCamelCaseRow(Map<String, Object> row) {
        Map<String, Object> converted = new LinkedHashMap<>();
        row.forEach((key, value) -> {
            String convertedKey = toLowerCamelCase(key);
            converted.put(convertedKey, frontendSafeMapValue(convertedKey, value));
        });
        return converted;
    }

    /**
     * 将数据库数值型标识转换为字符串，防止 JavaScript 解析超过 2^53-1 的 BIGINT 时丢失精度。
     * 金额、次数、耗时等非标识数值保持原类型，避免改变管理端统计和排序语义。
     *
     * @param key   已转换为 lowerCamelCase 的响应字段名
     * @param value JDBC 返回值
     * @return 可安全交给浏览器解析的响应值
     */
    private Object frontendSafeMapValue(String key, Object value) {
        boolean identifierKey = "id".equals(key) || key != null && key.endsWith("Id");
        if (identifierKey && (value instanceof Long || value instanceof BigInteger)) {
            return value.toString();
        }
        return value;
    }

    /**
     * 将下划线字段名转换为 lowerCamelCase；已经是驼峰的字段保持可读形式。
     *
     * @param value 数据库字段名或 SQL 别名
     * @return lowerCamelCase 字段名
     */
    private String toLowerCamelCase(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value.length());
        boolean upperNext = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '_') {
                upperNext = builder.length() > 0;
                continue;
            }
            if (upperNext) {
                builder.append(Character.toUpperCase(current));
                upperNext = false;
                continue;
            }
            builder.append(builder.isEmpty() ? Character.toLowerCase(current) : current);
        }
        return builder.toString();
    }

    /**
     * 整理可空整数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rs rs 输入值，参与 rs 的查询、校验、转换、写入或日志摘要
     * @param column column 输入值，参与 column 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * 整理本地日期时间，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rs rs 输入值，参与 rs 的查询、校验、转换、写入或日志摘要
     * @param column column 输入值，参与 column 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }

    /**
     * 整理默认汇率，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rate rate 输入值，参与 汇率 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private BigDecimal defaultRate(BigDecimal rate) {
        return rate == null ? new BigDecimal("1.00000000") : rate;
    }

    /**
     * 解析resolve商户响应编码，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 构造、转换或解析后的业务值
     */
    private String resolveMerchantResponseCode(String status) {
        if ("SUCCESS".equals(status)) {
            return ApiResultEnum.PAYMENT_SUCCESS.getCode();
        }
        if ("FAILED".equals(status)) {
            return ApiResultEnum.PAYMENT_REJECTED.getCode();
        }
        if ("PENDING".equals(status)) {
            return ApiResultEnum.PENDING.getCode();
        }
        return ApiResultEnum.PROCESSING.getCode();
    }

    /**
     * 解析resolve商户响应说明，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 构造、转换或解析后的业务值
     */
    private String resolveMerchantResponseMessage(String status) {
        if ("SUCCESS".equals(status)) {
            return ApiResultEnum.PAYMENT_SUCCESS.getMessage();
        }
        if ("FAILED".equals(status)) {
            return ApiResultEnum.PAYMENT_REJECTED.getMessage();
        }
        if ("PENDING".equals(status)) {
            return ApiResultEnum.PENDING.getMessage();
        }
        return ApiResultEnum.PROCESSING.getMessage();
    }

    /**
     * 查询stringifexists，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param rs rs 输入值，参与 rs 的查询、校验、转换、写入或日志摘要
     * @param column column 输入值，参与 column 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private String getStringIfExists(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException exception) {
            return null;
        }
    }

    /**
     * 查询bigdecimalifexists，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param rs rs 输入值，参与 rs 的查询、校验、转换、写入或日志摘要
     * @param column column 输入值，参与 column 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private BigDecimal getBigDecimalIfExists(ResultSet rs, String column) {
        try {
            return rs.getBigDecimal(column);
        } catch (SQLException exception) {
            return null;
        }
    }

    /** 将最早已登记季度转换为 ShardingSphere 可路由的半开范围起点。 */
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

    private record QueryTimeRange(LocalDateTime beginTime, LocalDateTime endTime, String queryTimeZone) {
    }

    private record SummaryRow(String transactionStatus,
                              String paymentMethod,
                              String paymentBrand,
                              String currency,
                              Integer currencyExponent,
                              long count,
                              BigDecimal amount) {
    }

    private static final class SummaryAccumulator {

        /**
         * total Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private long totalCount;
        /**
         * success Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private long successCount;
        /**
         * failed Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private long failedCount;
        /**
         * amounts，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private final Map<String, TransactionAmountSummaryResponse> amounts = new LinkedHashMap<>();
        /**
         * success Amounts，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private final Map<String, TransactionAmountSummaryResponse> successAmounts = new LinkedHashMap<>();
        /**
         * failed Amounts，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private final Map<String, TransactionAmountSummaryResponse> failedAmounts = new LinkedHashMap<>();
        /**
         * payments，用于保存 Summary Accumulator 中与 payments 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final Map<String, TransactionPaymentMethodSummaryResponse> payments = new LinkedHashMap<>();

        /**
         * 创建金额，完成必要校验后写入或委托下游服务处理。
         * <p>
         * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
         * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
         * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
         * </p>
         * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
         */
        private void addAmount(SummaryRow row) {
            totalCount += row.count();
            addAmount(amounts, row);
            if ("SUCCESS".equals(row.transactionStatus())) {
                successCount += row.count();
                addAmount(successAmounts, row);
            }
            if ("FAILED".equals(row.transactionStatus())) {
                failedCount += row.count();
                addAmount(failedAmounts, row);
            }
        }

        /**
         * 创建支付交易，完成必要校验后写入或委托下游服务处理。
         * <p>
         * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
         * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
         * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
         * </p>
         * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
         */
        private void addPayment(SummaryRow row) {
            String key = (row.paymentMethod() == null ? "UNKNOWN" : row.paymentMethod()) + "|" + (row.paymentBrand() == null ? "" : row.paymentBrand());
            TransactionPaymentMethodSummaryResponse payment = payments.computeIfAbsent(key, ignored -> {
                TransactionPaymentMethodSummaryResponse response = new TransactionPaymentMethodSummaryResponse();
                response.setPaymentMethod(row.paymentMethod());
                response.setPaymentBrand(row.paymentBrand());
                response.setAmountSummaries(new ArrayList<>());
                return response;
            });
            payment.setCount(payment.getCount() + row.count());
            Map<String, TransactionAmountSummaryResponse> map = new LinkedHashMap<>();
            payment.getAmountSummaries().forEach(item -> map.put(item.getCurrency(), item));
            addAmount(map, row);
            payment.setAmountSummaries(new ArrayList<>(map.values()));
        }

        /**
         * 创建金额，完成必要校验后写入或委托下游服务处理。
         * <p>
         * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
         * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
         * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
         * </p>
         * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
         * @param map map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
         * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
         */
        private void addAmount(Map<String, TransactionAmountSummaryResponse> map, SummaryRow row) {
            TransactionAmountSummaryResponse amount = map.computeIfAbsent(row.currency(), ignored -> {
                TransactionAmountSummaryResponse response = new TransactionAmountSummaryResponse();
                response.setCurrency(row.currency());
                response.setAmount(BigDecimal.ZERO);
                response.setCurrencyExponent(row.currencyExponent());
                return response;
            });
            amount.setAmount(amount.getAmount().add(row.amount() == null ? BigDecimal.ZERO : row.amount()));
        }

        /**
         * 构造response对象，完成字段复制、格式标准化和敏感数据处理。
         * <p>
         * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
         * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
         * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
         * </p>
         * @return 构造、转换或解析后的业务值
         */
        private TransactionOperationSummaryResponse toResponse() {
            TransactionOperationSummaryResponse response = new TransactionOperationSummaryResponse();
            response.setTotalCount(totalCount);
            response.setSuccessCount(successCount);
            response.setFailedCount(failedCount);
            response.setAmountSummaries(new ArrayList<>(amounts.values()));
            response.setSuccessAmountSummaries(new ArrayList<>(successAmounts.values()));
            response.setFailedAmountSummaries(new ArrayList<>(failedAmounts.values()));
            response.setPaymentMethodSummaries(new ArrayList<>(payments.values()));
            return response;
        }
    }
}
