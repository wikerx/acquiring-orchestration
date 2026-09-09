package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.model.ClearingCalculationModels.ClearingCalculationCommand;
import com.scott.payment.clearing.domain.model.ClearingCalculationModels.ClearingCalculationResult;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.finance.fee.core.FeeComponentCalculator;
import com.scott.payment.finance.fee.core.FeeRefundCalculator;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeComponentType;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeEvaluationStatus;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierContext;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierMetric;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeTierSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeCurrencyPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.PercentageBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.RefundFeeReturnPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReservePolicySnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveRefundPolicy;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundCommand;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundPolicy;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.RefundableFeeComponent;
import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveReturnCommand;
import com.scott.payment.finance.reserve.core.ReserveCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingCalculationServiceTest
 * @date : 2026-08-26 09:25
 * @email : scott_x@163.com
 * @description : 验证清分编排保持标签币种百分比、USD固定费和上下限、独立标签币种保证金的业务边界。
 * @status : create
 */
class DefaultClearingCalculationServiceTest {

    private final DefaultClearingCalculationService service = new DefaultClearingCalculationService();

    @Test
    void calculateShouldKeepPercentageInLabelCurrencyAndFixedFeeInUsd() {
        ClearingCalculationResult result = service.calculate(command("SUCCESS"));

        assertThat(result.required()).isTrue();
        assertThat(result.principal().amount()).isEqualByComparingTo("100.00");
        assertThat(result.principal().currency()).isEqualTo("EUR");
        assertThat(result.principalDirection().name()).isEqualTo("CREDIT");
        assertThat(result.fees()).hasSize(1);
        assertThat(result.fees().get(0).result().feeEvaluationStatus())
                .isEqualTo(FeeEvaluationStatus.PENDING_SETTLEMENT_RATE);
        assertThat(result.fees().get(0).result().components())
                .extracting(component -> component.componentType())
                .containsExactly(FeeComponentType.PERCENTAGE, FeeComponentType.FIXED);
        assertThat(result.fees().get(0).result().components())
                .extracting(component -> component.amount().currency())
                .containsExactly("EUR", "USD");
        assertThat(result.fees().get(0).result().components())
                .extracting(component -> component.amount().amount())
                .containsExactly(new BigDecimal("2.30"), new BigDecimal("0.30"));
        assertThat(result.reserve()).isNotNull();
        assertThat(result.reserve().amount().currency()).isEqualTo("EUR");
        assertThat(result.reserve().amount().amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void calculateShouldNotChargeSuccessOnlyRuleForFailedPayment() {
        ClearingCalculationResult result = service.calculate(command("FAILED"));

        assertThat(result.required()).isFalse();
        assertThat(result.principal()).isNull();
        assertThat(result.fees()).isEmpty();
        assertThat(result.reserve()).isNull();
    }

    @Test
    void calculateShouldSkipSettlementFxFeeForSameLabelAndSettlementCurrency() {
        ClearingCalculationCommand base = command("SUCCESS");
        FeeRuleSnapshot fxRule = new FeeRuleSnapshot(
                202L, FeeMode.STANDARD, new BigDecimal("1.5"),
                new Money(new BigDecimal("0.20"), "USD", 2), null, null, null);
        FeeRuleConfigurationSnapshot configuredFxRule = new FeeRuleConfigurationSnapshot(
                202L, "SETTLEMENT_FX_FEE", "ALL", "ALL", "ALL", "NONE",
                "NOT_APPLICABLE", fxRule, List.of());
        FeeVersionSnapshot snapshot = new FeeVersionSnapshot(
                base.feeSnapshot().schemaVersion(), base.feeSnapshot().merchantId(),
                base.feeSnapshot().feePlanId(), base.feeSnapshot().feePlanVersionId(),
                base.feeSnapshot().feePlanVersionNo(), base.feeSnapshot().pricingLockTime(), "EUR",
                base.feeSnapshot().percentageBasis(), base.feeSnapshot().feeCurrencyPolicy(),
                base.feeSnapshot().roundingMode(), base.feeSnapshot().reserve(),
                base.feeSnapshot().refundFeeReturnPolicy(),
                List.of(base.feeSnapshot().rules().get(0), configuredFxRule),
                base.feeSnapshot().snapshotHash());

        ClearingCalculationResult result = service.calculate(new ClearingCalculationCommand(
                base.operation(), snapshot, base.paymentType(), base.paymentMethod(),
                base.occurredRiskServices(), base.tierContexts(), null, null));

        assertThat(result.fees()).extracting(fee -> fee.rule().feeCategory())
                .containsExactly("TRANSACTION_FEE");
    }

    @Test
    void calculateShouldChargeSettlementFxFeeForDifferentSettlementCurrency() {
        ClearingCalculationCommand base = command("SUCCESS");
        FeeRuleSnapshot fxRule = new FeeRuleSnapshot(
                202L, FeeMode.STANDARD, new BigDecimal("1.5"),
                new Money(new BigDecimal("0.20"), "USD", 2), null, null, null);
        FeeRuleConfigurationSnapshot configuredFxRule = new FeeRuleConfigurationSnapshot(
                202L, "SETTLEMENT_FX_FEE", "ALL", "ALL", "ALL", "NONE",
                "NOT_APPLICABLE", fxRule, List.of());
        FeeVersionSnapshot source = base.feeSnapshot();
        FeeVersionSnapshot snapshot = new FeeVersionSnapshot(
                source.schemaVersion(), source.merchantId(), source.feePlanId(), source.feePlanVersionId(),
                source.feePlanVersionNo(), source.pricingLockTime(), source.settlementCurrency(),
                source.percentageBasis(), source.feeCurrencyPolicy(), source.roundingMode(), source.reserve(),
                source.refundFeeReturnPolicy(), List.of(source.rules().get(0), configuredFxRule),
                source.snapshotHash());

        ClearingCalculationResult result = service.calculate(new ClearingCalculationCommand(
                base.operation(), snapshot, base.paymentType(), base.paymentMethod(),
                base.occurredRiskServices(), base.tierContexts(), null, null));

        assertThat(result.fees()).extracting(fee -> fee.rule().feeCategory())
                .containsExactly("TRANSACTION_FEE", "SETTLEMENT_FX_FEE");
    }

    @Test
    void calculateShouldNotChargeSettlementFxFeeForAuthorizationWithoutSettlementPrincipal() {
        ClearingCalculationCommand base = command("SUCCESS");
        ClearingOperationFacts source = base.operation();
        ClearingOperationFacts authorization = new ClearingOperationFacts(
                source.transactionId(), source.operationId(), source.sourceTransactionId(), source.merchantId(),
                source.merchantOrderNo(), "AUTHORIZATION", source.transactionStatus(), source.labelCurrency(),
                source.labelAmount(), source.approvedCurrency(), source.approvedAmount(),
                source.transactionCurrency(), source.transactionAmount(), source.currencyExponent(),
                source.transactionDateTime(), source.transactionUtcTime(), source.transactionTimeZone(),
                source.operationVersion());
        FeeRuleSnapshot fxRule = new FeeRuleSnapshot(
                202L, FeeMode.STANDARD, new BigDecimal("1.5"),
                new Money(new BigDecimal("0.20"), "USD", 2), null, null, null);
        FeeRuleConfigurationSnapshot configuredFxRule = new FeeRuleConfigurationSnapshot(
                202L, "SETTLEMENT_FX_FEE", "ALL", "ALL", "ALL", "NONE",
                "NOT_APPLICABLE", fxRule, List.of());
        FeeVersionSnapshot sourceSnapshot = base.feeSnapshot();
        FeeVersionSnapshot snapshot = new FeeVersionSnapshot(
                sourceSnapshot.schemaVersion(), sourceSnapshot.merchantId(), sourceSnapshot.feePlanId(),
                sourceSnapshot.feePlanVersionId(), sourceSnapshot.feePlanVersionNo(),
                sourceSnapshot.pricingLockTime(), sourceSnapshot.settlementCurrency(),
                sourceSnapshot.percentageBasis(), sourceSnapshot.feeCurrencyPolicy(),
                sourceSnapshot.roundingMode(), sourceSnapshot.reserve(),
                sourceSnapshot.refundFeeReturnPolicy(), List.of(configuredFxRule),
                sourceSnapshot.snapshotHash());

        ClearingCalculationResult result = service.calculate(new ClearingCalculationCommand(
                authorization, snapshot, base.paymentType(), base.paymentMethod(), Set.of(), Map.of(), null, null));

        assertThat(result.required()).isFalse();
        assertThat(result.principal()).isNull();
        assertThat(result.fees()).isEmpty();
    }

    @Test
    void calculateShouldResolvePrincipalExponentFromApprovedCurrency() {
        ClearingCalculationCommand base = command("SUCCESS");
        ClearingOperationFacts original = base.operation();
        ClearingOperationFacts operation = new ClearingOperationFacts(
                original.transactionId(), original.operationId(), original.sourceTransactionId(),
                original.merchantId(), original.merchantOrderNo(), original.transactionType(),
                original.transactionStatus(), original.labelCurrency(), original.labelAmount(),
                "JPY", new BigDecimal("100"), original.transactionCurrency(),
                original.transactionAmount(), original.currencyExponent(), original.transactionDateTime(),
                original.transactionUtcTime(), original.transactionTimeZone(), original.operationVersion());

        ClearingCalculationResult result = service.calculate(new ClearingCalculationCommand(
                operation, base.feeSnapshot(), base.paymentType(), base.paymentMethod(),
                base.occurredRiskServices(), base.tierContexts(), base.feeRefundCommand(),
                base.reserveReturnCommand()));

        assertThat(result.principal().currency()).isEqualTo("JPY");
        assertThat(result.principal().exponent()).isZero();
    }

    @Test
    void calculateShouldRejectApprovedAmountBeyondCurrencyExponent() {
        ClearingCalculationCommand base = command("SUCCESS");
        ClearingOperationFacts original = base.operation();
        ClearingOperationFacts operation = new ClearingOperationFacts(
                original.transactionId(), original.operationId(), original.sourceTransactionId(),
                original.merchantId(), original.merchantOrderNo(), original.transactionType(),
                original.transactionStatus(), original.labelCurrency(), original.labelAmount(),
                "JPY", new BigDecimal("100.5"), original.transactionCurrency(),
                original.transactionAmount(), original.currencyExponent(), original.transactionDateTime(),
                original.transactionUtcTime(), original.transactionTimeZone(), original.operationVersion());

        assertThatThrownBy(() -> service.calculate(new ClearingCalculationCommand(
                operation, base.feeSnapshot(), base.paymentType(), base.paymentMethod(),
                base.occurredRiskServices(), base.tierContexts(), base.feeRefundCommand(),
                base.reserveReturnCommand())))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode()).isEqualTo(ClearingFailureCodeEnum.AMOUNT_INVALID));
    }

    @Test
    void calculateShouldAcceptThreeDecimalCurrencyFacts() {
        ClearingCalculationCommand base = command("SUCCESS");
        ClearingOperationFacts original = base.operation();
        ClearingOperationFacts operation = new ClearingOperationFacts(
                original.transactionId(), original.operationId(), original.sourceTransactionId(),
                original.merchantId(), original.merchantOrderNo(), original.transactionType(),
                original.transactionStatus(), "KWD", new BigDecimal("100.125"),
                "KWD", new BigDecimal("100.125"), "KWD", new BigDecimal("100.125"), 3,
                original.transactionDateTime(), original.transactionUtcTime(),
                original.transactionTimeZone(), original.operationVersion());

        ClearingCalculationResult result = service.calculate(new ClearingCalculationCommand(
                operation, base.feeSnapshot(), base.paymentType(), base.paymentMethod(),
                base.occurredRiskServices(), base.tierContexts(), base.feeRefundCommand(),
                base.reserveReturnCommand()));

        assertThat(result.principal().currency()).isEqualTo("KWD");
        assertThat(result.principal().amount()).isEqualByComparingTo("100.125");
        assertThat(result.reserve().amount().amount()).isEqualByComparingTo("10.013");
    }

    @Test
    void calculateRefundShouldSeparateRefundFeeOriginalFeeReturnAndReserveReturn() {
        ClearingCalculationCommand payment = command("SUCCESS");
        ClearingOperationFacts refund = new ClearingOperationFacts(
                "TX-R1", "OP-1", "TX-1", "M-1", "ORDER-1", "REFUND", "SUCCESS",
                "EUR", new BigDecimal("20.00"), "EUR", new BigDecimal("20.00"),
                "EUR", new BigDecimal("20.00"), 2,
                LocalDateTime.of(2026, 8, 27, 8, 30),
                LocalDateTime.of(2026, 8, 27, 0, 30), "Asia/Shanghai", 1);
        FeeRuleSnapshot refundRule = new FeeRuleSnapshot(
                201L, FeeMode.STANDARD, new BigDecimal("1"), null, null, null, null);
        FeeRuleConfigurationSnapshot configuredRefundRule = new FeeRuleConfigurationSnapshot(
                201L, "REFUND_FEE", "REFUND", "BANK_CARD", "VISA", "NONE", "SUCCESS",
                refundRule, List.of());
        FeeVersionSnapshot refundSnapshot = new FeeVersionSnapshot(
                3, "M-1", 20L, 21L, 1,
                LocalDateTime.of(2026, 8, 27, 8, 29), "USD",
                PercentageBasis.LABEL_AMOUNT,
                FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP,
                payment.feeSnapshot().reserve(), RefundFeeReturnPolicy.NONE,
                List.of(configuredRefundRule), "b".repeat(64));
        FeeRefundCommand feeRefund = new FeeRefundCommand(
                FeeRefundPolicy.PROPORTIONAL, new Money(new BigDecimal("20.00"), "EUR", 2),
                new Money(new BigDecimal("100.00"), "EUR", 2),
                new Money(BigDecimal.ZERO, "EUR", 2),
                List.of(new RefundableFeeComponent("CD-SOURCE-FEE",
                        new Money(new BigDecimal("2.30"), "EUR", 2),
                        new Money(BigDecimal.ZERO, "EUR", 2))), RoundingMode.HALF_UP);
        ReserveReturnCommand reserveReturn = new ReserveReturnCommand(
                new Money(new BigDecimal("20.00"), "EUR", 2),
                new Money(new BigDecimal("100.00"), "EUR", 2),
                new Money(BigDecimal.ZERO, "EUR", 2), new BigDecimal("10"),
                new Money(new BigDecimal("10.00"), "EUR", 2),
                new Money(BigDecimal.ZERO, "EUR", 2), RoundingMode.HALF_UP);

        ClearingCalculationResult result = service.calculate(new ClearingCalculationCommand(
                refund, refundSnapshot, "BANK_CARD", "VISA", Set.of(), Map.of(),
                feeRefund, reserveReturn));

        assertThat(result.principalDirection()).isEqualTo(
                com.scott.payment.finance.fee.model.FeeCalculationModels.EntryDirection.DEBIT);
        assertThat(result.fees()).singleElement().satisfies(fee ->
                assertThat(fee.result().components()).singleElement().satisfies(component ->
                        assertThat(component.amount().amount()).isEqualByComparingTo("0.20")));
        assertThat(result.feeRefund().components()).singleElement().satisfies(component ->
                assertThat(component.amount().amount()).isEqualByComparingTo("0.46"));
        assertThat(result.reserve().amount().amount()).isEqualByComparingTo("2.00");
    }

    @Test
    void calculateShouldClassifyInvalidLabelAmountFactsAsControlledAmountFailure() {
        ClearingCalculationCommand base = command("SUCCESS");
        ClearingOperationFacts original = base.operation();
        ClearingOperationFacts invalidOperation = new ClearingOperationFacts(
                original.transactionId(), original.operationId(), original.sourceTransactionId(),
                original.merchantId(), original.merchantOrderNo(), original.transactionType(),
                original.transactionStatus(), original.labelCurrency(), original.labelAmount(),
                original.approvedCurrency(), original.approvedAmount(), original.transactionCurrency(),
                original.transactionAmount(), 3, original.transactionDateTime(),
                original.transactionUtcTime(), original.transactionTimeZone(), original.operationVersion());

        assertThatThrownBy(() -> service.calculate(new ClearingCalculationCommand(
                invalidOperation, base.feeSnapshot(), base.paymentType(), base.paymentMethod(),
                base.occurredRiskServices(), base.tierContexts(), base.feeRefundCommand(),
                base.reserveReturnCommand())))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode()).isEqualTo(ClearingFailureCodeEnum.AMOUNT_INVALID));
    }

    @Test
    void calculateShouldClassifyMerchantSnapshotMismatchAsHashMismatch() {
        ClearingCalculationCommand base = command("SUCCESS");
        FeeVersionSnapshot original = base.feeSnapshot();
        FeeVersionSnapshot mismatchedSnapshot = new FeeVersionSnapshot(
                original.schemaVersion(), "M-2", original.feePlanId(), original.feePlanVersionId(),
                original.feePlanVersionNo(), original.pricingLockTime(), original.settlementCurrency(),
                original.percentageBasis(), original.feeCurrencyPolicy(), original.roundingMode(),
                original.reserve(), original.refundFeeReturnPolicy(), original.rules(), original.snapshotHash());

        assertThatThrownBy(() -> service.calculate(new ClearingCalculationCommand(
                base.operation(), mismatchedSnapshot, base.paymentType(), base.paymentMethod(),
                base.occurredRiskServices(), base.tierContexts(), base.feeRefundCommand(),
                base.reserveReturnCommand())))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.FEE_SNAPSHOT_HASH_MISMATCH));
    }

    @Test
    void calculateShouldClassifyNonTerminalTransactionSeparately() {
        ClearingCalculationCommand base = command("PROCESSING");

        assertThatThrownBy(() -> service.calculate(base))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.TRANSACTION_NOT_TERMINAL));
    }

    @Test
    void calculateShouldClassifyInvalidFrozenFeeRuleAsControlledConfigurationFailure() {
        ClearingCalculationCommand base = command("SUCCESS");
        FeeRuleConfigurationSnapshot originalRule = base.feeSnapshot().rules().get(0);
        FeeRuleConfigurationSnapshot invalidRule = new FeeRuleConfigurationSnapshot(
                originalRule.ruleId(), originalRule.feeCategory(), originalRule.transactionType(),
                originalRule.paymentType(), originalRule.paymentMethod(), originalRule.riskServiceType(),
                "UNSUPPORTED_TRIGGER", originalRule.calculationRule(), originalRule.tiers());
        FeeVersionSnapshot invalidSnapshot = new FeeVersionSnapshot(
                base.feeSnapshot().schemaVersion(), base.feeSnapshot().merchantId(),
                base.feeSnapshot().feePlanId(), base.feeSnapshot().feePlanVersionId(),
                base.feeSnapshot().feePlanVersionNo(), base.feeSnapshot().pricingLockTime(),
                base.feeSnapshot().settlementCurrency(), base.feeSnapshot().percentageBasis(),
                base.feeSnapshot().feeCurrencyPolicy(), base.feeSnapshot().roundingMode(),
                base.feeSnapshot().reserve(), base.feeSnapshot().refundFeeReturnPolicy(),
                List.of(invalidRule), base.feeSnapshot().snapshotHash());

        assertThatThrownBy(() -> service.calculate(new ClearingCalculationCommand(
                base.operation(), invalidSnapshot, base.paymentType(), base.paymentMethod(),
                base.occurredRiskServices(), base.tierContexts(), base.feeRefundCommand(),
                base.reserveReturnCommand())))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.FEE_RULE_NOT_CONFIGURED));
    }

    @Test
    void calculateShouldClassifyFeeRefundValidationAsControlledCurrencyFailure() {
        FeeRefundCalculator refundCalculator = mock(FeeRefundCalculator.class);
        DefaultClearingCalculationService calculationService = new DefaultClearingCalculationService(
                new FeeComponentCalculator(), refundCalculator, new ReserveCalculator());
        ClearingCalculationCommand payment = command("SUCCESS");
        ClearingOperationFacts refund = new ClearingOperationFacts(
                "TX-R2", "OP-1", "TX-1", "M-1", "ORDER-1", "REFUND", "SUCCESS",
                "EUR", new BigDecimal("20.00"), "EUR", new BigDecimal("20.00"),
                "EUR", new BigDecimal("20.00"), 2,
                LocalDateTime.of(2026, 8, 27, 8, 30),
                LocalDateTime.of(2026, 8, 27, 0, 30), "Asia/Shanghai", 1);
        FeeRefundCommand feeRefund = new FeeRefundCommand(
                FeeRefundPolicy.PROPORTIONAL, new Money(new BigDecimal("20.00"), "EUR", 2),
                new Money(new BigDecimal("100.00"), "EUR", 2),
                new Money(BigDecimal.ZERO, "EUR", 2), List.of(), RoundingMode.HALF_UP);
        when(refundCalculator.calculate(any())).thenThrow(new IllegalArgumentException(
                "source fee component currency is inconsistent"));

        assertThatThrownBy(() -> calculationService.calculate(new ClearingCalculationCommand(
                refund, payment.feeSnapshot(), payment.paymentType(), payment.paymentMethod(),
                payment.occurredRiskServices(), payment.tierContexts(), feeRefund, null)))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.FEE_COMPONENT_CURRENCY_INVALID));
    }

    @Test
    void calculateShouldClassifyReserveValidationAsControlledStateFailure() {
        ReserveCalculator reserveCalculator = mock(ReserveCalculator.class);
        DefaultClearingCalculationService calculationService = new DefaultClearingCalculationService(
                new FeeComponentCalculator(), new FeeRefundCalculator(), reserveCalculator);
        ClearingCalculationCommand command = command("SUCCESS");
        when(reserveCalculator.hold(any())).thenThrow(
                new IllegalArgumentException("reserve state facts are inconsistent"));

        assertThatThrownBy(() -> calculationService.calculate(command))
                .isInstanceOfSatisfying(ClearingProcessingException.class, failure ->
                        assertThat(failure.getFailureCode())
                                .isEqualTo(ClearingFailureCodeEnum.RESERVE_STATE_CONFLICT));
    }

    @Test
    void calculateShouldRetainMatchedZeroFeeTierForAccumulator() {
        ClearingCalculationCommand base = command("SUCCESS");
        FeeRuleSnapshot tierRule = new FeeRuleSnapshot(
                301L, FeeMode.TIER, BigDecimal.ZERO, null, null, null, TierMetric.COUNT);
        FeeRuleConfigurationSnapshot configuredRule = new FeeRuleConfigurationSnapshot(
                301L, "TRANSACTION_FEE", "PAYMENT", "BANK_CARD", "VISA", "NONE", "SUCCESS",
                tierRule, List.of(new FeeTierSnapshot(
                        302L, BigDecimal.ZERO, null, BigDecimal.ZERO, null, null, null)));
        FeeVersionSnapshot snapshot = new FeeVersionSnapshot(
                base.feeSnapshot().schemaVersion(), base.feeSnapshot().merchantId(),
                base.feeSnapshot().feePlanId(), base.feeSnapshot().feePlanVersionId(),
                base.feeSnapshot().feePlanVersionNo(), base.feeSnapshot().pricingLockTime(),
                base.feeSnapshot().settlementCurrency(), base.feeSnapshot().percentageBasis(),
                base.feeSnapshot().feeCurrencyPolicy(), base.feeSnapshot().roundingMode(),
                base.feeSnapshot().reserve(), base.feeSnapshot().refundFeeReturnPolicy(),
                List.of(configuredRule), base.feeSnapshot().snapshotHash());
        TierContext tierContext = new TierContext(4L, BigDecimal.ZERO, BigDecimal.ZERO);

        ClearingCalculationResult result = service.calculate(new ClearingCalculationCommand(
                base.operation(), snapshot, base.paymentType(), base.paymentMethod(),
                base.occurredRiskServices(), Map.of(301L, tierContext), null, null));

        assertThat(result.fees()).hasSize(1);
        assertThat(result.fees().get(0).tierContext()).isEqualTo(tierContext);
        assertThat(result.fees().get(0).result().matchedTierId()).isEqualTo(302L);
        assertThat(result.fees().get(0).result().components()).isEmpty();
    }

    @Test
    void calculateShouldNotTreatMatchedZeroFeeTierAsFinancialDetail() {
        ClearingCalculationCommand base = command("FAILED");
        FeeRuleSnapshot tierRule = new FeeRuleSnapshot(
                401L, FeeMode.TIER, BigDecimal.ZERO, null, null, null, TierMetric.COUNT);
        FeeRuleConfigurationSnapshot configuredRule = new FeeRuleConfigurationSnapshot(
                401L, "TRANSACTION_FEE", "PAYMENT", "BANK_CARD", "VISA", "NONE",
                "SUCCESS_OR_FAILURE", tierRule, List.of(new FeeTierSnapshot(
                        402L, BigDecimal.ZERO, null, BigDecimal.ZERO, null, null, null)));
        FeeVersionSnapshot snapshot = new FeeVersionSnapshot(
                base.feeSnapshot().schemaVersion(), base.feeSnapshot().merchantId(),
                base.feeSnapshot().feePlanId(), base.feeSnapshot().feePlanVersionId(),
                base.feeSnapshot().feePlanVersionNo(), base.feeSnapshot().pricingLockTime(),
                base.feeSnapshot().settlementCurrency(), base.feeSnapshot().percentageBasis(),
                base.feeSnapshot().feeCurrencyPolicy(), base.feeSnapshot().roundingMode(),
                base.feeSnapshot().reserve(), base.feeSnapshot().refundFeeReturnPolicy(),
                List.of(configuredRule), base.feeSnapshot().snapshotHash());

        ClearingCalculationResult result = service.calculate(new ClearingCalculationCommand(
                base.operation(), snapshot, base.paymentType(), base.paymentMethod(),
                base.occurredRiskServices(), Map.of(401L, TierContext.empty()), null, null));

        assertThat(result.fees()).hasSize(1);
        assertThat(result.required()).isFalse();
    }

    /** 授权类动作不产生应结本金或保证金，但仍可按明确配置收取动作手续费。 */
    @Test
    void calculateShouldChargeConfiguredAuthorizationFeesWithoutPrincipal() {
        for (String transactionType : List.of(
                "AUTHORIZATION", "PRE_AUTHORIZATION", "INCREMENTAL_AUTHORIZATION")) {
            ClearingCalculationCommand command = commandForTransactionType(transactionType, transactionType);

            ClearingCalculationResult result = service.calculate(command);

            assertThat(result.principal()).as(transactionType).isNull();
            assertThat(result.reserve()).as(transactionType).isNull();
            assertThat(result.fees()).as(transactionType).hasSize(1);
            assertThat(result.required()).as(transactionType).isTrue();
        }
    }

    /** 请款和拒付申诉形成贷记本金，退款和拒付形成借记本金，均使用动作自身批准金额。 */
    @Test
    void calculateShouldClassifySettlementBearingActionPrincipalDirection() {
        for (String transactionType : List.of("CAPTURE", "PRE_AUTH_COMPLETION", "REPRESENTMENT")) {
            ClearingCalculationResult result = service.calculate(
                    commandForTransactionType(transactionType, "UNMATCHED"));
            assertThat(result.principalDirection()).as(transactionType)
                    .isEqualTo(com.scott.payment.finance.fee.model.FeeCalculationModels.EntryDirection.CREDIT);
        }
        for (String transactionType : List.of("REFUND", "CHARGEBACK")) {
            ClearingCalculationResult result = service.calculate(
                    commandForTransactionType(transactionType, "UNMATCHED"));
            assertThat(result.principalDirection()).as(transactionType)
                    .isEqualTo(com.scott.payment.finance.fee.model.FeeCalculationModels.EntryDirection.DEBIT);
        }
    }

    /** 撤销成功不形成应结本金；没有明确撤销收费规则时清分结果为不需要清分。 */
    @Test
    void calculateShouldLeaveVoidWithoutFinancialFactsWhenNoRuleMatches() {
        ClearingCalculationResult result = service.calculate(commandForTransactionType("VOID", "UNMATCHED"));

        assertThat(result.principal()).isNull();
        assertThat(result.fees()).isEmpty();
        assertThat(result.reserve()).isNull();
        assertThat(result.required()).isFalse();
    }

    private ClearingCalculationCommand commandForTransactionType(String transactionType,
                                                                  String configuredTransactionType) {
        ClearingCalculationCommand base = command("SUCCESS");
        ClearingOperationFacts source = base.operation();
        ClearingOperationFacts operation = new ClearingOperationFacts(
                source.transactionId() + "-" + transactionType, source.operationId(), source.sourceTransactionId(),
                source.merchantId(), source.merchantOrderNo(), transactionType, source.transactionStatus(),
                source.labelCurrency(), source.labelAmount(), source.approvedCurrency(), source.approvedAmount(),
                source.transactionCurrency(), source.transactionAmount(), source.currencyExponent(),
                source.transactionDateTime(), source.transactionUtcTime(), source.transactionTimeZone(),
                source.operationVersion());
        FeeRuleConfigurationSnapshot originalRule = base.feeSnapshot().rules().get(0);
        FeeRuleConfigurationSnapshot configuredRule = new FeeRuleConfigurationSnapshot(
                originalRule.ruleId(), originalRule.feeCategory(), configuredTransactionType,
                originalRule.paymentType(), originalRule.paymentMethod(), originalRule.riskServiceType(),
                originalRule.chargeTrigger(), originalRule.calculationRule(), originalRule.tiers());
        FeeVersionSnapshot sourceSnapshot = base.feeSnapshot();
        FeeVersionSnapshot snapshot = new FeeVersionSnapshot(
                sourceSnapshot.schemaVersion(), sourceSnapshot.merchantId(), sourceSnapshot.feePlanId(),
                sourceSnapshot.feePlanVersionId(), sourceSnapshot.feePlanVersionNo(),
                sourceSnapshot.pricingLockTime(), sourceSnapshot.settlementCurrency(),
                sourceSnapshot.percentageBasis(), sourceSnapshot.feeCurrencyPolicy(),
                sourceSnapshot.roundingMode(), sourceSnapshot.reserve(), sourceSnapshot.refundFeeReturnPolicy(),
                List.of(configuredRule), sourceSnapshot.snapshotHash());
        return new ClearingCalculationCommand(operation, snapshot, base.paymentType(), base.paymentMethod(),
                Set.of(), Map.of(), null, null);
    }

    private ClearingCalculationCommand command(String transactionStatus) {
        ClearingOperationFacts operation = new ClearingOperationFacts(
                "TX-1", "OP-1", null, "M-1", "ORDER-1", "PAYMENT", transactionStatus,
                "EUR", new BigDecimal("100.00"), "EUR", new BigDecimal("100.00"),
                "EUR", new BigDecimal("100.00"), 2,
                LocalDateTime.of(2026, 8, 26, 8, 30),
                LocalDateTime.of(2026, 8, 26, 0, 30), "Asia/Shanghai", 3);
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                101L, FeeMode.STANDARD, new BigDecimal("2.3"),
                new Money(new BigDecimal("0.30"), "USD", 2),
                new Money(new BigDecimal("0.50"), "USD", 2),
                new Money(new BigDecimal("5.00"), "USD", 2), null);
        FeeRuleConfigurationSnapshot configuredRule = new FeeRuleConfigurationSnapshot(
                101L, "TRANSACTION_FEE", "PAYMENT", "BANK_CARD", "VISA", "NONE", "NOT_APPLICABLE",
                rule, List.of());
        FeeVersionSnapshot snapshot = new FeeVersionSnapshot(
                3, "M-1", 10L, 11L, 2,
                LocalDateTime.of(2026, 8, 26, 8, 29), "USD",
                PercentageBasis.LABEL_AMOUNT,
                FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP,
                new ReservePolicySnapshot(new BigDecimal("10"), ReserveBasis.LABEL_AMOUNT,
                        "D", 180, ReserveRefundPolicy.PROPORTIONAL_RETURN),
                RefundFeeReturnPolicy.NONE, List.of(configuredRule), "a".repeat(64));
        return new ClearingCalculationCommand(operation, snapshot, "BANK_CARD", "VISA",
                Set.of(), Map.of(101L, TierContext.empty()), null, null);
    }
}
