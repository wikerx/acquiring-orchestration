package com.scott.payment.job.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.job.api.internal.dto.JobRunLogQueryRequest;
import com.scott.payment.job.api.internal.dto.JobRunLogResponse;
import com.scott.payment.job.application.JobRunLogApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /**
     * job Run Log Application Service 依赖，用于 Job Run Log Internal Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
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

    /**
     * 按条件查询执行日志列表，供导出使用。
     *
     * @param request 查询条件
     * @return 执行日志列表
     */
    @PostMapping("/list")
    public CommonResult<List<JobRunLogResponse>> listLogs(@RequestBody(required = false) @Valid JobRunLogQueryRequest request) {
        return success(jobRunLogApplicationService.listLogs(request));
    }

    /**
     * 删除单条执行日志。
     *
     * @param id 日志主键
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public CommonResult<Void> removeLog(@PathVariable("id") Long id) {
        jobRunLogApplicationService.removeLog(id);
        return success();
    }

    /**
     * 按条件清空执行日志。
     *
     * @param request 查询条件
     * @return 删除数量
     */
    @PostMapping("/clean")
    public CommonResult<Integer> cleanLogs(@RequestBody(required = false) @Valid JobRunLogQueryRequest request) {
        return success(jobRunLogApplicationService.cleanLogs(request));
    }
}
