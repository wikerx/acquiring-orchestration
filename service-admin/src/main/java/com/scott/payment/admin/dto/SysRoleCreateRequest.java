package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理后台角色新增请求 DTO
 *
 * <p>用于新增后台角色，承载角色编码、角色名称、数据范围和排序等配置。</p>
 */
@Data
public class SysRoleCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新增角色由服务端生成编码；保留字段兼容旧客户端，提交值不参与创建。 */
    @Size(max = 80, message = "roleCode length must be less than 80")
    private String roleCode;

    @NotBlank(message = "roleName")
    @Size(max = 100, message = "roleName length must be less than 100")
    private String roleName;

    private String dataScope;

    @Size(max = 500, message = "description length must be less than 500")
    private String description;

    /**
     * 排序号，数值越小越优先展示或匹配。
     */
    private Integer sortNo;
}
