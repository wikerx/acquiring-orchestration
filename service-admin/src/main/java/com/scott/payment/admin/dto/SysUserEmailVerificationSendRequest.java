package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户管理发送邮箱验证邮件请求；仅定位目标账号，不允许直接修改邮箱验证事实。
 */
@Data
public class SysUserEmailVerificationSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 需要发送验证邮件的管理系统账号主键。
     */
    @NotNull(message = "账号ID不能为空")
    private Long accountId;
}
