package com.scott.payment.merchant.dto;

import lombok.Data;

import java.io.Serializable;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantDefaultLoginCredentialDTO
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Merchant Default Login Credential DTO 传输模型，位于 商户后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
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
