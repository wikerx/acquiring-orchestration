package com.scott.payment.merchant.dto.transaction.detail;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商户交易详情的扩展记录模型。
 *
 * <p>这些类型只声明商户可见字段，避免交易详情通过动态 Map 意外暴露数据库新增列、
 * 渠道报文正文或内部安全信息。</p>
 */
public final class TransactionDetailResponses {

    /**
     * 禁止实例化交易详情扩展响应聚合类。
     */
    private TransactionDetailResponses() {
    }

    /**
     * 交易状态流转记录。
     */
    @Data
    public static class TransactionStatusHistoryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台交易号。
         */
        private String transactionId;
        /**
         * 交易动作号。
         */
        private String operationId;
        /**
         * 流转前状态。
         */
        private String fromStatus;
        /**
         * 流转后状态。
         */
        private String toStatus;
        /**
         * 状态流转触发类型。
         */
        private String triggerType;
        /**
         * 状态流转执行结果。
         */
        private String transitionResult;
        /**
         * 商户可见的失败原因。
         */
        private String failReason;
        /**
         * 状态流转时间。
         */
        private LocalDateTime statusTime;
    }

    /**
     * 交易流程事件。
     */
    @Data
    public static class TransactionFlowEventResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台交易号。
         */
        private String transactionId;
        /**
         * 交易动作号。
         */
        private String operationId;
        /**
         * 流程事件类型。
         */
        private String eventType;
        /**
         * 流程事件阶段。
         */
        private String eventStage;
        /**
         * 流程事件状态。
         */
        private String eventStatus;
        /**
         * 流程事件展示名称。
         */
        private String eventName;
        /**
         * 商户可见的事件摘要。
         */
        private String eventContent;
        /**
         * 事件发生前交易状态。
         */
        private String previousStatus;
        /**
         * 事件发生后交易状态。
         */
        private String currentStatus;
        /**
         * 商户可见的事件错误码。
         */
        private String errorCode;
        /**
         * 商户可见的事件错误描述。
         */
        private String errorMessage;
        /**
         * 事件发生时间。
         */
        private LocalDateTime eventTime;
    }

    /**
     * 交易生命周期金额变动记录。
     *
     * <p>所有金额都必须结合 {@code amountCurrency} 解释，查询层不得跨币种合并。</p>
     */
    @Data
    public static class TransactionAmountChangeResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台交易号。
         */
        private String transactionId;
        /**
         * 触发金额变动的交易动作号。
         */
        private String operationId;
        /**
         * 被引用的源交易号。
         */
        private String sourceTransactionId;
        /**
         * 被引用的源动作号。
         */
        private String sourceOperationId;
        /**
         * 金额变动类型。
         */
        private String changeType;
        /**
         * 金额币种。
         */
        private String amountCurrency;
        /**
         * 本次变动金额。
         */
        private BigDecimal changeAmount;
        /**
         * 变动后累计授权金额。
         */
        private BigDecimal authorizedAfter;
        /**
         * 变动后累计请款金额。
         */
        private BigDecimal capturedAfter;
        /**
         * 变动后累计退款金额。
         */
        private BigDecimal refundedAfter;
        /**
         * 变动后可请款金额。
         */
        private BigDecimal availableCaptureAfter;
        /**
         * 变动后可退款金额。
         */
        private BigDecimal availableRefundAfter;
        /**
         * 商户可见的金额变动原因。
         */
        private String changeReason;
        /**
         * 金额变动时间。
         */
        private LocalDateTime changeTime;
    }

    /**
     * 商户通知任务。
     */
    @Data
    public static class MerchantNotificationResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户通知任务号。
         */
        private String notifyId;
        /**
         * 平台交易号。
         */
        private String transactionId;
        /**
         * 交易动作号。
         */
        private String operationId;
        /**
         * 商户订单号。
         */
        private String merchantOrderNo;
        /**
         * 通知业务类型。
         */
        private String notifyType;
        /**
         * 触发通知的交易事件类型。
         */
        private String eventType;
        /**
         * 通知任务状态。
         */
        private String notifyStatus;
        /**
         * 已脱敏的商户回调地址。
         */
        private String targetUrlMasked;
        /**
         * 最近一次通知尝试序号。
         */
        private Integer lastAttemptNo;
        /**
         * 自动通知最大尝试次数。
         */
        private Integer maxRetryCount;
        /**
         * 下一次自动重试时间。
         */
        private LocalDateTime nextRetryTime;
        /**
         * 通知成功时间。
         */
        private LocalDateTime successTime;
        /**
         * 商户可见的最近一次失败原因。
         */
        private String failReason;
    }

    /**
     * 商户通知单次投递记录。
     */
    @Data
    public static class MerchantNotificationDeliveryLogResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户通知投递日志号。
         */
        private String notifyLogId;
        /**
         * 商户通知任务号。
         */
        private String notifyId;
        /**
         * 平台交易号。
         */
        private String transactionId;
        /**
         * 交易动作号。
         */
        private String operationId;
        /**
         * 本次投递尝试序号。
         */
        private Integer attemptNo;
        /**
         * 本次投递状态。
         */
        private String deliveryStatus;
        /**
         * 商户回调 HTTP 响应状态码。
         */
        private Integer httpStatus;
        /**
         * 商户回调响应码。
         */
        private String merchantResponseCode;
        /**
         * 商户回调响应摘要。
         */
        private String merchantResponseMessage;
        /**
         * 商户可见的投递失败原因。
         */
        private String failReason;
        /**
         * 投递开始时间。
         */
        private LocalDateTime requestTime;
        /**
         * 投递完成时间。
         */
        private LocalDateTime responseTime;
        /**
         * 通知耗时，单位为毫秒。
         */
        private Integer durationMillis;
    }

    /**
     * 商户 OpenAPI 交互摘要。
     *
     * <p>该模型不返回 JWT、请求或响应密文以及支付资料明文。</p>
     */
    @Data
    public static class MerchantApiInteractionLogResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户 API 交互日志号。
         */
        private String apiLogId;
        /**
         * 商户本次请求唯一标识。
         */
        private String requestId;
        /**
         * 平台交易号。
         */
        private String transactionId;
        /**
         * 交易动作号。
         */
        private String operationId;
        /**
         * 商户订单号。
         */
        private String merchantOrderNo;
        /**
         * 商户请求号。
         */
        private String merchantOrderId;
        /**
         * 商户 API 交易动作。
         */
        private String apiOperation;
        /**
         * 商户请求路径。
         */
        private String requestPath;
        /**
         * 商户请求进入平台的时间。
         */
        private LocalDateTime requestTime;
        /**
         * 请求处理结果。
         */
        private String requestResult;
        /**
         * 平台响应生成时间。
         */
        private LocalDateTime responseTime;
        /**
         * 平台响应结果。
         */
        private String responseResult;
        /**
         * 商户侧可见响应码。
         */
        private String merchantResponseCode;
        /**
         * 商户侧可见响应描述。
         */
        private String merchantResponseMessage;
        /**
         * OpenAPI 到支付核心处理耗时，单位为毫秒。
         */
        private Integer durationMillis;
        /**
         * 调用链追踪号。
         */
        private String traceId;
    }
}
