package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.ChannelCallbackKind;
import com.scott.payment.channel.payment.executor.PaymentChannelCallbackExecutor;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackResultDTO;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionChannelCallbackDO;
import com.scott.payment.payment.entity.TransactionChannelCallbackLogDO;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.mapper.TransactionChannelCallbackLogMapper;
import com.scott.payment.payment.mapper.TransactionChannelCallbackMapper;
import com.scott.payment.payment.mq.TransactionMqConstants;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.TransactionCallbackService;
import com.scott.payment.payment.service.ChannelTransactionStatusResolver;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionCallbackService
 * @date : 2026-07-14 22:42
 * @email : scott_x@163.com
 * @description : 交易渠道回调服务默认实现，位于 service-payment 服务实现层，通过渠道回调 SPI 解析渠道事件，再完成脱敏原文、幂等记录落库和状态推进。
 * @status : create
 */
@Service
@Slf4j
public class DefaultTransactionCallbackService implements TransactionCallbackService {

    /**
     * 渠道回调原始日志逻辑表名。
     */
    private static final String TRANSACTION_CHANNEL_CALLBACK_LOG_TABLE = "transaction_channel_callback_log";

    /**
     * 渠道回调业务逻辑表名。
     */
    private static final String TRANSACTION_CHANNEL_CALLBACK_TABLE = "transaction_channel_callback";

    /**
     * 渠道回调日志编号前缀。
     */
    private static final String CALLBACK_LOG_PREFIX = "CCL";

    /**
     * 渠道回调业务编号前缀。
     */
    private static final String CALLBACK_PREFIX = "CCB";

    /**
     * 默认回调类型。
     */
    private static final String DEFAULT_CALLBACK_TYPE = "CHANNEL_CALLBACK";

    /**
     * 3DS callback only confirms payer-authentication progress and never proves payment finality.
     */
    private static final String THREE_DS_CALLBACK_TYPE = "THREE_DS_AUTHENTICATION_CALLBACK";

    /** 通用 3DS 渠道事件类型，用于兼容已进入回调链的旧内部分类值。 */
    private static final String THREE_DS_EVENT_TYPE = "THREE_DS_CALLBACK";

    /**
     * 回调接收状态。
     */
    private static final String CALLBACK_STATUS_RECEIVED = "RECEIVED";

    /**
     * 回调记录失败状态。
     */
    private static final String CALLBACK_STATUS_FAILED = "FAILED";

    /**
     * 回调已完成业务处理状态。
     */
    private static final String CALLBACK_STATUS_PROCESSED = "PROCESSED";

    /**
     * 回调已接收但暂不推进状态。
     */
    private static final String CALLBACK_STATUS_IGNORED = "IGNORED";

    /**
     * 仅记录待处理结果。
     */
    private static final String PROCESS_RESULT_PENDING = "PENDING_STATE_MAPPING";

    /**
     * 回调已完成状态推进。
     */
    private static final String PROCESS_RESULT_STATUS_CHANGED = "STATUS_CHANGED";

    /**
     * 回调重复或终态保护忽略。
     */
    private static final String PROCESS_RESULT_TERMINAL_IGNORED = "TERMINAL_IGNORED";

    /**
     * 回调来源安全校验失败处理结果。
     */
    private static final String PROCESS_RESULT_SECURITY_REJECTED = "SECURITY_REJECTED";

    /**
     * 未能解析平台交易号时的占位值，确保回调原文仍可按时间分表落库排查。
     */
    private static final String UNKNOWN_TRANSACTION_ID = "UNKNOWN";

    /**
     * 默认交易业务时区。
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /**
     * 交易事件聚合类型。
     */
    private static final String PAYMENT_TRANSACTION_AGGREGATE = "PAYMENT_TRANSACTION";

    /**
     * 本地事件初始状态。
     */
    private static final String EVENT_STATUS_INIT = "INIT";

    /**
     * 本地事件默认最大重试次数。
     */
    private static final int DEFAULT_EVENT_MAX_RETRY_COUNT = 200;

    /**
     * 初始版本号。
     */
    private static final int INITIAL_VERSION = 0;

    /**
     * 未删除标识。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 新逻辑表路径允许完成处理的初始回调状态。
     */
    private static final List<String> CALLBACK_PROCESSABLE_STATUSES = List.of(
            CALLBACK_STATUS_RECEIVED, CALLBACK_STATUS_FAILED);

    private final TransactionChannelCallbackLogMapper callbackLogMapper;

    private final TransactionChannelCallbackMapper callbackMapper;

    private final TransactionRecordService transactionRecordService;

    private final TransactionEventOutboxService transactionEventOutboxService;

    private final TransactionShardingKeyParser transactionShardingKeyParser;

    private final Optional<PaymentChannelCallbackExecutor> callbackExecutor;

    /**
     * 渠道状态解析服务，用于让渠道回调和查询勾兑共享平台状态映射。
     */
    private final ChannelTransactionStatusResolver channelStatusResolver;

    /**
     * 创建交易渠道回调服务默认实现。
     *
     * @param callbackLogMapper 渠道回调原始日志 Mapper
     * @param callbackMapper 渠道回调业务 Mapper
     * @param transactionRecordService 交易事实记录服务
     * @param transactionEventOutboxService 交易本地事件服务
     * @param transactionShardingKeyParser 交易分表键解析器
     * @param callbackExecutor 渠道回调执行器，可为空以兼容尚未接入回调 SPI 的测试场景
     */
    @Autowired
    public DefaultTransactionCallbackService(TransactionChannelCallbackLogMapper callbackLogMapper,
                                             TransactionChannelCallbackMapper callbackMapper,
                                             TransactionRecordService transactionRecordService,
                                             TransactionEventOutboxService transactionEventOutboxService,
                                             TransactionShardingKeyParser transactionShardingKeyParser,
                                             Optional<PaymentChannelCallbackExecutor> callbackExecutor) {
        this(callbackLogMapper,
                callbackMapper,
                transactionRecordService,
                transactionEventOutboxService,
                transactionShardingKeyParser,
                callbackExecutor,
                new DefaultChannelTransactionStatusResolver());
    }

    /**
     * 创建交易渠道回调服务默认实现。
     *
     * @param callbackLogMapper 渠道回调原始日志 Mapper
     * @param callbackMapper 渠道回调业务 Mapper
     * @param transactionRecordService 交易事实记录服务
     * @param transactionEventOutboxService 交易本地事件服务
     * @param transactionShardingKeyParser 交易分表键解析器
     * @param callbackExecutor 渠道回调执行器，可为空以兼容尚未接入回调 SPI 的测试场景
     * @param channelStatusResolver 渠道状态解析服务
     */
    public DefaultTransactionCallbackService(TransactionChannelCallbackLogMapper callbackLogMapper,
                                             TransactionChannelCallbackMapper callbackMapper,
                                             TransactionRecordService transactionRecordService,
                                             TransactionEventOutboxService transactionEventOutboxService,
                                             TransactionShardingKeyParser transactionShardingKeyParser,
                                             Optional<PaymentChannelCallbackExecutor> callbackExecutor,
                                             ChannelTransactionStatusResolver channelStatusResolver) {
        this.callbackLogMapper = callbackLogMapper;
        this.callbackMapper = callbackMapper;
        this.transactionRecordService = transactionRecordService;
        this.transactionEventOutboxService = transactionEventOutboxService;
        this.transactionShardingKeyParser = transactionShardingKeyParser;
        this.callbackExecutor = callbackExecutor == null ? Optional.empty() : callbackExecutor;
        this.channelStatusResolver = channelStatusResolver == null
                ? new DefaultChannelTransactionStatusResolver()
                : channelStatusResolver;
    }

    /**
     * 记录渠道回调原文和业务回调记录。
     *
     * @param commandDTO 渠道回调内部命令
     * @return 回调处理结果
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public TransactionChannelCallbackResultDTO recordChannelCallback(TransactionChannelCallbackCommandDTO commandDTO) {
        validate(commandDTO);
        long startNanos = System.nanoTime();
        CallbackBodyLogMetadata bodyLogMetadata = callbackBodyLogMetadata(commandDTO.getRequestBody());
        log.info("event: PAYMENT_CHANNEL_CALLBACK_START stage=CALLBACK traceId: {} channelCode: {} callbackType: {} requestUri: {} sourceIp: {} signatureValid: {} ipAllowed: {} bodyLength: {} bodySha256: {}",
                TraceContext.getTraceId(),
                normalizeChannelCode(commandDTO.getChannelCode()),
                resolveCallbackType(commandDTO),
                commandDTO.getRequestUri(),
                commandDTO.getSourceIp(),
                commandDTO.getSignatureValid(),
                commandDTO.getIpAllowed(),
                bodyLogMetadata.length(),
                bodyLogMetadata.sha256());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime receivedTime = commandDTO.getReceivedTime() == null ? now : commandDTO.getReceivedTime();
        ChannelCallbackResult channelCallbackResult = parseByChannelHandler(commandDTO);
        CallbackContext context = resolveContext(commandDTO, channelCallbackResult);
        String callbackLogId = PaymentOrderNoGenerator.nextOrderNo(CALLBACK_LOG_PREFIX, context.transactionDateTime());
        String callbackId = PaymentOrderNoGenerator.nextOrderNo(CALLBACK_PREFIX, context.transactionDateTime());
        String callbackTable = tableForLog(TRANSACTION_CHANNEL_CALLBACK_TABLE, context.transactionDateTime());
        String callbackLogTable = tableForLog(TRANSACTION_CHANNEL_CALLBACK_LOG_TABLE, context.transactionDateTime());
        TransactionChannelCallbackLogDO callbackLogDO = buildCallbackLog(
                commandDTO, channelCallbackResult, context, callbackLogId, receivedTime, now);
        int callbackLogRows = callbackLogMapper.insertLogical(callbackLogDO);
        log.info("event: PAYMENT_CHANNEL_CALLBACK_LOG_SAVED stage=CALLBACK traceId: {} channelCode: {} callbackLogId: {} transactionId: {} operationId: {} channelOrderNo: {} channelTransactionId: {} signatureValid: {} ipAllowed: {} logicalTable: {} physicalTable: {} affectedRows: {}",
                TraceContext.getTraceId(),
                normalizeChannelCode(commandDTO.getChannelCode()),
                callbackLogId,
                context.transactionId(),
                context.operationId(),
                context.channelOrderNo(),
                context.channelTransactionId(),
                commandDTO.getSignatureValid(),
                commandDTO.getIpAllowed(),
                TRANSACTION_CHANNEL_CALLBACK_LOG_TABLE,
                callbackLogTable,
                callbackLogRows);
        String idempotencyKey = buildIdempotencyKey(commandDTO, channelCallbackResult, context);
        TransactionChannelCallbackDO existed = findByIdempotency(
                callbackTable, commandDTO.getChannelCode(), idempotencyKey, context.transactionDateTime());
        if (existed != null) {
            return duplicateResult(callbackLogId, idempotencyKey, startNanos, existed);
        }
        TransactionChannelCallbackDO callbackDO = buildCallback(
                commandDTO, channelCallbackResult, context, callbackLogId, callbackId,
                idempotencyKey, receivedTime, now);
        int callbackRows;
        try {
            callbackRows = callbackMapper.insertLogical(callbackDO);
        } catch (DuplicateKeyException exception) {
            TransactionChannelCallbackDO concurrentlyCreated = findByIdempotency(
                    callbackTable, commandDTO.getChannelCode(), idempotencyKey, context.transactionDateTime());
            if (concurrentlyCreated == null) {
                throw exception;
            }
            return duplicateResult(callbackLogId, idempotencyKey, startNanos, concurrentlyCreated);
        }
        log.info("event: PAYMENT_CHANNEL_CALLBACK_IDEMPOTENCY_SAVED stage=CALLBACK_IDEMPOTENCY traceId: {} channelCode: {} callbackLogId: {} callbackId: {} transactionId: {} operationId: {} channelOrderNo: {} channelTransactionId: {} idempotencyKey: {} duplicated=false logicalTable: {} physicalTable: {} affectedRows: {}",
                TraceContext.getTraceId(),
                normalizeChannelCode(commandDTO.getChannelCode()),
                callbackLogId,
                callbackId,
                context.transactionId(),
                context.operationId(),
                context.channelOrderNo(),
                context.channelTransactionId(),
                idempotencyKey,
                TRANSACTION_CHANNEL_CALLBACK_TABLE,
                callbackTable,
                callbackRows);
        CallbackProcessOutcome outcome = processCallbackIfPossible(callbackTable, commandDTO, channelCallbackResult, context, callbackId, now);
        TransactionChannelCallbackResultDTO resultDTO = new TransactionChannelCallbackResultDTO();
        resultDTO.setCallbackLogId(callbackLogId);
        resultDTO.setCallbackId(callbackId);
        resultDTO.setTransactionId(context.transactionId());
        resultDTO.setCallbackStatus(outcome.callbackStatus());
        resultDTO.setProcessResult(outcome.processResult());
        resultDTO.setFailReason(outcome.failReason());
        log.info("event: PAYMENT_CHANNEL_CALLBACK_END stage=CALLBACK traceId: {} channelCode: {} callbackLogId: {} callbackId: {} transactionId: {} operationId: {} channelOrderNo: {} channelTransactionId: {} callbackStatus: {} processResult: {} merchantNotifyEvent: {} durationMs: {}",
                TraceContext.getTraceId(),
                normalizeChannelCode(commandDTO.getChannelCode()),
                callbackLogId,
                callbackId,
                context.transactionId(),
                context.operationId(),
                context.channelOrderNo(),
                context.channelTransactionId(),
                outcome.callbackStatus(),
                outcome.processResult(),
                PROCESS_RESULT_STATUS_CHANGED.equals(outcome.processResult()),
                elapsedMillis(startNanos));
        return resultDTO;
    }

    /**
     * 构造渠道回调原始审计日志。
     *
     * <p>请求头和请求体在写库前统一脱敏，保存签名与 IP 校验结论；完整密钥、令牌、PAN、
     * CVV 和未脱敏渠道认证值不得进入日志实体。</p>
     *
     * @return 待写入对应交易季度分表的回调日志
     */
    private TransactionChannelCallbackLogDO buildCallbackLog(TransactionChannelCallbackCommandDTO commandDTO,
                                                            ChannelCallbackResult channelCallbackResult,
                                                            CallbackContext context,
                                                            String callbackLogId,
                                                            LocalDateTime receivedTime,
                                                            LocalDateTime now) {
        TransactionChannelCallbackLogDO logDO = new TransactionChannelCallbackLogDO();
        logDO.setCallbackLogId(callbackLogId);
        logDO.setTransactionId(context.transactionId());
        logDO.setOperationId(context.operationId());
        logDO.setChannelCode(normalizeChannelCode(commandDTO.getChannelCode()));
        logDO.setCallbackType(resolveCallbackType(commandDTO, channelCallbackResult));
        logDO.setChannelOrderNo(context.channelOrderNo());
        logDO.setChannelTransactionId(context.channelTransactionId());
        logDO.setRequestUri(commandDTO.getRequestUri());
        logDO.setHttpMethod(commandDTO.getHttpMethod());
        logDO.setSourceIp(commandDTO.getSourceIp());
        logDO.setRequestHeaderJsonMasked(maskedJson(commandDTO.getRequestHeaders()));
        logDO.setRequestBodyJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(commandDTO.getRequestBody()));
        logDO.setSignatureValid(Boolean.TRUE.equals(commandDTO.getSignatureValid()) ? 1 : 0);
        logDO.setIpAllowed(Boolean.TRUE.equals(commandDTO.getIpAllowed()) ? 1 : 0);
        logDO.setPlatformResponseCode("ACCEPTED");
        logDO.setPlatformResponseBody("{\"result\":\"ACCEPTED\"}");
        logDO.setCallbackReceivedTime(receivedTime);
        fillTransactionTime(logDO, context.transactionDateTime());
        logDO.setCreateTime(now);
        return logDO;
    }

    /**
     * 构造渠道回调对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 转换过程不改变来源对象的业务状态；敏感字段仅保留目标模型所需的最小集合。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param channelCallbackResult 已完成渠道验签和协议解析的回调结果，不包含可直接记录的敏感原文
     * @param context 当前交易或请求上下文，用于透传交易身份、分片时间和审计信息
     * @param callbackLogId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param callbackId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param idempotencyKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param receivedTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 构造、转换或解析后的业务值
     */
    private TransactionChannelCallbackDO buildCallback(TransactionChannelCallbackCommandDTO commandDTO,
                                                       ChannelCallbackResult channelCallbackResult,
                                                       CallbackContext context,
                                                       String callbackLogId,
                                                       String callbackId,
                                                       String idempotencyKey,
                                                       LocalDateTime receivedTime,
                                                       LocalDateTime now) {
        TransactionChannelCallbackDO callbackDO = new TransactionChannelCallbackDO();
        callbackDO.setCallbackId(callbackId);
        callbackDO.setCallbackLogId(callbackLogId);
        callbackDO.setTransactionId(context.transactionId());
        callbackDO.setOperationId(context.operationId());
        callbackDO.setChannelCode(normalizeChannelCode(commandDTO.getChannelCode()));
        callbackDO.setChannelOrderNo(context.channelOrderNo());
        callbackDO.setChannelTransactionId(context.channelTransactionId());
        callbackDO.setCallbackType(resolveCallbackType(commandDTO, channelCallbackResult));
        callbackDO.setChannelEventType(resolveChannelEventType(commandDTO, channelCallbackResult));
        callbackDO.setCallbackStatus(context.transactionIdResolved() ? CALLBACK_STATUS_RECEIVED : CALLBACK_STATUS_FAILED);
        callbackDO.setIdempotencyKey(idempotencyKey);
        callbackDO.setSignatureValid(Boolean.TRUE.equals(commandDTO.getSignatureValid()) ? 1 : 0);
        callbackDO.setIpAllowed(Boolean.TRUE.equals(commandDTO.getIpAllowed()) ? 1 : 0);
        callbackDO.setProcessResult(context.transactionIdResolved() ? PROCESS_RESULT_PENDING : null);
        callbackDO.setFailReason(context.transactionIdResolved() ? null : "transaction_id can not be resolved from callback");
        callbackDO.setCallbackReceivedTime(receivedTime);
        callbackDO.setProcessedTime(context.transactionIdResolved() ? null : now);
        fillTransactionTime(callbackDO, context.transactionDateTime());
        callbackDO.setVersion(INITIAL_VERSION);
        callbackDO.setDeleted(NOT_DELETED);
        callbackDO.setCreateTime(now);
        callbackDO.setUpdateTime(now);
        return callbackDO;
    }

    /**
     * 在回调事实已落库后推进交易状态，并把安全拒绝、待确认和终态处理结果回写到同一回调记录。
     * <p>
     * 只有安全校验通过且状态机 CAS 确认发生终态变化时才写出站事件；重复回调、3DS 中间态和未知状态
     * 均保留审计事实，但不得覆盖交易终态或触发重复商户通知。
     */
    private CallbackProcessOutcome processCallbackIfPossible(String callbackTable,
                                                             TransactionChannelCallbackCommandDTO commandDTO,
                                                             ChannelCallbackResult channelCallbackResult,
                                                             CallbackContext context,
                                                             String callbackId,
                                                             LocalDateTime now) {
        String securityFailReason = callbackSecurityFailReason(commandDTO);
        if (securityFailReason != null) {
            log.warn("event: PAYMENT_CHANNEL_CALLBACK_SECURITY_REJECTED stage=CALLBACK_SECURITY traceId: {} channelCode: {} callbackId: {} transactionId: {} operationId: {} channelOrderNo: {} channelTransactionId: {} signatureValid: {} ipAllowed: {} failReason: {}",
                    TraceContext.getTraceId(),
                    normalizeChannelCode(commandDTO.getChannelCode()),
                    callbackId,
                    context.transactionId(),
                    context.operationId(),
                    context.channelOrderNo(),
                    context.channelTransactionId(),
                    commandDTO.getSignatureValid(),
                    commandDTO.getIpAllowed(),
                    securityFailReason);
            return updateCallbackProcessResult(callbackTable,
                    context.transactionDateTime(),
                    callbackId,
                    CALLBACK_STATUS_FAILED,
                    null,
                    context.operationDO() == null ? null : context.operationDO().getTransactionStatus(),
                    null,
                    PROCESS_RESULT_SECURITY_REJECTED,
                    securityFailReason,
                    now);
        }
        if (!context.transactionIdResolved() || context.operationDO() == null || context.orderDO() == null) {
            log.warn("event: PAYMENT_CHANNEL_CALLBACK_UNRESOLVED stage=CALLBACK_PROCESS traceId: {} channelCode: {} transactionId: {} channelOrderNo: {} channelTransactionId: {}",
                    TraceContext.getTraceId(),
                    normalizeChannelCode(commandDTO.getChannelCode()),
                    context.transactionId(),
                    context.channelOrderNo(),
                    context.channelTransactionId());
            return updateCallbackProcessResult(callbackTable, context.transactionDateTime(), callbackId,
                    CALLBACK_STATUS_FAILED, null, null,
                    null, null, "transaction_id can not be resolved from callback", now);
        }
        ParsedCallbackStatus parsedStatus = parseCallbackStatus(commandDTO, channelCallbackResult, context.operationDO().getTransactionType());
        if (isThreeDsCallback(commandDTO, channelCallbackResult)) {
            log.info("event: PAYMENT_CHANNEL_CALLBACK_3DS_RECEIVED stage=CALLBACK_PROCESS traceId: {} channelCode: {} callbackId: {} transactionId: {} operationId: {} rawChannelStatus: {} channelTradeStatus: {}",
                    TraceContext.getTraceId(),
                    normalizeChannelCode(commandDTO.getChannelCode()),
                    callbackId,
                    context.operationDO().getTransactionId(),
                    context.operationDO().getOperationId(),
                    channelCallbackResult == null ? null : channelCallbackResult.getRawChannelStatus(),
                    channelCallbackResult == null ? null : channelCallbackResult.getChannelTradeStatus());
            return updateCallbackProcessResult(callbackTable, context.transactionDateTime(), callbackId,
                    CALLBACK_STATUS_RECEIVED,
                    parsedStatus.targetStatus(),
                    context.operationDO().getTransactionStatus(),
                    parsedStatus.targetStatus(),
                    PROCESS_RESULT_PENDING,
                    "3ds authentication callback waits payer authentication or payment result confirmation",
                    now);
        }
        if (parsedStatus.targetStatus() == null) {
            log.warn("event: PAYMENT_CHANNEL_CALLBACK_STATUS_UNMAPPED stage=CALLBACK_PROCESS traceId: {} channelCode: {} callbackId: {} transactionId: {} operationId: {} rawChannelStatus: {} channelTradeStatus: {}",
                    TraceContext.getTraceId(),
                    normalizeChannelCode(commandDTO.getChannelCode()),
                    callbackId,
                    context.operationDO().getTransactionId(),
                    context.operationDO().getOperationId(),
                    channelCallbackResult == null ? null : channelCallbackResult.getRawChannelStatus(),
                    channelCallbackResult == null ? null : channelCallbackResult.getChannelTradeStatus());
            return updateCallbackProcessResult(callbackTable, context.transactionDateTime(), callbackId,
                    CALLBACK_STATUS_RECEIVED, null,
                    context.operationDO().getTransactionStatus(), null, PROCESS_RESULT_PENDING,
                    "callback status can not be mapped yet", now);
        }
        if (!isTerminalStatus(parsedStatus.targetStatus())) {
            log.info("event: PAYMENT_CHANNEL_CALLBACK_NON_TERMINAL stage=CALLBACK_PROCESS traceId: {} channelCode: {} callbackId: {} transactionId: {} operationId: {} currentStatus: {} parsedStatus: {} channelStatus: {}",
                    TraceContext.getTraceId(),
                    normalizeChannelCode(commandDTO.getChannelCode()),
                    callbackId,
                    context.operationDO().getTransactionId(),
                    context.operationDO().getOperationId(),
                    context.operationDO().getTransactionStatus(),
                    parsedStatus.targetStatus(),
                    parsedStatus.channelStatus());
            return updateCallbackProcessResult(callbackTable, context.transactionDateTime(), callbackId,
                    CALLBACK_STATUS_RECEIVED,
                    parsedStatus.targetStatus(),
                    context.operationDO().getTransactionStatus(),
                    parsedStatus.targetStatus(),
                    PROCESS_RESULT_PENDING,
                    "callback status is non-terminal and waits channel query match",
                    now);
        }
        boolean changed = transactionRecordService.completeByChannelCallback(
                context.operationDO(),
                context.orderDO(),
                callbackId,
                parsedStatus.targetStatus(),
                parsedStatus.failReasonCode(),
                parsedStatus.failReasonMessage(),
                parsedStatus.channelStatus(),
                parsedStatus.channelResponseCode(),
                parsedStatus.channelResponseMessage());
        log.info("event: PAYMENT_CHANNEL_CALLBACK_PROCESS_UPDATE stage=CALLBACK_PROCESS traceId: {} channelCode: {} callbackId: {} transactionId: {} operationId: {} previousStatus: {} targetStatus: {} changed: {} channelStatus: {} channelResponseCode: {}",
                TraceContext.getTraceId(),
                normalizeChannelCode(commandDTO.getChannelCode()),
                callbackId,
                context.operationDO().getTransactionId(),
                context.operationDO().getOperationId(),
                context.operationDO().getTransactionStatus(),
                parsedStatus.targetStatus(),
                changed,
                parsedStatus.channelStatus(),
                parsedStatus.channelResponseCode());
        if (changed) {
            saveCallbackProcessedEvent(context, parsedStatus, callbackId, now);
        }
        return updateCallbackProcessResult(callbackTable,
                context.transactionDateTime(),
                callbackId,
                changed ? CALLBACK_STATUS_PROCESSED : CALLBACK_STATUS_IGNORED,
                parsedStatus.targetStatus(),
                context.operationDO().getTransactionStatus(),
                parsedStatus.targetStatus(),
                changed ? PROCESS_RESULT_STATUS_CHANGED : PROCESS_RESULT_TERMINAL_IGNORED,
                changed ? null : "operation is already terminal or state has changed",
                now);
    }

    /**
     * 判断 OpenAPI 入口安全校验结果是否允许继续推进交易状态。
     * <p>
     * 渠道回调即使已经落原文日志，只要签名或 IP 白名单未通过，就只能保留排障记录，不能更新交易终态或触发商户通知。
     *
     * @param commandDTO OpenAPI 转发的回调命令
     * @return 拒绝原因；为空表示允许继续业务处理
     */
    private String callbackSecurityFailReason(TransactionChannelCallbackCommandDTO commandDTO) {
        if (!Boolean.TRUE.equals(commandDTO.getSignatureValid())) {
            return "channel callback signature is not valid";
        }
        if (!Boolean.TRUE.equals(commandDTO.getIpAllowed())) {
            return "channel callback source ip is not allowed";
        }
        return null;
    }

    /**
     * 判断回调解析出的平台状态是否为终态。
     * <p>
     * AUTHORIZED 这类非终态回调只记录待处理，不触发交易完成和商户终态通知。
     *
     * @param transactionStatus 平台交易状态
     * @return true 表示成功或失败终态
     */
    private boolean isTerminalStatus(String transactionStatus) {
        return PaymentTransactionStatusEnum.SUCCESS.getCode().equals(transactionStatus)
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus);
    }

    /**
     * 为成功推进的渠道回调写入交易 FIFO Outbox 事件。
     * <p>
     * 事件号复用 callbackId 保证持久化幂等，消息组使用 operationId 保证同一交易动作严格有序；
     * 该写入必须与交易状态变更处于同一事务，禁止出现状态已提交但通知事件丢失的窗口。
     */
    private void saveCallbackProcessedEvent(CallbackContext context,
                                            ParsedCallbackStatus parsedStatus,
                                            String callbackId,
                                            LocalDateTime now) {
        TransactionOperationDO operationDO = context.operationDO();
        if (operationDO == null || operationDO.getTransactionDateTime() == null) {
            return;
        }
        TransactionEventMessage message = new TransactionEventMessage();
        message.setMessageId(callbackId);
        message.setCreatedAt(now);
        message.setTransactionId(operationDO.getTransactionId());
        message.setOperationId(operationDO.getOperationId());
        message.setMerchantId(operationDO.getMerchantId());
        message.setMerchantOrderNo(operationDO.getMerchantOrderNo());
        message.setTransactionType(operationDO.getTransactionType());
        message.setTransactionStatus(parsedStatus.targetStatus());
        message.setEventType(TransactionMqConstants.TRANSACTION_CALLBACK_PROCESSED_TAG);
        message.setTransactionDateTime(operationDO.getTransactionDateTime());

        TransactionEventOutboxDO eventDO = new TransactionEventOutboxDO();
        eventDO.setEventNo(callbackId);
        eventDO.setAggregateType(PAYMENT_TRANSACTION_AGGREGATE);
        eventDO.setAggregateNo(operationDO.getOperationId());
        eventDO.setTransactionId(operationDO.getTransactionId());
        eventDO.setOperationId(operationDO.getOperationId());
        eventDO.setMerchantId(operationDO.getMerchantId());
        eventDO.setMerchantOrderNo(operationDO.getMerchantOrderNo());
        eventDO.setTransactionType(operationDO.getTransactionType());
        eventDO.setEventType(TransactionMqConstants.TRANSACTION_CALLBACK_PROCESSED_TAG);
        eventDO.setEventStatus(EVENT_STATUS_INIT);
        eventDO.setTopic(MqTopic.PAYMENT_TRANSACTION_FIFO);
        eventDO.setTag(TransactionMqConstants.TRANSACTION_CALLBACK_PROCESSED_TAG);
        eventDO.setMessageKey(callbackId);
        eventDO.setMessageGroup(operationDO.getOperationId());
        eventDO.setPayloadJson(JsonUtils.toJsonString(message));
        eventDO.setEventTime(now);
        eventDO.setTransactionDateTime(operationDO.getTransactionDateTime());
        eventDO.setTransactionUtcTime(toUtcTime(operationDO.getTransactionDateTime()));
        eventDO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        eventDO.setRetryCount(0);
        eventDO.setMaxRetryCount(DEFAULT_EVENT_MAX_RETRY_COUNT);
        eventDO.setNextRetryTime(now);
        eventDO.setVersion(INITIAL_VERSION);
        eventDO.setDeleted(NOT_DELETED);
        eventDO.setCreateTime(now);
        eventDO.setUpdateTime(now);
        transactionEventOutboxService.save(eventDO);
        log.info("event: PAYMENT_CALLBACK_OUTBOX_SAVED stage=MQ traceId: {} callbackId: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} transactionType: {} eventType: {} topic: {} tag: {} retryCount: {}",
                TraceContext.getTraceId(),
                callbackId,
                operationDO.getTransactionId(),
                operationDO.getOperationId(),
                operationDO.getMerchantId(),
                operationDO.getMerchantOrderNo(),
                operationDO.getTransactionType(),
                eventDO.getEventType(),
                eventDO.getTopic(),
                eventDO.getTag(),
                eventDO.getRetryCount());
    }

    /**
     * 通过逻辑分表 Mapper 对回调处理状态执行版本和可处理状态双重 CAS。
     * <p>
     * 受影响行数不是 1 表示并发消费者已处理或记录身份异常，必须抛错回滚当前事务，不能把并发冲突伪装成成功。
     */
    private CallbackProcessOutcome updateCallbackProcessResult(String callbackTable,
                                                               LocalDateTime transactionDateTime,
                                                               String callbackId,
                                                               String callbackStatus,
                                                               String parsedTransactionStatus,
                                                               String previousTransactionStatus,
                                                               String targetTransactionStatus,
                                                               String processResult,
                                                               String failReason,
                                                               LocalDateTime processedTime) {
        int affectedRows = callbackMapper.updateProcessResultLogical(
                callbackId,
                transactionDateTime,
                INITIAL_VERSION,
                CALLBACK_PROCESSABLE_STATUSES,
                callbackStatus,
                parsedTransactionStatus,
                previousTransactionStatus,
                targetTransactionStatus,
                processResult,
                failReason,
                processedTime);
        log.info("event: PAYMENT_CALLBACK_DB_UPDATE stage=CALLBACK_PROCESS traceId: {} callbackId: {} callbackStatus: {} previousStatus: {} targetStatus: {} processResult: {} logicalTable: {} physicalTable: {} affectedRows: {}",
                TraceContext.getTraceId(),
                callbackId,
                callbackStatus,
                previousTransactionStatus,
                targetTransactionStatus,
                processResult,
                TRANSACTION_CHANNEL_CALLBACK_TABLE,
                callbackTable,
                affectedRows);
        if (affectedRows != 1) {
            log.warn("event: PAYMENT_CALLBACK_CAS_CONFLICT stage=CALLBACK_PROCESS traceId: {} callbackId: {} transactionDateTime: {} expectedVersion: {} expectedStatuses: {} affectedRows: {}",
                    TraceContext.getTraceId(),
                    callbackId,
                    transactionDateTime,
                    INITIAL_VERSION,
                    CALLBACK_PROCESSABLE_STATUSES,
                    affectedRows);
            throw new ServiceException(
                    ApiResultEnum.NETWORK_BUSY.getCode(), "callback process state has changed");
        }
        return new CallbackProcessOutcome(callbackStatus, processResult, failReason);
    }

    /**
     * 按渠道、幂等键和交易分片时间查询已存在的回调业务记录。
     */
    private TransactionChannelCallbackDO findByIdempotency(String callbackTable,
                                                           String channelCode,
                                                           String idempotencyKey,
                                                           LocalDateTime transactionDateTime) {
        return callbackMapper.selectByIdempotency(
                normalizeChannelCode(channelCode), idempotencyKey, transactionDateTime);
    }

    private TransactionChannelCallbackResultDTO duplicateResult(String callbackLogId,
                                                                String idempotencyKey,
                                                                long startNanos,
                                                                TransactionChannelCallbackDO existed) {
        log.info("event: PAYMENT_CHANNEL_CALLBACK_DUPLICATE stage=CALLBACK_IDEMPOTENCY traceId: {} channelCode: {} callbackLogId: {} callbackId: {} transactionId: {} operationId: {} channelOrderNo: {} channelTransactionId: {} idempotencyKey: {} callbackStatus: {} duplicated=true durationMs: {}",
                TraceContext.getTraceId(),
                existed.getChannelCode(),
                callbackLogId,
                existed.getCallbackId(),
                existed.getTransactionId(),
                existed.getOperationId(),
                existed.getChannelOrderNo(),
                existed.getChannelTransactionId(),
                idempotencyKey,
                existed.getCallbackStatus(),
                elapsedMillis(startNanos));
        TransactionChannelCallbackResultDTO resultDTO = new TransactionChannelCallbackResultDTO();
        resultDTO.setCallbackLogId(callbackLogId);
        resultDTO.setCallbackId(existed.getCallbackId());
        resultDTO.setTransactionId(existed.getTransactionId());
        resultDTO.setCallbackStatus(existed.getCallbackStatus());
        resultDTO.setProcessResult("DUPLICATE");
        resultDTO.setFailReason(existed.getFailReason());
        return resultDTO;
    }

    private CallbackContext resolveContext(TransactionChannelCallbackCommandDTO commandDTO,
                                           ChannelCallbackResult channelCallbackResult) {
        CallbackPayload payload = parsePayload(commandDTO, channelCallbackResult);
        String channelOrderNo = firstText(commandDTO.getChannelOrderNo(), payload.channelOrderNo(), commandDTO.getTransactionId(), payload.transactionId());
        String channelTransactionId = firstText(commandDTO.getChannelTransactionId(), payload.channelTransactionId());
        String transactionId = firstText(commandDTO.getTransactionId(), payload.transactionId(), channelOrderNo);
        LocalDateTime transactionDateTime = parseTransactionDateTime(transactionId);
        String operationId = null;
        TransactionOperationDO operationDO = null;
        TransactionOrderDO orderDO = null;
        boolean resolved = transactionDateTime != null;
        if (resolved) {
            try {
                operationDO = resolveCallbackOperation(
                        commandDTO, channelCallbackResult, transactionId, channelOrderNo, channelTransactionId);
                if (operationDO == null) {
                    resolved = false;
                } else {
                    LocalDateTime orderTransactionDateTime = parseOperationDateTime(operationDO.getOperationId());
                    if (orderTransactionDateTime == null) {
                        orderTransactionDateTime = operationDO.getTransactionDateTime();
                    }
                    orderDO = transactionRecordService.findOrder(orderTransactionDateTime, operationDO.getOperationId());
                    operationId = operationDO.getOperationId();
                    transactionId = operationDO.getTransactionId();
                    transactionDateTime = operationDO.getTransactionDateTime();
                    if (orderDO != null && !StringUtils.hasText(channelOrderNo)) {
                        channelOrderNo = orderDO.getRootTransactionId();
                    }
                }
            } catch (ServiceException exception) {
                resolved = false;
            }
        }
        if (transactionDateTime == null) {
            transactionDateTime = LocalDateTime.now();
        }
        return new CallbackContext(
                firstText(transactionId, commandDTO.getChannelOrderNo(), payload.channelOrderNo(), UNKNOWN_TRANSACTION_ID),
                operationId,
                firstText(channelOrderNo, transactionId),
                channelTransactionId,
                transactionDateTime,
                resolved,
                operationDO,
                orderDO
        );
    }

    /**
     * 解析回调动作，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 仅返回规范化或计算结果，不直接提交交易状态。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param channelCallbackResult 已完成渠道验签和协议解析的回调结果，不包含可直接记录的敏感原文
     * @param transactionId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param channelOrderNo 渠道订单号，用于回调关联、渠道查询和对账
     * @param channelTransactionId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @return 构造、转换或解析后的业务值
     */
    private TransactionOperationDO resolveCallbackOperation(TransactionChannelCallbackCommandDTO commandDTO,
                                                            ChannelCallbackResult channelCallbackResult,
                                                            String transactionId,
                                                            String channelOrderNo,
                                                            String channelTransactionId) {
        if (isThreeDsCallback(commandDTO, channelCallbackResult)) {
            return transactionRecordService.findSourceOperationByTransactionId(transactionId);
        }
        if (StringUtils.hasText(channelOrderNo) && StringUtils.hasText(channelTransactionId)) {
            try {
                return transactionRecordService.findOperationByChannelTransaction(channelOrderNo, channelTransactionId);
            } catch (ServiceException exception) {
                // 渠道部分回调可能只携带 order.id，继续按平台交易号降级定位原交易。
            }
        }
        return transactionRecordService.findSourceOperationByTransactionId(transactionId);
    }

    private CallbackPayload parsePayload(TransactionChannelCallbackCommandDTO commandDTO,
                                         ChannelCallbackResult channelCallbackResult) {
        return new CallbackPayload(
                firstText(commandDTO.getTransactionId(), channelCallbackResult == null ? null : channelCallbackResult.getChannelOrderNo()),
                firstText(commandDTO.getChannelOrderNo(), channelCallbackResult == null ? null : channelCallbackResult.getChannelOrderNo()),
                firstText(commandDTO.getChannelTransactionId(), channelCallbackResult == null ? null : channelCallbackResult.getChannelTransactionId()));
    }

    /**
     * 解析渠道回调对应的平台状态。
     * <p>
     * 这里复用 ChannelTransactionStatusResolver，确保同步响应、回调和查询勾兑对
     * AUTHORIZED/CAPTURED 等统一动作状态的判断一致。
     *
     * @param commandDTO 回调内部命令
     * @param channelCallbackResult 渠道回调解析结果
     * @param transactionType 原动作交易类型
     * @return 平台状态解析结果
     */
    private ParsedCallbackStatus parseCallbackStatus(TransactionChannelCallbackCommandDTO commandDTO,
                                                     ChannelCallbackResult channelCallbackResult,
                                                     String transactionType) {
        if (channelCallbackResult == null || !StringUtils.hasText(channelCallbackResult.getChannelTradeStatus())) {
            return new ParsedCallbackStatus(null, null, null, null, null, null);
        }
        ChannelTransactionStatusResolution resolution = channelStatusResolver.resolveCallback(
                commandDTO.getChannelCode(),
                transactionType,
                channelCallbackResult);
        return new ParsedCallbackStatus(resolution.getTargetStatus(),
                resolution.getFailReasonCode(),
                firstText(resolution.getFailReasonMessage(), channelCallbackResult.getChannelResponseMessage()),
                resolution.getChannelStatus(),
                resolution.getChannelResponseCode(),
                resolution.getChannelResponseMessage());
    }

    /**
     * 调用渠道回调处理器解析原文。
     * <p>
     * 渠道处理器异常时仍保留原始回调日志，后续可人工排查；生产验签和 IP 白名单结果来自 OpenAPI 回调入口，
     * 这里不补做外部安全判断。
     *
     * @param commandDTO 回调内部命令
     * @return 渠道解析结果，解析失败时为空
     */
    private ChannelCallbackResult parseByChannelHandler(TransactionChannelCallbackCommandDTO commandDTO) {
        if (callbackExecutor.isEmpty()) {
            return null;
        }
        try {
            ChannelCallbackRequest request = new ChannelCallbackRequest();
            request.setChannelCode(commandDTO.getChannelCode());
            request.setRequestUri(commandDTO.getRequestUri());
            request.setClientIp(commandDTO.getSourceIp());
            if (commandDTO.getRequestHeaders() != null) {
                request.setHeaders(commandDTO.getRequestHeaders());
            }
            request.setBody(commandDTO.getRequestBody());
            return callbackExecutor.get().execute(request);
        } catch (ChannelException exception) {
            return null;
        }
    }

    /**
     * 构造渠道回调幂等键。
     * <p>
     * 优先使用渠道处理器解析出的 callbackEventId；否则使用渠道订单号、渠道交易号、原始状态和回调类型组合。
     * 同一渠道交易可能先回调 AUTHORIZED 再回调 CAPTURED，因此幂等键必须包含原始状态，不能吞掉后续终态事件。
     *
     * @param commandDTO 回调内部命令
     * @param channelCallbackResult 渠道回调解析结果
     * @param context 回调定位上下文
     * @return 回调幂等键
     */
    private String buildIdempotencyKey(TransactionChannelCallbackCommandDTO commandDTO,
                                       ChannelCallbackResult channelCallbackResult,
                                       CallbackContext context) {
        String eventId = channelCallbackResult == null ? null : channelCallbackResult.getCallbackEventId();
        if (StringUtils.hasText(eventId)) {
            return normalizeChannelCode(commandDTO.getChannelCode()) + ":EVENT:" + eventId;
        }
        return normalizeChannelCode(commandDTO.getChannelCode())
                + ":ORDER:" + firstText(context.channelOrderNo(), "-")
                + ":TX:" + firstText(context.channelTransactionId(), "-")
                + ":STATUS:" + firstText(rawStatus(channelCallbackResult), commandDTO.getChannelEventType(), "-")
                + ":TYPE:" + resolveCallbackType(commandDTO, channelCallbackResult);
    }

    /**
     * 取渠道回调原始状态。
     *
     * @param channelCallbackResult 渠道回调解析结果
     * @return 原始状态或渠道统一状态
     */
    private String rawStatus(ChannelCallbackResult channelCallbackResult) {
        return channelCallbackResult == null ? null : firstText(
                channelCallbackResult.getRawChannelStatus(),
                channelCallbackResult.getChannelTradeStatus());
    }

    /**
     * 识别 3DS 认证回调，避免认证结果回调误走普通支付入账状态机。
     *
     * @param commandDTO 渠道回调内部命令
     * @return true 表示该回调只处理 3DS 认证状态
     */
    private boolean isThreeDsCallback(TransactionChannelCallbackCommandDTO commandDTO,
                                      ChannelCallbackResult channelCallbackResult) {
        if (channelCallbackResult != null && channelCallbackResult.getCallbackKind() != null) {
            return ChannelCallbackKind.THREE_DS_AUTHENTICATION.equals(channelCallbackResult.getCallbackKind());
        }
        return THREE_DS_CALLBACK_TYPE.equalsIgnoreCase(resolveCallbackType(commandDTO))
                || THREE_DS_EVENT_TYPE.equalsIgnoreCase(commandDTO.getChannelEventType());
    }

    /**
     * Provider 已解析业务类型时以 Provider 结果为准；旧 Provider 未返回时兼容入口标签。
     */
    private String resolveCallbackType(TransactionChannelCallbackCommandDTO commandDTO,
                                       ChannelCallbackResult channelCallbackResult) {
        if (channelCallbackResult != null
                && ChannelCallbackKind.THREE_DS_AUTHENTICATION.equals(channelCallbackResult.getCallbackKind())) {
            return THREE_DS_CALLBACK_TYPE;
        }
        if (channelCallbackResult != null
                && ChannelCallbackKind.FINANCIAL_TRANSACTION.equals(channelCallbackResult.getCallbackKind())) {
            return DEFAULT_CALLBACK_TYPE;
        }
        return resolveCallbackType(commandDTO);
    }

    /** Provider 明确识别为资金事件时清除入口遗留的 3DS event type。 */
    private String resolveChannelEventType(TransactionChannelCallbackCommandDTO commandDTO,
                                           ChannelCallbackResult channelCallbackResult) {
        if (channelCallbackResult != null
                && ChannelCallbackKind.FINANCIAL_TRANSACTION.equals(channelCallbackResult.getCallbackKind())) {
            return null;
        }
        if (channelCallbackResult != null
                && ChannelCallbackKind.THREE_DS_AUTHENTICATION.equals(channelCallbackResult.getCallbackKind())) {
            return THREE_DS_EVENT_TYPE;
        }
        return commandDTO.getChannelEventType();
    }

    private String resolveCallbackType(TransactionChannelCallbackCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getCallbackType()) ? commandDTO.getCallbackType() : DEFAULT_CALLBACK_TYPE;
    }

    private String normalizeChannelCode(String channelCode) {
        return channelCode == null ? null : channelCode.toUpperCase(Locale.ROOT);
    }

    private String maskedJson(Object value) {
        if (value == null) {
            return null;
        }
        return SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(value));
    }

    private void fillTransactionTime(TransactionChannelCallbackLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    private void fillTransactionTime(TransactionChannelCallbackDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime) {
        return transactionDateTime.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * 返回当前模式下用于审计日志的表标识，不参与 SQL 拼接。
     *
     * @param logicalTable 逻辑表名
     * @param transactionDateTime 交易分片时间
     * @return 逻辑表名
     */
    private String tableForLog(String logicalTable, LocalDateTime transactionDateTime) {
        return logicalTable;
    }

    private LocalDateTime parseTransactionDateTime(String transactionId) {
        return transactionShardingKeyParser.parseTransactionDateTime(transactionId);
    }

    private LocalDateTime parseOperationDateTime(String operationId) {
        return transactionShardingKeyParser.parseOperationDateTime(operationId);
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /** 将不可信渠道回调正文转换为 UTF-8 字节长度和不可逆 SHA-256 日志元数据。 */
    private CallbackBodyLogMetadata callbackBodyLogMetadata(String requestBody) {
        byte[] bodyBytes = requestBody == null
                ? new byte[0]
                : requestBody.getBytes(StandardCharsets.UTF_8);
        try {
            String sha256 = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bodyBytes));
            return new CallbackBodyLogMetadata(bodyBytes.length, sha256);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /** 渠道回调正文的安全日志元数据，不保留任何可逆原文。 */
    private record CallbackBodyLogMetadata(int length, String sha256) {
    }

    private void validate(TransactionChannelCallbackCommandDTO commandDTO) {
        if (commandDTO == null || !StringUtils.hasText(commandDTO.getChannelCode())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private record CallbackPayload(String transactionId, String channelOrderNo, String channelTransactionId) {
    }

    private record CallbackContext(String transactionId,
                                   String operationId,
                                   String channelOrderNo,
                                   String channelTransactionId,
                                   LocalDateTime transactionDateTime,
                                   boolean transactionIdResolved,
                                   TransactionOperationDO operationDO,
                                   TransactionOrderDO orderDO) {
    }

    private record ParsedCallbackStatus(String targetStatus,
                                        String failReasonCode,
                                        String failReasonMessage,
                                        String channelStatus,
                                        String channelResponseCode,
                                        String channelResponseMessage) {
    }

    private record CallbackProcessOutcome(String callbackStatus,
                                          String processResult,
                                          String failReason) {
    }
}
