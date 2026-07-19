package com.scott.payment.admin.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserMfaDTOs
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 后台用户 MFA 管理 DTO 集合，位于 service-admin 传输层；用于 OTP 开启、重置、豁免、解锁和审计日志查询。
 * @status : create
 */
public final class AdminUserMfaDTOs {

    private AdminUserMfaDTOs() {
    }

    /**
     * 用户 MFA 操作请求。
     */
    @Data
    public static class UserMfaActionRequest {

        /**
         * 目标账号ID。
         */
        @NotNull(message = "accountId is required")
        private Long accountId;

        /**
         * 操作原因，安全敏感操作必须填写。
         */
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason length must not exceed 500")
        private String reason;
    }

    /**
     * 用户 MFA 豁免请求。
     */
    @Data
    public static class UserMfaExemptRequest {

        /**
         * 目标账号ID。
         */
        @NotNull(message = "accountId is required")
        private Long accountId;

        /**
         * 豁免原因，必须说明审批依据。
         */
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason length must not exceed 500")
        private String reason;

        /**
         * 豁免截止时间，空表示长期豁免。
         */
        private LocalDateTime exemptUntil;
    }

    /**
     * 用户 MFA 状态响应。
     */
    @Data
    public static class UserMfaStatusResponse {

        /**
         * 账号ID。
         */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long accountId;

        /**
         * 登录账号。
         */
        private String loginAccount;

        /**
         * MFA 策略。
         */
        private String mfaPolicy;

        /**
         * MFA 状态。
         */
        private String mfaStatus;

        /**
         * 完成绑定时间。
         */
        private LocalDateTime bindTime;

        /**
         * 最近验证成功时间。
         */
        private LocalDateTime lastVerifyTime;

        /**
         * 锁定截止时间。
         */
        private LocalDateTime lockedUntil;

        /**
         * 豁免截止时间。
         */
        private LocalDateTime exemptUntil;
    }

    /**
     * 用户 MFA 日志查询。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UserMfaLogQuery extends PageRequest {

        /**
         * 账号ID。
         */
        private Long accountId;

        /**
         * 登录账号模糊查询。
         */
        private String loginAccount;

        /**
         * 操作类型。
         */
        private String actionType;

        /**
         * 操作结果。
         */
        private String result;

        /**
         * 查询时区，前端按该时区传入 beginTime/endTime。
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
     * 用户 MFA 日志响应。
     */
    @Data
    public static class UserMfaLogResponse {

        /**
         * 主键ID。
         */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        /**
         * 账号ID。
         */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long accountId;

        /**
         * 登录账号。
         */
        private String loginAccount;

        /**
         * 操作类型。
         */
        private String actionType;

        /**
         * 操作结果。
         */
        private String result;

        /**
         * 原因或失败说明。
         */
        private String reason;

        /**
         * 变更前策略。
         */
        private String beforePolicy;

        /**
         * 变更前状态。
         */
        private String beforeStatus;

        /**
         * 变更后策略。
         */
        private String afterPolicy;

        /**
         * 变更后状态。
         */
        private String afterStatus;

        /**
         * 操作人账号。
         */
        private String operatorLoginAccount;

        /**
         * 客户端 IP。
         */
        private String clientIp;

        /**
         * 事件时间。
         */
        private LocalDateTime eventTime;
    }
}
