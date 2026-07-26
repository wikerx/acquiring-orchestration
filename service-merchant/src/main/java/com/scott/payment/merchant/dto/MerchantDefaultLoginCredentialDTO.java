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
 * @description : MerchantDefaultLoginCredentialDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
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
