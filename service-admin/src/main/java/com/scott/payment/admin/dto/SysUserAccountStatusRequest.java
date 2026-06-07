package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountStatusRequest
 * @date : 2026-06-07 00:00
 * @description : 管理后台用户状态更新请求
 * @status : create
 */
@Data
public class SysUserAccountStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "accountId")
    private Long accountId;

    @NotNull(message = "status")
    private Integer status;
}
