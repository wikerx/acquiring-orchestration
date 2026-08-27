package com.scott.payment.clearing.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 随服务自动启动的清分运维指标调度器；异常只记录固定类型并等待下一轮刷新。 */
@Slf4j
@Component
public class ClearingOperationalMetricsScheduler {

    private final ClearingOperationalMetricsRefreshService refreshService;

    public ClearingOperationalMetricsScheduler(ClearingOperationalMetricsRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    /** 定期刷新全部已发布季度的清分 Gauge。 */
    @Scheduled(
            initialDelayString = "${clearing.metrics.initial-delay-ms:30000}",
            fixedDelayString = "${clearing.metrics.fixed-delay-ms:60000}")
    public void refresh() {
        try {
            refreshService.refresh();
        } catch (RuntimeException exception) {
            log.error("event: CLEARING_OPERATIONAL_METRICS_REFRESH_FAILED exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
