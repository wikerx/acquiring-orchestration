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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeInternalController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Executor Node Internal 管理接口，位于 service-job 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/internal/job/nodes")
public class JobExecutorNodeInternalController {

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
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
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping
    public CommonResult<List<JobExecutorNodeResponse>> listNodes() {
        return success(jobExecutorNodeApplicationService.listNodes());
    }
}
