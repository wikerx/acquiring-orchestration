package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.model.ClearingCalculationModels.CalculatedFee;
import com.scott.payment.clearing.domain.model.ClearingCalculationModels.ClearingCalculationCommand;
import com.scott.payment.clearing.domain.model.ClearingCalculationModels.ClearingCalculationResult;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.domain.service.ClearingFeeRuleMatcher;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.service.ClearingCalculationService;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import com.scott.payment.finance.fee.core.FeeComponentCalculator;
import com.scott.payment.finance.fee.core.FeeRefundCalculator;
import com.scott.payment.finance.fee.model.FeeCalculationModels.EntryDirection;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeCalculationCommand;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeCalculationResult;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierContext;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundResult;
import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.reserve.core.ReserveCalculator;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveCalculationResult;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveHoldCommand;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingCalculationService
 * @date : 2026-08-26 09:32
 * @email : scott_x@163.com
 * @description : 清分计算默认实现，按终态触发规则调用 finance-library，保留标签币种百分比与USD固定费/上下限且不做换汇。
 * @status : create
 */
@Service
public class DefaultClearingCalculationService implements ClearingCalculationService {

    private static final Set<String> CREDIT_PRINCIPAL_TYPES = Set.of(
            "PAYMENT", "CAPTURE", "PRE_AUTH_COMPLETION", "REPRESENTMENT");
    private static final Set<String> DEBIT_PRINCIPAL_TYPES = Set.of("REFUND", "CHARGEBACK");
    private static final Set<String> RESERVE_HOLD_TYPES = Set.of(
            "PAYMENT", "CAPTURE", "PRE_AUTH_COMPLETION");

    private final FeeComponentCalculator feeCalculator;
    private final FeeRefundCalculator feeRefundCalculator;
    private final ReserveCalculator reserveCalculator;

    /** 创建不依赖 Spring 或外部系统的清分纯计算服务。 */
    public DefaultClearingCalculationService() {
        this(new FeeComponentCalculator(), new FeeRefundCalculator(), new ReserveCalculator());
    }

    /**
     * 创建可替换纯计算内核的清分服务。
     *
     * @param feeCalculator 费用组件计算器
     * @param feeRefundCalculator 原实际收费返还计算器
     * @param reserveCalculator 保证金计算器
     */
    DefaultClearingCalculationService(FeeComponentCalculator feeCalculator,
                                      FeeRefundCalculator feeRefundCalculator,
                                      ReserveCalculator reserveCalculator) {
        this.feeCalculator = feeCalculator;
        this.feeRefundCalculator = feeRefundCalculator;
        this.reserveCalculator = reserveCalculator;
    }

    /** {@inheritDoc} */
    @Override
    public ClearingCalculationResult calculate(ClearingCalculationCommand command) {
        validateCommand(command);
        Principal principal;
        Money labelAmount;
        try {
            principal = principal(command.operation());
            labelAmount = labelAmount(command.operation());
        } catch (IllegalArgumentException exception) {
            throw controlledFailure(ClearingFailureCodeEnum.AMOUNT_INVALID, exception);
        }
        List<CalculatedFee> fees;
        try {
            fees = calculateFees(command, labelAmount);
        } catch (IllegalArgumentException exception) {
            throw controlledFailure(ClearingFailureCodeEnum.FEE_RULE_NOT_CONFIGURED, exception);
        }
        FeeRefundResult feeRefund = calculateFeeRefund(command);
        ReserveCalculationResult reserve;
        try {
            reserve = calculateReserve(command, labelAmount);
        } catch (IllegalArgumentException exception) {
            throw controlledFailure(ClearingFailureCodeEnum.RESERVE_STATE_CONFLICT, exception);
        }
        return new ClearingCalculationResult(
                principal == null ? null : principal.amount(),
                principal == null ? null : principal.direction(), fees, feeRefund, reserve);
    }

    private ClearingProcessingException controlledFailure(ClearingFailureCodeEnum code,
                                                           IllegalArgumentException cause) {
        return new ClearingProcessingException(code, cause.getMessage());
    }

    private FeeRefundResult calculateFeeRefund(ClearingCalculationCommand command) {
        if (command.feeRefundCommand() == null) {
            return null;
        }
        try {
            return feeRefundCalculator.calculate(command.feeRefundCommand());
        } catch (IllegalArgumentException exception) {
            throw controlledFailure(ClearingFailureCodeEnum.FEE_COMPONENT_CURRENCY_INVALID, exception);
        }
    }

    private List<CalculatedFee> calculateFees(ClearingCalculationCommand command, Money labelAmount) {
        List<CalculatedFee> fees = new ArrayList<>();
        for (FeeRuleConfigurationSnapshot rule : command.feeSnapshot().rules()) {
            if (!ClearingFeeRuleMatcher.matches(command.operation(), command.paymentType(),
                    command.paymentMethod(), command.occurredRiskServices(),
                    command.feeSnapshot().settlementCurrency(), rule)) {
                continue;
            }
            TierContext tierContext = command.tierContexts().getOrDefault(rule.ruleId(), TierContext.empty());
            FeeCalculationResult result = feeCalculator.calculate(new FeeCalculationCommand(
                    labelAmount, rule.calculationRule(), rule.tiers(), tierContext,
                    command.feeSnapshot().roundingMode()));
            fees.add(new CalculatedFee(rule, tierContext, result));
        }
        return List.copyOf(fees);
    }

    private ReserveCalculationResult calculateReserve(ClearingCalculationCommand command, Money labelAmount) {
        ClearingOperationFacts operation = command.operation();
        if (!"SUCCESS".equals(operation.transactionStatus())) {
            return null;
        }
        if ("REFUND".equals(operation.transactionType())) {
            return command.reserveReturnCommand() == null
                    ? null : reserveCalculator.returnReserve(command.reserveReturnCommand());
        }
        if (!RESERVE_HOLD_TYPES.contains(operation.transactionType())
                || command.feeSnapshot().reserve().reserveRate().signum() == 0) {
            return null;
        }
        return reserveCalculator.hold(new ReserveHoldCommand(
                labelAmount, command.feeSnapshot().reserve().reserveRate(),
                command.feeSnapshot().roundingMode()));
    }

    private Principal principal(ClearingOperationFacts operation) {
        if (!"SUCCESS".equals(operation.transactionStatus())) {
            return null;
        }
        EntryDirection direction;
        if (CREDIT_PRINCIPAL_TYPES.contains(operation.transactionType())) {
            direction = EntryDirection.CREDIT;
        } else if (DEBIT_PRINCIPAL_TYPES.contains(operation.transactionType())) {
            direction = EntryDirection.DEBIT;
        } else {
            return null;
        }
        Money amount = approvedAmount(operation);
        if (amount == null) {
            amount = transactionAmount(operation);
        }
        if (amount == null || amount.amount().signum() < 0) {
            throw new IllegalArgumentException("successful principal action requires a non-negative approved amount");
        }
        return new Principal(amount, direction);
    }

    private Money approvedAmount(ClearingOperationFacts operation) {
        return money(operation.approvedAmount(), operation.approvedCurrency(), "approved amount");
    }

    private Money transactionAmount(ClearingOperationFacts operation) {
        return money(operation.transactionAmount(), operation.transactionCurrency(), "transaction amount");
    }

    private Money labelAmount(ClearingOperationFacts operation) {
        Money label = money(operation.labelAmount(), operation.labelCurrency(), "label amount");
        if (label == null || label.amount().signum() < 0) {
            throw new IllegalArgumentException("clearing label amount, currency and exponent are required");
        }
        if (!Objects.equals(label.exponent(), operation.currencyExponent())) {
            throw new IllegalArgumentException("stored label currency exponent does not match ISO currency");
        }
        return label;
    }

    private Money money(BigDecimal amount, String currency, String fieldName) {
        if (amount == null || !StringUtils.hasText(currency)) {
            return null;
        }
        String normalizedCurrency = currency.trim().toUpperCase(java.util.Locale.ROOT);
        int exponent = IsoCurrencyResolver.resolve(normalizedCurrency)
                .filter(value -> value.defaultFractionDigits() >= 0)
                .orElseThrow(() -> new IllegalArgumentException("unsupported ISO currency for clearing"))
                .defaultFractionDigits();
        Money result = new Money(amount, normalizedCurrency, exponent);
        if (amount.stripTrailingZeros().scale() > exponent) {
            throw new IllegalArgumentException(fieldName + " does not conform to ISO currency exponent");
        }
        return result;
    }

    private void validateCommand(ClearingCalculationCommand command) {
        Objects.requireNonNull(command, "clearing calculation command is required");
        if (!Objects.equals(command.operation().merchantId(), command.feeSnapshot().merchantId())) {
            throw new ClearingProcessingException(ClearingFailureCodeEnum.FEE_SNAPSHOT_HASH_MISMATCH,
                    "fee snapshot merchant does not match transaction merchant");
        }
        if (!Set.of("SUCCESS", "FAILED").contains(command.operation().transactionStatus())) {
            throw new ClearingProcessingException(ClearingFailureCodeEnum.TRANSACTION_NOT_TERMINAL,
                    "clearing calculation requires an authoritative terminal status");
        }
    }

    private record Principal(Money amount, EntryDirection direction) {
    }
}
