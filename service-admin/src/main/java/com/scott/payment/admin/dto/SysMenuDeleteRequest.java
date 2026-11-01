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

    /**
     * 菜单ID，用于定位 {@code SysMenuDeleteRequest} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @NotNull(message = "菜单ID不能为空")
    private Long menuId;
}
