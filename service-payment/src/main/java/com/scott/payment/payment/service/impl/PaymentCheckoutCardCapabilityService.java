package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.entity.PaymentCardBinRangeDO;
import com.scott.payment.payment.mapper.PaymentCardBinRangeMapper;
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

/** 聚合商户 MID 卡品牌能力，并提供服务端 BIN 品牌解析。 */
@Service
public class PaymentCheckoutCardCapabilityService {

    private static final List<String> ALL_CARD_BRANDS = List.of(
            "VISA", "MASTERCARD", "AMEX", "JCB", "MAESTRO", "UNIONPAY", "DISCOVER");

    private final MerchantRouteProfileCacheReader routeProfileReader;
    private final PaymentCardBinRangeMapper cardBinRangeMapper;

    public PaymentCheckoutCardCapabilityService(MerchantRouteProfileCacheReader routeProfileReader,
                                                PaymentCardBinRangeMapper cardBinRangeMapper) {
        this.routeProfileReader = routeProfileReader;
        this.cardBinRangeMapper = cardBinRangeMapper;
    }

    /** 从商户已启用 MID 聚合收银台可展示的支付方式与卡品牌。 */
    public List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> resolveAllowedMethods(
            String merchantId,
            List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> requestedMethods) {
        MerchantRouteProfile profile = routeProfileReader.findCached(merchantId);
        if (profile == null || profile.getRouteOptions() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, LinkedHashSet<String>> brandsByMethod = new LinkedHashMap<>();
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
            if ("BANK_CARD".equals(paymentMethod) && brands.isEmpty()) {
                continue;
            }
            brandsByMethod.computeIfAbsent(paymentMethod + "|" + normalize(option.getChannelCode()), ignored -> new LinkedHashSet<>())
                    .addAll(intersectRequestedBrands(requestedMethods, paymentMethod, option.getChannelCode(), brands));
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
            method.setThreeDsMode("OFF");
            result.add(method);
        });
        return result;
    }

    /** 数据库优先解析卡品牌，未命中时使用稳定卡组织前缀规则兜底。 */
    public String resolveCardBrand(String cardDigits) {
        String digits = digits(cardDigits);
        if (digits.length() >= 6) {
            String prefix = digits.substring(0, Math.min(11, digits.length()));
            long numericValue = Long.parseLong((prefix + "00000000000").substring(0, 11));
            PaymentCardBinRangeDO matched = cardBinRangeMapper.selectBestMatch(numericValue);
            if (matched != null && StringUtils.hasText(matched.getCardBrand())) {
                return normalize(matched.getCardBrand());
            }
        }
        return fallbackBrand(digits);
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

    private String fallbackBrand(String digits) {
        if (digits.startsWith("4")) return "VISA";
        if (digits.startsWith("34") || digits.startsWith("37")) return "AMEX";
        if (digits.startsWith("35")) return "JCB";
        if (digits.startsWith("5") || digits.startsWith("22")) return "MASTERCARD";
        if (digits.startsWith("62")) return "UNIONPAY";
        if (digits.startsWith("6011") || digits.startsWith("65")) return "DISCOVER";
        return "UNKNOWN";
    }

    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
