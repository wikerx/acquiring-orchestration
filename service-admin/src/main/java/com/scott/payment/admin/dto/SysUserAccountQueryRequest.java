package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * sys用户账号查询条件模型，用于运营后台，承载筛选字段、时间范围和分页边界，不包含数据范围授权结果。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserAccountQueryRequest extends PageRequest {

    /**
     * 请求中的登录账号，用于限定本次操作的输入和校验范围。
     */
    private String loginAccount;
    /**
     * 手机号，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
     */
    private String mobile;
    /**
     * 系统管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    private String email;
    private Long deptId;
    private Integer status;
    /**
     * 邮箱验证状态筛选：PENDING、VERIFIED；为空时不限制。
     */
    private String emailVerificationStatus;
    /**
     * 账号激活状态筛选：PENDING_ACTIVATION、ACTIVE；为空时不限制。
     */
    private String activationStatus;
}
