package com.scott.payment.job.application;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.job.api.internal.dto.JobRunLogQueryRequest;
import com.scott.payment.job.api.internal.dto.JobRunLogResponse;
import com.scott.payment.job.api.internal.dto.JobExecutionTrendResponse;
import com.scott.payment.job.converter.JobSchedulerConverter;
import com.scott.payment.job.entity.SysJobRunLogDO;
import com.scott.payment.job.service.JobRunLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务运行日志应用服务
 */
@Service
public class JobRunLogApplicationService {

    private static final int TREND_DAYS = 7;

    private final JobRunLogService jobRunLogService;
    /**
     * 任务调度对象转换器。
     */
    private final JobSchedulerConverter jobSchedulerConverter;

    /**
     * 生成自然日边界的系统时钟，生产使用 JVM 业务时区，测试可注入固定时钟。
     */
    private final Clock clock;

    /**
     * 创建执行日志应用服务。
     *
     * @param jobRunLogService 执行日志领域服务
     * @param jobSchedulerConverter 任务调度对象转换器
     */
    @Autowired
    public JobRunLogApplicationService(JobRunLogService jobRunLogService,
                                       JobSchedulerConverter jobSchedulerConverter) {
        this(jobRunLogService, jobSchedulerConverter, Clock.systemDefaultZone());
    }

    /**
     * 创建可注入时钟的执行日志应用服务。
     *
     * @param jobRunLogService      执行日志领域服务
     * @param jobSchedulerConverter 任务调度对象转换器
     * @param clock                 自然日边界时钟
     */
    JobRunLogApplicationService(JobRunLogService jobRunLogService,
                                JobSchedulerConverter jobSchedulerConverter,
                                Clock clock) {
        this.jobRunLogService = jobRunLogService;
        this.jobSchedulerConverter = jobSchedulerConverter;
        this.clock = clock;
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

    /**
     * 查询最近七个自然日的任务成功与失败终态数量，并为无日志日期补零。
     *
     * @return 固定包含七个升序日期点的趋势结果
     */
    public JobExecutionTrendResponse executionTrend() {
        LocalDate endDate = LocalDate.now(clock);
        LocalDate startDate = endDate.minusDays(TREND_DAYS - 1L);
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.plusDays(1).atStartOfDay();
        Map<LocalDate, JobExecutionTrendResponse.DailyPoint> indexed =
                jobRunLogService.summarizeExecutionTrend(startTime, endTime).stream()
                        .collect(Collectors.toMap(
                                JobExecutionTrendResponse.DailyPoint::getDate,
                                Function.identity(),
                                (left, right) -> left));

        JobExecutionTrendResponse response = new JobExecutionTrendResponse();
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        for (int offset = 0; offset < TREND_DAYS; offset++) {
            LocalDate date = startDate.plusDays(offset);
            JobExecutionTrendResponse.DailyPoint source = indexed.get(date);
            JobExecutionTrendResponse.DailyPoint point = new JobExecutionTrendResponse.DailyPoint();
            point.setDate(date);
            point.setSuccessCount(source == null ? 0L : source.getSuccessCount());
            point.setFailedCount(source == null ? 0L : source.getFailedCount());
            response.getPoints().add(point);
        }
        return response;
    }
}
