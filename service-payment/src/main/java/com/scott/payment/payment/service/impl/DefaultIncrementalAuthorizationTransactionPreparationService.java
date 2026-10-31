package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.order.FrozenRateFollowUpAmountCalculator;
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
import com.scott.payment.payment.service.IncrementalAuthorizationTransactionPreparationService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.IncrementalAuthorizationPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
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
 * 增量授权交易的本地准备服务。
 *
 * <p>在原授权主单锁内校验当前授权状态和未决资金动作，随后原子写入幂等占位、动作单、固定渠道请求
 * 身份及 Outbox。上层只能在准备事务提交后调用渠道，避免并发增额基于同一旧授权额度重复通过。</p>
 */
@Service
public class DefaultIncrementalAuthorizationTransactionPreparationService implements IncrementalAuthorizationTransactionPreparationService {

    /** 渠道请求记录号前缀；准备阶段生成后固定，供超时恢复、查询和回调关联。 */
    private static final String CHANNEL_REQUEST_ID_PREFIX = "CR";

    /** 平台侧渠道交易号前缀；真实外呼和重试必须复用准备阶段生成的同一编号。 */
    private static final String CHANNEL_TRANSACTION_ID_PREFIX = "CH";

    /** 交易动作幂等作用域，避免与其他业务域的同名键互相覆盖。 */
    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";

    /** 未显式提供时区时用于计算 UTC 分片时间的业务默认时区。 */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /** Outbox 聚合类型，保证同一支付生命周期的事件按 operationId 聚合。 */
    private static final String PAYMENT_TRANSACTION_AGGREGATE = "PAYMENT_TRANSACTION";

    /** Outbox 初始状态，表示业务事实已提交但消息尚未投递。 */
    private static final String EVENT_STATUS_INIT = "INIT";

    /** Outbox 最大投递次数，超过上限后停止自动重试并进入人工处置。 */
    private static final int DEFAULT_EVENT_MAX_RETRY_COUNT = 200;

    /** 新增记录的乐观锁初始版本。 */
    private static final int INITIAL_VERSION = 0;

    /** 新增记录的逻辑未删除标识。 */
    private static final int NOT_DELETED = 0;

    private final IsoDictionaryService isoDictionaryService;

    private final PaymentChannelRouteService paymentChannelRouteService;

    private final TransactionIdempotencyService transactionIdempotencyService;

    private final TransactionEventOutboxService transactionEventOutboxService;

    private final TransactionRecordService transactionRecordService;

    private final TransactionStateMachineService transactionStateMachineService;

    public DefaultIncrementalAuthorizationTransactionPreparationService(
            IsoDictionaryService isoDictionaryService,
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

    /**
     * 在事务内准备增量授权交易。
     *
     * <p>锁定原订单并按数据库授权累计值校验增量额度及冲突动作，Redis 不作为授权金额事实来源。</p>
     *
     * @param commandDTO     增量授权命令
     * @param idempotencyKey 数据库幂等键
     * @return 新建或幂等命中的增量授权准备结果
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public IncrementalAuthorizationPreparationResultDTO prepareIncrementalAuthorization(
            PaymentCreateCommandDTO commandDTO,
            String idempotencyKey) {
        if (transactionRecordService == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderDO resolvedSourceOrderDO = resolveSourceOrder(commandDTO);
        // 主单锁把增量授权、请款和撤销对授权额度的竞争收敛为单点串行决策。
        TransactionOrderDO sourceOrderDO = lockSourceOrder(resolvedSourceOrderDO);
        // 锁内使用数据库权威原交易身份计算指纹，阻止同一商户操作号替换金额或币种后重放。
        commandDTO.setRequestFingerprint(canonicalIncrementalAuthorizationRequestFingerprint(commandDTO, sourceOrderDO));
        return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                .map(record -> IncrementalAuthorizationPreparationResultDTO.duplicate(
                        resolveDuplicateIncrementalAuthorization(commandDTO, record)))
                .orElseGet(() -> prepareNewIncrementalAuthorization(commandDTO, idempotencyKey, sourceOrderDO));
    }

    /**
     * 创建新的增量授权幂等记录、动作单和 Outbox。
     *
     * @return 新增量授权准备结果；并发占用幂等键时返回既有结果
     */
    private IncrementalAuthorizationPreparationResultDTO prepareNewIncrementalAuthorization(
            PaymentCreateCommandDTO commandDTO,
            String idempotencyKey,
            TransactionOrderDO sourceOrderDO) {
        if (transactionStateMachineService == null) {
            throw new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE.getCode(),
                    "transaction state machine is not configured");
        }
        FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan = calculateIncrementalAuthorizationAmountPlan(
                commandDTO, sourceOrderDO);
        applyIncrementalAuthorizationAmountPlan(commandDTO, sourceOrderDO, amountPlan);
        // 状态机和未决动作扫描必须基于持锁后的同一快照，否则并发增额可能同时通过。
        transactionStateMachineService.validateFollowUpAction(
                sourceOrderDO,
                PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION,
                commandDTO.getTransactionAmount(),
                commandDTO.getTransactionCurrency());
        validateNoConflictingAuthorizationAction(commandDTO, sourceOrderDO, LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();
        TransactionIdempotencyDO beginRecord = transactionIdempotencyService.newProcessingRecord(
                TRANSACTION_OPERATION_SCOPE,
                idempotencyKey,
                commandDTO.getMerchantId(),
                sourceOrderDO.getMerchantOrderNo(),
                commandDTO.getMerchantOrderId(),
                PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode(),
                commandDTO.getTransactionDateTime(),
                DEFAULT_TIME_ZONE,
                commandDTO.getRequestFingerprint(),
                now);
        // 数据库唯一约束处理跨节点竞争；失败方只能复用同指纹的权威结果，不能创建第二个渠道请求。
        if (!transactionIdempotencyService.tryBegin(beginRecord)) {
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(record -> IncrementalAuthorizationPreparationResultDTO.duplicate(
                            resolveDuplicateIncrementalAuthorization(commandDTO, record)))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                            "incremental authorization idempotency conflict record is missing"));
        }
        TransactionOperationDO sourceOperationDO = transactionRecordService.findSourceOperationByTransactionId(
                commandDTO.getTransactionInfo().getSourceTransactionId(),
                commandDTO.getTransactionInfo().getSourceTransactionDateTime());
        bindIncrementalAuthorizationSourceChannelTransaction(commandDTO, sourceOperationDO);
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        PaymentCreateResultDTO resultDTO = buildIncrementalAuthorizationResult(commandDTO, sourceOrderDO, transactionId);
        int currencyExponent = resolveCurrencyExponent(sourceOrderDO.getTransactionCurrency());
        PaymentRouteResultDTO routeResultDTO;
        try {
            // 增量授权必须沿用原授权冻结的渠道和 MID，重新路由会破坏渠道侧授权链。
            routeResultDTO = OriginalTransactionRouteResolver.restore(
                    paymentChannelRouteService, sourceOrderDO, sourceOperationDO);
        } catch (ServiceException exception) {
            if (!OriginalTransactionRejectionSupport.isOriginalTransactionRejected(exception)) {
                throw exception;
            }
            PaymentRouteResultDTO sourceRouteSnapshot = OriginalTransactionRouteResolver.snapshot(
                    sourceOrderDO, sourceOperationDO);
            OriginalTransactionRejectionSupport.apply(resultDTO);
            enrichIncrementalAuthorizationResult(commandDTO, sourceOrderDO, null, resultDTO);
            OriginalTransactionRejectionSupport.apply(resultDTO);
            recordIncrementalAuthorizationPreparedFact(
                    commandDTO, sourceOrderDO, sourceRouteSnapshot, null, resultDTO, currencyExponent);
            saveTransactionCreatedEvent(commandDTO, resultDTO);
            completeIdempotency(idempotencyKey, commandDTO, resultDTO);
            IncrementalAuthorizationPreparationResultDTO target =
                    new IncrementalAuthorizationPreparationResultDTO();
            target.setCallChannel(false);
            target.setIdempotencyKey(idempotencyKey);
            target.setCommandDTO(commandDTO);
            target.setSourceOrderDO(sourceOrderDO);
            target.setRouteResultDTO(sourceRouteSnapshot);
            target.setResultDTO(resultDTO);
            target.setCurrencyExponent(currencyExponent);
            return target;
        }
        resultDTO.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        resultDTO.setProcessStage(PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode());
        enrichIncrementalAuthorizationResult(commandDTO, sourceOrderDO, null, resultDTO);
        PaymentPreparedChannelRequestDTO preparedChannelRequestDTO = prepareChannelRequest(commandDTO, sourceOrderDO);
        PaymentChannelInvokeResultDTO preparedInvokeResultDTO = buildPreparedInvokeResult(
                commandDTO, routeResultDTO, sourceOrderDO.getOperationId(), transactionId, preparedChannelRequestDTO);
        // 动作事实、固定请求身份和恢复事件先提交；上层看到 callChannel=true 后才执行真实渠道外呼。
        recordIncrementalAuthorizationPreparedFact(
                commandDTO, sourceOrderDO, routeResultDTO, preparedInvokeResultDTO, resultDTO, currencyExponent);
        saveTransactionCreatedEvent(commandDTO, resultDTO);
        completeIdempotency(idempotencyKey, commandDTO, resultDTO);

        IncrementalAuthorizationPreparationResultDTO target = new IncrementalAuthorizationPreparationResultDTO();
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
        TransactionOrderDO sourceOrderDO = transactionRecordService.findSourceOrderByTransactionId(
                sourceTransactionId,
                transactionInfoDTO.getSourceTransactionDateTime(),
                transactionInfoDTO.getRootTransactionDateTime());
        if (sourceOrderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return sourceOrderDO;
    }

    /** 锁定原授权主单，串行化增量授权与请款、预授权完成和撤销的额度竞争。 */
    private TransactionOrderDO lockSourceOrder(TransactionOrderDO sourceOrderDO) {
        if (sourceOrderDO == null
                || !StringUtils.hasText(sourceOrderDO.getOperationId())
                || sourceOrderDO.getTransactionDateTime() == null) {
            return sourceOrderDO;
        }
        return transactionRecordService.lockOrder(sourceOrderDO.getTransactionDateTime(), sourceOrderDO.getOperationId());
    }

    /** 拒绝任何处理中请款、增量授权或撤销动作，确保增额基于确定的授权累计值。 */
    private void validateNoConflictingAuthorizationAction(PaymentCreateCommandDTO commandDTO,
                                                          TransactionOrderDO sourceOrderDO,
                                                          LocalDateTime now) {
        LocalDateTime beginTime = sourceOrderDO.getTransactionDateTime();
        LocalDateTime endTime = laterOf(now, commandDTO.getTransactionDateTime());
        List<TransactionOperationDO> incrementalAuthorizations = transactionRecordService.findNonTerminalIncrementalAuthorizations(
                commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), beginTime, endTime);
        if (!incrementalAuthorizations.isEmpty()) {
            throw new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS.getCode(),
                    "source transaction has a pending incremental authorization action");
        }
        String sourceTransactionId = commandDTO.getTransactionInfo().getSourceTransactionId();
        List<TransactionOperationDO> captures = transactionRecordService.findNonTerminalCaptures(
                commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), sourceTransactionId, beginTime, endTime);
        if (!captures.isEmpty()) {
            throw new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS.getCode(),
                    "source transaction has a pending capture action");
        }
        List<TransactionOperationDO> voids = transactionRecordService.findNonTerminalVoids(
                commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), beginTime, endTime);
        if (!voids.isEmpty()) {
            throw new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS.getCode(),
                    "source transaction has a pending void action");
        }
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

    /** 仅在请求指纹一致且原记录已有交易号时复用增量授权幂等结果。 */
    private PaymentCreateResultDTO resolveDuplicateIncrementalAuthorization(PaymentCreateCommandDTO commandDTO,
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

    /** 对原交易、增额金额、币种和商户操作号生成稳定增量授权请求指纹。 */
    private String canonicalIncrementalAuthorizationRequestFingerprint(PaymentCreateCommandDTO commandDTO,
                                                                       TransactionOrderDO sourceOrderDO) {
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
                "transactionType=" + PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode(),
                "sourceTransactionId=" + normalizeFingerprintText(sourceTransactionId),
                "amount=" + normalizeFingerprintAmount(commandDTO.getAmount()),
                "currency=" + normalizeFingerprintText(effectiveCurrency));
        return sha256(canonical);
    }

    /** 指纹文本统一去除首尾空白并转大写，避免大小写差异绕过幂等冲突检测。 */
    private String normalizeFingerprintText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /** 指纹金额去除无意义尾零，使等值十进制金额命中同一幂等请求。 */
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

    /**
     * 按首笔授权冻结汇率计算增量授权的标签金额和渠道交易金额。
     *
     * @param commandDTO 增量授权命令
     * @param sourceOrderDO 已锁定的原授权主单
     * @return 冻结汇率金额方案
     */
    private FrozenRateFollowUpAmountCalculator.AmountPlan calculateIncrementalAuthorizationAmountPlan(
            PaymentCreateCommandDTO commandDTO,
            TransactionOrderDO sourceOrderDO) {
        String labelCurrency = firstText(sourceOrderDO.getLabelCurrency(), sourceOrderDO.getTransactionCurrency());
        return FrozenRateFollowUpAmountCalculator.calculateIncrementalAuthorization(
                sourceOrderDO,
                commandDTO.getAmount(),
                commandDTO.getCurrency(),
                resolveCurrencyExponent(labelCurrency),
                resolveCurrencyExponent(sourceOrderDO.getTransactionCurrency()));
    }

    /**
     * 把增量授权的标签口径、渠道交易口径和首笔冻结汇率写入内部命令。
     *
     * @param commandDTO 待归一的增量授权命令
     * @param sourceOrderDO 原授权主单
     * @param amountPlan 冻结汇率金额方案
     */
    private void applyIncrementalAuthorizationAmountPlan(
            PaymentCreateCommandDTO commandDTO,
            TransactionOrderDO sourceOrderDO,
            FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan) {
        validateIncrementalAuthorizationMerchantOrderNo(commandDTO, sourceOrderDO);
        commandDTO.setMerchantOrderNo(sourceOrderDO.getMerchantOrderNo());
        commandDTO.setLabelCurrency(amountPlan.labelCurrency());
        commandDTO.setLabelAmount(amountPlan.labelAmount());
        commandDTO.setTransactionCurrency(amountPlan.transactionCurrency());
        commandDTO.setTransactionAmount(amountPlan.transactionAmount());
        commandDTO.setTransactionRate(amountPlan.transactionRate());
        commandDTO.setRateSource(amountPlan.rateSource());
        commandDTO.setRateTime(amountPlan.rateTime());
        commandDTO.setDccEnabled(amountPlan.dccEnabled());
        commandDTO.setEdcEnabled(amountPlan.edcEnabled());
    }

    /**
     * 绑定原授权的渠道交易号，确保增量授权继续原渠道授权链。
     *
     * @param commandDTO 增量授权命令
     * @param sourceOrderDO 原授权主单
     * @param sourceOperationDO 原授权动作单
     */
    private void bindIncrementalAuthorizationSourceChannelTransaction(
            PaymentCreateCommandDTO commandDTO,
            TransactionOperationDO sourceOperationDO) {
        if (sourceOperationDO == null || !StringUtils.hasText(sourceOperationDO.getChannelTransactionId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "source channel transaction id is required for INCREMENTAL_AUTHORIZATION");
        }
        if (commandDTO.getTransactionInfo() != null) {
            commandDTO.getTransactionInfo().setSourceChannelTransactionId(sourceOperationDO.getChannelTransactionId());
        }
    }

    /**
     * 校验增量授权请求中的可选商户订单号与原交易一致。
     *
     * <p>未传商户订单号时沿用原交易身份；显式传入不同订单号会破坏后续动作
     * 与原支付流的幂等范围，因此在创建交易主单和动作单前直接拒绝。</p>
     *
     * @param commandDTO 增量授权交易命令
     * @param sourceOrderDO 被追加授权金额的原交易主单
     */
    private void validateIncrementalAuthorizationMerchantOrderNo(PaymentCreateCommandDTO commandDTO,
                                                                 TransactionOrderDO sourceOrderDO) {
        if (!StringUtils.hasText(commandDTO.getMerchantOrderNo())) {
            return;
        }
        if (!commandDTO.getMerchantOrderNo().equals(sourceOrderDO.getMerchantOrderNo())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "merchant order number must match source transaction");
        }
    }

    private PaymentCreateResultDTO buildIncrementalAuthorizationResult(PaymentCreateCommandDTO commandDTO,
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
        resultDTO.setTransactionType(PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode());
        resultDTO.setCurrency(commandDTO.getTransactionCurrency());
        resultDTO.setAmount(toMinorAmount(commandDTO.getTransactionAmount(), commandDTO.getTransactionCurrency()));
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
        invokeResultDTO.setRequestScene(PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode());
        invokeResultDTO.setRequestStartTime(LocalDateTime.now());
        return invokeResultDTO;
    }

    /** 在渠道调用前持久化增量授权动作及请求事实，失败或重试不得重复占用授权额度。 */
    private void recordIncrementalAuthorizationPreparedFact(PaymentCreateCommandDTO commandDTO,
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

    /** 将增量授权创建事件写入同一交易生命周期的 FIFO Outbox。 */
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

    /** 保存增量授权准备结果快照，重复请求只能返回原结果且禁止重复调用渠道。 */
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
                return PaymentPersistedResultSupport.overwriteLatestIdempotencyStatus(
                        resultDTO, record.getTransactionStatus());
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

    /** 使用原授权累计金额与本次交易事实补全增量授权响应，不暴露渠道原始敏感载荷。 */
    private void enrichIncrementalAuthorizationResult(PaymentCreateCommandDTO commandDTO,
                                                      TransactionOrderDO sourceOrderDO,
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
        resultDTO.setRootTransactionDateTime(sourceOrderDO.getTransactionDateTime());
        resultDTO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        resultDTO.setPaymentMethod(sourceOrderDO.getPaymentMethod());
        resultDTO.setPaymentBrand(sourceOrderDO.getPaymentBrand());
        resultDTO.setCallbackUrl(resolveCallbackUrl(commandDTO));
        enrichMerchantResponse(resultDTO);
        resultDTO.setTotalAuthorizedAmount(sourceOrderDO.getAuthorizedAmount());
        resultDTO.setTotalAuthorizedCancelAmount(sourceOrderDO.getAuthorizedCancelAmount());
        resultDTO.setTotalCapturedAmount(sourceOrderDO.getCapturedAmount());
        resultDTO.setTotalRefundAmount(sourceOrderDO.getRefundedAmount());
        resultDTO.setTotalRefuseAmount(sourceOrderDO.getChargebackAmount());
    }

    /** 后续动作优先使用本次回调地址，未提供时继承交易信息中的原回调地址。 */
    private String resolveCallbackUrl(PaymentCreateCommandDTO commandDTO) {
        if (StringUtils.hasText(commandDTO.getCallbackUrl())) {
            return commandDTO.getCallbackUrl();
        }
        if (commandDTO.getTransactionInfo() != null && StringUtils.hasText(commandDTO.getTransactionInfo().getCallbackUrl())) {
            return commandDTO.getTransactionInfo().getCallbackUrl();
        }
        return null;
    }

    private void enrichMerchantResponse(PaymentCreateResultDTO resultDTO) {
        PaymentOpenApiResultSupport.applyMerchantProjection(resultDTO);
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
        return firstText(
                sourceOrderDO.getChannelOrderNo(),
                sourceOrderDO.getRootTransactionId(),
                sourceOrderDO.getLatestTransactionId());
    }

    /** 按 ISO 4217 币种精度精确转换最小货币单位，超精度输入直接失败。 */
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
