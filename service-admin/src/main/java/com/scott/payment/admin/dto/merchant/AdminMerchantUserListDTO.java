package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Admin 商户用户列表 DTO，聚合账号、商户、组织归属、角色和启停状态供运营查询。
 */
@Data
public class AdminMerchantUserListDTO {

    private Long accountId;
    private Long userId;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;
    private String merchantName;
    private String loginAccount;
    private String realName;
    /**
     * 手机号，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
     */
    private String mobile;
    /**
     * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    private String email;
    private List<String> deptNames = Collections.emptyList();
    private List<String> postNames = Collections.emptyList();
    private List<String> roleNames = Collections.emptyList();
    private Integer status;
    private Boolean merchantAdmin;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createdAt;
}
