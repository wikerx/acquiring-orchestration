package com.scott.payment.clearing.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 保证金到期释放调度入口。
 *
 * <p>调度器只负责周期触发和汇总日志，不持有资金事务，也不直接修改保证金状态。</p>
 */
@Slf4j
@Component
public class ReserveReleaseScheduler {

    /** 服务启动后等待 30 秒，避免与启动期数据库初始化争用资源。 */
    private static final long INITIAL_DELAY_MILLIS = 30_000L;
    /** 每轮完成后等待一分钟再扫描，避免慢任务并发重入。 */
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
                log.info("到期保证金扫描完成, event: RESERVE_RELEASE_SCAN_COMPLETED, scanned: {}, released: {}, skipped: {}, failed: {}",
                        result.scanned(), result.released(), result.skipped(), result.failed());
            }
        } catch (RuntimeException exception) {
            log.error("到期保证金扫描失败, event: RESERVE_RELEASE_SCAN_FAILED, exceptionType: {}",
                    exception.getClass().getSimpleName(), exception);
        }
    }
}
