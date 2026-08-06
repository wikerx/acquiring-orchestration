package com.scott.payment.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.job.entity.SysJobTaskDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysJobTaskMapper
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 系统任务任务数据访问接口
 * @status : create
 */
public interface SysJobTaskMapper extends BaseMapper<SysJobTaskDO> {

    /**
     * 查询到期待执行任务。
     *
     * @param triggerTime 当前时间
     * @param limit       最多返回条数
     * @return 待执行任务列表
     */
    @Select("""
            SELECT *
            FROM sys_job_task
            WHERE deleted = 0
              AND status = 'ENABLED'
              AND cron_expression IS NOT NULL
              AND cron_expression <> ''
              AND next_trigger_time IS NOT NULL
              AND next_trigger_time <= #{triggerTime}
              AND (lock_until IS NULL OR lock_until < #{triggerTime})
            ORDER BY next_trigger_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<SysJobTaskDO> selectDueTasks(@Param("triggerTime") LocalDateTime triggerTime,
                                      @Param("limit") int limit);

    /**
     * 抢占任务锁。
     *
     * @param taskId     任务主键
     * @param nodeId     节点标识
     * @param lockUntil  锁过期时间
     * @param version    当前版本号
     * @return 影响行数，1 表示抢占成功
     */
    @Update("""
            UPDATE sys_job_task
            SET lock_owner = #{nodeId},
                lock_until = #{lockUntil},
                version = version + 1,
                update_time = NOW()
            WHERE id = #{taskId}
              AND deleted = 0
              AND status = 'ENABLED'
              AND (lock_until IS NULL OR lock_until < NOW())
              AND version = #{version}
            """)
    int acquireLock(@Param("taskId") Long taskId,
                    @Param("nodeId") String nodeId,
                    @Param("lockUntil") LocalDateTime lockUntil,
                    @Param("version") Integer version);

    /**
     * 更新锁续期时间。
     *
     * @param taskId    任务主键
     * @param nodeId    当前节点
     * @param lockUntil 新锁过期时间
     * @return 影响行数
     */
    @Update("""
            UPDATE sys_job_task
            SET lock_until = #{lockUntil},
                update_time = NOW()
            WHERE id = #{taskId}
              AND lock_owner = #{nodeId}
            """)
    int extendLock(@Param("taskId") Long taskId,
                   @Param("nodeId") String nodeId,
                   @Param("lockUntil") LocalDateTime lockUntil);

    /**
     * 更新任务启停状态和下一触发时间；禁用时显式将 next_trigger_time 写为 NULL。
     *
     * @param taskId 任务主键
     * @param status 目标状态
     * @param nextTriggerTime 下一触发时间，禁用时为空
     * @param operator 操作人
     * @param now 更新时间
     * @return 影响行数
     */
    @Update("""
            UPDATE sys_job_task
            SET status = #{status},
                next_trigger_time = #{nextTriggerTime},
                update_by = #{operator},
                update_time = #{now}
            WHERE id = #{taskId}
              AND deleted = 0
            """)
    int updateStatus(@Param("taskId") Long taskId,
                     @Param("status") String status,
                     @Param("nextTriggerTime") LocalDateTime nextTriggerTime,
                     @Param("operator") String operator,
                     @Param("now") LocalDateTime now);

    /**
     * 仅由当前锁持有节点写入运行终态并显式释放租约。
     *
     * @param taskId 任务主键
     * @param nodeId 当前锁持有节点
     * @param lastRunStatus 最终运行状态
     * @param now 完成时间
     * @return 1 表示终态和锁已更新，0 表示锁已转移或任务不存在
     */
    @Update("""
            UPDATE sys_job_task
            SET last_run_status = #{lastRunStatus},
                lock_owner = NULL,
                lock_until = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE id = #{taskId}
              AND deleted = 0
              AND lock_owner = #{nodeId}
            """)
    int finishTaskRun(@Param("taskId") Long taskId,
                      @Param("nodeId") String nodeId,
                      @Param("lastRunStatus") String lastRunStatus,
                      @Param("now") LocalDateTime now);
}
