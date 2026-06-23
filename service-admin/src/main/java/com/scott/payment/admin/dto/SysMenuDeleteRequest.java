package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 菜单删除请求 DTO。
 *
 * <p>用于逻辑删除指定菜单，删除前由服务层校验子菜单、权限资源和授权关系。</p>
 */
@Data
public class SysMenuDeleteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "菜单ID不能为空")
    private Long menuId;
}
