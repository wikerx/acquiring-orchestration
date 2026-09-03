package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.db.systemconfig.service.SystemConfigReadService;
import com.scott.payment.merchant.service.MerchantConfigService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantConfigServiceImpl
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户配置服务实现，位于 商户后台服务，执行该业务的规则校验和数据读写，并保持现有事务与异常边界。
 * @status : create
 */
@Service
public class MerchantConfigServiceImpl implements MerchantConfigService {

    /** 跨服务统一系统参数读取服务。 */
    private final SystemConfigReadService systemConfigReadService;

    /**
     * 创建商户端只读参数服务。
     *
     * @param systemConfigReadService 跨服务统一系统参数读取服务
     */
    public MerchantConfigServiceImpl(SystemConfigReadService systemConfigReadService) {
        this.systemConfigReadService = systemConfigReadService;
    }

    /**
     * 查询启用且未删除的平台公开参数值。
     *
     * <p>缓存、pending 门禁和主库降级由公共读取服务统一处理，商户服务不再维护独立缓存。</p>
     *
     * @param configKey 参数键名
     * @return 参数值；不存在、停用或空值时返回空
     */
    @Override
    public Optional<String> enabledConfigValue(String configKey) {
        return systemConfigReadService.findEnabledValue(configKey);
    }
}
