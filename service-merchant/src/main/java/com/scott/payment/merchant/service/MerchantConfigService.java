package com.scott.payment.merchant.service;

import java.util.Optional;

/**
 * 商户系统只读参数服务，位于 service-merchant 服务层，用于读取平台公共参数并避免商户服务重复维护配置项。
 */
public interface MerchantConfigService {

    /**
     * 查询启用且未删除的系统参数值。
     *
     * @param configKey 参数键名
     * @return 参数值；不存在、停用或空值时返回空
     */
    Optional<String> enabledConfigValue(String configKey);
}
