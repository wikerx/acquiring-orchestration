package com.scott.payment.job.client.clearing.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingCompensationClientDTOs
 * @date : 2026-09-01 22:30
 * @email : scott_x@163.com
 * @description : service-job 与 service-clearing 之间的补偿扫描 JSON 契约，使用交易时间与主键复合游标保证稳定翻页。
 * @status : update
 */
public final class ClearingCompensationClientDTOs {
    private ClearingCompensationClientDTOs() {
    }

    /** 单页补偿扫描请求；时间范围为左闭右开且必须落在同一物理季度。 */
    @Data
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 扫描模式：DRY_RUN 仅发现，SHADOW_WRITE 允许清分服务幂等恢复。 */
        private String mode;
        /** 扫描起点，包含，采用清分交易时间口径。 */
        private LocalDateTime beginTime;
        /** 扫描终点，不包含。 */
        private LocalDateTime endTime;
        /** 上一页末条交易时间；首页为空。 */
        private LocalDateTime cursorTransactionDateTime;
        /** 上一页末条主键，与交易时间共同形成稳定游标；首页为空。 */
        private Long cursorId;
        /** 单页最大扫描数量，必须受 Job 和清分服务双重上限约束。 */
        private Integer limit;
    }

    /** 单条补偿处置摘要，不包含异常堆栈或敏感业务报文。 */
    @Data
    public static class Record implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 平台交易号。 */
        private String transactionId;
        /** 被识别为补偿候选的原因摘要。 */
        private String reason;
        /** 本次处置结果摘要。 */
        private String result;
        /** 用于定位物理季度的交易时间。 */
        private LocalDateTime transactionDateTime;
    }

    /** 单页补偿扫描结果及下一页复合游标。 */
    @Data
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 清分服务实际执行的扫描模式。 */
        private String mode;
        /** 本页检查数量。 */
        private int scannedCount;
        /** 本页成功写入或恢复数量。 */
        private int writeCount;
        /** 本页因状态、幂等或规则不满足而跳过的数量。 */
        private int skippedCount;
        /** 是否仍有下一页。 */
        private boolean hasMore;
        /** 下一页复合游标的交易时间；hasMore=true 时不能为空。 */
        private LocalDateTime nextCursorTransactionDateTime;
        /** 下一页复合游标的主键；hasMore=true 时不能为空。 */
        private Long nextCursorId;
        /** 本页脱敏后的逐条处置摘要。 */
        private List<Record> records = Collections.emptyList();
    }
}
