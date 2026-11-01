package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理后台用户状态更新请求 DTO
 *
 * <p>用于后台用户启停切换，仅承载账号主键和目标状态值。</p>
 */
@Data
public class SysUserAccountStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "accountId")
    private Long accountId;

    @NotNull(message = "status")
    private Integer status;
}
