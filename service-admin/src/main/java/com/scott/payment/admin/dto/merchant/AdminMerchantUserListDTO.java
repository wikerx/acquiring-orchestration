package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 管理端商户用户列表响应。
 *
 * <p>手机号、邮箱和登录 IP 返回脱敏值，禁止携带密码、验证码、token、密钥等敏感凭据。</p>
 */
@Data
public class AdminMerchantUserListDTO {

    private Long accountId;
    private Long userId;
    private String merchantId;
    private String merchantName;
    private String loginAccount;
    private String realName;
    private String mobile;
    private String email;
    private List<String> deptNames = Collections.emptyList();
    private List<String> postNames = Collections.emptyList();
    private List<String> roleNames = Collections.emptyList();
    private Integer status;
    private Boolean merchantAdmin;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
}
