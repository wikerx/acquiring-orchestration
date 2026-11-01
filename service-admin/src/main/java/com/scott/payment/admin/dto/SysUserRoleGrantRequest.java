package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;


/**
 * sys用户角色授权请求模型，用于运营后台，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
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
