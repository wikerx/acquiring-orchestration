package com.scott.payment.job.api.internal.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 最近七个自然日任务执行终态聚合结果。
 *
 * <p>成功仅统计 SUCCESS，失败统计 FAILED、TIMEOUT 和 CANCELLED；
 * WAITING 与 RUNNING 不属于终态，不进入成功或失败数量。</p>
 */
@Data
public class JobExecutionTrendResponse {

    /**
     * 聚合开始日期，包含当天。
     */
    private LocalDate startDate;

    /**
     * 聚合结束日期，包含当天。
     */
    private LocalDate endDate;

    /**
     * 按自然日升序排列的执行结果，缺少日志的日期由应用层补零。
     */
    private List<DailyPoint> points = new ArrayList<>();

    /**
     * 单个自然日的任务终态数量。
     */
    @Data
    public static class DailyPoint {

        /**
         * 任务完成日期。
         */
        private LocalDate date;

        /**
         * 成功任务数量。
         */
        private long successCount;

        /**
         * 失败、超时或取消任务数量。
         */
        private long failedCount;
    }
}
