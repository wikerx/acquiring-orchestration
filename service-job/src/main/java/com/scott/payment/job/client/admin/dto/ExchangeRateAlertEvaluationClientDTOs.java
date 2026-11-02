package com.scott.payment.job.client.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * service-job 调用汇率波动评估内部接口的请求和响应对象。
 */
public final class ExchangeRateAlertEvaluationClientDTOs {

    private ExchangeRateAlertEvaluationClientDTOs() {
    }

    /**
     * 汇率波动评估请求。
     */
    @Data
    public static class Request implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 幂等请求键，同一次任务运行及其重试始终使用同一个值。
         */
        private String requestKey;

        /**
         * 本轮评估基准时间，所有规则窗口均以此时间计算。
         */
        private LocalDateTime evaluationTime;
    }

    /**
     * 汇率波动评估统计结果。
     */
    @Data
    public static class Response implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 幂等请求键。 */
        private String requestKey;

        /** 本轮评估状态。 */
        private String evaluationStatus;

        /** 已评估规则数。 */
        private Integer evaluatedRuleCount;

        /** 已评估币种对数。 */
        private Integer evaluatedPairCount;

        /** 新触发事件数。 */
        private Integer triggeredEventCount;

        /** 已更新活动事件数。 */
        private Integer updatedEventCount;

        /** 已恢复事件数。 */
        private Integer resolvedEventCount;

        /** 样本不足规则数。 */
        private Integer insufficientDataCount;

        /** 评估开始时间。 */
        private LocalDateTime startTime;

        /** 评估结束时间。 */
        private LocalDateTime endTime;
    }
}
