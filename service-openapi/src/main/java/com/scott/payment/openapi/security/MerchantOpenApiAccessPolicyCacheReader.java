package com.scott.payment.openapi.security;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.auth.entity.MerchantIpWhitelistDO;
import com.scott.payment.component.db.auth.entity.MerchantOpenApiAccessConfigDO;
import com.scott.payment.component.db.auth.mapper.MerchantIpWhitelistMapper;
import com.scott.payment.component.db.auth.mapper.MerchantOpenApiAccessConfigMapper;
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
     * 商户 OpenAPI 访问配置数据访问组件。
     */
    private final MerchantOpenApiAccessConfigMapper accessConfigMapper;

    /**
     * 商户 IP 白名单数据访问组件。
     */
    private final MerchantIpWhitelistMapper whitelistMapper;

    /**
     * 创建商户 OpenAPI 访问策略读取器。
     *
     * @param accessConfigMapper 商户 OpenAPI 访问配置 Mapper
     * @param whitelistMapper    商户 IP 白名单 Mapper
     */
    public MerchantOpenApiAccessPolicyCacheReader(
            MerchantOpenApiAccessConfigMapper accessConfigMapper,
            MerchantIpWhitelistMapper whitelistMapper) {
        this.accessConfigMapper = accessConfigMapper;
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
     * 失效门禁 pending 或状态未知时绕过缓存并直读主库。
     *
     * @param merchantId 已规范化的商户号
     * @return 商户访问策略
     */
    @DS(DataSourceName.MASTER)
    public MerchantOpenApiAccessPolicy findFresh(String merchantId) {
        return load(merchantId);
    }

    /**
     * 从主库组装商户 IP 白名单策略。
     *
     * <p>访问开关和白名单记录均以数据库为事实来源。白名单未启用时返回显式关闭策略；
     * 启用时仅装载状态正常、未删除且非空的 IP，不读取或缓存商户密钥材料。</p>
     *
     * @param merchantId 已规范化的商户号
     * @return 当前数据库状态对应的访问策略
     */
    private MerchantOpenApiAccessPolicy load(String merchantId) {
        MerchantOpenApiAccessPolicy policy = new MerchantOpenApiAccessPolicy();
        MerchantOpenApiAccessConfigDO config = accessConfigMapper.selectOne(
                Wrappers.<MerchantOpenApiAccessConfigDO>lambdaQuery()
                        .select(MerchantOpenApiAccessConfigDO::getIpWhitelistEnabled)
                        .eq(MerchantOpenApiAccessConfigDO::getDeleted, NOT_DELETED)
                        .eq(MerchantOpenApiAccessConfigDO::getMerchantId, merchantId)
                        .last("LIMIT 1")
        );
        boolean enabled =
                config != null && Integer.valueOf(ENABLED).equals(config.getIpWhitelistEnabled());
        policy.setWhitelistEnabled(enabled);
        if (!enabled) {
            return policy;
        }
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
