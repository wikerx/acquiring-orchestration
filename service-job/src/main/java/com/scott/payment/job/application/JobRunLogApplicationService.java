package com.scott.payment.job.application;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.job.api.internal.dto.JobRunLogQueryRequest;
import com.scott.payment.job.api.internal.dto.JobRunLogResponse;
import com.scott.payment.job.converter.JobSchedulerConverter;
import com.scott.payment.job.entity.SysJobRunLogDO;
import com.scott.payment.job.service.JobRunLogService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogApplicationService
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务运行日志应用服务
 * @status : create
 */

@Service
public class JobRunLogApplicationService {

    private final JobRunLogService jobRunLogService;

    /**
     * 创建执行日志应用服务。
     *
     * @param jobRunLogService 执行日志领域服务
     */
    public JobRunLogApplicationService(JobRunLogService jobRunLogService) {
        this.jobRunLogService = jobRunLogService;
    }

    /**
     * 分页查询执行日志。
     *
     * @param request 查询条件
     * @return 日志分页结果
     */
    public PageResult<JobRunLogResponse> pageLogs(JobRunLogQueryRequest request) {
        PageResult<SysJobRunLogDO> pageResult = jobRunLogService.pageLogs(request);
        return PageResult.of(
                pageResult.getTotal(),
                pageResult.getPageNo(),
                pageResult.getPageSize(),
                pageResult.getRecords().stream()
                        .map(JobSchedulerConverter.INSTANCE::toRunLogResponse)
                        .toList()
        );
    }
}
