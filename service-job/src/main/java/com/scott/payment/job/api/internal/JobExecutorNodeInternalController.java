package com.scott.payment.job.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.job.api.internal.dto.JobExecutorNodeResponse;
import com.scott.payment.job.application.JobExecutorNodeApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeInternalController
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务执行器节点内部控制器
 * @status : create
 */
@RestController
@RequestMapping("/internal/job/nodes")
public class JobExecutorNodeInternalController {

    private final JobExecutorNodeApplicationService jobExecutorNodeApplicationService;

    /**
     * 创建内部节点接口控制器。
     *
     * @param jobExecutorNodeApplicationService 节点应用服务
     */
    public JobExecutorNodeInternalController(JobExecutorNodeApplicationService jobExecutorNodeApplicationService) {
        this.jobExecutorNodeApplicationService = jobExecutorNodeApplicationService;
    }

    /**
     * 查询执行节点列表。
     *
     * @return 执行节点列表
     */
    @GetMapping
    public CommonResult<List<JobExecutorNodeResponse>> listNodes() {
        return success(jobExecutorNodeApplicationService.listNodes());
    }
}
