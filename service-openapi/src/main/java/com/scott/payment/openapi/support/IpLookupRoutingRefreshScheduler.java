package com.scott.payment.openapi.support;

import com.scott.payment.component.db.reference.cache.IpShardRoutingSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLookupRoutingRefreshScheduler
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : OpenAPI IP 分片路由预加载与轮询器；缓存按路由版本隔离并自然过期，不执行全局删除
 * @status : create
 */
@Slf4j
@Component
public class IpLookupRoutingRefreshScheduler implements ApplicationRunner {

    private final IpShardRoutingSnapshot routingSnapshot;
    private final AtomicLong lastWarningMillis = new AtomicLong();

    public IpLookupRoutingRefreshScheduler(IpShardRoutingSnapshot routingSnapshot) {
        this.routingSnapshot = routingSnapshot;
    }

    /** 在应用进入就绪状态前加载路由，避免首个冷 IP 多执行一次路由 SQL。 */
    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    /**
     * 周期读取 SLAVE 路由版本；查询缓存使用版本化 Key，旧版本 Key 按物理 TTL 自动回收。
     */
    @Scheduled(fixedDelayString = "${payment.ip.lookup.route-refresh-ms:60000}")
    public void refresh() {
        try {
            if (routingSnapshot.refresh()) {
                log.info("event: IP_LOOKUP_ROUTING_REFRESHED");
            }
        } catch (RuntimeException exception) {
            long now = System.currentTimeMillis();
            long previous = lastWarningMillis.get();
            if (now - previous >= 60_000 && lastWarningMillis.compareAndSet(previous, now)) {
                log.warn("event: IP_LOOKUP_ROUTING_REFRESH_FAILED exceptionType: {}",
                        exception.getClass().getSimpleName(), exception);
            }
        }
    }
}
