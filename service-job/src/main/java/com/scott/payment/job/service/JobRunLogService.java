package com.scott.payment.job.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.job.enums.JobRunStatusEnum;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.api.internal.dto.JobRunLogQueryRequest;
import com.scott.payment.job.entity.SysJobRunLogDO;
import com.scott.payment.job.entity.SysJobTaskDO;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogService
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务运行日志服务接口
 * @status : create
 */
public interface JobRunLogService {

    /**
     * 创建待执行日志。
     *
     * @param task          任务定义
     * @param context       执行上下文
     * @param maskedParams  已脱敏参数
     * @return 新建日志实体
     */
    SysJobRunLogDO createWaitingLog(SysJobTaskDO task, JobExecuteContext context, String maskedParams);

    /**
     * 标记日志进入 RUNNING 状态。
     *
     * @param logId 日志主键
     */
    void markRunning(Long logId);

    /**
     * 标记成功。
     *
     * @param logId         日志主键
     * @param durationMs    执行耗时
     * @param resultMessage 结果摘要
     */
    void finishAsSuccess(Long logId, long durationMs, String resultMessage);

    /**
     * 标记失败。
     *
     * @param logId        日志主键
     * @param durationMs   执行耗时
     * @param errorMessage 错误摘要
     */
    void finishAsFailed(Long logId, long durationMs, String errorMessage);

    /**
     * 标记超时。
     *
     * @param runLog 超时日志
     * @return true 表示状态从 RUNNING 成功切换到 TIMEOUT
     */
    boolean finishAsTimeout(SysJobRunLogDO runLog);

    /**
     * 分页查询日志。
     *
     * @param request 查询条件
     * @return 日志分页结果
     */
    PageResult<SysJobRunLogDO> pageLogs(JobRunLogQueryRequest request);

    /**
     * 按主键删除单条执行日志。
     *
     * @param id 日志主键
     */
    void removeLog(Long id);

    /**
     * 按条件清空执行日志。
     *
     * @param request 查询条件
     * @return 清理数量
     */
    int cleanLogs(JobRunLogQueryRequest request);

    /**
     * 按条件查询执行日志列表，供导出使用。
     *
     * @param request 查询条件
     * @return 执行日志列表
     */
    List<SysJobRunLogDO> listLogs(JobRunLogQueryRequest request);

    /**
     * 查询超时候选日志。
     *
     * @return 超时候选日志列表
     */
    List<SysJobRunLogDO> selectTimeoutCandidates();
}
