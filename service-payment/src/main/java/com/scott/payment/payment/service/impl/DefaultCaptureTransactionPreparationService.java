package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.db.constant.DataSourceName;
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
import com.scott.payment.payment.service.CaptureTransactionPreparationService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.CapturePreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
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
 * 请款类交易的本地准备服务。
 *
 * <p>在事务内串行化同一原交易的额度竞争，完成状态机校验、请求幂等占位、动作单、渠道请求快照
 * 和 Outbox 事件落库。只有本事务提交后，上层编排才可以执行真实渠道调用，从而保证进程中断后仍有
 * 可恢复的请求身份，且不会因重试重复占用可请款金额。</p>
 */
@Service
public class DefaultCaptureTransactionPreparationService implements CaptureTransactionPreparationService {

    /**
     * 渠道请求记录号前缀；该编号在渠道调用前生成并持久化，供超时恢复和回调关联使用。
     */
    private static final String CHANNEL_REQUEST_ID_PREFIX = "CR";

    /**
     * 平台侧渠道交易号前缀；同一次准备结果重试时必须复用，不得在外呼阶段重新生成。
     */
    private static final String CHANNEL_TRANSACTION_ID_PREFIX = "CH";

    /**
     * 幂等记录作用域，隔离交易动作与其他业务类型使用的同名幂等键。
     */
    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";

    /**
     * 未显式提供交易时区时使用的业务时区，用于生成稳定 UTC 分片时间。
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /**
     * Outbox 聚合类型；消费者以 operationId 作为同一支付生命周期的聚合键。
     */
    private static final String PAYMENT_TRANSACTION_AGGREGATE = "PAYMENT_TRANSACTION";

    /**
     * Outbox 初始状态，表示事件已与业务事实同事务落库但尚未投递。
     */
    private static final String EVENT_STATUS_INIT = "INIT";

    /**
     * Outbox 事件最大投递次数，超过后进入人工处置而不是无限重试。
     */
    private static final int DEFAULT_EVENT_MAX_RETRY_COUNT = 200;

    /**
     * 新增记录的乐观锁初始版本。
     */
    private static final int INITIAL_VERSION = 0;

    /**
     * 新增记录的逻辑未删除标识。
     */
    private static final int NOT_DELETED = 0;

    private final IsoDictionaryService isoDictionaryService;

    private final PaymentChannelRouteService paymentChannelRouteService;

    private final TransactionIdempotencyService transactionIdempotencyService;

    private final TransactionEventOutboxService transactionEventOutboxService;

    private final PaymentExchangeRateService paymentExchangeRateService;

    private final TransactionRecordService transactionRecordService;

    private final TransactionStateMachineService transactionStateMachineService;

    /**
     * 请款准备事实的短事务执行器；原单定位、路由恢复、币种字典和汇率追溯均在事务外完成。
     */
    private final TransactionOperations transactionOperations;

    /**
     * 创建请款本地准备服务。
     *
     * @param isoDictionaryService 币种辅币位字典
     * @param paymentChannelRouteService 原交易渠道和原 MID 恢复服务
     * @param transactionIdempotencyService 动作幂等服务
     * @param transactionEventOutboxService 可靠交易事件服务
     * @param paymentExchangeRateService 冻结交易汇率版本追溯服务
     * @param transactionRecordService 交易事实持久化服务
     * @param transactionStateMachineService 请款状态和累计金额规则服务
     */
    public DefaultCaptureTransactionPreparationService(IsoDictionaryService isoDictionaryService,
                                                       PaymentChannelRouteService paymentChannelRouteService,
                                                       TransactionIdempotencyService transactionIdempotencyService,
                                                       TransactionEventOutboxService transactionEventOutboxService,
                                                       PaymentExchangeRateService paymentExchangeRateService,
                                                       TransactionRecordService transactionRecordService,
                                                       TransactionStateMachineService transactionStateMachineService) {
        this(isoDictionaryService,
                paymentChannelRouteService,
                transactionIdempotencyService,
                transactionEventOutboxService,
                paymentExchangeRateService,
                transactionRecordService,
                transactionStateMachineService,
                null);
    }

    /**
     * Spring 生产构造器，使用交易数据源事务管理器建立主单锁短事务。
     */
    @Autowired
    public DefaultCaptureTransactionPreparationService(IsoDictionaryService isoDictionaryService,
                                                       PaymentChannelRouteService paymentChannelRouteService,
                                                       TransactionIdempotencyService transactionIdempotencyService,
                                                       TransactionEventOutboxService transactionEventOutboxService,
                                                       PaymentExchangeRateService paymentExchangeRateService,
                                                       TransactionRecordService transactionRecordService,
                                                       TransactionStateMachineService transactionStateMachineService,
                                                       PlatformTransactionManager transactionManager) {
        this.isoDictionaryService = isoDictionaryService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.transactionIdempotencyService = transactionIdempotencyService;
        this.transactionEventOutboxService = transactionEventOutboxService;
        this.paymentExchangeRateService = paymentExchangeRateService;
        this.transactionRecordService = transactionRecordService;
        this.transactionStateMachineService = transactionStateMachineService;
        this.transactionOperations = transactionManager == null
                ? null
                : new TransactionTemplate(transactionManager);
    }

    /**
     * 按标准请款类型准备后续交易。
     *
     * @param commandDTO    请款命令
     * @param idempotencyKey 数据库幂等键
     * @return 新建或幂等命中的请款准备结果
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public CapturePreparationResultDTO prepareCapture(PaymentCreateCommandDTO commandDTO, String idempotencyKey) {
        return prepareCapture(commandDTO, idempotencyKey, PaymentTransactionTypeEnum.CAPTURE);
    }

    /**
     * 按指定请款类动作准备交易。
     *
     * <p>事务内锁定原订单，重新计算规范请求指纹，并在创建动作前校验可请款金额和冲突中的资金动作。</p>
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public CapturePreparationResultDTO prepareCapture(PaymentCreateCommandDTO commandDTO,
                                                      String idempotencyKey,
                                                      PaymentTransactionTypeEnum transactionType) {
        if (transactionRecordService == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        PaymentTransactionTypeEnum captureLikeType = normalizeCaptureLikeType(transactionType);
        TransactionOrderDO resolvedSourceOrderDO = resolveSourceOrder(commandDTO);
        commandDTO.setRequestFingerprint(canonicalCaptureRequestFingerprint(
                commandDTO, resolvedSourceOrderDO, captureLikeType));
        CapturePreparationResultDTO duplicate = findDuplicateCapture(commandDTO, idempotencyKey);
        if (duplicate != null) {
            return duplicate;
        }
        CapturePreparationPlan plan = planCapture(resolvedSourceOrderDO, commandDTO);
        return executeInTransaction(() -> prepareCaptureInTransaction(
                commandDTO, idempotencyKey, resolvedSourceOrderDO, captureLikeType, plan));
    }

    /**
     * 在持有原订单行锁的短事务内完成额度决策、幂等占位和全部准备事实写入。
     */
    private CapturePreparationResultDTO prepareCaptureInTransaction(
            PaymentCreateCommandDTO commandDTO,
            String idempotencyKey,
            TransactionOrderDO resolvedSourceOrderDO,
            PaymentTransactionTypeEnum captureLikeType,
            CapturePreparationPlan plan) {
        // 后续状态校验、累计金额判断和幂等占位必须共享同一主单锁，避免并发动作同时通过额度校验。
        TransactionOrderDO sourceOrderDO = lockSourceOrderForCapture(resolvedSourceOrderDO);
        // 指纹必须在锁内基于数据库权威币种和原交易身份计算，不能信任可被商户重复提交的展示字段。
        commandDTO.setRequestFingerprint(canonicalCaptureRequestFingerprint(commandDTO, sourceOrderDO, captureLikeType));
        return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                .map(record -> CapturePreparationResultDTO.duplicate(resolveDuplicateCapture(commandDTO, record)))
                .orElseGet(() -> prepareNewCapture(
                        commandDTO, idempotencyKey, sourceOrderDO, captureLikeType, plan));
    }

    /**
     * 创建新的请款类幂等记录、动作单和 Outbox。
     *
     * <p>状态机和累计金额校验全部通过后才占用幂等键；并发失败时重读数据库幂等结果，
     * 不重复创建或调用渠道。</p>
     */
    private CapturePreparationResultDTO prepareNewCapture(PaymentCreateCommandDTO commandDTO,
                                                          String idempotencyKey,
                                                          TransactionOrderDO sourceOrderDO,
                                                          PaymentTransactionTypeEnum transactionType,
                                                          CapturePreparationPlan plan) {
        if (transactionStateMachineService == null) {
            throw new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE.getCode(),
                    "transaction state machine is not configured");
        }
        FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan = calculateCaptureAmountPlan(
                commandDTO,
                sourceOrderDO,
                LocalDateTime.now(),
                plan.labelCurrencyExponent(),
                plan.transactionCurrencyExponent());
        commandDTO.setLabelCurrencyExponent(plan.labelCurrencyExponent());
        applyCaptureAmountPlan(commandDTO, sourceOrderDO, amountPlan);
        // 状态机校验与冲突动作扫描均发生在主单锁内，形成对可请款额度的串行决策点。
        transactionStateMachineService.validateFollowUpAction(
                sourceOrderDO, transactionType, commandDTO.getTransactionAmount(), commandDTO.getTransactionCurrency());
        validateNoNonTerminalCapture(commandDTO, sourceOrderDO, LocalDateTime.now());
        validateNoNonTerminalIncrementalAuthorization(commandDTO, sourceOrderDO, LocalDateTime.now());
        validateNoNonTerminalVoid(commandDTO, sourceOrderDO, LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();
        TransactionIdempotencyDO beginRecord = transactionIdempotencyService.newProcessingRecord(
                TRANSACTION_OPERATION_SCOPE,
                idempotencyKey,
                commandDTO.getMerchantId(),
                sourceOrderDO.getMerchantOrderNo(),
                commandDTO.getMerchantOrderId(),
                transactionType.getCode(),
                commandDTO.getTransactionDateTime(),
                DEFAULT_TIME_ZONE,
                commandDTO.getRequestFingerprint(),
                now);
        // 数据库唯一约束是跨节点并发的第二道防线；竞争失败只允许复用同指纹结果，禁止再次创建动作。
        if (!transactionIdempotencyService.tryBegin(beginRecord)) {
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(record -> CapturePreparationResultDTO.duplicate(resolveDuplicateCapture(commandDTO, record)))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                            "capture idempotency conflict record is missing"));
        }
        TransactionOperationDO sourceOperationDO = plan.sourceOperationDO();
        bindCaptureSourceChannelTransaction(commandDTO, sourceOperationDO);
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        int currencyExponent = plan.transactionCurrencyExponent();
        PaymentCreateResultDTO resultDTO = buildCaptureResult(
                commandDTO, sourceOrderDO, transactionId, transactionType, currencyExponent);
        OriginalTransactionRouteResolver.Resolution routeResolution = plan.routeResolution();
        PaymentRouteResultDTO routeResultDTO = routeResolution.requireRouteResult();
        if (routeResolution.rejected()) {
            OriginalTransactionRejectionSupport.apply(resultDTO);
            enrichCaptureResult(commandDTO, sourceOrderDO, routeResultDTO, null, resultDTO);
            OriginalTransactionRejectionSupport.apply(resultDTO);
            recordCapturePreparedFact(
                    commandDTO, sourceOrderDO, routeResultDTO, null, resultDTO, currencyExponent);
            saveRateUsageIfApplied(commandDTO, resultDTO, amountPlan, plan.frozenRate(), currencyExponent);
            saveTransactionCreatedEvent(commandDTO, resultDTO);
            completeIdempotency(idempotencyKey, commandDTO, resultDTO);
            CapturePreparationResultDTO target = new CapturePreparationResultDTO();
            target.setCallChannel(false);
            target.setIdempotencyKey(idempotencyKey);
            target.setCommandDTO(commandDTO);
            target.setSourceOrderDO(sourceOrderDO);
            target.setRouteResultDTO(routeResultDTO);
            target.setResultDTO(resultDTO);
            target.setCurrencyExponent(currencyExponent);
            return target;
        }
        resultDTO.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        resultDTO.setProcessStage(PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode());
        enrichCaptureResult(commandDTO, sourceOrderDO, routeResultDTO, null, resultDTO);
        PaymentPreparedChannelRequestDTO preparedChannelRequestDTO = prepareChannelRequest(commandDTO, sourceOrderDO);
        PaymentChannelInvokeResultDTO preparedInvokeResultDTO = buildPreparedInvokeResult(
                commandDTO, routeResultDTO, sourceOrderDO.getOperationId(), transactionId, preparedChannelRequestDTO);
        // 先持久化动作、固定请求身份和恢复事件；事务提交后上层才可使用 callChannel=true 发起真实外呼。
        recordCapturePreparedFact(commandDTO, sourceOrderDO, routeResultDTO, preparedInvokeResultDTO, resultDTO, currencyExponent);
        saveRateUsageIfApplied(commandDTO, resultDTO, amountPlan, plan.frozenRate(), currencyExponent);
        saveTransactionCreatedEvent(commandDTO, resultDTO);
        completeIdempotency(idempotencyKey, commandDTO, resultDTO);

        CapturePreparationResultDTO target = new CapturePreparationResultDTO();
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

    /**
     * 读取并校验原交易主单。
     */
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

    /**
     * 在短事务前读取不可变原动作、渠道身份、币种精度和冻结汇率版本。
     */
    private CapturePreparationPlan planCapture(TransactionOrderDO sourceOrderDO,
                                               PaymentCreateCommandDTO commandDTO) {
        TransactionOperationDO sourceOperationDO = transactionRecordService.findSourceOperationByTransactionId(
                commandDTO.getTransactionInfo().getSourceTransactionId(),
                commandDTO.getTransactionInfo().getSourceTransactionDateTime());
        OriginalTransactionRouteResolver.Resolution routeResolution = OriginalTransactionRouteResolver.plan(
                paymentChannelRouteService, sourceOrderDO, sourceOperationDO);
        String labelCurrency = firstText(sourceOrderDO.getLabelCurrency(), sourceOrderDO.getTransactionCurrency());
        int labelCurrencyExponent = resolveCurrencyExponent(labelCurrency);
        int transactionCurrencyExponent = resolveCurrencyExponent(sourceOrderDO.getTransactionCurrency());
        FrozenTransactionRateResolverSupport.Resolution frozenRate = FrozenTransactionRateResolverSupport.planForSourceOrder(
                paymentExchangeRateService, sourceOrderDO);
        return new CapturePreparationPlan(
                sourceOperationDO,
                routeResolution,
                labelCurrencyExponent,
                transactionCurrencyExponent,
                frozenRate);
    }

    /**
     * 在路由和汇率规划前快速返回已完成的重复动作，降低重复流量的数据库锁竞争。
     */
    private CapturePreparationResultDTO findDuplicateCapture(PaymentCreateCommandDTO commandDTO,
                                                             String idempotencyKey) {
        return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                .map(record -> CapturePreparationResultDTO.duplicate(resolveDuplicateCapture(commandDTO, record)))
                .orElse(null);
    }

    /**
     * 使用 Spring 事务管理器执行主单锁短事务；无事务管理器仅用于现有直接构造的单元测试兼容。
     */
    private CapturePreparationResultDTO executeInTransaction(
            java.util.function.Supplier<CapturePreparationResultDTO> callback) {
        if (transactionOperations == null) {
            return callback.get();
        }
        return transactionOperations.execute(status -> callback.get());
    }

    /**
     * 锁定原授权主单，串行化请款、预授权完成、增量授权和撤销对同一可用额度的竞争。
     */
    private TransactionOrderDO lockSourceOrderForCapture(TransactionOrderDO sourceOrderDO) {
        if (sourceOrderDO == null
                || !StringUtils.hasText(sourceOrderDO.getOperationId())
                || sourceOrderDO.getTransactionDateTime() == null) {
            return sourceOrderDO;
        }
        return transactionRecordService.lockOrder(sourceOrderDO.getTransactionDateTime(), sourceOrderDO.getOperationId());
    }

    /**
     * 拒绝存在处理中请款类动作的原交易，防止并发重复占用可请款金额。
     */
    private void validateNoNonTerminalCapture(PaymentCreateCommandDTO commandDTO,
                                              TransactionOrderDO sourceOrderDO,
                                              LocalDateTime now) {
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
     * 拒绝存在处理中撤销动作的原交易，避免撤销与请款并发推进相反资金状态。
     */
    private void validateNoNonTerminalVoid(PaymentCreateCommandDTO commandDTO,
                                           TransactionOrderDO sourceOrderDO,
                                           LocalDateTime now) {
        LocalDateTime beginTime = sourceOrderDO.getTransactionDateTime();
        LocalDateTime endTime = laterOf(now, commandDTO.getTransactionDateTime());
        List<TransactionOperationDO> nonTerminalVoids = transactionRecordService.findNonTerminalVoids(
                commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), beginTime, endTime);
        if (nonTerminalVoids.isEmpty()) {
            return;
        }
        throw new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS.getCode(),
                "source transaction has a pending void action");
    }

    /**
     * 拒绝存在处理中增量授权的原交易，避免请款读取到尚未确定的授权额度。
     */
    private void validateNoNonTerminalIncrementalAuthorization(PaymentCreateCommandDTO commandDTO,
                                                               TransactionOrderDO sourceOrderDO,
                                                               LocalDateTime now) {
        LocalDateTime beginTime = sourceOrderDO.getTransactionDateTime();
        LocalDateTime endTime = laterOf(now, commandDTO.getTransactionDateTime());
        List<TransactionOperationDO> incrementalAuthorizations = transactionRecordService.findNonTerminalIncrementalAuthorizations(
                commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), beginTime, endTime);
        if (incrementalAuthorizations.isEmpty()) {
            return;
        }
        throw new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS.getCode(),
                "source transaction has a pending incremental authorization action");
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
     * 解析请款幂等命中结果；请求指纹不同视为商户操作号冲突，处理中记录不得伪造成已完成结果。
     */
    private PaymentCreateResultDTO resolveDuplicateCapture(PaymentCreateCommandDTO commandDTO,
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
     * 对商户、操作号、请款类型、原交易、金额和币种生成稳定请求指纹。
     * <p>金额使用去尾零十进制文本，避免 1.0 与 1.00 被误判为不同业务请求。</p>
     */
    private String canonicalCaptureRequestFingerprint(PaymentCreateCommandDTO commandDTO,
                                                      TransactionOrderDO sourceOrderDO,
                                                      PaymentTransactionTypeEnum transactionType) {
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
                "transactionType=" + transactionType.getCode(),
                "sourceTransactionId=" + normalizeFingerprintText(sourceTransactionId),
                "amount=" + normalizeFingerprintAmount(commandDTO.getAmount()),
                "currency=" + normalizeFingerprintText(effectiveCurrency));
        return sha256(canonical);
    }

    /**
     * 指纹文本统一去除首尾空白并转大写，避免大小写差异绕过幂等冲突检测。
     */
    private String normalizeFingerprintText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 指纹金额去除无意义尾零，使 1.0 与 1.00 表示同一业务金额。
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
     * 按原交易冻结汇率计算请款的标签金额和渠道交易金额。
     *
     * @param commandDTO 商户请款命令
     * @param sourceOrderDO 已锁定的原授权主单
     * @param decisionTime 本次金额决策时间
     * @return 冻结汇率金额方案
     */
    private FrozenRateFollowUpAmountCalculator.AmountPlan calculateCaptureAmountPlan(
            PaymentCreateCommandDTO commandDTO,
            TransactionOrderDO sourceOrderDO,
            LocalDateTime decisionTime,
            int labelCurrencyExponent,
            int transactionCurrencyExponent) {
        List<TransactionOperationDO> lifecycleOperations = transactionRecordService.findOperationsByOperationId(
                sourceOrderDO.getOperationId(),
                sourceOrderDO.getTransactionDateTime(),
                laterOf(decisionTime, commandDTO.getTransactionDateTime()));
        return FrozenRateFollowUpAmountCalculator.calculateCapture(
                sourceOrderDO,
                lifecycleOperations,
                commandDTO.getAmount(),
                commandDTO.getCurrency(),
                labelCurrencyExponent,
                transactionCurrencyExponent);
    }

    /**
     * 把请款标签口径、渠道交易口径和首笔冻结汇率同时写入内部命令。
     *
     * @param commandDTO 待归一的请款命令
     * @param sourceOrderDO 原授权主单
     * @param amountPlan 冻结汇率金额方案
     */
    private void applyCaptureAmountPlan(PaymentCreateCommandDTO commandDTO,
                                        TransactionOrderDO sourceOrderDO,
                                        FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan) {
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
     * 为真实使用首笔冻结汇率的请款类动作写入可靠汇率使用事件。
     *
     * <p>同币种或未启用 DCC/EDC 的历史兼容动作不属于汇率使用事实；跨币种事件必须先追溯到
     * 完全一致的业务汇率版本，避免只保存数值却丢失来源。</p>
     */
    private void saveRateUsageIfApplied(PaymentCreateCommandDTO commandDTO,
                                        PaymentCreateResultDTO resultDTO,
                                        FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan,
                                        FrozenTransactionRateResolverSupport.Resolution frozenRate,
                                        int currencyExponent) {
        boolean conversionEnabled = Integer.valueOf(1).equals(amountPlan.edcEnabled())
                || Integer.valueOf(1).equals(amountPlan.dccEnabled());
        if (!conversionEnabled
                || amountPlan.labelCurrency().equals(amountPlan.transactionCurrency())) {
            return;
        }
        TransactionRateUsageOutboxSupport.save(
                transactionEventOutboxService,
                commandDTO,
                resultDTO,
                FrozenTransactionRateResolverSupport.validate(frozenRate, amountPlan),
                LocalDateTime.now(),
                "transactionAmount = labelAmount * frozenTransactionRate; sourceTransactionId="
                        + commandDTO.getTransactionInfo().getSourceTransactionId()
                        + "; targetCurrencyExponent=" + currencyExponent);
    }

    /**
     * 绑定原授权的渠道交易号，确保请款继续原渠道授权链。
     *
     * @param commandDTO 请款命令
     * @param sourceOperationDO 原授权动作单
     */
    private void bindCaptureSourceChannelTransaction(PaymentCreateCommandDTO commandDTO,
                                                     TransactionOperationDO sourceOperationDO) {
        if (commandDTO.getTransactionInfo() != null && sourceOperationDO != null) {
            commandDTO.getTransactionInfo().setSourceChannelTransactionId(sourceOperationDO.getChannelTransactionId());
        }
    }

    /**
     * 构建请款结果。
     */
    private PaymentCreateResultDTO buildCaptureResult(PaymentCreateCommandDTO commandDTO,
                                                      TransactionOrderDO sourceOrderDO,
                                                      String transactionId,
                                                       PaymentTransactionTypeEnum transactionType,
                                                       int currencyExponent) {
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setOperationId(sourceOrderDO.getOperationId());
        resultDTO.setTransactionId(transactionId);
        resultDTO.setSourceTransactionId(commandDTO.getTransactionInfo().getSourceTransactionId());
        resultDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        resultDTO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        resultDTO.setMerchantId(commandDTO.getMerchantId());
        resultDTO.setSubMerchantInfo(toResultSubMerchantInfo(commandDTO.getSubMerchantInfo()));
        resultDTO.setTransactionType(transactionType.getCode());
        resultDTO.setCurrency(commandDTO.getTransactionCurrency());
        resultDTO.setAmount(toMinorAmount(
                commandDTO.getTransactionAmount(), commandDTO.getTransactionCurrency(), currencyExponent));
        return resultDTO;
    }

    /**
     * 构建渠道请求并补齐冻结路由身份。
     */
    private PaymentPreparedChannelRequestDTO prepareChannelRequest(PaymentCreateCommandDTO commandDTO,
                                                                  TransactionOrderDO sourceOrderDO) {
        PaymentPreparedChannelRequestDTO prepared = new PaymentPreparedChannelRequestDTO();
        prepared.setRequestId(PaymentOrderNoGenerator.nextOrderNo(CHANNEL_REQUEST_ID_PREFIX, commandDTO.getTransactionDateTime()));
        prepared.setChannelOrderNo(resolveChannelOrderNo(sourceOrderDO));
        prepared.setChannelTransactionId(PaymentOrderNoGenerator.nextOrderNo(CHANNEL_TRANSACTION_ID_PREFIX));
        return prepared;
    }

    /**
     * 构建待提交的渠道调用结果。
     */
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
        invokeResultDTO.setRequestScene(commandDTO.getTransactionType());
        invokeResultDTO.setRequestStartTime(LocalDateTime.now());
        return invokeResultDTO;
    }

    /**
     * 在调用渠道前原子持久化请款动作、渠道请求和商户查询快照，供失败恢复及幂等重放使用。
     */
    private void recordCapturePreparedFact(PaymentCreateCommandDTO commandDTO,
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

    /**
     * 将请款动作创建事件写入交易 FIFO Outbox，消息组固定为原生命周期 operationId 以保证动作有序。
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

    /**
     * 保存请款准备结果快照，使重复请求直接复用原交易号且不得再次调用渠道。
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
     * 构建幂等重复请求的返回结果。
     */
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

    /**
     * 用准备阶段的权威交易事实补全商户响应。
     *
     * <p>只暴露渠道返回的非敏感业务字段；卡数据、认证密文和渠道原始报文不得进入该响应对象。</p>
     */
    private void enrichCaptureResult(PaymentCreateCommandDTO commandDTO,
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
        resultDTO.setRootTransactionDateTime(sourceOrderDO.getTransactionDateTime());
        resultDTO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        resultDTO.setPaymentMethod(sourceOrderDO.getPaymentMethod());
        resultDTO.setPaymentBrand(sourceOrderDO.getPaymentBrand());
        resultDTO.setCallbackUrl(resolveCallbackUrl(commandDTO));
        enrichMerchantResponse(resultDTO);
        resultDTO.setTotalAuthorizedAmount(resolveDisplayAuthorizedAmount(sourceOrderDO));
        resultDTO.setTotalAuthorizedCancelAmount(sourceOrderDO.getLabelAuthorizedCancelAmount());
        resultDTO.setTotalCapturedAmount(sourceOrderDO.getLabelCapturedAmount());
        resultDTO.setTotalRefundAmount(sourceOrderDO.getLabelRefundedAmount());
        resultDTO.setTotalRefuseAmount(sourceOrderDO.getLabelChargebackAmount());
    }

    /**
     * 解析商户可见授权金额。
     */
    private BigDecimal resolveDisplayAuthorizedAmount(TransactionOrderDO sourceOrderDO) {
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            return firstPositive(sourceOrderDO.getLabelCapturedAmount(), sourceOrderDO.getLabelAmount());
        }
        return sourceOrderDO.getLabelAuthorizedAmount();
    }

    /**
     * 返回首个正金额。
     */
    private BigDecimal firstPositive(BigDecimal first, BigDecimal second) {
        if (first != null && first.compareTo(BigDecimal.ZERO) > 0) {
            return first;
        }
        return second;
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
     * 补充商户可见响应字段。
     */
    private void enrichMerchantResponse(PaymentCreateResultDTO resultDTO) {
        PaymentOpenApiResultSupport.applyMerchantProjection(resultDTO);
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
     * 解析渠道订单号。
     */
    private String resolveChannelOrderNo(TransactionOrderDO sourceOrderDO) {
        return firstText(
                sourceOrderDO.getChannelOrderNo(),
                sourceOrderDO.getRootTransactionId(),
                sourceOrderDO.getLatestTransactionId());
    }

    /**
     * 请款事务外规划结果；其中路由、币种精度和冻结汇率在主单锁建立前固定。
     *
     * @param sourceOperationDO 原授权动作快照
     * @param routeResolution 原交易路由规划结果
     * @param labelCurrencyExponent 标签币种辅币位数
     * @param transactionCurrencyExponent 渠道交易币种辅币位数
     * @param frozenRate 首次交易冻结业务汇率的事务外规划结果
     */
    private record CapturePreparationPlan(
            TransactionOperationDO sourceOperationDO,
            OriginalTransactionRouteResolver.Resolution routeResolution,
            int labelCurrencyExponent,
            int transactionCurrencyExponent,
            FrozenTransactionRateResolverSupport.Resolution frozenRate) {
    }

    /**
     * 按 ISO 4217 币种精度将十进制金额精确转换为最小货币单位，禁止静默截断小数。
     */
    private Long toMinorAmount(BigDecimal amount, String currency) {
        try {
            return isoDictionaryService.toMinorUnit(amount, currency);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "amount fraction digits exceed currency minor unit", exception);
        }
    }

    /**
     * 使用事务外已解析的币种精度转换金额，避免持有主单锁时再次访问币种字典。
     */
    private Long toMinorAmount(BigDecimal amount, String currency, int currencyExponent) {
        try {
            return amount.movePointRight(currencyExponent).longValueExact();
        } catch (ArithmeticException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "amount fraction digits exceed currency minor unit: " + currency, exception);
        }
    }

    /**
     * 解析币种辅币位数。
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
     * 仅允许标准请款和预授权完成共用本准备链，其他交易类型明确拒绝。
     */
    private PaymentTransactionTypeEnum normalizeCaptureLikeType(PaymentTransactionTypeEnum transactionType) {
        if (transactionType == null || PaymentTransactionTypeEnum.CAPTURE == transactionType) {
            return PaymentTransactionTypeEnum.CAPTURE;
        }
        if (PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION == transactionType) {
            return transactionType;
        }
        throw new ServiceException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED);
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
     * 将业务时间转换为 UTC 时间。
     */
    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime, String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone == null || timeZone.isBlank() ? DEFAULT_TIME_ZONE : timeZone);
        return transactionDateTime.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
