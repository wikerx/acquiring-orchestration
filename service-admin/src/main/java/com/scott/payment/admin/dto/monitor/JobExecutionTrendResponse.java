package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 最近七个自然日任务执行终态趋势。
 */
@Data
public class JobExecutionTrendResponse {

    /** 聚合开始日期，包含当天。 */
    private LocalDate startDate;

    /** 聚合结束日期，包含当天。 */
    private LocalDate endDate;

    /** 按自然日升序排列的成功和失败终态数量。 */
    private List<DailyPoint> points = new ArrayList<>();

    /**
     * 单个自然日的任务执行终态数量。
     */
    @Data
    public static class DailyPoint {

        /** 任务完成日期。 */
        private LocalDate date;

        /** SUCCESS 状态数量。 */
        private long successCount;

        /** FAILED、TIMEOUT 和 CANCELLED 状态数量。 */
        private long failedCount;
    }
}
