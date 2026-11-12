package com.scott.payment.job.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.job.api.internal.dto.JobRunLogQueryRequest;
import com.scott.payment.job.api.internal.dto.JobRunLogResponse;
import com.scott.payment.job.api.internal.dto.JobExecutionTrendResponse;
import com.scott.payment.job.application.JobRunLogApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 任务运行日志内部控制器
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

    /**
     * 分页查询执行日志，专供已通过 HMAC 鉴权的后台导出任务使用。
     * <p>
     * 该入口只开放只读分页能力，不接受浏览器管理员会话，也不复用删除、清理等写操作权限。
     * </p>
     *
     * @param request 查询条件
     * @return 分页日志结果
     */
    @PostMapping("/export/search")
    public CommonResult<PageResult<JobRunLogResponse>> pageLogsForExport(
            @RequestBody(required = false) @Valid JobRunLogQueryRequest request) {
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
     * 查询最近七个自然日任务执行终态趋势。
     *
     * @return 固定七个日期点的成功与失败数量
     */
    @GetMapping("/trend")
    public CommonResult<JobExecutionTrendResponse> executionTrend() {
        return success(jobRunLogApplicationService.executionTrend());
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
