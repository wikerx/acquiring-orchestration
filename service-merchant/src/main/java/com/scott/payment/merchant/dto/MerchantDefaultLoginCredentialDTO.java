package com.scott.payment.merchant.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantDefaultLoginCredentialDTO
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 新建商户用户后一次性返回的默认登录凭据 DTO；密码属于敏感数据，只允许在创建响应中短暂展示。
 * @status : create
 */
@Data
public class MerchantDefaultLoginCredentialDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 登录账号。
     */
    private String loginAccount;

    /**
     * 本地开发种子账号初始密码。
     */
    private String password;
}
