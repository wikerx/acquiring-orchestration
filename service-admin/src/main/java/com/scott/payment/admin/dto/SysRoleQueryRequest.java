package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台角色查询请求 DTO
 *
 * <p>用于角色分页检索，支持按角色编码、角色名称和状态过滤。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysRoleQueryRequest extends PageRequest {

    private String roleCode;

    private String roleName;

    private Integer status;
}
