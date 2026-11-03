package com.scott.payment.merchant.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 本地开发默认登录凭据配置。
 *
 * <p>该能力默认关闭，且密码必须由进程外配置显式注入，禁止在源码或默认配置中保存固定口令。</p>
 */
@Component
@ConfigurationProperties(prefix = "payment.merchant.default-login")
public class MerchantDefaultLoginCredentialProperties implements InitializingBean {

    /**
     * 开发凭据接口是否启用。
     */
    private boolean enabled;

    /**
     * 本地种子商户登录账号。
     */
    private String loginAccount = "admin";

    /**
     * 本地种子商户号；配置后凭据接口只查询该商户，避免多商户环境选中其他账号。
     */
    private String merchantId;

    /**
     * 本地种子商户初始密码，只能由环境变量或密钥配置注入。
     */
    private String password;

    /**
     * 校验显式启用时的商户、账号和密码配置，阻止空值或弱口令进入公开登录页。
     */
    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(merchantId)) {
            throw new IllegalStateException("payment.merchant.default-login.merchant-id must be configured");
        }
        if (!StringUtils.hasText(loginAccount)) {
            throw new IllegalStateException("payment.merchant.default-login.login-account must be configured");
        }
        if (!StringUtils.hasText(password)
                || password.getBytes(StandardCharsets.UTF_8).length < 12) {
            throw new IllegalStateException(
                    "payment.merchant.default-login.password must be configured with at least 12 bytes");
        }
    }

    /**
     * 返回开发凭据接口是否启用。
     *
     * @return 启用状态
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置开发凭据接口启用状态。
     *
     * @param enabled 启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回本地种子商户登录账号。
     *
     * @return 登录账号
     */
    public String getLoginAccount() {
        return loginAccount;
    }

    /**
     * 设置本地种子商户登录账号。
     *
     * @param loginAccount 登录账号
     */
    public void setLoginAccount(String loginAccount) {
        this.loginAccount = loginAccount;
    }

    /**
     * 返回本地种子商户号。
     *
     * @return 商户号，未配置时为空
     */
    public String getMerchantId() {
        return merchantId;
    }

    /**
     * 设置本地种子商户号。
     *
     * @param merchantId 商户号
     */
    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    /**
     * 返回进程外注入的本地种子商户初始密码。
     *
     * @return 初始密码
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置进程外注入的本地种子商户初始密码。
     *
     * @param password 初始密码
     */
    public void setPassword(String password) {
        this.password = password;
    }
}
