package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentPendingReasonEnum;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private final IsoDictionaryService isoDictionaryService;

    private final PaymentRiskInvokeService paymentRiskInvokeService;

    private final PaymentChannelRouteService paymentChannelRouteService;

    private final PaymentExchangeRateService paymentExchangeRateService;

    private final TransactionIdempotencyService transactionIdempotencyService;

    private final TransactionEventOutboxService transactionEventOutboxService;

    private final TransactionRecordService transactionRecordService;

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
        String idempotencyKey = transactionIdempotencyService.buildInitialTransactionKey(
                commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo());
        commandDTO.setRequestFingerprint(canonicalRequestFingerprint(commandDTO, transactionType));
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
            return resolveDuplicate(commandDTO, idempotencyKey);
        }
        validateExistingInitialFlow(commandDTO);
        String operationId = PaymentOrderNoGenerator.nextOrderNo(OPERATION_ID_PREFIX, commandDTO.getTransactionDateTime());
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        initializeLabelAmount(commandDTO);
        PaymentRiskDecisionDTO riskDecisionDTO = paymentRiskInvokeService.checkPreRoute(commandDTO);
        PaymentRiskDecisionEnum riskDecisionEnum = resolveRiskDecision(riskDecisionDTO);
        PaymentCreateResultDTO resultDTO = buildInitialResult(commandDTO, operationId, transactionId, transactionType);
        PaymentRouteResultDTO routeResultDTO = null;
        PaymentChannelInvokeResultDTO preparedInvokeResultDTO = null;
        boolean callChannel = false;
        if (!riskDecisionEnum.isAllowProceed()) {
            applyNoConversion(commandDTO);
            resultDTO.setCurrency(commandDTO.getTransactionCurrency());
            resultDTO.setAmount(toMinorAmount(commandDTO.getTransactionAmount(), commandDTO.getTransactionCurrency()));
            fillRiskStoppedResult(resultDTO, riskDecisionEnum);
        } else {
            routeResultDTO = paymentChannelRouteService.route(commandDTO);
            if (!applyCurrencyConversion(commandDTO, routeResultDTO, resultDTO)) {
                // resultDTO 已按缺失汇率填充为 FAILED。
            } else {
                resultDTO.setCurrency(commandDTO.getTransactionCurrency());
                resultDTO.setAmount(toMinorAmount(commandDTO.getTransactionAmount(), commandDTO.getTransactionCurrency()));
                resultDTO.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
                resultDTO.setProcessStage(PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode());
                PaymentPreparedChannelRequestDTO preparedChannelRequestDTO = prepareChannelRequest(commandDTO, routeResultDTO, operationId, transactionId);
                preparedInvokeResultDTO = buildPreparedInvokeResult(commandDTO, routeResultDTO, operationId, transactionId, preparedChannelRequestDTO);
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

    private PaymentInitialPreparationResultDTO resolveDuplicate(PaymentCreateCommandDTO commandDTO, String idempotencyKey) {
        TransactionIdempotencyDO existing = transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                .orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS));
        commandDTO.setRequestFingerprint(canonicalRequestFingerprint(commandDTO, commandDTO.getTransactionType()));
        if (StringUtils.hasText(existing.getRequestFingerprint())
                && !Objects.equals(existing.getRequestFingerprint(), commandDTO.getRequestFingerprint())) {
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                    "merchant order number already has a different payment request");
        }
        if (!StringUtils.hasText(existing.getTransactionId())) {
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                    "merchant order number is being processed");
        }
        return PaymentInitialPreparationResultDTO.duplicate(toDuplicateResult(existing));
    }

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
        prepared.setChannelOrderNo(transactionId);
        prepared.setChannelTransactionId(PaymentOrderNoGenerator.nextOrderNo(CHANNEL_TRANSACTION_ID_PREFIX));
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

    private String merchantVisibleFailureMessage(String transactionStatus, String failReasonCode) {
        if (!PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)
                || !StringUtils.hasText(failReasonCode)) {
            return null;
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
