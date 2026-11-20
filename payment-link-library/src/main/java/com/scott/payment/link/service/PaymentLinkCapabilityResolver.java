package com.scott.payment.link.service;

import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.link.model.PaymentLinkManagementModels.AllowedMethod;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;

/** 只读展示能力的公共计算规则，不识别卡号、不参与渠道路由执行。 */
public final class PaymentLinkCapabilityResolver {
    private static final List<String> ALL_CARD_BRANDS = List.of(
            "VISA", "MASTERCARD", "AMEX", "JCB", "MAESTRO", "UNIONPAY", "DISCOVER", "DINERS_CLUB",
            "CARTES_BANCAIRES", "EFTPOS_AUSTRALIA", "INTERAC");
    /** 过滤有效绑定、MID、渠道及交易币种后，合并支付方式与卡品牌能力；无有效候选时返回空。 */
    public List<AllowedMethod> resolve(MerchantRouteProfile profile, List<AllowedMethod> requestedMethods,
                                       String currency, LocalDateTime now) {
        if (profile == null || profile.getRouteOptions() == null) {
            return List.of();
        }
        Map<String, LinkedHashSet<String>> brandsByMethod = new LinkedHashMap<>();
        Map<String, Boolean> supports3dsByMethod = new LinkedHashMap<>();
        for (MerchantRouteProfile.RouteOption option : profile.getRouteOptions()) {
            // 展示能力只来自当前有效的路由候选，过期绑定或停用 MID/渠道不得暴露给付款人。
            if (!active(option, now)) {
                continue;
            }
            if (currency != null && (!scopeAllows(option.getCurrencyScope(), normalize(currency))
                    || !scopeAllows(option.getTransactionTypeScope(), "PAYMENT")
                    || !StringUtils.hasText(option.getCapabilityTransactionType())
                    || !scopeAllows(option.getCapabilityTransactionType(), "PAYMENT")
                    || option.getSupportedCurrencies() == null
                    || option.getSupportedCurrencies().stream().noneMatch(value -> normalize(currency).equals(normalize(value))))) {
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
                // MID 品牌范围与渠道能力取交集，任一侧不支持都不能展示该卡品牌。
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
        List<AllowedMethod> result = new ArrayList<>();
        brandsByMethod.forEach((key, brands) -> {
            if (key.startsWith("BANK_CARD|") && brands.isEmpty()) {
                return;
            }
            String[] parts = key.split("\\|", -1);
            AllowedMethod method =
                    new AllowedMethod();
            method.setPaymentMethod(parts[0]);
            method.setChannelCode(parts[1]);
            method.setBrands(new ArrayList<>(brands));
            method.setThreeDsMode(Boolean.TRUE.equals(supports3dsByMethod.get(key)) ? "AUTO" : "OFF");
            result.add(method);
        });
        return result;
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

    /**
     * 按生效含边界、到期不含边界判断配置有效窗口。
     */
    private boolean inWindow(LocalDateTime now, LocalDateTime start, LocalDateTime end) {
        return (start == null || !now.isBefore(start)) && (end == null || now.isBefore(end));
    }

    /**
     * 判断请求支付方式是否允许当前能力。
     */
    private boolean requestedMethodAllows(List<AllowedMethod> requested,
                                          String method,
                                          String channel) {
        if (requested == null || requested.isEmpty()) {
            return true;
        }
        return requested.stream().anyMatch(item -> method.equals(normalize(item.getPaymentMethod()))
                && (!StringUtils.hasText(item.getChannelCode()) || normalize(channel).equals(normalize(item.getChannelCode()))));
    }

    /**
     * 计算请求卡品牌与渠道卡品牌的交集。
     */
    private Set<String> intersectRequestedBrands(List<AllowedMethod> requested,
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

    /**
     * 解析渠道能力范围值集合。
     */
    private Set<String> scopeValues(String scope) {
        if (!StringUtils.hasText(scope) || "ALL".equalsIgnoreCase(scope.trim())) {
            return new LinkedHashSet<>(ALL_CARD_BRANDS);
        }
        if ("NONE".equalsIgnoreCase(scope.trim())) {
            return new LinkedHashSet<>();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Arrays.stream(scope.split(",")).map(this::normalize).filter(StringUtils::hasText).forEach(values::add);
        return values;
    }

    /**
     * 判断配置范围是否允许当前交易。
     */
    private boolean scopeAllows(String scope, String value) {
        if (!StringUtils.hasText(scope) || "ALL".equalsIgnoreCase(scope.trim())) {
            return true;
        }
        return Arrays.stream(scope.split(",")).map(this::normalize).anyMatch(normalize(value)::equals);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
