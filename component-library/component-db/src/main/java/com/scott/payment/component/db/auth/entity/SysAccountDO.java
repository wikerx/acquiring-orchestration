package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysAccountDO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 系统登录账号数据库实体
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysAccountDO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Account 数据库实体，位于 component-library/component-db 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@TableName("sys_account")
public class SysAccountDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 系统应用ID。
     */
    private Long appId;

    /**
     * 用户主体ID。
     */
    private Long userId;

    /**
     * 商户号，商户系统账号必须绑定已有 base_merchant_info。
     */
    private String merchantId;

    /**
     * 登录账号。
     */
    private String loginAccount;

    /**
     * 密码哈希。
     */
    private String passwordHash;

    /**
     * 密码盐。
     */
    private String passwordSalt;

    /**
     * 密码算法。
     */
    private String passwordAlgo;

    /**
     * 登录手机号。
     */
    private String mobile;

    /**
     * 登录邮箱。
     */
    private String email;

    /**
     * 是否开启 MFA。
     */
    private Integer mfaEnabled;

    /**
     * MFA 类型。
     */
    private String mfaType;

    /**
     * TOTP 密钥。
     */
    private String totpSecret;

    /**
     * 密码是否过期。
     */
    private Integer passwordExpired;

    /**
     * 密码更新时间。
     */
    private LocalDateTime passwordUpdatedAt;

    /**
     * 最后登录时间。
     */
    private LocalDateTime lastLoginAt;

    /**
     * 最后登录IP。
     */
    private String lastLoginIp;

    /**
     * 连续失败次数。
     */
    private Integer failedLoginCount;

    /**
     * 是否锁定。
     */
    private Integer locked;

    /**
     * 锁定时间。
     */
    private LocalDateTime lockedAt;

    /**
     * 锁定原因。
     */
    private String lockedReason;

    /**
     * 状态：0停用，1启用。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 创建人ID。
     */
    private Long createdBy;

    /**
     * 修改时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 修改人ID。
     */
    private Long updatedBy;

    /**
     * 删除标识：0未删除，大于0为删除记录ID。
     */
    private Long deleted;
}
