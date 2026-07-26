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
@Service
public class JobRunLogApplicationService {

    /**
     * job Run Log Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JobRunLogService jobRunLogService;
    /**
     * 任务调度对象转换器。
     */
    private final JobSchedulerConverter jobSchedulerConverter;

    /**
     * 创建执行日志应用服务。
     *
     * @param jobRunLogService 执行日志领域服务
     * @param jobSchedulerConverter 任务调度对象转换器
     */
    public JobRunLogApplicationService(JobRunLogService jobRunLogService,
                                       JobSchedulerConverter jobSchedulerConverter) {
        this.jobRunLogService = jobRunLogService;
        this.jobSchedulerConverter = jobSchedulerConverter;
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
                        .map(jobSchedulerConverter::toRunLogResponse)
                        .toList()
        );
    }

    /**
     * 按主键删除单条执行日志。
     *
     * @param id 日志主键
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
    public int cleanLogs(JobRunLogQueryRequest request) {
        return jobRunLogService.cleanLogs(request);
    }

    /**
     * 按条件查询执行日志列表，供导出使用。
     *
     * @param request 查询条件
     * @return 日志响应列表
     */
    public List<JobRunLogResponse> listLogs(JobRunLogQueryRequest request) {
        return jobRunLogService.listLogs(request).stream()
                .map(jobSchedulerConverter::toRunLogResponse)
                .toList();
    }
}
