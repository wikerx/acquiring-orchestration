package com.scott.payment.admin.support;

import com.scott.payment.component.db.reference.cache.IpShardRoutingSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 管理端 IP 分片路由预加载与轮询器，确保与 OpenAPI 共享相同的数据版本判断
 */
@Slf4j
@Component
public class IpLookupRoutingRefreshScheduler implements ApplicationRunner {

    private final IpShardRoutingSnapshot routingSnapshot;
    private final AtomicLong lastWarningMillis = new AtomicLong();

    /**
     * 创建 IP 地址库路由刷新调度器。
     */
    public IpLookupRoutingRefreshScheduler(IpShardRoutingSnapshot routingSnapshot) {
        this.routingSnapshot = routingSnapshot;
    }

    /**
     * 在管理端启动完成前从 SLAVE 预加载路由，避免首次精确查询额外读取路由表。
     */
    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    /**
     * 周期刷新 SLAVE 路由快照；路由或数据版本变化后，共享缓存中的旧版本值会被拒绝并按需覆盖。
     */
    @Scheduled(fixedDelayString = "${payment.ip.lookup.route-refresh-ms:60000}")
    public void refresh() {
        try {
            if (routingSnapshot.refresh()) {
                log.info("IP查询路由刷新完成, event: IP_LOOKUP_ROUTING_REFRESHED, service: admin");
            }
        } catch (RuntimeException exception) {
            long now = System.currentTimeMillis();
            long previous = lastWarningMillis.get();
            if (now - previous >= 60_000 && lastWarningMillis.compareAndSet(previous, now)) {
                log.warn("IP查询路由刷新失败, event: IP_LOOKUP_ROUTING_REFRESH_FAILED, service: admin, exceptionType: {}",
                        exception.getClass().getSimpleName(), exception);
            }
        }
    }
}
