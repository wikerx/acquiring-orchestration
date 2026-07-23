package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
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
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingRangeTableContext;
import com.scott.payment.component.db.sharding.ShardingSingleTableContext;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
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
 * @description : 交易事实记录服务默认实现，位于 service-payment 服务实现层，按 transaction_date_time 路由写入主单、动作单和状态历史物理分表。
 * @status : create
 */
@Service
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
     * 分表数据访问统一入口。
     */
    private final ShardingDataTemplate shardingDataTemplate;

    /**
     * 交易分表键解析器。
     */
    private final TransactionShardingKeyParser transactionShardingKeyParser;

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
     * @param shardingDataTemplate                   分表数据访问统一入口
     * @param transactionShardingKeyParser           交易分表键解析器
     */
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
                                           ShardingDataTemplate shardingDataTemplate,
                                           TransactionShardingKeyParser transactionShardingKeyParser) {
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
        this.shardingDataTemplate = shardingDataTemplate;
        this.transactionShardingKeyParser = transactionShardingKeyParser;
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
        TransactionOrderDO orderDO = buildOrder(commandDTO, routeResultDTO, channelResponse, resultDTO, riskDecisionEnum, currencyExponent, now);
        TransactionOperationDO operationDO = buildOperation(commandDTO, routeResultDTO, channelResponse, resultDTO, currencyExponent, now);
        TransactionStatusHistoryDO orderHistoryDO = buildStatusHistory(commandDTO, resultDTO, STATUS_OBJECT_ORDER, commandDTO.getTransactionDateTime(), now);
        TransactionStatusHistoryDO operationHistoryDO = buildStatusHistory(commandDTO, resultDTO, STATUS_OBJECT_OPERATION, commandDTO.getTransactionDateTime(), now);

        transactionOrderMapper.insertPhysical(resolvePhysicalTable(TRANSACTION_ORDER_TABLE, commandDTO.getTransactionDateTime()), orderDO);
        transactionOperationMapper.insertPhysical(resolvePhysicalTable(TRANSACTION_OPERATION_TABLE, commandDTO.getTransactionDateTime()), operationDO);
        String statusHistoryTable = resolvePhysicalTable(TRANSACTION_STATUS_HISTORY_TABLE, commandDTO.getTransactionDateTime());
        transactionStatusHistoryMapper.insertPhysical(statusHistoryTable, orderHistoryDO);
        transactionStatusHistoryMapper.insertPhysical(statusHistoryTable, operationHistoryDO);
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
        validate(commandDTO, resultDTO);
        if (channelInvokeResultDTO == null || channelInvokeResultDTO.getChannelRequest() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        TransactionOperationDO operationDO = findSourceOperationByTransactionId(resultDTO.getTransactionId());
        TransactionOrderDO orderDO = findOrder(commandDTO.getTransactionDateTime(), resultDTO.getOperationId());
        if (operationDO == null || orderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(operationDO.getTransactionStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(operationDO.getTransactionStatus())) {
            recordCallbackStatusHistory(operationDO, channelInvokeResultDTO.getRequestId(), resultDTO.getStatus(), TRANSITION_IGNORED,
                    "operation is already terminal or state has changed");
            return;
        }
        updateInitialChannelRequest(commandDTO, routeResultDTO, channelInvokeResultDTO, resultDTO, now);
        boolean statusChanged = isTerminal(resultDTO)
                ? completeInitialTerminalStatus(operationDO, orderDO, channelInvokeResultDTO, resultDTO)
                : updateInitialNonTerminalStatus(operationDO, channelInvokeResultDTO, resultDTO, now);
        if (!statusChanged) {
            recordCallbackStatusHistory(operationDO, channelInvokeResultDTO.getRequestId(), resultDTO.getStatus(), TRANSITION_IGNORED,
                    "operation is already terminal or state has changed");
            return;
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
        return transactionOrderMapper.selectByOperationIdPhysical(resolvePhysicalTable(TRANSACTION_ORDER_TABLE, transactionDateTime), operationId);
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
        TransactionOrderDO orderDO = transactionOrderMapper.selectByOperationIdForUpdatePhysical(
                resolvePhysicalTable(TRANSACTION_ORDER_TABLE, transactionDateTime), operationId);
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
     * 按平台当前交易 ID 定位原交易动作单。
     *
     * @param sourceTransactionId 原平台交易 ID
     * @return 原交易动作单
     */
    @Override
    public TransactionOperationDO findSourceOperationByTransactionId(String sourceTransactionId) {
        LocalDateTime sourceTransactionDateTime = parseTransactionDateTime(sourceTransactionId);
        if (sourceTransactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        String operationTable = resolvePhysicalTable(TRANSACTION_OPERATION_TABLE, sourceTransactionDateTime);
        TransactionOperationDO sourceOperationDO = transactionOperationMapper.selectByTransactionIdPhysical(operationTable, sourceTransactionId);
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
                                                                      String transactionId) {
        if (!StringUtils.hasText(merchantId) || !StringUtils.hasText(merchantOrderNo)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime parsedTransactionTime = parseTransactionDateTime(transactionId);
        if (parsedTransactionTime != null) {
            String operationTable = resolvePhysicalTable(TRANSACTION_OPERATION_TABLE, parsedTransactionTime);
            return transactionOperationMapper.selectByMerchantOrderPhysical(
                    operationTable, merchantId, merchantOrderNo, transactionId);
        }
        List<TransactionOperationDO> operations = new java.util.ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String operationTable : resolvePhysicalTables(TRANSACTION_OPERATION_TABLE, null, now)) {
            operations.addAll(transactionOperationMapper.selectByMerchantOrderPhysical(
                    operationTable, merchantId, merchantOrderNo, transactionId));
        }
        return operations;
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
        List<TransactionOperationDO> operations = new java.util.ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String operationTable : resolvePhysicalTables(TRANSACTION_OPERATION_TABLE, null, now)) {
            operations.addAll(transactionOperationMapper.selectInitialByMerchantOrderPhysical(
                    operationTable, merchantId, merchantOrderNo));
        }
        return operations;
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
                || !StringUtils.hasText(sourceTransactionId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime safeEndTime = endTime == null ? LocalDateTime.now() : endTime;
        LocalDateTime safeBeginTime = beginTime == null ? parseTransactionDateTime(sourceTransactionId) : beginTime;
        List<TransactionOperationDO> operations = new java.util.ArrayList<>();
        for (String operationTable : resolvePhysicalTables(TRANSACTION_OPERATION_TABLE, safeBeginTime, safeEndTime)) {
            operations.addAll(transactionOperationMapper.selectNonTerminalCapturesPhysical(
                    operationTable, merchantId, operationId, sourceTransactionId));
        }
        return operations;
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
        for (String operationTable : resolvePhysicalTables(TRANSACTION_OPERATION_TABLE, sourceTransactionDateTime, now)) {
            TransactionOperationDO operationDO = transactionOperationMapper.selectByChannelTransactionPhysical(
                    operationTable, channelOrderNo, channelTransactionId);
            if (operationDO != null) {
                return operationDO;
            }
        }
        throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
    }

    /**
     * 查询待渠道查询确认的动作单。
     *
     * @param transactionDateTime 交易业务时间，用于定位物理分表
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
        return transactionOperationMapper.selectPendingChannelMatchPhysical(
                resolvePhysicalTable(TRANSACTION_OPERATION_TABLE, transactionDateTime),
                channelCode,
                now == null ? LocalDateTime.now() : now,
                limit);
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
        String orderTable = resolvePhysicalTable(TRANSACTION_ORDER_TABLE, sourceOrderDO.getTransactionDateTime());
        String operationTable = resolvePhysicalTable(TRANSACTION_OPERATION_TABLE, actionTransactionDateTime);
        String statusHistoryTable = resolvePhysicalTable(TRANSACTION_STATUS_HISTORY_TABLE, actionTransactionDateTime);
        TransactionOperationDO operationDO = buildFollowUpOperation(recordDTO, now,
                countExistingOperations(sourceOrderDO.getOperationId(), sourceOrderDO.getTransactionDateTime(), actionTransactionDateTime) + 1);
        transactionOperationMapper.insertPhysical(operationTable, operationDO);
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())) {
            updateSourceOrderAmount(orderTable, sourceOrderDO, resultDTO);
            recordAmountChange(recordDTO, now);
        }
        transactionStatusHistoryMapper.insertPhysical(statusHistoryTable,
                buildStatusHistory(commandDTO, resultDTO, STATUS_OBJECT_ORDER, actionTransactionDateTime, now));
        transactionStatusHistoryMapper.insertPhysical(statusHistoryTable,
                buildStatusHistory(commandDTO, resultDTO, STATUS_OBJECT_OPERATION, actionTransactionDateTime, now));
        recordChannelAudit(commandDTO, recordDTO.getRouteResultDTO(), recordDTO.getChannelInvokeResultDTO(), resultDTO, now);
        recordPaymentMethodInfo(commandDTO, resultDTO, sourceOrderDO.getOperationId(), resultDTO.getTransactionId(),
                actionTransactionDateTime, now, sourceOrderDO);
        recordFlowEvents(commandDTO, recordDTO.getRouteResultDTO(), recordDTO.getChannelInvokeResultDTO(), resultDTO, PaymentRiskDecisionEnum.PASS, now);
        recordMerchantApiInteraction(commandDTO, resultDTO, now);
        recordMerchantNotificationIfNeeded(commandDTO, resultDTO, now);
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
        String operationTable = resolvePhysicalTable(TRANSACTION_OPERATION_TABLE, operationDO.getTransactionDateTime());
        int operationUpdated = transactionOperationMapper.completeStatusPhysical(
                operationTable,
                operationDO.getId(),
                operationDO.getVersion(),
                targetTransactionStatus,
                "FINISHED",
                failReasonCode,
                failReasonMessage,
                channelStatus,
                channelResponseCode,
                channelResponseMessage);
        if (operationUpdated != 1) {
            recordCallbackStatusHistory(operationDO, callbackId, targetTransactionStatus, TRANSITION_IGNORED,
                    "operation state has changed");
            return false;
        }
        String orderTable = resolvePhysicalTable(TRANSACTION_ORDER_TABLE, orderDO.getTransactionDateTime());
        boolean success = PaymentTransactionStatusEnum.SUCCESS.getCode().equals(targetTransactionStatus);
        boolean orderUpdated = updateOrderByCallback(orderTable, orderDO, operationDO, success,
                targetTransactionStatus, failReasonCode, failReasonMessage);
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
        int updated = transactionOperationMapper.updateChannelMatchPhysical(
                resolvePhysicalTable(TRANSACTION_OPERATION_TABLE, operationDO.getTransactionDateTime()),
                operationDO.getId(),
                operationDO.getVersion(),
                matchStatus,
                safeLength(matchResult, 256),
                requestId,
                matchTime == null ? LocalDateTime.now() : matchTime,
                nextMatchTime,
                safeLength(failReason, 512));
        return updated == 1;
    }

    /**
     * 回写商户 OpenAPI 响应加密后的密文摘要。
     *
     * @param commandDTO 响应日志回写命令
     * @return true 表示命中并更新日志，false 表示未找到对应记录
     */
    @Override
    public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateCommandDTO commandDTO) {
        if (commandDTO == null || !StringUtils.hasText(commandDTO.getTransactionId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime transactionDateTime = parseTransactionDateTime(commandDTO.getTransactionId());
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        int updated = transactionMerchantApiInteractionLogMapper.updateResponseCipherPhysical(
                resolvePhysicalTable(TRANSACTION_MERCHANT_API_INTERACTION_LOG_TABLE, transactionDateTime),
                commandDTO.getTransactionId(),
                commandDTO.getRequestId(),
                safeLength(commandDTO.getResponsePlainJsonMasked(), 16_000),
                commandDTO.getResponseCipherDigest(),
                commandDTO.getResponseCipherMasked(),
                commandDTO.getResponseTime() == null ? LocalDateTime.now() : commandDTO.getResponseTime());
        return updated > 0;
    }

    private TransactionOrderDO buildOrder(PaymentCreateCommandDTO commandDTO,
                                          PaymentRouteResultDTO routeResultDTO,
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
        orderDO.setFailReasonMessage(channelResponse == null ? resultDTO.getFailReasonCode() : channelResponse.getChannelResponseMessage());
        orderDO.setMerchantVisibleMessage(resultDTO.getFailReasonCode());
        orderDO.setPayerVisibleMessage(resultDTO.getFailReasonCode());
        fillAmountFields(orderDO, commandDTO, resultDTO, currencyExponent);
        fillOrderRouteFields(orderDO, routeResultDTO, channelResponse, riskDecisionEnum);
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

    private TransactionOperationDO buildOperation(PaymentCreateCommandDTO commandDTO,
                                                  PaymentRouteResultDTO routeResultDTO,
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
        operationDO.setFailReasonMessage(channelResponse == null ? resultDTO.getFailReasonCode() : channelResponse.getChannelResponseMessage());
        fillAmountFields(operationDO, commandDTO, resultDTO, currencyExponent);
        fillOperationRouteFields(operationDO, routeResultDTO, channelResponse);
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
        orderDO.setAuthorizedAmount(success && isAuthorizationLike(resultDTO.getTransactionType()) ? transactionAmount : zero);
        orderDO.setCapturedAmount(success && PaymentTransactionTypeEnum.PAYMENT.getCode().equals(resultDTO.getTransactionType()) ? transactionAmount : zero);
        orderDO.setRefundedAmount(zero);
        orderDO.setChargebackAmount(zero);
        orderDO.setAvailableCaptureAmount(success && isAuthorizationLike(resultDTO.getTransactionType()) ? transactionAmount : zero);
        orderDO.setAvailableRefundAmount(success && PaymentTransactionTypeEnum.PAYMENT.getCode().equals(resultDTO.getTransactionType()) ? transactionAmount : zero);
    }

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

    private String resolveLabelCurrency(PaymentCreateCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getLabelCurrency()) ? commandDTO.getLabelCurrency() : commandDTO.getCurrency();
    }

    private BigDecimal resolveLabelAmount(PaymentCreateCommandDTO commandDTO) {
        return commandDTO.getLabelAmount() == null ? commandDTO.getAmount() : commandDTO.getLabelAmount();
    }

    private String resolveTransactionCurrency(PaymentCreateCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getTransactionCurrency()) ? commandDTO.getTransactionCurrency() : commandDTO.getCurrency();
    }

    private BigDecimal resolveTransactionAmount(PaymentCreateCommandDTO commandDTO) {
        BigDecimal amount = commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount();
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private BigDecimal resolveTransactionRate(PaymentCreateCommandDTO commandDTO) {
        return commandDTO.getTransactionRate() == null ? new BigDecimal("1.00000000") : commandDTO.getTransactionRate();
    }

    private int flagValue(Integer value) {
        return value != null && ENABLED == value ? ENABLED : DISABLED;
    }

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

    private String resolvePaymentMethod(PaymentCreateCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getPaymentMethod()) ? commandDTO.getPaymentMethod() : DEFAULT_PAYMENT_METHOD;
    }

    private boolean isAuthorizationLike(String transactionType) {
        return Objects.equals(PaymentTransactionTypeEnum.AUTHORIZATION.getCode(), transactionType)
                || Objects.equals(PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode(), transactionType);
    }

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
     * @param orderTable 主单物理表
     * @param orderDO 生命周期主单
     * @param operationDO 被确认的动作单
     * @param success true 表示动作成功终态
     * @param targetTransactionStatus 目标交易状态
     * @param failReasonCode 失败原因码
     * @param failReasonMessage 失败原因描述
     * @return true 表示主单更新成功
     */
    private boolean updateOrderByCallback(String orderTable,
                                          TransactionOrderDO orderDO,
                                          TransactionOperationDO operationDO,
                                          boolean success,
                                          String targetTransactionStatus,
                                          String failReasonCode,
                                          String failReasonMessage) {
        if (success) {
            if (!StringUtils.hasText(operationDO.getSourceTransactionId())) {
                int updated = transactionOrderMapper.markInitialSuccessPhysical(
                        orderTable,
                        orderDO.getOperationId(),
                        operationDO.getTransactionId(),
                        operationDO.getTransactionAmount() == null ? BigDecimal.ZERO : operationDO.getTransactionAmount(),
                        orderDO.getVersion());
                return updated == 1;
            }
            PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
            resultDTO.setTransactionId(operationDO.getTransactionId());
            resultDTO.setTransactionType(operationDO.getTransactionType());
            resultDTO.setAmount(operationDO.getTransactionAmount() == null
                    ? null
                    : operationDO.getTransactionAmount().movePointRight(orderDO.getCurrencyExponent() == null ? 0 : orderDO.getCurrencyExponent()).longValue());
            updateSourceOrderAmount(orderTable, orderDO, resultDTO);
            return true;
        }
        int updated = transactionOrderMapper.completeStatusPhysical(
                orderTable,
                orderDO.getOperationId(),
                operationDO.getTransactionId(),
                orderDO.getVersion(),
                targetTransactionStatus,
                PaymentProcessStageEnum.FINISHED.getCode(),
                failReasonCode,
                failReasonMessage,
                failReasonCode,
                failReasonCode);
        return updated == 1;
    }

    private void updateInitialChannelRequest(PaymentCreateCommandDTO commandDTO,
                                             PaymentRouteResultDTO routeResultDTO,
                                             PaymentChannelInvokeResultDTO invokeResultDTO,
                                             PaymentCreateResultDTO resultDTO,
                                             LocalDateTime now) {
        String requestTable = resolvePhysicalTable(TRANSACTION_CHANNEL_REQUEST_TABLE, commandDTO.getTransactionDateTime());
        TransactionChannelRequestDO requestDO = transactionChannelRequestMapper.selectByRequestIdPhysical(
                requestTable,
                invokeResultDTO.getRequestId());
        if (requestDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND.getCode(), "channel request fact can not be found");
        }
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        Map<String, String> rawResponse = response == null ? Map.of() : response.getRawResponse();
        int updated = transactionChannelRequestMapper.updateStatusPhysical(
                requestTable,
                invokeResultDTO.getRequestId(),
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
    }

    private boolean isRequestResultConflict(TransactionChannelRequestDO requestDO, PaymentCreateResultDTO resultDTO) {
        return requestDO == null
                || !PaymentTransactionStatusEnum.SUCCESS.getCode().equals(requestDO.getPlatformResultCode())
                || !PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus());
    }

    private boolean completeInitialTerminalStatus(TransactionOperationDO operationDO,
                                                  TransactionOrderDO orderDO,
                                                  PaymentChannelInvokeResultDTO invokeResultDTO,
                                                  PaymentCreateResultDTO resultDTO) {
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        String operationTable = resolvePhysicalTable(TRANSACTION_OPERATION_TABLE, operationDO.getTransactionDateTime());
        int operationUpdated = transactionOperationMapper.completeStatusPhysical(
                operationTable,
                operationDO.getId(),
                operationDO.getVersion(),
                resultDTO.getStatus(),
                resultDTO.getProcessStage(),
                resultDTO.getFailReasonCode(),
                resultDTO.getFailReasonMessage(),
                response == null ? null : response.getRawChannelStatus(),
                response == null ? null : response.getChannelResponseCode(),
                response == null ? null : response.getChannelResponseMessage());
        if (operationUpdated != 1) {
            return false;
        }
        String orderTable = resolvePhysicalTable(TRANSACTION_ORDER_TABLE, orderDO.getTransactionDateTime());
        boolean success = PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus());
        if (success) {
            int orderUpdated = transactionOrderMapper.markInitialSuccessPhysical(
                    orderTable,
                    orderDO.getOperationId(),
                    resultDTO.getTransactionId(),
                    operationDO.getTransactionAmount() == null ? BigDecimal.ZERO : operationDO.getTransactionAmount(),
                    orderDO.getVersion());
            if (orderUpdated != 1) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "order state has changed");
            }
            return true;
        }
        int orderUpdated = transactionOrderMapper.completeStatusPhysical(
                orderTable,
                orderDO.getOperationId(),
                resultDTO.getTransactionId(),
                orderDO.getVersion(),
                resultDTO.getStatus(),
                resultDTO.getProcessStage(),
                resultDTO.getFailReasonCode(),
                resultDTO.getFailReasonMessage(),
                resultDTO.getFailReasonCode(),
                resultDTO.getFailReasonCode());
        if (orderUpdated != 1) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "order state has changed");
        }
        return true;
    }

    private boolean updateInitialNonTerminalStatus(TransactionOperationDO operationDO,
                                                   PaymentChannelInvokeResultDTO invokeResultDTO,
                                                   PaymentCreateResultDTO resultDTO,
                                                   LocalDateTime now) {
        ChannelPaymentResponse response = invokeResultDTO.getChannelResponse();
        String operationTable = resolvePhysicalTable(TRANSACTION_OPERATION_TABLE, operationDO.getTransactionDateTime());
        int updated = transactionOperationMapper.updateNonTerminalChannelResultPhysical(
                operationTable,
                operationDO.getId(),
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
        return updated == 1;
    }

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
        String historyTable = resolvePhysicalTable(TRANSACTION_STATUS_HISTORY_TABLE, operationDO.getTransactionDateTime());
        transactionStatusHistoryMapper.insertPhysical(historyTable,
                buildCallbackStatusHistory(operationDO, STATUS_OBJECT_OPERATION, callbackId, targetTransactionStatus,
                        TRANSITION_SUCCESS, null, now));
        transactionStatusHistoryMapper.insertPhysical(historyTable,
                buildCallbackStatusHistory(operationDO, STATUS_OBJECT_ORDER, callbackId, targetTransactionStatus,
                        orderUpdated ? TRANSITION_SUCCESS : TRANSITION_IGNORED,
                        orderUpdated ? null : "order state has changed", now));
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
        transactionFlowEventMapper.insertPhysical(
                resolvePhysicalTable(TRANSACTION_FLOW_EVENT_TABLE, operationDO.getTransactionDateTime()),
                eventDO);
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
        transactionStatusHistoryMapper.insertPhysical(
                resolvePhysicalTable(TRANSACTION_STATUS_HISTORY_TABLE, operationDO.getTransactionDateTime()),
                buildCallbackStatusHistory(operationDO, STATUS_OBJECT_OPERATION, callbackId, targetTransactionStatus,
                        transitionResult, failReason, LocalDateTime.now()));
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
        transactionMerchantNotificationMapper.activateByTransactionId(
                resolvePhysicalTable(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, operationDO.getTransactionDateTime()),
                operationDO.getTransactionId(),
                maskedJson(merchantVisiblePayload(operationDO, targetTransactionStatus, failReasonCode)),
                now,
                now);
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
     * @param orderTable 主单物理表
     * @param sourceOrderDO 原生命周期主单
     * @param resultDTO 后续交易结果
     */
    private void updateSourceOrderAmount(String orderTable,
                                         TransactionOrderDO sourceOrderDO,
                                         PaymentCreateResultDTO resultDTO) {
        BigDecimal amount = amountFromResult(resultDTO, sourceOrderDO);
        int updated;
        if (PaymentTransactionTypeEnum.CAPTURE.getCode().equals(resultDTO.getTransactionType())) {
            updated = transactionOrderMapper.increaseCapturedAmountPhysical(
                    orderTable, sourceOrderDO.getOperationId(), resultDTO.getTransactionId(), amount, sourceOrderDO.getVersion());
        } else if (PaymentTransactionTypeEnum.REFUND.getCode().equals(resultDTO.getTransactionType())) {
            updated = transactionOrderMapper.increaseRefundedAmountPhysical(
                    orderTable, sourceOrderDO.getOperationId(), resultDTO.getTransactionId(), amount, sourceOrderDO.getVersion());
        } else if (PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode().equals(resultDTO.getTransactionType())) {
            updated = transactionOrderMapper.increaseAuthorizedAmountPhysical(
                    orderTable, sourceOrderDO.getOperationId(), resultDTO.getTransactionId(), amount, sourceOrderDO.getVersion());
        } else if (PaymentTransactionTypeEnum.VOID.getCode().equals(resultDTO.getTransactionType())) {
            updated = transactionOrderMapper.markVoidSuccessPhysical(
                    orderTable, sourceOrderDO.getOperationId(), resultDTO.getTransactionId(), sourceOrderDO.getVersion());
        } else {
            updated = 1;
        }
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "source transaction state has changed");
        }
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
        String requestTable = resolvePhysicalTable(TRANSACTION_CHANNEL_REQUEST_TABLE, commandDTO.getTransactionDateTime());
        String interactionTable = resolvePhysicalTable(TRANSACTION_CHANNEL_INTERACTION_LOG_TABLE, commandDTO.getTransactionDateTime());
        transactionChannelRequestMapper.insertPhysical(requestTable,
                buildChannelRequest(commandDTO, routeResultDTO, invokeResultDTO, resultDTO, now));
        transactionChannelInteractionLogMapper.insertPhysical(interactionTable,
                buildChannelInteractionLog(commandDTO, invokeResultDTO, resultDTO, now));
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
        transactionPaymentMethodInfoMapper.insertPhysical(
                resolvePhysicalTable(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, transactionDateTime),
                infoDO);
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
        for (String table : resolvePhysicalTables(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, beginTime, endTime)) {
            List<TransactionPaymentMethodInfoDO> rows = transactionPaymentMethodInfoMapper.selectByOperationIdPhysical(
                    table, sourceOrderDO.getOperationId());
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            TransactionPaymentMethodInfoDO source = rows.stream()
                    .filter(this::hasCardSummary)
                    .findFirst()
                    .orElse(rows.get(0));
            if (source != null) {
                return source;
            }
        }
        return null;
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
     * 该表保存一次渠道请求的核心字段和平台成功判断；完整请求/响应 JSON 放入
     * transaction_channel_interaction_log，避免列表查询扫描大报文。
     *
     * @param commandDTO       支付核心交易命令
     * @param routeResultDTO   渠道路由结果
     * @param invokeResultDTO  渠道调用上下文
     * @param resultDTO        平台交易结果
     * @param now              当前处理时间
     * @return 渠道请求摘要
     */
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
        requestDO.setHttpMethod(invokeResultDTO.getHttpMethod());
        requestDO.setRequestUrlMasked(invokeResultDTO.getRequestUrlMasked());
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
        logDO.setInteractionType(StringUtils.hasText(invokeResultDTO.getExceptionType()) ? "EXCEPTION" : "REQUEST_RESPONSE");
        logDO.setHttpMethod(invokeResultDTO.getHttpMethod());
        logDO.setRequestUrlMasked(invokeResultDTO.getRequestUrlMasked());
        logDO.setHttpStatus(resolveChannelHttpStatus(invokeResultDTO));
        logDO.setRequestBodyJsonMasked(maskedJson(invokeResultDTO.getChannelRequest()));
        logDO.setResponseBodyJsonMasked(maskedJson(invokeResultDTO.getChannelResponse()));
        logDO.setExceptionType(invokeResultDTO.getExceptionType());
        logDO.setExceptionMessage(safeLength(invokeResultDTO.getExceptionMessage(), 1024));
        logDO.setDurationMillis(invokeResultDTO.getDurationMillis());
        logDO.setInteractionTime(defaultTime(invokeResultDTO.getRequestStartTime(), now));
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
        if (invokeResultDTO == null || invokeResultDTO.getChannelResponse() == null
                || invokeResultDTO.getChannelResponse().getRawResponse() == null) {
            return null;
        }
        String httpStatus = invokeResultDTO.getChannelResponse().getRawResponse().get("httpStatus");
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
        String table = resolvePhysicalTable(TRANSACTION_FLOW_EVENT_TABLE, commandDTO.getTransactionDateTime());
        insertFlowEvent(table, commandDTO, resultDTO, "API_ACCEPTED", "API", "SUCCESS",
                "API受理", "交易请求已进入支付核心", null, resultDTO.getStatus(), "MERCHANT",
                commandDTO.getMerchantId(), "TRANSACTION", resultDTO.getTransactionId(), null, null, now);
        insertFlowEvent(table, commandDTO, resultDTO, "RISK_CHECKED", "RISK",
                riskDecisionEnum != null && riskDecisionEnum.isAllowProceed() ? "SUCCESS" : resultDTO.getStatus(),
                "内风控检查", "内风控决策：" + (riskDecisionEnum == null ? "UNKNOWN" : riskDecisionEnum.getCode()),
                null, resultDTO.getStatus(), "SYSTEM", null, "TRANSACTION", resultDTO.getTransactionId(),
                resultDTO.getFailReasonCode(), null, now);
        if (routeResultDTO != null) {
            insertFlowEvent(table, commandDTO, resultDTO, "ROUTE_SELECTED", "ROUTE", "SUCCESS",
                    "渠道路由", "命中渠道：" + routeResultDTO.getChannelCode(), null, resultDTO.getStatus(),
                    "SYSTEM", null, "CHANNEL_MID", routeResultDTO.getMidConfigId() == null ? null : String.valueOf(routeResultDTO.getMidConfigId()),
                    null, null, now);
        }
        if (invokeResultDTO != null) {
            String channelEventStatus = resolveChannelFlowEventStatus(invokeResultDTO, resultDTO);
            insertFlowEvent(table, commandDTO, resultDTO, "CHANNEL_CALLED", "CHANNEL",
                    channelEventStatus,
                    "渠道请求", buildChannelFlowEventContent(invokeResultDTO, resultDTO), null, resultDTO.getStatus(),
                    "SYSTEM", null, "REQUEST", invokeResultDTO.getRequestId(),
                    PaymentTransactionStatusEnum.FAILED.getCode().equals(channelEventStatus) ? resultDTO.getFailReasonCode() : null,
                    PaymentTransactionStatusEnum.FAILED.getCode().equals(channelEventStatus) ? resolveChannelFlowErrorMessage(invokeResultDTO, resultDTO) : null,
                    now);
        }
        insertFlowEvent(table, commandDTO, resultDTO, "STATUS_RECORDED", "STATUS", resolveResultFlowEventStatus(resultDTO),
                "状态入库", "交易状态：" + resultDTO.getStatus(), null, resultDTO.getStatus(),
                "SYSTEM", null, "STATUS_HISTORY", resultDTO.getTransactionId(),
                PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus()) ? resultDTO.getFailReasonCode() : null,
                PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus()) ? resultDTO.getFailReasonMessage() : null,
                now);
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
        transactionMerchantApiInteractionLogMapper.insertPhysical(
                resolvePhysicalTable(TRANSACTION_MERCHANT_API_INTERACTION_LOG_TABLE, commandDTO.getTransactionDateTime()), logDO);
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
     * @param table          已解析并校验的物理表名
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
    private void insertFlowEvent(String table,
                                 PaymentCreateCommandDTO commandDTO,
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
        transactionFlowEventMapper.insertPhysical(table, eventDO);
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
        String table = resolvePhysicalTable(TRANSACTION_AMOUNT_CHANGE_LOG_TABLE, actionTransactionDateTime);
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
        transactionAmountChangeLogMapper.insertPhysical(table, logDO);
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
        String table = resolvePhysicalTable(TRANSACTION_AMOUNT_CHANGE_LOG_TABLE, actionTransactionDateTime);
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
        transactionAmountChangeLogMapper.insertPhysical(table, logDO);
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
        if (PaymentTransactionTypeEnum.CAPTURE.getCode().equals(transactionType)) {
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
        notificationDO.setNotifyConfigSnapshotJson(maskedJson(Map.of("callbackUrl", callbackUrl)));
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
        transactionMerchantNotificationMapper.insertPhysical(
                resolvePhysicalTable(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, commandDTO.getTransactionDateTime()), notificationDO);
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
        putIfPresent(transactionInfo, "message", resolveMerchantResponseMessage(targetTransactionStatus));
        putIfPresent(transactionInfo, "transactionId", operationDO.getTransactionId());
        putIfPresent(transactionInfo, "sourceTransactionId", operationDO.getSourceTransactionId());
        putIfPresent(transactionInfo, "transactionType", operationDO.getTransactionType());
        putIfPresent(transactionInfo, "transactionStatus", targetTransactionStatus);
        putIfPresent(transactionInfo, "processStage", operationDO.getProcessStage());
        putIfPresent(transactionInfo, "transactionDateTime", offsetDateTimeString(operationDO.getTransactionDateTime(), operationDO.getTransactionTimeZone()));
        putIfPresent(transactionInfo, "authCode", operationDO.getAuthCode());
        putIfPresent(transactionInfo, "arn", firstText(operationDO.getAcquirerReferenceNo(), operationDO.getRrn()));
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

    private Map<String, Object> merchantInfoPayload(PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        Map<String, Object> merchantInfo = new LinkedHashMap<>();
        putIfPresent(merchantInfo, "merchantId", firstText(resultDTO.getMerchantId(), commandDTO.getMerchantId()));
        putIfPresent(merchantInfo, "subMerchantInfo", subMerchantInfoPayload(resultDTO.getSubMerchantInfo()));
        return compactMap(merchantInfo);
    }

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

    private Map<String, Object> orderInfoPayload(PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        Map<String, Object> orderInfo = new LinkedHashMap<>();
        putIfPresent(orderInfo, "orderNo", firstText(resultDTO.getMerchantOrderNo(), commandDTO.getMerchantOrderNo()));
        putIfPresent(orderInfo, "orderId", firstText(resultDTO.getMerchantOrderId(), commandDTO.getMerchantOrderId()));
        putIfPresent(orderInfo, "amount", firstAmount(resultDTO.getOrderAmount(), commandDTO.getLabelAmount(), commandDTO.getAmount()));
        putIfPresent(orderInfo, "currency", firstText(resultDTO.getOrderCurrency(), commandDTO.getLabelCurrency(), commandDTO.getCurrency()));
        putIfPresent(orderInfo, "totalAuthorizedAmount", resultDTO.getTotalAuthorizedAmount());
        putIfPresent(orderInfo, "totalCapturedAmount", resultDTO.getTotalCapturedAmount());
        putIfPresent(orderInfo, "totalRefundAmount", resultDTO.getTotalRefundAmount());
        putIfPresent(orderInfo, "totalVoidAmount", resultDTO.getTotalVoidAmount());
        putIfPresent(orderInfo, "totalChargebackAmount", resultDTO.getTotalChargebackAmount());
        return compactMap(orderInfo);
    }

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

    private BigDecimal firstAmount(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal normalizeRate(BigDecimal value) {
        BigDecimal rate = value == null ? DEFAULT_TRANSACTION_RATE : value;
        return rate.setScale(8, RoundingMode.HALF_UP);
    }

    private String offsetDateTimeString(LocalDateTime dateTime, String timeZone) {
        if (dateTime == null) {
            return null;
        }
        ZoneId zoneId = ZoneId.of(StringUtils.hasText(timeZone) ? timeZone : DEFAULT_TIME_ZONE);
        return dateTime.atZone(zoneId).toOffsetDateTime().toString();
    }

    private String resolveMerchantResponseCode(PaymentCreateResultDTO resultDTO) {
        if (resultDTO != null && StringUtils.hasText(resultDTO.getMerchantResponseCode())) {
            return resultDTO.getMerchantResponseCode();
        }
        return resolveMerchantResponseCode(resultDTO == null ? null : resultDTO.getStatus());
    }

    private String resolveMerchantResponseMessage(PaymentCreateResultDTO resultDTO) {
        if (resultDTO != null && StringUtils.hasText(resultDTO.getMerchantResponseMessage())) {
            return resultDTO.getMerchantResponseMessage();
        }
        return resolveMerchantResponseMessage(resultDTO == null ? null : resultDTO.getStatus());
    }

    private String merchantVisibleFailureCode(String failReasonCode) {
        return StringUtils.hasText(failReasonCode) ? "PAYMENT_FAILED" : null;
    }

    private String merchantVisibleFailureMessage(String transactionStatus, String failReasonCode) {
        if (!PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)
                || !StringUtils.hasText(failReasonCode)) {
            return null;
        }
        return "Payment failed. Please use the transaction ID to query details or contact support.";
    }

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

    private String resolveChannelCode(PaymentRouteResultDTO routeResultDTO, ChannelPaymentResponse response) {
        if (response != null && StringUtils.hasText(response.getChannelCode())) {
            return response.getChannelCode();
        }
        return routeResultDTO == null ? null : routeResultDTO.getChannelCode();
    }

    private String resolveCallbackUrl(PaymentCreateCommandDTO commandDTO) {
        if (StringUtils.hasText(commandDTO.getCallbackUrl())) {
            return commandDTO.getCallbackUrl();
        }
        if (commandDTO.getTransactionInfo() != null && StringUtils.hasText(commandDTO.getTransactionInfo().getCallbackUrl())) {
            return commandDTO.getTransactionInfo().getCallbackUrl();
        }
        return null;
    }

    private String maskedJson(Object value) {
        if (value == null) {
            return null;
        }
        return SensitiveDataMaskUtils.maskJson(JsonUtils.toJsonString(value));
    }

    private String maskUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int queryIndex = url.indexOf('?');
        return queryIndex < 0 ? url : url.substring(0, queryIndex) + "?***";
    }

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

    private LocalDateTime defaultTime(LocalDateTime value, LocalDateTime fallback) {
        return value == null ? fallback : value;
    }

    private String safeLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean isNonTerminal(PaymentCreateResultDTO resultDTO) {
        return !PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                && !PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus());
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime, String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone == null || timeZone.isBlank() ? DEFAULT_TIME_ZONE : timeZone);
        return transactionDateTime.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private void fillTransactionTime(TransactionChannelRequestDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    private void fillTransactionTime(TransactionChannelInteractionLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    private void fillTransactionTime(TransactionFlowEventDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    private void fillTransactionTime(TransactionStatusHistoryDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    private void fillTransactionTime(TransactionAmountChangeLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    private void fillTransactionTime(TransactionMerchantNotificationDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    private void fillTransactionTime(TransactionMerchantApiInteractionLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    private void fillTransactionTime(TransactionPaymentMethodInfoDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime, DEFAULT_TIME_ZONE));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

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

    private String resolvePhysicalTable(String logicalTable, LocalDateTime transactionDateTime) {
        return shardingDataTemplate.resolvePhysicalTable(
                ShardingSingleTableContext.of(logicalTable, transactionDateTime, DataSourceName.MASTER));
    }

    private int countExistingOperations(String operationId, LocalDateTime sourceTransactionDateTime, LocalDateTime actionTransactionDateTime) {
        LocalDateTime beginTime = sourceTransactionDateTime.isAfter(actionTransactionDateTime)
                ? actionTransactionDateTime
                : sourceTransactionDateTime;
        LocalDateTime endTime = sourceTransactionDateTime.isAfter(actionTransactionDateTime)
                ? sourceTransactionDateTime
                : actionTransactionDateTime;
        int total = 0;
        for (String table : resolvePhysicalTables(TRANSACTION_OPERATION_TABLE, beginTime, endTime)) {
            total += transactionOperationMapper.countByOperationIdPhysical(table, operationId);
        }
        return total;
    }

    private List<String> resolvePhysicalTables(String logicalTable, LocalDateTime beginTime, LocalDateTime endTime) {
        return shardingDataTemplate.resolvePhysicalTables(
                ShardingRangeTableContext.of(logicalTable, beginTime, endTime, DataSourceName.MASTER));
    }

    private LocalDateTime parseTransactionDateTime(String transactionId) {
        return transactionShardingKeyParser.parseTransactionDateTime(transactionId);
    }

    private LocalDateTime parseOperationDateTime(String operationId) {
        return transactionShardingKeyParser.parseOperationDateTime(operationId);
    }

}
