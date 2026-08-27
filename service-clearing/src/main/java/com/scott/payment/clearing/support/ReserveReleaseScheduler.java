package com.scott.payment.clearing.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 随清分服务自动启动的保证金到期释放调度器；无 yml 或 Nacos 业务启停开关。 */
@Slf4j
@Component
public class ReserveReleaseScheduler {

    private static final long INITIAL_DELAY_MILLIS = 30_000L;
    private static final long FIXED_DELAY_MILLIS = 60_000L;

    private final ReserveReleaseScanService scanService;

    public ReserveReleaseScheduler(ReserveReleaseScanService scanService) {
        this.scanService = scanService;
    }

    /** 每轮完成后等待固定一分钟，再扫描所有已发布季度的到期候选。 */
    @Scheduled(initialDelay = INITIAL_DELAY_MILLIS, fixedDelay = FIXED_DELAY_MILLIS)
    public void run() {
        try {
            ReserveReleaseScanService.ReserveReleaseScanResult result = scanService.scan();
            if (result != null && (result.released() > 0 || result.failed() > 0)) {
                log.info("event: RESERVE_RELEASE_SCAN_COMPLETED scanned: {} released: {} skipped: {} failed: {}",
                        result.scanned(), result.released(), result.skipped(), result.failed());
            }
        } catch (RuntimeException exception) {
            log.error("event: RESERVE_RELEASE_SCAN_FAILED exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
