package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 管理后台角色菜单与操作权限统一保存请求。
 */
@Data
public class SysRoleGrantTreeSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "roleId")
    private Long roleId;

    private List<Long> menuIds = Collections.emptyList();

    private List<Long> permissionIds = Collections.emptyList();
}
