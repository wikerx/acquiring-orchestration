package com.scott.payment.merchant.converter;

import com.scott.payment.component.db.auth.dto.AuthMenuDTO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.entity.SysMerchantDeptDO;
import com.scott.payment.component.db.auth.entity.SysMerchantPostDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PermissionDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/**
 * 商户系统转换器，将组织、角色、权限和菜单实体转换为门户响应。
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MerchantSystemConverter {

    MerchantSystemConverter INSTANCE = Mappers.getMapper(MerchantSystemConverter.class);

    /**
     * 将商户部门实体转换为部门响应。
     * @param dept 商户部门持久化记录
     * @return 构造、转换或解析后的业务值
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "deptId", source = "id")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "deptCode", source = "deptCode")
    @Mapping(target = "deptName", source = "deptName")
    @Mapping(target = "leaderAccountId", source = "leaderAccountId")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "sortNo", source = "sortNo")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "remark", source = "remark")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    DeptDTO toDeptDTO(SysMerchantDeptDO dept);

    /**
     * 将商户岗位实体转换为岗位响应。
     * @param post 商户岗位持久化记录
     * @return 构造、转换或解析后的业务值
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "postId", source = "id")
    @Mapping(target = "postCode", source = "postCode")
    @Mapping(target = "postName", source = "postName")
    @Mapping(target = "sortNo", source = "sortNo")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "remark", source = "remark")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    PostDTO toPostDTO(SysMerchantPostDO post);

    /**
     * 将角色实体转换为角色响应。
     * @param role 商户角色持久化记录
     * @return 构造、转换或解析后的业务值
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "roleId", source = "id")
    @Mapping(target = "roleCode", source = "roleCode")
    @Mapping(target = "roleName", source = "roleName")
    @Mapping(target = "roleType", source = "roleType")
    @Mapping(target = "dataScope", source = "dataScope")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "sortNo", source = "sortNo")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    RoleDTO toRoleDTO(SysRoleDO role);

    /**
     * 将权限实体转换为权限响应。
     * @param permission 商户权限持久化记录
     * @return 构造、转换或解析后的业务值
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "permissionId", source = "id")
    @Mapping(target = "menuId", source = "menuId")
    @Mapping(target = "permissionCode", source = "permissionCode")
    @Mapping(target = "permissionName", source = "permissionName")
    @Mapping(target = "permissionType", source = "permissionType")
    @Mapping(target = "resourceMethod", source = "resourceMethod")
    @Mapping(target = "resourcePath", source = "resourcePath")
    PermissionDTO toPermissionDTO(SysPermissionDO permission);

    /**
     * 将菜单实体转换为授权菜单响应。
     * @param menu 商户菜单持久化记录
     * @return 构造、转换或解析后的业务值
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "menuCode", source = "menuCode")
    @Mapping(target = "menuName", source = "menuName")
    @Mapping(target = "menuType", source = "menuType")
    @Mapping(target = "routePath", source = "routePath")
    @Mapping(target = "componentPath", source = "componentPath")
    @Mapping(target = "permissionCode", source = "permissionCode")
    @Mapping(target = "icon", source = "icon")
    @Mapping(target = "visible", source = "visible")
    @Mapping(target = "sortNo", source = "sortNo")
    @Mapping(target = "externalLink", source = "externalLink")
    AuthMenuDTO toAuthMenuDTO(SysMenuDO menu);
}
