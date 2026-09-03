package com.scott.payment.clearing.support;

import com.scott.payment.clearing.service.TierPeriodReplayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TierPeriodReplayScheduler
 * @date : 2026-08-26 19:50
 * @email : scott_x@163.com
 * @description : 随服务启动自动推进已复核阶梯期间重放；单次有界扫描且逐项短事务，不依赖 yml 或 Nacos 开关。
 * @status : create
 */
@Slf4j
@Component
public class TierPeriodReplayScheduler {

    /**
     * {@code SCAN_LIMIT}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int SCAN_LIMIT = 20;
    private final TierPeriodReplayService replayService;
    private final Clock clock;

    @Autowired
    public TierPeriodReplayScheduler(TierPeriodReplayService replayService) {
        this(replayService, Clock.systemUTC());
    }

    TierPeriodReplayScheduler(TierPeriodReplayService replayService, Clock clock) {
        this.replayService = replayService;
        this.clock = clock;
    }

    /** 固定节奏推进到期重放；业务失败已落控制表，扫描级异常等待下个周期恢复。 */
    @Scheduled(initialDelay = 5000L, fixedDelay = 5000L)
    public void run() {
        try {
            replayService.runDue(SCAN_LIMIT, clock.instant());
        } catch (RuntimeException exception) {
            log.error("event: CLEARING_TIER_REPLAY_SCAN_FAILED exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
