package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.executor.PaymentChannelCallbackExecutor;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
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
     * callback Log Mapper 依赖，用于 Default Transaction Callback Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
     * </p>
     */
    private final TransactionChannelCallbackLogMapper callbackLogMapper;

    /**
     * callback Mapper 依赖，用于 Default Transaction Callback Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
     * </p>
     */
    private final TransactionChannelCallbackMapper callbackMapper;

    /**
     * transaction Record Service 依赖，用于 Default Transaction Callback Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionRecordService transactionRecordService;

    /**
     * transaction Event Outbox Service 依赖，用于 Default Transaction Callback Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionEventOutboxService transactionEventOutboxService;

    /**
     * sharding Data Template，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ShardingDataTemplate shardingDataTemplate;

    /**
     * transaction Sharding Key Parser，用于保存 Default Transaction Callback Service 中与 交易sharding密钥parser 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionShardingKeyParser transactionShardingKeyParser;

    /**
     * callback Executor，用于保存 Default Transaction Callback Service 中与 回调executor 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
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
        log.info("event: PAYMENT_CHANNEL_CALLBACK_START stage=CALLBACK traceId: {} channelCode: {} callbackType: {} requestUri: {} sourceIp: {} signatureValid: {} ipAllowed: {} bodySummary: {}",
                TraceContext.getTraceId(),
                normalizeChannelCode(commandDTO.getChannelCode()),
                resolveCallbackType(commandDTO),
                commandDTO.getRequestUri(),
                commandDTO.getSourceIp(),
                commandDTO.getSignatureValid(),
                commandDTO.getIpAllowed(),
                safeLength(SensitiveDataMaskUtils.maskJsonSafely(commandDTO.getRequestBody()), 1200));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime receivedTime = commandDTO.getReceivedTime() == null ? now : commandDTO.getReceivedTime();
        ChannelCallbackResult channelCallbackResult = parseByChannelHandler(commandDTO);
        CallbackContext context = resolveContext(commandDTO, channelCallbackResult);
        String callbackLogId = PaymentOrderNoGenerator.nextOrderNo(CALLBACK_LOG_PREFIX, context.transactionDateTime());
        String callbackId = PaymentOrderNoGenerator.nextOrderNo(CALLBACK_PREFIX, context.transactionDateTime());
        String callbackTable = physicalTable(TRANSACTION_CHANNEL_CALLBACK_TABLE, context.transactionDateTime());
        String callbackLogTable = physicalTable(TRANSACTION_CHANNEL_CALLBACK_LOG_TABLE, context.transactionDateTime());
        int callbackLogRows = callbackLogMapper.insertPhysical(
                callbackLogTable,
                buildCallbackLog(commandDTO, context, callbackLogId, receivedTime, now));
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
        TransactionChannelCallbackDO existed = callbackMapper.selectByIdempotencyPhysical(
                callbackTable, normalizeChannelCode(commandDTO.getChannelCode()), idempotencyKey);
        if (existed != null) {
            log.info("event: PAYMENT_CHANNEL_CALLBACK_DUPLICATE stage=CALLBACK_IDEMPOTENCY traceId: {} channelCode: {} callbackLogId: {} callbackId: {} transactionId: {} operationId: {} channelOrderNo: {} channelTransactionId: {} idempotencyKey: {} callbackStatus: {} duplicated=true durationMs: {}",
                    TraceContext.getTraceId(),
                    normalizeChannelCode(commandDTO.getChannelCode()),
                    callbackLogId,
                    existed.getCallbackId(),
                    existed.getTransactionId(),
                    existed.getOperationId(),
                    existed.getChannelOrderNo(),
                    existed.getChannelTransactionId(),
                    idempotencyKey,
                    existed.getCallbackStatus(),
                    elapsedMillis(startNanos));
            return duplicateResult(callbackLogId, existed);
        }
        int callbackRows = callbackMapper.insertPhysical(callbackTable,
                buildCallback(commandDTO, context, callbackLogId, callbackId, idempotencyKey, receivedTime, now));
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

    /**
     * 构造渠道回调业务幂等记录。
     * <p>
     * 前置条件：回调原始日志已准备写入，渠道处理器已尽力解析交易号、渠道订单号和渠道交易号。
     * 该方法生成 transaction_channel_callback 分表记录，保存 callbackId、callbackLogId、幂等键、签名校验结果、IP 白名单结果和初始处理状态；
     * 若无法解析平台交易号则记录 FAILED，保留原文日志供人工排查。
     * </p>
     * @param commandDTO OpenAPI 回调入口转发的内部命令
     * @param context 回调定位上下文，包含平台交易号、操作号、渠道订单号和分表时间
     * @param callbackLogId 已写入或即将写入的回调原始日志编号
     * @param callbackId 本次回调业务记录编号
     * @param idempotencyKey 渠道回调幂等键
     * @param receivedTime OpenAPI 入口收到回调的时间
     * @param now 支付核心记录回调业务状态的当前时间
     * @return 渠道回调业务记录
     */
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
 * 处理回调ifpossible流程，串联校验、状态判断和后续业务动作。
 * <p>
 * 前置条件：调用方已把 支付核心服务 的请求、消息或任务参数解析为当前方法可识别的模型。
 * 该方法按业务分支串联校验、状态判断、数据读写、远程调用或消息投递，关键阶段应保留 traceId 日志。
 * 异常边界：幂等冲突、状态不允许、外部系统失败或持久化失败按当前流程返回明确结果。
 * </p>
 * @param callbackTable callback Table 输入值，参与 回调table 的查询、校验、转换、写入或日志摘要
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param channelCallbackResult channel Callback Result 输入值，参与 渠道回调结果 的查询、校验、转换、写入或日志摘要
 * @param context context 输入值，参与 context 的查询、校验、转换、写入或日志摘要
 * @param callbackId callback ID 输入值，参与 回调ID 的查询、校验、转换、写入或日志摘要
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
 */
    private CallbackProcessOutcome processCallbackIfPossible(String callbackTable,
                                                             TransactionChannelCallbackCommandDTO commandDTO,
                                                             ChannelCallbackResult channelCallbackResult,
                                                             CallbackContext context,
                                                             String callbackId,
                                                             LocalDateTime now) {
        if (!context.transactionIdResolved() || context.operationDO() == null || context.orderDO() == null) {
            log.warn("event: PAYMENT_CHANNEL_CALLBACK_UNRESOLVED stage=CALLBACK_PROCESS traceId: {} channelCode: {} transactionId: {} channelOrderNo: {} channelTransactionId: {}",
                    TraceContext.getTraceId(),
                    normalizeChannelCode(commandDTO.getChannelCode()),
                    context.transactionId(),
                    context.channelOrderNo(),
                    context.channelTransactionId());
            return updateCallbackProcessResult(callbackTable, callbackId, CALLBACK_STATUS_FAILED, null, null,
                    null, null, "transaction_id can not be resolved from callback", now);
        }
        ParsedCallbackStatus parsedStatus = parseCallbackStatus(commandDTO, channelCallbackResult, context.operationDO().getTransactionType());
        if (parsedStatus.targetStatus() == null) {
            log.warn("event: PAYMENT_CHANNEL_CALLBACK_STATUS_UNMAPPED stage=CALLBACK_PROCESS traceId: {} channelCode: {} callbackId: {} transactionId: {} operationId: {} rawChannelStatus: {} channelTradeStatus: {}",
                    TraceContext.getTraceId(),
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
            log.info("event: PAYMENT_CHANNEL_CALLBACK_NON_TERMINAL stage=CALLBACK_PROCESS traceId: {} channelCode: {} callbackId: {} transactionId: {} operationId: {} currentStatus: {} parsedStatus: {} channelStatus: {}",
                    TraceContext.getTraceId(),
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
 * 创建回调处理事件，完成必要校验后写入或委托下游服务处理。
 * <p>
 * 前置条件：调用方已完成 支付核心服务 的身份、权限、必填字段和业务唯一性准备。
 * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
 * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
 * </p>
 * @param context context 输入值，参与 context 的查询、校验、转换、写入或日志摘要
 * @param parsedStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param callbackId callback ID 输入值，参与 回调ID 的查询、校验、转换、写入或日志摘要
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
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
 * 更新回调处理结果，保持业务状态、配置项或展示字段与请求意图一致。
 * <p>
 * 前置条件：调用方已确认 支付核心服务 中目标记录存在且当前状态允许变更。
 * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
 * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
 * </p>
 * @param callbackTable callback Table 输入值，参与 回调table 的查询、校验、转换、写入或日志摘要
 * @param callbackId callback ID 输入值，参与 回调ID 的查询、校验、转换、写入或日志摘要
 * @param callbackStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param parsedTransactionStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param previousTransactionStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param targetTransactionStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param processResult process Result 输入值，参与 process结果 的查询、校验、转换、写入或日志摘要
 * @param failReason fail Reason 输入值，参与 failreason 的查询、校验、转换、写入或日志摘要
 * @param processedTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @return 写入、更新或删除后的处理结果
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
        int affectedRows = callbackMapper.updateProcessResultPhysical(callbackTable,
                callbackId,
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
        return new CallbackProcessOutcome(callbackStatus, processResult, failReason);
    }

    /**
     * 整理重复请求结果，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param callbackLogId callback Log ID 输入值，参与 回调日志ID 的查询、校验、转换、写入或日志摘要
     * @param existed existed 输入值，参与 existed 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
 * 解析resolvecontext，将原始输入转换为当前调用链需要的规范化结果。
 * <p>
 * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
 * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
 * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
 * </p>
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param channelCallbackResult channel Callback Result 输入值，参与 渠道回调结果 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
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
 * 解析resolve回调动作，将原始输入转换为当前调用链需要的规范化结果。
 * <p>
 * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
 * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
 * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
 * </p>
 * @param transactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
 * @param channelOrderNo channel Order No 输入值，参与 渠道订单no 的查询、校验、转换、写入或日志摘要
 * @param channelTransactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
 * @return 构造、转换或解析后的业务值
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
 * 解析parsepayload，将原始输入转换为当前调用链需要的规范化结果。
 * <p>
 * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
 * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
 * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
 * </p>
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param channelCallbackResult channel Callback Result 输入值，参与 渠道回调结果 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
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
     * 解析resolve回调type，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolveCallbackType(TransactionChannelCallbackCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getCallbackType()) ? commandDTO.getCallbackType() : DEFAULT_CALLBACK_TYPE;
    }

    /**
     * 解析normalize渠道编码，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param channelCode channel Code 输入值，参与 渠道编码 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeChannelCode(String channelCode) {
        return channelCode == null ? null : channelCode.toUpperCase(Locale.ROOT);
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
     * 构造交易时间对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void fillTransactionTime(TransactionChannelCallbackLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime));
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
    private void fillTransactionTime(TransactionChannelCallbackDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtcTime(transactionDateTime));
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 构造utctime对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 构造、转换或解析后的业务值
     */
    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime) {
        return transactionDateTime.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * 整理物理表，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param logicalTable 逻辑表名，用于按交易时间解析真实物理分表
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String physicalTable(String logicalTable, LocalDateTime transactionDateTime) {
        return shardingDataTemplate.resolvePhysicalTable(
                ShardingSingleTableContext.of(logicalTable, transactionDateTime, DataSourceName.MASTER));
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

    /**
     * 整理耗时毫秒数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param startNanos start Nanos 输入值，参与 startnanos 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 截断日志摘要文本。
     *
     * @param value 原始摘要文本
     * @param maxLength 最大保留字符数
     * @return 长度受控的摘要文本
     */
    private String safeLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 整理首个非空文本，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param values values 输入值，参与 values 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 校验validate输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 支付核心服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
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
