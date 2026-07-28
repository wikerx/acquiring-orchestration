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

    /**
     * job Executor Node Application Service 依赖，用于 Job Executor Node Internal Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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
    @GetMapping
    public CommonResult<List<JobExecutorNodeResponse>> listNodes() {
        return success(jobExecutorNodeApplicationService.listNodes());
    }
}
