package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.redis.lock.RedisLockService;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentPendingReasonEnum;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.PaymentTransactionService;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionServiceImpl
 * @date : 2026-05-31 21:03
 * @email : scott_x@163.com
 * @description : 收单支付交易服务骨架实现，位于 service-payment 服务实现层，负责交易受理、幂等兜底、生命周期标识生成和本地事务事件落库。
 * @status : create
 */
@Service
public class PaymentTransactionServiceImpl implements PaymentTransactionService {

    /**
     * 原始交易生命周期主标识前缀，后续正式表建议命名为 transaction_order_no。
     */
    private static final String TRANSACTION_ORDER_PREFIX = "TO";

    /**
     * 当前交易动作单号前缀，后续正式表建议命名为 transaction_no。
     */
    private static final String TRANSACTION_NO_PREFIX = "TX";

    /**
     * 支付创建消息 Tag。
     */
    private static final String PAYMENT_CREATED_TAG = "PAYMENT_CREATED";

    /**
     * 支付创建幂等范围。
     */
    private static final String PAYMENT_CREATE_SCOPE = "PAYMENT_CREATE";

    /**
     * 交易事件聚合类型。
     */
    private static final String PAYMENT_TRANSACTION_AGGREGATE = "PAYMENT_TRANSACTION";

    /**
     * 本地事件初始状态。
     */
    private static final String EVENT_STATUS_INIT = "INIT";

    /**
     * 默认交易业务时区。
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /**
     * 交易创建分布式锁前缀。
     */
    private static final String PAYMENT_CREATE_LOCK_PREFIX = "transaction:payment:create:";

    /**
     * 交易创建锁过期秒数。
     */
    private static final long PAYMENT_CREATE_LOCK_TTL_SECONDS = 30L;

    /**
     * 本地事件默认最大重试次数。
     */
    private static final int DEFAULT_EVENT_MAX_RETRY_COUNT = 10;

    /**
     * 本地事件初始版本。
     */
    private static final int INITIAL_VERSION = 0;

    /**
     * 未删除标识。
     */
    private static final int NOT_DELETED = 0;

    /**
     * ISO 币种字典服务，用于按币种默认辅币位转换交易金额。
     */
    private final IsoDictionaryService isoDictionaryService;

    /**
     * 路由前风控调用服务。
     */
    private final PaymentRiskInvokeService paymentRiskInvokeService;

    /**
     * 收单渠道路由服务。
     */
    private final PaymentChannelRouteService paymentChannelRouteService;

    /**
     * 收单渠道调用服务。
     */
    private final PaymentChannelInvokeService paymentChannelInvokeService;

    /**
     * 交易幂等服务，用于数据库唯一键兜底重复请求。
     */
    private final TransactionIdempotencyService transactionIdempotencyService;

    /**
     * 交易本地事件服务，用于事务内落库待投递 RocketMQ 事件。
     */
    private final TransactionEventOutboxService transactionEventOutboxService;

    /**
     * Redis 分布式锁服务列表，未装配 Redis 时允许为空，最终幂等仍由数据库唯一键保护。
     */
    private final List<RedisLockService> redisLockServices;

    /**
     * 创建收单支付交易服务。
     *
     * @param isoDictionaryService        ISO 币种字典服务
     * @param paymentRiskInvokeService 路由前风控调用服务
     * @param paymentChannelRouteService 收单渠道路由服务
     * @param paymentChannelInvokeService 收单渠道调用服务
     * @param transactionIdempotencyService 交易幂等服务
     * @param transactionEventOutboxService 交易本地事件服务
     * @param redisLockServices Redis 分布式锁服务列表
     */
    public PaymentTransactionServiceImpl(IsoDictionaryService isoDictionaryService,
                                         PaymentRiskInvokeService paymentRiskInvokeService,
                                         PaymentChannelRouteService paymentChannelRouteService,
                                         PaymentChannelInvokeService paymentChannelInvokeService,
                                         TransactionIdempotencyService transactionIdempotencyService,
                                         TransactionEventOutboxService transactionEventOutboxService,
                                         List<RedisLockService> redisLockServices) {
        this.isoDictionaryService = isoDictionaryService;
        this.paymentRiskInvokeService = paymentRiskInvokeService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.paymentChannelInvokeService = paymentChannelInvokeService;
        this.transactionIdempotencyService = transactionIdempotencyService;
        this.transactionEventOutboxService = transactionEventOutboxService;
        this.redisLockServices = redisLockServices == null ? List.of() : redisLockServices;
    }

    /**
     * 创建收单授权交易；当前骨架实现生成生命周期主标识和交易动作标识，返回 PROCESSING 状态并发布创建事件。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateResultDTO createAuthorization(PaymentCreateCommandDTO commandDTO) {
        validateCreateCommand(commandDTO);
        String transactionType = resolveTransactionType(commandDTO);
        String idempotencyKey = transactionIdempotencyService.buildPaymentCreateKey(
                commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo(), transactionType);
        String lockValue = UUID.randomUUID().toString();
        boolean locked = tryLock(idempotencyKey, lockValue);
        if (!locked) {
            return transactionIdempotencyService.find(PAYMENT_CREATE_SCOPE, idempotencyKey)
                    .map(this::toDuplicateResult)
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.NETWORK_BUSY));
        }
        try {
            return createAuthorizationWithIdempotency(commandDTO, transactionType, idempotencyKey);
        } finally {
            unlockAfterTransaction(idempotencyKey, lockValue, locked);
        }
    }

    /**
     * 在幂等保护下创建收单授权交易。
     *
     * @param commandDTO     创建交易命令
     * @param transactionType 交易类型
     * @param idempotencyKey 幂等键
     * @return 创建交易结果
     */
    private PaymentCreateResultDTO createAuthorizationWithIdempotency(PaymentCreateCommandDTO commandDTO,
                                                                      String transactionType,
                                                                      String idempotencyKey) {
        LocalDateTime now = LocalDateTime.now();
        TransactionIdempotencyDO beginRecord = transactionIdempotencyService.newProcessingRecord(
                PAYMENT_CREATE_SCOPE,
                idempotencyKey,
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                transactionType,
                commandDTO.getTransactionDateTime(),
                DEFAULT_TIME_ZONE,
                commandDTO.getRequestFingerprint(),
                now);
        if (!transactionIdempotencyService.tryBegin(beginRecord)) {
            return transactionIdempotencyService.find(PAYMENT_CREATE_SCOPE, idempotencyKey)
                    .map(this::toDuplicateResult)
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS));
        }
        String transactionOrderNo = PaymentOrderNoGenerator.nextOrderNo(TRANSACTION_ORDER_PREFIX);
        String transactionNo = PaymentOrderNoGenerator.nextOrderNo(TRANSACTION_NO_PREFIX);
        PaymentRiskDecisionDTO riskDecisionDTO = paymentRiskInvokeService.checkPreRoute(commandDTO);
        PaymentRiskDecisionEnum riskDecisionEnum = resolveRiskDecision(riskDecisionDTO);
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setPaymentOrderNo(transactionOrderNo);
        resultDTO.setTransactionOrderNo(transactionOrderNo);
        resultDTO.setTransactionNo(transactionNo);
        resultDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        resultDTO.setTransactionType(transactionType);
        resultDTO.setCurrency(commandDTO.getCurrency());
        resultDTO.setAmount(toMinorAmount(commandDTO.getAmount(), commandDTO.getCurrency()));
        if (!riskDecisionEnum.isAllowProceed()) {
            fillRiskStoppedResult(resultDTO, riskDecisionEnum);
            completeIdempotency(idempotencyKey, resultDTO);
            return resultDTO;
        }
        PaymentRouteResultDTO routeResultDTO = paymentChannelRouteService.route(commandDTO);
        ChannelPaymentResponse channelResponse = paymentChannelInvokeService.invoke(commandDTO, routeResultDTO, transactionOrderNo, transactionNo);
        fillChannelResult(resultDTO, channelResponse);
        savePaymentCreatedEvent(commandDTO, resultDTO);
        completeIdempotency(idempotencyKey, resultDTO);
        return resultDTO;
    }

    private void fillChannelResult(PaymentCreateResultDTO resultDTO, ChannelPaymentResponse channelResponse) {
        if (channelResponse == null) {
            resultDTO.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
            resultDTO.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
            return;
        }
        String channelTradeStatus = channelResponse.getChannelTradeStatus();
        if (ChannelTradeStatus.SUCCESS.getCode().equals(channelTradeStatus)) {
            resultDTO.setStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
            resultDTO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
            return;
        }
        if (ChannelTradeStatus.FAILED.getCode().equals(channelTradeStatus)) {
            resultDTO.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
            resultDTO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
            resultDTO.setFailReasonCode(PaymentFailureReasonEnum.CHANNEL_REQUEST_FAILED.getCode());
            return;
        }
        if (ChannelTradeStatus.NEED_REDIRECT.getCode().equals(channelTradeStatus)) {
            resultDTO.setStatus(PaymentTransactionStatusEnum.PENDING.getCode());
            resultDTO.setProcessStage(PaymentProcessStageEnum.WAITING_3DS.getCode());
            resultDTO.setPendingReasonCode(PaymentPendingReasonEnum.NEED_REDIRECT.getCode());
            return;
        }
        resultDTO.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        resultDTO.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
    }

    /**
     * 校验创建交易命令。
     *
     * @param commandDTO 创建交易命令
     */
    private void validateCreateCommand(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO == null
                || !StringUtils.hasText(commandDTO.getMerchantId())
                || !StringUtils.hasText(commandDTO.getMerchantOrderNo())
                || !StringUtils.hasText(commandDTO.getCurrency())
                || commandDTO.getAmount() == null
                || commandDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        if (commandDTO.getTransactionDateTime() == null) {
            commandDTO.setTransactionDateTime(LocalDateTime.now());
        }
        if (!StringUtils.hasText(commandDTO.getTransactionType())) {
            commandDTO.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        }
    }

    /**
     * 解析交易类型，当前授权入口默认使用 AUTHORIZATION。
     *
     * @param commandDTO 创建交易命令
     * @return 交易类型编码
     */
    private String resolveTransactionType(PaymentCreateCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getTransactionType())
                ? commandDTO.getTransactionType()
                : PaymentTransactionTypeEnum.AUTHORIZATION.getCode();
    }

    /**
     * 解析风控决策，空响应或未知响应按拒绝处理，避免风控异常导致交易被误放行。
     *
     * @param riskDecisionDTO 风控决策响应
     * @return 支付侧风控决策枚举
     */
    private PaymentRiskDecisionEnum resolveRiskDecision(PaymentRiskDecisionDTO riskDecisionDTO) {
        if (riskDecisionDTO == null) {
            return PaymentRiskDecisionEnum.UNKNOWN;
        }
        PaymentRiskDecisionEnum decisionEnum = PaymentRiskDecisionEnum.of(riskDecisionDTO.getDecision());
        if (!riskDecisionDTO.isPassed() && decisionEnum.isAllowProceed()) {
            return PaymentRiskDecisionEnum.UNKNOWN;
        }
        return decisionEnum;
    }

    /**
     * 按风控决策填充交易停止或挂起结果，保持风控状态和 transaction_status 字典状态分离。
     *
     * @param resultDTO        创建交易结果
     * @param riskDecisionEnum 风控决策
     */
    private void fillRiskStoppedResult(PaymentCreateResultDTO resultDTO, PaymentRiskDecisionEnum riskDecisionEnum) {
        if (PaymentRiskDecisionEnum.REQUIRE_3DS == riskDecisionEnum) {
            resultDTO.setStatus(PaymentTransactionStatusEnum.PENDING.getCode());
            resultDTO.setProcessStage(PaymentProcessStageEnum.WAITING_3DS.getCode());
            resultDTO.setPendingReasonCode(PaymentPendingReasonEnum.NEED_REDIRECT.getCode());
            return;
        }
        if (PaymentRiskDecisionEnum.REVIEW == riskDecisionEnum) {
            resultDTO.setStatus(PaymentTransactionStatusEnum.PENDING.getCode());
            resultDTO.setProcessStage(PaymentProcessStageEnum.WAITING_RISK_REVIEW.getCode());
            resultDTO.setPendingReasonCode(PaymentPendingReasonEnum.RISK_REVIEW.getCode());
            return;
        }
        resultDTO.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        resultDTO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        resultDTO.setFailReasonCode(PaymentFailureReasonEnum.RISK_REJECTED.getCode());
    }

    /**
     * 按 ISO 4217 币种默认辅币位把主币种单位金额转换为最小币种单位。
     *
     * @param amount   主币种单位金额
     * @param currency ISO 4217 币种代码或名称
     * @return 最小币种单位金额
     */
    private Long toMinorAmount(BigDecimal amount, String currency) {
        try {
            return isoDictionaryService.toMinorUnit(amount, currency);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "amount fraction digits exceed currency minor unit", exception);
        }
    }

    /**
     * 保存支付创建本地事务事件，实际 RocketMQ 投递由后续 outbox relay 在事务提交后处理。
     *
     * @param commandDTO 创建交易命令
     * @param resultDTO  创建交易结果
     */
    private void savePaymentCreatedEvent(PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        BaseMqMessage message = new BaseMqMessage();
        message.setMessageId(resultDTO.getTransactionNo());
        message.setCreatedAt(LocalDateTime.now());
        TransactionEventOutboxDO eventDO = new TransactionEventOutboxDO();
        eventDO.setEventNo(resultDTO.getTransactionNo());
        eventDO.setAggregateType(PAYMENT_TRANSACTION_AGGREGATE);
        eventDO.setAggregateNo(resultDTO.getTransactionOrderNo());
        eventDO.setTransactionOrderNo(resultDTO.getTransactionOrderNo());
        eventDO.setTransactionNo(resultDTO.getTransactionNo());
        eventDO.setMerchantId(commandDTO.getMerchantId());
        eventDO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        eventDO.setTransactionType(resultDTO.getTransactionType());
        eventDO.setEventType(PAYMENT_CREATED_TAG);
        eventDO.setEventStatus(EVENT_STATUS_INIT);
        eventDO.setTopic(MqTopic.PAYMENT_EVENT);
        eventDO.setTag(PAYMENT_CREATED_TAG);
        eventDO.setMessageKey(resultDTO.getTransactionNo());
        eventDO.setPayloadJson(JsonUtils.toJsonString(message));
        eventDO.setEventTime(message.getCreatedAt());
        eventDO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        eventDO.setTimeZone(DEFAULT_TIME_ZONE);
        eventDO.setRetryCount(0);
        eventDO.setMaxRetryCount(DEFAULT_EVENT_MAX_RETRY_COUNT);
        eventDO.setNextRetryTime(message.getCreatedAt());
        eventDO.setVersion(INITIAL_VERSION);
        eventDO.setDeleted(NOT_DELETED);
        eventDO.setCreateTime(message.getCreatedAt());
        eventDO.setUpdateTime(message.getCreatedAt());
        transactionEventOutboxService.save(eventDO);
    }

    /**
     * 完成幂等记录并保存可重复返回的结果快照。
     *
     * @param idempotencyKey 幂等键
     * @param resultDTO      创建交易结果
     */
    private void completeIdempotency(String idempotencyKey, PaymentCreateResultDTO resultDTO) {
        transactionIdempotencyService.complete(
                PAYMENT_CREATE_SCOPE,
                idempotencyKey,
                resultDTO.getTransactionOrderNo(),
                resultDTO.getTransactionNo(),
                resultDTO.getStatus(),
                resultDTO.getAmount(),
                resultDTO.getCurrency(),
                JsonUtils.toJsonString(resultDTO));
    }

    /**
     * 将重复请求命中的幂等记录转换为创建交易响应。
     *
     * @param record 幂等记录
     * @return 创建交易响应
     */
    private PaymentCreateResultDTO toDuplicateResult(TransactionIdempotencyDO record) {
        if (StringUtils.hasText(record.getResultSnapshot())) {
            PaymentCreateResultDTO resultDTO = JsonUtils.parseObject(record.getResultSnapshot(), PaymentCreateResultDTO.class);
            if (resultDTO != null) {
                return resultDTO;
            }
        }
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setPaymentOrderNo(record.getTransactionOrderNo());
        resultDTO.setTransactionOrderNo(record.getTransactionOrderNo());
        resultDTO.setTransactionNo(record.getTransactionNo());
        resultDTO.setMerchantOrderNo(record.getMerchantOrderNo());
        resultDTO.setTransactionType(record.getTransactionType());
        resultDTO.setStatus(record.getTransactionStatus());
        resultDTO.setAmount(record.getTransactionAmountMinor());
        resultDTO.setCurrency(record.getTransactionCurrency());
        return resultDTO;
    }

    /**
     * 尝试获取交易创建锁；未装配 Redis 锁时跳过锁保护，由数据库唯一键兜底。
     *
     * @param idempotencyKey 幂等键
     * @param lockValue      锁值
     * @return true 表示可继续处理
     */
    private boolean tryLock(String idempotencyKey, String lockValue) {
        if (redisLockServices.isEmpty()) {
            return true;
        }
        return redisLockServices.get(0).tryLock(PAYMENT_CREATE_LOCK_PREFIX + idempotencyKey, lockValue, PAYMENT_CREATE_LOCK_TTL_SECONDS);
    }

    /**
     * 在本地事务完成后释放交易创建锁，避免事务提交前锁先释放导致并发请求提前进入。
     *
     * @param idempotencyKey 幂等键
     * @param lockValue      锁值
     * @param locked         是否已获取锁
     */
    private void unlockAfterTransaction(String idempotencyKey, String lockValue, boolean locked) {
        if (!locked || redisLockServices.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    unlock(idempotencyKey, lockValue);
                }
            });
            return;
        }
        unlock(idempotencyKey, lockValue);
    }

    /**
     * 释放交易创建锁。
     *
     * @param idempotencyKey 幂等键
     * @param lockValue      锁值
     */
    private void unlock(String idempotencyKey, String lockValue) {
        redisLockServices.get(0).unlock(PAYMENT_CREATE_LOCK_PREFIX + idempotencyKey, lockValue);
    }
}
