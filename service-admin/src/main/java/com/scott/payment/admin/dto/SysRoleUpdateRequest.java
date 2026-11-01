package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理后台角色编辑请求 DTO
 *
 * <p>用于更新角色展示信息、数据范围、状态和排序等可变配置。</p>
 */
@Data
public class SysRoleUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "roleId")
    private Long roleId;

    @Size(max = 100, message = "roleName length must be less than 100")
    private String roleName;

    private String dataScope;

    @Size(max = 500, message = "description length must be less than 500")
    private String description;

    private Integer status;

    /**
     * 排序号，数值越小越优先展示或匹配。
     */
    private Integer sortNo;
}
