package com.scott.payment.clearing.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingCompensationDTOs
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分内部补偿协议模型，限制为单季度主键游标扫描和预览/执行模式；不携带异常堆栈、持卡人数据或任意目标状态。
 * @status : update
 */
public final class ClearingCompensationDTOs {

    private ClearingCompensationDTOs() {
    }

    /** 单季度补偿扫描请求。 */
    @Data
    public static class CompensationScanRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 扫描模式：预览或执行，由服务端白名单校验。 */
        private String mode;
        /** 单季度半开时间窗口起点，不能为空。 */
        private LocalDateTime beginTime;
        /** 单季度半开时间窗口终点，不能为空。 */
        private LocalDateTime endTime;
        /** 上一页最后动作分片时间；首页为空。 */
        private LocalDateTime cursorTransactionDateTime;
        /** 上一页最后动作表主键；必须与时间游标同时为空或同时有值。 */
        private Long cursorId;
        /** 单页扫描上限，由服务端限制最大值。 */
        private Integer limit;
    }

    /** 单个补偿候选及处置结果。 */
    @Data
    public static class CompensationRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 动作交易号。 */
        private String transactionId;
        /** 动作操作号。 */
        private String operationId;
        /** 平台商户号。 */
        private String merchantId;
        /** 扫描时清分权威状态。 */
        private String clearingStatus;
        /** 候选进入补偿的稳定原因分类。 */
        private String reason;
        /** 本次预览或恢复的稳定处置码。 */
        private String result;
        /** 动作季度分片时间。 */
        private LocalDateTime transactionDateTime;
    }

    /** 补偿扫描响应。 */
    @Data
    public static class CompensationScanResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 实际执行的扫描模式。 */
        private String mode;
        /** 本页读取候选数量。 */
        private int scannedCount;
        /** 本页成功写入补偿状态或 Outbox 的数量。 */
        private int writeCount;
        /** 因状态变化、预览模式或幂等命中而跳过的数量。 */
        private int skippedCount;
        /** 是否仍存在下一页候选。 */
        private boolean hasMore;
        /** 下一页动作分片时间游标；无下一页时为空。 */
        private LocalDateTime nextCursorTransactionDateTime;
        /** 下一页动作表主键游标；无下一页时为空。 */
        private Long nextCursorId;
        /** 本页逐条稳定处置结果，默认空列表。 */
        private List<CompensationRecord> records = Collections.emptyList();
    }
}
