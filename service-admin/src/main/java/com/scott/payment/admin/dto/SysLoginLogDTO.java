package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统登录日志响应 DTO
 */
@Data
public class SysLoginLogDTO {

    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 系统应用ID。
     */
    private Long appId;

    /**
     * 账号ID。
     */
    private Long accountId;

    /**
     * 用户主体ID。
     */
    private Long userId;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 登录账号。
     */
    private String loginAccount;

    /**
     * 登录IP。
     */
    private String loginIp;

    /**
     * User-Agent。
     */
    private String userAgent;

    /**
     * 登录状态：0失败，1成功。
     */
    private Integer loginStatus;

    /**
     * 失败原因。
     */
    private String failReason;

    /**
     * 登录时间。
     */
    private LocalDateTime loginAt;
}
