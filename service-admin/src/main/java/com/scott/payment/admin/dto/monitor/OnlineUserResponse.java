package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端在线用户会话响应。
 */
@Data
public class OnlineUserResponse {

    /**
     * 登录会话主键；使用字符串返回，避免浏览器解析 BIGINT 时丢失精度。
     */
    private String sessionId;

    /**
     * 用户真实姓名；缺失时回退为登录账号。
     */
    private String userName;

    /**
     * 登录 IP 地址。
     */
    private String loginIp;

    /**
     * 登录时间。
     */
    private LocalDateTime loginTime;

    /**
     * 登录客户端 User-Agent。
     */
    private String userAgent;
}
