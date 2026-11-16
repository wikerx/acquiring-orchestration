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
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.VoidTransactionPreparationService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import com.scott.payment.payment.service.dto.VoidPreparationResultDTO;
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
 * 撤销及授权取消交易的本地准备服务。
 *
 * <p>在原交易主单锁内检查终态和所有冲突资金动作，随后原子写入幂等占位、撤销动作、固定渠道请求
 * 身份及 Outbox。上层只能在准备事务提交后调用渠道，避免撤销与请款、退款或增量授权并发推进。</p>
 */
@Service
public class DefaultVoidTransactionPreparationService implements VoidTransactionPreparationService {

    /**
     * 渠道请求记录号前缀；准备阶段生成后固定，供超时恢复和回调关联。
     */
    private static final String CHANNEL_REQUEST_ID_PREFIX = "CR";

    /**
     * 平台侧渠道撤销号前缀；外呼重试必须复用准备阶段持久化的同一编号。
     */
    private static final String CHANNEL_TRANSACTION_ID_PREFIX = "CH";

    /**
     * 交易动作幂等作用域，隔离其他业务域使用的同名幂等键。
     */
    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";

    /**
     * 未显式提供时区时用于计算 UTC 分片时间的业务默认时区。
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /**
     * Outbox 聚合类型，同一支付生命周期按 operationId 关联事件。
     */
    private static final String PAYMENT_TRANSACTION_AGGREGATE = "PAYMENT_TRANSACTION";

    /**
     * Outbox 初始状态，表示撤销事实已落库但事件尚未投递。
     */
    private static final String EVENT_STATUS_INIT = "INIT";

    /**
     * Outbox 最大自动投递次数，超过后转人工处置。
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

    private final TransactionRecordService transactionRecordService;

    private final TransactionStateMachineService transactionStateMachineService;

    /**
     * 撤销准备事实的短事务执行器；原动作、路由和币种精度读取均在锁事务外完成。
     */
    private final TransactionOperations transactionOperations;

    /**
     * 创建授权取消本地准备服务。
     *
     * @param isoDictionaryService 币种辅币位字典
     * @param paymentChannelRouteService 原交易渠道和原 MID 恢复服务
     * @param transactionIdempotencyService 动作幂等服务
     * @param transactionEventOutboxService 可靠交易事件服务
     * @param transactionRecordService 交易事实持久化服务
     * @param transactionStateMachineService 授权取消状态和累计金额规则服务
     */
    public DefaultVoidTransactionPreparationService(IsoDictionaryService isoDictionaryService,
                                                    PaymentChannelRouteService paymentChannelRouteService,
                                                    TransactionIdempotencyService transactionIdempotencyService,
                                                    TransactionEventOutboxService transactionEventOutboxService,
                                                    TransactionRecordService transactionRecordService,
                                                    TransactionStateMachineService transactionStateMachineService) {
        this(isoDictionaryService,
                paymentChannelRouteService,
                transactionIdempotencyService,
                transactionEventOutboxService,
                transactionRecordService,
                transactionStateMachineService,
                null);
    }

    /**
     * Spring 生产构造器，使用交易数据源事务管理器建立主单锁短事务。
     */
    @Autowired
    public DefaultVoidTransactionPreparationService(IsoDictionaryService isoDictionaryService,
                                                    PaymentChannelRouteService paymentChannelRouteService,
                                                    TransactionIdempotencyService transactionIdempotencyService,
                                                    TransactionEventOutboxService transactionEventOutboxService,
                                                    TransactionRecordService transactionRecordService,
                                                    TransactionStateMachineService transactionStateMachineService,
                                                    PlatformTransactionManager transactionManager) {
        this.isoDictionaryService = isoDictionaryService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.transactionIdempotencyService = transactionIdempotencyService;
        this.transactionEventOutboxService = transactionEventOutboxService;
        this.transactionRecordService = transactionRecordService;
        this.transactionStateMachineService = transactionStateMachineService;
        this.transactionOperations = transactionManager == null
                ? null
                : new TransactionTemplate(transactionManager);
    }

    /**
     * 在事务内准备撤销交易。
     *
     * <p>锁定原订单并校验状态机、请款、退款和其他非终态资金动作，防止并发撤销破坏累计金额。</p>
     *
     * @param commandDTO     撤销命令
     * @param idempotencyKey 数据库幂等键
     * @return 新建或幂等命中的撤销准备结果
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public VoidPreparationResultDTO prepareVoid(PaymentCreateCommandDTO commandDTO, String idempotencyKey) {
        if (transactionRecordService == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderDO resolvedSourceOrderDO = resolveSourceOrder(commandDTO);
        commandDTO.setRequestFingerprint(canonicalVoidRequestFingerprint(commandDTO, resolvedSourceOrderDO));
        VoidPreparationResultDTO duplicate = findDuplicateVoid(commandDTO, idempotencyKey);
        if (duplicate != null) {
            return duplicate;
        }
        VoidPreparationPlan plan = planVoid(resolvedSourceOrderDO, commandDTO);
        return executeInTransaction(() -> prepareVoidInTransaction(
                commandDTO, idempotencyKey, resolvedSourceOrderDO, plan));
    }

    /**
     * 在持有原订单行锁的短事务内完成状态、冲突动作校验和撤销事实写入。
     */
    private VoidPreparationResultDTO prepareVoidInTransaction(
            PaymentCreateCommandDTO commandDTO,
            String idempotencyKey,
            TransactionOrderDO resolvedSourceOrderDO,
            VoidPreparationPlan plan) {
        // 主单锁把撤销与请款、退款、增量授权的状态竞争收敛为串行决策。
        TransactionOrderDO sourceOrderDO = lockSourceOrder(resolvedSourceOrderDO);
        // 基于锁内权威原交易身份计算指纹，禁止相同商户操作号被替换后重复撤销。
        commandDTO.setRequestFingerprint(canonicalVoidRequestFingerprint(commandDTO, sourceOrderDO));
        return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                .map(record -> VoidPreparationResultDTO.duplicate(resolveDuplicateVoid(commandDTO, record)))
                .orElseGet(() -> prepareNewVoid(commandDTO, idempotencyKey, sourceOrderDO, plan));
    }

    /**
     * 创建新的撤销幂等记录、动作单和 Outbox。
     *
     * @return 新撤销准备结果；并发占用幂等键时返回既有结果
     */
    private VoidPreparationResultDTO prepareNewVoid(PaymentCreateCommandDTO commandDTO,
                                                    String idempotencyKey,
                                                    TransactionOrderDO sourceOrderDO,
                                                    VoidPreparationPlan plan) {
        if (transactionStateMachineService == null) {
            throw new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE.getCode(),
                    "transaction state machine is not configured");
        }
        TransactionOperationDO sourceOperationDO = plan.sourceOperationDO();
        FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan =
                FrozenRateFollowUpAmountCalculator.calculateVoid(
                        sourceOrderDO, commandDTO.getAmount(), commandDTO.getCurrency());
        commandDTO.setLabelCurrencyExponent(plan.labelCurrencyExponent());
        applyVoidAmountPlan(commandDTO, sourceOrderDO, sourceOperationDO, amountPlan);
        // 双口径金额归一、状态机校验和未决动作扫描共享同一锁快照，避免相反资金动作同时通过。
        transactionStateMachineService.validateFollowUpAction(
                sourceOrderDO, PaymentTransactionTypeEnum.VOID,
                commandDTO.getTransactionAmount(), commandDTO.getTransactionCurrency());
        validateNoConflictingFundAction(commandDTO, sourceOrderDO, LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();
        TransactionIdempotencyDO beginRecord = transactionIdempotencyService.newProcessingRecord(
                TRANSACTION_OPERATION_SCOPE,
                idempotencyKey,
                commandDTO.getMerchantId(),
                sourceOrderDO.getMerchantOrderNo(),
                commandDTO.getMerchantOrderId(),
                PaymentTransactionTypeEnum.VOID.getCode(),
                commandDTO.getTransactionDateTime(),
                DEFAULT_TIME_ZONE,
                commandDTO.getRequestFingerprint(),
                now);
        // 数据库唯一约束兜住跨节点竞争；失败方只能读取同指纹结果，不能生成第二个撤销请求。
        if (!transactionIdempotencyService.tryBegin(beginRecord)) {
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(record -> VoidPreparationResultDTO.duplicate(resolveDuplicateVoid(commandDTO, record)))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                            "void idempotency conflict record is missing"));
        }
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        int currencyExponent = plan.transactionCurrencyExponent();
        PaymentCreateResultDTO resultDTO = buildVoidResult(
                commandDTO, sourceOrderDO, transactionId, plan.labelCurrencyExponent());
        OriginalTransactionRouteResolver.Resolution routeResolution = plan.routeResolution();
        PaymentRouteResultDTO routeResultDTO = routeResolution.requireRouteResult();
        if (routeResolution.rejected()) {
            OriginalTransactionRejectionSupport.apply(resultDTO);
            enrichVoidResult(commandDTO, sourceOrderDO, routeResultDTO, null, resultDTO);
            OriginalTransactionRejectionSupport.apply(resultDTO);
            recordVoidPreparedFact(
                    commandDTO, sourceOrderDO, routeResultDTO, null, resultDTO, currencyExponent);
            saveTransactionCreatedEvent(commandDTO, resultDTO);
            completeIdempotency(idempotencyKey, commandDTO, resultDTO);
            VoidPreparationResultDTO target = new VoidPreparationResultDTO();
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
        enrichVoidResult(commandDTO, sourceOrderDO, routeResultDTO, null, resultDTO);
        PaymentPreparedChannelRequestDTO preparedChannelRequestDTO = prepareChannelRequest(commandDTO, sourceOrderDO);
        PaymentChannelInvokeResultDTO preparedInvokeResultDTO = buildPreparedInvokeResult(
                commandDTO, routeResultDTO, sourceOrderDO.getOperationId(), transactionId, preparedChannelRequestDTO);
        // 先提交动作事实、固定请求身份和恢复事件；上层仅在事务提交后执行真实渠道外呼。
        recordVoidPreparedFact(commandDTO, sourceOrderDO, routeResultDTO, preparedInvokeResultDTO, resultDTO, currencyExponent);
        saveTransactionCreatedEvent(commandDTO, resultDTO);
        completeIdempotency(idempotencyKey, commandDTO, resultDTO);

        VoidPreparationResultDTO target = new VoidPreparationResultDTO();
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
     * 在短事务前读取不可变原动作、原渠道身份和双币种精度。
     */
    private VoidPreparationPlan planVoid(TransactionOrderDO sourceOrderDO,
                                         PaymentCreateCommandDTO commandDTO) {
        TransactionOperationDO sourceOperationDO = transactionRecordService.findSourceOperationByTransactionId(
                commandDTO.getTransactionInfo().getSourceTransactionId(),
                commandDTO.getTransactionInfo().getSourceTransactionDateTime());
        OriginalTransactionRouteResolver.Resolution routeResolution = OriginalTransactionRouteResolver.plan(
                paymentChannelRouteService, sourceOrderDO, sourceOperationDO);
        String labelCurrency = StringUtils.hasText(sourceOrderDO.getLabelCurrency())
                ? sourceOrderDO.getLabelCurrency() : sourceOrderDO.getTransactionCurrency();
        int labelCurrencyExponent = resolveCurrencyExponent(labelCurrency);
        int transactionCurrencyExponent = resolveCurrencyExponent(sourceOrderDO.getTransactionCurrency());
        return new VoidPreparationPlan(
                sourceOperationDO, routeResolution, labelCurrencyExponent, transactionCurrencyExponent);
    }

    /**
     * 在路由规划前快速返回已完成的重复撤销，降低重复请求的主单锁竞争。
     */
    private VoidPreparationResultDTO findDuplicateVoid(PaymentCreateCommandDTO commandDTO,
                                                       String idempotencyKey) {
        return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                .map(record -> VoidPreparationResultDTO.duplicate(resolveDuplicateVoid(commandDTO, record)))
                .orElse(null);
    }

    /**
     * 使用 Spring 事务管理器执行主单锁短事务；无事务管理器仅用于现有直接构造的单元测试兼容。
     */
    private VoidPreparationResultDTO executeInTransaction(
            java.util.function.Supplier<VoidPreparationResultDTO> callback) {
        if (transactionOperations == null) {
            return callback.get();
        }
        return transactionOperations.execute(status -> callback.get());
    }

    /**
     * 锁定原交易主单，串行化撤销与请款、增量授权及退款的状态竞争。
     */
    private TransactionOrderDO lockSourceOrder(TransactionOrderDO sourceOrderDO) {
        if (sourceOrderDO == null
                || !StringUtils.hasText(sourceOrderDO.getOperationId())
                || sourceOrderDO.getTransactionDateTime() == null) {
            return sourceOrderDO;
        }
        return transactionRecordService.lockOrder(sourceOrderDO.getTransactionDateTime(), sourceOrderDO.getOperationId());
    }

    /**
     * 拒绝存在处理中请款、增量授权、退款或撤销的原交易，防止撤销覆盖未决资金动作。
     */
    private void validateNoConflictingFundAction(PaymentCreateCommandDTO commandDTO,
                                                 TransactionOrderDO sourceOrderDO,
                                                 LocalDateTime now) {
        LocalDateTime beginTime = sourceOrderDO.getTransactionDateTime();
        LocalDateTime endTime = laterOf(now, commandDTO.getTransactionDateTime());
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
        List<TransactionOperationDO> incrementalAuthorizations = transactionRecordService.findNonTerminalIncrementalAuthorizations(
                commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), beginTime, endTime);
        if (!incrementalAuthorizations.isEmpty()) {
            throw new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS.getCode(),
                    "source transaction has a pending incremental authorization action");
        }
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            List<TransactionOperationDO> refunds = transactionRecordService.findNonTerminalRefunds(
                    commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), beginTime, endTime);
            if (!refunds.isEmpty()) {
                throw new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS.getCode(),
                        "source transaction has a pending refund action");
            }
        }
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
     * 仅在请求指纹一致且原记录已有交易号时复用撤销幂等结果。
     */
    private PaymentCreateResultDTO resolveDuplicateVoid(PaymentCreateCommandDTO commandDTO,
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
     * 对原交易、撤销类型、币种和商户操作号生成稳定撤销请求指纹。
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
     * 将撤销命令归一为商户标签口径、渠道交易口径和原渠道交易号。
     *
     * <p>商户兼容字段继续使用标签金额和标签币种，渠道专用字段使用渠道剩余授权金额和币种；
     * 两套金额共同沿用首笔交易冻结汇率。缺少原渠道交易号时明确拒绝，避免形成无法关联的渠道撤销。</p>
     */
    private void applyVoidAmountPlan(PaymentCreateCommandDTO commandDTO,
                                    TransactionOrderDO sourceOrderDO,
                                    TransactionOperationDO sourceOperationDO,
                                    FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan) {
        commandDTO.setMerchantOrderNo(sourceOrderDO.getMerchantOrderNo());
        commandDTO.setLabelCurrency(amountPlan.labelCurrency());
        commandDTO.setLabelAmount(amountPlan.labelAmount());
        commandDTO.setCurrency(amountPlan.labelCurrency());
        commandDTO.setAmount(amountPlan.labelAmount());
        if (sourceOperationDO == null || !StringUtils.hasText(sourceOperationDO.getChannelTransactionId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "source channel transaction id is required for VOID");
        }
        if (commandDTO.getTransactionInfo() != null) {
            commandDTO.getTransactionInfo().setSourceChannelTransactionId(sourceOperationDO.getChannelTransactionId());
        }
        commandDTO.setTransactionCurrency(amountPlan.transactionCurrency());
        commandDTO.setTransactionAmount(amountPlan.transactionAmount());
        commandDTO.setTransactionRate(amountPlan.transactionRate());
        commandDTO.setRateSource(amountPlan.rateSource());
        commandDTO.setRateTime(amountPlan.rateTime());
        commandDTO.setDccEnabled(amountPlan.dccEnabled());
        commandDTO.setEdcEnabled(amountPlan.edcEnabled());
    }

    /**
     * 构建撤销结果。
     */
    private PaymentCreateResultDTO buildVoidResult(PaymentCreateCommandDTO commandDTO,
                                                   TransactionOrderDO sourceOrderDO,
                                                   String transactionId,
                                                   int currencyExponent) {
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setOperationId(sourceOrderDO.getOperationId());
        resultDTO.setTransactionId(transactionId);
        resultDTO.setSourceTransactionId(commandDTO.getTransactionInfo().getSourceTransactionId());
        resultDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        resultDTO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        resultDTO.setMerchantId(commandDTO.getMerchantId());
        resultDTO.setSubMerchantInfo(toResultSubMerchantInfo(commandDTO.getSubMerchantInfo()));
        resultDTO.setTransactionType(PaymentTransactionTypeEnum.VOID.getCode());
        resultDTO.setCurrency(commandDTO.getLabelCurrency());
        resultDTO.setAmount(toMinorAmount(
                commandDTO.getLabelAmount(), commandDTO.getLabelCurrency(), currencyExponent));
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
        channelRequest.setAmount(commandDTO.getTransactionAmount());
        channelRequest.setCurrency(commandDTO.getTransactionCurrency());
        channelRequest.setTransactionDateTime(commandDTO.getTransactionDateTime());
        PaymentChannelInvokeResultDTO invokeResultDTO = new PaymentChannelInvokeResultDTO();
        invokeResultDTO.setRequestId(preparedChannelRequestDTO.getRequestId());
        invokeResultDTO.setChannelRequest(channelRequest);
        invokeResultDTO.setRequestStatus("INIT");
        invokeResultDTO.setHttpMethod("PUT");
        invokeResultDTO.setRequestScene(PaymentTransactionTypeEnum.VOID.getCode());
        invokeResultDTO.setRequestStartTime(LocalDateTime.now());
        return invokeResultDTO;
    }

    /**
     * 在渠道调用前持久化撤销动作及原渠道路由请求事实，供失败恢复和幂等重放使用。
     */
    private void recordVoidPreparedFact(PaymentCreateCommandDTO commandDTO,
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
     * 将撤销创建事件写入原交易生命周期的 FIFO Outbox。
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
     * 保存撤销准备结果快照，重复请求只能返回原结果且禁止再次调用渠道。
     */
    private void completeIdempotency(String idempotencyKey, PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        transactionIdempotencyService.complete(
                TRANSACTION_OPERATION_SCOPE,
                idempotencyKey,
                resultDTO.getOperationId(),
                resultDTO.getTransactionId(),
                resultDTO.getStatus(),
                commandDTO.getLabelAmount(),
                commandDTO.getLabelCurrency(),
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
     * 使用原交易累计金额和本次撤销事实补全商户响应，不暴露渠道原始敏感载荷。
     */
    private void enrichVoidResult(PaymentCreateCommandDTO commandDTO,
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
        resultDTO.setTotalCapturedAmount(resolveDisplayCapturedAmount(sourceOrderDO));
        resultDTO.setTotalRefundAmount(sourceOrderDO.getLabelRefundedAmount());
        resultDTO.setTotalRefuseAmount(sourceOrderDO.getLabelChargebackAmount());
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())) {
            resultDTO.setTotalAuthorizedCancelAmount(sourceOrderDO.getLabelAuthorizedAmount());
        }
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
     * 解析商户可见已请款金额。
     */
    private BigDecimal resolveDisplayCapturedAmount(TransactionOrderDO sourceOrderDO) {
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            return firstPositive(sourceOrderDO.getLabelCapturedAmount(), sourceOrderDO.getLabelAmount());
        }
        return sourceOrderDO.getLabelCapturedAmount();
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
     * 撤销事务外规划结果；主单锁内继续以数据库权威状态和冲突动作完成最终决策。
     *
     * @param sourceOperationDO 原交易动作快照
     * @param routeResolution 原交易路由规划结果
     * @param labelCurrencyExponent 标签币种辅币位数
     * @param transactionCurrencyExponent 渠道交易币种辅币位数
     */
    private record VoidPreparationPlan(
            TransactionOperationDO sourceOperationDO,
            OriginalTransactionRouteResolver.Resolution routeResolution,
            int labelCurrencyExponent,
            int transactionCurrencyExponent) {
    }

    /**
     * 按原交易币种的 ISO 4217 精度精确转换最小货币单位。
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
        return target;
    }

    /**
     * 将业务时间转换为 UTC 时间。
     */
    private LocalDateTime toUtcTime(LocalDateTime localTime, String timeZone) {
        if (localTime == null) {
            return null;
        }
        ZoneId sourceZone = StringUtils.hasText(timeZone) ? ZoneId.of(timeZone) : ZoneId.systemDefault();
        return localTime.atZone(sourceZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
