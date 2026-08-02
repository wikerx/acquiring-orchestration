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
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.mq.TransactionMqConstants;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.PaymentTransactionPreparationService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import com.scott.payment.payment.service.dto.PaymentInitialPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
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
@DS(DataSourceName.TRANSACTION)
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

    /**
     * ISO Dictionary Service 依赖，用于 Default Payment Transaction Preparation Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final IsoDictionaryService isoDictionaryService;

    /**
     * payment Risk Invoke Service 依赖，用于 Default Payment Transaction Preparation Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final PaymentRiskInvokeService paymentRiskInvokeService;

    /**
     * payment Channel Route Service 依赖，用于 Default Payment Transaction Preparation Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final PaymentChannelRouteService paymentChannelRouteService;

    /**
     * payment Exchange Rate Service 依赖，用于 Default Payment Transaction Preparation Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final PaymentExchangeRateService paymentExchangeRateService;

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionIdempotencyService transactionIdempotencyService;

    /**
     * transaction Event Outbox Service 依赖，用于 Default Payment Transaction Preparation Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionEventOutboxService transactionEventOutboxService;

    /**
     * transaction Record Service 依赖，用于 Default Payment Transaction Preparation Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
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
        this.isoDictionaryService = isoDictionaryService;
        this.paymentRiskInvokeService = paymentRiskInvokeService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.paymentExchangeRateService = paymentExchangeRateService;
        this.transactionIdempotencyService = transactionIdempotencyService;
        this.transactionEventOutboxService = transactionEventOutboxService;
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
    @Transactional(rollbackFor = Exception.class)
    public PaymentInitialPreparationResultDTO prepareInitialTransaction(PaymentCreateCommandDTO commandDTO, String transactionType) {
        long startNanos = System.nanoTime();
        String idempotencyKey = transactionIdempotencyService.buildInitialTransactionKey(
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
        log.info("event: PAYMENT_IDEMPOTENCY_BEGIN_OK stage=IDEMPOTENCY_BEGIN traceId: {} merchantId: {} merchantOrderNo: {} transactionType: {} idempotencyKey: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                transactionType,
                idempotencyKey);
        validateExistingInitialFlow(commandDTO);
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
            routeResultDTO = paymentChannelRouteService.route(commandDTO);
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
        if (isTerminal(resultDTO)) {
            saveTransactionCreatedEvent(commandDTO, resultDTO);
        } else if (callChannel) {
            saveTransactionCreatedEvent(commandDTO, resultDTO);
        }
        completeIdempotency(idempotencyKey, commandDTO, resultDTO);
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

    /**
     * 校验同一商户订单下是否已有有效初始支付流程。
     * <p>
     * 前置条件：调用方已完成商户号和商户订单号必填校验。
     * 该方法按 merchantId + merchantOrderNo 查询历史初始动作单，FAILED 记录允许重新发起，其它状态视为仍占用商户订单号；
     * 命中有效流程时抛出幂等冲突异常，避免同一商户订单生成多条活跃支付链路。
     * </p>
     * @param commandDTO 支付创建命令，提供商户号和商户订单号
     */
    private void validateExistingInitialFlow(PaymentCreateCommandDTO commandDTO) {
        if (transactionRecordService == null) {
            return;
        }
        List<TransactionOperationDO> operations = transactionRecordService.findInitialOperationsByMerchantOrder(
                commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo());
        for (TransactionOperationDO operationDO : operations) {
            if (operationDO == null || PaymentTransactionStatusEnum.FAILED.getCode().equals(operationDO.getTransactionStatus())) {
                continue;
            }
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                    "merchant order number already has an active payment flow");
        }
    }

    /**
     * 解析支付创建幂等命中结果。
     * <p>
     * 前置条件：当前请求的 operation 幂等键已在幂等表中存在。
     * 该方法重新计算请求指纹并与历史记录比对；指纹不同返回冲突，历史交易号尚未落入幂等记录时返回处理中，
     * 指纹一致且已有交易号时返回重复请求对应的交易结果快照。
     * </p>
     * @param commandDTO 当前支付创建命令，用于计算请求指纹和构造重复响应
     * @param idempotencyKey 平台生成的交易创建幂等键
     * @return 幂等命中后的准备结果，标记为 duplicate
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
     * 整理规范化请求指纹，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param transactionType transaction Type 输入值，参与 交易type 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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

    /**
     * 解析normalize文本，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 解析normalize金额，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param amount 金额值，单位必须结合 currency 或同名币种字段解释
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    /**
     * 计算sha256摘要，用不可逆指纹关联原始内容而不暴露明文。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "request fingerprint can not be calculated", exception);
        }
    }

/**
 * 构造初始结果对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param operationId 平台操作号，用于定位单次授权、请款、退款、撤销或通知动作
 * @param transactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
 * @param transactionType transaction Type 输入值，参与 交易type 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
 */
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

/**
 * 整理prepare渠道请求，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param routeResultDTO route Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param operationId 平台操作号，用于定位单次授权、请款、退款、撤销或通知动作
 * @param transactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
 */
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

/**
 * 构造preparedinvokeresult对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param routeResultDTO route Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param operationId 平台操作号，用于定位单次授权、请款、退款、撤销或通知动作
 * @param transactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
 * @param preparedChannelRequestDTO prepared Channel Request DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @return 构造、转换或解析后的业务值
 */
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

    /**
     * 构造prepared渠道request对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param invokeResultDTO invoke Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private PaymentPreparedChannelRequestDTO toPreparedChannelRequest(PaymentChannelInvokeResultDTO invokeResultDTO) {
        PaymentPreparedChannelRequestDTO prepared = new PaymentPreparedChannelRequestDTO();
        prepared.setRequestId(invokeResultDTO.getRequestId());
        prepared.setChannelOrderNo(invokeResultDTO.getChannelRequest().getChannelOrderNo());
        prepared.setChannelTransactionId(invokeResultDTO.getChannelRequest().getChannelTransactionId());
        return prepared;
    }

    /**
     * 解析resolve请求url，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param routeResultDTO route Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 整理initializelabel金额，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
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
 * 应用应用币种换汇，把校验后的配置、金额、状态或字段值写入目标对象。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param routeResultDTO route Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 应用应用no换汇，把校验后的配置、金额、状态或字段值写入目标对象。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
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
        resultDTO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        resultDTO.setPaymentMethod(commandDTO.getPaymentMethod());
        resultDTO.setPaymentBrand(resolvePaymentBrand(commandDTO));
        resultDTO.setCardBin(resolveCardBin(commandDTO));
        resultDTO.setDescription(commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getDescription());
        resultDTO.setCallbackUrl(resolveCallbackUrl(commandDTO));
        enrichMerchantResponse(resultDTO, channelResponse);
        if (PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus())) {
            resultDTO.setFailReasonMessage(merchantVisibleFailureMessage(resultDTO.getStatus(), resultDTO.getFailReasonCode()));
        }
        fillInitialTotals(resultDTO);
    }

    /**
     * 规范化completeidempotency，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param idempotencyKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
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
     * 创建交易createdevent，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 支付核心服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
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
     * 构造重复请求结果对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param record record 输入值，参与 记录 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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

/**
 * 构造商户响应对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
 */
    private void enrichMerchantResponse(PaymentCreateResultDTO resultDTO,
                                        com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse response) {
        resultDTO.setMerchantResponseCode(resolveMerchantResponseCode(resultDTO.getStatus()));
        resultDTO.setMerchantResponseMessage(resolveMerchantResponseMessage(resultDTO, response));
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
 * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
 * @return 构造、转换或解析后的业务值
 */
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
     * 构造initialtotals对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
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

    /**
     * 解析resolvepayment品牌，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
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
     * 解析resolvecardBIN，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
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
     * 构造子商户响应信息对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
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
     * 判断 is empty sub merchant info 条件是否成立，用于控制 Default Payment Transaction Preparation Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 条件满足时返回 true，否则返回 false
     */
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
     * 解析resolve币种小数位，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param currency 币种代码，格式为 ISO 4217 三位大写字母
     * @return 构造、转换或解析后的业务值
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
     * 构造minor金额对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param amount 金额值，单位必须结合 currency 或同名币种字段解释
     * @param currency 币种代码，格式为 ISO 4217 三位大写字母
     * @return 构造、转换或解析后的业务值
     */
    private Long toMinorAmount(BigDecimal amount, String currency) {
        try {
            return isoDictionaryService.toMinorUnit(amount, currency);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "amount fraction digits exceed currency minor unit", exception);
        }
    }

    /**
     * 判断 is terminal 条件是否成立，用于控制 Default Payment Transaction Preparation Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isTerminal(PaymentCreateResultDTO resultDTO) {
        return resultDTO != null
                && (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus()));
    }

    /**
     * 整理默认交易汇率，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private BigDecimal defaultTransactionRate() {
        return new BigDecimal("1.00000000");
    }

    /**
     * 解析normalize币种，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param currency 币种代码，格式为 ISO 4217 三位大写字母
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
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
     * 整理拼接编码and说明，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String joinCodeAndMessage(String code, String message) {
        if (StringUtils.hasText(code) && StringUtils.hasText(message)) {
            return code + ": " + message;
        }
        return firstText(code, message);
    }
}
