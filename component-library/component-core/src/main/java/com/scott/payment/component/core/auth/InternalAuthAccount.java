package com.scott.payment.component.core.auth;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalAuthAccount
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 内部管理类系统当前登录账号上下文
 * @status : create
 */
@Data
public class InternalAuthAccount implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 应用编码，例如 ADMIN、MERCHANT。
     */
    private String appCode;

    /**
     * 系统应用ID。
     */
    private Long appId;

    /**
     * 登录账号ID。
     */
    private Long accountId;

    /**
     * 用户主体ID。
     */
    private Long userId;

    /**
     * 商户号，后台账号为空，商户账号必填。
     */
    private String merchantId;

    /**
     * 登录账号。
     */
    private String loginAccount;

    /**
     * 用户真实姓名。
     */
    private String realName;

    /**
     * 当前账号拥有的权限编码集合。
     */
    private List<String> permissions = Collections.emptyList();
}
