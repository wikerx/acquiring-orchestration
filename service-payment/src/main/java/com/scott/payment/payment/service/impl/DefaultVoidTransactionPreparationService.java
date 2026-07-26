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
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.VoidTransactionPreparationService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import com.scott.payment.payment.service.dto.VoidPreparationResultDTO;
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
 * @classname : DefaultVoidTransactionPreparationService
 * @date : 2026-07-24 00:00
 * @description : Void 本地准备默认实现，确保 Void / Authorization Cancel 调用前提交幂等、动作单、渠道请求 INIT 和恢复入口。
 * @status : create
 */
@Service
public class DefaultVoidTransactionPreparationService implements VoidTransactionPreparationService {

    /**
     * CHANNEL REQUEST ID PREFIX 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String CHANNEL_REQUEST_ID_PREFIX = "CR";

    /**
     * CHANNEL TRANSACTION ID PREFIX 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String CHANNEL_TRANSACTION_ID_PREFIX = "CH";

    /**
     * TRANSACTION OPERATION SCOPE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";

    /**
     * DEFAULT TIME ZONE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /**
     * PAYMENT TRANSACTION AGGREGATE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String PAYMENT_TRANSACTION_AGGREGATE = "PAYMENT_TRANSACTION";

    /**
     * EVENT STATUS INIT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String EVENT_STATUS_INIT = "INIT";

    /**
     * DEFAULT EVENT MAX RETRY COUNT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int DEFAULT_EVENT_MAX_RETRY_COUNT = 200;

    /**
     * INITIAL VERSION 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int INITIAL_VERSION = 0;

    /**
     * NOT DELETED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int NOT_DELETED = 0;

    /**
     * iso Dictionary Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final IsoDictionaryService isoDictionaryService;

    /**
     * payment Channel Route Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final PaymentChannelRouteService paymentChannelRouteService;

    /**
     * transaction Idempotency Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionIdempotencyService transactionIdempotencyService;

    /**
     * transaction Event Outbox Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionEventOutboxService transactionEventOutboxService;

    /**
     * transaction Record Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionRecordService transactionRecordService;

    /**
     * transaction State Machine Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionStateMachineService transactionStateMachineService;

/**
 * 创建 DefaultVoidTransactionPreparationService 实例并注入其运行所需依赖。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param isoDictionaryService iso Dictionary Service 输入值，含义由调用方法名称和所属业务对象限定
 * @param paymentChannelRouteService payment Channel Route Service 输入值，含义由调用方法名称和所属业务对象限定
 * @param transactionIdempotencyService 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param transactionEventOutboxService transaction Event Outbox Service 输入值，含义由调用方法名称和所属业务对象限定
 * @param transactionRecordService transaction Record Service 输入值，含义由调用方法名称和所属业务对象限定
 * @param transactionStateMachineService transaction State Machine Service 输入值，含义由调用方法名称和所属业务对象限定
 */
    public DefaultVoidTransactionPreparationService(IsoDictionaryService isoDictionaryService,
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
    public VoidPreparationResultDTO prepareVoid(PaymentCreateCommandDTO commandDTO, String idempotencyKey) {
        if (transactionRecordService == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderDO resolvedSourceOrderDO = resolveSourceOrder(commandDTO);
        TransactionOrderDO sourceOrderDO = lockSourceOrder(resolvedSourceOrderDO);
        commandDTO.setRequestFingerprint(canonicalVoidRequestFingerprint(commandDTO, sourceOrderDO));
        return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                .map(record -> VoidPreparationResultDTO.duplicate(resolveDuplicateVoid(commandDTO, record)))
                .orElseGet(() -> prepareNewVoid(commandDTO, idempotencyKey, sourceOrderDO));
    }

    private VoidPreparationResultDTO prepareNewVoid(PaymentCreateCommandDTO commandDTO,
                                                    String idempotencyKey,
                                                    TransactionOrderDO sourceOrderDO) {
        if (transactionStateMachineService == null) {
            throw new ServiceException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED.getCode(), "transaction state machine is not configured");
        }
        transactionStateMachineService.validateFollowUpAction(
                sourceOrderDO, PaymentTransactionTypeEnum.VOID, commandDTO.getAmount(), commandDTO.getCurrency());
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
        if (!transactionIdempotencyService.tryBegin(beginRecord)) {
            return transactionIdempotencyService.find(TRANSACTION_OPERATION_SCOPE, idempotencyKey)
                    .map(record -> VoidPreparationResultDTO.duplicate(resolveDuplicateVoid(commandDTO, record)))
                    .orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS));
        }
        TransactionOperationDO sourceOperationDO = transactionRecordService.findSourceOperationByTransactionId(
                commandDTO.getTransactionInfo().getSourceTransactionId());
        normalizeVoidCommand(commandDTO, sourceOrderDO, sourceOperationDO);
        String transactionId = PaymentOrderNoGenerator.nextTransactionId(commandDTO.getTransactionDateTime());
        PaymentRouteResultDTO routeResultDTO = paymentChannelRouteService.route(commandDTO);
        PaymentCreateResultDTO resultDTO = buildVoidResult(commandDTO, sourceOrderDO, transactionId);
        resultDTO.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        resultDTO.setProcessStage(PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode());
        enrichVoidResult(commandDTO, sourceOrderDO, routeResultDTO, null, resultDTO);
        int currencyExponent = resolveCurrencyExponent(sourceOrderDO.getTransactionCurrency());
        PaymentPreparedChannelRequestDTO preparedChannelRequestDTO = prepareChannelRequest(commandDTO, sourceOrderDO);
        PaymentChannelInvokeResultDTO preparedInvokeResultDTO = buildPreparedInvokeResult(
                commandDTO, routeResultDTO, sourceOrderDO.getOperationId(), transactionId, preparedChannelRequestDTO);
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
     * 执行 resolve Source Order 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private TransactionOrderDO resolveSourceOrder(PaymentCreateCommandDTO commandDTO) {
        String sourceTransactionId = commandDTO.getTransactionInfo().getSourceTransactionId();
        TransactionOrderDO sourceOrderDO = transactionRecordService.findSourceOrderByTransactionId(sourceTransactionId);
        if (sourceOrderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return sourceOrderDO;
    }

    /**
     * 执行 lock Source Order 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
 * 执行 validate No Conflicting Fund Action 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param now now 输入值，含义由调用方法名称和所属业务对象限定
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
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                    "source transaction has a pending capture action");
        }
        List<TransactionOperationDO> voids = transactionRecordService.findNonTerminalVoids(
                commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), beginTime, endTime);
        if (!voids.isEmpty()) {
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                    "source transaction has a pending void action");
        }
        List<TransactionOperationDO> incrementalAuthorizations = transactionRecordService.findNonTerminalIncrementalAuthorizations(
                commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), beginTime, endTime);
        if (!incrementalAuthorizations.isEmpty()) {
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                    "source transaction has a pending incremental authorization action");
        }
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            List<TransactionOperationDO> refunds = transactionRecordService.findNonTerminalRefunds(
                    commandDTO.getMerchantId(), sourceOrderDO.getOperationId(), beginTime, endTime);
            if (!refunds.isEmpty()) {
                throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS.getCode(),
                        "source transaction has a pending refund action");
            }
        }
    }

    /**
     * 执行 later Of 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param first first 输入值，含义由调用方法名称和所属业务对象限定
     * @param second second 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
 * 执行 resolve Duplicate Void 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param record record 输入值，含义由调用方法名称和所属业务对象限定
 * @return 解析或查询得到的业务值
 */
    private PaymentCreateResultDTO resolveDuplicateVoid(PaymentCreateCommandDTO commandDTO,
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

/**
 * 执行 canonical Void Request Fingerprint 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 normalize Fingerprint Text 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 标准化后的业务字段值
     */
    private String normalizeFingerprintText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 执行 sha256 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
 * 执行 normalize Void Command 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param sourceOperationDO source Operation DO 输入值，含义由调用方法名称和所属业务对象限定
 */
    private void normalizeVoidCommand(PaymentCreateCommandDTO commandDTO,
                                      TransactionOrderDO sourceOrderDO,
                                      TransactionOperationDO sourceOperationDO) {
        BigDecimal transactionAmount = commandDTO.getAmount() == null ? sourceOrderDO.getTransactionAmount() : commandDTO.getAmount();
        commandDTO.setMerchantOrderNo(sourceOrderDO.getMerchantOrderNo());
        commandDTO.setLabelCurrency(resolveLabelCurrency(commandDTO, sourceOrderDO));
        commandDTO.setLabelAmount(commandDTO.getLabelAmount() == null ? transactionAmount : commandDTO.getLabelAmount());
        commandDTO.setCurrency(sourceOrderDO.getTransactionCurrency());
        commandDTO.setAmount(transactionAmount);
        if (sourceOperationDO == null || !StringUtils.hasText(sourceOperationDO.getChannelTransactionId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "source channel transaction id is required for VOID");
        }
        if (commandDTO.getTransactionInfo() != null) {
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
     * 执行 resolve Label Currency 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
     * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的 ISO 4217 币种代码
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
 * 执行 build Void Result 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @return 转换或构建后的目标对象
 */
    private PaymentCreateResultDTO buildVoidResult(PaymentCreateCommandDTO commandDTO,
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
        resultDTO.setTransactionType(PaymentTransactionTypeEnum.VOID.getCode());
        resultDTO.setCurrency(sourceOrderDO.getTransactionCurrency());
        resultDTO.setAmount(toMinorAmount(sourceOrderDO.getTransactionAmount(), sourceOrderDO.getTransactionCurrency()));
        return resultDTO;
    }

/**
 * 执行 prepare Channel Request 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @return 方法签名声明的返回值，具体结构由返回类型定义
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
 * 执行 build Prepared Invoke Result 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param preparedChannelRequestDTO prepared Channel Request DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @return 转换或构建后的目标对象
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
 * 执行 record Void Prepared Fact 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
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
     * 执行 save Transaction Created Event 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
     * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
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
     * 执行 complete Idempotency 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param idempotencyKey idempotency Key 输入值，含义由调用方法名称和所属业务对象限定
     * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
     * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void completeIdempotency(String idempotencyKey, PaymentCreateCommandDTO commandDTO, PaymentCreateResultDTO resultDTO) {
        transactionIdempotencyService.complete(
                TRANSACTION_OPERATION_SCOPE,
                idempotencyKey,
                resultDTO.getOperationId(),
                resultDTO.getTransactionId(),
                resultDTO.getStatus(),
                commandDTO.getTransactionAmount(),
                resultDTO.getCurrency(),
                JsonUtils.toJsonString(resultDTO));
    }

    /**
     * 执行 to Duplicate Result 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param record record 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
 * 执行 enrich Void Result 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelResponse channel Response 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
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
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())) {
            resultDTO.setTotalAuthorizedCancelAmount(sourceOrderDO.getAuthorizedAmount());
        }
    }

    /**
     * 执行 resolve Display Authorized Amount 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
     * @return 按渠道协议格式化后的金额字符串或金额计算结果
     */
    private BigDecimal resolveDisplayAuthorizedAmount(TransactionOrderDO sourceOrderDO) {
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            return firstPositive(sourceOrderDO.getCapturedAmount(), sourceOrderDO.getTransactionAmount());
        }
        return sourceOrderDO.getAuthorizedAmount();
    }

    /**
     * 执行 resolve Display Captured Amount 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
     * @return 按渠道协议格式化后的金额字符串或金额计算结果
     */
    private BigDecimal resolveDisplayCapturedAmount(TransactionOrderDO sourceOrderDO) {
        if (PaymentTransactionTypeEnum.PAYMENT.getCode().equals(sourceOrderDO.getTransactionType())) {
            return firstPositive(sourceOrderDO.getCapturedAmount(), sourceOrderDO.getTransactionAmount());
        }
        return sourceOrderDO.getCapturedAmount();
    }

    /**
     * 执行 first Positive 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param first first 输入值，含义由调用方法名称和所属业务对象限定
     * @param second second 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private BigDecimal firstPositive(BigDecimal first, BigDecimal second) {
        if (first != null && first.compareTo(BigDecimal.ZERO) > 0) {
            return first;
        }
        return second;
    }

    /**
     * 执行 resolve Callback Url 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
 * 执行 enrich Merchant Response 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelResponse channel Response 输入值，含义由调用方法名称和所属业务对象限定
 */
    private void enrichMerchantResponse(PaymentCreateResultDTO resultDTO,
                                        com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse channelResponse) {
        resultDTO.setMerchantResponseCode(resolveMerchantResponseCode(resultDTO.getStatus()));
        resultDTO.setMerchantResponseMessage(resolveMerchantResponseMessage(resultDTO, channelResponse));
    }

    /**
     * 执行 resolve Merchant Response Code 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param transactionStatus 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 解析或查询得到的业务值
     */
    private String resolveMerchantResponseCode(String transactionStatus) {
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_SUCCESS.getCode();
        }
        if (PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_REJECTED.getCode();
        }
        return ApiResultEnum.PROCESSING.getCode();
    }

/**
 * 执行 resolve Merchant Response Message 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param response response 输入值，含义由调用方法名称和所属业务对象限定
 * @return 解析或查询得到的业务值
 */
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

    /**
     * 执行 join Code And Message 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param code code 输入值，含义由调用方法名称和所属业务对象限定
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String joinCodeAndMessage(String code, String message) {
        if (StringUtils.hasText(code) && StringUtils.hasText(message)) {
            return code + ": " + message;
        }
        return firstText(code, message);
    }

    /**
     * 执行 first Text 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param values values 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 resolve Channel Order No 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sourceOrderDO source Order DO 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolveChannelOrderNo(TransactionOrderDO sourceOrderDO) {
        return StringUtils.hasText(sourceOrderDO.getRootTransactionId())
                ? sourceOrderDO.getRootTransactionId()
                : sourceOrderDO.getLatestTransactionId();
    }

    /**
     * 执行 to Minor Amount 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param amount 金额值，单位由关联币种决定，调用前必须完成币种精度校验
     * @param currency 币种代码，格式为 ISO 4217 三位大写字母
     * @return 按渠道协议格式化后的金额字符串或金额计算结果
     */
    private Long toMinorAmount(BigDecimal amount, String currency) {
        try {
            return isoDictionaryService.toMinorUnit(amount, currency);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "amount fraction digits exceed currency minor unit", exception);
        }
    }

    /**
     * 执行 resolve Currency Exponent 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param currency 币种代码，格式为 ISO 4217 三位大写字母
     * @return 标准化后的 ISO 4217 币种代码
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
     * 执行 default Transaction Rate 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private BigDecimal defaultTransactionRate() {
        return new BigDecimal("1.00000000");
    }

    /**
     * 执行 normalize Currency 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param currency 币种代码，格式为 ISO 4217 三位大写字母
     * @return 标准化后的 ISO 4217 币种代码
     */
    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 执行 to Result Sub Merchant Info 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 执行 to Utc Time 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultVoidTransactionPreparationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param localTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param timeZone 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 转换或构建后的目标对象
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
