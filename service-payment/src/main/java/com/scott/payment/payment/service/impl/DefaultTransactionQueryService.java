package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingRangeTableContext;
import com.scott.payment.component.db.sharding.ShardingSingleTableContext;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.TransactionChannelInteractionLogDO;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.entity.TransactionPaymentMethodInfoDO;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.mapper.TransactionAmountChangeLogMapper;
import com.scott.payment.payment.mapper.TransactionChannelCallbackLogMapper;
import com.scott.payment.payment.mapper.TransactionChannelCallbackMapper;
import com.scott.payment.payment.mapper.TransactionChannelInteractionLogMapper;
import com.scott.payment.payment.mapper.TransactionChannelRequestMapper;
import com.scott.payment.payment.mapper.TransactionFlowEventMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationLogMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationMapper;
import com.scott.payment.payment.mapper.TransactionMerchantApiInteractionLogMapper;
import com.scott.payment.payment.mapper.TransactionOperationMapper;
import com.scott.payment.payment.mapper.TransactionOrderMapper;
import com.scott.payment.payment.mapper.TransactionPaymentMethodInfoMapper;
import com.scott.payment.payment.mapper.TransactionStatusHistoryMapper;
import com.scott.payment.payment.service.TransactionQueryService;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.ChannelCallbackQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.ChannelInteractionResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.ChannelLogQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.MerchantNotificationQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionDetailResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionAmountSummaryResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationSearchResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationSummaryRow;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOrderResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionPageQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationSummaryResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionPaymentMethodSummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionQueryService
 * @date : 2026-07-14 23:18
 * @email : scott_x@163.com
 * @description : 交易聚合查询服务默认实现，位于 service-payment 服务实现层，按 transaction_date_time 分表范围读取后台交易列表和详情聚合数据。
 * @status : create
 */
@Service
@Slf4j
public class DefaultTransactionQueryService implements TransactionQueryService {

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
     * TRANSACTION STATUS HISTORY TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_STATUS_HISTORY_TABLE = "transaction_status_history";
    /**
     * TRANSACTION CHANNEL REQUEST TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_CHANNEL_REQUEST_TABLE = "transaction_channel_request";
    /**
     * TRANSACTION CHANNEL INTERACTION LOG TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE = "transaction_channel_interaction_log";
    /**
     * TRANSACTION CHANNEL CALLBACK LOG TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_CHANNEL_CALLBACK_LOG_TABLE = "transaction_channel_callback_log";
    /**
     * TRANSACTION CHANNEL CALLBACK TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_CHANNEL_CALLBACK_TABLE = "transaction_channel_callback";
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
     * TRANSACTION PAYMENT METHOD INFO TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_PAYMENT_METHOD_INFO_TABLE = "transaction_payment_method_info";
    /**
     * DEFAULT QUERY TIME ZONE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DEFAULT_QUERY_TIME_ZONE = "Asia/Shanghai";
    /**
     * order Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionOrderMapper orderMapper;
    /**
     * operation Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionOperationMapper operationMapper;
    /**
     * status History Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionStatusHistoryMapper statusHistoryMapper;
    /**
     * channel Request Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionChannelRequestMapper channelRequestMapper;
    /**
     * interaction Log Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionChannelInteractionLogMapper interactionLogMapper;
    /**
     * callback Log Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionChannelCallbackLogMapper callbackLogMapper;
    /**
     * callback Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionChannelCallbackMapper callbackMapper;
    /**
     * flow Event Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionFlowEventMapper flowEventMapper;
    /**
     * amount Change Log Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionAmountChangeLogMapper amountChangeLogMapper;
    /**
     * notification Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionMerchantNotificationMapper notificationMapper;
    /**
     * notification Log Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionMerchantNotificationLogMapper notificationLogMapper;
    /**
     * merchant Api Interaction Log Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionMerchantApiInteractionLogMapper merchantApiInteractionLogMapper;
    /**
     * payment Method Info Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionPaymentMethodInfoMapper paymentMethodInfoMapper;
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
     * 创建交易聚合查询服务默认实现。
     *
     * @param orderMapper 交易主单 Mapper
     * @param operationMapper 交易动作 Mapper
     * @param statusHistoryMapper 状态历史 Mapper
     * @param channelRequestMapper 渠道请求 Mapper
     * @param interactionLogMapper 渠道交互日志 Mapper
     * @param callbackLogMapper 渠道回调日志 Mapper
     * @param callbackMapper 渠道回调业务 Mapper
     * @param flowEventMapper 流程事件 Mapper
     * @param notificationMapper 商户通知任务 Mapper
     * @param notificationLogMapper 商户通知日志 Mapper
     * @param merchantApiInteractionLogMapper 商户 OpenAPI 交互日志 Mapper
     * @param paymentMethodInfoMapper 支付工具摘要 Mapper
     * @param shardingDataTemplate 分表数据访问统一入口
     * @param transactionShardingKeyParser 交易分表键解析器
     */
    public DefaultTransactionQueryService(TransactionOrderMapper orderMapper,
                                          TransactionOperationMapper operationMapper,
                                          TransactionStatusHistoryMapper statusHistoryMapper,
                                          TransactionChannelRequestMapper channelRequestMapper,
                                          TransactionChannelInteractionLogMapper interactionLogMapper,
                                          TransactionChannelCallbackLogMapper callbackLogMapper,
                                          TransactionChannelCallbackMapper callbackMapper,
                                          TransactionFlowEventMapper flowEventMapper,
                                          TransactionAmountChangeLogMapper amountChangeLogMapper,
                                          TransactionMerchantNotificationMapper notificationMapper,
                                          TransactionMerchantNotificationLogMapper notificationLogMapper,
                                          TransactionMerchantApiInteractionLogMapper merchantApiInteractionLogMapper,
                                          TransactionPaymentMethodInfoMapper paymentMethodInfoMapper,
                                          ShardingDataTemplate shardingDataTemplate,
                                          TransactionShardingKeyParser transactionShardingKeyParser) {
        this.orderMapper = orderMapper;
        this.operationMapper = operationMapper;
        this.statusHistoryMapper = statusHistoryMapper;
        this.channelRequestMapper = channelRequestMapper;
        this.interactionLogMapper = interactionLogMapper;
        this.callbackLogMapper = callbackLogMapper;
        this.callbackMapper = callbackMapper;
        this.flowEventMapper = flowEventMapper;
        this.amountChangeLogMapper = amountChangeLogMapper;
        this.notificationMapper = notificationMapper;
        this.notificationLogMapper = notificationLogMapper;
        this.merchantApiInteractionLogMapper = merchantApiInteractionLogMapper;
        this.paymentMethodInfoMapper = paymentMethodInfoMapper;
        this.shardingDataTemplate = shardingDataTemplate;
        this.transactionShardingKeyParser = transactionShardingKeyParser;
    }

    /**
     * 分页查询交易生命周期主单。
     *
     * @param query 查询条件
     * @return 主单分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        long total = 0L;
        List<TransactionOrderDO> rows = new ArrayList<>();
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        for (String table : physicalTablesInRange(TRANSACTION_ORDER_TABLE, safeQuery.getBeginTime(), safeQuery.getEndTime())) {
            long count = orderMapper.countPagePhysical(table, safeQuery.getMerchantId(), safeQuery.getMerchantOrderNo(),
                    safeQuery.getTransactionId(), safeQuery.getTransactionStatus(), safeQuery.getBeginTime(), safeQuery.getEndTime());
            total += count;
            if (rows.size() < limit && offset < count) {
                rows.addAll(orderMapper.selectPagePhysical(table, safeQuery.getMerchantId(), safeQuery.getMerchantOrderNo(),
                        safeQuery.getTransactionId(), safeQuery.getTransactionStatus(), safeQuery.getBeginTime(), safeQuery.getEndTime(),
                        offset, limit - rows.size()));
                offset = 0;
            } else {
                offset = Math.max(0, offset - count);
            }
        }
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), rows.stream().map(this::toOrderResponse).toList());
    }

    /**
     * 分页查询交易动作单。
     *
     * @param query 查询条件
     * @return 动作单分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        return pageOperationsNormalized(safeQuery);
    }

    /**
     * 分页查询交易动作单，并返回同一查询条件下的全量统计。
     *
     * @param query 查询条件
     * @return 动作单分页与统计响应
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public TransactionOperationSearchResponse searchOperations(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        TransactionOperationSearchResponse response = new TransactionOperationSearchResponse();
        response.setPage(pageOperationsNormalized(safeQuery));
        response.setSummary(operationSummary(safeQuery));
        return response;
    }

    private PageResult<TransactionOperationResponse> pageOperationsNormalized(TransactionPageQuery safeQuery) {
        long total = 0L;
        List<TransactionOperationDO> rows = new ArrayList<>();
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        for (String table : physicalTablesInRange(TRANSACTION_OPERATION_TABLE, safeQuery.getBeginTime(), safeQuery.getEndTime())) {
            String paymentInfoTable = paymentInfoTableForOperationTable(table);
            long count = operationMapper.countPagePhysical(table, paymentInfoTable, safeQuery.getMerchantId(), safeQuery.getMerchantOrderNo(),
                    safeQuery.getTransactionId(), safeQuery.getSourceTransactionId(), safeQuery.getTransactionType(),
                    safeQuery.getTransactionStatus(), safeQuery.getChannelCode(), safeQuery.getChannelOrderNo(),
                    safeQuery.getChannelResponseCode(), safeQuery.getAuthCode(), safeQuery.getAcquirerReferenceNo(),
                    safeQuery.getChannelMatchStatus(), safeQuery.getReconciliationStatus(), safeQuery.getSettlementStatus(),
                    safeQuery.getPaymentMethod(), safeQuery.getPaymentBrand(), safeQuery.getCardBin(),
                    safeQuery.getBeginTime(), safeQuery.getEndTime());
            total += count;
            if (rows.size() < limit && offset < count) {
                rows.addAll(operationMapper.selectPagePhysical(table, paymentInfoTable, safeQuery.getMerchantId(), safeQuery.getMerchantOrderNo(),
                        safeQuery.getTransactionId(), safeQuery.getSourceTransactionId(), safeQuery.getTransactionType(),
                        safeQuery.getTransactionStatus(), safeQuery.getChannelCode(), safeQuery.getChannelOrderNo(),
                        safeQuery.getChannelResponseCode(), safeQuery.getAuthCode(), safeQuery.getAcquirerReferenceNo(),
                        safeQuery.getChannelMatchStatus(), safeQuery.getReconciliationStatus(), safeQuery.getSettlementStatus(),
                        safeQuery.getPaymentMethod(), safeQuery.getPaymentBrand(), safeQuery.getCardBin(),
                        safeQuery.getBeginTime(), safeQuery.getEndTime(), offset, limit - rows.size()));
                offset = 0;
            } else {
                offset = Math.max(0, offset - count);
            }
        }
        Map<String, TransactionPaymentMethodInfoDO> paymentInfoMap = paymentInfoMap(rows);
        Map<String, TransactionOrderDO> orderMap = orderMap(rows);
        Map<String, String> merchantNotificationStatusMap = merchantNotificationStatusMap(rows);
        List<TransactionOperationResponse> responses = rows.stream()
                .map(row -> toOperationResponse(row, paymentInfoMap.get(row.getTransactionId()),
                        orderMap.get(row.getOperationId()), merchantNotificationStatusMap.get(row.getTransactionId())))
                .toList();
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), responses);
    }

    /**
     * 按当前查询条件聚合全部命中交易动作。
     * <p>
     * 统计口径必须和分页列表完全一致，只去掉分页偏移和分页大小；前端据此展示查询结果整体金额，
     * 避免用户切换页码后统计卡片变化。
     *
     * @param safeQuery 已归一化的交易动作查询条件
     * @return 交易动作统计结果
     */
    private TransactionOperationSummaryResponse operationSummary(TransactionPageQuery safeQuery) {
        TransactionOperationSummaryAccumulator accumulator = new TransactionOperationSummaryAccumulator();
        for (String table : physicalTablesInRange(TRANSACTION_OPERATION_TABLE,
                safeQuery.getBeginTime(), safeQuery.getEndTime())) {
            String paymentInfoTable = paymentInfoTableForOperationTable(table);
            List<TransactionOperationSummaryRow> amountRows = operationMapper.selectAmountSummaryPhysical(
                    table, paymentInfoTable, safeQuery.getMerchantId(), safeQuery.getMerchantOrderNo(),
                    safeQuery.getTransactionId(), safeQuery.getSourceTransactionId(), safeQuery.getTransactionType(),
                    safeQuery.getTransactionStatus(), safeQuery.getChannelCode(), safeQuery.getChannelOrderNo(),
                    safeQuery.getChannelResponseCode(), safeQuery.getAuthCode(), safeQuery.getAcquirerReferenceNo(),
                    safeQuery.getChannelMatchStatus(), safeQuery.getReconciliationStatus(), safeQuery.getSettlementStatus(),
                    safeQuery.getPaymentMethod(), safeQuery.getPaymentBrand(), safeQuery.getCardBin(),
                    safeQuery.getBeginTime(), safeQuery.getEndTime());
            amountRows.forEach(accumulator::addAmountRow);
            List<TransactionOperationSummaryRow> paymentMethodRows = operationMapper.selectPaymentMethodSummaryPhysical(
                    table, paymentInfoTable, safeQuery.getMerchantId(), safeQuery.getMerchantOrderNo(),
                    safeQuery.getTransactionId(), safeQuery.getSourceTransactionId(), safeQuery.getTransactionType(),
                    safeQuery.getTransactionStatus(), safeQuery.getChannelCode(), safeQuery.getChannelOrderNo(),
                    safeQuery.getChannelResponseCode(), safeQuery.getAuthCode(), safeQuery.getAcquirerReferenceNo(),
                    safeQuery.getChannelMatchStatus(), safeQuery.getReconciliationStatus(), safeQuery.getSettlementStatus(),
                    safeQuery.getPaymentMethod(), safeQuery.getPaymentBrand(), safeQuery.getCardBin(),
                    safeQuery.getBeginTime(), safeQuery.getEndTime());
            paymentMethodRows.forEach(accumulator::addPaymentMethodRow);
        }
        return accumulator.toResponse();
    }

    /**
     * 查询交易详情聚合数据。
     *
     * @param transactionId 平台交易 ID
     * @return 交易详情
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public TransactionDetailResponse detail(String transactionId) {
        if (!StringUtils.hasText(transactionId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime transactionDateTime = parseTransactionDateTime(transactionId);
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        String operationTable = physicalTable(TRANSACTION_OPERATION_TABLE, transactionDateTime);
        TransactionOperationDO sourceOperation = operationMapper.selectByTransactionIdPhysical(operationTable, transactionId);
        if (sourceOperation == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime orderTransactionDateTime = parseOperationDateTime(sourceOperation.getOperationId());
        if (orderTransactionDateTime == null) {
            orderTransactionDateTime = sourceOperation.getTransactionDateTime();
        }
        String orderTable = physicalTable(TRANSACTION_ORDER_TABLE, orderTransactionDateTime);
        TransactionOrderDO order = orderMapper.selectByOperationIdPhysical(orderTable, sourceOperation.getOperationId());
        if (order == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime detailEndTime = LocalDateTime.now();
        LocalDateTime detailBeginTime = order.getTransactionDateTime();
        List<TransactionOperationDO> operations = selectOperationsByOperationId(sourceOperation.getOperationId(), detailBeginTime, detailEndTime);
        if (operations.stream().noneMatch(item -> Objects.equals(item.getTransactionId(), sourceOperation.getTransactionId()))) {
            operations = new ArrayList<>(operations);
            operations.add(sourceOperation);
        }
        operations.sort(Comparator.comparing(TransactionOperationDO::getOperationSequence,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TransactionOperationDO::getOperationTime,
                        Comparator.nullsLast(LocalDateTime::compareTo)));
        TransactionDetailResponse detail = new TransactionDetailResponse();
        detail.setOrder(toOrderResponse(order));
        Map<String, TransactionPaymentMethodInfoDO> paymentInfoMap = paymentInfoMap(operations);
        detail.setOperations(operations.stream()
                .map(operation -> toOperationResponse(operation, paymentInfoMap.get(operation.getTransactionId()), order))
                .toList());
        Map<String, String> operationTypeMap = operations.stream()
                .filter(operation -> StringUtils.hasText(operation.getTransactionId()))
                .filter(operation -> StringUtils.hasText(operation.getTransactionType()))
                .collect(Collectors.toMap(TransactionOperationDO::getTransactionId,
                        TransactionOperationDO::getTransactionType, (left, right) -> left));
        detail.setStatusHistory(selectByOperationIdAcrossTables(TRANSACTION_STATUS_HISTORY_TABLE, detailBeginTime, detailEndTime,
                table -> statusHistoryMapper.selectByOperationIdPhysical(table, order.getOperationId())));
        detail.setFlowEvents(selectByOperationIdAcrossTables(TRANSACTION_FLOW_EVENT_TABLE, detailBeginTime, detailEndTime,
                table -> flowEventMapper.selectByOperationIdPhysical(table, order.getOperationId())));
        detail.setAmountChanges(selectByOperationIdAcrossTables(TRANSACTION_AMOUNT_CHANGE_LOG_TABLE, detailBeginTime, detailEndTime,
                table -> amountChangeLogMapper.selectByOperationIdPhysical(table, order.getOperationId())));
        List<TransactionChannelRequestDO> channelRequests = selectByOperationIdAcrossTables(
                TRANSACTION_CHANNEL_REQUEST_TABLE,
                detailBeginTime,
                detailEndTime,
                table -> channelRequestMapper.selectByOperationIdPhysical(table, order.getOperationId()));
        detail.setChannelRequests(channelRequests);
        List<TransactionChannelInteractionLogDO> interactionLogs = selectByOperationIdAcrossTables(
                TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE,
                detailBeginTime,
                detailEndTime,
                table -> interactionLogMapper.selectByOperationIdPhysical(table, order.getOperationId()));
        Map<String, TransactionChannelRequestDO> channelRequestMap = channelRequestMap(channelRequests);
        detail.setChannelInteractionLogs(enrichChannelInteractionTransactionTypes(
                mergeChannelInteractionLogs(interactionLogs, channelRequestMap), operationTypeMap));
        detail.setChannelCallbacks(selectByOperationIdAcrossTables(TRANSACTION_CHANNEL_CALLBACK_TABLE, detailBeginTime, detailEndTime,
                table -> callbackMapper.selectByOperationIdPhysical(table, order.getOperationId())));
        detail.setChannelCallbackLogs(selectByOperationIdAcrossTables(TRANSACTION_CHANNEL_CALLBACK_LOG_TABLE, detailBeginTime, detailEndTime,
                table -> callbackLogMapper.selectByOperationIdPhysical(table, order.getOperationId())));
        detail.setMerchantNotifications(selectByOperationIdAcrossTables(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, detailBeginTime, detailEndTime,
                table -> notificationMapper.selectByOperationIdPhysical(table, order.getOperationId())));
        detail.setMerchantNotificationLogs(selectByOperationIdAcrossTables(TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE, detailBeginTime, detailEndTime,
                table -> notificationLogMapper.selectByOperationIdPhysical(table, order.getOperationId())));
        detail.setMerchantApiInteractionLogs(selectOptionalByOperationIdAcrossTables(TRANSACTION_MERCHANT_API_INTERACTION_LOG_TABLE, detailBeginTime, detailEndTime,
                table -> merchantApiInteractionLogMapper.selectByOperationIdPhysical(table, order.getOperationId())));
        return detail;
    }

    /**
     * 分页查询渠道交互日志。
     *
     * @param query 查询条件
     * @return 渠道交互日志分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<?> pageChannelLogs(ChannelLogQuery query) {
        ChannelLogQuery safeQuery = normalize(query);
        long total = 0L;
        List<TransactionChannelInteractionLogDO> rawRows = new ArrayList<>();
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        for (String table : physicalTablesInRange(TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE, safeQuery.getBeginTime(), safeQuery.getEndTime())) {
            long count = interactionLogMapper.countPagePhysical(table, safeQuery.getChannelCode(), safeQuery.getTransactionId(),
                    safeQuery.getInteractionType(), safeQuery.getBeginTime(), safeQuery.getEndTime());
            total += count;
            if (rawRows.size() < limit && offset < count) {
                rawRows.addAll(interactionLogMapper.selectPagePhysical(table, safeQuery.getChannelCode(), safeQuery.getTransactionId(),
                        safeQuery.getInteractionType(), safeQuery.getBeginTime(), safeQuery.getEndTime(), offset, limit - rawRows.size()));
                offset = 0;
            } else {
                offset = Math.max(0, offset - count);
            }
        }
        Map<String, TransactionChannelRequestDO> channelRequestMap = channelRequestMap(rawRows, safeQuery);
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(),
                enrichChannelInteractionTransactionTypes(mergeChannelInteractionLogs(rawRows, channelRequestMap)));
    }

    /**
     * 分页查询渠道回调业务记录。
     *
     * @param query 查询条件
     * @return 渠道回调业务记录分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<?> pageChannelCallbacks(ChannelCallbackQuery query) {
        ChannelCallbackQuery safeQuery = normalize(query);
        long total = 0L;
        List<Object> rows = new ArrayList<>();
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        for (String table : physicalTablesInRange(TRANSACTION_CHANNEL_CALLBACK_TABLE,
                safeQuery.getBeginTime(), safeQuery.getEndTime())) {
            long count = callbackMapper.countPagePhysical(table, safeQuery.getChannelCode(), safeQuery.getTransactionId(),
                    safeQuery.getChannelOrderNo(), safeQuery.getChannelTransactionId(), safeQuery.getCallbackStatus(),
                    safeQuery.getBeginTime(), safeQuery.getEndTime());
            total += count;
            if (rows.size() < limit && offset < count) {
                rows.addAll(callbackMapper.selectPagePhysical(table, safeQuery.getChannelCode(), safeQuery.getTransactionId(),
                        safeQuery.getChannelOrderNo(), safeQuery.getChannelTransactionId(), safeQuery.getCallbackStatus(),
                        safeQuery.getBeginTime(), safeQuery.getEndTime(), offset, limit - rows.size()));
                offset = 0;
            } else {
                offset = Math.max(0, offset - count);
            }
        }
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), rows);
    }

    /**
     * 分页查询商户通知任务。
     *
     * @param query 查询条件
     * @return 商户通知任务分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<?> pageMerchantNotifications(MerchantNotificationQuery query) {
        MerchantNotificationQuery safeQuery = normalize(query);
        long total = 0L;
        List<Object> rows = new ArrayList<>();
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        for (String table : physicalTablesInRange(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, safeQuery.getBeginTime(), safeQuery.getEndTime())) {
            long count = notificationMapper.countPagePhysical(table, safeQuery.getMerchantId(), safeQuery.getTransactionId(),
                    safeQuery.getNotifyStatus(), safeQuery.getBeginTime(), safeQuery.getEndTime());
            total += count;
            if (rows.size() < limit && offset < count) {
                rows.addAll(notificationMapper.selectPagePhysical(table, safeQuery.getMerchantId(), safeQuery.getTransactionId(),
                        safeQuery.getNotifyStatus(), safeQuery.getBeginTime(), safeQuery.getEndTime(), offset, limit - rows.size()));
                offset = 0;
            } else {
                offset = Math.max(0, offset - count);
            }
        }
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), rows);
    }

    private TransactionPageQuery normalize(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = query == null ? new TransactionPageQuery() : query;
        fillDefaultTimeRange(safeQuery);
        normalizeMerchantResponseCode(safeQuery);
        return safeQuery;
    }

    /**
     * 将商户响应码查询条件映射为平台交易状态。
     * <p>
     * 管理端查询展示的是商户侧 code/message，数据库交易事实仍以 transaction_status 字典值落库；
     * 无法映射的响应码使用永不命中的状态值，避免误返回其他状态交易。
     *
     * @param query 交易分页查询条件
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
     * 执行 normalize 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private ChannelLogQuery normalize(ChannelLogQuery query) {
        ChannelLogQuery safeQuery = query == null ? new ChannelLogQuery() : query;
        fillDefaultTimeRange(safeQuery);
        return safeQuery;
    }

    /**
     * 执行 normalize 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private MerchantNotificationQuery normalize(MerchantNotificationQuery query) {
        MerchantNotificationQuery safeQuery = query == null ? new MerchantNotificationQuery() : query;
        fillDefaultTimeRange(safeQuery);
        return safeQuery;
    }

    /**
     * 执行 normalize 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private ChannelCallbackQuery normalize(ChannelCallbackQuery query) {
        ChannelCallbackQuery safeQuery = query == null ? new ChannelCallbackQuery() : query;
        fillDefaultTimeRange(safeQuery);
        return safeQuery;
    }

    /**
     * 归一化交易列表查询时间范围。
     *
     * @param query 交易列表查询条件
     */
    private void fillDefaultTimeRange(TransactionPageQuery query) {
        QueryTimeRange timeRange = normalizeQueryTimeRange(query.getBeginTime(), query.getEndTime(), query.getQueryTimeZone());
        query.setBeginTime(timeRange.beginTime());
        query.setEndTime(timeRange.endTime());
        query.setQueryTimeZone(timeRange.queryTimeZone());
    }

    /**
     * 归一化渠道日志查询时间范围。
     *
     * @param query 渠道日志查询条件
     */
    private void fillDefaultTimeRange(ChannelLogQuery query) {
        QueryTimeRange timeRange = normalizeQueryTimeRange(query.getBeginTime(), query.getEndTime(), query.getQueryTimeZone());
        query.setBeginTime(timeRange.beginTime());
        query.setEndTime(timeRange.endTime());
        query.setQueryTimeZone(timeRange.queryTimeZone());
    }

    /**
     * 归一化商户通知查询时间范围。
     *
     * @param query 商户通知查询条件
     */
    private void fillDefaultTimeRange(MerchantNotificationQuery query) {
        QueryTimeRange timeRange = normalizeQueryTimeRange(query.getBeginTime(), query.getEndTime(), query.getQueryTimeZone());
        query.setBeginTime(timeRange.beginTime());
        query.setEndTime(timeRange.endTime());
        query.setQueryTimeZone(timeRange.queryTimeZone());
    }

    /**
     * 归一化渠道回调查询时间范围。
     *
     * @param query 渠道回调查询条件
     */
    private void fillDefaultTimeRange(ChannelCallbackQuery query) {
        QueryTimeRange timeRange = normalizeQueryTimeRange(query.getBeginTime(), query.getEndTime(), query.getQueryTimeZone());
        query.setBeginTime(timeRange.beginTime());
        query.setEndTime(timeRange.endTime());
        query.setQueryTimeZone(timeRange.queryTimeZone());
    }

    /**
     * 执行 offset 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private long offset(com.scott.payment.component.core.model.PageRequest query) {
        return (query.safePageNo() - 1) * query.safePageSize();
    }

    /**
     * 执行 to Order Response 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private TransactionOrderResponse toOrderResponse(TransactionOrderDO row) {
        TransactionOrderResponse response = new TransactionOrderResponse();
        response.setOperationId(row.getOperationId());
        response.setRootTransactionId(row.getRootTransactionId());
        response.setLatestTransactionId(row.getLatestTransactionId());
        response.setMerchantId(row.getMerchantId());
        response.setMerchantOrderNo(row.getMerchantOrderNo());
        response.setMerchantOrderId(row.getMerchantOrderId());
        response.setPaymentMethod(row.getPaymentMethod());
        response.setPaymentBrand(row.getPaymentBrand());
        response.setTransactionType(row.getTransactionType());
        response.setTransactionStatus(row.getTransactionStatus());
        response.setLifecycleStatus(resolveLifecycleStatus(row));
        response.setLifecycleStatusMessage(resolveLifecycleStatusMessage(row));
        response.setProcessStage(row.getProcessStage());
        response.setLabelCurrency(row.getLabelCurrency());
        response.setLabelAmount(row.getLabelAmount());
        response.setTransactionCurrency(row.getTransactionCurrency());
        response.setTransactionAmount(row.getTransactionAmount());
        response.setCurrentCurrency(row.getTransactionCurrency());
        response.setCurrentAmount(resolveCurrentAmount(row));
        response.setCurrencyExponent(row.getCurrencyExponent());
        response.setTransactionRate(defaultRate(row.getTransactionRate()));
        response.setDccEnabled(row.getDccEnabled());
        response.setEdcEnabled(row.getEdcEnabled());
        response.setMerchantResponseCode(resolveMerchantResponseCode(row.getTransactionStatus()));
        response.setMerchantResponseMessage(resolveMerchantResponseMessage(row.getTransactionStatus()));
        response.setAuthorizedAmount(row.getAuthorizedAmount());
        response.setCapturedAmount(row.getCapturedAmount());
        response.setRefundedAmount(row.getRefundedAmount());
        response.setAvailableCaptureAmount(row.getAvailableCaptureAmount());
        response.setAvailableRefundAmount(row.getAvailableRefundAmount());
        response.setSettlementStatus(row.getSettlementStatus());
        response.setReconciliationStatus(row.getReconciliationStatus());
        response.setAccountingStatus(row.getAccountingStatus());
        response.setChannelMatchStatus(row.getChannelMatchStatus());
        response.setChannelCode(row.getChannelCode());
        response.setChannelOrderNo(row.getChannelOrderNo());
        response.setTransactionDateTime(row.getTransactionDateTime());
        response.setTransactionTimeZone(row.getTransactionTimeZone());
        return response;
    }

    /**
     * 执行 resolve Current Amount 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 按渠道协议格式化后的金额字符串或金额计算结果
     */
    private BigDecimal resolveCurrentAmount(TransactionOrderDO row) {
        if (row == null) {
            return null;
        }
        if (PaymentTransactionTypeEnum.AUTHORIZATION.getCode().equals(row.getTransactionType())
                || PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode().equals(row.getTransactionType())
                || PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION.getCode().equals(row.getTransactionType())) {
            return row.getAuthorizedAmount();
        }
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(row.getTransactionType())) {
            return row.getTransactionAmount();
        }
        return row.getTransactionAmount();
    }

    /**
     * 推导交易生命周期状态。
     * <p>
     * transaction_status 仍表示主单基础交易状态；生命周期状态用于后台订单跟踪展示授权后请款、退款、撤销等完整进展。
     *
     * @param row 交易主单
     * @return 生命周期展示状态
     */
    private String resolveLifecycleStatus(TransactionOrderDO row) {
        if (row == null) {
            return null;
        }
        if (!PaymentTransactionStatusEnum.SUCCESS.getCode().equals(row.getTransactionStatus())) {
            return row.getTransactionStatus();
        }
        BigDecimal authorized = zeroIfNull(row.getAuthorizedAmount());
        BigDecimal captured = zeroIfNull(row.getCapturedAmount());
        BigDecimal refunded = zeroIfNull(row.getRefundedAmount());
        BigDecimal availableCapture = zeroIfNull(row.getAvailableCaptureAmount());
        BigDecimal availableRefund = zeroIfNull(row.getAvailableRefundAmount());
        if (isAuthorizationLike(row.getTransactionType()) && captured.signum() == 0
                && refunded.signum() == 0 && authorized.signum() > 0 && availableCapture.signum() == 0) {
            return "VOIDED";
        }
        if (refunded.signum() > 0 && availableRefund.signum() == 0) {
            return "FULLY_REFUNDED";
        }
        if (refunded.signum() > 0) {
            return "PARTIALLY_REFUNDED";
        }
        if (captured.signum() > 0 && availableCapture.signum() == 0) {
            return "CAPTURED";
        }
        if (captured.signum() > 0) {
            return "PARTIALLY_CAPTURED";
        }
        return row.getTransactionStatus();
    }

    /**
     * 执行 resolve Lifecycle Status Message 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolveLifecycleStatusMessage(TransactionOrderDO row) {
        String lifecycleStatus = resolveLifecycleStatus(row);
        if (lifecycleStatus == null) {
            return null;
        }
        return lifecycleStatus;
    }

    /**
     * 执行 zero If Null 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 执行 is Authorization Like 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param transactionType 交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isAuthorizationLike(String transactionType) {
        return PaymentTransactionTypeEnum.AUTHORIZATION.getCode().equals(transactionType)
                || PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode().equals(transactionType);
    }

    /**
     * 执行 to Operation Response 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private TransactionOperationResponse toOperationResponse(TransactionOperationDO row) {
        return toOperationResponse(row, null, null, null);
    }

/**
 * 执行 to Operation Response 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param row row 输入值，含义由调用方法名称和所属业务对象限定
 * @param paymentInfoDO payment Info DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param orderDO order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @return 渠道 API 操作类型或平台操作映射结果
 */
    private TransactionOperationResponse toOperationResponse(TransactionOperationDO row,
                                                             TransactionPaymentMethodInfoDO paymentInfoDO,
                                                             TransactionOrderDO orderDO) {
        return toOperationResponse(row, paymentInfoDO, orderDO, null);
    }

/**
 * 执行 to Operation Response 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param row row 输入值，含义由调用方法名称和所属业务对象限定
 * @param paymentInfoDO payment Info DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param orderDO order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param merchantNotificationStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @return 渠道 API 操作类型或平台操作映射结果
 */
    private TransactionOperationResponse toOperationResponse(TransactionOperationDO row,
                                                             TransactionPaymentMethodInfoDO paymentInfoDO,
                                                             TransactionOrderDO orderDO,
                                                             String merchantNotificationStatus) {
        TransactionOperationResponse response = new TransactionOperationResponse();
        response.setOperationId(row.getOperationId());
        response.setTransactionId(row.getTransactionId());
        response.setSourceTransactionId(row.getSourceTransactionId());
        response.setMerchantId(row.getMerchantId());
        response.setMerchantOrderNo(row.getMerchantOrderNo());
        response.setMerchantOrderId(row.getMerchantOrderId());
        response.setOperationSequence(row.getOperationSequence());
        response.setTransactionType(row.getTransactionType());
        response.setTransactionStatus(row.getTransactionStatus());
        response.setProcessStage(row.getProcessStage());
        response.setLabelCurrency(row.getLabelCurrency());
        response.setLabelAmount(row.getLabelAmount());
        response.setTransactionCurrency(row.getTransactionCurrency());
        response.setTransactionAmount(row.getTransactionAmount());
        response.setCurrencyExponent(row.getCurrencyExponent());
        response.setTransactionRate(defaultRate(row.getTransactionRate()));
        response.setDccEnabled(row.getDccEnabled());
        response.setEdcEnabled(row.getEdcEnabled());
        response.setMerchantResponseCode(resolveMerchantResponseCode(row.getTransactionStatus()));
        response.setMerchantResponseMessage(resolveMerchantResponseMessage(row.getTransactionStatus()));
        response.setMerchantNotificationStatus(merchantNotificationStatus);
        response.setAuthorizedAmount(orderDO == null ? null : orderDO.getAuthorizedAmount());
        response.setCapturedAmount(orderDO == null ? null : orderDO.getCapturedAmount());
        response.setRefundedAmount(orderDO == null ? null : orderDO.getRefundedAmount());
        response.setAvailableCaptureAmount(orderDO == null ? null : orderDO.getAvailableCaptureAmount());
        response.setAvailableRefundAmount(orderDO == null ? null : orderDO.getAvailableRefundAmount());
        response.setPaymentMethod(paymentInfoDO == null ? null : paymentInfoDO.getPaymentMethod());
        response.setPaymentBrand(paymentInfoDO == null ? null : paymentInfoDO.getPaymentBrand());
        response.setCardBin(paymentInfoDO == null ? null : paymentInfoDO.getCardBin());
        response.setCardNumberMasked(normalizeCardNumberMasked(paymentInfoDO));
        response.setAccessType("DIRECT_API");
        response.setChannelCode(row.getChannelCode());
        response.setChannelOrderNo(row.getChannelOrderNo());
        response.setChannelTransactionId(row.getChannelTransactionId());
        response.setChannelResponseCode(row.getChannelResponseCode());
        response.setChannelResponseMessage(row.getChannelResponseMessage());
        response.setAuthCode(row.getAuthCode());
        response.setAcquirerReferenceNo(row.getAcquirerReferenceNo());
        response.setRrn(row.getRrn());
        response.setSettlementStatus(row.getSettlementStatus());
        response.setReconciliationStatus(row.getReconciliationStatus());
        response.setAccountingStatus(row.getAccountingStatus());
        response.setChannelMatchStatus(row.getChannelMatchStatus());
        response.setTransactionDateTime(row.getTransactionDateTime());
        response.setOperationTime(row.getOperationTime());
        return response;
    }

    /**
     * 合并同一 requestId 的渠道交互日志。
     * <p>
     * 当前表结构优先把请求和响应聚合为一行 REQUEST_RESPONSE；这里仍兼容历史 REQUEST/RESPONSE
     * 两行模式，前端详情可以在同一卡片展示请求、响应、耗时和平台成功判断。
     *
     * @param rows       渠道交互日志行
     * @param requestMap requestId 到渠道请求摘要的映射
     * @return 后台渠道交互日志视图
     */
    private List<ChannelInteractionResponse> mergeChannelInteractionLogs(List<TransactionChannelInteractionLogDO> rows,
                                                                         Map<String, TransactionChannelRequestDO> requestMap) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, List<TransactionChannelInteractionLogDO>> groups = new LinkedHashMap<>();
        for (TransactionChannelInteractionLogDO row : rows) {
            String groupKey = StringUtils.hasText(row.getRequestId()) ? row.getRequestId() : row.getInteractionLogId();
            groups.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(row);
        }
        return groups.values()
                .stream()
                .map(groupRows -> toChannelInteractionResponse(groupRows, requestMap))
                .sorted(Comparator.comparing(ChannelInteractionResponse::getRequestTime,
                        Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .toList();
    }

    /**
     * 将一组渠道交互日志转换为后台展示对象。
     *
     * @param groupRows  同一 requestId 下的渠道交互日志
     * @param requestMap requestId 到渠道请求摘要的映射
     * @return 渠道交互日志展示对象
     */
    private ChannelInteractionResponse toChannelInteractionResponse(List<TransactionChannelInteractionLogDO> groupRows,
                                                                    Map<String, TransactionChannelRequestDO> requestMap) {
        TransactionChannelInteractionLogDO requestRow = selectRequestLogRow(groupRows);
        TransactionChannelInteractionLogDO responseRow = selectResponseLogRow(groupRows, requestRow);
        TransactionChannelInteractionLogDO primary = requestRow == null ? responseRow : requestRow;
        ChannelInteractionResponse response = new ChannelInteractionResponse();
        if (primary == null) {
            return response;
        }
        String requestId = firstText(primary.getRequestId(), responseRow == null ? null : responseRow.getRequestId());
        TransactionChannelRequestDO requestDO = requestMap == null ? null : requestMap.get(requestId);
        ChannelPaymentResponse channelResponse = parseChannelPaymentResponse(
                firstText(responseRow == null ? null : responseRow.getResponseBodyJsonMasked(),
                        primary.getResponseBodyJsonMasked()));
        response.setInteractionLogId(primary.getInteractionLogId());
        response.setRequestInteractionLogId(requestRow == null ? null : requestRow.getInteractionLogId());
        response.setResponseInteractionLogId(responseRow == null ? null : responseRow.getInteractionLogId());
        response.setRequestId(requestId);
        response.setTransactionId(firstText(primary.getTransactionId(), responseRow == null ? null : responseRow.getTransactionId()));
        response.setOperationId(firstText(primary.getOperationId(), responseRow == null ? null : responseRow.getOperationId()));
        response.setRequestStatus(requestDO == null ? null : requestDO.getRequestStatus());
        response.setTransactionType(requestDO == null ? null : requestDO.getTransactionType());
        response.setChannelCode(firstText(primary.getChannelCode(), responseRow == null ? null : responseRow.getChannelCode()));
        response.setChannelOrderNo(firstText(channelResponse == null ? null : channelResponse.getChannelOrderNo(),
                requestDO == null ? null : requestDO.getChannelOrderNo()));
        response.setChannelTransactionId(firstText(channelResponse == null ? null : channelResponse.getChannelTransactionId(),
                requestDO == null ? null : requestDO.getChannelTransactionId()));
        response.setInteractionType(resolveInteractionType(primary, responseRow));
        response.setHttpMethod(firstText(primary.getHttpMethod(), responseRow == null ? null : responseRow.getHttpMethod()));
        response.setRequestUrlMasked(firstText(primary.getRequestUrlMasked(), responseRow == null ? null : responseRow.getRequestUrlMasked()));
        response.setHttpStatus(firstNonNull(primary.getHttpStatus(), responseRow == null ? null : responseRow.getHttpStatus()));
        response.setRequestHeaderJsonMasked(firstText(primary.getRequestHeaderJsonMasked(), responseRow == null ? null : responseRow.getRequestHeaderJsonMasked()));
        response.setRequestBodyJsonMasked(firstText(primary.getRequestBodyJsonMasked(), responseRow == null ? null : responseRow.getRequestBodyJsonMasked()));
        response.setResponseHeaderJsonMasked(firstText(primary.getResponseHeaderJsonMasked(), responseRow == null ? null : responseRow.getResponseHeaderJsonMasked()));
        response.setResponseBodyJsonMasked(firstText(primary.getResponseBodyJsonMasked(), responseRow == null ? null : responseRow.getResponseBodyJsonMasked()));
        response.setExceptionType(firstText(primary.getExceptionType(), responseRow == null ? null : responseRow.getExceptionType()));
        response.setExceptionMessage(firstText(primary.getExceptionMessage(), responseRow == null ? null : responseRow.getExceptionMessage()));
        fillChannelInteractionResult(response, requestDO, channelResponse);
        response.setDurationMillis(firstNonNull(primary.getDurationMillis(), responseRow == null ? null : responseRow.getDurationMillis()));
        response.setTraceId(firstText(primary.getTraceId(), responseRow == null ? null : responseRow.getTraceId()));
        response.setRequestTime(resolveRequestTime(requestRow, responseRow));
        response.setResponseTime(resolveResponseTime(requestRow, responseRow));
        response.setInteractionTime(firstNonNull(response.getRequestTime(), primary.getInteractionTime()));
        response.setTransactionDateTime(firstNonNull(primary.getTransactionDateTime(), responseRow == null ? null : responseRow.getTransactionDateTime()));
        return response;
    }

    /**
     * 按 requestId 建立渠道请求摘要索引，供合一交互日志展示业务结果。
     *
     * @param rows 渠道请求摘要列表
     * @return requestId 到摘要行的映射
     */
    private Map<String, TransactionChannelRequestDO> channelRequestMap(List<TransactionChannelRequestDO> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, TransactionChannelRequestDO> result = new HashMap<>();
        for (TransactionChannelRequestDO row : rows) {
            if (row != null && StringUtils.hasText(row.getRequestId())) {
                result.putIfAbsent(row.getRequestId(), row);
            }
        }
        return result;
    }

    /**
     * 分页日志查询只返回交互表，需要按命中的 requestId 回查请求摘要，避免把“HTTP请求成功”误展示为交易成功。
     *
     * @param rows      渠道交互日志行
     * @param safeQuery 已归一化的查询条件
     * @return requestId 到渠道请求摘要的映射
     */
    private Map<String, TransactionChannelRequestDO> channelRequestMap(List<TransactionChannelInteractionLogDO> rows,
                                                                       ChannelLogQuery safeQuery) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        List<String> requestIds = rows.stream()
                .map(TransactionChannelInteractionLogDO::getRequestId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (requestIds.isEmpty()) {
            return Map.of();
        }
        List<TransactionChannelRequestDO> requestRows = new ArrayList<>();
        for (String table : physicalTablesInRange(TRANSACTION_CHANNEL_REQUEST_TABLE,
                safeQuery.getBeginTime(), safeQuery.getEndTime())) {
            requestRows.addAll(channelRequestMapper.selectByRequestIdsPhysical(table, requestIds));
        }
        return channelRequestMap(requestRows);
    }

    /**
     * 解析交互日志中已脱敏的渠道响应 JSON，只读取响应摘要字段，不影响原始报文展示。
     *
     * @param responseBodyJsonMasked 渠道响应脱敏 JSON
     * @return 渠道统一响应摘要
     */
    private ChannelPaymentResponse parseChannelPaymentResponse(String responseBodyJsonMasked) {
        if (!StringUtils.hasText(responseBodyJsonMasked)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(responseBodyJsonMasked, ChannelPaymentResponse.class);
        } catch (RuntimeException exception) {
            log.warn("解析渠道交互响应摘要失败，responseLength={}", responseBodyJsonMasked.length(), exception);
            return null;
        }
    }

    /**
     * 将渠道请求摘要和渠道响应摘要合并到后台日志视图。
     *
     * @param response        后台日志响应对象
     * @param requestDO       渠道请求摘要
     * @param channelResponse 渠道统一响应摘要
     */
    private void fillChannelInteractionResult(ChannelInteractionResponse response,
                                              TransactionChannelRequestDO requestDO,
                                              ChannelPaymentResponse channelResponse) {
        response.setGatewayResult(firstText(rawValue(channelResponse, "result"),
                requestDO == null ? null : requestDO.getGatewayResult()));
        response.setGatewayCode(firstText(rawValue(channelResponse, "gatewayCode"),
                requestDO == null ? null : requestDO.getGatewayCode()));
        response.setAcquirerCode(firstText(rawValue(channelResponse, "acquirerCode"),
                requestDO == null ? null : requestDO.getAcquirerCode()));
        response.setAcquirerMessage(firstText(rawValue(channelResponse, "acquirerMessage"),
                requestDO == null ? null : requestDO.getAcquirerMessage()));
        response.setChannelTradeStatus(firstText(channelResponse == null ? null : channelResponse.getChannelTradeStatus()));
        response.setRawChannelStatus(firstText(channelResponse == null ? null : channelResponse.getRawChannelStatus(),
                requestDO == null ? null : requestDO.getChannelStatus()));
        response.setChannelResponseCode(firstText(channelResponse == null ? null : channelResponse.getChannelResponseCode(),
                requestDO == null ? null : requestDO.getGatewayCode()));
        response.setChannelResponseMessage(firstText(channelResponse == null ? null : channelResponse.getChannelResponseMessage(),
                requestDO == null ? null : requestDO.getAcquirerMessage()));
        response.setPlatformResultCode(resolveInteractionPlatformResult(response, requestDO, channelResponse));
        response.setPlatformFailReason(resolveInteractionFailReason(response, requestDO, channelResponse));
    }

    /**
     * 从渠道响应原文字段中读取摘要值。
     *
     * @param channelResponse 渠道统一响应
     * @param key             原始响应字段名
     * @return 原始响应字段值
     */
    private String rawValue(ChannelPaymentResponse channelResponse, String key) {
        if (channelResponse == null || channelResponse.getRawResponse() == null) {
            return null;
        }
        return channelResponse.getRawResponse().get(key);
    }

    /**
     * 解析渠道交互的平台招商结果。
     * <p>
     * HTTP 200/201 或 MPGS 外层 result=SUCCESS 不等于资金成功；后台日志必须优先按
     * platform_success、渠道交易状态、渠道 raw status 和异常信息判断颜色和状态。
     *
     * @param response        后台日志响应对象
     * @param requestDO       渠道请求摘要
     * @param channelResponse 渠道统一响应
     * @return 平台交易状态字典值
     */
    private String resolveInteractionPlatformResult(ChannelInteractionResponse response,
                                                    TransactionChannelRequestDO requestDO,
                                                    ChannelPaymentResponse channelResponse) {
        if (StringUtils.hasText(response.getExceptionType())) {
            return PaymentTransactionStatusEnum.FAILED.getCode();
        }
        if (channelResponse != null && ("FAILED".equalsIgnoreCase(channelResponse.getChannelTradeStatus())
                || "ERROR".equalsIgnoreCase(channelResponse.getRawChannelStatus())
                || "DECLINED".equalsIgnoreCase(channelResponse.getRawChannelStatus()))) {
            return PaymentTransactionStatusEnum.FAILED.getCode();
        }
        if (requestDO != null && requestDO.getPlatformSuccess() != null) {
            return requestDO.getPlatformSuccess() == 1
                    ? PaymentTransactionStatusEnum.SUCCESS.getCode()
                    : PaymentTransactionStatusEnum.FAILED.getCode();
        }
        if (channelResponse != null && StringUtils.hasText(channelResponse.getChannelTradeStatus())) {
            return channelResponse.getChannelTradeStatus();
        }
        return requestDO == null ? null : requestDO.getPlatformResultCode();
    }

    /**
     * 解析渠道交互失败原因。
     *
     * @param response        后台日志响应对象
     * @param requestDO       渠道请求摘要
     * @param channelResponse 渠道统一响应
     * @return 后台可见失败原因
     */
    private String resolveInteractionFailReason(ChannelInteractionResponse response,
                                                TransactionChannelRequestDO requestDO,
                                                ChannelPaymentResponse channelResponse) {
        if (response == null || !PaymentTransactionStatusEnum.FAILED.getCode().equals(response.getPlatformResultCode())) {
            return null;
        }
        return firstText(response.getExceptionMessage(),
                channelResponse == null ? null : channelResponse.getChannelResponseMessage(),
                requestDO == null ? null : requestDO.getPlatformFailReason());
    }

    /**
     * 为渠道交互日志补齐交易类型。
     *
     * @param responses 渠道交互日志视图
     * @return 已补齐交易类型的日志视图
     */
    private List<ChannelInteractionResponse> enrichChannelInteractionTransactionTypes(List<ChannelInteractionResponse> responses) {
        return enrichChannelInteractionTransactionTypes(responses, Map.of());
    }

    /**
     * 为渠道交互日志补齐交易类型。
     * <p>
     * 渠道交互表保存 HTTP 交互事实，业务交易类型优先来自 transaction_channel_request；
     * 若分页查询只命中交互表，则按 transaction_id + transaction_date_time 回查动作单补齐。
     *
     * @param responses    渠道交互日志视图
     * @param knownTypeMap 已知交易类型映射
     * @return 已补齐交易类型的日志视图
     */
    private List<ChannelInteractionResponse> enrichChannelInteractionTransactionTypes(List<ChannelInteractionResponse> responses,
                                                                                     Map<String, String> knownTypeMap) {
        if (responses == null || responses.isEmpty()) {
            return List.of();
        }
        Map<String, String> transactionTypeMap = new HashMap<>(knownTypeMap == null ? Map.of() : knownTypeMap);
        for (ChannelInteractionResponse response : responses) {
            if (StringUtils.hasText(response.getTransactionType()) || !StringUtils.hasText(response.getTransactionId())) {
                continue;
            }
            String transactionType = transactionTypeMap.computeIfAbsent(response.getTransactionId(),
                    transactionId -> findTransactionType(response.getTransactionId(), response.getTransactionDateTime()));
            response.setTransactionType(transactionType);
        }
        return responses;
    }

    /**
     * 查询单笔交易动作类型。
     *
     * @param transactionId       平台当前交易 ID
     * @param transactionDateTime 交易业务时间，可为空
     * @return 交易类型字典值
     */
    private String findTransactionType(String transactionId, LocalDateTime transactionDateTime) {
        if (!StringUtils.hasText(transactionId)) {
            return null;
        }
        LocalDateTime shardTime = transactionDateTime == null ? parseTransactionDateTime(transactionId) : transactionDateTime;
        if (shardTime == null) {
            return null;
        }
        try {
            String table = physicalTable(TRANSACTION_OPERATION_TABLE, shardTime);
            TransactionOperationDO operation = operationMapper.selectByTransactionIdPhysical(table, transactionId);
            return operation == null ? null : operation.getTransactionType();
        } catch (DataAccessException | ServiceException exception) {
            log.warn("查询渠道交互日志交易类型失败，transactionId={}", transactionId, exception);
            return null;
        }
    }

    /**
     * 选择请求日志行，兼容历史 REQUEST 行和当前 REQUEST_RESPONSE 行。
     *
     * @param rows 同一 requestId 的日志行
     * @return 请求日志行
     */
    private TransactionChannelInteractionLogDO selectRequestLogRow(List<TransactionChannelInteractionLogDO> rows) {
        return rows.stream()
                .filter(row -> "REQUEST".equals(row.getInteractionType()) || StringUtils.hasText(row.getRequestBodyJsonMasked()))
                .findFirst()
                .orElse(rows.get(0));
    }

    /**
     * 选择响应日志行，兼容历史 RESPONSE/EXCEPTION 行和当前 REQUEST_RESPONSE 行。
     *
     * @param rows       同一 requestId 的日志行
     * @param requestRow 已选择的请求行
     * @return 响应日志行
     */
    private TransactionChannelInteractionLogDO selectResponseLogRow(List<TransactionChannelInteractionLogDO> rows,
                                                                    TransactionChannelInteractionLogDO requestRow) {
        return rows.stream()
                .filter(row -> row != requestRow)
                .filter(row -> "RESPONSE".equals(row.getInteractionType())
                        || "EXCEPTION".equals(row.getInteractionType())
                        || StringUtils.hasText(row.getResponseBodyJsonMasked())
                        || StringUtils.hasText(row.getExceptionType()))
                .findFirst()
                .orElse(hasResponsePayload(requestRow) ? requestRow : null);
    }

    /**
     * 判断日志行是否已经包含响应或异常载荷。
     *
     * @param row 渠道交互日志行
     * @return true 表示包含响应或异常信息
     */
    private boolean hasResponsePayload(TransactionChannelInteractionLogDO row) {
        return row != null && (StringUtils.hasText(row.getResponseBodyJsonMasked())
                || StringUtils.hasText(row.getResponseHeaderJsonMasked())
                || StringUtils.hasText(row.getExceptionType()));
    }

    /**
     * 解析后台展示用交互类型。
     *
     * @param primary     请求或主日志行
     * @param responseRow 响应日志行
     * @return 交互类型
     */
    private String resolveInteractionType(TransactionChannelInteractionLogDO primary,
                                          TransactionChannelInteractionLogDO responseRow) {
        if ((primary != null && StringUtils.hasText(primary.getExceptionType()))
                || (responseRow != null && StringUtils.hasText(responseRow.getExceptionType()))) {
            return "EXCEPTION";
        }
        if (primary != null && "REQUEST_RESPONSE".equals(primary.getInteractionType())) {
            return primary.getInteractionType();
        }
        if (responseRow != null && "RESPONSE".equals(responseRow.getInteractionType())) {
            return "REQUEST_RESPONSE";
        }
        return primary == null ? null : primary.getInteractionType();
    }

    /**
     * 解析渠道请求时间。
     *
     * @param requestRow  请求日志行
     * @param responseRow 响应日志行
     * @return 请求发起时间
     */
    private LocalDateTime resolveRequestTime(TransactionChannelInteractionLogDO requestRow,
                                             TransactionChannelInteractionLogDO responseRow) {
        if (requestRow != null && requestRow.getInteractionTime() != null) {
            return requestRow.getInteractionTime();
        }
        if (responseRow != null && responseRow.getDurationMillis() != null && responseRow.getInteractionTime() != null) {
            return responseRow.getInteractionTime().minus(responseRow.getDurationMillis(), ChronoUnit.MILLIS);
        }
        return responseRow == null ? null : responseRow.getInteractionTime();
    }

    /**
     * 解析渠道响应时间。
     *
     * @param requestRow  请求日志行
     * @param responseRow 响应日志行
     * @return 响应接收时间
     */
    private LocalDateTime resolveResponseTime(TransactionChannelInteractionLogDO requestRow,
                                              TransactionChannelInteractionLogDO responseRow) {
        if (responseRow != null && responseRow != requestRow && responseRow.getInteractionTime() != null) {
            return responseRow.getInteractionTime();
        }
        if (requestRow != null && requestRow.getDurationMillis() != null && requestRow.getInteractionTime() != null) {
            return requestRow.getInteractionTime().plus(requestRow.getDurationMillis(), ChronoUnit.MILLIS);
        }
        return null;
    }

    /**
     * 归一化查询时间范围并转换到系统交易时区。
     * <p>
     * 页面输入时间按 queryTimeZone 解释；实际分表字段 transaction_date_time 按系统默认交易时区存储。
     *
     * @param beginTime     用户选择的开始时间
     * @param endTime       用户选择的结束时间
     * @param queryTimeZone 用户选择的查询时区
     * @return 转换后的系统分表查询时间范围
     */
    private QueryTimeRange normalizeQueryTimeRange(LocalDateTime beginTime,
                                                   LocalDateTime endTime,
                                                   String queryTimeZone) {
        ZoneId queryZone = resolveQueryZone(queryTimeZone);
        LocalDateTime safeEnd = endTime == null ? LocalDateTime.now(queryZone) : endTime;
        LocalDateTime safeBegin = beginTime == null ? safeEnd.toLocalDate().atStartOfDay() : beginTime;
        if (safeBegin.isAfter(safeEnd)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "beginTime must not be after endTime");
        }
        return new QueryTimeRange(
                convertBetweenZones(safeBegin, queryZone, ZoneId.of(DEFAULT_QUERY_TIME_ZONE)),
                convertBetweenZones(safeEnd, queryZone, ZoneId.of(DEFAULT_QUERY_TIME_ZONE)),
                queryZone.getId());
    }

    /**
     * 解析查询时区。
     *
     * @param queryTimeZone 查询时区，可为 IANA 时区或 UTC/GMT 偏移写法
     * @return Java ZoneId
     */
    private ZoneId resolveQueryZone(String queryTimeZone) {
        String zone = StringUtils.hasText(queryTimeZone) ? queryTimeZone.trim() : DEFAULT_QUERY_TIME_ZONE;
        try {
            return ZoneId.of(normalizeZoneId(zone));
        } catch (DateTimeException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "queryTimeZone is invalid", exception);
        }
    }

    /**
     * 标准化 UTC/GMT 偏移时区写法。
     *
     * @param zone 原始时区字符串
     * @return ZoneId 可识别的时区字符串
     */
    private String normalizeZoneId(String zone) {
        if (!StringUtils.hasText(zone)) {
            return DEFAULT_QUERY_TIME_ZONE;
        }
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
                return prefix + offset.charAt(0) + String.format("%02d:%s", Integer.parseInt(parts[0]), parts[1]);
            }
        }
        return normalized;
    }

    /**
     * 按时区换算本地时间。
     *
     * @param sourceTime 源本地时间
     * @param sourceZone 源时区
     * @param targetZone 目标时区
     * @return 目标时区下的本地时间
     */
    private LocalDateTime convertBetweenZones(LocalDateTime sourceTime, ZoneId sourceZone, ZoneId targetZone) {
        if (sourceTime == null) {
            return null;
        }
        return sourceTime.atZone(sourceZone).withZoneSameInstant(targetZone).toLocalDateTime();
    }

    /**
     * 执行 resolve Merchant Response Code 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param transactionStatus 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 解析或查询得到的业务值
     */
    private String resolveMerchantResponseCode(String transactionStatus) {
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_SUCCESS.getCode();
        }
        if (PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_REJECTED.getCode();
        }
        if (PaymentTransactionStatusEnum.PENDING.getCode().equals(transactionStatus)) {
            return ApiResultEnum.PENDING.getCode();
        }
        return ApiResultEnum.PROCESSING.getCode();
    }

    /**
     * 执行 default Rate 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private BigDecimal defaultRate(BigDecimal value) {
        return value == null ? new BigDecimal("1.00000000") : value;
    }

    /**
     * 执行 resolve Merchant Response Message 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param transactionStatus 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 解析或查询得到的业务值
     */
    private String resolveMerchantResponseMessage(String transactionStatus) {
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_SUCCESS.getMessage();
        }
        if (PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_REJECTED.getMessage();
        }
        if (PaymentTransactionStatusEnum.PENDING.getCode().equals(transactionStatus)) {
            return ApiResultEnum.PENDING.getMessage();
        }
        return ApiResultEnum.PROCESSING.getMessage();
    }

    /**
     * 执行 resolve Status By Merchant Response Code 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param merchantResponseCode merchant Response Code 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolveStatusByMerchantResponseCode(String merchantResponseCode) {
        if (!StringUtils.hasText(merchantResponseCode)) {
            return null;
        }
        String code = merchantResponseCode.trim();
        if (ApiResultEnum.PAYMENT_SUCCESS.getCode().equalsIgnoreCase(code)
                || ApiResultEnum.SUCCESS.getCode().equalsIgnoreCase(code)) {
            return PaymentTransactionStatusEnum.SUCCESS.getCode();
        }
        if (ApiResultEnum.PAYMENT_REJECTED.getCode().equalsIgnoreCase(code)
                || ApiResultEnum.PAYMENT_REJECTED_BY_ISSUER.getCode().equalsIgnoreCase(code)) {
            return PaymentTransactionStatusEnum.FAILED.getCode();
        }
        if (ApiResultEnum.PENDING.getCode().equalsIgnoreCase(code)) {
            return PaymentTransactionStatusEnum.PENDING.getCode();
        }
        if (ApiResultEnum.PROCESSING.getCode().equalsIgnoreCase(code)) {
            return PaymentTransactionStatusEnum.PROCESSING.getCode();
        }
        return null;
    }

    /**
     * 执行 normalize Card Number Masked 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param paymentInfoDO payment Info DO 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private String normalizeCardNumberMasked(TransactionPaymentMethodInfoDO paymentInfoDO) {
        if (paymentInfoDO == null) {
            return null;
        }
        String cardBin = paymentInfoDO.getCardBin();
        String cardLast4 = paymentInfoDO.getCardLast4();
        if (StringUtils.hasText(cardBin) && cardBin.length() >= 6 && StringUtils.hasText(cardLast4)) {
            return cardBin.substring(0, 6) + "****" + cardLast4;
        }
        String masked = paymentInfoDO.getCardNumberMasked();
        if (!StringUtils.hasText(masked)) {
            return null;
        }
        String digits = masked.replaceAll("\\D", "");
        if (digits.length() >= 10) {
            return digits.substring(0, 6) + "****" + digits.substring(digits.length() - 4);
        }
        return masked;
    }

    /**
     * 执行 first Text 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param values values 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Map<String, TransactionPaymentMethodInfoDO> paymentInfoMap(List<TransactionOperationDO> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, TransactionPaymentMethodInfoDO> byTransactionId = rows.stream()
                .filter(row -> row.getTransactionDateTime() != null && StringUtils.hasText(row.getTransactionId()))
                .collect(Collectors.groupingBy(TransactionOperationDO::getTransactionDateTime))
                .entrySet()
                .stream()
                .flatMap(entry -> {
                    List<String> transactionIds = entry.getValue().stream()
                            .map(TransactionOperationDO::getTransactionId)
                            .distinct()
                            .toList();
                    if (transactionIds.isEmpty()) {
                        return List.<TransactionPaymentMethodInfoDO>of().stream();
                    }
                    String table = physicalTable(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, entry.getKey());
                    return paymentMethodInfoMapper.selectByTransactionIdsPhysical(table, transactionIds).stream();
                })
                .collect(Collectors.toMap(TransactionPaymentMethodInfoDO::getTransactionId, Function.identity(), (left, right) -> left));
        Map<String, TransactionPaymentMethodInfoDO> byOperationId = paymentInfoMapByOperationId(rows);
        Map<String, TransactionPaymentMethodInfoDO> result = new HashMap<>();
        rows.stream()
                .filter(row -> StringUtils.hasText(row.getTransactionId()))
                .forEach(row -> {
                    TransactionPaymentMethodInfoDO infoDO = firstPaymentInfo(
                            byTransactionId.get(row.getTransactionId()), byOperationId.get(row.getOperationId()));
                    if (infoDO != null) {
                        result.putIfAbsent(row.getTransactionId(), infoDO);
                    }
                });
        return result;
    }

    private Map<String, TransactionPaymentMethodInfoDO> paymentInfoMapByOperationId(List<TransactionOperationDO> rows) {
        Map<String, List<TransactionOperationDO>> operationRows = rows.stream()
                .filter(row -> StringUtils.hasText(row.getOperationId()) && row.getTransactionDateTime() != null)
                .collect(Collectors.groupingBy(TransactionOperationDO::getOperationId));
        Map<String, TransactionPaymentMethodInfoDO> result = new java.util.HashMap<>();
        operationRows.forEach((operationId, operationList) -> {
            TransactionPaymentMethodInfoDO infoDO = findPaymentInfoByOperationId(operationId, operationList);
            if (infoDO != null) {
                result.put(operationId, infoDO);
            }
        });
        return result;
    }

    /**
     * 执行 find Payment Info By Operation Id 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
     * @param rows rows 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private TransactionPaymentMethodInfoDO findPaymentInfoByOperationId(String operationId, List<TransactionOperationDO> rows) {
        LocalDateTime rowBeginTime = rows.stream()
                .map(TransactionOperationDO::getTransactionDateTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime rootBeginTime = parseOperationDateTime(operationId);
        LocalDateTime beginTime = rootBeginTime == null ? rowBeginTime : rootBeginTime;
        LocalDateTime endTime = rows.stream()
                .map(TransactionOperationDO::getTransactionDateTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (beginTime == null || endTime == null) {
            return null;
        }
        for (String table : physicalTablesInRange(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, beginTime, endTime)) {
            List<TransactionPaymentMethodInfoDO> infos = paymentMethodInfoMapper.selectByOperationIdPhysical(table, operationId);
            if (infos == null || infos.isEmpty()) {
                continue;
            }
            TransactionPaymentMethodInfoDO info = infos.stream()
                    .filter(this::hasCardSummary)
                    .findFirst()
                    .orElse(infos.get(0));
            if (info != null) {
                return info;
            }
        }
        return null;
    }

/**
 * 执行 first Payment Info 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param current current 输入值，含义由调用方法名称和所属业务对象限定
 * @param fallback fallback 输入值，含义由调用方法名称和所属业务对象限定
 * @return 方法签名声明的返回值，具体结构由返回类型定义
 */
    private TransactionPaymentMethodInfoDO firstPaymentInfo(TransactionPaymentMethodInfoDO current,
                                                            TransactionPaymentMethodInfoDO fallback) {
        if (hasCardSummary(current) || fallback == null) {
            return current;
        }
        return fallback;
    }

    /**
     * 执行 has Card Summary 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param infoDO info DO 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean hasCardSummary(TransactionPaymentMethodInfoDO infoDO) {
        return infoDO != null && (StringUtils.hasText(infoDO.getCardBin())
                || StringUtils.hasText(infoDO.getCardLast4())
                || StringUtils.hasText(infoDO.getCardNumberMasked()));
    }

    /**
     * 执行 order Map 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param rows rows 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private Map<String, TransactionOrderDO> orderMap(List<TransactionOperationDO> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, TransactionOrderDO> result = new HashMap<>();
        rows.stream()
                .filter(row -> StringUtils.hasText(row.getOperationId()))
                .forEach(row -> {
                    TransactionOrderDO orderDO = findOrderForOperation(row);
                    if (orderDO != null) {
                        result.putIfAbsent(row.getOperationId(), orderDO);
                    }
                });
        return result;
    }

    /**
     * 执行 find Order For Operation 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private TransactionOrderDO findOrderForOperation(TransactionOperationDO row) {
        LocalDateTime orderTransactionDateTime = parseOperationDateTime(row.getOperationId());
        if (orderTransactionDateTime == null) {
            orderTransactionDateTime = row.getTransactionDateTime();
        }
        if (orderTransactionDateTime == null) {
            return null;
        }
        String table = physicalTable(TRANSACTION_ORDER_TABLE, orderTransactionDateTime);
        return orderMapper.selectByOperationIdPhysical(table, row.getOperationId());
    }

    /**
     * 执行 merchant Notification Status Map 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param rows rows 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private Map<String, String> merchantNotificationStatusMap(List<TransactionOperationDO> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        rows.stream()
                .filter(row -> row.getTransactionDateTime() != null && StringUtils.hasText(row.getTransactionId()))
                .collect(Collectors.groupingBy(TransactionOperationDO::getTransactionDateTime))
                .forEach((transactionDateTime, operationRows) -> {
                    String table = physicalTable(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, transactionDateTime);
                    operationRows.stream()
                            .map(TransactionOperationDO::getTransactionId)
                            .distinct()
                            .forEach(transactionId -> {
                                List<TransactionMerchantNotificationDO> notifications = notificationMapper.selectByTransactionIdPhysical(table, transactionId);
                                if (notifications != null && !notifications.isEmpty() && StringUtils.hasText(notifications.get(0).getNotifyStatus())) {
                                    result.put(transactionId, notifications.get(0).getNotifyStatus());
                                }
                            });
                });
        return result;
    }

    /**
     * 执行 payment Info Table For Operation Table 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param operationPhysicalTable operation Physical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private String paymentInfoTableForOperationTable(String operationPhysicalTable) {
        if (!StringUtils.hasText(operationPhysicalTable)) {
            return TRANSACTION_PAYMENT_METHOD_INFO_TABLE;
        }
        return operationPhysicalTable.replaceFirst("^" + TRANSACTION_OPERATION_TABLE, TRANSACTION_PAYMENT_METHOD_INFO_TABLE);
    }

/**
 * 执行 select Operations By Operation Id 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @return 渠道 API 操作类型或平台操作映射结果
 */
    private List<TransactionOperationDO> selectOperationsByOperationId(String operationId,
                                                                       LocalDateTime beginTime,
                                                                       LocalDateTime endTime) {
        List<TransactionOperationDO> rows = new ArrayList<>();
        for (String table : physicalTablesInRange(TRANSACTION_OPERATION_TABLE, beginTime, endTime)) {
            rows.addAll(operationMapper.selectByOperationIdPhysical(table, operationId));
        }
        return rows;
    }

    private <T> List<T> selectByOperationIdAcrossTables(String logicalTable,
                                                        LocalDateTime beginTime,
                                                        LocalDateTime endTime,
                                                        PhysicalTableSelector<T> selector) {
        List<T> rows = new ArrayList<>();
        for (String table : physicalTablesInRange(logicalTable, beginTime, endTime)) {
            rows.addAll(selector.select(table));
        }
        return rows;
    }

    private <T> List<T> selectOptionalByOperationIdAcrossTables(String logicalTable,
                                                                LocalDateTime beginTime,
                                                                LocalDateTime endTime,
                                                                PhysicalTableSelector<T> selector) {
        List<T> rows = new ArrayList<>();
        List<String> physicalTables;
        try {
            physicalTables = physicalTablesInRange(logicalTable, beginTime, endTime);
        } catch (ServiceException exception) {
            log.warn("交易详情附属日志分表规则不可用，logicalTable：{}，beginTime：{}，endTime：{}",
                    logicalTable, beginTime, endTime, exception);
            return rows;
        }
        for (String table : physicalTables) {
            try {
                rows.addAll(selector.select(table));
            } catch (DataAccessException exception) {
                log.warn("交易详情附属日志查询失败，logicalTable：{}，physicalTable：{}，beginTime：{}，endTime：{}",
                        logicalTable, table, beginTime, endTime, exception);
            }
        }
        return rows;
    }

    /**
     * 执行 physical Table 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param logicalTable logical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String physicalTable(String logicalTable, LocalDateTime transactionDateTime) {
        return shardingDataTemplate.resolvePhysicalTable(
                ShardingSingleTableContext.of(logicalTable, transactionDateTime, DataSourceName.SLAVE));
    }

    /**
     * 执行 physical Tables In Range 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param logicalTable logical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private List<String> physicalTablesInRange(String logicalTable, LocalDateTime beginTime, LocalDateTime endTime) {
        return shardingDataTemplate.resolvePhysicalTables(
                ShardingRangeTableContext.of(logicalTable, beginTime, endTime, DataSourceName.SLAVE));
    }

    /**
     * 执行 parse Transaction Date Time 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
     * @return 解析后的内部数据结构或业务值
     */
    private LocalDateTime parseTransactionDateTime(String transactionId) {
        return transactionShardingKeyParser.parseTransactionDateTime(transactionId);
    }

    /**
     * 执行 parse Operation Date Time 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionQueryService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private LocalDateTime parseOperationDateTime(String operationId) {
        return transactionShardingKeyParser.parseOperationDateTime(operationId);
    }

    private record QueryTimeRange(LocalDateTime beginTime, LocalDateTime endTime, String queryTimeZone) {
    }

    /**
     * 交易动作统计累加器。
     * <p>
     * 每个物理分表先在数据库内完成分组汇总，服务层只负责跨分表合并同币种、
     * 同状态和同支付方式的结果，避免把明细全量拉回内存。
     */
    private static final class TransactionOperationSummaryAccumulator {

        /**
         * 空支付方式分组展示值。
         */
        private static final String UNKNOWN_VALUE = "UNKNOWN";

        /**
         * 全部交易按币种汇总。
         */
        private final Map<String, AmountBucket> totalAmountBuckets = new HashMap<>();

        /**
         * 成功交易按币种汇总。
         */
        private final Map<String, AmountBucket> successAmountBuckets = new HashMap<>();

        /**
         * 失败交易按币种汇总。
         */
        private final Map<String, AmountBucket> failedAmountBuckets = new HashMap<>();

        /**
         * 支付方式/卡品牌汇总。
         */
        private final Map<String, PaymentBucket> paymentBuckets = new HashMap<>();

        /**
         * 全部命中交易笔数。
         */
        private long totalCount;

        /**
         * 成功交易笔数。
         */
        private long successCount;

        /**
         * 失败交易笔数。
         */
        private long failedCount;

        /**
         * 累加状态与币种维度汇总行。
         *
         * @param row Mapper 聚合行
         */
        private void addAmountRow(TransactionOperationSummaryRow row) {
            if (row == null) {
                return;
            }
            long rowCount = row.getCount();
            totalCount += rowCount;
            addAmount(totalAmountBuckets, row);
            if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(row.getTransactionStatus())) {
                successCount += rowCount;
                addAmount(successAmountBuckets, row);
            }
            if (PaymentTransactionStatusEnum.FAILED.getCode().equals(row.getTransactionStatus())) {
                failedCount += rowCount;
                addAmount(failedAmountBuckets, row);
            }
        }

        /**
         * 累加支付方式和卡品牌维度汇总行。
         *
         * @param row Mapper 聚合行
         */
        private void addPaymentMethodRow(TransactionOperationSummaryRow row) {
            if (row == null) {
                return;
            }
            String paymentMethod = normalizeGroupValue(row.getPaymentMethod());
            String paymentBrand = row.getPaymentBrand();
            String paymentKey = paymentMethod + ":" + Optional.ofNullable(paymentBrand).orElse("");
            PaymentBucket bucket = paymentBuckets.computeIfAbsent(paymentKey,
                    key -> new PaymentBucket(paymentMethod, paymentBrand));
            bucket.count += row.getCount();
            addAmount(bucket.amountBuckets, row);
        }

        /**
         * 转换为接口响应对象。
         *
         * @return 交易动作统计响应
         */
        private TransactionOperationSummaryResponse toResponse() {
            TransactionOperationSummaryResponse response = new TransactionOperationSummaryResponse();
            response.setTotalCount(totalCount);
            response.setSuccessCount(successCount);
            response.setFailedCount(failedCount);
            response.setAmountSummaries(toAmountResponses(totalAmountBuckets));
            response.setSuccessAmountSummaries(toAmountResponses(successAmountBuckets));
            response.setFailedAmountSummaries(toAmountResponses(failedAmountBuckets));
            response.setPaymentMethodSummaries(toPaymentResponses());
            return response;
        }

        /**
         * 计算 add Amount 对应的数值结果，调用方负责保证金额和币种上下文一致。
         * <p>
         * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 TransactionOperationSummaryAccumulator 的方法签名及调用链约束。
         * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
         * </p>
         * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
         * @param buckets buckets 输入值，含义由调用方法名称和所属业务对象限定
         * @param row row 输入值，含义由调用方法名称和所属业务对象限定
         */
        private void addAmount(Map<String, AmountBucket> buckets, TransactionOperationSummaryRow row) {
            String currency = normalizeGroupValue(row.getCurrency());
            AmountBucket bucket = buckets.computeIfAbsent(currency, key -> new AmountBucket(currency));
            bucket.amount = bucket.amount.add(row.getAmount() == null ? BigDecimal.ZERO : row.getAmount());
            if (bucket.currencyExponent == null) {
                bucket.currencyExponent = row.getCurrencyExponent();
            }
        }

        /**
         * 转换生成 to Payment Responses 对应的传输对象、导出行或协议字段。
         * <p>
         * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 TransactionOperationSummaryAccumulator 的方法签名及调用链约束。
         * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
         * </p>
         * @return 转换或构建后的目标对象
         */
        private List<TransactionPaymentMethodSummaryResponse> toPaymentResponses() {
            return paymentBuckets.values()
                    .stream()
                    .sorted(Comparator.comparingLong(PaymentBucket::getCount).reversed()
                            .thenComparing(bucket -> bucket.paymentMethod)
                            .thenComparing(bucket -> Optional.ofNullable(bucket.paymentBrand).orElse("")))
                    .map(bucket -> {
                        TransactionPaymentMethodSummaryResponse response = new TransactionPaymentMethodSummaryResponse();
                        response.setPaymentMethod(bucket.paymentMethod);
                        response.setPaymentBrand(bucket.paymentBrand);
                        response.setCount(bucket.count);
                        response.setAmountSummaries(toAmountResponses(bucket.amountBuckets));
                        return response;
                    })
                    .toList();
        }

        /**
         * 转换生成 to Amount Responses 对应的传输对象、导出行或协议字段。
         * <p>
         * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 TransactionOperationSummaryAccumulator 的方法签名及调用链约束。
         * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
         * </p>
         * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
         * @param buckets buckets 输入值，含义由调用方法名称和所属业务对象限定
         * @return 按渠道协议格式化后的金额字符串或金额计算结果
         */
        private List<TransactionAmountSummaryResponse> toAmountResponses(Map<String, AmountBucket> buckets) {
            return buckets.values()
                    .stream()
                    .sorted(Comparator.comparing(AmountBucket::getAmount).reversed()
                            .thenComparing(AmountBucket::getCurrency))
                    .map(bucket -> {
                        TransactionAmountSummaryResponse response = new TransactionAmountSummaryResponse();
                        response.setCurrency(bucket.currency);
                        response.setAmount(bucket.amount);
                        response.setCurrencyExponent(bucket.currencyExponent);
                        return response;
                    })
                    .toList();
        }

        /**
         * 标准化 normalize Group Value 输入值，统一大小写、空白字符或协议格式。
         * <p>
         * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 TransactionOperationSummaryAccumulator 的方法签名及调用链约束。
         * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
         * </p>
         * @param value 待校验或转换的原始值
         * @return 标准化后的业务字段值
         */
        private static String normalizeGroupValue(String value) {
            return StringUtils.hasText(value) ? value : UNKNOWN_VALUE;
        }
    }

    /**
     * 币种金额汇总桶。
     */
    private static final class AmountBucket {

        /**
         * 交易币种代码。
         */
        private final String currency;

        /**
         * 汇总金额，主币种单位。
         */
        private BigDecimal amount = BigDecimal.ZERO;

        /**
         * 币种默认小数位。
         */
        private Integer currencyExponent;

        /**
         * 创建 AmountBucket 实例并注入其运行所需依赖。
         * <p>
         * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 AmountBucket 的方法签名及调用链约束。
         * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
         * </p>
         * @param currency 币种代码，格式为 ISO 4217 三位大写字母
         */
        private AmountBucket(String currency) {
            this.currency = currency;
        }

        /**
         * 完成 get Currency 的本地校验、字段转换或结果组装，供当前调用链继续使用。
         * <p>
         * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 AmountBucket 的方法签名及调用链约束。
         * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
         * </p>
         * @return 标准化后的 ISO 4217 币种代码
         */
        private String getCurrency() {
            return currency;
        }

        /**
         * 完成 get Amount 的本地校验、字段转换或结果组装，供当前调用链继续使用。
         * <p>
         * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 AmountBucket 的方法签名及调用链约束。
         * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
         * </p>
         * @return 按渠道协议格式化后的金额字符串或金额计算结果
         */
        private BigDecimal getAmount() {
            return amount;
        }
    }

    /**
     * 支付方式/卡品牌汇总桶。
     */
    private static final class PaymentBucket {

        /**
         * 支付方式。
         */
        private final String paymentMethod;

        /**
         * 支付品牌或卡品牌。
         */
        private final String paymentBrand;

        /**
         * 当前支付方式命中的交易笔数。
         */
        private long count;

        /**
         * 当前支付方式下按币种聚合的金额。
         */
        private final Map<String, AmountBucket> amountBuckets = new HashMap<>();

        /**
         * 创建 PaymentBucket 实例并注入其运行所需依赖。
         * <p>
         * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 PaymentBucket 的方法签名及调用链约束。
         * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
         * </p>
         * @param paymentMethod payment Method 输入值，含义由调用方法名称和所属业务对象限定
         * @param paymentBrand payment Brand 输入值，含义由调用方法名称和所属业务对象限定
         */
        private PaymentBucket(String paymentMethod, String paymentBrand) {
            this.paymentMethod = paymentMethod;
            this.paymentBrand = paymentBrand;
        }

        /**
         * 完成 get Count 的本地校验、字段转换或结果组装，供当前调用链继续使用。
         * <p>
         * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 PaymentBucket 的方法签名及调用链约束。
         * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
         * </p>
         * @return 方法签名声明的返回值，具体结构由返回类型定义
         */
        private long getCount() {
            return count;
        }
    }

    @FunctionalInterface
    private interface PhysicalTableSelector<T> {

        List<T> select(String table);
    }
}
