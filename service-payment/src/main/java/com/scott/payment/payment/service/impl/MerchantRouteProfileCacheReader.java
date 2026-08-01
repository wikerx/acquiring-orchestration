package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.component.db.route.model.MerchantRouteProfile.RouteOption;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRouteProfileCacheReader
 * @date : 2026-08-01 15:35
 * @email : scott_x@163.com
 * @description : 使用五次有界批量主库查询构建 merchant:route 永久快照，禁止把渠道敏感元数据写入 Redis
 * @status : create
 */
@Service
public class MerchantRouteProfileCacheReader {

    /** 数据库未删除标识。 */
    private static final long NOT_DELETED = 0L;

    /** 数据库启用标识。 */
    private static final int ENABLED = 1;

    /** 收单业务类型。 */
    private static final String BUSINESS_ACQUIRING = "ACQUIRING";

    /** 范围字段的全量匹配标识。 */
    private static final String ALL = "ALL";

    /** 商户渠道绑定 Mapper。 */
    private final PaymentMerchantChannelMidBindingMapper midBindingMapper;

    /** 渠道 MID Mapper。 */
    private final PaymentChannelMidConfigMapper midConfigMapper;

    /** 渠道基础信息 Mapper。 */
    private final PaymentChannelInfoMapper channelInfoMapper;

    /** 渠道支付能力 Mapper。 */
    private final PaymentChannelPaymentCapabilityMapper capabilityMapper;

    /** 渠道能力币种 Mapper。 */
    private final PaymentChannelCapabilityCurrencyMapper capabilityCurrencyMapper;

    /**
     * 创建商户路由快照读取器。
     */
    public MerchantRouteProfileCacheReader(PaymentMerchantChannelMidBindingMapper midBindingMapper,
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
     * 读取永久快照，未命中时从主库构建。
     *
     * <p>永久配置重建固定读取 MASTER，避免从库延迟把旧路由再次写回 Redis。</p>
     */
    @DS(DataSourceName.MASTER)
    @Cacheable(cacheNames = PaymentCacheNames.MERCHANT_ROUTE, key = "#p0")
    public MerchantRouteProfile findCached(String merchantId) {
        return load(merchantId);
    }

    /** 失效 pending 或门禁未知时绕过 Redis 并读取主库。 */
    @DS(DataSourceName.MASTER)
    public MerchantRouteProfile findFresh(String merchantId) {
        return load(merchantId);
    }

    /** 使用批量查询组装不含渠道凭据的商户路由快照。 */
    private MerchantRouteProfile load(String merchantId) {
        MerchantRouteProfile profile = new MerchantRouteProfile();
        profile.setMerchantId(merchantId);
        List<MerchantChannelMidBindingDO> bindings = safeList(midBindingMapper.selectList(
                Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                        .eq(MerchantChannelMidBindingDO::getMerchantId, merchantId)
                        .eq(MerchantChannelMidBindingDO::getBindingStatus, ENABLED)
                        .eq(MerchantChannelMidBindingDO::getDeleted, NOT_DELETED)
                        .orderByAsc(MerchantChannelMidBindingDO::getId)
        ));
        profile.setBindingCount(bindings.size());
        if (bindings.isEmpty()) {
            return profile;
        }

        Set<Long> midIds = ids(bindings.stream().map(MerchantChannelMidBindingDO::getMidConfigId).toList());
        if (midIds.isEmpty()) {
            return profile;
        }
        List<ChannelMidConfigDO> mids = safeList(midConfigMapper.selectList(
                Wrappers.<ChannelMidConfigDO>lambdaQuery()
                        .in(ChannelMidConfigDO::getId, midIds)
                        .eq(ChannelMidConfigDO::getMidStatus, ENABLED)
                        .eq(ChannelMidConfigDO::getDeleted, NOT_DELETED)
        ));
        Map<Long, ChannelMidConfigDO> midMap = index(mids, ChannelMidConfigDO::getId);
        Set<Long> channelIds = ids(mids.stream().map(ChannelMidConfigDO::getChannelId).toList());
        if (channelIds.isEmpty()) {
            return profile;
        }

        List<ChannelInfoDO> channels = safeList(channelInfoMapper.selectList(
                Wrappers.<ChannelInfoDO>lambdaQuery()
                        .in(ChannelInfoDO::getId, channelIds)
                        .eq(ChannelInfoDO::getChannelStatus, ENABLED)
                        .eq(ChannelInfoDO::getSupportAcquiring, ENABLED)
                        .eq(ChannelInfoDO::getDeleted, NOT_DELETED)
        ));
        Map<Long, ChannelInfoDO> channelMap = index(channels, ChannelInfoDO::getId);
        if (channelMap.isEmpty()) {
            return profile;
        }

        List<ChannelPaymentCapabilityDO> capabilities = safeList(capabilityMapper.selectList(
                Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                        .in(ChannelPaymentCapabilityDO::getChannelId, channelMap.keySet())
                        .eq(ChannelPaymentCapabilityDO::getBusinessType, BUSINESS_ACQUIRING)
                        .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED)
                        .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                        .orderByAsc(ChannelPaymentCapabilityDO::getSortOrder)
                        .orderByAsc(ChannelPaymentCapabilityDO::getId)
        ));
        Set<Long> capabilityIds = ids(capabilities.stream().map(ChannelPaymentCapabilityDO::getId).toList());
        if (capabilityIds.isEmpty()) {
            return profile;
        }

        List<ChannelCapabilityCurrencyDO> currencyRows = safeList(capabilityCurrencyMapper.selectList(
                Wrappers.<ChannelCapabilityCurrencyDO>lambdaQuery()
                        .in(ChannelCapabilityCurrencyDO::getCapabilityId, capabilityIds)
                        .eq(ChannelCapabilityCurrencyDO::getCurrencyStatus, ENABLED)
                        .eq(ChannelCapabilityCurrencyDO::getDeleted, NOT_DELETED)
                        .orderByAsc(ChannelCapabilityCurrencyDO::getCurrencyCode)
        ));
        Map<Long, List<String>> currenciesByCapability = currencyRows.stream()
                .filter(row -> row.getCapabilityId() != null && StringUtils.hasText(row.getCurrencyCode()))
                .collect(Collectors.groupingBy(
                        ChannelCapabilityCurrencyDO::getCapabilityId,
                        Collectors.mapping(row -> normalize(row.getCurrencyCode()), Collectors.toList())
                ));
        Map<Long, List<ChannelPaymentCapabilityDO>> capabilitiesByChannel = capabilities.stream()
                .collect(Collectors.groupingBy(ChannelPaymentCapabilityDO::getChannelId));

        ArrayList<RouteOption> options = new ArrayList<>();
        for (MerchantChannelMidBindingDO binding : bindings) {
            ChannelMidConfigDO mid = midMap.get(binding.getMidConfigId());
            ChannelInfoDO channel = mid == null ? null : channelMap.get(mid.getChannelId());
            if (mid == null || channel == null || !BUSINESS_ACQUIRING.equals(normalize(mid.getBusinessType()))) {
                continue;
            }
            for (ChannelPaymentCapabilityDO capability
                    : capabilitiesByChannel.getOrDefault(channel.getId(), List.of())) {
                List<String> supportedCurrencies = intersectCurrencies(
                        currenciesByCapability.getOrDefault(capability.getId(), List.of()),
                        mid.getCurrencyScope()
                );
                if (!supportedCurrencies.isEmpty()) {
                    options.add(toOption(binding, mid, channel, capability, supportedCurrencies));
                }
            }
        }
        profile.setRouteOptions(options);
        return profile;
    }

    /** 将五张表的非敏感字段展平为稳定缓存对象。 */
    private RouteOption toOption(MerchantChannelMidBindingDO binding,
                                 ChannelMidConfigDO mid,
                                 ChannelInfoDO channel,
                                 ChannelPaymentCapabilityDO capability,
                                 List<String> supportedCurrencies) {
        RouteOption option = new RouteOption();
        option.setBindingId(binding.getId());
        option.setBindingStatus(binding.getBindingStatus());
        option.setBindingEffectiveTime(binding.getEffectiveTime());
        option.setBindingExpireTime(binding.getExpireTime());
        option.setMidConfigId(mid.getId());
        option.setChannelMid(mid.getChannelMid());
        option.setBusinessType(mid.getBusinessType());
        option.setPaymentMethodScope(mid.getPaymentMethodScope());
        option.setTransactionTypeScope(mid.getTransactionTypeScope());
        option.setCurrencyScope(mid.getCurrencyScope());
        option.setAllowedCountryScope(mid.getAllowedCountryScope());
        option.setMidStatus(mid.getMidStatus());
        option.setMidEffectiveTime(mid.getEffectiveTime());
        option.setMidExpireTime(mid.getExpireTime());
        option.setMidModifiedTime(mid.getUpdateTime());
        option.setChannelId(channel.getId());
        option.setChannelCode(channel.getChannelCode());
        option.setChannelStatus(channel.getChannelStatus());
        option.setSupportAcquiring(channel.getSupportAcquiring());
        option.setRequestUrl(channel.getDefaultRequestUrl());
        option.setConnectTimeoutSeconds(channel.getConnectTimeoutSeconds());
        option.setReadTimeoutSeconds(channel.getReadTimeoutSeconds());
        option.setCapabilityId(capability.getId());
        option.setCapabilityBusinessType(capability.getBusinessType());
        option.setCapabilityPaymentMethod(capability.getPaymentMethod());
        option.setCapabilityTransactionType(capability.getTransactionType());
        option.setCapabilityStatus(capability.getCapabilityStatus());
        option.setCapabilitySortOrder(capability.getSortOrder());
        option.setSupportedCurrencies(new ArrayList<>(supportedCurrencies));
        return option;
    }

    /** 计算能力币种与 MID 币种范围的稳定交集。 */
    private List<String> intersectCurrencies(List<String> capabilityCurrencies, String midScope) {
        ArrayList<String> normalizedCurrencies = capabilityCurrencies.stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        Set<String> midCurrencies = scopeValues(midScope);
        if (midCurrencies.isEmpty()) {
            return normalizedCurrencies;
        }
        normalizedCurrencies.removeIf(currency -> !midCurrencies.contains(currency));
        return normalizedCurrencies;
    }

    /** 将逗号分隔范围转换为大写集合；空值和 ALL 表示不限制。 */
    private Set<String> scopeValues(String scope) {
        if (!StringUtils.hasText(scope) || ALL.equalsIgnoreCase(scope.trim())) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String item : scope.split(",")) {
            String normalized = normalize(item);
            if (StringUtils.hasText(normalized)) {
                values.add(normalized);
            }
        }
        return values;
    }

    /** 过滤空主键并保持数据库顺序。 */
    private Set<Long> ids(List<Long> source) {
        return source.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 按非空主键建立索引，重复记录保留第一条。 */
    private <T> Map<Long, T> index(List<T> rows, Function<T, Long> idExtractor) {
        return rows.stream().filter(row -> idExtractor.apply(row) != null)
                .collect(Collectors.toMap(idExtractor, Function.identity(), (left, right) -> left));
    }

    /** 兼容 Mapper 测试替身或异常实现返回 null。 */
    private <T> List<T> safeList(List<T> source) {
        return source == null ? List.of() : source;
    }

    /** 使用固定 Locale 规范化渠道和币种编码。 */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
