package com.scott.payment.component.db.auth.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthAccountDTO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 登录账号基础响应
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthAccountDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Auth Account 数据传输对象，位于 component-library/component-db 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
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
     * 商户号。
     */
    private String merchantId;

    /**
     * 是否商户管理员，仅商户系统登录时有业务含义。
     */
    private Boolean merchantAdmin;

    /**
     * 账号状态。
     */
    private Integer status;
}
