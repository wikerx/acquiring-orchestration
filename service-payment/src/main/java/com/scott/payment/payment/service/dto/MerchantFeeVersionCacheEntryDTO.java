package com.scott.payment.payment.service.dto;

/**
 * fee:version 不可变 Redis 值，使用规范化配置摘要检测损坏或错写，不包含动作时间、汇率和持卡人信息。
 */
public record MerchantFeeVersionCacheEntryDTO(MerchantFeeVersionConfigurationDTO configuration,
                                              String payloadHash) {
}
