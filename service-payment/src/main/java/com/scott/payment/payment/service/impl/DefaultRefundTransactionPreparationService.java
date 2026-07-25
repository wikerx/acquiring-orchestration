package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.domain.state.TransactionStateMachineService;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.mq.TransactionMqConstants;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.RefundTransactionPreparationService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.RefundPreparationResultDTO;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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
 * @classname : DefaultRefundTransactionPreparationService
 * @date : 2026-07-24 00:00
 * @description : Refund 本地准备默认实现，确保渠道 Refund 调用前提交幂等、动作单、渠道请求 INIT 和恢复入口。
 * @status : create
 */
@Service
public class DefaultRefundTransactionPreparationService implements RefundTransactionPreparationService {

    private static final String CHANNEL_REQUEST_ID_PREFIX = "CR";

    private static final String CHANNEL_TRANSACTION_ID_PREFIX = "CH";

    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";

    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    private static final String PAYMENT_TRANSACTION_AGGREGATE = "PAYMENT_TRANSACTION";

    private static final String EVENT_STATUS_INIT = "INIT";

    private static final int DEFAULT_EVENT_MAX_RETRY_COUNT = 200;

    private static final int INITIAL_VERSION = 0;

    private static final int NOT_DELETED = 0;

    private final IsoDictionaryService isoDictionaryService;

    private final PaymentChannelRouteService paymentChannelRouteService;

    private final TransactionIdempotencyService transactionIdempotencyService;

    private final TransactionEventOutboxService transactionEventOutboxService;

    private final TransactionRecordService transactionRecordService;

    private final TransactionStateMachineService transactionStateMachineService;

    public DefaultRefundTransactionPreparationService(IsoDictionaryService isoDictionaryService,
                                                      PaymentChannelRouteService paymentChannelRouteService,
                                                      TransactionIdempotencyService transactionIdempotencyService,
                                                      TransactionEventOutboxService transactionEventOutboxService,
                                                      TransactionRecordService transactionRecordService,
                                                      TransactionStateMachineService transactionStateMachineService) {
        this.isoDictionaryService = isoDictionaryService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.transactionIdempotencyService = transactionIdempotencyService;
        this.transactionEventOutboxService = transactionEventOutboxService;
        this.transactionRecordService = transactionRecordService;
        this.transactionStateMachineService = transactionStateMachineService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundPreparationResultDTO prepareRefund(PaymentCreateCommandDTO commandDTO, String idempotencyKey) {
        if (transactionRecordService == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderDO resolvedSourceOrderDO = resolveSourceOrder(commandDTO);
        TransactionOrderDO sourceOrderDO = lockSourceOrder(resolvedSourceOrderDO);
        commandDTO.setRequestFingerprint(canonicalRefundRequestFingerprint(commandDTO, sourceOrderDO));
        return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                .map(record -> RefundPreparationResultDTO.duplicate(resolveDuplicateRefund(commandDTO, record)))
                .orElseGet(() -> prepareNewRefund(commandDTO, idempotencyKey, sourceOrderDO));
    }

    private RefundPreparationResultDTO prepareNewRefund(PaymentCreateCommandDTO commandDTO,
                                                        String idempotencyKey,
                                                        TransactionOrderDO sourceOrderDO) {
        if (transactionStateMachineService == null) {
            throw new ServiceException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED.getCode(), "transaction state machine is not configured");
        }
        transactionStateMachineService.validateFollowUpAction(
                sourceOrderDO, PaymentTransactionTypeEnum.REFUND, commandDTO.getAmount(), commandDTO.getCurrency());
        validateRefundCapacity(commandDTO, sourceOrderDO, LocalDateTime.now());
        validateNoNonTerminalVoid(commandDTO, sourceOrderDO, LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();
        TransactionIdempotencyDO beginRecord = transactionIdempotencyService.newProcessingRecord(
                TRANSACTION_OPERATION_SCOPE,
                idempotencyKey,
                commandDTO.getMerchantId(),
                sourceOrderDO.getMerchantOrderNo(),
                commandDTO.getMerchantOrderId(),
                PaymentTransactionTypeEnum.REFUND.getCode(),
                commandDTO.getTransactionDateTime(),
                DEFAULT_TIME_ZONE,
                commandDTO.getRequestFingerprint(),
                now);
        if (!transactionIdempotencyService.tryBegin(beginRecord)) {
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(record -> RefundPreparationResultDTO.duplicate(resolveDuplicateRefund(commandDTO, record)))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS));
        }
        TransactionOperationDO sourceOperationDO = transactionRecordService.findSourceOperationByTransactionId(
                commandDTO.getTransactionInfo().getSourceTransactionId());
        normalizeRefundCommand(commandDTO, sourceOrderDO, sourceOperationDO);
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        PaymentRouteResultDTO routeResultDTO = paymentChannelRouteService.route(commandDTO);
        PaymentCreateResultDTO resultDTO = buildRefundResult(commandDTO, sourceOrderDO, transactionId);
        resultDTO.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        resultDTO.setProcessStage(PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode());
        enrichRefundResult(commandDTO, sourceOrderDO, routeResultDTO, null, resultDTO);
        int currencyExponent = resolveCurrencyExponent(sourceOrderDO.getTransactionCurrency());
        PaymentPreparedChannelRequestDTO preparedChannelRequestDTO = prepareChannelRequest(commandDTO, sourceOrderDO);
        PaymentChannelInvokeResultDTO preparedInvokeResultDTO = buildPreparedInvokeResult(
                commandDTO, routeResultDTO, sourceOrderDO.getOperationId(), transactionId, preparedChannelRequestDTO);
        recordRefundPreparedFact(commandDTO, sourceOrderDO, routeResultDTO, preparedInvokeResultDTO, resultDTO, currencyExponent);
        saveTransactionCreatedEvent(commandDTO, resultDTO);
        completeIdempotency(idempotencyKey, commandDTO, resultDTO);

        RefundPreparationResultDTO target = new RefundPreparationResultDTO();
        target.setCallChannel(true);
        target.setIdempotencyKey(idempotencyKey);
        target.setCommandDTO(commandDTO);
        target.setSourceOrderDO(sourceOrderDO);
        target.setRouteResultDTO(routeResultDTO);
        target.setPreparedChannelRequestDTO(preparedChannelRequestDTO);
        target.setResultDTO(resultDTO);
        target.setCurrencyExponent(currencyExponent);
        return target;
    }

    private TransactionOrderDO resolveSourceOrder(PaymentCreateCommandDTO commandDTO) {
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfoDTO = commandDTO.getTransactionInfo();
        String sourceTransactionId = transactionInfoDTO.getSourceTransactionId();
        TransactionOrderDO sourceOrderDO = transactionRecordService.findSourceOrderByTransactionId(sourceTransactionId);
        if (sourceOrderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return sourceOrderDO;
    }

    private TransactionOrderDO lockSourceOrder(TransactionOrderDO sourceOrderDO) {
        if (sourceOrderDO == null
                || !StringUtils.hasText(sourceOrderDO.getOperationId())
                || sourceOrderDO.getTransactionDateTime() == null) {
            return sourceOrderDO;
        }
        return transactionRecordService.lockOrder(sourceOrderDO.getTransactionDateTime(), sourceOrderDO.getOperationId());
    }

    private void validateRefundCapacity(PaymentCreateCommandDTO commandDTO,
                                        TransactionOrderDO sourceOrderDO,
                                        LocalDateTime now) {
        BigDecimal requestAmount = commandDTO.getAmount();
        BigDecimal availableRefundAmount = sourceOrderDO.getAvailableRefundAmount() == null
                ? BigDecimal.ZERO : sourceOrderDO.getAvailableRefundAmount();
        BigDecimal pendingRefundAmount = sumNonTerminalRefundAmount(commandDTO, sourceOrderDO, now);
        if (availableRefundAmount.subtract(pendingRefundAmount).compareTo(requestAmount) < 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "REFUND amount exceeds available amount");
        }
    }

    private BigDecimal sumNonTerminalRefundAmount(PaymentCreateCommandDTO commandDTO,
                                                  TransactionOrderDO sourceOrderDO,
                                                  LocalDateTime now) {
        LocalDateTime beginTime = sourceOrderDO.getTransactionDateTime();
        LocalDateTime endTime = laterOf(now, commandDTO.getTransactionDateTime());
        List<TransactionOperationDO> nonTerminalRefunds = transactionRecordService.findNonTerminalRefunds(
                commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), beginTime, endTime);
        return nonTerminalRefunds.stream()
                .map(TransactionOperationDO::getTransactionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateNoNonTerminalVoid(PaymentCreateCommandDTO commandDTO,
                                           TransactionOrderDO sourceOrderDO,
                                           LocalDateTime now) {
        if (!PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            return;
        }
        LocalDateTime beginTime = sourceOrderDO.getTransactionDateTime();
        LocalDateTime endTime = laterOf(now, commandDTO.getTransactionDateTime());
        List<TransactionOperationDO> nonTerminalVoids = transactionRecordService.findNonTerminalVoids(
                commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), beginTime, endTime);
        if (nonTerminalVoids.isEmpty()) {
            return;
        }
        throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                "source transaction has a pending void action");
    }

    private LocalDateTime laterOf(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private PaymentCreateResultDTO resolveDuplicateRefund(PaymentCreateCommandDTO commandDTO,
                                                          TransactionIdempotencyDO record) {
        if (StringUtils.hasText(record.getRequestFingerprint())
                && !Objects.equals(record.getRequestFingerprint(), commandDTO.getRequestFingerprint())) {
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                    "merchant operation number already has a different request");
        }
        if (!StringUtils.hasText(record.getTransactionId())) {
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                    "merchant operation is being processed");
        }
        return toDuplicateResult(record);
    }

    private String canonicalRefundRequestFingerprint(PaymentCreateCommandDTO commandDTO,
                                                     TransactionOrderDO sourceOrderDO) {
        String sourceTransactionId = commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getSourceTransactionId();
        String effectiveCurrency = StringUtils.hasText(commandDTO.getCurrency())
                ? commandDTO.getCurrency()
                : sourceOrderDO == null ? null : sourceOrderDO.getTransactionCurrency();
        String canonical = String.join("|",
                "v1",
                "merchantId=" + normalizeFingerprintText(commandDTO.getMerchantId()),
                "merchantOrderNo=" + normalizeFingerprintText(sourceOrderDO == null
                        ? commandDTO.getMerchantOrderNo()
                        : sourceOrderDO.getMerchantOrderNo()),
                "merchantOperationNo=" + normalizeFingerprintText(commandDTO.getMerchantOrderId()),
                "transactionType=" + PaymentTransactionTypeEnum.REFUND.getCode(),
                "sourceTransactionId=" + normalizeFingerprintText(sourceTransactionId),
                "amount=" + normalizeFingerprintAmount(commandDTO.getAmount()),
                "currency=" + normalizeFingerprintText(effectiveCurrency));
        return sha256(canonical);
    }

    private String normalizeFingerprintText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeFingerprintAmount(BigDecimal amount) {
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
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "request fingerprint can not be calculated", exception);
        }
    }

    private void normalizeRefundCommand(PaymentCreateCommandDTO commandDTO,
                                        TransactionOrderDO sourceOrderDO,
                                        TransactionOperationDO sourceOperationDO) {
        validateRefundMerchantOrderNo(commandDTO, sourceOrderDO);
        commandDTO.setMerchantOrderNo(sourceOrderDO.getMerchantOrderNo());
        commandDTO.setLabelCurrency(StringUtils.hasText(commandDTO.getCurrency())
                ? normalizeCurrency(commandDTO.getCurrency())
                : sourceOrderDO.getTransactionCurrency());
        commandDTO.setLabelAmount(commandDTO.getAmount());
        commandDTO.setCurrency(sourceOrderDO.getTransactionCurrency());
        if (commandDTO.getTransactionInfo() != null && sourceOperationDO != null) {
            commandDTO.getTransactionInfo().setSourceChannelTransactionId(sourceOperationDO.getChannelTransactionId());
        }
        commandDTO.setTransactionCurrency(sourceOrderDO.getTransactionCurrency());
        commandDTO.setTransactionAmount(commandDTO.getAmount());
        commandDTO.setTransactionRate(defaultTransactionRate());
        commandDTO.setRateSource(null);
        commandDTO.setRateTime(null);
        commandDTO.setDccEnabled(0);
        commandDTO.setEdcEnabled(0);
    }

    private void validateRefundMerchantOrderNo(PaymentCreateCommandDTO commandDTO, TransactionOrderDO sourceOrderDO) {
        if (!StringUtils.hasText(commandDTO.getMerchantOrderNo())) {
            return;
        }
        if (!commandDTO.getMerchantOrderNo().equals(sourceOrderDO.getMerchantOrderNo())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "merchant order number must match source transaction");
        }
    }

    private PaymentCreateResultDTO buildRefundResult(PaymentCreateCommandDTO commandDTO,
                                                     TransactionOrderDO sourceOrderDO,
                                                     String transactionId) {
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setOperationId(sourceOrderDO.getOperationId());
        resultDTO.setTransactionId(transactionId);
        resultDTO.setSourceTransactionId(commandDTO.getTransactionInfo().getSourceTransactionId());
        resultDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        resultDTO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        resultDTO.setMerchantId(commandDTO.getMerchantId());
        resultDTO.setSubMerchantInfo(toResultSubMerchantInfo(commandDTO.getSubMerchantInfo()));
        resultDTO.setTransactionType(PaymentTransactionTypeEnum.REFUND.getCode());
        resultDTO.setCurrency(sourceOrderDO.getTransactionCurrency());
        resultDTO.setAmount(toMinorAmount(commandDTO.getAmount(), sourceOrderDO.getTransactionCurrency()));
        return resultDTO;
    }

    private PaymentPreparedChannelRequestDTO prepareChannelRequest(PaymentCreateCommandDTO commandDTO,
                                                                  TransactionOrderDO sourceOrderDO) {
        PaymentPreparedChannelRequestDTO prepared = new PaymentPreparedChannelRequestDTO();
        prepared.setRequestId(PaymentOrderNoGenerator.nextOrderNo(CHANNEL_REQUEST_ID_PREFIX, commandDTO.getTransactionDateTime()));
        prepared.setChannelOrderNo(resolveChannelOrderNo(sourceOrderDO));
        prepared.setChannelTransactionId(PaymentOrderNoGenerator.nextOrderNo(CHANNEL_TRANSACTION_ID_PREFIX));
        return prepared;
    }

    private PaymentChannelInvokeResultDTO buildPreparedInvokeResult(PaymentCreateCommandDTO commandDTO,
                                                                    PaymentRouteResultDTO routeResultDTO,
                                                                    String operationId,
                                                                    String transactionId,
                                                                    PaymentPreparedChannelRequestDTO preparedChannelRequestDTO) {
        com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest channelRequest =
                new com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest();
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
        invokeResultDTO.setHttpMethod("PUT");
        invokeResultDTO.setRequestScene(PaymentTransactionTypeEnum.REFUND.getCode());
        invokeResultDTO.setRequestStartTime(LocalDateTime.now());
        return invokeResultDTO;
    }

    private void recordRefundPreparedFact(PaymentCreateCommandDTO commandDTO,
                                          TransactionOrderDO sourceOrderDO,
                                          PaymentRouteResultDTO routeResultDTO,
                                          PaymentChannelInvokeResultDTO invokeResultDTO,
                                          PaymentCreateResultDTO resultDTO,
                                          int currencyExponent) {
        TransactionFollowUpRecordDTO recordDTO = new TransactionFollowUpRecordDTO();
        recordDTO.setSourceOrderDO(sourceOrderDO);
        recordDTO.setCommandDTO(commandDTO);
        recordDTO.setRouteResultDTO(routeResultDTO);
        recordDTO.setChannelInvokeResultDTO(invokeResultDTO);
        recordDTO.setResultDTO(resultDTO);
        recordDTO.setCurrencyExponent(currencyExponent);
        transactionRecordService.recordFollowUpTransaction(recordDTO);
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

    private void enrichRefundResult(PaymentCreateCommandDTO commandDTO,
                                    TransactionOrderDO sourceOrderDO,
                                    PaymentRouteResultDTO routeResultDTO,
                                    com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse channelResponse,
                                    PaymentCreateResultDTO resultDTO) {
        resultDTO.setMerchantId(commandDTO.getMerchantId());
        resultDTO.setSubMerchantInfo(toResultSubMerchantInfo(commandDTO.getSubMerchantInfo()));
        resultDTO.setOrderAmount(commandDTO.getLabelAmount());
        resultDTO.setOrderCurrency(commandDTO.getLabelCurrency());
        resultDTO.setLabelAmount(commandDTO.getLabelAmount());
        resultDTO.setLabelCurrency(commandDTO.getLabelCurrency());
        resultDTO.setTransactionAmount(commandDTO.getTransactionAmount());
        resultDTO.setTransactionCurrency(commandDTO.getTransactionCurrency());
        resultDTO.setTransactionRate(commandDTO.getTransactionRate());
        resultDTO.setRateSource(commandDTO.getRateSource());
        resultDTO.setRateTime(commandDTO.getRateTime());
        resultDTO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        resultDTO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        resultDTO.setPaymentMethod(sourceOrderDO.getPaymentMethod());
        resultDTO.setPaymentBrand(sourceOrderDO.getPaymentBrand());
        resultDTO.setCallbackUrl(resolveCallbackUrl(commandDTO));
        enrichMerchantResponse(resultDTO, channelResponse);
        resultDTO.setTotalAuthorizedAmount(resolveDisplayAuthorizedAmount(sourceOrderDO));
        resultDTO.setTotalCapturedAmount(resolveDisplayCapturedAmount(sourceOrderDO));
        resultDTO.setTotalRefundAmount(sourceOrderDO.getRefundedAmount());
        resultDTO.setTotalChargebackAmount(sourceOrderDO.getChargebackAmount());
    }

    private BigDecimal resolveDisplayAuthorizedAmount(TransactionOrderDO sourceOrderDO) {
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            return firstPositive(sourceOrderDO.getCapturedAmount(), sourceOrderDO.getTransactionAmount());
        }
        return sourceOrderDO.getAuthorizedAmount();
    }

    private BigDecimal resolveDisplayCapturedAmount(TransactionOrderDO sourceOrderDO) {
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            return firstPositive(sourceOrderDO.getCapturedAmount(), sourceOrderDO.getTransactionAmount());
        }
        return sourceOrderDO.getCapturedAmount();
    }

    private BigDecimal firstPositive(BigDecimal first, BigDecimal second) {
        if (first != null && first.compareTo(BigDecimal.ZERO) > 0) {
            return first;
        }
        return second;
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

    private void enrichMerchantResponse(PaymentCreateResultDTO resultDTO,
                                        com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse channelResponse) {
        resultDTO.setMerchantResponseCode(resolveMerchantResponseCode(resultDTO.getStatus()));
        resultDTO.setMerchantResponseMessage(resolveMerchantResponseMessage(resultDTO, channelResponse));
    }

    private String resolveMerchantResponseCode(String transactionStatus) {
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_SUCCESS.getCode();
        }
        if (PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_REJECTED.getCode();
        }
        return ApiResultEnum.PROCESSING.getCode();
    }

    private String resolveMerchantResponseMessage(PaymentCreateResultDTO resultDTO,
                                                  com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse response) {
        if (resultDTO == null) {
            return ApiResultEnum.PROCESSING.getMessage();
        }
        if (!PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus())) {
            return PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                    ? ApiResultEnum.PAYMENT_SUCCESS.getMessage()
                    : ApiResultEnum.PROCESSING.getMessage();
        }
        if (response == null || "ERROR".equalsIgnoreCase(response.getRawChannelStatus())) {
            return ApiResultEnum.PAYMENT_REJECTED.getMessage();
        }
        return firstText(joinCodeAndMessage(response.getChannelResponseCode(), response.getChannelResponseMessage()),
                ApiResultEnum.PAYMENT_REJECTED.getMessage());
    }

    private String joinCodeAndMessage(String code, String message) {
        if (StringUtils.hasText(code) && StringUtils.hasText(message)) {
            return code + ": " + message;
        }
        return firstText(code, message);
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

    private String resolveChannelOrderNo(TransactionOrderDO sourceOrderDO) {
        return StringUtils.hasText(sourceOrderDO.getRootTransactionId())
                ? sourceOrderDO.getRootTransactionId()
                : sourceOrderDO.getLatestTransactionId();
    }

    private Long toMinorAmount(BigDecimal amount, String currency) {
        try {
            return isoDictionaryService.toMinorUnit(amount, currency);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "amount fraction digits exceed currency minor unit", exception);
        }
    }

    private int resolveCurrencyExponent(String currency) {
        IsoCurrencyInfo currencyInfo = isoDictionaryService.getCurrency(currency)
                .orElseThrow(() -> new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "currency can not be resolved"));
        if (currencyInfo.defaultFractionDigits() < 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "currency fraction digits can not be resolved");
        }
        return currencyInfo.defaultFractionDigits();
    }

    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal defaultTransactionRate() {
        return new BigDecimal("1.00000000");
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

    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime, String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone == null || timeZone.isBlank() ? DEFAULT_TIME_ZONE : timeZone);
        return transactionDateTime.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
