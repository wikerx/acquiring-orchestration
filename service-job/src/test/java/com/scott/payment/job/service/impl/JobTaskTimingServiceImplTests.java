package com.scott.payment.job.service.impl;

import com.scott.payment.component.job.enums.JobMisfireStrategyEnum;
import com.scott.payment.component.job.enums.JobStatusEnum;
import com.scott.payment.job.entity.SysJobTaskDO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskTimingServiceImplTests
 * @date : 2026-08-21 11:24
 * @email : scott_x@163.com
 * @description : 校验定时任务完成一次到期补偿后始终推进到未来 Cron 时刻，避免扫描延迟导致同一触发点重复执行。
 * @status : create
 */
@Slf4j
class JobTaskTimingServiceImplTests {

    /** 扫描晚到不足一秒时，FIRE_ONCE 也必须跳过已经触发的当前 Cron 时刻。 */
    @Test
    void fireOnceShouldAdvancePastCurrentCronOccurrenceWhenScannerRunsLate() {
        SysJobTaskDO task = new SysJobTaskDO();
        task.setStatus(JobStatusEnum.ENABLED.name());
        task.setCronExpression("0 */5 * * * ?");
        task.setMisfireStrategy(JobMisfireStrategyEnum.FIRE_ONCE.name());
        task.setNextTriggerTime(LocalDateTime.of(2026, 8, 21, 11, 20));
        LocalDateTime actualTriggerTime = LocalDateTime.of(2026, 8, 21, 11, 20, 0, 900_000_000);

        LocalDateTime nextTriggerTime = new JobTaskTimingServiceImpl()
                .calculateNextTriggerTime(task, actualTriggerTime);

        log.info("测试FIRE_ONCE晚到扫描：计划触发时间={}, 实际触发时间={}, 下一触发时间={}",
                task.getNextTriggerTime(), actualTriggerTime, nextTriggerTime);
        assertThat(nextTriggerTime).isEqualTo(LocalDateTime.of(2026, 8, 21, 11, 25));
    }
}
