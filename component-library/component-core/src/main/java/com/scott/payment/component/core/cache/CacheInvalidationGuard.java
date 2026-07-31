package com.scott.payment.component.core.cache;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CacheInvalidationGuard
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : 公共组件层永久缓存失效门禁契约，向管理端写链路和业务读取端暴露窄化的租约协调能力
 * @status : create
 */
public interface CacheInvalidationGuard {

    /**
     * 获取指定业务缓存 Key 的失效门禁。
     *
     * @param cacheName   Spring Cache 名称
     * @param businessKey 业务缓存 Key
     * @param ttl         门禁最长持有时间
     * @return 门禁持有凭证
     */
    CacheInvalidationLease acquire(String cacheName, String businessKey, Duration ttl);

    /**
     * 判断指定业务缓存 Key 是否正在失效。
     *
     * @param cacheName   Spring Cache 名称
     * @param businessKey 业务缓存 Key
     * @return true 表示读取必须绕过旧缓存
     */
    boolean isPending(String cacheName, String businessKey);

    /**
     * 仅由当前持有者释放失效门禁。
     *
     * @param lease 门禁持有凭证
     * @return true 表示释放成功；false 表示门禁已不存在或已由其他持有者接管
     */
    boolean release(CacheInvalidationLease lease);
}
