package com.scott.payment.payment.service.dto;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFeeVersionCacheEntryDTO
 * @date : 2026-08-25 22:45
 * @email : scott_x@163.com
 * @description : fee:version 不可变 Redis 值，使用规范化配置摘要检测损坏或错写，不包含动作时间、汇率和持卡人信息。
 * @status : create
 * @param configuration 不可变费用版本配置
 * @param payloadHash 排除本字段后的规范化配置 JSON SHA-256
 */
public record MerchantFeeVersionCacheEntryDTO(MerchantFeeVersionConfigurationDTO configuration,
                                              String payloadHash) {
}
