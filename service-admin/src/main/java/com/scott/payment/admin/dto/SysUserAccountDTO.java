package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountDTO
 * @date : 2026-06-07 08:26
 * @email : scott_x@163.com
 * @description : Admin 系统用户账号 DTO，承载账号基础信息、组织归属、角色和登录安全状态。
 * @status : create
 */
@Data
public class SysUserAccountDTO {

    /**
     * 账号ID。
     */
    private Long accountId;
    /**
     * 用户主体ID。
     */
    private Long userId;
    /**
     * 所属部门ID。
     */
    private Long deptId;
    /**
     * 所属部门名称。
     */
    private String deptName;
    /**
     * 所属岗位ID集合。
     */
    private List<Long> postIds;
    /**
     * 所属岗位名称集合。
     */
    private List<String> postNames;
    /**
     * 已绑定角色ID集合。
     */
    private List<Long> roleIds;
    /**
     * 已绑定角色名称集合。
     */
    private List<String> roleNames;
    /**
     * 登录账号。
     */
    private String loginAccount;
    /**
     * 用户真实姓名。
     */
    private String realName;
    /**
     * 手机号。
     */
    private String mobile;
    /**
     * 邮箱。
     */
    private String email;
    /**
     * 用户类型。
     */
    private String userType;
    /**
     * 账号状态：1启用，0停用。
     */
    private Integer status;
    /**
     * 是否锁定：1锁定，0未锁定。
     */
    private Integer locked;
    /**
     * 最后登录时间。
     */
    private LocalDateTime lastLoginAt;
    /**
     * 最后登录IP。
     */
    private String lastLoginIp;
    /**
     * MFA 策略：OPTIONAL、REQUIRED、EXEMPT。
     */
    private String mfaPolicy;
    /**
     * MFA 状态：NOT_ENABLED、PENDING_BIND、ENABLED、RESET_REQUIRED、EXEMPT、LOCKED、DISABLED。
     */
    private String mfaStatus;
    /**
     * OTP 完成绑定时间。
     */
    private LocalDateTime mfaBindTime;
    /**
     * 最近一次 OTP 验证成功时间。
     */
    private LocalDateTime mfaLastVerifyTime;
    /**
     * OTP 豁免截止时间。
     */
    private LocalDateTime mfaExemptUntil;
    /**
     * OTP 临时锁定截止时间。
     */
    private LocalDateTime mfaLockedUntil;
    /**
     * 备注。
     */
    private String remark;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}
