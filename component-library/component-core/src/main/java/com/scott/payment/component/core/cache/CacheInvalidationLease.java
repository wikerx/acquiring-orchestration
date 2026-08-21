package com.scott.payment.component.core.cache;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CacheInvalidationLease
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : 公共组件层受管缓存失效租约，只携带精确缓存目标和不可记录日志的门禁持有者 token
 * @status : update
 *
 * @param cacheName   Spring Cache 名称
 * @param businessKey 业务缓存 Key
 * @param token       门禁持有者令牌，属于内部控制数据，不得写入普通日志
 */
public record CacheInvalidationLease(String cacheName, String businessKey, String token) {
}
