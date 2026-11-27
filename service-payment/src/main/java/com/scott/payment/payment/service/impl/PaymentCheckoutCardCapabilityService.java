package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.component.db.reference.cache.CardBinCacheReader;
import com.scott.payment.component.db.reference.model.CardBinLookupCacheEntry;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryResultDTO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import com.scott.payment.payment.service.MerchantRouteProfileCacheService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checkout 银行卡展示能力与卡品牌识别服务。
 *
 * <p>可展示方式必须同时满足商户绑定、MID、渠道能力、币种/品牌范围和有效期。卡品牌优先使用
 * BIN 基础数据，查询失败时只降级到公开 IIN 规则；BIN 是辅助信息，故障不得阻断支付，也不得
 * 把卡号或 BIN 写入日志。</p>
 */
@Slf4j
@Service
public class PaymentCheckoutCardCapabilityService {

    /**
     * 发卡国家进入交易快照前必须是 ISO 3166-1 Alpha-3 大写代码。
     */
    private static final Pattern ISO_ALPHA_3_PATTERN = Pattern.compile("[A-Z]{3}");

    private final MerchantRouteProfileCacheService routeProfileCacheService;
    private final CardBinCacheReader cardBinCacheReader;
    private final com.scott.payment.link.service.PaymentLinkMethodScope linkMethodScope;
    private final DefaultPaymentChannelRouteService channelRoutes;
    private final PaymentExchangeRateService exchangeRates;

    /**
     * 创建收银台银行卡能力服务。
     *
     * @param routeProfileCacheService 商户 MID 路由能力缓存服务
     * @param cardBinCacheReader       BIN 基础数据缓存读取服务
     */
    public PaymentCheckoutCardCapabilityService(MerchantRouteProfileCacheService routeProfileCacheService,
                                                CardBinCacheReader cardBinCacheReader) {
        this(routeProfileCacheService, cardBinCacheReader, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentCheckoutCardCapabilityService(MerchantRouteProfileCacheService routeProfileCacheService,
                                                CardBinCacheReader cardBinCacheReader,
                                                com.scott.payment.link.service.PaymentLinkMethodScope linkMethodScope,
                                                DefaultPaymentChannelRouteService channelRoutes,
                                                PaymentExchangeRateService exchangeRates) {
        this.routeProfileCacheService = routeProfileCacheService;
        this.cardBinCacheReader = cardBinCacheReader;
        this.linkMethodScope = linkMethodScope;
        this.channelRoutes = channelRoutes;
        this.exchangeRates = exchangeRates;
    }

    /**
     * 从商户已启用 MID 聚合收银台可展示的支付方式与卡品牌。
     *
     * @param merchantId       商户编号
     * @param requestedMethods 商户请求展示的支付方式，可为空
     * @return 同时满足商户请求和有效 MID 能力的支付方式
     */
    public List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> resolveAllowedMethods(
            String merchantId,
            List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> requestedMethods) {
        return resolveAllowedMethods(merchantId, requestedMethods, null);
    }

    /** 支付链接创建时按确定币种和 PAYMENT 交易类型缩小能力，旧调用保持原查询语义。 */
    public List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> resolveAllowedMethods(
            String merchantId,
            List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> requestedMethods,
            String currency) {
        var resolver = new com.scott.payment.link.service.PaymentLinkCapabilityResolver();
        List<com.scott.payment.link.model.PaymentLinkManagementModels.AllowedMethod> requested = requestedMethods == null
                ? null : requestedMethods.stream().map(this::managementMethod).toList();
        return resolver.resolve(routeProfileCacheService.findRouteProfile(merchantId), requested, currency, LocalDateTime.now())
                .stream().map(this::checkoutMethod).toList();
    }

    /** 已核验链接按原方式取交集，配置变更不会扩大或恢复该会话的冻结范围。 */
    public List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> revalidateLinkScope(
            String merchantId, String currency,
            List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> methods) {
        if (linkMethodScope == null) {
            return List.of();
        }
        List<String> keys = methods.stream().flatMap(method -> "BANK_CARD".equals(method.getPaymentMethod())
                        ? method.getBrands().stream().map(brand -> "CARD_BRAND:" + brand)
                        : java.util.stream.Stream.of("METHOD:" + method.getPaymentMethod()))
                .distinct().toList();
        return linkMethodScope.resolve(merchantId, currency, keys, false).stream()
                .map(this::checkoutMethod).toList();
    }

    /** 在独立主库只读事务内核验非卡展示，避免会话回读沿用交易库连接。 */
    @DS(DataSourceName.MASTER)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> resolveHostedNonCardMethods(
            String merchantId, String currency, String billingCountry, Set<String> frozenMethods) {
        if (linkMethodScope == null) {
            return List.of();
        }
        var facts = linkMethodScope.snapshot(merchantId);
        var command = checkoutRouteCommand(merchantId, currency, "PAYMENT");
        if (StringUtils.hasText(billingCountry)) {
            var billing = new PaymentCreateCommandDTO.BillingCardHolderInfoDTO();
            billing.setCountry(billingCountry);
            command.setBillingCardHolderInfo(billing);
        }
        Map<String, Boolean> usableRates = new HashMap<>();
        List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> options = new ArrayList<>();
        for (String method : linkMethodScope.hostedNonCardMethods(facts, frozenMethods)) {
            command.setPaymentMethod(method);
            var route = channelRoutes.assessCandidates(facts.profile(), command, facts.evaluatedAt());
            if (route.status() == DefaultPaymentChannelRouteService.CandidateStatus.UNIQUE_DIRECT
                    || (route.status() == DefaultPaymentChannelRouteService.CandidateStatus.UNIQUE_EDC
                    && usableRates.computeIfAbsent(route.routedCurrency(),
                    target -> hasCheckoutRate(currency, target, facts.evaluatedAt())))) {
                var option = new PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO();
                option.setPaymentMethod(method);
                option.setBrands(List.of());
                options.add(option);
            }
        }
        return options;
    }

    /**
     * 一次读取商户授权、费率和路由快照，计算各冻结钱包在任意付款国家下的可用性。
     * 读取结果用于建会话和展示。真正发起付款仍按提交国家重新核验。
     */
    @DS(DataSourceName.MASTER)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Map<String, Map<String, Boolean>> resolveHostedNonCardCountryScopes(
            String merchantId, String currency, Set<String> frozenMethods) {
        if (linkMethodScope == null) {
            return Map.of();
        }
        var facts = linkMethodScope.snapshot(merchantId);
        var command = checkoutRouteCommand(merchantId, currency, "PAYMENT");
        Map<String, Boolean> usableRates = new HashMap<>();
        Map<String, Map<String, Boolean>> scopes = new LinkedHashMap<>();
        for (String method : linkMethodScope.hostedNonCardMethods(facts, frozenMethods)) {
            command.setPaymentMethod(method);
            var coverage = channelRoutes.checkoutCountryCoverage(facts.profile(), command, facts.evaluatedAt(),
                    target -> usableRates.computeIfAbsent(target,
                            code -> hasCheckoutRate(currency, code, facts.evaluatedAt())));
            if (coverage.containsValue(true)) {
                scopes.put(method, coverage);
            }
        }
        return scopes;
    }

    /** 仅投影本单候选；使用受失效门禁保护的路由快照，复用同一批配置计算全部品牌。 */
    public Map<String, Map<String, Boolean>> checkoutCountryScopes(
            String merchantId, String currency, String action,
            List<PaymentCheckoutSessionQueryResultDTO.PaymentMethodDTO> options,
            Map<String, Map<String, Boolean>> walletScopes, LocalDateTime now) {
        MerchantRouteProfile profile = routeProfileCacheService.findRouteProfile(merchantId);
        var command = checkoutRouteCommand(merchantId, currency, action);
        var transaction = new PaymentCreateCommandDTO.TransactionInfoDTO();
        command.setTransactionInfo(transaction);
        Map<String, Map<String, Boolean>> scopes = new LinkedHashMap<>();
        for (var option : options) {
            String method = option.getPaymentMethod();
            command.setPaymentMethod(method);
            if ("BANK_CARD".equals(method)) {
                if (option.getBrands() == null) {
                    continue;
                }
                for (String brand : option.getBrands()) {
                    transaction.setCardBrand(brand);
                    scopes.computeIfAbsent("CARD_BRAND:" + brand, ignored ->
                            channelRoutes.checkoutCountryCoverage(profile, command, now, null));
                }
            } else {
                transaction.setCardBrand(null);
                scopes.computeIfAbsent("METHOD:" + method, ignored -> walletScopes.containsKey(method)
                        ? walletScopes.get(method) : channelRoutes.checkoutCountryCoverage(profile, command, now, null));
            }
        }
        return scopes;
    }


    private PaymentCreateCommandDTO checkoutRouteCommand(String merchantId, String currency, String action) {
        var command = new PaymentCreateCommandDTO();
        command.setMerchantId(merchantId);
        command.setCurrency(currency);
        command.setTransactionType(action);
        return command;
    }

    private boolean hasCheckoutRate(String currency, String target, LocalDateTime evaluatedAt) {
        return exchangeRates.findTransactionRate(currency, target, evaluatedAt)
                .filter(rate -> rate.getRateId() != null && rate.getRateId() > 0
                        && rate.getFinalRate() != null && rate.getFinalRate().signum() > 0)
                .isPresent();
    }

    /**
     * 按 BIN 基础数据优先、平台卡组织规则降级的顺序解析卡品牌。
     *
     * <p>BIN 未命中、品牌缺失或查询异常时执行平台品牌规则。</p>
     *
     * @param cardDigits 6 至 11 位 BIN 或完整卡号；非数字字符会在查询前移除
     * @return 平台标准卡品牌，无法识别时返回 {@code UNKNOWN}
     */
    public String resolveCardBrand(String cardDigits) {
        String digits = digits(cardDigits);
        CardBinLookupCacheEntry matched = resolveCardBin(digits);
        return resolveCardBrand(digits, matched);
    }

    /**
     * 根据卡号补齐平台内部卡品牌和 ISO Alpha-3 发卡国家。
     *
     * <p>商户 OpenAPI 无需上送这些字段；BIN 查询异常时仅补齐规则可识别的卡品牌，发卡国家保持为空。</p>
     *
     * @param commandDTO 支付创建命令；非银行卡或未提供卡号时不处理
     */
    public void enrichCardBrand(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO == null
                || !"BANK_CARD".equals(normalize(commandDTO.getPaymentMethod()))
                || commandDTO.getCardInfo() == null
                || !StringUtils.hasText(commandDTO.getCardInfo().getCardNo())) {
            return;
        }
        String cardDigits = digits(commandDTO.getCardInfo().getCardNo());
        CardBinLookupCacheEntry matched = resolveCardBin(cardDigits);
        if (commandDTO.getTransactionInfo() == null) {
            commandDTO.setTransactionInfo(new PaymentCreateCommandDTO.TransactionInfoDTO());
        }
        commandDTO.getTransactionInfo().setCardBrand(resolveCardBrand(cardDigits, matched));
        if (matched != null) {
            commandDTO.getTransactionInfo().setIssuerCountry(
                    normalizeIssuerCountryAlpha3(matched.result().issuerCountryAlpha3()));
        }
    }

    /**
     * 优先采用 BIN 数据提供的已知品牌。记录中的 UNKNOWN 仅表示缺少品牌信息。
     */
    private String resolveCardBrand(String digits, CardBinLookupCacheEntry matched) {
        String databaseBrand = matched == null ? "" : normalizeCardBrand(matched.result().cardBrand());
        if (!databaseBrand.isEmpty() && !"UNKNOWN".equals(databaseBrand)) {
            return databaseBrand;
        }
        return PaymentCardBrandRuleMatcher.resolve(digits);
    }

    /**
     * 查询 BIN 基础数据；数据访问异常时按未命中处理，由调用方执行平台规则降级。
     *
     * <p>异常日志只记录异常类型，禁止记录卡号、BIN 或 SQL 参数。</p>
     *
     * @param cardDigits 已规范化的卡号数字
     * @return BIN 命中结果；长度不足或数据访问异常时返回 {@code null}
     */
    private CardBinLookupCacheEntry resolveCardBin(String cardDigits) {
        if (cardDigits.length() < 6) {
            return null;
        }
        // 支付侧按卡号前 11 位识别；输入仅有 6 至 10 位时沿用补零规则，不改变既有路由行为。
        String prefix = CardBinCacheReader.toElevenDigitPrefix(cardDigits);
        try {
            return cardBinCacheReader.findByPrefix(prefix);
        } catch (DataAccessException | ServiceException exception) {
            log.warn("卡BIN基础数据查询异常，已降级使用平台卡品牌规则, event: CARD_BIN_LOOKUP_FALLBACK, exceptionType: {}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 交易快照只接受三位字母国家代码，避免统计维度混入 Alpha-2 或国家名称。
     */
    private String normalizeIssuerCountryAlpha3(String value) {
        String normalized = normalize(value);
        return ISO_ALPHA_3_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private com.scott.payment.link.model.PaymentLinkManagementModels.AllowedMethod managementMethod(
            PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO source) {
        var target = new com.scott.payment.link.model.PaymentLinkManagementModels.AllowedMethod();
        target.setPaymentMethod(source.getPaymentMethod()); target.setChannelCode(source.getChannelCode());
        target.setBrands(source.getBrands()); target.setThreeDsMode(source.getThreeDsMode());
        return target;
    }

    private PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO checkoutMethod(
            com.scott.payment.link.model.PaymentLinkManagementModels.AllowedMethod source) {
        var target = new PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO();
        target.setPaymentMethod(source.getPaymentMethod()); target.setChannelCode(source.getChannelCode());
        target.setBrands(source.getBrands()); target.setThreeDsMode(source.getThreeDsMode());
        return target;
    }

    /**
     * 规范化卡品牌编码。
     */
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

    /**
     * 提取文本中的数字字符。
     */
    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    /**
     * 规范化卡品牌能力匹配值。
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
