package com.scott.payment.merchant.service;

import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantConfigService
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户系统只读参数服务，位于 service-merchant 服务层，用于读取平台公共参数并避免商户服务重复维护配置项。
 * @status : create
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
