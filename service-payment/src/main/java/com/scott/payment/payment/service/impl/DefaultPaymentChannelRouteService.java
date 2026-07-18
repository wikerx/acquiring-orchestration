package com.scott.payment.payment.service.impl;

import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.entity.ChannelCapabilityCurrencyDO;
import com.scott.payment.payment.entity.ChannelInfoDO;
import com.scott.payment.payment.entity.ChannelMidConfigDO;
import com.scott.payment.payment.entity.ChannelPaymentCapabilityDO;
import com.scott.payment.payment.entity.MerchantChannelMidBindingDO;
import com.scott.payment.payment.mapper.PaymentChannelCapabilityCurrencyMapper;
import com.scott.payment.payment.mapper.PaymentChannelInfoMapper;
import com.scott.payment.payment.mapper.PaymentChannelMidConfigMapper;
import com.scott.payment.payment.mapper.PaymentChannelPaymentCapabilityMapper;
import com.scott.payment.payment.mapper.PaymentMerchantChannelMidBindingMapper;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentChannelRouteService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道路由默认实现，位于 service-payment 服务实现层，按商户 MID 绑定和 MID 能力范围选择渠道，当前不做权重和复杂路由。
 * @status : create
 */
@Service
public class DefaultPaymentChannelRouteService implements PaymentChannelRouteService {

    private static final long NOT_DELETED = 0L;

    private static final int ENABLED = 1;

    private static final String BUSINESS_ACQUIRING = "ACQUIRING";

    private static final String ALL = "ALL";

    private static final String SCOPE_SEPARATOR = ",";

    private static final String DEFAULT_PAYMENT_METHOD = "BANK_CARD";

    private final PaymentMerchantChannelMidBindingMapper midBindingMapper;

    private final PaymentChannelMidConfigMapper midConfigMapper;

    private final PaymentChannelInfoMapper channelInfoMapper;

    private final PaymentChannelPaymentCapabilityMapper capabilityMapper;

    private final PaymentChannelCapabilityCurrencyMapper capabilityCurrencyMapper;

    /**
     * 创建渠道路由服务。
     *
     * @param midBindingMapper 商户 MID 绑定 Mapper
     * @param midConfigMapper  MID 配置 Mapper
     * @param channelInfoMapper 渠道基础信息 Mapper
     * @param capabilityMapper 渠道支付能力 Mapper
     * @param capabilityCurrencyMapper 渠道能力币种 Mapper
     */
    public DefaultPaymentChannelRouteService(PaymentMerchantChannelMidBindingMapper midBindingMapper,
                                             PaymentChannelMidConfigMapper midConfigMapper,
                                             PaymentChannelInfoMapper channelInfoMapper,
                                             PaymentChannelPaymentCapabilityMapper capabilityMapper,
                                             PaymentChannelCapabilityCurrencyMapper capabilityCurrencyMapper) {
        this.midBindingMapper = midBindingMapper;
        this.midConfigMapper = midConfigMapper;
        this.channelInfoMapper = channelInfoMapper;
        this.capabilityMapper = capabilityMapper;
        this.capabilityCurrencyMapper = capabilityCurrencyMapper;
    }

    /**
     * 根据商户、支付方式、交易类型、币种和国家选择可用渠道 MID。
     *
     * @param commandDTO 创建交易命令
     * @return 渠道路由结果
     */
    @Override
    public PaymentRouteResultDTO route(PaymentCreateCommandDTO commandDTO) {
        List<MerchantChannelMidBindingDO> bindings = midBindingMapper.selectList(Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                .eq(MerchantChannelMidBindingDO::getDeleted, NOT_DELETED)
                .eq(MerchantChannelMidBindingDO::getBindingStatus, ENABLED)
                .eq(MerchantChannelMidBindingDO::getMerchantId, commandDTO.getMerchantId()));
        LocalDateTime now = LocalDateTime.now();
        List<RouteCandidate> candidates = bindings.stream()
                .filter(binding -> isActive(now, binding.getEffectiveTime(), binding.getExpireTime()))
                .map(binding -> toCandidate(binding, commandDTO, now))
                .filter(candidate -> candidate != null)
                .toList();
        if (candidates.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户未配置可用渠道MID");
        }
        if (candidates.size() > 1) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户渠道MID配置命中多条，请先收敛绑定关系");
        }
        RouteCandidate candidate = candidates.get(0);
        PaymentRouteResultDTO resultDTO = PaymentRouteResultDTO.routed(candidate.channelInfo().getChannelCode());
        resultDTO.setChannelId(candidate.channelInfo().getId());
        resultDTO.setMidConfigId(candidate.midConfig().getId());
        resultDTO.setMidNo(candidate.midConfig().getChannelMid());
        resultDTO.setRequestUrl(candidate.channelInfo().getDefaultRequestUrl());
        resultDTO.setConnectTimeoutSeconds(candidate.channelInfo().getConnectTimeoutSeconds());
        resultDTO.setReadTimeoutSeconds(candidate.channelInfo().getReadTimeoutSeconds());
        resultDTO.setMetadataValues(parseMetadata(candidate.midConfig().getMetadataValueJson()));
        resultDTO.setRequestedCurrency(normalize(commandDTO.getCurrency()));
        resultDTO.setRoutedCurrency(candidate.routedCurrency());
        resultDTO.setEdcRequired(candidate.edcRequired());
        resultDTO.setCapabilityId(candidate.capability().getId());
        resultDTO.setSupportedCurrencies(candidate.supportedCurrencies());
        resultDTO.setRouteReason("MERCHANT_MID_BINDING");
        return resultDTO;
    }

    private RouteCandidate toCandidate(MerchantChannelMidBindingDO binding,
                                       PaymentCreateCommandDTO commandDTO,
                                       LocalDateTime now) {
        ChannelMidConfigDO midConfig = midConfigMapper.selectOne(Wrappers.<ChannelMidConfigDO>lambdaQuery()
                .eq(ChannelMidConfigDO::getDeleted, NOT_DELETED)
                .eq(ChannelMidConfigDO::getId, binding.getMidConfigId())
                .eq(ChannelMidConfigDO::getMidStatus, ENABLED));
        if (midConfig == null || !isActive(now, midConfig.getEffectiveTime(), midConfig.getExpireTime())) {
            return null;
        }
        ChannelInfoDO channelInfo = channelInfoMapper.selectOne(Wrappers.<ChannelInfoDO>lambdaQuery()
                .eq(ChannelInfoDO::getDeleted, NOT_DELETED)
                .eq(ChannelInfoDO::getId, midConfig.getChannelId())
                .eq(ChannelInfoDO::getChannelStatus, ENABLED)
                .eq(ChannelInfoDO::getSupportAcquiring, ENABLED));
        if (channelInfo == null) {
            return null;
        }
        if (!BUSINESS_ACQUIRING.equals(normalize(midConfig.getBusinessType()))) {
            return null;
        }
        String paymentMethod = resolvePaymentMethod(commandDTO);
        if (!matchesScope(midConfig.getPaymentMethodScope(), paymentMethod)) {
            return null;
        }
        if (!matchesScope(midConfig.getTransactionTypeScope(), commandDTO.getTransactionType())) {
            return null;
        }
        String payerCountry = commandDTO.getBillingCardHolderInfo() == null ? null : commandDTO.getBillingCardHolderInfo().getCountry();
        if (!matchesScope(midConfig.getAllowedCountryScope(), payerCountry)) {
            return null;
        }
        List<ChannelPaymentCapabilityDO> capabilities = capabilityMapper.selectList(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getChannelId, channelInfo.getId())
                .eq(ChannelPaymentCapabilityDO::getBusinessType, BUSINESS_ACQUIRING)
                .eq(ChannelPaymentCapabilityDO::getPaymentMethod, paymentMethod)
                .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED)
                .orderByAsc(ChannelPaymentCapabilityDO::getSortOrder)
                .orderByAsc(ChannelPaymentCapabilityDO::getId));
        for (ChannelPaymentCapabilityDO capability : capabilities) {
            if (!matchesTransactionType(capability.getTransactionType(), commandDTO.getTransactionType())) {
                continue;
            }
            List<String> currencies = resolveSupportedCurrencies(capability, midConfig);
            if (currencies.isEmpty()) {
                continue;
            }
            String requestedCurrency = normalize(commandDTO.getCurrency());
            boolean directCurrencySupported = currencies.stream().anyMatch(item -> item.equals(requestedCurrency));
            String routedCurrency = directCurrencySupported ? requestedCurrency : currencies.get(0);
            return new RouteCandidate(channelInfo, midConfig, capability, currencies, routedCurrency, !directCurrencySupported);
        }
        return null;
    }

    private boolean isActive(LocalDateTime now, LocalDateTime effectiveTime, LocalDateTime expireTime) {
        return (effectiveTime == null || !now.isBefore(effectiveTime))
                && (expireTime == null || now.isBefore(expireTime));
    }

    private boolean matchesScope(String scope, String value) {
        if (!StringUtils.hasText(scope) || ALL.equalsIgnoreCase(scope.trim())) {
            return true;
        }
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalizedValue = normalize(value);
        for (String item : scope.split(SCOPE_SEPARATOR)) {
            if (normalizedValue.equals(normalize(item))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesTransactionType(String capabilityTransactionType, String requestedTransactionType) {
        return matchesScope(capabilityTransactionType, requestedTransactionType);
    }

    private List<String> resolveSupportedCurrencies(ChannelPaymentCapabilityDO capability, ChannelMidConfigDO midConfig) {
        List<String> capabilityCurrencies = capabilityCurrencyMapper.selectList(Wrappers.<ChannelCapabilityCurrencyDO>lambdaQuery()
                        .eq(ChannelCapabilityCurrencyDO::getDeleted, NOT_DELETED)
                        .eq(ChannelCapabilityCurrencyDO::getCapabilityId, capability.getId())
                        .eq(ChannelCapabilityCurrencyDO::getCurrencyStatus, ENABLED)
                        .orderByAsc(ChannelCapabilityCurrencyDO::getCurrencyCode))
                .stream()
                .map(ChannelCapabilityCurrencyDO::getCurrencyCode)
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .distinct()
                .toList();
        Set<String> midCurrencies = parseScopeValues(midConfig.getCurrencyScope());
        if (midCurrencies.isEmpty()) {
            return capabilityCurrencies;
        }
        List<String> result = new ArrayList<>();
        for (String currency : capabilityCurrencies) {
            if (midCurrencies.contains(currency)) {
                result.add(currency);
            }
        }
        return result;
    }

    private Set<String> parseScopeValues(String scope) {
        if (!StringUtils.hasText(scope) || ALL.equalsIgnoreCase(scope.trim())) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : scope.split(SCOPE_SEPARATOR)) {
            String normalized = normalize(item);
            if (StringUtils.hasText(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String resolvePaymentMethod(PaymentCreateCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getPaymentMethod()) ? normalize(commandDTO.getPaymentMethod()) : DEFAULT_PAYMENT_METHOD;
    }

    private Map<String, String> parseMetadata(String metadataValueJson) {
        if (!StringUtils.hasText(metadataValueJson)) {
            return Map.of();
        }
        Map<String, Object> values = JsonUtils.parseObject(metadataValueJson, new TypeReference<Map<String, Object>>() {
        });
        Map<String, String> result = new LinkedHashMap<>();
        if (values == null) {
            return result;
        }
        values.forEach((key, value) -> result.put(key, value == null ? null : String.valueOf(value)));
        return result;
    }

    private record RouteCandidate(ChannelInfoDO channelInfo,
                                  ChannelMidConfigDO midConfig,
                                  ChannelPaymentCapabilityDO capability,
                                  List<String> supportedCurrencies,
                                  String routedCurrency,
                                  boolean edcRequired) {
    }
}
