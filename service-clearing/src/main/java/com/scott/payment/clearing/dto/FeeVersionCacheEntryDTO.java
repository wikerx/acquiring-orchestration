package com.scott.payment.clearing.dto;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeVersionCacheEntryDTO
 * @date : 2026-08-26 09:55
 * @email : scott_x@163.com
 * @description : fee:version Redis 不可变配置值，使用 payloadHash 检测缓存损坏或跨版本错写。
 * @status : create
 * @param configuration 不可变费用版本配置
 * @param payloadHash 规范化配置 JSON 的 SHA-256
 */
public record FeeVersionCacheEntryDTO(FeeVersionConfigurationDTO configuration,
                                      String payloadHash) {
}
