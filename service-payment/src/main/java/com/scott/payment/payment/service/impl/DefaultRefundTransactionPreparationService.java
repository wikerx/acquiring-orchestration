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
import com.scott.payment.payment.service.RefundApprovalPolicyService;
import com.scott.payment.payment.service.RefundApprovalWorkflowService;
import com.scott.payment.payment.service.RefundScopeService;
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
import org.springframework.beans.factory.annotation.Autowired;
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
 * 退款交易的本地准备服务。
 *
 * <p>在原交易主单锁内计算已退款与未决退款累计金额，执行退款范围和审批策略，并原子写入幂等占位、
 * 动作单、固定渠道请求身份及 Outbox。准备事务提交前不得调用渠道，避免并发退款共同透支可退余额。</p>
 */
@Service
public class DefaultRefundTransactionPreparationService implements RefundTransactionPreparationService {

    /**
     * 渠道请求记录号前缀；准备阶段持久化后供超时恢复、查询和回调关联。
     */
    private static final String CHANNEL_REQUEST_ID_PREFIX = "CR";

    /**
     * 平台侧渠道退款号前缀；审批后执行和失败重试必须复用同一编号。
     */
    private static final String CHANNEL_TRANSACTION_ID_PREFIX = "CH";

    /**
     * 交易动作幂等作用域，防止与其他业务域的同名幂等键冲突。
     */
    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";

    /**
     * 未显式提供交易时区时用于生成 UTC 分片时间的业务默认时区。
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /**
     * Outbox 聚合类型，同一支付生命周期按 operationId 保持事件关联。
     */
    private static final String PAYMENT_TRANSACTION_AGGREGATE = "PAYMENT_TRANSACTION";

    /**
     * Outbox 初始状态，表示退款事实已落库但事件尚未成功投递。
     */
    private static final String EVENT_STATUS_INIT = "INIT";

    /**
     * Outbox 最大自动投递次数，超过上限后转人工处置。
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
     * 退款范围金额规则，严格区分原始本金全退和退完当前剩余额度。
     */
    private final RefundScopeService refundScopeService;

    /**
     * 退款审批策略解释服务，默认 NONE 保持现有同步行为。
     */
    private final RefundApprovalPolicyService refundApprovalPolicyService;

    /**
     * 退款审批工作流；只有策略命中时才创建普通审批表记录。
     */
    private final RefundApprovalWorkflowService refundApprovalWorkflowService;

    /**
     * 退款准备事实的短事务执行器；原动作、路由和币种精度读取均在锁事务外完成。
     */
    private final TransactionOperations transactionOperations;

    /**
     * 创建退款本地准备服务。
     *
     * @param isoDictionaryService 币种辅币位字典
     * @param paymentChannelRouteService 原交易渠道和原 MID 恢复服务
     * @param transactionIdempotencyService 动作幂等服务
     * @param transactionEventOutboxService 可靠交易事件服务
     * @param transactionRecordService 交易事实持久化服务
     * @param transactionStateMachineService 退款金额和状态规则服务
     * @param refundScopeService 全额、部分和剩余全额退款范围服务
     * @param refundApprovalPolicyService 退款审批策略解释服务
     * @param refundApprovalWorkflowService 退款审批事实和执行 Outbox 服务
     */
    public DefaultRefundTransactionPreparationService(IsoDictionaryService isoDictionaryService,
                                                      PaymentChannelRouteService paymentChannelRouteService,
                                                      TransactionIdempotencyService transactionIdempotencyService,
                                                      TransactionEventOutboxService transactionEventOutboxService,
                                                      TransactionRecordService transactionRecordService,
                                                      TransactionStateMachineService transactionStateMachineService,
                                                      RefundScopeService refundScopeService,
                                                      RefundApprovalPolicyService refundApprovalPolicyService,
                                                      RefundApprovalWorkflowService refundApprovalWorkflowService) {
        this(isoDictionaryService,
                paymentChannelRouteService,
                transactionIdempotencyService,
                transactionEventOutboxService,
                transactionRecordService,
                transactionStateMachineService,
                refundScopeService,
                refundApprovalPolicyService,
                refundApprovalWorkflowService,
                null);
    }

    /**
     * Spring 生产构造器，使用交易数据源事务管理器建立主单锁短事务。
     */
    @Autowired
    public DefaultRefundTransactionPreparationService(IsoDictionaryService isoDictionaryService,
                                                      PaymentChannelRouteService paymentChannelRouteService,
                                                      TransactionIdempotencyService transactionIdempotencyService,
                                                      TransactionEventOutboxService transactionEventOutboxService,
                                                      TransactionRecordService transactionRecordService,
                                                      TransactionStateMachineService transactionStateMachineService,
                                                      RefundScopeService refundScopeService,
                                                      RefundApprovalPolicyService refundApprovalPolicyService,
                                                      RefundApprovalWorkflowService refundApprovalWorkflowService,
                                                      PlatformTransactionManager transactionManager) {
        this.isoDictionaryService = isoDictionaryService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.transactionIdempotencyService = transactionIdempotencyService;
        this.transactionEventOutboxService = transactionEventOutboxService;
        this.transactionRecordService = transactionRecordService;
        this.transactionStateMachineService = transactionStateMachineService;
        this.refundScopeService = refundScopeService;
        this.refundApprovalPolicyService = refundApprovalPolicyService;
        this.refundApprovalWorkflowService = refundApprovalWorkflowService;
        this.transactionOperations = transactionManager == null
                ? null
                : new TransactionTemplate(transactionManager);
    }

    /**
     * 兼容现有单元测试和早期手工装配；默认审批关闭，不改变原同步退款行为。
     */
    public DefaultRefundTransactionPreparationService(IsoDictionaryService isoDictionaryService,
                                                       PaymentChannelRouteService paymentChannelRouteService,
                                                       TransactionIdempotencyService transactionIdempotencyService,
                                                       TransactionEventOutboxService transactionEventOutboxService,
                                                       TransactionRecordService transactionRecordService,
                                                       TransactionStateMachineService transactionStateMachineService) {
        this(isoDictionaryService, paymentChannelRouteService, transactionIdempotencyService,
                transactionEventOutboxService, transactionRecordService, transactionStateMachineService,
                new RefundScopeService(),
                new RefundApprovalPolicyService(new com.scott.payment.payment.config.RefundManagementProperties()),
                null);
    }

    /**
     * 在事务内准备退款交易。
     *
     * <p>锁定原订单后按数据库已完成及进行中退款计算可退额度，并校验与撤销动作的冲突；
     * Redis 不参与退款额度事实计算。</p>
     *
     * @param commandDTO     退款命令
     * @param idempotencyKey 数据库幂等键
     * @return 新建或幂等命中的退款准备结果
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public RefundPreparationResultDTO prepareRefund(PaymentCreateCommandDTO commandDTO, String idempotencyKey) {
        if (transactionRecordService == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderDO resolvedSourceOrderDO = resolveSourceOrder(commandDTO);
        commandDTO.setRequestFingerprint(canonicalRefundRequestFingerprint(commandDTO, resolvedSourceOrderDO));
        RefundPreparationResultDTO duplicate = findDuplicateRefund(commandDTO, idempotencyKey);
        if (duplicate != null) {
            return duplicate;
        }
        RefundPreparationPlan plan = planRefund(resolvedSourceOrderDO, commandDTO);
        return executeInTransaction(() -> prepareRefundInTransaction(
                commandDTO, idempotencyKey, resolvedSourceOrderDO, plan));
    }

    /**
     * 在持有原订单行锁的短事务内完成可退余额、冲突动作、审批策略和事实写入。
     */
    private RefundPreparationResultDTO prepareRefundInTransaction(
            PaymentCreateCommandDTO commandDTO,
            String idempotencyKey,
            TransactionOrderDO resolvedSourceOrderDO,
            RefundPreparationPlan plan) {
        // 主单锁保证退款、撤销及其他资金动作基于同一可退余额快照串行决策。
        TransactionOrderDO sourceOrderDO = lockSourceOrder(resolvedSourceOrderDO);
        // 指纹在锁内使用原交易权威币种生成，避免同一商户操作号替换金额后重复退款。
        commandDTO.setRequestFingerprint(canonicalRefundRequestFingerprint(commandDTO, sourceOrderDO));
        return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                .map(record -> RefundPreparationResultDTO.duplicate(resolveDuplicateRefund(commandDTO, record)))
                .orElseGet(() -> prepareNewRefund(commandDTO, idempotencyKey, sourceOrderDO, plan));
    }

    /**
     * 创建新的退款幂等记录、动作单和 Outbox。
     *
     * @return 新退款准备结果；并发占用幂等键时返回既有结果
     */
    private RefundPreparationResultDTO prepareNewRefund(PaymentCreateCommandDTO commandDTO,
                                                        String idempotencyKey,
                                                        TransactionOrderDO sourceOrderDO,
                                                        RefundPreparationPlan plan) {
        if (transactionStateMachineService == null) {
            throw new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE.getCode(),
                    "transaction state machine is not configured");
        }
        // 已成功退款与全部非终态退款共同占用可退余额，校验和退款范围计算必须在同一主单锁内完成。
        transactionStateMachineService.validateFollowUpAction(
                sourceOrderDO, PaymentTransactionTypeEnum.REFUND, commandDTO.getAmount(), commandDTO.getCurrency());
        BigDecimal pendingRefundAmount = validateRefundCapacity(commandDTO, sourceOrderDO, LocalDateTime.now());
        validateNoNonTerminalVoid(commandDTO, sourceOrderDO, LocalDateTime.now());
        commandDTO.setRefundScope(refundScopeService.resolve(
                sourceOrderDO, commandDTO.getAmount(), pendingRefundAmount));
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
        // 唯一约束兜住跨节点并发；竞争失败只能复用同指纹结果，禁止创建第二笔渠道退款。
        if (!transactionIdempotencyService.tryBegin(beginRecord)) {
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(record -> RefundPreparationResultDTO.duplicate(resolveDuplicateRefund(commandDTO, record)))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                            "refund idempotency conflict record is missing"));
        }
        TransactionOperationDO sourceOperationDO = plan.sourceOperationDO();
        normalizeRefundCommand(commandDTO, sourceOrderDO, sourceOperationDO);
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        int currencyExponent = plan.transactionCurrencyExponent();
        PaymentCreateResultDTO resultDTO = buildRefundResult(
                commandDTO, sourceOrderDO, transactionId, currencyExponent);
        OriginalTransactionRouteResolver.Resolution routeResolution = plan.routeResolution();
        PaymentRouteResultDTO routeResultDTO = routeResolution.requireRouteResult();
        if (routeResolution.rejected()) {
            OriginalTransactionRejectionSupport.apply(resultDTO);
            enrichRefundResult(commandDTO, sourceOrderDO, routeResultDTO, null, resultDTO);
            OriginalTransactionRejectionSupport.apply(resultDTO);
            recordRefundPreparedFact(
                    commandDTO, sourceOrderDO, routeResultDTO, null, resultDTO, currencyExponent);
            saveTransactionCreatedEvent(commandDTO, resultDTO);
            completeIdempotency(idempotencyKey, commandDTO, resultDTO);
            RefundPreparationResultDTO target = new RefundPreparationResultDTO();
            target.setCallChannel(false);
            target.setIdempotencyKey(idempotencyKey);
            target.setCommandDTO(commandDTO);
            target.setSourceOrderDO(sourceOrderDO);
            target.setRouteResultDTO(routeResultDTO);
            target.setResultDTO(resultDTO);
            target.setCurrencyExponent(currencyExponent);
            return target;
        }
        boolean approvalRequired = refundApprovalPolicyService.requiresApproval(commandDTO.getRefundScope());
        resultDTO.setStatus(approvalRequired
                ? PaymentTransactionStatusEnum.PENDING.getCode()
                : PaymentTransactionStatusEnum.PROCESSING.getCode());
        resultDTO.setProcessStage(approvalRequired
                ? PaymentProcessStageEnum.WAITING_APPROVAL.getCode()
                : PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode());
        enrichRefundResult(commandDTO, sourceOrderDO, routeResultDTO, null, resultDTO);
        PaymentPreparedChannelRequestDTO preparedChannelRequestDTO = prepareChannelRequest(commandDTO, sourceOrderDO);
        PaymentChannelInvokeResultDTO preparedInvokeResultDTO = buildPreparedInvokeResult(
                commandDTO, routeResultDTO, sourceOrderDO.getOperationId(), transactionId, preparedChannelRequestDTO);
        // 即使需要审批，也先冻结唯一渠道请求身份；审批通过后复用该身份，不能重新生成退款号。
        recordRefundPreparedFact(commandDTO, sourceOrderDO, routeResultDTO, preparedInvokeResultDTO, resultDTO, currencyExponent);
        if (approvalRequired) {
            if (refundApprovalWorkflowService == null) {
                throw new IllegalStateException("refund approval workflow is not configured");
            }
            refundApprovalWorkflowService.createPendingApproval(
                    commandDTO,
                    sourceOrderDO,
                    resultDTO,
                    refundApprovalPolicyService.currentPolicyCode(),
                    refundApprovalPolicyService.approvalExpireMinutes(),
                    now);
        }
        saveTransactionCreatedEvent(commandDTO, resultDTO);
        completeIdempotency(idempotencyKey, commandDTO, resultDTO);

        RefundPreparationResultDTO target = new RefundPreparationResultDTO();
        // 审批分支只提交本地事实和审批任务；未审批前不得触发真实渠道调用。
        target.setCallChannel(!approvalRequired);
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
     * 在短事务前读取不可变原动作、原渠道身份和交易币种精度。
     */
    private RefundPreparationPlan planRefund(TransactionOrderDO sourceOrderDO,
                                             PaymentCreateCommandDTO commandDTO) {
        TransactionOperationDO sourceOperationDO = transactionRecordService.findSourceOperationByTransactionId(
                commandDTO.getTransactionInfo().getSourceTransactionId(),
                commandDTO.getTransactionInfo().getSourceTransactionDateTime());
        OriginalTransactionRouteResolver.Resolution routeResolution = OriginalTransactionRouteResolver.plan(
                paymentChannelRouteService, sourceOrderDO, sourceOperationDO);
        int transactionCurrencyExponent = resolveCurrencyExponent(sourceOrderDO.getTransactionCurrency());
        return new RefundPreparationPlan(
                sourceOperationDO, routeResolution, transactionCurrencyExponent);
    }

    /**
     * 在路由规划前快速返回已完成的重复退款，降低重复请求的主单锁竞争。
     */
    private RefundPreparationResultDTO findDuplicateRefund(PaymentCreateCommandDTO commandDTO,
                                                           String idempotencyKey) {
        return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                .map(record -> RefundPreparationResultDTO.duplicate(resolveDuplicateRefund(commandDTO, record)))
                .orElse(null);
    }

    /**
     * 使用 Spring 事务管理器执行主单锁短事务；无事务管理器仅用于现有直接构造的单元测试兼容。
     */
    private RefundPreparationResultDTO executeInTransaction(
            java.util.function.Supplier<RefundPreparationResultDTO> callback) {
        if (transactionOperations == null) {
            return callback.get();
        }
        return transactionOperations.execute(status -> callback.get());
    }

    /**
     * 锁定原交易主单，串行化退款、请款和撤销对可退金额及生命周期状态的竞争。
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
     * 基于锁读主单的可退余额扣除全部处理中退款后校验本次额度。
     * <p>处理中退款也必须预占额度，不能等渠道成功后再校验，否则并发请求可能造成超额退款。</p>
     */
    private BigDecimal validateRefundCapacity(PaymentCreateCommandDTO commandDTO,
                                              TransactionOrderDO sourceOrderDO,
                                              LocalDateTime now) {
        BigDecimal requestAmount = commandDTO.getAmount();
        BigDecimal availableRefundAmount = sourceOrderDO.getAvailableRefundAmount() == null
                ? BigDecimal.ZERO : sourceOrderDO.getAvailableRefundAmount();
        BigDecimal pendingRefundAmount = sumNonTerminalRefundAmount(commandDTO, sourceOrderDO, now);
        if (availableRefundAmount.subtract(pendingRefundAmount).compareTo(requestAmount) < 0) {
            throw new ServiceException(ApiResultEnum.REFUND_AMOUNT_EXCEEDS_AVAILABLE);
        }
        return pendingRefundAmount;
    }

    /**
     * 汇总同一生命周期内所有非终态退款金额，金额单位始终为原交易币种。
     */
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

    /**
     * 拒绝存在处理中撤销动作的原交易，避免退款和撤销同时消耗或释放资金状态。
     */
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
        throw new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS.getCode(),
                "source transaction has a pending void action");
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
     * 仅在请求指纹一致且原记录已有交易号时复用退款幂等结果。
     */
    private PaymentCreateResultDTO resolveDuplicateRefund(PaymentCreateCommandDTO commandDTO,
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
     * 对原交易、退款范围、金额、币种和商户操作号生成稳定退款请求指纹。
     */
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

    /**
     * 指纹文本统一去除首尾空白并转大写，避免大小写差异绕过幂等冲突检测。
     */
    private String normalizeFingerprintText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 指纹金额去除无意义尾零，使等值退款金额命中同一幂等请求。
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
     * 将退款命令归一到原交易币种和原渠道交易号。
     *
     * <p>退款金额使用原交易币种且不二次换汇；展示金额和币种继续保留商户请求语义。</p>
     */
    private void normalizeRefundCommand(PaymentCreateCommandDTO commandDTO,
                                        TransactionOrderDO sourceOrderDO,
                                        TransactionOperationDO sourceOperationDO) {
        validateRefundMerchantOrderNo(commandDTO, sourceOrderDO);
        BigDecimal transactionAmount = commandDTO.getAmount();
        commandDTO.setMerchantOrderNo(sourceOrderDO.getMerchantOrderNo());
        commandDTO.setLabelCurrency(resolveLabelCurrency(commandDTO, sourceOrderDO));
        commandDTO.setLabelAmount(commandDTO.getLabelAmount() == null ? transactionAmount : commandDTO.getLabelAmount());
        commandDTO.setCurrency(sourceOrderDO.getTransactionCurrency());
        if (commandDTO.getTransactionInfo() != null && sourceOperationDO != null) {
            commandDTO.getTransactionInfo().setSourceChannelTransactionId(sourceOperationDO.getChannelTransactionId());
        }
        commandDTO.setTransactionCurrency(sourceOrderDO.getTransactionCurrency());
        commandDTO.setTransactionAmount(transactionAmount);
        commandDTO.setTransactionRate(defaultTransactionRate());
        commandDTO.setRateSource(null);
        commandDTO.setRateTime(null);
        commandDTO.setDccEnabled(0);
        commandDTO.setEdcEnabled(0);
    }

    /**
     * 按本次展示币种、请求币种、原交易展示币种、原交易币种的顺序解析商户展示币种。
     */
    private String resolveLabelCurrency(PaymentCreateCommandDTO commandDTO, TransactionOrderDO sourceOrderDO) {
        if (StringUtils.hasText(commandDTO.getLabelCurrency())) {
            return normalizeCurrency(commandDTO.getLabelCurrency());
        }
        if (StringUtils.hasText(commandDTO.getCurrency())) {
            return normalizeCurrency(commandDTO.getCurrency());
        }
        if (StringUtils.hasText(sourceOrderDO.getLabelCurrency())) {
            return sourceOrderDO.getLabelCurrency();
        }
        return sourceOrderDO.getTransactionCurrency();
    }

    /**
     * 可选商户订单号一旦提供就必须与原交易一致，防止跨订单引用同一退款源交易。
     */
    private void validateRefundMerchantOrderNo(PaymentCreateCommandDTO commandDTO, TransactionOrderDO sourceOrderDO) {
        if (!StringUtils.hasText(commandDTO.getMerchantOrderNo())) {
            return;
        }
        if (!commandDTO.getMerchantOrderNo().equals(sourceOrderDO.getMerchantOrderNo())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "merchant order number must match source transaction");
        }
    }

    /**
     * 构建退款结果。
     */
    private PaymentCreateResultDTO buildRefundResult(PaymentCreateCommandDTO commandDTO,
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
        resultDTO.setTransactionType(PaymentTransactionTypeEnum.REFUND.getCode());
        resultDTO.setCurrency(sourceOrderDO.getTransactionCurrency());
        resultDTO.setAmount(toMinorAmount(
                commandDTO.getAmount(), sourceOrderDO.getTransactionCurrency(), currencyExponent));
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
        invokeResultDTO.setRequestScene(PaymentTransactionTypeEnum.REFUND.getCode());
        invokeResultDTO.setRequestStartTime(LocalDateTime.now());
        return invokeResultDTO;
    }

    /**
     * 在审批或渠道调用前持久化退款动作及请求事实，供额度占用、查询和幂等重放使用。
     */
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

    /**
     * 将退款创建事件写入原交易生命周期的 FIFO Outbox，审批等待状态也必须可审计。
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
     * 保存退款准备或待审批结果快照，重复请求不得再次占用退款额度或调用渠道。
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
     * 使用原交易累计金额与本次退款事实补全商户响应，不暴露渠道原始敏感载荷。
     */
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
        resultDTO.setRootTransactionDateTime(sourceOrderDO.getTransactionDateTime());
        resultDTO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        resultDTO.setPaymentMethod(sourceOrderDO.getPaymentMethod());
        resultDTO.setPaymentBrand(sourceOrderDO.getPaymentBrand());
        resultDTO.setCallbackUrl(resolveCallbackUrl(commandDTO));
        enrichMerchantResponse(resultDTO);
        resultDTO.setTotalAuthorizedAmount(resolveDisplayAuthorizedAmount(sourceOrderDO));
        resultDTO.setTotalAuthorizedCancelAmount(sourceOrderDO.getAuthorizedCancelAmount());
        resultDTO.setTotalCapturedAmount(resolveDisplayCapturedAmount(sourceOrderDO));
        resultDTO.setTotalRefundAmount(sourceOrderDO.getRefundedAmount());
        resultDTO.setTotalRefuseAmount(sourceOrderDO.getChargebackAmount());
    }

    /**
     * 解析商户可见授权金额。
     */
    private BigDecimal resolveDisplayAuthorizedAmount(TransactionOrderDO sourceOrderDO) {
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            return firstPositive(sourceOrderDO.getCapturedAmount(), sourceOrderDO.getTransactionAmount());
        }
        return sourceOrderDO.getAuthorizedAmount();
    }

    /**
     * 解析商户可见已请款金额。
     */
    private BigDecimal resolveDisplayCapturedAmount(TransactionOrderDO sourceOrderDO) {
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            return firstPositive(sourceOrderDO.getCapturedAmount(), sourceOrderDO.getTransactionAmount());
        }
        return sourceOrderDO.getCapturedAmount();
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
        return StringUtils.hasText(sourceOrderDO.getRootTransactionId())
                ? sourceOrderDO.getRootTransactionId()
                : sourceOrderDO.getLatestTransactionId();
    }

    /**
     * 退款事务外规划结果；主单锁内继续以数据库权威余额和状态完成最终决策。
     *
     * @param sourceOperationDO 原交易动作快照
     * @param routeResolution 原交易路由规划结果
     * @param transactionCurrencyExponent 交易币种辅币位数
     */
    private record RefundPreparationPlan(
            TransactionOperationDO sourceOperationDO,
            OriginalTransactionRouteResolver.Resolution routeResolution,
            int transactionCurrencyExponent) {
    }

    /**
     * 按原交易币种的 ISO 4217 精度精确转换最小货币单位，超精度退款金额直接失败。
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
     * 规范化币种。
     */
    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 返回无换汇场景的单位交易汇率。
     */
    private BigDecimal defaultTransactionRate() {
        return new BigDecimal("1.00000000");
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
