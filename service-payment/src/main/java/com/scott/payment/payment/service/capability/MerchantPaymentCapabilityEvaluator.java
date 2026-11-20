package com.scott.payment.payment.service.capability;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.capability.MerchantAdmissionPolicy;
import com.scott.payment.capability.MerchantAdmissionReader;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.impl.DefaultPaymentChannelRouteService;
import com.scott.payment.payment.service.impl.PaymentCheckoutCardCapabilityService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 批量评估当前授权、费用覆盖和路由候选。评估结果不替代交易的最终受理。 */
@Service
@DS(DataSourceName.MASTER)
public class MerchantPaymentCapabilityEvaluator {

    private static final String BANK_CARD = "BANK_CARD";
    private static final Set<String> INITIAL_ACTIONS = Set.of("PAYMENT", "AUTHORIZATION", "PRE_AUTHORIZATION");

    private final MerchantAdmissionReader admissionReader;
    private final PaymentCheckoutCardCapabilityService cardCapabilities;
    private final DefaultPaymentChannelRouteService routeService;

    public MerchantPaymentCapabilityEvaluator(MerchantAdmissionReader admissionReader,
                                              PaymentCheckoutCardCapabilityService cardCapabilities,
                                              DefaultPaymentChannelRouteService routeService) {
        this.admissionReader = admissionReader;
        this.cardCapabilities = cardCapabilities;
        this.routeService = routeService;
    }

    /** 有界调用一次读取商户和费用版本，持久事实无法评估时保留与零数量的区别。 */
    public Assessment evaluate(Query query) {
        List<String> keys = query.itemKeys().stream().distinct().toList();
        String action = query.transactionType() == null ? "PAYMENT" : normalize(query.transactionType());
        if (!INITIAL_ACTIONS.contains(action) || keys.stream().anyMatch(key -> !MerchantAdmissionPolicy.isItemKey(key))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            MerchantAdmissionReader.MerchantAuthority authority = admissionReader.read(query.merchantId());
            List<MerchantAdmissionReader.FeeCoverage> rules = admissionReader.transactionFeeCoverage(query.merchantId());
            Set<String> directory = admissionReader.enabledDirectory();
            List<AllowedPaymentMethodDTO> broadCandidates = cardCapabilities.resolveAllowedMethods(query.merchantId(), null);
            return new Assessment("EVALUATED", now, keys.stream()
                    .map(key -> assessMethod(query, action, authority, rules, broadCandidates, directory, key, now)).toList());
        } catch (DataAccessException exception) {
            return new Assessment("UNAVAILABLE", now, List.of());
        }
    }

    private MethodAssessment assessMethod(Query query, String action, MerchantAdmissionReader.MerchantAuthority authority,
                                          List<MerchantAdmissionReader.FeeCoverage> rules,
                                          List<AllowedPaymentMethodDTO> broadCandidates, Set<String> directory,
                                          String key, LocalDateTime now) {
        boolean card = key.startsWith("CARD_BRAND:");
        String code = key.substring(key.indexOf(':') + 1);
        String method = card ? BANK_CARD : code;
        MerchantAdmissionReader.ItemAuthority facts = authority.items().get(key);
        MerchantAdmissionPolicy.Grant grant = facts == null ? null : facts.grant();
        var authorization = MerchantAdmissionPolicy.state(grant, now);
        boolean authorized = MerchantAdmissionPolicy.permits(authority.mode(), grant, facts != null && facts.frozenLegacyScope(), now);
        boolean missingFee = rules.stream().noneMatch(rule -> rule.covers(action, method, card ? code : "ALL"));
        boolean broad = broadCandidates.stream().anyMatch(candidate -> method.equals(normalize(candidate.getPaymentMethod()))
                && (!card || candidate.getBrands().contains(code)));
        boolean completeContext = query.currency() != null && query.amount() != null && query.payerCountry() != null;
        var route = completeContext ? routeService.assessCandidates(command(query, action, card, method, code), now) : null;
        boolean missingMid = completeContext
                ? route == DefaultPaymentChannelRouteService.CandidateStatus.NO_TARGET : !broad;
        List<String> reasons = new ArrayList<>();
        if (!directory.contains(key)) {
            reasons.add("METHOD_DISABLED");
        }
        if (!authorized) {
            reasons.add(authority.mode() == null ? "ADMISSION_MODE_NOT_SET" : "AUTHORIZATION_UNAVAILABLE");
        }
        if (missingFee) {
            reasons.add("FEE_MISSING");
        }
        if (missingMid) {
            reasons.add("MID_UNAVAILABLE");
        }
        if (route == DefaultPaymentChannelRouteService.CandidateStatus.AMBIGUOUS) {
            reasons.add("ROUTE_AMBIGUOUS");
        }
        if (!reasons.isEmpty()) {
            return new MethodAssessment(key, authorization.name(), "BLOCKED", missingFee, missingMid, List.copyOf(reasons));
        }
        if (!completeContext || route == DefaultPaymentChannelRouteService.CandidateStatus.UNIQUE_EDC) {
            String reason = completeContext ? "EXCHANGE_RATE_RECHECK" : "PAYMENT_CONTEXT_REQUIRED";
            return new MethodAssessment(key, authorization.name(), "CONDITIONAL", false, false, List.of(reason));
        }
        return new MethodAssessment(key, authorization.name(), "READY", false, false, List.of());
    }

    private static PaymentCreateCommandDTO command(Query query, String action, boolean card, String method, String code) {
        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setMerchantId(query.merchantId());
        command.setTransactionType(action);
        command.setPaymentMethod(method);
        command.setCurrency(normalize(query.currency()));
        command.setAmount(query.amount());
        var billing = new PaymentCreateCommandDTO.BillingCardHolderInfoDTO();
        billing.setCountry(normalize(query.payerCountry()));
        command.setBillingCardHolderInfo(billing);
        if (card) {
            var transaction = new PaymentCreateCommandDTO.TransactionInfoDTO();
            transaction.setCardBrand(code);
            command.setTransactionInfo(transaction);
        }
        return command;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /** 内部受限的批量评估参数。 */
    public record Query(@NotBlank @Size(max = 32) String merchantId,
                        @NotEmpty @Size(max = 64) List<@NotBlank String> itemKeys,
                        String currency, String transactionType, BigDecimal amount, String payerCountry) {
    }

    /** 保留评估时间及未知状态，不混入渠道凭据。 */
    public record Assessment(String assessmentStatus, LocalDateTime assessedAt, List<MethodAssessment> methods) {
    }

    /** 可供内部运营投影的结构化结果。 */
    public record MethodAssessment(String itemKey, String authorizationStatus, String businessReadiness,
                                   boolean missingFee, boolean missingMid, List<String> reasonCodes) {
    }
}
