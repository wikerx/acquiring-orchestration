package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台用户账号响应 DTO。
 *
 * <p>用于用户列表、详情和导出场景，聚合账号、自然人资料、部门和岗位摘要。</p>
 */
@Data
public class SysUserAccountDTO {

    private Long accountId;
    private Long userId;
    private Long deptId;
    private String deptName;
    private List<Long> postIds;
    private List<String> postNames;
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
