package com.scott.payment.link.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.link.mapper.PaymentLinkCapabilityMapper;
import com.scott.payment.link.model.PaymentLinkManagementModels.AllowedMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 管理命令直接读取主库，绕过路由缓存的失效窗口；输出只含展示所需信息。 */
@Service
@Transactional(readOnly = true)
public class PaymentLinkDatabaseCapabilities implements PaymentLinkCapabilities {
    private final PaymentLinkCapabilityMapper mapper;
    public PaymentLinkDatabaseCapabilities(PaymentLinkCapabilityMapper mapper) { this.mapper = mapper; }
    /** 从主库读取当前能力并按请求方式、币种及有效时点过滤，不依赖路由缓存。 */
    @Override
    @DS(DataSourceName.MASTER)
    public List<AllowedMethod> resolve(String merchantId, List<AllowedMethod> requested, String currency) {
        return new PaymentLinkCapabilityResolver().resolve(profile(merchantId), requested, currency, LocalDateTime.now());
    }
    /** 在主库只读事务内冻结商户能力及查询时点，后续逐币种计算复用快照。 */
    @Override
    @DS(DataSourceName.MASTER)
    public PaymentLinkCapabilities snapshot(String merchantId) {
        var snapshot = profile(merchantId);
        var now = LocalDateTime.now();
        return (ignored, requested, currency) -> new PaymentLinkCapabilityResolver().resolve(snapshot, requested, currency, now);
    }
    /** Options 在同一只读事务内复用一个商户快照，避免逐币种反复访问数据库。 */
    @DS(DataSourceName.MASTER)
    public MerchantRouteProfile profile(String merchantId) {
        var profile = new MerchantRouteProfile();
        var routes = mapper.routes(merchantId);
        if (routes.isEmpty()) { profile.setRouteOptions(List.of()); return profile; }
        var currencies = group(mapper.currencies(merchantId)); var brands = group(mapper.brands(merchantId));
        for (var route : routes) {
            route.setSupportedCurrencies(currencies.getOrDefault(route.getCapabilityId(), List.of()).stream()
                    .filter(value -> allows(route.getCurrencyScope(), value)).toList());
            route.setCapabilitySupportedCardBrands(brands.getOrDefault(route.getCapabilityId(), List.of()));
        }
        profile.setRouteOptions(routes); return profile;
    }
    private Map<Long, List<String>> group(List<PaymentLinkCapabilityMapper.Value> values) {
        return values.stream().filter(value -> value.getCapabilityId() != null && value.getCode() != null)
                .collect(Collectors.groupingBy(PaymentLinkCapabilityMapper.Value::getCapabilityId,
                        Collectors.mapping(value -> value.getCode().trim().toUpperCase(Locale.ROOT), Collectors.toList())));
    }
    private boolean allows(String scope, String value) {
        return scope == null || scope.isBlank() || "ALL".equalsIgnoreCase(scope.trim())
                || Arrays.stream(scope.split(",")).map(String::trim).anyMatch(value::equalsIgnoreCase);
    }
}
