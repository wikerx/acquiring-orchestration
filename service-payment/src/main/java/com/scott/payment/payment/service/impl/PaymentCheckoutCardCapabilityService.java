package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.model.PaymentCardBinCacheEntry;
import com.scott.payment.payment.service.MerchantRouteProfileCacheService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 聚合商户 MID 卡品牌能力，并提供服务端 BIN 品牌解析。 */
@Service
public class PaymentCheckoutCardCapabilityService {

    private static final List<String> ALL_CARD_BRANDS = List.of(
            "VISA", "MASTERCARD", "AMEX", "JCB", "MAESTRO", "UNIONPAY", "DISCOVER", "DINERS_CLUB",
            "CARTES_BANCAIRES", "EFTPOS_AUSTRALIA", "INTERAC");
    private static final Pattern ISO_ALPHA_3_PATTERN = Pattern.compile("[A-Z]{3}");

    private final MerchantRouteProfileCacheService routeProfileCacheService;
    private final PaymentCardBinCacheReader cardBinCacheReader;

    public PaymentCheckoutCardCapabilityService(MerchantRouteProfileCacheService routeProfileCacheService,
                                                PaymentCardBinCacheReader cardBinCacheReader) {
        this.routeProfileCacheService = routeProfileCacheService;
        this.cardBinCacheReader = cardBinCacheReader;
    }

    /** 从商户已启用 MID 聚合收银台可展示的支付方式与卡品牌。 */
    public List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> resolveAllowedMethods(
            String merchantId,
            List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> requestedMethods) {
        MerchantRouteProfile profile = routeProfileCacheService.findRouteProfile(merchantId);
        if (profile == null || profile.getRouteOptions() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, LinkedHashSet<String>> brandsByMethod = new LinkedHashMap<>();
        Map<String, Boolean> supports3dsByMethod = new LinkedHashMap<>();
        for (MerchantRouteProfile.RouteOption option : profile.getRouteOptions()) {
            if (!active(option, now)) {
                continue;
            }
            String paymentMethod = normalize(option.getCapabilityPaymentMethod());
            if (!scopeAllows(option.getPaymentMethodScope(), paymentMethod)
                    || !requestedMethodAllows(requestedMethods, paymentMethod, option.getChannelCode())) {
                continue;
            }
            Set<String> brands = scopeValues(option.getCardBrandScope());
            if ("BANK_CARD".equals(paymentMethod)) {
                Set<String> capabilityBrands = new LinkedHashSet<>();
                if (option.getCapabilitySupportedCardBrands() != null) {
                    option.getCapabilitySupportedCardBrands().stream()
                            .map(this::normalize)
                            .filter(StringUtils::hasText)
                            .forEach(capabilityBrands::add);
                }
                brands.retainAll(capabilityBrands);
                if (brands.isEmpty()) {
                    continue;
                }
            }
            String methodKey = paymentMethod + "|" + normalize(option.getChannelCode());
            brandsByMethod.computeIfAbsent(methodKey, ignored -> new LinkedHashSet<>())
                    .addAll(intersectRequestedBrands(requestedMethods, paymentMethod, option.getChannelCode(), brands));
            supports3dsByMethod.merge(
                    methodKey,
                    Integer.valueOf(1).equals(option.getCapabilitySupport3ds()),
                    Boolean::logicalOr
            );
        }
        List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> result = new ArrayList<>();
        brandsByMethod.forEach((key, brands) -> {
            if (key.startsWith("BANK_CARD|") && brands.isEmpty()) {
                return;
            }
            String[] parts = key.split("\\|", -1);
            PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO method =
                    new PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO();
            method.setPaymentMethod(parts[0]);
            method.setChannelCode(parts[1]);
            method.setBrands(new ArrayList<>(brands));
            method.setThreeDsMode(Boolean.TRUE.equals(supports3dsByMethod.get(key)) ? "AUTO" : "OFF");
            result.add(method);
        });
        return result;
    }

    /** 数据库优先解析卡品牌，未命中时使用稳定卡组织前缀规则兜底。 */
    public String resolveCardBrand(String cardDigits) {
        String digits = digits(cardDigits);
        PaymentCardBinCacheEntry matched = resolveCardBin(digits);
        if (matched != null && StringUtils.hasText(matched.getCardBrand())) {
            return normalizeCardBrand(matched.getCardBrand());
        }
        return PaymentCardBrandRuleMatcher.resolve(digits);
    }

    /** 根据卡号补齐平台内部卡品牌和 ISO Alpha-3 发卡国家，商户 OpenAPI 不需要上送这些字段。 */
    public void enrichCardBrand(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO == null
                || !"BANK_CARD".equals(normalize(commandDTO.getPaymentMethod()))
                || commandDTO.getCardInfo() == null
                || !StringUtils.hasText(commandDTO.getCardInfo().getCardNo())) {
            return;
        }
        String cardDigits = digits(commandDTO.getCardInfo().getCardNo());
        PaymentCardBinCacheEntry matched = resolveCardBin(cardDigits);
        String cardBrand = matched != null && StringUtils.hasText(matched.getCardBrand())
                ? normalizeCardBrand(matched.getCardBrand())
                : PaymentCardBrandRuleMatcher.resolve(cardDigits);
        if (commandDTO.getTransactionInfo() == null) {
            commandDTO.setTransactionInfo(new PaymentCreateCommandDTO.TransactionInfoDTO());
        }
        commandDTO.getTransactionInfo().setCardBrand(cardBrand);
        if (matched != null) {
            commandDTO.getTransactionInfo().setIssuerCountry(
                    normalizeIssuerCountryAlpha3(matched.getIssuerCountryAlpha3()));
        }
    }

    private PaymentCardBinCacheEntry resolveCardBin(String cardDigits) {
        if (cardDigits.length() < 6) {
            return null;
        }
        String prefix = (cardDigits + "00000000000").substring(0, 11);
        return cardBinCacheReader.findByPrefix(prefix);
    }

    /** 交易快照只接受三位字母国家代码，避免统计维度混入 Alpha-2 或国家名称。 */
    private String normalizeIssuerCountryAlpha3(String value) {
        String normalized = normalize(value);
        return ISO_ALPHA_3_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private boolean active(MerchantRouteProfile.RouteOption option, LocalDateTime now) {
        return Integer.valueOf(1).equals(option.getBindingStatus())
                && Integer.valueOf(1).equals(option.getMidStatus())
                && Integer.valueOf(1).equals(option.getChannelStatus())
                && Integer.valueOf(1).equals(option.getSupportAcquiring())
                && Integer.valueOf(1).equals(option.getCapabilityStatus())
                && inWindow(now, option.getBindingEffectiveTime(), option.getBindingExpireTime())
                && inWindow(now, option.getMidEffectiveTime(), option.getMidExpireTime())
                && "ACQUIRING".equals(normalize(option.getBusinessType()));
    }

    private boolean inWindow(LocalDateTime now, LocalDateTime start, LocalDateTime end) {
        return (start == null || !now.isBefore(start)) && (end == null || now.isBefore(end));
    }

    private boolean requestedMethodAllows(List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> requested,
                                          String method,
                                          String channel) {
        if (requested == null || requested.isEmpty()) {
            return true;
        }
        return requested.stream().anyMatch(item -> method.equals(normalize(item.getPaymentMethod()))
                && (!StringUtils.hasText(item.getChannelCode()) || normalize(channel).equals(normalize(item.getChannelCode()))));
    }

    private Set<String> intersectRequestedBrands(List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> requested,
                                                 String method,
                                                 String channel,
                                                 Set<String> supported) {
        if (requested == null) {
            return supported;
        }
        Set<String> requestedBrands = new LinkedHashSet<>();
        requested.stream()
                .filter(item -> method.equals(normalize(item.getPaymentMethod())))
                .filter(item -> !StringUtils.hasText(item.getChannelCode())
                        || normalize(channel).equals(normalize(item.getChannelCode())))
                .filter(item -> item.getBrands() != null)
                .flatMap(item -> item.getBrands().stream())
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .forEach(requestedBrands::add);
        if (requestedBrands.isEmpty()) {
            return supported;
        }
        LinkedHashSet<String> intersection = new LinkedHashSet<>(supported);
        intersection.retainAll(requestedBrands);
        return intersection;
    }

    private Set<String> scopeValues(String scope) {
        if (!StringUtils.hasText(scope) || "ALL".equalsIgnoreCase(scope.trim())) {
            return new LinkedHashSet<>(ALL_CARD_BRANDS);
        }
        if ("NONE".equalsIgnoreCase(scope.trim())) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Arrays.stream(scope.split(",")).map(this::normalize).filter(StringUtils::hasText).forEach(values::add);
        return values;
    }

    private boolean scopeAllows(String scope, String value) {
        if (!StringUtils.hasText(scope) || "ALL".equalsIgnoreCase(scope.trim())) {
            return true;
        }
        return Arrays.stream(scope.split(",")).map(this::normalize).anyMatch(normalize(value)::equals);
    }

    private String normalizeCardBrand(String value) {
        String normalized = normalize(value).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "MASTER", "MASTER_CARD" -> "MASTERCARD";
            case "AMERICA_EXPRESS", "AMERICAN_EXPRESS", "AE" -> "AMEX";
            case "JAPAN_CREDIT_BUREAU" -> "JCB";
            case "UNION_PAY" -> "UNIONPAY";
            case "DINERS", "DINERSCLUB", "DINERS_CLUB_CARD" -> "DINERS_CLUB";
            default -> normalized;
        };
    }

    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
