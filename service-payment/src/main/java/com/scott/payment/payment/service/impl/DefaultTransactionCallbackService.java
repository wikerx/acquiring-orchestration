package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.executor.PaymentChannelCallbackExecutor;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackResultDTO;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
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
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.support.TransactionShardingSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
    private static final int DEFAULT_EVENT_MAX_RETRY_COUNT = 10;

    /**
     * 初始版本号。
     */
    private static final int INITIAL_VERSION = 0;

    /**
     * 未删除标识。
     */
    private static final int NOT_DELETED = 0;

    private final TransactionChannelCallbackLogMapper callbackLogMapper;

    private final TransactionChannelCallbackMapper callbackMapper;

    private final TransactionRecordService transactionRecordService;

    private final TransactionEventOutboxService transactionEventOutboxService;

    private final TransactionShardingSupport shardingSupport;

    private final Optional<PaymentChannelCallbackExecutor> callbackExecutor;

    /**
     * 创建交易渠道回调服务默认实现。
     *
     * @param callbackLogMapper 渠道回调原始日志 Mapper
     * @param callbackMapper 渠道回调业务 Mapper
     * @param transactionRecordService 交易事实记录服务
     * @param transactionEventOutboxService 交易本地事件服务
     * @param shardingSupport 交易分表支撑组件
     * @param callbackExecutor 渠道回调执行器，可为空以兼容尚未接入回调 SPI 的测试场景
     */
    public DefaultTransactionCallbackService(TransactionChannelCallbackLogMapper callbackLogMapper,
                                             TransactionChannelCallbackMapper callbackMapper,
                                             TransactionRecordService transactionRecordService,
                                             TransactionEventOutboxService transactionEventOutboxService,
                                             TransactionShardingSupport shardingSupport,
                                             Optional<PaymentChannelCallbackExecutor> callbackExecutor) {
        this.callbackLogMapper = callbackLogMapper;
        this.callbackMapper = callbackMapper;
        this.transactionRecordService = transactionRecordService;
        this.transactionEventOutboxService = transactionEventOutboxService;
        this.shardingSupport = shardingSupport;
        this.callbackExecutor = callbackExecutor == null ? Optional.empty() : callbackExecutor;
    }

    /**
     * 记录渠道回调原文和业务回调记录。
     *
     * @param commandDTO 渠道回调内部命令
     * @return 回调处理结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionChannelCallbackResultDTO recordChannelCallback(TransactionChannelCallbackCommandDTO commandDTO) {
        validate(commandDTO);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime receivedTime = commandDTO.getReceivedTime() == null ? now : commandDTO.getReceivedTime();
        ChannelCallbackResult channelCallbackResult = parseByChannelHandler(commandDTO);
        CallbackContext context = resolveContext(commandDTO, channelCallbackResult);
        String callbackLogId = PaymentOrderNoGenerator.nextOrderNo(CALLBACK_LOG_PREFIX, context.transactionDateTime());
        String callbackId = PaymentOrderNoGenerator.nextOrderNo(CALLBACK_PREFIX, context.transactionDateTime());
        String callbackTable = shardingSupport.physicalTable(TRANSACTION_CHANNEL_CALLBACK_TABLE, context.transactionDateTime());
        callbackLogMapper.insertPhysical(
                shardingSupport.physicalTable(TRANSACTION_CHANNEL_CALLBACK_LOG_TABLE, context.transactionDateTime()),
                buildCallbackLog(commandDTO, context, callbackLogId, receivedTime, now));
        String idempotencyKey = buildIdempotencyKey(commandDTO, context);
        TransactionChannelCallbackDO existed = callbackMapper.selectByIdempotencyPhysical(
                callbackTable, normalizeChannelCode(commandDTO.getChannelCode()), idempotencyKey);
        if (existed != null) {
            return duplicateResult(callbackLogId, existed);
        }
        callbackMapper.insertPhysical(callbackTable,
                buildCallback(commandDTO, context, callbackLogId, callbackId, idempotencyKey, receivedTime, now));
        CallbackProcessOutcome outcome = processCallbackIfPossible(callbackTable, commandDTO, channelCallbackResult, context, callbackId, now);
        TransactionChannelCallbackResultDTO resultDTO = new TransactionChannelCallbackResultDTO();
        resultDTO.setCallbackLogId(callbackLogId);
        resultDTO.setCallbackId(callbackId);
        resultDTO.setTransactionId(context.transactionId());
        resultDTO.setCallbackStatus(outcome.callbackStatus());
        resultDTO.setProcessResult(outcome.processResult());
        resultDTO.setFailReason(outcome.failReason());
        return resultDTO;
    }

    private TransactionChannelCallbackLogDO buildCallbackLog(TransactionChannelCallbackCommandDTO commandDTO,
                                                            CallbackContext context,
                                                            String callbackLogId,
                                                            LocalDateTime receivedTime,
                                                            LocalDateTime now) {
        TransactionChannelCallbackLogDO logDO = new TransactionChannelCallbackLogDO();
        logDO.setCallbackLogId(callbackLogId);
        logDO.setTransactionId(context.transactionId());
        logDO.setOperationId(context.operationId());
        logDO.setChannelCode(normalizeChannelCode(commandDTO.getChannelCode()));
        logDO.setCallbackType(resolveCallbackType(commandDTO));
        logDO.setChannelOrderNo(context.channelOrderNo());
        logDO.setChannelTransactionId(context.channelTransactionId());
        logDO.setRequestUri(commandDTO.getRequestUri());
        logDO.setHttpMethod(commandDTO.getHttpMethod());
        logDO.setSourceIp(commandDTO.getSourceIp());
        logDO.setRequestHeaderJsonMasked(maskedJson(commandDTO.getRequestHeaders()));
        logDO.setRequestBodyJsonMasked(SensitiveDataMaskUtils.maskJson(commandDTO.getRequestBody()));
        logDO.setSignatureValid(Boolean.TRUE.equals(commandDTO.getSignatureValid()) ? 1 : 0);
        logDO.setIpAllowed(Boolean.TRUE.equals(commandDTO.getIpAllowed()) ? 1 : 0);
        logDO.setPlatformResponseCode("ACCEPTED");
        logDO.setPlatformResponseBody("{\"result\":\"ACCEPTED\"}");
        logDO.setCallbackReceivedTime(receivedTime);
        fillTransactionTime(logDO, context.transactionDateTime());
        logDO.setCreateTime(now);
        return logDO;
    }

    private TransactionChannelCallbackDO buildCallback(TransactionChannelCallbackCommandDTO commandDTO,
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
        callbackDO.setCallbackType(resolveCallbackType(commandDTO));
        callbackDO.setChannelEventType(commandDTO.getChannelEventType());
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

    private CallbackProcessOutcome processCallbackIfPossible(String callbackTable,
                                                             TransactionChannelCallbackCommandDTO commandDTO,
                                                             ChannelCallbackResult channelCallbackResult,
                                                             CallbackContext context,
                                                             String callbackId,
                                                             LocalDateTime now) {
        if (!context.transactionIdResolved() || context.operationDO() == null || context.orderDO() == null) {
            return updateCallbackProcessResult(callbackTable, callbackId, CALLBACK_STATUS_FAILED, null, null,
                    null, null, "transaction_id can not be resolved from callback", now);
        }
        ParsedCallbackStatus parsedStatus = parseCallbackStatus(commandDTO, channelCallbackResult);
        if (parsedStatus.targetStatus() == null) {
            return updateCallbackProcessResult(callbackTable, callbackId, CALLBACK_STATUS_RECEIVED, null,
                    context.operationDO().getTransactionStatus(), null, PROCESS_RESULT_PENDING,
                    "callback status can not be mapped yet", now);
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
        if (changed) {
            saveCallbackProcessedEvent(context, parsedStatus, callbackId, now);
        }
        return updateCallbackProcessResult(callbackTable,
                callbackId,
                changed ? CALLBACK_STATUS_PROCESSED : CALLBACK_STATUS_IGNORED,
                parsedStatus.targetStatus(),
                context.operationDO().getTransactionStatus(),
                parsedStatus.targetStatus(),
                changed ? PROCESS_RESULT_STATUS_CHANGED : PROCESS_RESULT_TERMINAL_IGNORED,
                changed ? null : "operation is already terminal or state has changed",
                now);
    }

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
        eventDO.setTopic(MqTopic.PAYMENT_EVENT);
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
    }

    private CallbackProcessOutcome updateCallbackProcessResult(String callbackTable,
                                                               String callbackId,
                                                               String callbackStatus,
                                                               String parsedTransactionStatus,
                                                               String previousTransactionStatus,
                                                               String targetTransactionStatus,
                                                               String processResult,
                                                               String failReason,
                                                               LocalDateTime processedTime) {
        callbackMapper.updateProcessResultPhysical(callbackTable,
                callbackId,
                callbackStatus,
                parsedTransactionStatus,
                previousTransactionStatus,
                targetTransactionStatus,
                processResult,
                failReason,
                processedTime);
        return new CallbackProcessOutcome(callbackStatus, processResult, failReason);
    }

    private TransactionChannelCallbackResultDTO duplicateResult(String callbackLogId, TransactionChannelCallbackDO existed) {
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
        LocalDateTime transactionDateTime = shardingSupport.parseTransactionDateTime(transactionId);
        String operationId = null;
        TransactionOperationDO operationDO = null;
        TransactionOrderDO orderDO = null;
        boolean resolved = transactionDateTime != null;
        if (resolved) {
            try {
                operationDO = resolveCallbackOperation(transactionId, channelOrderNo, channelTransactionId);
                LocalDateTime orderTransactionDateTime = shardingSupport.parseOperationDateTime(operationDO.getOperationId());
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

    private TransactionOperationDO resolveCallbackOperation(String transactionId,
                                                            String channelOrderNo,
                                                            String channelTransactionId) {
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

    private ParsedCallbackStatus parseCallbackStatus(TransactionChannelCallbackCommandDTO commandDTO,
                                                     ChannelCallbackResult channelCallbackResult) {
        if (channelCallbackResult == null || !StringUtils.hasText(channelCallbackResult.getChannelTradeStatus())) {
            return new ParsedCallbackStatus(null, null, null, null, null, null);
        }
        if (ChannelTradeStatus.SUCCESS.getCode().equals(channelCallbackResult.getChannelTradeStatus())) {
            return new ParsedCallbackStatus(PaymentTransactionStatusEnum.SUCCESS.getCode(),
                    null,
                    null,
                    channelCallbackResult.getRawChannelStatus(),
                    channelCallbackResult.getChannelResponseCode(),
                    channelCallbackResult.getChannelResponseMessage());
        }
        if (ChannelTradeStatus.FAILED.getCode().equals(channelCallbackResult.getChannelTradeStatus())) {
            return new ParsedCallbackStatus(PaymentTransactionStatusEnum.FAILED.getCode(),
                    PaymentFailureReasonEnum.CHANNEL_REQUEST_FAILED.getCode(),
                    firstText(channelCallbackResult.getChannelResponseMessage(), "channel callback declined"),
                    channelCallbackResult.getRawChannelStatus(),
                    channelCallbackResult.getChannelResponseCode(),
                    channelCallbackResult.getChannelResponseMessage());
        }
        return new ParsedCallbackStatus(null, null, null,
                channelCallbackResult.getRawChannelStatus(),
                channelCallbackResult.getChannelResponseCode(),
                channelCallbackResult.getChannelResponseMessage());
    }

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

    private String buildIdempotencyKey(TransactionChannelCallbackCommandDTO commandDTO, CallbackContext context) {
        return normalizeChannelCode(commandDTO.getChannelCode())
                + ":"
                + firstText(context.channelOrderNo(), "-")
                + ":"
                + firstText(context.channelTransactionId(), "-")
                + ":"
                + resolveCallbackType(commandDTO);
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
        return SensitiveDataMaskUtils.maskJson(JsonUtils.toJsonString(value));
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
