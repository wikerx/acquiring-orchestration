package com.scott.payment.clearing.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingOperationalMetricsScheduler
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 随服务自动启动的清分运维指标调度器；异常只记录固定类型并等待下一轮刷新。
 * @status : update
 */
@Slf4j
@Component
public class ClearingOperationalMetricsScheduler {

    private final ClearingOperationalMetricsRefreshService refreshService;

    public ClearingOperationalMetricsScheduler(ClearingOperationalMetricsRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    /**
     * 定期刷新全部已发布季度的清分 Gauge。
     *
     * <p>刷新失败仅记录低敏日志并保留上一轮指标，不推进任何清分状态。</p>
     */
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
