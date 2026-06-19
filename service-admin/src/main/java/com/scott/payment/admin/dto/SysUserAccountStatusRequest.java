package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountStatusRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台用户状态更新请求 DTO
 * @status : create
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
