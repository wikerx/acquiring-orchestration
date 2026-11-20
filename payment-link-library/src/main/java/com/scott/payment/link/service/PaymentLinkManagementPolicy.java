package com.scott.payment.link.service;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.security.OutboundHttpUrlPolicy;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.link.model.PaymentLinkManagementModels.AllowedMethod;
import com.scott.payment.link.model.PaymentLinkManagementModels.*;
import com.scott.payment.link.config.PaymentLinkSettings;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 固定金额、商户身份、数据范围和真实收单能力校验，不触碰资金事实。 */
@Service("paymentLinkManagementPolicy")
public class PaymentLinkManagementPolicy {
    private final PaymentLinkSettings properties;
    private final MerchantRuntimeProfileCacheService merchants;
    private final IsoDictionaryService currencies;
    private final PaymentLinkCapabilities capabilities;
    private final Validator validator;
    private final OutboundHttpUrlPolicy urlPolicy;
    private final PaymentLinkMethodScope methodScope;

    public PaymentLinkManagementPolicy(PaymentLinkSettings properties, MerchantRuntimeProfileCacheService merchants,
                             IsoDictionaryService currencies, PaymentLinkCapabilities capabilities,
                             Validator validator, Environment environment) {
        this(properties, merchants, currencies, capabilities, validator, environment, null);
    }

    @Autowired
    public PaymentLinkManagementPolicy(PaymentLinkSettings properties, MerchantRuntimeProfileCacheService merchants,
                             IsoDictionaryService currencies, PaymentLinkCapabilities capabilities,
                             Validator validator, Environment environment, PaymentLinkMethodScope methodScope) {
        this.properties = properties;
        this.merchants = merchants;
        this.currencies = currencies;
        this.capabilities = capabilities;
        this.validator = validator;
        this.urlPolicy = new OutboundHttpUrlPolicy(OutboundHttpUrlPolicy.modeForProfiles(environment.getActiveProfiles()));
        this.methodScope = methodScope;
    }

    /** 链接入口与存储必须同时就绪，否则以配置不可用拒绝管理操作。 */
    public void requireEnabled() {
        if (!properties.isEnabled() || !properties.isStorageReady()) {
            throw new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE.getCode(), "payment links are not enabled");
        }
    }

    /** 验证可信端身份和目标商户范围，商户账号不能扩大到全部商户。 */
    public void requireScope(Actor actor, String merchantId) {
        if (actor == null || actor.getActorId() == null || actor.getActorId().isBlank()
                || !("ADMIN".equals(actor.getAppCode()) || "MERCHANT".equals(actor.getAppCode()))
                || merchantId == null || merchantId.isBlank()
                || !("ADMIN".equals(actor.getAppCode()) && actor.isAllMerchants()
                    || actor.getMerchantIds() != null && actor.getMerchantIds().contains(merchantId))) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
    }

    /** 只接受已启用且激活的商户运行配置，缺失或停用时拒绝创建或进入付款。 */
    public MerchantRuntimeProfile merchant(String merchantId) {
        MerchantRuntimeProfile profile = merchants.findRuntimeProfile(merchantId);
        if (profile == null || !Integer.valueOf(1).equals(profile.getMerchantStatus())
                || !"ACTIVE".equals(profile.getActivationStatus())) {
            throw new ServiceException(ApiResultEnum.MERCHANT_PAYMENT_CONFIG_UNAVAILABLE);
        }
        return profile;
    }

    /** 校验固定金额、币种精度、有效期及回调地址；金额仅补足精度，不做隐式舍入。 */
    public BigDecimal validate(Create input) {
        if (input == null || !validator.validate(input).isEmpty()
                || input.getExpireMinutes() != null && input.getExpireTime() != null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        IsoCurrencyInfo currency = currencies.getCurrency(input.getCurrency())
                .filter(value -> value.defaultFractionDigits() >= 0 && value.defaultFractionDigits() <= 6)
                .orElseThrow(() -> new ServiceException(ApiResultEnum.PARAM_INVALID));
        BigDecimal amount = new BigDecimal(input.getAmount());
        // 支付链接的新建限额是产品规则；不修改已有链接、普通 Checkout 或其他交易入口的金额语义。
        if (amount.compareTo(BigDecimal.ONE) < 0 || amount.compareTo(new BigDecimal("999999.99")) > 0
                || amount.scale() > Math.min(2, currency.defaultFractionDigits())
                || !currencies.isCurrencyFractionValid(amount, input.getCurrency())) {
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

    /** 从 ISO 字典读取币种精度；未知币种拒绝使用默认小数位。 */
    public int exponent(String currency) {
        return currencies.getCurrency(currency).orElseThrow(() -> new ServiceException(ApiResultEnum.PARAM_INVALID))
                .defaultFractionDigits();
    }

    /** 创建时要求所选卡品牌全部符合当前商户能力，不把部分可用静默视为全部开通。 */
    public List<AllowedMethod> allowedMethods(
            String merchantId, String currency, List<String> brands) {
        return resolveMethods(merchantId, currency, brands, true);
    }

    /** 进入既有链接时取冻结品牌与当前能力交集，停用一种品牌不误伤剩余可用品牌。 */
    public List<AllowedMethod> currentAllowedMethods(
            String merchantId, String currency, List<String> brands) {
        return resolveMethods(merchantId, currency, brands, false);
    }

    /** 新契约明确按规范方式冻结。授权、费率和当前配置由同一主库事实复核。 */
    public List<AllowedMethod> resolveMethodScope(String merchantId, String currency, List<String> keys,
                                                  boolean requireAll) {
        if (methodScope == null) {
            throw new ServiceException(ApiResultEnum.MERCHANT_PAYMENT_CONFIG_UNAVAILABLE);
        }
        return methodScope.resolve(merchantId, currency, keys, requireAll);
    }

    private List<AllowedMethod> resolveMethods(
            String merchantId, String currency, List<String> brands, boolean requireAll) {
        if (methodScope != null) {
            PaymentLinkMethodScope.Snapshot governed = methodScope.snapshot(merchantId);
            if (governed.authority().mode() != null) {
                List<String> enabledCards = methodScope.enabledKeys(governed, currency).stream()
                        .filter(key -> key.startsWith("CARD_BRAND:")).toList();
                List<String> requestedCards = brands == null || brands.isEmpty() ? enabledCards
                        : brands.stream().map(brand -> "CARD_BRAND:" + brand).distinct().toList();
                List<String> keys = requestedCards.stream().filter(enabledCards::contains).toList();
                if (keys.isEmpty() || requireAll && keys.size() != requestedCards.size()) {
                    throw new ServiceException(ApiResultEnum.MERCHANT_PAYMENT_CONFIG_UNAVAILABLE);
                }
                return methodScope.resolve(merchantId, currency, keys, requireAll);
            }
        }
        AllowedMethod requested =
                new AllowedMethod();
        requested.setPaymentMethod("BANK_CARD");
        requested.setBrands(brands == null ? List.of() : brands);
        List<AllowedMethod> result =
                capabilities.resolve(merchantId, List.of(requested), currency);
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

    public <T extends Options> T options(Actor actor, String merchantId, java.util.function.Supplier<T> factory) {
        requireEnabled();
        requireScope(actor, merchantId);
        MerchantRuntimeProfile merchant = merchant(merchantId);
        T result = factory.get();
        result.setMerchantId(merchantId);
        result.setMerchantDisplayName(merchant.getMerchantName());
        java.util.Map<String, List<String>> byCurrency = new java.util.TreeMap<>();
        java.util.Map<String, List<String>> methodsByCurrency = new java.util.TreeMap<>();
        java.util.Map<String, List<String>> keysByCurrency = new java.util.TreeMap<>();
        java.util.Map<String, Integer> exponents = new java.util.TreeMap<>();
        PaymentLinkCapabilities snapshot = capabilities.snapshot(merchantId);
        PaymentLinkMethodScope.Snapshot governed = methodScope == null ? null : methodScope.snapshot(merchantId);
        for (IsoCurrencyInfo currency : currencies.listCurrencies()) {
            if (currency.defaultFractionDigits() < 0 || currency.defaultFractionDigits() > 6) continue;
            exponents.put(currency.alphabeticCode(), currency.defaultFractionDigits());
            if (governed != null && governed.authority().mode() != null) {
                keysByCurrency.put(currency.alphabeticCode(), methodScope.enabledKeys(governed, currency.alphabeticCode()));
            }
            var allowed = snapshot.resolve(merchantId, null, currency.alphabeticCode());
            methodsByCurrency.put(currency.alphabeticCode(), allowed.stream()
                    .map(AllowedMethod::getPaymentMethod)
                    .distinct().sorted().toList());
            List<String> brands = allowed.stream()
                    .filter(method -> "BANK_CARD".equals(method.getPaymentMethod()))
                    .flatMap(method -> method.getBrands().stream()).distinct().sorted().toList();
            if (!brands.isEmpty()) { byCurrency.put(currency.alphabeticCode(), brands); }
        }
        result.setPaymentMethodsByCurrency(methodsByCurrency);
        if (governed != null && governed.authority().mode() != null) {
            result.setPaymentMethodKeysByCurrency(keysByCurrency);
        }
        result.setCardBrandsByCurrency(byCurrency);
        result.setCurrencies(List.copyOf(exponents.keySet()));
        result.setCurrencyExponents(exponents);
        result.setDefaultCurrency(merchant.getSettlementCurrency() != null && exponents.containsKey(merchant.getSettlementCurrency())
                ? merchant.getSettlementCurrency() : null);
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
