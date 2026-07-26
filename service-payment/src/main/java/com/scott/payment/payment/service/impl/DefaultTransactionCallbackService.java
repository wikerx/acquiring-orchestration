package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.executor.PaymentChannelCallbackExecutor;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingSingleTableContext;
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
import org.springframework.beans.factory.annotation.Autowired;
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
     * transaction Record Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionRecordService transactionRecordService;

    /**
     * transaction Event Outbox Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionEventOutboxService transactionEventOutboxService;

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
     * callback Executor 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
     * @param shardingDataTemplate 分表数据访问统一入口
     * @param transactionShardingKeyParser 交易分表键解析器
     * @param callbackExecutor 渠道回调执行器，可为空以兼容尚未接入回调 SPI 的测试场景
     */
    public DefaultTransactionCallbackService(TransactionChannelCallbackLogMapper callbackLogMapper,
                                             TransactionChannelCallbackMapper callbackMapper,
                                             TransactionRecordService transactionRecordService,
                                             TransactionEventOutboxService transactionEventOutboxService,
                                             ShardingDataTemplate shardingDataTemplate,
                                             TransactionShardingKeyParser transactionShardingKeyParser,
                                             Optional<PaymentChannelCallbackExecutor> callbackExecutor) {
        this(callbackLogMapper,
                callbackMapper,
                transactionRecordService,
                transactionEventOutboxService,
                shardingDataTemplate,
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
     * @param shardingDataTemplate 分表数据访问统一入口
     * @param transactionShardingKeyParser 交易分表键解析器
     * @param callbackExecutor 渠道回调执行器，可为空以兼容尚未接入回调 SPI 的测试场景
     * @param channelStatusResolver 渠道状态解析服务
     */
    @Autowired
    public DefaultTransactionCallbackService(TransactionChannelCallbackLogMapper callbackLogMapper,
                                             TransactionChannelCallbackMapper callbackMapper,
                                             TransactionRecordService transactionRecordService,
                                             TransactionEventOutboxService transactionEventOutboxService,
                                             ShardingDataTemplate shardingDataTemplate,
                                             TransactionShardingKeyParser transactionShardingKeyParser,
                                             Optional<PaymentChannelCallbackExecutor> callbackExecutor,
                                             ChannelTransactionStatusResolver channelStatusResolver) {
        this.callbackLogMapper = callbackLogMapper;
        this.callbackMapper = callbackMapper;
        this.transactionRecordService = transactionRecordService;
        this.transactionEventOutboxService = transactionEventOutboxService;
        this.shardingDataTemplate = shardingDataTemplate;
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
    @Transactional(rollbackFor = Exception.class)
    public TransactionChannelCallbackResultDTO recordChannelCallback(TransactionChannelCallbackCommandDTO commandDTO) {
        validate(commandDTO);
        long startNanos = System.nanoTime();
        log.info("event: PAYMENT_CHANNEL_CALLBACK_START channelCode: {} callbackType: {} requestUri: {} sourceIp: {} signatureValid: {} ipAllowed: {}",
                normalizeChannelCode(commandDTO.getChannelCode()),
                resolveCallbackType(commandDTO),
                commandDTO.getRequestUri(),
                commandDTO.getSourceIp(),
                commandDTO.getSignatureValid(),
                commandDTO.getIpAllowed());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime receivedTime = commandDTO.getReceivedTime() == null ? now : commandDTO.getReceivedTime();
        ChannelCallbackResult channelCallbackResult = parseByChannelHandler(commandDTO);
        CallbackContext context = resolveContext(commandDTO, channelCallbackResult);
        String callbackLogId = PaymentOrderNoGenerator.nextOrderNo(CALLBACK_LOG_PREFIX, context.transactionDateTime());
        String callbackId = PaymentOrderNoGenerator.nextOrderNo(CALLBACK_PREFIX, context.transactionDateTime());
        String callbackTable = physicalTable(TRANSACTION_CHANNEL_CALLBACK_TABLE, context.transactionDateTime());
        callbackLogMapper.insertPhysical(
                physicalTable(TRANSACTION_CHANNEL_CALLBACK_LOG_TABLE, context.transactionDateTime()),
                buildCallbackLog(commandDTO, context, callbackLogId, receivedTime, now));
        String idempotencyKey = buildIdempotencyKey(commandDTO, channelCallbackResult, context);
        TransactionChannelCallbackDO existed = callbackMapper.selectByIdempotencyPhysical(
                callbackTable, normalizeChannelCode(commandDTO.getChannelCode()), idempotencyKey);
        if (existed != null) {
            log.info("event: PAYMENT_CHANNEL_CALLBACK_DUPLICATE channelCode: {} callbackLogId: {} callbackId: {} transactionId: {} operationId: {} idempotencyKey: {} callbackStatus: {} durationMs: {}",
                    normalizeChannelCode(commandDTO.getChannelCode()),
                    callbackLogId,
                    existed.getCallbackId(),
                    existed.getTransactionId(),
                    existed.getOperationId(),
                    idempotencyKey,
                    existed.getCallbackStatus(),
                    elapsedMillis(startNanos));
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
        log.info("event: PAYMENT_CHANNEL_CALLBACK_END channelCode: {} callbackLogId: {} callbackId: {} transactionId: {} operationId: {} channelOrderNo: {} channelTransactionId: {} callbackStatus: {} processResult: {} durationMs: {}",
                normalizeChannelCode(commandDTO.getChannelCode()),
                callbackLogId,
                callbackId,
                context.transactionId(),
                context.operationId(),
                context.channelOrderNo(),
                context.channelTransactionId(),
                outcome.callbackStatus(),
                outcome.processResult(),
                elapsedMillis(startNanos));
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

/**
 * 执行 process Callback If Possible 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param callbackTable callback Table 输入值，含义由调用方法名称和所属业务对象限定
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelCallbackResult channel Callback Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param context context 输入值，含义由调用方法名称和所属业务对象限定
 * @param callbackId callback Id 输入值，含义由调用方法名称和所属业务对象限定
 * @param now now 输入值，含义由调用方法名称和所属业务对象限定
 * @return 方法签名声明的返回值，具体结构由返回类型定义
 */
    private CallbackProcessOutcome processCallbackIfPossible(String callbackTable,
                                                             TransactionChannelCallbackCommandDTO commandDTO,
                                                             ChannelCallbackResult channelCallbackResult,
                                                             CallbackContext context,
                                                             String callbackId,
                                                             LocalDateTime now) {
        if (!context.transactionIdResolved() || context.operationDO() == null || context.orderDO() == null) {
            log.warn("event: PAYMENT_CHANNEL_CALLBACK_UNRESOLVED channelCode: {} transactionId: {} channelOrderNo: {} channelTransactionId: {}",
                    normalizeChannelCode(commandDTO.getChannelCode()),
                    context.transactionId(),
                    context.channelOrderNo(),
                    context.channelTransactionId());
            return updateCallbackProcessResult(callbackTable, callbackId, CALLBACK_STATUS_FAILED, null, null,
                    null, null, "transaction_id can not be resolved from callback", now);
        }
        ParsedCallbackStatus parsedStatus = parseCallbackStatus(commandDTO, channelCallbackResult, context.operationDO().getTransactionType());
        if (parsedStatus.targetStatus() == null) {
            log.warn("event: PAYMENT_CHANNEL_CALLBACK_STATUS_UNMAPPED channelCode: {} callbackId: {} transactionId: {} operationId: {} rawChannelStatus: {} channelTradeStatus: {}",
                    normalizeChannelCode(commandDTO.getChannelCode()),
                    callbackId,
                    context.operationDO().getTransactionId(),
                    context.operationDO().getOperationId(),
                    channelCallbackResult == null ? null : channelCallbackResult.getRawChannelStatus(),
                    channelCallbackResult == null ? null : channelCallbackResult.getChannelTradeStatus());
            return updateCallbackProcessResult(callbackTable, callbackId, CALLBACK_STATUS_RECEIVED, null,
                    context.operationDO().getTransactionStatus(), null, PROCESS_RESULT_PENDING,
                    "callback status can not be mapped yet", now);
        }
        if (!isTerminalStatus(parsedStatus.targetStatus())) {
            log.info("event: PAYMENT_CHANNEL_CALLBACK_NON_TERMINAL channelCode: {} callbackId: {} transactionId: {} operationId: {} currentStatus: {} parsedStatus: {} channelStatus: {}",
                    normalizeChannelCode(commandDTO.getChannelCode()),
                    callbackId,
                    context.operationDO().getTransactionId(),
                    context.operationDO().getOperationId(),
                    context.operationDO().getTransactionStatus(),
                    parsedStatus.targetStatus(),
                    parsedStatus.channelStatus());
            return updateCallbackProcessResult(callbackTable, callbackId, CALLBACK_STATUS_RECEIVED,
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
        log.info("event: PAYMENT_CHANNEL_CALLBACK_PROCESS_UPDATE channelCode: {} callbackId: {} transactionId: {} operationId: {} previousStatus: {} targetStatus: {} changed: {} channelStatus: {} channelResponseCode: {}",
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
     * 判断回调解析出的平台状态是否为终态。
     * <p>
     * WorldPay AUTHORISED 这类非终态回调只记录待处理，不触发交易完成和商户终态通知。
     *
     * @param transactionStatus 平台交易状态
     * @return true 表示成功或失败终态
     */
    private boolean isTerminalStatus(String transactionStatus) {
        return PaymentTransactionStatusEnum.SUCCESS.getCode().equals(transactionStatus)
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus);
    }

/**
 * 执行 save Callback Processed Event 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param context context 输入值，含义由调用方法名称和所属业务对象限定
 * @param parsedStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param callbackId callback Id 输入值，含义由调用方法名称和所属业务对象限定
 * @param now now 输入值，含义由调用方法名称和所属业务对象限定
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
        log.info("event: PAYMENT_CALLBACK_OUTBOX_SAVED callbackId: {} transactionId: {} operationId: {} eventType: {} topic: {} tag: {}",
                callbackId,
                operationDO.getTransactionId(),
                operationDO.getOperationId(),
                eventDO.getEventType(),
                eventDO.getTopic(),
                eventDO.getTag());
    }

/**
 * 执行 update Callback Process Result 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param callbackTable callback Table 输入值，含义由调用方法名称和所属业务对象限定
 * @param callbackId callback Id 输入值，含义由调用方法名称和所属业务对象限定
 * @param callbackStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param parsedTransactionStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param previousTransactionStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param targetTransactionStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param processResult process Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param failReason fail Reason 输入值，含义由调用方法名称和所属业务对象限定
 * @param processedTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @return 方法签名声明的返回值，具体结构由返回类型定义
 */
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

    /**
     * 执行 duplicate Result 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param callbackLogId callback Log Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param existed existed 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
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

/**
 * 执行 resolve Context 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelCallbackResult channel Callback Result 输入值，含义由调用方法名称和所属业务对象限定
 * @return 解析或查询得到的业务值
 */
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
                operationDO = resolveCallbackOperation(transactionId, channelOrderNo, channelTransactionId);
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
 * 执行 resolve Callback Operation 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param channelOrderNo channel Order No 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @return 渠道 API 操作类型或平台操作映射结果
 */
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

/**
 * 执行 parse Payload 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelCallbackResult channel Callback Result 输入值，含义由调用方法名称和所属业务对象限定
 * @return 解析后的内部数据结构或业务值
 */
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
     * 这里复用 ChannelTransactionStatusResolver，确保同步响应、回调和查询勾兑对 WPGXML/WPGJSON
     * AUTHORISED/CAPTURED 的判断一致。
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
     * WorldPay 可能先回调 AUTHORISED 再回调 CAPTURED，因此幂等键必须包含原始状态，不能吞掉后续终态事件。
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
                + ":TYPE:" + resolveCallbackType(commandDTO);
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
     * 执行 resolve Callback Type 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolveCallbackType(TransactionChannelCallbackCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getCallbackType()) ? commandDTO.getCallbackType() : DEFAULT_CALLBACK_TYPE;
    }

    /**
     * 执行 normalize Channel Code 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param channelCode channel Code 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private String normalizeChannelCode(String channelCode) {
        return channelCode == null ? null : channelCode.toUpperCase(Locale.ROOT);
    }

    /**
     * 执行 masked Json 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String maskedJson(Object value) {
        if (value == null) {
            return null;
        }
        return SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(value));
    }

    /**
     * 执行 fill Transaction Time 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param target target 输入值，含义由调用方法名称和所属业务对象限定
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void fillTransactionTime(TransactionChannelCallbackLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 执行 fill Transaction Time 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param target target 输入值，含义由调用方法名称和所属业务对象限定
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void fillTransactionTime(TransactionChannelCallbackDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 执行 to Utc Time 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 转换或构建后的目标对象
     */
    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime) {
        return transactionDateTime.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * 执行 physical Table 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param logicalTable logical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String physicalTable(String logicalTable, LocalDateTime transactionDateTime) {
        return shardingDataTemplate.resolvePhysicalTable(
                ShardingSingleTableContext.of(logicalTable, transactionDateTime, DataSourceName.MASTER));
    }

    /**
     * 执行 parse Transaction Date Time 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
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
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private LocalDateTime parseOperationDateTime(String operationId) {
        return transactionShardingKeyParser.parseOperationDateTime(operationId);
    }

    /**
     * 执行 elapsed Millis 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param startNanos start Nanos 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 执行 first Text 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param values values 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
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

    /**
     * 执行 validate 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionCallbackService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
     */
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
