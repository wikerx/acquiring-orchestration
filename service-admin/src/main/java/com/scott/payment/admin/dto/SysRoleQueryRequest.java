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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleQueryRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Role Query 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysRoleQueryRequest extends PageRequest {

    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String roleCode;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String roleName;

    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private Integer status;
}
