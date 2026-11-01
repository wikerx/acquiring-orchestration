package com.scott.payment.clearing.dto;

/**
 * fee:version Redis 不可变配置值，使用 payloadHash 检测缓存损坏或跨版本错写。
 * @param configuration 不可变费用版本配置
 * @param payloadHash 规范化配置 JSON 的 SHA-256
 */
public record FeeVersionCacheEntryDTO(FeeVersionConfigurationDTO configuration,
                                      String payloadHash) {
}
