package com.scott.payment.job.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.job.api.internal.dto.JobRunLogQueryRequest;
import com.scott.payment.job.api.internal.dto.JobRunLogResponse;
import com.scott.payment.job.application.JobRunLogApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogInternalController
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务运行日志内部控制器
 * @status : create
 */

@RestController
@RequestMapping("/internal/job/logs")
public class JobRunLogInternalController {

    private final JobRunLogApplicationService jobRunLogApplicationService;

    /**
     * 创建内部执行日志控制器。
     *
     * @param jobRunLogApplicationService 执行日志应用服务
     */
    public JobRunLogInternalController(JobRunLogApplicationService jobRunLogApplicationService) {
        this.jobRunLogApplicationService = jobRunLogApplicationService;
    }

    /**
     * 分页查询执行日志。
     *
     * @param request 查询条件
     * @return 分页日志结果
     */
    @PostMapping("/search")
    public CommonResult<PageResult<JobRunLogResponse>> pageLogs(@RequestBody(required = false) @Valid JobRunLogQueryRequest request) {
        return success(jobRunLogApplicationService.pageLogs(request));
    }
}
