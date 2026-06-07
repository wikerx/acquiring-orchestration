package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleQueryRequest
 * @date : 2026-06-07 00:00
 * @description : 管理后台角色查询请求
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysRoleQueryRequest extends PageRequest {

    private String roleCode;

    private String roleName;

    private Integer status;
}
