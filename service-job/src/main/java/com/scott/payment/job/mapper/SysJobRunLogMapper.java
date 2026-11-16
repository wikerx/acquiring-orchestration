package com.scott.payment.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.job.api.internal.dto.JobExecutionTrendResponse;
import com.scott.payment.job.entity.SysJobRunLogDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统任务运行日志数据访问接口
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

    /**
     * 按任务完成日期聚合指定半开时间区间内的成功和失败终态。
     *
     * <p>使用结束时间确定终态归属日期，兼容历史记录时回退更新时间；
     * 非终态日志不会进入结果。</p>
     *
     * @param startTime 开始时间，包含
     * @param endTime   结束时间，不包含
     * @return 仅包含存在终态日志日期的聚合结果
     */
    @Select("""
            SELECT DATE(COALESCE(end_time, update_time)) AS date,
                   SUM(CASE WHEN run_status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count,
                   SUM(CASE WHEN run_status IN ('FAILED', 'TIMEOUT', 'CANCELLED') THEN 1 ELSE 0 END) AS failed_count
            FROM sys_job_run_log
            WHERE COALESCE(end_time, update_time) >= #{startTime}
              AND COALESCE(end_time, update_time) < #{endTime}
              AND run_status IN ('SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELLED')
            GROUP BY DATE(COALESCE(end_time, update_time))
            ORDER BY date ASC
            """)
    List<JobExecutionTrendResponse.DailyPoint> selectDailyTrend(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
