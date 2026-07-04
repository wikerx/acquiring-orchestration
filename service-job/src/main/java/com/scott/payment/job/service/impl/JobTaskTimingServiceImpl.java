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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskTimingServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Task Timing Service Impl，位于 service-job 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class JobTaskTimingServiceImpl implements JobTaskTimingService {

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param task 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param referenceAt 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
