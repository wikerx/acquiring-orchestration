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
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiAccessPolicyCacheReader
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 访问策略读取器，为 facade 提供可代理的缓存与直读入口。
 * @status : create
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
     * 绕过方法级缓存读取当前数据源中的最新业务值。
     * <p>
     * 只读操作；实现必须沿用 商户开放接口服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param merchantId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @DS(DataSourceName.MASTER)
    public MerchantOpenApiAccessPolicy findFresh(String merchantId) {
        return load(merchantId);
    }

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
