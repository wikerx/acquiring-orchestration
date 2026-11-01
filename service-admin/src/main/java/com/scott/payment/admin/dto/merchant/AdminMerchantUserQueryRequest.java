package com.scott.payment.admin.dto.merchant;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 管理端商户用户查询条件模型，用于运营后台，承载筛选字段、时间范围和分页边界，不包含数据范围授权结果。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminMerchantUserQueryRequest extends PageRequest {

    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;
    private String merchantName;
    /**
     * 请求中的登录账号，用于限定本次操作的输入和校验范围。
     */
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
    private String roleName;
    private String deptName;
    private String postName;
    private Integer status;
    /**
     * 请求中的创建开始时间，用于限定本次操作的输入和校验范围。
     */
    private LocalDateTime createdStartTime;
    /**
     * 请求中的创建结束时间，用于限定本次操作的输入和校验范围。
     */
    private LocalDateTime createdEndTime;
}
