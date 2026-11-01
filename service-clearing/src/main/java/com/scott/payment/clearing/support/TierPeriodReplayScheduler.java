package com.scott.payment.clearing.support;

import com.scott.payment.clearing.service.TierPeriodReplayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 阶梯费用期间重放调度入口。
 *
 * <p>每轮只领取有限数量的已复核任务，具体顺序、版本门禁和游标推进由重放服务在短事务中保证。</p>
 */
@Slf4j
@Component
public class TierPeriodReplayScheduler {

    /**
     * 单轮最多推进的重放任务数，控制锁竞争和故障影响范围。
     */
    private static final int SCAN_LIMIT = 20;
    private final TierPeriodReplayService replayService;
    private final Clock clock;

    /**
     * 创建期间重放调度器，生产环境统一使用 UTC 系统时钟。
     */
    @Autowired
    public TierPeriodReplayScheduler(TierPeriodReplayService replayService) {
        this(replayService, Clock.systemUTC());
    }

    /**
     * 创建可注入时钟的期间重放调度器，供测试稳定验证到期任务领取。
     */
    TierPeriodReplayScheduler(TierPeriodReplayService replayService, Clock clock) {
        this.replayService = replayService;
        this.clock = clock;
    }

    /**
     * 按配置节奏推进到期重放；周期参数只控制扫描压力，业务失败仍由控制表和下个周期恢复。
     */
    @Scheduled(
            initialDelayString = "${clearing.tier-period-replay.initial-delay-ms:5000}",
            fixedDelayString = "${clearing.tier-period-replay.fixed-delay-ms:5000}")
    public void run() {
        try {
            replayService.runDue(SCAN_LIMIT, clock.instant());
        } catch (RuntimeException exception) {
            log.error("阶梯费用期间重放扫描失败, event: CLEARING_TIER_REPLAY_SCAN_FAILED, exceptionType: {}",
                    exception.getClass().getSimpleName(), exception);
        }
    }
}
