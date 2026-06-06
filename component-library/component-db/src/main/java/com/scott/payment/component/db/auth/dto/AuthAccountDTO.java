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
     * 账号状态。
     */
    private Integer status;
}
