package com.scott.payment.job.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.job.enums.JobRunStatusEnum;
import com.scott.payment.job.api.internal.dto.JobTaskQueryRequest;
import com.scott.payment.job.api.internal.dto.JobTaskSaveRequest;
import com.scott.payment.job.entity.SysJobTaskDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskService
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务任务服务接口
 * @status : create
 */

public interface JobTaskService {

    /**
     * 分页查询任务。
     *
     * @param request 查询条件
     * @return 任务分页结果
     */
    PageResult<SysJobTaskDO> pageTasks(JobTaskQueryRequest request);

    /**
     * 新增任务。
     *
     * @param request 保存请求
     * @return 任务实体
     */
    SysJobTaskDO createTask(JobTaskSaveRequest request);

    /**
     * 更新任务。
     *
     * @param taskId  任务主键
     * @param request 保存请求
     * @return 任务实体
     */
    SysJobTaskDO updateTask(Long taskId, JobTaskSaveRequest request);

    /**
     * 切换任务状态。
     *
     * @param taskId   任务主键
     * @param status   目标状态
     * @param operator 操作人
     * @return 任务实体
     */
    SysJobTaskDO changeStatus(Long taskId, String status, String operator);

    /**
     * 逻辑删除任务。
     *
     * @param taskId   任务主键
     * @param operator 操作人
     */
    void deleteTask(Long taskId, String operator);

    /**
     * 查询指定任务。
     *
     * @param taskId 任务主键
     * @return 任务实体
     */
    SysJobTaskDO getRequiredTask(Long taskId);

    /**
     * 查询到期任务。
     *
     * @param triggerTime 当前时间
     * @param limit       返回条数
     * @return 到期任务列表
     */
    List<SysJobTaskDO> selectDueTasks(LocalDateTime triggerTime, int limit);

    /**
     * 尝试抢占任务锁。
     *
     * @param task        任务实体
     * @param nodeId      节点标识
     * @param currentTime 当前时间
     * @return true 表示抢占成功
     */
    boolean tryAcquireLock(SysJobTaskDO task, String nodeId, LocalDateTime currentTime);

    /**
     * 任务被定时调度触发后更新元数据。
     *
     * @param taskId           任务主键
     * @param lastTriggerTime  本次触发时间
     * @param nextTriggerTime  下次触发时间
     */
    void markScheduled(Long taskId, LocalDateTime lastTriggerTime, LocalDateTime nextTriggerTime);

    /**
     * 续租任务锁。
     *
     * @param taskId    任务主键
     * @param nodeId    节点标识
     * @param lockUntil 锁过期时间
     */
    void extendLock(Long taskId, String nodeId, LocalDateTime lockUntil);

    /**
     * 任务执行结束后更新任务终态并释放锁。
     *
     * @param taskId         任务主键
     * @param lastRunStatus  最终运行状态
     */
    void finishTaskRun(Long taskId, JobRunStatusEnum lastRunStatus);
}
