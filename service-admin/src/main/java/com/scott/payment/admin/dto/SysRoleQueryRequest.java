package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleQueryRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台角色查询请求 DTO
 * @status : create
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
