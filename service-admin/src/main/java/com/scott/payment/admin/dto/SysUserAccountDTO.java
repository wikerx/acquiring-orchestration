package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountDTO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理后台用户账号响应 DTO
 * @status : create
 *
 * <p>用于用户列表与详情展示，承载账号标识、联系人信息、状态和最近登录摘要。</p>
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
