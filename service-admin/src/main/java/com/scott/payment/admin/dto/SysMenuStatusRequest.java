package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuStatusRequest
 * @date : 2026-06-07 00:00
 * @description : 后台菜单状态请求对象
 * @status : create
 */
@Data
public class SysMenuStatusRequest {

    @NotNull(message = "菜单ID不能为空")
    private Long menuId;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
