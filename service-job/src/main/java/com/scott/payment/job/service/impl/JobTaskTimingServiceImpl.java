package com.scott.payment.job.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.enums.JobMisfireStrategyEnum;
import com.scott.payment.component.job.enums.JobStatusEnum;
import com.scott.payment.job.entity.SysJobTaskDO;
import com.scott.payment.job.service.JobTaskTimingService;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskTimingServiceImpl
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务任务时间计算服务实现
 * @status : create
 */
@Service
public class JobTaskTimingServiceImpl implements JobTaskTimingService {

    /**
     * 根据任务状态、Cron 和错过执行策略计算下一触发时间。
     * <p>
     * 禁用任务或未配置 Cron 时不再调度；FIRE_ONCE 且旧触发点已过期时以当前时间前一秒为
     * 基线，使 Cron 立即给出下一次可执行时刻。
     * </p>
     *
     * @param task        任务定义
     * @param referenceAt 调度计算基准时间
     * @return 下一触发时间；任务不可调度时返回 {@code null}
     * @throws ServiceException Cron 表达式非法时抛出
     */
    @Override
    public LocalDateTime calculateNextTriggerTime(SysJobTaskDO task, LocalDateTime referenceAt) {
        if (task == null || !JobStatusEnum.ENABLED.name().equals(task.getStatus())) {
            return null;
        }
        if (!StringUtils.hasText(task.getCronExpression())) {
            return null;
        }
        CronExpression cronExpression;
        try {
            cronExpression = CronExpression.parse(task.getCronExpression());
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "invalid cron expression");
        }
        LocalDateTime baseline = referenceAt;
        if (JobMisfireStrategyEnum.FIRE_ONCE.name().equals(task.getMisfireStrategy())
                && task.getNextTriggerTime() != null
                && task.getNextTriggerTime().isBefore(referenceAt)) {
            baseline = referenceAt.minusSeconds(1);
        }
        return cronExpression.next(baseline);
    }
}
