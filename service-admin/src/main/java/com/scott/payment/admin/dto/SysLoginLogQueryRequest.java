package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统登录日志查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysLoginLogQueryRequest extends PageRequest {

    /**
     * 系统应用ID。
     */
    private Long appId;

    /**
     * 登录账号，支持右模糊查询。
     */
    private String loginAccount;

    /**
     * 登录IP。
     */
    private String loginIp;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 登录状态：0失败，1成功。
     */
    private Integer loginStatus;

    /**
     * 登录开始时间。
     */
    private LocalDateTime loginStartAt;

    /**
     * 登录结束时间。
     */
    private LocalDateTime loginEndAt;
}
