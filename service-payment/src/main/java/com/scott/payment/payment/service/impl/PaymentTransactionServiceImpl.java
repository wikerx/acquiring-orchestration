package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.openapi.result.OpenApiMessageAudience;
import com.scott.payment.component.core.openapi.result.OpenApiMessageLocale;
import com.scott.payment.component.core.openapi.result.OpenApiTransactionProjection;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.lock.DistributedLockService;
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
import com.scott.payment.payment.service.CaptureChannelResultTransactionService;
import com.scott.payment.payment.service.CaptureTransactionPreparationService;
import com.scott.payment.payment.service.IncrementalAuthorizationChannelResultTransactionService;
import com.scott.payment.payment.service.IncrementalAuthorizationTransactionPreparationService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.PaymentChannelResultTransactionService;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.RefundChannelResultTransactionService;
import com.scott.payment.payment.service.RefundTransactionPreparationService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.PaymentTransactionService;
import com.scott.payment.payment.service.PaymentTransactionPreparationService;
import com.scott.payment.payment.service.VoidChannelResultTransactionService;
import com.scott.payment.payment.service.VoidTransactionPreparationService;
import com.scott.payment.payment.service.dto.CapturePreparationResultDTO;
import com.scott.payment.payment.service.dto.IncrementalAuthorizationPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentInitialPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.RefundPreparationResultDTO;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import com.scott.payment.payment.service.dto.VoidPreparationResultDTO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.entity.TransactionPaymentMethodInfoDO;
import com.scott.payment.payment.service.impl.DefaultPaymentChannelInvokeService.PaymentChannelInvokeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 收单支付交易应用编排服务。
 *
 * <p>负责首次支付及后续授权、请款、退款、撤销的受理、幂等编排、渠道调用和结果收口。
 * 本地准备事务必须先于外部渠道调用提交；渠道结果通过独立事务和版本 CAS 推进，CAS 失败时以主库
 * 已提交事实为准，避免同步响应覆盖并发回调、查询勾兑或恢复任务写入的终态。</p>
 */
@Service
@Slf4j
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
     * 支付 Redis 业务域。
     */
    private static final String PAYMENT_REDIS_DOMAIN = "lock";

    /**
     * 支付 Redis 锁业务用途。
     */
    private static final String PAYMENT_LOCK_BUSINESS = "payment";

    /**
     * 交易动作准备锁用途。
     */
    private static final String TRANSACTION_OPERATION_LOCK_PURPOSE = "operation";

    /**
     * 交易动作锁过期秒数。
     */
    private static final Duration TRANSACTION_OPERATION_LOCK_WAIT_TIME = Duration.ZERO;

    /**
     * 支付准备临界区固定租约，不覆盖后续渠道调用。
     */
    private static final Duration TRANSACTION_OPERATION_LOCK_LEASE_TIME = Duration.ofSeconds(30);

    /**
     * 商户订单首次流程准备锁用途。
     */
    private static final String MERCHANT_ORDER_FLOW_LOCK_PURPOSE = "merchant-order-flow";

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
     * 首次交易本地准备服务，用于在渠道调用前独立提交幂等、交易事实和渠道请求 INIT。
     */
    private final PaymentTransactionPreparationService paymentTransactionPreparationService;

    /**
     * 渠道同步结果事务服务，用于在渠道调用后通过独立事务保存结果并推进状态。
     */
    private final PaymentChannelResultTransactionService paymentChannelResultTransactionService;

    /**
     * Capture 本地准备事务服务，用于在渠道 Capture 前提交幂等、动作事实和渠道请求 INIT。
     */
    private final CaptureTransactionPreparationService captureTransactionPreparationService;

    /**
     * Capture 渠道结果事务服务，用于在渠道调用后独立保存结果并 CAS 推进动作。
     */
    private final CaptureChannelResultTransactionService captureChannelResultTransactionService;

    /**
     * Refund 本地准备事务服务，用于在渠道 Refund 前提交幂等、退款事实和渠道请求 INIT。
     */
    private final RefundTransactionPreparationService refundTransactionPreparationService;

    /**
     * Refund 渠道结果事务服务，用于在渠道调用后独立保存结果并 CAS 推进退款动作。
     */
    private final RefundChannelResultTransactionService refundChannelResultTransactionService;

    /**
     * Void 本地准备事务服务，用于在渠道 Void 前提交幂等、撤销事实和渠道请求 INIT。
     */
    private final VoidTransactionPreparationService voidTransactionPreparationService;

    /**
     * Void 渠道结果事务服务，用于在渠道调用后独立保存结果并 CAS 推进撤销动作。
     */
    private final VoidChannelResultTransactionService voidChannelResultTransactionService;

    /**
     * Incremental Authorization 本地准备事务服务，用于在渠道增量授权前提交幂等、动作事实和渠道请求 INIT。
     */
    private final IncrementalAuthorizationTransactionPreparationService incrementalAuthorizationTransactionPreparationService;

    /**
     * Incremental Authorization 渠道结果事务服务，用于在渠道调用后独立保存结果并 CAS 推进增量授权动作。
     */
    private final IncrementalAuthorizationChannelResultTransactionService incrementalAuthorizationChannelResultTransactionService;

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
     * 支付准备阶段统一分布式锁服务。
     */
    private final DistributedLockService distributedLockService;

    /**
     * Redis 物理 Key 构造配置，统一注入系统和环境隔离前缀。
     */
    private final PaymentRedisProperties redisProperties;

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
     * @param distributedLockService 分布式锁服务
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
                                         DistributedLockService distributedLockService) {
        this(isoDictionaryService,
                paymentRiskInvokeService,
                paymentChannelRouteService,
                paymentChannelInvokeService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                paymentExchangeRateService,
                transactionIdempotencyService,
                transactionEventOutboxService,
                transactionRecordService,
                transactionStateMachineService,
                new DefaultChannelTransactionStatusResolver(),
                distributedLockService,
                new PaymentRedisProperties());
    }

    /**
     * 创建收单支付交易服务。
     *
     * @param isoDictionaryService        ISO 币种字典服务
     * @param paymentRiskInvokeService 路由前风控调用服务
     * @param paymentChannelRouteService 收单渠道路由服务
     * @param paymentChannelInvokeService 收单渠道调用服务
     * @param paymentTransactionPreparationService 首次交易本地准备服务
     * @param paymentChannelResultTransactionService 渠道同步结果事务服务
     * @param paymentExchangeRateService 交易汇率服务
     * @param transactionIdempotencyService 交易幂等服务
     * @param transactionEventOutboxService 交易本地事件服务
     * @param transactionRecordService 交易事实记录服务
     * @param transactionStateMachineService 交易状态机服务
     * @param channelStatusResolver 渠道状态解析服务
     * @param distributedLockService 分布式锁服务
     */
    public PaymentTransactionServiceImpl(IsoDictionaryService isoDictionaryService,
                                         PaymentRiskInvokeService paymentRiskInvokeService,
                                         PaymentChannelRouteService paymentChannelRouteService,
                                         PaymentChannelInvokeService paymentChannelInvokeService,
                                         PaymentTransactionPreparationService paymentTransactionPreparationService,
                                         PaymentChannelResultTransactionService paymentChannelResultTransactionService,
                                         PaymentExchangeRateService paymentExchangeRateService,
                                         TransactionIdempotencyService transactionIdempotencyService,
                                         TransactionEventOutboxService transactionEventOutboxService,
                                         TransactionRecordService transactionRecordService,
                                         TransactionStateMachineService transactionStateMachineService,
                                         ChannelTransactionStatusResolver channelStatusResolver,
                                         DistributedLockService distributedLockService) {
        this(isoDictionaryService,
                paymentRiskInvokeService,
                paymentChannelRouteService,
                paymentChannelInvokeService,
                paymentTransactionPreparationService,
                paymentChannelResultTransactionService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                paymentExchangeRateService,
                transactionIdempotencyService,
                transactionEventOutboxService,
                transactionRecordService,
                transactionStateMachineService,
                channelStatusResolver,
                distributedLockService,
                new PaymentRedisProperties());
    }

    /**
     * 创建收单支付交易服务。
     *
     * @param isoDictionaryService        ISO 币种字典服务
     * @param paymentRiskInvokeService 路由前风控调用服务
     * @param paymentChannelRouteService 收单渠道路由服务
     * @param paymentChannelInvokeService 收单渠道调用服务
     * @param paymentTransactionPreparationService 首次交易本地准备服务
     * @param paymentChannelResultTransactionService 渠道同步结果事务服务
     * @param captureTransactionPreparationService Capture 本地准备事务服务
     * @param captureChannelResultTransactionService Capture 渠道结果事务服务
     * @param refundTransactionPreparationService Refund 本地准备事务服务
     * @param refundChannelResultTransactionService Refund 渠道结果事务服务
     * @param voidTransactionPreparationService Void 本地准备事务服务
     * @param voidChannelResultTransactionService Void 渠道结果事务服务
     * @param incrementalAuthorizationTransactionPreparationService Incremental Authorization 本地准备事务服务
     * @param incrementalAuthorizationChannelResultTransactionService Incremental Authorization 渠道结果事务服务
     * @param paymentExchangeRateService 交易汇率服务
     * @param transactionIdempotencyService 交易幂等服务
     * @param transactionEventOutboxService 交易本地事件服务
     * @param transactionRecordService 交易事实记录服务
     * @param transactionStateMachineService 交易状态机服务
     * @param channelStatusResolver 渠道状态解析服务
     * @param distributedLockService 分布式锁服务
     * @param redisProperties Redis 物理 Key 配置
     */
    @Autowired
    public PaymentTransactionServiceImpl(IsoDictionaryService isoDictionaryService,
                                         PaymentRiskInvokeService paymentRiskInvokeService,
                                         PaymentChannelRouteService paymentChannelRouteService,
                                         PaymentChannelInvokeService paymentChannelInvokeService,
                                         PaymentTransactionPreparationService paymentTransactionPreparationService,
                                         PaymentChannelResultTransactionService paymentChannelResultTransactionService,
                                         CaptureTransactionPreparationService captureTransactionPreparationService,
                                         CaptureChannelResultTransactionService captureChannelResultTransactionService,
                                         RefundTransactionPreparationService refundTransactionPreparationService,
                                         RefundChannelResultTransactionService refundChannelResultTransactionService,
                                         VoidTransactionPreparationService voidTransactionPreparationService,
                                         VoidChannelResultTransactionService voidChannelResultTransactionService,
                                         IncrementalAuthorizationTransactionPreparationService incrementalAuthorizationTransactionPreparationService,
                                         IncrementalAuthorizationChannelResultTransactionService incrementalAuthorizationChannelResultTransactionService,
                                         PaymentExchangeRateService paymentExchangeRateService,
                                         TransactionIdempotencyService transactionIdempotencyService,
                                         TransactionEventOutboxService transactionEventOutboxService,
                                         TransactionRecordService transactionRecordService,
                                         TransactionStateMachineService transactionStateMachineService,
                                         ChannelTransactionStatusResolver channelStatusResolver,
                                         DistributedLockService distributedLockService,
                                         PaymentRedisProperties redisProperties) {
        this.isoDictionaryService = isoDictionaryService;
        this.paymentRiskInvokeService = paymentRiskInvokeService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.paymentChannelInvokeService = paymentChannelInvokeService;
        this.paymentExchangeRateService = paymentExchangeRateService;
        this.transactionIdempotencyService = transactionIdempotencyService;
        this.transactionEventOutboxService = transactionEventOutboxService;
        this.transactionRecordService = transactionRecordService;
        TransactionLifecycleEventService lifecycleEventService =
                new DefaultTransactionLifecycleEventService(transactionEventOutboxService);
        this.paymentTransactionPreparationService = paymentTransactionPreparationService == null
                ? new DefaultPaymentTransactionPreparationService(
                isoDictionaryService,
                paymentRiskInvokeService,
                paymentChannelRouteService,
                paymentExchangeRateService,
                transactionIdempotencyService,
                transactionEventOutboxService,
                transactionRecordService)
                : paymentTransactionPreparationService;
        this.paymentChannelResultTransactionService = paymentChannelResultTransactionService == null
                ? new DefaultPaymentChannelResultTransactionService(
                        transactionRecordService, lifecycleEventService)
                : paymentChannelResultTransactionService;
        this.captureTransactionPreparationService = captureTransactionPreparationService == null
                ? new DefaultCaptureTransactionPreparationService(
                isoDictionaryService,
                paymentChannelRouteService,
                transactionIdempotencyService,
                transactionEventOutboxService,
                paymentExchangeRateService,
                transactionRecordService,
                transactionStateMachineService)
                : captureTransactionPreparationService;
        this.captureChannelResultTransactionService = captureChannelResultTransactionService == null
                ? new DefaultCaptureChannelResultTransactionService(
                        transactionRecordService, lifecycleEventService)
                : captureChannelResultTransactionService;
        this.refundTransactionPreparationService = refundTransactionPreparationService == null
                ? new DefaultRefundTransactionPreparationService(
                isoDictionaryService,
                paymentChannelRouteService,
                transactionIdempotencyService,
                transactionEventOutboxService,
                transactionRecordService,
                transactionStateMachineService)
                : refundTransactionPreparationService;
        this.refundChannelResultTransactionService = refundChannelResultTransactionService == null
                ? new DefaultRefundChannelResultTransactionService(
                        transactionRecordService, lifecycleEventService)
                : refundChannelResultTransactionService;
        this.voidTransactionPreparationService = voidTransactionPreparationService == null
                ? new DefaultVoidTransactionPreparationService(
                isoDictionaryService,
                paymentChannelRouteService,
                transactionIdempotencyService,
                transactionEventOutboxService,
                transactionRecordService,
                transactionStateMachineService)
                : voidTransactionPreparationService;
        this.voidChannelResultTransactionService = voidChannelResultTransactionService == null
                ? new DefaultVoidChannelResultTransactionService(
                        transactionRecordService, lifecycleEventService)
                : voidChannelResultTransactionService;
        this.incrementalAuthorizationTransactionPreparationService = incrementalAuthorizationTransactionPreparationService == null
                ? new DefaultIncrementalAuthorizationTransactionPreparationService(
                isoDictionaryService,
                paymentChannelRouteService,
                transactionIdempotencyService,
                transactionEventOutboxService,
                paymentExchangeRateService,
                transactionRecordService,
                transactionStateMachineService)
                : incrementalAuthorizationTransactionPreparationService;
        this.incrementalAuthorizationChannelResultTransactionService = incrementalAuthorizationChannelResultTransactionService == null
                ? new DefaultIncrementalAuthorizationChannelResultTransactionService(
                        transactionRecordService, lifecycleEventService)
                : incrementalAuthorizationChannelResultTransactionService;
        this.transactionStateMachineService = transactionStateMachineService;
        this.channelStatusResolver = channelStatusResolver == null
                ? new DefaultChannelTransactionStatusResolver()
                : channelStatusResolver;
        this.distributedLockService = Objects.requireNonNull(
                distributedLockService, "distributedLockService");
        this.redisProperties = redisProperties == null ? new PaymentRedisProperties() : redisProperties;
    }

    /**
     * 创建一步支付交易；当前骨架实现生成生命周期主标识和交易动作标识，返回渠道同步状态并发布交易事件。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    @Override
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
    public PaymentCreateResultDTO createIncrementalAuthorization(PaymentCreateCommandDTO commandDTO) {
        return createIncrementalAuthorizationTransaction(commandDTO);
    }

    /**
     * 发起请款交易。
     * <p>
     * 请款先在本地准备事务中提交动作事实、幂等和渠道请求 INIT，再在事务外调用渠道，最后用独立结果事务 CAS 推进状态。
     *
     * @param commandDTO 请款命令
     * @return 请款结果
     */
    @Override
    public PaymentCreateResultDTO capture(PaymentCreateCommandDTO commandDTO) {
        return createCaptureTransaction(commandDTO, PaymentTransactionTypeEnum.CAPTURE);
    }

    /**
     * 发起预授权完成交易。
     * <p>
     * 预授权完成与请款共享渠道 Capture 能力，但平台动作类型、幂等记录和商户响应保留 PRE_AUTH_COMPLETION。
     *
     * @param commandDTO 预授权完成命令
     * @return 预授权完成结果
     */
    @Override
    public PaymentCreateResultDTO preAuthCompletion(PaymentCreateCommandDTO commandDTO) {
        return createCaptureTransaction(commandDTO, PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION);
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
    public PaymentCreateResultDTO refund(PaymentCreateCommandDTO commandDTO) {
        return createRefundTransaction(commandDTO);
    }

    /**
     * 发起撤销交易。
     * <p>
     * 撤销先在本地准备事务中提交动作事实、幂等和渠道请求 INIT，再在事务外调用渠道，最后用独立结果事务 CAS 推进状态。
     *
     * @param commandDTO 撤销命令
     * @return 撤销结果
     */
    @Override
    public PaymentCreateResultDTO voidPayment(PaymentCreateCommandDTO commandDTO) {
        return createVoidTransaction(commandDTO);
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
        LocalDateTime requestedTransactionDateTime = commandDTO.getTransactionInfo().getSourceTransactionDateTime();
        LocalDateTime rootTransactionDateTime = commandDTO.getTransactionInfo().getRootTransactionDateTime();
        List<TransactionOperationDO> operations = transactionRecordService.findOperationsByMerchantOrder(
                commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo(), requestedTransactionId,
                requestedTransactionDateTime, rootTransactionDateTime);
        if (operations.isEmpty()) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderDO sourceOrderDO = resolveOrderForQuery(operations.get(0), rootTransactionDateTime);
        if (!Objects.equals(commandDTO.getMerchantId(), sourceOrderDO.getMerchantId())
                || !Objects.equals(commandDTO.getMerchantOrderNo(), sourceOrderDO.getMerchantOrderNo())) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        PaymentQueryResultDTO resultDTO = new PaymentQueryResultDTO();
        resultDTO.setMerchantId(commandDTO.getMerchantId());
        resultDTO.setMerchantOrderNo(sourceOrderDO.getMerchantOrderNo());
        resultDTO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        fillQueryOrderSummary(resultDTO, sourceOrderDO);
        Map<String, TransactionPaymentMethodInfoDO> paymentMethodInfos =
                transactionRecordService.findPaymentMethodInfos(operations);
        resultDTO.setTransactionInfo(operations.stream()
                .map(operationDO -> toQueryTransactionInfo(
                        operationDO,
                        sourceOrderDO,
                        operations,
                        paymentMethodInfos == null ? null : paymentMethodInfos.get(operationDO.getTransactionId())))
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
        long startNanos = System.nanoTime();
        PaymentInitialPreparationResultDTO preparationResultDTO = prepareTransaction(commandDTO, transactionTypeEnum);
        if (preparationResultDTO.isDuplicate() || !preparationResultDTO.isCallChannel()) {
            logPaymentEnd("PAYMENT_TRANSACTION_END", commandDTO, preparationResultDTO.getResultDTO(), startNanos);
            return preparationResultDTO.getResultDTO();
        }
        PaymentCreateResultDTO resultDTO = submitPreparedTransaction(preparationResultDTO);
        logPaymentEnd("PAYMENT_TRANSACTION_END", preparationResultDTO.getCommandDTO(), resultDTO, startNanos);
        return resultDTO;
    }

    /**
     * 准备支付交易，在执行外部动作前冻结幂等、路由、费用、渠道请求和恢复事实。
     *
     * @param commandDTO 已通过 OpenAPI 安全链路转换的支付命令
     * @return 已提交的本地准备结果；是否允许外呼由 {@code callChannel} 决定
     */
    @Override
    public PaymentInitialPreparationResultDTO preparePayment(PaymentCreateCommandDTO commandDTO) {
        return prepareTransaction(commandDTO, PaymentTransactionTypeEnum.PAYMENT);
    }

    /**
     * 准备授权交易，在执行外部动作前冻结幂等、路由、费用、渠道请求和恢复事实。
     *
     * @param commandDTO 已通过 OpenAPI 安全链路转换的授权命令
     * @return 已提交的本地准备结果；是否允许外呼由 {@code callChannel} 决定
     */
    @Override
    public PaymentInitialPreparationResultDTO prepareAuthorization(PaymentCreateCommandDTO commandDTO) {
        return prepareTransaction(commandDTO, PaymentTransactionTypeEnum.AUTHORIZATION);
    }

    /**
     * 使用现有分布式准备锁和数据库幂等表提交首次交易事实，但不调用 PSP。
     */
    private PaymentInitialPreparationResultDTO prepareTransaction(PaymentCreateCommandDTO commandDTO,
                                                                  PaymentTransactionTypeEnum transactionTypeEnum) {
        if (commandDTO != null) {
            commandDTO.setTransactionType(transactionTypeEnum.getCode());
        }
        validateCreateCommand(commandDTO);
        String transactionType = resolveTransactionType(commandDTO);
        String idempotencyKey = transactionIdempotencyService.buildTransactionOperationKey(
                commandDTO.getMerchantId(), commandDTO.getMerchantOrderId(), transactionType);
        log.info("支付交易受理开始, event: PAYMENT_TRANSACTION_START, stage: ACCEPT, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionType: {}, paymentMethod: {}, currency: {}, amount: {}, idempotencyKey: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                transactionType,
                commandDTO.getPaymentMethod(),
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
                idempotencyKey);
        logPaymentRequestContext(commandDTO);
        String flowLockKey = merchantOrderFlowLockKey(commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo());
        String operationLockKey = transactionOperationLockKey(idempotencyKey);
        // 先锁具体动作幂等键，再锁商户订单生命周期；固定顺序避免同一订单并发动作形成锁顺序反转。
        // Redis 锁只用于削峰，最终幂等仍由准备事务中的持久化幂等记录和条件写保护。
        boolean operationLocked = tryLock(operationLockKey);
        boolean flowLocked = false;
        if (!operationLocked) {
            log.warn("支付动作幂等锁被占用, event: PAYMENT_IDEMPOTENCY_LOCK_BUSY, stage: OPERATION_LOCK, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionType: {}, idempotencyKey: {}",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    transactionType,
                    idempotencyKey);
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(record -> PaymentInitialPreparationResultDTO.duplicate(toDuplicateResult(record)))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.NETWORK_BUSY));
        }
        PaymentInitialPreparationResultDTO preparationResultDTO;
        try {
            flowLocked = tryLock(flowLockKey);
            if (!flowLocked) {
                log.warn("支付订单生命周期锁被占用, event: PAYMENT_IDEMPOTENCY_LOCK_BUSY, stage: FLOW_LOCK, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionType: {}, idempotencyKey: {}",
                        TraceContext.getTraceId(),
                        commandDTO.getMerchantId(),
                        commandDTO.getMerchantOrderNo(),
                        transactionType,
                        idempotencyKey);
                return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                        .map(record -> PaymentInitialPreparationResultDTO.duplicate(toDuplicateResult(record)))
                        .orElseThrow(() -> new ServiceException(ApiResultEnum.NETWORK_BUSY));
            }
            // 准备事务一次性提交动作事实、渠道请求 INIT、幂等记录和出站事实；此处不进行网络调用。
            preparationResultDTO = paymentTransactionPreparationService.prepareInitialTransaction(
                    commandDTO, transactionType);
        } finally {
            unlockPreparationLock(flowLockKey, flowLocked);
            unlockPreparationLock(operationLockKey, operationLocked);
        }
        if (preparationResultDTO.isDuplicate()) {
            return preparationResultDTO;
        }
        logRouteDecision(commandDTO, preparationResultDTO.getRouteResultDTO(), preparationResultDTO.getResultDTO());
        log.info("支付交易本地准备完成, event: PAYMENT_TRANSACTION_PREPARED, stage: LOCAL_PREPARE, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, callChannel: {}, riskDecision: {}, channelCode: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                preparationResultDTO.getResultDTO() == null ? null : preparationResultDTO.getResultDTO().getTransactionId(),
                preparationResultDTO.getResultDTO() == null ? null : preparationResultDTO.getResultDTO().getOperationId(),
                preparationResultDTO.isCallChannel(),
                preparationResultDTO.getRiskDecisionEnum(),
                preparationResultDTO.getRouteResultDTO() == null ? null : preparationResultDTO.getRouteResultDTO().getChannelCode());
        return preparationResultDTO;
    }

    /**
     * 提交已经落库的首次交易渠道请求。
     *
     * <p>先通过数据库条件更新抢占唯一发送权，再在事务外执行网络调用；结果由独立事务以 CAS 推进。</p>
     */
    @Override
    public PaymentCreateResultDTO submitPreparedTransaction(PaymentInitialPreparationResultDTO preparationResultDTO) {
        if (preparationResultDTO == null || preparationResultDTO.getResultDTO() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "prepared transaction is required");
        }
        if (preparationResultDTO.isDuplicate() || !preparationResultDTO.isCallChannel()
                || isTerminal(preparationResultDTO.getResultDTO())) {
            return preparationResultDTO.getResultDTO();
        }
        PaymentPreparedChannelRequestDTO preparedRequest = preparationResultDTO.getPreparedChannelRequestDTO();
        if (preparedRequest == null || !StringUtils.hasText(preparedRequest.getRequestId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "prepared channel request is required");
        }
        // 通过数据库条件更新抢占唯一提交权，避免浏览器回跳、恢复任务和原请求同时重复请求渠道。
        if (!paymentChannelResultTransactionService.claimInitialChannelSubmission(
                preparedRequest.getRequestId(), preparationResultDTO.getCommandDTO().getTransactionDateTime())) {
            return latestPreparedResult(preparationResultDTO);
        }
        // 渠道网络调用必须位于本地事务之外，避免长事务持锁；超时或异常由 invoke 结果标记为待确认。
        PaymentChannelInvokeResultDTO invokeResultDTO = invokeChannelSafely(
                preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getRouteResultDTO(),
                preparationResultDTO.getResultDTO().getOperationId(),
                preparationResultDTO.getResultDTO().getTransactionId(),
                preparationResultDTO.getPreparedChannelRequestDTO());
        ChannelPaymentResponse channelResponse = invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse();
        PaymentCreateResultDTO resultDTO = preparationResultDTO.getResultDTO();
        fillChannelResult(resultDTO, invokeResultDTO, channelResponse);
        enrichResult(preparationResultDTO.getCommandDTO(), preparationResultDTO.getRouteResultDTO(), channelResponse, resultDTO);
        // 独立结果事务以版本 CAS 推进状态；只有 CAS 胜出方可以创建后续金额、通知和事件事实。
        boolean statusChanged = paymentChannelResultTransactionService.recordInitialChannelResult(
                preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getRouteResultDTO(),
                invokeResultDTO,
                resultDTO,
                preparationResultDTO.getRiskDecisionEnum(),
                preparationResultDTO.getCurrencyExponent());
        resultDTO = reconcilePersistedResultAfterCasConflict(
                resultDTO, preparationResultDTO.getCommandDTO(), statusChanged);
        completeIdempotency(preparationResultDTO.getIdempotencyKey(), preparationResultDTO.getCommandDTO(), resultDTO);
        return resultDTO;
    }

    /**
     * 使用持久化的交易号和请求号恢复未发送的准备结果，不生成新的幂等或渠道身份。
     */
    @Override
    public PaymentCreateResultDTO resumePreparedTransaction(PaymentCreateCommandDTO commandDTO) {
        validatePreparedIdentity(commandDTO);
        return submitPreparedTransaction(restorePreparedTransaction(commandDTO));
    }

    /**
     * 将确认未发送到渠道的准备结果收口为失败。
     *
     * <p>不得用于超时或发送结果未知的请求，否则重试可能造成重复扣款。</p>
     */
    @Override
    public PaymentCreateResultDTO failPreparedTransaction(PaymentCreateCommandDTO commandDTO,
                                                          String failureCode,
                                                          String failureMessage) {
        validatePreparedIdentity(commandDTO);
        PaymentInitialPreparationResultDTO preparation = restorePreparedTransaction(commandDTO);
        PaymentCreateResultDTO resultDTO = preparation.getResultDTO();
        if (isTerminal(resultDTO)) {
            return resultDTO;
        }
        resultDTO.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        resultDTO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        resultDTO.setFailReasonCode(StringUtils.hasText(failureCode)
                ? failureCode : PaymentFailureReasonEnum.CHANNEL_REQUEST_NOT_SENT.getCode());
        resultDTO.setFailReasonMessage(failureMessage);
        PaymentOpenApiResultSupport.applyMerchantProjection(resultDTO);
        PaymentChannelInvokeResultDTO preChannelFailure = preparedFailureInvokeResult(
                preparation.getPreparedChannelRequestDTO(), preparation.getCommandDTO(),
                preparation.getRouteResultDTO(), resultDTO.getOperationId(), resultDTO.getTransactionId(),
                resultDTO.getFailReasonCode(), failureMessage);
        boolean statusChanged = paymentChannelResultTransactionService.recordInitialPreChannelFailure(
                preparation.getCommandDTO(),
                preparation.getRouteResultDTO(),
                preChannelFailure,
                resultDTO,
                preparation.getRiskDecisionEnum(),
                preparation.getCurrencyExponent());
        if (!statusChanged) {
            return latestPreparedResult(preparation);
        }
        completeIdempotency(preparation.getIdempotencyKey(), preparation.getCommandDTO(), resultDTO);
        return resultDTO;
    }

    /**
     * 在交易认证信息中记录 3DS 指示值，不改变交易金额和币种。
     * @param transactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param indicator 渠道或认证流程给出的 3DS 执行指示；空值按 REQUIRED 处理
     */
    @Override
    public void markThreeDsIndicator(String transactionId,
                                     LocalDateTime transactionDateTime,
                                     String indicator) {
        paymentChannelResultTransactionService.markThreeDsIndicator(
                transactionId, transactionDateTime,
                StringUtils.hasText(indicator) ? indicator : "REQUIRED");
    }

    /**
     * 从动作单、主单和原渠道请求恢复浏览器回跳后的提交上下文。
     */
    private PaymentInitialPreparationResultDTO restorePreparedTransaction(PaymentCreateCommandDTO commandDTO) {
        // 恢复路径只从主库权威事实重建身份，禁止相信浏览器回跳或重试请求携带的金额、币种和渠道标识。
        TransactionOperationDO operationDO = transactionRecordService.findSourceOperationByTransactionIdPrimary(
                commandDTO.getTransactionId(), commandDTO.getTransactionDateTime());
        if (operationDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderDO orderDO = transactionRecordService.findOrderPrimary(
                operationDO.getTransactionDateTime(), operationDO.getOperationId());
        com.scott.payment.payment.entity.TransactionChannelRequestDO requestDO =
                transactionRecordService.findOriginalChannelRequestForQueryPrimary(operationDO);
        if (orderDO == null || requestDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND.getCode(), "prepared transaction can not be restored");
        }
        commandDTO.setTransactionType(operationDO.getTransactionType());
        commandDTO.setMerchantId(operationDO.getMerchantId());
        commandDTO.setMerchantOrderNo(operationDO.getMerchantOrderNo());
        commandDTO.setMerchantOrderId(orderDO.getMerchantOrderId());
        commandDTO.setPaymentMethod(orderDO.getPaymentMethod());
        commandDTO.setAmount(operationDO.getLabelAmount());
        commandDTO.setCurrency(operationDO.getLabelCurrency());
        commandDTO.setLabelAmount(operationDO.getLabelAmount());
        commandDTO.setLabelCurrency(operationDO.getLabelCurrency());
        commandDTO.setTransactionAmount(operationDO.getTransactionAmount());
        commandDTO.setTransactionCurrency(operationDO.getTransactionCurrency());
        commandDTO.setTransactionRate(operationDO.getTransactionRate());
        commandDTO.setDccEnabled(operationDO.getDccEnabled());
        commandDTO.setEdcEnabled(operationDO.getEdcEnabled());
        PaymentCreateCommandDTO.ChannelIdentityDTO identity = commandDTO.getChannelIdentity();
        if (identity == null) {
            identity = new PaymentCreateCommandDTO.ChannelIdentityDTO();
            commandDTO.setChannelIdentity(identity);
        }
        identity.setChannelCode(operationDO.getChannelCode());
        identity.setChannelId(operationDO.getChannelId());
        identity.setChannelMidConfigId(operationDO.getChannelMidConfigId());
        identity.setChannelOrderNo(requestDO.getChannelOrderNo());
        identity.setChannelTransactionId(requestDO.getChannelTransactionId());

        PaymentRouteResultDTO route = OriginalTransactionRouteResolver.restore(
                paymentChannelRouteService, orderDO, operationDO);
        PaymentPreparedChannelRequestDTO preparedRequest = new PaymentPreparedChannelRequestDTO();
        preparedRequest.setRequestId(requestDO.getRequestId());
        preparedRequest.setChannelOrderNo(requestDO.getChannelOrderNo());
        preparedRequest.setChannelTransactionId(requestDO.getChannelTransactionId());
        PaymentCreateResultDTO result = resultFromPreparedFacts(commandDTO, operationDO, orderDO);
        PaymentInitialPreparationResultDTO preparation = new PaymentInitialPreparationResultDTO();
        preparation.setCallChannel(!isTerminal(result));
        preparation.setIdempotencyKey(transactionIdempotencyService.buildTransactionOperationKey(
                commandDTO.getMerchantId(), commandDTO.getMerchantOrderId(), commandDTO.getTransactionType()));
        preparation.setCommandDTO(commandDTO);
        preparation.setRouteResultDTO(route);
        preparation.setPreparedChannelRequestDTO(preparedRequest);
        preparation.setResultDTO(result);
        preparation.setRiskDecisionEnum(PaymentRiskDecisionEnum.of(orderDO.getInternalRiskDecision()));
        preparation.setCurrencyExponent(operationDO.getCurrencyExponent() == null ? 0 : operationDO.getCurrencyExponent());
        return preparation;
    }

    /**
     * 构造当前数据库事实对应的首次交易结果。
     */
    private PaymentCreateResultDTO resultFromPreparedFacts(PaymentCreateCommandDTO commandDTO,
                                                            TransactionOperationDO operationDO,
                                                            TransactionOrderDO orderDO) {
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        result.setTransactionId(operationDO.getTransactionId());
        result.setOperationId(operationDO.getOperationId());
        result.setMerchantOrderNo(operationDO.getMerchantOrderNo());
        result.setMerchantOrderId(orderDO.getMerchantOrderId());
        result.setMerchantId(operationDO.getMerchantId());
        result.setTransactionType(operationDO.getTransactionType());
        result.setStatus(operationDO.getTransactionStatus());
        result.setProcessStage(operationDO.getProcessStage());
        result.setFailReasonCode(operationDO.getFailReasonCode());
        result.setFailReasonMessage(operationDO.getFailReasonMessage());
        result.setLabelAmount(operationDO.getLabelAmount());
        result.setLabelCurrency(operationDO.getLabelCurrency());
        result.setTransactionAmount(operationDO.getTransactionAmount());
        result.setTransactionCurrency(operationDO.getTransactionCurrency());
        result.setCurrency(operationDO.getTransactionCurrency());
        result.setAmount(operationDO.getTransactionAmount() == null
                ? null : toMinorAmount(operationDO.getTransactionAmount(), operationDO.getTransactionCurrency()));
        result.setTransactionRate(operationDO.getTransactionRate());
        result.setTransactionDateTime(operationDO.getTransactionDateTime());
        result.setRootTransactionDateTime(orderDO.getTransactionDateTime());
        result.setTransactionTimeZone(orderDO.getTransactionTimeZone());
        result.setPaymentMethod(orderDO.getPaymentMethod());
        result.setPaymentBrand(orderDO.getPaymentBrand());
        enrichMerchantResponse(result);
        return result;
    }

    /**
     * 渠道请求已被其他线程抢占时返回数据库中的最新状态。
     */
    private PaymentCreateResultDTO latestPreparedResult(PaymentInitialPreparationResultDTO preparation) {
        return restorePreparedTransaction(preparation.getCommandDTO()).getResultDTO();
    }

    /**
     * 同步结果 CAS 失败表示回调或其他恢复流程已先提交，此时响应和幂等快照必须改用数据库事实。
     */
    private PaymentCreateResultDTO reconcilePersistedResultAfterCasConflict(PaymentCreateResultDTO resultDTO,
                                                                             PaymentCreateCommandDTO commandDTO,
                                                                             boolean statusChanged) {
        if (statusChanged) {
            return resultDTO;
        }
        // CAS 失败说明另一提交方已经改变状态；同步线程的内存结果必须整体让位于主库事实。
        TransactionOperationDO persistedOperation = readAuthoritativeOperationAfterCasConflict(
                resultDTO.getTransactionId(), commandDTO.getTransactionDateTime());
        if (persistedOperation == null) {
            throw authoritativeResultUnavailable(null);
        }
        TransactionOrderDO persistedOrder = readAuthoritativeOrderAfterCasConflict(
                resultDTO.getRootTransactionDateTime(), persistedOperation.getOperationId());
        if (persistedOrder == null) {
            throw authoritativeResultUnavailable(null);
        }
        return PaymentPersistedResultSupport.overwriteAuthoritativeFields(
                resultDTO, persistedOperation, persistedOrder);
    }

    /**
     * 将 CAS 冲突后的“主库暂未读到”统一收口为可重试错误，避免误报订单不存在。
     */
    private TransactionOperationDO readAuthoritativeOperationAfterCasConflict(
            String transactionId,
            LocalDateTime transactionDateTime) {
        try {
            return transactionRecordService.findSourceOperationByTransactionIdPrimary(
                    transactionId, transactionDateTime);
        } catch (ServiceException exception) {
            if (ApiResultEnum.ORDER_NOT_FOUND.getCode().equals(exception.getCode())) {
                throw authoritativeResultUnavailable(exception);
            }
            throw exception;
        }
    }

    /**
     * 将 CAS 冲突后的“主库暂未读到”统一收口为可重试错误，避免误报订单不存在。
     */
    private TransactionOrderDO readAuthoritativeOrderAfterCasConflict(
            LocalDateTime transactionDateTime,
            String operationId) {
        try {
            return transactionRecordService.findOrderPrimary(transactionDateTime, operationId);
        } catch (ServiceException exception) {
            if (ApiResultEnum.ORDER_NOT_FOUND.getCode().equals(exception.getCode())) {
                throw authoritativeResultUnavailable(exception);
            }
            throw exception;
        }
    }

    /**
     * CAS 已确认输给并发提交方后禁止回退同步旧结果；调用方可按原幂等键安全重试。
     */
    private ServiceException authoritativeResultUnavailable(Throwable cause) {
        return new ServiceException(ApiResultEnum.NETWORK_BUSY, cause);
    }

    /**
     * 构造 3DS 等资金请求前失败的本地结果上下文，不包含伪造的渠道响应。
     */
    private PaymentChannelInvokeResultDTO preparedFailureInvokeResult(PaymentPreparedChannelRequestDTO prepared,
                                                                       PaymentCreateCommandDTO command,
                                                                       PaymentRouteResultDTO route,
                                                                       String operationId,
                                                                       String transactionId,
                                                                       String failureCode,
                                                                       String failureMessage) {
        com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest request =
                new com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest();
        request.setChannelCode(route.getChannelCode());
        request.setOperationId(operationId);
        request.setTransactionId(transactionId);
        request.setMerchantId(command.getMerchantId());
        request.setMerchantOrderNo(command.getMerchantOrderNo());
        request.setMerchantOrderId(command.getMerchantOrderId());
        request.setTransactionType(command.getTransactionType());
        request.setPaymentMethod(command.getPaymentMethod());
        request.setAmount(command.getTransactionAmount());
        request.setCurrency(command.getTransactionCurrency());
        request.setTransactionDateTime(command.getTransactionDateTime());
        request.setChannelOrderNo(prepared.getChannelOrderNo());
        request.setChannelTransactionId(prepared.getChannelTransactionId());
        PaymentChannelInvokeResultDTO invokeResult = new PaymentChannelInvokeResultDTO();
        invokeResult.setRequestId(prepared.getRequestId());
        invokeResult.setChannelRequest(request);
        invokeResult.setRequestStatus("FAILED");
        invokeResult.setRequestScene(command.getTransactionType());
        invokeResult.setExceptionType(failureCode);
        invokeResult.setExceptionMessage(failureMessage);
        invokeResult.setOutcomeUncertain(false);
        invokeResult.setResponseTime(LocalDateTime.now());
        invokeResult.setDurationMillis(0);
        return invokeResult;
    }

    /**
     * 校验收银台回跳携带的已准备交易身份。
     */
    private void validatePreparedIdentity(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO == null
                || !StringUtils.hasText(commandDTO.getTransactionId())
                || commandDTO.getTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "prepared transaction identity is required");
        }
        if (Boolean.TRUE.equals(commandDTO.getThreeDsRequired())
                && (commandDTO.getThreeDsInfo() == null
                || !"PASSED".equals(commandDTO.getThreeDsInfo().getAuthenticationStatus()))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "3DS authentication has not passed");
        }
    }

    /**
     * 创建请款或预授权完成交易。
     *
     * <p>短锁只合并同动作并发，准备事务负责持久化幂等与额度校验，渠道调用在事务外执行。</p>
     */
    private PaymentCreateResultDTO createCaptureTransaction(PaymentCreateCommandDTO commandDTO,
                                                            PaymentTransactionTypeEnum transactionType) {
        if (commandDTO != null) {
            commandDTO.setTransactionType(transactionType.getCode());
        }
        validateFollowUpCommand(commandDTO, transactionType);
        long startNanos = System.nanoTime();
        String idempotencyKey = buildFollowUpIdempotencyKey(commandDTO, transactionType);
        log.info("支付后续动作受理开始, event: PAYMENT_FOLLOW_UP_START, stage: ACCEPT, traceId: {}, merchantId: {}, sourceTransactionId: {}, transactionType: {}, currency: {}, amount: {}, idempotencyKey: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getSourceTransactionId(),
                transactionType.getCode(),
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
                idempotencyKey);
        logPaymentRequestContext(commandDTO);
        String operationLockKey = transactionOperationLockKey(idempotencyKey);
        // 同一请款动作先用短锁合并并发请求；准备服务中的持久化幂等记录仍是最终保护。
        boolean locked = tryLock(operationLockKey);
        if (!locked) {
            commandDTO.setRequestFingerprint(resolveCaptureLikeRequestFingerprint(commandDTO, transactionType));
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(record -> resolveDuplicateFollowUp(commandDTO, record))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.NETWORK_BUSY));
        }
        CapturePreparationResultDTO preparationResultDTO;
        try {
            preparationResultDTO = captureTransactionPreparationService.prepareCapture(
                    commandDTO, idempotencyKey, transactionType);
        } finally {
            unlockPreparationLock(operationLockKey, locked);
        }
        if (preparationResultDTO.isDuplicate()) {
            logPaymentEnd("PAYMENT_FOLLOW_UP_END", commandDTO, preparationResultDTO.getResultDTO(), startNanos);
            return preparationResultDTO.getResultDTO();
        }
        if (!preparationResultDTO.isCallChannel()) {
            logPaymentEnd("PAYMENT_FOLLOW_UP_END", preparationResultDTO.getCommandDTO(), preparationResultDTO.getResultDTO(), startNanos);
            return preparationResultDTO.getResultDTO();
        }
        // 准备事务已提交后才调用渠道，避免外部网络耗时占用数据库事务和原交易行锁。
        PaymentChannelInvokeResultDTO invokeResultDTO = invokeChannelSafely(
                preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getRouteResultDTO(),
                preparationResultDTO.getResultDTO().getOperationId(),
                preparationResultDTO.getResultDTO().getTransactionId(),
                preparationResultDTO.getPreparedChannelRequestDTO());
        ChannelPaymentResponse channelResponse = invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse();
        PaymentCreateResultDTO resultDTO = preparationResultDTO.getResultDTO();
        fillChannelResult(resultDTO, invokeResultDTO, channelResponse);
        enrichFollowUpResult(preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getSourceOrderDO(),
                preparationResultDTO.getRouteResultDTO(),
                channelResponse,
                resultDTO);
        // 结果事务以动作版本 CAS 更新请款终态和累计请款金额，确保并发回调只生效一次。
        boolean statusChanged = captureChannelResultTransactionService.recordCaptureChannelResult(
                preparationResultDTO, invokeResultDTO);
        resultDTO = reconcilePersistedResultAfterCasConflict(
                resultDTO, preparationResultDTO.getCommandDTO(), statusChanged);
        completeIdempotency(preparationResultDTO.getIdempotencyKey(), preparationResultDTO.getCommandDTO(), resultDTO);
        logPaymentEnd("PAYMENT_FOLLOW_UP_END", preparationResultDTO.getCommandDTO(), resultDTO, startNanos);
        return resultDTO;
    }

    /**
     * 创建退款交易。
     *
     * <p>准备阶段在原交易锁内预占可退余额；若命中审批策略，只提交审批事实，不立即调用渠道。</p>
     */
    private PaymentCreateResultDTO createRefundTransaction(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO != null) {
            commandDTO.setTransactionType(PaymentTransactionTypeEnum.REFUND.getCode());
        }
        validateFollowUpCommand(commandDTO, PaymentTransactionTypeEnum.REFUND);
        long startNanos = System.nanoTime();
        String idempotencyKey = buildFollowUpIdempotencyKey(commandDTO, PaymentTransactionTypeEnum.REFUND);
        log.info("支付后续动作受理开始, event: PAYMENT_FOLLOW_UP_START, stage: ACCEPT, traceId: {}, merchantId: {}, sourceTransactionId: {}, transactionType: {}, currency: {}, amount: {}, idempotencyKey: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getSourceTransactionId(),
                PaymentTransactionTypeEnum.REFUND.getCode(),
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
                idempotencyKey);
        logPaymentRequestContext(commandDTO);
        String operationLockKey = transactionOperationLockKey(idempotencyKey);
        // 同一退款动作先用短锁合并并发请求；数据库幂等记录和累计可退金额条件才是最终保护。
        boolean locked = tryLock(operationLockKey);
        if (!locked) {
            commandDTO.setRequestFingerprint(resolveRefundRequestFingerprint(commandDTO));
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(record -> resolveDuplicateFollowUp(commandDTO, record))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.NETWORK_BUSY));
        }
        RefundPreparationResultDTO preparationResultDTO;
        try {
            preparationResultDTO = refundTransactionPreparationService.prepareRefund(commandDTO, idempotencyKey);
        } finally {
            unlockPreparationLock(operationLockKey, locked);
        }
        if (preparationResultDTO.isDuplicate()) {
            logPaymentEnd("PAYMENT_FOLLOW_UP_END", commandDTO, preparationResultDTO.getResultDTO(), startNanos);
            return preparationResultDTO.getResultDTO();
        }
        if (!preparationResultDTO.isCallChannel()) {
            logPaymentEnd("PAYMENT_FOLLOW_UP_END", preparationResultDTO.getCommandDTO(), preparationResultDTO.getResultDTO(), startNanos);
            return preparationResultDTO.getResultDTO();
        }
        // 退款事实和渠道请求 INIT 已提交，随后在事务外执行真实渠道调用。
        PaymentChannelInvokeResultDTO invokeResultDTO = invokeChannelSafely(
                preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getRouteResultDTO(),
                preparationResultDTO.getResultDTO().getOperationId(),
                preparationResultDTO.getResultDTO().getTransactionId(),
                preparationResultDTO.getPreparedChannelRequestDTO());
        ChannelPaymentResponse channelResponse = invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse();
        PaymentCreateResultDTO resultDTO = preparationResultDTO.getResultDTO();
        fillChannelResult(resultDTO, invokeResultDTO, channelResponse);
        enrichFollowUpResult(preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getSourceOrderDO(),
                preparationResultDTO.getRouteResultDTO(),
                channelResponse,
                resultDTO);
        // 结果事务以动作版本 CAS 更新退款终态和累计退款金额，避免重复退款回调重复累计。
        boolean statusChanged = refundChannelResultTransactionService.recordRefundChannelResult(
                preparationResultDTO, invokeResultDTO);
        resultDTO = reconcilePersistedResultAfterCasConflict(
                resultDTO, preparationResultDTO.getCommandDTO(), statusChanged);
        completeIdempotency(preparationResultDTO.getIdempotencyKey(), preparationResultDTO.getCommandDTO(), resultDTO);
        logPaymentEnd("PAYMENT_FOLLOW_UP_END", preparationResultDTO.getCommandDTO(), resultDTO, startNanos);
        return resultDTO;
    }

    /**
     * 创建撤销或授权取消交易。
     *
     * <p>准备阶段拒绝与请款、退款和增量授权的未决竞争，结果事务使用 CAS 防止迟到响应覆盖终态。</p>
     */
    private PaymentCreateResultDTO createVoidTransaction(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO != null) {
            commandDTO.setTransactionType(PaymentTransactionTypeEnum.VOID.getCode());
        }
        validateFollowUpCommand(commandDTO, PaymentTransactionTypeEnum.VOID);
        long startNanos = System.nanoTime();
        String idempotencyKey = buildFollowUpIdempotencyKey(commandDTO, PaymentTransactionTypeEnum.VOID);
        log.info("支付后续动作受理开始, event: PAYMENT_FOLLOW_UP_START, stage: ACCEPT, traceId: {}, merchantId: {}, sourceTransactionId: {}, transactionType: {}, currency: {}, amount: {}, idempotencyKey: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getSourceTransactionId(),
                PaymentTransactionTypeEnum.VOID.getCode(),
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
                idempotencyKey);
        logPaymentRequestContext(commandDTO);
        String operationLockKey = transactionOperationLockKey(idempotencyKey);
        // 撤销请求先按动作幂等键串行化，持久化准备事务负责校验原交易状态和可撤销金额。
        boolean locked = tryLock(operationLockKey);
        if (!locked) {
            commandDTO.setRequestFingerprint(resolveVoidRequestFingerprint(commandDTO));
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(record -> resolveDuplicateFollowUp(commandDTO, record))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.NETWORK_BUSY));
        }
        VoidPreparationResultDTO preparationResultDTO;
        try {
            preparationResultDTO = voidTransactionPreparationService.prepareVoid(commandDTO, idempotencyKey);
        } finally {
            unlockPreparationLock(operationLockKey, locked);
        }
        if (preparationResultDTO.isDuplicate()) {
            logPaymentEnd("PAYMENT_FOLLOW_UP_END", commandDTO, preparationResultDTO.getResultDTO(), startNanos);
            return preparationResultDTO.getResultDTO();
        }
        if (!preparationResultDTO.isCallChannel()) {
            logPaymentEnd("PAYMENT_FOLLOW_UP_END", preparationResultDTO.getCommandDTO(), preparationResultDTO.getResultDTO(), startNanos);
            return preparationResultDTO.getResultDTO();
        }
        // 本地撤销事实提交后再进行渠道调用，超时结果保持待确认并等待回调或查询勾兑。
        PaymentChannelInvokeResultDTO invokeResultDTO = invokeChannelSafely(
                preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getRouteResultDTO(),
                preparationResultDTO.getResultDTO().getOperationId(),
                preparationResultDTO.getResultDTO().getTransactionId(),
                preparationResultDTO.getPreparedChannelRequestDTO());
        ChannelPaymentResponse channelResponse = invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse();
        PaymentCreateResultDTO resultDTO = preparationResultDTO.getResultDTO();
        fillChannelResult(resultDTO, invokeResultDTO, channelResponse);
        enrichFollowUpResult(preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getSourceOrderDO(),
                preparationResultDTO.getRouteResultDTO(),
                channelResponse,
                resultDTO);
        // 结果事务用 CAS 同时保护撤销动作终态和原交易累计字段，迟到响应不能覆盖并发终态。
        boolean statusChanged = voidChannelResultTransactionService.recordVoidChannelResult(
                preparationResultDTO, invokeResultDTO);
        resultDTO = reconcilePersistedResultAfterCasConflict(
                resultDTO, preparationResultDTO.getCommandDTO(), statusChanged);
        completeIdempotency(preparationResultDTO.getIdempotencyKey(), preparationResultDTO.getCommandDTO(), resultDTO);
        logPaymentEnd("PAYMENT_FOLLOW_UP_END", preparationResultDTO.getCommandDTO(), resultDTO, startNanos);
        return resultDTO;
    }

    /**
     * 创建增量授权交易。
     *
     * <p>准备阶段锁定原授权并排除未决资金动作，成功结果才允许累计授权金额。</p>
     */
    private PaymentCreateResultDTO createIncrementalAuthorizationTransaction(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO != null) {
            commandDTO.setTransactionType(PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode());
        }
        validateFollowUpCommand(commandDTO, PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION);
        long startNanos = System.nanoTime();
        String idempotencyKey = buildFollowUpIdempotencyKey(commandDTO, PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION);
        log.info("支付后续动作受理开始, event: PAYMENT_FOLLOW_UP_START, stage: ACCEPT, traceId: {}, merchantId: {}, sourceTransactionId: {}, transactionType: {}, currency: {}, amount: {}, idempotencyKey: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getSourceTransactionId(),
                PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode(),
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
                idempotencyKey);
        logPaymentRequestContext(commandDTO);
        String operationLockKey = transactionOperationLockKey(idempotencyKey);
        // 增量授权按动作幂等键串行化，准备事务继续承担原授权状态和累计金额边界校验。
        boolean locked = tryLock(operationLockKey);
        if (!locked) {
            commandDTO.setRequestFingerprint(resolveIncrementalAuthorizationRequestFingerprint(commandDTO));
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(record -> resolveDuplicateFollowUp(commandDTO, record))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.NETWORK_BUSY));
        }
        IncrementalAuthorizationPreparationResultDTO preparationResultDTO;
        try {
            preparationResultDTO =
                    incrementalAuthorizationTransactionPreparationService.prepareIncrementalAuthorization(commandDTO, idempotencyKey);
        } finally {
            unlockPreparationLock(operationLockKey, locked);
        }
        if (preparationResultDTO.isDuplicate()) {
            logPaymentEnd("PAYMENT_FOLLOW_UP_END", commandDTO, preparationResultDTO.getResultDTO(), startNanos);
            return preparationResultDTO.getResultDTO();
        }
        if (!preparationResultDTO.isCallChannel()) {
            logPaymentEnd("PAYMENT_FOLLOW_UP_END", preparationResultDTO.getCommandDTO(), preparationResultDTO.getResultDTO(), startNanos);
            return preparationResultDTO.getResultDTO();
        }
        // 本地准备已提交，此后渠道调用不占用数据库事务；不确定结果不得直接写失败终态。
        PaymentChannelInvokeResultDTO invokeResultDTO = invokeChannelSafely(
                preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getRouteResultDTO(),
                preparationResultDTO.getResultDTO().getOperationId(),
                preparationResultDTO.getResultDTO().getTransactionId(),
                preparationResultDTO.getPreparedChannelRequestDTO());
        ChannelPaymentResponse channelResponse = invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse();
        PaymentCreateResultDTO resultDTO = preparationResultDTO.getResultDTO();
        fillChannelResult(resultDTO, invokeResultDTO, channelResponse);
        enrichFollowUpResult(preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getSourceOrderDO(),
                preparationResultDTO.getRouteResultDTO(),
                channelResponse,
                resultDTO);
        // 结果事务通过动作版本 CAS 保证增量授权和累计授权金额只推进一次。
        boolean statusChanged = incrementalAuthorizationChannelResultTransactionService.recordIncrementalAuthorizationChannelResult(
                preparationResultDTO, invokeResultDTO);
        resultDTO = reconcilePersistedResultAfterCasConflict(
                resultDTO, preparationResultDTO.getCommandDTO(), statusChanged);
        completeIdempotency(preparationResultDTO.getIdempotencyKey(), preparationResultDTO.getCommandDTO(), resultDTO);
        logPaymentEnd("PAYMENT_FOLLOW_UP_END", preparationResultDTO.getCommandDTO(), resultDTO, startNanos);
        return resultDTO;
    }

    /**
     * 基于原交易权威订单号、币种和本次退款金额生成稳定请求指纹。
     */
    private String resolveRefundRequestFingerprint(PaymentCreateCommandDTO commandDTO) {
        TransactionOrderDO fingerprintSourceOrderDO = transactionRecordService == null ? null : resolveSourceOrder(commandDTO);
        return canonicalFollowUpRequestFingerprint(commandDTO, fingerprintSourceOrderDO, PaymentTransactionTypeEnum.REFUND);
    }

    /**
     * 为请款或预授权完成生成包含原交易、金额和币种的稳定请求指纹。
     */
    private String resolveCaptureLikeRequestFingerprint(PaymentCreateCommandDTO commandDTO,
                                                        PaymentTransactionTypeEnum transactionType) {
        TransactionOrderDO fingerprintSourceOrderDO = transactionRecordService == null ? null : resolveSourceOrder(commandDTO);
        return canonicalFollowUpRequestFingerprint(commandDTO, fingerprintSourceOrderDO, transactionType);
    }

    /**
     * 为撤销生成不依赖可选金额字段的稳定请求指纹，确保同一原交易动作唯一。
     */
    private String resolveVoidRequestFingerprint(PaymentCreateCommandDTO commandDTO) {
        TransactionOrderDO fingerprintSourceOrderDO = transactionRecordService == null ? null : resolveSourceOrder(commandDTO);
        return canonicalVoidRequestFingerprint(commandDTO, fingerprintSourceOrderDO);
    }

    /**
     * 为增量授权生成包含原授权、增额和原币种的稳定请求指纹。
     */
    private String resolveIncrementalAuthorizationRequestFingerprint(PaymentCreateCommandDTO commandDTO) {
        TransactionOrderDO fingerprintSourceOrderDO = transactionRecordService == null ? null : resolveSourceOrder(commandDTO);
        return canonicalFollowUpRequestFingerprint(
                commandDTO, fingerprintSourceOrderDO, PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION);
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
        String idempotencyKey = buildFollowUpIdempotencyKey(commandDTO, transactionTypeEnum);
        TransactionOrderDO fingerprintSourceOrderDO = transactionRecordService == null ? null : resolveSourceOrder(commandDTO);
        commandDTO.setRequestFingerprint(canonicalFollowUpRequestFingerprint(commandDTO, fingerprintSourceOrderDO, transactionTypeEnum));
        return createFollowUpTransactionWithIdempotency(commandDTO, transactionTypeEnum, idempotencyKey);
    }

    /**
     * 在幂等保护下创建请款、退款、撤销、增量授权等后续交易。
     * <p>
     * 后续交易必须先定位原生命周期主单并通过状态机校验，再沿用原渠道订单号发起渠道动作。
     * 渠道请求发送前的确定性失败直接落失败终态；请求可能已送达但结果不确定时，
     * 交易保持 PROCESSING，等待渠道回调或主动查询勾兑，避免误判失败后重复发起资金动作。
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
        sourceOrderDO = lockSourceOrderForCapture(sourceOrderDO, transactionTypeEnum);
        commandDTO.setRequestFingerprint(canonicalFollowUpRequestFingerprint(commandDTO, sourceOrderDO, transactionTypeEnum));
        Optional<TransactionIdempotencyDO> existingRecord = transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey);
        if (existingRecord.isPresent()) {
            return resolveDuplicateFollowUp(commandDTO, existingRecord.get());
        }
        if (transactionStateMachineService == null) {
            throw new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE.getCode(),
                    "transaction state machine is not configured");
        }
        transactionStateMachineService.validateFollowUpAction(sourceOrderDO, transactionTypeEnum, commandDTO.getAmount(), commandDTO.getCurrency());
        validateNoNonTerminalCapture(commandDTO, sourceOrderDO, transactionTypeEnum, LocalDateTime.now());
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
                    .map(record -> resolveDuplicateFollowUp(commandDTO, record))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                            "follow-up idempotency conflict record is missing"));
        }
        TransactionOperationDO sourceOperationDO = transactionRecordService.findSourceOperationByTransactionId(
                commandDTO.getTransactionInfo().getSourceTransactionId(),
                commandDTO.getTransactionInfo().getSourceTransactionDateTime());
        normalizeFollowUpCommand(commandDTO, sourceOrderDO, sourceOperationDO);
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        PaymentCreateResultDTO resultDTO = buildFollowUpResult(commandDTO, sourceOrderDO, transactionId, transactionTypeEnum);
        logGeneratedIdentifiers(commandDTO, resultDTO.getOperationId(), transactionId, idempotencyKey);
        PaymentRouteResultDTO routeResultDTO = paymentChannelRouteService.route(commandDTO);
        logRouteDecision(commandDTO, routeResultDTO, resultDTO);
        PaymentChannelInvokeResultDTO invokeResultDTO = invokeChannelSafely(
                commandDTO, routeResultDTO, sourceOrderDO.getOperationId(), transactionId, resolveChannelOrderNo(commandDTO, sourceOrderDO));
        ChannelPaymentResponse channelResponse = invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse();
        fillChannelResult(resultDTO, invokeResultDTO, channelResponse);
        logStatusMapping(resultDTO, invokeResultDTO, channelResponse);
        int currencyExponent = resolveCurrencyExponent(sourceOrderDO.getTransactionCurrency());
        enrichFollowUpResult(commandDTO, sourceOrderDO, routeResultDTO, channelResponse, resultDTO);
        logMerchantResponseBuilt(commandDTO, resultDTO);
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
            log.warn("支付幂等记录冲突, event: PAYMENT_IDEMPOTENCY_CONFLICT, stage: IDEMPOTENCY_BEGIN, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionType: {}, idempotencyKey: {}",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    transactionType,
                    idempotencyKey);
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(this::toDuplicateResult)
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                            "transaction idempotency conflict record is missing"));
        }
        String operationId = PaymentOrderNoGenerator.nextOrderNo(OPERATION_ID_PREFIX, commandDTO.getTransactionDateTime());
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        logGeneratedIdentifiers(commandDTO, operationId, transactionId, idempotencyKey);
        initializeLabelAmount(commandDTO);
        PaymentRiskDecisionDTO riskDecisionDTO = paymentRiskInvokeService.checkPreRoute(commandDTO);
        PaymentRiskDecisionEnum riskDecisionEnum = PaymentRiskDecisionSupport.resolve(riskDecisionDTO);
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
            PaymentRiskDecisionSupport.fillStoppedResult(resultDTO, riskDecisionEnum);
            enrichResult(commandDTO, null, null, resultDTO);
            recordInitialTransaction(commandDTO, null, null, resultDTO, riskDecisionEnum, currencyExponent);
            if (isTerminal(resultDTO)) {
                saveTransactionCreatedEvent(commandDTO, resultDTO);
            }
            completeIdempotency(idempotencyKey, commandDTO, resultDTO);
            return resultDTO;
        }
        PaymentRouteResultDTO routeResultDTO = resolveInitialRoute(commandDTO);
        logRouteDecision(commandDTO, routeResultDTO, resultDTO);
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
        logStatusMapping(resultDTO, invokeResultDTO, channelResponse);
        enrichResult(commandDTO, routeResultDTO, channelResponse, resultDTO);
        logMerchantResponseBuilt(commandDTO, resultDTO);
        recordInitialTransaction(commandDTO, routeResultDTO, invokeResultDTO, resultDTO, riskDecisionEnum, currencyExponent);
        saveTransactionCreatedEvent(commandDTO, resultDTO);
        completeIdempotency(idempotencyKey, commandDTO, resultDTO);
        return resultDTO;
    }

    /**
     * 填充渠道同步响应对应的平台状态。
     * <p>
     * 该方法只解释同步响应，不直接推进数据库终态；AUTHORIZED/CAPTURED 等统一动作状态由
     * ChannelTransactionStatusResolver 结合交易类型处理。渠道调用异常必须区分确定性失败和结果不确定：前者进入失败终态，
     * 后者保持 PROCESSING 并等待查询或回调勾兑，避免重复发起可能已经被渠道受理的资金动作。
     *
     * @param resultDTO 待填充交易结果
     * @param invokeResultDTO 渠道调用上下文
     * @param channelResponse 渠道同步响应
     */
    private void fillChannelResult(PaymentCreateResultDTO resultDTO,
                                   PaymentChannelInvokeResultDTO invokeResultDTO,
                                   ChannelPaymentResponse channelResponse) {
        if (isChannelInvokeFailed(invokeResultDTO)) {
            if (invokeResultDTO.isOutcomeUncertain()) {
                resultDTO.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
                resultDTO.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
                resultDTO.setPendingReasonCode(PaymentPendingReasonEnum.CHANNEL_RESULT_UNKNOWN.getCode());
            } else {
                resultDTO.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
                resultDTO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
                resultDTO.setFailReasonCode(confirmedChannelFailureReason(invokeResultDTO));
            }
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
     * 该判断只识别是否发生渠道调用异常，不负责推断渠道是否已经受理。是否保持处理中由
     * PaymentChannelInvokeResultDTO.outcomeUncertain 显式表达，避免根据异常类名或请求状态猜测资金结果。
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
     * 将结果确定的渠道调用异常收敛为可形成终态的失败原因。
     *
     * @param invokeResultDTO 渠道调用上下文
     * @return 渠道明确拒绝或发送前平台故障；其他值失败关闭为发送前故障
     */
    private String confirmedChannelFailureReason(PaymentChannelInvokeResultDTO invokeResultDTO) {
        return PaymentFailureReasonEnum.CHANNEL_DECLINED.getCode().equals(invokeResultDTO.getFailureReasonCode())
                ? PaymentFailureReasonEnum.CHANNEL_DECLINED.getCode()
                : PaymentFailureReasonEnum.CHANNEL_REQUEST_NOT_SENT.getCode();
    }

    /**
     * 安全调用渠道并保留异常上下文。
     * <p>
     * 渠道异常不能在这里继续抛出导致交易事实缺失；上层根据异常上下文中的 outcomeUncertain 区分确定性失败与
     * 结果不确定，只有请求可能已经到达渠道的资金动作才进入 PROCESSING 并等待回调或查询勾兑。
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
     * 调用渠道并把超时或未知结果保留为非终态。
     */
    private PaymentChannelInvokeResultDTO invokeChannelSafely(PaymentCreateCommandDTO commandDTO,
                                                              PaymentRouteResultDTO routeResultDTO,
                                                              String operationId,
                                                              String transactionId,
                                                              PaymentPreparedChannelRequestDTO preparedChannelRequestDTO) {
        try {
            return paymentChannelInvokeService.invoke(commandDTO, routeResultDTO, operationId, transactionId, preparedChannelRequestDTO);
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
        commandDTO.setTransactionDateTime(normalizeShardingTime(commandDTO.getTransactionDateTime()));
        if (!StringUtils.hasText(commandDTO.getTransactionType())) {
            commandDTO.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        }
        if (Boolean.TRUE.equals(commandDTO.getThreeDsRequired())
                && (commandDTO.getThreeDsInfo() == null
                || !"PASSED".equals(commandDTO.getThreeDsInfo().getAuthenticationStatus()))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "3DS authentication has not passed");
        }
    }

    /**
     * Hosted Checkout 已完成路由和 3DS 策略评估时恢复同一 MID，禁止资金动作再次随机路由。
     */
    private PaymentRouteResultDTO resolveInitialRoute(PaymentCreateCommandDTO commandDTO) {
        PaymentCreateCommandDTO.ChannelIdentityDTO identity = commandDTO.getChannelIdentity();
        if (identity == null) {
            return paymentChannelRouteService.route(commandDTO);
        }
        if (!StringUtils.hasText(identity.getChannelCode()) || identity.getChannelMidConfigId() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "fixed channel identity is incomplete");
        }
        return paymentChannelRouteService.restore(identity.getChannelCode(), identity.getChannelId(),
                identity.getChannelMidConfigId(), null);
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
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfoDTO = commandDTO.getTransactionInfo();
        if (transactionInfoDTO.getSourceTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(),
                    "sourceTransactionDateTime is required");
        }
        if (transactionInfoDTO.getRootTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(),
                    "rootTransactionDateTime is required");
        }
        if (commandDTO.getTransactionDateTime() == null) {
            commandDTO.setTransactionDateTime(LocalDateTime.now());
        }
        commandDTO.setTransactionDateTime(normalizeShardingTime(commandDTO.getTransactionDateTime()));
        if (requiresAmount(transactionTypeEnum)
                && (commandDTO.getAmount() == null || commandDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || requiresRequestCurrency(transactionTypeEnum) && !StringUtils.hasText(commandDTO.getCurrency()))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /**
     * 将交易分片时间统一为 MySQL {@code DATETIME(3)} 精度。
     * <p>
     * 该值会同时进入持久化、渠道结果回写、CAS、Outbox 和通知链路；必须在首次使用前冻结，
     * 避免 JDBC 落库后的毫秒值与内存中的微秒值不一致而导致精确路由查询未命中。
     * </p>
     *
     * @param transactionDateTime 调用链传入或服务端生成的交易时间
     * @return 毫秒精度的交易分片时间
     */
    private LocalDateTime normalizeShardingTime(LocalDateTime transactionDateTime) {
        return transactionDateTime.truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * 构造后续动作幂等键。
     * <p>
     * 后续动作必须按 sourceTransactionId 区分授权生命周期，不能把原 Payment/Auth 的 merchantOrderNo 当作 Capture 动作号。
     *
     * @param commandDTO 后续动作命令
     * @param transactionTypeEnum 后续交易类型
     * @return 后续动作幂等键
     */
    private String buildFollowUpIdempotencyKey(PaymentCreateCommandDTO commandDTO,
                                               PaymentTransactionTypeEnum transactionTypeEnum) {
        String sourceTransactionId = commandDTO.getTransactionInfo().getSourceTransactionId();
        String merchantOperationNo = sourceTransactionId + ":" + commandDTO.getMerchantOrderId();
        return transactionIdempotencyService.buildTransactionOperationKey(
                commandDTO.getMerchantId(), merchantOperationNo, transactionTypeEnum.getCode());
    }

    /**
     * 阻断同一授权生命周期下已有未明确结果的 Capture。
     * <p>
     * PROCESSING/PENDING Capture 可能已经被渠道受理，必须先用原渠道身份恢复结果；恢复前不允许新的渠道 Capture。
     *
     * @param commandDTO 后续动作命令
     * @param sourceOrderDO 原交易生命周期主单
     * @param transactionTypeEnum 后续交易类型
     * @param now 当前时间
     */
    private void validateNoNonTerminalCapture(PaymentCreateCommandDTO commandDTO,
                                              TransactionOrderDO sourceOrderDO,
                                              PaymentTransactionTypeEnum transactionTypeEnum,
                                              LocalDateTime now) {
        if (!isCaptureLike(transactionTypeEnum)) {
            return;
        }
        String sourceTransactionId = commandDTO.getTransactionInfo().getSourceTransactionId();
        LocalDateTime beginTime = sourceOrderDO.getTransactionDateTime();
        LocalDateTime endTime = laterOf(now, commandDTO.getTransactionDateTime());
        List<TransactionOperationDO> nonTerminalCaptures = transactionRecordService.findNonTerminalCaptures(
                commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), sourceTransactionId, beginTime, endTime);
        if (nonTerminalCaptures.isEmpty()) {
            return;
        }
        throw new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS.getCode(),
                "source transaction has a pending capture-like action");
    }

    /**
     * 返回两个时间中的较晚值。
     */
    private LocalDateTime laterOf(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    /**
     * Capture 创建前锁定原授权生命周期主单，串行化同一授权下余额校验和未终态 Capture 检查。
     *
     * @param sourceOrderDO 原交易生命周期主单
     * @param transactionTypeEnum 后续交易类型
     * @return 锁定后的生命周期主单
     */
    private TransactionOrderDO lockSourceOrderForCapture(TransactionOrderDO sourceOrderDO,
                                                         PaymentTransactionTypeEnum transactionTypeEnum) {
        if (!isCaptureLike(transactionTypeEnum)
                || sourceOrderDO == null
                || !StringUtils.hasText(sourceOrderDO.getOperationId())
                || sourceOrderDO.getTransactionDateTime() == null) {
            return sourceOrderDO;
        }
        return transactionRecordService.lockOrder(sourceOrderDO.getTransactionDateTime(), sourceOrderDO.getOperationId());
    }

    /**
     * 处理后续动作重复请求。
     *
     * @param commandDTO 后续动作命令
     * @param record 已存在幂等记录
     * @return 幂等快照结果
     */
    private PaymentCreateResultDTO resolveDuplicateFollowUp(PaymentCreateCommandDTO commandDTO,
                                                            TransactionIdempotencyDO record) {
        if (StringUtils.hasText(record.getRequestFingerprint())
                && !Objects.equals(record.getRequestFingerprint(), commandDTO.getRequestFingerprint())) {
            throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT.getCode(),
                    "merchant operation number already has a different request");
        }
        if (!StringUtils.hasText(record.getTransactionId())) {
            throw new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS.getCode(),
                    "merchant operation is being processed");
        }
        return toDuplicateResult(record);
    }

    /**
     * 生成后续资金动作请求指纹。
     *
     * <p>使用原交易权威订单号、原交易号、动作类型、金额和币种，哈希后保存，不持久化拼接原文。</p>
     */
    private String canonicalFollowUpRequestFingerprint(PaymentCreateCommandDTO commandDTO,
                                                       TransactionOrderDO sourceOrderDO,
                                                       PaymentTransactionTypeEnum transactionTypeEnum) {
        String sourceTransactionId = commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getSourceTransactionId();
        String effectiveCurrency = StringUtils.hasText(commandDTO.getCurrency())
                ? commandDTO.getCurrency()
                : sourceOrderDO == null ? null : firstText(
                        sourceOrderDO.getLabelCurrency(), sourceOrderDO.getTransactionCurrency());
        String canonical = String.join("|",
                "v1",
                "merchantId=" + normalizeFingerprintText(commandDTO.getMerchantId()),
                "merchantOrderNo=" + normalizeFingerprintText(sourceOrderDO == null
                        ? commandDTO.getMerchantOrderNo()
                        : sourceOrderDO.getMerchantOrderNo()),
                "merchantOperationNo=" + normalizeFingerprintText(commandDTO.getMerchantOrderId()),
                "transactionType=" + normalizeFingerprintText(transactionTypeEnum.getCode()),
                "sourceTransactionId=" + normalizeFingerprintText(sourceTransactionId),
                "amount=" + normalizeFingerprintAmount(commandDTO.getAmount()),
                "currency=" + normalizeFingerprintText(effectiveCurrency));
        return sha256(canonical);
    }

    /**
     * 撤销指纹绑定商户、原订单、商户操作号和原交易号，避免可选金额差异破坏动作幂等。
     */
    private String canonicalVoidRequestFingerprint(PaymentCreateCommandDTO commandDTO,
                                                   TransactionOrderDO sourceOrderDO) {
        String sourceTransactionId = commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getSourceTransactionId();
        String canonical = String.join("|",
                "v1",
                "merchantId=" + normalizeFingerprintText(commandDTO.getMerchantId()),
                "merchantOrderNo=" + normalizeFingerprintText(sourceOrderDO == null
                        ? commandDTO.getMerchantOrderNo()
                        : sourceOrderDO.getMerchantOrderNo()),
                "merchantOperationNo=" + normalizeFingerprintText(commandDTO.getMerchantOrderId()),
                "transactionType=" + PaymentTransactionTypeEnum.VOID.getCode(),
                "sourceTransactionId=" + normalizeFingerprintText(sourceTransactionId));
        return sha256(canonical);
    }

    /**
     * 指纹文本统一去除首尾空白并转大写，避免大小写差异绕过幂等冲突检测。
     */
    private String normalizeFingerprintText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 指纹金额去除无意义尾零，使等值十进制金额命中同一幂等请求。
     */
    private String normalizeFingerprintAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    /**
     * 计算文本的 SHA-256 摘要。
     */
    private String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "request fingerprint can not be calculated", exception);
        }
    }

    /**
     * 判断当前交易类型是否要求请求币种。
     */
    private boolean requiresRequestCurrency(PaymentTransactionTypeEnum transactionTypeEnum) {
        return PaymentTransactionTypeEnum.CAPTURE != transactionTypeEnum
                && PaymentTransactionTypeEnum.REFUND != transactionTypeEnum;
    }

    /**
     * 判断当前交易类型是否要求商户订单号。
     */
    private boolean requiresMerchantOrderNo(PaymentTransactionTypeEnum transactionTypeEnum) {
        return PaymentTransactionTypeEnum.REFUND != transactionTypeEnum;
    }

    /**
     * 构建商户订单流程锁键。
     */
    private String merchantOrderFlowLockKey(String merchantId, String merchantOrderNo) {
        String lockIdentity = (merchantId == null ? "" : merchantId.trim().toUpperCase(Locale.ROOT))
                + ':'
                + (merchantOrderNo == null ? "" : merchantOrderNo.trim().toUpperCase(Locale.ROOT));
        return redisProperties.coLocatedBusinessKey(
                PAYMENT_REDIS_DOMAIN,
                PAYMENT_LOCK_BUSINESS,
                lockIdentity,
                MERCHANT_ORDER_FLOW_LOCK_PURPOSE);
    }

    /**
     * 构造单笔交易动作幂等锁 Key。
     *
     * @param idempotencyKey 交易动作幂等键
     * @return Redis 锁 Key
     */
    private String transactionOperationLockKey(String idempotencyKey) {
        return redisProperties.coLocatedBusinessKey(
                PAYMENT_REDIS_DOMAIN,
                PAYMENT_LOCK_BUSINESS,
                idempotencyKey,
                TRANSACTION_OPERATION_LOCK_PURPOSE);
    }

    /**
     * 校验后续动作关联的原交易。
     */
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
                || commandDTO.getTransactionInfo() == null
                || commandDTO.getTransactionInfo().getSourceTransactionDateTime() == null
                || commandDTO.getTransactionInfo().getRootTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /**
     * 定位原交易主单。
     * <p>
     * 调用链必须同时传入源动作和生命周期主单的真实分片时间，支付热链路不解析业务编号。
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
        if (transactionInfoDTO.getSourceTransactionDateTime() == null
                || transactionInfoDTO.getRootTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(),
                    "sourceTransactionDateTime and rootTransactionDateTime are required");
        }
        TransactionOrderDO sourceOrderDO = transactionRecordService.findSourceOrderByTransactionId(
                sourceTransactionId,
                normalizeShardingTime(transactionInfoDTO.getSourceTransactionDateTime()),
                normalizeShardingTime(transactionInfoDTO.getRootTransactionDateTime()));
        if (sourceOrderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return sourceOrderDO;
    }

    /**
     * 判断交易是否属于请款类动作。
     */
    private boolean isCaptureLike(PaymentTransactionTypeEnum transactionTypeEnum) {
        return PaymentTransactionTypeEnum.CAPTURE == transactionTypeEnum
                || PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION == transactionTypeEnum;
    }

    /**
     * 根据查询命中的动作单读取生命周期主单。
     *
     * @param operationDO 查询命中的动作单
     * @param rootTransactionDateTime 生命周期根主单分片时间
     * @return 生命周期主单
     */
    private TransactionOrderDO resolveOrderForQuery(TransactionOperationDO operationDO,
                                                    LocalDateTime rootTransactionDateTime) {
        if (operationDO == null
                || !StringUtils.hasText(operationDO.getOperationId())
                || rootTransactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderDO orderDO = transactionRecordService.findOrder(
                normalizeShardingTime(rootTransactionDateTime), operationDO.getOperationId());
        if (orderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return orderDO;
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
        resultDTO.setTotalAuthorizedCancelAmount(orderDO.getLabelAuthorizedCancelAmount());
        resultDTO.setTotalCapturedAmount(resolveDisplayCapturedAmount(orderDO));
        resultDTO.setTotalRefundAmount(orderDO.getLabelRefundedAmount());
        resultDTO.setTotalRefuseAmount(orderDO.getLabelChargebackAmount());
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
                                                                           TransactionOrderDO orderDO,
                                                                           List<TransactionOperationDO> operations,
                                                                           TransactionPaymentMethodInfoDO paymentMethodInfo) {
        PaymentQueryResultDTO.TransactionInfoDTO target = new PaymentQueryResultDTO.TransactionInfoDTO();
        target.setTransactionId(operationDO.getTransactionId());
        target.setSourceTransactionId(operationDO.getSourceTransactionId());
        target.setSourceTransactionDateTime(resolveSourceTransactionDateTime(operationDO, operations));
        OpenApiTransactionProjection publicResult = PaymentOpenApiResultSupport.project(operationDO);
        target.setCode(publicResult.resultCode().getCode());
        target.setMessage(publicResult.message(
                OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US));
        target.setTransactionType(operationDO.getTransactionType());
        target.setTransactionStatus(operationDO.getTransactionStatus());
        target.setProcessStage(operationDO.getProcessStage());
        target.setFailReasonCode(operationDO.getFailReasonCode());
        target.setFailReasonMessage(operationDO.getFailReasonMessage());
        target.setPendingReasonCode(operationDO.getPendingReasonCode());
        target.setTransactionDateTime(operationDO.getTransactionDateTime());
        target.setRootTransactionDateTime(orderDO.getTransactionDateTime());
        target.setPaymentMethod(paymentMethodInfo == null || !StringUtils.hasText(paymentMethodInfo.getPaymentMethod())
                ? firstText(orderDO.getPaymentMethod(), DEFAULT_PAYMENT_METHOD)
                : paymentMethodInfo.getPaymentMethod());
        target.setCardBrand(paymentMethodInfo == null || !StringUtils.hasText(paymentMethodInfo.getPaymentBrand())
                ? orderDO.getPaymentBrand() : paymentMethodInfo.getPaymentBrand());
        target.setCardBin(paymentMethodInfo == null
                ? null : firstText(paymentMethodInfo.getCardNumberMasked(), maskedCardSummary(paymentMethodInfo)));
        target.setAuthCode(operationDO.getAuthCode());
        target.setArn(operationDO.getAcquirerReferenceNo());
        target.setDescription(operationDO.getDescription());
        target.setCallbackUrl(orderDO.getCallbackUrl());
        target.setMerchantWebsite(orderDO.getMerchantWebsite());
        target.setRedirectUrl(orderDO.getRedirectUrl());
        target.setLanguage(orderDO.getLanguage());
        return target;
    }

    /**
     * 从本次查询动作集合或交易事实表恢复源动作时间，商户请求无需携带该字段。
     */
    private LocalDateTime resolveSourceTransactionDateTime(TransactionOperationDO operationDO,
                                                           List<TransactionOperationDO> operations) {
        if (operationDO == null || !StringUtils.hasText(operationDO.getSourceTransactionId())) {
            return null;
        }
        if (operations != null) {
            Optional<LocalDateTime> matched = operations.stream()
                    .filter(item -> operationDO.getSourceTransactionId().equals(item.getTransactionId()))
                    .map(TransactionOperationDO::getTransactionDateTime)
                    .filter(Objects::nonNull)
                    .findFirst();
            if (matched.isPresent()) {
                return matched.get();
            }
        }
        TransactionOperationDO source = transactionRecordService.findSourceOperationByTransactionId(
                operationDO.getSourceTransactionId());
        return source == null ? null : source.getTransactionDateTime();
    }

    /**
     * 由独立保存的 BIN 和尾号构造商户允许看到的脱敏卡摘要。
     */
    private String maskedCardSummary(TransactionPaymentMethodInfoDO paymentMethodInfo) {
        if (paymentMethodInfo == null
                || !StringUtils.hasText(paymentMethodInfo.getCardBin())
                || !StringUtils.hasText(paymentMethodInfo.getCardLast4())) {
            return null;
        }
        return paymentMethodInfo.getCardBin() + "****" + paymentMethodInfo.getCardLast4();
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
        resultDTO.setSourceTransactionDateTime(commandDTO.getTransactionInfo().getSourceTransactionDateTime());
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
                || PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION == transactionTypeEnum
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
        resultDTO.setRootTransactionDateTime(commandDTO.getTransactionDateTime());
        resultDTO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        resultDTO.setPaymentMethod(commandDTO.getPaymentMethod());
        resultDTO.setPaymentBrand(resolvePaymentBrand(commandDTO));
        resultDTO.setCardBin(resolveCardBin(commandDTO));
        enrichPaymentMethodSummary(resultDTO, channelResponse);
        resultDTO.setDescription(commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getDescription());
        resultDTO.setCallbackUrl(resolveCallbackUrl(commandDTO));
        resultDTO.setMerchantWebsite(commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getMerchantWebsite());
        resultDTO.setRedirectUrl(commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getRedirectUrl());
        resultDTO.setLanguage(commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getLanguage());
        if (channelResponse != null) {
            resultDTO.setAuthCode(channelResponse.getAuthCode());
            resultDTO.setAcquirerReferenceNo(channelResponse.getAcquirerReferenceNo());
        }
        enrichMerchantResponse(resultDTO);
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

    /**
     * 规范化渠道返回的脱敏卡号。
     */
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
        resultDTO.setRootTransactionDateTime(sourceOrderDO.getTransactionDateTime());
        resultDTO.setPaymentMethod(sourceOrderDO.getPaymentMethod());
        resultDTO.setPaymentBrand(firstText(resultDTO.getPaymentBrand(), sourceOrderDO.getPaymentBrand()));
        resultDTO.setOrderAmount(commandDTO.getLabelAmount());
        resultDTO.setOrderCurrency(commandDTO.getLabelCurrency());
        resultDTO.setTotalAuthorizedAmount(resolveDisplayAuthorizedAmount(sourceOrderDO));
        resultDTO.setTotalAuthorizedCancelAmount(sourceOrderDO.getLabelAuthorizedCancelAmount());
        resultDTO.setTotalCapturedAmount(resolveDisplayCapturedAmount(sourceOrderDO));
        resultDTO.setTotalRefundAmount(sourceOrderDO.getLabelRefundedAmount());
        resultDTO.setTotalRefuseAmount(sourceOrderDO.getLabelChargebackAmount());
        resultDTO.setTotalAuthorizedAmount(sumOnSuccess(resultDTO.getTotalAuthorizedAmount(), resultDTO, PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION));
        resultDTO.setTotalCapturedAmount(sumCapturedOnSuccess(resultDTO.getTotalCapturedAmount(), resultDTO));
        resultDTO.setTotalRefundAmount(sumOnSuccess(
                sourceOrderDO.getLabelRefundedAmount(), resultDTO, PaymentTransactionTypeEnum.REFUND));
        if (PaymentTransactionTypeEnum.VOID.getCode().equals(resultDTO.getTransactionType())
                && PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())) {
            resultDTO.setTotalAuthorizedCancelAmount(sourceOrderDO.getLabelAuthorizedAmount());
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
            return firstPositive(sourceOrderDO.getLabelCapturedAmount(), sourceOrderDO.getLabelAmount());
        }
        return sourceOrderDO.getLabelAuthorizedAmount();
    }

    /**
     * 解析请款金额展示口径。
     *
     * @param sourceOrderDO 生命周期主单
     * @return 请款展示金额
     */
    private BigDecimal resolveDisplayCapturedAmount(TransactionOrderDO sourceOrderDO) {
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            return firstPositive(sourceOrderDO.getLabelCapturedAmount(), sourceOrderDO.getLabelAmount());
        }
        return sourceOrderDO.getLabelCapturedAmount();
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
                || resultDTO.getLabelAmount() == null) {
            return;
        }
        if (PaymentTransactionTypeEnum.AUTHORIZATION.getCode().equals(resultDTO.getTransactionType())
                || PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode().equals(resultDTO.getTransactionType())
                || PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode().equals(resultDTO.getTransactionType())) {
            resultDTO.setTotalAuthorizedAmount(resultDTO.getLabelAmount());
            return;
        }
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(resultDTO.getTransactionType())
                || PaymentTransactionTypeEnum.CAPTURE.getCode().equals(resultDTO.getTransactionType())
                || PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION.getCode().equals(resultDTO.getTransactionType())) {
            if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(resultDTO.getTransactionType())) {
                resultDTO.setTotalAuthorizedAmount(resultDTO.getLabelAmount());
            }
            resultDTO.setTotalCapturedAmount(resultDTO.getLabelAmount());
            return;
        }
        if (PaymentTransactionTypeEnum.REFUND.getCode().equals(resultDTO.getTransactionType())) {
            resultDTO.setTotalRefundAmount(resultDTO.getLabelAmount());
            return;
        }
        if (PaymentTransactionTypeEnum.VOID.getCode().equals(resultDTO.getTransactionType())) {
            resultDTO.setTotalAuthorizedCancelAmount(resultDTO.getLabelAmount());
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
                && resultDTO.getLabelAmount() != null) {
            return current.add(resultDTO.getLabelAmount());
        }
        return current;
    }

    /**
     * 仅在请款成功时累计已请款金额。
     */
    private BigDecimal sumCapturedOnSuccess(BigDecimal existingAmount,
                                            PaymentCreateResultDTO resultDTO) {
        BigDecimal current = existingAmount == null ? BigDecimal.ZERO : existingAmount;
        if ((PaymentTransactionTypeEnum.CAPTURE.getCode().equals(resultDTO.getTransactionType())
                || PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION.getCode().equals(resultDTO.getTransactionType()))
                && PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                && resultDTO.getLabelAmount() != null) {
            return current.add(resultDTO.getLabelAmount());
        }
        return current;
    }

    /**
     * 补充商户可见响应字段。
     */
    private void enrichMerchantResponse(PaymentCreateResultDTO resultDTO) {
        PaymentOpenApiResultSupport.applyMerchantProjection(resultDTO);
    }

    /**
     * 解析平台标准支付品牌。
     */
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

    /**
     * 解析脱敏卡号中的 BIN。
     */
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

    /**
     * 转换为响应中的子商户信息。
     */
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

    /**
     * 判断子商户信息是否为空。
     */
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

    /**
     * 返回首个非空文本。
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
     * 后续动作优先使用本次回调地址，未提供时继承交易信息中的原回调地址。
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

    /**
     * 应用无需换汇的金额计划。
     */
    private void applyNoConversion(PaymentCreateCommandDTO commandDTO) {
        commandDTO.setTransactionCurrency(normalizeCurrency(commandDTO.getLabelCurrency()));
        commandDTO.setTransactionAmount(commandDTO.getLabelAmount());
        commandDTO.setTransactionRate(defaultTransactionRate());
        commandDTO.setRateSource(null);
        commandDTO.setRateTime(null);
        commandDTO.setDccEnabled(0);
        commandDTO.setEdcEnabled(0);
    }

    /**
     * 返回无换汇场景的单位交易汇率。
     */
    private BigDecimal defaultTransactionRate() {
        return new BigDecimal("1.00000000");
    }

    /**
     * 规范化币种。
     */
    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
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
     * 判断是否终态。
     */
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

    /**
     * 记录后续交易动作及其金额事实。
     */
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
        message.setTraceId(TraceContext.getOrCreateTraceId());
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
        eventDO.setTopic(MqTopic.PAYMENT_TRANSACTION_FIFO);
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
        log.info("支付出站事件已保存, event: PAYMENT_OUTBOX_SAVED, stage: MQ, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, transactionType: {}, platformStatus: {}, eventType: {}, topic: {}, tag: {}, retryCount: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                resultDTO.getTransactionId(),
                resultDTO.getOperationId(),
                resultDTO.getTransactionType(),
                resultDTO.getStatus(),
                eventDO.getEventType(),
                eventDO.getTopic(),
                eventDO.getTag(),
                eventDO.getRetryCount());
    }

    /**
     * 记录支付处理完成日志。
     */
    private void logPaymentEnd(String event, PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO, long startNanos) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        log.info("支付交易处理结束, event: {}, stage: FINISH, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, transactionType: {}, currency: {}, amount: {}, platformStatus: {}, durationMs: {}",
                event,
                TraceContext.getTraceId(),
                commandDTO == null ? null : commandDTO.getMerchantId(),
                commandDTO == null ? null : commandDTO.getMerchantOrderNo(),
                resultDTO == null ? null : resultDTO.getTransactionId(),
                resultDTO == null ? null : resultDTO.getOperationId(),
                resultDTO == null ? null : resultDTO.getTransactionType(),
                resultDTO == null ? null : resultDTO.getCurrency(),
                resultDTO == null ? null : resultDTO.getAmount(),
                resultDTO == null ? null : resultDTO.getStatus(),
                durationMs);
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
        if (isInitialTransactionType(resultDTO.getTransactionType())) {
            transactionIdempotencyService.complete(
                    "MERCHANT_ORDER_FLOW",
                    transactionIdempotencyService.buildMerchantOrderFlowKey(
                            commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo()),
                    resultDTO.getOperationId(),
                    resultDTO.getTransactionId(),
                    resultDTO.getStatus(),
                    commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount(),
                    resultDTO.getCurrency(),
                    JsonUtils.toJsonString(resultDTO));
        }
        log.info("支付幂等结果已完成, event: PAYMENT_IDEMPOTENCY_COMPLETE, stage: IDEMPOTENCY, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, transactionType: {}, platformStatus: {}, currency: {}, amount: {}, idempotencyKey: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                resultDTO.getTransactionId(),
                resultDTO.getOperationId(),
                resultDTO.getTransactionType(),
                resultDTO.getStatus(),
                resultDTO.getCurrency(),
                commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount(),
                idempotencyKey);
    }

    /**
     * 将重复请求命中的幂等记录转换为创建交易响应。
     *
     * @param record 幂等记录
     * @return 创建交易响应
     */
    private PaymentCreateResultDTO toDuplicateResult(TransactionIdempotencyDO record) {
        log.info("支付幂等结果命中, event: PAYMENT_IDEMPOTENCY_HIT, stage: IDEMPOTENCY, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, transactionType: {}, platformStatus: {}, currency: {}, amount: {}",
                TraceContext.getTraceId(),
                record.getMerchantId(),
                record.getMerchantOrderNo(),
                record.getTransactionId(),
                record.getOperationId(),
                record.getTransactionType(),
                record.getTransactionStatus(),
                record.getTransactionCurrency(),
                record.getTransactionAmount());
        if (StringUtils.hasText(record.getResultSnapshot())) {
            PaymentCreateResultDTO resultDTO = JsonUtils.parseObject(record.getResultSnapshot(), PaymentCreateResultDTO.class);
            if (resultDTO != null) {
                applyLatestIdempotencyStatus(resultDTO, record.getTransactionStatus());
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

    /**
     * 以幂等记录的最新状态覆盖渠道异步确认前保存的历史响应快照。
     */
    private void applyLatestIdempotencyStatus(PaymentCreateResultDTO resultDTO, String transactionStatus) {
        PaymentPersistedResultSupport.overwriteLatestIdempotencyStatus(resultDTO, transactionStatus);
    }

    /**
     * 判断当前动作是否为共用商户订单流守卫的首次交易类型。
     */
    private boolean isInitialTransactionType(String transactionType) {
        return PaymentTransactionTypeEnum.PAYMENT.getCode().equals(transactionType)
                || PaymentTransactionTypeEnum.AUTHORIZATION.getCode().equals(transactionType)
                || PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode().equals(transactionType);
    }

    /**
     * 记录交易主单号、动作单号和幂等键生成结果。
     * <p>
     * 日志覆盖商户、订单、交易类型、币种和金额，用于串联请求接收、幂等判断和本地落库。
     * 该方法只写日志，不修改交易状态、不提交事务、不调用外部系统；卡号和安全码不进入日志字段。
     * </p>
     * @param commandDTO 支付创建命令，提供商户号、商户订单号、交易类型、币种和金额
     * @param operationId 本次交易动作单号，不允许为空
     * @param transactionId 平台交易主单号，不允许为空
     * @param idempotencyKey 支付创建幂等键，来源于商户、订单和交易类型等幂等维度
     */
    private void logGeneratedIdentifiers(PaymentCreateCommandDTO commandDTO,
                                         String operationId,
                                         String transactionId,
                                         String idempotencyKey) {
        log.info("支付交易标识已生成, event: PAYMENT_IDENTIFIERS_GENERATED, stage: IDENTIFIER, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, sourceTransactionId: {}, transactionType: {}, currency: {}, amount: {}, idempotencyKey: {}",
                TraceContext.getTraceId(),
                commandDTO == null ? null : commandDTO.getMerchantId(),
                commandDTO == null ? null : commandDTO.getMerchantOrderNo(),
                transactionId,
                operationId,
                commandDTO == null || commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getSourceTransactionId(),
                commandDTO == null ? null : commandDTO.getTransactionType(),
                commandDTO == null ? null : commandDTO.getCurrency(),
                commandDTO == null ? null : commandDTO.getAmount(),
                idempotencyKey);
    }

    /**
     * 记录支付路由决策结果。
     * <p>
     * 日志覆盖候选决策后的最终渠道、渠道 MID、渠道能力、支付方式、币种和金额，
     * 用于核对风控后路由选择与渠道调用输入。该方法不改变路由结果，不发送渠道请求。
     * </p>
     * @param commandDTO 支付创建命令，提供商户、订单、交易类型、支付方式、币种和金额
     * @param routeResultDTO 路由服务返回的最终渠道、MID 和能力信息，允许为空表示未成功路由
     * @param resultDTO 当前支付创建结果，提供平台交易号和动作单号
     */
    private void logRouteDecision(PaymentCreateCommandDTO commandDTO,
                                  PaymentRouteResultDTO routeResultDTO,
                                  PaymentCreateResultDTO resultDTO) {
        log.info("支付渠道路由决策完成, event: PAYMENT_ROUTE_DECISION, stage: ROUTE, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, transactionType: {}, paymentMethod: {}, currency: {}, amount: {}, channelCode: {}, channelMidId: {}, channelCapability: {}, midNo: {}, requestedCurrency: {}, routedCurrency: {}, edcRequired: {}, supportedCurrencies: {}, endpointHost: {}, connectTimeoutSeconds: {}, readTimeoutSeconds: {}, routed: {}, routeReason: {}",
                TraceContext.getTraceId(),
                commandDTO == null ? null : commandDTO.getMerchantId(),
                commandDTO == null ? null : commandDTO.getMerchantOrderNo(),
                resultDTO == null ? null : resultDTO.getTransactionId(),
                resultDTO == null ? null : resultDTO.getOperationId(),
                commandDTO == null ? null : commandDTO.getTransactionType(),
                commandDTO == null ? null : commandDTO.getPaymentMethod(),
                commandDTO == null ? null : commandDTO.getTransactionCurrency(),
                commandDTO == null ? null : commandDTO.getTransactionAmount(),
                routeResultDTO == null ? null : routeResultDTO.getChannelCode(),
                routeResultDTO == null ? null : routeResultDTO.getMidConfigId(),
                routeResultDTO == null ? null : routeResultDTO.getCapabilityId(),
                routeResultDTO == null ? null : maskShort(routeResultDTO.getMidNo()),
                routeResultDTO == null ? null : routeResultDTO.getRequestedCurrency(),
                routeResultDTO == null ? null : routeResultDTO.getRoutedCurrency(),
                routeResultDTO != null && routeResultDTO.isEdcRequired(),
                routeResultDTO == null ? null : routeResultDTO.getSupportedCurrencies(),
                routeResultDTO == null ? null : endpointHost(routeResultDTO.getRequestUrl()),
                routeResultDTO == null ? null : routeResultDTO.getConnectTimeoutSeconds(),
                routeResultDTO == null ? null : routeResultDTO.getReadTimeoutSeconds(),
                routeResultDTO != null && routeResultDTO.isRouted(),
                routeResultDTO == null ? null : routeResultDTO.getRouteReason());
    }

    /**
     * 记录 payment 收到的 OpenAPI 请求上下文。
     * <p>
     * 日志用于确认 OpenAPI 到 payment 之间的商户请求来源、密文摘要和明文脱敏参数是否完整传递；
     * 不输出完整请求密文、PAN、CVV、JWT、内部签名或完整 callbackUrl query。
     * </p>
     * @param commandDTO 支付创建命令
     */
    private void logPaymentRequestContext(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO == null) {
            return;
        }
        log.info("支付OpenAPI上下文已记录, event: PAYMENT_OPENAPI_CONTEXT, stage: ACCEPT, traceId: {}, merchantId: {}, merchantOrderNo: {}, merchantOrderId: {}, transactionType: {}, openApiRequestPath: {}, requestFingerprint: {}, requestCipherMasked: {}, requestSource: {}, plainRequestSummary: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getMerchantOrderId(),
                commandDTO.getTransactionType(),
                commandDTO.getOpenApiRequestPath(),
                commandDTO.getRequestFingerprint(),
                commandDTO.getMerchantRequestCipherMasked(),
                requestSourceSummary(commandDTO),
                commandDTO.getMerchantRequestPlainJsonMasked());
    }

    /**
     * 生成 payment 收到的请求来源摘要。
     *
     * @param commandDTO 支付创建命令
     * @return 请求来源摘要 JSON
     */
    private String requestSourceSummary(PaymentCreateCommandDTO commandDTO) {
        PaymentRequestSourceSummary summary = new PaymentRequestSourceSummary(
                digestNullable(commandDTO.getIngressClientIp()),
                digestNullable(commandDTO.getPayerIp()),
                maskUrl(commandDTO.getSourceUrl()),
                maskUrl(commandDTO.getCallbackUrl()),
                truncateText(commandDTO.getUserAgent(), 160));
        return JsonUtils.toJsonString(summary);
    }

    /**
     * 为空值保留空语义，为非空文本计算摘要。
     */
    private String digestNullable(String value) {
        return StringUtils.hasText(value) ? sha256(value.trim()) : null;
    }

    /**
     * 脱敏 URL 查询参数。
     *
     * @param url 原始 URL
     * @return 脱敏 URL
     */
    private String maskUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int queryIndex = url.indexOf('?');
        if (queryIndex < 0) {
            return url;
        }
        return url.substring(0, queryIndex) + "?...";
    }

    /**
     * 脱敏 MID 或类似短标识。
     *
     * @param value 原始值
     * @return 掩码值
     */
    private String maskShort(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= 6) {
            return "***";
        }
        return normalized.substring(0, 3) + "***" + normalized.substring(normalized.length() - 3);
    }

    /**
     * 提取 URL 主机名。
     *
     * @param url 原始 URL
     * @return 主机名
     */
    private String endpointHost(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return java.net.URI.create(url).getHost();
        } catch (RuntimeException exception) {
            return "invalid_url";
        }
    }

    /**
     * 截断文本摘要。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断后文本
     */
    private String truncateText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    /**
     * 记录渠道响应到平台状态的映射结果。
     * <p>
     * 日志覆盖渠道请求号、渠道交易号、渠道业务码、收单参考号、失败码和平台状态，
     * 用于排查渠道返回、状态机流转和商户响应之间的一致性。该方法不写库、不触发 MQ。
     * </p>
     * @param resultDTO 平台支付创建结果，包含交易标识、交易类型、平台状态和失败码
     * @param invokeResultDTO 渠道调用包装结果，包含渠道请求号、请求状态和结果确定性
     * @param channelResponse 渠道响应映射对象，包含渠道码、渠道交易号和渠道业务码
     */
    private void logStatusMapping(PaymentCreateResultDTO resultDTO,
                                  PaymentChannelInvokeResultDTO invokeResultDTO,
                                  ChannelPaymentResponse channelResponse) {
        log.info("支付渠道状态映射完成, event: PAYMENT_STATUS_MAPPED, stage: STATUS_MAPPING, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, transactionType: {}, channelCode: {}, channelRequestId: {}, channelTransactionId: {}, platformStatus: {}, channelResultCode: {}, acquirerCode: {}, failureCode: {}, requestStatus: {}, outcomeUncertain: {}",
                TraceContext.getTraceId(),
                resultDTO == null ? null : resultDTO.getMerchantId(),
                resultDTO == null ? null : resultDTO.getMerchantOrderNo(),
                resultDTO == null ? null : resultDTO.getTransactionId(),
                resultDTO == null ? null : resultDTO.getOperationId(),
                resultDTO == null ? null : resultDTO.getTransactionType(),
                channelResponse == null ? null : channelResponse.getChannelCode(),
                invokeResultDTO == null ? null : invokeResultDTO.getRequestId(),
                channelResponse == null ? null : channelResponse.getChannelTransactionId(),
                resultDTO == null ? null : resultDTO.getStatus(),
                channelResponse == null ? null : channelResponse.getChannelResponseCode(),
                channelResponse == null ? null : channelResponse.getAcquirerReferenceNo(),
                resultDTO == null ? null : resultDTO.getFailReasonCode(),
                invokeResultDTO == null ? null : invokeResultDTO.getRequestStatus(),
                invokeResultDTO != null && invokeResultDTO.isOutcomeUncertain());
    }

    /**
     * 记录返回商户前的平台响应构造结果。
     * <p>
     * 日志覆盖商户订单、平台交易号、动作单号、平台状态、业务码、币种和金额，
     * 用于核对开放接口响应与支付核心结果。该方法不包含卡号、CVV、JWT 或渠道完整报文。
     * </p>
     * @param commandDTO 支付创建命令，提供商户号和商户订单号
     * @param resultDTO 平台支付创建结果，提供交易标识、状态、业务码、币种和金额
     */
    private void logMerchantResponseBuilt(PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        log.info("支付商户响应已构建, event: PAYMENT_MERCHANT_RESPONSE_BUILT, stage: RESPONSE, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, transactionType: {}, platformStatus: {}, platformCode: {}, currency: {}, amount: {}",
                TraceContext.getTraceId(),
                commandDTO == null ? null : commandDTO.getMerchantId(),
                commandDTO == null ? null : commandDTO.getMerchantOrderNo(),
                resultDTO == null ? null : resultDTO.getTransactionId(),
                resultDTO == null ? null : resultDTO.getOperationId(),
                resultDTO == null ? null : resultDTO.getTransactionType(),
                resultDTO == null ? null : resultDTO.getStatus(),
                resultDTO == null ? null : resultDTO.getMerchantResponseCode(),
                resultDTO == null ? null : resultDTO.getCurrency(),
                resultDTO == null ? null : resultDTO.getAmount());
    }

    /**
     * 将业务时间转换为 UTC 时间。
     */
    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime, String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone == null || timeZone.isBlank() ? DEFAULT_TIME_ZONE : timeZone);
        return transactionDateTime.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * 尝试快速获取仅覆盖本地准备事务的交易锁。
     *
     * @param lockKey   Redis 锁 Key
     * @return true 表示可继续处理
     */
    private boolean tryLock(String lockKey) {
        return distributedLockService.tryLock(
                lockKey,
                TRANSACTION_OPERATION_LOCK_WAIT_TIME,
                TRANSACTION_OPERATION_LOCK_LEASE_TIME
        );
    }

    /**
     * 释放仅保护本地准备阶段的交易锁。
     *
     * <p>准备服务是独立事务 Bean，方法返回即代表本地事务已完成；锁必须在后续渠道 I/O 前释放。</p>
     *
     * @param lockKey   Redis 锁 Key
     * @param locked    是否已获取锁
     */
    private void unlockPreparationLock(String lockKey, boolean locked) {
        if (!locked) {
            return;
        }
        try {
            distributedLockService.unlock(lockKey);
        } catch (RuntimeException exception) {
            log.warn("支付准备锁释放失败, event: PAYMENT_PREPARATION_LOCK_RELEASE_FAILED, stage: LOCAL_PREPARE, traceId: {}, lockKey: {}",
                    TraceContext.getTraceId(),
                    lockKey,
                    exception);
        }
    }

    /**
     * 支付请求来源的脱敏日志摘要。
     *
     * @param ingressClientIpHash 网关识别的客户端 IP 摘要
     * @param payerIpHash 商户上送付款人 IP 摘要
     * @param sourceUrl 不含查询参数的来源地址
     * @param callbackUrl 不含查询参数的商户回调地址
     * @param userAgent 长度受控的浏览器标识
     */
    private record PaymentRequestSourceSummary(String ingressClientIpHash,
                                               String payerIpHash,
                                               String sourceUrl,
                                               String callbackUrl,
                                               String userAgent) {
    }

}
