package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.mq.TransactionMqConstants;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.PaymentTransactionPreparationService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import com.scott.payment.payment.service.dto.PaymentInitialPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentTransactionPreparationService
 * @date : 2026-07-23 00:00
 * @email : scott_x@163.com
 * @description : 首次交易本地准备默认实现，位于 service-payment 服务实现层，保证渠道调用前提交幂等、交易事实、路由和渠道请求 INIT。
 * @status : create
 */
@Service
@Slf4j
public class DefaultPaymentTransactionPreparationService implements PaymentTransactionPreparationService {

    /**
     * 平台内部生命周期关联 ID 前缀，对应 transaction_order.operation_id。
     */
    private static final String OPERATION_ID_PREFIX = "OP";

    /**
     * 平台渠道请求 ID 前缀，对应 transaction_channel_request.request_id。
     */
    private static final String CHANNEL_REQUEST_ID_PREFIX = "CR";

    /**
     * 渠道交易 ID 前缀，用于 MPGS transaction.id 等渠道侧资金动作幂等标识。
     */
    private static final String CHANNEL_TRANSACTION_ID_PREFIX = "CH";

    /**
     * 交易动作幂等范围。
     */
    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";

    /** 商户订单支付流守卫范围。 */
    private static final String MERCHANT_ORDER_FLOW_SCOPE = "MERCHANT_ORDER_FLOW";

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
     * 本地事件初始版本。
     */
    private static final int INITIAL_VERSION = 0;

    /**
     * 未删除标识。
     */
    private static final int NOT_DELETED = 0;

    private final IsoDictionaryService isoDictionaryService;

    private final PaymentRiskInvokeService paymentRiskInvokeService;

    private final PaymentChannelRouteService paymentChannelRouteService;

    /** 服务端卡品牌解析器；BIN 数据库优先，公开 IIN 规则兜底。 */
    private final PaymentCheckoutCardCapabilityService cardCapabilityService;

    private final PaymentExchangeRateService paymentExchangeRateService;

    private final TransactionIdempotencyService transactionIdempotencyService;

    private final TransactionEventOutboxService transactionEventOutboxService;

    /** 无渠道短路已进入终态时，与交易事实同事务写入状态变更 Outbox。 */
    private final TransactionLifecycleEventService lifecycleEventService;

    private final TransactionRecordService transactionRecordService;

    /**
     * 风控累计限额预占补偿器；本地准备失败时负责取消已创建但尚未确认的预占。
     */
    private final PaymentRiskReservationCompensation riskReservationCompensation;

    /**
     * 创建首次交易本地准备默认实现。
     *
     * @param isoDictionaryService ISO 币种字典服务
     * @param paymentRiskInvokeService 路由前风控调用服务
     * @param paymentChannelRouteService 收单渠道路由服务
     * @param paymentExchangeRateService 交易汇率服务
     * @param transactionIdempotencyService 交易幂等服务
     * @param transactionEventOutboxService 交易本地事件服务
     * @param transactionRecordService 交易事实记录服务
     */
    public DefaultPaymentTransactionPreparationService(IsoDictionaryService isoDictionaryService,
                                                       PaymentRiskInvokeService paymentRiskInvokeService,
                                                       PaymentChannelRouteService paymentChannelRouteService,
                                                       PaymentExchangeRateService paymentExchangeRateService,
                                                       TransactionIdempotencyService transactionIdempotencyService,
                                                       TransactionEventOutboxService transactionEventOutboxService,
                                                       TransactionRecordService transactionRecordService) {
        this(isoDictionaryService,
                paymentRiskInvokeService,
                paymentChannelRouteService,
                paymentExchangeRateService,
                transactionIdempotencyService,
                transactionEventOutboxService,
                transactionRecordService,
                null);
    }

    /** Spring 生产构造器，注入统一卡品牌解析能力。 */
    @Autowired
    public DefaultPaymentTransactionPreparationService(IsoDictionaryService isoDictionaryService,
                                                       PaymentRiskInvokeService paymentRiskInvokeService,
                                                       PaymentChannelRouteService paymentChannelRouteService,
                                                       PaymentExchangeRateService paymentExchangeRateService,
                                                       TransactionIdempotencyService transactionIdempotencyService,
                                                       TransactionEventOutboxService transactionEventOutboxService,
                                                       TransactionRecordService transactionRecordService,
                                                       PaymentCheckoutCardCapabilityService cardCapabilityService) {
        this.isoDictionaryService = isoDictionaryService;
        this.paymentRiskInvokeService = paymentRiskInvokeService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.cardCapabilityService = cardCapabilityService;
        this.paymentExchangeRateService = paymentExchangeRateService;
        this.transactionIdempotencyService = transactionIdempotencyService;
        this.transactionEventOutboxService = transactionEventOutboxService;
        this.lifecycleEventService = new DefaultTransactionLifecycleEventService(transactionEventOutboxService);
        this.transactionRecordService = transactionRecordService;
        this.riskReservationCompensation =
                new PaymentRiskReservationCompensation(paymentRiskInvokeService);
    }

    /**
     * 在独立本地事务中准备首次交易事实。
     *
     * @param commandDTO 创建交易命令
     * @param transactionType 首次交易类型
     * @return 本地准备结果
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public PaymentInitialPreparationResultDTO prepareInitialTransaction(PaymentCreateCommandDTO commandDTO, String transactionType) {
        long startNanos = System.nanoTime();
        if (cardCapabilityService != null) {
            cardCapabilityService.enrichCardBrand(commandDTO);
        }
        String idempotencyKey = transactionIdempotencyService.buildTransactionOperationKey(
                commandDTO.getMerchantId(), commandDTO.getMerchantOrderId(), transactionType);
        String merchantOrderFlowKey = transactionIdempotencyService.buildMerchantOrderFlowKey(
                commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo());
        commandDTO.setRequestFingerprint(canonicalRequestFingerprint(commandDTO, transactionType));
        LocalDateTime now = LocalDateTime.now();
        log.info("event: PAYMENT_LOCAL_PREPARE_BEGIN stage=LOCAL_PREPARE traceId: {} merchantId: {} merchantOrderNo: {} merchantOrderId: {} transactionType: {} paymentMethod: {} currency: {} amount: {} idempotencyKey: {} requestFingerprint: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getMerchantOrderId(),
                transactionType,
                commandDTO.getPaymentMethod(),
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
                idempotencyKey,
                commandDTO.getRequestFingerprint());
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
            log.warn("event: PAYMENT_IDEMPOTENCY_BEGIN_CONFLICT stage=IDEMPOTENCY_BEGIN traceId: {} merchantId: {} merchantOrderNo: {} transactionType: {} idempotencyKey: {} requestFingerprint: {}",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    transactionType,
                    idempotencyKey,
                    commandDTO.getRequestFingerprint());
            return resolveDuplicate(commandDTO, idempotencyKey);
        }
        acquireMerchantOrderFlow(commandDTO, transactionType, merchantOrderFlowKey, now);
        log.info("event: PAYMENT_IDEMPOTENCY_BEGIN_OK stage=IDEMPOTENCY_BEGIN traceId: {} merchantId: {} merchantOrderNo: {} transactionType: {} idempotencyKey: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                transactionType,
                idempotencyKey);
        String operationId = PaymentOrderNoGenerator.nextOrderNo(OPERATION_ID_PREFIX, commandDTO.getTransactionDateTime());
        String transactionId = StringUtils.hasText(commandDTO.getTransactionId())
                ? commandDTO.getTransactionId()
                : PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        commandDTO.setTransactionId(transactionId);
        log.info("event: PAYMENT_IDENTIFIERS_GENERATED stage=IDENTIFIER traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} currency: {} amount: {} idempotencyKey: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                transactionId,
                operationId,
                transactionType,
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
                idempotencyKey);
        initializeLabelAmount(commandDTO);
        long riskStartNanos = System.nanoTime();
        log.info("event: PAYMENT_RISK_EVALUATE_BEGIN stage=RISK traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} paymentMethod: {} currency: {} amount: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                transactionId,
                operationId,
                transactionType,
                commandDTO.getPaymentMethod(),
                commandDTO.getCurrency(),
                commandDTO.getAmount());
        PaymentRiskDecisionDTO riskDecisionDTO = paymentRiskInvokeService.checkPreRoute(commandDTO);
        riskReservationCompensation.register(commandDTO, riskDecisionDTO);
        PaymentRiskDecisionEnum riskDecisionEnum = PaymentRiskDecisionSupport.resolve(riskDecisionDTO);
        log.info("event: PAYMENT_RISK_EVALUATE_END stage=RISK traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} riskDecision: {} riskPassed: {} riskRecordNo: {} hitRuleId: {} durationMs: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                transactionId,
                operationId,
                transactionType,
                riskDecisionEnum.getCode(),
                riskDecisionDTO == null ? null : riskDecisionDTO.isPassed(),
                riskDecisionDTO == null ? null : riskDecisionDTO.getRiskRecordNo(),
                riskDecisionDTO == null ? null : riskDecisionDTO.getRiskCode(),
                elapsedMillis(riskStartNanos));
        PaymentCreateResultDTO resultDTO = buildInitialResult(commandDTO, operationId, transactionId, transactionType);
        PaymentRouteResultDTO routeResultDTO = null;
        PaymentChannelInvokeResultDTO preparedInvokeResultDTO = null;
        boolean callChannel = false;
        if (!riskDecisionEnum.isAllowProceed()) {
            applyNoConversion(commandDTO);
            resultDTO.setCurrency(commandDTO.getTransactionCurrency());
            resultDTO.setAmount(toMinorAmount(commandDTO.getTransactionAmount(), commandDTO.getTransactionCurrency()));
            PaymentRiskDecisionSupport.fillStoppedResult(resultDTO, riskDecisionEnum);
        } else {
            long routeStartNanos = System.nanoTime();
            log.info("event: PAYMENT_ROUTE_EVALUATE_BEGIN stage=ROUTE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} paymentMethod: {} currency: {} amount: {}",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    transactionId,
                    operationId,
                    transactionType,
                    commandDTO.getPaymentMethod(),
                    commandDTO.getCurrency(),
                    commandDTO.getAmount());
            routeResultDTO = resolveInitialRoute(commandDTO);
            log.info("event: PAYMENT_ROUTE_EVALUATE_END stage=ROUTE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} channelCode: {} channelMidId: {} midNo: {} requestedCurrency: {} routedCurrency: {} edcRequired: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    transactionId,
                    operationId,
                    transactionType,
                    routeResultDTO == null ? null : routeResultDTO.getChannelCode(),
                    routeResultDTO == null ? null : routeResultDTO.getMidConfigId(),
                    routeResultDTO == null ? null : maskShort(routeResultDTO.getMidNo()),
                    routeResultDTO == null ? null : routeResultDTO.getRequestedCurrency(),
                    routeResultDTO == null ? null : routeResultDTO.getRoutedCurrency(),
                    routeResultDTO != null && routeResultDTO.isEdcRequired(),
                    elapsedMillis(routeStartNanos));
            if (!applyCurrencyConversion(commandDTO, routeResultDTO, resultDTO)) {
                // resultDTO 已按缺失汇率填充为 FAILED。
                log.warn("event: PAYMENT_EXCHANGE_RATE_MISSING stage=AMOUNT traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} labelCurrency: {} routedCurrency: {} labelAmount: {} platformStatus: {} failureCode: {}",
                        TraceContext.getTraceId(),
                        commandDTO.getMerchantId(),
                        commandDTO.getMerchantOrderNo(),
                        transactionId,
                        operationId,
                        transactionType,
                        commandDTO.getLabelCurrency(),
                        routeResultDTO == null ? null : routeResultDTO.getRoutedCurrency(),
                        commandDTO.getLabelAmount(),
                        resultDTO.getStatus(),
                        resultDTO.getFailReasonCode());
            } else {
                resultDTO.setCurrency(commandDTO.getTransactionCurrency());
                resultDTO.setAmount(toMinorAmount(commandDTO.getTransactionAmount(), commandDTO.getTransactionCurrency()));
                resultDTO.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
                resultDTO.setProcessStage(PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode());
                PaymentPreparedChannelRequestDTO preparedChannelRequestDTO = prepareChannelRequest(commandDTO, routeResultDTO, operationId, transactionId);
                preparedInvokeResultDTO = buildPreparedInvokeResult(commandDTO, routeResultDTO, operationId, transactionId, preparedChannelRequestDTO);
                log.info("event: PAYMENT_CHANNEL_REQUEST_PREPARED stage=CHANNEL_PREPARE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} paymentMethod: {} currency: {} amount: {} channelCode: {} channelMidId: {} channelRequestId: {} channelOrderNo: {} channelTransactionId: {} endpointHost: {} endpointPath: {}",
                        TraceContext.getTraceId(),
                        commandDTO.getMerchantId(),
                        commandDTO.getMerchantOrderNo(),
                        transactionId,
                        operationId,
                        transactionType,
                        commandDTO.getPaymentMethod(),
                        commandDTO.getTransactionCurrency(),
                        commandDTO.getTransactionAmount(),
                        routeResultDTO == null ? null : routeResultDTO.getChannelCode(),
                        routeResultDTO == null ? null : routeResultDTO.getMidConfigId(),
                        preparedChannelRequestDTO.getRequestId(),
                        preparedChannelRequestDTO.getChannelOrderNo(),
                        preparedChannelRequestDTO.getChannelTransactionId(),
                        endpointHost(routeResultDTO == null ? null : routeResultDTO.getRequestUrl()),
                        endpointPath(routeResultDTO == null ? null : routeResultDTO.getRequestUrl()));
                callChannel = true;
            }
        }
        enrichResult(commandDTO, null, resultDTO);
        int currencyExponent = resolveCurrencyExponent(commandDTO.getTransactionCurrency());
        transactionRecordService.recordInitialTransaction(
                commandDTO,
                routeResultDTO,
                preparedInvokeResultDTO,
                resultDTO,
                riskDecisionEnum,
                currencyExponent);
        if (isTerminal(resultDTO) || callChannel) {
            saveTransactionCreatedEvent(commandDTO, resultDTO);
        }
        if (isTerminal(resultDTO)) {
            lifecycleEventService.saveStatusChanged(
                    resultDTO.getTransactionId(),
                    resultDTO.getOperationId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    resultDTO.getTransactionType(),
                    resultDTO.getStatus(),
                    commandDTO.getTransactionDateTime());
        }
        completeIdempotency(idempotencyKey, merchantOrderFlowKey, commandDTO, resultDTO);
        log.info("event: PAYMENT_LOCAL_PREPARE_END stage=LOCAL_PREPARE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} paymentMethod: {} currency: {} amount: {} platformStatus: {} riskDecision: {} callChannel: {} channelCode: {} channelMidId: {} durationMs: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                resultDTO.getTransactionId(),
                resultDTO.getOperationId(),
                resultDTO.getTransactionType(),
                commandDTO.getPaymentMethod(),
                resultDTO.getCurrency(),
                resultDTO.getAmount(),
                resultDTO.getStatus(),
                riskDecisionEnum.getCode(),
                callChannel,
                routeResultDTO == null ? null : routeResultDTO.getChannelCode(),
                routeResultDTO == null ? null : routeResultDTO.getMidConfigId(),
                elapsedMillis(startNanos));

        PaymentInitialPreparationResultDTO target = new PaymentInitialPreparationResultDTO();
        target.setCallChannel(callChannel);
        target.setIdempotencyKey(idempotencyKey);
        target.setCommandDTO(commandDTO);
        target.setRouteResultDTO(routeResultDTO);
        target.setPreparedChannelRequestDTO(preparedInvokeResultDTO == null ? null : toPreparedChannelRequest(preparedInvokeResultDTO));
        target.setResultDTO(resultDTO);
        target.setRiskDecisionEnum(riskDecisionEnum);
        target.setCurrencyExponent(currencyExponent);
        return target;
    }

    /** 已有渠道身份表示上游完成过路由，准备阶段必须恢复同一 MID。 */
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
     * 占用商户订单支付流。首次插入依赖 scope/key 唯一索引，失败重试依赖 FAILED + version CAS。
     */
    private void acquireMerchantOrderFlow(PaymentCreateCommandDTO commandDTO,
                                          String transactionType,
                                          String merchantOrderFlowKey,
                                          LocalDateTime now) {
        TransactionIdempotencyDO flowRecord = transactionIdempotencyService.newProcessingRecord(
                MERCHANT_ORDER_FLOW_SCOPE,
                merchantOrderFlowKey,
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getMerchantOrderId(),
                transactionType,
                commandDTO.getTransactionDateTime(),
                DEFAULT_TIME_ZONE,
                commandDTO.getRequestFingerprint(),
                now);
        if (transactionIdempotencyService.tryBegin(flowRecord)) {
            return;
        }
        TransactionIdempotencyDO existing = transactionIdempotencyService
                .find(MERCHANT_ORDER_FLOW_SCOPE, merchantOrderFlowKey)
                .orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS));
        if (PaymentTransactionStatusEnum.FAILED.getCode().equals(existing.getTransactionStatus())
                && transactionIdempotencyService.tryRestartFailedFlow(existing, flowRecord)) {
            return;
        }
        throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                "merchant order number already has an active or successful payment flow");
    }

    /**
     * 解析首次支付幂等命中结果，同时核对商户订单流和操作级请求指纹。
     * <p>指纹不同或原请求仍在处理中必须拒绝，禁止把相同商户订单号用于不同金额、币种或支付工具。</p>
     */
    private PaymentInitialPreparationResultDTO resolveDuplicate(PaymentCreateCommandDTO commandDTO, String idempotencyKey) {
        TransactionIdempotencyDO existing = transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                .orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS));
        commandDTO.setRequestFingerprint(canonicalRequestFingerprint(commandDTO, commandDTO.getTransactionType()));
        if (StringUtils.hasText(existing.getRequestFingerprint())
                && !Objects.equals(existing.getRequestFingerprint(), commandDTO.getRequestFingerprint())) {
            log.warn("event: PAYMENT_IDEMPOTENCY_CONFLICT stage=IDEMPOTENCY_DUPLICATE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} idempotencyKey: {}",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    existing.getTransactionId(),
                    existing.getOperationId(),
                    existing.getTransactionType(),
                    idempotencyKey);
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                    "merchant order number already has a different payment request");
        }
        if (!StringUtils.hasText(existing.getTransactionId())) {
            log.warn("event: PAYMENT_IDEMPOTENCY_PROCESSING stage=IDEMPOTENCY_DUPLICATE traceId: {} merchantId: {} merchantOrderNo: {} transactionType: {} idempotencyKey: {}",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    existing.getTransactionType(),
                    idempotencyKey);
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                    "merchant order number is being processed");
        }
        log.info("event: PAYMENT_IDEMPOTENCY_HIT stage=IDEMPOTENCY_DUPLICATE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} platformStatus: {} idempotencyKey: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                existing.getTransactionId(),
                existing.getOperationId(),
                existing.getTransactionType(),
                existing.getTransactionStatus(),
                idempotencyKey);
        return PaymentInitialPreparationResultDTO.duplicate(toDuplicateResult(existing));
    }

    /**
     * 对商户、订单、交易类型、金额、币种、支付方式、来源交易和不可逆卡身份摘要生成稳定请求指纹。
     * <p>指纹不包含卡号明文，金额使用去尾零十进制文本以保持等值输入一致。</p>
     */
    private String canonicalRequestFingerprint(PaymentCreateCommandDTO commandDTO, String transactionType) {
        String cardIdentityHash = null;
        if (commandDTO.getCardInfo() != null && StringUtils.hasText(commandDTO.getCardInfo().getCardNo())) {
            cardIdentityHash = sha256("card:" + commandDTO.getCardInfo().getCardNo().trim());
        }
        String sourceTransactionId = commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getSourceTransactionId();
        String canonical = String.join("|",
                "v1",
                "merchantId=" + normalizeText(commandDTO.getMerchantId()),
                "merchantOrderNo=" + normalizeText(commandDTO.getMerchantOrderNo()),
                "transactionType=" + normalizeText(transactionType),
                "amount=" + normalizeAmount(commandDTO.getAmount()),
                "currency=" + normalizeCurrency(commandDTO.getCurrency()),
                "paymentMethod=" + normalizeText(commandDTO.getPaymentMethod()),
                "sourceTransactionId=" + normalizeText(sourceTransactionId),
                "cardIdentityHash=" + normalizeText(cardIdentityHash));
        return sha256(canonical);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "request fingerprint can not be calculated", exception);
        }
    }

    private PaymentCreateResultDTO buildInitialResult(PaymentCreateCommandDTO commandDTO,
                                                      String operationId,
                                                      String transactionId,
                                                      String transactionType) {
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setOperationId(operationId);
        resultDTO.setTransactionId(transactionId);
        resultDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        resultDTO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        resultDTO.setMerchantId(commandDTO.getMerchantId());
        resultDTO.setSubMerchantInfo(toResultSubMerchantInfo(commandDTO.getSubMerchantInfo()));
        resultDTO.setTransactionType(transactionType);
        return resultDTO;
    }

    private PaymentPreparedChannelRequestDTO prepareChannelRequest(PaymentCreateCommandDTO commandDTO,
                                                                  PaymentRouteResultDTO routeResultDTO,
                                                                  String operationId,
                                                                  String transactionId) {
        PaymentPreparedChannelRequestDTO prepared = new PaymentPreparedChannelRequestDTO();
        prepared.setRequestId(PaymentOrderNoGenerator.nextOrderNo(CHANNEL_REQUEST_ID_PREFIX, commandDTO.getTransactionDateTime()));
        PaymentCreateCommandDTO.ChannelIdentityDTO channelIdentity = commandDTO.getChannelIdentity();
        prepared.setChannelOrderNo(channelIdentity != null && StringUtils.hasText(channelIdentity.getChannelOrderNo())
                ? channelIdentity.getChannelOrderNo()
                : transactionId);
        prepared.setChannelTransactionId(channelIdentity != null && StringUtils.hasText(channelIdentity.getChannelTransactionId())
                ? channelIdentity.getChannelTransactionId()
                : PaymentOrderNoGenerator.nextOrderNo(CHANNEL_TRANSACTION_ID_PREFIX));
        return prepared;
    }

    private PaymentChannelInvokeResultDTO buildPreparedInvokeResult(PaymentCreateCommandDTO commandDTO,
                                                                    PaymentRouteResultDTO routeResultDTO,
                                                                    String operationId,
                                                                    String transactionId,
                                                                    PaymentPreparedChannelRequestDTO preparedChannelRequestDTO) {
        ChannelPaymentRequest channelRequest = new ChannelPaymentRequest();
        channelRequest.setChannelCode(routeResultDTO.getChannelCode());
        channelRequest.setOperationId(operationId);
        channelRequest.setTransactionId(transactionId);
        channelRequest.setSourceTransactionId(commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getSourceTransactionId());
        channelRequest.setChannelOrderNo(preparedChannelRequestDTO.getChannelOrderNo());
        channelRequest.setChannelTransactionId(preparedChannelRequestDTO.getChannelTransactionId());
        channelRequest.setMerchantId(commandDTO.getMerchantId());
        channelRequest.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        channelRequest.setMerchantOrderId(commandDTO.getMerchantOrderId());
        channelRequest.setTransactionType(commandDTO.getTransactionType());
        channelRequest.setPaymentMethod(commandDTO.getPaymentMethod());
        channelRequest.setAmount(commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount());
        channelRequest.setCurrency(commandDTO.getTransactionCurrency() == null ? commandDTO.getCurrency() : commandDTO.getTransactionCurrency());
        channelRequest.setTransactionDateTime(commandDTO.getTransactionDateTime());
        PaymentChannelInvokeResultDTO invokeResultDTO = new PaymentChannelInvokeResultDTO();
        invokeResultDTO.setRequestId(preparedChannelRequestDTO.getRequestId());
        invokeResultDTO.setChannelRequest(channelRequest);
        invokeResultDTO.setRequestStatus("INIT");
        invokeResultDTO.setHttpMethod("QUERY".equalsIgnoreCase(commandDTO.getTransactionType()) ? "GET" : "PUT");
        invokeResultDTO.setRequestScene("QUERY".equalsIgnoreCase(commandDTO.getTransactionType()) ? "RETRIEVE" : commandDTO.getTransactionType());
        invokeResultDTO.setRequestUrlMasked(resolveRequestUrl(routeResultDTO, channelRequest));
        invokeResultDTO.setRequestStartTime(LocalDateTime.now());
        return invokeResultDTO;
    }

    private PaymentPreparedChannelRequestDTO toPreparedChannelRequest(PaymentChannelInvokeResultDTO invokeResultDTO) {
        PaymentPreparedChannelRequestDTO prepared = new PaymentPreparedChannelRequestDTO();
        prepared.setRequestId(invokeResultDTO.getRequestId());
        prepared.setChannelOrderNo(invokeResultDTO.getChannelRequest().getChannelOrderNo());
        prepared.setChannelTransactionId(invokeResultDTO.getChannelRequest().getChannelTransactionId());
        return prepared;
    }

    private String resolveRequestUrl(PaymentRouteResultDTO routeResultDTO, ChannelPaymentRequest request) {
        if (routeResultDTO == null || routeResultDTO.getRequestUrl() == null || routeResultDTO.getRequestUrl().isBlank()) {
            return null;
        }
        String baseUrl = routeResultDTO.getRequestUrl().endsWith("/") ? routeResultDTO.getRequestUrl() : routeResultDTO.getRequestUrl() + "/";
        return baseUrl + "order/" + request.getChannelOrderNo() + "/transaction/" + request.getChannelTransactionId();
    }

    /**
     * 计算日志耗时。
     *
     * @param startNanos 起始纳秒时间
     * @return 耗时，单位毫秒
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 脱敏 MID 或类似渠道商户号。
     *
     * @param value 原始 MID
     * @return 可写日志的 MID 摘要
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
     * 提取渠道 endpoint 主机。
     *
     * @param url 渠道基础 URL
     * @return 主机名；URL 非法时返回 invalid_url
     */
    private String endpointHost(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException exception) {
            return "invalid_url";
        }
    }

    /**
     * 提取渠道 endpoint path。
     *
     * @param url 渠道基础 URL
     * @return path；URL 非法时返回 invalid_url
     */
    private String endpointPath(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return URI.create(url).getPath();
        } catch (RuntimeException exception) {
            return "invalid_url";
        }
    }

    /** 固化商户标签币种和标签金额；未显式传入时与原请求金额币种一致，后续换汇不得覆盖该口径。 */
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
     * 按路由目标币种和交易时点汇率生成交易币种金额快照。
     * <p>
     * 仅最终汇率为正时执行一次 HALF_UP 目标币种舍入；缺失汇率时以明确失败终态落库并禁止调用渠道，
     * 不允许用 1:1 或最新汇率兜底。
     * </p>
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

    /**
     * 汇总支付准备结果：复制订单和换汇字段、生成商户可见响应，并初始化累计金额。
     *
     * @param commandDTO      已完成本地准备的支付命令
     * @param channelResponse 渠道响应，允许为空
     * @param resultDTO       待补全的支付结果
     */
    private void enrichResult(PaymentCreateCommandDTO commandDTO,
                              com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse channelResponse,
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
        resultDTO.setDescription(commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getDescription());
        resultDTO.setCallbackUrl(resolveCallbackUrl(commandDTO));
        resultDTO.setMerchantWebsite(commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getMerchantWebsite());
        resultDTO.setRedirectUrl(commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getRedirectUrl());
        resultDTO.setLanguage(commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getLanguage());
        enrichMerchantResponse(resultDTO, channelResponse);
        if (PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus())) {
            resultDTO.setFailReasonMessage(merchantVisibleFailureMessage(resultDTO.getStatus(), resultDTO.getFailReasonCode()));
        }
        fillInitialTotals(resultDTO);
    }

    /** 同时完成操作级和商户订单流两层幂等快照，确保重复请求复用同一交易结果。 */
    private void completeIdempotency(String idempotencyKey,
                                     String merchantOrderFlowKey,
                                     PaymentCreateCommandDTO commandDTO,
                                     PaymentCreateResultDTO resultDTO) {
        transactionIdempotencyService.complete(
                TRANSACTION_OPERATION_SCOPE,
                idempotencyKey,
                resultDTO.getOperationId(),
                resultDTO.getTransactionId(),
                resultDTO.getStatus(),
                commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount(),
                resultDTO.getCurrency(),
                JsonUtils.toJsonString(resultDTO));
        transactionIdempotencyService.complete(
                MERCHANT_ORDER_FLOW_SCOPE,
                merchantOrderFlowKey,
                resultDTO.getOperationId(),
                resultDTO.getTransactionId(),
                resultDTO.getStatus(),
                commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount(),
                resultDTO.getCurrency(),
                JsonUtils.toJsonString(resultDTO));
    }

    /** 将首次支付创建事件写入以 operationId 分组的交易 FIFO Outbox，与交易事实保持同事务。 */
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
    }

    private PaymentCreateResultDTO toDuplicateResult(TransactionIdempotencyDO record) {
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

    /** 以幂等事实中的最新终态覆盖可能仍为 PROCESSING 的历史响应快照。 */
    private void applyLatestIdempotencyStatus(PaymentCreateResultDTO resultDTO, String transactionStatus) {
        if (!StringUtils.hasText(transactionStatus)) {
            return;
        }
        resultDTO.setStatus(transactionStatus);
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(transactionStatus)) {
            resultDTO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
            resultDTO.setMerchantResponseCode(ApiResultEnum.PAYMENT_SUCCESS.getCode());
            resultDTO.setMerchantResponseMessage(ApiResultEnum.PAYMENT_SUCCESS.getMessage());
        } else if (PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)) {
            resultDTO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
            resultDTO.setMerchantResponseCode(ApiResultEnum.PAYMENT_REJECTED.getCode());
            resultDTO.setMerchantResponseMessage(ApiResultEnum.PAYMENT_REJECTED.getMessage());
        }
    }

    private void enrichMerchantResponse(PaymentCreateResultDTO resultDTO,
                                        com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse response) {
        resultDTO.setMerchantResponseCode(resolveMerchantResponseCode(resultDTO.getStatus()));
        resultDTO.setMerchantResponseMessage(resolveMerchantResponseMessage(resultDTO, response));
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

    private String resolveMerchantResponseMessage(PaymentCreateResultDTO resultDTO,
                                                  com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse response) {
        if (resultDTO == null) {
            return resolveMerchantResponseMessage((String) null);
        }
        if (!PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus())) {
            return resolveMerchantResponseMessage(resultDTO.getStatus());
        }
        if (PaymentRiskDecisionSupport.isRiskRejected(resultDTO.getFailReasonCode())) {
            return PaymentRiskDecisionSupport.MERCHANT_RISK_BLOCKED_MESSAGE;
        }
        if (response == null || "ERROR".equalsIgnoreCase(response.getRawChannelStatus())) {
            return ApiResultEnum.PAYMENT_REJECTED.getMessage();
        }
        return firstText(joinCodeAndMessage(response.getChannelResponseCode(), response.getChannelResponseMessage()),
                ApiResultEnum.PAYMENT_REJECTED.getMessage());
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
     * 根据交易状态和受控失败码生成商户可见失败说明，不暴露渠道或内部异常细节。
     * @param transactionStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param failReasonCode 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
     * @return 当前方法生成或规范化后的文本值
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

    private void fillInitialTotals(PaymentCreateResultDTO resultDTO) {
        if (!PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                || resultDTO.getTransactionAmount() == null) {
            return;
        }
        if (PaymentTransactionTypeEnum.AUTHORIZATION.getCode().equals(resultDTO.getTransactionType())
                || PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode().equals(resultDTO.getTransactionType())) {
            resultDTO.setTotalAuthorizedAmount(resultDTO.getTransactionAmount());
            return;
        }
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(resultDTO.getTransactionType())) {
            resultDTO.setTotalAuthorizedAmount(resultDTO.getTransactionAmount());
            resultDTO.setTotalCapturedAmount(resultDTO.getTransactionAmount());
        }
    }

    private String resolvePaymentBrand(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO.getTransactionInfo() != null && StringUtils.hasText(commandDTO.getTransactionInfo().getCardBrand())) {
            return commandDTO.getTransactionInfo().getCardBrand();
        }
        if (commandDTO.getCardInfo() == null || !StringUtils.hasText(commandDTO.getCardInfo().getCardNo())) {
            return null;
        }
        String digits = commandDTO.getCardInfo().getCardNo().replaceAll("\\D", "");
        return PaymentCardBrandRuleMatcher.resolve(digits);
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
        return value == null || java.util.stream.Stream.of(
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
     * 解析回调地址，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 仅返回规范化或计算结果，不直接提交交易状态。
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

    private int resolveCurrencyExponent(String currency) {
        IsoCurrencyInfo currencyInfo = isoDictionaryService.getCurrency(currency)
                .orElseThrow(() -> new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "currency can not be resolved"));
        if (currencyInfo.defaultFractionDigits() < 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "currency fraction digits can not be resolved");
        }
        return currencyInfo.defaultFractionDigits();
    }

    /** 按 ISO 4217 币种精度精确转换最小货币单位，超精度或 long 溢出直接失败。 */
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

    private BigDecimal defaultTransactionRate() {
        return new BigDecimal("1.00000000");
    }

    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime, String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone == null || timeZone.isBlank() ? DEFAULT_TIME_ZONE : timeZone);
        return transactionDateTime.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
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

    private String joinCodeAndMessage(String code, String message) {
        if (StringUtils.hasText(code) && StringUtils.hasText(message)) {
            return code + ": " + message;
        }
        return firstText(code, message);
    }
}
