package com.scott.payment.job.application;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.job.api.internal.dto.JobRunLogQueryRequest;
import com.scott.payment.job.api.internal.dto.JobRunLogResponse;
import com.scott.payment.job.converter.JobSchedulerConverter;
import com.scott.payment.job.entity.SysJobRunLogDO;
import com.scott.payment.job.service.JobRunLogService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogApplicationService
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务运行日志应用服务
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Run Log Application 服务契约，位于 service-job 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class JobRunLogApplicationService {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
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
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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

    /**
     * 按主键删除单条执行日志。
     *
     * @param id 日志主键
     */
    /**
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void removeLog(Long id) {
        jobRunLogService.removeLog(id);
    }

    /**
     * 按条件清空执行日志。
     *
     * @param request 查询条件
     * @return 删除数量
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public int cleanLogs(JobRunLogQueryRequest request) {
        return jobRunLogService.cleanLogs(request);
    }

    /**
     * 按条件查询执行日志列表，供导出使用。
     *
     * @param request 查询条件
     * @return 日志响应列表
     */
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public List<JobRunLogResponse> listLogs(JobRunLogQueryRequest request) {
        return jobRunLogService.listLogs(request).stream()
                .map(JobSchedulerConverter.INSTANCE::toRunLogResponse)
                .toList();
    }
}
