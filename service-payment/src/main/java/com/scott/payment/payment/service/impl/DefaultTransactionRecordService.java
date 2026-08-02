package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionMerchantApiResponseLogUpdateCommandDTO;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.TransactionAmountChangeLogDO;
import com.scott.payment.payment.entity.TransactionChannelInteractionLogDO;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionFlowEventDO;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.entity.TransactionMerchantApiInteractionLogDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.entity.TransactionPaymentMethodInfoDO;
import com.scott.payment.payment.entity.TransactionStatusHistoryDO;
import com.scott.payment.payment.mapper.TransactionAmountChangeLogMapper;
import com.scott.payment.payment.mapper.TransactionChannelInteractionLogMapper;
import com.scott.payment.payment.mapper.TransactionChannelRequestMapper;
import com.scott.payment.payment.mapper.TransactionFlowEventMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationMapper;
import com.scott.payment.payment.mapper.TransactionMerchantApiInteractionLogMapper;
import com.scott.payment.payment.mapper.TransactionOperationMapper;
import com.scott.payment.payment.mapper.TransactionOrderMapper;
import com.scott.payment.payment.mapper.TransactionPaymentMethodInfoMapper;
import com.scott.payment.payment.mapper.TransactionStatusHistoryMapper;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionRecordService
 * @date : 2026-07-14 17:45
 * @email : scott_x@163.com
 * @description : 交易事实记录服务默认实现，位于 service-payment 服务实现层，通过交易逻辑表写入共享 transaction_date_time 的主单、动作单和状态历史。
 * @status : create
 */
@Service
@Slf4j
@DS(DataSourceName.TRANSACTION)
public class DefaultTransactionRecordService implements TransactionRecordService {

    /**
     * 交易生命周期主单逻辑表名。
     */
    private static final String TRANSACTION_ORDER_TABLE = "transaction_order";

    /**
     * 交易动作单逻辑表名。
     */
    private static final String TRANSACTION_OPERATION_TABLE = "transaction_operation";

    /**
     * 交易状态历史逻辑表名。
     */
    private static final String TRANSACTION_STATUS_HISTORY_TABLE = "transaction_status_history";

    /**
     * 渠道请求逻辑表名。
     */
    private static final String TRANSACTION_CHANNEL_REQUEST_TABLE = "transaction_channel_request";

    /**
     * 渠道交互日志逻辑表名。
     */
    private static final String TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE = "transaction_channel_interaction_log";

    /**
     * 交易流程事件逻辑表名。
     */
    private static final String TRANSACTION_FLOW_EVENT_TABLE = "transaction_flow_event";

    /**
     * 交易金额变动日志逻辑表名。
     */
    private static final String TRANSACTION_AMOUNT_CHANGE_LOG_TABLE = "transaction_amount_change_log";

    /**
     * 交易支付工具摘要逻辑表名。
     */
    private static final String TRANSACTION_PAYMENT_METHOD_INFO_TABLE = "transaction_payment_method_info";

    /**
     * 商户通知任务逻辑表名。
     */
    private static final String TRANSACTION_MERCHANT_NOTIFICATION_TABLE = "transaction_merchant_notification";

    /**
     * 商户 OpenAPI 交互日志逻辑表名。
     */
    private static final String TRANSACTION_MERCHANT_API_INTERACTION_LOG_TABLE = "transaction_merchant_api_interaction_log";

    /**
     * 状态历史编号前缀。
     */
    private static final String STATUS_HISTORY_PREFIX = "TSH";

    /**
     * 流程事件编号前缀。
     */
    private static final String FLOW_EVENT_PREFIX = "TFE";

    /**
     * 渠道交互日志编号前缀。
     */
    private static final String CHANNEL_INTERACTION_LOG_PREFIX = "CIL";

    /**
     * 金额变动编号前缀。
     */
    private static final String AMOUNT_CHANGE_PREFIX = "TAC";

    /**
     * 支付工具摘要编号前缀。
     */
    private static final String PAYMENT_INFO_PREFIX = "TPM";

    /**
     * 商户通知任务编号前缀。
     */
    private static final String MERCHANT_NOTIFICATION_PREFIX = "TMN";

    /**
     * 商户 OpenAPI 交互日志编号前缀。
     */
    private static final String MERCHANT_API_LOG_PREFIX = "MAL";

    /**
     * RAW REQUEST HEADER JSON MASKED，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：JSON 字符串或结构化对象；不允许为空；非敏感字段。
     * 取值范围：内容必须先脱敏再进入日志；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String RAW_REQUEST_HEADER_JSON_MASKED = "requestHeaderJsonMasked";

    /**
     * RAW REQUEST BODY JSON MASKED，表示请求体、响应体或消息载荷，日志中只能保留脱敏摘要。
     * <p>
     * 单位：无；格式：JSON 字符串或结构化对象；不允许为空；非敏感字段。
     * 取值范围：内容必须先脱敏再进入日志；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String RAW_REQUEST_BODY_JSON_MASKED = "requestBodyJsonMasked";

    /**
     * RAW RESPONSE HEADER JSON MASKED，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：JSON 字符串或结构化对象；不允许为空；非敏感字段。
     * 取值范围：内容必须先脱敏再进入日志；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String RAW_RESPONSE_HEADER_JSON_MASKED = "responseHeaderJsonMasked";

    /**
     * RAW RESPONSE BODY JSON MASKED，表示请求体、响应体或消息载荷，日志中只能保留脱敏摘要。
     * <p>
     * 单位：无；格式：JSON 字符串或结构化对象；不允许为空；非敏感字段。
     * 取值范围：内容必须先脱敏再进入日志；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String RAW_RESPONSE_BODY_JSON_MASKED = "responseBodyJsonMasked";

    /**
     * RAW REQUEST URL MASKED，表示当前内部调用、渠道调用或商户通知的目标地址。
     * <p>
     * 单位：无；格式：HTTP/HTTPS URL 或服务路径；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度和协议由调用方校验；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String RAW_REQUEST_URL_MASKED = "requestUrlMasked";

    /**
     * 未换汇交易默认汇率，商户响应和后台日志统一保留 8 位小数。
     */
    private static final BigDecimal DEFAULT_TRANSACTION_RATE = new BigDecimal("1.00000000");

    /**
     * 首次类交易动作序号。
     */
    private static final int INITIAL_OPERATION_SEQUENCE = 1;

    /**
     * 初始版本号。
     */
    private static final int INITIAL_VERSION = 0;

    /**
     * 未删除标识。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 未启用标识。
     */
    private static final int DISABLED = 0;

    /**
     * 已启用标识。
     */
    private static final int ENABLED = 1;

    /**
     * 默认卡交易支付方式。
     */
    private static final String DEFAULT_PAYMENT_METHOD = "BANK_CARD";

    /**
     * 默认交易业务时区。
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /**
     * 未结算状态。
     */
    private static final String NOT_SETTLED = "NOT_SETTLED";

    /**
     * 未对账状态。
     */
    private static final String NOT_RECONCILED = "NOT_RECONCILED";

    /**
     * 未入账状态。
     */
    private static final String NOT_ACCOUNTED = "NOT_ACCOUNTED";

    /**
     * 当前不需要渠道勾兑。
     */
    private static final String CHANNEL_MATCH_NOT_REQUIRED = "NOT_REQUIRED";

    /**
     * 等待渠道查询勾兑确认。
     */
    private static final String CHANNEL_MATCH_PENDING = "PENDING";

    /**
     * 渠道查询或回调已确认最终结果。
     */
    private static final String CHANNEL_MATCH_MATCHED = "MATCHED";

    /**
     * 初始商户通知任务状态。
     */
    private static final String NOTIFY_STATUS_INIT = "INIT";

    /**
     * 默认商户通知最大重试次数。
     */
    private static final int DEFAULT_NOTIFY_MAX_RETRY_COUNT = 2000;

    /**
     * 状态对象：订单。
     */
    private static final String STATUS_OBJECT_ORDER = "ORDER";

    /**
     * 状态对象：动作。
     */
    private static final String STATUS_OBJECT_OPERATION = "OPERATION";

    /**
     * API 触发状态变化。
     */
    private static final String TRIGGER_TYPE_API = "API";

    /**
     * 渠道回调触发状态变化。
     */
    private static final String TRIGGER_TYPE_CHANNEL_CALLBACK = "CHANNEL_CALLBACK";

    /**
     * 状态流转成功。
     */
    private static final String TRANSITION_SUCCESS = "SUCCESS";

    /**
     * 状态流转被忽略。
     */
    private static final String TRANSITION_IGNORED = "IGNORED";

    /**
     * 交易生命周期主单 Mapper。
     */
    private final TransactionOrderMapper transactionOrderMapper;

    /**
     * 交易动作单 Mapper。
     */
    private final TransactionOperationMapper transactionOperationMapper;

    /**
     * 状态历史 Mapper。
     */
    private final TransactionStatusHistoryMapper transactionStatusHistoryMapper;

    /**
     * 渠道请求 Mapper。
     */
    private final TransactionChannelRequestMapper transactionChannelRequestMapper;

    /**
     * 渠道交互日志 Mapper。
     */
    private final TransactionChannelInteractionLogMapper transactionChannelInteractionLogMapper;

    /**
     * 交易流程事件 Mapper。
     */
    private final TransactionFlowEventMapper transactionFlowEventMapper;

    /**
     * 金额变动日志 Mapper。
     */
    private final TransactionAmountChangeLogMapper transactionAmountChangeLogMapper;

    /**
     * 商户通知任务 Mapper。
     */
    private final TransactionMerchantNotificationMapper transactionMerchantNotificationMapper;

    /**
     * 商户 OpenAPI 交互日志 Mapper。
     */
    private final TransactionMerchantApiInteractionLogMapper transactionMerchantApiInteractionLogMapper;

    /**
     * 支付工具摘要 Mapper。
     */
    private final TransactionPaymentMethodInfoMapper transactionPaymentMethodInfoMapper;

    /**
     * 交易分表键解析器。
     */
    private final TransactionShardingKeyParser transactionShardingKeyParser;

    /**
     * 已验证逻辑节点与查询边界配置。
     */
    private final TransactionShardingProperties transactionShardingProperties;

    /**
     * 创建交易事实记录服务默认实现。
     *
     * @param transactionOrderMapper         交易生命周期主单 Mapper
     * @param transactionOperationMapper     交易动作单 Mapper
     * @param transactionStatusHistoryMapper 状态历史 Mapper
     * @param transactionChannelRequestMapper        渠道请求 Mapper
     * @param transactionChannelInteractionLogMapper 渠道交互日志 Mapper
     * @param transactionFlowEventMapper             交易流程事件 Mapper
     * @param transactionAmountChangeLogMapper       金额变动日志 Mapper
     * @param transactionMerchantNotificationMapper  商户通知任务 Mapper
     * @param transactionMerchantApiInteractionLogMapper 商户 OpenAPI 交互日志 Mapper
     * @param transactionPaymentMethodInfoMapper     支付工具摘要 Mapper
     * @param transactionShardingKeyParser           交易分表键解析器
     * @param transactionShardingProperties          已验证逻辑节点配置
     */
    @Autowired
    public DefaultTransactionRecordService(TransactionOrderMapper transactionOrderMapper,
                                           TransactionOperationMapper transactionOperationMapper,
                                           TransactionStatusHistoryMapper transactionStatusHistoryMapper,
                                           TransactionChannelRequestMapper transactionChannelRequestMapper,
                                           TransactionChannelInteractionLogMapper transactionChannelInteractionLogMapper,
                                           TransactionFlowEventMapper transactionFlowEventMapper,
                                           TransactionAmountChangeLogMapper transactionAmountChangeLogMapper,
                                           TransactionMerchantNotificationMapper transactionMerchantNotificationMapper,
                                           TransactionMerchantApiInteractionLogMapper transactionMerchantApiInteractionLogMapper,
                                           TransactionPaymentMethodInfoMapper transactionPaymentMethodInfoMapper,
                                           TransactionShardingKeyParser transactionShardingKeyParser,
                                           TransactionShardingProperties transactionShardingProperties) {
        this.transactionOrderMapper = transactionOrderMapper;
        this.transactionOperationMapper = transactionOperationMapper;
        this.transactionStatusHistoryMapper = transactionStatusHistoryMapper;
        this.transactionChannelRequestMapper = transactionChannelRequestMapper;
        this.transactionChannelInteractionLogMapper = transactionChannelInteractionLogMapper;
        this.transactionFlowEventMapper = transactionFlowEventMapper;
        this.transactionAmountChangeLogMapper = transactionAmountChangeLogMapper;
        this.transactionMerchantNotificationMapper = transactionMerchantNotificationMapper;
        this.transactionMerchantApiInteractionLogMapper = transactionMerchantApiInteractionLogMapper;
        this.transactionPaymentMethodInfoMapper = transactionPaymentMethodInfoMapper;
        this.transactionShardingKeyParser = transactionShardingKeyParser;
        this.transactionShardingProperties = transactionShardingProperties;
    }

    /**
     * 记录首次类交易事实。
     *
     * @param commandDTO       创建交易命令
     * @param routeResultDTO   渠道路由结果
     * @param channelInvokeResultDTO 渠道调用结果
     * @param resultDTO        交易结果
     * @param riskDecisionEnum 内风控决策
     * @param currencyExponent 交易币种默认小数位
     */
    @Override
    public void recordInitialTransaction(PaymentCreateCommandDTO commandDTO,
                                         PaymentRouteResultDTO routeResultDTO,
                                         PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                         PaymentCreateResultDTO resultDTO,
                                         PaymentRiskDecisionEnum riskDecisionEnum,
                                         int currencyExponent) {
        validate(commandDTO, resultDTO);
        LocalDateTime now = LocalDateTime.now();
        ChannelPaymentResponse channelResponse = channelInvokeResultDTO == null ? null : channelInvokeResultDTO.getChannelResponse();
        TransactionOrderDO orderDO = buildOrder(commandDTO, routeResultDTO, channelInvokeResultDTO, channelResponse,
                resultDTO, riskDecisionEnum, currencyExponent, now);
        TransactionOperationDO operationDO = buildOperation(commandDTO, routeResultDTO, channelInvokeResultDTO,
                channelResponse, resultDTO, currencyExponent, now);
        TransactionStatusHistoryDO orderHistoryDO = buildStatusHistory(commandDTO, resultDTO, STATUS_OBJECT_ORDER, commandDTO.getTransactionDateTime(), now);
        TransactionStatusHistoryDO operationHistoryDO = buildStatusHistory(commandDTO, resultDTO, STATUS_OBJECT_OPERATION, commandDTO.getTransactionDateTime(), now);

        int orderRows = transactionOrderMapper.insert(orderDO);
        int operationRows = transactionOperationMapper.insert(operationDO);
        int orderHistoryRows = transactionStatusHistoryMapper.insertLogical(orderHistoryDO);
        int operationHistoryRows = transactionStatusHistoryMapper.insertLogical(operationHistoryDO);
        log.info("event: PAYMENT_LOCAL_PREPARE_COMMIT stage=LOCAL_PREPARE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} paymentMethod: {} currency: {} amount: {} channelCode: {} channelMidId: {} platformStatus: {} logicalTable: {} affectedRows: {} statusBefore: {} statusAfter: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                resultDTO.getTransactionId(),
                resultDTO.getOperationId(),
                resultDTO.getTransactionType(),
                commandDTO.getPaymentMethod(),
                resultDTO.getCurrency(),
                resultDTO.getAmount(),
                routeResultDTO == null ? null : routeResultDTO.getChannelCode(),
                routeResultDTO == null ? null : routeResultDTO.getMidConfigId(),
                resultDTO.getStatus(),
                TRANSACTION_ORDER_TABLE + "," + TRANSACTION_OPERATION_TABLE + "," + TRANSACTION_STATUS_HISTORY_TABLE,
                orderRows + operationRows + orderHistoryRows + operationHistoryRows,
                null,
                resultDTO.getStatus());
        recordChannelAudit(commandDTO, routeResultDTO, channelInvokeResultDTO, resultDTO, now);
        recordPaymentMethodInfo(commandDTO, resultDTO, resultDTO.getOperationId(), resultDTO.getTransactionId(),
                commandDTO.getTransactionDateTime(), now);
        recordFlowEvents(commandDTO, routeResultDTO, channelInvokeResultDTO, resultDTO, riskDecisionEnum, now);
        recordMerchantApiInteraction(commandDTO, resultDTO, now);
        recordMerchantNotificationIfNeeded(commandDTO, resultDTO, now);
    }

    /**
     * 记录首次类交易渠道同步结果。
     *
     * @param commandDTO       创建交易命令
     * @param routeResultDTO   渠道路由结果
     * @param channelInvokeResultDTO 渠道调用结果
     * @param resultDTO        渠道映射后的平台结果
     * @param riskDecisionEnum 内风控决策
     * @param currencyExponent 交易币种默认小数位
     */
    @Override
    public void completeInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                             PaymentRouteResultDTO routeResultDTO,
                                             PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                             PaymentCreateResultDTO resultDTO,
                                             PaymentRiskDecisionEnum riskDecisionEnum,
                                             int currencyExponent) {
        completeInitialChannelResultAndReport(
                commandDTO,
                routeResultDTO,
                channelInvokeResultDTO,
                resultDTO,
                riskDecisionEnum,
                currencyExponent);
    }

    /**
     * 持久化首次交易渠道结果并返回状态是否真正发生变化。
     *
     * <p>先读取已准备的订单和动作事实，再通过 CAS 推进状态并写渠道请求、支付工具、
     * 风控、商户交互和通知记录；重复或迟到结果不能覆盖既有终态。</p>
     *
     * @return true 表示动作状态由本次结果成功推进
     */
    @Override
    public boolean completeInitialChannelResultAndReport(
            PaymentCreateCommandDTO commandDTO,
            PaymentRouteResultDTO routeResultDTO,
            PaymentChannelInvokeResultDTO channelInvokeResultDTO,
            PaymentCreateResultDTO resultDTO,
            PaymentRiskDecisionEnum riskDecisionEnum,
            int currencyExponent) {
        validate(commandDTO, resultDTO);
        if (channelInvokeResultDTO == null || channelInvokeResultDTO.getChannelRequest() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        TransactionOperationDO operationDO = findSourceOperationByTransactionId(
                resultDTO.getTransactionId(), commandDTO.getTransactionDateTime());
        TransactionOrderDO orderDO = findOrder(commandDTO.getTransactionDateTime(), resultDTO.getOperationId());
        if (operationDO == null || orderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(operationDO.getTransactionStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(operationDO.getTransactionStatus())) {
            recordCallbackStatusHistory(operationDO, channelInvokeResultDTO.getRequestId(), resultDTO.getStatus(), TRANSITION_IGNORED,
                    "operation is already terminal or state has changed");
            return false;
        }
        updateInitialChannelRequest(commandDTO, routeResultDTO, channelInvokeResultDTO, resultDTO, now);
        boolean statusChanged = isTerminal(resultDTO)
                ? completeInitialTerminalStatus(operationDO, orderDO, channelInvokeResultDTO, resultDTO)
                : updateInitialNonTerminalStatus(operationDO, channelInvokeResultDTO, resultDTO, now);
        if (!statusChanged) {
            recordCallbackStatusHistory(operationDO, channelInvokeResultDTO.getRequestId(), resultDTO.getStatus(), TRANSITION_IGNORED,
                    "operation is already terminal or state has changed");
            return false;
        }
        insertCallbackStateAndFlow(
                mergeOperationResult(operationDO, channelInvokeResultDTO, resultDTO),
                orderDO,
                channelInvokeResultDTO.getRequestId(),
                resultDTO.getStatus(),
                resultDTO.getFailReasonCode(),
                resultDTO.getFailReasonMessage(),
                true,
                now);
        if (isTerminal(resultDTO)) {
            activateMerchantNotification(operationDO, resultDTO.getStatus(), resultDTO.getFailReasonCode(), resultDTO.getFailReasonMessage(), now);
        }
        updateMerchantApiFinalResult(commandDTO, resultDTO, now);
        return true;
    }

    /**
     * 按原交易业务时间和 operation_id 定位交易生命周期主单。
     *
     * @param transactionDateTime 原交易业务时间
     * @param operationId         平台内部生命周期关联标识
     * @return 交易生命周期主单
     */
    @Override
    public TransactionOrderDO findOrder(LocalDateTime transactionDateTime, String operationId) {
        if (transactionDateTime == null || !StringUtils.hasText(operationId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time and operation_id are required");
        }
        return transactionOrderMapper.selectByOperationId(operationId, transactionDateTime);
    }

    /**
     * 按 operation_id 锁定交易生命周期主单。
     *
     * @param transactionDateTime 原交易业务时间
     * @param operationId         平台内部生命周期关联标识
     * @return 加锁后的交易生命周期主单
     */
    @Override
    public TransactionOrderDO lockOrder(LocalDateTime transactionDateTime, String operationId) {
        if (transactionDateTime == null || !StringUtils.hasText(operationId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time and operation_id are required");
        }
        TransactionOrderDO orderDO = transactionOrderMapper.selectByOperationIdForUpdate(
                operationId, transactionDateTime);
        if (orderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return orderDO;
    }

    /**
     * 按平台当前交易 ID 定位原交易生命周期主单。
     *
     * @param sourceTransactionId 原平台交易 ID
     * @return 原交易生命周期主单
     */
    @Override
    public TransactionOrderDO findSourceOrderByTransactionId(String sourceTransactionId) {
        TransactionOperationDO sourceOperationDO = findSourceOperationByTransactionId(sourceTransactionId);
        if (sourceOperationDO == null || !StringUtils.hasText(sourceOperationDO.getOperationId())) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime orderTransactionDateTime = parseOperationDateTime(sourceOperationDO.getOperationId());
        if (orderTransactionDateTime == null) {
            orderTransactionDateTime = sourceOperationDO.getTransactionDateTime();
        }
        TransactionOrderDO sourceOrderDO = findOrder(orderTransactionDateTime, sourceOperationDO.getOperationId());
        if (sourceOrderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return sourceOrderDO;
    }

    /**
     * 使用显式动作时间和根主单时间定位生命周期，支付热链路不得从平台编号恢复分片时间。
     *
     * @param sourceTransactionId       源平台交易 ID
     * @param sourceTransactionDateTime 源动作单分片时间
     * @param rootTransactionDateTime   生命周期根主单分片时间
     * @return 原交易生命周期主单
     */
    @Override
    public TransactionOrderDO findSourceOrderByTransactionId(String sourceTransactionId,
                                                             LocalDateTime sourceTransactionDateTime,
                                                             LocalDateTime rootTransactionDateTime) {
        TransactionOperationDO sourceOperationDO = findSourceOperationByTransactionId(
                sourceTransactionId, sourceTransactionDateTime);
        if (!StringUtils.hasText(sourceOperationDO.getOperationId()) || rootTransactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderDO sourceOrderDO = findOrder(rootTransactionDateTime, sourceOperationDO.getOperationId());
        if (sourceOrderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return sourceOrderDO;
    }

    /**
     * 按平台当前交易 ID 定位原交易动作单。
     *
     * @param sourceTransactionId 原平台交易 ID
     * @return 原交易动作单
     */
    @Override
    public TransactionOperationDO findSourceOperationByTransactionId(String sourceTransactionId) {
        LocalDateTime sourceTransactionDateTime = parseTransactionDateTime(sourceTransactionId);
        return findSourceOperationByTransactionId(sourceTransactionId, sourceTransactionDateTime);
    }

    /**
     * 使用调用链中冻结的原始分片时间定位动作单，避免交易号时间精度低于数据库时间而误报订单不存在。
     *
     * @param sourceTransactionId       平台交易 ID
     * @param sourceTransactionDateTime 原始交易分片时间
     * @return 原交易动作单
     */
    @Override
    public TransactionOperationDO findSourceOperationByTransactionId(
            String sourceTransactionId,
            LocalDateTime sourceTransactionDateTime) {
        if (sourceTransactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOperationDO sourceOperationDO = transactionOperationMapper.selectByTransactionId(
                sourceTransactionId, sourceTransactionDateTime);
        if (sourceOperationDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return sourceOperationDO;
    }

    /**
     * 按商户订单号查询交易动作单。
     *
     * @param merchantId      平台商户号
     * @param merchantOrderNo 商户订单号
     * @param transactionId   平台交易 ID，可为空
     * @return 交易动作单列表
     */
    @Override
    public List<TransactionOperationDO> findOperationsByMerchantOrder(String merchantId,
                                                                      String merchantOrderNo,
                                                                      String transactionId,
                                                                      LocalDateTime transactionDateTime,
                                                                      LocalDateTime rootTransactionDateTime) {
        if (!StringUtils.hasText(merchantId)
                || !StringUtils.hasText(merchantOrderNo)
                || transactionDateTime == null
                || rootTransactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        if (StringUtils.hasText(transactionId)) {
            LocalDateTime quarterBegin = quarterBegin(transactionDateTime);
            return transactionOperationMapper.selectByMerchantOrder(
                    merchantId, merchantOrderNo, transactionId, quarterBegin, quarterBegin.plusMonths(3));
        }
        LocalDateTime now = LocalDateTime.now();
        return transactionOperationMapper.selectByMerchantOrder(
                merchantId, merchantOrderNo, transactionId, logicalBegin(rootTransactionDateTime), exclusiveEnd(now));
    }

    /**
     * 按商户订单号查询首次起点动作单。
     * <p>
     * 首次起点动作可能分布在历史季度分表，必须按当前分表规则从最小可查表扫描到当前表；调用方只用于创建前互斥校验，
     * 结果集规模受 merchant_id + merchant_order_no + transaction_type 条件约束。
     *
     * @param merchantId      平台商户号
     * @param merchantOrderNo 商户订单号
     * @return 首次起点动作单列表
     */
    @Override
    public List<TransactionOperationDO> findInitialOperationsByMerchantOrder(String merchantId, String merchantOrderNo) {
        if (!StringUtils.hasText(merchantId) || !StringUtils.hasText(merchantOrderNo)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        return transactionOperationMapper.selectInitialByMerchantOrder(
                merchantId, merchantOrderNo, logicalBegin(null), exclusiveEnd(now));
    }

    /**
     * 查询同一授权生命周期下未恢复为明确结果的 Capture 动作。
     *
     * @param merchantId          平台商户号
     * @param operationId         平台内部生命周期关联标识
     * @param sourceTransactionId 原授权或预授权平台交易 ID
     * @param beginTime           查询开始时间
     * @param endTime             查询结束时间
     * @return 未终态 Capture 动作列表
     */
    @Override
    public List<TransactionOperationDO> findNonTerminalCaptures(String merchantId,
                                                                String operationId,
                                                                String sourceTransactionId,
                                                                LocalDateTime beginTime,
                                                                LocalDateTime endTime) {
        if (!StringUtils.hasText(merchantId)
                || !StringUtils.hasText(operationId)
                || !StringUtils.hasText(sourceTransactionId)
                || beginTime == null
                || endTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return transactionOperationMapper.selectNonTerminalCaptures(
                merchantId, operationId, sourceTransactionId, logicalBegin(beginTime), exclusiveEnd(endTime));
    }

    /**
     * 查询同一交易生命周期下未恢复为明确结果的 Refund 动作。
     *
     * @param merchantId  平台商户号
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime   查询开始时间
     * @param endTime     查询结束时间
     * @return 未终态 Refund 动作列表
     */
    @Override
    public List<TransactionOperationDO> findNonTerminalRefunds(String merchantId,
                                                               String operationId,
                                                               LocalDateTime beginTime,
                                                               LocalDateTime endTime) {
        if (!StringUtils.hasText(merchantId)
                || !StringUtils.hasText(operationId)
                || beginTime == null
                || endTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return transactionOperationMapper.selectNonTerminalRefunds(
                merchantId, operationId, logicalBegin(beginTime), exclusiveEnd(endTime));
    }

    /**
     * 查询同一交易生命周期下未恢复为明确结果的 Void 动作。
     *
     * @param merchantId  平台商户号
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime   查询开始时间
     * @param endTime     查询结束时间
     * @return 未终态 Void 动作列表
     */
    @Override
    public List<TransactionOperationDO> findNonTerminalVoids(String merchantId,
                                                             String operationId,
                                                             LocalDateTime beginTime,
                                                             LocalDateTime endTime) {
        if (!StringUtils.hasText(merchantId)
                || !StringUtils.hasText(operationId)
                || beginTime == null
                || endTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return transactionOperationMapper.selectNonTerminalVoids(
                merchantId, operationId, logicalBegin(beginTime), exclusiveEnd(endTime));
    }

    /**
     * 查询同一授权生命周期下未恢复为明确结果的 Incremental Authorization 动作。
     *
     * @param merchantId  平台商户号
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime   查询开始时间
     * @param endTime     查询结束时间
     * @return 未终态 Incremental Authorization 动作列表
     */
    @Override
    public List<TransactionOperationDO> findNonTerminalIncrementalAuthorizations(String merchantId,
                                                                                 String operationId,
                                                                                 LocalDateTime beginTime,
                                                                                 LocalDateTime endTime) {
        if (!StringUtils.hasText(merchantId)
                || !StringUtils.hasText(operationId)
                || beginTime == null
                || endTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return transactionOperationMapper.selectNonTerminalIncrementalAuthorizations(
                merchantId, operationId, logicalBegin(beginTime), exclusiveEnd(endTime));
    }

    /**
     * 按渠道订单号和渠道交易 ID 定位动作单。
     *
     * @param channelOrderNo       渠道订单号
     * @param channelTransactionId 渠道交易 ID
     * @return 动作单
     */
    @Override
    public TransactionOperationDO findOperationByChannelTransaction(String channelOrderNo, String channelTransactionId) {
        if (!StringUtils.hasText(channelOrderNo) || !StringUtils.hasText(channelTransactionId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "channel_order_no and channel_transaction_id are required");
        }
        LocalDateTime sourceTransactionDateTime = parseTransactionDateTime(channelOrderNo);
        if (sourceTransactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        return transactionOperationMapper.selectByChannelTransaction(
                channelOrderNo, channelTransactionId, sourceTransactionDateTime, exclusiveEnd(now));
    }

    /**
     * 查询待渠道查询确认的动作单。
     *
     * @param transactionDateTime 交易业务时间，用于 ShardingSphere 精确定位季度
     * @param channelCode 渠道编码，可为空
     * @param now 当前时间
     * @param limit 最大查询数量
     * @return 待勾兑动作单列表
     */
    @Override
    public List<TransactionOperationDO> listPendingChannelMatch(LocalDateTime transactionDateTime,
                                                                String channelCode,
                                                                LocalDateTime now,
                                                                int limit) {
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transactionDateTime is required");
        }
        LocalDateTime beginTime = quarterBegin(transactionDateTime);
        return transactionOperationMapper.selectPendingChannelMatch(
                channelCode,
                beginTime,
                beginTime.plusMonths(3),
                now == null ? LocalDateTime.now() : now,
                limit);
    }

    /**
     * 定位主动查询需要关联的原资金动作渠道请求记录。
     * <p>
     * 优先使用动作单最近保存的 request_id；缺失时用平台 transaction_id + channel_code 找原资金动作请求；
     * 再缺失时才使用完整渠道身份查找。该方法只读取既有记录，不构造新的渠道身份。
     *
     * @param operationDO 待恢复交易动作单
     * @return 原资金动作渠道请求记录，不存在时返回 null
     */
    @Override
    public TransactionChannelRequestDO findOriginalChannelRequestForQuery(TransactionOperationDO operationDO) {
        if (operationDO == null || operationDO.getTransactionDateTime() == null) {
            return null;
        }
        if (StringUtils.hasText(operationDO.getLastChannelMatchRequestId())) {
            TransactionChannelRequestDO requestDO = transactionChannelRequestMapper.selectByRequestId(
                    operationDO.getLastChannelMatchRequestId(), operationDO.getTransactionDateTime());
            if (requestDO != null) {
                return requestDO;
            }
        }
        if (StringUtils.hasText(operationDO.getTransactionId()) && StringUtils.hasText(operationDO.getChannelCode())) {
            TransactionChannelRequestDO requestDO = transactionChannelRequestMapper.selectOriginalByTransaction(
                    operationDO.getTransactionId(), operationDO.getChannelCode(), operationDO.getTransactionDateTime());
            if (requestDO != null) {
                return requestDO;
            }
        }
        if (StringUtils.hasText(operationDO.getChannelCode())
                && StringUtils.hasText(operationDO.getChannelOrderNo())
                && StringUtils.hasText(operationDO.getChannelTransactionId())) {
            LocalDateTime beginTime = quarterBegin(operationDO.getTransactionDateTime());
            return transactionChannelRequestMapper.selectByChannelTransaction(
                    operationDO.getChannelCode(),
                    operationDO.getChannelOrderNo(),
                    operationDO.getChannelTransactionId(),
                    beginTime,
                    beginTime.plusMonths(3));
        }
        return null;
    }

    /**
     * 记录后续交易动作事实，并在渠道同步成功时推进主单金额汇总。
     *
     * @param recordDTO 后续交易动作记录上下文
     */
    @Override
    public void recordFollowUpTransaction(TransactionFollowUpRecordDTO recordDTO) {
        validateFollowUpRecord(recordDTO);
        LocalDateTime now = LocalDateTime.now();
        PaymentCreateCommandDTO commandDTO = recordDTO.getCommandDTO();
        PaymentCreateResultDTO resultDTO = recordDTO.getResultDTO();
        TransactionOrderDO sourceOrderDO = recordDTO.getSourceOrderDO();
        LocalDateTime actionTransactionDateTime = commandDTO.getTransactionDateTime();
        TransactionOperationDO operationDO = buildFollowUpOperation(recordDTO, now,
                countExistingOperations(sourceOrderDO.getOperationId(), sourceOrderDO.getTransactionDateTime(), actionTransactionDateTime) + 1);
        int operationRows = transactionOperationMapper.insert(operationDO);
        int orderRows = 0;
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())) {
            orderRows = updateSourceOrderAmount(sourceOrderDO, resultDTO);
            recordAmountChange(recordDTO, now);
        }
        TransactionStatusHistoryDO orderHistory = buildStatusHistory(
                commandDTO, resultDTO, STATUS_OBJECT_ORDER, actionTransactionDateTime, now);
        TransactionStatusHistoryDO operationHistory = buildStatusHistory(
                commandDTO, resultDTO, STATUS_OBJECT_OPERATION, actionTransactionDateTime, now);
        int orderHistoryRows = transactionStatusHistoryMapper.insertLogical(orderHistory);
        int operationHistoryRows = transactionStatusHistoryMapper.insertLogical(operationHistory);
        log.info("event: PAYMENT_LOCAL_PREPARE_COMMIT stage=LOCAL_PREPARE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} sourceTransactionId: {} transactionType: {} paymentMethod: {} currency: {} amount: {} channelCode: {} channelMidId: {} platformStatus: {} logicalTable: {} affectedRows: {} statusBefore: {} statusAfter: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                resultDTO.getTransactionId(),
                sourceOrderDO.getOperationId(),
                resultDTO.getSourceTransactionId(),
                resultDTO.getTransactionType(),
                commandDTO.getPaymentMethod(),
                resultDTO.getCurrency(),
                resultDTO.getAmount(),
                recordDTO.getRouteResultDTO() == null ? null : recordDTO.getRouteResultDTO().getChannelCode(),
                recordDTO.getRouteResultDTO() == null ? null : recordDTO.getRouteResultDTO().getMidConfigId(),
                resultDTO.getStatus(),
                TRANSACTION_OPERATION_TABLE + "," + TRANSACTION_ORDER_TABLE + "," + TRANSACTION_STATUS_HISTORY_TABLE,
                operationRows + orderRows + orderHistoryRows + operationHistoryRows,
                sourceOrderDO.getTransactionStatus(),
                resultDTO.getStatus());
        recordChannelAudit(commandDTO, recordDTO.getRouteResultDTO(), recordDTO.getChannelInvokeResultDTO(), resultDTO, now);
        recordPaymentMethodInfo(commandDTO, resultDTO, sourceOrderDO.getOperationId(), resultDTO.getTransactionId(),
                actionTransactionDateTime, now, sourceOrderDO);
        recordFlowEvents(commandDTO, recordDTO.getRouteResultDTO(), recordDTO.getChannelInvokeResultDTO(), resultDTO,
                resolveFollowUpRiskDecision(recordDTO), now);
        recordMerchantApiInteraction(commandDTO, resultDTO, now);
        recordMerchantNotificationIfNeeded(commandDTO, resultDTO, now);
    }

    /**
     * 持久化请款渠道结果并按 CAS 更新原订单累计金额。
     *
     * @return true 表示请款动作状态成功推进；false 表示终态或并发冲突
     */
    @Override
    public boolean completeCaptureChannelResult(TransactionOperationDO operationDO,
                                                TransactionOrderDO sourceOrderDO,
                                                PaymentCreateCommandDTO commandDTO,
                                                PaymentRouteResultDTO routeResultDTO,
                                                PaymentChannelInvokeResultDTO invokeResultDTO,
                                                PaymentCreateResultDTO resultDTO,
                                                int currencyExponent) {
        if (operationDO == null || sourceOrderDO == null || commandDTO == null || resultDTO == null
                || invokeResultDTO == null || invokeResultDTO.getChannelRequest() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        updateInitialChannelRequest(commandDTO, routeResultDTO, invokeResultDTO, resultDTO, now);
        boolean statusChanged = isTerminal(resultDTO)
                ? completeCaptureTerminalStatus(operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent, now)
                : updateCaptureNonTerminalStatus(operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent, now);
        if (!statusChanged) {
            recordCallbackStatusHistory(operationDO, invokeResultDTO.getRequestId(), resultDTO.getStatus(), TRANSITION_IGNORED,
                    "operation is already terminal or state has changed");
        }
        return statusChanged;
    }

    /**
     * 持久化退款渠道结果并按 CAS 更新原订单累计退款金额。
     *
     * @return true 表示退款动作状态成功推进；false 表示终态或并发冲突
     */
    @Override
    public boolean completeRefundChannelResult(TransactionOperationDO operationDO,
                                               TransactionOrderDO sourceOrderDO,
                                               PaymentCreateCommandDTO commandDTO,
                                               PaymentRouteResultDTO routeResultDTO,
                                               PaymentChannelInvokeResultDTO invokeResultDTO,
                                               PaymentCreateResultDTO resultDTO,
                                               int currencyExponent) {
        if (operationDO == null || sourceOrderDO == null || commandDTO == null || resultDTO == null
                || invokeResultDTO == null || invokeResultDTO.getChannelRequest() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        updateInitialChannelRequest(commandDTO, routeResultDTO, invokeResultDTO, resultDTO, now);
        boolean statusChanged = isTerminal(resultDTO)
                ? completeCaptureTerminalStatus(operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent, now)
                : updateCaptureNonTerminalStatus(operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent, now);
        if (!statusChanged) {
            recordCallbackStatusHistory(operationDO, invokeResultDTO.getRequestId(), resultDTO.getStatus(), TRANSITION_IGNORED,
                    "operation is already terminal or state has changed");
        }
        return statusChanged;
    }

    /**
     * 持久化撤销渠道结果并按 CAS 更新原订单状态及累计字段。
     *
     * @return true 表示撤销动作状态成功推进；false 表示终态或并发冲突
     */
    @Override
    public boolean completeVoidChannelResult(TransactionOperationDO operationDO,
                                             TransactionOrderDO sourceOrderDO,
                                             PaymentCreateCommandDTO commandDTO,
                                             PaymentRouteResultDTO routeResultDTO,
                                             PaymentChannelInvokeResultDTO invokeResultDTO,
                                             PaymentCreateResultDTO resultDTO,
                                             int currencyExponent) {
        if (operationDO == null || sourceOrderDO == null || commandDTO == null || resultDTO == null
                || invokeResultDTO == null || invokeResultDTO.getChannelRequest() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        updateInitialChannelRequest(commandDTO, routeResultDTO, invokeResultDTO, resultDTO, now);
        boolean statusChanged = isTerminal(resultDTO)
                ? completeCaptureTerminalStatus(operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent, now)
                : updateCaptureNonTerminalStatus(operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent, now);
        if (!statusChanged) {
            recordCallbackStatusHistory(operationDO, invokeResultDTO.getRequestId(), resultDTO.getStatus(), TRANSITION_IGNORED,
                    "operation is already terminal or state has changed");
        }
        return statusChanged;
    }

    /**
     * 按增量授权渠道返回结果更新原授权交易累计授权金额和动作单状态。
     * <p>
     * 前置条件：调用方已经完成增量授权请求落库并拿到渠道同步响应。
     * 该方法复用授权类终态推进逻辑；终态结果会更新渠道请求、动作单状态、状态历史和授权累计金额，
     * 非终态结果只记录当前渠道状态，状态已终结或版本冲突时记录忽略历史。
     * </p>
     * @param operationDO 当前增量授权动作单
     * @param sourceOrderDO 原授权交易主单
     * @param commandDTO 增量授权命令，提供商户、金额、币种和原交易号
     * @param routeResultDTO 渠道路由结果，提供渠道和 MID 配置
     * @param invokeResultDTO 渠道调用结果，提供渠道请求记录和响应摘要
     * @param resultDTO 平台统一交易结果，提供目标状态和失败码
     * @param currencyExponent 币种小数位，用于累计金额变更校验
     * @return true 表示状态或金额累计实际发生变更
     */
    @Override
    public boolean completeIncrementalAuthorizationChannelResult(TransactionOperationDO operationDO,
                                                                 TransactionOrderDO sourceOrderDO,
                                                                 PaymentCreateCommandDTO commandDTO,
                                                                 PaymentRouteResultDTO routeResultDTO,
                                                                 PaymentChannelInvokeResultDTO invokeResultDTO,
                                                                 PaymentCreateResultDTO resultDTO,
                                                                 int currencyExponent) {
        if (operationDO == null || sourceOrderDO == null || commandDTO == null || resultDTO == null
                || invokeResultDTO == null || invokeResultDTO.getChannelRequest() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        updateInitialChannelRequest(commandDTO, routeResultDTO, invokeResultDTO, resultDTO, now);
        boolean statusChanged = isTerminal(resultDTO)
                ? completeCaptureTerminalStatus(operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent, now)
                : updateCaptureNonTerminalStatus(operationDO, sourceOrderDO, commandDTO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent, now);
        if (!statusChanged) {
            recordCallbackStatusHistory(operationDO, invokeResultDTO.getRequestId(), resultDTO.getStatus(), TRANSITION_IGNORED,
                    "operation is already terminal or state has changed");
        }
        return statusChanged;
    }

    /**
     * 按渠道回调结果推进交易动作终态，并补齐状态历史、流程事件和商户通知激活。
     *
     * @param operationDO             被推进的交易动作单
     * @param orderDO                 所属交易生命周期主单
     * @param callbackId              渠道回调业务 ID
     * @param targetTransactionStatus 目标交易状态
     * @param failReasonCode          失败原因码
     * @param failReasonMessage       后台可见失败原因
     * @param channelStatus           渠道原始状态
     * @param channelResponseCode     渠道响应码
     * @param channelResponseMessage  渠道响应描述
     * @return true 表示推进成功
     */
    @Override
    public boolean completeByChannelCallback(TransactionOperationDO operationDO,
                                             TransactionOrderDO orderDO,
                                             String callbackId,
                                             String targetTransactionStatus,
                                             String failReasonCode,
                                             String failReasonMessage,
                                             String channelStatus,
                                             String channelResponseCode,
                                             String channelResponseMessage) {
        if (operationDO == null || orderDO == null || !StringUtils.hasText(targetTransactionStatus)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(operationDO.getTransactionStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(operationDO.getTransactionStatus())) {
            recordCallbackStatusHistory(operationDO, callbackId, targetTransactionStatus, TRANSITION_IGNORED,
                    "operation is already terminal");
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        int operationUpdated = transactionOperationMapper.completeStatus(
                operationDO.getId(),
                operationDO.getTransactionDateTime(),
                operationDO.getVersion(),
                targetTransactionStatus,
                "FINISHED",
                failReasonCode,
                failReasonMessage,
                channelStatus,
                channelResponseCode,
                channelResponseMessage,
                null,
                null,
                null,
                CHANNEL_MATCH_MATCHED);
        if (operationUpdated != 1) {
            recordCallbackStatusHistory(operationDO, callbackId, targetTransactionStatus, TRANSITION_IGNORED,
                    "operation state has changed");
            return false;
        }
        boolean success = PaymentTransactionStatusEnum.SUCCESS.getCode().equals(targetTransactionStatus);
        boolean orderUpdated = updateOrderByCallback(orderDO, operationDO, success,
                targetTransactionStatus, failReasonCode, failReasonMessage);
        log.info("event: PAYMENT_CALLBACK_DB_UPDATE stage=CALLBACK_RESULT traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} sourceTransactionId: {} transactionType: {} currency: {} amount: {} channelCode: {} channelTransactionId: {} platformStatus: {} channelResultCode: {} logicalTable: {} affectedRows: {} statusBefore: {} statusAfter: {}",
                TraceContext.getTraceId(),
                operationDO.getMerchantId(),
                operationDO.getMerchantOrderNo(),
                operationDO.getTransactionId(),
                operationDO.getOperationId(),
                operationDO.getSourceTransactionId(),
                operationDO.getTransactionType(),
                operationDO.getTransactionCurrency(),
                operationDO.getTransactionAmount(),
                operationDO.getChannelCode(),
                operationDO.getChannelTransactionId(),
                targetTransactionStatus,
                channelResponseCode,
                TRANSACTION_OPERATION_TABLE + "," + TRANSACTION_ORDER_TABLE,
                operationUpdated + (orderUpdated ? 1 : 0),
                operationDO.getTransactionStatus(),
                targetTransactionStatus);
        if (success && orderUpdated && StringUtils.hasText(operationDO.getSourceTransactionId())) {
            recordAmountChange(operationDO, orderDO, now);
        }
        insertCallbackStateAndFlow(operationDO, orderDO, callbackId, targetTransactionStatus,
                failReasonCode, failReasonMessage, orderUpdated, now);
        if (orderUpdated) {
            activateMerchantNotification(operationDO, targetTransactionStatus, failReasonCode, failReasonMessage, now);
        }
        return true;
    }

    /**
     * 更新渠道查询勾兑摘要。
     *
     * @param operationDO 被勾兑的动作单
     * @param matchStatus 勾兑状态
     * @param matchResult 勾兑结果摘要
     * @param requestId 最近一次渠道查询请求 ID
     * @param matchTime 最近一次查询时间
     * @param nextMatchTime 下一次查询时间
     * @param failReason 失败原因
     * @return true 表示更新成功
     */
    @Override
    public boolean updateChannelMatch(TransactionOperationDO operationDO,
                                      String matchStatus,
                                      String matchResult,
                                      String requestId,
                                      LocalDateTime matchTime,
                                      LocalDateTime nextMatchTime,
                                      String failReason) {
        if (operationDO == null || operationDO.getId() == null || operationDO.getTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime actualMatchTime = matchTime == null ? LocalDateTime.now() : matchTime;
        int updated = transactionOperationMapper.updateChannelMatch(
                operationDO.getId(),
                operationDO.getTransactionDateTime(),
                operationDO.getVersion(),
                matchStatus,
                safeLength(matchResult, 256),
                requestId,
                actualMatchTime,
                nextMatchTime,
                safeLength(failReason, 512));
        return updated == 1;
    }

    /**
     * 根据主动查询结果回写原资金动作渠道请求。
     *
     * @param operationDO 待恢复交易动作单
     * @param originalRequestDO 原资金动作渠道请求记录
     * @param invokeResultDTO 渠道查询调用结果
     * @param platformResultCode 平台解析结果
     * @param failReason 平台失败或待恢复原因
     * @return true 表示更新成功或没有可更新原请求
     */
    @Override
    public boolean updateOriginalChannelRequestByQuery(TransactionOperationDO operationDO,
                                                       TransactionChannelRequestDO originalRequestDO,
                                                       PaymentChannelInvokeResultDTO invokeResultDTO,
                                                       String platformResultCode,
                                                       String failReason) {
        if (operationDO == null || operationDO.getTransactionDateTime() == null || originalRequestDO == null
                || !StringUtils.hasText(originalRequestDO.getRequestId())) {
            return true;
        }
        ChannelPaymentResponse response = invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse();
        Map<String, String> rawResponse = response == null ? Map.of() : response.getRawResponse();
        String requestStatus = invokeResultDTO == null ? originalRequestDO.getRequestStatus() : invokeResultDTO.getRequestStatus();
        int updated = transactionChannelRequestMapper.updateStatusLogical(
                originalRequestDO.getRequestId(),
                operationDO.getTransactionDateTime(),
                originalRequestDO.getVersion(),
                List.of("INIT", "SENT", "TIMEOUT", "FAILED"),
                requestStatus,
                rawResponse.get("result"),
                rawResponse.get("gatewayCode"),
                rawResponse.get("acquirerCode"),
                rawResponse.get("acquirerMessage"),
                response == null ? originalRequestDO.getChannelStatus() : response.getRawChannelStatus(),
                PaymentTransactionStatusEnum.SUCCESS.getCode().equals(platformResultCode) ? 1 : 0,
                platformResultCode,
                firstText(failReason,
                        invokeResultDTO == null ? null : invokeResultDTO.getExceptionMessage(),
                        originalRequestDO.getPlatformFailReason()),
                invokeResultDTO == null || invokeResultDTO.getResponseTime() == null
                        ? LocalDateTime.now() : invokeResultDTO.getResponseTime(),
                invokeResultDTO == null ? null : invokeResultDTO.getDurationMillis());
        return updated == 1 || !isOriginalRequestResultConflict(originalRequestDO, platformResultCode);
    }

    /**
     * 回写商户 OpenAPI 响应加密后的密文摘要。
     *
     * @param commandDTO 响应日志回写命令
     * @return true 表示命中并更新日志，false 表示未找到对应记录
     */
    @Override
    public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateCommandDTO commandDTO) {
        if (commandDTO == null
                || !StringUtils.hasText(commandDTO.getTransactionId())
                || commandDTO.getTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime transactionDateTime = commandDTO.getTransactionDateTime();
        int updated = transactionMerchantApiInteractionLogMapper.updateResponseCipherLogical(
                commandDTO.getTransactionId(),
                transactionDateTime,
                commandDTO.getRequestId(),
                safeLength(commandDTO.getResponsePlainJsonMasked(), 16_000),
                commandDTO.getResponseCipherDigest(),
                commandDTO.getResponseCipherMasked(),
                commandDTO.getResponseTime() == null ? LocalDateTime.now() : commandDTO.getResponseTime());
        log.info("event: PAYMENT_MERCHANT_API_LOG_RESPONSE_UPDATED stage=RESPONSE_LOG traceId: {} transactionId: {} requestId: {} logicalTable: {} affectedRows: {} responseCipherDigest: {} responseCipherMasked: {} responsePlainLength: {}",
                TraceContext.getTraceId(),
                commandDTO.getTransactionId(),
                commandDTO.getRequestId(),
                TRANSACTION_MERCHANT_API_INTERACTION_LOG_TABLE,
                updated,
                commandDTO.getResponseCipherDigest(),
                commandDTO.getResponseCipherMasked(),
                commandDTO.getResponsePlainJsonMasked() == null ? 0 : commandDTO.getResponsePlainJsonMasked().length());
        return updated > 0;
    }

    /**
     * 构造首次交易订单事实记录。
     *
     * <p>金额按币种精度统一填充，渠道失败原文先归一化和脱敏；交易业务时间同时保存平台时区和 UTC。</p>
     *
     * @return 待持久化交易订单
     */
    private TransactionOrderDO buildOrder(PaymentCreateCommandDTO commandDTO,
                                          PaymentRouteResultDTO routeResultDTO,
                                          PaymentChannelInvokeResultDTO invokeResultDTO,
                                          ChannelPaymentResponse channelResponse,
                                          PaymentCreateResultDTO resultDTO,
                                          PaymentRiskDecisionEnum riskDecisionEnum,
                                          int currencyExponent,
                                          LocalDateTime now) {
        TransactionOrderDO orderDO = new TransactionOrderDO();
        orderDO.setOperationId(resultDTO.getOperationId());
        orderDO.setRootTransactionId(resultDTO.getTransactionId());
        orderDO.setLatestTransactionId(resultDTO.getTransactionId());
        orderDO.setMerchantId(commandDTO.getMerchantId());
        orderDO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        orderDO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        orderDO.setSourceTransactionId(commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getSourceTransactionId());
        orderDO.setPaymentMethod(resolvePaymentMethod(commandDTO));
        orderDO.setPaymentBrand(resultDTO.getPaymentBrand());
        orderDO.setTransactionType(resultDTO.getTransactionType());
        orderDO.setTransactionStatus(resultDTO.getStatus());
        orderDO.setProcessStage(resultDTO.getProcessStage());
        orderDO.setPendingReasonCode(resultDTO.getPendingReasonCode());
        orderDO.setFailReasonCode(resultDTO.getFailReasonCode());
        orderDO.setFailReasonMessage(resolveFailureMessage(resultDTO, channelResponse));
        orderDO.setMerchantVisibleMessage(merchantVisibleFailureMessage(resultDTO.getStatus(), resultDTO.getFailReasonCode()));
        orderDO.setPayerVisibleMessage(merchantVisibleFailureMessage(resultDTO.getStatus(), resultDTO.getFailReasonCode()));
        fillAmountFields(orderDO, commandDTO, resultDTO, currencyExponent);
        fillOrderRouteFields(orderDO, routeResultDTO, channelResponse, riskDecisionEnum);
        orderDO.setInternalRiskRecordNo(commandDTO.getRiskRecordNo());
        fillOrderRouteFieldsFromRequest(orderDO, invokeResultDTO);
        orderDO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        orderDO.setTransactionUtcTime(toUtcTime(commandDTO.getTransactionDateTime(), DEFAULT_TIME_ZONE));
        orderDO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        orderDO.setTransactionTimezoneOffset("+08:00");
        orderDO.setLastStatusTime(now);
        orderDO.setVersion(INITIAL_VERSION);
        orderDO.setDeleted(NOT_DELETED);
        orderDO.setCreateTime(now);
        orderDO.setUpdateTime(now);
        return orderDO;
    }

    /**
     * 构造首次交易动作事实记录。
     *
     * <p>动作记录承载本次状态、金额和渠道关联，不保存完整 PAN、CVV、密钥或渠道敏感原文。</p>
     *
     * @return 待持久化交易动作
     */
    private TransactionOperationDO buildOperation(PaymentCreateCommandDTO commandDTO,
                                                  PaymentRouteResultDTO routeResultDTO,
                                                  PaymentChannelInvokeResultDTO invokeResultDTO,
                                                  ChannelPaymentResponse channelResponse,
                                                  PaymentCreateResultDTO resultDTO,
                                                  int currencyExponent,
                                                  LocalDateTime now) {
        TransactionOperationDO operationDO = new TransactionOperationDO();
        operationDO.setOperationId(resultDTO.getOperationId());
        operationDO.setTransactionId(resultDTO.getTransactionId());
        operationDO.setSourceTransactionId(commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getSourceTransactionId());
        operationDO.setMerchantId(commandDTO.getMerchantId());
        operationDO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        operationDO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        operationDO.setMerchantOperationNo(commandDTO.getMerchantOrderNo());
        operationDO.setOperationSequence(INITIAL_OPERATION_SEQUENCE);
        operationDO.setTransactionType(resultDTO.getTransactionType());
        operationDO.setTransactionStatus(resultDTO.getStatus());
        operationDO.setProcessStage(resultDTO.getProcessStage());
        operationDO.setPendingReasonCode(resultDTO.getPendingReasonCode());
        operationDO.setFailReasonCode(resultDTO.getFailReasonCode());
        operationDO.setFailReasonMessage(resolveFailureMessage(resultDTO, channelResponse));
        fillAmountFields(operationDO, commandDTO, resultDTO, currencyExponent);
        fillOperationRouteFields(operationDO, routeResultDTO, channelResponse);
        fillOperationRouteFieldsFromRequest(operationDO, invokeResultDTO);
        operationDO.setSettlementStatus(NOT_SETTLED);
        operationDO.setReconciliationStatus(NOT_RECONCILED);
        operationDO.setAccountingStatus(NOT_ACCOUNTED);
        operationDO.setChannelMatchStatus(resolveChannelMatchStatus(resultDTO));
        operationDO.setChannelMatchCount(0);
        operationDO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        operationDO.setTransactionUtcTime(toUtcTime(commandDTO.getTransactionDateTime(), DEFAULT_TIME_ZONE));
        operationDO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        operationDO.setOperationTime(now);
        operationDO.setCompleteTime(isTerminal(resultDTO) ? now : null);
        operationDO.setVersion(INITIAL_VERSION);
        operationDO.setDeleted(NOT_DELETED);
        operationDO.setCreateTime(now);
        operationDO.setUpdateTime(now);
        return operationDO;
    }

/**
 * 构造followup动作对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param recordDTO record DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
 * @param operationSequence operation Sequence 输入值，参与 动作sequence 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
 */
    private TransactionOperationDO buildFollowUpOperation(TransactionFollowUpRecordDTO recordDTO,
                                                          LocalDateTime now,
                                                          int operationSequence) {
        TransactionOrderDO sourceOrderDO = recordDTO.getSourceOrderDO();
        PaymentCreateCommandDTO commandDTO = recordDTO.getCommandDTO();
        PaymentCreateResultDTO resultDTO = recordDTO.getResultDTO();
        TransactionOperationDO operationDO = new TransactionOperationDO();
        operationDO.setOperationId(sourceOrderDO.getOperationId());
        operationDO.setTransactionId(resultDTO.getTransactionId());
        operationDO.setSourceTransactionId(resultDTO.getSourceTransactionId());
        operationDO.setSourceOperationId(sourceOrderDO.getOperationId());
        operationDO.setMerchantId(commandDTO.getMerchantId());
        operationDO.setMerchantOrderNo(sourceOrderDO.getMerchantOrderNo());
        operationDO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        operationDO.setMerchantOperationNo(commandDTO.getMerchantOrderId());
        operationDO.setOperationSequence(operationSequence);
        operationDO.setTransactionType(resultDTO.getTransactionType());
        operationDO.setTransactionStatus(resultDTO.getStatus());
        operationDO.setProcessStage(resultDTO.getProcessStage());
        operationDO.setPendingReasonCode(resultDTO.getPendingReasonCode());
        operationDO.setFailReasonCode(resultDTO.getFailReasonCode());
        operationDO.setFailReasonMessage(recordDTO.getChannelResponse() == null
                ? resultDTO.getFailReasonCode()
                : recordDTO.getChannelResponse().getChannelResponseMessage());
        fillFollowUpAmountFields(operationDO, sourceOrderDO, commandDTO, resultDTO, recordDTO.getCurrencyExponent());
        fillOperationRouteFields(operationDO, recordDTO.getRouteResultDTO(), recordDTO.getChannelResponse());
        fillOperationRouteFieldsFromRequest(operationDO, recordDTO.getChannelInvokeResultDTO());
        operationDO.setSettlementStatus(NOT_SETTLED);
        operationDO.setReconciliationStatus(NOT_RECONCILED);
        operationDO.setAccountingStatus(NOT_ACCOUNTED);
        operationDO.setChannelMatchStatus(resolveChannelMatchStatus(resultDTO));
        operationDO.setChannelMatchCount(0);
        operationDO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        operationDO.setTransactionUtcTime(toUtcTime(commandDTO.getTransactionDateTime(), DEFAULT_TIME_ZONE));
        operationDO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        operationDO.setOperationTime(now);
        operationDO.setCompleteTime(isTerminal(resultDTO) ? now : null);
        operationDO.setVersion(INITIAL_VERSION);
        operationDO.setDeleted(NOT_DELETED);
        operationDO.setCreateTime(now);
        operationDO.setUpdateTime(now);
        return operationDO;
    }

/**
 * 构造动作routefieldsfrom请求对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
 * @param invokeResultDTO invoke Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 */
    private void fillOperationRouteFieldsFromRequest(TransactionOperationDO operationDO,
                                                     PaymentChannelInvokeResultDTO invokeResultDTO) {
        if (operationDO == null || invokeResultDTO == null || invokeResultDTO.getChannelRequest() == null) {
            return;
        }
        operationDO.setChannelCode(firstText(operationDO.getChannelCode(), invokeResultDTO.getChannelRequest().getChannelCode()));
        operationDO.setChannelOrderNo(firstText(operationDO.getChannelOrderNo(), invokeResultDTO.getChannelRequest().getChannelOrderNo()));
        operationDO.setChannelTransactionId(firstText(operationDO.getChannelTransactionId(), invokeResultDTO.getChannelRequest().getChannelTransactionId()));
    }

/**
 * 构造订单routefieldsfrom请求对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param orderDO order DO 输入值，参与 订单do 的查询、校验、转换、写入或日志摘要
 * @param invokeResultDTO invoke Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 */
    private void fillOrderRouteFieldsFromRequest(TransactionOrderDO orderDO,
                                                 PaymentChannelInvokeResultDTO invokeResultDTO) {
        if (orderDO == null || invokeResultDTO == null || invokeResultDTO.getChannelRequest() == null) {
            return;
        }
        orderDO.setChannelCode(firstText(orderDO.getChannelCode(), invokeResultDTO.getChannelRequest().getChannelCode()));
        orderDO.setChannelOrderNo(firstText(orderDO.getChannelOrderNo(), invokeResultDTO.getChannelRequest().getChannelOrderNo()));
    }

/**
 * 构造状态history对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param statusObject 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
 */
    private TransactionStatusHistoryDO buildStatusHistory(PaymentCreateCommandDTO commandDTO,
                                                          PaymentCreateResultDTO resultDTO,
                                                          String statusObject,
                                                          LocalDateTime transactionDateTime,
                                                          LocalDateTime now) {
        TransactionStatusHistoryDO historyDO = new TransactionStatusHistoryDO();
        historyDO.setStatusHistoryId(PaymentOrderNoGenerator.nextOrderNo(STATUS_HISTORY_PREFIX, transactionDateTime));
        historyDO.setTransactionId(resultDTO.getTransactionId());
        historyDO.setOperationId(resultDTO.getOperationId());
        historyDO.setStatusObject(statusObject);
        historyDO.setToStatus(resultDTO.getStatus());
        historyDO.setTriggerType(TRIGGER_TYPE_API);
        historyDO.setTriggerId(resultDTO.getTransactionId());
        historyDO.setTransitionResult(TRANSITION_SUCCESS);
        historyDO.setVersionBefore(null);
        historyDO.setVersionAfter(INITIAL_VERSION);
        historyDO.setStatusTime(now);
        historyDO.setTransactionDateTime(transactionDateTime);
        historyDO.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        historyDO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        historyDO.setCreateTime(now);
        return historyDO;
    }

/**
 * 构造金额fields对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param orderDO order DO 输入值，参与 订单do 的查询、校验、转换、写入或日志摘要
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 */
    private void fillAmountFields(TransactionOrderDO orderDO,
                                  PaymentCreateCommandDTO commandDTO,
                                  PaymentCreateResultDTO resultDTO,
                                  int currencyExponent) {
        BigDecimal zero = BigDecimal.ZERO;
        boolean success = PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus());
        BigDecimal transactionAmount = resolveTransactionAmount(commandDTO);
        String transactionCurrency = resolveTransactionCurrency(commandDTO);
        orderDO.setLabelCurrency(resolveLabelCurrency(commandDTO));
        orderDO.setLabelAmount(resolveLabelAmount(commandDTO));
        orderDO.setTransactionCurrency(transactionCurrency);
        orderDO.setTransactionAmount(transactionAmount);
        orderDO.setChannelRequestCurrency(transactionCurrency);
        orderDO.setChannelRequestAmount(transactionAmount);
        orderDO.setSettlementCurrency(null);
        orderDO.setSettlementAmount(null);
        orderDO.setCurrencyExponent(currencyExponent);
        orderDO.setDccEnabled(flagValue(commandDTO.getDccEnabled()));
        orderDO.setEdcEnabled(flagValue(commandDTO.getEdcEnabled()));
        orderDO.setTransactionRate(resolveTransactionRate(commandDTO));
        orderDO.setRateSource(commandDTO.getRateSource());
        orderDO.setRateTime(commandDTO.getRateTime());
        orderDO.setAuthorizedAmount(success && (isAuthorizationLike(resultDTO.getTransactionType())
                || PaymentTransactionTypeEnum.PAYMENT.getCode().equals(resultDTO.getTransactionType())) ? transactionAmount : zero);
        orderDO.setAuthorizedCancelAmount(zero);
        orderDO.setCapturedAmount(success && PaymentTransactionTypeEnum.PAYMENT.getCode().equals(resultDTO.getTransactionType()) ? transactionAmount : zero);
        orderDO.setRefundedAmount(zero);
        orderDO.setChargebackAmount(zero);
        orderDO.setAvailableCaptureAmount(success && isAuthorizationLike(resultDTO.getTransactionType()) ? transactionAmount : zero);
        orderDO.setAvailableRefundAmount(success && PaymentTransactionTypeEnum.PAYMENT.getCode().equals(resultDTO.getTransactionType()) ? transactionAmount : zero);
    }

/**
 * 构造金额fields对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 */
    private void fillAmountFields(TransactionOperationDO operationDO,
                                  PaymentCreateCommandDTO commandDTO,
                                  PaymentCreateResultDTO resultDTO,
                                  int currencyExponent) {
        BigDecimal transactionAmount = resolveTransactionAmount(commandDTO);
        String transactionCurrency = resolveTransactionCurrency(commandDTO);
        boolean success = PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus());
        operationDO.setLabelCurrency(resolveLabelCurrency(commandDTO));
        operationDO.setLabelAmount(resolveLabelAmount(commandDTO));
        operationDO.setTransactionCurrency(transactionCurrency);
        operationDO.setTransactionAmount(transactionAmount);
        operationDO.setApprovedCurrency(success ? transactionCurrency : null);
        operationDO.setApprovedAmount(success ? transactionAmount : null);
        operationDO.setChannelRequestCurrency(transactionCurrency);
        operationDO.setChannelRequestAmount(transactionAmount);
        operationDO.setSettlementCurrency(null);
        operationDO.setSettlementAmount(null);
        operationDO.setCurrencyExponent(currencyExponent);
        operationDO.setDccEnabled(flagValue(commandDTO.getDccEnabled()));
        operationDO.setEdcEnabled(flagValue(commandDTO.getEdcEnabled()));
        operationDO.setTransactionRate(resolveTransactionRate(commandDTO));
    }

/**
 * 构造followup金额fields对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
 * @param sourceOrderDO source Order DO 输入值，参与 来源订单do 的查询、校验、转换、写入或日志摘要
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 */
    private void fillFollowUpAmountFields(TransactionOperationDO operationDO,
                                          TransactionOrderDO sourceOrderDO,
                                          PaymentCreateCommandDTO commandDTO,
                                          PaymentCreateResultDTO resultDTO,
                                          int currencyExponent) {
        BigDecimal transactionAmount = resolveTransactionAmount(commandDTO);
        boolean success = PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus());
        operationDO.setLabelCurrency(resolveLabelCurrency(commandDTO));
        operationDO.setLabelAmount(resolveLabelAmount(commandDTO));
        operationDO.setTransactionCurrency(sourceOrderDO.getTransactionCurrency());
        operationDO.setTransactionAmount(transactionAmount);
        operationDO.setApprovedCurrency(success ? sourceOrderDO.getTransactionCurrency() : null);
        operationDO.setApprovedAmount(success ? transactionAmount : null);
        operationDO.setChannelRequestCurrency(sourceOrderDO.getTransactionCurrency());
        operationDO.setChannelRequestAmount(transactionAmount);
        operationDO.setSettlementCurrency(null);
        operationDO.setSettlementAmount(null);
        operationDO.setCurrencyExponent(currencyExponent);
        operationDO.setDccEnabled(flagValue(commandDTO.getDccEnabled()));
        operationDO.setEdcEnabled(flagValue(commandDTO.getEdcEnabled()));
        operationDO.setTransactionRate(resolveTransactionRate(commandDTO));
    }

    /**
     * 解析resolvelabel币种，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolveLabelCurrency(PaymentCreateCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getLabelCurrency()) ? commandDTO.getLabelCurrency() : commandDTO.getCurrency();
    }

    /**
     * 解析resolvelabel金额，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private BigDecimal resolveLabelAmount(PaymentCreateCommandDTO commandDTO) {
        return commandDTO.getLabelAmount() == null ? commandDTO.getAmount() : commandDTO.getLabelAmount();
    }

    /**
     * 解析resolve交易币种，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolveTransactionCurrency(PaymentCreateCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getTransactionCurrency()) ? commandDTO.getTransactionCurrency() : commandDTO.getCurrency();
    }

    /**
     * 解析resolve交易金额，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private BigDecimal resolveTransactionAmount(PaymentCreateCommandDTO commandDTO) {
        BigDecimal amount = commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount();
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /**
     * 解析resolve交易汇率，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private BigDecimal resolveTransactionRate(PaymentCreateCommandDTO commandDTO) {
        return commandDTO.getTransactionRate() == null ? new BigDecimal("1.00000000") : commandDTO.getTransactionRate();
    }

    /**
     * 整理flag值，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int flagValue(Integer value) {
        return value != null && ENABLED == value ? ENABLED : DISABLED;
    }

/**
 * 构造订单routefields对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param orderDO order DO 输入值，参与 订单do 的查询、校验、转换、写入或日志摘要
 * @param routeResultDTO route Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param channelResponse 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
 * @param riskDecisionEnum risk Decision Enum 输入值，参与 风控结论enum 的查询、校验、转换、写入或日志摘要
 */
    private void fillOrderRouteFields(TransactionOrderDO orderDO,
                                      PaymentRouteResultDTO routeResultDTO,
                                      ChannelPaymentResponse channelResponse,
                                      PaymentRiskDecisionEnum riskDecisionEnum) {
        orderDO.setSettlementStatus(NOT_SETTLED);
        orderDO.setReconciliationStatus(NOT_RECONCILED);
        orderDO.setAccountingStatus(NOT_ACCOUNTED);
        orderDO.setChannelMatchStatus(resolveChannelMatchStatus(orderDO.getTransactionStatus()));
        orderDO.setChannelMatchCount(0);
        if (routeResultDTO != null) {
            orderDO.setChannelId(routeResultDTO.getChannelId());
            orderDO.setChannelCode(routeResultDTO.getChannelCode());
            orderDO.setChannelMidConfigId(routeResultDTO.getMidConfigId());
            orderDO.setChannelMerchantId(routeResultDTO.getMidNo());
        }
        if (channelResponse != null) {
            orderDO.setChannelCode(channelResponse.getChannelCode());
            orderDO.setChannelOrderNo(channelResponse.getChannelOrderNo());
        }
        orderDO.setInternalRiskDecision(riskDecisionEnum == null ? PaymentRiskDecisionEnum.UNKNOWN.getCode() : riskDecisionEnum.getCode());
    }

/**
 * 构造动作routefields对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
 * @param routeResultDTO route Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param channelResponse 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
 */
    private void fillOperationRouteFields(TransactionOperationDO operationDO,
                                          PaymentRouteResultDTO routeResultDTO,
                                          ChannelPaymentResponse channelResponse) {
        if (routeResultDTO != null) {
            operationDO.setChannelId(routeResultDTO.getChannelId());
            operationDO.setChannelCode(routeResultDTO.getChannelCode());
            operationDO.setChannelMidConfigId(routeResultDTO.getMidConfigId());
        }
        if (channelResponse != null) {
            operationDO.setChannelCode(channelResponse.getChannelCode());
            operationDO.setChannelOrderNo(channelResponse.getChannelOrderNo());
            operationDO.setChannelTransactionId(channelResponse.getChannelTransactionId());
            operationDO.setChannelStatus(channelResponse.getRawChannelStatus());
            operationDO.setChannelResponseCode(channelResponse.getChannelResponseCode());
            operationDO.setChannelResponseMessage(channelResponse.getChannelResponseMessage());
            operationDO.setAuthCode(channelResponse.getAuthCode());
            operationDO.setRrn(channelResponse.getRrn());
            operationDO.setAcquirerReferenceNo(channelResponse.getAcquirerReferenceNo());
        }
    }

    /**
     * 解析resolvepaymentmethod，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolvePaymentMethod(PaymentCreateCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getPaymentMethod()) ? commandDTO.getPaymentMethod() : DEFAULT_PAYMENT_METHOD;
    }

    /**
     * 判断 is authorization like 条件是否成立，用于控制 Default Transaction Record Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param transactionType transaction Type 输入值，参与 交易type 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isAuthorizationLike(String transactionType) {
        return Objects.equals(PaymentTransactionTypeEnum.AUTHORIZATION.getCode(), transactionType)
                || Objects.equals(PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode(), transactionType);
    }

    /**
     * 判断 is terminal 条件是否成立，用于控制 Default Transaction Record Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isTerminal(PaymentCreateResultDTO resultDTO) {
        return PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus());
    }

    /**
     * 按回调或查询勾兑结果更新生命周期主单。
     * <p>
     * 首次交易成功直接标记主单成功；后续交易成功只更新对应授权、请款、退款或撤销汇总金额；
     * 失败终态通过 CAS 更新主单状态，避免终态被重复回调覆盖。
     *
     * @param orderDO 生命周期主单
     * @param operationDO 被确认的动作单
     * @param success true 表示动作成功终态
     * @param targetTransactionStatus 目标交易状态
     * @param failReasonCode 失败原因码
     * @param failReasonMessage 失败原因描述
     * @return true 表示主单更新成功
     */
    private boolean updateOrderByCallback(TransactionOrderDO orderDO,
                                          TransactionOperationDO operationDO,
                                          boolean success,
                                          String targetTransactionStatus,
                                          String failReasonCode,
                                          String failReasonMessage) {
        if (success) {
            if (!StringUtils.hasText(operationDO.getSourceTransactionId())) {
                int updated = transactionOrderMapper.markInitialSuccess(
                        orderDO.getOperationId(),
                        orderDO.getTransactionDateTime(),
                        operationDO.getTransactionId(),
                        operationDO.getTransactionAmount() == null
                                ? BigDecimal.ZERO : operationDO.getTransactionAmount(),
                        orderDO.getVersion(),
                        CHANNEL_MATCH_MATCHED);
                return updated == 1;
            }
            PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
            resultDTO.setTransactionId(operationDO.getTransactionId());
            resultDTO.setTransactionType(operationDO.getTransactionType());
            resultDTO.setAmount(operationDO.getTransactionAmount() == null
                    ? null
                    : operationDO.getTransactionAmount().movePointRight(orderDO.getCurrencyExponent() == null ? 0 : orderDO.getCurrencyExponent()).longValue());
            updateSourceOrderAmount(orderDO, resultDTO);
            return true;
        }
        if (StringUtils.hasText(operationDO.getSourceTransactionId())
                && PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode().equals(operationDO.getTransactionType())) {
            return true;
        }
        int updated = transactionOrderMapper.completeStatus(
                orderDO.getOperationId(),
                orderDO.getTransactionDateTime(),
                operationDO.getTransactionId(),
                orderDO.getVersion(),
                targetTransactionStatus,
                PaymentProcessStageEnum.FINISHED.getCode(),
                failReasonCode,
                failReasonMessage,
                failReasonCode,
                failReasonCode,
                CHANNEL_MATCH_MATCHED);
        return updated == 1;
    }

/**
 * 更新初始渠道请求，保持业务状态、配置项或展示字段与请求意图一致。
 * <p>
 * 前置条件：调用方已确认 支付核心服务 中目标记录存在且当前状态允许变更。
 * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
 * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
 * </p>
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param routeResultDTO route Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param invokeResultDTO invoke Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
 */
    private void updateInitialChannelRequest(PaymentCreateCommandDTO commandDTO,
                                             PaymentRouteResultDTO routeResultDTO,
                                             PaymentChannelInvokeResultDTO invokeResultDTO,
                                             PaymentCreateResultDTO resultDTO,
                                             LocalDateTime now) {
        TransactionChannelRequestDO requestDO = transactionChannelRequestMapper.selectByRequestId(
                invokeResultDTO.getRequestId(), commandDTO.getTransactionDateTime());
        if (requestDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND.getCode(), "channel request fact can not be found");
        }
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        Map<String, String> rawResponse = response == null ? Map.of() : response.getRawResponse();
        int updated = transactionChannelRequestMapper.updateStatusLogical(
                invokeResultDTO.getRequestId(),
                commandDTO.getTransactionDateTime(),
                requestDO.getVersion(),
                List.of("INIT", "SENT", "TIMEOUT", "FAILED"),
                invokeResultDTO.getRequestStatus(),
                rawResponse.get("result"),
                rawResponse.get("gatewayCode"),
                rawResponse.get("acquirerCode"),
                rawResponse.get("acquirerMessage"),
                response == null ? null : response.getRawChannelStatus(),
                PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus()) ? 1 : 0,
                resultDTO.getStatus(),
                firstText(invokeResultDTO.getExceptionMessage(), resultDTO.getFailReasonCode(), resultDTO.getFailReasonMessage()),
                invokeResultDTO.getResponseTime() == null ? now : invokeResultDTO.getResponseTime(),
                invokeResultDTO.getDurationMillis());
        if (updated != 1 && isRequestResultConflict(requestDO, resultDTO)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "channel request state has changed");
        }
        log.info("event: PAYMENT_CHANNEL_REQUEST_DB_UPDATED stage=CHANNEL_RESULT traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} currency: {} amount: {} channelCode: {} channelRequestId: {} channelTransactionId: {} requestStatus: {} platformStatus: {} channelResultCode: {} acquirerCode: {} logicalTable: {} affectedRows: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                resultDTO.getTransactionId(),
                resultDTO.getOperationId(),
                resultDTO.getTransactionType(),
                resultDTO.getCurrency(),
                resultDTO.getAmount(),
                routeResultDTO == null ? null : routeResultDTO.getChannelCode(),
                invokeResultDTO.getRequestId(),
                invokeResultDTO.getChannelRequest().getChannelTransactionId(),
                invokeResultDTO.getRequestStatus(),
                resultDTO.getStatus(),
                response == null ? null : response.getChannelResponseCode(),
                response == null ? null : response.getAcquirerReferenceNo(),
                TRANSACTION_CHANNEL_REQUEST_TABLE,
                updated);
        updateChannelInteractionLog(commandDTO, invokeResultDTO, resultDTO, now);
    }

/**
 * 更新渠道interaction日志，保持业务状态、配置项或展示字段与请求意图一致。
 * <p>
 * 前置条件：调用方已确认 支付核心服务 中目标记录存在且当前状态允许变更。
 * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
 * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
 * </p>
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param invokeResultDTO invoke Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
 */
    private void updateChannelInteractionLog(PaymentCreateCommandDTO commandDTO,
                                             PaymentChannelInvokeResultDTO invokeResultDTO,
                                             PaymentCreateResultDTO resultDTO,
                                             LocalDateTime now) {
        if (commandDTO == null || invokeResultDTO == null || !StringUtils.hasText(invokeResultDTO.getRequestId())) {
            return;
        }
        TransactionChannelInteractionLogDO logDO = buildChannelInteractionLog(commandDTO, invokeResultDTO, resultDTO, now);
        int updated = transactionChannelInteractionLogMapper.updateByRequestIdLogical(
                invokeResultDTO.getRequestId(),
                commandDTO.getTransactionDateTime(),
                logDO.getInteractionType(),
                logDO.getHttpMethod(),
                logDO.getRequestUrlMasked(),
                logDO.getHttpStatus(),
                logDO.getRequestHeaderJsonMasked(),
                logDO.getRequestBodyJsonMasked(),
                logDO.getResponseHeaderJsonMasked(),
                logDO.getResponseBodyJsonMasked(),
                logDO.getExceptionType(),
                logDO.getExceptionMessage(),
                logDO.getDurationMillis(),
                logDO.getInteractionTime());
        if (updated > 1) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "duplicate channel interaction facts found");
        }
        int affectedRows = updated;
        if (updated == 0) {
            TransactionChannelInteractionLogDO current = transactionChannelInteractionLogMapper.selectByRequestId(
                    invokeResultDTO.getRequestId(), commandDTO.getTransactionDateTime());
            if (current == null) {
                throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND.getCode(), "channel interaction fact can not be found");
            }
            if (!hasSameChannelInteractionResult(current, logDO)) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "channel interaction result has changed");
            }
        }
        log.info("event: PAYMENT_CHANNEL_INTERACTION_LOG_SAVED stage=CHANNEL_RESULT traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} channelRequestId: {} httpStatus: {} durationMs: {} logicalTable: {} affectedRows: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                resultDTO == null ? null : resultDTO.getTransactionId(),
                resultDTO == null ? null : resultDTO.getOperationId(),
                resultDTO == null ? null : resultDTO.getTransactionType(),
                invokeResultDTO.getRequestId(),
                logDO.getHttpStatus(),
                logDO.getDurationMillis(),
                TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE,
                affectedRows);
    }

    /**
     * 判断已落库的首个渠道结果是否与本次重复结果完全一致。
     * 交互时间属于落库审计时间，不参与业务幂等比较；其余结果字段发生差异时必须拒绝覆盖。
     *
     * @param current 已落库渠道交互事实
     * @param incoming 本次准备回填的渠道结果
     * @return true 表示本次为同结果幂等重放
     */
    private boolean hasSameChannelInteractionResult(TransactionChannelInteractionLogDO current,
                                                    TransactionChannelInteractionLogDO incoming) {
        return Objects.equals(current.getInteractionType(), incoming.getInteractionType())
                && Objects.equals(current.getHttpMethod(), incoming.getHttpMethod())
                && Objects.equals(current.getRequestUrlMasked(), incoming.getRequestUrlMasked())
                && Objects.equals(current.getHttpStatus(), incoming.getHttpStatus())
                && Objects.equals(current.getRequestHeaderJsonMasked(), incoming.getRequestHeaderJsonMasked())
                && Objects.equals(current.getRequestBodyJsonMasked(), incoming.getRequestBodyJsonMasked())
                && Objects.equals(current.getResponseHeaderJsonMasked(), incoming.getResponseHeaderJsonMasked())
                && Objects.equals(current.getResponseBodyJsonMasked(), incoming.getResponseBodyJsonMasked())
                && Objects.equals(current.getExceptionType(), incoming.getExceptionType())
                && Objects.equals(current.getExceptionMessage(), incoming.getExceptionMessage())
                && Objects.equals(current.getDurationMillis(), incoming.getDurationMillis());
    }

    /**
     * 判断 is request result conflict 条件是否成立，用于控制 Default Transaction Record Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param requestDO request DO 输入值，参与 请求do 的查询、校验、转换、写入或日志摘要
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isRequestResultConflict(TransactionChannelRequestDO requestDO, PaymentCreateResultDTO resultDTO) {
        return requestDO == null
                || !PaymentTransactionStatusEnum.SUCCESS.getCode().equals(requestDO.getPlatformResultCode())
                || !PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus());
    }

/**
 * 更新商户apifinal结果，保持业务状态、配置项或展示字段与请求意图一致。
 * <p>
 * 前置条件：调用方已确认 支付核心服务 中目标记录存在且当前状态允许变更。
 * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
 * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
 * </p>
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
 */
    private void updateMerchantApiFinalResult(PaymentCreateCommandDTO commandDTO,
                                              PaymentCreateResultDTO resultDTO,
                                              LocalDateTime now) {
        if (transactionMerchantApiInteractionLogMapper == null
                || commandDTO == null
                || resultDTO == null
                || !StringUtils.hasText(commandDTO.getMerchantRequestPlainJsonMasked())) {
            return;
        }
        transactionMerchantApiInteractionLogMapper.updateFinalResultLogical(
                resultDTO.getTransactionId(),
                commandDTO.getTransactionDateTime(),
                commandDTO.getRequestId(),
                resultDTO.getStatus(),
                resultDTO.getStatus(),
                resolveMerchantResponseCode(resultDTO),
                resolveMerchantResponseMessage(resultDTO),
                safeLength(maskedJson(merchantVisiblePayload(commandDTO, resultDTO)), 16_000),
                now,
                resolveDurationMillis(commandDTO.getOpenApiRequestTime(), now));
    }

    /**
     * 判断 is original request result conflict 条件是否成立，用于控制 Default Transaction Record Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param requestDO request DO 输入值，参与 请求do 的查询、校验、转换、写入或日志摘要
     * @param platformResultCode platform Result Code 输入值，参与 platform结果编码 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isOriginalRequestResultConflict(TransactionChannelRequestDO requestDO, String platformResultCode) {
        return requestDO == null
                || !PaymentTransactionStatusEnum.SUCCESS.getCode().equals(requestDO.getPlatformResultCode())
                || !PaymentTransactionStatusEnum.SUCCESS.getCode().equals(platformResultCode);
    }

/**
 * 整理complete初始terminal状态，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
 * @param orderDO order DO 输入值，参与 订单do 的查询、校验、转换、写入或日志摘要
 * @param invokeResultDTO invoke Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
 */
    private boolean completeInitialTerminalStatus(TransactionOperationDO operationDO,
                                                  TransactionOrderDO orderDO,
                                                  PaymentChannelInvokeResultDTO invokeResultDTO,
                                                  PaymentCreateResultDTO resultDTO) {
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        int operationUpdated = transactionOperationMapper.completeStatus(
                operationDO.getId(),
                operationDO.getTransactionDateTime(),
                operationDO.getVersion(),
                resultDTO.getStatus(),
                resultDTO.getProcessStage(),
                resultDTO.getFailReasonCode(),
                resultDTO.getFailReasonMessage(),
                response == null ? null : response.getRawChannelStatus(),
                response == null ? null : response.getChannelResponseCode(),
                response == null ? null : response.getChannelResponseMessage(),
                response == null ? null : response.getAuthCode(),
                response == null ? null : response.getRrn(),
                response == null ? null : response.getAcquirerReferenceNo(),
                CHANNEL_MATCH_NOT_REQUIRED);
        if (operationUpdated != 1) {
            return false;
        }
        boolean success = PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus());
        if (success) {
            int orderUpdated = transactionOrderMapper.markInitialSuccess(
                    orderDO.getOperationId(),
                    orderDO.getTransactionDateTime(),
                    resultDTO.getTransactionId(),
                    operationDO.getTransactionAmount() == null
                            ? BigDecimal.ZERO : operationDO.getTransactionAmount(),
                    orderDO.getVersion(),
                    CHANNEL_MATCH_NOT_REQUIRED);
            if (orderUpdated != 1) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "order state has changed");
            }
            return true;
        }
        int orderUpdated = transactionOrderMapper.completeStatus(
                orderDO.getOperationId(),
                orderDO.getTransactionDateTime(),
                resultDTO.getTransactionId(),
                orderDO.getVersion(),
                resultDTO.getStatus(),
                resultDTO.getProcessStage(),
                resultDTO.getFailReasonCode(),
                resultDTO.getFailReasonMessage(),
                resultDTO.getFailReasonCode(),
                resultDTO.getFailReasonCode(),
                CHANNEL_MATCH_NOT_REQUIRED);
        if (orderUpdated != 1) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "order state has changed");
        }
        return true;
    }

/**
 * 更新初始nonterminal状态，保持业务状态、配置项或展示字段与请求意图一致。
 * <p>
 * 前置条件：调用方已确认 支付核心服务 中目标记录存在且当前状态允许变更。
 * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
 * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
 * </p>
 * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
 * @param invokeResultDTO invoke Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
 * @return 写入、更新或删除后的处理结果
 */
    private boolean updateInitialNonTerminalStatus(TransactionOperationDO operationDO,
                                                   PaymentChannelInvokeResultDTO invokeResultDTO,
                                                   PaymentCreateResultDTO resultDTO,
                                                   LocalDateTime now) {
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        int updated = transactionOperationMapper.updateNonTerminalChannelResult(
                operationDO.getId(),
                operationDO.getTransactionDateTime(),
                operationDO.getVersion(),
                resultDTO.getStatus(),
                resultDTO.getProcessStage(),
                resultDTO.getPendingReasonCode(),
                resultDTO.getFailReasonCode(),
                resultDTO.getFailReasonMessage(),
                response == null ? null : response.getRawChannelStatus(),
                response == null ? null : response.getChannelResponseCode(),
                response == null ? null : response.getChannelResponseMessage(),
                invokeResultDTO.getRequestId(),
                now);
        log.info("event: PAYMENT_STATUS_DB_UPDATED stage=STATUS_RESULT traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} sourceTransactionId: {} transactionType: {} currency: {} amount: {} channelCode: {} channelRequestId: {} channelTransactionId: {} platformStatus: {} channelResultCode: {} logicalTable: {} affectedRows: {} statusBefore: {} statusAfter: {}",
                TraceContext.getTraceId(),
                operationDO.getMerchantId(),
                operationDO.getMerchantOrderNo(),
                operationDO.getTransactionId(),
                operationDO.getOperationId(),
                operationDO.getSourceTransactionId(),
                operationDO.getTransactionType(),
                operationDO.getTransactionCurrency(),
                operationDO.getTransactionAmount(),
                operationDO.getChannelCode(),
                invokeResultDTO.getRequestId(),
                operationDO.getChannelTransactionId(),
                resultDTO.getStatus(),
                response == null ? null : response.getChannelResponseCode(),
                TRANSACTION_OPERATION_TABLE,
                updated,
                operationDO.getTransactionStatus(),
                resultDTO.getStatus());
        return updated == 1;
    }

/**
 * 整理completecaptureterminal状态，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
 * @param sourceOrderDO source Order DO 输入值，参与 来源订单do 的查询、校验、转换、写入或日志摘要
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param routeResultDTO route Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param invokeResultDTO invoke Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
 */
    private boolean completeCaptureTerminalStatus(TransactionOperationDO operationDO,
                                                  TransactionOrderDO sourceOrderDO,
                                                  PaymentCreateCommandDTO commandDTO,
                                                  PaymentRouteResultDTO routeResultDTO,
                                                  PaymentChannelInvokeResultDTO invokeResultDTO,
                                                  PaymentCreateResultDTO resultDTO,
                                                  int currencyExponent,
                                                  LocalDateTime now) {
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        int operationUpdated = transactionOperationMapper.completeStatus(
                operationDO.getId(),
                operationDO.getTransactionDateTime(),
                operationDO.getVersion(),
                resultDTO.getStatus(),
                resultDTO.getProcessStage(),
                resultDTO.getFailReasonCode(),
                resultDTO.getFailReasonMessage(),
                response == null ? null : response.getRawChannelStatus(),
                response == null ? null : response.getChannelResponseCode(),
                response == null ? null : response.getChannelResponseMessage(),
                response == null ? null : response.getAuthCode(),
                response == null ? null : response.getRrn(),
                response == null ? null : response.getAcquirerReferenceNo(),
                CHANNEL_MATCH_NOT_REQUIRED);
        if (operationUpdated != 1) {
            return false;
        }
        TransactionOperationDO mergedOperation = mergeOperationResult(operationDO, invokeResultDTO, resultDTO);
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())) {
            updateSourceOrderAmount(sourceOrderDO, resultDTO);
            recordAmountChange(mergedOperation, sourceOrderDO, now);
        }
        insertCallbackStateAndFlow(mergedOperation,
                sourceOrderDO,
                invokeResultDTO.getRequestId(),
                resultDTO.getStatus(),
                resultDTO.getFailReasonCode(),
                resultDTO.getFailReasonMessage(),
                PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus()),
                now);
        activateMerchantNotification(operationDO, resultDTO.getStatus(), resultDTO.getFailReasonCode(), resultDTO.getFailReasonMessage(), now);
        return true;
    }

/**
 * 更新capturenonterminal状态，保持业务状态、配置项或展示字段与请求意图一致。
 * <p>
 * 前置条件：调用方已确认 支付核心服务 中目标记录存在且当前状态允许变更。
 * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
 * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
 * </p>
 * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
 * @param sourceOrderDO source Order DO 输入值，参与 来源订单do 的查询、校验、转换、写入或日志摘要
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param routeResultDTO route Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param invokeResultDTO invoke Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
 * @return 写入、更新或删除后的处理结果
 */
    private boolean updateCaptureNonTerminalStatus(TransactionOperationDO operationDO,
                                                   TransactionOrderDO sourceOrderDO,
                                                   PaymentCreateCommandDTO commandDTO,
                                                   PaymentRouteResultDTO routeResultDTO,
                                                   PaymentChannelInvokeResultDTO invokeResultDTO,
                                                   PaymentCreateResultDTO resultDTO,
                                                   int currencyExponent,
                                                   LocalDateTime now) {
        boolean updated = updateInitialNonTerminalStatus(operationDO, invokeResultDTO, resultDTO, now);
        if (!updated) {
            return false;
        }
        insertCallbackStateAndFlow(mergeOperationResult(operationDO, invokeResultDTO, resultDTO),
                sourceOrderDO,
                invokeResultDTO.getRequestId(),
                resultDTO.getStatus(),
                resultDTO.getFailReasonCode(),
                resultDTO.getFailReasonMessage(),
                false,
                now);
        return true;
    }

/**
 * 构造动作结果对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
 * @param invokeResultDTO invoke Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
 */
    private TransactionOperationDO mergeOperationResult(TransactionOperationDO source,
                                                       PaymentChannelInvokeResultDTO invokeResultDTO,
                                                       PaymentCreateResultDTO resultDTO) {
        TransactionOperationDO target = new TransactionOperationDO();
        target.setId(source.getId());
        target.setOperationId(source.getOperationId());
        target.setTransactionId(source.getTransactionId());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setMerchantId(source.getMerchantId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setMerchantOrderId(source.getMerchantOrderId());
        target.setMerchantOperationNo(source.getMerchantOperationNo());
        target.setTransactionType(source.getTransactionType());
        target.setTransactionStatus(resultDTO.getStatus());
        target.setProcessStage(resultDTO.getProcessStage());
        target.setPendingReasonCode(resultDTO.getPendingReasonCode());
        target.setFailReasonCode(resultDTO.getFailReasonCode());
        target.setFailReasonMessage(resultDTO.getFailReasonMessage());
        target.setTransactionAmount(source.getTransactionAmount());
        target.setTransactionCurrency(source.getTransactionCurrency());
        target.setCurrencyExponent(source.getCurrencyExponent());
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        target.setChannelCode(firstText(response == null ? null : response.getChannelCode(), source.getChannelCode()));
        target.setChannelOrderNo(source.getChannelOrderNo());
        target.setChannelTransactionId(source.getChannelTransactionId());
        target.setChannelStatus(response == null ? source.getChannelStatus() : response.getRawChannelStatus());
        target.setChannelResponseCode(response == null ? source.getChannelResponseCode() : response.getChannelResponseCode());
        target.setChannelResponseMessage(response == null ? source.getChannelResponseMessage() : response.getChannelResponseMessage());
        target.setAuthCode(response == null ? source.getAuthCode() : response.getAuthCode());
        target.setRrn(response == null ? source.getRrn() : response.getRrn());
        target.setAcquirerReferenceNo(response == null ? source.getAcquirerReferenceNo() : response.getAcquirerReferenceNo());
        target.setTransactionDateTime(source.getTransactionDateTime());
        target.setVersion(source.getVersion());
        return target;
    }

    /**
     * 记录回调或查询勾兑触发的状态历史和流程事件。
     *
     * @param operationDO 被确认动作单
     * @param orderDO 生命周期主单
     * @param callbackId 回调或勾兑事件 ID
     * @param targetTransactionStatus 目标交易状态
     * @param failReasonCode 失败原因码
     * @param failReasonMessage 失败原因描述
     * @param orderUpdated 主单是否更新成功
     * @param now 当前处理时间
     */
    private void insertCallbackStateAndFlow(TransactionOperationDO operationDO,
                                            TransactionOrderDO orderDO,
                                            String callbackId,
                                            String targetTransactionStatus,
                                            String failReasonCode,
                                            String failReasonMessage,
                                            boolean orderUpdated,
                                            LocalDateTime now) {
        TransactionStatusHistoryDO operationHistory = buildCallbackStatusHistory(
                operationDO, STATUS_OBJECT_OPERATION, callbackId, targetTransactionStatus,
                TRANSITION_SUCCESS, null, now);
        TransactionStatusHistoryDO orderHistory = buildCallbackStatusHistory(
                operationDO, STATUS_OBJECT_ORDER, callbackId, targetTransactionStatus,
                orderUpdated ? TRANSITION_SUCCESS : TRANSITION_IGNORED,
                orderUpdated ? null : "order state has changed", now);
        transactionStatusHistoryMapper.insertLogical(operationHistory);
        transactionStatusHistoryMapper.insertLogical(orderHistory);
        TransactionFlowEventDO eventDO = new TransactionFlowEventDO();
        eventDO.setFlowEventId(PaymentOrderNoGenerator.nextOrderNo(FLOW_EVENT_PREFIX, operationDO.getTransactionDateTime()));
        eventDO.setTransactionId(operationDO.getTransactionId());
        eventDO.setOperationId(operationDO.getOperationId());
        eventDO.setEventType("CHANNEL_CALLBACK_PROCESSED");
        eventDO.setEventStage("CALLBACK");
        eventDO.setEventStatus(orderUpdated ? "SUCCESS" : "SKIPPED");
        eventDO.setEventName("渠道回调处理");
        eventDO.setEventContent(safeLength("回调状态：" + targetTransactionStatus, 1024));
        eventDO.setPreviousStatus(operationDO.getTransactionStatus());
        eventDO.setCurrentStatus(targetTransactionStatus);
        eventDO.setOperatorType("CHANNEL");
        eventDO.setReferenceType("CALLBACK");
        eventDO.setReferenceId(callbackId);
        eventDO.setErrorCode(failReasonCode);
        eventDO.setErrorMessage(safeLength(failReasonMessage, 512));
        eventDO.setEventTime(now);
        fillTransactionTime(eventDO, operationDO.getTransactionDateTime());
        eventDO.setCreateTime(now);
        transactionFlowEventMapper.insertLogical(eventDO);
    }

    /**
     * 记录被终态保护或版本冲突拦截的回调状态历史。
     *
     * @param operationDO 被回调动作单
     * @param callbackId 回调或勾兑事件 ID
     * @param targetTransactionStatus 目标状态
     * @param transitionResult 流转结果
     * @param failReason 拦截原因
     */
    private void recordCallbackStatusHistory(TransactionOperationDO operationDO,
                                             String callbackId,
                                             String targetTransactionStatus,
                                             String transitionResult,
                                             String failReason) {
        if (operationDO == null || operationDO.getTransactionDateTime() == null) {
            return;
        }
        TransactionStatusHistoryDO historyDO = buildCallbackStatusHistory(
                operationDO, STATUS_OBJECT_OPERATION, callbackId, targetTransactionStatus,
                transitionResult, failReason, LocalDateTime.now());
        transactionStatusHistoryMapper.insertLogical(historyDO);
    }

    /**
     * 构造回调或查询勾兑对应的状态历史。
     *
     * @param operationDO 交易动作单
     * @param statusObject 状态对象，主单或动作单
     * @param callbackId 回调或勾兑事件 ID
     * @param targetTransactionStatus 目标状态
     * @param transitionResult 流转结果
     * @param failReason 失败或忽略原因
     * @param now 当前处理时间
     * @return 状态历史记录
     */
    private TransactionStatusHistoryDO buildCallbackStatusHistory(TransactionOperationDO operationDO,
                                                                  String statusObject,
                                                                  String callbackId,
                                                                  String targetTransactionStatus,
                                                                  String transitionResult,
                                                                  String failReason,
                                                                  LocalDateTime now) {
        TransactionStatusHistoryDO historyDO = new TransactionStatusHistoryDO();
        historyDO.setStatusHistoryId(PaymentOrderNoGenerator.nextOrderNo(STATUS_HISTORY_PREFIX, operationDO.getTransactionDateTime()));
        historyDO.setTransactionId(operationDO.getTransactionId());
        historyDO.setOperationId(operationDO.getOperationId());
        historyDO.setStatusObject(statusObject);
        historyDO.setFromStatus(operationDO.getTransactionStatus());
        historyDO.setToStatus(targetTransactionStatus);
        historyDO.setTriggerType(TRIGGER_TYPE_CHANNEL_CALLBACK);
        historyDO.setTriggerId(callbackId);
        historyDO.setTransitionResult(transitionResult);
        historyDO.setFailReason(safeLength(failReason, 512));
        historyDO.setVersionBefore(operationDO.getVersion());
        historyDO.setVersionAfter(operationDO.getVersion() == null ? null : operationDO.getVersion() + 1);
        historyDO.setStatusTime(now);
        fillTransactionTime(historyDO, operationDO.getTransactionDateTime());
        historyDO.setCreateTime(now);
        return historyDO;
    }

    /**
     * 激活待发送商户通知。
     * <p>
     * 只有主单状态或动作终态被成功推进后才激活通知，避免 WorldPay AUTHORISED 这类非终态事件提前通知商户成功。
     *
     * @param operationDO 交易动作单
     * @param targetTransactionStatus 目标交易状态
     * @param failReasonCode 失败原因码
     * @param failReasonMessage 失败原因描述
     * @param now 当前处理时间
     */
    private void activateMerchantNotification(TransactionOperationDO operationDO,
                                              String targetTransactionStatus,
                                              String failReasonCode,
                                              String failReasonMessage,
                                              LocalDateTime now) {
        int affectedRows = transactionMerchantNotificationMapper.activateByTransactionId(
                operationDO.getTransactionId(),
                operationDO.getTransactionDateTime(),
                INITIAL_VERSION,
                maskedJson(merchantVisiblePayload(operationDO, targetTransactionStatus, failReasonCode)),
                now,
                now);
        log.info("event: PAYMENT_MERCHANT_NOTIFY_ACTIVATED stage=NOTIFY_CREATE traceId: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} platformStatus: {} logicalTable: {} affectedRows: {} willNotify: {}",
                TraceContext.getTraceId(),
                operationDO.getTransactionId(),
                operationDO.getOperationId(),
                operationDO.getMerchantId(),
                operationDO.getMerchantOrderNo(),
                targetTransactionStatus,
                TRANSACTION_MERCHANT_NOTIFICATION_TABLE,
                affectedRows,
                affectedRows > 0);
    }

    /**
     * 根据交易结果判断是否还需要渠道查询勾兑。
     *
     * @param resultDTO 交易结果
     * @return 勾兑状态
     */
    private String resolveChannelMatchStatus(PaymentCreateResultDTO resultDTO) {
        return resultDTO == null ? CHANNEL_MATCH_PENDING : resolveChannelMatchStatus(resultDTO.getStatus());
    }

    /**
     * 根据平台状态判断是否还需要渠道查询勾兑。
     *
     * @param transactionStatus 平台交易状态
     * @return 勾兑状态
     */
    private String resolveChannelMatchStatus(String transactionStatus) {
        return PaymentTransactionStatusEnum.SUCCESS.getCode().equals(transactionStatus)
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)
                ? CHANNEL_MATCH_NOT_REQUIRED
                : CHANNEL_MATCH_PENDING;
    }

    /**
     * 成功后续交易回写生命周期汇总金额。
     *
     * @param sourceOrderDO 原生命周期主单
     * @param resultDTO 后续交易结果
     */
    private int updateSourceOrderAmount(TransactionOrderDO sourceOrderDO,
                                        PaymentCreateResultDTO resultDTO) {
        BigDecimal amount = amountFromResult(resultDTO, sourceOrderDO);
        int updated;
        if (PaymentTransactionTypeEnum.CAPTURE.getCode().equals(resultDTO.getTransactionType())
                || PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION.getCode().equals(resultDTO.getTransactionType())) {
            updated = transactionOrderMapper.increaseCapturedAmount(
                    sourceOrderDO.getOperationId(), sourceOrderDO.getTransactionDateTime(),
                    resultDTO.getTransactionId(), amount, sourceOrderDO.getVersion());
        } else if (PaymentTransactionTypeEnum.REFUND.getCode().equals(resultDTO.getTransactionType())) {
            updated = transactionOrderMapper.increaseRefundedAmount(
                    sourceOrderDO.getOperationId(), sourceOrderDO.getTransactionDateTime(),
                    resultDTO.getTransactionId(), amount, sourceOrderDO.getVersion());
        } else if (PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode().equals(resultDTO.getTransactionType())) {
            updated = transactionOrderMapper.increaseAuthorizedAmount(
                    sourceOrderDO.getOperationId(), sourceOrderDO.getTransactionDateTime(),
                    resultDTO.getTransactionId(), amount, sourceOrderDO.getVersion());
        } else if (PaymentTransactionTypeEnum.VOID.getCode().equals(resultDTO.getTransactionType())) {
            BigDecimal cancelAmount = remainingAuthorizedAmount(sourceOrderDO);
            updated = transactionOrderMapper.markVoidSuccess(
                    sourceOrderDO.getOperationId(), sourceOrderDO.getTransactionDateTime(),
                    resultDTO.getTransactionId(), cancelAmount, sourceOrderDO.getVersion());
        } else {
            updated = 1;
        }
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "source transaction state has changed");
        }
        log.info("event: PAYMENT_AMOUNT_CHANGED stage=AMOUNT_UPDATE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} sourceTransactionId: {} transactionType: {} currency: {} amount: {} logicalTable: {} affectedRows: {} authorizedBefore: {} capturedBefore: {} refundedBefore: {} availableCaptureBefore: {} availableRefundBefore: {}",
                TraceContext.getTraceId(),
                sourceOrderDO.getMerchantId(),
                sourceOrderDO.getMerchantOrderNo(),
                resultDTO.getTransactionId(),
                sourceOrderDO.getOperationId(),
                resultDTO.getSourceTransactionId(),
                resultDTO.getTransactionType(),
                sourceOrderDO.getTransactionCurrency(),
                amount,
                TRANSACTION_ORDER_TABLE,
                updated,
                sourceOrderDO.getAuthorizedAmount(),
                sourceOrderDO.getCapturedAmount(),
                sourceOrderDO.getRefundedAmount(),
                sourceOrderDO.getAvailableCaptureAmount(),
                sourceOrderDO.getAvailableRefundAmount());
        return updated;
    }

    /**
     * 从接口返回金额恢复主币种金额。
     *
     * @param resultDTO 交易结果
     * @param sourceOrderDO 原生命周期主单
     * @return 主币种金额
     */
    private BigDecimal amountFromResult(PaymentCreateResultDTO resultDTO, TransactionOrderDO sourceOrderDO) {
        if (resultDTO.getAmount() == null) {
            return BigDecimal.ZERO;
        }
        int exponent = sourceOrderDO.getCurrencyExponent() == null ? 0 : sourceOrderDO.getCurrencyExponent();
        return BigDecimal.valueOf(resultDTO.getAmount()).movePointLeft(exponent);
    }

    /**
     * 整理remainingauthorized金额，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param sourceOrderDO source Order DO 输入值，参与 来源订单do 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private BigDecimal remainingAuthorizedAmount(TransactionOrderDO sourceOrderDO) {
        BigDecimal authorizedAmount = sourceOrderDO.getAuthorizedAmount() == null
                ? BigDecimal.ZERO : sourceOrderDO.getAuthorizedAmount();
        BigDecimal authorizedCancelAmount = sourceOrderDO.getAuthorizedCancelAmount() == null
                ? BigDecimal.ZERO : sourceOrderDO.getAuthorizedCancelAmount();
        return authorizedAmount.subtract(authorizedCancelAmount);
    }

    /**
     * 记录渠道请求和交互审计。
     * <p>
     * 即使渠道请求异常也要保留请求摘要和脱敏日志，便于后续查询勾兑、人工排查和资金状态追踪。
     *
     * @param commandDTO 交易命令
     * @param routeResultDTO 渠道路由结果
     * @param invokeResultDTO 渠道调用上下文
     * @param resultDTO 平台交易结果
     * @param now 当前处理时间
     */
    private void recordChannelAudit(PaymentCreateCommandDTO commandDTO,
                                    PaymentRouteResultDTO routeResultDTO,
                                    PaymentChannelInvokeResultDTO invokeResultDTO,
                                    PaymentCreateResultDTO resultDTO,
                                    LocalDateTime now) {
        if (invokeResultDTO == null || invokeResultDTO.getChannelRequest() == null) {
            return;
        }
        TransactionChannelRequestDO requestDO = buildChannelRequest(
                commandDTO, routeResultDTO, invokeResultDTO, resultDTO, now);
        TransactionChannelInteractionLogDO interactionDO = buildChannelInteractionLog(
                commandDTO, invokeResultDTO, resultDTO, now);
        int requestRows = transactionChannelRequestMapper.insertLogical(requestDO);
        int interactionRows = transactionChannelInteractionLogMapper.insertLogical(interactionDO);
        log.info("event: PAYMENT_CHANNEL_AUDIT_SAVED stage=CHANNEL_AUDIT traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} currency: {} amount: {} channelCode: {} channelMidId: {} channelRequestId: {} channelTransactionId: {} platformStatus: {} logicalTable: {} affectedRows: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                resultDTO.getTransactionId(),
                resultDTO.getOperationId(),
                resultDTO.getTransactionType(),
                resultDTO.getCurrency(),
                resultDTO.getAmount(),
                routeResultDTO == null ? null : routeResultDTO.getChannelCode(),
                routeResultDTO == null ? null : routeResultDTO.getMidConfigId(),
                invokeResultDTO.getRequestId(),
                invokeResultDTO.getChannelRequest().getChannelTransactionId(),
                resultDTO.getStatus(),
                TRANSACTION_CHANNEL_REQUEST_TABLE + "," + TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE,
                requestRows + interactionRows);
    }

    /**
     * 记录交易支付工具摘要。
     * <p>
     * 首次授权/支付从商户请求卡信息提取 BIN、last4 和 hash；后续请款、退款、撤销通常不再上传卡号，
     * 需要从原生命周期支付工具摘要继承，确保交易查询列表每个动作都能展示卡品牌和卡号摘要。
     *
     * @param commandDTO           支付核心交易命令
     * @param resultDTO           交易结果
     * @param operationId         平台内部生命周期关联标识
     * @param transactionId       平台当前交易 ID
     * @param transactionDateTime 交易业务时间
     * @param now                 当前处理时间
     */
    private void recordPaymentMethodInfo(PaymentCreateCommandDTO commandDTO,
                                         PaymentCreateResultDTO resultDTO,
                                         String operationId,
                                         String transactionId,
                                         LocalDateTime transactionDateTime,
                                         LocalDateTime now) {
        recordPaymentMethodInfo(commandDTO, resultDTO, operationId, transactionId, transactionDateTime, now, null);
    }

    /**
     * 记录交易支付工具摘要，并可从原主单继承卡摘要。
     *
     * @param commandDTO          支付核心交易命令
     * @param resultDTO           交易结果
     * @param operationId         平台内部生命周期关联标识
     * @param transactionId       平台当前交易 ID
     * @param transactionDateTime 交易业务时间
     * @param now                 当前处理时间
     * @param sourceOrderDO       原生命周期主单，后续动作可为空
     */
    private void recordPaymentMethodInfo(PaymentCreateCommandDTO commandDTO,
                                         PaymentCreateResultDTO resultDTO,
                                         String operationId,
                                         String transactionId,
                                         LocalDateTime transactionDateTime,
                                         LocalDateTime now,
                                         TransactionOrderDO sourceOrderDO) {
        if (transactionPaymentMethodInfoMapper == null || !StringUtils.hasText(transactionId)) {
            return;
        }
        TransactionPaymentMethodInfoDO infoDO = new TransactionPaymentMethodInfoDO();
        infoDO.setPaymentInfoId(PaymentOrderNoGenerator.nextOrderNo(PAYMENT_INFO_PREFIX, transactionDateTime));
        infoDO.setTransactionId(transactionId);
        infoDO.setOperationId(operationId);
        infoDO.setPaymentMethod(resolvePaymentMethod(commandDTO));
        infoDO.setPaymentBrand(resolvePaymentBrand(commandDTO, resultDTO, sourceOrderDO));
        fillCardSummary(infoDO, commandDTO);
        fillChannelPaymentMethodSummary(infoDO, resultDTO);
        inheritPaymentMethodInfo(infoDO, sourceOrderDO, transactionDateTime);
        fillTransactionTime(infoDO, transactionDateTime);
        infoDO.setCreateTime(now);
        infoDO.setUpdateTime(now);
        int affectedRows = transactionPaymentMethodInfoMapper.insertLogical(infoDO);
        log.info("event: PAYMENT_METHOD_INFO_SAVED stage=PAYMENT_METHOD traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} paymentMethod: {} paymentBrand: {} cardBin: {} cardLast4: {} logicalTable: {} affectedRows: {}",
                TraceContext.getTraceId(),
                commandDTO == null ? null : commandDTO.getMerchantId(),
                commandDTO == null ? null : commandDTO.getMerchantOrderNo(),
                transactionId,
                operationId,
                resultDTO == null ? null : resultDTO.getTransactionType(),
                infoDO.getPaymentMethod(),
                infoDO.getPaymentBrand(),
                infoDO.getCardBin(),
                infoDO.getCardLast4(),
                TRANSACTION_PAYMENT_METHOD_INFO_TABLE,
                affectedRows);
    }

    /**
     * 从原生命周期支付工具摘要继承卡信息。
     * <p>
     * 请款、退款、撤销等后续动作不会再次携带 PAN/CVV，后台列表和详情需要展示同一张卡的摘要；
     * 这里只复制脱敏或 hash 后的信息，禁止恢复完整卡号。
     *
     * @param target              当前动作支付工具摘要
     * @param sourceOrderDO       原生命周期主单
     * @param transactionDateTime 当前动作交易时间
     */
    private void inheritPaymentMethodInfo(TransactionPaymentMethodInfoDO target,
                                          TransactionOrderDO sourceOrderDO,
                                          LocalDateTime transactionDateTime) {
        if (sourceOrderDO == null || hasCardSummary(target) || !StringUtils.hasText(sourceOrderDO.getOperationId())) {
            return;
        }
        TransactionPaymentMethodInfoDO source = findSourcePaymentMethodInfo(sourceOrderDO, transactionDateTime);
        if (source == null) {
            return;
        }
        target.setPaymentMethod(firstText(target.getPaymentMethod(), source.getPaymentMethod(), sourceOrderDO.getPaymentMethod()));
        target.setPaymentBrand(firstText(target.getPaymentBrand(), source.getPaymentBrand(), sourceOrderDO.getPaymentBrand()));
        target.setCardBin(source.getCardBin());
        target.setCardLast4(source.getCardLast4());
        target.setCardNumberMasked(source.getCardNumberMasked());
        target.setCardholderNameMasked(source.getCardholderNameMasked());
        target.setExpiryMonth(source.getExpiryMonth());
        target.setExpiryYear(source.getExpiryYear());
        target.setTokenId(source.getTokenId());
        target.setWalletType(source.getWalletType());
        target.setPaymentAccountHash(source.getPaymentAccountHash());
        target.setIssuerCountry(source.getIssuerCountry());
        target.setFundingMethod(source.getFundingMethod());
        target.setThreeDsIndicator(source.getThreeDsIndicator());
        target.setCscResult(source.getCscResult());
        target.setAvsResult(source.getAvsResult());
    }

    /**
     * 查询原生命周期最早可用的支付工具摘要。
     *
     * @param sourceOrderDO             原生命周期主单
     * @param actionTransactionDateTime 当前动作交易时间
     * @return 原支付工具摘要
     */
    private TransactionPaymentMethodInfoDO findSourcePaymentMethodInfo(TransactionOrderDO sourceOrderDO,
                                                                       LocalDateTime actionTransactionDateTime) {
        LocalDateTime beginTime = sourceOrderDO.getTransactionDateTime();
        LocalDateTime endTime = actionTransactionDateTime == null ? LocalDateTime.now() : actionTransactionDateTime;
        List<TransactionPaymentMethodInfoDO> rows = transactionPaymentMethodInfoMapper.selectByOperationId(
                sourceOrderDO.getOperationId(), logicalBegin(beginTime), exclusiveEnd(endTime));
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.stream()
                .filter(this::hasCardSummary)
                .findFirst()
                .orElse(rows.get(0));
    }

    /**
     * 判断支付工具摘要是否包含卡号摘要。
     *
     * @param infoDO 支付工具摘要
     * @return true 表示存在 BIN、last4 或脱敏卡号
     */
    private boolean hasCardSummary(TransactionPaymentMethodInfoDO infoDO) {
        return infoDO != null && (StringUtils.hasText(infoDO.getCardBin())
                || StringUtils.hasText(infoDO.getCardLast4())
                || StringUtils.hasText(infoDO.getCardNumberMasked()));
    }

    /**
     * 解析支付品牌；首次交易优先使用支付核心识别结果，后续动作继承原生命周期主单。
     *
     * @param commandDTO     支付核心交易命令
     * @param sourceOrderDO  原生命周期主单，首次交易为空
     * @return 统一支付品牌枚举
     */
    private String resolvePaymentBrand(PaymentCreateCommandDTO commandDTO,
                                       PaymentCreateResultDTO resultDTO,
                                       TransactionOrderDO sourceOrderDO) {
        return firstText(
                resultDTO == null ? null : resultDTO.getPaymentBrand(),
                commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getCardBrand(),
                sourceOrderDO == null ? null : sourceOrderDO.getPaymentBrand());
    }

    /**
     * 使用渠道返回的支付工具摘要补充落库字段。
     * <p>
     * MPGS 等渠道会在响应中返回卡品牌、资金类型、发卡国家、CSC 校验结果和脱敏卡号；
     * 这些字段用于后台查询、争议和对账排查，禁止从中恢复完整 PAN。
     *
     * @param infoDO    支付工具摘要
     * @param resultDTO 当前交易结果
     */
    private void fillChannelPaymentMethodSummary(TransactionPaymentMethodInfoDO infoDO,
                                                 PaymentCreateResultDTO resultDTO) {
        if (infoDO == null || resultDTO == null) {
            return;
        }
        infoDO.setPaymentBrand(firstText(resultDTO.getPaymentBrand(), infoDO.getPaymentBrand()));
        infoDO.setCardNumberMasked(firstText(resultDTO.getCardNumberMasked(), infoDO.getCardNumberMasked()));
        fillCardPartsFromMaskedNumber(infoDO, resultDTO.getCardNumberMasked());
        infoDO.setExpiryMonth(firstText(resultDTO.getExpiryMonth(), infoDO.getExpiryMonth()));
        infoDO.setExpiryYear(firstText(resultDTO.getExpiryYear(), infoDO.getExpiryYear()));
        infoDO.setIssuerCountry(firstText(resultDTO.getIssuerCountry(), infoDO.getIssuerCountry()));
        infoDO.setFundingMethod(firstText(resultDTO.getFundingMethod(), infoDO.getFundingMethod()));
        infoDO.setCscResult(firstText(resultDTO.getCscResult(), infoDO.getCscResult()));
    }

    /**
     * 构造cardpartsfrom脱敏number对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param infoDO info DO 输入值，参与 infodo 的查询、校验、转换、写入或日志摘要
     * @param cardNumberMasked 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
    private void fillCardPartsFromMaskedNumber(TransactionPaymentMethodInfoDO infoDO, String cardNumberMasked) {
        if (!StringUtils.hasText(cardNumberMasked)) {
            return;
        }
        String digits = cardNumberMasked.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
            infoDO.setCardBin(firstText(infoDO.getCardBin(), digits.substring(0, 6)));
            infoDO.setCardLast4(firstText(infoDO.getCardLast4(), digits.substring(digits.length() - 4)));
        }
    }

    /**
     * 返回第一个有文本内容的字符串。
     *
     * @param values 候选字符串
     * @return 第一个非空白字符串
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 从商户请求卡信息提取脱敏卡摘要。
     * <p>
     * 卡号原文只允许在内存中流向渠道，落库只保存 BIN、last4、脱敏卡号和 SHA-256 hash。
     *
     * @param infoDO     支付工具摘要
     * @param commandDTO 支付核心交易命令
     */
    private void fillCardSummary(TransactionPaymentMethodInfoDO infoDO, PaymentCreateCommandDTO commandDTO) {
        PaymentCreateCommandDTO.CardInfoDTO cardInfoDTO = commandDTO.getCardInfo();
        if (cardInfoDTO == null || !StringUtils.hasText(cardInfoDTO.getCardNo())) {
            return;
        }
        String cardNo = cardInfoDTO.getCardNo().trim();
        infoDO.setCardBin(cardNo.length() >= 6 ? cardNo.substring(0, Math.min(8, cardNo.length())) : cardNo);
        infoDO.setCardLast4(cardNo.length() >= 4 ? cardNo.substring(cardNo.length() - 4) : null);
        infoDO.setCardNumberMasked(SensitiveDataMaskUtils.maskPan(cardNo));
        infoDO.setPaymentAccountHash(sha256Hex(cardNo));
        infoDO.setExpiryMonth(cardInfoDTO.getExpirationMonth());
        infoDO.setExpiryYear(cardInfoDTO.getExpirationYear());
        if (commandDTO.getThreeDsInfo() != null) {
            infoDO.setThreeDsIndicator(commandDTO.getThreeDsInfo().getEci());
        }
    }

    /**
     * 构造渠道请求摘要。
     * <p>
     * 该记录用于后台展示、查询勾兑关联和渠道问题排查，只保存脱敏 URL、请求状态、渠道订单号和响应摘要，
     * 不保存完整卡号、CVV 或渠道密钥。
     *
     * @param commandDTO 交易命令
     * @param routeResultDTO 渠道路由结果
     * @param invokeResultDTO 渠道调用上下文
     * @param resultDTO 平台交易结果
     * @param now 当前处理时间
     * @return 渠道请求摘要
     */
    private TransactionChannelRequestDO buildChannelRequest(PaymentCreateCommandDTO commandDTO,
                                                            PaymentRouteResultDTO routeResultDTO,
                                                            PaymentChannelInvokeResultDTO invokeResultDTO,
                                                            PaymentCreateResultDTO resultDTO,
                                                            LocalDateTime now) {
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        TransactionChannelRequestDO requestDO = new TransactionChannelRequestDO();
        requestDO.setRequestId(invokeResultDTO.getRequestId());
        requestDO.setTransactionId(resultDTO.getTransactionId());
        requestDO.setOperationId(resultDTO.getOperationId());
        requestDO.setChannelId(routeResultDTO == null ? null : routeResultDTO.getChannelId());
        requestDO.setChannelCode(resolveChannelCode(routeResultDTO, response));
        requestDO.setChannelMidConfigId(routeResultDTO == null ? null : routeResultDTO.getMidConfigId());
        requestDO.setTransactionType(resultDTO.getTransactionType());
        requestDO.setRequestScene(invokeResultDTO.getRequestScene());
        requestDO.setChannelMatchFlag(0);
        requestDO.setRequestStatus(invokeResultDTO.getRequestStatus());
        requestDO.setHttpMethod(resolveChannelHttpMethod(invokeResultDTO));
        requestDO.setRequestUrlMasked(resolveChannelRequestUrl(invokeResultDTO));
        requestDO.setRequestCurrency(invokeResultDTO.getChannelRequest().getCurrency());
        requestDO.setRequestAmount(invokeResultDTO.getChannelRequest().getAmount());
        requestDO.setChannelOrderNo(response == null ? invokeResultDTO.getChannelRequest().getChannelOrderNo() : response.getChannelOrderNo());
        requestDO.setChannelTransactionId(response == null ? invokeResultDTO.getChannelRequest().getChannelTransactionId() : response.getChannelTransactionId());
        Map<String, String> rawResponse = response == null ? Map.of() : response.getRawResponse();
        requestDO.setGatewayResult(rawResponse.get("result"));
        requestDO.setGatewayCode(rawResponse.get("gatewayCode"));
        requestDO.setAcquirerCode(rawResponse.get("acquirerCode"));
        requestDO.setAcquirerMessage(rawResponse.get("acquirerMessage"));
        requestDO.setChannelStatus(response == null ? null : response.getRawChannelStatus());
        requestDO.setPlatformSuccess(PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus()) ? 1 : 0);
        requestDO.setPlatformResultCode(resultDTO.getStatus());
        requestDO.setPlatformFailReason(StringUtils.hasText(invokeResultDTO.getExceptionMessage())
                ? invokeResultDTO.getExceptionMessage() : resultDTO.getFailReasonCode());
        requestDO.setRequestStartTime(invokeResultDTO.getRequestStartTime() == null ? now : invokeResultDTO.getRequestStartTime());
        requestDO.setResponseTime(invokeResultDTO.getResponseTime());
        requestDO.setDurationMillis(invokeResultDTO.getDurationMillis());
        fillTransactionTime(requestDO, commandDTO.getTransactionDateTime());
        requestDO.setVersion(INITIAL_VERSION);
        requestDO.setDeleted(NOT_DELETED);
        requestDO.setCreateTime(now);
        requestDO.setUpdateTime(now);
        return requestDO;
    }

    /**
     * 构造渠道交互日志。
     * <p>
     * 当前采用一行保存请求和响应，requestBody/responseBody 使用统一 JSON 脱敏后直接落库，
     * 便于后台复制到 JSON 工具排查；完整 PAN、CVV、认证令牌和渠道密钥不得进入该字段。
     *
     * @param commandDTO      支付核心交易命令
     * @param invokeResultDTO 渠道调用上下文
     * @param resultDTO       平台交易结果
     * @param now             当前处理时间
     * @return 渠道交互日志
     */
    private TransactionChannelInteractionLogDO buildChannelInteractionLog(PaymentCreateCommandDTO commandDTO,
                                                                          PaymentChannelInvokeResultDTO invokeResultDTO,
                                                                          PaymentCreateResultDTO resultDTO,
                                                                          LocalDateTime now) {
        TransactionChannelInteractionLogDO logDO = new TransactionChannelInteractionLogDO();
        logDO.setInteractionLogId(PaymentOrderNoGenerator.nextOrderNo(CHANNEL_INTERACTION_LOG_PREFIX, commandDTO.getTransactionDateTime()));
        logDO.setRequestId(invokeResultDTO.getRequestId());
        logDO.setTransactionId(resultDTO.getTransactionId());
        logDO.setOperationId(resultDTO.getOperationId());
        logDO.setChannelCode(invokeResultDTO.getChannelRequest().getChannelCode());
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        if (StringUtils.hasText(invokeResultDTO.getExceptionType())) {
            logDO.setInteractionType("EXCEPTION");
        } else if (response == null && invokeResultDTO.getResponseTime() == null && invokeResultDTO.getDurationMillis() == null) {
            logDO.setInteractionType("REQUEST");
        } else {
            logDO.setInteractionType("REQUEST_RESPONSE");
        }
        logDO.setHttpMethod(resolveChannelHttpMethod(invokeResultDTO));
        logDO.setRequestUrlMasked(resolveChannelRequestUrl(invokeResultDTO));
        logDO.setHttpStatus(resolveChannelHttpStatus(invokeResultDTO));
        logDO.setRequestHeaderJsonMasked(safeLength(channelRawAudit(response, RAW_REQUEST_HEADER_JSON_MASKED), 16_000));
        logDO.setRequestBodyJsonMasked(firstText(
                safeLength(channelRawAudit(response, RAW_REQUEST_BODY_JSON_MASKED), 16_000),
                maskedJson(invokeResultDTO.getChannelRequest())));
        logDO.setResponseHeaderJsonMasked(safeLength(channelRawAudit(response, RAW_RESPONSE_HEADER_JSON_MASKED), 16_000));
        logDO.setResponseBodyJsonMasked(firstText(
                safeLength(channelRawAudit(response, RAW_RESPONSE_BODY_JSON_MASKED), 16_000),
                maskedJson(response)));
        logDO.setExceptionType(invokeResultDTO.getExceptionType());
        logDO.setExceptionMessage(safeLength(invokeResultDTO.getExceptionMessage(), 1024));
        logDO.setDurationMillis(invokeResultDTO.getDurationMillis());
        logDO.setInteractionTime(defaultTime(invokeResultDTO.getResponseTime(),
                defaultTime(invokeResultDTO.getRequestStartTime(), now)));
        fillTransactionTime(logDO, commandDTO.getTransactionDateTime());
        logDO.setCreateTime(now);
        return logDO;
    }

    /**
     * 从渠道统一响应原始字段中解析 HTTP 状态码。
     *
     * @param invokeResultDTO 渠道调用上下文
     * @return HTTP 状态码，无法解析时为空
     */
    private Integer resolveChannelHttpStatus(PaymentChannelInvokeResultDTO invokeResultDTO) {
        if (invokeResultDTO == null || invokeResultDTO.getChannelResponse() == null) {
            return null;
        }
        if (invokeResultDTO.getChannelResponse().getHttpStatus() != null) {
            return invokeResultDTO.getChannelResponse().getHttpStatus();
        }
        String httpStatus = invokeResultDTO.getChannelResponse().getRawResponse() == null
                ? null
                : invokeResultDTO.getChannelResponse().getRawResponse().get("httpStatus");
        if (!StringUtils.hasText(httpStatus)) {
            return null;
        }
        try {
            return Integer.valueOf(httpStatus);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 解析resolve渠道HTTPmethod，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param invokeResultDTO invoke Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolveChannelHttpMethod(PaymentChannelInvokeResultDTO invokeResultDTO) {
        if (invokeResultDTO == null) {
            return null;
        }
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        return firstText(response == null ? null : response.getHttpMethod(), invokeResultDTO.getHttpMethod());
    }

    /**
     * 解析resolve渠道请求url，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param invokeResultDTO invoke Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolveChannelRequestUrl(PaymentChannelInvokeResultDTO invokeResultDTO) {
        if (invokeResultDTO == null) {
            return null;
        }
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        return firstText(
                response == null ? null : response.getRequestUrlMasked(),
                channelRawAudit(response, RAW_REQUEST_URL_MASKED),
                invokeResultDTO.getRequestUrlMasked());
    }

    /**
     * 整理渠道rawaudit，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String channelRawAudit(ChannelPaymentResponse response, String key) {
        if (response == null || !StringUtils.hasText(key)) {
            return null;
        }
        if (RAW_REQUEST_HEADER_JSON_MASKED.equals(key)) {
            return firstText(response.getRequestHeaderJsonMasked(), rawResponseValue(response, key));
        }
        if (RAW_REQUEST_BODY_JSON_MASKED.equals(key)) {
            return firstText(response.getRequestBodyJsonMasked(), rawResponseValue(response, key));
        }
        if (RAW_RESPONSE_HEADER_JSON_MASKED.equals(key)) {
            return firstText(response.getResponseHeaderJsonMasked(), rawResponseValue(response, key));
        }
        if (RAW_RESPONSE_BODY_JSON_MASKED.equals(key)) {
            return firstText(response.getResponseBodyJsonMasked(), rawResponseValue(response, key));
        }
        return rawResponseValue(response, key);
    }

    /**
     * 整理raw响应值，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String rawResponseValue(ChannelPaymentResponse response, String key) {
        if (response == null || response.getRawResponse() == null || !StringUtils.hasText(key)) {
            return null;
        }
        return response.getRawResponse().get(key);
    }

    /**
     * 记录交易流程时间轴事件。
     * <p>
     * 时间轴事件只记录业务节点和摘要，不替代渠道交互日志；渠道请求节点必须根据渠道交易状态、
     * raw status 和平台结果判断颜色，不能把 HTTP 调用成功误当交易成功。
     *
     * @param commandDTO       支付核心交易命令
     * @param routeResultDTO   渠道路由结果
     * @param invokeResultDTO  渠道调用上下文
     * @param resultDTO        平台交易结果
     * @param riskDecisionEnum 风控决策
     * @param now              当前处理时间
     */
    private void recordFlowEvents(PaymentCreateCommandDTO commandDTO,
                                  PaymentRouteResultDTO routeResultDTO,
                                  PaymentChannelInvokeResultDTO invokeResultDTO,
                                  PaymentCreateResultDTO resultDTO,
                                  PaymentRiskDecisionEnum riskDecisionEnum,
                                  LocalDateTime now) {
        insertFlowEvent(commandDTO, resultDTO, "API_ACCEPTED", "API", "SUCCESS",
                "API受理", "交易请求已进入支付核心", null, resultDTO.getStatus(), "MERCHANT",
                commandDTO.getMerchantId(), "TRANSACTION", resultDTO.getTransactionId(), null, null, now);
        insertFlowEvent(commandDTO, resultDTO, "RISK_CHECKED", "RISK",
                PaymentRiskDecisionSupport.flowEventStatus(riskDecisionEnum),
                "内风控检查", riskFlowEventContent(commandDTO, riskDecisionEnum),
                null, resultDTO.getStatus(), "SYSTEM", null, "TRANSACTION", resultDTO.getTransactionId(),
                resultDTO.getFailReasonCode(), null, now);
        if (routeResultDTO != null) {
            insertFlowEvent(commandDTO, resultDTO, "ROUTE_SELECTED", "ROUTE", "SUCCESS",
                    "渠道路由", "命中渠道：" + routeResultDTO.getChannelCode(), null, resultDTO.getStatus(),
                    "SYSTEM", null, "CHANNEL_MID", routeResultDTO.getMidConfigId() == null ? null : String.valueOf(routeResultDTO.getMidConfigId()),
                    null, null, now);
        }
        if (invokeResultDTO != null) {
            String channelEventStatus = resolveChannelFlowEventStatus(invokeResultDTO, resultDTO);
            insertFlowEvent(commandDTO, resultDTO, "CHANNEL_CALLED", "CHANNEL",
                    channelEventStatus,
                    "渠道请求", buildChannelFlowEventContent(invokeResultDTO, resultDTO), null, resultDTO.getStatus(),
                    "SYSTEM", null, "REQUEST", invokeResultDTO.getRequestId(),
                    PaymentTransactionStatusEnum.FAILED.getCode().equals(channelEventStatus) ? resultDTO.getFailReasonCode() : null,
                    PaymentTransactionStatusEnum.FAILED.getCode().equals(channelEventStatus) ? resolveChannelFlowErrorMessage(invokeResultDTO, resultDTO) : null,
                    now);
        }
        String merchantResponseCode = resolveMerchantResponseCode(resultDTO);
        String merchantResponseMessage = resolveMerchantResponseMessage(resultDTO);
        boolean transactionFailed = PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus());
        insertFlowEvent(commandDTO, resultDTO, "STATUS_RECORDED", "STATUS", resolveResultFlowEventStatus(resultDTO),
                resultFlowEventName(resultDTO.getStatus()),
                resultFlowEventContent(resultDTO.getStatus(), merchantResponseCode, merchantResponseMessage),
                null, resultDTO.getStatus(),
                "SYSTEM", null, "STATUS_HISTORY", resultDTO.getTransactionId(),
                transactionFailed ? merchantResponseCode : null,
                transactionFailed ? merchantResponseMessage : null,
                now);
    }

    /**
     * 返回交易结果流程节点名称。
     *
     * @param transactionStatus 平台交易状态
     * @return 面向管理端时间轴的交易结果名称
     */
    private String resultFlowEventName(String transactionStatus) {
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(transactionStatus)) {
            return "交易成功";
        }
        if (PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)) {
            return "交易失败";
        }
        if (PaymentTransactionStatusEnum.PENDING.getCode().equals(transactionStatus)) {
            return "交易待确认";
        }
        return "交易处理中";
    }

    /**
     * 构造交易结果流程摘要，响应码和响应说明必须与返回商户的内容一致。
     *
     * @param transactionStatus       平台交易状态
     * @param merchantResponseCode    商户响应码
     * @param merchantResponseMessage 商户响应说明
     * @return 时间轴结果摘要
     */
    private String resultFlowEventContent(String transactionStatus,
                                          String merchantResponseCode,
                                          String merchantResponseMessage) {
        if (StringUtils.hasText(merchantResponseCode) && StringUtils.hasText(merchantResponseMessage)) {
            return merchantResponseCode + "：" + merchantResponseMessage;
        }
        if (StringUtils.hasText(merchantResponseCode)) {
            return merchantResponseCode;
        }
        if (StringUtils.hasText(merchantResponseMessage)) {
            return merchantResponseMessage;
        }
        return "交易状态：" + transactionStatus;
    }

    /**
     * 解析渠道请求时间轴状态。
     * <p>
     * MPGS 等渠道可能 HTTP 成功但业务失败，必须结合 channelTradeStatus、rawChannelStatus
     * 和平台最终状态判断，否则交易失败时前端时间轴会错误显示绿色成功。
     *
     * @param invokeResultDTO 渠道调用上下文
     * @param resultDTO       平台交易结果
     * @return transaction_status 字典值
     */
    private String resolveChannelFlowEventStatus(PaymentChannelInvokeResultDTO invokeResultDTO,
                                                 PaymentCreateResultDTO resultDTO) {
        if (invokeResultDTO == null) {
            return PaymentTransactionStatusEnum.PROCESSING.getCode();
        }
        if (StringUtils.hasText(invokeResultDTO.getExceptionType())
                || "FAILED".equals(invokeResultDTO.getRequestStatus())
                || "TIMEOUT".equals(invokeResultDTO.getRequestStatus())) {
            return PaymentTransactionStatusEnum.FAILED.getCode();
        }
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        if (response != null && ("FAILED".equals(response.getChannelTradeStatus())
                || "ERROR".equals(response.getRawChannelStatus())
                || "DECLINED".equals(response.getRawChannelStatus()))) {
            return PaymentTransactionStatusEnum.FAILED.getCode();
        }
        if (resultDTO != null && PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus())) {
            return PaymentTransactionStatusEnum.FAILED.getCode();
        }
        if (response != null && "SUCCESS".equals(response.getChannelTradeStatus())) {
            return PaymentTransactionStatusEnum.SUCCESS.getCode();
        }
        return resultDTO == null || !StringUtils.hasText(resultDTO.getStatus())
                ? PaymentTransactionStatusEnum.PROCESSING.getCode()
                : resultDTO.getStatus();
    }

    /**
     * 解析状态入库节点的时间轴状态。
     *
     * @param resultDTO 平台交易结果
     * @return transaction_status 字典值
     */
    private String resolveResultFlowEventStatus(PaymentCreateResultDTO resultDTO) {
        if (resultDTO == null || !StringUtils.hasText(resultDTO.getStatus())) {
            return PaymentTransactionStatusEnum.PROCESSING.getCode();
        }
        return resultDTO.getStatus();
    }

    /**
     * 构造渠道请求时间轴摘要。
     *
     * @param invokeResultDTO 渠道调用上下文
     * @param resultDTO       平台交易结果
     * @return 面向后台的渠道请求摘要
     */
    private String buildChannelFlowEventContent(PaymentChannelInvokeResultDTO invokeResultDTO,
                                                PaymentCreateResultDTO resultDTO) {
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        StringBuilder builder = new StringBuilder("渠道请求状态：")
                .append(invokeResultDTO.getRequestStatus() == null ? "UNKNOWN" : invokeResultDTO.getRequestStatus());
        if (response != null && StringUtils.hasText(response.getRawChannelStatus())) {
            builder.append("；渠道原始状态：").append(response.getRawChannelStatus());
        }
        if (response != null && StringUtils.hasText(response.getChannelTradeStatus())) {
            builder.append("；渠道交易状态：").append(response.getChannelTradeStatus());
        }
        if (response != null && StringUtils.hasText(response.getChannelResponseCode())) {
            builder.append("；渠道响应码：").append(response.getChannelResponseCode());
        }
        if (resultDTO != null && StringUtils.hasText(resultDTO.getStatus())) {
            builder.append("；平台交易状态：").append(resultDTO.getStatus());
        }
        return builder.toString();
    }

    /**
     * 解析渠道请求时间轴失败原因。
     *
     * @param invokeResultDTO 渠道调用上下文
     * @param resultDTO       平台交易结果
     * @return 后台可见失败原因
     */
    private String resolveChannelFlowErrorMessage(PaymentChannelInvokeResultDTO invokeResultDTO,
                                                  PaymentCreateResultDTO resultDTO) {
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        return firstText(
                invokeResultDTO.getExceptionMessage(),
                response == null ? null : response.getChannelResponseMessage(),
                resultDTO == null ? null : resultDTO.getFailReasonMessage(),
                resultDTO == null ? null : resultDTO.getFailReasonCode());
    }

    /**
     * 记录商户 OpenAPI 请求与平台同步响应日志。
     * <p>
     * 请求密文只保存摘要和掩码，解密明文保存前必须脱敏；响应明文按商户可见结构生成，
     * 响应密文摘要由 service-openapi 响应加密切面完成后回写。
     *
     * @param commandDTO 支付核心交易命令
     * @param resultDTO  平台交易结果
     * @param now        当前处理时间
     */
    private void recordMerchantApiInteraction(PaymentCreateCommandDTO commandDTO,
                                              PaymentCreateResultDTO resultDTO,
                                              LocalDateTime now) {
        if (transactionMerchantApiInteractionLogMapper == null || !StringUtils.hasText(commandDTO.getMerchantRequestPlainJsonMasked())) {
            return;
        }
        TransactionMerchantApiInteractionLogDO logDO = new TransactionMerchantApiInteractionLogDO();
        logDO.setApiLogId(PaymentOrderNoGenerator.nextOrderNo(MERCHANT_API_LOG_PREFIX, commandDTO.getTransactionDateTime()));
        logDO.setRequestId(commandDTO.getRequestId());
        logDO.setTransactionId(resultDTO.getTransactionId());
        logDO.setOperationId(resultDTO.getOperationId());
        logDO.setMerchantId(commandDTO.getMerchantId());
        logDO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        logDO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        logDO.setApiOperation(resultDTO.getTransactionType());
        logDO.setRequestPath(commandDTO.getOpenApiRequestPath());
        logDO.setRequestTime(defaultTime(commandDTO.getOpenApiRequestTime(), commandDTO.getTransactionDateTime()));
        logDO.setRequestResult(resultDTO.getStatus());
        logDO.setRequestCipherDigest(commandDTO.getRequestFingerprint());
        logDO.setRequestCipherMasked(commandDTO.getMerchantRequestCipherMasked());
        logDO.setRequestPlainJsonMasked(safeLength(commandDTO.getMerchantRequestPlainJsonMasked(), 16_000));
        logDO.setResponseTime(now);
        logDO.setResponseResult(resultDTO.getStatus());
        logDO.setMerchantResponseCode(resolveMerchantResponseCode(resultDTO));
        logDO.setMerchantResponseMessage(resolveMerchantResponseMessage(resultDTO));
        logDO.setResponsePlainJsonMasked(safeLength(maskedJson(merchantVisiblePayload(commandDTO, resultDTO)), 16_000));
        logDO.setDurationMillis(resolveDurationMillis(commandDTO.getOpenApiRequestTime(), now));
        fillTransactionTime(logDO, commandDTO.getTransactionDateTime());
        logDO.setCreateTime(now);
        int affectedRows = transactionMerchantApiInteractionLogMapper.insertLogical(logDO);
        log.info("event: PAYMENT_MERCHANT_API_LOG_SAVED stage=REQUEST_LOG traceId: {} apiLogId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} requestId: {} apiOperation: {} logicalTable: {} affectedRows: {} requestCipherDigest: {} requestCipherMasked: {} requestPlainLength: {} responsePlainLength: {} durationMs: {}",
                TraceContext.getTraceId(),
                logDO.getApiLogId(),
                logDO.getMerchantId(),
                logDO.getMerchantOrderNo(),
                logDO.getTransactionId(),
                logDO.getOperationId(),
                logDO.getRequestId(),
                logDO.getApiOperation(),
                TRANSACTION_MERCHANT_API_INTERACTION_LOG_TABLE,
                affectedRows,
                logDO.getRequestCipherDigest(),
                logDO.getRequestCipherMasked(),
                logDO.getRequestPlainJsonMasked() == null ? 0 : logDO.getRequestPlainJsonMasked().length(),
                logDO.getResponsePlainJsonMasked() == null ? 0 : logDO.getResponsePlainJsonMasked().length(),
                logDO.getDurationMillis());
    }

    /**
     * 计算处理耗时。
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 耗时毫秒，无法计算时为空
     */
    private Integer resolveDurationMillis(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || endTime.isBefore(startTime)) {
            return null;
        }
        long millis = ChronoUnit.MILLIS.between(startTime, endTime);
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    /**
     * 写入单个流程时间轴事件。
     *
     * @param commandDTO     支付核心交易命令
     * @param resultDTO      平台交易结果
     * @param eventType      事件类型
     * @param eventStage     事件阶段
     * @param eventStatus    事件状态
     * @param eventName      事件中文名称
     * @param eventContent   事件摘要
     * @param previousStatus 前置状态
     * @param currentStatus  当前状态
     * @param operatorType   操作方类型
     * @param operatorId     操作方 ID
     * @param referenceType  关联对象类型
     * @param referenceId    关联对象 ID
     * @param errorCode      错误码
     * @param errorMessage   错误摘要
     * @param now            当前处理时间
     */
    private void insertFlowEvent(PaymentCreateCommandDTO commandDTO,
                                 PaymentCreateResultDTO resultDTO,
                                 String eventType,
                                 String eventStage,
                                 String eventStatus,
                                 String eventName,
                                 String eventContent,
                                 String previousStatus,
                                 String currentStatus,
                                 String operatorType,
                                 String operatorId,
                                 String referenceType,
                                 String referenceId,
                                 String errorCode,
                                 String errorMessage,
                                 LocalDateTime now) {
        TransactionFlowEventDO eventDO = new TransactionFlowEventDO();
        eventDO.setFlowEventId(PaymentOrderNoGenerator.nextOrderNo(FLOW_EVENT_PREFIX, commandDTO.getTransactionDateTime()));
        eventDO.setTransactionId(resultDTO.getTransactionId());
        eventDO.setOperationId(resultDTO.getOperationId());
        eventDO.setEventType(eventType);
        eventDO.setEventStage(eventStage);
        eventDO.setEventStatus(eventStatus);
        eventDO.setEventName(eventName);
        eventDO.setEventContent(safeLength(eventContent, 1024));
        eventDO.setPreviousStatus(previousStatus);
        eventDO.setCurrentStatus(currentStatus);
        eventDO.setOperatorType(operatorType);
        eventDO.setOperatorId(operatorId);
        eventDO.setReferenceType(referenceType);
        eventDO.setReferenceId(referenceId);
        eventDO.setErrorCode(errorCode);
        eventDO.setErrorMessage(safeLength(errorMessage, 512));
        eventDO.setEventTime(now);
        fillTransactionTime(eventDO, commandDTO.getTransactionDateTime());
        eventDO.setCreateTime(now);
        transactionFlowEventMapper.insertLogical(eventDO);
    }

    /**
     * 记录后续动作金额变动日志。
     * <p>
     * 增量授权、请款、退款和撤销成功时，主单累计金额会同步更新，本日志保存更新前后快照用于审计。
     *
     * @param recordDTO 后续交易记录上下文
     * @param now       当前处理时间
     */
    private void recordAmountChange(TransactionFollowUpRecordDTO recordDTO, LocalDateTime now) {
        LocalDateTime actionTransactionDateTime = recordDTO.getCommandDTO().getTransactionDateTime();
        TransactionOrderDO source = recordDTO.getSourceOrderDO();
        PaymentCreateResultDTO result = recordDTO.getResultDTO();
        BigDecimal amount = amountFromResult(result, source);
        TransactionAmountChangeLogDO logDO = new TransactionAmountChangeLogDO();
        logDO.setAmountChangeId(PaymentOrderNoGenerator.nextOrderNo(AMOUNT_CHANGE_PREFIX, actionTransactionDateTime));
        logDO.setTransactionId(result.getTransactionId());
        logDO.setOperationId(source.getOperationId());
        logDO.setSourceTransactionId(result.getSourceTransactionId());
        logDO.setChangeType(result.getTransactionType());
        logDO.setAmountCurrency(source.getTransactionCurrency());
        logDO.setChangeAmount(amount);
        fillAmountBeforeAfter(logDO, source, result.getTransactionType(), amount);
        logDO.setChangeReason("交易动作成功：" + result.getTransactionType());
        logDO.setChangeTime(now);
        fillTransactionTime(logDO, actionTransactionDateTime);
        logDO.setCreateTime(now);
        int affectedRows = transactionAmountChangeLogMapper.insertLogical(logDO);
        log.info("event: PAYMENT_AMOUNT_CHANGE_LOG_SAVED stage=AMOUNT_UPDATE traceId: {} transactionId: {} operationId: {} sourceTransactionId: {} transactionType: {} currency: {} amount: {} logicalTable: {} affectedRows: {} authorizedBefore: {} authorizedAfter: {} capturedBefore: {} capturedAfter: {} refundedBefore: {} refundedAfter: {} availableCaptureBefore: {} availableCaptureAfter: {} availableRefundBefore: {} availableRefundAfter: {}",
                TraceContext.getTraceId(),
                result.getTransactionId(),
                source.getOperationId(),
                result.getSourceTransactionId(),
                result.getTransactionType(),
                logDO.getAmountCurrency(),
                logDO.getChangeAmount(),
                TRANSACTION_AMOUNT_CHANGE_LOG_TABLE,
                affectedRows,
                logDO.getAuthorizedBefore(),
                logDO.getAuthorizedAfter(),
                logDO.getCapturedBefore(),
                logDO.getCapturedAfter(),
                logDO.getRefundedBefore(),
                logDO.getRefundedAfter(),
                logDO.getAvailableCaptureBefore(),
                logDO.getAvailableCaptureAfter(),
                logDO.getAvailableRefundBefore(),
                logDO.getAvailableRefundAfter());
    }

    /**
     * 记录渠道回调确认后的金额变动日志。
     *
     * @param operationDO    被渠道回调确认的交易动作
     * @param sourceOrderDO  所属生命周期主单
     * @param now            当前处理时间
     */
    private void recordAmountChange(TransactionOperationDO operationDO,
                                    TransactionOrderDO sourceOrderDO,
                                    LocalDateTime now) {
        LocalDateTime actionTransactionDateTime = operationDO.getTransactionDateTime();
        BigDecimal amount = operationDO.getTransactionAmount() == null ? BigDecimal.ZERO : operationDO.getTransactionAmount();
        TransactionAmountChangeLogDO logDO = new TransactionAmountChangeLogDO();
        logDO.setAmountChangeId(PaymentOrderNoGenerator.nextOrderNo(AMOUNT_CHANGE_PREFIX, actionTransactionDateTime));
        logDO.setTransactionId(operationDO.getTransactionId());
        logDO.setOperationId(sourceOrderDO.getOperationId());
        logDO.setSourceTransactionId(operationDO.getSourceTransactionId());
        logDO.setChangeType(operationDO.getTransactionType());
        logDO.setAmountCurrency(sourceOrderDO.getTransactionCurrency());
        logDO.setChangeAmount(amount);
        fillAmountBeforeAfter(logDO, sourceOrderDO, operationDO.getTransactionType(), amount);
        logDO.setChangeReason("渠道回调确认交易动作成功：" + operationDO.getTransactionType());
        logDO.setChangeTime(now);
        fillTransactionTime(logDO, actionTransactionDateTime);
        logDO.setCreateTime(now);
        int affectedRows = transactionAmountChangeLogMapper.insertLogical(logDO);
        log.info("event: PAYMENT_AMOUNT_CHANGE_LOG_SAVED stage=CALLBACK_AMOUNT_UPDATE traceId: {} transactionId: {} operationId: {} sourceTransactionId: {} transactionType: {} currency: {} amount: {} logicalTable: {} affectedRows: {} authorizedBefore: {} authorizedAfter: {} capturedBefore: {} capturedAfter: {} refundedBefore: {} refundedAfter: {} availableCaptureBefore: {} availableCaptureAfter: {} availableRefundBefore: {} availableRefundAfter: {}",
                TraceContext.getTraceId(),
                operationDO.getTransactionId(),
                sourceOrderDO.getOperationId(),
                operationDO.getSourceTransactionId(),
                operationDO.getTransactionType(),
                logDO.getAmountCurrency(),
                logDO.getChangeAmount(),
                TRANSACTION_AMOUNT_CHANGE_LOG_TABLE,
                affectedRows,
                logDO.getAuthorizedBefore(),
                logDO.getAuthorizedAfter(),
                logDO.getCapturedBefore(),
                logDO.getCapturedAfter(),
                logDO.getRefundedBefore(),
                logDO.getRefundedAfter(),
                logDO.getAvailableCaptureBefore(),
                logDO.getAvailableCaptureAfter(),
                logDO.getAvailableRefundBefore(),
                logDO.getAvailableRefundAfter());
    }

    /**
     * 填充金额变动前后快照。
     *
     * @param logDO           金额变动日志
     * @param source          原生命周期主单
     * @param transactionType 当前动作类型
     * @param amount          当前动作金额
     */
    private void fillAmountBeforeAfter(TransactionAmountChangeLogDO logDO,
                                       TransactionOrderDO source,
                                       String transactionType,
                                       BigDecimal amount) {
        BigDecimal authorized = zeroIfNull(source.getAuthorizedAmount());
        BigDecimal captured = zeroIfNull(source.getCapturedAmount());
        BigDecimal refunded = zeroIfNull(source.getRefundedAmount());
        BigDecimal availableCapture = zeroIfNull(source.getAvailableCaptureAmount());
        BigDecimal availableRefund = zeroIfNull(source.getAvailableRefundAmount());
        logDO.setAuthorizedBefore(authorized);
        logDO.setCapturedBefore(captured);
        logDO.setRefundedBefore(refunded);
        logDO.setAvailableCaptureBefore(availableCapture);
        logDO.setAvailableRefundBefore(availableRefund);
        logDO.setAuthorizedAfter(authorized);
        logDO.setCapturedAfter(captured);
        logDO.setRefundedAfter(refunded);
        logDO.setAvailableCaptureAfter(availableCapture);
        logDO.setAvailableRefundAfter(availableRefund);
        if (PaymentTransactionTypeEnum.CAPTURE.getCode().equals(transactionType)
                || PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION.getCode().equals(transactionType)) {
            logDO.setCapturedAfter(captured.add(amount));
            logDO.setAvailableCaptureAfter(availableCapture.subtract(amount));
            logDO.setAvailableRefundAfter(availableRefund.add(amount));
        } else if (PaymentTransactionTypeEnum.REFUND.getCode().equals(transactionType)) {
            logDO.setRefundedAfter(refunded.add(amount));
            logDO.setAvailableRefundAfter(availableRefund.subtract(amount));
        } else if (PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode().equals(transactionType)) {
            logDO.setAuthorizedAfter(authorized.add(amount));
            logDO.setAvailableCaptureAfter(availableCapture.add(amount));
        } else if (PaymentTransactionTypeEnum.VOID.getCode().equals(transactionType)) {
            logDO.setAvailableCaptureAfter(BigDecimal.ZERO);
        }
    }

    /**
     * 根据商户回调地址创建商户通知任务。
     * <p>
     * 非终态交易只落通知任务但不设置 nextRetryTime；交易进入 SUCCESS/FAILED 后再由 MQ/定时任务触发通知。
     *
     * @param commandDTO 支付核心交易命令
     * @param resultDTO  平台交易结果
     * @param now        当前处理时间
     */
    private void recordMerchantNotificationIfNeeded(PaymentCreateCommandDTO commandDTO,
                                                    PaymentCreateResultDTO resultDTO,
                                                    LocalDateTime now) {
        String callbackUrl = resolveCallbackUrl(commandDTO);
        if (!StringUtils.hasText(callbackUrl)) {
            return;
        }
        TransactionMerchantNotificationDO notificationDO = new TransactionMerchantNotificationDO();
        notificationDO.setNotifyId(PaymentOrderNoGenerator.nextOrderNo(MERCHANT_NOTIFICATION_PREFIX, commandDTO.getTransactionDateTime()));
        notificationDO.setTransactionId(resultDTO.getTransactionId());
        notificationDO.setOperationId(resultDTO.getOperationId());
        notificationDO.setMerchantId(commandDTO.getMerchantId());
        notificationDO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        notificationDO.setNotifyType(resultDTO.getTransactionType() + "_RESULT");
        notificationDO.setEventType("TRANSACTION_CREATED");
        notificationDO.setNotifyStatus(NOTIFY_STATUS_INIT);
        // 实际回调地址只保存在数据库任务快照中供 service-data 投递；日志和查询继续使用脱敏字段，禁止写入 Redis。
        notificationDO.setNotifyConfigSnapshotJson(JsonUtils.toJsonString(Map.of("callbackUrl", callbackUrl)));
        notificationDO.setTargetUrlHash(sha256Hex(callbackUrl));
        notificationDO.setTargetUrlMasked(maskUrl(callbackUrl));
        notificationDO.setPayloadJsonMasked(maskedJson(merchantVisiblePayload(commandDTO, resultDTO)));
        notificationDO.setLastAttemptNo(0);
        notificationDO.setMaxRetryCount(DEFAULT_NOTIFY_MAX_RETRY_COUNT);
        notificationDO.setNextRetryTime(isNonTerminal(resultDTO) ? null : now);
        fillTransactionTime(notificationDO, commandDTO.getTransactionDateTime());
        notificationDO.setVersion(INITIAL_VERSION);
        notificationDO.setDeleted(NOT_DELETED);
        notificationDO.setCreateTime(now);
        notificationDO.setUpdateTime(now);
        int affectedRows = transactionMerchantNotificationMapper.insertLogical(notificationDO);
        log.info("event: PAYMENT_MERCHANT_NOTIFY_CREATED stage=NOTIFY_CREATE traceId: {} notifyId: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} callbackUrl: {} notifyStatus: {} nextRetryTime: {} logicalTable: {} affectedRows: {}",
                TraceContext.getTraceId(),
                notificationDO.getNotifyId(),
                notificationDO.getTransactionId(),
                notificationDO.getOperationId(),
                notificationDO.getMerchantId(),
                notificationDO.getMerchantOrderNo(),
                notificationDO.getTargetUrlMasked(),
                notificationDO.getNotifyStatus(),
                notificationDO.getNextRetryTime(),
                TRANSACTION_MERCHANT_NOTIFICATION_TABLE,
                affectedRows);
    }

    /**
     * 构造商户可见的交易结果载荷。
     * <p>
     * 该结构同时用于同步 OpenAPI 响应日志和异步商户通知，字段命名与商户接入文档保持一致；
     * 内部 operation_id、渠道交易 ID、DCC/EDC 布尔标识不进入商户载荷。
     *
     * @param commandDTO 商户请求命令
     * @param resultDTO  交易处理结果
     * @return 已剔除空字段的商户可见载荷
     */
    private Map<String, Object> merchantVisiblePayload(PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "merchantInfo", merchantInfoPayload(commandDTO, resultDTO));
        putIfPresent(payload, "orderInfo", orderInfoPayload(commandDTO, resultDTO));
        putIfPresent(payload, "transactionInfo", transactionInfoPayload(commandDTO, resultDTO));
        putIfPresent(payload, "billingInfo", billingInfoPayload(commandDTO, resultDTO));
        return compactMap(payload);
    }

/**
 * 整理商户可见payload，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
 * @param targetTransactionStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param failReasonCode fail Reason Code 输入值，参与 failreason编码 的查询、校验、转换、写入或日志摘要
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
 */
    private Map<String, Object> merchantVisiblePayload(TransactionOperationDO operationDO,
                                                       String targetTransactionStatus,
                                                       String failReasonCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> merchantInfo = new LinkedHashMap<>();
        putIfPresent(merchantInfo, "merchantId", operationDO.getMerchantId());
        putIfPresent(payload, "merchantInfo", merchantInfo);

        Map<String, Object> orderInfo = new LinkedHashMap<>();
        putIfPresent(orderInfo, "orderNo", operationDO.getMerchantOrderNo());
        putIfPresent(orderInfo, "orderId", firstText(operationDO.getMerchantOrderId(), operationDO.getMerchantOperationNo()));
        putIfPresent(orderInfo, "amount", operationDO.getLabelAmount());
        putIfPresent(orderInfo, "currency", operationDO.getLabelCurrency());
        putIfPresent(payload, "orderInfo", orderInfo);

        Map<String, Object> transactionInfo = new LinkedHashMap<>();
        putIfPresent(transactionInfo, "code", resolveMerchantResponseCode(targetTransactionStatus));
        putIfPresent(transactionInfo, "message", resolveMerchantResponseMessage(targetTransactionStatus, failReasonCode));
        putIfPresent(transactionInfo, "transactionId", operationDO.getTransactionId());
        putIfPresent(transactionInfo, "sourceTransactionId", operationDO.getSourceTransactionId());
        putIfPresent(transactionInfo, "transactionType", operationDO.getTransactionType());
        putIfPresent(transactionInfo, "transactionStatus", targetTransactionStatus);
        putIfPresent(transactionInfo, "processStage", operationDO.getProcessStage());
        putIfPresent(transactionInfo, "transactionDateTime", offsetDateTimeString(operationDO.getTransactionDateTime(), operationDO.getTransactionTimeZone()));
        putIfPresent(transactionInfo, "authCode", operationDO.getAuthCode());
        putIfPresent(transactionInfo, "arn", operationDO.getAcquirerReferenceNo());
        putIfPresent(transactionInfo, "failReasonCode", merchantVisibleFailureCode(failReasonCode));
        putIfPresent(transactionInfo, "failReasonMessage", merchantVisibleFailureMessage(targetTransactionStatus, failReasonCode));
        putIfPresent(payload, "transactionInfo", transactionInfo);

        Map<String, Object> billingInfo = new LinkedHashMap<>();
        putIfPresent(billingInfo, "labelAmount", operationDO.getLabelAmount());
        putIfPresent(billingInfo, "labelCurrency", operationDO.getLabelCurrency());
        putIfPresent(billingInfo, "transactionAmount", operationDO.getTransactionAmount());
        putIfPresent(billingInfo, "transactionCurrency", operationDO.getTransactionCurrency());
        putIfPresent(billingInfo, "transactionRate", normalizeRate(operationDO.getTransactionRate()));
        putIfPresent(billingInfo, "settlementCurrency", operationDO.getSettlementCurrency());
        putIfPresent(billingInfo, "settlementAmount", operationDO.getSettlementAmount());
        putIfPresent(payload, "billingInfo", billingInfo);
        return compactMap(payload);
    }

    /**
     * 整理商户信息payload，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, Object> merchantInfoPayload(PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        Map<String, Object> merchantInfo = new LinkedHashMap<>();
        putIfPresent(merchantInfo, "merchantId", firstText(resultDTO.getMerchantId(), commandDTO.getMerchantId()));
        putIfPresent(merchantInfo, "subMerchantInfo", subMerchantInfoPayload(resultDTO.getSubMerchantInfo()));
        return compactMap(merchantInfo);
    }

    /**
     * 整理sub商户信息payload，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, Object> subMerchantInfoPayload(PaymentCreateResultDTO.SubMerchantInfoDTO source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> subMerchantInfo = new LinkedHashMap<>();
        putIfPresent(subMerchantInfo, "subId", source.getSubId());
        putIfPresent(subMerchantInfo, "subName", source.getSubName());
        putIfPresent(subMerchantInfo, "subCompanyName", source.getSubCompanyName());
        putIfPresent(subMerchantInfo, "subCountryCode", source.getSubCountryCode());
        putIfPresent(subMerchantInfo, "subState", source.getSubState());
        putIfPresent(subMerchantInfo, "subCity", source.getSubCity());
        putIfPresent(subMerchantInfo, "subStreet", source.getSubStreet());
        putIfPresent(subMerchantInfo, "merchantCategory", source.getMerchantCategory());
        putIfPresent(subMerchantInfo, "intesCode", source.getIntesCode());
        putIfPresent(subMerchantInfo, "chargeType", source.getChargeType());
        return compactMap(subMerchantInfo);
    }

    /**
     * 整理订单信息payload，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, Object> orderInfoPayload(PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        Map<String, Object> orderInfo = new LinkedHashMap<>();
        putIfPresent(orderInfo, "orderNo", firstText(resultDTO.getMerchantOrderNo(), commandDTO.getMerchantOrderNo()));
        putIfPresent(orderInfo, "orderId", firstText(resultDTO.getMerchantOrderId(), commandDTO.getMerchantOrderId()));
        putIfPresent(orderInfo, "amount", firstAmount(resultDTO.getOrderAmount(), commandDTO.getLabelAmount(), commandDTO.getAmount()));
        putIfPresent(orderInfo, "currency", firstText(resultDTO.getOrderCurrency(), commandDTO.getLabelCurrency(), commandDTO.getCurrency()));
        putIfPresent(orderInfo, "totalAuthorizedAmount", resultDTO.getTotalAuthorizedAmount());
        putIfPresent(orderInfo, "totalAuthorizedCancelAmount", resultDTO.getTotalAuthorizedCancelAmount());
        putIfPresent(orderInfo, "totalCapturedAmount", resultDTO.getTotalCapturedAmount());
        putIfPresent(orderInfo, "totalRefundAmount", resultDTO.getTotalRefundAmount());
        putIfPresent(orderInfo, "totalRefuseAmount", resultDTO.getTotalRefuseAmount());
        return compactMap(orderInfo);
    }

    /**
     * 整理交易信息payload，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, Object> transactionInfoPayload(PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        Map<String, Object> transactionInfo = new LinkedHashMap<>();
        putIfPresent(transactionInfo, "code", resolveMerchantResponseCode(resultDTO));
        putIfPresent(transactionInfo, "message", resolveMerchantResponseMessage(resultDTO));
        putIfPresent(transactionInfo, "transactionId", resultDTO.getTransactionId());
        putIfPresent(transactionInfo, "sourceTransactionId", resultDTO.getSourceTransactionId());
        putIfPresent(transactionInfo, "transactionType", resultDTO.getTransactionType());
        putIfPresent(transactionInfo, "transactionStatus", resultDTO.getStatus());
        putIfPresent(transactionInfo, "processStage", resultDTO.getProcessStage());
        putIfPresent(transactionInfo, "transactionDateTime", offsetDateTimeString(resultDTO.getTransactionDateTime(), resultDTO.getTransactionTimeZone()));
        putIfPresent(transactionInfo, "paymentMethod", firstText(resultDTO.getPaymentMethod(), commandDTO.getPaymentMethod()));
        putIfPresent(transactionInfo, "cardBrand", resultDTO.getPaymentBrand());
        putIfPresent(transactionInfo, "cardBin", resultDTO.getCardBin());
        putIfPresent(transactionInfo, "authCode", resultDTO.getAuthCode());
        putIfPresent(transactionInfo, "arn", resultDTO.getAcquirerReferenceNo());
        putIfPresent(transactionInfo, "description", resultDTO.getDescription());
        putIfPresent(transactionInfo, "callbackUrl", resultDTO.getCallbackUrl());
        putIfPresent(transactionInfo, "failReasonCode", merchantVisibleFailureCode(resultDTO.getFailReasonCode()));
        putIfPresent(transactionInfo, "failReasonMessage", merchantVisibleFailureMessage(resultDTO.getStatus(), resultDTO.getFailReasonCode()));
        putIfPresent(transactionInfo, "pendingReasonCode", resultDTO.getPendingReasonCode());
        return compactMap(transactionInfo);
    }

    /**
     * 整理billing信息payload，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, Object> billingInfoPayload(PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        Map<String, Object> billingInfo = new LinkedHashMap<>();
        putIfPresent(billingInfo, "labelAmount", firstAmount(resultDTO.getLabelAmount(), commandDTO.getLabelAmount(), commandDTO.getAmount()));
        putIfPresent(billingInfo, "labelCurrency", firstText(resultDTO.getLabelCurrency(), commandDTO.getLabelCurrency(), commandDTO.getCurrency()));
        putIfPresent(billingInfo, "transactionAmount", firstAmount(resultDTO.getTransactionAmount(), commandDTO.getTransactionAmount(), commandDTO.getAmount()));
        putIfPresent(billingInfo, "transactionCurrency", firstText(resultDTO.getTransactionCurrency(), commandDTO.getTransactionCurrency(), resultDTO.getCurrency(), commandDTO.getCurrency()));
        putIfPresent(billingInfo, "transactionRate", normalizeRate(firstAmount(resultDTO.getTransactionRate(), commandDTO.getTransactionRate(), DEFAULT_TRANSACTION_RATE)));
        putIfPresent(billingInfo, "rateSource", resultDTO.getRateSource());
        putIfPresent(billingInfo, "rateTime", offsetDateTimeString(resultDTO.getRateTime(), resultDTO.getTransactionTimeZone()));
        putIfPresent(billingInfo, "settlementCurrency", resultDTO.getSettlementCurrency());
        putIfPresent(billingInfo, "settlementAmount", resultDTO.getSettlementAmount());
        return compactMap(billingInfo);
    }

    /**
     * 整理非空摘要字段，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && !StringUtils.hasText(text)) {
            return;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return;
        }
        target.put(key, value);
    }

    /**
     * 规范化compactmap，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, Object> compactMap(Map<String, Object> source) {
        Map<String, Object> compact = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (value instanceof Map<?, ?> mapValue) {
                Map<String, Object> nested = new LinkedHashMap<>();
                mapValue.forEach((nestedKey, nestedValue) -> {
                    if (nestedKey != null) {
                        putIfPresent(nested, String.valueOf(nestedKey), nestedValue);
                    }
                });
                putIfPresent(compact, key, nested);
                return;
            }
            putIfPresent(compact, key, value);
        });
        return compact;
    }

    /**
     * 整理首个金额，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param values values 输入值，参与 values 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private BigDecimal firstAmount(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 解析normalize汇率，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private BigDecimal normalizeRate(BigDecimal value) {
        BigDecimal rate = value == null ? DEFAULT_TRANSACTION_RATE : value;
        return rate.setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * 整理offsetdate时间string，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param dateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param timeZone 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String offsetDateTimeString(LocalDateTime dateTime, String timeZone) {
        if (dateTime == null) {
            return null;
        }
        ZoneId zoneId = ZoneId.of(StringUtils.hasText(timeZone) ? timeZone : DEFAULT_TIME_ZONE);
        return dateTime.atZone(zoneId).toOffsetDateTime().toString();
    }

    /**
     * 解析resolve商户响应编码，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolveMerchantResponseCode(PaymentCreateResultDTO resultDTO) {
        if (resultDTO != null && StringUtils.hasText(resultDTO.getMerchantResponseCode())) {
            return resultDTO.getMerchantResponseCode();
        }
        return resolveMerchantResponseCode(resultDTO == null ? null : resultDTO.getStatus());
    }

    /**
     * 解析resolve商户响应说明，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolveMerchantResponseMessage(PaymentCreateResultDTO resultDTO) {
        if (resultDTO != null && StringUtils.hasText(resultDTO.getMerchantResponseMessage())) {
            return resultDTO.getMerchantResponseMessage();
        }
        return resolveMerchantResponseMessage(resultDTO == null ? null : resultDTO.getStatus());
    }

    /**
     * 解析交易失败说明：风控拒绝统一脱敏，其余情况优先使用渠道说明再回退内部失败说明。
     *
     * @param resultDTO       支付处理结果
     * @param channelResponse 渠道响应
     * @return 可持久化并向商户展示的失败说明
     */
    private String resolveFailureMessage(PaymentCreateResultDTO resultDTO, ChannelPaymentResponse channelResponse) {
        if (resultDTO == null) {
            return null;
        }
        if (PaymentRiskDecisionSupport.isRiskRejected(resultDTO.getFailReasonCode())) {
            return PaymentRiskDecisionSupport.MERCHANT_RISK_BLOCKED_MESSAGE;
        }
        if (channelResponse != null && StringUtils.hasText(channelResponse.getChannelResponseMessage())) {
            return channelResponse.getChannelResponseMessage();
        }
        return resultDTO.getFailReasonMessage();
    }

    /**
     * 整理商户可见失败编码，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param failReasonCode fail Reason Code 输入值，参与 failreason编码 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String merchantVisibleFailureCode(String failReasonCode) {
        return StringUtils.hasText(failReasonCode) ? "PAYMENT_FAILED" : null;
    }

    /**
     * 整理商户可见失败说明，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param transactionStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param failReasonCode fail Reason Code 输入值，参与 failreason编码 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String merchantVisibleFailureMessage(String transactionStatus, String failReasonCode) {
        if (!PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)
                || !StringUtils.hasText(failReasonCode)) {
            return null;
        }
        if (PaymentRiskDecisionSupport.isRiskRejected(failReasonCode)) {
            return PaymentRiskDecisionSupport.MERCHANT_RISK_BLOCKED_MESSAGE;
        }
        return "Payment failed. Please use the transaction ID to query details or contact support.";
    }

    /**
     * 解析resolve商户响应编码，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param transactionStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 构造、转换或解析后的业务值
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
     * 解析resolve商户响应说明，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param transactionStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 构造、转换或解析后的业务值
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
     * 根据交易状态和失败原因生成商户响应说明，风控拒绝不暴露具体命中规则。
     *
     * @param transactionStatus 交易状态
     * @param failReasonCode    内部失败原因码
     * @return 商户可见响应说明
     */
    private String resolveMerchantResponseMessage(String transactionStatus, String failReasonCode) {
        if (PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)
                && PaymentRiskDecisionSupport.isRiskRejected(failReasonCode)) {
            return PaymentRiskDecisionSupport.MERCHANT_RISK_BLOCKED_MESSAGE;
        }
        return resolveMerchantResponseMessage(transactionStatus);
    }

    /**
     * 解析resolve渠道编码，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param routeResultDTO route Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @return 构造、转换或解析后的业务值
     */
    private String resolveChannelCode(PaymentRouteResultDTO routeResultDTO, ChannelPaymentResponse response) {
        if (response != null && StringUtils.hasText(response.getChannelCode())) {
            return response.getChannelCode();
        }
        return routeResultDTO == null ? null : routeResultDTO.getChannelCode();
    }

    /**
     * 解析resolve回调url，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolveCallbackUrl(PaymentCreateCommandDTO commandDTO) {
        if (StringUtils.hasText(commandDTO.getCallbackUrl())) {
            return commandDTO.getCallbackUrl();
        }
        if (commandDTO.getTransactionInfo() != null && StringUtils.hasText(commandDTO.getTransactionInfo().getCallbackUrl())) {
            return commandDTO.getTransactionInfo().getCallbackUrl();
        }
        return null;
    }

    /**
     * 脱敏json，返回可安全写入日志或展示的摘要文本。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String maskedJson(Object value) {
        if (value == null) {
            return null;
        }
        return SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(value));
    }

    /**
     * 脱敏url，返回可安全写入日志或展示的摘要文本。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param url 请求地址或路径，用于定位内部服务、渠道接口或商户回调目标
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String maskUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int queryIndex = url.indexOf('?');
        return queryIndex < 0 ? url : url.substring(0, queryIndex) + "?***";
    }

    /**
     * 计算SHA-256 十六进制摘要，用不可逆指纹关联原始内容而不暴露明文。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "SHA-256 digest unavailable");
        }
    }

    /**
     * 整理默认时间，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param fallback fallback 输入值，参与 fallback 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LocalDateTime defaultTime(LocalDateTime value, LocalDateTime fallback) {
        return value == null ? fallback : value;
    }

    /**
     * 规范化length，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param maxLength max Length 输入值，参与 maxlength 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String safeLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 判断 is non terminal 条件是否成立，用于控制 Default Transaction Record Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isNonTerminal(PaymentCreateResultDTO resultDTO) {
        return !PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                && !PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus());
    }

    /**
     * 规范化zeroifnull，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 构造utctime对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param timeZone 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 构造、转换或解析后的业务值
     */
    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime, String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone == null || timeZone.isBlank() ? DEFAULT_TIME_ZONE : timeZone);
        return transactionDateTime.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * 构造交易时间对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void fillTransactionTime(TransactionChannelRequestDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 构造交易时间对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void fillTransactionTime(TransactionChannelInteractionLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 构造交易时间对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void fillTransactionTime(TransactionFlowEventDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 构造交易时间对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void fillTransactionTime(TransactionStatusHistoryDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 构造交易时间对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void fillTransactionTime(TransactionAmountChangeLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 构造交易时间对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void fillTransactionTime(TransactionMerchantNotificationDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 构造交易时间对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void fillTransactionTime(TransactionMerchantApiInteractionLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 构造交易时间对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void fillTransactionTime(TransactionPaymentMethodInfoDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 校验validate输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 支付核心服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    private void validate(PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        if (commandDTO == null
                || resultDTO == null
                || commandDTO.getTransactionDateTime() == null
                || !StringUtils.hasText(commandDTO.getMerchantId())
                || !StringUtils.hasText(commandDTO.getMerchantOrderNo())
                || commandDTO.getAmount() == null
                || !StringUtils.hasText(commandDTO.getCurrency())
                || !StringUtils.hasText(resultDTO.getOperationId())
                || !StringUtils.hasText(resultDTO.getTransactionId())
                || !StringUtils.hasText(resultDTO.getTransactionType())
                || !StringUtils.hasText(resultDTO.getStatus())
                || !StringUtils.hasText(resultDTO.getProcessStage())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /**
     * 校验followuprecord输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 支付核心服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param recordDTO record DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    private void validateFollowUpRecord(TransactionFollowUpRecordDTO recordDTO) {
        if (recordDTO == null
                || recordDTO.getSourceOrderDO() == null
                || recordDTO.getCommandDTO() == null
                || recordDTO.getResultDTO() == null
                || !StringUtils.hasText(recordDTO.getSourceOrderDO().getOperationId())
                || recordDTO.getSourceOrderDO().getTransactionDateTime() == null
                || recordDTO.getCommandDTO().getTransactionDateTime() == null
                || !StringUtils.hasText(recordDTO.getResultDTO().getTransactionId())
                || !StringUtils.hasText(recordDTO.getResultDTO().getOperationId())
                || !StringUtils.hasText(recordDTO.getResultDTO().getTransactionType())
                || !StringUtils.hasText(recordDTO.getResultDTO().getStatus())
                || !StringUtils.hasText(recordDTO.getResultDTO().getProcessStage())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /**
     * 后续交易不重复占用首次交易的累计限额预占，因此固定跳过本次风险预占。
     *
     * @param recordDTO 后续交易记录上下文
     * @return {@link PaymentRiskDecisionEnum#SKIP}
     */
    private PaymentRiskDecisionEnum resolveFollowUpRiskDecision(TransactionFollowUpRecordDTO recordDTO) {
        return PaymentRiskDecisionEnum.SKIP;
    }

    /**
     * 构造支付流程中的风控审计文本，仅记录决策、记录号和原因摘要，不包含卡号等敏感值。
     *
     * @param commandDTO       支付命令
     * @param riskDecisionEnum 风控决策
     * @return 可写入交易流程事件的脱敏文本
     */
    private String riskFlowEventContent(PaymentCreateCommandDTO commandDTO, PaymentRiskDecisionEnum riskDecisionEnum) {
        if (PaymentRiskDecisionEnum.SKIP == riskDecisionEnum) {
            return "内风控决策：SKIP（当前交易类型不适用）";
        }
        StringBuilder content = new StringBuilder("内风控决策：")
                .append(riskDecisionEnum == null ? "UNKNOWN" : riskDecisionEnum.getCode());
        if (commandDTO != null && StringUtils.hasText(commandDTO.getRiskRecordNo())) {
            content.append("；风控记录号：").append(commandDTO.getRiskRecordNo());
        }
        if (commandDTO != null && StringUtils.hasText(commandDTO.getRiskCode())) {
            content.append("；风控原因码：").append(commandDTO.getRiskCode());
        }
        if (commandDTO != null && StringUtils.hasText(commandDTO.getRiskMessage())) {
            content.append("；风控原因：").append(commandDTO.getRiskMessage());
        }
        return content.toString();
    }

    /**
     * 计算交易时间所属季度的开始时刻。
     *
     * @param transactionDateTime 交易分片时间
     * @return 季度开始时刻
     */
    private LocalDateTime quarterBegin(LocalDateTime transactionDateTime) {
        int firstMonth = ((transactionDateTime.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDateTime.of(transactionDateTime.getYear(), firstMonth, 1, 0, 0);
    }

    /**
     * 返回逻辑查询开始时间；未指定时使用最早已验证物理节点。
     *
     * @param requestedBegin 调用方指定开始时间，可为空
     * @return 可用于 ShardingSphere 路由的开始时间
     */
    private LocalDateTime logicalBegin(LocalDateTime requestedBegin) {
        if (requestedBegin != null) {
            return requestedBegin;
        }
        String earliestSuffix = transactionShardingProperties.getPhysicalNodes().stream()
                .min(String::compareTo)
                .orElseThrow(() -> new ServiceException(
                        ApiResultEnum.COMMON_FAILED.getCode(), "transaction sharding has no verified physical node"));
        if (!earliestSuffix.matches("\\d{4}0[1-4]")) {
            throw new ServiceException(
                    ApiResultEnum.COMMON_FAILED.getCode(), "transaction sharding physical node is invalid");
        }
        int year = Integer.parseInt(earliestSuffix.substring(0, 4));
        int quarter = Integer.parseInt(earliestSuffix.substring(5));
        return LocalDateTime.of(year, (quarter - 1) * 3 + 1, 1, 0, 0);
    }

    /**
     * 将现有包含式结束时间转换为 DATETIME(3) 半开区间上界。
     *
     * @param endTime 现有查询结束时间
     * @return 不包含的结束时间
     */
    private LocalDateTime exclusiveEnd(LocalDateTime endTime) {
        LocalDateTime actualEnd = endTime == null ? LocalDateTime.now() : endTime;
        return actualEnd.plusNanos(1_000_000L);
    }

    /**
     * 统计existing动作，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param operationId 平台操作号，用于定位单次授权、请款、退款、撤销或通知动作
     * @param sourceTransactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param actionTransactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int countExistingOperations(String operationId, LocalDateTime sourceTransactionDateTime, LocalDateTime actionTransactionDateTime) {
        LocalDateTime beginTime = sourceTransactionDateTime.isAfter(actionTransactionDateTime)
                ? actionTransactionDateTime
                : sourceTransactionDateTime;
        LocalDateTime endTime = sourceTransactionDateTime.isAfter(actionTransactionDateTime)
                ? sourceTransactionDateTime
                : actionTransactionDateTime;
        return transactionOperationMapper.countByOperationId(
                operationId, logicalBegin(beginTime), exclusiveEnd(endTime));
    }

    /**
     * 解析parse交易date时间，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param transactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
     * @return 构造、转换或解析后的业务值
     */
    private LocalDateTime parseTransactionDateTime(String transactionId) {
        return transactionShardingKeyParser.parseTransactionDateTime(transactionId);
    }

    /**
     * 解析parse动作date时间，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param operationId 平台操作号，用于定位单次授权、请款、退款、撤销或通知动作
     * @return 构造、转换或解析后的业务值
     */
    private LocalDateTime parseOperationDateTime(String operationId) {
        return transactionShardingKeyParser.parseOperationDateTime(operationId);
    }

}
