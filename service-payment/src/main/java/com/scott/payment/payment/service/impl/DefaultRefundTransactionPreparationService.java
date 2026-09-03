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
 * @email : scott_x@163.com
 * @description : Refund 本地准备默认实现，确保渠道 Refund 调用前提交幂等、动作单、渠道请求 INIT 和恢复入口。
 * @status : create
 */
@Service
public class DefaultRefundTransactionPreparationService implements RefundTransactionPreparationService {

    /**
     * {@code CHANNEL_REQUEST_ID_PREFIX}常量，统一 {@code DefaultRefundTransactionPreparationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
     * </p>
     */
    private static final String CHANNEL_REQUEST_ID_PREFIX = "CR";

    /**
     * {@code CHANNEL_TRANSACTION_ID_PREFIX}常量，统一 {@code DefaultRefundTransactionPreparationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
     * </p>
     */
    private static final String CHANNEL_TRANSACTION_ID_PREFIX = "CH";

    /**
     * 交易动作范围常量，统一 {@code DefaultRefundTransactionPreparationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";

    /**
     * 默认时间时区常量，统一 {@code DefaultRefundTransactionPreparationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /**
     * {@code PAYMENT_TRANSACTION_AGGREGATE}常量，统一 {@code DefaultRefundTransactionPreparationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String PAYMENT_TRANSACTION_AGGREGATE = "PAYMENT_TRANSACTION";

    /**
     * {@code EVENT_STATUS_INIT}，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String EVENT_STATUS_INIT = "INIT";

    /**
     * {@code DEFAULT_EVENT_MAX_RETRY_COUNT}，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int DEFAULT_EVENT_MAX_RETRY_COUNT = 200;

    /**
     * 初始版本，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int INITIAL_VERSION = 0;

    /**
     * {@code NOT_DELETED}常量，统一 {@code DefaultRefundTransactionPreparationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int NOT_DELETED = 0;

    private final IsoDictionaryService isoDictionaryService;

    private final PaymentChannelRouteService paymentChannelRouteService;

    private final TransactionIdempotencyService transactionIdempotencyService;

    private final TransactionEventOutboxService transactionEventOutboxService;

    private final TransactionRecordService transactionRecordService;

    private final TransactionStateMachineService transactionStateMachineService;

    /** 退款范围金额规则，严格区分原始本金全退和退完当前剩余额度。 */
    private final RefundScopeService refundScopeService;

    /** 退款审批策略解释服务，默认 NONE 保持现有同步行为。 */
    private final RefundApprovalPolicyService refundApprovalPolicyService;

    /** 退款审批工作流；只有策略命中时才创建普通审批表记录。 */
    private final RefundApprovalWorkflowService refundApprovalWorkflowService;

    @Autowired
    public DefaultRefundTransactionPreparationService(IsoDictionaryService isoDictionaryService,
                                                      PaymentChannelRouteService paymentChannelRouteService,
                                                      TransactionIdempotencyService transactionIdempotencyService,
                                                      TransactionEventOutboxService transactionEventOutboxService,
                                                      TransactionRecordService transactionRecordService,
                                                      TransactionStateMachineService transactionStateMachineService,
                                                      RefundScopeService refundScopeService,
                                                      RefundApprovalPolicyService refundApprovalPolicyService,
                                                      RefundApprovalWorkflowService refundApprovalWorkflowService) {
        this.isoDictionaryService = isoDictionaryService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.transactionIdempotencyService = transactionIdempotencyService;
        this.transactionEventOutboxService = transactionEventOutboxService;
        this.transactionRecordService = transactionRecordService;
        this.transactionStateMachineService = transactionStateMachineService;
        this.refundScopeService = refundScopeService;
        this.refundApprovalPolicyService = refundApprovalPolicyService;
        this.refundApprovalWorkflowService = refundApprovalWorkflowService;
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

    /**
     * 创建新的退款幂等记录、动作单和 Outbox。
     *
     * @return 新退款准备结果；并发占用幂等键时返回既有结果
     */
    private RefundPreparationResultDTO prepareNewRefund(PaymentCreateCommandDTO commandDTO,
                                                        String idempotencyKey,
                                                        TransactionOrderDO sourceOrderDO) {
        if (transactionStateMachineService == null) {
            throw new ServiceException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED.getCode(), "transaction state machine is not configured");
        }
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
        if (!transactionIdempotencyService.tryBegin(beginRecord)) {
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(record -> RefundPreparationResultDTO.duplicate(resolveDuplicateRefund(commandDTO, record)))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS));
        }
        TransactionOperationDO sourceOperationDO = transactionRecordService.findSourceOperationByTransactionId(
                commandDTO.getTransactionInfo().getSourceTransactionId(),
                commandDTO.getTransactionInfo().getSourceTransactionDateTime());
        normalizeRefundCommand(commandDTO, sourceOrderDO, sourceOperationDO);
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        PaymentCreateResultDTO resultDTO = buildRefundResult(commandDTO, sourceOrderDO, transactionId);
        int currencyExponent = resolveCurrencyExponent(sourceOrderDO.getTransactionCurrency());
        PaymentRouteResultDTO routeResultDTO;
        try {
            routeResultDTO = restoreSourceRoute(sourceOrderDO, sourceOperationDO);
        } catch (ServiceException exception) {
            if (!OriginalTransactionRejectionSupport.isOriginalTransactionRejected(exception)) {
                throw exception;
            }
            PaymentRouteResultDTO sourceRouteSnapshot = OriginalTransactionRouteResolver.snapshot(
                    sourceOrderDO, sourceOperationDO);
            OriginalTransactionRejectionSupport.apply(resultDTO);
            enrichRefundResult(commandDTO, sourceOrderDO, sourceRouteSnapshot, null, resultDTO);
            OriginalTransactionRejectionSupport.apply(resultDTO);
            recordRefundPreparedFact(
                    commandDTO, sourceOrderDO, sourceRouteSnapshot, null, resultDTO, currencyExponent);
            saveTransactionCreatedEvent(commandDTO, resultDTO);
            completeIdempotency(idempotencyKey, commandDTO, resultDTO);
            RefundPreparationResultDTO target = new RefundPreparationResultDTO();
            target.setCallChannel(false);
            target.setIdempotencyKey(idempotencyKey);
            target.setCommandDTO(commandDTO);
            target.setSourceOrderDO(sourceOrderDO);
            target.setRouteResultDTO(sourceRouteSnapshot);
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
     * 从原交易事实恢复退款渠道身份，退款不得按当前商户路由重新选择其他 MID。
     *
     * @param sourceOrderDO 原交易主单
     * @param sourceOperationDO 原交易动作单
     * @return 原交易渠道和 MID 对应的路由快照
     */
    private PaymentRouteResultDTO restoreSourceRoute(TransactionOrderDO sourceOrderDO,
                                                     TransactionOperationDO sourceOperationDO) {
        return OriginalTransactionRouteResolver.restore(
                paymentChannelRouteService, sourceOrderDO, sourceOperationDO);
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

    /** 锁定原交易主单，串行化退款、请款和撤销对可退金额及生命周期状态的竞争。 */
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

    /** 汇总同一生命周期内所有非终态退款金额，金额单位始终为原交易币种。 */
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

    /** 拒绝存在处理中撤销动作的原交易，避免退款和撤销同时消耗或释放资金状态。 */
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

    /** 仅在请求指纹一致且原记录已有交易号时复用退款幂等结果。 */
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

    /** 对原交易、退款范围、金额、币种和商户操作号生成稳定退款请求指纹。 */
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
     * 解析指纹文本，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 仅返回规范化或计算结果，不直接提交交易状态。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeFingerprintText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 解析指纹金额，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 仅返回规范化或计算结果，不直接提交交易状态。
     * </p>
     * @param amount 金额值，单位必须结合 currency 或同名币种字段解释
     * @return 构造、转换或解析后的业务值
     */
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
     * 解析{@code normalizeRefundCommand}，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 仅返回规范化或计算结果，不直接提交交易状态。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param sourceOrderDO 已从数据库读取或准备持久化的记录对象，状态、版本和审计字段必须保持一致
     * @param sourceOperationDO 已从数据库读取或准备持久化的记录对象，状态、版本和审计字段必须保持一致
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
     * 解析标签币种，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 仅返回规范化或计算结果，不直接提交交易状态。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param sourceOrderDO 已从数据库读取或准备持久化的记录对象，状态、版本和审计字段必须保持一致
     * @return 构造、转换或解析后的业务值
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
     * 校验退款商户订单编号输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 校验失败时按 支付核心服务 统一异常语义中断流程，不返回部分校验结果。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param sourceOrderDO 已从数据库读取或准备持久化的记录对象，状态、版本和审计字段必须保持一致
     */
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

    /** 在审批或渠道调用前持久化退款动作及请求事实，供额度占用、查询和幂等重放使用。 */
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

    /** 将退款创建事件写入原交易生命周期的 FIFO Outbox，审批等待状态也必须可审计。 */
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

    /** 保存退款准备或待审批结果快照，重复请求不得再次占用退款额度或调用渠道。 */
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

    /**
     * 构造退款结果对象，完成字段复制、格式标准化和敏感数据处理。
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param sourceOrderDO 已从数据库读取或准备持久化的记录对象，状态、版本和审计字段必须保持一致
     * @param routeResultDTO route Result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param channelResponse 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @param resultDTO result DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
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
        enrichMerchantResponse(resultDTO, channelResponse);
        resultDTO.setTotalAuthorizedAmount(resolveDisplayAuthorizedAmount(sourceOrderDO));
        resultDTO.setTotalAuthorizedCancelAmount(sourceOrderDO.getAuthorizedCancelAmount());
        resultDTO.setTotalCapturedAmount(resolveDisplayCapturedAmount(sourceOrderDO));
        resultDTO.setTotalRefundAmount(sourceOrderDO.getRefundedAmount());
        resultDTO.setTotalRefuseAmount(sourceOrderDO.getChargebackAmount());
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

    /** 按原交易币种的 ISO 4217 精度精确转换最小货币单位，超精度退款金额直接失败。 */
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
