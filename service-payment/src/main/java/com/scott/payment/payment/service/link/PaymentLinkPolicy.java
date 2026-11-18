package com.scott.payment.payment.service.link;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.security.OutboundHttpUrlPolicy;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentLinkDTOs.*;
import com.scott.payment.payment.config.PaymentLinkProperties;
import com.scott.payment.payment.service.impl.PaymentCheckoutCardCapabilityService;
import jakarta.validation.Validator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 固定金额、商户身份、数据范围和真实收单能力校验，不触碰资金事实。 */
@Service
public class PaymentLinkPolicy {
    private final PaymentLinkProperties properties;
    private final MerchantRuntimeProfileCacheService merchants;
    private final IsoDictionaryService currencies;
    private final PaymentCheckoutCardCapabilityService capabilities;
    private final Validator validator;
    private final OutboundHttpUrlPolicy urlPolicy;

    public PaymentLinkPolicy(PaymentLinkProperties properties, MerchantRuntimeProfileCacheService merchants,
                             IsoDictionaryService currencies, PaymentCheckoutCardCapabilityService capabilities,
                             Validator validator, Environment environment) {
        this.properties = properties;
        this.merchants = merchants;
        this.currencies = currencies;
        this.capabilities = capabilities;
        this.validator = validator;
        this.urlPolicy = new OutboundHttpUrlPolicy(OutboundHttpUrlPolicy.modeForProfiles(environment.getActiveProfiles()));
    }

    public void requireEnabled() {
        if (!properties.isEnabled() || !properties.isStorageReady()) {
            throw new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE.getCode(), "payment links are not enabled");
        }
    }

    public void requireScope(Actor actor, String merchantId) {
        if (actor == null || actor.getActorId() == null || actor.getActorId().isBlank()
                || !("ADMIN".equals(actor.getAppCode()) || "MERCHANT".equals(actor.getAppCode()))
                || merchantId == null || merchantId.isBlank()
                || !("ADMIN".equals(actor.getAppCode()) && actor.isAllMerchants()
                    || actor.getMerchantIds() != null && actor.getMerchantIds().contains(merchantId))) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
    }

    public MerchantRuntimeProfile merchant(String merchantId) {
        MerchantRuntimeProfile profile = merchants.findRuntimeProfile(merchantId);
        if (profile == null || !Integer.valueOf(1).equals(profile.getMerchantStatus())
                || !"ACTIVE".equals(profile.getActivationStatus())) {
            throw new ServiceException(ApiResultEnum.MERCHANT_PAYMENT_CONFIG_UNAVAILABLE);
        }
        return profile;
    }

    public BigDecimal validate(Create input) {
        if (input == null || !validator.validate(input).isEmpty()
                || input.getExpireMinutes() != null && input.getExpireTime() != null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        IsoCurrencyInfo currency = currencies.getCurrency(input.getCurrency())
                .filter(value -> value.defaultFractionDigits() >= 0 && value.defaultFractionDigits() <= 6)
                .orElseThrow(() -> new ServiceException(ApiResultEnum.PARAM_INVALID));
        BigDecimal amount = new BigDecimal(input.getAmount());
        if (amount.signum() <= 0 || !currencies.isCurrencyFractionValid(amount, input.getCurrency())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        validateUrl(input.getNotifyUrl());
        validateUrl(input.getRedirectUrl());
        return amount.setScale(currency.defaultFractionDigits(), RoundingMode.UNNECESSARY);
    }

    /** 筛选使用 ISO 基础字典，避免为生成下拉而对大量业务记录去重扫描。 */
    public List<String> filterCurrencies() {
        return currencies.listCurrencies().stream().map(IsoCurrencyInfo::alphabeticCode).distinct().sorted().toList();
    }

    public int exponent(String currency) {
        return currencies.getCurrency(currency).orElseThrow(() -> new ServiceException(ApiResultEnum.PARAM_INVALID))
                .defaultFractionDigits();
    }

    public List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> methods(
            String merchantId, String currency, List<String> brands) {
        return resolveMethods(merchantId, currency, brands, true);
    }

    /** 进入既有链接时取冻结品牌与当前能力交集，停用一种品牌不误伤剩余可用品牌。 */
    public List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> currentMethods(
            String merchantId, String currency, List<String> brands) {
        return resolveMethods(merchantId, currency, brands, false);
    }

    private List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> resolveMethods(
            String merchantId, String currency, List<String> brands, boolean requireAll) {
        PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO requested =
                new PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO();
        requested.setPaymentMethod("BANK_CARD");
        requested.setBrands(brands == null ? List.of() : brands);
        List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> result =
                capabilities.resolveAllowedMethods(merchantId, List.of(requested), currency);
        if (result.isEmpty()) {
            throw new ServiceException(ApiResultEnum.MERCHANT_PAYMENT_CONFIG_UNAVAILABLE);
        }
        if (requireAll && brands != null && !brands.isEmpty()) {
            java.util.Set<String> resolved = result.stream().flatMap(method -> method.getBrands().stream())
                    .collect(java.util.stream.Collectors.toSet());
            if (!resolved.containsAll(brands)) { throw new ServiceException(ApiResultEnum.MERCHANT_PAYMENT_CONFIG_UNAVAILABLE); }
        }
        return result;
    }

    public Options options(Actor actor, String merchantId) {
        requireEnabled();
        requireScope(actor, merchantId);
        MerchantRuntimeProfile merchant = merchant(merchantId);
        Options result = new Options();
        result.setMerchantId(merchantId);
        result.setMerchantDisplayName(merchant.getMerchantName());
        java.util.Map<String, List<String>> byCurrency = new java.util.TreeMap<>();
        for (IsoCurrencyInfo currency : currencies.listCurrencies()) {
            List<String> brands = capabilities.resolveAllowedMethods(merchantId, null, currency.alphabeticCode()).stream()
                    .filter(method -> "BANK_CARD".equals(method.getPaymentMethod()))
                    .flatMap(method -> method.getBrands().stream()).distinct().sorted().toList();
            if (!brands.isEmpty()) { byCurrency.put(currency.alphabeticCode(), brands); }
        }
        result.setCardBrandsByCurrency(byCurrency);
        result.setCurrencies(List.copyOf(byCurrency.keySet()));
        result.setCardBrands(byCurrency.values().stream().flatMap(List::stream).distinct().sorted().toList());
        return result;
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            urlPolicy.validate(url);
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "payment link URL is not allowed");
        }
    }
}
