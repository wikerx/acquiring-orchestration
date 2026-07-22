package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.redis.lock.RedisLockService;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse.PaymentMethodSummary;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.mq.TransactionMqConstants;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentPendingReasonEnum;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.domain.state.TransactionStateMachineService;
import com.scott.payment.payment.service.ChannelTransactionStatusResolver;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.PaymentTransactionService;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.impl.DefaultPaymentChannelInvokeService.PaymentChannelInvokeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

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
     * 平台内部生命周期关联 ID 前缀，对应 transaction_order.operation_id。
     */
    private static final String OPERATION_ID_PREFIX = "OP";

    /**
     * 交易动作幂等范围。
     */
    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";

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
     * 默认卡交易支付方式。
     */
    private static final String DEFAULT_PAYMENT_METHOD = "BANK_CARD";

    /**
     * 交易动作分布式锁前缀。
     */
    private static final String TRANSACTION_OPERATION_LOCK_PREFIX = "transaction:operation:";

    /**
     * 交易动作锁过期秒数。
     */
    private static final long TRANSACTION_OPERATION_LOCK_TTL_SECONDS = 30L;

    /**
     * 商户订单首次流程锁前缀。
     */
    private static final String MERCHANT_ORDER_FLOW_LOCK_PREFIX = "transaction:merchant-order-flow:";

    /**
     * 本地事件默认最大重试次数。
     */
    private static final int DEFAULT_EVENT_MAX_RETRY_COUNT = 200;

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
     * 交易汇率服务，用于渠道不支持标签币种时执行 EDC 交易汇率查询。
     */
    private final PaymentExchangeRateService paymentExchangeRateService;

    /**
     * 交易幂等服务，用于数据库唯一键兜底重复请求。
     */
    private final TransactionIdempotencyService transactionIdempotencyService;

    /**
     * 交易本地事件服务，用于事务内落库待投递 RocketMQ 事件。
     */
    private final TransactionEventOutboxService transactionEventOutboxService;

    /**
     * 交易事实记录服务，用于在本地事务内写入主单、动作单和状态历史。
     */
    private final TransactionRecordService transactionRecordService;

    /**
     * 交易状态机服务，用于后续资金动作发起前校验原交易状态和可用金额。
     */
    private final TransactionStateMachineService transactionStateMachineService;

    /**
     * 渠道状态解析服务，用于同步响应、回调和查询勾兑共享平台状态映射。
     */
    private final ChannelTransactionStatusResolver channelStatusResolver;

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
     * @param paymentExchangeRateService 交易汇率服务
     * @param transactionIdempotencyService 交易幂等服务
     * @param transactionEventOutboxService 交易本地事件服务
     * @param transactionRecordService 交易事实记录服务
     * @param transactionStateMachineService 交易状态机服务
     * @param redisLockServices Redis 分布式锁服务列表
     */
    public PaymentTransactionServiceImpl(IsoDictionaryService isoDictionaryService,
                                         PaymentRiskInvokeService paymentRiskInvokeService,
                                         PaymentChannelRouteService paymentChannelRouteService,
                                         PaymentChannelInvokeService paymentChannelInvokeService,
                                         PaymentExchangeRateService paymentExchangeRateService,
                                         TransactionIdempotencyService transactionIdempotencyService,
                                         TransactionEventOutboxService transactionEventOutboxService,
                                         TransactionRecordService transactionRecordService,
                                         TransactionStateMachineService transactionStateMachineService,
                                         List<RedisLockService> redisLockServices) {
        this(isoDictionaryService,
                paymentRiskInvokeService,
                paymentChannelRouteService,
                paymentChannelInvokeService,
                paymentExchangeRateService,
                transactionIdempotencyService,
                transactionEventOutboxService,
                transactionRecordService,
                transactionStateMachineService,
                new DefaultChannelTransactionStatusResolver(),
                redisLockServices);
    }

    /**
     * 创建收单支付交易服务。
     *
     * @param isoDictionaryService        ISO 币种字典服务
     * @param paymentRiskInvokeService 路由前风控调用服务
     * @param paymentChannelRouteService 收单渠道路由服务
     * @param paymentChannelInvokeService 收单渠道调用服务
     * @param paymentExchangeRateService 交易汇率服务
     * @param transactionIdempotencyService 交易幂等服务
     * @param transactionEventOutboxService 交易本地事件服务
     * @param transactionRecordService 交易事实记录服务
     * @param transactionStateMachineService 交易状态机服务
     * @param channelStatusResolver 渠道状态解析服务
     * @param redisLockServices Redis 分布式锁服务列表
     */
    @Autowired
    public PaymentTransactionServiceImpl(IsoDictionaryService isoDictionaryService,
                                         PaymentRiskInvokeService paymentRiskInvokeService,
                                         PaymentChannelRouteService paymentChannelRouteService,
                                         PaymentChannelInvokeService paymentChannelInvokeService,
                                         PaymentExchangeRateService paymentExchangeRateService,
                                         TransactionIdempotencyService transactionIdempotencyService,
                                         TransactionEventOutboxService transactionEventOutboxService,
                                         TransactionRecordService transactionRecordService,
                                         TransactionStateMachineService transactionStateMachineService,
                                         ChannelTransactionStatusResolver channelStatusResolver,
                                         List<RedisLockService> redisLockServices) {
        this.isoDictionaryService = isoDictionaryService;
        this.paymentRiskInvokeService = paymentRiskInvokeService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.paymentChannelInvokeService = paymentChannelInvokeService;
        this.paymentExchangeRateService = paymentExchangeRateService;
        this.transactionIdempotencyService = transactionIdempotencyService;
        this.transactionEventOutboxService = transactionEventOutboxService;
        this.transactionRecordService = transactionRecordService;
        this.transactionStateMachineService = transactionStateMachineService;
        this.channelStatusResolver = channelStatusResolver == null
                ? new DefaultChannelTransactionStatusResolver()
                : channelStatusResolver;
        this.redisLockServices = redisLockServices == null ? List.of() : redisLockServices;
    }

    /**
     * 创建一步支付交易；当前骨架实现生成生命周期主标识和交易动作标识，返回渠道同步状态并发布交易事件。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateResultDTO createPayment(PaymentCreateCommandDTO commandDTO) {
        return createTransaction(commandDTO, PaymentTransactionTypeEnum.PAYMENT);
    }

    /**
     * 创建收单授权交易；当前骨架实现生成生命周期主标识和交易动作标识，返回渠道同步状态并发布交易事件。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateResultDTO createAuthorization(PaymentCreateCommandDTO commandDTO) {
        return createTransaction(commandDTO, PaymentTransactionTypeEnum.AUTHORIZATION);
    }

    /**
     * 创建预授权交易；当前与授权共享首次交易受理骨架，渠道层按 PRE_AUTHORIZATION 分发。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateResultDTO createPreAuthorization(PaymentCreateCommandDTO commandDTO) {
        return createTransaction(commandDTO, PaymentTransactionTypeEnum.PRE_AUTHORIZATION);
    }

    /**
     * 创建增量授权交易。
     * <p>
     * 增量授权先按 transaction_id 解析原交易时间并定位原交易，再校验状态机和金额，最后通过渠道统一执行器发起渠道请求。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateResultDTO createIncrementalAuthorization(PaymentCreateCommandDTO commandDTO) {
        return createFollowUpTransaction(commandDTO, PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION);
    }

    /**
     * 发起请款交易。
     * <p>
     * 请款先按 transaction_id 解析原交易时间并定位原交易，再校验可请款金额和幂等，最后通过渠道统一执行器发起渠道请求。
     *
     * @param commandDTO 请款命令
     * @return 请款结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateResultDTO capture(PaymentCreateCommandDTO commandDTO) {
        return createFollowUpTransaction(commandDTO, PaymentTransactionTypeEnum.CAPTURE);
    }

    /**
     * 发起退款交易。
     * <p>
     * 退款先按 transaction_id 解析原交易时间并定位原交易，再校验可退金额和幂等，最后通过渠道统一执行器发起渠道请求。
     *
     * @param commandDTO 退款命令
     * @return 退款结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateResultDTO refund(PaymentCreateCommandDTO commandDTO) {
        return createFollowUpTransaction(commandDTO, PaymentTransactionTypeEnum.REFUND);
    }

    /**
     * 发起撤销交易。
     * <p>
     * 撤销先按 transaction_id 解析原交易时间并定位原交易，再校验未请款未退款状态，最后通过渠道统一执行器发起渠道请求。
     *
     * @param commandDTO 撤销命令
     * @return 撤销结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateResultDTO voidPayment(PaymentCreateCommandDTO commandDTO) {
        return createFollowUpTransaction(commandDTO, PaymentTransactionTypeEnum.VOID);
    }

    /**
     * 查询交易状态。
     * <p>
     * 查询先按 transaction_id 解析原交易时间，再路由分表读取主单；详情聚合操作单、状态历史和渠道日志后续在查询服务中扩展。
     *
     * @param commandDTO 查询命令
     * @return 查询结果
     */
    @Override
    public PaymentQueryResultDTO query(PaymentCreateCommandDTO commandDTO) {
        validateQueryCommand(commandDTO);
        if (transactionRecordService == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        String requestedTransactionId = commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getTransactionId();
        List<TransactionOperationDO> operations = transactionRecordService.findOperationsByMerchantOrder(
                commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo(), requestedTransactionId);
        if (operations.isEmpty()) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderDO sourceOrderDO = resolveOrderForQuery(operations.get(0));
        if (!Objects.equals(commandDTO.getMerchantId(), sourceOrderDO.getMerchantId())
                || !Objects.equals(commandDTO.getMerchantOrderNo(), sourceOrderDO.getMerchantOrderNo())) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        PaymentQueryResultDTO resultDTO = new PaymentQueryResultDTO();
        resultDTO.setMerchantId(commandDTO.getMerchantId());
        resultDTO.setMerchantOrderNo(sourceOrderDO.getMerchantOrderNo());
        resultDTO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        fillQueryOrderSummary(resultDTO, sourceOrderDO);
        resultDTO.setTransactionInfo(operations.stream()
                .map(operationDO -> toQueryTransactionInfo(operationDO, sourceOrderDO))
                .toList());
        return resultDTO;
    }

    /**
     * 创建首次类交易动作。
     *
     * @param commandDTO 创建交易命令
     * @param transactionTypeEnum 交易类型
     * @return 创建交易结果
     */
    private PaymentCreateResultDTO createTransaction(PaymentCreateCommandDTO commandDTO,
                                                     PaymentTransactionTypeEnum transactionTypeEnum) {
        if (commandDTO != null) {
            commandDTO.setTransactionType(transactionTypeEnum.getCode());
        }
        validateCreateCommand(commandDTO);
        String transactionType = resolveTransactionType(commandDTO);
        String idempotencyKey = transactionIdempotencyService.buildTransactionOperationKey(
                commandDTO.getMerchantId(), commandDTO.getMerchantOrderId(), transactionType);
        String flowLockKey = merchantOrderFlowLockKey(commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo());
        String lockValue = UUID.randomUUID().toString();
        boolean operationLocked = tryLock(transactionOperationLockKey(idempotencyKey), lockValue);
        boolean flowLocked = false;
        if (!operationLocked) {
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(this::toDuplicateResult)
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.NETWORK_BUSY));
        }
        try {
            flowLocked = tryLock(flowLockKey, lockValue);
            if (!flowLocked) {
                throw new ServiceException(ApiResultEnum.NETWORK_BUSY);
            }
            validateMerchantOrderFlow(commandDTO, transactionType);
            return createTransactionWithIdempotency(commandDTO, transactionType, idempotencyKey);
        } finally {
            unlockAfterTransaction(flowLockKey, lockValue, flowLocked);
            unlockAfterTransaction(transactionOperationLockKey(idempotencyKey), lockValue, operationLocked);
        }
    }

    /**
     * 创建后续类交易动作。
     *
     * @param commandDTO 后续动作命令
     * @param transactionTypeEnum 后续交易类型
     * @return 后续动作创建结果
     */
    private PaymentCreateResultDTO createFollowUpTransaction(PaymentCreateCommandDTO commandDTO,
                                                             PaymentTransactionTypeEnum transactionTypeEnum) {
        if (commandDTO != null) {
            commandDTO.setTransactionType(transactionTypeEnum.getCode());
        }
        validateFollowUpCommand(commandDTO, transactionTypeEnum);
        String idempotencyKey = transactionIdempotencyService.buildTransactionOperationKey(
                commandDTO.getMerchantId(), commandDTO.getMerchantOrderId(), transactionTypeEnum.getCode());
        String lockValue = UUID.randomUUID().toString();
        boolean locked = tryLock(transactionOperationLockKey(idempotencyKey), lockValue);
        if (!locked) {
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(this::toDuplicateResult)
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.NETWORK_BUSY));
        }
        try {
            return createFollowUpTransactionWithIdempotency(commandDTO, transactionTypeEnum, idempotencyKey);
        } finally {
            unlockAfterTransaction(transactionOperationLockKey(idempotencyKey), lockValue, locked);
        }
    }

    /**
     * 在幂等保护下创建请款、退款、撤销、增量授权等后续交易。
     * <p>
     * 后续交易必须先定位原生命周期主单并通过状态机校验，再沿用原渠道订单号发起渠道动作；
     * 渠道异常不会直接落失败，而是进入 PROCESSING 等待回调或查询勾兑。
     *
     * @param commandDTO 后续交易命令
     * @param transactionTypeEnum 后续交易类型
     * @param idempotencyKey 幂等键
     * @return 创建交易结果
     */
    private PaymentCreateResultDTO createFollowUpTransactionWithIdempotency(PaymentCreateCommandDTO commandDTO,
                                                                            PaymentTransactionTypeEnum transactionTypeEnum,
                                                                            String idempotencyKey) {
        if (transactionRecordService == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderDO sourceOrderDO = resolveSourceOrder(commandDTO);
        LocalDateTime now = LocalDateTime.now();
        TransactionIdempotencyDO beginRecord = transactionIdempotencyService.newProcessingRecord(
                TRANSACTION_OPERATION_SCOPE,
                idempotencyKey,
                commandDTO.getMerchantId(),
                sourceOrderDO.getMerchantOrderNo(),
                commandDTO.getMerchantOrderId(),
                transactionTypeEnum.getCode(),
                commandDTO.getTransactionDateTime(),
                DEFAULT_TIME_ZONE,
                commandDTO.getRequestFingerprint(),
                now);
        if (!transactionIdempotencyService.tryBegin(beginRecord)) {
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(this::toDuplicateResult)
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS));
        }
        if (transactionStateMachineService == null) {
            throw new ServiceException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED.getCode(), "transaction state machine is not configured");
        }
        transactionStateMachineService.validateFollowUpAction(sourceOrderDO, transactionTypeEnum, commandDTO.getAmount(), commandDTO.getCurrency());
        TransactionOperationDO sourceOperationDO = transactionRecordService.findSourceOperationByTransactionId(
                commandDTO.getTransactionInfo().getSourceTransactionId());
        normalizeFollowUpCommand(commandDTO, sourceOrderDO, sourceOperationDO);
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        PaymentRouteResultDTO routeResultDTO = paymentChannelRouteService.route(commandDTO);
        PaymentCreateResultDTO resultDTO = buildFollowUpResult(commandDTO, sourceOrderDO, transactionId, transactionTypeEnum);
        PaymentChannelInvokeResultDTO invokeResultDTO = invokeChannelSafely(
                commandDTO, routeResultDTO, sourceOrderDO.getOperationId(), transactionId, resolveChannelOrderNo(commandDTO, sourceOrderDO));
        ChannelPaymentResponse channelResponse = invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse();
        fillChannelResult(resultDTO, invokeResultDTO, channelResponse);
        int currencyExponent = resolveCurrencyExponent(sourceOrderDO.getTransactionCurrency());
        enrichFollowUpResult(commandDTO, sourceOrderDO, routeResultDTO, channelResponse, resultDTO);
        recordFollowUpTransaction(commandDTO, sourceOrderDO, routeResultDTO, invokeResultDTO, resultDTO, currencyExponent);
        saveTransactionCreatedEvent(commandDTO, resultDTO);
        completeIdempotency(idempotencyKey, commandDTO, resultDTO);
        return resultDTO;
    }

    /**
     * 在幂等保护下创建首次类交易。
     *
     * @param commandDTO     创建交易命令
     * @param transactionType 交易类型
     * @param idempotencyKey 幂等键
     * @return 创建交易结果
     */
    private PaymentCreateResultDTO createTransactionWithIdempotency(PaymentCreateCommandDTO commandDTO,
                                                                    String transactionType,
                                                                    String idempotencyKey) {
        LocalDateTime now = LocalDateTime.now();
        TransactionIdempotencyDO beginRecord = transactionIdempotencyService.newProcessingRecord(
                TRANSACTION_OPERATION_SCOPE,
                idempotencyKey,
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getMerchantOrderId(),
                transactionType,
                commandDTO.getTransactionDateTime(),
                DEFAULT_TIME_ZONE,
                commandDTO.getRequestFingerprint(),
                now);
        if (!transactionIdempotencyService.tryBegin(beginRecord)) {
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(this::toDuplicateResult)
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS));
        }
        String operationId = PaymentOrderNoGenerator.nextOrderNo(OPERATION_ID_PREFIX, commandDTO.getTransactionDateTime());
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        initializeLabelAmount(commandDTO);
        PaymentRiskDecisionDTO riskDecisionDTO = paymentRiskInvokeService.checkPreRoute(commandDTO);
        PaymentRiskDecisionEnum riskDecisionEnum = resolveRiskDecision(riskDecisionDTO);
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setOperationId(operationId);
        resultDTO.setTransactionId(transactionId);
        resultDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        resultDTO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        resultDTO.setMerchantId(commandDTO.getMerchantId());
        resultDTO.setSubMerchantInfo(toResultSubMerchantInfo(commandDTO.getSubMerchantInfo()));
        resultDTO.setTransactionType(transactionType);
        if (!riskDecisionEnum.isAllowProceed()) {
            applyNoConversion(commandDTO);
            resultDTO.setCurrency(commandDTO.getTransactionCurrency());
            resultDTO.setAmount(toMinorAmount(commandDTO.getTransactionAmount(), commandDTO.getTransactionCurrency()));
            int currencyExponent = resolveCurrencyExponent(commandDTO.getTransactionCurrency());
            fillRiskStoppedResult(resultDTO, riskDecisionEnum);
            enrichResult(commandDTO, null, null, resultDTO);
            recordInitialTransaction(commandDTO, null, null, resultDTO, riskDecisionEnum, currencyExponent);
            if (isTerminal(resultDTO)) {
                saveTransactionCreatedEvent(commandDTO, resultDTO);
            }
            completeIdempotency(idempotencyKey, commandDTO, resultDTO);
            return resultDTO;
        }
        PaymentRouteResultDTO routeResultDTO = paymentChannelRouteService.route(commandDTO);
        if (!applyCurrencyConversion(commandDTO, routeResultDTO, resultDTO)) {
            int currencyExponent = resolveCurrencyExponent(commandDTO.getTransactionCurrency());
            enrichResult(commandDTO, routeResultDTO, null, resultDTO);
            recordInitialTransaction(commandDTO, routeResultDTO, null, resultDTO, riskDecisionEnum, currencyExponent);
            saveTransactionCreatedEvent(commandDTO, resultDTO);
            completeIdempotency(idempotencyKey, commandDTO, resultDTO);
            return resultDTO;
        }
        resultDTO.setCurrency(commandDTO.getTransactionCurrency());
        resultDTO.setAmount(toMinorAmount(commandDTO.getTransactionAmount(), commandDTO.getTransactionCurrency()));
        int currencyExponent = resolveCurrencyExponent(commandDTO.getTransactionCurrency());
        PaymentChannelInvokeResultDTO invokeResultDTO = invokeChannelSafely(
                commandDTO, routeResultDTO, operationId, transactionId, transactionId);
        ChannelPaymentResponse channelResponse = invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse();
        fillChannelResult(resultDTO, invokeResultDTO, channelResponse);
        enrichResult(commandDTO, routeResultDTO, channelResponse, resultDTO);
        recordInitialTransaction(commandDTO, routeResultDTO, invokeResultDTO, resultDTO, riskDecisionEnum, currencyExponent);
        saveTransactionCreatedEvent(commandDTO, resultDTO);
        completeIdempotency(idempotencyKey, commandDTO, resultDTO);
        return resultDTO;
    }

    /**
     * 填充渠道同步响应对应的平台状态。
     * <p>
     * 该方法只解释同步响应，不直接推进数据库终态；WPGXML/WPGJSON 的 AUTHORISED/CAPTURED 语义由
     * ChannelTransactionStatusResolver 统一处理，渠道请求异常统一保持 PROCESSING。
     *
     * @param resultDTO 待填充交易结果
     * @param invokeResultDTO 渠道调用上下文
     * @param channelResponse 渠道同步响应
     */
    private void fillChannelResult(PaymentCreateResultDTO resultDTO,
                                   PaymentChannelInvokeResultDTO invokeResultDTO,
                                   ChannelPaymentResponse channelResponse) {
        if (isChannelInvokeFailed(invokeResultDTO)) {
            resultDTO.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
            resultDTO.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
            return;
        }
        ChannelTransactionStatusResolution resolution = channelStatusResolver.resolveSync(
                channelResponse == null ? null : channelResponse.getChannelCode(),
                resultDTO.getTransactionType(),
                channelResponse);
        resultDTO.setStatus(resolution.getTargetStatus());
        resultDTO.setProcessStage(resolution.getProcessStage());
        resultDTO.setPendingReasonCode(resolution.getPendingReasonCode());
        resultDTO.setFailReasonCode(resolution.getFailReasonCode());
        resultDTO.setFailReasonMessage(resolution.getFailReasonMessage());
    }

    /**
     * 判断渠道调用是否在请求阶段失败。
     * <p>
     * 无渠道业务响应但存在网络、超时、响应解析或系统异常时，平台无法确认渠道是否已受理交易。
     * 资金类动作必须落为 PROCESSING 并等待自动查询勾兑，避免把渠道可能已成功的交易误判失败。
     *
     * @param invokeResultDTO 渠道调用上下文
     * @return true 表示渠道请求阶段已失败
     */
    private boolean isChannelInvokeFailed(PaymentChannelInvokeResultDTO invokeResultDTO) {
        return invokeResultDTO != null
                && (StringUtils.hasText(invokeResultDTO.getExceptionType())
                || "FAILED".equals(invokeResultDTO.getRequestStatus())
                || "TIMEOUT".equals(invokeResultDTO.getRequestStatus()));
    }

    /**
     * 安全调用渠道并保留异常上下文。
     * <p>
     * 渠道超时、网络失败、解析异常或 WorldPay 未接通异常都可能发生在请求阶段，不能在这里直接抛出导致交易事实缺失；
     * 上层会根据返回的异常上下文将动作落为 PROCESSING，交由回调或查询勾兑确认最终状态。
     *
     * @param commandDTO 交易命令
     * @param routeResultDTO 渠道路由结果
     * @param operationId 生命周期动作 ID
     * @param transactionId 当前交易 ID
     * @param channelOrderNo 渠道订单号
     * @return 渠道调用上下文
     */
    private PaymentChannelInvokeResultDTO invokeChannelSafely(PaymentCreateCommandDTO commandDTO,
                                                              PaymentRouteResultDTO routeResultDTO,
                                                              String operationId,
                                                              String transactionId,
                                                              String channelOrderNo) {
        try {
            return paymentChannelInvokeService.invoke(commandDTO, routeResultDTO, operationId, transactionId, channelOrderNo);
        } catch (PaymentChannelInvokeException exception) {
            return exception.getInvokeResult();
        }
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
                || !StringUtils.hasText(commandDTO.getMerchantOrderId())
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
     * 校验后续交易命令。
     *
     * @param commandDTO 后续动作命令
     * @param transactionTypeEnum 后续交易类型
     */
    private void validateFollowUpCommand(PaymentCreateCommandDTO commandDTO,
                                         PaymentTransactionTypeEnum transactionTypeEnum) {
        if (commandDTO == null
                || !StringUtils.hasText(commandDTO.getMerchantId())
                || requiresMerchantOrderNo(transactionTypeEnum) && !StringUtils.hasText(commandDTO.getMerchantOrderNo())
                || !StringUtils.hasText(commandDTO.getMerchantOrderId())
                || commandDTO.getTransactionInfo() == null
                || !StringUtils.hasText(commandDTO.getTransactionInfo().getSourceTransactionId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        if (commandDTO.getTransactionDateTime() == null) {
            commandDTO.setTransactionDateTime(LocalDateTime.now());
        }
        if (requiresAmount(transactionTypeEnum)
                && (commandDTO.getAmount() == null || commandDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || requiresRequestCurrency(transactionTypeEnum) && !StringUtils.hasText(commandDTO.getCurrency()))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private boolean requiresRequestCurrency(PaymentTransactionTypeEnum transactionTypeEnum) {
        return PaymentTransactionTypeEnum.REFUND != transactionTypeEnum;
    }

    private boolean requiresMerchantOrderNo(PaymentTransactionTypeEnum transactionTypeEnum) {
        return PaymentTransactionTypeEnum.REFUND != transactionTypeEnum;
    }

    /**
     * 校验同一商户订单号下支付流和授权流互斥。
     * <p>
     * PAYMENT 与 AUTHORIZATION/PRE_AUTHORIZATION 都是首次起点动作；同一商户订单号只能存在一条非失败起点流程。
     * 后续请款、退款、撤销必须通过 sourceTransactionId 进入同一生命周期；FAILED 起点允许商户修正参数后重试。
     *
     * @param commandDTO      首次交易命令
     * @param transactionType 当前首次交易类型
     */
    private void validateMerchantOrderFlow(PaymentCreateCommandDTO commandDTO, String transactionType) {
        if (transactionRecordService == null || !isInitialFlowType(transactionType)) {
            return;
        }
        List<TransactionOperationDO> operations = transactionRecordService.findInitialOperationsByMerchantOrder(
                commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo());
        for (TransactionOperationDO operationDO : operations) {
            if (operationDO == null || isFailedStatus(operationDO.getTransactionStatus())) {
                continue;
            }
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                    "merchant order number already has an active payment flow");
        }
    }

    private boolean isInitialFlowType(String transactionType) {
        return PaymentTransactionTypeEnum.PAYMENT.getCode().equals(transactionType)
                || PaymentTransactionTypeEnum.AUTHORIZATION.getCode().equals(transactionType)
                || PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode().equals(transactionType);
    }

    private boolean isFailedStatus(String transactionStatus) {
        return PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus);
    }

    private String merchantOrderFlowLockKey(String merchantId, String merchantOrderNo) {
        return MERCHANT_ORDER_FLOW_LOCK_PREFIX
                + (merchantId == null ? "" : merchantId.trim().toUpperCase(Locale.ROOT))
                + ':'
                + (merchantOrderNo == null ? "" : merchantOrderNo.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 构造单笔交易动作幂等锁 Key。
     *
     * @param idempotencyKey 交易动作幂等键
     * @return Redis 锁 Key
     */
    private String transactionOperationLockKey(String idempotencyKey) {
        return TRANSACTION_OPERATION_LOCK_PREFIX + idempotencyKey;
    }

    private void validateSourceTransaction(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO == null
                || commandDTO.getTransactionInfo() == null
                || !StringUtils.hasText(commandDTO.getTransactionInfo().getSourceTransactionId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /**
     * 校验商户交易查询命令。
     * <p>
     * 查询只要求 merchantId、orderInfo.orderNo、orderInfo.orderId 和 transactionInfo 对象；
     * transactionInfo.transactionId 是可选精确过滤条件，不再要求商户传 sourceTransactionId 或原交易时间。
     *
     * @param commandDTO 查询命令
     */
    private void validateQueryCommand(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO == null
                || !StringUtils.hasText(commandDTO.getMerchantId())
                || !StringUtils.hasText(commandDTO.getMerchantOrderNo())
                || !StringUtils.hasText(commandDTO.getMerchantOrderId())
                || commandDTO.getTransactionInfo() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /**
     * 定位原交易主单。
     * <p>
     * 商户 OpenAPI 不要求上送原交易时间；支付核心按 sourceTransactionId 中的业务时间片段定位动作分表，
     * 再通过动作单的 operation_id 精确读取同一生命周期主单。
     *
     * @param commandDTO 后续动作或查询命令
     * @return 原交易主单
     */
    private TransactionOrderDO resolveSourceOrder(PaymentCreateCommandDTO commandDTO) {
        if (transactionRecordService == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfoDTO = commandDTO.getTransactionInfo();
        String sourceTransactionId = transactionInfoDTO.getSourceTransactionId();
        TransactionOrderDO sourceOrderDO = transactionRecordService.findSourceOrderByTransactionId(sourceTransactionId);
        if (sourceOrderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return sourceOrderDO;
    }

    /**
     * 根据查询命中的动作单读取生命周期主单。
     *
     * @param operationDO 查询命中的动作单
     * @return 生命周期主单
     */
    private TransactionOrderDO resolveOrderForQuery(TransactionOperationDO operationDO) {
        if (operationDO == null || !StringUtils.hasText(operationDO.getOperationId())) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime orderTransactionDateTime = parseOperationDateTime(operationDO);
        TransactionOrderDO orderDO = transactionRecordService.findOrder(orderTransactionDateTime, operationDO.getOperationId());
        if (orderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return orderDO;
    }

    /**
     * 从 operationId 中解析生命周期主单所在分表时间。
     * <p>
     * operation_id 带有生成时间片段，优先用于定位主单分表；解析失败时回退动作业务时间，兼容历史或人工补录数据。
     *
     * @param operationDO 查询命中的动作单
     * @return 主单分表时间
     */
    private LocalDateTime parseOperationDateTime(TransactionOperationDO operationDO) {
        String operationId = operationDO.getOperationId();
        int startIndex = operationId.startsWith(OPERATION_ID_PREFIX) ? OPERATION_ID_PREFIX.length() : 0;
        int endIndex = startIndex + 17;
        if (operationId.length() >= endIndex) {
            try {
                return LocalDateTime.parse(operationId.substring(startIndex, endIndex),
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS", Locale.ROOT));
            } catch (java.time.format.DateTimeParseException ignored) {
                // 回退到动作业务时间，兼容历史 operation_id 或异常补录数据。
            }
        }
        return operationDO.getTransactionDateTime();
    }

    /**
     * 填充商户查询接口中的生命周期金额汇总。
     * <p>
     * 一步支付成功后展示口径按 captured 金额兜底交易金额，避免前端把支付订单误展示为未请款。
     *
     * @param resultDTO 查询结果
     * @param orderDO 生命周期主单
     */
    private void fillQueryOrderSummary(PaymentQueryResultDTO resultDTO, TransactionOrderDO orderDO) {
        resultDTO.setOrderAmount(orderDO.getLabelAmount());
        resultDTO.setOrderCurrency(orderDO.getLabelCurrency());
        resultDTO.setTotalAuthorizedAmount(resolveDisplayAuthorizedAmount(orderDO));
        resultDTO.setTotalCapturedAmount(resolveDisplayCapturedAmount(orderDO));
        resultDTO.setTotalRefundAmount(orderDO.getRefundedAmount());
        resultDTO.setTotalChargebackAmount(orderDO.getChargebackAmount());
        resultDTO.setLabelAmount(orderDO.getLabelAmount());
        resultDTO.setLabelCurrency(orderDO.getLabelCurrency());
        resultDTO.setTransactionAmount(orderDO.getTransactionAmount());
        resultDTO.setTransactionCurrency(orderDO.getTransactionCurrency());
        resultDTO.setTransactionRate(orderDO.getTransactionRate());
        resultDTO.setRateSource(orderDO.getRateSource());
        resultDTO.setRateTime(orderDO.getRateTime());
        resultDTO.setSettlementAmount(orderDO.getSettlementAmount());
        resultDTO.setSettlementCurrency(orderDO.getSettlementCurrency());
        resultDTO.setTransactionTimeZone(orderDO.getTransactionTimeZone());
    }

    /**
     * 转换单笔动作展示信息。
     *
     * @param operationDO 交易动作单
     * @param orderDO 生命周期主单
     * @return 商户查询交易明细
     */
    private PaymentQueryResultDTO.TransactionInfoDTO toQueryTransactionInfo(TransactionOperationDO operationDO,
                                                                           TransactionOrderDO orderDO) {
        PaymentQueryResultDTO.TransactionInfoDTO target = new PaymentQueryResultDTO.TransactionInfoDTO();
        target.setTransactionId(operationDO.getTransactionId());
        target.setSourceTransactionId(operationDO.getSourceTransactionId());
        target.setCode(resolveMerchantResponseCode(operationDO.getTransactionStatus()));
        target.setMessage(resolveMerchantResponseMessage(operationDO));
        target.setTransactionType(operationDO.getTransactionType());
        target.setTransactionDateTime(operationDO.getTransactionDateTime());
        target.setPaymentMethod(StringUtils.hasText(orderDO.getPaymentMethod()) ? orderDO.getPaymentMethod() : DEFAULT_PAYMENT_METHOD);
        target.setCardBrand(orderDO.getPaymentBrand());
        target.setCardBin(null);
        target.setAuthCode(operationDO.getAuthCode());
        target.setArn(firstText(operationDO.getAcquirerReferenceNo(), operationDO.getRrn()));
        target.setDescription(null);
        target.setCallbackUrl(null);
        return target;
    }

    /**
     * 解析交易类型，当前首次类交易入口默认使用 AUTHORIZATION。
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
     * 规范化后续交易命令。
     * <p>
     * 后续交易统一继承原交易生命周期币种和渠道交易 ID，上送渠道时不能按商户新请求重新换汇或重新生成目标交易号。
     *
     * @param commandDTO 后续交易命令
     * @param sourceOrderDO 原生命周期主单
     * @param sourceOperationDO 被关联的原动作单
     */
    private void normalizeFollowUpCommand(PaymentCreateCommandDTO commandDTO,
                                          TransactionOrderDO sourceOrderDO,
                                          TransactionOperationDO sourceOperationDO) {
        validateFollowUpMerchantOrderNo(commandDTO, sourceOrderDO);
        commandDTO.setMerchantOrderNo(sourceOrderDO.getMerchantOrderNo());
        commandDTO.setLabelCurrency(StringUtils.hasText(commandDTO.getCurrency())
                ? normalizeCurrency(commandDTO.getCurrency())
                : sourceOrderDO.getTransactionCurrency());
        commandDTO.setLabelAmount(commandDTO.getAmount() == null ? sourceOrderDO.getTransactionAmount() : commandDTO.getAmount());
        commandDTO.setCurrency(sourceOrderDO.getTransactionCurrency());
        if (commandDTO.getTransactionInfo() != null && sourceOperationDO != null) {
            commandDTO.getTransactionInfo().setSourceChannelTransactionId(sourceOperationDO.getChannelTransactionId());
        }
        if (commandDTO.getAmount() == null) {
            commandDTO.setAmount(sourceOrderDO.getTransactionAmount());
        }
        commandDTO.setTransactionCurrency(sourceOrderDO.getTransactionCurrency());
        commandDTO.setTransactionAmount(commandDTO.getAmount());
        commandDTO.setTransactionRate(defaultTransactionRate());
        commandDTO.setRateSource(null);
        commandDTO.setRateTime(null);
        commandDTO.setDccEnabled(0);
        commandDTO.setEdcEnabled(0);
    }

    /**
     * 校验退款请求中的商户订单号。
     * <p>
     * 退款允许商户不传 merchantOrderNo；如传入则必须和原生命周期一致，避免跨订单退款。
     *
     * @param commandDTO 退款命令
     * @param sourceOrderDO 原生命周期主单
     */
    private void validateFollowUpMerchantOrderNo(PaymentCreateCommandDTO commandDTO, TransactionOrderDO sourceOrderDO) {
        if (!PaymentTransactionTypeEnum.REFUND.getCode().equals(commandDTO.getTransactionType())) {
            return;
        }
        if (!StringUtils.hasText(commandDTO.getMerchantOrderNo())) {
            return;
        }
        if (!commandDTO.getMerchantOrderNo().equals(sourceOrderDO.getMerchantOrderNo())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "merchant order number must match source transaction");
        }
    }

    /**
     * 解析后续交易使用的渠道订单号。
     *
     * @param commandDTO 后续交易命令
     * @param sourceOrderDO 原生命周期主单
     * @return 渠道订单号
     */
    private String resolveChannelOrderNo(PaymentCreateCommandDTO commandDTO, TransactionOrderDO sourceOrderDO) {
        // MPGS 等渠道用 orderId 关联原始授权/支付订单；即使商户用请款动作发起退款，也不能把请款 ID 当渠道订单号。
        return StringUtils.hasText(sourceOrderDO.getRootTransactionId())
                ? sourceOrderDO.getRootTransactionId()
                : sourceOrderDO.getLatestTransactionId();
    }

    /**
     * 构造后续交易初始返回结果。
     *
     * @param commandDTO 后续交易命令
     * @param sourceOrderDO 原生命周期主单
     * @param transactionId 当前后续交易 ID
     * @param transactionTypeEnum 后续交易类型
     * @return 初始返回结果
     */
    private PaymentCreateResultDTO buildFollowUpResult(PaymentCreateCommandDTO commandDTO,
                                                       TransactionOrderDO sourceOrderDO,
                                                       String transactionId,
                                                       PaymentTransactionTypeEnum transactionTypeEnum) {
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setOperationId(sourceOrderDO.getOperationId());
        resultDTO.setTransactionId(transactionId);
        resultDTO.setSourceTransactionId(commandDTO.getTransactionInfo().getSourceTransactionId());
        resultDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        resultDTO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        resultDTO.setMerchantId(commandDTO.getMerchantId());
        resultDTO.setSubMerchantInfo(toResultSubMerchantInfo(commandDTO.getSubMerchantInfo()));
        resultDTO.setTransactionType(transactionTypeEnum.getCode());
        resultDTO.setCurrency(sourceOrderDO.getTransactionCurrency());
        resultDTO.setAmount(requiresAmount(transactionTypeEnum)
                ? toMinorAmount(commandDTO.getAmount(), sourceOrderDO.getTransactionCurrency())
                : toMinorAmount(sourceOrderDO.getTransactionAmount(), sourceOrderDO.getTransactionCurrency()));
        return resultDTO;
    }

    /**
     * 判断后续交易是否必须携带金额。
     *
     * @param transactionTypeEnum 后续交易类型
     * @return true 表示必须校验请求金额
     */
    private boolean requiresAmount(PaymentTransactionTypeEnum transactionTypeEnum) {
        return PaymentTransactionTypeEnum.CAPTURE == transactionTypeEnum
                || PaymentTransactionTypeEnum.REFUND == transactionTypeEnum
                || PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION == transactionTypeEnum;
    }

    /**
     * 填充首次类交易返回的业务展示字段。
     *
     * @param commandDTO 创建交易命令
     * @param routeResultDTO 渠道路由结果，可为空
     * @param channelResponse 渠道响应，可为空
     * @param resultDTO 待填充返回结果
     */
    private void enrichResult(PaymentCreateCommandDTO commandDTO,
                              PaymentRouteResultDTO routeResultDTO,
                              ChannelPaymentResponse channelResponse,
                              PaymentCreateResultDTO resultDTO) {
        resultDTO.setMerchantId(commandDTO.getMerchantId());
        resultDTO.setSubMerchantInfo(toResultSubMerchantInfo(commandDTO.getSubMerchantInfo()));
        resultDTO.setOrderAmount(commandDTO.getLabelAmount() == null ? commandDTO.getAmount() : commandDTO.getLabelAmount());
        resultDTO.setOrderCurrency(StringUtils.hasText(commandDTO.getLabelCurrency()) ? commandDTO.getLabelCurrency() : commandDTO.getCurrency());
        resultDTO.setLabelAmount(commandDTO.getLabelAmount());
        resultDTO.setLabelCurrency(commandDTO.getLabelCurrency());
        resultDTO.setTransactionAmount(commandDTO.getTransactionAmount());
        resultDTO.setTransactionCurrency(commandDTO.getTransactionCurrency());
        resultDTO.setTransactionRate(commandDTO.getTransactionRate());
        resultDTO.setRateSource(commandDTO.getRateSource());
        resultDTO.setRateTime(commandDTO.getRateTime());
        resultDTO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        resultDTO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        resultDTO.setPaymentMethod(commandDTO.getPaymentMethod());
        resultDTO.setPaymentBrand(resolvePaymentBrand(commandDTO));
        resultDTO.setCardBin(resolveCardBin(commandDTO));
        enrichPaymentMethodSummary(resultDTO, channelResponse);
        resultDTO.setDescription(commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getDescription());
        resultDTO.setCallbackUrl(resolveCallbackUrl(commandDTO));
        if (channelResponse != null) {
            resultDTO.setAuthCode(channelResponse.getAuthCode());
            resultDTO.setAcquirerReferenceNo(firstText(channelResponse.getAcquirerReferenceNo(), channelResponse.getRrn()));
        }
        enrichMerchantResponse(resultDTO, channelResponse);
        if (PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus())) {
            resultDTO.setFailReasonMessage(merchantVisibleFailureMessage(resultDTO.getStatus(), resultDTO.getFailReasonCode()));
        }
        fillInitialTotals(resultDTO);
    }

    /**
     * 使用渠道返回的支付工具摘要补齐平台交易结果。
     * <p>
     * 渠道识别出的卡品牌、发卡国家、资金类型和 CSC 结果比商户上送或 BIN 推断更可靠；
     * 完整 PAN 和 CVV 不允许进入该结果对象或后续落库。
     *
     * @param resultDTO       待填充交易结果
     * @param channelResponse 渠道响应，可为空
     */
    private void enrichPaymentMethodSummary(PaymentCreateResultDTO resultDTO, ChannelPaymentResponse channelResponse) {
        if (resultDTO == null || channelResponse == null || channelResponse.getPaymentMethodSummary() == null) {
            return;
        }
        PaymentMethodSummary summary = channelResponse.getPaymentMethodSummary();
        resultDTO.setPaymentBrand(firstText(summary.getPaymentBrand(), resultDTO.getPaymentBrand()));
        resultDTO.setCardBin(firstText(normalizeChannelCardNumber(summary.getCardNumberMasked()), resultDTO.getCardBin()));
        resultDTO.setCardNumberMasked(summary.getCardNumberMasked());
        resultDTO.setExpiryMonth(summary.getExpiryMonth());
        resultDTO.setExpiryYear(summary.getExpiryYear());
        resultDTO.setIssuerCountry(summary.getIssuerCountry());
        resultDTO.setFundingMethod(summary.getFundingMethod());
        resultDTO.setCscResult(summary.getCscResult());
    }

    private String normalizeChannelCardNumber(String cardNumberMasked) {
        if (!StringUtils.hasText(cardNumberMasked)) {
            return null;
        }
        String digits = cardNumberMasked.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
            return digits.substring(0, 6) + "****" + digits.substring(digits.length() - 4);
        }
        return cardNumberMasked;
    }

    /**
     * 填充后续交易返回的生命周期金额汇总。
     * <p>
     * 成功的请款、退款、增量授权会即时叠加到展示汇总；非成功状态保持原主单汇总，等待回调或勾兑终态确认。
     *
     * @param commandDTO 后续交易命令
     * @param sourceOrderDO 原生命周期主单
     * @param routeResultDTO 渠道路由结果
     * @param channelResponse 渠道响应，可为空
     * @param resultDTO 待填充返回结果
     */
    private void enrichFollowUpResult(PaymentCreateCommandDTO commandDTO,
                                      TransactionOrderDO sourceOrderDO,
                                      PaymentRouteResultDTO routeResultDTO,
                                      ChannelPaymentResponse channelResponse,
                                      PaymentCreateResultDTO resultDTO) {
        enrichResult(commandDTO, routeResultDTO, channelResponse, resultDTO);
        resultDTO.setPaymentMethod(sourceOrderDO.getPaymentMethod());
        resultDTO.setPaymentBrand(firstText(resultDTO.getPaymentBrand(), sourceOrderDO.getPaymentBrand()));
        resultDTO.setOrderAmount(commandDTO.getLabelAmount());
        resultDTO.setOrderCurrency(commandDTO.getLabelCurrency());
        resultDTO.setTotalAuthorizedAmount(resolveDisplayAuthorizedAmount(sourceOrderDO));
        resultDTO.setTotalCapturedAmount(resolveDisplayCapturedAmount(sourceOrderDO));
        resultDTO.setTotalRefundAmount(sourceOrderDO.getRefundedAmount());
        resultDTO.setTotalChargebackAmount(sourceOrderDO.getChargebackAmount());
        resultDTO.setTotalAuthorizedAmount(sumOnSuccess(resultDTO.getTotalAuthorizedAmount(), resultDTO, PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION));
        resultDTO.setTotalCapturedAmount(sumOnSuccess(resultDTO.getTotalCapturedAmount(), resultDTO, PaymentTransactionTypeEnum.CAPTURE));
        resultDTO.setTotalRefundAmount(sumOnSuccess(sourceOrderDO.getRefundedAmount(), resultDTO, PaymentTransactionTypeEnum.REFUND));
        if (PaymentTransactionTypeEnum.VOID.getCode().equals(resultDTO.getTransactionType())
                && PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())) {
            resultDTO.setTotalVoidAmount(sourceOrderDO.getAuthorizedAmount());
        }
    }

    /**
     * 解析授权金额展示口径。
     *
     * @param sourceOrderDO 生命周期主单
     * @return 授权展示金额
     */
    private BigDecimal resolveDisplayAuthorizedAmount(TransactionOrderDO sourceOrderDO) {
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            return firstPositive(sourceOrderDO.getCapturedAmount(), sourceOrderDO.getTransactionAmount());
        }
        return sourceOrderDO.getAuthorizedAmount();
    }

    /**
     * 解析请款金额展示口径。
     *
     * @param sourceOrderDO 生命周期主单
     * @return 请款展示金额
     */
    private BigDecimal resolveDisplayCapturedAmount(TransactionOrderDO sourceOrderDO) {
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            return firstPositive(sourceOrderDO.getCapturedAmount(), sourceOrderDO.getTransactionAmount());
        }
        return sourceOrderDO.getCapturedAmount();
    }

    /**
     * 取优先的正数金额。
     *
     * @param first 第一候选金额
     * @param second 第二候选金额
     * @return 优先金额
     */
    private BigDecimal firstPositive(BigDecimal first, BigDecimal second) {
        if (first != null && first.compareTo(BigDecimal.ZERO) > 0) {
            return first;
        }
        return second;
    }

    /**
     * 填充首次交易同步成功时的生命周期汇总金额。
     *
     * @param resultDTO 交易结果
     */
    private void fillInitialTotals(PaymentCreateResultDTO resultDTO) {
        if (!PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                || resultDTO.getTransactionAmount() == null) {
            return;
        }
        if (PaymentTransactionTypeEnum.AUTHORIZATION.getCode().equals(resultDTO.getTransactionType())
                || PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode().equals(resultDTO.getTransactionType())
                || PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode().equals(resultDTO.getTransactionType())) {
            resultDTO.setTotalAuthorizedAmount(resultDTO.getTransactionAmount());
            return;
        }
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(resultDTO.getTransactionType())
                || PaymentTransactionTypeEnum.CAPTURE.getCode().equals(resultDTO.getTransactionType())
                || PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION.getCode().equals(resultDTO.getTransactionType())) {
            resultDTO.setTotalCapturedAmount(resultDTO.getTransactionAmount());
            return;
        }
        if (PaymentTransactionTypeEnum.REFUND.getCode().equals(resultDTO.getTransactionType())) {
            resultDTO.setTotalRefundAmount(resultDTO.getTransactionAmount());
            return;
        }
        if (PaymentTransactionTypeEnum.VOID.getCode().equals(resultDTO.getTransactionType())) {
            resultDTO.setTotalVoidAmount(resultDTO.getTransactionAmount());
        }
    }

    /**
     * 在同步成功时把当前动作金额累加到生命周期汇总。
     *
     * @param existingAmount 原汇总金额
     * @param resultDTO 当前交易结果
     * @param targetTypeEnum 需要累加的交易类型
     * @return 新汇总金额
     */
    private BigDecimal sumOnSuccess(BigDecimal existingAmount,
                                    PaymentCreateResultDTO resultDTO,
                                    PaymentTransactionTypeEnum targetTypeEnum) {
        BigDecimal current = existingAmount == null ? BigDecimal.ZERO : existingAmount;
        if (targetTypeEnum.getCode().equals(resultDTO.getTransactionType())
                && PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                && resultDTO.getTransactionAmount() != null) {
            return current.add(resultDTO.getTransactionAmount());
        }
        return current;
    }

    private void enrichMerchantResponse(PaymentCreateResultDTO resultDTO, ChannelPaymentResponse channelResponse) {
        resultDTO.setMerchantResponseCode(resolveMerchantResponseCode(resultDTO.getStatus()));
        resultDTO.setMerchantResponseMessage(resolveMerchantResponseMessage(resultDTO, channelResponse));
    }

    private String resolvePaymentBrand(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO.getTransactionInfo() != null && StringUtils.hasText(commandDTO.getTransactionInfo().getCardBrand())) {
            return commandDTO.getTransactionInfo().getCardBrand();
        }
        if (commandDTO.getCardInfo() == null || !StringUtils.hasText(commandDTO.getCardInfo().getCardNo())) {
            return null;
        }
        String cardNo = commandDTO.getCardInfo().getCardNo().trim();
        if (cardNo.startsWith("4")) {
            return "VISA";
        }
        if (cardNo.startsWith("34") || cardNo.startsWith("37")) {
            return "AMEX";
        }
        if (cardNo.startsWith("35")) {
            return "JCB";
        }
        if (cardNo.startsWith("62")) {
            return "UNIONPAY";
        }
        if (cardNo.startsWith("5") || cardNo.startsWith("22")) {
            return "MASTERCARD";
        }
        return null;
    }

    private String resolveCardBin(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO.getCardInfo() == null || !StringUtils.hasText(commandDTO.getCardInfo().getCardNo())) {
            return null;
        }
        String cardNo = commandDTO.getCardInfo().getCardNo().trim();
        if (cardNo.length() < 10) {
            return null;
        }
        return cardNo.substring(0, 6) + "****" + cardNo.substring(cardNo.length() - 4);
    }

    private PaymentCreateResultDTO.SubMerchantInfoDTO toResultSubMerchantInfo(PaymentCreateCommandDTO.SubMerchantInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateResultDTO.SubMerchantInfoDTO target = new PaymentCreateResultDTO.SubMerchantInfoDTO();
        target.setSubId(source.getSubId());
        target.setSubName(source.getSubName());
        target.setSubCompanyName(source.getSubCompanyName());
        target.setSubCountryCode(source.getSubCountryCode());
        target.setSubState(source.getSubState());
        target.setSubCity(source.getSubCity());
        target.setSubStreet(source.getSubStreet());
        target.setSubPostal(source.getSubPostal());
        target.setSubEmail(source.getSubEmail());
        target.setSubPhone(source.getSubPhone());
        target.setSubTaxId(source.getSubTaxId());
        target.setMerchantCategory(source.getMerchantCategory());
        target.setIntesCode(source.getIntesCode());
        target.setChargeType(source.getChargeType());
        return isEmptySubMerchantInfo(target) ? null : target;
    }

    private boolean isEmptySubMerchantInfo(PaymentCreateResultDTO.SubMerchantInfoDTO value) {
        return value == null || Stream.of(
                value.getSubId(),
                value.getSubName(),
                value.getSubCompanyName(),
                value.getSubCountryCode(),
                value.getSubState(),
                value.getSubCity(),
                value.getSubStreet(),
                value.getSubPostal(),
                value.getSubEmail(),
                value.getSubPhone(),
                value.getSubTaxId(),
                value.getMerchantCategory(),
                value.getIntesCode(),
                value.getChargeType()).allMatch(item -> !StringUtils.hasText(item));
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

    /**
     * 解析同步响应给商户的失败文案。
     * <p>
     * MPGS result=ERROR 多为参数、格式或技术类错误，对商户统一模糊展示；非 ERROR 的渠道拒绝可展示渠道响应码和响应描述，
     * 方便商户排查发卡行拒绝、支付细节需更换等付款侧问题。
     *
     * @param resultDTO 当前交易结果
     * @return 商户可见响应文案
     */
    private String resolveMerchantResponseMessage(PaymentCreateResultDTO resultDTO, ChannelPaymentResponse response) {
        if (resultDTO == null) {
            return resolveMerchantResponseMessage((String) null);
        }
        if (!PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus())) {
            return resolveMerchantResponseMessage(resultDTO.getStatus());
        }
        if (response == null || "ERROR".equalsIgnoreCase(response.getRawChannelStatus())) {
            return ApiResultEnum.PAYMENT_REJECTED.getMessage();
        }
        return firstText(joinCodeAndMessage(response.getChannelResponseCode(), response.getChannelResponseMessage()),
                ApiResultEnum.PAYMENT_REJECTED.getMessage());
    }

    /**
     * 解析查询接口中交易动作的商户响应文案。
     *
     * @param operationDO 交易动作单
     * @return 商户可见响应文案
     */
    private String resolveMerchantResponseMessage(TransactionOperationDO operationDO) {
        if (operationDO == null) {
            return resolveMerchantResponseMessage((String) null);
        }
        if (!PaymentTransactionStatusEnum.FAILED.getCode().equals(operationDO.getTransactionStatus())) {
            return resolveMerchantResponseMessage(operationDO.getTransactionStatus());
        }
        if ("ERROR".equalsIgnoreCase(operationDO.getChannelStatus())) {
            return ApiResultEnum.PAYMENT_REJECTED.getMessage();
        }
        return firstText(joinCodeAndMessage(operationDO.getChannelResponseCode(), operationDO.getChannelResponseMessage()),
                ApiResultEnum.PAYMENT_REJECTED.getMessage());
    }

    private String joinCodeAndMessage(String code, String message) {
        if (StringUtils.hasText(code) && StringUtils.hasText(message)) {
            return code + ": " + message;
        }
        return firstText(code, message);
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

    /**
     * 固定商户标签金额和币种，后续 EDC 只能写入交易金额字段，不能覆盖商户原始请求语义。
     *
     * @param commandDTO 创建交易命令
     */
    private void initializeLabelAmount(PaymentCreateCommandDTO commandDTO) {
        commandDTO.setCurrency(normalizeCurrency(commandDTO.getCurrency()));
        if (commandDTO.getLabelAmount() == null) {
            commandDTO.setLabelAmount(commandDTO.getAmount());
        }
        if (!StringUtils.hasText(commandDTO.getLabelCurrency())) {
            commandDTO.setLabelCurrency(commandDTO.getCurrency());
        } else {
            commandDTO.setLabelCurrency(normalizeCurrency(commandDTO.getLabelCurrency()));
        }
    }

    /**
     * 按路由币种决定是否执行 EDC。汇率缺失时交易失败并落库，不再调用渠道。
     *
     * @param commandDTO     创建交易命令
     * @param routeResultDTO 路由结果
     * @param resultDTO      创建结果
     * @return true 表示可继续调用渠道，false 表示交易已因汇率缺失失败
     */
    private boolean applyCurrencyConversion(PaymentCreateCommandDTO commandDTO,
                                            PaymentRouteResultDTO routeResultDTO,
                                            PaymentCreateResultDTO resultDTO) {
        String labelCurrency = normalizeCurrency(commandDTO.getLabelCurrency());
        String routedCurrency = normalizeCurrency(routeResultDTO == null ? null : routeResultDTO.getRoutedCurrency());
        if (!StringUtils.hasText(routedCurrency) || labelCurrency.equals(routedCurrency)) {
            applyNoConversion(commandDTO);
            return true;
        }
        PaymentExchangeRateDTO rateDTO = paymentExchangeRateService.findTransactionRate(
                        labelCurrency, routedCurrency, commandDTO.getTransactionDateTime())
                .orElse(null);
        if (rateDTO == null || rateDTO.getFinalRate() == null || rateDTO.getFinalRate().compareTo(BigDecimal.ZERO) <= 0) {
            commandDTO.setTransactionCurrency(labelCurrency);
            commandDTO.setTransactionAmount(commandDTO.getLabelAmount());
            commandDTO.setTransactionRate(defaultTransactionRate());
            commandDTO.setRateSource(null);
            commandDTO.setRateTime(null);
            commandDTO.setDccEnabled(0);
            commandDTO.setEdcEnabled(1);
            resultDTO.setCurrency(labelCurrency);
            resultDTO.setAmount(toMinorAmount(commandDTO.getTransactionAmount(), labelCurrency));
            resultDTO.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
            resultDTO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
            resultDTO.setFailReasonCode(PaymentFailureReasonEnum.EXCHANGE_RATE_NOT_FOUND.getCode());
            return false;
        }
        int targetExponent = resolveCurrencyExponent(routedCurrency);
        BigDecimal transactionAmount = commandDTO.getLabelAmount()
                .multiply(rateDTO.getFinalRate())
                .setScale(targetExponent, RoundingMode.HALF_UP);
        commandDTO.setTransactionCurrency(routedCurrency);
        commandDTO.setTransactionAmount(transactionAmount);
        commandDTO.setTransactionRate(rateDTO.getFinalRate());
        commandDTO.setRateSource(rateDTO.getSourceCode());
        commandDTO.setRateTime(rateDTO.getEffectiveTime());
        commandDTO.setDccEnabled(0);
        commandDTO.setEdcEnabled(1);
        return true;
    }

    private void applyNoConversion(PaymentCreateCommandDTO commandDTO) {
        commandDTO.setTransactionCurrency(normalizeCurrency(commandDTO.getLabelCurrency()));
        commandDTO.setTransactionAmount(commandDTO.getLabelAmount());
        commandDTO.setTransactionRate(defaultTransactionRate());
        commandDTO.setRateSource(null);
        commandDTO.setRateTime(null);
        commandDTO.setDccEnabled(0);
        commandDTO.setEdcEnabled(0);
    }

    private BigDecimal defaultTransactionRate() {
        return new BigDecimal("1.00000000");
    }

    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
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

    private boolean isTerminal(PaymentCreateResultDTO resultDTO) {
        return resultDTO != null
                && (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus()));
    }

    /**
     * 从 ISO 字典读取交易币种默认小数位，禁止默认所有币种都是 2 位小数。
     *
     * @param currency ISO 4217 币种代码或名称
     * @return 默认小数位
     */
    private int resolveCurrencyExponent(String currency) {
        IsoCurrencyInfo currencyInfo = isoDictionaryService.getCurrency(currency)
                .orElseThrow(() -> new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "currency can not be resolved"));
        if (currencyInfo.defaultFractionDigits() < 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "currency fraction digits can not be resolved");
        }
        return currencyInfo.defaultFractionDigits();
    }

    /**
     * 记录首次类交易事实，测试或早期骨架未装配记录服务时允许跳过，但正式环境必须装配默认实现。
     *
     * @param commandDTO 创建交易命令
     * @param routeResultDTO 路由结果
     * @param channelResponse 渠道响应
     * @param resultDTO 交易结果
     * @param riskDecisionEnum 风控决策
     * @param currencyExponent 交易币种默认小数位
     */
    private void recordInitialTransaction(PaymentCreateCommandDTO commandDTO,
                                          PaymentRouteResultDTO routeResultDTO,
                                          PaymentChannelInvokeResultDTO invokeResultDTO,
                                          PaymentCreateResultDTO resultDTO,
                                          PaymentRiskDecisionEnum riskDecisionEnum,
                                          int currencyExponent) {
        if (transactionRecordService == null) {
            return;
        }
        transactionRecordService.recordInitialTransaction(
                commandDTO,
                routeResultDTO,
                invokeResultDTO,
                resultDTO,
                riskDecisionEnum,
                currencyExponent);
    }

    private void recordFollowUpTransaction(PaymentCreateCommandDTO commandDTO,
                                           TransactionOrderDO sourceOrderDO,
                                           PaymentRouteResultDTO routeResultDTO,
                                           PaymentChannelInvokeResultDTO invokeResultDTO,
                                           PaymentCreateResultDTO resultDTO,
                                           int currencyExponent) {
        if (transactionRecordService == null) {
            return;
        }
        TransactionFollowUpRecordDTO recordDTO = new TransactionFollowUpRecordDTO();
        recordDTO.setSourceOrderDO(sourceOrderDO);
        recordDTO.setCommandDTO(commandDTO);
        recordDTO.setRouteResultDTO(routeResultDTO);
        recordDTO.setChannelResponse(invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse());
        recordDTO.setChannelInvokeResultDTO(invokeResultDTO);
        recordDTO.setResultDTO(resultDTO);
        recordDTO.setCurrencyExponent(currencyExponent);
        transactionRecordService.recordFollowUpTransaction(recordDTO);
    }

    /**
     * 保存支付创建本地事务事件，实际 RocketMQ 投递由后续 outbox relay 在事务提交后处理。
     *
     * @param commandDTO 创建交易命令
     * @param resultDTO  创建交易结果
     */
    private void saveTransactionCreatedEvent(PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        TransactionEventMessage message = new TransactionEventMessage();
        message.setMessageId(resultDTO.getTransactionId());
        message.setCreatedAt(LocalDateTime.now());
        message.setTransactionId(resultDTO.getTransactionId());
        message.setOperationId(resultDTO.getOperationId());
        message.setMerchantId(commandDTO.getMerchantId());
        message.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        message.setTransactionType(resultDTO.getTransactionType());
        message.setTransactionStatus(resultDTO.getStatus());
        message.setEventType(TransactionMqConstants.TRANSACTION_CREATED_TAG);
        message.setTransactionDateTime(commandDTO.getTransactionDateTime());
        TransactionEventOutboxDO eventDO = new TransactionEventOutboxDO();
        eventDO.setEventNo(resultDTO.getTransactionId());
        eventDO.setAggregateType(PAYMENT_TRANSACTION_AGGREGATE);
        eventDO.setAggregateNo(resultDTO.getOperationId());
        eventDO.setOperationId(resultDTO.getOperationId());
        eventDO.setTransactionId(resultDTO.getTransactionId());
        eventDO.setMerchantId(commandDTO.getMerchantId());
        eventDO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        eventDO.setTransactionType(resultDTO.getTransactionType());
        eventDO.setEventType(TransactionMqConstants.TRANSACTION_CREATED_TAG);
        eventDO.setEventStatus(EVENT_STATUS_INIT);
        eventDO.setTopic(MqTopic.PAYMENT_EVENT);
        eventDO.setTag(TransactionMqConstants.TRANSACTION_CREATED_TAG);
        eventDO.setMessageKey(resultDTO.getTransactionId());
        eventDO.setMessageGroup(resultDTO.getOperationId());
        eventDO.setPayloadJson(JsonUtils.toJsonString(message));
        eventDO.setEventTime(message.getCreatedAt());
        eventDO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        eventDO.setTransactionUtcTime(toUtcTime(commandDTO.getTransactionDateTime(), DEFAULT_TIME_ZONE));
        eventDO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
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
     * @param commandDTO     创建交易命令
     * @param resultDTO      创建交易结果
     */
    private void completeIdempotency(String idempotencyKey, PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        transactionIdempotencyService.complete(
                TRANSACTION_OPERATION_SCOPE,
                idempotencyKey,
                resultDTO.getOperationId(),
                resultDTO.getTransactionId(),
                resultDTO.getStatus(),
                commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount(),
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
        resultDTO.setOperationId(record.getOperationId());
        resultDTO.setTransactionId(record.getTransactionId());
        resultDTO.setMerchantOrderNo(record.getMerchantOrderNo());
        resultDTO.setMerchantOrderId(record.getMerchantOrderId());
        resultDTO.setTransactionType(record.getTransactionType());
        resultDTO.setStatus(record.getTransactionStatus());
        resultDTO.setAmount(record.getTransactionAmount() == null || record.getTransactionCurrency() == null
                ? null
                : toMinorAmount(record.getTransactionAmount(), record.getTransactionCurrency()));
        resultDTO.setCurrency(record.getTransactionCurrency());
        return resultDTO;
    }

    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime, String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone == null || timeZone.isBlank() ? DEFAULT_TIME_ZONE : timeZone);
        return transactionDateTime.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * 尝试获取交易锁；未装配 Redis 锁时跳过锁保护，由数据库唯一键兜底。
     *
     * @param lockKey   Redis 锁 Key
     * @param lockValue 锁值
     * @return true 表示可继续处理
     */
    private boolean tryLock(String lockKey, String lockValue) {
        if (redisLockServices.isEmpty()) {
            return true;
        }
        return redisLockServices.get(0).tryLock(lockKey, lockValue, TRANSACTION_OPERATION_LOCK_TTL_SECONDS);
    }

    /**
     * 在本地事务完成后释放交易锁，避免事务提交前锁先释放导致并发请求提前进入。
     *
     * @param lockKey   Redis 锁 Key
     * @param lockValue 锁值
     * @param locked    是否已获取锁
     */
    private void unlockAfterTransaction(String lockKey, String lockValue, boolean locked) {
        if (!locked || redisLockServices.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    unlock(lockKey, lockValue);
                }
            });
            return;
        }
        unlock(lockKey, lockValue);
    }

    /**
     * 释放交易锁。
     *
     * @param lockKey   Redis 锁 Key
     * @param lockValue 锁值
     */
    private void unlock(String lockKey, String lockValue) {
        redisLockServices.get(0).unlock(lockKey, lockValue);
    }
}
