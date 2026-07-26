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
     * job Run Log Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
