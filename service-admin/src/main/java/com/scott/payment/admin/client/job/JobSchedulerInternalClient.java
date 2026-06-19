package com.scott.payment.admin.client.job;

import com.scott.payment.admin.client.job.dto.JobTaskRemoteSaveRequest;
import com.scott.payment.admin.dto.monitor.JobExecutorNodeResponse;
import com.scott.payment.admin.dto.monitor.JobHandlerOptionResponse;
import com.scott.payment.admin.dto.monitor.JobManualTriggerRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogQueryRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogResponse;
import com.scott.payment.admin.dto.monitor.JobTaskQueryRequest;
import com.scott.payment.admin.dto.monitor.JobTaskResponse;
import com.scott.payment.component.core.model.PageResult;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerInternalClient
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台调用调度中心的内部客户端接口
 * @status : create
 */

public interface JobSchedulerInternalClient {

    /**
     * 查询任务处理器白名单。
     *
     * @return 处理器列表
     */
    List<JobHandlerOptionResponse> listHandlers();

    /**
     * 分页查询任务定义。
     *
     * @param request 查询条件
     * @return 任务分页结果
     */
    PageResult<JobTaskResponse> pageTasks(JobTaskQueryRequest request);

    /**
     * 新增任务定义。
     *
     * @param request 保存请求
     * @return 任务响应
     */
    JobTaskResponse createTask(JobTaskRemoteSaveRequest request);

    /**
     * 更新任务定义。
     *
     * @param taskId  任务主键
     * @param request 保存请求
     * @return 任务响应
     */
    JobTaskResponse updateTask(Long taskId, JobTaskRemoteSaveRequest request);

    /**
     * 切换任务状态。
     *
     * @param taskId   任务主键
     * @param status   目标状态
     * @param operator 操作人
     * @return 任务响应
     */
    JobTaskResponse changeStatus(Long taskId, String status, String operator);

    /**
     * 手动执行一次任务。
     *
     * @param taskId   任务主键
     * @param request  执行请求
     * @return 执行批次号
     */
    String trigger(Long taskId, JobManualTriggerRequest request);

    /**
     * 删除任务。
     *
     * @param taskId   任务主键
     * @param operator 操作人
     */
    void deleteTask(Long taskId, String operator);

    /**
     * 分页查询执行日志。
     *
     * @param request 查询条件
     * @return 日志分页结果
     */
    PageResult<JobRunLogResponse> pageRunLogs(JobRunLogQueryRequest request);

    /**
     * 查询执行节点列表。
     *
     * @return 执行节点列表
     */
    List<JobExecutorNodeResponse> listNodes();
}
