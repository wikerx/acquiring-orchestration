package com.scott.payment.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.job.entity.SysJobRunLogDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
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
     * @param endTime       应用侧完成时间，与开始时间使用同一时钟和 JDBC 时区转换
     * @return 影响行数
     */
    @Update("""
            UPDATE sys_job_run_log
            SET run_status = #{runStatus},
                result_message = #{resultMessage},
                error_message = #{errorMessage},
                duration_ms = #{durationMs},
                end_time = GREATEST(#{endTime}, start_time),
                update_time = GREATEST(#{endTime}, start_time)
            WHERE id = #{id}
              AND run_status = 'RUNNING'
            """)
    int finishIfRunning(@Param("id") Long id,
                        @Param("runStatus") String runStatus,
                        @Param("resultMessage") String resultMessage,
                        @Param("errorMessage") String errorMessage,
                        @Param("durationMs") Long durationMs,
                        @Param("endTime") LocalDateTime endTime);

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
    List<SysJobRunLogDO> selectTimeoutCandidates();
}
