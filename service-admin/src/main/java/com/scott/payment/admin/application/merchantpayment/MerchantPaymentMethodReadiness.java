package com.scott.payment.admin.application.merchantpayment;

import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.AccessSummary;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.AuthorizationCoverage;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.FeeReference;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.KnownCapability;
import com.scott.payment.admin.service.impl.MerchantPaymentCapabilityReadinessService;
import com.scott.payment.admin.service.impl.MerchantPaymentCapabilityReadinessService.CapabilityKey;
import com.scott.payment.admin.service.impl.MerchantPaymentCapabilityReadinessService.ReadinessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** 商户方式页面的配置摘要，无具体请求时不承诺任意币种、地区或入口可付款。 */
@Service
class MerchantPaymentMethodReadiness {
    private static final Logger LOG = LoggerFactory.getLogger(MerchantPaymentMethodReadiness.class);
    private static final Set<String> INITIAL = Set.of("PAYMENT", "AUTHORIZATION", "PRE_AUTHORIZATION");
    private static final BiPredicate<String, String> NO_FEE_COVERAGE = (type, method) -> false;
    private final MerchantPaymentRepository repository;
    private final MerchantPaymentCapabilityReadinessService current;

    MerchantPaymentMethodReadiness(MerchantPaymentRepository repository,
                                    MerchantPaymentCapabilityReadinessService current) {
        this.repository = repository;
        this.current = current;
    }

    Evaluation evaluate(String merchant, LocalDateTime now) {
        return evaluate(merchant, now, false);
    }

    Evaluation evaluateForMethods(String merchant, LocalDateTime now) {
        return evaluate(merchant, now, true);
    }

    private Evaluation evaluate(String merchant, LocalDateTime now, boolean includeIndependentFeeCoverage) {
        try {
            FeeReference fee = repository.currentFeeReference(merchant, now).orElse(null);
            Long feeVersionId = fee == null ? null : Long.valueOf(fee.versionId());
            ReadinessResult acquiring = current.evaluateCurrent(merchant, now, () -> feeVersionId).acquiring();
            BiPredicate<String, String> feeCoverage = includeIndependentFeeCoverage && feeVersionId != null && acquiring != null
                    ? current.initialFeeCoverage(feeVersionId) : NO_FEE_COVERAGE;
            return new Evaluation(acquiring, fee, feeCoverage);
        } catch (RuntimeException exception) {
            LOG.warn("商户支付方式配置评估不可用, event: MERCHANT_PAYMENT_METHOD_READINESS_UNAVAILABLE, merchantId: {}, exceptionType: {}",
                    merchant, exception.getClass().getSimpleName());
            return new Evaluation(null, null, NO_FEE_COVERAGE);
        }
    }

    record AuthorityFacts(List<AuthorizationCoverage> coverage, Set<String> enabledMethods) {
    }

    record Evaluation(ReadinessResult source, FeeReference fee, BiPredicate<String, String> initialFeeCoverage) {

        AccessSummary accessSummary(AuthorityFacts authority, boolean held, LocalDateTime now) {
            if (source == null) {
                return AccessSummary.unavailable("UNAVAILABLE");
            }
            int authorized = count(authority, "ACTIVE");
            int historical = count(authority, "LEGACY_OBSERVED");
            int restricted = count(authority, "SUSPENDED") + count(authority, "TERMINATED");
            Set<String> availableAuthority = authority.coverage().stream()
                    .filter(method -> Set.of("ACTIVE", "LEGACY_OBSERVED").contains(method.authorizationStatus()))
                    .map(AuthorizationCoverage::itemKey).filter(authority.enabledMethods()::contains)
                    .collect(Collectors.toSet());

            var configured = fee == null ? List.<CapabilityKey>of() : source.capabilities().stream()
                    .filter(key -> "PAYMENT".equals(key.transactionType()))
                    .filter(key -> availableAuthority.contains(itemKey(key)))
                    .filter(key -> !source.routeConflicts().contains(key) && !source.feeGaps().contains(key)
                            && !source.feeAmbiguities().contains(key))
                    .toList();
            int configuredMethods = (int) configured.stream().map(Evaluation::itemKey).distinct().count();

            // 现行 Direct API 的真实收款请求只承接 BANK_CARD。非卡配置仍可就绪，但无法由此推断已接入付款。
            var direct = held ? List.<CapabilityKey>of()
                    : configured.stream().filter(key -> "BANK_CARD".equals(key.paymentType())).toList();
            int executableMethods = (int) direct.stream().map(Evaluation::itemKey).distinct().count();
            var capabilities = direct.stream()
                    .map(key -> new KnownCapability(key.paymentType(), key.paymentMethod(), key.currency()))
                    .distinct().toList();
            return new AccessSummary("EVALUATED", now,
                    (long) Math.max(0, source.activeBindingCount() - source.unavailableBindingCount()),
                    authorized, historical, configuredMethods, authorized + historical - configuredMethods, restricted,
                    executableMethods, capabilities);
        }

        private static int count(AuthorityFacts authority, String status) {
            return (int) authority.coverage().stream().filter(coverage -> status.equals(coverage.authorizationStatus())).count();
        }

        private static String itemKey(CapabilityKey key) {
            return "BANK_CARD".equals(key.paymentType())
                    ? "CARD_BRAND:" + key.paymentMethod()
                    : "METHOD:" + key.paymentType();
        }

        Projection forMethod(String itemKey) {
            if (source == null) {
                return new Projection("UNKNOWN", List.of("PAYMENT_CAPABILITY_READINESS_UNAVAILABLE"), null);
            }
            Predicate<CapabilityKey> applicable = key -> INITIAL.contains(key.transactionType())
                    && itemKey.equals(itemKey(key));
            List<CapabilityKey> candidates = source.capabilities().stream().filter(applicable).toList();
            if (candidates.isEmpty()) {
                boolean cardBrand = itemKey.startsWith("CARD_BRAND:");
                String code = itemKey.substring(itemKey.indexOf(':') + 1);
                boolean feeCovered = fee != null && (cardBrand
                        ? initialFeeCoverage.test("BANK_CARD", code) : initialFeeCoverage.test(code, "ALL"));
                return feeCovered
                        ? new Projection("BLOCKED", List.of("CHANNEL_MID_UNAVAILABLE"), fee)
                        : new Projection("BLOCKED", List.of("CHANNEL_MID_UNAVAILABLE", "FEE_CAPABILITY_NOT_COVERED"), null);
            }

            List<String> reasons = new ArrayList<>();
            if (source.routeConflicts().stream().anyMatch(applicable)) {
                reasons.add("CHANNEL_ROUTE_CONFLICT");
            }
            if (source.feeGaps().stream().anyMatch(applicable) || fee == null) {
                reasons.add("FEE_CAPABILITY_NOT_COVERED");
            }
            if (source.feeAmbiguities().stream().anyMatch(applicable)) {
                reasons.add("FEE_CAPABILITY_AMBIGUOUS");
            }
            boolean covered = fee != null && candidates.stream().anyMatch(
                    key -> !source.feeGaps().contains(key) && !source.feeAmbiguities().contains(key));
            boolean selectable = covered && candidates.stream().anyMatch(key -> !source.routeConflicts().contains(key)
                    && !source.feeGaps().contains(key) && !source.feeAmbiguities().contains(key));
            String status = !selectable ? "BLOCKED" : reasons.isEmpty() ? "READY" : "CONDITIONAL";
            return new Projection(status, List.copyOf(reasons), covered ? fee : null);
        }
    }

    record Projection(String status, List<String> reasons, FeeReference feeReference) {}
}
