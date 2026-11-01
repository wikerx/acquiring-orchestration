package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * Sys Menu Delete Request 删除请求模型，用于运营后台，承载批量删除、软删除或停用操作所需的记录标识。
 */
@Data
public class SysMenuDeleteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "菜单ID不能为空")
    private Long menuId;
}
