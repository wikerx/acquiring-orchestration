package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionCommand;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.LocatorFacts;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.SourceContext;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.clearing.entity.ClearingPaymentMethodInfoDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionLocatorDO;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.mapper.ClearingTransactionContextMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.ClearingPreparationService;
import com.scott.payment.clearing.service.FeeConfigurationSnapshotService;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReservePolicySnapshot;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingPreparationService
 * @date : 2026-08-26 11:20
 * @email : scott_x@163.com
 * @description : 清分事务外准备实现，使用 locator 恢复精确分片时间并校验源动作，远程缓存或主从降级均在阶段 B 事务外完成。
 * @status : create
 */
@Service
public class DefaultClearingPreparationService implements ClearingPreparationService {

    private static final Set<String> NON_EXECUTED_THREE_DS_INDICATORS =
            Set.of("N", "NO", "NONE", "NOT_REQUIRED", "SKIPPED", "FAILED", "REQUIRED");
    private static final Set<String> RESERVE_HOLD_TRANSACTION_TYPES =
            Set.of("PAYMENT", "CAPTURE", "PRE_AUTH_COMPLETION");

    /** 读取非分表定位记录、支付维度和真实风险调用事实。 */
    private final ClearingTransactionContextMapper contextMapper;
    /** 按 locator 提供的真实分片时间读取源动作。 */
    private final ClearingTransactionOperationMapper operationMapper;
    /** 校验源动作是否已经形成不可逆清分结果。 */
    private final ClearingTransactionFinanceStateMapper financeStateMapper;
    /** 加载并校验动作受理时冻结的确切费用版本。 */
    private final FeeConfigurationSnapshotService snapshotService;

    /**
     * 创建清分事务外准备服务。
     *
     * @param contextMapper 清分定位和支付维度 Mapper
     * @param operationMapper 交易动作精确读取 Mapper
     * @param financeStateMapper 动作清分状态 Mapper
     * @param snapshotService 确切费用版本加载服务
     */
    public DefaultClearingPreparationService(ClearingTransactionContextMapper contextMapper,
                                             ClearingTransactionOperationMapper operationMapper,
                                             ClearingTransactionFinanceStateMapper financeStateMapper,
                                             FeeConfigurationSnapshotService snapshotService) {
        this.contextMapper = contextMapper;
        this.operationMapper = operationMapper;
        this.financeStateMapper = financeStateMapper;
        this.snapshotService = snapshotService;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public CompletionCommand prepare(PaymentTransactionEventMessage message,
                                     ClearingClaimResult claim,
                                     String processingOwner) {
        ClearingOperationFacts operation = validateRequest(message, claim, processingOwner);
        FeeVersionSnapshot currentSnapshot = snapshotService.load(
                operation.merchantId(), operation.operationId(), operation.transactionId(),
                operation.transactionDateTime());
        return prepareWithSnapshot(message, claim, processingOwner, currentSnapshot);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public CompletionCommand prepareForRecalculation(PaymentTransactionEventMessage message,
                                                     ClearingClaimResult claim,
                                                     String processingOwner,
                                                     FeeVersionSnapshot targetSnapshot) {
        ClearingOperationFacts operation = validateRequest(message, claim, processingOwner);
        if (targetSnapshot == null || !Objects.equals(operation.merchantId(), targetSnapshot.merchantId())) {
            throw new IllegalArgumentException("target recalculation fee snapshot is missing or mismatched");
        }
        return prepareWithSnapshot(message, claim, processingOwner, targetSnapshot);
    }

    /**
     * 使用已确定的费用版本快照组装清分完成命令，并补齐支付维度、风控服务和源交易事实。
     * <p>
     * 重算路径传入指定历史版本，普通路径传入当前冻结版本；后续计算只能消费该快照，禁止重新读取最新费率配置。
     */
    private CompletionCommand prepareWithSnapshot(PaymentTransactionEventMessage message,
                                                  ClearingClaimResult claim,
                                                  String processingOwner,
                                                  FeeVersionSnapshot currentSnapshot) {
        ClearingOperationFacts operation = validateRequest(message, claim, processingOwner);
        ClearingTransactionLocatorDO locatorRow = contextMapper.selectLocator(
                operation.merchantId(), operation.transactionId());
        LocatorFacts currentLocator = validateLocator(locatorRow, operation);
        ClearingPaymentMethodInfoDO paymentMethod = contextMapper.selectPaymentMethod(
                currentLocator.rootTransactionId(), currentLocator.rootTransactionDateTime());
        validatePaymentMethod(paymentMethod);

        Set<String> riskServices = new LinkedHashSet<>();
        if (contextMapper.existsInternalRiskCall(operation.transactionId(), operation.transactionDateTime())) {
            riskServices.add("INTERNAL");
        }
        if (hasExecutedThreeDs(paymentMethod.getThreeDsIndicator())) {
            riskServices.add("THREE_DS");
        }

        SourceContext source = loadSource(operation);
        LocalDate settlementEligibleDate = operation.transactionDateTime().toLocalDate();
        LocalDate expectedReserveReleaseDate = reserveReleaseDate(operation, currentSnapshot.reserve());
        return new CompletionCommand(message, claim, processingOwner, currentSnapshot, currentLocator,
                paymentMethod.getPaymentMethod(), paymentMethod.getPaymentBrand(), riskServices, source,
                settlementEligibleDate, expectedReserveReleaseDate);
    }

    /** 事务外准备只接受 Stage A 已领取的动作，并校验消息与领取事实一致。 */
    private ClearingOperationFacts validateRequest(PaymentTransactionEventMessage message,
                                                   ClearingClaimResult claim,
                                                   String processingOwner) {
        if (message == null || claim == null || !claim.acquired() || claim.operation() == null
                || !StringUtils.hasText(processingOwner)) {
            throw new IllegalArgumentException("acquired clearing claim and processing owner are required");
        }
        ClearingOperationFacts operation = claim.operation();
        if (!Objects.equals(message.getTransactionId(), operation.transactionId())
                || !Objects.equals(message.getOperationId(), operation.operationId())
                || !Objects.equals(message.getMerchantId(), operation.merchantId())
                || !Objects.equals(message.getTransactionDateTime(), operation.transactionDateTime())) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "clearing event identity no longer matches claimed operation");
        }
        return operation;
    }

    /** Locator 必须匹配当前动作、根交易、商户和真实分片时间。 */
    private LocatorFacts validateLocator(ClearingTransactionLocatorDO row,
                                         ClearingOperationFacts operation) {
        if (row == null
                || !Objects.equals(row.getTransactionId(), operation.transactionId())
                || !Objects.equals(row.getOperationId(), operation.operationId())
                || !Objects.equals(row.getMerchantId(), operation.merchantId())
                || !Objects.equals(row.getTransactionDateTime(), operation.transactionDateTime())
                || !StringUtils.hasText(row.getRootTransactionId())
                || row.getRootTransactionDateTime() == null) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "current transaction locator is missing or inconsistent");
        }
        return toLocatorFacts(row);
    }

    /** 费用维度只读取非敏感支付方式和品牌，缺失时不猜测默认值。 */
    private void validatePaymentMethod(ClearingPaymentMethodInfoDO paymentMethod) {
        if (paymentMethod == null || !StringUtils.hasText(paymentMethod.getPaymentMethod())
                || !StringUtils.hasText(paymentMethod.getPaymentBrand())) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_NOT_FOUND,
                    "root payment method dimensions are unavailable");
        }
    }

    /**
     * 按 locator 的真实分片时间读取退款等后续动作的原交易清分事实和费用快照。
     * <p>
     * 原交易尚未清分完成时返回可重试的等待来源失败，禁止使用当前交易配置替代原交易冻结配置计算退款。
     */
    private SourceContext loadSource(ClearingOperationFacts operation) {
        if (!StringUtils.hasText(operation.sourceTransactionId())) {
            return null;
        }
        ClearingTransactionLocatorDO locatorRow = contextMapper.selectLocator(
                operation.merchantId(), operation.sourceTransactionId());
        if (locatorRow == null) {
            throw failure(ClearingFailureCodeEnum.SOURCE_CLEARING_NOT_FOUND,
                    "source transaction locator is unavailable");
        }
        if (!Objects.equals(locatorRow.getTransactionId(), operation.sourceTransactionId())
                || !Objects.equals(locatorRow.getOperationId(), operation.operationId())
                || !Objects.equals(locatorRow.getMerchantId(), operation.merchantId())
                || locatorRow.getTransactionDateTime() == null) {
            throw failure(ClearingFailureCodeEnum.SOURCE_CLEARING_NOT_FOUND,
                    "source transaction locator is inconsistent");
        }
        ClearingTransactionOperationDO sourceOperationRow = operationMapper.selectByTransaction(
                locatorRow.getTransactionId(), locatorRow.getTransactionDateTime());
        ClearingOperationFacts sourceOperation = validateSourceOperation(sourceOperationRow, locatorRow, operation);
        ClearingTransactionFinanceStateDO sourceState = financeStateMapper.selectByTransaction(
                locatorRow.getTransactionId(), locatorRow.getTransactionDateTime());
        if (sourceState == null || !isCompleted(sourceState.getClearingStatus())) {
            throw failure(ClearingFailureCodeEnum.SOURCE_CLEARING_PENDING,
                    "source transaction clearing is not completed");
        }
        FeeVersionSnapshot sourceSnapshot = snapshotService.load(
                sourceOperation.merchantId(), sourceOperation.operationId(), sourceOperation.transactionId(),
                sourceOperation.transactionDateTime());
        return new SourceContext(sourceOperation, toLocatorFacts(locatorRow), sourceSnapshot);
    }

    /** 退款等源动作必须按 locator 精确读取并校验生命周期归属。 */
    private ClearingOperationFacts validateSourceOperation(ClearingTransactionOperationDO row,
                                                           ClearingTransactionLocatorDO locator,
                                                           ClearingOperationFacts current) {
        if (row == null
                || !Objects.equals(row.getTransactionId(), locator.getTransactionId())
                || !Objects.equals(row.getTransactionDateTime(), locator.getTransactionDateTime())
                || !Objects.equals(row.getOperationId(), current.operationId())
                || !Objects.equals(row.getMerchantId(), current.merchantId())) {
            throw failure(ClearingFailureCodeEnum.SOURCE_CLEARING_NOT_FOUND,
                    "source transaction operation is unavailable or inconsistent");
        }
        return new ClearingOperationFacts(
                row.getTransactionId(), row.getOperationId(), row.getSourceTransactionId(), row.getMerchantId(),
                row.getMerchantOrderNo(), row.getTransactionType(), row.getTransactionStatus(),
                row.getLabelCurrency(), row.getLabelAmount(), row.getApprovedCurrency(), row.getApprovedAmount(),
                row.getTransactionCurrency(), row.getTransactionAmount(), row.getCurrencyExponent(),
                row.getTransactionDateTime(), row.getTransactionUtcTime(), row.getTransactionTimeZone(), row.getVersion());
    }

    /** 只有清分完成或无需清分状态可作为退款来源。 */
    private boolean isCompleted(String clearingStatus) {
        if (!StringUtils.hasText(clearingStatus)) {
            return false;
        }
        try {
            return ClearingStateEnum.valueOf(clearingStatus).isCompletedTerminal();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** 根据支付工具冻结的 3DS 指示判断服务是否实际执行；空值和显式未执行值不得计费。 */
    private boolean hasExecutedThreeDs(String indicator) {
        return StringUtils.hasText(indicator)
                && !NON_EXECUTED_THREE_DS_INDICATORS.contains(indicator.trim().toUpperCase(Locale.ROOT));
    }

    /** 保证金释放日按动作业务日和冻结留存规则计算，不使用服务器默认时区。 */
    private LocalDate reserveReleaseDate(ClearingOperationFacts operation,
                                         ReservePolicySnapshot reserve) {
        if (reserve == null || !RESERVE_HOLD_TRANSACTION_TYPES.contains(operation.transactionType())
                || reserve.reserveRate().signum() == 0) {
            return null;
        }
        LocalDate businessDate = operation.transactionDateTime().toLocalDate();
        if ("D".equals(reserve.delayUnit())) {
            return businessDate.plusDays(reserve.delayDays());
        }
        return plusWeekdays(businessDate, reserve.delayDays());
    }

    /**
     * 按自然工作日推算保证金释放日，仅跳过周六和周日。
     * <p>
     * 该规则不隐式读取法定节假日表；配置语义变更时必须通过显式日历策略升级，不能改变历史释放日期。
     */
    private LocalDate plusWeekdays(LocalDate date, int days) {
        LocalDate result = date;
        int remaining = days;
        while (remaining > 0) {
            result = result.plusDays(1);
            DayOfWeek day = result.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }
        return result;
    }

    private LocatorFacts toLocatorFacts(ClearingTransactionLocatorDO row) {
        return new LocatorFacts(row.getTransactionId(), row.getOperationId(), row.getRootTransactionId(),
                row.getMerchantId(), row.getMerchantOrderNo(), row.getTransactionType(),
                row.getTransactionDateTime(), row.getRootTransactionDateTime());
    }

    private ClearingProcessingException failure(ClearingFailureCodeEnum code, String message) {
        return new ClearingProcessingException(code, message);
    }
}
