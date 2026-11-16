package com.scott.payment.merchant.dto.transaction;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 商户端交易通知记录 DTO 集合。
 *
 * <p>模型只声明商户排查自身回调所需的脱敏字段，不接收 merchantId，也不返回管理端专用的
 * 地址哈希、配置版本、人工重发事件号或内部异常详情。</p>
 */
public final class MerchantNotificationDTOs {

    /**
     * 禁止实例化交易通知 DTO 聚合类。
     */
    private MerchantNotificationDTOs() {
    }

    /**
     * 交易通知分页查询条件，商户边界由服务端认证上下文单独传入。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class NotificationQuery extends PageRequest {

        private static final long serialVersionUID = 1L;

        /** 平台交易号。 */
        private String transactionId;
        /** 商户订单号。 */
        private String merchantOrderNo;
        /** 通知业务类型。 */
        private String notifyType;
        /** 通知任务状态。 */
        private String notifyStatus;
        /** 查询开始时间。 */
        private LocalDateTime beginTime;
        /** 查询结束时间。 */
        private LocalDateTime endTime;
        /** 查询时区，默认使用 Asia/Shanghai。 */
        private String queryTimeZone;
    }

    /**
     * 商户端通知任务列表行。
     */
    @Data
    public static class NotificationRecord implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商户通知任务号。 */
        private String notifyId;
        /** 平台交易号。 */
        private String transactionId;
        /** 交易动作号。 */
        private String operationId;
        /** 商户订单号。 */
        private String merchantOrderNo;
        /** 通知业务类型。 */
        private String notifyType;
        /** 触发通知的交易事件类型。 */
        private String eventType;
        /** 通知任务状态。 */
        private String notifyStatus;
        /** 最近一次通知尝试序号。 */
        private Integer lastAttemptNo;
        /** 自动通知最大尝试次数。 */
        private Integer maxRetryCount;
        /** 下一次自动重试时间。 */
        private LocalDateTime nextRetryTime;
        /** 通知成功时间。 */
        private LocalDateTime successTime;
        /** 商户可见的失败分类编码。 */
        private String failureCode;
        /** 原交易真实分片时间。 */
        private LocalDateTime transactionDateTime;
        /** 通知任务创建时间。 */
        private LocalDateTime createTime;
        /** 通知任务更新时间。 */
        private LocalDateTime updateTime;
    }

    /**
     * 商户端通知任务详情。
     */
    @Data
    public static class NotificationDetail implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商户通知任务号。 */
        private String notifyId;
        /** 平台交易号。 */
        private String transactionId;
        /** 交易动作号。 */
        private String operationId;
        /** 商户订单号。 */
        private String merchantOrderNo;
        /** 通知业务类型。 */
        private String notifyType;
        /** 触发通知的交易事件类型。 */
        private String eventType;
        /** 通知任务状态。 */
        private String notifyStatus;
        /** 已脱敏的实际回调地址。 */
        private String targetUrlMasked;
        /** 已脱敏的通知业务载荷。 */
        private String payloadJsonMasked;
        /** 通知签名方式。 */
        private String signType;
        /** 最近一次通知尝试序号。 */
        private Integer lastAttemptNo;
        /** 自动通知最大尝试次数。 */
        private Integer maxRetryCount;
        /** 下一次自动重试时间。 */
        private LocalDateTime nextRetryTime;
        /** 通知成功时间。 */
        private LocalDateTime successTime;
        /** 商户可见的失败分类编码。 */
        private String failureCode;
        /** 原交易真实分片时间。 */
        private LocalDateTime transactionDateTime;
        /** 通知任务创建时间。 */
        private LocalDateTime createTime;
        /** 通知任务更新时间。 */
        private LocalDateTime updateTime;
    }

    /**
     * 商户端单次通知投递记录。
     */
    @Data
    public static class DeliveryLog implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 投递日志业务号。 */
        private String notifyLogId;
        /** 商户通知任务号。 */
        private String notifyId;
        /** 投递模式，例如 AUTO 或 MANUAL。 */
        private String deliveryMode;
        /** 本次投递尝试序号。 */
        private Integer attemptNo;
        /** 商户回调 HTTP 响应状态码。 */
        private Integer httpStatus;
        /** 已脱敏的通知请求头。 */
        private String requestHeaderJsonMasked;
        /** 已脱敏的通知请求体。 */
        private String requestBodyJsonMasked;
        /** 已脱敏的商户响应体。 */
        private String responseBodyJsonMasked;
        /** 本次投递是否成功。 */
        private Boolean success;
        /** 商户可见的失败分类编码。 */
        private String failureCode;
        /** 通知发起时间。 */
        private LocalDateTime notifyTime;
        /** 通知响应完成时间。 */
        private LocalDateTime responseTime;
        /** 通知耗时，单位为毫秒。 */
        private Integer durationMillis;
        /** 固定为 POST 的回调 HTTP 方法。 */
        private String httpMethod;
    }

    /**
     * 通知任务及其投递尝试详情。
     */
    @Data
    public static class NotificationDetailResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 通知任务脱敏快照。 */
        private NotificationDetail notification;
        /** 兼容旧客户端的投递记录字段，主详情接口固定返回空集合；请使用独立分页接口查询。 */
        private List<DeliveryLog> deliveryLogs = Collections.emptyList();
    }

    /**
     * 商户人工重发通知请求。
     *
     * <p>商户号不从请求体接收；服务端使用认证上下文绑定任务归属。请求号由客户端为一次点击生成，
     * 网络重试必须复用同一个值，Outbox 才能返回同一幂等结果。</p>
     */
    @Data
    public static class NotificationRetryRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商户通知任务号。 */
        @NotBlank
        @Size(max = 64)
        private String notifyId;

        /** 原交易真实分片时间。 */
        @NotNull
        private LocalDateTime transactionDateTime;

        /** 本次人工重发客户端请求号。 */
        @NotBlank
        @Size(max = 48)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,47}")
        private String requestId;
    }

    /**
     * 认证商户可人工重发的通知任务定位结果。
     *
     * <p>该对象只在应用层内部传递，不暴露通知明文、回调地址或内部失败原因。</p>
     */
    @Data
    public static class NotificationRetryTarget {

        /** 商户通知任务号。 */
        private String notifyId;
        /** 平台交易号。 */
        private String transactionId;
        /** 原交易真实分片时间。 */
        private LocalDateTime transactionDateTime;
    }

}
