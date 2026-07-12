package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserRoleGrantRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 后台用户角色授权请求，位于 service-admin 接口传输层；提交角色ID集合，权限下放边界由后端服务强制校验。
 * @status : create
 */
@Data
public class SysUserRoleGrantRequest {

    /**
     * 目标账号ID。
     */
    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    /**
     * 本次保存后的角色ID集合，允许为空表示清空角色。
     */
    private List<Long> roleIds;
}
