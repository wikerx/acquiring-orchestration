package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理后台用户重置密码请求 DTO
 *
 * <p>用于后台管理员为指定账号发送一次性重置邮件，接口不接收、生成或返回明文密码。</p>
 */
@Data
public class SysUserAccountResetPasswordRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "accountId")
    private Long accountId;

}
