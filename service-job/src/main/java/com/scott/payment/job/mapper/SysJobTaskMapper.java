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
                                      /**
                                       * 完成 m 分支的校验或状态更新。
                                       * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                       * <p>
                                       * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                       * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                       * </p>
                                       * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
                                       */
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
                    /**
                     * 完成 m 分支的校验或状态更新。
                     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                     * <p>
                     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                     * </p>
                     * @param version version 输入值，含义由调用方法名称和所属业务对象限定
                     */
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
                   /**
                    * 完成 m 分支的校验或状态更新。
                    * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                    * <p>
                    * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                    * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                    * </p>
                    * @param lockUntil lock Until 输入值，含义由调用方法名称和所属业务对象限定
                    */
                   @Param("lockUntil") LocalDateTime lockUntil);
}
