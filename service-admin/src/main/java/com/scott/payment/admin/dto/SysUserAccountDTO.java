package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountDTO
 * @date : 2026-06-06 00:00
 * @description : 管理后台用户账号响应 DTO
 * @status : create
 */
@Data
public class SysUserAccountDTO {

    private Long accountId;
    private Long userId;
    private String loginAccount;
    private String realName;
    private String mobile;
    private String email;
    private String userType;
    private Integer status;
    private Integer locked;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
}
