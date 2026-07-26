package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
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
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingRangeTableContext;
import com.scott.payment.component.db.sharding.ShardingSingleTableContext;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminTransactionQueryService
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 管理后台交易 JDBC 只读查询实现，位于 service-admin 服务实现层，按公共分表组件解析物理表并读取备库。
 * @status : create
 */
@Service
public class JdbcAdminTransactionQueryService implements AdminTransactionQueryService {

    /**
     * TRANSACTION ORDER TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_ORDER_TABLE = "transaction_order";
    /**
     * TRANSACTION OPERATION TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_OPERATION_TABLE = "transaction_operation";
    /**
     * TRANSACTION PAYMENT METHOD INFO TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_PAYMENT_METHOD_INFO_TABLE = "transaction_payment_method_info";
    /**
     * TRANSACTION CHANNEL INTERACTION LOG TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE = "transaction_channel_interaction_log";
    /**
     * TRANSACTION CHANNEL CALLBACK TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_CHANNEL_CALLBACK_TABLE = "transaction_channel_callback";
    /**
     * TRANSACTION STATUS HISTORY TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_STATUS_HISTORY_TABLE = "transaction_status_history";
    /**
     * TRANSACTION FLOW EVENT TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_FLOW_EVENT_TABLE = "transaction_flow_event";
    /**
     * TRANSACTION AMOUNT CHANGE LOG TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_AMOUNT_CHANGE_LOG_TABLE = "transaction_amount_change_log";
    /**
     * TRANSACTION CHANNEL REQUEST TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_CHANNEL_REQUEST_TABLE = "transaction_channel_request";
    /**
     * TRANSACTION CHANNEL CALLBACK LOG TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_CHANNEL_CALLBACK_LOG_TABLE = "transaction_channel_callback_log";
    /**
     * TRANSACTION MERCHANT NOTIFICATION TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_MERCHANT_NOTIFICATION_TABLE = "transaction_merchant_notification";
    /**
     * TRANSACTION MERCHANT NOTIFICATION LOG TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE = "transaction_merchant_notification_log";
    /**
     * TRANSACTION MERCHANT API INTERACTION LOG TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_MERCHANT_API_INTERACTION_LOG_TABLE = "transaction_merchant_api_interaction_log";
    /**
     * DEFAULT QUERY TIME ZONE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
     * jdbc Template 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;
    /**
     * sharding Data Template 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ShardingDataTemplate shardingDataTemplate;
    /**
     * transaction Sharding Key Parser 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionShardingKeyParser transactionShardingKeyParser;

/**
 * 创建 JdbcAdminTransactionQueryService 实例并注入其运行所需依赖。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param jdbcTemplate jdbc Template 输入值，含义由调用方法名称和所属业务对象限定
 * @param shardingDataTemplate sharding Data Template 输入值，含义由调用方法名称和所属业务对象限定
 * @param transactionShardingKeyParser transaction Sharding Key Parser 输入值，含义由调用方法名称和所属业务对象限定
 */
    public JdbcAdminTransactionQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                            ShardingDataTemplate shardingDataTemplate,
                                            TransactionShardingKeyParser transactionShardingKeyParser) {
        this.jdbcTemplate = jdbcTemplate;
        this.shardingDataTemplate = shardingDataTemplate;
        this.transactionShardingKeyParser = transactionShardingKeyParser;
    }

    @Override
    @DS(DataSourceName.SLAVE)
    /**
     * 完成 page Orders 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        long total = 0L;
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        List<TransactionOrderResponse> rows = new ArrayList<>();
        for (String table : physicalTablesInRange(TRANSACTION_ORDER_TABLE, safeQuery.getBeginTime(), safeQuery.getEndTime())) {
            long count = count(table, orderWhereSql(safeQuery), orderParams(safeQuery));
            total += count;
            if (rows.size() < limit && offset < count) {
                rows.addAll(selectOrders(table, safeQuery, offset, limit - rows.size()));
                offset = 0;
            } else {
                offset = Math.max(0, offset - count);
            }
        }
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), rows);
    }

    @Override
    @DS(DataSourceName.SLAVE)
    /**
     * 完成 page Operations 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    public PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query) {
        return pageOperationsNormalized(normalize(query));
    }

    @Override
    @DS(DataSourceName.SLAVE)
    /**
     * 完成 search Operations 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    public TransactionOperationSearchResponse searchOperations(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        TransactionOperationSearchResponse response = new TransactionOperationSearchResponse();
        response.setPage(pageOperationsNormalized(safeQuery));
        response.setSummary(operationSummary(safeQuery));
        return response;
    }

    @Override
    @DS(DataSourceName.SLAVE)
    /**
     * 完成 detail 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
     * @return 当前方法计算或转换后的业务结果
     */
    public TransactionDetailResponse detail(String transactionId) {
        if (!StringUtils.hasText(transactionId)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime transactionDateTime = transactionShardingKeyParser.parseTransactionDateTime(transactionId);
        if (transactionDateTime == null) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOperationResponse sourceOperation = selectOperationByTransactionId(
                physicalTable(TRANSACTION_OPERATION_TABLE, transactionDateTime), transactionId);
        if (sourceOperation == null) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime orderTime = transactionShardingKeyParser.parseOperationDateTime(sourceOperation.getOperationId());
        if (orderTime == null) {
            orderTime = sourceOperation.getTransactionDateTime();
        }
        TransactionOrderResponse order = selectOrderByOperationId(physicalTable(TRANSACTION_ORDER_TABLE, orderTime), sourceOperation.getOperationId());
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
        TransactionDetailResponse detail = new TransactionDetailResponse();
        detail.setOrder(order);
        detail.setOperations(operations);
        detail.setStatusHistory(selectMapsByOperationId(TRANSACTION_STATUS_HISTORY_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setFlowEvents(selectMapsByOperationId(TRANSACTION_FLOW_EVENT_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setAmountChanges(selectMapsByOperationId(TRANSACTION_AMOUNT_CHANGE_LOG_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setChannelRequests(selectMapsByOperationId(TRANSACTION_CHANNEL_REQUEST_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setChannelInteractionLogs(selectMapsByOperationId(TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setChannelCallbacks(selectMapsByOperationId(TRANSACTION_CHANNEL_CALLBACK_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setChannelCallbackLogs(selectMapsByOperationId(TRANSACTION_CHANNEL_CALLBACK_LOG_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setMerchantNotifications(selectMapsByOperationId(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setMerchantNotificationLogs(selectMapsByOperationId(TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE, beginTime, endTime, order.getOperationId()));
        detail.setMerchantApiInteractionLogs(selectOptionalMapsByOperationId(TRANSACTION_MERCHANT_API_INTERACTION_LOG_TABLE, beginTime, endTime, order.getOperationId()));
        return detail;
    }

    @Override
    @DS(DataSourceName.SLAVE)
    /**
     * 完成 page Channel Logs 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public PageResult<Map<String, Object>> pageChannelLogs(ChannelLogQuery query) {
        ChannelLogQuery safeQuery = normalize(query);
        return pageChannelLogsNormalized(safeQuery);
    }

    @Override
    @DS(DataSourceName.SLAVE)
    /**
     * 完成 page Channel Callbacks 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public PageResult<Map<String, Object>> pageChannelCallbacks(ChannelCallbackQuery query) {
        ChannelCallbackQuery safeQuery = normalize(query);
        return pageMaps(TRANSACTION_CHANNEL_CALLBACK_TABLE, channelCallbackWhereSql(safeQuery), channelCallbackParams(safeQuery), safeQuery);
    }

    @Override
    @DS(DataSourceName.SLAVE)
    /**
     * 完成 page Merchant Notifications 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public PageResult<Map<String, Object>> pageMerchantNotifications(MerchantNotificationQuery query) {
        MerchantNotificationQuery safeQuery = normalize(query);
        return pageMaps(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, merchantNotificationWhereSql(safeQuery), merchantNotificationParams(safeQuery), safeQuery);
    }

    /**
     * 完成 page Operations Normalized 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param safeQuery safe Query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private PageResult<TransactionOperationResponse> pageOperationsNormalized(TransactionPageQuery safeQuery) {
        long total = 0L;
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        List<TransactionOperationResponse> rows = new ArrayList<>();
        for (String table : physicalTablesInRange(TRANSACTION_OPERATION_TABLE, safeQuery.getBeginTime(), safeQuery.getEndTime())) {
            String paymentTable = paymentInfoTableForOperationTable(table);
            long count = countOperations(table, paymentTable, safeQuery);
            total += count;
            if (rows.size() < limit && offset < count) {
                rows.addAll(selectOperations(table, paymentTable, safeQuery, offset, limit - rows.size()));
                offset = 0;
            } else {
                offset = Math.max(0, offset - count);
            }
        }
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), rows);
    }

    /**
     * 完成 page Maps 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param logicalTable logical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param whereSql where Sql 输入值，含义由调用方法名称和所属业务对象限定
     * @param params params 输入值，含义由调用方法名称和所属业务对象限定
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private PageResult<Map<String, Object>> pageMaps(String logicalTable, String whereSql, MapSqlParameterSource params, PageRequest query) {
        long total = 0L;
        long offset = offset(query);
        long limit = query.safePageSize();
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean softDeleteTable = hasSoftDeleteColumn(logicalTable);
        for (String table : physicalTablesInRange(logicalTable, timeValue(params, "beginTime"), timeValue(params, "endTime"))) {
            long count = countMaps(table, whereSql, params, softDeleteTable);
            total += count;
            if (rows.size() < limit && offset < count) {
                rows.addAll(selectMaps(table, whereSql, params, offset, limit - rows.size(), softDeleteTable));
                offset = 0;
            } else {
                offset = Math.max(0, offset - count);
            }
        }
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 完成 page Channel Logs Normalized 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private PageResult<Map<String, Object>> pageChannelLogsNormalized(ChannelLogQuery query) {
        long total = 0L;
        long offset = offset(query);
        long limit = query.safePageSize();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String table : physicalTablesInRange(TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE, query.getBeginTime(), query.getEndTime())) {
            String requestTable = channelRequestTableForInteractionLogTable(table);
            String operationTable = operationTableForInteractionLogTable(table);
            long count = countChannelLogs(table, requestTable, operationTable, query);
            total += count;
            if (rows.size() < limit && offset < count) {
                rows.addAll(selectChannelLogs(table, requestTable, operationTable, query, offset, limit - rows.size()));
                offset = 0;
            } else {
                offset = Math.max(0, offset - count);
            }
        }
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 完成 count 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param table table 输入值，含义由调用方法名称和所属业务对象限定
     * @param whereSql where Sql 输入值，含义由调用方法名称和所属业务对象限定
     * @param params params 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 count Maps 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param table table 输入值，含义由调用方法名称和所属业务对象限定
     * @param whereSql where Sql 输入值，含义由调用方法名称和所属业务对象限定
     * @param params params 输入值，含义由调用方法名称和所属业务对象限定
     * @param softDeleteTable soft Delete Table 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
 * 查询 select Maps 所需数据，未命中时按调用场景返回空值或抛出异常。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param table table 输入值，含义由调用方法名称和所属业务对象限定
 * @param whereSql where Sql 输入值，含义由调用方法名称和所属业务对象限定
 * @param params params 输入值，含义由调用方法名称和所属业务对象限定
 * @param offset offset 输入值，含义由调用方法名称和所属业务对象限定
 * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
 * @param softDeleteTable soft Delete Table 输入值，含义由调用方法名称和所属业务对象限定
 * @return 解析或查询得到的业务值
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
     * 完成 count Channel Logs 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param table table 输入值，含义由调用方法名称和所属业务对象限定
     * @param requestTable request Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param operationTable operation Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private long countChannelLogs(String table, String requestTable, String operationTable, ChannelLogQuery query) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM %s l
                LEFT JOIN %s r ON r.request_id = l.request_id AND r.deleted = 0
                LEFT JOIN %s o ON o.transaction_id = l.transaction_id AND o.deleted = 0
                WHERE l.transaction_date_time >= :beginTime
                  AND l.transaction_date_time <= :endTime
                %s
                """.formatted(table, requestTable, operationTable, channelLogWhereSql(query)), channelLogParams(query), Long.class);
        return count == null ? 0L : count;
    }

/**
 * 查询 select Channel Logs 所需数据，未命中时按调用场景返回空值或抛出异常。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param table table 输入值，含义由调用方法名称和所属业务对象限定
 * @param requestTable request Table 输入值，含义由调用方法名称和所属业务对象限定
 * @param operationTable operation Table 输入值，含义由调用方法名称和所属业务对象限定
 * @param query query 输入值，含义由调用方法名称和所属业务对象限定
 * @param offset offset 输入值，含义由调用方法名称和所属业务对象限定
 * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
 * @return 解析或查询得到的业务值
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
                  AND l.transaction_date_time <= :endTime
                %s
                ORDER BY l.interaction_time DESC, l.id DESC
                LIMIT :offset, :limit
                """.formatted(table, requestTable, operationTable, channelLogWhereSql(query)), params));
    }

    /**
     * 查询 select Orders 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param table table 输入值，含义由调用方法名称和所属业务对象限定
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @param offset offset 输入值，含义由调用方法名称和所属业务对象限定
     * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 完成 count Operations 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param table table 输入值，含义由调用方法名称和所属业务对象限定
     * @param paymentTable payment Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
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
     * 查询 select Operations 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param table table 输入值，含义由调用方法名称和所属业务对象限定
     * @param paymentTable payment Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @param offset offset 输入值，含义由调用方法名称和所属业务对象限定
     * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private List<TransactionOperationResponse> selectOperations(String table, String paymentTable, TransactionPageQuery query, long offset, long limit) {
        MapSqlParameterSource params = operationParams(query).addValue("offset", offset).addValue("limit", limit);
        String orderTable = orderTableForOperationTable(table);
        return jdbcTemplate.query("""
                SELECT o.*,
                       lifecycle.authorized_amount AS lifecycle_authorized_amount,
                       lifecycle.captured_amount AS lifecycle_captured_amount,
                       lifecycle.refunded_amount AS lifecycle_refunded_amount,
                       lifecycle.available_capture_amount AS lifecycle_available_capture_amount,
                       lifecycle.available_refund_amount AS lifecycle_available_refund_amount,
                       p.payment_method AS joined_payment_method,
                       p.payment_brand AS joined_payment_brand,
                       p.card_bin AS joined_card_bin,
                       p.card_number_masked AS joined_card_number_masked
                FROM %s o
                LEFT JOIN %s p ON p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time
                LEFT JOIN %s lifecycle ON lifecycle.operation_id = o.operation_id AND lifecycle.deleted = 0
                WHERE o.deleted = 0
                %s
                ORDER BY o.transaction_date_time DESC, o.id DESC
                LIMIT :offset, :limit
                """.formatted(table, paymentTable, orderTable, operationWhereSql(query, paymentTable)), params, operationMapper(true));
    }

    /**
     * 完成 operation Summary 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private TransactionOperationSummaryResponse operationSummary(TransactionPageQuery query) {
        SummaryAccumulator accumulator = new SummaryAccumulator();
        for (String table : physicalTablesInRange(TRANSACTION_OPERATION_TABLE, query.getBeginTime(), query.getEndTime())) {
            String paymentTable = paymentInfoTableForOperationTable(table);
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
                    """.formatted(table, operationWhereSql(query, paymentTable)), operationParams(query), summaryMapper())
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
                    """.formatted(table, paymentTable, operationWhereSql(query, paymentTable)), operationParams(query), summaryMapper())
                    .forEach(accumulator::addPayment);
        }
        return accumulator.toResponse();
    }

    /**
     * 完成 order Where Sql 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String orderWhereSql(TransactionPageQuery query) {
        StringBuilder sql = new StringBuilder(" AND transaction_date_time >= :beginTime AND transaction_date_time <= :endTime");
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
     * 完成 operation Where Sql 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @param paymentTable payment Table 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private String operationWhereSql(TransactionPageQuery query, String paymentTable) {
        StringBuilder sql = new StringBuilder(" AND o.transaction_date_time >= :beginTime AND o.transaction_date_time <= :endTime");
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
     * 完成 channel Log Where Sql 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 channel Callback Where Sql 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String channelCallbackWhereSql(ChannelCallbackQuery query) {
        StringBuilder sql = new StringBuilder(" AND transaction_date_time >= :beginTime AND transaction_date_time <= :endTime");
        append(sql, query.getChannelCode(), "AND channel_code = :channelCode");
        append(sql, query.getTransactionId(), "AND transaction_id = :transactionId");
        append(sql, query.getChannelOrderNo(), "AND channel_order_no = :channelOrderNo");
        append(sql, query.getChannelTransactionId(), "AND channel_transaction_id = :channelTransactionId");
        append(sql, query.getCallbackStatus(), "AND callback_status = :callbackStatus");
        return sql.toString();
    }

    /**
     * 完成 merchant Notification Where Sql 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String merchantNotificationWhereSql(MerchantNotificationQuery query) {
        StringBuilder sql = new StringBuilder(" AND transaction_date_time >= :beginTime AND transaction_date_time <= :endTime");
        append(sql, query.getMerchantId(), "AND merchant_id = :merchantId");
        append(sql, query.getTransactionId(), "AND transaction_id = :transactionId");
        append(sql, query.getNotifyStatus(), "AND notify_status = :notifyStatus");
        return sql.toString();
    }

    /**
     * 完成 append 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param sql sql 输入值，含义由调用方法名称和所属业务对象限定
     * @param value 待校验或转换的原始值
     * @param fragment fragment 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void append(StringBuilder sql, String value, String fragment) {
        if (StringUtils.hasText(value)) {
            sql.append(' ').append(fragment);
        }
    }

    /**
     * 完成 order Params 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 operation Params 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
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
     * 完成 channel Log Params 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 channel Callback Params 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 merchant Notification Params 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private MapSqlParameterSource merchantNotificationParams(MerchantNotificationQuery query) {
        return baseParams(query.getBeginTime(), query.getEndTime())
                .addValue("merchantId", query.getMerchantId())
                .addValue("transactionId", query.getTransactionId())
                .addValue("notifyStatus", query.getNotifyStatus());
    }

    /**
     * 完成 base Params 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 当前方法计算或转换后的业务结果
     */
    private MapSqlParameterSource baseParams(LocalDateTime beginTime, LocalDateTime endTime) {
        return new MapSqlParameterSource().addValue("beginTime", beginTime).addValue("endTime", endTime);
    }

    /**
     * 标准化 normalize 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private TransactionPageQuery normalize(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = query == null ? new TransactionPageQuery() : query;
        fillDefaultTimeRange(safeQuery);
        normalizeMerchantResponseCode(safeQuery);
        return safeQuery;
    }

    /**
     * 标准化 normalize 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private ChannelLogQuery normalize(ChannelLogQuery query) {
        ChannelLogQuery safeQuery = query == null ? new ChannelLogQuery() : query;
        QueryTimeRange range = normalizeQueryTimeRange(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        safeQuery.setBeginTime(range.beginTime());
        safeQuery.setEndTime(range.endTime());
        safeQuery.setQueryTimeZone(range.queryTimeZone());
        return safeQuery;
    }

    /**
     * 标准化 normalize 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private ChannelCallbackQuery normalize(ChannelCallbackQuery query) {
        ChannelCallbackQuery safeQuery = query == null ? new ChannelCallbackQuery() : query;
        QueryTimeRange range = normalizeQueryTimeRange(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        safeQuery.setBeginTime(range.beginTime());
        safeQuery.setEndTime(range.endTime());
        safeQuery.setQueryTimeZone(range.queryTimeZone());
        return safeQuery;
    }

    /**
     * 标准化 normalize 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private MerchantNotificationQuery normalize(MerchantNotificationQuery query) {
        MerchantNotificationQuery safeQuery = query == null ? new MerchantNotificationQuery() : query;
        QueryTimeRange range = normalizeQueryTimeRange(safeQuery.getBeginTime(), safeQuery.getEndTime(), safeQuery.getQueryTimeZone());
        safeQuery.setBeginTime(range.beginTime());
        safeQuery.setEndTime(range.endTime());
        safeQuery.setQueryTimeZone(range.queryTimeZone());
        return safeQuery;
    }

    /**
     * 填充 fill Default Time Range 相关字段，保持来源对象与目标对象的业务含义一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void fillDefaultTimeRange(TransactionPageQuery query) {
        QueryTimeRange range = normalizeQueryTimeRange(query.getBeginTime(), query.getEndTime(), query.getQueryTimeZone());
        query.setBeginTime(range.beginTime());
        query.setEndTime(range.endTime());
        query.setQueryTimeZone(range.queryTimeZone());
    }

    /**
     * 标准化 normalize Query Time Range 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param queryTimeZone 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 标准化后的业务字段值
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
     * 解析 resolve Query Zone 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param queryTimeZone 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 解析或查询得到的业务值
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
     * 标准化 normalize Zone Id 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param zone zone 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
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
     * 完成 convert Between Zones 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param sourceTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param sourceZone source Zone 输入值，含义由调用方法名称和所属业务对象限定
     * @param targetZone target Zone 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private LocalDateTime convertBetweenZones(LocalDateTime sourceTime, ZoneId sourceZone, ZoneId targetZone) {
        ZonedDateTime source = sourceTime.atZone(sourceZone);
        return source.withZoneSameInstant(targetZone).toLocalDateTime();
    }

    /**
     * 标准化 normalize Merchant Response Code 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
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
     * 解析 resolve Status By Merchant Response Code 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param code code 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 完成 order Mapper 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
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
            row.setTransactionTimeZone(rs.getString("transaction_time_zone"));
            return row;
        };
    }

    /**
     * 完成 operation Mapper 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param joinedPaymentInfo joined Payment Info 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
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
     * 计算 summary Mapper 对应的数值结果，调用方负责保证金额和币种上下文一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
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
     * 查询 select Operation By Transaction Id 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param table table 输入值，含义由调用方法名称和所属业务对象限定
     * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private TransactionOperationResponse selectOperationByTransactionId(String table, String transactionId) {
        List<TransactionOperationResponse> rows = jdbcTemplate.query("""
                SELECT o.*,
                       p.payment_method AS joined_payment_method,
                       p.payment_brand AS joined_payment_brand,
                       p.card_bin AS joined_card_bin,
                       p.card_number_masked AS joined_card_number_masked
                FROM %s o
                LEFT JOIN %s p ON p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time
                WHERE o.deleted = 0 AND o.transaction_id = :transactionId
                LIMIT 1
                """.formatted(table, paymentInfoTableForOperationTable(table)),
                new MapSqlParameterSource("transactionId", transactionId), operationMapper(true));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 查询 select Order By Operation Id 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param table table 输入值，含义由调用方法名称和所属业务对象限定
     * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private TransactionOrderResponse selectOrderByOperationId(String table, String operationId) {
        List<TransactionOrderResponse> rows = jdbcTemplate.query("""
                SELECT *
                FROM %s
                WHERE deleted = 0 AND operation_id = :operationId
                LIMIT 1
                """.formatted(table), new MapSqlParameterSource("operationId", operationId), orderMapper());
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 查询 select Operations By Operation Id 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private List<TransactionOperationResponse> selectOperationsByOperationId(String operationId, LocalDateTime beginTime, LocalDateTime endTime) {
        List<TransactionOperationResponse> rows = new ArrayList<>();
        for (String table : physicalTablesInRange(TRANSACTION_OPERATION_TABLE, beginTime, endTime)) {
            rows.addAll(jdbcTemplate.query("""
                    SELECT o.*,
                           p.payment_method AS joined_payment_method,
                           p.payment_brand AS joined_payment_brand,
                           p.card_bin AS joined_card_bin,
                           p.card_number_masked AS joined_card_number_masked
                    FROM %s o
                    LEFT JOIN %s p ON p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time
                    WHERE o.deleted = 0 AND o.operation_id = :operationId
                    ORDER BY o.operation_sequence ASC, o.operation_time ASC
                    """.formatted(table, paymentInfoTableForOperationTable(table)),
                    new MapSqlParameterSource("operationId", operationId), operationMapper(true)));
        }
        return rows;
    }

    /**
     * 查询 select Maps By Operation Id 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param logicalTable logical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private List<Map<String, Object>> selectMapsByOperationId(String logicalTable, LocalDateTime beginTime, LocalDateTime endTime, String operationId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean softDeleteTable = hasSoftDeleteColumn(logicalTable);
        MapSqlParameterSource params = baseParams(beginTime, endTime).addValue("operationId", operationId);
        for (String table : physicalTablesInRange(logicalTable, beginTime, endTime)) {
            rows.addAll(toCamelCaseRows(jdbcTemplate.queryForList("""
                    SELECT *
                    FROM %s
                    WHERE %s
                      AND operation_id = :operationId
                      AND transaction_date_time >= :beginTime
                      AND transaction_date_time <= :endTime
                    ORDER BY transaction_date_time ASC, id ASC
                    """.formatted(table, softDeleteCondition(softDeleteTable)),
                    params)));
        }
        return rows;
    }

    /**
     * 查询 select Optional Maps By Operation Id 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param logicalTable logical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private List<Map<String, Object>> selectOptionalMapsByOperationId(String logicalTable, LocalDateTime beginTime, LocalDateTime endTime, String operationId) {
        try {
            return selectMapsByOperationId(logicalTable, beginTime, endTime, operationId);
        } catch (RuntimeException exception) {
            return Collections.emptyList();
        }
    }

    /**
     * 完成 physical Table 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param logicalTable logical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 当前方法计算或转换后的业务结果
     */
    private String physicalTable(String logicalTable, LocalDateTime transactionDateTime) {
        return shardingDataTemplate.resolvePhysicalTable(
                ShardingSingleTableContext.of(logicalTable, transactionDateTime, DataSourceName.SLAVE));
    }

    /**
     * 完成 physical Tables In Range 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param logicalTable logical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 当前方法计算或转换后的业务结果
     */
    private List<String> physicalTablesInRange(String logicalTable, LocalDateTime beginTime, LocalDateTime endTime) {
        return shardingDataTemplate.resolvePhysicalTables(
                ShardingRangeTableContext.of(logicalTable, beginTime, endTime, DataSourceName.SLAVE));
    }

    /**
     * 完成 payment Info Table For Operation Table 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param operationPhysicalTable operation Physical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private String paymentInfoTableForOperationTable(String operationPhysicalTable) {
        return operationPhysicalTable.replaceFirst("^" + TRANSACTION_OPERATION_TABLE, TRANSACTION_PAYMENT_METHOD_INFO_TABLE);
    }

    /**
     * 完成 order Table For Operation Table 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param operationPhysicalTable operation Physical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private String orderTableForOperationTable(String operationPhysicalTable) {
        return operationPhysicalTable.replaceFirst("^" + TRANSACTION_OPERATION_TABLE, TRANSACTION_ORDER_TABLE);
    }

    /**
     * 完成 channel Request Table For Interaction Log Table 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param interactionLogPhysicalTable interaction Log Physical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String channelRequestTableForInteractionLogTable(String interactionLogPhysicalTable) {
        return interactionLogPhysicalTable.replaceFirst("^" + TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE, TRANSACTION_CHANNEL_REQUEST_TABLE);
    }

    /**
     * 完成 operation Table For Interaction Log Table 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param interactionLogPhysicalTable interaction Log Physical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private String operationTableForInteractionLogTable(String interactionLogPhysicalTable) {
        return interactionLogPhysicalTable.replaceFirst("^" + TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE, TRANSACTION_OPERATION_TABLE);
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
     * 完成 offset 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private long offset(PageRequest query) {
        return (query.safePageNo() - 1) * query.safePageSize();
    }

    /**
     * 完成 time Value 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param params params 输入值，含义由调用方法名称和所属业务对象限定
     * @param key key 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private LocalDateTime timeValue(MapSqlParameterSource params, String key) {
        Object value = params.getValue(key);
        return value instanceof LocalDateTime localDateTime ? localDateTime : null;
    }

    /**
     * 完成 copy 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
        row.forEach((key, value) -> converted.put(toLowerCamelCase(key), value));
        return converted;
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
     * 完成 nullable Int 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rs rs 输入值，含义由调用方法名称和所属业务对象限定
     * @param column column 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * 完成 local Date Time 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rs rs 输入值，含义由调用方法名称和所属业务对象限定
     * @param column column 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }

    /**
     * 完成 default Rate 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rate rate 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private BigDecimal defaultRate(BigDecimal rate) {
        return rate == null ? new BigDecimal("1.00000000") : rate;
    }

    /**
     * 解析 resolve Merchant Response Code 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 解析或查询得到的业务值
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
     * 解析 resolve Merchant Response Message 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 解析或查询得到的业务值
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
     * 完成 get String If Exists 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rs rs 输入值，含义由调用方法名称和所属业务对象限定
     * @param column column 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String getStringIfExists(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException exception) {
            return null;
        }
    }

    /**
     * 完成 get Big Decimal If Exists 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rs rs 输入值，含义由调用方法名称和所属业务对象限定
     * @param column column 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private BigDecimal getBigDecimalIfExists(ResultSet rs, String column) {
        try {
            return rs.getBigDecimal(column);
        } catch (SQLException exception) {
            return null;
        }
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
         * total Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private long totalCount;
        /**
         * success Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private long successCount;
        /**
         * failed Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private long failedCount;
        /**
         * amounts 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final Map<String, TransactionAmountSummaryResponse> amounts = new LinkedHashMap<>();
        /**
         * success Amounts 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final Map<String, TransactionAmountSummaryResponse> successAmounts = new LinkedHashMap<>();
        /**
         * failed Amounts 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final Map<String, TransactionAmountSummaryResponse> failedAmounts = new LinkedHashMap<>();
        /**
         * payments 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final Map<String, TransactionPaymentMethodSummaryResponse> payments = new LinkedHashMap<>();

        /**
         * 计算 add Amount 对应的数值结果，调用方负责保证金额和币种上下文一致。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param row row 输入值，含义由调用方法名称和所属业务对象限定
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
         * 计算 add Payment 对应的数值结果，调用方负责保证金额和币种上下文一致。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param row row 输入值，含义由调用方法名称和所属业务对象限定
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
         * 计算 add Amount 对应的数值结果，调用方负责保证金额和币种上下文一致。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
         * @param map map 输入值，含义由调用方法名称和所属业务对象限定
         * @param row row 输入值，含义由调用方法名称和所属业务对象限定
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
         * 转换生成 to Response 对应的传输对象、导出行或协议字段。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @return 转换或构建后的目标对象
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
