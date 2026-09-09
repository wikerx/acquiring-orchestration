package com.scott.payment.admin.dto.security;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SecurityInterceptEventDTOs
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 安全拦截事件后台 DTO 集合，位于 service-admin 传输层，用于查询、详情、导出和人工处理标记。
 * @status : create
 */
public final class SecurityInterceptEventDTOs {

    private SecurityInterceptEventDTOs() {
    }

    /**
     * 安全拦截事件分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SecurityInterceptEventQuery extends PageRequest {
        /**
         * 事件号，精确筛选。
         */
        private String eventNo;
        /**
         * 来源层级，例如 OPENAPI、CHANNEL。
         */
        private String sourceLayer;
        /**
         * 事件类型。
         */
        private String eventType;
        /**
         * 风险等级。
         */
        private String riskLevel;
        /**
         * 处置动作。
         */
        private String action;
        /**
         * 商户号。
         */
        private String merchantId;
        /**
         * 客户端 IP。
         */
        private String clientIp;
        /**
         * 请求路径模糊筛选。
         */
        private String requestPath;
        /**
         * 链路追踪号，用于关联本次安全拦截涉及的跨服务日志。
         */
        private String traceId;
        /**
         * 请求号，用于关联一次入口请求内的拦截、响应和审计记录。
         */
        private String requestId;
        /**
         * 命中规则编码。
         */
        private String hitRuleCode;
        /**
         * 处理状态：0 未处理，1 已处理，2 忽略。
         */
        private Integer processStatus;
        /**
         * 查询时区。后台按该时区解释 beginTime/endTime 后换算为事件入库时区查询。
         */
        private String queryTimeZone;
        /**
         * 事件开始时间。
         */
        private LocalDateTime beginTime;
        /**
         * 事件结束时间。
         */
        private LocalDateTime endTime;
    }

    /**
     * 安全拦截事件后台列表响应。
     */
    @Data
    public static class SecurityInterceptEventResponse {
        /**
         * 主键，前端按字符串处理避免 JS 精度丢失。
         */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        /**
         * 安全事件号。
         */
        private String eventNo;
        /**
         * 事件发生时间。
         */
        private LocalDateTime eventTime;
        /**
         * 来源层级。
         */
        private String sourceLayer;
        /**
         * 事件类型。
         */
        private String eventType;
        /**
         * 风险等级。
         */
        private String riskLevel;
        /**
         * 处置动作。
         */
        private String action;
        /**
         * 商户号。
         */
        private String merchantId;
        /**
         * 客户端 IP。
         */
        private String clientIp;
        /**
         * 请求方法。
         */
        private String requestMethod;
        /**
         * 请求路径。
         */
        private String requestPath;
        /**
         * 链路追踪号，用于关联本次安全拦截涉及的跨服务日志。
         */
        private String traceId;
        /**
         * 请求号，用于关联一次入口请求内的拦截、响应和审计记录。
         */
        private String requestId;
        /**
         * User-Agent 摘要。
         */
        private String userAgent;
        /**
         * 原因码。
         */
        private String reasonCode;
        /**
         * 原因说明。
         */
        private String reasonMessage;
        /**
         * 服务名。
         */
        private String serviceName;
        /**
         * 命中规则。
         */
        private String hitRuleCode;
        /**
         * 请求头摘要。
         */
        private String headerSummary;
        /**
         * 处理状态。
         */
        private Integer processStatus;
        /**
         * 处理备注。
         */
        private String processRemark;
        /**
         * 处理人。
         */
        private String processedBy;
        /**
         * 处理时间。
         */
        private LocalDateTime processedTime;
        /**
         * 创建时间。
         */
        private LocalDateTime gmtCreate;
        /**
         * 更新时间。
         */
        private LocalDateTime gmtModified;
    }

    /**
     * 安全事件处理标记请求。
     */
    @Data
    public static class SecurityInterceptEventMarkRequest {
        /**
         * 处理状态：1 已处理，2 忽略。
         */
        @NotNull(message = "processStatus is required")
        private Integer processStatus;
        /**
         * 处理备注。
         */
        private String processRemark;
    }
}
