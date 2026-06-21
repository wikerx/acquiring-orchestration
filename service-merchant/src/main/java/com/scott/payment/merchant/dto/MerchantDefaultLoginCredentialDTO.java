package com.scott.payment.merchant.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 商户门户登录页本地开发默认凭据。
 *
 * <p>仅用于登录页初始化演示账号，不承载生产账号密码查询能力。</p>
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
