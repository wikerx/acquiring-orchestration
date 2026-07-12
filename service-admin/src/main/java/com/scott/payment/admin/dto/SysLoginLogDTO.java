package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysLoginLogDTO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 系统登录日志响应 DTO
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysLoginLogDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Login Log 数据传输对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
