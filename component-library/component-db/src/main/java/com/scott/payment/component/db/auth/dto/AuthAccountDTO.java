package com.scott.payment.component.db.auth.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthAccountDTO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 登录账号基础响应
 * @status : create
 */
@Data
public class AuthAccountDTO implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 账号ID。
     */
    private Long accountId;

    /**
     * 用户主体ID。
     */
    private Long userId;

    /**
     * 商户端用户ID，仅商户系统登录时返回。
     */
    private Long merchantUserId;

    /**
     * 应用编码。
     */
    private String appCode;

    /**
     * 登录账号。
     */
    private String loginAccount;

    /**
     * 用户姓名。
     */
    private String realName;

    /**
     * 用户昵称，用于个人中心资料展示。
     */
    private String nickname;

    /**
     * 用户信息表手机号，用于个人中心资料展示。
     */
    private String mobile;

    /**
     * 用户信息表邮箱，用于个人中心资料展示。
     */
    private String email;

    /**
     * 当前账号角色名称集合，仅用于页面展示，权限判断仍使用登录响应 roles 中的角色编码。
     */
    private List<String> roleNames = Collections.emptyList();

    /**
     * 用户主体创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 商户业务时区，仅商户系统登录时由商户基础资料返回。
     */
    private String timezone;

    /**
     * 是否商户管理员，仅商户系统登录时有业务含义。
     */
    private Boolean merchantAdmin;

    /**
     * 账号状态。
     */
    private Integer status;
}
