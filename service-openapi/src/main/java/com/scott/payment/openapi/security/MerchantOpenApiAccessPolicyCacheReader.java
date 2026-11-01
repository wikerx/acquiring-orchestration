package com.scott.payment.openapi.security;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.auth.entity.MerchantIpWhitelistDO;
import com.scott.payment.component.db.auth.mapper.MerchantIpWhitelistMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 商户 OpenAPI 访问策略读取器，为 facade 提供可代理的缓存与直读入口。
 */
@Service
public class MerchantOpenApiAccessPolicyCacheReader {

    /**
     * 数据库逻辑未删除标识。
     */
    private static final long NOT_DELETED = 0L;

    /**
     * 数据库启用状态值。
     */
    private static final int ENABLED = 1;

    /**
     * 商户 IP 白名单数据访问组件。
     */
    private final MerchantIpWhitelistMapper whitelistMapper;

    /**
     * 创建商户 OpenAPI 访问策略读取器。
     *
     * @param whitelistMapper    商户 IP 白名单 Mapper
     */
    public MerchantOpenApiAccessPolicyCacheReader(MerchantIpWhitelistMapper whitelistMapper) {
        this.whitelistMapper = whitelistMapper;
    }

    /**
     * 正常状态下读取缓存，未命中时从主库加载。
     *
     * @param merchantId 已规范化的商户号
     * @return 商户访问策略
     */
    @DS(DataSourceName.MASTER)
    @Cacheable(cacheNames = PaymentCacheNames.MERCHANT_OPENAPI_ACCESS, key = "#p0")
    public MerchantOpenApiAccessPolicy findCached(String merchantId) {
        return load(merchantId);
    }

    /**
     * 绕过方法级缓存，从主库读取商户当前生效的 IP 白名单策略。
     *
     * @param merchantId 已规范化的商户号
     * @return 包含当前启用 IP 集合的访问策略；无记录时返回空集合策略
     */
    @DS(DataSourceName.MASTER)
    public MerchantOpenApiAccessPolicy findFresh(String merchantId) {
        return load(merchantId);
    }

    /**
     * 查询已审批、已启用且未删除的商户 IP 白名单记录。
     *
     * @param merchantId 已规范化的商户号
     * @return 去除空白后的允许 IP 集合
     */
    private MerchantOpenApiAccessPolicy load(String merchantId) {
        MerchantOpenApiAccessPolicy policy = new MerchantOpenApiAccessPolicy();
        whitelistMapper.selectList(
                        Wrappers.<MerchantIpWhitelistDO>lambdaQuery()
                                .select(MerchantIpWhitelistDO::getIpValue)
                                .eq(MerchantIpWhitelistDO::getDeleted, NOT_DELETED)
                                .eq(MerchantIpWhitelistDO::getApprovalStatus, ENABLED)
                                .eq(MerchantIpWhitelistDO::getStatus, ENABLED)
                                .eq(MerchantIpWhitelistDO::getMerchantId, merchantId)
                )
                .stream()
                .map(MerchantIpWhitelistDO::getIpValue)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(policy.getAllowedIps()::add);
        return policy;
    }
}
