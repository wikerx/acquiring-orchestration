package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.channel.ChannelDTOs.MidPaymentCapability;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCardBrandDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCurrencyDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelMidConfigDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelPaymentCapabilityDO;
import com.scott.payment.admin.mapper.ChannelCapabilityCardBrandMapper;
import com.scott.payment.admin.mapper.ChannelCapabilityCurrencyMapper;
import com.scott.payment.admin.mapper.ChannelPaymentCapabilityMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 批量读取管理端 MID 详情和路由候选共用的收单能力。 */
@Component
class AcquiringMidCapabilityProjection {
    private final ChannelPaymentCapabilityMapper capabilities;
    private final ChannelCapabilityCurrencyMapper currencies;
    private final ChannelCapabilityCardBrandMapper brands;

    AcquiringMidCapabilityProjection(ChannelPaymentCapabilityMapper capabilities,
                                     ChannelCapabilityCurrencyMapper currencies,
                                     ChannelCapabilityCardBrandMapper brands) {
        this.capabilities = capabilities;
        this.currencies = currencies;
        this.brands = brands;
    }

    Snapshot load(Collection<Long> channelIds) {
        if (channelIds.isEmpty()) return new Snapshot(Map.of(), Map.of(), Map.of());
        List<ChannelPaymentCapabilityDO> rows = capabilities.selectList(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .in(ChannelPaymentCapabilityDO::getChannelId, channelIds)
                .eq(ChannelPaymentCapabilityDO::getBusinessType, "ACQUIRING")
                .eq(ChannelPaymentCapabilityDO::getDeleted, 0L)
                .orderByAsc(ChannelPaymentCapabilityDO::getSortOrder, ChannelPaymentCapabilityDO::getId));
        if (rows.isEmpty()) return new Snapshot(Map.of(), Map.of(), Map.of());
        List<Long> ids = rows.stream().map(ChannelPaymentCapabilityDO::getId).toList();
        var currencyRows = currencies.selectList(Wrappers.<ChannelCapabilityCurrencyDO>lambdaQuery()
                .in(ChannelCapabilityCurrencyDO::getCapabilityId, ids)
                .eq(ChannelCapabilityCurrencyDO::getDeleted, 0L)
                .eq(ChannelCapabilityCurrencyDO::getCurrencyStatus, 1)
                .orderByAsc(ChannelCapabilityCurrencyDO::getCurrencyCode));
        var brandRows = brands.selectList(Wrappers.<ChannelCapabilityCardBrandDO>lambdaQuery()
                .in(ChannelCapabilityCardBrandDO::getCapabilityId, ids)
                .eq(ChannelCapabilityCardBrandDO::getDeleted, 0L)
                .eq(ChannelCapabilityCardBrandDO::getBrandStatus, 1)
                .orderByAsc(ChannelCapabilityCardBrandDO::getSortOrder));
        return new Snapshot(rows.stream().collect(Collectors.groupingBy(ChannelPaymentCapabilityDO::getChannelId)),
                currencyRows.stream().collect(Collectors.groupingBy(ChannelCapabilityCurrencyDO::getCapabilityId,
                        Collectors.mapping(ChannelCapabilityCurrencyDO::getCurrencyCode, Collectors.toList()))),
                brandRows.stream().collect(Collectors.groupingBy(ChannelCapabilityCardBrandDO::getCapabilityId,
                        Collectors.mapping(ChannelCapabilityCardBrandDO::getCardBrand, Collectors.toList()))));
    }

    record Snapshot(Map<Long, List<ChannelPaymentCapabilityDO>> byChannel,
                    Map<Long, List<String>> currencies, Map<Long, List<String>> brands) {
        List<MidPaymentCapability> describe(ChannelMidConfigDO mid, String paymentMethod) {
            return byChannel.getOrDefault(mid.getChannelId(), List.of()).stream()
                    .filter(row -> !StringUtils.hasText(paymentMethod) || paymentMethod.equals(row.getPaymentMethod()))
                    .filter(row -> allows(mid.getPaymentMethodScope(), row.getPaymentMethod()))
                    .filter(row -> !independentCard(row) || allows(mid.getCardBrandScope(), row.getCardBrand()))
                    .map(row -> describe(mid, row)).toList();
        }

        private MidPaymentCapability describe(ChannelMidConfigDO mid, ChannelPaymentCapabilityDO row) {
            String method = row.getPaymentMethod();
            String currency = Integer.valueOf(1).equals(mid.getInheritTransactionCurrency())
                    ? row.getDefaultTransactionCurrency() : mid.getDefaultTransactionCurrency();
            List<String> effectiveCurrencies = currencies.getOrDefault(row.getId(), List.of()).stream()
                    .filter(code -> allows(mid.getCurrencyScope(), code)).toList();
            List<String> effectiveBrands = independentCard(row) ? List.of(row.getCardBrand())
                    : brands.getOrDefault(row.getId(), List.of()).stream()
                            .filter(code -> allows(mid.getCardBrandScope(), code)).toList();
            List<String> transactionTypes = codes(row.getTransactionType()).stream()
                    .filter(type -> allows(mid.getTransactionTypeScope(), type))
                    .filter(type -> !"INCREMENTAL_AUTHORIZATION".equals(type)
                            || Integer.valueOf(1).equals(row.getSupportIncrementalAuthorization()))
                    .toList();
            return new MidPaymentCapability(method, effectiveBrands, transactionTypes, effectiveCurrencies,
                    effectiveCurrencies.contains(currency) ? currency : null,
                    StringUtils.hasText(row.getAllowedCountryScope()) ? row.getAllowedCountryScope() : "ALL",
                    mid.getAllowedCountryScope(), Integer.valueOf(1).equals(row.getCapabilityStatus())
                            && !effectiveCurrencies.isEmpty() && !transactionTypes.isEmpty()
                            && (!"BANK_CARD".equals(method) || !effectiveBrands.isEmpty()));
        }

        private static boolean independentCard(ChannelPaymentCapabilityDO row) {
            return "BANK_CARD".equals(row.getPaymentMethod()) && StringUtils.hasText(row.getCardBrand())
                    && !"NONE".equals(row.getCardBrand());
        }

        static boolean includesBrand(MidPaymentCapability capability, String brand) {
            return !"BANK_CARD".equals(capability.paymentMethod()) || !StringUtils.hasText(brand)
                    || "ALL".equals(brand) || capability.cardBrands().contains(brand);
        }

        private static boolean allows(String scope, String value) {
            return !StringUtils.hasText(scope) || "ALL".equalsIgnoreCase(scope) || codes(scope).contains(value);
        }

        private static List<String> codes(String scope) {
            if (!StringUtils.hasText(scope) || "NONE".equalsIgnoreCase(scope)) return List.of();
            return Arrays.stream(scope.split(",")).map(String::trim).filter(StringUtils::hasText).distinct().toList();
        }
    }
}
