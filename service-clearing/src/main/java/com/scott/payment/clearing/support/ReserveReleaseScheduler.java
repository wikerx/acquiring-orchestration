package com.scott.payment.clearing.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReserveReleaseScheduler
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 随清分服务自动启动的保证金到期释放调度器；无 yml 或 Nacos 业务启停开关。
 * @status : update
 */
@Slf4j
@Component
public class ReserveReleaseScheduler {

    /**
     * 初始延迟毫秒数常量，统一 {@code ReserveReleaseScheduler} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long INITIAL_DELAY_MILLIS = 30_000L;
    /**
     * {@code FIXED_DELAY_MILLIS}常量，统一 {@code ReserveReleaseScheduler} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
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
