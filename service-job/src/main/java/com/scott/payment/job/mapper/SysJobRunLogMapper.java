package com.scott.payment.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.job.entity.SysJobRunLogDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysJobRunLogMapper
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 系统任务运行日志数据访问接口
 * @status : create
 */
public interface SysJobRunLogMapper extends BaseMapper<SysJobRunLogDO> {

    /**
     * 仅在当前仍为 RUNNING 状态时更新任务日志终态，避免异步完成覆盖 TIMEOUT。
     *
     * @param id            日志主键
     * @param runStatus     目标运行状态
     * @param resultMessage 结果摘要
     * @param errorMessage  错误摘要
     * @param durationMs    执行耗时
     * @return 影响行数
     */
    @Update("""
            UPDATE sys_job_run_log
            SET run_status = #{runStatus},
                result_message = #{resultMessage},
                error_message = #{errorMessage},
                duration_ms = #{durationMs},
                end_time = NOW(),
                update_time = NOW()
            WHERE id = #{id}
              AND run_status = 'RUNNING'
            """)
    int finishIfRunning(@Param("id") Long id,
                        @Param("runStatus") String runStatus,
                        @Param("resultMessage") String resultMessage,
                        @Param("errorMessage") String errorMessage,
                        /**
                         * 完成 m 分支的校验或状态更新。
                         * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                         * <p>
                         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                         * </p>
                         * @param durationMs duration Ms 输入值，含义由调用方法名称和所属业务对象限定
                         */
                        @Param("durationMs") Long durationMs);

    /**
     * 查询超时仍在运行的日志。
     *
     * @return 超时日志列表
     */
    @Select("""
            SELECT *
            FROM sys_job_run_log
            WHERE run_status = 'RUNNING'
              AND start_time IS NOT NULL
              AND timeout_seconds IS NOT NULL
              AND TIMESTAMPDIFF(SECOND, start_time, NOW()) >= timeout_seconds
            ORDER BY start_time ASC
            LIMIT 100
            """)
    /**
     * 查询 select Timeout Candidates 所需数据，未命中时按调用场景返回空值或抛出异常。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 解析或查询得到的业务值
     */
    List<SysJobRunLogDO> selectTimeoutCandidates();
}
