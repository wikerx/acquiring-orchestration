package com.scott.payment.job.service;

import com.scott.payment.job.entity.SysJobTaskDO;

import java.time.LocalDateTime;

/**
 * 任务任务时间计算服务接口
 */
public interface JobTaskTimingService {

    /**
     * 计算下次触发时间。
     *
     * @param task        任务定义
     * @param referenceAt 参考时间
     * @return 下次触发时间
     */
    LocalDateTime calculateNextTriggerTime(SysJobTaskDO task, LocalDateTime referenceAt);
}
